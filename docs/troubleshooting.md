# Troubleshooting

A symptom-first guide to the things that actually bite when standing this platform up.
Ordered roughly by when you hit them: **build → infra → runtime → deploy**.

```mermaid
flowchart TD
  A[Problem] --> B{Build fails?}
  B -->|docker build| B0[CRLF in mvnw? — .gitattributes / re-clone]
  B -->|./mvnw| B1[JDK 21? Maven 3.9+? — enforcer]
  B -->|no| C{Infra unhealthy?}
  C -->|yes| C1[Docker RAM ≥8 GB · free ports · ES vm.max_map_count]
  C -->|no| D{Service won't start?}
  D -->|yes| D1[JWT_SECRET ≥32B · schema-registry · DB reachable]
  D -->|no| E{Runtime wrong?}
  E -->|events stuck| E1[check .DLT topics in kafka-ui]
  E -->|scores all rule-only| E2[risk-scoring down → breaker open]
  E -->|no traces| E3[OTLP endpoint / customer-service quirk]
```

## Build

### Docker image builds

**`./mvnw: bad interpreter: No such file or directory`, `exec ./mvnw: no such file or
directory`, or `Invalid distributionUrl` — every one of the nine builds fails at the
same step.** The clone has **CRLF line endings**. Git for Windows defaults to
`core.autocrlf=true`, which rewrites `mvnw` so its shebang becomes `#!/bin/sh\r` — a
path the Linux build container cannot resolve — and appends a stray `\r` to the
`distributionUrl` in `.mvn/wrapper/maven-wrapper.properties`.

`.gitattributes` pins these files to LF, so a fresh clone is immune, and the Dockerfiles
strip CRs defensively (`sed -i 's/\r$//' mvnw …`) so even a poisoned clone builds. If you
cloned **before** that commit, renormalize:

```bash
git pull
git rm --cached -r . && git reset --hard    # re-checkout everything under the new rules
git config --global core.autocrlf input     # optional: stop it happening in other repos
```

Confirm with `git ls-files --eol mvnw` → `i/lf w/lf attr/text eol=lf`.

**Every image re-downloads the whole dependency tree.** The build stages share one Maven
repository through a BuildKit cache mount, so dependencies are fetched once for all nine
images. That needs BuildKit — it is the default, but `DOCKER_BUILDKIT=0` in your
environment disables it and restores the slow path. Unset it. (`scripts/up.sh` refuses to
build when it is set.)

**The build stalls or fails resolving `io.confluent:*`.** Those artifacts come from
`packages.confluent.io`, not Maven Central. The build container needs to reach it — allow
it through the proxy, or point `MAVEN_OPTS`/`settings.xml` at an internal mirror.

### From-source builds (`./mvnw`)

**`mvn`/enforcer fails immediately: "Java 21+ is required".**
The reactor enforces **JDK 21** (`maven-enforcer-plugin`, `requireJavaVersion [21,)`)
and **Maven 3.9.0+**. This is the single most common blocker. Check `java -version`;
point `JAVA_HOME` at a JDK 21 build. There is no workaround short of installing
JDK 21 — the enforcer runs before compilation by design (see
[`technology-versions.md`](technology-versions.md)).

**Enforcer fails on a dependency version** (`alpha/beta/rc/milestone` banned, or
`SNAPSHOT` banned, or duplicate versions). The version policy is mechanical — pin a
GA release. See [`technology-versions.md`](technology-versions.md).

**`kafka-avro-serializer` / `kafka-schema-registry-client` won't resolve.** They're
hosted on the **Confluent** repository, not Maven Central — it's declared in the root
`pom.xml`. Behind a proxy/mirror, allow `packages.confluent.io` or mirror it.

**Integration tests don't run / "no Docker".** `*IT` classes run under Failsafe in
`./mvnw verify` and need a **Docker daemon**. `./mvnw test` skips them. See
[`testing.md`](testing.md).

## Infrastructure (Docker Compose)

**A container dies with exit code 137.** That is the kernel OOM-killer: Docker was not
given enough memory. The lean stack wants **8 GB**, and **12 GB** with the consoles
(`--full`). Raise it in **Docker Desktop → Settings → Resources → Memory**. If you cannot,
run without `--full` (Kibana + Kafka UI are ~2 GB) and trim the heaps in
`deploy/docker/.env`:

```properties
ES_HEAP=384m
CASSANDRA_HEAP=384M
CASSANDRA_NEWSIZE=96M
```

`scripts/up.sh` warns before starting when the allocation looks too small, and names the
offending container if one is killed.

