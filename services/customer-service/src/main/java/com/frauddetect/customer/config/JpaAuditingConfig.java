package com.frauddetect.customer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing (populates {@code @CreatedDate} / {@code @LastModifiedDate} on the customer
 * and user entities). Kept as a dedicated {@code @Configuration} rather than on the application
 * bootstrap class so web-only slices ({@code @WebMvcTest}) don't trigger the auditing registrar —
 * which would otherwise fail with an empty JPA metamodel when no {@code EntityManagerFactory} is
 * present.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingConfig {
}
