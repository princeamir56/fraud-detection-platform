package com.frauddetect.fraud.repository;

import com.frauddetect.fraud.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Dedupe store for consumed {@code transaction.created} events, keyed by Avro {@code eventId}. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
