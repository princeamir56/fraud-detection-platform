package com.frauddetect.fraud.domain;

/**
 * Discriminator that binds a configured {@link FraudRuleEntity} to the evaluator strategy able to
 * test it (Section 9 detection catalogue). Adding a new detection means adding a value here plus a
 * matching {@code RuleEvaluator} bean — configuration for it then lives as data in {@code fraud_rules}.
 */
public enum RuleType {

    /** Amount exceeds an absolute threshold (thresholdNumeric) or a multiple of the 30d average. */
    LARGE_AMOUNT,

    /** Too many transactions in a short rolling window (thresholdInt count in last hour). */
    RAPID_VELOCITY,

    /** Geographic movement faster than physically possible (km/h implied by kmFromLastTx / dwell). */
    IMPOSSIBLE_TRAVEL,

    /** Transaction country is on a configured high-risk list, or a sudden country change. */
    SUSPICIOUS_COUNTRY,

    /** Daily transaction frequency well above the customer's own baseline (24h vs history). */
    UNUSUAL_FREQUENCY,

    /** Repeated failed/declined attempts preceding this one (thresholdInt failures in last hour). */
    REPEATED_FAILURES,

    /** Merchant category the customer never uses, or a burst against one merchant. */
    ABNORMAL_MERCHANT,

    /** First time this device is seen for the customer. */
    SUSPICIOUS_DEVICE,

    /** Incorporates the risk-scoring-service model sub-score (0-1) as weighted points. */
    MODEL_RISK
}
