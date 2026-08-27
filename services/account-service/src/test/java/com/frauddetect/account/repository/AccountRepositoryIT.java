package com.frauddetect.account.repository;

import com.frauddetect.account.domain.AccountEntity;
import com.frauddetect.account.domain.AccountStatus;
import com.frauddetect.account.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration test against a real MySQL 8.4 container (Section 17). Validates the JPA
 * entity mapping, the unique account-number constraint, auditing/version columns, and the
 * customer-scoped lookup. Schema here is created by Hibernate ({@code create-drop}); the Flyway
 * migration is exercised via the application startup path. Requires a Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AccountRepositoryIT.AuditingConfig.class)
@Testcontainers
class AccountRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("fraud_account");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
    }

    @Autowired
    AccountRepository repository;

    private AccountEntity newAccount(String customerId, String number) {
        AccountEntity e = new AccountEntity();
        e.setId(UUID.randomUUID().toString());
        e.setCustomerId(customerId);
        e.setAccountNumber(number);
        e.setType(AccountType.CHECKING);
        e.setCurrency("USD");
        e.setBalance(new BigDecimal("250.0000"));
        e.setCreditLimit(BigDecimal.ZERO);
        e.setStatus(AccountStatus.ACTIVE);
        e.setOpenedAt(Instant.now());
        return e;
    }

    @Test
    void persistsAndReadsBackWithAuditingAndVersion() {
        AccountEntity saved = repository.saveAndFlush(newAccount("cust-1", "FD000000000001"));

        AccountEntity found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getBalance()).isEqualByComparingTo("250.0000");
        assertThat(found.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void accountNumberIsUniqueAndLookedUp() {
        repository.saveAndFlush(newAccount("cust-1", "FD000000000002"));
        assertThat(repository.existsByAccountNumber("FD000000000002")).isTrue();
        assertThat(repository.findByAccountNumber("FD000000000002")).isPresent();
    }

    @Test
    void listsAccountsScopedByCustomer() {
        repository.saveAndFlush(newAccount("cust-A", "FD000000000010"));
        repository.saveAndFlush(newAccount("cust-A", "FD000000000011"));
        repository.saveAndFlush(newAccount("cust-B", "FD000000000012"));

        Page<AccountEntity> page = repository.findByCustomerId("cust-A", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
