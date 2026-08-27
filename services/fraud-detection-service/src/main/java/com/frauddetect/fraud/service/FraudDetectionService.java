package com.frauddetect.fraud.service;

import com.frauddetect.fraud.client.RiskScoringClient;
import com.frauddetect.fraud.engine.FraudRuleEngine;
import com.frauddetect.fraud.engine.FraudScore;
import com.frauddetect.fraud.feature.TransactionFeatures;
import com.frauddetect.fraud.messaging.EventIds;
import com.frauddetect.fraud.messaging.FraudEventProducer;
import com.frauddetect.fraud.search.FraudSearchService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the fraud analysis of a single transaction (the "brain", Sections 9 + 18). Sequence:
 * <ol>
 *   <li>derive behavioural + velocity {@link TransactionFeatures} from Cassandra;</li>
 *   <li>obtain the model risk sub-score synchronously over gRPC (resilient, degrades to {@code null});</li>
 *   <li>run the configurable rule engine to produce the 0-100 score, severity and decision;</li>
 *   <li>persist the outcome to Cassandra and index it into Elasticsearch;</li>
 *   <li>publish {@code fraud.score.calculated} always, and {@code fraud.detected} for HIGH/CRITICAL.</li>
 * </ol>
 *
 * <p>Effects run before the offset is committed and outbound event ids are deterministic
 * ({@link EventIds}), so at-least-once redelivery is safe: Cassandra/Elasticsearch writes are
 * idempotent by key and re-emitted events dedupe downstream. A {@code fraud.analysis} timer captures
 * end-to-end hot-path latency (Section 12).
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final BehaviorService behaviorService;
    private final RiskScoringClient riskScoringClient;
    private final FraudRuleEngine engine;
    private final FraudSearchService searchService;
    private final FraudEventProducer producer;
    private final Timer analysisTimer;
    private final Counter detectionCounter;

    public FraudDetectionService(BehaviorService behaviorService,
                                 RiskScoringClient riskScoringClient,
                                 FraudRuleEngine engine,
                                 FraudSearchService searchService,
                                 FraudEventProducer producer,
                                 MeterRegistry meterRegistry) {
        this.behaviorService = behaviorService;
        this.riskScoringClient = riskScoringClient;
        this.engine = engine;
        this.searchService = searchService;
        this.producer = producer;
        this.analysisTimer = Timer.builder("fraud.analysis")
                .description("End-to-end fraud analysis latency for one transaction")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        this.detectionCounter = Counter.builder("fraud.detections")
                .description("Transactions scored in the HIGH/CRITICAL band")
                .register(meterRegistry);
    }

    /**
     * Analyses one transaction end to end and emits the resulting events.
     *
     * @param ctx            the transaction under analysis
     * @param inboundEventId the consumed event id, used to derive deterministic outbound ids
     * @return the computed {@link FraudScore} (also useful for tests and structured logging)
     */
    public FraudScore analyze(TransactionContext ctx, String inboundEventId) {
        Timer.Sample sample = Timer.start();
        try {
            TransactionFeatures features = behaviorService.buildFeatures(ctx);
            Double modelRiskScore = riskScoringClient.score(ctx, features).orElse(null);
            FraudScore score = engine.evaluate(features, modelRiskScore);

            behaviorService.recordOutcome(ctx, features, score);
            searchService.indexTransaction(ctx, features, score);

            producer.publishScore(ctx, score, EventIds.derive(inboundEventId, "score"));
            if (score.isFraudulent()) {
                detectionCounter.increment();
                String detectedEventId = EventIds.derive(inboundEventId, "detected");
                producer.publishDetected(ctx, score, detectedEventId);
                searchService.indexFraudEvent(detectedEventId, ctx, score);
            }

            log.info("Analysed tx={} customer={} score={} severity={} decision={} model={} rules={}",
                    ctx.transactionId(), ctx.customerId(), score.score(), score.severity(),
                    score.decision(), modelRiskScore, score.triggeredRuleCodes());
            return score;
        } finally {
            sample.stop(analysisTimer);
        }
    }
}
