package com.frauddetect.fraud.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for updating an existing fraud rule. The immutable business {@code code} is taken from the
 * path, so it is intentionally absent here; every other configurable attribute may be tuned. A
 * successful update triggers {@link com.frauddetect.fraud.engine.FraudRuleEngine#reload()} so the
 * change takes effect immediately, without a redeploy.
 */
public record FraudRuleUpdateRequest(

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
