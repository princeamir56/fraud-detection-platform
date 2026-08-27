package com.frauddetect.fraud.web;

import com.frauddetect.fraud.search.FraudEventDocument;
import com.frauddetect.fraud.search.FraudQuery;
import com.frauddetect.fraud.search.FraudSearchService;
import com.frauddetect.fraud.search.SearchResults;
import com.frauddetect.fraud.search.TransactionDocument;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Analyst-facing search API over Elasticsearch (Section 6): full-text plus exact filters and a time
 * range across indexed transactions and fraud events. Open to ANALYST/INVESTIGATOR/ADMIN (Section 11
 * RBAC). All parameters are optional; an empty query returns the most recent items.
 *
 * <p>{@code from}/{@code to} accept ISO-8601 instants (e.g. {@code 2026-08-01T00:00:00Z}).
 */
@RestController
@RequestMapping("/api/v1/search")
@PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')")
public class FraudSearchController {

    private final FraudSearchService searchService;

    public FraudSearchController(FraudSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/transactions")
    public SearchResults<TransactionDocument> transactions(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return searchService.searchTransactions(
                new FraudQuery(text, customerId, severity, decision, from, to, page, size));
    }

    @GetMapping("/fraud-events")
    public SearchResults<FraudEventDocument> fraudEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return searchService.searchFraudEvents(
                new FraudQuery(text, customerId, severity, decision, from, to, page, size));
    }
}
