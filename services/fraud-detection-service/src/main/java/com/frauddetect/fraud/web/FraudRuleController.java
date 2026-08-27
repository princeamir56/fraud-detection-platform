package com.frauddetect.fraud.web;

import com.frauddetect.fraud.dto.FraudRuleRequest;
import com.frauddetect.fraud.dto.FraudRuleResponse;
import com.frauddetect.fraud.dto.FraudRuleUpdateRequest;
import com.frauddetect.fraud.service.FraudRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Rule-administration REST API (Section 9 + Section 11 RBAC). Controllers stay thin: validation via
 * Bean Validation on the DTOs, authorization via {@code @PreAuthorize}, and all logic delegated to
 * {@link FraudRuleService}. Mutations are ADMIN-only; reads are open to ANALYST/INVESTIGATOR/ADMIN so
 * analysts can inspect the active configuration.
 */
@RestController
@RequestMapping("/api/v1/fraud-rules")
public class FraudRuleController {

    private final FraudRuleService service;

    public FraudRuleController(FraudRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')")
    public List<FraudRuleResponse> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')")
    public FraudRuleResponse get(@PathVariable String code) {
        return service.get(code);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FraudRuleResponse> create(@Valid @RequestBody FraudRuleRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        FraudRuleResponse created = service.create(request);
        URI location = uriBuilder.path("/api/v1/fraud-rules/{code}").buildAndExpand(created.code()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public FraudRuleResponse update(@PathVariable String code,
                                    @Valid @RequestBody FraudRuleUpdateRequest request) {
        return service.update(code, request);
    }

    /** Fast enable/disable toggle without resubmitting the whole rule body. */
    @PatchMapping("/{code}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public FraudRuleResponse setEnabled(@PathVariable String code, @RequestParam boolean enabled) {
        return service.setEnabled(code, enabled);
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
