package com.frauddetect.fraud.engine.evaluators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetect.fraud.domain.FraudRuleEntity;
import com.frauddetect.fraud.domain.RuleType;
import com.frauddetect.fraud.engine.RuleEvaluator;
import com.frauddetect.fraud.engine.RuleHit;
import com.frauddetect.fraud.feature.TransactionFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The concrete fraud rule evaluators (Section 9 detection catalogue), grouped as static
 * {@code @Component} strategies so the whole rule surface reads top-to-bottom in one place. Each is
 * discovered by Spring and registered by {@link com.frauddetect.fraud.engine.FraudRuleEngine} keyed
 * on its {@link RuleType}.
 *
 * <p>Threshold semantics per rule are documented on each evaluator and seeded in
 * {@code V1__init_fraud_detection.sql}. All firing rules award scaled points bounded by the rule's
 * configured {@code weight}, so re-weighting a rule immediately changes its influence.
 */
public final class RuleEvaluators {

    private RuleEvaluators() {
    }

    /** Scales a fired rule's points by how far the trigger ratio exceeds 1.0, capped at the weight. */
    static int scaledPoints(int weight, double ratio) {
        if (ratio < 1.0) {
            return 0;
        }
        // ratio 1.0 → ~half weight, ratio >=2.0 → full weight; always at least 1 point when fired.
        int points = (int) Math.round(weight * Math.min(ratio, 2.0) / 2.0);
        return Math.max(1, Math.min(weight, points));
    }

    // ---------------------------------------------------------------------
    // Amount-based
    // ---------------------------------------------------------------------

    /**
     * Unusually large transaction. Fires when the amount exceeds an absolute ceiling
     * ({@code thresholdNumeric}) OR a multiple ({@code thresholdInt}) of the customer's 30d average.
     */
    @Component
    public static class LargeAmountEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.LARGE_AMOUNT;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            double absCeil = rule.getThresholdNumeric() != null
                    ? rule.getThresholdNumeric().doubleValue() : Double.MAX_VALUE;
            int avgMultiple = rule.getThresholdInt() != null ? rule.getThresholdInt() : Integer.MAX_VALUE;

            double ratioAbs = f.amount() / absCeil;
            double ratioAvg = (f.avgAmount30d() > 0)
                    ? f.amount() / (f.avgAmount30d() * avgMultiple) : 0.0;
            double ratio = Math.max(ratioAbs, ratioAvg);

