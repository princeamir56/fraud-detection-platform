package com.frauddetect.alert.web;

import com.frauddetect.alert.domain.AlertStatus;
import com.frauddetect.alert.dto.AlertResponse;
import com.frauddetect.alert.dto.ResolveAlertRequest;
import com.frauddetect.alert.search.AlertDocument;
import com.frauddetect.alert.search.AlertQuery;
import com.frauddetect.alert.search.AlertSearchService;
import com.frauddetect.alert.search.SearchResults;
import com.frauddetect.alert.service.AlertService;
import com.frauddetect.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Alert triage API (Section 7 + Section 11 RBAC).
 *
 * <p>Reads (list, get, search) are open to ANALYST/INVESTIGATOR/ADMIN; the state-changing triage
 * actions (acknowledge, resolve) are restricted to INVESTIGATOR/ADMIN. The acting user is taken from
 * the authenticated principal — never from the request body — so it cannot be spoofed.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')")
public class AlertController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AlertService alertService;
    private final AlertSearchService searchService;

    public AlertController(AlertService alertService, AlertSearchService searchService) {
        this.alertService = alertService;
        this.searchService = searchService;
    }

    /** Lists alerts from MySQL (source of truth), newest first, with optional exact filters. */
    @GetMapping
    public PageResponse<AlertResponse> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AlertResponse> result = alertService.list(status, customerId, severity, pageable)
                .map(AlertResponse::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /** Full-text + filter search over the Elasticsearch alert projection (for dashboards/triage). */
    @GetMapping("/search")
    public SearchResults<AlertDocument> search(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return searchService.search(
                new AlertQuery(text, severity, status, customerId, correlationId, from, to, page, size));
    }

    @GetMapping("/{id}")
    public AlertResponse get(@PathVariable String id) {
        return AlertResponse.from(alertService.get(id));
    }

    /** Investigator picks up an open alert; it is assigned to the authenticated user. */
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('INVESTIGATOR','ADMIN')")
    public AlertResponse acknowledge(@PathVariable String id, Authentication authentication) {
        return AlertResponse.from(alertService.acknowledge(id, authentication.getName()));
    }

    /** Investigator records a terminal disposition; emits {@code alert.resolved}. */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('INVESTIGATOR','ADMIN')")
    public AlertResponse resolve(@PathVariable String id,
                                 @Valid @RequestBody ResolveAlertRequest request,
                                 Authentication authentication) {
        return AlertResponse.from(
                alertService.resolve(id, request.resolution(), request.notes(), authentication.getName()));
    }
}
