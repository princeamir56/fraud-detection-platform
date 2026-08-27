package com.frauddetect.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Customer / identity service.
 *
 * <p>Owns the {@code fraud_customer} MySQL schema (users + customer profiles), issues the JWTs that
 * every other service on the platform validates, and hashes credentials with BCrypt. JPA auditing
 * ({@code @CreatedDate}/{@code @LastModifiedDate}) is enabled in
 * {@link com.frauddetect.customer.config.JpaAuditingConfig} rather than here, so web-only test
 * slices don't activate the auditing registrar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
