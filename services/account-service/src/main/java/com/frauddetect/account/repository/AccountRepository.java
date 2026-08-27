package com.frauddetect.account.repository;

import com.frauddetect.account.domain.AccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence for {@link AccountEntity}. Spring Data derives the queries; the account-number lookup
 * backs the create-time uniqueness check ahead of the DB unique constraint (friendly 409 vs. raw
 * constraint violation).
 */
public interface AccountRepository extends JpaRepository<AccountEntity, String> {

    boolean existsByAccountNumber(String accountNumber);

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    Page<AccountEntity> findByCustomerId(String customerId, Pageable pageable);
}
