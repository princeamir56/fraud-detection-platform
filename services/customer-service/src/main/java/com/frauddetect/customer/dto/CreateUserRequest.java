package com.frauddetect.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Administrative creation of a staff user (ADMIN/ANALYST/INVESTIGATOR). Restricted to ADMIN callers.
 * Roles are validated against the platform's known set in the service layer.
 */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username may contain letters, digits, dot, underscore, hyphen")
        String username,

        @NotBlank @Size(min = 8, max = 72)
        String password,

        @NotEmpty(message = "at least one role is required")
        List<@NotBlank String> roles) {
}
