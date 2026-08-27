-- Alert service schema (MySQL 8.4, InnoDB, utf8mb4).
-- Column types match the JPA entity mappings so `ddl-auto: validate` passes at startup.

CREATE TABLE alerts (
    id               VARCHAR(36)   NOT NULL,
    transaction_id   VARCHAR(36)   NOT NULL,
    customer_id      VARCHAR(36)   NOT NULL,
    account_id       VARCHAR(36)   NOT NULL,
    severity         VARCHAR(16)   NOT NULL,
    score            INT           NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    title            VARCHAR(255)  NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    primary_reason   VARCHAR(500)  NULL,
    assigned_to      VARCHAR(100)  NULL,
    resolution       VARCHAR(32)   NULL,
    resolved_by      VARCHAR(100)  NULL,
    resolution_notes VARCHAR(2000) NULL,
    resolved_at      DATETIME(6)   NULL,
    correlation_id   VARCHAR(64)   NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL,
    CONSTRAINT pk_alerts PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_alert_status      ON alerts (status);
CREATE INDEX idx_alert_customer    ON alerts (customer_id, created_at);
CREATE INDEX idx_alert_transaction ON alerts (transaction_id);
CREATE INDEX idx_alert_severity    ON alerts (severity);

-- Consumer-side dedupe: one row per processed Kafka eventId (fraud.detected).
CREATE TABLE processed_events (
    event_id     VARCHAR(80) NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
