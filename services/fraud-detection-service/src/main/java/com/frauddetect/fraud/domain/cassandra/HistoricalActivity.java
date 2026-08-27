package com.frauddetect.fraud.domain.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * Long-retention per-customer activity log (Cassandra, Section 5).
 *
 * <p>Distinct from {@link TransactionVelocity} (short rolling window for hot velocity math): this is
 * the durable behavioural history an investigator scans when working a case. Same key shape
 * ({@code (customer_id, day_bucket)} partition, {@code event_time DESC} clustering) but a longer TTL.
 */
@Table("historical_activity")
public class HistoricalActivity {

    @PrimaryKey
    private VelocityKey key;

    @Column("account_id")
    private String accountId;

    @Column("amount")
    private double amount;

    @Column("currency")
    private String currency;

    @Column("transaction_type")
    private String transactionType;

    @Column("country_code")
    private String countryCode;

    @Column("merchant_id")
    private String merchantId;

    @Column("merchant_category")
    private String merchantCategory;

    @Column("score")
    private int score;

    @Column("severity")
    private String severity;

    @Column("decision")
    private String decision;

    public HistoricalActivity() {
    }

    public VelocityKey getKey() {
        return key;
    }

    public void setKey(VelocityKey key) {
        this.key = key;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
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

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}
