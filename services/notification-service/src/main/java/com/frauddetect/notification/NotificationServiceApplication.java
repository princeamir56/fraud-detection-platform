package com.frauddetect.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Notification service (Section 3). Consumes {@code alert.created} and performs mocked multi-channel
 * delivery (EMAIL always; SMS for the highest severity band), persisting a durable ledger row per
 * delivery attempt with its status. Correlation ids from the Avro envelope are restored into the MDC
 * so a notification can be traced back to the originating transaction.
 *
 * <p>JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate}) is enabled in
 * {@link com.frauddetect.notification.config.JpaAuditingConfig} rather than here, so web-only test
 * slices don't activate the auditing registrar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
