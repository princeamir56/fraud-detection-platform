package com.frauddetect.notification.repository;

import com.frauddetect.notification.domain.NotificationChannel;
import com.frauddetect.notification.domain.NotificationEntity;
import com.frauddetect.notification.domain.NotificationStatus;
import com.frauddetect.notification.domain.ProcessedEvent;
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
 * mapping for {@code notifications} (auditing + version), the customer-scoped lookup, and the
 * {@code processed_events} dedupe store. Requires a Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationRepositoryIT.AuditingConfig.class)
@Testcontainers
class NotificationRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("fraud_notification");

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

    @Autowired NotificationRepository notifications;
    @Autowired ProcessedEventRepository processedEvents;

    private NotificationEntity newNotification(String customerId) {
        NotificationEntity n = new NotificationEntity();
        n.setId(UUID.randomUUID().toString());
        n.setAlertId("alert-1");
        n.setCustomerId(customerId);
        n.setChannel(NotificationChannel.EMAIL);
        n.setRecipient("customer-" + customerId + "@notify.example");
        n.setSubject("Fraud alert");
        n.setBody("Suspicious activity detected");
        n.setSeverity("HIGH");
        n.setStatus(NotificationStatus.SENT);
        return n;
    }

    @Test
    void persistsNotificationWithAuditingAndVersion() {
        NotificationEntity saved = notifications.saveAndFlush(newNotification("cust-1"));

        NotificationEntity found = notifications.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void findsNotificationsScopedByCustomer() {
        notifications.saveAndFlush(newNotification("cust-A"));
        notifications.saveAndFlush(newNotification("cust-B"));

        var page = notifications.findByCustomerId("cust-A", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getCustomerId()).isEqualTo("cust-A");
    }

    @Test
    void dedupeMarkerRoundTrips() {
        processedEvents.saveAndFlush(new ProcessedEvent("evt-1", "notification-service", Instant.now()));
        assertThat(processedEvents.existsById("evt-1")).isTrue();
        assertThat(processedEvents.existsById("evt-unknown")).isFalse();
    }
}
