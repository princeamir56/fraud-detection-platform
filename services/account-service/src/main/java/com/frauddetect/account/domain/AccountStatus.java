package com.frauddetect.account.domain;

/**
 * Account lifecycle state. {@code FROZEN} is the fraud-response state: balance operations are
 * rejected while an investigation is open, but the account is not closed.
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
