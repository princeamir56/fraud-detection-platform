package com.frauddetect.alert.repository;

import com.frauddetect.alert.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Dedupe store for consumed {@code fraud.detected} events, keyed by Avro {@code eventId}. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
