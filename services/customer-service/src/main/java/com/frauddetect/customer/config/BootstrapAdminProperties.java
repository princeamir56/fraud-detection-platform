package com.frauddetect.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the default administrator provisioned on first startup. Sourced from the
 * environment ({@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD}/{@code ADMIN_EMAIL}); never hard-coded.
 *
 * @param username admin login
 * @param password admin password (dev default is clearly a placeholder; override in every real env)
 * @param email    admin contact email
 */
@ConfigurationProperties(prefix = "bootstrap.admin")
public record BootstrapAdminProperties(String username, String password, String email) {
}
