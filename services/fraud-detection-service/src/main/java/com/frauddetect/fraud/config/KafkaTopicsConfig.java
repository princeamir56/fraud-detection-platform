package com.frauddetect.fraud.config;

import com.frauddetect.common.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service owns — the fraud analysis outputs {@code fraud.score.calculated}
 * (emitted for every transaction) and {@code fraud.detected} (emitted only for HIGH/CRITICAL) — plus
 * the dead-letter topic for the {@code transaction.created} stream it consumes. KafkaAdmin creates
 * them on startup so a fresh cluster is usable without manual provisioning. Replication factor is 1
 * for the single-broker dev cluster; override for production (see docs/kafka.md).
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    NewTopic fraudScoreCalculatedTopic() {
        return TopicBuilder.name(KafkaTopics.FRAUD_SCORE_CALCULATED).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    NewTopic fraudDetectedTopic() {
        return TopicBuilder.name(KafkaTopics.FRAUD_DETECTED).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    /** DLT for the inbound stream, so poison {@code transaction.created} records are quarantined. */
    @Bean
    NewTopic transactionCreatedDlt() {
        return TopicBuilder.name(KafkaTopics.dlt(KafkaTopics.TRANSACTION_CREATED))
                .partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
