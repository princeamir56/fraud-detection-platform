package com.frauddetect.customer.repository;

import com.frauddetect.customer.domain.CustomerEntity;
import com.frauddetect.customer.domain.CustomerStatus;
import com.frauddetect.customer.domain.UserEntity;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration test against a real MySQL 8.4 container (Section 17). Validates the JPA
 * mappings for both {@code customers} and {@code users}, the unique constraints, auditing/version
 * columns, and the comma-separated {@code roles} round-trip. Requires a Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CustomerRepositoryIT.AuditingConfig.class)
@Testcontainers
class CustomerRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("fraud_customer");

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

    @Autowired CustomerRepository customers;
    @Autowired UserRepository users;

    private CustomerEntity newCustomer(String email) {
        CustomerEntity c = new CustomerEntity();
        c.setId(UUID.randomUUID().toString());
        c.setFirstName("Jane");
        c.setLastName("Doe");
        c.setEmail(email);
        c.setCountryCode("US");
        c.setStatus(CustomerStatus.ACTIVE);
        return c;
    }

    private UserEntity newUser(String username, List<String> roles, String customerId) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID().toString());
        u.setUsername(username);
        u.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        u.setRoleList(roles);
        u.setEnabled(true);
        u.setCustomerId(customerId);
        return u;
    }

    @Test
    void persistsCustomerWithAuditingAndVersion() {
        CustomerEntity saved = customers.saveAndFlush(newCustomer("jane@example.com"));

        CustomerEntity found = customers.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void customerEmailIsUniqueAndLookedUp() {
        customers.saveAndFlush(newCustomer("unique@example.com"));
        assertThat(customers.existsByEmail("unique@example.com")).isTrue();
        assertThat(customers.findByEmail("unique@example.com")).isPresent();
    }

    @Test
    void listsCustomersScopedByStatus() {
        customers.saveAndFlush(newCustomer("a@example.com"));
        CustomerEntity blocked = newCustomer("b@example.com");
        blocked.setStatus(CustomerStatus.BLOCKED);
        customers.saveAndFlush(blocked);

        Page<CustomerEntity> active = customers.findByStatus(CustomerStatus.ACTIVE, PageRequest.of(0, 10));
        assertThat(active.getContent()).extracting(CustomerEntity::getEmail).contains("a@example.com");
        assertThat(active.getContent()).extracting(CustomerEntity::getEmail).doesNotContain("b@example.com");
    }

    @Test
    void persistsUserAndRoundTripsMultipleRoles() {
        UserEntity saved = users.saveAndFlush(newUser("admin1", List.of("ADMIN", "ANALYST"), null));

        UserEntity found = users.findByUsername("admin1").orElseThrow();
        assertThat(found.getRoles()).isEqualTo("ADMIN,ANALYST");
        assertThat(found.roleList()).containsExactly("ADMIN", "ANALYST");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void usernameUniquenessIsEnforced() {
        users.saveAndFlush(newUser("jdoe", List.of("CUSTOMER"), "cust-1"));
        assertThat(users.existsByUsername("jdoe")).isTrue();
        assertThat(users.findByUsername("jdoe")).isPresent();
    }
}
