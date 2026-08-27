package com.frauddetect.common.constants;

/**
 * Canonical Kafka topic names shared by producers and consumers so a typo in one
 * service cannot silently create a divergent topic. One domain event == one topic.
 * Dead-letter topics use the {@code .DLT} suffix consumed by the error handler.
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    // ---- Transaction lifecycle (produced by transaction-service) ----
    public static final String TRANSACTION_CREATED = "transaction.created";
    public static final String TRANSACTION_COMPLETED = "transaction.completed";
    public static final String TRANSACTION_REJECTED = "transaction.rejected";

    // ---- Fraud analysis (fraud-detection-service) ----
    public static final String FRAUD_CHECK_REQUESTED = "fraud.check.requested";
    public static final String FRAUD_SCORE_CALCULATED = "fraud.score.calculated";
    public static final String FRAUD_DETECTED = "fraud.detected";

    // ---- Alerting (alert-service) ----
    public static final String ALERT_CREATED = "alert.created";
    public static final String ALERT_RESOLVED = "alert.resolved";

    // ---- Dead-letter suffix appended by the common DLT recoverer ----
    public static final String DLT_SUFFIX = ".DLT";

    public static String dlt(String topic) {
        return topic + DLT_SUFFIX;
    }
}
