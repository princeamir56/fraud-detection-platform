package com.frauddetect.alert.repository;

import com.frauddetect.alert.domain.AlertEntity;
import com.frauddetect.alert.domain.AlertStatus;
import com.frauddetect.alert.domain.ProcessedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration test against a real MySQL 8.4 container (Section 17). Validates the JPA
 * mapping for {@code alerts} (auditing + optimistic version), the status- and customer-scoped lookups,
 * and the {@code processed_events} dedupe store. Requires a Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AlertRepositoryIT.AuditingConfig.class)
@Testcontainers
class AlertRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("fraud_alert");

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

    @Autowired AlertRepository alerts;
    @Autowired ProcessedEventRepository processedEvents;

    private AlertEntity newAlert(String customerId, AlertStatus status) {
        AlertEntity a = new AlertEntity();
        a.setId(UUID.randomUUID().toString());
        a.setTransactionId("tx-" + customerId);
        a.setCustomerId(customerId);
        a.setAccountId("acc-" + customerId);
        a.setSeverity("HIGH");
        a.setScore(80);
        a.setStatus(status);
        a.setTitle("HIGH-severity fraud on transaction tx-" + customerId);
        a.setDescription("Fraud score 80, decision BLOCK.");
        return a;
    }

    @Test
    void persistsAlertWithAuditingAndVersion() {
        AlertEntity saved = alerts.saveAndFlush(newAlert("cust-1", AlertStatus.OPEN));

        AlertEntity found = alerts.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void findsAlertsScopedByStatus() {
        alerts.saveAndFlush(newAlert("cust-A", AlertStatus.OPEN));
        alerts.saveAndFlush(newAlert("cust-B", AlertStatus.RESOLVED));

        var open = alerts.findByStatus(AlertStatus.OPEN, PageRequest.of(0, 10));
        assertThat(open.getContent()).hasSize(1);
        assertThat(open.getContent().getFirst().getStatus()).isEqualTo(AlertStatus.OPEN);
    }

    @Test
    void findsAlertsScopedByCustomer() {
        alerts.saveAndFlush(newAlert("cust-X", AlertStatus.OPEN));
        alerts.saveAndFlush(newAlert("cust-Y", AlertStatus.OPEN));

        var page = alerts.findByCustomerId("cust-X", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getCustomerId()).isEqualTo("cust-X");
    }

    @Test
    void dedupeMarkerRoundTrips() {
        processedEvents.saveAndFlush(new ProcessedEvent("evt-1", "alert-service", Instant.now()));
        assertThat(processedEvents.existsById("evt-1")).isTrue();
        assertThat(processedEvents.existsById("evt-unknown")).isFalse();
    }
}
