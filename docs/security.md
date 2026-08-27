# Security

Security is layered — **defence in depth**. The gateway authenticates and
authorizes at the edge, and every downstream service **independently re-validates**
the same token, so a request that somehow reaches a service directly is still
checked. All JWT logic is centralized in [`libs/common`](../libs/common) and reused
everywhere; there is deliberately **no Spring Security OAuth2 resource-server** — a
small custom filter over `jjwt` keeps the trust model explicit and dependency-light.

- **JWT library:** `io.jsonwebtoken:jjwt` **0.12.6** (`jjwt-api`/`-impl`/`-jackson`)
- **Algorithm:** **HS256** (HMAC-SHA256, symmetric)
- **Sessions:** stateless everywhere (`SessionCreationPolicy.STATELESS`); CSRF, form-login, and HTTP-Basic disabled; 401 via `HttpStatusEntryPoint`
- **Roles:** `ADMIN`, `ANALYST`, `INVESTIGATOR`, `CUSTOMER` ([`SecurityRoles.java`](../libs/common/src/main/java/com/frauddetect/common/constants/SecurityRoles.java))

## Authentication flow

```mermaid
sequenceDiagram
  autonumber
  participant U as Client
  participant G as api-gateway
  participant C as customer-service
  participant S as any service

  U->>G: POST /api/v1/auth/login {username,password}
  G->>C: (public path, proxied)
  C->>C: BCrypt.matches(password, users.password_hash)
  C-->>U: 200 { token (HS256 JWT), ... }
  Note over U: token carries sub=username, roles claim, iss, exp

  U->>G: GET /api/v1/... (Authorization: Bearer <token>)
  G->>G: strip inbound X-User-*; JwtService.isValid(token)
  alt invalid / missing
    G-->>U: 401 (not proxied)
  else valid
    G->>S: proxy + X-User-Id, X-User-Roles
    S->>S: JwtAuthenticationFilter re-validates the SAME token
    S->>S: @PreAuthorize checks role
    S-->>U: 200 / 403
  end
```

## 1. Token issuance

**`customer-service` is the sole issuer** ([`AuthController`](../services/customer-service/src/main/java/com/frauddetect/customer/web/AuthController.java)):
`POST /api/v1/auth/login` and `POST /api/v1/auth/register` (which auto-logs-in) return
a token; `POST /api/v1/users` (admin creating staff) mints a user but returns none.

Signing and claims live in [`JwtService`](../libs/common/src/main/java/com/frauddetect/common/security/JwtService.java):

| Aspect | Value |
|--------|-------|
| Algorithm | HS256 via `signWith(key)` over an HMAC `SecretKey` (`Keys.hmacShaKeyFor`) |
| Secret | `security.jwt.secret` ← env **`JWT_SECRET`**; **≥ 32 bytes enforced** (else `IllegalStateException` at startup) |
| Issuer | `security.jwt.issuer` ← `JWT_ISSUER`, default `fraud-detection-platform` |
| TTL | `security.jwt.access-token-ttl` ← `JWT_TTL`, default **PT1H** |
| Clock skew | `security.jwt.clock-skew` = PT30S (applied on validation) |
| Claims | `iss`, `sub` = username, `iat`, `exp`, and **`roles`** — a JSON list of role names *without* the `ROLE_` prefix |

The token carries identity and roles only — no email, no `customerId` (those are
returned in the HTTP response body, not embedded in the JWT).

## 2. Token validation — two independent layers

Both layers call the same `JwtService.parse()` (verifies signature with the HMAC key,
requires the configured issuer, applies clock skew). Wired by
[`CommonSecurityAutoConfiguration`](../libs/common/src/main/java/com/frauddetect/common/autoconfigure/CommonSecurityAutoConfiguration.java),
which publishes a `JwtService` bean whenever `security.jwt.secret` is set.

- **Edge (reactive) — gateway:** [`JwtAuthenticationWebFilter`](../services/api-gateway/src/main/java/com/frauddetect/gateway/filter/JwtAuthenticationWebFilter.java) validates every non-public request, returns **401 without proxying** on failure, and forwards identity downstream as `X-User-Id` / `X-User-Roles`. Crucially it **strips any inbound `X-User-*` headers first**, so a client cannot spoof identity. Public paths (`gateway.security.public-paths`): `/api/v1/auth/**`, `/actuator/**`.
- **Per-service (servlet):** [`JwtAuthenticationFilter`](../libs/common/src/main/java/com/frauddetect/common/security/JwtAuthenticationFilter.java) — a `OncePerRequestFilter` that reads `Authorization: Bearer`, verifies, and populates the `SecurityContext` with `ROLE_`-prefixed authorities. Slotted into each service's chain via `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`.

Both derive their key from the same `JWT_SECRET`, so a token minted by
customer-service validates at the gateway and again at each service.

## 3. Authorization (RBAC)

Enforcement is **method-level** via `@PreAuthorize` (`@EnableMethodSecurity` in each
service). The `SecurityFilterChain`s only `permitAll()` actuator/swagger (and the two
auth routes in customer-service) and require authentication for `anyRequest()`;
role gating is not in the filter chain, it's on the handlers:

