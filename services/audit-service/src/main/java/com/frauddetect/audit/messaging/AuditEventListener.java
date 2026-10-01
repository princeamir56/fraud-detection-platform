package com.frauddetect.audit.messaging;

import com.frauddetect.audit.search.AuditEventDocument;
import com.frauddetect.audit.search.AuditSearchService;
import com.frauddetect.common.constants.KafkaTopics;
import com.frauddetect.common.correlation.CorrelationContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The audit fan-in (Section 3): a single listener subscribed to <em>every</em> domain topic. Each
 * record is mapped ({@link AuditEventMapper}) to a flat {@link AuditEventDocument} and indexed into the
 * append-only {@code audit-events} index, keyed by {@code eventId} so redeliveries are idempotent.
 *
 * <p>Correlation id is restored into the MDC for the duration of handling and cleared in a
 * {@code finally} so it never leaks onto the next poll. Indexing failures are intentionally allowed to
 * propagate to the container error handler (retry → DLT) rather than being swallowed — an audit record
 * must not be lost.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditSearchService auditSearchService;

    public AuditEventListener(AuditSearchService auditSearchService) {
        this.auditSearchService = auditSearchService;
    }

    @KafkaListener(
            topics = {
                    KafkaTopics.TRANSACTION_CREATED,
                    KafkaTopics.TRANSACTION_COMPLETED,
                    KafkaTopics.TRANSACTION_REJECTED,
                    KafkaTopics.FRAUD_CHECK_REQUESTED,
                    KafkaTopics.FRAUD_SCORE_CALCULATED,
                    KafkaTopics.FRAUD_DETECTED,
                    KafkaTopics.ALERT_CREATED,
                    KafkaTopics.ALERT_RESOLVED
            },
            containerFactory = "kafkaListenerContainerFactory")
    public void onEvent(ConsumerRecord<String, Object> record) {
        // Take the record explicitly: an untyped Object parameter is bound to the whole ConsumerRecord,
        // not the Avro payload, which the mapper would reject as an unsupported event type.
        AuditEventDocument doc = AuditEventMapper.toDocument(record.value(), Instant.now());
        CorrelationContext.setCorrelationId(doc.correlationId());
        try {
            log.debug("Auditing {} eventId={}", doc.eventType(), doc.eventId());
            auditSearchService.index(doc);
        } finally {
            CorrelationContext.clear();
        }
    }
}
