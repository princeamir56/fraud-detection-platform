package com.frauddetect.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Consumer-side dedupe marker. The {@code fraud.score.calculated} listener records the Avro
 * {@code eventId} here inside the same transaction that applies the verdict, so redelivery
 * (at-least-once Kafka) can be detected and skipped — the status update and the follow-on event
 * emission are not naturally idempotent.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 80)
    private String eventId;

    @Column(name = "consumer", nullable = false, length = 64)
    private String consumer;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA
    }

    public ProcessedEvent(String eventId, String consumer, Instant processedAt) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getConsumer() {
        return consumer;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
