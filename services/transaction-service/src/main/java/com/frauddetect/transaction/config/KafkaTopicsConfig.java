package com.frauddetect.transaction.config;

import com.frauddetect.common.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service owns (the {@code transaction.*} lifecycle) plus the dead-letter
 * topic for the event it consumes. KafkaAdmin creates them on startup so a fresh cluster is usable
 * without manual topic provisioning. Replication factor is 1 for the single-broker dev cluster;
 * override for production (see docs/kafka.md).
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    NewTopic transactionCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.TRANSACTION_CREATED).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    NewTopic transactionCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.TRANSACTION_COMPLETED).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    NewTopic transactionRejectedTopic() {
        return TopicBuilder.name(KafkaTopics.TRANSACTION_REJECTED).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    NewTopic fraudScoreCalculatedDlt() {
        return TopicBuilder.name(KafkaTopics.dlt(KafkaTopics.FRAUD_SCORE_CALCULATED))
                .partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