            int points = scaledPoints(rule.getWeight(), ratio);
            return points == 0 ? Optional.empty()
                    : Optional.of(new RuleHit(rule.getCode(), rule.getDescription(), rule.getWeight(), points));
        }
    }

    // ---------------------------------------------------------------------
    // Velocity / frequency
    // ---------------------------------------------------------------------

    /** Rapid repeated transactions: too many in the last hour ({@code thresholdInt}). */
    @Component
    public static class RapidVelocityEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.RAPID_VELOCITY;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            int max = rule.getThresholdInt() != null ? rule.getThresholdInt() : Integer.MAX_VALUE;
            if (max <= 0 || f.txCountLastHour() <= max) {
                return Optional.empty();
            }
            int points = scaledPoints(rule.getWeight(), (double) f.txCountLastHour() / max);
            return Optional.of(new RuleHit(rule.getCode(), rule.getDescription(), rule.getWeight(), points));
        }
    }

    /** Unusual daily frequency: transactions in the last 24h exceed {@code thresholdInt}. */
    @Component
    public static class UnusualFrequencyEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.UNUSUAL_FREQUENCY;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            int max = rule.getThresholdInt() != null ? rule.getThresholdInt() : Integer.MAX_VALUE;
            if (max <= 0 || f.txCountLast24h() <= max) {
                return Optional.empty();
            }
            int points = scaledPoints(rule.getWeight(), (double) f.txCountLast24h() / max);
            return Optional.of(new RuleHit(rule.getCode(), rule.getDescription(), rule.getWeight(), points));
        }
    }

    /** Repeated failed transactions preceding this one ({@code thresholdInt} in the last hour). */
    @Component
    public static class RepeatedFailuresEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.REPEATED_FAILURES;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            int max = rule.getThresholdInt() != null ? rule.getThresholdInt() : Integer.MAX_VALUE;
            if (max <= 0 || f.failedTxLastHour() < max) {
                return Optional.empty();
            }
            int points = scaledPoints(rule.getWeight(), (double) f.failedTxLastHour() / max);
            return Optional.of(new RuleHit(rule.getCode(), rule.getDescription(), rule.getWeight(), points));
        }
    }

    // ---------------------------------------------------------------------
    // Geography
    // ---------------------------------------------------------------------

    /** Impossible geographic movement: implied speed exceeds {@code thresholdInt} km/h. */
    @Component
    public static class ImpossibleTravelEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.IMPOSSIBLE_TRAVEL;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            int maxKmh = rule.getThresholdInt() != null ? rule.getThresholdInt() : Integer.MAX_VALUE;
            double speed = f.impliedSpeedKmh();
            if (maxKmh <= 0 || speed <= maxKmh) {
                return Optional.empty();
            }
            int points = scaledPoints(rule.getWeight(), speed / maxKmh);
            return Optional.of(new RuleHit(rule.getCode(), rule.getDescription(), rule.getWeight(), points));
        }
    }

    /**
     * Suspicious country: transaction originates in a configured high-risk country (full weight) or
     * the customer's origin country changed since their last transaction (partial weight).
     * High-risk list comes from {@code params_json}: {@code {"countries":["NG","RU",...]}}.
     */
    @Component
    public static class SuspiciousCountryEvaluator implements RuleEvaluator {

        private static final Logger log = LoggerFactory.getLogger(SuspiciousCountryEvaluator.class);
        // Self-constructed (thread-safe, reused): Boot 4 auto-configures a Jackson 3 JsonMapper, not a
        // Jackson 2 ObjectMapper bean, so this evaluator must not depend on one being injectable. It
        // only parses the small params_json country list — matching the search services' convention.
        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public RuleType type() {
            return RuleType.SUSPICIOUS_COUNTRY;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            Set<String> highRisk = parseCountries(rule.getParamsJson());
            String country = f.countryCode() == null ? "" : f.countryCode().toUpperCase(Locale.ROOT);

            if (highRisk.contains(country)) {
                return Optional.of(new RuleHit(rule.getCode(), rule.getDescription(),
                        rule.getWeight(), rule.getWeight()));
            }
            if (f.countryChanged()) {
                int points = Math.max(1, rule.getWeight() / 2);
                return Optional.of(new RuleHit(rule.getCode(),
                        rule.getDescription() + " (country change)", rule.getWeight(), points));
            }
            return Optional.empty();
        }

        private Set<String> parseCountries(String json) {
            Set<String> countries = new HashSet<>();
            if (json == null || json.isBlank()) {
                return countries;
            }
            try {
                JsonNode node = MAPPER.readTree(json).path("countries");
                if (node.isArray()) {
                    node.forEach(n -> countries.add(n.asText().toUpperCase(Locale.ROOT)));
                }
            } catch (Exception ex) {
                log.warn("Rule {} has invalid params_json, ignoring country list: {}",
                        rule(json), ex.getMessage());
            }
            return countries;
        }

        private static String rule(String json) {
            return json == null ? "<null>" : json;
        }
    }

    // ---------------------------------------------------------------------
    // Merchant / device
    // ---------------------------------------------------------------------

    /** Abnormal merchant behaviour: first time this customer transacts in this merchant category. */
    @Component
    public static class AbnormalMerchantEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.ABNORMAL_MERCHANT;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            if (!f.newMerchant()) {
                return Optional.empty();
            }
            return Optional.of(new RuleHit(rule.getCode(), rule.getDescription(),
                    rule.getWeight(), rule.getWeight()));
        }
    }

    /** Suspicious device: first time this device is seen for the customer. */
    @Component
    public static class SuspiciousDeviceEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.SUSPICIOUS_DEVICE;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            if (!f.newDevice()) {
                return Optional.empty();
            }
            return Optional.of(new RuleHit(rule.getCode(), rule.getDescription(),
                    rule.getWeight(), rule.getWeight()));
        }
    }

    // ---------------------------------------------------------------------
    // Model
    // ---------------------------------------------------------------------

    /**
     * Incorporates the gRPC risk-scoring model sub-score. Fires when the model score (0-1) meets
     * {@code thresholdNumeric}; points scale with the score. When the score is {@code null} (the
     * model was unavailable and we degraded gracefully) this rule simply does not fire — the
     * rule-based signals still produce a decision.
     */
    @Component
    public static class ModelRiskEvaluator implements RuleEvaluator {
        @Override
        public RuleType type() {
            return RuleType.MODEL_RISK;
        }

        @Override
        public Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures f, Double model) {
            if (model == null) {
                return Optional.empty();
            }
            double min = rule.getThresholdNumeric() != null
                    ? rule.getThresholdNumeric().doubleValue() : 0.0;
            if (model < min) {
                return Optional.empty();
            }
            int points = Math.max(1, Math.min(rule.getWeight(),
                    (int) Math.round(rule.getWeight() * model)));
            String desc = "%s (model risk %.2f)".formatted(rule.getDescription(), model);
            return Optional.of(new RuleHit(rule.getCode(), desc, rule.getWeight(), points));
        }
    }

    /** Utility retained for tests/documentation of threshold parsing. */
    static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
