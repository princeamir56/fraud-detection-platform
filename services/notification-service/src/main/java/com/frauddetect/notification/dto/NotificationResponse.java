package com.frauddetect.notification.dto;

import com.frauddetect.notification.domain.NotificationChannel;
import com.frauddetect.notification.domain.NotificationEntity;
import com.frauddetect.notification.domain.NotificationStatus;

import java.time.Instant;

/** Read-model view of a ledger row. */
public record NotificationResponse(
        String id,
        String alertId,
        String transactionId,
        String customerId,
        NotificationChannel channel,
        String recipient,
        String subject,
        String severity,
        NotificationStatus status,
        Instant createdAt
) {
    public static NotificationResponse from(NotificationEntity e) {
        return new NotificationResponse(
                e.getId(), e.getAlertId(), e.getTransactionId(), e.getCustomerId(),
                e.getChannel(), e.getRecipient(), e.getSubject(), e.getSeverity(),
                e.getStatus(), e.getCreatedAt());
    }
}
