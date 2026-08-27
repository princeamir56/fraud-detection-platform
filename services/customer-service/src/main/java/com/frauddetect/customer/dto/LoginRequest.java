package com.frauddetect.customer.dto;

import jakarta.validation.constraints.NotBlank;

/** Credentials presented at {@code POST /api/v1/auth/login}. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
