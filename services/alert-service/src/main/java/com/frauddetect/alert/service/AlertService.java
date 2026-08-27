package com.frauddetect.alert.service;

import com.frauddetect.alert.domain.AlertEntity;
import com.frauddetect.alert.domain.AlertStatus;
import com.frauddetect.alert.domain.ProcessedEvent;
import com.frauddetect.alert.domain.Resolution;
import com.frauddetect.alert.messaging.AlertEventProducer;
import com.frauddetect.alert.messaging.EventIds;
import com.frauddetect.alert.repository.AlertRepository;
import com.frauddetect.alert.repository.ProcessedEventRepository;
import com.frauddetect.alert.search.AlertSearchService;
import com.frauddetect.avro.events.FraudDetected;
import com.frauddetect.common.error.ConflictException;
import com.frauddetect.common.error.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Alert lifecycle orchestration (Section 7).
 *
 * <p><b>Ingest:</b> {@link #createFromFraudDetected(FraudDetected)} opens an alert from a
 * {@code fraud.detected} event. It is effectively-once via three layers: a consumer dedupe table keyed
 * by the inbound {@code eventId}, a deterministic alert primary key derived from that id (so a
 * concurrent redelivery collides on the PK rather than opening a duplicate case), and downstream
 * consumers that dedupe {@code alert.created}. MySQL is the source of truth; the Elasticsearch index
 * is updated best-effort and never fails the flow.
 *
 * <p><b>Triage:</b> {@link #acknowledge} and {@link #resolve} drive the REST-facing state machine
 * OPEN → ACKNOWLEDGED → RESOLVED, guarded by optimistic locking and explicit state checks. Resolving
 * emits {@code alert.resolved}.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final String CONSUMER = "alert-service";

    private final AlertRepository alertRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final AlertSearchService searchService;
    private final AlertEventProducer producer;
    private final Counter alertsCreated;
    private final Counter alertsResolved;

    public AlertService(AlertRepository alertRepository,
                        ProcessedEventRepository processedEventRepository,
                        AlertSearchService searchService,
                        AlertEventProducer producer,
                        MeterRegistry meterRegistry) {
        this.alertRepository = alertRepository;
        this.processedEventRepository = processedEventRepository;
        this.searchService = searchService;
        this.producer = producer;
        this.alertsCreated = Counter.builder("alert.created")
                .description("Alerts opened from fraud.detected events")
                .register(meterRegistry);
        this.alertsResolved = Counter.builder("alert.resolved")
                .description("Alerts closed with a resolution")
                .register(meterRegistry);
    }

    // ---- ingest ----

    @Transactional
    public void createFromFraudDetected(FraudDetected event) {
        String eventId = event.getEventId();
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed fraud.detected event {} (tx {})",
                    eventId, event.getTransactionId());
            return;
        }

        String alertId = EventIds.derive(eventId, "alert");
        AlertEntity alert = buildAlert(alertId, event);
        try {
            alert = alertRepository.save(alert);
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent redelivery already opened this alert (PK collision). Record the marker and stop.
            log.debug("Alert {} already exists for event {}; treating as duplicate", alertId, eventId);
            markProcessed(eventId);
            return;
        }

        searchService.index(alert);
        producer.publishCreated(alert, eventId);
        markProcessed(eventId);
        alertsCreated.increment();
        log.info("Opened alert {} (severity={}, score={}) for tx {}",
                alert.getId(), alert.getSeverity(), alert.getScore(), alert.getTransactionId());
    }

    private AlertEntity buildAlert(String alertId, FraudDetected event) {
        AlertEntity alert = new AlertEntity();
        alert.setId(alertId);
        alert.setTransactionId(event.getTransactionId());
        alert.setCustomerId(event.getCustomerId());
        alert.setAccountId(event.getAccountId());
        alert.setSeverity(event.getSeverity());
        alert.setScore(event.getScore());
        alert.setStatus(AlertStatus.OPEN);
        alert.setTitle(buildTitle(event));
        alert.setDescription(buildDescription(event));
        alert.setPrimaryReason(event.getPrimaryReason());
        alert.setCorrelationId(event.getCorrelationId());
        return alert;
    }

    private static String buildTitle(FraudDetected event) {
        String title = "%s-severity fraud on transaction %s"
                .formatted(event.getSeverity(), event.getTransactionId());
        return title.length() > 255 ? title.substring(0, 255) : title;
    }

    private static String buildDescription(FraudDetected event) {
        List<String> rules = event.getTriggeredRuleCodes();
        String rulesText = (rules == null || rules.isEmpty()) ? "none" : String.join(", ", rules);
        String desc = "Fraud score %d, decision %s. Primary reason: %s. Triggered rules: %s."
                .formatted(event.getScore(), event.getDecision(),
                        event.getPrimaryReason() != null ? event.getPrimaryReason() : "unspecified",
                        rulesText);
        return desc.length() > 2000 ? desc.substring(0, 2000) : desc;
    }

    private void markProcessed(String eventId) {
        if (eventId == null) {
            return;
        }
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER, Instant.now()));
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent redelivery already recorded it; effects are idempotent, so this is benign.
            log.debug("Dedupe marker for event {} already present", eventId);
        }
    }

    // ---- query ----

    @Transactional(readOnly = true)
    public Page<AlertEntity> list(AlertStatus status, String customerId, String severity, Pageable pageable) {
        if (customerId != null && !customerId.isBlank()) {
            return alertRepository.findByCustomerId(customerId, pageable);
        }
        if (status != null) {
            return alertRepository.findByStatus(status, pageable);
        }
        if (severity != null && !severity.isBlank()) {
            return alertRepository.findBySeverity(severity, pageable);
        }
        return alertRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public AlertEntity get(String id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }

    // ---- triage ----

    @Transactional
    public AlertEntity acknowledge(String id, String assignee) {
        AlertEntity alert = get(id);
        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new ConflictException("Alert " + id + " is already resolved and cannot be acknowledged");
        }
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAssignedTo(assignee);
        alert = alertRepository.save(alert);
        searchService.index(alert);
        log.info("Alert {} acknowledged by {}", id, assignee);
        return alert;
    }

    @Transactional
    public AlertEntity resolve(String id, Resolution resolution, String notes, String resolvedBy) {
        AlertEntity alert = get(id);
        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new ConflictException("Alert " + id + " is already resolved");
        }
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolution(resolution);
        alert.setResolutionNotes(notes);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(Instant.now());
        alert = alertRepository.save(alert);

        searchService.index(alert);
        producer.publishResolved(alert);
        alertsResolved.increment();
        log.info("Alert {} resolved as {} by {}", id, resolution, resolvedBy);
        return alert;
    }
}
