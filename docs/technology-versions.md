# Technology Versions

> **Version policy (Section 0).** This platform pins the **latest stable + compatible**
> release of each technology, never alpha/beta/milestone/RC/nightly and never an EOL line.
> Versions are centralized in the parent [`pom.xml`](../pom.xml) `<properties>` block and in
> [`deploy/docker/docker-compose.yml`](../deploy/docker/docker-compose.yml) so there is a single
> source of truth.
>
> ⚠️ **Build-time verification required.** This repository was authored in an isolated
> environment with **no access to Maven Central, Docker Hub, or vendor release feeds**, so the
> exact patch numbers below reflect the known release cadence as of **August 2026** and must be
> re-confirmed before a production build. Run [`scripts/check-versions.sh`](../scripts/check-versions.sh)
> (documented at the end of this file) on a networked machine; it queries Maven Central metadata
> and the Docker registry and prints the newest stable patch for every coordinate. Bump the
> property, re-run `./mvnw -q dependency:tree`, and only commit if the tree resolves cleanly.

## Runtime & build platform

| Technology | Selected version | Why selected | Compatibility notes | Official source |
|---|---|---|---|---|
| **Java (JDK)** | **21 (21.0.x LTS, latest CPU patch)** | Java 21 is an LTS release (GA Sep 2023) with multi-year security patches; we track the latest quarterly Critical Patch Update. Matches the sibling Spring Boot projects and compiles on the local JDK 23 via `--release 21`. | Compiled and run with `--release 21`. Uses stable language features only (records, pattern matching, virtual threads, sealed types). No preview/incubator flags. Spring Boot 4.1 baseline is Java 17, so 21 is fully supported. | https://openjdk.org/projects/jdk/21/ · https://www.oracle.com/java/technologies/java-se-support-roadmap.html |
| **Spring Boot** | **4.1.x (latest 4.1 patch)** | Newest stable feature line of Spring Boot 4; built on Spring Framework 7 and Jakarta EE 11. Long OSS support window. | Requires Java 17+. Jakarta namespace (`jakarta.*`). Uses `RestClient`/`RestTemplate`, `@HttpExchange` interface clients, and built-in Micrometer observation. Avoids APIs removed in the 3.x→4.x migration. | https://spring.io/projects/spring-boot · https://github.com/spring-projects/spring-boot/wiki |
| **Spring Framework** | **7.0.x** (managed transitively by Spring Boot 4.1) | Comes from the Boot BOM; not pinned independently. | JSpecify null-safety annotations; `RestClient` HTTP interface; PathPattern routing. | https://spring.io/projects/spring-framework |
| **Spring Cloud Gateway** | **2025.1.3 ("Oakwood" line)** | Reactive, non-blocking edge gateway; first-class Spring Boot integration for routing, filters, rate limiting. | This train targets Boot **4.0.x** and no Spring Cloud train targets 4.1.x yet, so the gateway sets `spring.cloud.compatibility-verifier.enabled=false`. Gateway Server WebFlux runs cleanly on Boot 4.1.0 — proven by the gateway's `@SpringBootTest` context-load test. We use the *Server WebFlux* artifact (`spring-cloud-starter-gateway-server-webflux`). Re-enable the verifier once a Boot 4.1-aligned train ships. | https://spring.io/projects/spring-cloud-gateway · https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions |
| **Spring gRPC** | **latest stable aligned to Boot 4.1** | Official Spring gRPC project provides Boot auto-configuration for gRPC servers/clients (supersedes third-party `grpc-spring-boot-starter` for Boot 4). | Depends on `grpc-java`; keep `grpc.version` and Spring gRPC in step. Server runs on a dedicated port (9090s), separate from the REST/actuator port. | https://spring.io/projects/spring-grpc |
| **Maven** | **3.9.x (wrapper-pinned)** | Stable Maven 3.9 line; reproducible via the Maven Wrapper committed in the repo. | `./mvnw` downloads the pinned distribution on first run. Maven 4 is intentionally *not* used yet for maximum plugin compatibility. | https://maven.apache.org/ |

## Messaging, serialization & RPC

