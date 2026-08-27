package com.frauddetect.alert.dto;

import com.frauddetect.alert.domain.AlertEntity;

import java.time.Instant;

/**
 * API projection of an alert. Records are immutable and validation-friendly; {@link #from(AlertEntity)}
 * maps the persistent entity to the wire shape so the entity never leaks out of the service layer.
 */
public record AlertResponse(
        String id,
        String transactionId,
        String customerId,
        String accountId,
        String severity,
        int score,
        String status,
        String title,
        String description,
        String primaryReason,
        String assignedTo,
        String resolution,
        String resolvedBy,
        String resolutionNotes,
        Instant resolvedAt,
        String correlationId,
        Instant createdAt,
        Instant updatedAt) {

    public static AlertResponse from(AlertEntity a) {
        return new AlertResponse(
                a.getId(),
                a.getTransactionId(),
                a.getCustomerId(),
                a.getAccountId(),
                a.getSeverity(),
                a.getScore(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getTitle(),
                a.getDescription(),
                a.getPrimaryReason(),
                a.getAssignedTo(),
                a.getResolution() != null ? a.getResolution().name() : null,
                a.getResolvedBy(),
                a.getResolutionNotes(),
                a.getResolvedAt(),
                a.getCorrelationId(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
