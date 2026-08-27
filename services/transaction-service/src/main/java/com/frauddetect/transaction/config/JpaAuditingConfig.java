package com.frauddetect.transaction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing ({@code @CreatedDate} / {@code @LastModifiedDate}) for the transaction entities.
 *
 * <p>Kept in a dedicated {@code @Configuration} rather than on the bootstrap class so that web-only
 * test slices ({@code @WebMvcTest}) do not activate the JPA auditing registrar — which would otherwise
 * fail with "JPA metamodel must not be empty" when no JPA infrastructure is loaded in the slice.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingConfig {
}
