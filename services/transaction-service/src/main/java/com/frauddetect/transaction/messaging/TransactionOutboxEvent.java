package com.frauddetect.transaction.messaging;

import java.math.BigDecimal;

/**
 * Internal application events published by {@code TransactionService} and consumed by
 * {@link TransactionEventProducer} on {@code AFTER_COMMIT}. This decouples the DB write from the
 * Kafka publish so an event is emitted only if (and after) the transaction actually commits —
 * a lightweight stand-in for a full transactional outbox (see docs/kafka.md for the trade-off).
 */
public sealed interface TransactionOutboxEvent {

    String correlationId();

    /** A transaction was persisted in PENDING state → {@code transaction.created}. */
    record Created(
            String transactionId,
            String accountId,
            String customerId,
            BigDecimal amount,
            String currency,
            String type,
            String merchantId,
            String merchantCategory,
            String countryCode,
            String city,
            Double latitude,
            Double longitude,
            String deviceId,
            String ipAddress,
            String channel,
            String correlationId
    ) implements TransactionOutboxEvent {
    }

    /** A transaction cleared fraud analysis → {@code transaction.completed}. */
    record Completed(
            String transactionId,
            String accountId,
            String customerId,
            BigDecimal amount,
            String currency,
            int fraudScore,
            String severity,
            String correlationId
    ) implements TransactionOutboxEvent {
    }

    /** A transaction was blocked by a fraud verdict → {@code transaction.rejected}. */
    record Rejected(
            String transactionId,
            String accountId,
            String customerId,
            String reasonCode,
            String reason,
            Integer fraudScore,
            String severity,
            String correlationId
    ) implements TransactionOutboxEvent {
    }
}