| Technology | Selected version | Why selected | Compatibility notes | Official source |
|---|---|---|---|---|
| **Apache Kafka (broker)** | **4.0.x (KRaft mode, ZooKeeper-free)** | Current stable Kafka; KRaft is the default and ZooKeeper is removed. High-throughput event backbone. | Broker image via Confluent Platform (below). Clients come from `spring-kafka`. KRaft requires no ZooKeeper container. | https://kafka.apache.org/documentation/ |
| **Confluent Platform** (Kafka + Schema Registry + Kafka UI ecosystem) | **8.0.x (Kafka 4.0 line)** | Provides the Schema Registry required for Avro + versioned schema evolution, plus tested broker images. | Confluent `8.0.x` tracks Kafka `4.0.x`. The `kafka-avro-serializer` client version **must match** the Schema Registry line. | https://docs.confluent.io/platform/current/installation/versions-interoperability.html |
| **Confluent Schema Registry** | **8.0.x** | Central registry enforcing Avro compatibility (BACKWARD by default). | Serializers auto-register schemas; compatibility mode configured per subject. | https://docs.confluent.io/platform/current/schema-registry/ |
| **Apache Avro** | **1.12.x** | Compact binary domain-event serialization with schema evolution; industry standard with Kafka. | `avro-maven-plugin` generates `SpecificRecord` classes from `.avsc` at build time. Keep plugin + `kafka-avro-serializer`'s bundled Avro aligned. | https://avro.apache.org/docs/ |
| **gRPC (grpc-java)** | **1.7x.x (latest stable)** | Low-latency, strongly-typed synchronous RPC for the fraud→risk-scoring hot path. | Netty transport (`grpc-netty-shaded`). Version must be compatible with the Protobuf runtime and Spring gRPC. | https://github.com/grpc/grpc-java/releases |
| **Protocol Buffers (protobuf-java)** | **4.29.x** | Schema/IDL + wire format for gRPC contracts. | protobuf-java 4.x pairs with modern grpc-java. `.proto` uses `proto3`. Compiled by `protobuf-maven-plugin` using OS-classified `protoc` + `protoc-gen-grpc-java`. | https://protobuf.dev/ · https://github.com/protocolbuffers/protobuf/releases |

## Datastores & search

| Technology | Selected version | Why selected | Compatibility notes | Official source |
|---|---|---|---|---|
| **MySQL** | **8.4 LTS** | LTS release for transactional/relational data (customers, accounts, transactions, rules, users, cases, alert metadata). Long support horizon. | Driver `com.mysql:mysql-connector-j`. InnoDB, `utf8mb4`. Managed schema via **Flyway**. MySQL 9.x is an *innovation* line — LTS chosen for stability. | https://dev.mysql.com/doc/relnotes/mysql/8.4/en/ |
| **Flyway** | **latest stable managed by Boot 4.1 BOM** | Versioned, forward-only SQL migrations checked into VCS. Chosen over Liquibase for plain-SQL clarity. | `flyway-mysql` module required for MySQL 8.4. Migrations under `db/migration`. | https://documentation.red-gate.com/flyway |
| **Apache Cassandra** | **5.0.x** | Wide-column store for high-volume behavioral/time-series data (velocity, features, behavior, history). Linear write scalability, TTL-based retention. | Spring Data Cassandra + DataStax Java Driver 4.x (from Boot BOM). Data modeled **query-first**, not relationally. | https://cassandra.apache.org/doc/latest/ |
| **Elasticsearch** | **9.x (latest 9 patch)** | Full-text + analytical search over transactions, fraud events, alerts, audit, investigations. | Uses the **modern `co.elastic.clients:elasticsearch-java` client** — the deprecated High Level REST Client is explicitly **not** used. Client major version must equal the cluster major version. | https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html |
| **Kibana** | **9.x — EXACT same patch as Elasticsearch** | Dashboards/visualizations over the ES indices. | Kibana and Elasticsearch **must** be on the identical version string; both pinned to the same `${elastic.version}` compose variable. | https://www.elastic.co/guide/en/kibana/current/index.html |

## Reliability, security & observability

