package com.frauddetect.common.autoconfigure;

import com.frauddetect.common.security.JwtProperties;
import com.frauddetect.common.security.JwtService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Exposes a {@link JwtService} bean bound to {@link JwtProperties} whenever a signing secret is
 * configured ({@code security.jwt.secret}). Services that don't need JWT simply omit the property.
 * <p>
 * This configuration is deliberately servlet-agnostic — it references no servlet types — so it also
 * activates in reactive services (e.g. the WebFlux API gateway) that validate JWTs at the edge. The
 * servlet-only {@link com.frauddetect.common.security.JwtAuthenticationFilter} is published
 * separately by {@link CommonServletSecurityAutoConfiguration}, which is gated on the Servlet API so
 * it silently backs off on a reactive classpath.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "security.jwt", name = "secret")
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }
}
