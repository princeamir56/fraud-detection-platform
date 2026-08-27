package com.frauddetect.audit.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Elasticsearch connection settings (Section 5). Bound from the {@code elasticsearch.*} tree. Empty
 * credentials mean "no auth" (the local dev cluster runs security-disabled); populate
 * {@code ELASTICSEARCH_USERNAME}/{@code ELASTICSEARCH_PASSWORD} in secured environments.
 *
 * @param uris     comma-free single origin, e.g. {@code http://localhost:9200}
 * @param username optional basic-auth user (blank = anonymous)
 * @param password optional basic-auth password
 */
@ConfigurationProperties(prefix = "elasticsearch")
public record ElasticsearchProperties(String uris, String username, String password) {

    public ElasticsearchProperties {
        if (uris == null || uris.isBlank()) {
            uris = "http://localhost:9200";
        }
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
