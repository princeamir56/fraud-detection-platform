package com.frauddetect.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Downstream service base URIs used by {@link GatewayRoutingConfig} to build the reactive routes.
 * Values come from {@code gateway.routes.*} (localhost defaults for local runs; service hostnames in
 * the {@code docker} profile). The compact constructor re-applies the localhost defaults so the app
 * still starts if a property is blank/absent.
 */
@ConfigurationProperties("gateway.routes")
public record GatewayRoutesProperties(
        String customerUri,
        String accountUri,
        String transactionUri,
        String fraudUri,
        String alertUri,
        String notificationUri,
        String auditUri) {

    public GatewayRoutesProperties {
        customerUri = orDefault(customerUri, "http://localhost:8081");
        accountUri = orDefault(accountUri, "http://localhost:8082");
        transactionUri = orDefault(transactionUri, "http://localhost:8083");
        fraudUri = orDefault(fraudUri, "http://localhost:8084");
        alertUri = orDefault(alertUri, "http://localhost:8086");
        notificationUri = orDefault(notificationUri, "http://localhost:8087");
        auditUri = orDefault(auditUri, "http://localhost:8088");
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
