package com.frauddetect.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Reactive edge gateway (Spring Cloud Gateway on WebFlux/Netty): the single public entry point for the
 * platform. It authenticates JWTs at the edge, propagates the authenticated identity and correlation id
 * downstream, applies per-client rate limiting, and routes to the internal REST services.
 *
 * <p>No servlet stack is present here — inheriting {@code common} does not drag in Spring MVC/Security
 * (those deps are {@code provided}/{@code optional}), and {@code spring.main.web-application-type=reactive}
 * pins the runtime to Netty.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
