package com.frauddetect.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Alert / case-management service (Section 3). Consumes {@code fraud.detected}, opens an alert in MySQL
 * and publishes {@code alert.created}; exposes a triage REST API for investigators to acknowledge and
 * resolve alerts, publishing {@code alert.resolved} on closure; and indexes alerts into Elasticsearch
 * for analyst search and Kibana dashboards.
 *
 * <p>JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate}) on the alert entity is enabled in
 * {@link com.frauddetect.alert.config.JpaAuditingConfig} rather than here, so web-only test slices
 * don't activate the auditing registrar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}
