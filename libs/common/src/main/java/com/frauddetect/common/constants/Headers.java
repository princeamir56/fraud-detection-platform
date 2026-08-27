package com.frauddetect.common.constants;

/** HTTP / Kafka header names used for request correlation and idempotency. */
public final class Headers {

    private Headers() {
    }

    /** Correlation id propagated end-to-end across REST, Kafka and gRPC hops. */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** Per-hop request id (regenerated at each service boundary). */
    public static final String REQUEST_ID = "X-Request-Id";

    /** Client-supplied idempotency key for safe transaction retries. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Authenticated subject propagated downstream by the gateway. */
    public static final String USER_ID = "X-User-Id";

    /** Comma-separated roles propagated downstream by the gateway. */
    public static final String USER_ROLES = "X-User-Roles";
}
