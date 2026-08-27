package com.frauddetect.fraud.messaging;

import com.frauddetect.avro.events.TransactionCreated;
import com.frauddetect.common.constants.KafkaTopics;
import com.frauddetect.common.correlation.CorrelationContext;
import com.frauddetect.fraud.domain.ProcessedEvent;
import com.frauddetect.fraud.repository.ProcessedEventRepository;
import com.frauddetect.fraud.service.FraudDetectionService;
import com.frauddetect.fraud.service.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Entry point of the fraud hot path (Sections 7 + 18): consumes {@code transaction.created}, restores
 * the correlation id into the MDC, dedupes redeliveries, maps the Avro envelope to an Avro-free
 * {@link TransactionContext}, and hands off to {@link FraudDetectionService}.
 *
 * <p>Failures propagate to the container's {@link org.springframework.kafka.listener.DefaultErrorHandler}
 * for bounded retry then DLT. The dedupe marker is written only after a fully successful analysis, so
 * a mid-processing failure is retried rather than silently swallowed.
 */
@Component
public class TransactionCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionCreatedListener.class);
    private static final String CONSUMER = "fraud-detection";

    private final FraudDetectionService fraudDetectionService;
    private final ProcessedEventRepository processedEventRepository;

    public TransactionCreatedListener(FraudDetectionService fraudDetectionService,
                                      ProcessedEventRepository processedEventRepository) {
        this.fraudDetectionService = fraudDetectionService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = KafkaTopics.TRANSACTION_CREATED, containerFactory = "kafkaListenerContainerFactory")
    public void onTransactionCreated(TransactionCreated event) {
        String eventId = event.getEventId();
        CorrelationContext.setCorrelationId(event.getCorrelationId());
        try {
            if (eventId != null && processedEventRepository.existsById(eventId)) {
                log.debug("Skipping already-processed event {} (tx {})", eventId, event.getTransactionId());
                return;
            }
            TransactionContext ctx = toContext(event);
            fraudDetectionService.analyze(ctx, eventId);
            markProcessed(eventId, ctx.transactionId());
        } finally {
            CorrelationContext.clear();
        }
    }

    private void markProcessed(String eventId, String transactionId) {
        if (eventId == null) {
            return;
        }
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, transactionId, Instant.now()));
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent redelivery already recorded it; the effects are idempotent, so this is benign.
            log.debug("Dedupe marker for event {} already present", eventId);
        }
    }

    /** Maps the Avro event to the internal, Avro-free transaction snapshot. */
    private TransactionContext toContext(TransactionCreated e) {
        return new TransactionContext(
                e.getTransactionId(),
                e.getAccountId(),
                e.getCustomerId(),
                e.getAmount(),
                e.getCurrency(),
                e.getType(),
                e.getCountryCode(),
                e.getMerchantId(),
                e.getMerchantCategory(),
                e.getDeviceId(),
                e.getIpAddress(),
                e.getLatitude(),
                e.getLongitude(),
                e.getOccurredAt(),
                e.getCorrelationId());
    }
}
