package com.frauddetect.customer.service;

/**
 * Raised when authentication fails (unknown username, disabled user, or wrong password). Mapped to
 * HTTP 401 by {@link com.frauddetect.customer.web.AuthExceptionHandler}. The message is deliberately
 * generic so the API never reveals which of username/password was wrong (Section 11).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
