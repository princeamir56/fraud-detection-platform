package com.frauddetect.fraud.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for creating a fraud rule (Section 9: "rules must be configurable"). Bean Validation runs
 * before any business logic; {@code ruleType} is validated as a string and resolved to a
 * {@link com.frauddetect.fraud.domain.RuleType} in the service so an unknown type yields a clear 422
 * rather than a deserialization 500.
 *
 * <p>Threshold semantics depend on the rule type and are documented on each evaluator in
 * {@code RuleEvaluators}; {@code paramsJson} carries richer config (e.g. a high-risk country list).
 */
public record FraudRuleRequest(

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9_]{2,64}", message = "code must be UPPER_SNAKE_CASE (A-Z, 0-9, _)")
        String code,

        @NotBlank
        @Size(max = 160)
        String name,

        @NotBlank
        @Size(max = 512)
        String description,

        @NotBlank
        @Size(max = 48)
        String ruleType,

        @Min(value = 1, message = "weight must be at least 1")
        @Max(value = 100, message = "weight cannot exceed 100 (the max aggregate score)")
        int weight,

        boolean enabled,

        @DecimalMin(value = "0.0", message = "thresholdNumeric cannot be negative")
        @Digits(integer = 15, fraction = 4, message = "thresholdNumeric exceeds allowed precision")
        BigDecimal thresholdNumeric,

        @Min(value = 0, message = "thresholdInt cannot be negative")
        Integer thresholdInt,

        @Size(max = 4000, message = "paramsJson is too large")
        String paramsJson
) {
}
