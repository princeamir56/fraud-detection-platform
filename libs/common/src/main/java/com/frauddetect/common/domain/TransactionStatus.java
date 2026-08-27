package com.frauddetect.common.domain;

/**
 * Lifecycle of a transaction as it flows through validation and fraud analysis.
 * <p>
 * {@code PENDING -> VALIDATED -> (COMPLETED | REJECTED | FLAGGED)} with
 * {@code FLAGGED -> UNDER_REVIEW -> (COMPLETED | BLOCKED)} on the investigation path.
 */
public enum TransactionStatus {
    /** Persisted, not yet validated. */
    PENDING,
    /** Passed structural/business validation, awaiting async fraud verdict. */
    VALIDATED,
    /** Cleared fraud analysis. */
    COMPLETED,
    /** Rejected during validation or by a blocking fraud verdict. */
    REJECTED,
    /** Fraud score in HIGH/CRITICAL band; held for review, alert raised. */
    FLAGGED,
    /** An investigator has opened a case against this transaction. */
    UNDER_REVIEW,
    /** Confirmed fraudulent / administratively blocked. */
    BLOCKED
}
