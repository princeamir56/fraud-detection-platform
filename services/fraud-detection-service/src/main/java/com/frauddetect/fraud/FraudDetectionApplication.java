package com.frauddetect.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Fraud-detection-service — the analytical core ("brain") of the platform.
 *
 * <p>On each {@code transaction.created} event it: updates per-customer velocity/behaviour in
 * Cassandra, builds a feature vector, obtains a model risk sub-score from risk-scoring-service over
 * gRPC (bounded by a deadline + circuit breaker, degrading gracefully when unavailable), runs the
 * configurable MySQL-backed rule engine to produce a 0-100 score / severity / decision, persists
 * features + historical activity to Cassandra, indexes the transaction and fraud event into
 * Elasticsearch, and publishes {@code fraud.score.calculated} (always) plus {@code fraud.detected}
 * (HIGH/CRITICAL only).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionApplication.class, args);
    }
}
