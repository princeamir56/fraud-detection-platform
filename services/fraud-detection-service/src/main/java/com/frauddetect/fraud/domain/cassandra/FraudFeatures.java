package com.frauddetect.fraud.domain.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * Persisted feature vector + engine outcome per transaction (Cassandra, Section 5).
 *
 * <p>This is the durable record of exactly what the model/engine saw and decided — invaluable for
 * offline model training, back-testing rule changes, and explaining a decision after the fact.
 * Reuses {@link VelocityKey} (partition {@code (customer_id, day_bucket)}, clustered by
 * {@code event_time DESC}) so an analyst can page a customer's most recent scored features. Longer
 * TTL than the velocity window (retention documented in schema.cql / docs/database.md).
 */
@Table("fraud_features")
public class FraudFeatures {

    @PrimaryKey
    private VelocityKey key;

    @Column("amount")
    private double amount;

    @Column("avg_amount_30d")
    private double avgAmount30d;

    @Column("tx_count_last_hour")
    private long txCountLastHour;

    @Column("tx_count_last_24h")
    private long txCountLast24h;

    @Column("amount_sum_last_24h")
    private double amountSumLast24h;

    @Column("distinct_countries_last_24h")
    private long distinctCountriesLast24h;

    @Column("new_device")
    private boolean newDevice;

    @Column("new_merchant")
    private boolean newMerchant;

    @Column("failed_tx_last_hour")
    private long failedTxLastHour;

    @Column("km_from_last_tx")
    private double kmFromLastTx;

    @Column("seconds_since_last_tx")
    private long secondsSinceLastTx;

    @Column("model_risk_score")
    private Double modelRiskScore;

    @Column("score")
    private int score;

    @Column("severity")
    private String severity;

    @Column("decision")
    private String decision;

    public FraudFeatures() {
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

    public double getAvgAmount30d() {
        return avgAmount30d;
    }

    public void setAvgAmount30d(double avgAmount30d) {
        this.avgAmount30d = avgAmount30d;
    }

    public long getTxCountLastHour() {
        return txCountLastHour;
    }

    public void setTxCountLastHour(long txCountLastHour) {
        this.txCountLastHour = txCountLastHour;
    }

    public long getTxCountLast24h() {
        return txCountLast24h;
    }

    public void setTxCountLast24h(long txCountLast24h) {
        this.txCountLast24h = txCountLast24h;
    }

    public double getAmountSumLast24h() {
        return amountSumLast24h;
    }

    public void setAmountSumLast24h(double amountSumLast24h) {
        this.amountSumLast24h = amountSumLast24h;
    }

    public long getDistinctCountriesLast24h() {
        return distinctCountriesLast24h;
    }

    public void setDistinctCountriesLast24h(long distinctCountriesLast24h) {
        this.distinctCountriesLast24h = distinctCountriesLast24h;
    }

    public boolean isNewDevice() {
        return newDevice;
    }

    public void setNewDevice(boolean newDevice) {
        this.newDevice = newDevice;
    }

    public boolean isNewMerchant() {
        return newMerchant;
    }

    public void setNewMerchant(boolean newMerchant) {
        this.newMerchant = newMerchant;
    }

    public long getFailedTxLastHour() {
        return failedTxLastHour;
    }

    public void setFailedTxLastHour(long failedTxLastHour) {
        this.failedTxLastHour = failedTxLastHour;
    }

    public double getKmFromLastTx() {
        return kmFromLastTx;
    }

    public void setKmFromLastTx(double kmFromLastTx) {
        this.kmFromLastTx = kmFromLastTx;
    }

    public long getSecondsSinceLastTx() {
        return secondsSinceLastTx;
    }

    public void setSecondsSinceLastTx(long secondsSinceLastTx) {
        this.secondsSinceLastTx = secondsSinceLastTx;
    }

    public Double getModelRiskScore() {
        return modelRiskScore;
    }

    public void setModelRiskScore(Double modelRiskScore) {
        this.modelRiskScore = modelRiskScore;
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
