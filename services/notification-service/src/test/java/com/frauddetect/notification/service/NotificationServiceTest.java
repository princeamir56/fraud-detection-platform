package com.frauddetect.notification.service;

import com.frauddetect.avro.events.AlertCreated;
import com.frauddetect.notification.config.NotificationProperties;
import com.frauddetect.notification.domain.NotificationChannel;
import com.frauddetect.notification.domain.NotificationEntity;
import com.frauddetect.notification.domain.NotificationStatus;
import com.frauddetect.notification.repository.NotificationRepository;
import com.frauddetect.notification.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the notification fan-out + idempotency logic. Repositories and the (mock) delivery
 * gateway are mocked; a real {@link SimpleMeterRegistry} backs the counters.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notifications;
    @Mock ProcessedEventRepository processedEvents;
    @Mock NotificationDeliveryService delivery;
    @Captor ArgumentCaptor<NotificationEntity> notificationCaptor;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, processedEvents, delivery,
                new NotificationProperties("fraud@test", "CRITICAL"), new SimpleMeterRegistry());
    }

    private AlertCreated alert(String eventId, String severity) {
        return AlertCreated.newBuilder()
                .setEventId(eventId)
                .setCorrelationId("corr-1")
                .setOccurredAt(Instant.now())
                .setAlertId("alert-1")
                .setTransactionId("tx-1")
                .setCustomerId("cust-1")
                .setAccountId("acc-1")
                .setSeverity(severity)
                .setScore(85)
                .setStatus("OPEN")
                .setTitle("Suspicious transaction")
                .setDescription("Score 85 exceeded the block threshold")
                .setAssignedTo(null)
                .build();
    }

    @Test
    void mediumSeverityCreatesSingleEmailNotification() {
        when(processedEvents.existsById("evt-1")).thenReturn(false);
        when(delivery.deliver(any())).thenReturn(true);

        service.handleAlertCreated(alert("evt-1", "MEDIUM"));

        verify(notifications, times(1)).save(notificationCaptor.capture());
        NotificationEntity saved = notificationCaptor.getValue();
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getRecipient()).isEqualTo("customer-cust-1@notify.example");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getSubject()).contains("Suspicious transaction");
        verify(processedEvents).save(any());
    }

    @Test
    void criticalSeverityAlsoFansOutToSms() {
        when(processedEvents.existsById("evt-2")).thenReturn(false);
        when(delivery.deliver(any())).thenReturn(true);

        service.handleAlertCreated(alert("evt-2", "CRITICAL"));

        verify(notifications, times(2)).save(notificationCaptor.capture());
        List<NotificationChannel> channels = notificationCaptor.getAllValues().stream()
                .map(NotificationEntity::getChannel).toList();
        assertThat(channels).containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.SMS);
    }

    @Test
    void failedDeliveryIsRecordedAsFailed() {
        when(processedEvents.existsById("evt-3")).thenReturn(false);
        when(delivery.deliver(any())).thenReturn(false);

        service.handleAlertCreated(alert("evt-3", "MEDIUM"));

        verify(notifications).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void alreadyProcessedEventIsSkipped() {
        when(processedEvents.existsById("dupe")).thenReturn(true);

        service.handleAlertCreated(alert("dupe", "CRITICAL"));

        verify(notifications, never()).save(any());
        verify(processedEvents, never()).save(any());
    }
}
