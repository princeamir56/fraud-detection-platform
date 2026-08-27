package com.frauddetect.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted API idempotency record. A client that retries {@code POST /transactions} with the same
 * {@code Idempotency-Key} gets the original transaction back instead of creating a duplicate.
 * The key is the primary key, so a concurrent duplicate insert fails fast on the unique constraint.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 80)
    private String key;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public IdempotencyRecord(String key, String transactionId, Instant createdAt) {
        this.key = key;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
