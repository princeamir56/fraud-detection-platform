package com.frauddetect.fraud.dto;

import com.frauddetect.fraud.domain.FraudRuleEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model for a fraud rule. Exposes the audit and optimistic-lock metadata ({@code version}) so
 * clients can perform safe read-modify-write cycles against the rule catalogue.
 */
public record FraudRuleResponse(
        String code,
        String name,
        String description,
        String ruleType,
        int weight,
        boolean enabled,
        BigDecimal thresholdNumeric,
        Integer thresholdInt,
        String paramsJson,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static FraudRuleResponse from(FraudRuleEntity e) {
        return new FraudRuleResponse(
                e.getCode(),
                e.getName(),
                e.getDescription(),
                e.getRuleType() == null ? null : e.getRuleType().name(),
                e.getWeight(),
                e.isEnabled(),
                e.getThresholdNumeric(),
                e.getThresholdInt(),
                e.getParamsJson(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }
}
