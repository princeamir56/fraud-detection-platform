package com.frauddetect.audit.web;

import com.frauddetect.audit.search.AuditEventDocument;
import com.frauddetect.audit.search.AuditQuery;
import com.frauddetect.audit.search.AuditSearchService;
import com.frauddetect.audit.search.SearchResults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Compliance search over the append-only audit trail (Section 11). Restricted to INVESTIGATOR/ADMIN —
 * the audit log spans every customer and event and is more sensitive than analyst fraud search, so
 * ANALYST/CUSTOMER are intentionally excluded. All parameters are optional; an empty query returns the
 * most recent events across all types.
 *
 * <p>{@code correlationId} is the most useful filter for incident forensics: it stitches together every
 * event a single request produced as it fanned out across the services. {@code from}/{@code to} accept
 * ISO-8601 instants (e.g. {@code 2026-08-01T00:00:00Z}).
 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('INVESTIGATOR','ADMIN')")
public class AuditController {

    private final AuditSearchService auditSearchService;

    public AuditController(AuditSearchService auditSearchService) {
        this.auditSearchService = auditSearchService;
    }

    @GetMapping("/events")
    public SearchResults<AuditEventDocument> events(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return auditSearchService.search(
                new AuditQuery(text, eventType, customerId, correlationId, transactionId, from, to, page, size));
    }
}
