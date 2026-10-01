package com.frauddetect.fraud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A configurable fraud rule (Section 9). Rules are data, not code: analysts tune weights and
 * thresholds through the REST API and changes take effect without a redeploy. Each row binds a
 * {@link RuleType} (which selects the evaluator implementation) to a weight and optional thresholds.
 *
 * <p>Stored in MySQL because rule configuration is low-volume, strongly relational, and edited
 * transactionally with optimistic locking ({@link Version}).
 */
@Entity
@Table(name = "fraud_rules", indexes = {
        @Index(name = "idx_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_rule_type", columnList = "rule_type")
})
@EntityListeners(AuditingEntityListener.class)
public class FraudRuleEntity {

    /** Stable business code, e.g. {@code LARGE_AMOUNT}. Unique; referenced in triggered-rule output. */
    @Id
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 160, nullable = false)
    private String name;

    @Column(name = "description", length = 512, nullable = false)
    private String description;

    /** Selects the evaluator strategy that knows how to test this rule. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", length = 48, nullable = false)
    private RuleType ruleType;

    /** Maximum points this rule can contribute to the 0-100 aggregate score. */
    @Column(name = "weight", nullable = false)
    private int weight;

    /** Whether the engine evaluates this rule at all. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Primary numeric threshold (semantics depend on {@link RuleType}; nullable). */
    @Column(name = "threshold_numeric", precision = 19, scale = 4)
    private BigDecimal thresholdNumeric;

    /** Secondary integer threshold (e.g. count/seconds window; nullable). */
    @Column(name = "threshold_int")
    private Integer thresholdInt;

    /** Free-form JSON for rules needing richer parameters (e.g. country lists). */
    // LONGVARCHAR matches the LONGTEXT column; @Lob maps to CLOB, which fails schema validation on MySQL.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "params_json")
    private String paramsJson;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public FraudRuleEntity() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getThresholdNumeric() {
        return thresholdNumeric;
    }

    public void setThresholdNumeric(BigDecimal thresholdNumeric) {
        this.thresholdNumeric = thresholdNumeric;
    }

    public Integer getThresholdInt() {
        return thresholdInt;
    }

    public void setThresholdInt(Integer thresholdInt) {
        this.thresholdInt = thresholdInt;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
