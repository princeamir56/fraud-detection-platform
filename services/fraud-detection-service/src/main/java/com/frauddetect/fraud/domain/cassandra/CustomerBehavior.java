package com.frauddetect.fraud.domain.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.Set;

/**
 * Slowly-evolving per-customer behavioural profile (Cassandra, Section 5).
 *
 * <p>Query pattern: point read by {@code customer_id} on the fraud hot path to learn what is
 * "normal" for this customer (baseline amount, previously-seen countries/devices/merchant
 * categories, last known location). Written as an idempotent upsert after every transaction.
 * Partition key = {@code customer_id}; exactly one row per customer, so partitions stay tiny.
 */
@Table("customer_behavior")
public class CustomerBehavior {

    @PrimaryKey("customer_id")
    private String customerId;

    @Column("tx_count")
    private long txCount;

    @Column("amount_sum_30d")
    private double amountSum30d;

    @Column("avg_amount_30d")
    private double avgAmount30d;

    @Column("known_countries")
    private Set<String> knownCountries;

    @Column("known_devices")
    private Set<String> knownDevices;

    @Column("known_merchant_categories")
    private Set<String> knownMerchantCategories;

    @Column("last_country")
    private String lastCountry;

    @Column("last_latitude")
    private Double lastLatitude;

    @Column("last_longitude")
    private Double lastLongitude;

    @Column("last_event_time")
    private Instant lastEventTime;

    @Column("updated_at")
    private Instant updatedAt;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public long getTxCount() {
        return txCount;
    }

    public void setTxCount(long txCount) {
        this.txCount = txCount;
    }

    public double getAmountSum30d() {
        return amountSum30d;
    }

    public void setAmountSum30d(double amountSum30d) {
        this.amountSum30d = amountSum30d;
    }

    public double getAvgAmount30d() {
        return avgAmount30d;
    }

    public void setAvgAmount30d(double avgAmount30d) {
        this.avgAmount30d = avgAmount30d;
    }

    public Set<String> getKnownCountries() {
        return knownCountries;
    }

    public void setKnownCountries(Set<String> knownCountries) {
        this.knownCountries = knownCountries;
    }

    public Set<String> getKnownDevices() {
        return knownDevices;
    }

    public void setKnownDevices(Set<String> knownDevices) {
        this.knownDevices = knownDevices;
    }

    public Set<String> getKnownMerchantCategories() {
        return knownMerchantCategories;
    }

    public void setKnownMerchantCategories(Set<String> knownMerchantCategories) {
        this.knownMerchantCategories = knownMerchantCategories;
    }

    public String getLastCountry() {
        return lastCountry;
    }

    public void setLastCountry(String lastCountry) {
        this.lastCountry = lastCountry;
    }

    public Double getLastLatitude() {
        return lastLatitude;
    }

    public void setLastLatitude(Double lastLatitude) {
        this.lastLatitude = lastLatitude;
    }

    public Double getLastLongitude() {
        return lastLongitude;
    }

    public void setLastLongitude(Double lastLongitude) {
        this.lastLongitude = lastLongitude;
    }

    public Instant getLastEventTime() {
        return lastEventTime;
    }

    public void setLastEventTime(Instant lastEventTime) {
        this.lastEventTime = lastEventTime;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
