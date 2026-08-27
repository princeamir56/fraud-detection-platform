package com.frauddetect.audit.messaging;

import com.frauddetect.audit.search.AuditEventDocument;
import com.frauddetect.avro.events.AlertResolved;
import com.frauddetect.avro.events.FraudDetected;
import com.frauddetect.avro.events.TransactionCreated;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pure event→document mapping — the bulk of audit-service's logic. Exercises the
 * three structurally-distinct branches (a plain transaction event, a fraud verdict with score/decision,
 * and an alert-lifecycle event that carries no account id) plus the unknown-type guard.
 */
class AuditEventMapperTest {

    private static final Instant OCCURRED = Instant.parse("2026-08-22T10:15:30Z");
    private static final Instant INDEXED = Instant.parse("2026-08-22T10:15:31Z");

    @Test
    void mapsTransactionCreatedWithAmountAndCurrency() {
        TransactionCreated event = TransactionCreated.newBuilder()
                .setEventId("evt-1")
                .setCorrelationId("corr-1")
                .setOccurredAt(OCCURRED)
                .setTransactionId("txn-1")
                .setAccountId("acc-1")
                .setCustomerId("cust-1")
                .setAmount(new BigDecimal("125.5000"))
                .setCurrency("USD")
                .setType("PURCHASE")
                .setCountryCode("US")
                .build();

        AuditEventDocument doc = AuditEventMapper.toDocument(event, INDEXED);

        assertThat(doc.eventType()).isEqualTo("TransactionCreated");
        assertThat(doc.eventId()).isEqualTo("evt-1");
        assertThat(doc.correlationId()).isEqualTo("corr-1");
        assertThat(doc.occurredAt()).isEqualTo(OCCURRED);
        assertThat(doc.indexedAt()).isEqualTo(INDEXED);
        assertThat(doc.transactionId()).isEqualTo("txn-1");
        assertThat(doc.accountId()).isEqualTo("acc-1");
        assertThat(doc.customerId()).isEqualTo("cust-1");
        assertThat(doc.amount()).isEqualTo(125.5);
        assertThat(doc.currency()).isEqualTo("USD");
        assertThat(doc.score()).isNull();
        assertThat(doc.alertId()).isNull();
        assertThat(doc.summary()).contains("PURCHASE").contains("USD");
    }

    @Test
    void mapsFraudDetectedWithScoreSeverityDecisionAndReason() {
        FraudDetected event = FraudDetected.newBuilder()
                .setEventId("evt-2")
                .setCorrelationId("corr-2")
                .setOccurredAt(OCCURRED)
                .setTransactionId("txn-2")
                .setCustomerId("cust-2")
                .setAccountId("acc-2")
                .setScore(88)
                .setSeverity("CRITICAL")
                .setDecision("BLOCK")
                .setPrimaryReason("Velocity: 6 txns in 60s")
                .setTriggeredRuleCodes(List.of("VELOCITY", "GEO_JUMP"))
                .setAmount(new BigDecimal("9000.0000"))
                .setCurrency("EUR")
                .setCountryCode("FR")
                .build();

        AuditEventDocument doc = AuditEventMapper.toDocument(event, INDEXED);

        assertThat(doc.eventType()).isEqualTo("FraudDetected");
        assertThat(doc.score()).isEqualTo(88);
        assertThat(doc.severity()).isEqualTo("CRITICAL");
        assertThat(doc.decision()).isEqualTo("BLOCK");
        assertThat(doc.reasonCode()).isEqualTo("Velocity: 6 txns in 60s");
        assertThat(doc.amount()).isEqualTo(9000.0);
        assertThat(doc.currency()).isEqualTo("EUR");
        assertThat(doc.summary()).contains("CRITICAL").contains("88");
    }

    @Test
    void mapsAlertResolvedWithNoAccountId() {
        AlertResolved event = AlertResolved.newBuilder()
                .setEventId("evt-3")
                .setCorrelationId("corr-3")
                .setOccurredAt(OCCURRED)
                .setAlertId("alert-3")
                .setTransactionId("txn-3")
                .setCustomerId("cust-3")
                .setResolution("FALSE_POSITIVE")
                .setResolvedBy("investigator-7")
                .build();

        AuditEventDocument doc = AuditEventMapper.toDocument(event, INDEXED);

        assertThat(doc.eventType()).isEqualTo("AlertResolved");
        assertThat(doc.alertId()).isEqualTo("alert-3");
        assertThat(doc.decision()).isEqualTo("FALSE_POSITIVE");
        assertThat(doc.reasonCode()).isEqualTo("FALSE_POSITIVE");
        assertThat(doc.accountId()).isNull();
        assertThat(doc.summary()).contains("FALSE_POSITIVE").contains("investigator-7");
    }

    @Test
    void rejectsUnknownEventType() {
        assertThatThrownBy(() -> AuditEventMapper.toDocument("not-an-event", INDEXED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported event type");
    }

    @Test
    void rejectsNullEvent() {
        assertThatThrownBy(() -> AuditEventMapper.toDocument(null, INDEXED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
