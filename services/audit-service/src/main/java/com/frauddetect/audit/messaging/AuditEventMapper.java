package com.frauddetect.audit.messaging;

import com.frauddetect.audit.search.AuditEventDocument;
import com.frauddetect.avro.events.AlertCreated;
import com.frauddetect.avro.events.AlertResolved;
import com.frauddetect.avro.events.FraudCheckRequested;
import com.frauddetect.avro.events.FraudDetected;
import com.frauddetect.avro.events.FraudScoreCalculated;
import com.frauddetect.avro.events.TransactionCompleted;
import com.frauddetect.avro.events.TransactionCreated;
import com.frauddetect.avro.events.TransactionRejected;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pure, side-effect-free translation of any of the eight domain events into the flat
 * {@link AuditEventDocument} projection. Kept free of Spring, Elasticsearch and Kafka so it is trivially
 * unit-testable (the bulk of audit-service's logic lives here).
 *
 * <p>Each case fills only the fields its event carries; everything else stays {@code null}. The
 * exhaustive {@code switch} means adding a ninth event type without extending this mapper is a compile
 * error, not a silently-dropped audit row.
 */
public final class AuditEventMapper {

    private AuditEventMapper() {
    }

    public static AuditEventDocument toDocument(Object event, Instant indexedAt) {
        return switch (event) {
            case TransactionCreated e -> AuditEventDocument
                    .builder("TransactionCreated", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .accountId(e.getAccountId())
                    .customerId(e.getCustomerId())
                    .amount(toDouble(e.getAmount()))
                    .currency(e.getCurrency())
                    .summary("Transaction created: " + e.getType() + " " + e.getAmount() + " "
                            + e.getCurrency() + " in " + e.getCountryCode())
                    .build();

            case FraudCheckRequested e -> AuditEventDocument
                    .builder("FraudCheckRequested", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .accountId(e.getAccountId())
                    .customerId(e.getCustomerId())
                    .amount(toDouble(e.getAmount()))
                    .currency(e.getCurrency())
                    .summary("Fraud check requested for " + e.getType() + " " + e.getAmount() + " "
                            + e.getCurrency())
                    .build();

            case FraudScoreCalculated e -> AuditEventDocument
                    .builder("FraudScoreCalculated", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .accountId(e.getAccountId())
                    .customerId(e.getCustomerId())
                    .score(e.getScore())
                    .severity(e.getSeverity())
                    .decision(e.getDecision())
                    .summary("Fraud score " + e.getScore() + " (" + e.getSeverity() + ") -> " + e.getDecision())
                    .build();

            case FraudDetected e -> AuditEventDocument
                    .builder("FraudDetected", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .accountId(e.getAccountId())
                    .customerId(e.getCustomerId())
                    .amount(toDouble(e.getAmount()))
                    .currency(e.getCurrency())
                    .score(e.getScore())
                    .severity(e.getSeverity())
                    .decision(e.getDecision())
                    .reasonCode(e.getPrimaryReason())
                    .summary("Fraud detected (" + e.getSeverity() + ", score " + e.getScore() + "): "
                            + e.getPrimaryReason())
                    .build();

            case TransactionCompleted e -> AuditEventDocument
                    .builder("TransactionCompleted", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .accountId(e.getAccountId())
                    .customerId(e.getCustomerId())
                    .amount(toDouble(e.getAmount()))
                    .currency(e.getCurrency())
                    .score(e.getFraudScore())
                    .severity(e.getSeverity())
                    .summary("Transaction completed: " + e.getAmount() + " " + e.getCurrency()
                            + " (score " + e.getFraudScore() + ")")
                    .build();

            case TransactionRejected e -> AuditEventDocument
                    .builder("TransactionRejected", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .accountId(e.getAccountId())
                    .customerId(e.getCustomerId())
                    .score(e.getFraudScore())
                    .severity(e.getSeverity())
                    .reasonCode(e.getReasonCode())
                    .summary("Transaction rejected [" + e.getReasonCode() + "]: " + e.getReason())
                    .build();

            case AlertCreated e -> AuditEventDocument
                    .builder("AlertCreated", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .accountId(e.getAccountId())
                    .customerId(e.getCustomerId())
                    .alertId(e.getAlertId())
                    .score(e.getScore())
                    .severity(e.getSeverity())
                    .summary("Alert created (" + e.getSeverity() + "): " + e.getTitle())
                    .build();

            case AlertResolved e -> AuditEventDocument
                    .builder("AlertResolved", e.getEventId(), e.getCorrelationId(), e.getOccurredAt())
                    .indexedAt(indexedAt)
                    .transactionId(e.getTransactionId())
                    .customerId(e.getCustomerId())
                    .alertId(e.getAlertId())
                    .decision(e.getResolution())
                    .reasonCode(e.getResolution())
                    .summary("Alert resolved [" + e.getResolution() + "] by " + e.getResolvedBy())
                    .build();

            case null -> throw new IllegalArgumentException("Cannot audit a null event");
            default -> throw new IllegalArgumentException(
                    "Unsupported event type for audit: " + event.getClass().getName());
        };
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
