package com.frauddetect.customer.dto;

import com.frauddetect.customer.domain.CustomerEntity;
import com.frauddetect.customer.domain.CustomerStatus;

import java.time.Instant;

/** Customer profile projection returned by the API. */
public record CustomerResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String countryCode,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static CustomerResponse from(CustomerEntity e) {
        return new CustomerResponse(e.getId(), e.getFirstName(), e.getLastName(), e.getEmail(),
                e.getPhone(), e.getCountryCode(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion());
    }
}
