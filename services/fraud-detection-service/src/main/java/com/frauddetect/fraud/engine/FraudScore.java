package com.frauddetect.fraud.engine;

import com.frauddetect.common.domain.FraudSeverity;

import java.util.Comparator;
import java.util.List;

/**
 * Immutable outcome of the fraud engine for one transaction.
 *
 * @param score          aggregate 0-100 fraud score
 * @param severity       band derived from {@code score} (LOW/MEDIUM/HIGH/CRITICAL)
 * @param decision       action derived from severity (ALLOW/REVIEW/BLOCK)
 * @param hits           the rules that fired, highest points first
 * @param modelRiskScore risk sub-score (0-1) from risk-scoring-service; {@code null} on degradation
 */
public record FraudScore(
        int score,
        FraudSeverity severity,
        Decision decision,
        List<RuleHit> hits,
        Double modelRiskScore
) {
    /** True when severity is HIGH or CRITICAL — the band that drives {@code fraud.detected}/alerts. */
    public boolean isFraudulent() {
        return severity.requiresBlocking();
    }

    /** Highest-weighted triggered rule description, or a default when nothing fired. */
    public String primaryReason() {
        return hits.stream()
                .max(Comparator.comparingInt(RuleHit::points))
                .map(RuleHit::description)
                .orElse("No rule triggered");
    }

    public List<String> triggeredRuleCodes() {
        return hits.stream().map(RuleHit::ruleCode).toList();
    }
}
