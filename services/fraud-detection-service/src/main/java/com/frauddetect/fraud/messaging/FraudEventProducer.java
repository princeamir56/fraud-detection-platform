package com.frauddetect.fraud.messaging;

import com.frauddetect.avro.events.FraudDetected;
import com.frauddetect.avro.events.FraudScoreCalculated;
import com.frauddetect.avro.events.TriggeredRule;
import com.frauddetect.common.constants.Headers;
import com.frauddetect.common.constants.KafkaTopics;
import com.frauddetect.fraud.engine.FraudScore;
import com.frauddetect.fraud.engine.RuleHit;
import com.frauddetect.fraud.service.TransactionContext;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Publishes the fraud analysis results (Section 7). {@code fraud.score.calculated} is emitted for
 * every scored transaction; {@code fraud.detected} is emitted only for the HIGH/CRITICAL band that
 * drives alerting. Both are keyed by {@code customerId} so a customer's events stay ordered on one
 * partition, and carry the end-to-end {@code X-Correlation-Id} header.
 *
 * <p>Outbound {@code eventId}s are derived deterministically ({@link EventIds}) by the orchestrator
 * and passed in, so a re-emitted event on reprocessing carries the SAME id (and the same
 * Elasticsearch fraud-event doc id) — turning an at-least-once pipeline into effectively-once end to
 * end.
 */
@Component
public class FraudEventProducer {

    private static final Logger log = LoggerFactory.getLogger(FraudEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishScore(TransactionContext ctx, FraudScore score, String eventId) {
        List<TriggeredRule> rules = score.hits().stream()
                .map(FraudEventProducer::toAvroRule)
                .toList();
        FraudScoreCalculated event = FraudScoreCalculated.newBuilder()
                .setEventId(eventId)
                .setCorrelationId(ctx.correlationId())
                .setOccurredAt(Instant.now())
                .setTransactionId(ctx.transactionId())
                .setCustomerId(ctx.customerId())
                .setAccountId(ctx.accountId())
                .setScore(score.score())
                .setSeverity(score.severity().name())
                .setDecision(score.decision().name())
                .setModelRiskScore(score.modelRiskScore())
                .setTriggeredRules(rules)
                .build();
        send(KafkaTopics.FRAUD_SCORE_CALCULATED, ctx.customerId(), event, ctx.correlationId());
    }

    public void publishDetected(TransactionContext ctx, FraudScore score, String eventId) {
        FraudDetected event = FraudDetected.newBuilder()
                .setEventId(eventId)
                .setCorrelationId(ctx.correlationId())
                .setOccurredAt(Instant.now())
                .setTransactionId(ctx.transactionId())
                .setCustomerId(ctx.customerId())
                .setAccountId(ctx.accountId())
                .setScore(score.score())
                .setSeverity(score.severity().name())
                .setDecision(score.decision().name())
                .setPrimaryReason(score.primaryReason())
                .setTriggeredRuleCodes(score.triggeredRuleCodes())
                .setAmount(ctx.amount())
                .setCurrency(ctx.currency())
                .setCountryCode(ctx.countryCode())
                .build();
        send(KafkaTopics.FRAUD_DETECTED, ctx.customerId(), event, ctx.correlationId());
    }

    private static TriggeredRule toAvroRule(RuleHit hit) {
        return TriggeredRule.newBuilder()
                .setRuleCode(hit.ruleCode())
                .setDescription(hit.description())
                .setWeight(hit.weight())
                .setScore(hit.points())
                .build();
    }

    private void send(String topic, String key, SpecificRecord value, String correlationId) {
        var record = new ProducerRecord<String, Object>(topic, null, key, value);
        if (correlationId != null) {
            record.headers().add(new RecordHeader(Headers.CORRELATION_ID,
                    correlationId.getBytes(StandardCharsets.UTF_8)));
        }
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} for key={} corr={}: {}", topic, key, correlationId, ex.toString());
            } else {
                log.debug("Published {} key={} partition={} offset={}", topic, key,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
