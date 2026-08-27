package com.frauddetect.fraud.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the modern Elasticsearch Java client (Section 6).
 *
 * @param uris     Elasticsearch endpoint, e.g. {@code http://localhost:9200}
 * @param username optional basic-auth user (prod/secured clusters); blank = no auth (dev/compose)
 * @param password optional basic-auth password
 */
@ConfigurationProperties(prefix = "elasticsearch")
public record ElasticsearchProperties(
        String uris,
        String username,
        String password
) {
    public ElasticsearchProperties {
        if (uris == null || uris.isBlank()) {
            uris = "http://localhost:9200";
        }
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }
}
