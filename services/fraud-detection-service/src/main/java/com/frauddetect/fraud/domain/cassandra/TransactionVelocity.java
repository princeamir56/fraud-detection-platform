package com.frauddetect.fraud.domain.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * One row per transaction in the short-retention velocity store (Cassandra, Section 5).
 *
 * <p>Query pattern: {@code WHERE customer_id = ? AND day_bucket IN (?, ?) AND event_time >= ?} to
 * pull a customer's recent transactions (typically today + yesterday buckets) and derive velocity
 * features (count last 1h/24h, amount sum, distinct countries, failed count) in memory. Rows carry
 * a TTL (see schema) so the store self-prunes — it is a rolling window, not a system of record.
 */
@Table("transaction_velocity")
public class TransactionVelocity {

    @PrimaryKey
    private VelocityKey key;

    @Column("amount")
    private double amount;

    @Column("currency")
    private String currency;

    @Column("country_code")
    private String countryCode;

    @Column("merchant_id")
    private String merchantId;

    @Column("merchant_category")
    private String merchantCategory;

    @Column("device_id")
    private String deviceId;

    @Column("status")
    private String status;

    public TransactionVelocity() {
    }

    public VelocityKey getKey() {
        return key;
    }

    public void setKey(VelocityKey key) {
        this.key = key;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
