package com.frauddetect.audit.search;

import java.time.Instant;

/**
 * One immutable row of the audit trail as projected into the {@code audit-events} index (Section 3 +
 * Section 11). Every domain event on the bus maps to exactly one of these, keyed by the Avro
 * {@code eventId} so redeliveries overwrite rather than duplicate.
 *
 * <p>The shape is a superset across all eight event types: fields that a given event does not carry
 * are left {@code null}. {@code eventType} names the source Avro record (e.g. {@code TransactionCreated});
 * {@code summary} is a short human-readable line for quick scanning in Kibana; {@code indexedAt} records
 * when audit-service wrote the row (distinct from the business {@code occurredAt}).
 */
public record AuditEventDocument(
        String eventId,
        String eventType,
        String correlationId,
        Instant occurredAt,
        Instant indexedAt,
        String transactionId,
        String accountId,
        String customerId,
        String alertId,
        Double amount,
        String currency,
        Integer score,
        String severity,
        String decision,
        String reasonCode,
        String summary
) {

    /** Starts a document with the four fields every domain event carries. */
    public static Builder builder(String eventType, String eventId, String correlationId, Instant occurredAt) {
        return new Builder(eventType, eventId, correlationId, occurredAt);
    }

    /** Fluent builder — keeps {@code AuditEventMapper}'s per-type cases compact and readable. */
    public static final class Builder {
        private final String eventType;
        private final String eventId;
        private final String correlationId;
        private final Instant occurredAt;
        private Instant indexedAt;
        private String transactionId;
        private String accountId;
        private String customerId;
        private String alertId;
        private Double amount;
        private String currency;
        private Integer score;
        private String severity;
        private String decision;
        private String reasonCode;
        private String summary;

        private Builder(String eventType, String eventId, String correlationId, Instant occurredAt) {
            this.eventType = eventType;
            this.eventId = eventId;
            this.correlationId = correlationId;
            this.occurredAt = occurredAt;
        }

        public Builder indexedAt(Instant v) { this.indexedAt = v; return this; }
        public Builder transactionId(String v) { this.transactionId = v; return this; }
        public Builder accountId(String v) { this.accountId = v; return this; }
        public Builder customerId(String v) { this.customerId = v; return this; }
        public Builder alertId(String v) { this.alertId = v; return this; }
        public Builder amount(Double v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder score(Integer v) { this.score = v; return this; }
        public Builder severity(String v) { this.severity = v; return this; }
        public Builder decision(String v) { this.decision = v; return this; }
        public Builder reasonCode(String v) { this.reasonCode = v; return this; }
        public Builder summary(String v) { this.summary = v; return this; }

        public AuditEventDocument build() {
            return new AuditEventDocument(eventId, eventType, correlationId, occurredAt, indexedAt,
                    transactionId, accountId, customerId, alertId, amount, currency, score, severity,
                    decision, reasonCode, summary);
        }
    }
}
