package com.frauddetect.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues and validates HS256 JWTs (jjwt 0.12.x API). Roles are carried in a {@code roles} claim
 * and turned into Spring Security authorities by each service's security layer.
 * <p>
 * The signing key is derived from {@link JwtProperties#secret()}, which is sourced from the
 * environment / a K8s Secret — never a literal in code (Section 11).
 */
public class JwtService {

    public static final String ROLES_CLAIM = "roles";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least 32 bytes for HS256; provide it via env/secret.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /** Mint a signed access token for a subject with the given roles. */
    public String generateToken(String subject, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(ROLES_CLAIM, roles)
                .signWith(key)
                .compact();
    }

    /** Parse and cryptographically verify a token, returning its claims. Throws {@link io.jsonwebtoken.JwtException} if invalid. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clockSkewSeconds(properties.clockSkew().toSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public String extractSubject(String token) {
        return parse(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parse(token).get(ROLES_CLAIM);
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }
}
