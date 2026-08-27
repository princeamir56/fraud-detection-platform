package com.frauddetect.customer.dto;

import java.util.List;

/**
 * Result of a successful authentication (login or registration): the signed access token and enough
 * claims for the client to render context without decoding the JWT itself.
 */
public record AuthResponse(
        String token,
        String tokenType,
        String subject,
        List<String> roles,
        String customerId,
        long expiresInSeconds) {

    public static AuthResponse bearer(String token, String subject, List<String> roles,
                                      String customerId, long expiresInSeconds) {
        return new AuthResponse(token, "Bearer", subject, roles, customerId, expiresInSeconds);
    }
}
