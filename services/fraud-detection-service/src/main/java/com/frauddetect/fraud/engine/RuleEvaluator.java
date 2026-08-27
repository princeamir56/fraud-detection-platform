package com.frauddetect.fraud.engine;

import com.frauddetect.fraud.domain.FraudRuleEntity;
import com.frauddetect.fraud.domain.RuleType;
import com.frauddetect.fraud.feature.TransactionFeatures;

import java.util.Optional;

/**
 * Strategy that knows how to test one {@link RuleType} against a transaction's features. The engine
 * holds one evaluator per type and applies only the enabled, configured rules. Keeping evaluators
 * separate from the {@link FraudRuleEntity} configuration is what makes rules tunable as data
 * (Section 9): operators change weights/thresholds without touching this code.
 */
public interface RuleEvaluator {

    /** The rule type this evaluator handles. */
    RuleType type();

    /**
     * @return a {@link RuleHit} if the rule fires for the given features, otherwise empty.
     * @param rule           the configured rule (weight + thresholds)
     * @param features       the computed feature vector
     * @param modelRiskScore risk-scoring model sub-score (0-1) or {@code null} if unavailable
     */
    Optional<RuleHit> evaluate(FraudRuleEntity rule, TransactionFeatures features, Double modelRiskScore);
}