| Endpoint | Required authority |
|----------|--------------------|
| `POST /api/v1/auth/login` \| `/register` | public |
| `POST /api/v1/users` (create staff) | `hasRole('ADMIN')` |
| `GET /api/v1/customers` | `hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')` |
| `POST /api/v1/transactions` | `hasAnyRole('CUSTOMER','ANALYST','ADMIN')` |
| `POST /api/v1/fraud-rules` | `hasRole('ADMIN')` |
| `GET /api/v1/fraud-rules` | `hasAnyRole('ANALYST','INVESTIGATOR','ADMIN')` |
| `POST /api/v1/accounts/{id}/credit` | `hasAnyRole('ADMIN','ANALYST')` |
| `POST /api/v1/alerts/{id}/resolve` | `hasAnyRole('INVESTIGATOR','ADMIN')` |
| `GET /api/v1/audit/events` | `hasAnyRole('INVESTIGATOR','ADMIN')` |

## 4. Password hashing

- **BCrypt**, default strength **10** (`BCryptPasswordEncoder`), bean in customer-service [`SecurityConfig`](../services/customer-service/src/main/java/com/frauddetect/customer/config/SecurityConfig.java).
- Credentials live only in customer-service: `UserEntity.password_hash` (BCrypt, never plaintext) in MySQL `users`. `AuthService` encodes on register/create and `matches` on login. `DefaultAdminInitializer` bootstraps one ADMIN with a BCrypt-hashed env password on first boot.
- Passwords are bound `@Size(min=8, max=72)` (72 = BCrypt's input limit).

## 5. Rate limiting

A custom in-process **token-bucket** `WebFilter` at the gateway
([`RateLimitingWebFilter`](../services/api-gateway/src/main/java/com/frauddetect/gateway/filter/RateLimitingWebFilter.java)),
keyed per client IP (first `X-Forwarded-For` hop, else socket address). It runs
**before** authentication (`HIGHEST_PRECEDENCE + 10`, ahead of the JWT filter at
`+20`), so it also shields the public `/api/v1/auth` endpoints from credential
stuffing. Over-limit → **429** with `Retry-After`.

Config (`gateway.rate-limit`, defaults ≈ **100 requests/min/IP**): `enabled`
(`GATEWAY_RATE_LIMIT_ENABLED`), `capacity` (100), `refill-tokens` (100),
`refill-period` (1m). State is a per-instance `ConcurrentHashMap` — correct for a
single replica; for horizontal scale, swap in a Redis-backed limiter.

## 6. Input validation

Jakarta Bean Validation — `@Valid` on `@RequestBody` records with
`jakarta.validation.constraints`; violations are turned into RFC-style problem
responses by [`GlobalExceptionHandler`](../libs/common/src/main/java/com/frauddetect/common/error/GlobalExceptionHandler.java).
Examples: usernames `@Pattern`+`@Size(3..100)`; amounts `@DecimalMin`(exclusive)+`@Digits(15,4)`;
currency `@Pattern("[A-Z]{3}")` (ISO-4217); country `@Pattern("[A-Z]{2}")`;
channel constrained to `WEB|MOBILE|ATM|POS|API`.

## 7. Audit logging

Auditing is **asynchronous, Kafka-based fan-in**. `audit-service` runs a single
`@KafkaListener` across all eight domain topics
([`AuditEventListener`](../services/audit-service/src/main/java/com/frauddetect/audit/messaging/AuditEventListener.java)):
each Avro event → `AuditEventMapper.toDocument()` → append-only Elasticsearch
`audit-events` index, keyed by `eventId` (idempotent on redelivery). Indexing
failures fall through to the container error handler (retry → DLT) so records aren't
lost. Captured fields include `eventType`, `correlationId`, `occurredAt` + `indexedAt`,
the domain ids, `amount`/`currency`, `score`/`severity`/`decision`, and a `summary`.

> **Scope caveat:** this is a **domain-event trail**, not an access/security audit —
> there is no authenticated-actor/action/resource triad. HTTP caller identity
> (`X-User-Id`) is not persisted to the audit index; the closest actor signal is
> `AlertResolved.resolvedBy`, folded into `summary`. For a compliance-grade access
> log, add actor capture at the gateway.

## 8. Secrets hygiene

**No real secrets are committed.** The only literals are clearly-labelled dev-only
fallbacks in `${ENV:default}` form — e.g. `JWT_SECRET` defaults to `change-me-in-prod-…`
and `ADMIN_PASSWORD` to `admin-change-me` (which logs a startup warning if left as-is).
Real values come from the environment:

| Deployment | Mechanism |
|------------|-----------|
| Docker Compose | gitignored `.env` (template [`deploy/docker/.env.example`](../deploy/docker/.env.example), marked DEV-ONLY) |
| Kubernetes | `platform-secrets` Secret consumed via `envFrom: secretRef` on every app Deployment ([`deploy/k8s/config.yaml`](../deploy/k8s/config.yaml)); committed values are placeholders |

For production, source secrets from **External Secrets Operator / Sealed Secrets /
Vault** rather than a committed manifest. Security-relevant env vars: `JWT_SECRET`,
`JWT_ISSUER`, `JWT_TTL`; `ADMIN_USERNAME`/`ADMIN_PASSWORD`/`ADMIN_EMAIL`;
`MYSQL_USER`/`MYSQL_PASSWORD`; and the `GATEWAY_RATE_LIMIT_*` knobs.

## Notes on the trust boundary

- The gateway itself does **not** pull `spring-boot-starter-security` — it's a pure
  reactive `WebFilter` chain plus `JwtService`. `libs/common` depends on
  `spring-security-web`/`-core` (not the full starter) so it can supply the servlet
  filter without forcing the starter onto every consumer.
- `risk-scoring-service` is gRPC-only and sits **inside** the trust boundary (not on
  the public HTTP JWT path); it's reachable only from `fraud-detection-service` within
  the cluster network.
