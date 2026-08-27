-- Fraud-detection-service schema (MySQL 8.4, InnoDB, utf8mb4).
-- Column types are chosen to match the JPA entity mappings so `ddl-auto: validate` passes:
--   * boolean  -> BIT        (Hibernate MySQL dialect renders boolean as `bit`)
--   * @Lob String -> LONGTEXT
--   * Instant  -> DATETIME(6)

-- ---------------------------------------------------------------------------
-- Configurable fraud rules (Section 9). Rules are DATA, not code: analysts tune
-- weights/thresholds via the REST API and the engine reloads on commit.
-- ---------------------------------------------------------------------------
CREATE TABLE fraud_rules (
    code              VARCHAR(64)   NOT NULL,
    name              VARCHAR(160)  NOT NULL,
    description       VARCHAR(512)  NOT NULL,
    rule_type         VARCHAR(48)   NOT NULL,
    weight            INT           NOT NULL,
    enabled           BIT           NOT NULL,
    threshold_numeric DECIMAL(19,4) NULL,
    threshold_int     INT           NULL,
    params_json       LONGTEXT      NULL,
    created_at        DATETIME(6)   NULL,
    updated_at        DATETIME(6)   NULL,
    version           BIGINT        NOT NULL,
    CONSTRAINT pk_fraud_rules PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_rule_enabled ON fraud_rules (enabled);
CREATE INDEX idx_rule_type    ON fraud_rules (rule_type);

-- Consumer-side dedupe: one row per fully-processed inbound Kafka eventId (idempotency).
CREATE TABLE processed_events (
    event_id       VARCHAR(80) NOT NULL,
    transaction_id VARCHAR(64) NULL,
    processed_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- Seed the detection catalogue: one enabled rule per RuleType (Section 9).
-- Weights intentionally may sum above 100 — the aggregate score saturates at 100,
-- so several concurrent signals push a transaction into HIGH/CRITICAL.
-- Threshold semantics (documented on each evaluator in RuleEvaluators):
--   threshold_numeric = absolute ceiling / min model score; threshold_int = count or km/h window.
-- ---------------------------------------------------------------------------
INSERT INTO fraud_rules
    (code, name, description, rule_type, weight, enabled, threshold_numeric, threshold_int, params_json, created_at, updated_at, version)
VALUES
    ('LARGE_AMOUNT', 'Large amount',
     'Transaction amount exceeds an absolute ceiling or a multiple of the customer''s 30-day average.',
     'LARGE_AMOUNT', 30, 1, 5000.0000, 10, NULL, NOW(6), NOW(6), 0),

    ('RAPID_VELOCITY', 'Rapid repeated transactions',
     'More transactions in the last hour than the configured limit.',
     'RAPID_VELOCITY', 25, 1, NULL, 5, NULL, NOW(6), NOW(6), 0),

    ('IMPOSSIBLE_TRAVEL', 'Impossible travel',
     'Implied travel speed since the previous transaction exceeds the configured km/h limit.',
     'IMPOSSIBLE_TRAVEL', 40, 1, NULL, 900, NULL, NOW(6), NOW(6), 0),

    ('SUSPICIOUS_COUNTRY', 'Suspicious country',
     'Transaction originates in a configured high-risk country, or the origin country changed.',
     'SUSPICIOUS_COUNTRY', 35, 1, NULL, NULL, '{"countries":["NG","RU","IR","KP","SY","VE"]}', NOW(6), NOW(6), 0),

    ('UNUSUAL_FREQUENCY', 'Unusual daily frequency',
     'Transactions in the last 24 hours exceed the configured daily limit.',
     'UNUSUAL_FREQUENCY', 20, 1, NULL, 20, NULL, NOW(6), NOW(6), 0),

    ('REPEATED_FAILURES', 'Repeated failed transactions',
     'The customer had at least this many blocked/declined attempts in the last hour.',
     'REPEATED_FAILURES', 30, 1, NULL, 3, NULL, NOW(6), NOW(6), 0),

    ('ABNORMAL_MERCHANT', 'Abnormal merchant category',
     'First time this customer transacts in this merchant category.',
     'ABNORMAL_MERCHANT', 15, 1, NULL, NULL, NULL, NOW(6), NOW(6), 0),

    ('SUSPICIOUS_DEVICE', 'Suspicious device',
     'First time this device is seen for the customer.',
     'SUSPICIOUS_DEVICE', 20, 1, NULL, NULL, NULL, NOW(6), NOW(6), 0),

    ('MODEL_RISK', 'Model risk score',
     'The risk-scoring model sub-score (0-1) meets the configured minimum; points scale with the score.',
     'MODEL_RISK', 40, 1, 0.7000, NULL, NULL, NOW(6), NOW(6), 0);