**`Bind for 0.0.0.0:3306 failed: port is already allocated`** (or 8080, 9200, 9042 …).
Something on the host already owns that port — a local MySQL, Tomcat, or another
Elasticsearch. Every **host-side** port is overridable from `deploy/docker/.env`; see
`.env.example` for the full list:

```properties
MYSQL_PORT=13306
GATEWAY_PORT=18080
```

`scripts/up.sh` scans all of them before starting and prints the exact variable to set for
each conflict. Container-side ports stay fixed, so in-network wiring is unaffected.

**`Unknown database 'fraud_customer'`, or Flyway fails on every DB-backed service.**
`deploy/docker/mysql/init/01-init.sh` never created the six `fraud_*` schemas. Two causes:

1. **CRLF** in that script — the MySQL entrypoint aborts it silently. Same fix as the
   `bad interpreter` entry above.
2. **A pre-existing volume.** MySQL only runs `/docker-entrypoint-initdb.d` on a *first*
   initialisation, so a volume created by an earlier broken run stays empty forever.

```bash
docker compose -p fraud-detection-platform exec mysql mysql -uroot -proot -e 'SHOW DATABASES'
```

If the `fraud_*` schemas are missing, wipe and re-init: `scripts/down.sh --volumes` then
`scripts/up.sh`.

**Elasticsearch container exits / bootstrap check fails.** Set the host kernel param:

```bash
sudo sysctl -w vm.max_map_count=262144      # persist in /etc/sysctl.conf
```

On Docker Desktop this applies to the VM; a restart of the ES container after setting
it usually clears it. Also give Docker enough RAM (ES + Kafka + Cassandra + MySQL is
memory-hungry — 8 GB+ recommended).

**Elasticsearch exits complaining about `memory locking requested … but memory is not
locked`.** Locking the heap into RAM needs a memlock rlimit the host may refuse. The
default is `ES_MEMORY_LOCK=false` for exactly that reason — only set it `true` on a host
you know grants it.

**Kibana / Kafka UI aren't running.** By design: they sit behind the `consoles` compose
profile. Start them with `scripts/up.sh --full`, or
`docker compose --profile consoles up -d`. The profile must also be passed to `down`, or
Compose leaves them behind — `scripts/down.sh` always does.

**A service boots before its database/broker is ready.** Compose gates every service
with health-checked `depends_on`, so start-up is ordered
(`cd deploy/docker && docker compose up -d`). If you start a single service manually,
bring up infra first and wait for `docker compose ps` to show healthy.

**Cassandra: keyspace/tables missing.** The `cassandra-init` one-shot applies
`schema.cql` (keyspace `fraud`). In the `docker` profile the app uses
`schema-action: NONE` — it does **not** create tables itself. If init didn't run,
re-run it or apply `schema.cql` with `cqlsh`. On k8s the `cassandra-schema-init` Job
does this.

## Port & endpoint quick-reference

These are the **defaults**. Each is overridable from `deploy/docker/.env` (see
`.env.example`) — the variable is the row's subject in upper snake case plus `_PORT`, e.g.
`MYSQL_PORT`, `GATEWAY_PORT`, `ES_PORT`, `KIBANA_PORT`. Overrides change only what is
published on the host.

