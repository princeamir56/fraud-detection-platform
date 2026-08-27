package com.frauddetect.alert.domain;

/**
 * Terminal disposition recorded when an investigator resolves an alert. Mirrors the
 * {@code AlertResolved} Avro {@code resolution} field (string on the wire for loose coupling).
 */
public enum Resolution {
    /** Confirmed genuine fraud — the transaction/behaviour was malicious. */
    CONFIRMED_FRAUD,
    /** Legitimate activity the rules flagged in error. */
    FALSE_POSITIVE,
    /** Closed without a fraud/no-fraud determination (duplicate, insufficient evidence, etc.). */
    DISMISSED
}
