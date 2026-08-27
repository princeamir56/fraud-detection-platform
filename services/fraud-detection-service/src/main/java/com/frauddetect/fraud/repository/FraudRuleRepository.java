package com.frauddetect.fraud.repository;

import com.frauddetect.fraud.domain.FraudRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for the configurable {@link FraudRuleEntity} catalogue (MySQL). The engine loads the
 * enabled set (cached, refreshed on write) rather than reading per transaction.
 */
public interface FraudRuleRepository extends JpaRepository<FraudRuleEntity, String> {

    List<FraudRuleEntity> findByEnabledTrue();
}
