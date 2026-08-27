package com.frauddetect.fraud.engine;

/**
 * The action the platform takes for a scored transaction. Mapped from severity by the aggregator
 * (Section 9): LOW/MEDIUM → ALLOW, HIGH → REVIEW (hold for an analyst), CRITICAL → BLOCK.
 * Mirrors the {@code decision} field of the {@code FraudScoreCalculated}/{@code FraudDetected} events.
 */
public enum Decision {
    ALLOW,
    REVIEW,
    BLOCK
}
