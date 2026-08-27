package com.frauddetect.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT configuration bound from {@code security.jwt.*}. The signing secret is injected from the
 * environment / K8s Secret and is never hard-coded (Section 11). HS256 is used for the demo;
 * swap {@code algorithm}/keys for RS256 in production by supplying a key pair.
 *
 * @param secret          base64 or raw HMAC secret (>= 32 bytes for HS256); from env var
 * @param issuer          expected {@code iss} claim
 * @param accessTokenTtl  access-token lifetime
 * @param clockSkew       allowed clock skew when validating exp/nbf
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration clockSkew) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "fraud-detection-platform";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofHours(1);
        }
        if (clockSkew == null) {
            clockSkew = Duration.ofSeconds(30);
        }
    }
}
