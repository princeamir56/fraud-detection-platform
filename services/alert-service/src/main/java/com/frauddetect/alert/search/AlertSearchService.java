package com.frauddetect.alert.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.frauddetect.alert.domain.AlertEntity;
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
 * Indexes alerts into the {@code alerts} Elasticsearch index and serves analyst triage search using
 * the modern typed client.
 *
 * <p><b>Indexing is best-effort.</b> MySQL is the source of truth for alerts; the Elasticsearch
 * projection exists only for search and Kibana dashboards. So index failures are caught, counted
 * ({@code alert.es.index.failures}) and logged rather than propagated — a transient ES outage must
 * never fail an alert creation or a resolution (which have already been committed to MySQL and
 * emitted to Kafka). This is the graceful-degradation posture for the Elasticsearch hop.
 *
 * <p>Queries are assembled as Elasticsearch query-DSL JSON and submitted via {@code withJson}, which
 * keeps search robust across client versions while still deserialising hits into typed records.
 */
@Service
public class AlertSearchService {

    private static final Logger log = LoggerFactory.getLogger(AlertSearchService.class);

    public static final String INDEX_ALERTS = "alerts";

    private static final int MAX_PAGE_SIZE = 200;
    private static final List<String> TEXT_FIELDS =
            List.of("title", "primaryReason", "severity", "status");

    private final ElasticsearchClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Counter indexFailures;

    public AlertSearchService(ElasticsearchClient client, MeterRegistry meterRegistry) {
        this.client = client;
        this.indexFailures = Counter.builder("alert.es.index.failures")
                .description("Elasticsearch index operations that failed (alert pipeline continues)")
                .register(meterRegistry);
    }

    // ---- indexing (best-effort) ----

    /** Projects an alert row to its search document and indexes it (doc id = alertId, overwrite-in-place). */
    public void index(AlertEntity alert) {
        AlertDocument doc = new AlertDocument(
                alert.getId(), alert.getTransactionId(), alert.getCustomerId(), alert.getAccountId(),
                alert.getSeverity(), alert.getScore(), alert.getStatus().name(),
                alert.getTitle(), alert.getPrimaryReason(),
                alert.getResolution() != null ? alert.getResolution().name() : null,
                alert.getResolvedBy(),
                alert.getCreatedAt(), alert.getResolvedAt(), alert.getCorrelationId());
        try {
            client.index(i -> i.index(INDEX_ALERTS).id(doc.alertId()).document(doc));
            log.debug("Indexed alert doc id={}", doc.alertId());
        } catch (Exception ex) {
            indexFailures.increment();
            log.warn("Elasticsearch index of alert id={} failed: {}", doc.alertId(), ex.toString());
        }
    }

    // ---- search ----

    public SearchResults<AlertDocument> search(AlertQuery query) {
        int page = Math.max(0, query.page());
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), MAX_PAGE_SIZE);
        String queryJson = buildQueryJson(query);
        int from = page * size;
        try {
            SearchResponse<AlertDocument> resp = client.search(s -> s
                            .index(INDEX_ALERTS)
                            .query(q -> q.withJson(new ByteArrayInputStream(queryJson.getBytes(StandardCharsets.UTF_8))))
                            .from(from)
                            .size(size)
                            .trackTotalHits(t -> t.enabled(true))
                            .sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc))),
                    AlertDocument.class);
            List<AlertDocument> items = new ArrayList<>();
            resp.hits().hits().forEach(hit -> {
                if (hit.source() != null) {
                    items.add(hit.source());
                }
            });
            long total = resp.hits().total() != null ? resp.hits().total().value() : items.size();
            return SearchResults.of(items, total, page, size);
        } catch (Exception ex) {
            log.warn("Elasticsearch alert search failed: {}", ex.toString());
            return SearchResults.empty(page, size);
        }
    }

    /** Builds an Elasticsearch bool query as JSON from the optional criteria. */
    private String buildQueryJson(AlertQuery q) {
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
        addTerm(filter, "customerId", q.customerId());
        addTerm(filter, "severity", q.severity());
        addTerm(filter, "status", q.status());
        addTerm(filter, "correlationId", q.correlationId());

        if (q.from() != null || q.to() != null) {
            ObjectNode range = filter.addObject().putObject("range").putObject("createdAt");
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
}
