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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the alert lifecycle orchestration (Section 7): idempotent ingest from
 * {@code fraud.detected}, the resolution state machine, and the guard against re-resolving. Collaborators
 * are mocked; a real {@link SimpleMeterRegistry} verifies the domain counters.
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock ProcessedEventRepository processedEventRepository;
    @Mock AlertSearchService searchService;
    @Mock AlertEventProducer producer;

    SimpleMeterRegistry meterRegistry;
    AlertService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new AlertService(alertRepository, processedEventRepository, searchService, producer, meterRegistry);
    }

    private static FraudDetected fraudDetected(String eventId) {
        return FraudDetected.newBuilder()
                .setEventId(eventId)
                .setCorrelationId("corr-1")
                .setOccurredAt(Instant.parse("2026-08-22T10:15:30Z"))
                .setTransactionId("tx-1")
                .setCustomerId("cust-1")
                .setAccountId("acc-1")
                .setScore(90)
                .setSeverity("CRITICAL")
                .setDecision("BLOCK")
                .setPrimaryReason("Impossible travel velocity")
                .setTriggeredRuleCodes(List.of("GEO_VELOCITY", "HIGH_AMOUNT"))
                .setAmount(new BigDecimal("2500.0000"))
                .setCurrency("USD")
                .setCountryCode("US")
                .build();
    }

    @Test
    void createFromFraudDetectedOpensAlertPublishesAndMarksProcessed() {
        FraudDetected event = fraudDetected("evt-1");
        when(processedEventRepository.existsById("evt-1")).thenReturn(false);
        when(alertRepository.save(any(AlertEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createFromFraudDetected(event);

        ArgumentCaptor<AlertEntity> captor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(alertRepository).save(captor.capture());
        AlertEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(EventIds.derive("evt-1", "alert"));
        assertThat(saved.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getScore()).isEqualTo(90);
        assertThat(saved.getCustomerId()).isEqualTo("cust-1");
        assertThat(saved.getPrimaryReason()).isEqualTo("Impossible travel velocity");
        assertThat(saved.getDescription()).contains("GEO_VELOCITY", "HIGH_AMOUNT");

        verify(searchService).index(saved);
        verify(producer).publishCreated(saved, "evt-1");
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        assertThat(meterRegistry.get("alert.created").counter().count()).isEqualTo(1.0);
    }

    @Test
    void createFromFraudDetectedSkipsAlreadyProcessedEvent() {
        FraudDetected event = fraudDetected("evt-dup");
        when(processedEventRepository.existsById("evt-dup")).thenReturn(true);

        service.createFromFraudDetected(event);

        verify(alertRepository, never()).save(any());
        verify(producer, never()).publishCreated(any(), any());
        verify(searchService, never()).index(any());
    }

    @Test
    void resolveSetsResolutionIndexesAndPublishes() {
        AlertEntity open = new AlertEntity();
        open.setId("alert-1");
        open.setTransactionId("tx-1");
        open.setCustomerId("cust-1");
        open.setAccountId("acc-1");
        open.setSeverity("HIGH");
        open.setScore(75);
        open.setStatus(AlertStatus.OPEN);
        open.setTitle("t");
        open.setDescription("d");
        when(alertRepository.findById("alert-1")).thenReturn(Optional.of(open));
        when(alertRepository.save(any(AlertEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertEntity result = service.resolve("alert-1", Resolution.CONFIRMED_FRAUD, "chargeback", "inv-bob");

        assertThat(result.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(result.getResolution()).isEqualTo(Resolution.CONFIRMED_FRAUD);
        assertThat(result.getResolvedBy()).isEqualTo("inv-bob");
        assertThat(result.getResolutionNotes()).isEqualTo("chargeback");
        assertThat(result.getResolvedAt()).isNotNull();
        verify(searchService).index(result);
        verify(producer).publishResolved(result);
        assertThat(meterRegistry.get("alert.resolved").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveRejectsAlreadyResolvedAlert() {
        AlertEntity resolved = new AlertEntity();
        resolved.setId("alert-2");
        resolved.setStatus(AlertStatus.RESOLVED);
        when(alertRepository.findById("alert-2")).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> service.resolve("alert-2", Resolution.DISMISSED, null, "inv-amy"))
                .isInstanceOf(ConflictException.class);

        verify(alertRepository, never()).save(any());
        verify(producer, never()).publishResolved(any());
    }

    @Test
    void acknowledgeAssignsAndMovesToAcknowledged() {
        AlertEntity open = new AlertEntity();
        open.setId("alert-3");
        open.setStatus(AlertStatus.OPEN);
        when(alertRepository.findById("alert-3")).thenReturn(Optional.of(open));
        when(alertRepository.save(any(AlertEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertEntity result = service.acknowledge("alert-3", "inv-amy");

        assertThat(result.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(result.getAssignedTo()).isEqualTo("inv-amy");
        verify(searchService).index(result);
        verify(producer, never()).publishResolved(any());
    }

    @Test
    void acknowledgeRejectsResolvedAlert() {
        AlertEntity resolved = new AlertEntity();
        resolved.setId("alert-4");
        resolved.setStatus(AlertStatus.RESOLVED);
        when(alertRepository.findById("alert-4")).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> service.acknowledge("alert-4", "inv-amy"))
                .isInstanceOf(ConflictException.class);
        verify(alertRepository, never()).save(any());
    }
}
