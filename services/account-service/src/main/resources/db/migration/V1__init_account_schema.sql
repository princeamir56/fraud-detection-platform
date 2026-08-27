-- Account service schema (MySQL 8.4, InnoDB, utf8mb4).
-- Column types are chosen to match the JPA entity mappings so `ddl-auto: validate` passes.

CREATE TABLE accounts (
    id             VARCHAR(36)   NOT NULL,
    customer_id    VARCHAR(64)   NOT NULL,
    account_number VARCHAR(34)   NOT NULL,
    type           VARCHAR(16)   NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    balance        DECIMAL(19,4) NOT NULL,
    credit_limit   DECIMAL(19,4) NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    opened_at      DATETIME(6)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uk_account_number UNIQUE (account_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_account_customer ON accounts (customer_id);
CREATE INDEX idx_account_status   ON accounts (status);
