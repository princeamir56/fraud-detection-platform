package com.frauddetect.alert.repository;

import com.frauddetect.alert.domain.AlertEntity;
import com.frauddetect.alert.domain.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for fraud alerts. The unfiltered {@code findAll(Pageable)} is inherited; the finders
 * below back the triage list API's optional {@code status}/{@code customerId} filters.
 */
public interface AlertRepository extends JpaRepository<AlertEntity, String> {

    Page<AlertEntity> findByStatus(AlertStatus status, Pageable pageable);

    Page<AlertEntity> findByCustomerId(String customerId, Pageable pageable);

    Page<AlertEntity> findBySeverity(String severity, Pageable pageable);
}
