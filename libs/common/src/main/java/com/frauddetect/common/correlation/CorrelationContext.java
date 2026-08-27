package com.frauddetect.common.correlation;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Thread-scoped access to the current correlation id, backed by SLF4J {@link MDC} so it is
 * automatically included in structured log lines ({@code %X{correlationId}}).
 * <p>
 * For work handed to other threads (async, Kafka listener pools) the id must be copied
 * explicitly via {@link #getCorrelationId()} / {@link #setCorrelationId(String)}.
 */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";

    private CorrelationContext() {
    }

    public static String getCorrelationId() {
        return MDC.get(MDC_KEY);
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, correlationId);
        }
    }

    /** Returns the current id or generates a fresh one if none is set. */
    public static String getOrCreate() {
        String existing = getCorrelationId();
        if (existing == null || existing.isBlank()) {
            existing = newId();
            setCorrelationId(existing);
        }
        return existing;
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
