package com.frauddetect.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Transaction service — REST entry point of the platform. Persists transactions to MySQL (PENDING),
 * emits {@code transaction.created} (Avro), and consumes {@code fraud.score.calculated} to finalise
 * each transaction's lifecycle (COMPLETED | FLAGGED | REJECTED).
 *
 * <p>JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate}) is enabled in
 * {@link com.frauddetect.transaction.config.JpaAuditingConfig} rather than here, so web-only test
 * slices don't activate the auditing registrar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
