package com.frauddetect.notification.messaging;

import com.frauddetect.avro.events.AlertCreated;
import com.frauddetect.common.correlation.CorrelationContext;
import com.frauddetect.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Verifies the listener delegates to the service and always clears the correlation MDC. */
@ExtendWith(MockitoExtension.class)
class AlertCreatedListenerTest {

    @Mock NotificationService notificationService;

    private AlertCreated alert() {
        return AlertCreated.newBuilder()
                .setEventId("evt-1")
                .setCorrelationId("corr-42")
                .setOccurredAt(Instant.now())
                .setAlertId("alert-1")
                .setTransactionId("tx-1")
                .setCustomerId("cust-1")
                .setAccountId("acc-1")
                .setSeverity("HIGH")
                .setScore(70)
                .setStatus("OPEN")
                .setTitle("t")
                .setDescription("d")
                .setAssignedTo(null)
                .build();
    }

    @Test
    void delegatesToServiceAndClearsCorrelation() {
        AlertCreated event = alert();

        new AlertCreatedListener(notificationService).onAlertCreated(event);

        verify(notificationService).handleAlertCreated(event);
        // MDC must not leak onto the pooled consumer thread after processing.
        assertThat(CorrelationContext.getCorrelationId()).isNull();
    }
}
