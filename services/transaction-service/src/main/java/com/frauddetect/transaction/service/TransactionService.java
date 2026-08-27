package com.frauddetect.transaction.service;

import com.frauddetect.avro.events.FraudScoreCalculated;
import com.frauddetect.common.domain.TransactionStatus;
import com.frauddetect.common.error.ConflictException;
import com.frauddetect.common.error.ResourceNotFoundException;
import com.frauddetect.common.correlation.CorrelationContext;
import com.frauddetect.transaction.domain.IdempotencyRecord;
import com.frauddetect.transaction.domain.ProcessedEvent;
import com.frauddetect.transaction.domain.TransactionEntity;
import com.frauddetect.transaction.dto.CreateTransactionRequest;
import com.frauddetect.transaction.dto.TransactionResponse;
import com.frauddetect.transaction.messaging.TransactionOutboxEvent;
import com.frauddetect.transaction.repository.IdempotencyRepository;
import com.frauddetect.transaction.repository.ProcessedEventRepository;
import com.frauddetect.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Transaction lifecycle owner. Creation is idempotent (via {@code Idempotency-Key}); the fraud
 * verdict is applied exactly-once (via {@code eventId} dedupe). All Kafka emission happens through
 * {@link TransactionOutboxEvent}s published only after the DB transaction commits.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final String CONSUMER = "transaction-service";
    private static final Set<TransactionStatus> TERMINAL =
            EnumSet.of(TransactionStatus.COMPLETED, TransactionStatus.REJECTED, TransactionStatus.BLOCKED);

    private final TransactionRepository transactions;
    private final IdempotencyRepository idempotencyKeys;
    private final ProcessedEventRepository processedEvents;
    private final ApplicationEventPublisher publisher;

    public TransactionService(TransactionRepository transactions,
                              IdempotencyRepository idempotencyKeys,
                              ProcessedEventRepository processedEvents,
                              ApplicationEventPublisher publisher) {
        this.transactions = transactions;
        this.idempotencyKeys = idempotencyKeys;
        this.processedEvents = processedEvents;
        this.publisher = publisher;
    }

    /**
     * Persist a new transaction (PENDING) and schedule {@code transaction.created}. Replays the
     * original result if the same {@code Idempotency-Key} was already used.
     */
    @Transactional
    public TransactionResponse create(CreateTransactionRequest req, String idempotencyKey) {
        String correlationId = CorrelationContext.getOrCreate();
        boolean hasKey = idempotencyKey != null && !idempotencyKey.isBlank();

        if (hasKey) {
            var replay = idempotencyKeys.findById(idempotencyKey)
                    .map(rec -> transactions.findById(rec.getTransactionId()).orElseThrow(
                            () -> new IllegalStateException("Idempotency record without transaction: " + rec.getKey())))
                    .map(TransactionResponse::from);
            if (replay.isPresent()) {
                log.debug("Idempotent replay for key={} corr={}", idempotencyKey, correlationId);
                return replay.get();
            }
        }

        TransactionEntity e = new TransactionEntity();
        e.setId(UUID.randomUUID().toString());
        e.setAccountId(req.accountId());
        e.setCustomerId(req.customerId());
        e.setAmount(req.amount());
        e.setCurrency(req.currency());
        e.setType(req.type());
        e.setStatus(TransactionStatus.PENDING);
        e.setMerchantId(req.merchantId());
        e.setMerchantCategory(req.merchantCategory());
        e.setCountryCode(req.countryCode());
        e.setCity(req.city());
        e.setLatitude(req.latitude());
        e.setLongitude(req.longitude());
        e.setDeviceId(req.deviceId());
        e.setIpAddress(req.ipAddress());
        e.setChannel(req.channelOrDefault());
        e.setCorrelationId(correlationId);
        transactions.save(e);

        if (hasKey) {
            try {
                idempotencyKeys.saveAndFlush(new IdempotencyRecord(idempotencyKey, e.getId(), Instant.now()));
            } catch (DataIntegrityViolationException dup) {
                // A concurrent request claimed the same key first; the whole tx rolls back.
                throw new ConflictException("Duplicate request for Idempotency-Key: " + idempotencyKey);
            }
        }

        publisher.publishEvent(new TransactionOutboxEvent.Created(
                e.getId(), e.getAccountId(), e.getCustomerId(), e.getAmount(), e.getCurrency(),
                e.getType().name(), e.getMerchantId(), e.getMerchantCategory(), e.getCountryCode(),
                e.getCity(), e.getLatitude(), e.getLongitude(), e.getDeviceId(), e.getIpAddress(),
                e.getChannel(), correlationId));

        log.info("Transaction {} created (PENDING) amount={} {} type={} corr={}",
                e.getId(), e.getAmount(), e.getCurrency(), e.getType(), correlationId);
        return TransactionResponse.from(e);
    }

    /**
     * Apply the fraud verdict to a transaction and schedule the terminal event. Idempotent: a
     * redelivered {@code eventId} or an already-finalised transaction is a no-op.
     */
    @Transactional
    public void applyFraudVerdict(FraudScoreCalculated event) {
        String eventId = event.getEventId();
        if (processedEvents.existsById(eventId)) {
            log.debug("Skipping already-processed fraud verdict eventId={}", eventId);
            return;
        }

        TransactionEntity tx = transactions.findById(event.getTransactionId()).orElse(null);
        if (tx == null) {
            log.warn("Fraud verdict for unknown transaction {} (eventId={}); marking processed",
                    event.getTransactionId(), eventId);
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER, Instant.now()));
            return;
        }
        if (TERMINAL.contains(tx.getStatus())) {
            log.debug("Transaction {} already terminal ({}); ignoring verdict", tx.getId(), tx.getStatus());
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER, Instant.now()));
            return;
        }

        int score = event.getScore();
        String severity = event.getSeverity();
        String decision = event.getDecision();
        tx.setFraudScore(score);
        tx.setSeverity(severity);
        tx.setDecision(decision);

        switch (decision == null ? "ALLOW" : decision) {
            case "BLOCK" -> {
                tx.setStatus(TransactionStatus.BLOCKED);
                tx.setReasonCode("FRAUD_BLOCKED");
                tx.setReason("Blocked by fraud engine (severity " + severity + ", score " + score + ")");
                publisher.publishEvent(new TransactionOutboxEvent.Rejected(
                        tx.getId(), tx.getAccountId(), tx.getCustomerId(), tx.getReasonCode(),
                        tx.getReason(), score, severity, event.getCorrelationId()));
            }
            case "REVIEW" -> {
                // Not finalised: flagged for investigation. alert-service drives the case.
                tx.setStatus(TransactionStatus.FLAGGED);
            }
            default -> {
                tx.setStatus(TransactionStatus.COMPLETED);
                publisher.publishEvent(new TransactionOutboxEvent.Completed(
                        tx.getId(), tx.getAccountId(), tx.getCustomerId(), tx.getAmount(), tx.getCurrency(),
                        score, severity, event.getCorrelationId()));
            }
        }

        transactions.save(tx);
        processedEvents.save(new ProcessedEvent(eventId, CONSUMER, Instant.now()));
        log.info("Transaction {} → {} (score={} severity={} decision={}) corr={}",
                tx.getId(), tx.getStatus(), score, severity, decision, event.getCorrelationId());
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(String id) {
        return transactions.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> byAccount(String accountId, Pageable pageable) {
        return transactions.findByAccountIdOrderByCreatedAtDesc(accountId, pageable).map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> byCustomer(String customerId, Pageable pageable) {
        return transactions.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable).map(TransactionResponse::from);
    }
}
