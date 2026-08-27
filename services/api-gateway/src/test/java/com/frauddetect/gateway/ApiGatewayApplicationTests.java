package com.frauddetect.gateway;

import com.frauddetect.common.security.JwtService;
import com.frauddetect.gateway.filter.CorrelationIdWebFilter;
import com.frauddetect.gateway.filter.JwtAuthenticationWebFilter;
import com.frauddetect.gateway.filter.RateLimitingWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context smoke test: the reactive gateway boots, the programmatic route table exposes exactly the
 * public REST services (and not the gRPC-only risk-scoring-service), the shared {@link JwtService} is
 * autoconfigured from {@code security.jwt.secret}, and all three edge {@code WebFilter}s are wired.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @Autowired
    ApplicationContext context;

    @Test
    void exposesRoutesForEveryPublicRestService() {
        RouteLocator locator = context.getBean("platformRoutes", RouteLocator.class);

        var routeIds = locator.getRoutes().map(Route::getId).collectList().block();

        assertThat(routeIds).containsExactlyInAnyOrder(
                "customer-service", "account-service", "transaction-service",
                "fraud-detection-service", "alert-service", "notification-service", "audit-service");
    }

    @Test
    void wiresJwtServiceAndEdgeFilters() {
        assertThat(context.getBeansOfType(JwtService.class)).isNotEmpty();
        assertThat(context.getBeansOfType(CorrelationIdWebFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(RateLimitingWebFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(JwtAuthenticationWebFilter.class)).hasSize(1);
    }
}
