-- =====================================================================================
-- customer-service schema (MySQL 8.4, InnoDB, utf8mb4)
-- Owns identity (users) + customer profiles. Column types mirror the JPA entities so that
-- Hibernate `ddl-auto: validate` passes (Section 6):
--   * String(36) id      -> VARCHAR(36)
--   * Instant             -> DATETIME(6)
--   * boolean             -> BIT        (Hibernate MySQL dialect renders boolean as `bit`)
--   * long  @Version      -> BIGINT
-- Credentials are stored ONLY as BCrypt hashes (never plaintext); the default admin is
-- bootstrapped at runtime by DefaultAdminInitializer, so no password hash is baked into SQL.
-- =====================================================================================

CREATE TABLE customers (
    id           VARCHAR(36)  NOT NULL,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    phone        VARCHAR(32)  NULL,
    country_code VARCHAR(2)   NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    version      BIGINT       NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uk_customer_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_customer_status  ON customers (status);
CREATE INDEX idx_customer_country ON customers (country_code);

CREATE TABLE users (
    id            VARCHAR(36)  NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    roles         VARCHAR(255) NOT NULL,
    enabled       BIT          NOT NULL,
    customer_id   VARCHAR(36)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_customer ON users (customer_id);
