package com.frauddetect.audit.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Indexes audit rows into the {@code audit-events} index and serves the compliance search API
 * (Section 5), using the modern typed Elasticsearch client.
 *
 * <p><b>Indexing is NOT best-effort here</b> — this is the key difference from the fraud hot path.
 * An audit record must never be silently lost, so an index failure is counted and then <em>rethrown</em>
 * ({@link AuditIndexException}); the Kafka listener lets it propagate to the container error handler,
 * which retries with backoff and finally routes to the DLT. The Kafka log remains the durable source of
 * truth, and Elasticsearch is a rebuildable projection.
 *
 * <p>Documents are keyed by the Avro {@code eventId}, so a redelivery overwrites the same row rather
 * than creating a duplicate — indexing is idempotent without a separate dedupe store.
 *
 * <p>Queries are assembled as Elasticsearch query-DSL JSON and submitted via {@code withJson}, keeping
 * the search code robust across client versions while still deserialising hits into typed records.
 */
@Service
public class AuditSearchService {

    private static final Logger log = LoggerFactory.getLogger(AuditSearchService.class);

    public static final String INDEX_AUDIT_EVENTS = "audit-events";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final List<String> TEXT_FIELDS = List.of("summary", "eventType", "reasonCode");

    private final ElasticsearchClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Counter indexed;
    private final Counter indexFailures;

    public AuditSearchService(ElasticsearchClient client, MeterRegistry meterRegistry) {
        this.client = client;
        this.indexed = Counter.builder("audit.events.indexed")
                .description("Audit events successfully indexed into Elasticsearch")
                .register(meterRegistry);
        this.indexFailures = Counter.builder("audit.events.index.failures")
                .description("Audit index attempts that failed and will be retried / dead-lettered")
                .register(meterRegistry);
    }

    /**
     * Indexes one audit row, keyed by its {@code eventId} for idempotency. Rethrows on failure so the
     * caller (the Kafka listener) does not commit the offset and the record is retried / dead-lettered.
     */
    public void index(AuditEventDocument doc) {
        try {
            client.index(i -> i.index(INDEX_AUDIT_EVENTS).id(doc.eventId()).document(doc));
            indexed.increment();
            log.debug("Indexed audit event {} ({})", doc.eventId(), doc.eventType());
        } catch (Exception ex) {
            indexFailures.increment();
            log.error("Failed to index audit event {} ({}): {}", doc.eventId(), doc.eventType(), ex.toString());
            throw new AuditIndexException(doc.eventId(), ex);
        }
    }

    public SearchResults<AuditEventDocument> search(AuditQuery query) {
        int page = Math.max(0, query.page());
        int size = query.size() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);
        String queryJson = buildQueryJson(query);
        int from = page * size;
        try {
            SearchResponse<AuditEventDocument> resp = client.search(s -> s
                            .index(INDEX_AUDIT_EVENTS)
                            .query(q -> q.withJson(new ByteArrayInputStream(queryJson.getBytes(StandardCharsets.UTF_8))))
                            .from(from)
                            .size(size)
                            .trackTotalHits(t -> t.enabled(true))
                            .sort(so -> so.field(f -> f.field("occurredAt").order(SortOrder.Desc))),
                    AuditEventDocument.class);
            List<AuditEventDocument> items = new ArrayList<>();
            resp.hits().hits().forEach(hit -> {
                if (hit.source() != null) {
                    items.add(hit.source());
                }
            });
            long total = resp.hits().total() != null ? resp.hits().total().value() : items.size();
            return SearchResults.of(items, total, page, size);
        } catch (Exception ex) {
            log.warn("Elasticsearch audit search failed: {}", ex.toString());
            return SearchResults.empty(page, size);
        }
    }

    /** Builds an Elasticsearch bool query as JSON from the optional criteria. */
    private String buildQueryJson(AuditQuery q) {
        ObjectNode bool = mapper.createObjectNode();
        ObjectNode boolBody = bool.putObject("bool");
        ArrayNode must = boolBody.putArray("must");
        ArrayNode filter = boolBody.putArray("filter");

        if (present(q.text())) {
            ObjectNode mm = must.addObject().putObject("multi_match");
            mm.put("query", q.text());
            ArrayNode fields = mm.putArray("fields");
            TEXT_FIELDS.forEach(fields::add);
        }
        addTerm(filter, "eventType", q.eventType());
        addTerm(filter, "customerId", q.customerId());
        addTerm(filter, "correlationId", q.correlationId());
        addTerm(filter, "transactionId", q.transactionId());

        if (q.from() != null || q.to() != null) {
            ObjectNode range = filter.addObject().putObject("range").putObject("occurredAt");
            if (q.from() != null) {
                range.put("gte", q.from().toString());
            }
            if (q.to() != null) {
                range.put("lte", q.to().toString());
            }
        }

        if (must.isEmpty() && filter.isEmpty()) {
            return "{\"match_all\":{}}";
        }
        return bool.toString();
    }

    private static void addTerm(ArrayNode filter, String field, String value) {
        if (present(value)) {
            filter.addObject().putObject("term").put(field, value);
        }
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    /** Raised when an audit row cannot be indexed; propagates to the Kafka error handler for retry/DLT. */
    public static class AuditIndexException extends RuntimeException {
        public AuditIndexException(String eventId, Throwable cause) {
            super("Failed to index audit event " + eventId, cause);
        }
    }
}
