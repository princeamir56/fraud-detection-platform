package com.frauddetect.alert.dto;

import com.frauddetect.alert.domain.Resolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/alerts/{id}/resolve}. The investigator is taken from the authenticated
 * principal, not the request body, so it cannot be spoofed. {@code notes} is optional free text.
 */
public record ResolveAlertRequest(
        @NotNull(message = "resolution is required")
        Resolution resolution,

        @Size(max = 2000, message = "notes must be at most 2000 characters")
        String notes) {
}
