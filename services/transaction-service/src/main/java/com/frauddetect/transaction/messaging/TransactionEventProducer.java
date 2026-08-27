package com.frauddetect.transaction.messaging;

import com.frauddetect.avro.events.TransactionCompleted;
import com.frauddetect.avro.events.TransactionCreated;
import com.frauddetect.avro.events.TransactionRejected;
import com.frauddetect.common.constants.Headers;
import com.frauddetect.common.constants.KafkaTopics;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@code transaction.*} Avro events to Kafka, keyed by {@code customerId} so a customer's
 * events land on one partition (ordered per-customer processing downstream). Listeners fire on
 * {@code AFTER_COMMIT}, guaranteeing an event is only emitted for a transaction that truly persisted.
 */
@Component
public class TransactionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransactionEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(TransactionOutboxEvent.Created e) {
        TransactionCreated avro = TransactionCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCorrelationId(e.correlationId())
                .setOccurredAt(Instant.now())
                .setTransactionId(e.transactionId())
                .setAccountId(e.accountId())
                .setCustomerId(e.customerId())
                .setAmount(e.amount())
                .setCurrency(e.currency())
                .setType(e.type())
                .setMerchantId(e.merchantId())
                .setMerchantCategory(e.merchantCategory())
                .setCountryCode(e.countryCode())
                .setCity(e.city())
                .setLatitude(e.latitude())
                .setLongitude(e.longitude())
                .setDeviceId(e.deviceId())
                .setIpAddress(e.ipAddress())
                .setChannel(e.channel())
                .build();
        send(KafkaTopics.TRANSACTION_CREATED, e.customerId(), avro, e.correlationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(TransactionOutboxEvent.Completed e) {
        TransactionCompleted avro = TransactionCompleted.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCorrelationId(e.correlationId())
                .setOccurredAt(Instant.now())
                .setTransactionId(e.transactionId())
                .setAccountId(e.accountId())
                .setCustomerId(e.customerId())
                .setAmount(e.amount())
                .setCurrency(e.currency())
                .setFraudScore(e.fraudScore())
                .setSeverity(e.severity())
                .build();
        send(KafkaTopics.TRANSACTION_COMPLETED, e.customerId(), avro, e.correlationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRejected(TransactionOutboxEvent.Rejected e) {
        TransactionRejected avro = TransactionRejected.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCorrelationId(e.correlationId())
                .setOccurredAt(Instant.now())
                .setTransactionId(e.transactionId())
                .setAccountId(e.accountId())
                .setCustomerId(e.customerId())
                .setReasonCode(e.reasonCode())
                .setReason(e.reason())
                .setFraudScore(e.fraudScore())
                .setSeverity(e.severity())
                .build();
        send(KafkaTopics.TRANSACTION_REJECTED, e.customerId(), avro, e.correlationId());
    }

    private void send(String topic, String key, SpecificRecord value, String correlationId) {
        var record = new ProducerRecord<String, Object>(topic, null, key, value);
        if (correlationId != null) {
            record.headers().add(new RecordHeader(Headers.CORRELATION_ID,
                    correlationId.getBytes(StandardCharsets.UTF_8)));
        }
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} for key={} corr={}: {}", topic, key, correlationId, ex.toString());
            } else {
                log.debug("Published {} key={} partition={} offset={}", topic, key,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
