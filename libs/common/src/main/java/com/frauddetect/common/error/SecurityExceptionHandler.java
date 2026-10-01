package com.frauddetect.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Authenticated-but-unauthorized access. Spring Security's method security
 * ({@code @PreAuthorize}) throws {@code AuthorizationDeniedException} (a subclass of
 * {@link AccessDeniedException}) from inside the controller invocation, so it surfaces here
 * rather than at {@code ExceptionTranslationFilter}. Map it to 403 explicitly — otherwise the
 * catch-all in {@link GlobalExceptionHandler} would turn a legitimate authorization denial into a
 * misleading 500. (Anonymous requests are rejected earlier by the authentication entry point as 401.)
 * <p>
 * Kept separate from {@link GlobalExceptionHandler} because Spring Security is optional in this
 * library: services without it (e.g. risk-scoring-service) would otherwise fail at startup with a
 * {@code NoClassDefFoundError} while introspecting the handler. Highest precedence so it is
 * consulted before the global catch-all.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return GlobalExceptionHandler.build(HttpStatus.FORBIDDEN, "Access is denied", req);
    }
}
