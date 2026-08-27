package com.frauddetect.notification.messaging;

import com.frauddetect.avro.events.AlertCreated;
import com.frauddetect.common.constants.KafkaTopics;
import com.frauddetect.common.correlation.CorrelationContext;
import com.frauddetect.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code alert.created} (Section 7) and hands off to {@link NotificationService}, which
 * persists + (mock-)delivers notifications idempotently. The correlation id is restored from the Avro
 * envelope into the MDC for the duration of processing. Failures propagate to the container's
 * {@link org.springframework.kafka.listener.DefaultErrorHandler} for bounded retry then DLT.
 */
@Component
public class AlertCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(AlertCreatedListener.class);

    private final NotificationService notificationService;

    public AlertCreatedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = KafkaTopics.ALERT_CREATED, containerFactory = "kafkaListenerContainerFactory")
    public void onAlertCreated(AlertCreated event) {
        CorrelationContext.setCorrelationId(event.getCorrelationId());
        try {
            log.debug("Received alert.created alert={} customer={} severity={}",
                    event.getAlertId(), event.getCustomerId(), event.getSeverity());
            notificationService.handleAlertCreated(event);
        } finally {
            CorrelationContext.clear();
        }
    }
}
