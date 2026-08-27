package com.frauddetect.common.domain;

/**
 * Fraud severity bands derived from a 0-100 risk score (Section 9 scoring policy).
 * <pre>
 *   0-29  LOW
 *  30-59  MEDIUM
 *  60-79  HIGH
 *  80-100 CRITICAL
 * </pre>
 * Centralised here so every service (fraud-detection, risk-scoring, alert, audit)
 * maps scores to severity identically.
 */
public enum FraudSeverity {

    LOW(0, 29),
    MEDIUM(30, 59),
    HIGH(60, 79),
    CRITICAL(80, 100);

    private final int minInclusive;
    private final int maxInclusive;

    FraudSeverity(int minInclusive, int maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public int minInclusive() {
        return minInclusive;
    }

    public int maxInclusive() {
        return maxInclusive;
    }

    public boolean contains(int score) {
        return score >= minInclusive && score <= maxInclusive;
    }

    /**
     * Maps a raw score to a severity band. Scores are clamped to [0,100] so callers
     * never have to defensively bound-check upstream model output.
     */
    public static FraudSeverity fromScore(int score) {
        int clamped = Math.max(0, Math.min(100, score));
        for (FraudSeverity severity : values()) {
            if (severity.contains(clamped)) {
                return severity;
            }
        }
        return CRITICAL; // unreachable given the contiguous bands, kept for exhaustiveness
    }

    /** CRITICAL and HIGH block/hold the transaction; lower bands allow it through. */
    public boolean requiresBlocking() {
        return this == HIGH || this == CRITICAL;
    }
}
