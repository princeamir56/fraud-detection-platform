package com.frauddetect.customer.web;

import com.frauddetect.common.correlation.CorrelationContext;
import com.frauddetect.common.error.ApiError;
import com.frauddetect.customer.service.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps authentication failures to HTTP 401. This lives alongside the common
 * {@code GlobalExceptionHandler}; a more specific {@code @ExceptionHandler} here takes precedence
 * over the common catch-all, so a failed login returns 401 rather than 500.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                             HttpServletRequest req) {
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(), ex.getMessage(),
                req.getRequestURI(), CorrelationContext.getCorrelationId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
