package com.frauddetect.transaction.service;

import com.frauddetect.avro.events.FraudScoreCalculated;
import com.frauddetect.common.domain.TransactionStatus;
import com.frauddetect.common.domain.TransactionType;
import com.frauddetect.common.error.ConflictException;
import com.frauddetect.transaction.domain.IdempotencyRecord;
import com.frauddetect.transaction.domain.TransactionEntity;
import com.frauddetect.transaction.dto.CreateTransactionRequest;
import com.frauddetect.transaction.dto.TransactionResponse;
import com.frauddetect.transaction.messaging.TransactionOutboxEvent;
import com.frauddetect.transaction.repository.IdempotencyRepository;
import com.frauddetect.transaction.repository.ProcessedEventRepository;
import com.frauddetect.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactions;
    @Mock IdempotencyRepository idempotencyKeys;
    @Mock ProcessedEventRepository processedEvents;
    @Mock ApplicationEventPublisher publisher;

    @InjectMocks TransactionService service;

    private CreateTransactionRequest sampleRequest() {
        return new CreateTransactionRequest("acc-1", "cust-1", new BigDecimal("125.50"), "USD",
                TransactionType.PURCHASE, "m-1", "GROCERY", "US", "Denver", 39.7, -104.9,
                "dev-1", "10.0.0.1", "WEB");
    }

    private TransactionEntity persisted(String id, TransactionStatus status) {
        TransactionEntity e = new TransactionEntity();
        e.setId(id);
        e.setAccountId("acc-1");
        e.setCustomerId("cust-1");
        e.setAmount(new BigDecimal("125.50"));
        e.setCurrency("USD");
        e.setType(TransactionType.PURCHASE);
        e.setStatus(status);
        e.setCountryCode("US");
        e.setChannel("WEB");
        return e;
    }

    private FraudScoreCalculated verdict(String txId, int score, String severity, String decision) {
        return FraudScoreCalculated.newBuilder()
                .setEventId("evt-" + txId)
                .setCorrelationId("corr-1")
                .setOccurredAt(Instant.now())
                .setTransactionId(txId)
                .setCustomerId("cust-1")
                .setAccountId("acc-1")
                .setScore(score)
                .setSeverity(severity)
                .setDecision(decision)
                .build();
    }

    @Test
    void createPersistsPendingAndPublishesCreatedEvent() {
        TransactionResponse response = service.create(sampleRequest(), null);

        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
        verify(transactions).save(any(TransactionEntity.class));
        verify(publisher).publishEvent(any(TransactionOutboxEvent.Created.class));
    }

    @Test
    void createReplaysExistingResultForKnownIdempotencyKey() {
        var record = new IdempotencyRecord("key-1", "tx-1", Instant.now());
        when(idempotencyKeys.findById("key-1")).thenReturn(Optional.of(record));
        when(transactions.findById("tx-1")).thenReturn(Optional.of(persisted("tx-1", TransactionStatus.PENDING)));

        TransactionResponse response = service.create(sampleRequest(), "key-1");

        assertThat(response.id()).isEqualTo("tx-1");
        verify(transactions, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void createTranslatesDuplicateKeyRaceToConflict() {
        when(idempotencyKeys.findById("key-1")).thenReturn(Optional.empty());
        when(idempotencyKeys.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(sampleRequest(), "key-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void allowVerdictCompletesTransaction() {
        when(processedEvents.existsById(anyString())).thenReturn(false);
        when(transactions.findById("tx-1")).thenReturn(Optional.of(persisted("tx-1", TransactionStatus.PENDING)));

        service.applyFraudVerdict(verdict("tx-1", 12, "LOW", "ALLOW"));

        ArgumentCaptor<TransactionEntity> saved = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(publisher).publishEvent(any(TransactionOutboxEvent.Completed.class));
        verify(processedEvents).save(any());
    }

    @Test
    void blockVerdictBlocksTransactionAndPublishesRejected() {
        when(processedEvents.existsById(anyString())).thenReturn(false);
        when(transactions.findById("tx-2")).thenReturn(Optional.of(persisted("tx-2", TransactionStatus.PENDING)));

        service.applyFraudVerdict(verdict("tx-2", 92, "CRITICAL", "BLOCK"));

        ArgumentCaptor<TransactionEntity> saved = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransactionStatus.BLOCKED);
        assertThat(saved.getValue().getReasonCode()).isEqualTo("FRAUD_BLOCKED");
        verify(publisher).publishEvent(any(TransactionOutboxEvent.Rejected.class));
    }

    @Test
    void reviewVerdictFlagsWithoutTerminalEvent() {
        when(processedEvents.existsById(anyString())).thenReturn(false);
        when(transactions.findById("tx-3")).thenReturn(Optional.of(persisted("tx-3", TransactionStatus.PENDING)));

        service.applyFraudVerdict(verdict("tx-3", 55, "MEDIUM", "REVIEW"));

        ArgumentCaptor<TransactionEntity> saved = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransactionStatus.FLAGGED);
        verifyNoInteractions(publisher);
    }

    @Test
    void duplicateVerdictIsIgnored() {
        when(processedEvents.existsById("evt-tx-4")).thenReturn(true);

        service.applyFraudVerdict(verdict("tx-4", 12, "LOW", "ALLOW"));

        verify(transactions, never()).findById(anyString());
        verify(transactions, never()).save(any());
        verifyNoInteractions(publisher);
    }
}
