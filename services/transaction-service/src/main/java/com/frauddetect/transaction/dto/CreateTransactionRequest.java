package com.frauddetect.transaction.dto;

import com.frauddetect.common.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request to create a transaction. Validated with Jakarta Bean Validation before any business logic
 * runs (Section 11: input validation). Optional enrichment fields (merchant, geo, device) feed the
 * downstream fraud features.
 */
public record CreateTransactionRequest(

        @NotBlank
        @Size(max = 64)
        String accountId,

        @NotBlank
        @Size(max = 64)
        String customerId,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be positive")
        @Digits(integer = 15, fraction = 4, message = "amount exceeds allowed precision")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO-4217 alpha-3 code")
        String currency,

        @NotNull
        TransactionType type,

        @Size(max = 64)
        String merchantId,

        @Size(max = 64)
        String merchantCategory,

        @NotBlank
        @Pattern(regexp = "[A-Z]{2}", message = "countryCode must be an ISO-3166 alpha-2 code")
        String countryCode,

        @Size(max = 128)
        String city,

        Double latitude,

        Double longitude,

        @Size(max = 128)
        String deviceId,

        @Size(max = 45)
        String ipAddress,

        @Pattern(regexp = "WEB|MOBILE|ATM|POS|API", message = "channel must be WEB|MOBILE|ATM|POS|API")
        String channel
) {
    /** Channel defaults to WEB when the client omits it. */
    public String channelOrDefault() {
        return channel == null || channel.isBlank() ? "WEB" : channel;
    }
}
