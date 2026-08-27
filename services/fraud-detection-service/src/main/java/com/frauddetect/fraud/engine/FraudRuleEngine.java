package com.frauddetect.fraud.engine;

import com.frauddetect.common.domain.FraudSeverity;
import com.frauddetect.fraud.domain.FraudRuleEntity;
import com.frauddetect.fraud.domain.RuleType;
import com.frauddetect.fraud.feature.TransactionFeatures;
import com.frauddetect.fraud.repository.FraudRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The configurable fraud rule engine (Section 9). It evaluates the enabled {@link FraudRuleEntity}
 * set against a transaction's {@link TransactionFeatures}, sums the awarded points into a 0-100
 * score, and maps that to a {@link FraudSeverity} band and a {@link Decision}.
 *
 * <p>Rules are cached in memory and refreshed on write ({@link #reload()}) so the hot path never
 * queries MySQL per transaction, yet operators see tuning changes take effect immediately.
 * Evaluators are injected by type, so adding a detection is additive and never touches the
 * aggregation logic here (open/closed).
 */
@Component
public class FraudRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleEngine.class);
    private static final int MAX_SCORE = 100;

    private final FraudRuleRepository ruleRepository;
    private final Map<RuleType, RuleEvaluator> evaluatorsByType = new EnumMap<>(RuleType.class);

    /** Immutable snapshot of enabled rules; replaced wholesale on reload for lock-free reads. */
    private volatile List<FraudRuleEntity> enabledRules = List.of();

    public FraudRuleEngine(FraudRuleRepository ruleRepository, List<RuleEvaluator> evaluators) {
        this.ruleRepository = ruleRepository;
        for (RuleEvaluator evaluator : evaluators) {
            RuleEvaluator previous = evaluatorsByType.put(evaluator.type(), evaluator);
            if (previous != null) {
                throw new IllegalStateException("Duplicate evaluator for rule type " + evaluator.type());
            }
        }
    }

    @PostConstruct
    public void reload() {
        List<FraudRuleEntity> loaded = ruleRepository.findByEnabledTrue();
        this.enabledRules = List.copyOf(loaded);
        log.info("Loaded {} enabled fraud rules into the engine", loaded.size());
    }

    /**
     * Scores a transaction. Each enabled rule with a matching evaluator is applied; awarded points
     * accumulate (capped at 100). Severity and decision are derived from the aggregate score.
     */
    public FraudScore evaluate(TransactionFeatures features, Double modelRiskScore) {
        List<RuleHit> hits = new ArrayList<>();
        int total = 0;

        for (FraudRuleEntity rule : enabledRules) {
            RuleEvaluator evaluator = evaluatorsByType.get(rule.getRuleType());
            if (evaluator == null) {
                log.warn("No evaluator registered for rule type {} (rule {}) - skipping",
                        rule.getRuleType(), rule.getCode());
                continue;
            }
            try {
                evaluator.evaluate(rule, features, modelRiskScore).ifPresent(hit -> {
                    hits.add(hit);
                });
            } catch (RuntimeException ex) {
                // A single misconfigured rule must never sink the whole analysis.
                log.error("Rule {} evaluation failed; skipping it", rule.getCode(), ex);
            }
        }

        for (RuleHit hit : hits) {
            total += hit.points();
        }
        int score = Math.max(0, Math.min(MAX_SCORE, total));

        hits.sort(Comparator.comparingInt(RuleHit::points).reversed());
        FraudSeverity severity = FraudSeverity.fromScore(score);
        Decision decision = decisionFor(severity);
        return new FraudScore(score, severity, decision, List.copyOf(hits), modelRiskScore);
    }

    /** LOW/MEDIUM allow, HIGH holds for review, CRITICAL blocks (Section 9). */
    private Decision decisionFor(FraudSeverity severity) {
        return switch (severity) {
            case LOW, MEDIUM -> Decision.ALLOW;
            case HIGH -> Decision.REVIEW;
            case CRITICAL -> Decision.BLOCK;
        };
    }

    /** Visible for testing/monitoring: how many rules are currently active. */
    public int activeRuleCount() {
        return enabledRules.size();
    }
}
