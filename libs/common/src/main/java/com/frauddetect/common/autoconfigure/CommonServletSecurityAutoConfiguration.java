package com.frauddetect.common.autoconfigure;

import com.frauddetect.common.security.JwtAuthenticationFilter;
import com.frauddetect.common.security.JwtService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Publishes the servlet-based {@link JwtAuthenticationFilter} so each servlet service's
 * {@code SecurityFilterChain} can slot it in without re-implementing token parsing.
 * <p>
 * Split out from {@link CommonSecurityAutoConfiguration} and gated on the Servlet API
 * ({@code jakarta.servlet.Filter}) rather than on {@code OncePerRequestFilter}: the latter's class
 * file ships in spring-web (present even on a reactive classpath), so gating on it would pass and
 * then fail to load. Gating on the Servlet API means this configuration is never introspected in a
 * reactive application (e.g. the WebFlux API gateway), avoiding a {@code NoClassDefFoundError} for
 * {@code jakarta.servlet.Filter} during condition evaluation.
 */
@AutoConfiguration(after = CommonSecurityAutoConfiguration.class)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class CommonServletSecurityAutoConfiguration {

    @Bean
    @ConditionalOnBean(JwtService.class)
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }
}
