package com.frauddetect.fraud.domain.cassandra;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite primary key for {@code transaction_velocity}.
 *
 * <p>Partition key is {@code (customer_id, day_bucket)} — bucketing by UTC day keeps a hot
 * customer's partition bounded (Cassandra anti-pattern avoided: no unbounded per-customer
 * partition). Clustering by {@code event_time DESC} then {@code transaction_id} makes "most recent
 * first" range scans (last hour / last 24h) cheap and naturally ordered.
 */
@PrimaryKeyClass
public class VelocityKey implements Serializable {

    @PrimaryKeyColumn(name = "customer_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    private String customerId;

    /** UTC day bucket, format {@code yyyy-MM-dd}, part of the partition key to bound partition size. */
    @PrimaryKeyColumn(name = "day_bucket", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
    private String dayBucket;

    @PrimaryKeyColumn(name = "event_time", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING)
    private Instant eventTime;

    @PrimaryKeyColumn(name = "transaction_id", type = PrimaryKeyType.CLUSTERED, ordinal = 3, ordering = Ordering.ASCENDING)
    private String transactionId;

    public VelocityKey() {
    }

    public VelocityKey(String customerId, String dayBucket, Instant eventTime, String transactionId) {
        this.customerId = customerId;
        this.dayBucket = dayBucket;
        this.eventTime = eventTime;
        this.transactionId = transactionId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getDayBucket() {
        return dayBucket;
    }

    public void setDayBucket(String dayBucket) {
        this.dayBucket = dayBucket;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VelocityKey that)) {
            return false;
        }
        return Objects.equals(customerId, that.customerId)
                && Objects.equals(dayBucket, that.dayBucket)
                && Objects.equals(eventTime, that.eventTime)
                && Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, dayBucket, eventTime, transactionId);
    }
}
