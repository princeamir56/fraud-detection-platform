package com.frauddetect.gateway.filter;

import com.frauddetect.common.constants.Headers;
import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtAuthenticationWebFilter}: public paths bypass auth, protected paths without
 * a valid bearer token are rejected with {@code 401}, a valid token forwards the authenticated identity
 * downstream, and any client-supplied identity headers are always stripped (anti-spoofing).
 */
class JwtAuthenticationWebFilterTest {

    private static final JwtService JWT = new JwtService(
            new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null));
    private static final JwtAuthenticationWebFilter FILTER = new JwtAuthenticationWebFilter(
            JWT, new GatewaySecurityProperties(List.of("/api/v1/auth/**", "/actuator/**")));

    @Test
    void publicPathBypassesAuthentication() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").build());
        CapturingChain chain = new CapturingChain();

        FILTER.filter(exchange, chain).block();

        assertThat(chain.captured).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(chain.captured.getRequest().getHeaders().getFirst(Headers.USER_ID)).isNull();
    }

    @Test
    void protectedPathWithoutTokenIsUnauthorized() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/alerts").build());
        CapturingChain chain = new CapturingChain();

        FILTER.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chain.captured).isNull();
    }

    @Test
    void protectedPathWithInvalidTokenIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                        .build());
        CapturingChain chain = new CapturingChain();

        FILTER.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chain.captured).isNull();
    }

    @Test
    void validTokenForwardsIdentityDownstream() {
        String token = JWT.generateToken("analyst-jane", List.of("ANALYST", "INVESTIGATOR"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        CapturingChain chain = new CapturingChain();

        FILTER.filter(exchange, chain).block();

        assertThat(chain.captured).isNotNull();
        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst(Headers.USER_ID)).isEqualTo("analyst-jane");
        assertThat(forwarded.getFirst(Headers.USER_ROLES)).isEqualTo("ANALYST,INVESTIGATOR");
    }

    @Test
    void stripsSpoofedIdentityHeadersAndReplacesWithVerifiedIdentity() {
        String token = JWT.generateToken("analyst-jane", List.of("ANALYST"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(Headers.USER_ID, "attacker")
                        .header(Headers.USER_ROLES, "ADMIN")
                        .build());
        CapturingChain chain = new CapturingChain();

        FILTER.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst(Headers.USER_ID)).isEqualTo("analyst-jane");
        assertThat(forwarded.get(Headers.USER_ID)).containsExactly("analyst-jane");
        assertThat(forwarded.getFirst(Headers.USER_ROLES)).isEqualTo("ANALYST");
    }

    @Test
    void stripsSpoofedIdentityHeadersOnPublicPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header(Headers.USER_ID, "attacker")
                        .header(Headers.USER_ROLES, "ADMIN")
                        .build());
        CapturingChain chain = new CapturingChain();

        FILTER.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst(Headers.USER_ID)).isNull();
        assertThat(forwarded.getFirst(Headers.USER_ROLES)).isNull();
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
