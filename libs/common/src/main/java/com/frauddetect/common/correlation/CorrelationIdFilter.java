package com.frauddetect.common.correlation;

import com.frauddetect.common.constants.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts the inbound {@code X-Correlation-Id} (or mints one), binds it to the logging
 * context for the duration of the request, and echoes it back on the response so callers
 * — and Kibana — can stitch a single request across every service hop.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    // Run before security/other filters so every downstream log line carries the id.
    private int order = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(Headers.CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationContext.newId();
        }
        try {
            CorrelationContext.setCorrelationId(correlationId);
            response.setHeader(Headers.CORRELATION_ID, correlationId);
            chain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
