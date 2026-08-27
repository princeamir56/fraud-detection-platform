package com.frauddetect.common.autoconfigure;

import com.frauddetect.common.correlation.CorrelationIdFilter;
import com.frauddetect.common.error.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

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
