package com.frauddetect.fraud.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.frauddetect.fraud.engine.FraudScore;
import com.frauddetect.fraud.feature.TransactionFeatures;
import com.frauddetect.fraud.service.TransactionContext;
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
 * Indexes transactions and fraud verdicts into Elasticsearch and serves analyst search (Section 6),
 * using the modern typed client.
 *
 * <p><b>Indexing is best-effort.</b> Search/observability must never block or fail the fraud
 * decision, so index failures are caught, counted ({@code fraud.es.index.failures}) and logged — the
 * Cassandra writes and Kafka events remain the source of truth. This is the graceful-degradation
 * posture for the Elasticsearch hop.
 *
 * <p>Queries are assembled as Elasticsearch query-DSL JSON and submitted via {@code withJson}. This
 * keeps the search code robust across client versions (the strongly-typed query-builder API evolves
 * between releases) while still deserialising hits into typed document records.
 */
@Service
public class FraudSearchService {

    private static final Logger log = LoggerFactory.getLogger(FraudSearchService.class);

    public static final String INDEX_TRANSACTIONS = "transactions";
    public static final String INDEX_FRAUD_EVENTS = "fraud-events";

    private static final int MAX_PAGE_SIZE = 200;
    private static final List<String> TX_TEXT_FIELDS =
            List.of("merchantCategory", "type", "countryCode", "currency", "merchantId");
    private static final List<String> FRAUD_TEXT_FIELDS =
            List.of("primaryReason", "severity", "decision", "countryCode");

    private final ElasticsearchClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Counter indexFailures;

    public FraudSearchService(ElasticsearchClient client, MeterRegistry meterRegistry) {
        this.client = client;
        this.indexFailures = Counter.builder("fraud.es.index.failures")
                .description("Elasticsearch index operations that failed (fraud pipeline continues)")
                .register(meterRegistry);
    }

    // ---- indexing (best-effort) ----

    public void indexTransaction(TransactionContext ctx, TransactionFeatures features, FraudScore score) {
        TransactionDocument doc = new TransactionDocument(
                ctx.transactionId(), ctx.accountId(), ctx.customerId(),
                features.amount(), ctx.currency(), ctx.type(), ctx.countryCode(),
                ctx.merchantId(), ctx.merchantCategory(), ctx.deviceId(), ctx.ipAddress(),
                ctx.latitude(), ctx.longitude(),
                score.score(), score.severity().name(), score.decision().name(), score.modelRiskScore(),
                ctx.occurredAt(), ctx.correlationId());
        safeIndex(INDEX_TRANSACTIONS, ctx.transactionId(), doc);
    }

    public void indexFraudEvent(String eventId, TransactionContext ctx, FraudScore score) {
        FraudEventDocument doc = new FraudEventDocument(
                eventId, ctx.transactionId(), ctx.customerId(), ctx.accountId(),
                score.score(), score.severity().name(), score.decision().name(),
                score.primaryReason(), score.triggeredRuleCodes(),
                ctx.amount().doubleValue(), ctx.currency(), ctx.countryCode(),
                score.modelRiskScore(), ctx.occurredAt(), ctx.correlationId());
        safeIndex(INDEX_FRAUD_EVENTS, eventId, doc);
    }

    private void safeIndex(String index, String id, Object document) {
        try {
            client.index(i -> i.index(index).id(id).document(document));
            log.debug("Indexed {} doc id={}", index, id);
        } catch (Exception ex) {
            indexFailures.increment();
            log.warn("Elasticsearch index into {} (id={}) failed: {}", index, id, ex.toString());
        }
    }

    // ---- search ----

    public SearchResults<TransactionDocument> searchTransactions(FraudQuery query) {
        return search(INDEX_TRANSACTIONS, query, TX_TEXT_FIELDS, TransactionDocument.class);
    }

    public SearchResults<FraudEventDocument> searchFraudEvents(FraudQuery query) {
        return search(INDEX_FRAUD_EVENTS, query, FRAUD_TEXT_FIELDS, FraudEventDocument.class);
    }

    private <T> SearchResults<T> search(String index, FraudQuery query, List<String> textFields, Class<T> type) {
        int page = Math.max(0, query.page());
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), MAX_PAGE_SIZE);
        String queryJson = buildQueryJson(query, textFields);
        int from = page * size;
        try {
            SearchResponse<T> resp = client.search(s -> s
                            .index(index)
                            .query(q -> q.withJson(new ByteArrayInputStream(queryJson.getBytes(StandardCharsets.UTF_8))))
                            .from(from)
                            .size(size)
                            .trackTotalHits(t -> t.enabled(true))
                            .sort(so -> so.field(f -> f.field("occurredAt").order(SortOrder.Desc))),
                    type);
            List<T> items = new ArrayList<>();
            resp.hits().hits().forEach(hit -> {
                if (hit.source() != null) {
                    items.add(hit.source());
                }
            });
            long total = resp.hits().total() != null ? resp.hits().total().value() : items.size();
            return SearchResults.of(items, total, page, size);
        } catch (Exception ex) {
            log.warn("Elasticsearch search on {} failed: {}", index, ex.toString());
            return SearchResults.empty(page, size);
        }
    }

    /** Builds an Elasticsearch bool query as JSON from the optional criteria. */
    private String buildQueryJson(FraudQuery q, List<String> textFields) {
        ObjectNode bool = mapper.createObjectNode();
        ObjectNode boolBody = bool.putObject("bool");
        ArrayNode must = boolBody.putArray("must");
        ArrayNode filter = boolBody.putArray("filter");

        if (present(q.text())) {
            ObjectNode mm = must.addObject().putObject("multi_match");
            mm.put("query", q.text());
            ArrayNode fields = mm.putArray("fields");
            textFields.forEach(fields::add);
        }
        addTerm(filter, "customerId", q.customerId());
        addTerm(filter, "severity", q.severity());
        addTerm(filter, "decision", q.decision());

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
}
