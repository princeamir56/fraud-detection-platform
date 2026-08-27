package com.frauddetect.common.error;

import java.time.Instant;
import java.util.List;

/**
 * RFC-7807-inspired error body returned by every REST service. Immutable record so it
 * serialises deterministically and is safe to share.
 *
 * @param timestamp     when the error was produced (UTC)
 * @param status        HTTP status code
 * @param error         HTTP reason phrase
 * @param message       human-readable, non-sensitive message
 * @param path          request path
 * @param correlationId end-to-end correlation id for log/trace lookup
 * @param violations    field-level validation failures (may be empty)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, List.of());
    }
}
