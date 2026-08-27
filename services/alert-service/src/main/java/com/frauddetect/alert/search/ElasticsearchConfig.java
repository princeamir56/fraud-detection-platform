package com.frauddetect.alert.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Wires the modern, typed Elasticsearch Java client — explicitly NOT the deprecated High Level Rest
 * Client. The low-level {@link Rest5Client} (Apache HttpClient 5, async) owns the HTTP connection
 * pool; the {@link Rest5ClientTransport} adapts it with a Jackson JSON-P mapper configured for
 * ISO-8601 timestamps (so {@code Instant} fields map to ES {@code date}); {@link ElasticsearchClient}
 * is the typed façade used to index and search alerts. Beans expose {@code close()} so connections
 * drain on shutdown.
 */
@Configuration(proxyBeanMethods = false)
public class ElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Bean(destroyMethod = "close")
    public Rest5Client elasticsearchRestClient(ElasticsearchProperties props) {
        log.info("Creating Elasticsearch Rest5Client for {} (auth={})", props.uris(), props.hasCredentials());
        return new Rest5ClientBuilderHolder(props).build();
    }

    @Bean(destroyMethod = "close")
    public ElasticsearchTransport elasticsearchTransport(Rest5Client elasticsearchRestClient) {
        JsonMapper jsonMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        jsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Rest5ClientTransport(elasticsearchRestClient, new JacksonJsonpMapper(jsonMapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport elasticsearchTransport) {
        return new ElasticsearchClient(elasticsearchTransport);
    }

    /** Small helper so the builder + optional basic-auth wiring stays readable. */
    private record Rest5ClientBuilderHolder(ElasticsearchProperties props) {
        Rest5Client build() {
            var builder = Rest5Client.builder(URI.create(props.uris()));
            if (props.hasCredentials()) {
                BasicCredentialsProvider credentials = new BasicCredentialsProvider();
                credentials.setCredentials(new AuthScope(null, -1),
                        new UsernamePasswordCredentials(props.username(), props.password().toCharArray()));
                builder.setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(credentials));
            }
            return builder.build();
        }
    }
}
