package com.frauddetect.fraud.service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Avro-free snapshot of the transaction under analysis. Built from the {@code TransactionCreated}
 * event at the edge (the Kafka listener) so the detection core — behaviour service, rule engine,
 * gRPC client — never depends on generated Avro types.
 */
public record TransactionContext(
        String transactionId,
        String accountId,
        String customerId,
        BigDecimal amount,
        String currency,
        String type,
        String countryCode,
        String merchantId,
        String merchantCategory,
        String deviceId,
        String ipAddress,
        Double latitude,
        Double longitude,
        Instant occurredAt,
        String correlationId
) {
    public String merchantCategoryOrEmpty() {
        return merchantCategory == null ? "" : merchantCategory;
    }
}
