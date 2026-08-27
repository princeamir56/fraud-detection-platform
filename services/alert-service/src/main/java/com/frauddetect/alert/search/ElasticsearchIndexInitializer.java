package com.frauddetect.alert.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Ensures the {@code alerts} index exists with an explicit mapping so exact-match filters resolve
 * against {@code keyword} fields, full-text search against {@code text} fields, and time-range against
 * {@code date} — rather than relying on dynamic mapping, which would type everything as text and break
 * term filters and range queries.
 *
 * <p>Best-effort and idempotent: an existing index is left untouched; if Elasticsearch is unreachable
 * at startup the service still boots, so a transient ES outage never blocks alert processing.
 */
@Component
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

    private final ElasticsearchClient client;

    public ElasticsearchIndexInitializer(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndex(AlertSearchService.INDEX_ALERTS, "elasticsearch/alerts-index.json");
    }

    private void ensureIndex(String index, String mappingResource) {
        try {
            boolean exists = client.indices().exists(e -> e.index(index)).value();
            if (exists) {
                log.info("Elasticsearch index '{}' already present", index);
                return;
            }
            try (InputStream is = new ClassPathResource(mappingResource).getInputStream()) {
                client.indices().create(c -> c.index(index).withJson(is));
            }
            log.info("Created Elasticsearch index '{}' from {}", index, mappingResource);
        } catch (Exception ex) {
            log.warn("Could not ensure Elasticsearch index '{}' at startup ({}); "
                    + "search will use whatever mapping exists", index, ex.toString());
        }
    }
}
