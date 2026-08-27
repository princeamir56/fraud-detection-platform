-- Notification service schema (MySQL 8.4, InnoDB, utf8mb4).
-- Column types match the JPA entity mappings so `ddl-auto: validate` passes at startup.

CREATE TABLE notifications (
    id             VARCHAR(36)   NOT NULL,
    alert_id       VARCHAR(36)   NOT NULL,
    transaction_id VARCHAR(36)   NULL,
    customer_id    VARCHAR(36)   NOT NULL,
    channel        VARCHAR(16)   NOT NULL,
    recipient      VARCHAR(255)  NOT NULL,
    subject        VARCHAR(255)  NOT NULL,
    body           VARCHAR(2000) NOT NULL,
    severity       VARCHAR(16)   NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    correlation_id VARCHAR(64)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_notification_customer ON notifications (customer_id, created_at);
CREATE INDEX idx_notification_alert    ON notifications (alert_id);
CREATE INDEX idx_notification_status   ON notifications (status);

-- Consumer-side dedupe: one row per processed Kafka eventId (alert.created).
CREATE TABLE processed_events (
    event_id     VARCHAR(80) NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
