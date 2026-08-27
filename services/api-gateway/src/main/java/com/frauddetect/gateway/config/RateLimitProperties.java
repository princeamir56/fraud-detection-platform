package com.frauddetect.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Token-bucket rate-limit settings bound from {@code gateway.rate-limit.*}. A bucket starts full at
 * {@code capacity} and refills {@code refillTokens} tokens every {@code refillPeriod}. The compact
 * constructor applies safe defaults so misconfiguration never yields a zero/negative bucket.
 */
@ConfigurationProperties("gateway.rate-limit")
public record RateLimitProperties(
        Boolean enabled,
        Integer capacity,
        Integer refillTokens,
        Duration refillPeriod) {

    public RateLimitProperties {
        enabled = (enabled == null) ? Boolean.TRUE : enabled;
        capacity = (capacity == null || capacity <= 0) ? 100 : capacity;
        refillTokens = (refillTokens == null || refillTokens <= 0) ? capacity : refillTokens;
        refillPeriod = (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative())
                ? Duration.ofMinutes(1)
                : refillPeriod;
    }
}
