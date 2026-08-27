package com.frauddetect.fraud.service;

import com.frauddetect.fraud.domain.cassandra.CustomerBehavior;
import com.frauddetect.fraud.domain.cassandra.FraudFeatures;
import com.frauddetect.fraud.domain.cassandra.HistoricalActivity;
import com.frauddetect.fraud.domain.cassandra.TransactionVelocity;
import com.frauddetect.fraud.domain.cassandra.VelocityKey;
import com.frauddetect.fraud.engine.Decision;
import com.frauddetect.fraud.engine.FraudScore;
import com.frauddetect.fraud.feature.GeoUtil;
import com.frauddetect.fraud.feature.TransactionFeatures;
import com.frauddetect.fraud.repository.cassandra.CustomerBehaviorRepository;
import com.frauddetect.fraud.repository.cassandra.FraudFeaturesRepository;
import com.frauddetect.fraud.repository.cassandra.HistoricalActivityRepository;
import com.frauddetect.fraud.repository.cassandra.TransactionVelocityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Cassandra-backed behavioural layer (Section 5). Two responsibilities:
 * <ol>
 *   <li>{@link #buildFeatures(TransactionContext)} — read the customer's profile + recent velocity
 *       window and derive the {@link TransactionFeatures} the engine and model score against;</li>
 *   <li>{@link #recordOutcome(TransactionContext, TransactionFeatures, FraudScore)} — persist the
 *       velocity row, update the rolling behavioural profile, and append the durable feature vector
 *       and activity-history rows.</li>
 * </ol>
 *
 * <p>All reads are query-first: the velocity window uses the {@code (customer_id, day_bucket)}
 * partition with an {@code event_time} clustering range, so no scatter-gather or {@code ALLOW
 * FILTERING}. All writes are idempotent upserts keyed by {@link VelocityKey}, so redelivery of the
 * same transaction is naturally safe on the Cassandra side.
 *
 * <p>Modelling note: {@code failed_tx_last_hour} counts prior transactions the platform itself
 * BLOCKED in the trailing hour — the "repeated failed transactions" pattern (card testing) is
 * operationalised against block decisions, which is the outcome this service authoritatively owns.
 */
@Service
public class BehaviorService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorService.class);

    private static final DateTimeFormatter BUCKET_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final Duration WINDOW_24H = Duration.ofHours(24);
    private static final Duration WINDOW_1H = Duration.ofHours(1);
    /** Bound the profile set columns so a hot customer's row cannot grow without limit. */
    private static final int MAX_SET_SIZE = 100;

    private final CustomerBehaviorRepository behaviorRepository;
    private final TransactionVelocityRepository velocityRepository;
    private final FraudFeaturesRepository fraudFeaturesRepository;
    private final HistoricalActivityRepository historicalActivityRepository;

    public BehaviorService(CustomerBehaviorRepository behaviorRepository,
                           TransactionVelocityRepository velocityRepository,
                           FraudFeaturesRepository fraudFeaturesRepository,
                           HistoricalActivityRepository historicalActivityRepository) {
        this.behaviorRepository = behaviorRepository;
        this.velocityRepository = velocityRepository;
        this.fraudFeaturesRepository = fraudFeaturesRepository;
        this.historicalActivityRepository = historicalActivityRepository;
    }

    /** Reads Cassandra and derives the feature vector for {@code ctx}. Never mutates state. */
    public TransactionFeatures buildFeatures(TransactionContext ctx) {
        Instant ref = ctx.occurredAt() != null ? ctx.occurredAt() : Instant.now();
        Instant since24h = ref.minus(WINDOW_24H);
        Instant since1h = ref.minus(WINDOW_1H);

        CustomerBehavior behavior = behaviorRepository.findById(ctx.customerId()).orElse(null);
        boolean hasHistory = behavior != null && behavior.getTxCount() > 0;

        List<String> buckets = bucketsCovering(since24h, ref);
        List<TransactionVelocity> recent = velocityRepository.findRecent(ctx.customerId(), buckets, since24h);

        long txCountLast24h = 0;
        long txCountLastHour = 0;
        long failedTxLastHour = 0;
        double amountSumLast24h = 0.0;
        Set<String> countries24h = new HashSet<>();

        for (TransactionVelocity v : recent) {
            Instant t = v.getKey() != null ? v.getKey().getEventTime() : null;
            // Defensive: the query already bounds the lower edge; ignore any clock-skewed future rows.
            if (t == null || t.isBefore(since24h) || t.isAfter(ref)) {
                continue;
            }
            // Skip this transaction's own velocity row (present only on reprocessing) so a redelivered
            // event never inflates its own velocity features — keeps buildFeatures idempotent.
            if (ctx.transactionId().equals(v.getKey().getTransactionId())) {
                continue;
            }
            txCountLast24h++;
            amountSumLast24h += v.getAmount();
            if (v.getCountryCode() != null && !v.getCountryCode().isBlank()) {
                countries24h.add(v.getCountryCode());
            }
            if (!t.isBefore(since1h)) {
                txCountLastHour++;
                if (isBlocked(v.getStatus())) {
                    failedTxLastHour++;
                }
            }
        }

        boolean newDevice = hasHistory && present(ctx.deviceId())
                && !containsSafe(behavior.getKnownDevices(), ctx.deviceId());
        boolean newMerchant = hasHistory && present(ctx.merchantCategory())
                && !containsSafe(behavior.getKnownMerchantCategories(), ctx.merchantCategory());
        boolean countryChanged = hasHistory && behavior.getLastCountry() != null
                && present(ctx.countryCode()) && !behavior.getLastCountry().equals(ctx.countryCode());

        double kmFromLastTx = 0.0;
        long secondsSinceLastTx = 0;
        if (hasHistory) {
            kmFromLastTx = GeoUtil.distanceKm(behavior.getLastLatitude(), behavior.getLastLongitude(),
                    ctx.latitude(), ctx.longitude());
            if (behavior.getLastEventTime() != null) {
                long secs = Duration.between(behavior.getLastEventTime(), ref).getSeconds();
                secondsSinceLastTx = Math.max(0, secs);
            }
        }

        double avgAmount30d = behavior != null ? behavior.getAvgAmount30d() : 0.0;

        return new TransactionFeatures(
                ctx.amount().doubleValue(),
                ctx.currency(),
                ctx.type(),
                ctx.countryCode(),
                ctx.merchantCategoryOrEmpty(),
                txCountLastHour,
                txCountLast24h,
                amountSumLast24h,
                avgAmount30d,
                countries24h.size(),
                newDevice,
                newMerchant,
                failedTxLastHour,
                kmFromLastTx,
                secondsSinceLastTx,
                countryChanged);
    }

    /** Persists the velocity row, updates the profile, and appends feature + history rows. */
    public void recordOutcome(TransactionContext ctx, TransactionFeatures features, FraudScore score) {
        Instant ref = ctx.occurredAt() != null ? ctx.occurredAt() : Instant.now();
        String bucket = BUCKET_FMT.format(ref);
        VelocityKey key = new VelocityKey(ctx.customerId(), bucket, ref, ctx.transactionId());
        double amount = features.amount();

        velocityRepository.save(velocityRow(ctx, key, amount, score.decision()));
        fraudFeaturesRepository.save(featuresRow(key, features, score));
        historicalActivityRepository.save(historyRow(ctx, key, amount, score));
        updateBehavior(ctx, ref, amount);
    }

    private TransactionVelocity velocityRow(TransactionContext ctx, VelocityKey key, double amount, Decision decision) {
        TransactionVelocity v = new TransactionVelocity();
        v.setKey(key);
        v.setAmount(amount);
        v.setCurrency(ctx.currency());
        v.setCountryCode(ctx.countryCode());
        v.setMerchantId(ctx.merchantId());
        v.setMerchantCategory(ctx.merchantCategory());
        v.setDeviceId(ctx.deviceId());
        // Store the platform's decision so future velocity math can see BLOCKED attempts.
        v.setStatus(decision.name());
        return v;
    }

    private FraudFeatures featuresRow(VelocityKey key, TransactionFeatures f, FraudScore score) {
        FraudFeatures ff = new FraudFeatures();
        ff.setKey(key);
        ff.setAmount(f.amount());
        ff.setAvgAmount30d(f.avgAmount30d());
        ff.setTxCountLastHour(f.txCountLastHour());
        ff.setTxCountLast24h(f.txCountLast24h());
        ff.setAmountSumLast24h(f.amountSumLast24h());
        ff.setDistinctCountriesLast24h(f.distinctCountriesLast24h());
        ff.setNewDevice(f.newDevice());
        ff.setNewMerchant(f.newMerchant());
        ff.setFailedTxLastHour(f.failedTxLastHour());
        ff.setKmFromLastTx(f.kmFromLastTx());
        ff.setSecondsSinceLastTx(f.secondsSinceLastTx());
        ff.setModelRiskScore(score.modelRiskScore());
        ff.setScore(score.score());
        ff.setSeverity(score.severity().name());
        ff.setDecision(score.decision().name());
        return ff;
    }

    private HistoricalActivity historyRow(TransactionContext ctx, VelocityKey key, double amount, FraudScore score) {
        HistoricalActivity h = new HistoricalActivity();
        h.setKey(key);
        h.setAccountId(ctx.accountId());
        h.setAmount(amount);
        h.setCurrency(ctx.currency());
        h.setTransactionType(ctx.type());
        h.setCountryCode(ctx.countryCode());
        h.setMerchantId(ctx.merchantId());
        h.setMerchantCategory(ctx.merchantCategory());
        h.setScore(score.score());
        h.setSeverity(score.severity().name());
        h.setDecision(score.decision().name());
        return h;
    }

    private void updateBehavior(TransactionContext ctx, Instant ref, double amount) {
        CustomerBehavior behavior = behaviorRepository.findById(ctx.customerId()).orElseGet(() -> {
            CustomerBehavior fresh = new CustomerBehavior();
            fresh.setCustomerId(ctx.customerId());
            return fresh;
        });

        long newCount = behavior.getTxCount() + 1;
        double newSum = behavior.getAmountSum30d() + amount;
        behavior.setTxCount(newCount);
        behavior.setAmountSum30d(newSum);
        behavior.setAvgAmount30d(newSum / newCount);

        behavior.setKnownCountries(withValue(behavior.getKnownCountries(), ctx.countryCode()));
        behavior.setKnownDevices(withValue(behavior.getKnownDevices(), ctx.deviceId()));
        behavior.setKnownMerchantCategories(withValue(behavior.getKnownMerchantCategories(), ctx.merchantCategory()));

        if (present(ctx.countryCode())) {
            behavior.setLastCountry(ctx.countryCode());
        }
        if (ctx.latitude() != null && ctx.longitude() != null) {
            behavior.setLastLatitude(ctx.latitude());
            behavior.setLastLongitude(ctx.longitude());
        }
        behavior.setLastEventTime(ref);
        behavior.setUpdatedAt(Instant.now());

        behaviorRepository.save(behavior);
    }

    // ---- helpers ----

    /** UTC day buckets covering {@code [from, to]}; a 24h window spans at most two buckets. */
    private static List<String> bucketsCovering(Instant from, Instant to) {
        Set<String> buckets = new LinkedHashSet<>();
        buckets.add(BUCKET_FMT.format(from));
        buckets.add(BUCKET_FMT.format(to));
        return new ArrayList<>(buckets);
    }

    private static boolean isBlocked(String status) {
        return Decision.BLOCK.name().equalsIgnoreCase(status);
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean containsSafe(Set<String> set, String value) {
        return set != null && set.contains(value);
    }

    /** Returns a new set with {@code value} added (bounded), or the original when nothing to add. */
    private static Set<String> withValue(Set<String> current, String value) {
        Set<String> result = current == null ? new HashSet<>() : new HashSet<>(current);
        if (present(value) && result.size() < MAX_SET_SIZE) {
            result.add(value);
        }
        return result;
    }
}
