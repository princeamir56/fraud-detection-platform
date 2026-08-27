package com.frauddetect.risk.scoring;

import com.frauddetect.grpc.risk.TransactionRiskFeatures;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent, explainable risk model. Each behavioural feature contributes a bounded weight to a
 * logit; the logit is squashed to a 0-1 probability via the logistic function. Alongside the score
 * we return the human-readable factors that pushed it up, so an analyst (or an interviewer) can see
 * exactly <em>why</em> a transaction was scored the way it was.
 *
 * <p>This is intentionally a hand-tuned heuristic rather than an opaque ML blob: it is deterministic,
 * unit-testable, and easy to reason about. Swapping in a real model means replacing {@link #score}.
 */
@Component
public class RiskModel {

    public static final int MODEL_VERSION = 1;

    /** Physically implausible ground speed between two transactions (km/h) => likely account takeover. */
    private static final double IMPOSSIBLE_SPEED_KMH = 900.0;

    public record RiskAssessment(double score, String band, List<String> factors, int modelVersion) {
    }

    public RiskAssessment score(TransactionRiskFeatures f) {
        double logit = -2.2; // base rate: most transactions are legitimate
        List<String> factors = new ArrayList<>();

        // Velocity: many transactions in a short window.
        if (f.getTxCountLastHour() >= 10) {
            logit += 1.4;
            factors.add("High velocity: %d transactions in last hour".formatted(f.getTxCountLastHour()));
        } else if (f.getTxCountLastHour() >= 5) {
            logit += 0.7;
            factors.add("Elevated velocity: %d transactions in last hour".formatted(f.getTxCountLastHour()));
        }

        // Spending spike vs. 30-day average.
        double avg = f.getAvgAmountLast30D();
        if (avg > 0 && f.getAmount() > avg * 8) {
            logit += 1.3;
            factors.add("Amount %.2f is %.1fx the 30-day average".formatted(f.getAmount(), f.getAmount() / avg));
        } else if (avg > 0 && f.getAmount() > avg * 4) {
            logit += 0.6;
            factors.add("Amount %.2f well above 30-day average".formatted(f.getAmount()));
        }

        // Geo dispersion.
        if (f.getDistinctCountriesLast24H() >= 3) {
            logit += 1.1;
            factors.add("%d distinct countries in last 24h".formatted(f.getDistinctCountriesLast24H()));
        }

        // Impossible travel.
        if (f.getSecondsSinceLastTx() > 0 && f.getKmFromLastTx() > 0) {
            double hours = f.getSecondsSinceLastTx() / 3600.0;
            double speed = f.getKmFromLastTx() / Math.max(hours, 1e-6);
            if (speed > IMPOSSIBLE_SPEED_KMH) {
                logit += 1.8;
                factors.add("Impossible travel: %.0f km in %ds (%.0f km/h)"
                        .formatted(f.getKmFromLastTx(), f.getSecondsSinceLastTx(), speed));
            }
        }

        // Device / merchant novelty.
        if (f.getNewDevice()) {
            logit += 0.5;
            factors.add("First-seen device");
        }
        if (f.getNewMerchant()) {
            logit += 0.3;
            factors.add("First-seen merchant");
        }

        // Failed attempts (card testing).
        if (f.getFailedTxLastHour() >= 3) {
            logit += 0.9;
            factors.add("%d failed transactions in last hour".formatted(f.getFailedTxLastHour()));
        }

        // Cash-out risk on large withdrawals.
        if ("WITHDRAWAL".equalsIgnoreCase(f.getTransactionType()) && f.getAmount() >= 2000) {
            logit += 0.6;
            factors.add("Large cash withdrawal");
        }

        double probability = sigmoid(logit);
        return new RiskAssessment(round(probability), band(probability), List.copyOf(factors), MODEL_VERSION);
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    private static String band(double p) {
        if (p < 0.30) return "LOW";
        if (p < 0.60) return "MEDIUM";
        if (p < 0.80) return "HIGH";
        return "CRITICAL";
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
