package com.frauddetect.notification.service;

import com.frauddetect.avro.events.AlertCreated;
import com.frauddetect.notification.config.NotificationProperties;
import com.frauddetect.notification.domain.NotificationChannel;
import com.frauddetect.notification.domain.NotificationEntity;
import com.frauddetect.notification.domain.NotificationStatus;
import com.frauddetect.notification.domain.ProcessedEvent;
import com.frauddetect.notification.repository.NotificationRepository;
import com.frauddetect.notification.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns an {@code alert.created} event into one or more (mocked) notifications and a durable ledger
 * row per delivery attempt (Section 3). EMAIL is always sent; an SMS is added for the top severity
 * band ({@code notification.sms-on-severity}, default CRITICAL).
 *
 * <p>The whole handler is a single transaction that also writes the {@link ProcessedEvent} dedupe
 * marker, so persistence + dedupe commit atomically: a redelivered event is skipped and never
 * produces a duplicate notification.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String CONSUMER = "notification-service";

    private final NotificationRepository notifications;
    private final ProcessedEventRepository processedEvents;
    private final NotificationDeliveryService delivery;
    private final NotificationProperties properties;
    private final Counter sent;
    private final Counter failed;

    public NotificationService(NotificationRepository notifications,
                               ProcessedEventRepository processedEvents,
                               NotificationDeliveryService delivery,
                               NotificationProperties properties,
                               MeterRegistry meterRegistry) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.delivery = delivery;
        this.properties = properties;
        this.sent = Counter.builder("notification.delivery")
                .tag("outcome", "sent").register(meterRegistry);
        this.failed = Counter.builder("notification.delivery")
                .tag("outcome", "failed").register(meterRegistry);
    }

    /**
     * Persists and (mock-)delivers the notifications for one alert. Idempotent on the inbound
     * {@code eventId}: a redelivered event whose id is already recorded is skipped.
     */
    @Transactional
    public void handleAlertCreated(AlertCreated event) {
        String eventId = event.getEventId();
        if (eventId != null && processedEvents.existsById(eventId)) {
            log.debug("Skipping already-processed alert.created event {} (alert {})",
                    eventId, event.getAlertId());
            return;
        }

        for (NotificationChannel channel : channelsFor(event.getSeverity())) {
            NotificationEntity n = build(event, channel);
            boolean ok = delivery.deliver(n);
            n.setStatus(ok ? NotificationStatus.SENT : NotificationStatus.FAILED);
            notifications.save(n);
            (ok ? sent : failed).increment();
        }

        if (eventId != null) {
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER, Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationEntity> list(Pageable pageable) {
        return notifications.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<NotificationEntity> listByCustomer(String customerId, Pageable pageable) {
        return notifications.findByCustomerId(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<NotificationEntity> listByAlert(String alertId, Pageable pageable) {
        return notifications.findByAlertId(alertId, pageable);
    }

    /** EMAIL for every alert; SMS additionally for the configured top severity band. */
    private List<NotificationChannel> channelsFor(String severity) {
        List<NotificationChannel> channels = new ArrayList<>();
        channels.add(NotificationChannel.EMAIL);
        if (properties.smsOnSeverity().equalsIgnoreCase(severity)) {
            channels.add(NotificationChannel.SMS);
        }
        return channels;
    }

    private NotificationEntity build(AlertCreated event, NotificationChannel channel) {
        NotificationEntity n = new NotificationEntity();
        n.setId(UUID.randomUUID().toString());
        n.setAlertId(event.getAlertId());
        n.setTransactionId(event.getTransactionId());
        n.setCustomerId(event.getCustomerId());
        n.setChannel(channel);
        n.setRecipient(recipientFor(channel, event.getCustomerId()));
        n.setSubject("Fraud alert [" + event.getSeverity() + "]: " + event.getTitle());
        n.setBody(event.getDescription());
        n.setSeverity(String.valueOf(event.getSeverity()));
        n.setStatus(NotificationStatus.PENDING);
        n.setCorrelationId(event.getCorrelationId());
        return n;
    }

    /** Deterministic mock recipient derived from the customer id (no PII lookup in this service). */
    private String recipientFor(NotificationChannel channel, String customerId) {
        return switch (channel) {
            case EMAIL -> "customer-" + customerId + "@notify.example";
            case SMS -> "sms:" + customerId;
            case PUSH -> "push:" + customerId;
        };
    }
}
