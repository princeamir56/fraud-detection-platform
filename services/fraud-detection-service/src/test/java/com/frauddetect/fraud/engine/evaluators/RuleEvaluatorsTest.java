package com.frauddetect.fraud.engine.evaluators;

import com.frauddetect.fraud.domain.FraudRuleEntity;
import com.frauddetect.fraud.domain.RuleType;
import com.frauddetect.fraud.engine.RuleHit;
import com.frauddetect.fraud.feature.TransactionFeatures;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit tests for individual evaluators (Section 9) and the shared point-scaling helper,
 * covering the boundaries the aggregate engine test does not exercise directly.
 */
class RuleEvaluatorsTest {

    @Test
    void scaledPointsRespectsBoundaries() {
        assertThat(RuleEvaluators.scaledPoints(30, 0.5)).isZero();          // below trigger ratio
        assertThat(RuleEvaluators.scaledPoints(30, 1.0)).isEqualTo(15);     // ~half weight at ratio 1
        assertThat(RuleEvaluators.scaledPoints(30, 2.0)).isEqualTo(30);     // full weight at ratio >= 2
        assertThat(RuleEvaluators.scaledPoints(30, 9.0)).isEqualTo(30);     // never exceeds the weight
        assertThat(RuleEvaluators.scaledPoints(1, 1.0)).isEqualTo(1);       // always >= 1 when fired
    }

    @Test
    void suspiciousCountryAwardsPartialWeightOnCountryChange() {
        var evaluator = new RuleEvaluators.SuspiciousCountryEvaluator();
        FraudRuleEntity rule = rule(RuleType.SUSPICIOUS_COUNTRY, 35, null);

        Features f = new Features();
        f.countryCode = "US";      // not on the high-risk list
        f.countryChanged = true;   // ... but the origin country changed

        Optional<RuleHit> hit = evaluator.evaluate(rule, f.build(), null);

        assertThat(hit).isPresent();
        assertThat(hit.get().points()).isEqualTo(17); // max(1, weight/2)
        assertThat(hit.get().description()).contains("country change");
    }

    @Test
    void suspiciousCountryToleratesMalformedParamsJson() {
        var evaluator = new RuleEvaluators.SuspiciousCountryEvaluator();
        FraudRuleEntity rule = rule(RuleType.SUSPICIOUS_COUNTRY, 35, "{ not valid json");

        Features f = new Features();
        f.countryCode = "US";
        f.countryChanged = false;

        // A misconfigured country list must never throw; it simply yields no high-risk match.
        assertThat(evaluator.evaluate(rule, f.build(), null)).isEmpty();
    }

    @Test
    void impossibleTravelDoesNotFireWithoutDwellTime() {
        var evaluator = new RuleEvaluators.ImpossibleTravelEvaluator();
        FraudRuleEntity rule = rule(RuleType.IMPOSSIBLE_TRAVEL, 40, null);
        rule.setThresholdInt(900);

        Features f = new Features();
        f.kmFromLastTx = 10_000;
        f.secondsSinceLastTx = 0; // unknown dwell -> implied speed 0 -> cannot conclude impossible travel

        assertThat(evaluator.evaluate(rule, f.build(), null)).isEmpty();
    }

    @Test
    void repeatedFailuresFiresOnlyAtOrAboveThreshold() {
        var evaluator = new RuleEvaluators.RepeatedFailuresEvaluator();
        FraudRuleEntity rule = rule(RuleType.REPEATED_FAILURES, 30, null);
        rule.setThresholdInt(3);

        Features below = new Features();
        below.failedTxLastHour = 2;
        assertThat(evaluator.evaluate(rule, below.build(), null)).isEmpty();

        Features atThreshold = new Features();
        atThreshold.failedTxLastHour = 3;
        assertThat(evaluator.evaluate(rule, atThreshold.build(), null)).isPresent();
    }

    private static FraudRuleEntity rule(RuleType type, int weight, String params) {
        FraudRuleEntity r = new FraudRuleEntity();
        r.setCode(type.name());
        r.setName(type.name());
        r.setDescription(type.name());
        r.setRuleType(type);
        r.setWeight(weight);
        r.setEnabled(true);
        r.setParamsJson(params);
        return r;
    }

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
