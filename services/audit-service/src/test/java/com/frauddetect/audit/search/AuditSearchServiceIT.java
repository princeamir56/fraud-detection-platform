package com.frauddetect.audit.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Elasticsearch indexing + search round-trip against a real ES container
 * (Section 19). Verifies that the explicit mapping is applied (so {@code term} filters resolve against
 * {@code keyword} fields), that documents are keyed by {@code eventId} (a re-index overwrites rather
 * than duplicates), and that the query-DSL filters and full-text search behave as expected.
 *
 * <p>ES version is pinned to the {@code elasticsearch-java} client version for wire compatibility.
 */
@Testcontainers
class AuditSearchServiceIT {

    @Container
    static final ElasticsearchContainer ES = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.4"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    private static Rest5Client restClient;
    private static ElasticsearchClient client;
    private static AuditSearchService service;

    @BeforeAll
    static void setUp() {
        restClient = Rest5Client.builder(URI.create("http://" + ES.getHttpHostAddress())).build();
        JsonMapper jsonMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        jsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ElasticsearchTransport transport = new Rest5ClientTransport(restClient, new JacksonJsonpMapper(jsonMapper));
        client = new ElasticsearchClient(transport);
        service = new AuditSearchService(client, new SimpleMeterRegistry());

        // Apply the real mapping from the shipped resource, exactly as the app does at startup.
        new ElasticsearchIndexInitializer(client).run(null);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (restClient != null) {
            restClient.close();
        }
    }

    @Test
    void indexesAndSearchesByFilters() throws Exception {
        service.index(doc("evt-a", "TransactionCreated", "corr-1", "cust-1", "PURCHASE 100 USD"));
        service.index(doc("evt-b", "FraudDetected", "corr-1", "cust-1", "Fraud detected CRITICAL velocity"));
        service.index(doc("evt-c", "AlertCreated", "corr-2", "cust-2", "Alert created HIGH"));
        refresh();

        // match-all sees every indexed document
        assertThat(service.search(query(null, null, null, null)).total()).isEqualTo(3);

        // exact term filter on a keyword field
        SearchResults<AuditEventDocument> byCustomer = service.search(query(null, null, "cust-1", null));
        assertThat(byCustomer.total()).isEqualTo(2);
        assertThat(byCustomer.items()).extracting(AuditEventDocument::customerId).containsOnly("cust-1");

        // correlation id stitches a single request's fan-out together
        assertThat(service.search(query(null, null, null, "corr-1")).total()).isEqualTo(2);

        // event-type filter
        assertThat(service.search(query(null, "AlertCreated", null, null)).total()).isEqualTo(1);

        // full-text over the summary
        assertThat(service.search(query("velocity", null, null, null)).total()).isEqualTo(1);
    }

    @Test
    void reindexingSameEventIdOverwritesRatherThanDuplicates() throws Exception {
        service.index(doc("evt-dup", "TransactionCreated", "corr-9", "cust-9", "first"));
        service.index(doc("evt-dup", "TransactionCreated", "corr-9", "cust-9", "second"));
        refresh();

        SearchResults<AuditEventDocument> results = service.search(query(null, null, "cust-9", null));
        assertThat(results.total()).isEqualTo(1);
        assertThat(results.items().getFirst().summary()).isEqualTo("second");
    }

    private static AuditEventDocument doc(String eventId, String type, String corr, String customerId, String summary) {
        return AuditEventDocument.builder(type, eventId, corr, Instant.parse("2026-08-22T10:00:00Z"))
                .indexedAt(Instant.parse("2026-08-22T10:00:01Z"))
                .customerId(customerId)
                .summary(summary)
                .build();
    }

    private static AuditQuery query(String text, String eventType, String customerId, String correlationId) {
        return new AuditQuery(text, eventType, customerId, correlationId, null, null, null, 0, 20);
    }

    private static void refresh() throws Exception {
        client.indices().refresh(r -> r.index(AuditSearchService.INDEX_AUDIT_EVENTS));
    }
}
