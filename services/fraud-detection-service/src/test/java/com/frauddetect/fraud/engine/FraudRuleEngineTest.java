package com.frauddetect.fraud.engine;

import com.frauddetect.common.domain.FraudSeverity;
import com.frauddetect.fraud.domain.FraudRuleEntity;
import com.frauddetect.fraud.domain.RuleType;
import com.frauddetect.fraud.engine.evaluators.RuleEvaluators;
import com.frauddetect.fraud.feature.TransactionFeatures;
import com.frauddetect.fraud.repository.FraudRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the configurable rule engine (Section 9): scoring aggregation, the 0-100 cap, and
 * the severity/decision mapping. The engine is exercised with the real evaluator strategies and a
 * seeded rule catalogue (mirroring {@code V1__init_fraud_detection.sql}) so the tests validate the
 * end-to-end scoring behaviour, not mocks of it.
 */
class FraudRuleEngineTest {

    private FraudRuleEngine engine;

    @BeforeEach
    void setUp() {
        FraudRuleRepository repository = mock(FraudRuleRepository.class);
        when(repository.findByEnabledTrue()).thenReturn(seededRules());

        List<RuleEvaluator> evaluators = List.of(
                new RuleEvaluators.LargeAmountEvaluator(),
                new RuleEvaluators.RapidVelocityEvaluator(),
                new RuleEvaluators.UnusualFrequencyEvaluator(),
                new RuleEvaluators.RepeatedFailuresEvaluator(),
                new RuleEvaluators.ImpossibleTravelEvaluator(),
                new RuleEvaluators.SuspiciousCountryEvaluator(),
                new RuleEvaluators.AbnormalMerchantEvaluator(),
                new RuleEvaluators.SuspiciousDeviceEvaluator(),
                new RuleEvaluators.ModelRiskEvaluator());

        engine = new FraudRuleEngine(repository, evaluators);
        engine.reload();
    }

    @Test
    void loadsAllSeededRules() {
        assertThat(engine.activeRuleCount()).isEqualTo(9);
    }

    @Test
    void cleanTransactionScoresLowAndIsAllowed() {
        FraudScore score = engine.evaluate(new Features().build(), null);

        assertThat(score.score()).isZero();
        assertThat(score.severity()).isEqualTo(FraudSeverity.LOW);
        assertThat(score.decision()).isEqualTo(Decision.ALLOW);
        assertThat(score.isFraudulent()).isFalse();
        assertThat(score.triggeredRuleCodes()).isEmpty();
        assertThat(score.primaryReason()).isEqualTo("No rule triggered");
    }

