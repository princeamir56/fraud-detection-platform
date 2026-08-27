package com.frauddetect.alert.search;

import java.time.Instant;

/**
 * Denormalised, analyst-facing projection of an alert stored in the {@code alerts} Elasticsearch
 * index for full-text triage search and Kibana dashboards. The document id is the {@code alertId},
 * so re-indexing an updated alert (e.g. after resolution) overwrites in place rather than duplicating.
 */
public record AlertDocument(
        String alertId,
        String transactionId,
        String customerId,
        String accountId,
        String severity,
        Integer score,
        String status,
        String title,
        String primaryReason,
        String resolution,
        String resolvedBy,
        Instant createdAt,
        Instant resolvedAt,
        String correlationId) {
}
