package com.frauddetect.gateway.filter;

import com.frauddetect.common.constants.Headers;
import com.frauddetect.common.security.JwtService;
import com.frauddetect.gateway.config.GatewaySecurityProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Edge authentication. Validates the {@code Authorization: Bearer <jwt>} on every non-public request
 * using the shared {@link JwtService}. On success it forwards the authenticated identity downstream as
 * {@code X-User-Id} / {@code X-User-Roles} (which services trust for RBAC); on failure it returns
 * {@code 401} without proxying. Inbound identity headers are always stripped first, so a client can
 * never spoof them. Downstream services still re-validate the JWT independently (defence in depth).
 */
@Component
public class JwtAuthenticationWebFilter implements WebFilter, Ordered {

    private final JwtService jwtService;
    private final List<PathPattern> publicPatterns;

    public JwtAuthenticationWebFilter(JwtService jwtService, GatewaySecurityProperties securityProperties) {
        this.jwtService = jwtService;
        PathPatternParser parser = new PathPatternParser();
        this.publicPatterns = securityProperties.publicPaths().stream()
                .map(parser::parse)
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Never trust inbound identity headers — the gateway is the sole authority that sets them.
        ServerHttpRequest.Builder mutated = request.mutate()
                .headers(h -> {
                    h.remove(Headers.USER_ID);
                    h.remove(Headers.USER_ROLES);
                });

        if (isPublic(request.getPath().pathWithinApplication())) {
            return chain.filter(exchange.mutate().request(mutated.build()).build());
        }

        String token = bearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return unauthorized(exchange, "Missing bearer token");
        }
        if (!jwtService.isValid(token)) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        List<String> roles = jwtService.extractRoles(token);
        mutated.header(Headers.USER_ID, jwtService.extractSubject(token));
        if (!roles.isEmpty()) {
            mutated.header(Headers.USER_ROLES, String.join(",", roles));
        }
        return chain.filter(exchange.mutate().request(mutated.build()).build());
    }

    private boolean isPublic(PathContainer path) {
        return publicPatterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            String token = authorization.substring(prefix.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\""
                + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