| Technology | Selected version | Why selected | Compatibility notes | Official source |
|---|---|---|---|---|
| **Resilience4j** | **latest stable (Boot 3+/4 line)** | Circuit breakers, retries, time limiters, bulkheads for gRPC + inter-service calls. Lightweight, functional. | Applied **only** to remote calls (gRPC, cross-service REST), not to local logic. Spring Boot AOP starter. | https://resilience4j.readme.io/ |
| **Micrometer** | **managed by Boot 4.1 BOM** | Metrics facade → Prometheus registry; Observation API for tracing. | Built into Spring Boot 4. | https://micrometer.io/ |
| **Micrometer Tracing + OpenTelemetry** | **bridge managed by Boot BOM** | Distributed tracing; W3C `traceparent` propagation; OTLP export. | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`. Correlates with structured logs via MDC. | https://opentelemetry.io/ · https://docs.micrometer.io/tracing/ |
| **JWT (jjwt)** | **0.12.x** | Stateless auth tokens signed with HS256/RS256; RBAC claims. | `io.jsonwebtoken:jjwt-api/impl/jackson`. Secrets injected from env/K8s Secret, never hard-coded. | https://github.com/jwtk/jjwt |
| **Spring Security** | **managed by Boot 4.1 BOM** | Authentication, method-level RBAC (`@PreAuthorize`), password hashing (BCrypt/Argon2). | Resource-server JWT validation at the gateway and per service. | https://spring.io/projects/spring-security |

## Containers, orchestration & CI

| Technology | Selected version | Why selected | Compatibility notes | Official source |
|---|---|---|---|---|
| **Docker Engine** | **latest stable (27.x+)** | Local build/run of images and infra. | Multi-stage builds; BuildKit. Compose v2 (`docker compose`). | https://docs.docker.com/engine/release-notes/ |
| **Base image — build** | **`eclipse-temurin:21-jdk`** | Reproducible JDK 21 build layer. | Pin by digest in production. | https://hub.docker.com/_/eclipse-temurin |
| **Base image — runtime** | **`eclipse-temurin:21-jre`** (non-root) | Minimal JRE 21 runtime; runs as unprivileged UID. | Compatible with OpenShift arbitrary-UID model (group `0`, group-writable dirs). | https://hub.docker.com/_/eclipse-temurin |
| **Kubernetes** | **stable APIs: `apps/v1`, `networking.k8s.io/v1`, `autoscaling/v2`** | GA API groups only — no beta. Portable across current supported clusters (1.29+). | `Ingress` = `networking.k8s.io/v1`; `HorizontalPodAutoscaler` = `autoscaling/v2`. | https://kubernetes.io/docs/reference/ |
| **OpenShift** | **4.x (current stable)** | Enterprise Kubernetes; adds `Route`, `SecurityContextConstraints`. | Containers comply with the `restricted-v2` SCC (arbitrary UID, no root, dropped capabilities). | https://docs.openshift.com/container-platform/latest/welcome/index.html |

## Testing

| Technology | Selected version | Why selected | Compatibility notes | Official source |
|---|---|---|---|---|
| **JUnit 5 (Jupiter)** | **managed by Boot 4.1 BOM** | Standard test platform. | `spring-boot-starter-test`. | https://junit.org/junit5/ |
| **Mockito** | **managed by Boot BOM** | Mocking for unit tests. | `mockito-junit-jupiter`. | https://site.mockito.org/ |
| **AssertJ** | **managed by Boot BOM** | Fluent assertions. | Bundled in the test starter. | https://assertj.github.io/doc/ |
| **Testcontainers** | **latest stable** | Real MySQL, Cassandra, Elasticsearch, Kafka (Redpanda/Confluent) in integration tests. | BOM-managed; modules: `mysql`, `cassandra`, `elasticsearch`, `kafka`. Requires a Docker daemon at test time. | https://testcontainers.com/ |

## How to verify versions on a networked machine

```bash
# Newest stable Spring Boot 4.1 patch
scripts/check-versions.sh spring-boot        # queries Maven Central metadata

# Newest stable of any Maven coordinate, e.g. Avro
scripts/check-versions.sh org.apache.avro avro

# Confluent / Elasticsearch / Kibana image tags
scripts/check-versions.sh docker confluentinc/cp-kafka
scripts/check-versions.sh docker elasticsearch

# Then resolve the whole tree and fail on any RELEASE/SNAPSHOT/beta leak:
./mvnw -q versions:display-dependency-updates
./mvnw -q dependency:tree | grep -iE 'alpha|beta|rc|snapshot|milestone' && echo "UNSTABLE DEP FOUND" || echo "clean"
```

The parent POM also runs the **Maven Enforcer plugin** (`requireJavaVersion [21,)`, `requireMavenVersion`,
`requireReleaseDeps` which bans any `*-SNAPSHOT`, and `banDuplicatePomDependencyVersions`) so an
unstable dependency fails the build automatically. Pre-release qualifiers (`*-alpha*`, `*-beta*`,
`*-rc*`, `*-M*`) are excluded by **policy** — every non-BOM dependency is pinned to a GA version and
the rest are inherited from the all-GA Spring Boot BOM — rather than by a `bannedDependencies` rule,
because maven-enforcer's substring version matcher treats globs like `*:*:*-M*` as fail-open (they
also match GA versions such as `4.1.0`, breaking every build).
