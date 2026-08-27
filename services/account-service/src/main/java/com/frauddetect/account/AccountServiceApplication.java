package com.frauddetect.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Account service — owns account master data and balances in MySQL (Section 4). Exposes REST for
 * account creation/lookup and the balance + lifecycle operations (credit, debit, freeze) that the
 * rest of the platform relies on. Purely relational and synchronous: it publishes no events and
 * consumes none (see docs/PLATFORM-CONVENTIONS.md).
 *
 * <p>JPA auditing lives in {@link com.frauddetect.account.config.JpaAuditingConfig} rather than
 * here, so web-only test slices don't activate the auditing registrar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
