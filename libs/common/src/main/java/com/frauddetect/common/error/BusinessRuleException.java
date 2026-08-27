package com.frauddetect.common.error;

/** Thrown when a business/domain rule is violated (e.g. insufficient funds). Mapped to HTTP 422. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
