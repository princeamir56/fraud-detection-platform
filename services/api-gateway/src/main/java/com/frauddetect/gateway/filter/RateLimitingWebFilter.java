package com.frauddetect.gateway.filter;

import com.frauddetect.gateway.config.RateLimitProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process token-bucket rate limiter keyed by client IP. It runs before authentication so it also
 * shields the public {@code /api/v1/auth} endpoint from credential-stuffing. Over-limit requests get
 * {@code 429 Too Many Requests} with a {@code Retry-After} header and never reach a downstream service.
 *
 * <p>State lives in a per-instance {@link ConcurrentHashMap}, so this limiter is correct for a single
 * replica / local runs. For a horizontally-scaled deployment, swap in a distributed limiter (e.g. Spring
 * Cloud Gateway's Redis {@code RequestRateLimiter}) so the budget is shared across pods.
 */
@Component
public class RateLimitingWebFilter implements WebFilter, Ordered {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingWebFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }
        Bucket bucket = buckets.computeIfAbsent(clientKey(exchange), k -> new Bucket(properties.capacity()));
        if (bucket.tryConsume(properties)) {
            return chain.filter(exchange);
        }
        return tooManyRequests(exchange);
    }

    /** Prefer the first {@code X-Forwarded-For} hop (behind a load balancer), else the socket address. */
    private static String clientKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return (remote != null && remote.getAddress() != null)
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        long retryAfter = Math.max(1, properties.refillPeriod().toSeconds());
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        byte[] body = ("{\"status\":429,\"error\":\"Too Many Requests\","
                + "\"message\":\"Rate limit exceeded\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /** Synchronized token bucket; refills continuously toward {@code capacity}. */
    private static final class Bucket {
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume(RateLimitProperties p) {
            refill(p);
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return true;
            }
            return false;
        }

        private void refill(RateLimitProperties p) {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double periodNanos = (double) p.refillPeriod().toNanos();
            double refill = (elapsed / periodNanos) * p.refillTokens();
            if (refill >= 1.0d) {
                tokens = Math.min(capacity, tokens + refill);
                lastRefillNanos = now;
            }
        }
    }
}
