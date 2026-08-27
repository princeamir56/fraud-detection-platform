package com.frauddetect.fraud.search;

import java.time.Instant;
import java.util.List;

/**
 * Fraud verdict indexed into the {@code fraud-events} index (Section 6). One document per scored
 * transaction, carrying the triggered rules and the primary reason so dashboards can chart fraud
 * rate, severity mix and top firing rules, and analysts can full-text search the reason.
 */
public record FraudEventDocument(
        String eventId,
        String transactionId,
        String customerId,
        String accountId,
        int score,
        String severity,
        String decision,
        String primaryReason,
        List<String> triggeredRuleCodes,
        double amount,
        String currency,
        String countryCode,
        Double modelRiskScore,
        Instant occurredAt,
        String correlationId
) {
}
