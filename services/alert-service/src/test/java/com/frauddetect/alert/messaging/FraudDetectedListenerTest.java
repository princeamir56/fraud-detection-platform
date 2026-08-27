package com.frauddetect.alert.messaging;

import com.frauddetect.alert.service.AlertService;
import com.frauddetect.avro.events.FraudDetected;
import com.frauddetect.common.correlation.CorrelationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the {@code fraud.detected} listener: it must delegate to {@link AlertService} and
 * always clear the correlation id from the MDC afterwards — even when processing throws (so the id
 * does not leak onto the next record handled by the pooled consumer thread).
 */
@ExtendWith(MockitoExtension.class)
class FraudDetectedListenerTest {

    @Mock AlertService alertService;

    private static FraudDetected event() {
        return FraudDetected.newBuilder()
                .setEventId("evt-1")
                .setCorrelationId("corr-xyz")
                .setOccurredAt(Instant.parse("2026-08-22T10:15:30Z"))
                .setTransactionId("tx-1")
                .setCustomerId("cust-1")
                .setAccountId("acc-1")
                .setScore(90)
                .setSeverity("CRITICAL")
                .setDecision("BLOCK")
                .setPrimaryReason("velocity")
                .setTriggeredRuleCodes(List.of("GEO_VELOCITY"))
                .setAmount(new BigDecimal("2500.0000"))
                .setCurrency("USD")
                .setCountryCode("US")
                .build();
    }

    @Test
    void delegatesToServiceAndClearsCorrelation() {
        FraudDetectedListener listener = new FraudDetectedListener(alertService);
        FraudDetected event = event();

        listener.onFraudDetected(event);

        verify(alertService).createFromFraudDetected(event);
        assertThat(CorrelationContext.getCorrelationId()).isNull();
    }

    @Test
    void clearsCorrelationEvenWhenProcessingFails() {
        FraudDetectedListener listener = new FraudDetectedListener(alertService);
        FraudDetected event = event();
        doThrow(new RuntimeException("boom")).when(alertService).createFromFraudDetected(event);

        assertThatThrownBy(() -> listener.onFraudDetected(event))
                .isInstanceOf(RuntimeException.class);
        assertThat(CorrelationContext.getCorrelationId()).isNull();
    }
}