| Thing | Default host port | Note |
|-------|-------------------|------|
| api-gateway | 8080 | the only public entry |
| services | 8081–8088 | see [`architecture.md`](architecture.md#service-catalog) |
| risk-scoring gRPC | 9095 | internal only |
| **Schema Registry** | **8090** | container listens on 8081 — **host maps 8090** |
| Kafka UI | 8100 | inspect topics **and `.DLT`** — needs `--profile consoles` |
| Kibana | 5601 | dashboards — needs `--profile consoles` |
| Jaeger UI | 16686 | traces |
| Elasticsearch | 9200 | |
| OTel Collector | 4318 (HTTP) / 4317 (gRPC) | |
| MySQL / Cassandra / Kafka | 3306 / 9042 / 29092 | Kafka's in-network listener is 9092 |

> **Gotcha:** connecting a local tool to Schema Registry uses `localhost:8090`, but
> *inside* the compose/k8s network services use `http://schema-registry:8081`. Don't
> mix them.

## Runtime

**Service fails to start: "JWT secret must be at least 32 bytes".** `JwtService`
enforces a ≥32-byte `JWT_SECRET` at startup (HS256). Set a real secret; the dev
default is a clearly-labelled placeholder. See [`security.md`](security.md#1-token-issuance).

**Everything logs a warning about the admin password.** `customer-service` bootstraps
a default ADMIN and **warns loudly** if `ADMIN_PASSWORD` is still `admin-change-me`.
Set `ADMIN_USERNAME`/`ADMIN_PASSWORD` (and `JWT_SECRET`) via `.env` (compose) or
`platform-secrets` (k8s).

**401 on every call.** The gateway strips inbound `X-User-*` and requires a valid
`Bearer` token on non-public paths. Get one from `POST /api/v1/auth/login`; public
paths are `/api/v1/auth/**` and `/actuator/**` only. **403** instead means
authenticated but wrong role — check the endpoint→role table in
[`security.md`](security.md#3-authorization-rbac).

**429 Too Many Requests.** The gateway token-bucket limiter (~100 req/min/IP) tripped
— it sits *before* auth. Tune `GATEWAY_RATE_LIMIT_*` or wait for `Retry-After`.

**Events produced but never processed; consumer lag stuck.** The record probably
exhausted its retries (3×, exponential 500ms→5s) and landed on the **`<topic>.DLT`**
topic. Inspect DLTs in Kafka UI (`localhost:8100`); the failure cause is on the
record headers. See [`kafka.md`](kafka.md#retries--dead-letter-topics).

**Producer/consumer errors mentioning schema incompatibility.** Registry compatibility
is global **BACKWARD**. An Avro change that isn't backward-compatible is rejected at
registration. Follow the evolution rules in [`avro.md`](avro.md#schema-evolution).

**All fraud scores look "rule-only" / `fraud.risk.degraded` climbing.** The gRPC call
to `risk-scoring-service` is failing, so the circuit breaker opened and scoring
**degrades gracefully to rule-only** (by design — not an outage of fraud detection).
Check risk-scoring-service health on `:8085/actuator/health` and that
fraud-detection-service can reach it on `:9095`. See
[`grpc.md`](grpc.md#resilience).

**No traces in Jaeger.** Traces flow service → OTel Collector (`:4318`) → Jaeger.
Verify the collector is up and `MANAGEMENT_OTLP_TRACING_ENDPOINT` points at it.
**Known quirk:** `customer-service` reads **`OTEL_EXPORTER_OTLP_ENDPOINT`** instead —
if only that service is missing traces, set that variable. See
[`observability.md`](observability.md#tracing).

**Duplicate side effects on redelivery.** There shouldn't be any — consumers dedupe
via `ProcessedEvent` and ES docs are keyed by `eventId`. If you see duplicates,
confirm the `processed_events` table exists (Flyway ran) and the consumer isn't
committing before processing. See [`kafka.md`](kafka.md#idempotency--effectively-once).

## Kubernetes / OpenShift

**`kustomize build` errors: "security; file is not in or below the base dir".**
The ConfigMap generators intentionally read canonical files *outside* `deploy/k8s`
(shared MySQL init, OTel config, `schema.cql`). Build with:

```bash
kustomize build --load-restrictor=LoadRestrictionsNone deploy/k8s | kubectl apply -f -
```

**Pods `ImagePullBackOff`.** Images are `frauddetect/<svc>:1.0.0` with
`imagePullPolicy: IfNotPresent` and are **not** on a public registry — build and load
them (`kind load docker-image ...` / `minikube image load ...`), or push to your
registry. See [`kubernetes.md`](kubernetes.md#images).

**HPA shows `<unknown>` targets.** Install `metrics-server`.

**East-west traffic blocked / services can't talk.** NetworkPolicies default-deny
ingress and need a policy-enforcing CNI (Calico/Cilium); on a CNI that ignores
policies they're simply inert. See [`kubernetes.md`](kubernetes.md#security-posture).

**OpenShift: app pods won't admit / SCC errors.** The app manifests are
`restricted-v2`-clean *after* the overlay strips `runAsUser`/`fsGroup`. Deploy via
`deploy/openshift` (not `deploy/k8s`) so those patches apply. The bundled **dev infra**
is *not* SCC-clean — run it only in a throwaway project (`anyuid`) or replace it with
operators. See [`openshift.md`](openshift.md#-dev-infra-on-openshift).

## Still stuck?

- Every service exposes `/actuator/health` (with `readiness`/`liveness` groups) and
  `/actuator/prometheus` — start there.
- Filter logs and traces by the **`correlationId`** that threads a whole request across
  REST, gRPC, and Kafka (see [`observability.md`](observability.md#correlation-ids)).
- Cross-reference the deep-dive docs linked from [`architecture.md`](architecture.md).
