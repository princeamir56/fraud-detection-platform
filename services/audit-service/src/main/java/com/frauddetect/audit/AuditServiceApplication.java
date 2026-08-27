package com.frauddetect.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Audit service (Section 3 + Section 11 audit logging). Subscribes to every domain topic on the bus
 * and writes an append-only projection into the Elasticsearch {@code audit-events} index, keyed by
 * the Avro {@code eventId} so redeliveries are naturally idempotent. Exposes a compliance search API.
 *
 * <p>No relational store: the Kafka log is the durable source of truth and Elasticsearch is the
 * queryable projection. Indexing failures are NOT swallowed here (unlike the fraud hot path) — they
 * propagate so the container retries and, if still failing, routes to the DLT rather than dropping an
 * audit record.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
