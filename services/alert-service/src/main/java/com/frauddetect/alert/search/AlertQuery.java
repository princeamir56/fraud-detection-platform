package com.frauddetect.alert.search;

import java.time.Instant;

/**
 * Filter criteria for the alert search API. All fields are optional; {@code text} runs a full-text
 * {@code multi_match} over title/reason, while the remaining fields are exact-term filters. {@code from}/{@code to}
 * bound {@code createdAt}. {@code page}/{@code size} drive pagination.
 */
public record AlertQuery(
        String text,
        String severity,
        String status,
        String customerId,
        String correlationId,
        Instant from,
        Instant to,
        int page,
        int size) {
}
