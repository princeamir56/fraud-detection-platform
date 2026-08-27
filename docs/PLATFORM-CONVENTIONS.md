# Platform Conventions (build contract)

> Single reference every service module conforms to. Read this before touching any service.
> Group id `com.frauddetect`, version `1.0.0`, Java 21, Spring Boot 4.1.x. No Lombok.

## Service registry — ports & responsibilities

| Service | REST port | gRPC port | MySQL schema | Owns (datastore) | Kafka consumer group |
|---|---|---|---|---|---|
| api-gateway | 8080 | — | — | — (edge; JWT validation, routing, rate limit) | — |
| customer-service | 8081 | — | `fraud_customer` | MySQL: `users`, `customers` (identity + profile, issues JWT) | — |
| account-service | 8082 | — | `fraud_account` | MySQL: `accounts` | — |
| transaction-service | 8083 | — | `fraud_transaction` | MySQL: `transactions`, `idempotency_keys` | `transaction-service` |
| fraud-detection-service | 8084 | — | `fraud_detection` | MySQL: `fraud_rules`; Cassandra: `customer_behavior`, `transaction_velocity`, `fraud_features`, `historical_activity`; ES: `transactions`, `fraud-events` | `fraud-detection` |
| risk-scoring-service | 8085 | 9095 | — | stateless compute (gRPC server) | — |
| alert-service | 8086 | — | `fraud_alert` | MySQL: `alerts`, `alert_metadata`, `investigation_cases`; ES: `alerts`, `investigation-events` | `alert-service` |
| notification-service | 8087 | — | `fraud_notification` | MySQL: `notifications` (mock delivery) | `notification-service` |
| audit-service | 8088 | — | — | ES: `audit-events` | `audit-service` |

`management.server.port` (actuator) = REST port; endpoints under `/actuator`.

## Packages
`com.frauddetect.<service>` where `<service>` ∈ {gateway, customer, account, transaction, fraud, risk, alert, notification, audit}. Sub-packages: `config`, `web` (controllers), `service`, `domain` (entities), `repository`, `dto`, `messaging` (Kafka), `client` (gRPC/REST clients).

Generated code: Avro → `com.frauddetect.avro.events.*`; gRPC → `com.frauddetect.grpc.risk.*`.

## End-to-end event flow (the demo path)
```
Client → api-gateway → transaction-service  POST /api/v1/transactions
  → MySQL persist (PENDING) → publish transaction.created (Avro)
fraud-detection-service consumes transaction.created:
  → read/update velocity+behavior (Cassandra)
  → build TransactionRiskFeatures → gRPC CalculateRiskScore → risk-scoring-service (blocking, deadline+circuit-breaker)
  → rule engine aggregates 0-100 score + severity + decision
  → write fraud_features/historical_activity (Cassandra); index transactions + fraud-events (ES)
  → publish fraud.score.calculated; if HIGH/CRITICAL also publish fraud.detected
transaction-service consumes fraud.score.calculated → update status (COMPLETED|FLAGGED|REJECTED)
  → publish transaction.completed | transaction.rejected
alert-service consumes fraud.detected → create alert (MySQL) + index (ES) → publish alert.created
notification-service consumes alert.created → deliver (mock)
audit-service consumes ALL events → index audit-events (ES)
```

## Kafka
- Bootstrap: `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`; Schema Registry `${SCHEMA_REGISTRY_URL:...}` → **compose runs Schema Registry on container port `8081`, host-mapped to `8090`** (internal `http://schema-registry:8081`, external `http://localhost:8090`). Host port 8090 is chosen to avoid clashing with any service REST port (8080–8088) or the risk-scoring gRPC port (9095).
- Producer: `KafkaAvroSerializer`, `acks=all`, `enable.idempotence=true`, `retries=Integer.MAX`, `max.in.flight=5`, key = entity id (partition affinity per customer/account).
- Consumer: `KafkaAvroDeserializer`, `specific.avro.reader=true`, `isolation.level=read_committed`, `enable.auto.commit=false`, manual/`RECORD` ack via container ack-mode, `auto.offset.reset=earliest`.
- Reliability: `DefaultErrorHandler` + `ExponentialBackOff` + `DeadLetterPublishingRecorder` → topic `<topic>.DLT`. Idempotency by `eventId` (consumer-side dedupe where side-effects are non-idempotent).
- Topic names: `com.frauddetect.common.constants.KafkaTopics`.

## gRPC
- risk-scoring-service starts an `io.grpc.Server` (netty-shaded) on `GRPC_PORT` (9095) via a `SmartLifecycle` bean — **not** an uncertain third-party starter, so it compiles against stable grpc-java APIs only.
- fraud-detection-service holds a `ManagedChannel` bean + blocking stub; every call sets a **deadline** (`withDeadlineAfter`) and is wrapped in a Resilience4j `CircuitBreaker` + `TimeLimiter`; on open circuit / timeout it **degrades gracefully** (rule-only score, `modelRiskScore=null`).

## Security (all REST services)
- Stateless `SecurityFilterChain`, `SessionCreationPolicy.STATELESS`, CSRF disabled (token auth).
- Custom `JwtAuthenticationFilter` uses `common`'s `JwtService`; roles claim → `ROLE_*` authorities.
- Method security via `@EnableMethodSecurity` + `@PreAuthorize`. Roles: ADMIN, ANALYST, INVESTIGATOR, CUSTOMER.
- `security.jwt.secret` from env `JWT_SECRET` (never in code). BCrypt for password hashing (customer-service).
- Actuator health/info + swagger open; everything else authenticated.

## Config & profiles
- `application.yml` with sane localhost defaults; profile `docker` (compose hostnames) and `k8s` (env/config-driven).
- All infra endpoints/secrets via env vars with localhost fallbacks: `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `SCHEMA_REGISTRY_URL`, `CASSANDRA_CONTACT_POINTS`, `ELASTICSEARCH_URIS`, `RISK_GRPC_HOST/PORT`, `JWT_SECRET`.

## Observability
- `spring-boot-starter-actuator`, `micrometer-registry-prometheus` (scrape `/actuator/prometheus`), `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` (`MANAGEMENT_OTLP_TRACING_ENDPOINT`).
- Structured JSON logging with `%X{correlationId}`, `traceId`, `spanId` in MDC (logback pattern in each service).

## Persistence conventions
- JPA entities: `@Entity`, surrogate `String`/`UUID` ids where natural, `@Version` optimistic locking on mutable aggregates, auditing (`created_at`, `updated_at`) via `@EntityListeners(AuditingEntityListener.class)`.
- Flyway migrations under `src/main/resources/db/migration/V1__*.sql` (MySQL 8.4, `utf8mb4`, InnoDB).
- Cassandra: query-first tables (see docs/database.md), TTL for retention, `@Table`/`@PrimaryKeyClass`.
- Elasticsearch: modern `co.elastic.clients.ElasticsearchClient` bean; index helpers create index-with-mapping on startup if absent.

## HTTP API base path
`/api/v1/...` for all business endpoints. DTOs are Java `record`s with Jakarta Bean Validation.

## Dockerfile (every service)
Multi-stage: `eclipse-temurin:21-jdk` (build via wrapper) → `eclipse-temurin:21-jre` runtime. Non-root UID 1001, group 0, read-only-friendly, `ENTRYPOINT exec java -jar app.jar`. OpenShift: no fixed UID assumption, group-writable.
