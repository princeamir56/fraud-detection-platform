package com.frauddetect.gateway.filter;

import com.frauddetect.common.constants.Headers;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ensures every request entering the platform carries a stable {@code X-Correlation-Id}. A
 * client-supplied id is preserved (so client-side traces stitch together); otherwise a fresh UUID is
 * minted. The id is echoed on the response and forwarded on the proxied request, giving downstream
 * services — which lift it into their logging MDC — one shared correlation id per request.
 *
 * <p>Runs at highest precedence so the id exists for every later filter, including the rate-limit and
 * auth short-circuits that never reach a downstream service.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(Headers.CORRELATION_ID);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(Headers.CORRELATION_ID, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(Headers.CORRELATION_ID, correlationId);
        // Downstream services echo the header too and the proxy merges it in; re-set just before commit
        // so the client receives exactly one value instead of "id, id".
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(Headers.CORRELATION_ID, correlationId);
            return Mono.empty();
        });

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
