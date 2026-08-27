package com.frauddetect.alert.messaging;

import com.frauddetect.alert.domain.AlertEntity;
import com.frauddetect.avro.events.AlertCreated;
import com.frauddetect.avro.events.AlertResolved;
import com.frauddetect.common.constants.Headers;
import com.frauddetect.common.constants.KafkaTopics;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Publishes alert lifecycle events (Section 7). {@code alert.created} is emitted when an alert opens
 * from a {@code fraud.detected} event; {@code alert.resolved} when an investigator records a
 * disposition. Both are keyed by {@code customerId} so a customer's alert events stay ordered on one
 * partition, and carry the end-to-end {@code X-Correlation-Id} header.
 *
 * <p>The {@code alert.created} {@code eventId} is derived deterministically from the inbound fraud
 * event id ({@link EventIds}), so a re-emit on reprocessing carries the SAME id and downstream
 * consumers (audit, notification) dedupe it. {@code alert.resolved} is a REST-initiated one-shot, so
 * it uses a fresh random id.
 */
@Component
public class AlertEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AlertEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Emits {@code alert.created}. {@code sourceFraudEventId} is the inbound {@code fraud.detected}
     * event id, used to derive a stable outbound id.
     */
    public void publishCreated(AlertEntity alert, String sourceFraudEventId) {
        AlertCreated event = AlertCreated.newBuilder()
                .setEventId(EventIds.derive(sourceFraudEventId, "alert-created"))
                .setCorrelationId(alert.getCorrelationId())
                .setOccurredAt(Instant.now())
                .setAlertId(alert.getId())
                .setTransactionId(alert.getTransactionId())
                .setCustomerId(alert.getCustomerId())
                .setAccountId(alert.getAccountId())
                .setSeverity(alert.getSeverity())
                .setScore(alert.getScore())
                .setStatus(alert.getStatus().name())
                .setTitle(alert.getTitle())
                .setDescription(alert.getDescription())
                .setAssignedTo(alert.getAssignedTo())
                .build();
        send(KafkaTopics.ALERT_CREATED, alert.getCustomerId(), event, alert.getCorrelationId());
    }

    /** Emits {@code alert.resolved} after an investigator records a disposition. */
    public void publishResolved(AlertEntity alert) {
        AlertResolved event = AlertResolved.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCorrelationId(alert.getCorrelationId())
                .setOccurredAt(Instant.now())
                .setAlertId(alert.getId())
                .setTransactionId(alert.getTransactionId())
                .setCustomerId(alert.getCustomerId())
                .setResolution(alert.getResolution().name())
                .setResolvedBy(alert.getResolvedBy())
                .setNotes(alert.getResolutionNotes())
                .setCaseId(null)
                .build();
        send(KafkaTopics.ALERT_RESOLVED, alert.getCustomerId(), event, alert.getCorrelationId());
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
