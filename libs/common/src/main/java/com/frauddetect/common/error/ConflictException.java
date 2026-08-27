package com.frauddetect.common.error;

/** Thrown on state conflicts such as duplicate keys or idempotency clashes. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
