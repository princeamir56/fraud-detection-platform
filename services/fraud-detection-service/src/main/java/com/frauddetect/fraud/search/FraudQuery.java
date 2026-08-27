package com.frauddetect.fraud.search;

import java.time.Instant;

/**
 * Search criteria shared by the transaction and fraud-event searches. All fields are optional; a
 * fully-empty query matches everything (newest first). {@code text} drives a full-text multi-match;
 * the remaining fields are exact-match term filters plus an {@code occurredAt} range.
 *
 * @param text       free-text query (merchant category, type, country, reason)
 * @param customerId exact customer filter
 * @param severity   exact severity filter (LOW|MEDIUM|HIGH|CRITICAL)
 * @param decision   exact decision filter (ALLOW|REVIEW|BLOCK)
 * @param from       lower bound (inclusive) on {@code occurredAt}
 * @param to         upper bound (inclusive) on {@code occurredAt}
 * @param page       zero-based page index
 * @param size       page size (bounded by the service)
 */
public record FraudQuery(
        String text,
        String customerId,
        String severity,
        String decision,
        Instant from,
        Instant to,
        int page,
        int size
) {
}
