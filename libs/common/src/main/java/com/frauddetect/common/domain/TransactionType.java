package com.frauddetect.common.domain;

/** Category of a monetary movement. Used for both persistence and fraud features. */
public enum TransactionType {
    PURCHASE,
    WITHDRAWAL,
    TRANSFER,
    DEPOSIT,
    PAYMENT,
    REFUND
}
