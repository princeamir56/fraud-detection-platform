package com.frauddetect.transaction.dto;

import com.frauddetect.common.domain.TransactionStatus;
import com.frauddetect.common.domain.TransactionType;
import com.frauddetect.transaction.domain.TransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API view of a transaction. Exposes the fraud verdict fields once populated so a client can poll
 * {@code GET /transactions/{id}} and watch PENDING → COMPLETED|FLAGGED|REJECTED.
 */
public record TransactionResponse(
        String id,
        String accountId,
        String customerId,
        BigDecimal amount,
        String currency,
        TransactionType type,
        TransactionStatus status,
        String merchantId,
        String merchantCategory,
        String countryCode,
        String city,
        String channel,
        Integer fraudScore,
        String severity,
        String decision,
        String reasonCode,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionResponse from(TransactionEntity e) {
        return new TransactionResponse(
                e.getId(), e.getAccountId(), e.getCustomerId(), e.getAmount(), e.getCurrency(),
                e.getType(), e.getStatus(), e.getMerchantId(), e.getMerchantCategory(),
                e.getCountryCode(), e.getCity(), e.getChannel(), e.getFraudScore(), e.getSeverity(),
                e.getDecision(), e.getReasonCode(), e.getReason(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
