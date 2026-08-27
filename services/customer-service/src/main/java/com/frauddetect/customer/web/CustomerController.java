package com.frauddetect.customer.web;

import com.frauddetect.common.api.PageResponse;
import com.frauddetect.customer.domain.CustomerStatus;
import com.frauddetect.customer.dto.CustomerResponse;
import com.frauddetect.customer.dto.UpdateCustomerRequest;
import com.frauddetect.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer profile REST API (Section 1 + Section 11 RBAC). Reads are open to any authenticated
 * principal; profile edits are staff-only; status changes (BLOCK/CLOSE) are a fraud/ops control
 * granted to ANALYST/INVESTIGATOR/ADMIN. All logic is delegated to {@link CustomerService}.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CustomerResponse getById(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')")
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<CustomerResponse> result = service.list(status, pageable);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public CustomerResponse update(@PathVariable String id, @Valid @RequestBody UpdateCustomerRequest request) {
        return service.update(id, request);
    }

    /** Block/unblock/close a customer — the BLOCK path is a fraud-response control. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','INVESTIGATOR')")
    public CustomerResponse changeStatus(@PathVariable String id, @RequestParam CustomerStatus status) {
        return service.changeStatus(id, status);
    }
}
