package com.frauddetect.transaction.web;

import com.frauddetect.common.api.PageResponse;
import com.frauddetect.common.constants.Headers;
import com.frauddetect.transaction.dto.CreateTransactionRequest;
import com.frauddetect.transaction.dto.TransactionResponse;
import com.frauddetect.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Transaction REST API (Section 1). Controllers stay thin: validation via Bean Validation on the
 * DTO, authorization via {@code @PreAuthorize}, and all business logic delegated to
 * {@link TransactionService} (Section 9: no business logic in controllers).
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * Submit a transaction. Returns 202 Accepted with the PENDING resource — the fraud verdict is
     * applied asynchronously; clients poll {@code GET /{id}} (see the Location header) for the outcome.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ANALYST','ADMIN')")
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(value = Headers.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            UriComponentsBuilder uriBuilder) {

        TransactionResponse created = service.create(request, idempotencyKey);
        URI location = uriBuilder.path("/api/v1/transactions/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.accepted().location(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TransactionResponse getById(@PathVariable String id) {
        return service.getById(id);
    }

    /**
     * Transaction history filtered by exactly one of {@code accountId} or {@code customerId}.
     * Analysts/admins can read any history; customers should be scoped by the gateway to their own.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN','CUSTOMER')")
    public PageResponse<TransactionResponse> history(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<TransactionResponse> result;
        if (accountId != null && !accountId.isBlank()) {
            result = service.byAccount(accountId, pageable);
        } else if (customerId != null && !customerId.isBlank()) {
            result = service.byCustomer(customerId, pageable);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide exactly one of 'accountId' or 'customerId'");
        }
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
