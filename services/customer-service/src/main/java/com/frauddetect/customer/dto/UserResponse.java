package com.frauddetect.customer.dto;

import com.frauddetect.customer.domain.UserEntity;

import java.time.Instant;
import java.util.List;

/** Non-sensitive view of a user — never exposes the password hash. */
public record UserResponse(
        String id,
        String username,
        List<String> roles,
        boolean enabled,
        String customerId,
        Instant createdAt) {

    public static UserResponse from(UserEntity e) {
        return new UserResponse(e.getId(), e.getUsername(), e.roleList(), e.isEnabled(),
                e.getCustomerId(), e.getCreatedAt());
    }
}
