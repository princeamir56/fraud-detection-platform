package com.frauddetect.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for a balance movement (credit or debit). The amount is strictly positive; the direction is
 * determined by the endpoint. An optional reason is recorded in logs/traces for auditability.
 *
 * @param amount strictly-positive movement amount
 * @param reason optional human-readable reason (e.g. "settlement tx-123")
 */
public record BalanceChangeRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be strictly positive")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Size(max = 255)
        String reason
) {
}
