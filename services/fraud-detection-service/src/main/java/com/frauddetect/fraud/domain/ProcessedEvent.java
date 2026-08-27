package com.frauddetect.fraud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Consumer-side dedupe marker. The {@code transaction.created} listener records the inbound Avro
 * {@code eventId} here after a transaction is fully analysed, so at-least-once redelivery is detected
 * and skipped. Cassandra/Elasticsearch writes are idempotent by key, but event emission is not — this
 * marker (plus deterministic outbound event ids) keeps the pipeline effectively-once.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 80)
    private String eventId;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA
    }

    public ProcessedEvent(String eventId, String transactionId, Instant processedAt) {
        this.eventId = eventId;
        this.transactionId = transactionId;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
