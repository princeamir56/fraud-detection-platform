package com.frauddetect.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gRPC server tuning bound from {@code grpc.server.*}.
 *
 * @param port                    listen port for the gRPC server
 * @param shutdownGraceSeconds    max seconds to await in-flight RPCs on shutdown
 * @param maxInboundMessageBytes  guard against oversized frames
 */
@ConfigurationProperties(prefix = "grpc.server")
public record GrpcServerProperties(
        int port,
        int shutdownGraceSeconds,
        int maxInboundMessageBytes) {

    public GrpcServerProperties {
        if (port <= 0) {
            port = 9095;
        }
        if (shutdownGraceSeconds <= 0) {
            shutdownGraceSeconds = 15;
        }
        if (maxInboundMessageBytes <= 0) {
            maxInboundMessageBytes = 4 * 1024 * 1024;
        }
    }
}
