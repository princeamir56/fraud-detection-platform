package com.frauddetect.notification.repository;

import com.frauddetect.notification.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Dedupe store for consumed {@code alert.created} events, keyed by Avro {@code eventId}. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
