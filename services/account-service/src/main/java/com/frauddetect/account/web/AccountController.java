package com.frauddetect.account.web;

import com.frauddetect.account.domain.AccountStatus;
import com.frauddetect.account.dto.AccountResponse;
import com.frauddetect.account.dto.BalanceChangeRequest;
import com.frauddetect.account.dto.CreateAccountRequest;
import com.frauddetect.account.service.AccountService;
import com.frauddetect.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Account REST API (Section 1 + Section 11 RBAC). Controllers stay thin: validation via Bean
 * Validation on the DTOs, authorization via {@code @PreAuthorize}, and all logic delegated to
 * {@link AccountService} (Section 9: no business logic in controllers). Balance movements and status
 * changes are privileged operations (ANALYST/ADMIN, plus INVESTIGATOR for freezing); account holders
 * (CUSTOMER) may read their own accounts.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        AccountResponse created = service.create(request);
        URI location = uriBuilder.path("/api/v1/accounts/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public AccountResponse getById(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN','CUSTOMER')")
    public PageResponse<AccountResponse> byCustomer(
            @RequestParam String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<AccountResponse> result = service.byCustomer(customerId, pageable);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @PostMapping("/{id}/credit")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public AccountResponse credit(@PathVariable String id, @Valid @RequestBody BalanceChangeRequest request) {
        return service.credit(id, request);
    }

    @PostMapping("/{id}/debit")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public AccountResponse debit(@PathVariable String id, @Valid @RequestBody BalanceChangeRequest request) {
        return service.debit(id, request);
    }

    /** Freeze/unfreeze/close an account — the freeze path is the fraud-response control. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','INVESTIGATOR')")
    public AccountResponse changeStatus(@PathVariable String id, @RequestParam AccountStatus status) {
        return service.changeStatus(id, status);
    }
}
