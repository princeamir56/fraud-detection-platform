package com.frauddetect.customer.repository;

import com.frauddetect.customer.domain.CustomerEntity;
import com.frauddetect.customer.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {

    boolean existsByEmail(String email);

    Optional<CustomerEntity> findByEmail(String email);

    Page<CustomerEntity> findByStatus(CustomerStatus status, Pageable pageable);
}
