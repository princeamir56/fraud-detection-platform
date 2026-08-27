package com.frauddetect.gateway.filter;

import com.frauddetect.common.constants.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorrelationIdWebFilter}: a fresh id is minted when the client sends none, an
 * inbound id is preserved, and in both cases the same id is echoed on the response and forwarded on
 * the (mutated) proxied request.
 */
class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void generatesCorrelationIdWhenAbsent() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/alerts").build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        String forwarded = chain.captured.getRequest().getHeaders().getFirst(Headers.CORRELATION_ID);
        String echoed = exchange.getResponse().getHeaders().getFirst(Headers.CORRELATION_ID);
        assertThat(forwarded).isNotBlank();
        assertThat(echoed).isEqualTo(forwarded);
    }

    @Test
    void preservesInboundCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/alerts")
                        .header(Headers.CORRELATION_ID, "corr-abc")
                        .build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured.getRequest().getHeaders().getFirst(Headers.CORRELATION_ID))
                .isEqualTo("corr-abc");
        assertThat(exchange.getResponse().getHeaders().getFirst(Headers.CORRELATION_ID))
                .isEqualTo("corr-abc");
    }

    /** Captures the (possibly mutated) exchange that the filter forwards downstream. */
    static final class CapturingChain implements WebFilterChain {
        ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }
    }
}
