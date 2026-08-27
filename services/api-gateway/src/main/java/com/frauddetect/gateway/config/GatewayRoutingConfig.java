package com.frauddetect.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route table (Spring Cloud Gateway 5.x Fluent Java Routes API). Routes are defined in
 * code rather than YAML so the mapping is compiler-checked and version-stable across the 2025.x
 * property-prefix changes. Each route maps a public path prefix to a downstream service base URI
 * (resolved from {@link GatewayRoutesProperties}).
 *
 * <p>Note: risk-scoring-service is intentionally absent — it exposes only gRPC internally and has no
 * public REST surface, so the gateway does not route to it.
 */
@Configuration
public class GatewayRoutingConfig {

    @Bean
    public RouteLocator platformRoutes(RouteLocatorBuilder builder, GatewayRoutesProperties routes) {
        return builder.routes()
                // customer-service owns auth (token issuance), user administration, and customer profiles.
                .route("customer-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/customers/**")
                        .uri(routes.customerUri()))
                .route("account-service", r -> r
                        .path("/api/v1/accounts/**")
                        .uri(routes.accountUri()))
                .route("transaction-service", r -> r
                        .path("/api/v1/transactions/**")
                        .uri(routes.transactionUri()))
                // fraud-detection-service exposes both the rule admin API and the alert/tx search API.
                .route("fraud-detection-service", r -> r
                        .path("/api/v1/fraud-rules/**", "/api/v1/search/**")
                        .uri(routes.fraudUri()))
                .route("alert-service", r -> r
                        .path("/api/v1/alerts/**")
                        .uri(routes.alertUri()))
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .uri(routes.notificationUri()))
                .route("audit-service", r -> r
                        .path("/api/v1/audit/**")
                        .uri(routes.auditUri()))
                .build();
    }
}
