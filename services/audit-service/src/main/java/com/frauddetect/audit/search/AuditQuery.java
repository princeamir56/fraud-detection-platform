package com.frauddetect.audit.search;

import java.time.Instant;

/**
 * Search criteria for the audit trail. All fields are optional; a fully-empty query matches every
 * event (newest first). {@code text} drives a full-text multi-match over the summary and descriptive
 * fields; the remaining fields are exact-match term filters plus an {@code occurredAt} range.
 *
 * @param text          free-text query (summary, event type, reason)
 * @param eventType     exact event-type filter (e.g. {@code FraudDetected})
 * @param customerId    exact customer filter
 * @param correlationId exact correlation-id filter (trace a single request across services)
 * @param transactionId exact transaction filter
 * @param from          lower bound (inclusive) on {@code occurredAt}
 * @param to            upper bound (inclusive) on {@code occurredAt}
 * @param page          zero-based page index
 * @param size          page size (bounded by the service)
 */
public record AuditQuery(
        String text,
        String eventType,
        String customerId,
        String correlationId,
        String transactionId,
        Instant from,
        Instant to,
        int page,
        int size
) {
}
