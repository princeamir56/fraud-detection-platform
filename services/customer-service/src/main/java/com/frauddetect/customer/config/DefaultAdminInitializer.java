package com.frauddetect.customer.config;

import com.frauddetect.common.constants.SecurityRoles;
import com.frauddetect.customer.domain.UserEntity;
import com.frauddetect.customer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Bootstraps a single default ADMIN user on first startup so a freshly-cloned deployment is usable
 * without a manual SQL insert. Idempotent: it only creates the account if the username is absent, so
 * it is safe to run on every boot. The password is BCrypt-hashed from an environment-sourced value —
 * no plaintext or pre-computed hash lives in the repo (Section 11 / Section 20).
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);
    private static final String DEV_DEFAULT_PASSWORD = "admin-change-me";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties adminProps;

    public DefaultAdminInitializer(UserRepository users, PasswordEncoder passwordEncoder,
                                   BootstrapAdminProperties adminProps) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminProps = adminProps;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = adminProps.username();
        if (username == null || username.isBlank() || users.existsByUsername(username)) {
            return;
        }

        UserEntity admin = new UserEntity();
        admin.setId(UUID.randomUUID().toString());
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(adminProps.password()));
        admin.setRoleList(List.of(SecurityRoles.ADMIN));
        admin.setEnabled(true);
        admin.setCustomerId(null);
        users.save(admin);

        log.info("Bootstrapped default ADMIN user '{}'", username);
        if (DEV_DEFAULT_PASSWORD.equals(adminProps.password())) {
            log.warn("Default ADMIN is using the built-in dev password — set ADMIN_PASSWORD before any real deployment.");
        }
    }
}
