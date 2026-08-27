package com.frauddetect.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Delivery configuration for the (mocked) notification channels.
 *
 * @param fromAddress   the sender identity stamped on outbound EMAIL notifications
 * @param smsOnSeverity severities at or above which an SMS is additionally sent (default CRITICAL)
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        String fromAddress,
        String smsOnSeverity
) {
    public NotificationProperties {
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = "fraud-alerts@frauddetect.example";
        }
        if (smsOnSeverity == null || smsOnSeverity.isBlank()) {
            smsOnSeverity = "CRITICAL";
        }
    }
}