    @Test
    void largeAmountAloneIsMediumAndAllowed() {
        Features f = new Features();
        f.amount = 100_000;   // vastly exceeds both the 5000 ceiling and 10x the 100 average
        f.avg30d = 100;

        FraudScore score = engine.evaluate(f.build(), null);

        assertThat(score.triggeredRuleCodes()).containsExactly("LARGE_AMOUNT");
        assertThat(score.score()).isEqualTo(30);
        assertThat(score.severity()).isEqualTo(FraudSeverity.MEDIUM);
        assertThat(score.decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void threeSignalsReachHighAndHoldForReview() {
        Features f = new Features();
        f.amount = 100_000;       // LARGE_AMOUNT   -> 30
        f.avg30d = 100;
        f.newDevice = true;       // SUSPICIOUS_DEVICE -> 20
        f.newMerchant = true;     // ABNORMAL_MERCHANT -> 15

        FraudScore score = engine.evaluate(f.build(), null);

        assertThat(score.score()).isEqualTo(65);
        assertThat(score.severity()).isEqualTo(FraudSeverity.HIGH);
        assertThat(score.decision()).isEqualTo(Decision.REVIEW);
        assertThat(score.isFraudulent()).isTrue();
        assertThat(score.triggeredRuleCodes())
                .contains("LARGE_AMOUNT", "SUSPICIOUS_DEVICE", "ABNORMAL_MERCHANT");
    }

    @Test
    void manySignalsSaturateAtCriticalAndBlock() {
        Features f = new Features();
        f.amount = 100_000;       // LARGE_AMOUNT      -> 30
        f.avg30d = 100;
        f.txCountLastHour = 20;   // RAPID_VELOCITY    -> 25
        f.txCountLast24h = 20;
        f.newDevice = true;       // SUSPICIOUS_DEVICE -> 20
        f.newMerchant = true;     // ABNORMAL_MERCHANT -> 15

        FraudScore score = engine.evaluate(f.build(), null);

        assertThat(score.score()).isEqualTo(90); // 30+25+20+15, still under the 100 cap
        assertThat(score.severity()).isEqualTo(FraudSeverity.CRITICAL);
        assertThat(score.decision()).isEqualTo(Decision.BLOCK);
        // Hits are ordered highest-points first, so the primary reason is the largest contributor.
        assertThat(score.hits().get(0).points()).isGreaterThanOrEqualTo(score.hits().get(1).points());
    }

    @Test
    void scoreIsCappedAtOneHundred() {
        Features f = new Features();
        f.amount = 100_000;
        f.avg30d = 100;
        f.txCountLastHour = 50;
        f.txCountLast24h = 100;
        f.failedTxLastHour = 10;
        f.newDevice = true;
        f.newMerchant = true;
        f.countryCode = "RU";        // high-risk list -> full 35
        f.kmFromLastTx = 20_000;     // impossible travel
        f.secondsSinceLastTx = 600;

        FraudScore score = engine.evaluate(f.build(), 0.99);

        assertThat(score.score()).isEqualTo(100);
        assertThat(score.severity()).isEqualTo(FraudSeverity.CRITICAL);
    }

    @Test
    void highRiskCountryFiresAtFullWeight() {
        Features f = new Features();
        f.countryCode = "RU";

        FraudScore score = engine.evaluate(f.build(), null);

        assertThat(score.triggeredRuleCodes()).containsExactly("SUSPICIOUS_COUNTRY");
        assertThat(score.score()).isEqualTo(35);
    }

    @Test
    void modelRiskContributesWhenPresentAndIsSkippedWhenDegraded() {
        FraudScore withModel = engine.evaluate(new Features().build(), 0.90);
        assertThat(withModel.triggeredRuleCodes()).contains("MODEL_RISK");
        assertThat(withModel.score()).isEqualTo(36); // round(40 * 0.90)

        FraudScore degraded = engine.evaluate(new Features().build(), null);
        assertThat(degraded.triggeredRuleCodes()).doesNotContain("MODEL_RISK");
        assertThat(degraded.score()).isZero();
    }

    // --- seeded rule catalogue (mirrors V1__init_fraud_detection.sql) ---

    private static List<FraudRuleEntity> seededRules() {
        return List.of(
                rule("LARGE_AMOUNT", RuleType.LARGE_AMOUNT, 30, new BigDecimal("5000.0000"), 10, null),
                rule("RAPID_VELOCITY", RuleType.RAPID_VELOCITY, 25, null, 5, null),
                rule("IMPOSSIBLE_TRAVEL", RuleType.IMPOSSIBLE_TRAVEL, 40, null, 900, null),
                rule("SUSPICIOUS_COUNTRY", RuleType.SUSPICIOUS_COUNTRY, 35, null, null,
                        "{\"countries\":[\"NG\",\"RU\",\"IR\",\"KP\",\"SY\",\"VE\"]}"),
                rule("UNUSUAL_FREQUENCY", RuleType.UNUSUAL_FREQUENCY, 20, null, 20, null),
                rule("REPEATED_FAILURES", RuleType.REPEATED_FAILURES, 30, null, 3, null),
                rule("ABNORMAL_MERCHANT", RuleType.ABNORMAL_MERCHANT, 15, null, null, null),
                rule("SUSPICIOUS_DEVICE", RuleType.SUSPICIOUS_DEVICE, 20, null, null, null),
                rule("MODEL_RISK", RuleType.MODEL_RISK, 40, new BigDecimal("0.7000"), null, null));
    }

    private static FraudRuleEntity rule(String code, RuleType type, int weight,
                                        BigDecimal numeric, Integer intThreshold, String params) {
        FraudRuleEntity r = new FraudRuleEntity();
        r.setCode(code);
        r.setName(code);
        r.setDescription(code);
        r.setRuleType(type);
        r.setWeight(weight);
        r.setEnabled(true);
        r.setThresholdNumeric(numeric);
        r.setThresholdInt(intThreshold);
        r.setParamsJson(params);
        return r;
    }

    /** Mutable builder for the immutable {@link TransactionFeatures} record — clean-transaction defaults. */
    private static final class Features {
        double amount = 100;
        String currency = "USD";
        String type = "PURCHASE";
        String countryCode = "US";
        String merchantCategory = "grocery";
        long txCountLastHour = 1;
        long txCountLast24h = 1;
        double amountSumLast24h = 100;
        double avg30d = 100;
        long distinctCountries = 1;
        boolean newDevice = false;
        boolean newMerchant = false;
        long failedTxLastHour = 0;
        double kmFromLastTx = 0;
        long secondsSinceLastTx = 0;
        boolean countryChanged = false;

        TransactionFeatures build() {
            return new TransactionFeatures(amount, currency, type, countryCode, merchantCategory,
                    txCountLastHour, txCountLast24h, amountSumLast24h, avg30d, distinctCountries,
                    newDevice, newMerchant, failedTxLastHour, kmFromLastTx, secondsSinceLastTx, countryChanged);
        }
    }
}
