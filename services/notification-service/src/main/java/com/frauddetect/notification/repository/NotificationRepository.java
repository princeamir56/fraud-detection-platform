package com.frauddetect.notification.repository;

import com.frauddetect.notification.domain.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read/write access to the durable notification ledger. */
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    Page<NotificationEntity> findByCustomerId(String customerId, Pageable pageable);

    Page<NotificationEntity> findByAlertId(String alertId, Pageable pageable);
}
