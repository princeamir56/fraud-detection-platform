package com.frauddetect.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A platform user / principal (Section 11). Holds the BCrypt password hash (never plaintext) and the
 * granted roles as a comma-separated list (e.g. {@code "CUSTOMER"} or {@code "ANALYST,ADMIN"}) —
 * stored without the {@code ROLE_} prefix, matching the JWT {@code roles} claim.
 *
 * <p>A {@code CUSTOMER} user links to its profile via {@code customerId}; staff users
 * (ADMIN/ANALYST/INVESTIGATOR) have a {@code null} {@code customerId}.
 */
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
        indexes = @Index(name = "idx_user_customer", columnList = "customer_id"))
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "username", length = 100, nullable = false)
    private String username;

    /** BCrypt hash of the user's password. Plaintext credentials are never persisted. */
    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    /** Comma-separated role names without the {@code ROLE_} prefix. */
    @Column(name = "roles", length = 255, nullable = false)
    private String roles;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Profile this login belongs to; {@code null} for staff users. */
    @Column(name = "customer_id", length = 36)
    private String customerId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public UserEntity() {
    }

    /** Roles as a list, split from the stored comma-separated column. */
    public List<String> roleList() {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Set the roles column from a list, joining with commas. */
    public void setRoleList(List<String> roleList) {
        this.roles = String.join(",", roleList);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
