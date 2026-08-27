package com.frauddetect.account.dto;

import com.frauddetect.account.domain.AccountEntity;
import com.frauddetect.account.domain.AccountStatus;
import com.frauddetect.account.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model for an account. Exposes the optimistic-lock {@code version} so clients can perform safe
 * read-modify-write cycles, and the audit timestamps.
 */
public record AccountResponse(
        String id,
        String customerId,
        String accountNumber,
        AccountType type,
        String currency,
        BigDecimal balance,
        BigDecimal creditLimit,
        AccountStatus status,
        Instant openedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static AccountResponse from(AccountEntity e) {
        return new AccountResponse(
                e.getId(),
                e.getCustomerId(),
                e.getAccountNumber(),
                e.getType(),
                e.getCurrency(),
                e.getBalance(),
                e.getCreditLimit(),
                e.getStatus(),
                e.getOpenedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }
}
