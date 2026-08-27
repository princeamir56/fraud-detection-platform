-- Transaction service schema (MySQL 8.4, InnoDB, utf8mb4).
-- Column types chosen to match the JPA entity mappings so `ddl-auto: validate` passes.

CREATE TABLE transactions (
    id                VARCHAR(36)   NOT NULL,
    account_id        VARCHAR(64)   NOT NULL,
    customer_id       VARCHAR(64)   NOT NULL,
    amount            DECIMAL(19,4) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    type              VARCHAR(16)   NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    merchant_id       VARCHAR(64)   NULL,
    merchant_category VARCHAR(64)   NULL,
    country_code      VARCHAR(2)    NOT NULL,
    city              VARCHAR(128)  NULL,
    latitude          DOUBLE        NULL,
    longitude         DOUBLE        NULL,
    device_id         VARCHAR(128)  NULL,
    ip_address        VARCHAR(45)   NULL,
    channel           VARCHAR(16)   NOT NULL,
    fraud_score       INT           NULL,
    severity          VARCHAR(16)   NULL,
    decision          VARCHAR(16)   NULL,
    reason_code       VARCHAR(48)   NULL,
    reason            VARCHAR(512)  NULL,
    correlation_id    VARCHAR(64)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_tx_account  ON transactions (account_id, created_at);
CREATE INDEX idx_tx_customer ON transactions (customer_id, created_at);
CREATE INDEX idx_tx_status   ON transactions (status);

-- API-level idempotency: one row per client Idempotency-Key.
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(80) NOT NULL,
    transaction_id  VARCHAR(36) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Consumer-side dedupe: one row per processed Kafka eventId.
CREATE TABLE processed_events (
    event_id     VARCHAR(80) NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
