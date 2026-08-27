package com.frauddetect.transaction.repository;

import com.frauddetect.common.domain.TransactionStatus;
import com.frauddetect.common.domain.TransactionType;
import com.frauddetect.transaction.domain.TransactionEntity;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration test against a real MySQL 8.4 container (Section 17). Validates the JPA
 * entity mapping and the derived history queries. Schema here is created by Hibernate
 * ({@code create-drop}); the hand-written Flyway migration is exercised by the application/e2e
 * startup path (docs/testing.md). Requires a Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TransactionRepositoryIT.AuditingConfig.class)
@Testcontainers
class TransactionRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("fraud_transaction");

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
    TransactionRepository repository;

    private TransactionEntity newTx(String accountId, String customerId) {
        TransactionEntity e = new TransactionEntity();
        e.setId(UUID.randomUUID().toString());
        e.setAccountId(accountId);
        e.setCustomerId(customerId);
        e.setAmount(new BigDecimal("99.9900"));
        e.setCurrency("USD");
        e.setType(TransactionType.PURCHASE);
        e.setStatus(TransactionStatus.PENDING);
        e.setCountryCode("US");
        e.setChannel("WEB");
        return e;
    }

    @Test
    void persistsAndReadsBackWithAuditingAndVersion() {
        TransactionEntity saved = repository.saveAndFlush(newTx("acc-1", "cust-1"));

        TransactionEntity found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAmount()).isEqualByComparingTo("99.9900");
        assertThat(found.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void historyIsScopedByAccountAndPaged() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            repository.saveAndFlush(newTx("acc-A", "cust-1"));
            Thread.sleep(10); // distinct created_at (microsecond precision) for deterministic ordering
        }
        repository.saveAndFlush(newTx("acc-B", "cust-2"));

        Page<TransactionEntity> firstPage =
                repository.findByAccountIdOrderByCreatedAtDesc("acc-A", PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent().get(0).getCreatedAt())
                .isAfterOrEqualTo(firstPage.getContent().get(1).getCreatedAt());
        assertThat(repository.findByAccountIdOrderByCreatedAtDesc("acc-B", PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }
}
