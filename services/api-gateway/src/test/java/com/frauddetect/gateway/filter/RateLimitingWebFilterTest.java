package com.frauddetect.gateway.filter;

import com.frauddetect.gateway.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitingWebFilter}: requests within the bucket pass, the request that
 * exhausts it gets {@code 429} + {@code Retry-After}, buckets are isolated per client IP, and a
 * disabled limiter never blocks. A long refill period keeps refills out of the assertions.
 */
class RateLimitingWebFilterTest {

    private static MockServerWebExchange exchangeFrom(String ip) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/alerts")
                        .remoteAddress(new InetSocketAddress(ip, 40000))
                        .build());
    }

    @Test
    void allowsUpToCapacityThenBlocks() {
        RateLimitingWebFilter filter =
                new RateLimitingWebFilter(new RateLimitProperties(true, 2, 2, Duration.ofHours(1)));

        assertThat(passes(filter, "10.0.0.5")).isTrue();
        assertThat(passes(filter, "10.0.0.5")).isTrue();

        MockServerWebExchange blocked = exchangeFrom("10.0.0.5");
        CapturingChain chain = new CapturingChain();
        filter.filter(blocked, chain).block();

        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(chain.captured).isNull();
    }

    @Test
    void bucketsAreIsolatedPerClientIp() {
        RateLimitingWebFilter filter =
                new RateLimitingWebFilter(new RateLimitProperties(true, 1, 1, Duration.ofHours(1)));

        assertThat(passes(filter, "10.0.0.1")).isTrue();
        assertThat(passes(filter, "10.0.0.1")).isFalse();
        // A different client still has a full bucket.
        assertThat(passes(filter, "10.0.0.2")).isTrue();
    }

    @Test
    void disabledLimiterAlwaysPasses() {
        RateLimitingWebFilter filter =
                new RateLimitingWebFilter(new RateLimitProperties(false, 1, 1, Duration.ofHours(1)));

        for (int i = 0; i < 5; i++) {
            assertThat(passes(filter, "10.0.0.9")).isTrue();
        }
    }

    private static boolean passes(RateLimitingWebFilter filter, String ip) {
        MockServerWebExchange exchange = exchangeFrom(ip);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();
        return chain.captured != null && exchange.getResponse().getStatusCode() == null;
    }

    static final class CapturingChain implements WebFilterChain {
        ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }
    }
}
