package com.frauddetect.alert.messaging;

import com.frauddetect.alert.service.AlertService;
import com.frauddetect.avro.events.FraudDetected;
import com.frauddetect.common.constants.KafkaTopics;
import com.frauddetect.common.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code fraud.detected} (HIGH/CRITICAL verdicts) and opens a triage alert (Section 7).
 * Restores the correlation id into the MDC for the duration of processing, delegates the dedupe +
 * persist + publish to {@link AlertService}, and always clears the MDC afterwards.
 *
 * <p>Failures propagate to the container's {@link org.springframework.kafka.listener.DefaultErrorHandler}
 * for bounded retry then DLT; the dedupe marker is written by the service only after the alert is
 * fully persisted and {@code alert.created} published, so a mid-processing failure is retried.
 */
@Component
public class FraudDetectedListener {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectedListener.class);

    private final AlertService alertService;

    public FraudDetectedListener(AlertService alertService) {
        this.alertService = alertService;
    }

    @KafkaListener(topics = KafkaTopics.FRAUD_DETECTED, containerFactory = "kafkaListenerContainerFactory")
    public void onFraudDetected(FraudDetected event) {
        CorrelationContext.setCorrelationId(event.getCorrelationId());
        try {
            log.debug("Received fraud.detected event {} for tx {}", event.getEventId(), event.getTransactionId());
            alertService.createFromFraudDetected(event);
        } finally {
            CorrelationContext.clear();
        }
    }
}
