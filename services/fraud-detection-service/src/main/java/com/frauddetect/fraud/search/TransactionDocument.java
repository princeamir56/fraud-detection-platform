package com.frauddetect.fraud.search;

import java.time.Instant;

/**
 * Denormalised transaction record indexed into the {@code transactions} index (Section 6) for
 * analyst search: full-text over merchant/type/country plus term filters (customer, severity,
 * decision) and {@code occurredAt} time-range. Enriched with the fraud verdict so a single query
 * answers "show me this customer's HIGH-severity purchases last week".
 */
public record TransactionDocument(
        String transactionId,
        String accountId,
        String customerId,
        double amount,
        String currency,
        String type,
        String countryCode,
        String merchantId,
        String merchantCategory,
        String deviceId,
        String ipAddress,
        Double latitude,
        Double longitude,
        int score,
        String severity,
        String decision,
        Double modelRiskScore,
        Instant occurredAt,
        String correlationId
) {
}
