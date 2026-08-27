package com.frauddetect.transaction.repository;

import com.frauddetect.transaction.domain.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Transaction persistence. History queries are paged and backed by the composite indexes declared
 * on {@link TransactionEntity} (account/customer + created_at).
 */
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    Page<TransactionEntity> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);

    Page<TransactionEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);
}
