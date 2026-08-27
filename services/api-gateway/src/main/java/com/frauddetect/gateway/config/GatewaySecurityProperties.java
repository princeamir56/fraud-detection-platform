package com.frauddetect.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Edge authorization policy. {@code publicPaths} are Ant-style patterns reachable without a JWT
 * (token issuance and the actuator probes); every other path requires a valid bearer token.
 */
@ConfigurationProperties("gateway.security")
public record GatewaySecurityProperties(List<String> publicPaths) {

    public GatewaySecurityProperties {
        publicPaths = (publicPaths == null || publicPaths.isEmpty())
                ? List.of("/api/v1/auth/**", "/actuator/**")
                : List.copyOf(publicPaths);
    }
}
