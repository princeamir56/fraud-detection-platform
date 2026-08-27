package com.frauddetect.alert.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the modern Elasticsearch Java client. Bound from {@code elasticsearch.*}.
 * {@code uris} is a single origin (e.g. {@code http://localhost:9200}); credentials are optional and
 * only applied when both {@code username} and {@code password} are present.
 */
@ConfigurationProperties(prefix = "elasticsearch")
public record ElasticsearchProperties(String uris, String username, String password) {

    public ElasticsearchProperties {
        if (uris == null || uris.isBlank()) {
            uris = "http://localhost:9200";
        }
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
