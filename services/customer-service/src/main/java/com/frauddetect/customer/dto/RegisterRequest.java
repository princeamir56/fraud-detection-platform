package com.frauddetect.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service customer registration. Creates a {@code CUSTOMER} user plus a linked profile and
 * returns a signed JWT (auto-login). Password is bounded to 72 bytes — BCrypt's input limit.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username may contain letters, digits, dot, underscore, hyphen")
        String username,

        @NotBlank @Size(min = 8, max = 72)
        String password,

        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,

        @NotBlank @Email @Size(max = 255) String email,

        @Size(max = 32) String phone,

        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode must be a 2-letter ISO code")
        String countryCode) {
}
