package com.frauddetect.fraud.service;

import com.frauddetect.common.error.BusinessRuleException;
import com.frauddetect.common.error.ConflictException;
import com.frauddetect.common.error.ResourceNotFoundException;
import com.frauddetect.fraud.domain.FraudRuleEntity;
import com.frauddetect.fraud.domain.RuleType;
import com.frauddetect.fraud.dto.FraudRuleRequest;
import com.frauddetect.fraud.dto.FraudRuleResponse;
import com.frauddetect.fraud.dto.FraudRuleUpdateRequest;
import com.frauddetect.fraud.engine.FraudRuleEngine;
import com.frauddetect.fraud.repository.FraudRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rule-administration service (Section 9: "rules must be configurable; do not hard-code all business
 * logic inside controllers"). Every mutation is transactional and, on commit, refreshes the
 * {@link FraudRuleEngine}'s in-memory snapshot so operators' tuning takes effect immediately without
 * a redeploy.
 *
 * <p>The engine reload is registered as an <em>after-commit</em> callback: the live rule set is
 * updated only once the change is durably committed, so a rolled-back edit never leaks into scoring.
 * If (defensively) no transaction is active, the reload runs inline.
 */
@Service
public class FraudRuleService {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleService.class);

    private final FraudRuleRepository repository;
    private final FraudRuleEngine engine;

    public FraudRuleService(FraudRuleRepository repository, FraudRuleEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public List<FraudRuleResponse> list() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(FraudRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FraudRuleResponse get(String code) {
        return FraudRuleResponse.from(require(code));
    }

    @Transactional
    public FraudRuleResponse create(FraudRuleRequest request) {
        if (repository.existsById(request.code())) {
            throw new ConflictException("Fraud rule already exists: " + request.code());
        }
        FraudRuleEntity entity = new FraudRuleEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setRuleType(parseRuleType(request.ruleType()));
        entity.setWeight(request.weight());
        entity.setEnabled(request.enabled());
        entity.setThresholdNumeric(request.thresholdNumeric());
        entity.setThresholdInt(request.thresholdInt());
        entity.setParamsJson(request.paramsJson());

        FraudRuleEntity saved = repository.save(entity);
        reloadEngineAfterCommit();
        log.info("Created fraud rule {} (type={}, weight={}, enabled={})",
                saved.getCode(), saved.getRuleType(), saved.getWeight(), saved.isEnabled());
        return FraudRuleResponse.from(saved);
    }

    @Transactional
    public FraudRuleResponse update(String code, FraudRuleUpdateRequest request) {
        FraudRuleEntity entity = require(code);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setRuleType(parseRuleType(request.ruleType()));
        entity.setWeight(request.weight());
        entity.setEnabled(request.enabled());
        entity.setThresholdNumeric(request.thresholdNumeric());
        entity.setThresholdInt(request.thresholdInt());
        entity.setParamsJson(request.paramsJson());

        FraudRuleEntity saved = repository.save(entity);
        reloadEngineAfterCommit();
        log.info("Updated fraud rule {} (type={}, weight={}, enabled={})",
                saved.getCode(), saved.getRuleType(), saved.getWeight(), saved.isEnabled());
        return FraudRuleResponse.from(saved);
    }

    @Transactional
    public FraudRuleResponse setEnabled(String code, boolean enabled) {
        FraudRuleEntity entity = require(code);
        if (entity.isEnabled() != enabled) {
            entity.setEnabled(enabled);
            repository.save(entity);
            reloadEngineAfterCommit();
            log.info("{} fraud rule {}", enabled ? "Enabled" : "Disabled", code);
        }
        return FraudRuleResponse.from(entity);
    }

    @Transactional
    public void delete(String code) {
        FraudRuleEntity entity = require(code);
        repository.delete(entity);
        reloadEngineAfterCommit();
        log.info("Deleted fraud rule {}", code);
    }

    private FraudRuleEntity require(String code) {
        return repository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Fraud rule", code));
    }

    /** Resolves the rule-type string, surfacing a clear 422 (with valid options) on an unknown value. */
    private RuleType parseRuleType(String value) {
        try {
            return RuleType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            String valid = Arrays.stream(RuleType.values()).map(Enum::name).collect(Collectors.joining(", "));
            throw new BusinessRuleException("Unknown ruleType '" + value + "'. Valid types: " + valid);
        }
    }

    /**
     * Refreshes the engine's rule snapshot once the surrounding transaction commits, so a rolled-back
     * edit never becomes active. Falls back to an immediate reload if there is no active transaction.
     */
    private void reloadEngineAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    engine.reload();
                }
            });
        } else {
            engine.reload();
        }
    }
}
