package com.frauddetect.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the gRPC call into risk-scoring-service (Section 8 + 10).
 *
 * @param host        risk-scoring gRPC host
 * @param port        risk-scoring gRPC port
 * @param deadlineMs  per-call deadline; the hard time bound on the hot path
 * @param plaintext   use plaintext (dev/compose); TLS is terminated by the mesh/ingress in prod
 */
@ConfigurationProperties(prefix = "risk.grpc")
public record RiskScoringProperties(
        String host,
        int port,
        long deadlineMs,
        boolean plaintext
) {
    public RiskScoringProperties {
        if (host == null || host.isBlank()) {
            host = "localhost";
        }
        if (port <= 0) {
            port = 9095;
        }
        if (deadlineMs <= 0) {
            deadlineMs = 300;
        }
    }
}
