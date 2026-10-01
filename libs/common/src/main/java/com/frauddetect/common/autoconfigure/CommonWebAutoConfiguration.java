package com.frauddetect.common.autoconfigure;

import com.frauddetect.common.correlation.CorrelationIdFilter;
import com.frauddetect.common.error.GlobalExceptionHandler;
import com.frauddetect.common.error.SecurityExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-registers the shared servlet-layer beans (correlation filter + global error handler)
 * for every service that has a servlet web stack, without requiring services to component-scan
 * the {@code com.frauddetect.common} package.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Registered only when Spring Security is present; see {@link SecurityExceptionHandler}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    static class SecurityExceptionHandlerConfiguration {

        @Bean
        public SecurityExceptionHandler securityExceptionHandler() {
            return new SecurityExceptionHandler();
        }
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        CorrelationIdFilter filter = new CorrelationIdFilter();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(filter.getOrder());
        registration.setName("correlationIdFilter");
        return registration;
    }
}
