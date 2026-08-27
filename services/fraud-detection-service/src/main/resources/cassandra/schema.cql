// ===========================================================================
// Cassandra schema for fraud-detection-service (Section 5).
//
// Query-first design (NOT relational): every table is shaped around the exact
// read the fraud hot path performs. All time-series tables share a bucketed
// partition key (customer_id, day_bucket) so a customer's rows are spread across
// day-sized partitions (bounded partition growth) while still allowing a single
// customer+day to be read with one partition hit and a clustering range.
//
// Clustering order (event_time DESC, transaction_id ASC) means the most recent
// activity is first — the velocity/feature reads want "the last N events".
//
// Retention is enforced with per-table default_time_to_live so old rows expire
// automatically (no batch cleanup): velocity is short-lived, features/history longer.
//
// Apply in dev:     cqlsh -f schema.cql
// Apply in compose: mounted into the Cassandra init container (see docker-compose).
// In prod, replace SimpleStrategy with NetworkTopologyStrategy and RF >= 3.
// ===========================================================================

CREATE KEYSPACE IF NOT EXISTS fraud
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE fraud;

// ---------------------------------------------------------------------------
// customer_behavior — rolling per-customer profile (point read + upsert).
// Query pattern: SELECT * FROM customer_behavior WHERE customer_id = ?;
// One row per customer, updated in place; no TTL (this is the live baseline).
// ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_behavior (
    customer_id               text PRIMARY KEY,
    tx_count                  bigint,
    amount_sum_30d            double,
    avg_amount_30d            double,
    known_countries           set<text>,
    known_devices             set<text>,
    known_merchant_categories set<text>,
    last_country              text,
    last_latitude             double,
    last_longitude            double,
    last_event_time           timestamp,
    updated_at                timestamp
);

// ---------------------------------------------------------------------------
// transaction_velocity — recent transaction stream for rolling-window counts.
// Query pattern (per day bucket, newest first, bounded by time):
//   SELECT * FROM transaction_velocity
//     WHERE customer_id = ? AND day_bucket IN (?, ?) AND event_time >= ?;
// Retention: 7 days (only recent activity feeds last-hour / last-24h windows).
// ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transaction_velocity (
    customer_id       text,
    day_bucket        text,
    event_time        timestamp,
    transaction_id    text,
    amount            double,
    currency          text,
    country_code      text,
    merchant_id       text,
    merchant_category text,
    device_id         text,
    status            text,
    PRIMARY KEY ((customer_id, day_bucket), event_time, transaction_id)
) WITH CLUSTERING ORDER BY (event_time DESC, transaction_id ASC)
  AND default_time_to_live = 604800;

// ---------------------------------------------------------------------------
// fraud_features — the exact feature vector + verdict the engine saw per tx.
// Query pattern: read back a customer/day window for audit and model replay.
// Retention: 90 days (investigation / model backtesting horizon).
// ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fraud_features (
    customer_id                 text,
    day_bucket                  text,
    event_time                  timestamp,
    transaction_id              text,
    amount                      double,
    avg_amount_30d              double,
    tx_count_last_hour          bigint,
    tx_count_last_24h           bigint,
    amount_sum_last_24h         double,
    distinct_countries_last_24h bigint,
    new_device                  boolean,
    new_merchant                boolean,
    failed_tx_last_hour         bigint,
    km_from_last_tx             double,
    seconds_since_last_tx       bigint,
    model_risk_score            double,
    score                       int,
    severity                    text,
    decision                    text,
    PRIMARY KEY ((customer_id, day_bucket), event_time, transaction_id)
) WITH CLUSTERING ORDER BY (event_time DESC, transaction_id ASC)
  AND default_time_to_live = 7776000;

// ---------------------------------------------------------------------------
// historical_activity — long-term per-customer transaction history.
// Query pattern: chronological history for a customer/day window.
// Retention: 365 days.
// ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS historical_activity (
    customer_id       text,
    day_bucket        text,
    event_time        timestamp,
    transaction_id    text,
    account_id        text,
    amount            double,
    currency          text,
    transaction_type  text,
    country_code      text,
    merchant_id       text,
    merchant_category text,
    score             int,
    severity          text,
    decision          text,
    PRIMARY KEY ((customer_id, day_bucket), event_time, transaction_id)
) WITH CLUSTERING ORDER BY (event_time DESC, transaction_id ASC)
  AND default_time_to_live = 31536000;
