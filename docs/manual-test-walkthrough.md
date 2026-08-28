# Manual test walkthrough (with screenshot plan)

A scripted end-to-end tour of the running platform. Every step is a real call against the
live stack, ordered so that later steps depend on earlier ones. Steps marked **📸** are the
ones worth capturing for the README.

The scores below are **exact, not approximate** — the rule engine and the risk model are both
deterministic (`RiskModel` is a hand-tuned logistic function, not a trained blob), and the rule
weights are seeded by `V1__init_fraud_detection.sql`. If a number here doesn't match what you
get, something is genuinely wrong, and §20 tells you where to look.

Everything goes through the **gateway on `:8080`** — that is the only public entry point.

---

## Screenshot plan

Put the files in `docs/images/`. Suggested names match the steps below.

| # | File | Step | What it proves |
|---|------|------|----------------|
| 1 | `01-stack-healthy.png` | §1 | 17 containers, all healthy, one command |
| 2 | `02-swagger.png` | §2 | Documented OpenAPI surface per service |
| 3 | `03-login-jwt.png` | §3 | JWT issuance with roles + claims |
| 4 | `04-rbac-403.png` | §4 | RBAC actually denies (401 *and* 403) |
| 5 | `05-transaction-verdicts.png` | §6 | All four verdicts in one table: 0/35/65/100 |
| 6 | `06-transaction-block.png` | §6 | Fraud → `BLOCKED`, score 100, `FRAUD_BLOCKED` |
| 7 | `07-alerts-list.png` | §7 | Alert case management (`OPEN` → `RESOLVED`) |
| 8 | `08-notifications.png` | §8 | Fan-out to notification channels |
| 9 | `09-audit-trail.png` | §9 | Immutable audit trail in Elasticsearch |
| 10 | `10-es-search.png` | §10 | Full-text + filtered fraud search |
| 11 | `11-rules-as-data.png` | §11 | Re-weighting a rule changes the verdict, no redeploy |
| 12 | `12-jaeger-trace.png` | §13 | **The money shot** — one trace across REST → Kafka → gRPC |
| 13 | `13-kafka-ui-topics.png` | §14 | Topics, partitions, consumer lag, `.DLT` |
| 14 | `14-kibana-dashboard.png` | §15 | Fraud dashboards |
| 15 | `15-prometheus-metrics.png` | §16 | Custom business metrics |
| 16 | `16-circuit-breaker.png` | §17 | Graceful degradation when risk-scoring dies |

Capture tips: use a **light terminal theme** and a window ~100 columns wide — dark
screenshots with tiny text read badly in a README on GitHub's light default. For JSON, pipe
through `jq` so the shape is legible.

---

## 0. Setup

Everything below is `bash` (Git Bash, WSL, macOS, Linux). A PowerShell appendix for the
awkward calls is in §19.

```bash
export GW=http://localhost:8080
```

`jq` makes the output readable but is optional — drop the `| jq ...` and read the raw JSON if
you don't have it.

```bash
jq --version || echo "no jq: drop the '| jq' pipes below"
```

---

## 1. 📸 The stack is up and healthy

```bash
docker compose -p fraud-detection-platform ps
```

Expect **17 containers** on the lean profile (19 with `--full`): 9 services, plus mysql,
cassandra, cassandra-init, kafka, schema-registry, elasticsearch, jaeger, otel-collector.
`cassandra-init` should read `Exited (0)` — it is a one-shot schema job, not a failure.

Confirm the six MySQL schemas actually got created (this is the step that silently fails on a
CRLF-poisoned clone):

```bash
docker compose -p fraud-detection-platform exec mysql mysql -uroot -proot -e 'SHOW DATABASES'
```

You want `fraud_customer`, `fraud_account`, `fraud_transaction`, `fraud_detection`,
`fraud_alert`, `fraud_notification`. If they're missing, see
[`troubleshooting.md`](troubleshooting.md) — *Unknown database 'fraud_customer'*.

And the Cassandra keyspace:

```bash
docker compose -p fraud-detection-platform exec cassandra cqlsh -e "DESCRIBE KEYSPACE fraud" | head -20
```

Every service answers its own health probe:

```bash
for p in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
  printf '%s -> %s\n' "$p" "$(curl -s localhost:$p/actuator/health | jq -r .status)"
done
```

Nine `UP` lines.

> **📸 Shot 1** — `docker compose ps` with everything healthy. If you used `scripts/up.sh`,
> its final endpoint table is an even better screenshot.

---

## 2. 📸 API documentation

Each service serves its own OpenAPI UI. Open a couple in a browser:

- <http://localhost:8083/swagger-ui.html> — transaction-service
- <http://localhost:8084/swagger-ui.html> — fraud-detection-service
- <http://localhost:8086/swagger-ui.html> — alert-service

> **📸 Shot 2** — transaction-service Swagger with `POST /api/v1/transactions` expanded so the
> request schema is visible.

---

## 3. 📸 Authenticate

`/api/v1/auth/**` and `/actuator/**` are the *only* unauthenticated paths.

```bash
curl -s $GW/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-change-me"}' | jq
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "subject": "admin",
  "roles": ["ADMIN"],
  "customerId": null,
  "expiresInSeconds": 3600
}
```

Capture it:

```bash
export TOKEN=$(curl -s $GW/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-change-me"}' | jq -r .token)
export AUTH="Authorization: Bearer $TOKEN"
echo "${TOKEN:0:40}..."
```

Now register a real customer — self-service registration creates a `CUSTOMER` user, a linked
profile, and auto-logs in. **The response carries the `customerId` you need for everything
downstream.**

```bash
curl -s $GW/api/v1/auth/register -H 'Content-Type: application/json' -d '{
  "username":"alice.martin",
  "password":"Str0ngPassw0rd!",
  "firstName":"Alice","lastName":"Martin",
  "email":"alice.martin@example.com",
  "phone":"+33600000000","countryCode":"FR"
}' | jq
```

```bash
export CUST_TOKEN=$(curl -s $GW/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice.martin","password":"Str0ngPassw0rd!"}' | jq -r .token)
export CID=$(curl -s $GW/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice.martin","password":"Str0ngPassw0rd!"}' | jq -r .customerId)
echo "customerId=$CID"
```

> **📸 Shot 3** — the login response showing `token`, `roles`, `expiresInSeconds`.

---

## 4. 📸 RBAC is real

Three distinct outcomes. **401** — no token:

```bash
curl -s -o /dev/null -w 'no token      -> %{http_code}\n' $GW/api/v1/alerts
```

**403** — valid token, wrong role. Alice is a `CUSTOMER`; alerts need
`ANALYST`/`INVESTIGATOR`/`ADMIN`:

```bash
curl -s -o /dev/null -w 'CUSTOMER role -> %{http_code}\n' \
  -H "Authorization: Bearer $CUST_TOKEN" $GW/api/v1/alerts
```

**200** — admin:

```bash
curl -s -o /dev/null -w 'ADMIN role    -> %{http_code}\n' -H "$AUTH" $GW/api/v1/alerts
```

Expected: `401`, `403`, `200`.

Header spoofing is also blocked — the gateway strips inbound `X-User-*` before routing, so
this does **not** escalate you:

```bash
curl -s -o /dev/null -w 'spoofed header -> %{http_code}\n' \
  -H 'X-User-Id: admin' -H 'X-User-Roles: ADMIN' $GW/api/v1/alerts
```

Still `401`.

> **📸 Shot 4** — all four lines together. This is a strong security screenshot.

---

## 5. Open an account

```bash
export ACC=$(curl -s $GW/api/v1/accounts -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"customerId\":\"$CID\",
  \"type\":\"CHECKING\",
  \"currency\":\"EUR\",
  \"initialBalance\":5000.00
}" | jq -r .id)
echo "accountId=$ACC"
```

Credit it so there's balance to move:

```bash
curl -s $GW/api/v1/accounts/$ACC/credit -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"amount":45000.00,"reason":"demo funding"}' | jq '{id,balance,status}'
```

---

## 6. 📸 The core flow — four transactions, four verdicts

This is the heart of the demo. **Run them in this order.** The engine is stateful — device
novelty, merchant novelty, country change, velocity and the 30-day average all depend on what
came before — so reordering changes every number below.

### Scoring model, for reference

Rule weights are seeded data, not code. Severity bands come from `FraudSeverity`:

| Score | Severity | Decision | Transaction status |
|-------|----------|----------|--------------------|
| 0–29 | LOW | ALLOW | `COMPLETED` |
| 30–59 | MEDIUM | ALLOW | `COMPLETED` |
| 60–79 | HIGH | **REVIEW** | `FLAGGED` |
| 80–100 | CRITICAL | **BLOCK** | `BLOCKED` |

An alert is raised whenever severity is HIGH or CRITICAL — so transactions 3 and 4 open cases,
1 and 2 do not.

One subtlety that explains transaction 1: the novelty rules (`SUSPICIOUS_DEVICE`,
`ABNORMAL_MERCHANT`) and `IMPOSSIBLE_TRAVEL` are all gated on the customer having prior history
(`BehaviorService` computes `hasHistory = txCount > 0`). A customer's *first* transaction
therefore cannot be "novel" — there is no baseline to deviate from. That is deliberate: it
avoids flagging every new customer's first purchase.

### Transaction 1 — establishing the baseline → score 0

Small domestic purchase, and Alice's first ever transaction.

```bash
export TX1=$(curl -s $GW/api/v1/transactions -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
  \"amount\":120.50, \"currency\":\"EUR\", \"type\":\"PURCHASE\",
  \"merchantId\":\"merch-grocery-01\", \"merchantCategory\":\"GROCERY\",
  \"countryCode\":\"FR\", \"city\":\"Paris\", \"latitude\":48.8566, \"longitude\":2.3522,
  \"deviceId\":\"device-laptop-01\", \"ipAddress\":\"203.0.113.10\", \"channel\":\"WEB\"
}" | jq -r .id)
echo "TX1=$TX1"
```

Returns **202 Accepted** with status `PENDING` and a `Location` header — the verdict is applied
asynchronously over Kafka. Wait a moment, then read it back:

```bash
sleep 5
curl -s $GW/api/v1/transactions/$TX1 -H "$AUTH" \
  | jq '{status,fraudScore,severity,decision,reasonCode}'
```

```json
{ "status": "COMPLETED", "fraudScore": 0, "severity": "LOW", "decision": "ALLOW", "reasonCode": null }
```

**Why 0:** no history, so no rule can fire. `LARGE_AMOUNT` needs > 5000 absolute or > 10× the
30-day average (which is 0 and therefore skipped). The model returns **0.0998** (`LOW`) — the
bare base rate — well under the 0.70 `MODEL_RISK` threshold.

This transaction's real job is to seed the profile: known device `device-laptop-01`, known
category `GROCERY`, last country `FR`, last position Paris, 30-day average `120.50`.

### Transaction 2 — novel but harmless → MEDIUM, still allowed

Another small Paris purchase, but from a new device and in a new category. Now that history
exists, the novelty rules can fire.

```bash
export TX2=$(curl -s $GW/api/v1/transactions -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
  \"amount\":85.00, \"currency\":\"EUR\", \"type\":\"PURCHASE\",
  \"merchantId\":\"merch-bistro-12\", \"merchantCategory\":\"RESTAURANT\",
  \"countryCode\":\"FR\", \"city\":\"Paris\", \"latitude\":48.8566, \"longitude\":2.3522,
  \"deviceId\":\"device-tablet-02\", \"ipAddress\":\"203.0.113.11\", \"channel\":\"MOBILE\"
}" | jq -r .id)
sleep 5
curl -s $GW/api/v1/transactions/$TX2 -H "$AUTH" \
  | jq '{status,fraudScore,severity,decision}'
```

```json
{ "status": "COMPLETED", "fraudScore": 35, "severity": "MEDIUM", "decision": "ALLOW" }
```

**Why 35:** `SUSPICIOUS_DEVICE` 20 + `ABNORMAL_MERCHANT` 15. Identical coordinates mean
`IMPOSSIBLE_TRAVEL` cannot fire; `FR` is unchanged so `SUSPICIOUS_COUNTRY` stays silent; €85 is
nowhere near any amount threshold. Model: **0.1978** (`LOW`).

This is the important "no false positive" case — mild novelty raises the score without blocking
a legitimate customer.

### Transaction 3 — suspicious → REVIEW

Large amount, brand-new device, new category — same city, so still no geography penalty.

```bash
export TX3=$(curl -s $GW/api/v1/transactions -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
  \"amount\":12000.00, \"currency\":\"EUR\", \"type\":\"PURCHASE\",
  \"merchantId\":\"merch-electro-77\", \"merchantCategory\":\"ELECTRONICS\",
  \"countryCode\":\"FR\", \"city\":\"Paris\", \"latitude\":48.8566, \"longitude\":2.3522,
  \"deviceId\":\"device-newphone-03\", \"ipAddress\":\"203.0.113.55\", \"channel\":\"MOBILE\"
}" | jq -r .id)
sleep 5
curl -s $GW/api/v1/transactions/$TX3 -H "$AUTH" \
  | jq '{status,fraudScore,severity,decision}'
```

```json
{ "status": "FLAGGED", "fraudScore": 65, "severity": "HIGH", "decision": "REVIEW" }
```

**Why 65:** `LARGE_AMOUNT` 30 + `SUSPICIOUS_DEVICE` 20 + `ABNORMAL_MERCHANT` 15. €12 000 is both
> 2× the 5000 absolute ceiling and ~117× the running average, so `LARGE_AMOUNT` awards its full
weight. Model: **0.4750** (`MEDIUM`) — rising, but still under 0.70, so `MODEL_RISK` abstains.

`FLAGGED` is deliberately **not** terminal: the alert case decides what happens next (§7).

### Transaction 4 — fraud → BLOCK

Huge withdrawal, high-risk country, unknown device, and physically impossible travel from Paris
seconds earlier.

```bash
export TX4=$(curl -s $GW/api/v1/transactions -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
  \"amount\":25000.00, \"currency\":\"EUR\", \"type\":\"WITHDRAWAL\",
  \"merchantId\":\"merch-crypto-9\", \"merchantCategory\":\"CRYPTO\",
  \"countryCode\":\"NG\", \"city\":\"Lagos\", \"latitude\":6.5244, \"longitude\":3.3792,
  \"deviceId\":\"device-unknown-04\", \"ipAddress\":\"198.51.100.7\", \"channel\":\"API\"
}" | jq -r .id)
sleep 5
curl -s $GW/api/v1/transactions/$TX4 -H "$AUTH" \
  | jq '{status,fraudScore,severity,decision,reasonCode,reason}'
```

```json
{
  "status": "BLOCKED",
  "fraudScore": 100,
  "severity": "CRITICAL",
  "decision": "BLOCK",
  "reasonCode": "FRAUD_BLOCKED",
  "reason": "Blocked by fraud engine (severity CRITICAL, score 100)"
}
```

**Why 100** — six rules fire at once and the raw total is clamped to the 0–100 ceiling:

| Rule | Points | Trigger |
|---|---:|---|
| `IMPOSSIBLE_TRAVEL` | 40 | ~4 700 km from Paris in seconds ≫ 900 km/h |
| `SUSPICIOUS_COUNTRY` | 35 | `NG` is on the seeded high-risk list |
| `MODEL_RISK` | 33 | model **0.8320** (`CRITICAL`) ≥ 0.70 threshold |
| `LARGE_AMOUNT` | 30 | 5× the 5000 absolute ceiling |
| `SUSPICIOUS_DEVICE` | 20 | `device-unknown-04` never seen |
| `ABNORMAL_MERCHANT` | 15 | first `CRYPTO` transaction |
| **raw total** | **173** | → clamped to **100** |

This is the only transaction in the walkthrough where `MODEL_RISK` fires. The model's logit
picks up large-cash-withdrawal (+0.6), impossible travel (+1.8), amount > 4× average (+0.6),
new device (+0.5) and new merchant (+0.3) on top of the −2.2 base rate → 1.6 → σ(1.6) = 0.8320.
Worth calling out when you present this: the rule engine and the model agree independently.

### The whole history in one view

```bash
curl -s "$GW/api/v1/transactions?customerId=$CID&size=10" -H "$AUTH" \
  | jq -r '.content[] | [.amount,.countryCode,.status,.fraudScore,.severity,.decision] | @tsv' \
  | column -t
```

```
120.50    FR  COMPLETED  0    LOW       ALLOW
85.00     FR  COMPLETED  35   MEDIUM    ALLOW
12000.00  FR  FLAGGED    65   HIGH      REVIEW
25000.00  NG  BLOCKED    100  CRITICAL  BLOCK
```

> **📸 Shot 5** — this four-row table. It is the single best screenshot in the set: one frame
> showing a clean pass, a soft flag, a review, and a block, with the scores that produced each.
> **📸 Shot 6** — transaction 4's full JSON with `reasonCode` and `reason`, for the detail view.

## 7. 📸 Alert case management

Two alerts should exist — one per HIGH/CRITICAL transaction.

```bash
curl -s "$GW/api/v1/alerts?status=OPEN" -H "$AUTH" \
  | jq '.content[] | {id,severity,score,status,title,primaryReason,transactionId}'
```

Walk one through its lifecycle (`OPEN` → `ACKNOWLEDGED` → `RESOLVED`). The investigator
identity is taken from the JWT, not the body, so it cannot be spoofed:

```bash
export ALERT=$(curl -s "$GW/api/v1/alerts?status=OPEN" -H "$AUTH" | jq -r '.content[0].id')

curl -s -X POST $GW/api/v1/alerts/$ALERT/acknowledge -H "$AUTH" \
  | jq '{id,status,assignedTo}'

curl -s -X POST $GW/api/v1/alerts/$ALERT/resolve -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"resolution":"CONFIRMED_FRAUD","notes":"Card-not-present fraud from Lagos; card reissued."}' \
  | jq '{id,status,resolution,resolvedBy,resolutionNotes,resolvedAt}'
```

Valid `resolution` values: `CONFIRMED_FRAUD`, `FALSE_POSITIVE`, `DISMISSED`.

The fraud-response control — freeze the account:

```bash
curl -s -X PATCH "$GW/api/v1/accounts/$ACC/status?status=FROZEN" -H "$AUTH" \
  | jq '{id,status}'
```

Be accurate about what this does if you narrate it: `FROZEN` is an **account-service** state.
transaction-service is fully decoupled from account-service (no synchronous call between them),
so freezing does not retroactively gate transaction admission — later steps in this guide keep
working on the same account. Set it back so nothing downstream is confusing:

```bash
curl -s -X PATCH "$GW/api/v1/accounts/$ACC/status?status=ACTIVE" -H "$AUTH" | jq '{id,status}'
```

> **📸 Shot 7** — the `OPEN` alert list, then the resolved alert showing `resolution`,
> `resolvedBy` and `resolvedAt`.

---

## 8. 📸 Notifications fanned out

```bash
curl -s "$GW/api/v1/notifications?customerId=$CID" -H "$AUTH" | jq '.content'
```

Channels are `EMAIL`, `SMS`, `PUSH`. Nothing leaves the machine — the senders are simulated —
but the records, statuses and retries are real.

> **📸 Shot 8** — notification records tied to the alert.

---

## 9. 📸 Audit trail

Append-only, in Elasticsearch, queryable by `correlationId` — the id that threads a single
request across REST, Kafka and gRPC.

```bash
curl -s "$GW/api/v1/audit/events?customerId=$CID&size=20" -H "$AUTH" \
  | jq '.items[] | {eventType,summary,decision,correlationId,occurredAt}'
```

Trace one request end to end by sending your own correlation id. The gateway echoes it back on
the response, so `-i` shows it round-tripping:

```bash
export CORR=demo-$(date +%s)
curl -si $GW/api/v1/transactions -H "$AUTH" -H "X-Correlation-Id: $CORR" \
  -H 'Content-Type: application/json' -d "{
  \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
  \"amount\":50.00, \"currency\":\"EUR\", \"type\":\"PURCHASE\",
  \"merchantCategory\":\"GROCERY\", \"countryCode\":\"FR\",
  \"deviceId\":\"device-laptop-01\", \"channel\":\"WEB\"
}" | grep -i -E '^(HTTP|x-correlation-id|location)'
```

Now one that will actually raise an alert, so there is a full chain to follow:

```bash
curl -s $GW/api/v1/transactions -H "$AUTH" -H "X-Correlation-Id: $CORR" \
  -H 'Content-Type: application/json' -d "{
  \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
  \"amount\":8000.00, \"currency\":\"EUR\", \"type\":\"TRANSFER\",
  \"merchantId\":\"merch-x\", \"merchantCategory\":\"GAMBLING\",
  \"countryCode\":\"RU\", \"city\":\"Moscow\", \"latitude\":55.7558, \"longitude\":37.6173,
  \"deviceId\":\"device-unknown-05\", \"channel\":\"WEB\"
}" > /dev/null
sleep 6
curl -s "$GW/api/v1/audit/events?correlationId=$CORR" -H "$AUTH" \
  | jq '.items[] | {eventType,summary,occurredAt}'
```

One id, several services, one ordered story. **Keep `$CORR`** — the alert index and the Kibana
logs are queryable by it too:

```bash
curl -s "$GW/api/v1/alerts/search?correlationId=$CORR" -H "$AUTH" | jq '.items[0]'
```

> **📸 Shot 9** — the audit events for a single `correlationId`.
>
> Note: `X-Correlation-Id` is a **log/audit** correlator — it is bound to the SLF4J MDC and
> echoed on the response, but it is not attached to OpenTelemetry spans. Use it in Kibana and
> in the audit/alert indices; use Jaeger's own trace id (§13) for span-level correlation.

---

## 10. 📸 Elasticsearch-backed search

Filtered:

```bash
curl -s "$GW/api/v1/search/transactions?customerId=$CID&decision=BLOCK" -H "$AUTH" \
  | jq '{total, items: [.items[] | {amount,countryCode,severity,decision}]}'
```

Fraud events by severity — this payload carries `triggeredRuleCodes`, which makes the verdict
self-explaining and is the single most useful response on the platform:

```bash
curl -s "$GW/api/v1/search/fraud-events?severity=CRITICAL" -H "$AUTH" \
  | jq '.items[0] | {score,severity,decision,primaryReason,triggeredRuleCodes,modelRiskScore,countryCode}'
```

```json
{
  "score": 100,
  "severity": "CRITICAL",
  "decision": "BLOCK",
  "primaryReason": "Implied travel speed since the previous transaction exceeds the configured km/h limit.",
  "triggeredRuleCodes": ["IMPOSSIBLE_TRAVEL","SUSPICIOUS_COUNTRY","MODEL_RISK","LARGE_AMOUNT","SUSPICIOUS_DEVICE","ABNORMAL_MERCHANT"],
  "modelRiskScore": 0.832,
  "countryCode": "NG"
}
```

Rule codes are ordered by points descending, so `triggeredRuleCodes[0]` is the dominant signal
and matches `primaryReason`.

Free-text. The two indices expose different `multi_match` field sets, so match your term to the
right one:

| Endpoint | `text=` searches |
|---|---|
| `/api/v1/search/transactions` | `merchantCategory`, `type`, `countryCode`, `currency`, `merchantId` |
| `/api/v1/search/fraud-events` | `primaryReason`, `severity`, `decision`, `countryCode` |
| `/api/v1/alerts/search` | `title`, `primaryReason`, `severity`, `status` |
| `/api/v1/audit/events` | `summary`, `eventType`, `reasonCode` |

```bash
curl -s "$GW/api/v1/search/transactions?text=CRYPTO" -H "$AUTH" | jq '.total'
curl -s "$GW/api/v1/search/fraud-events?text=travel" -H "$AUTH" | jq '.total'
```

City names and free prose are **not** indexed — `text=Lagos` correctly returns `0`. Use
`countryCode` terms (`text=NG`) or the structured filters instead.

> **📸 Shot 10** — the fraud event with its `triggeredRuleCodes`. This is the "explainable AI"
> screenshot: the decision, the score, and every reason behind it.

---

## 11. 📸 Rules are data, not code

List the nine seeded rules with their live weights:

```bash
curl -s $GW/api/v1/fraud-rules -H "$AUTH" \
  | jq '.[] | {code,ruleType,weight,enabled,thresholdNumeric,thresholdInt}'
```

Now prove re-weighting takes effect without a redeploy. Measure a **baseline first** — by this
point in the walkthrough the velocity rules (`RAPID_VELOCITY` at >5/hour, `UNUSUAL_FREQUENCY` at
>20/24h) have started contributing, so compare against a fresh reading rather than against §6's
numbers.

Define a repeatable probe that reuses an already-seen device and category, so the *only* variable
is the rule you toggle:

```bash
probe() {
  local id
  id=$(curl -s $GW/api/v1/transactions -H "$AUTH" -H 'Content-Type: application/json' -d "{
    \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
    \"amount\":25000.00, \"currency\":\"EUR\", \"type\":\"WITHDRAWAL\",
    \"merchantId\":\"merch-crypto-9\", \"merchantCategory\":\"CRYPTO\",
    \"countryCode\":\"NG\", \"city\":\"Lagos\", \"latitude\":6.5244, \"longitude\":3.3792,
    \"deviceId\":\"device-unknown-04\", \"channel\":\"API\"
  }" | jq -r .id)
  sleep 5
  curl -s $GW/api/v1/transactions/$id -H "$AUTH" | jq -c '{fraudScore,severity,decision}'
}
```

Baseline, with the rule enabled:

```bash
echo "before: $(probe)"
```

Disable the country rule and probe again:

```bash
curl -s -X PATCH "$GW/api/v1/fraud-rules/SUSPICIOUS_COUNTRY/enabled?enabled=false" -H "$AUTH" \
  | jq -c '{code,enabled}'
echo "after:  $(probe)"
```

The score falls by the 35 points that rule was contributing — enough to move the verdict out of
`BLOCK`, unless the remaining rules still total ≥ 80. No rebuild, no restart, no redeploy: the
weights live in the `fraud_rules` table and are read per evaluation.

Put it back:

```bash
curl -s -X PATCH "$GW/api/v1/fraud-rules/SUSPICIOUS_COUNTRY/enabled?enabled=true" -H "$AUTH" \
  | jq -c '{code,enabled}'
```

You can also re-weight instead of disabling — `PUT /api/v1/fraud-rules/{code}` (ADMIN only),
which is the more realistic tuning operation.

> **📸 Shot 11** — the rule list beside the `before:` / `after:` lines in the same terminal.

---

## 12. Idempotency — no duplicate side effects

Send the *same* `Idempotency-Key` twice. You get the same transaction id back, and only one
transaction exists:

```bash
export KEY=idem-$(date +%s)
for i in 1 2; do
  curl -s $GW/api/v1/transactions -H "$AUTH" -H "Idempotency-Key: $KEY" \
    -H 'Content-Type: application/json' -d "{
    \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
    \"amount\":75.00, \"currency\":\"EUR\", \"type\":\"PURCHASE\",
    \"merchantCategory\":\"GROCERY\", \"countryCode\":\"FR\",
    \"deviceId\":\"device-laptop-01\", \"channel\":\"WEB\"
  }" | jq -r '"attempt '$i': " + .id'
done
```

Both lines print the **same** id.

---

## 13. 📸 Distributed tracing (the money shot)

Open Jaeger: <http://localhost:16686>

1. **Service** → `api-gateway`, **Find Traces**.
2. Pick the trace for a transaction POST.
3. Expand it: `api-gateway` → `transaction-service` → (Kafka) → `fraud-detection-service` →
   (gRPC) → `risk-scoring-service` → `alert-service` → `notification-service`.

Narrow the list with **Min Duration** (e.g. `100ms`) or by tagging on
`http.route=/api/v1/transactions` to skip the actuator health-probe spans, which dominate the
trace list otherwise.

> **📸 Shot 12** — the span waterfall with the gRPC hop to risk-scoring visible. This one
> screenshot demonstrates all three communication styles at once; it belongs near the top of
> the README.

---

## 14. 📸 Kafka topics and DLTs

Needs the consoles profile:

```bash
scripts/up.sh --full        # or: docker compose --profile consoles up -d
```

Open Kafka UI: <http://localhost:8100>

- **Topics** — per-topic partitions (3) and message counts
- **Consumers** — consumer groups and lag (should be ~0)
- **`.DLT` topics** — dead-letter topics. Empty is the *correct* result on a healthy run;
  worth showing precisely because the mechanism exists.
- **Schema Registry** — the 8 registered Avro schemas, compatibility `BACKWARD`

From the CLI instead:

```bash
docker compose -p fraud-detection-platform exec kafka \
  kafka-topics --bootstrap-server localhost:9092 --list
curl -s localhost:8090/subjects | jq
```

> **📸 Shot 13** — the topic list with message counts.

---

## 15. 📸 Kibana dashboards

Import the saved objects first:

```bash
deploy/kibana/import-saved-objects.sh
```

Open <http://localhost:5601> → **Dashboard**.

> **📸 Shot 14** — a fraud dashboard with data in it. Run a few more transactions first if it
> looks sparse.

---

## 16. 📸 Metrics

```bash
curl -s localhost:8084/actuator/prometheus | grep -E '^fraud_' | head -20
```

Business metrics, not just JVM ones. The five custom meters are:

| Meter | Prometheus name | Meaning |
|---|---|---|
| `fraud.analysis` | `fraud_analysis_seconds_*` | End-to-end scoring latency |
| `fraud.detections` | `fraud_detections_total` | HIGH/CRITICAL verdicts raised |
| `fraud.risk.calls` | `fraud_risk_calls_total` | gRPC calls to the model |
| `fraud.risk.degraded` | `fraud_risk_degraded_total` | Model unavailable → rule-only fallback (§17) |
| `fraud.es.index.failures` | `fraud_es_index_failures_total` | Elasticsearch write failures |

```bash
curl -s localhost:8084/actuator/metrics | jq '.names | map(select(startswith("fraud")))'
```

> **📸 Shot 15** — the `fraud_*` metric families.

---

## 17. 📸 Graceful degradation (circuit breaker)

Kill the model and show that fraud detection **keeps working**, rule-only, instead of failing.

```bash
docker compose -p fraud-detection-platform stop risk-scoring-service
```

The breaker is `COUNT_BASED`, sliding window 20, **`minimumNumberOfCalls = 10`**, failure-rate
threshold 50%, 10 s in open state. So a *single* transaction degrades gracefully but leaves the
breaker `CLOSED` — you need at least 10 failing calls before it trips. Send 12:

```bash
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code} " $GW/api/v1/transactions -H "$AUTH" \
    -H 'Content-Type: application/json' -d "{
    \"accountId\":\"$ACC\", \"customerId\":\"$CID\",
    \"amount\":$((3000 + i * 100)).00, \"currency\":\"EUR\", \"type\":\"WITHDRAWAL\",
    \"merchantCategory\":\"CRYPTO\", \"countryCode\":\"IR\",
    \"deviceId\":\"device-cb-$i\", \"channel\":\"API\"
  }"
done; echo
```

Every one returns **202** — the platform never rejects a transaction because the model is down.
Now watch the breaker open and the fallback counter climb:

```bash
curl -s localhost:8084/actuator/prometheus \
  | grep -E 'fraud_risk_degraded_total|resilience4j_circuitbreaker_state|fraud_risk_calls_total' \
  | grep -v ' 0.0$'
```

`resilience4j_circuitbreaker_state{...,state="open",} 1.0` is the line you want, alongside a
non-zero `fraud_risk_degraded_total`. In the logs:

```bash
docker compose -p fraud-detection-platform logs --tail=40 fraud-detection-service \
  | grep -iE 'degrad|breaker|CallNotPermitted|risk'
```

And confirm the verdicts still landed — `MODEL_RISK` simply abstains when the model score is
null, so the rule signals alone decide:

```bash
curl -s "$GW/api/v1/transactions?customerId=$CID&size=5" -H "$AUTH" \
  | jq '.content[] | {amount,status,fraudScore,severity,decision}'
```

Restore it. `automaticTransitionFromOpenToHalfOpenEnabled` means the breaker recovers on its
own after ~10 s — no restart of fraud-detection-service needed:

```bash
docker compose -p fraud-detection-platform start risk-scoring-service
sleep 20
curl -s localhost:8084/actuator/prometheus | grep 'circuitbreaker_state.*closed'
```

> **📸 Shot 16** — the `open` breaker state and non-zero `fraud_risk_degraded_total` beside a
> transaction list where every verdict still resolved. This is the most senior-looking
> screenshot in the set: the system loses a dependency and *decides anyway*.

---

## 18. Rate limiting (run last)

The gateway limiter is ~100 req/min per IP and sits **before** authentication. Running this
will throttle your own subsequent calls for a minute, so save it for the end.

```bash
for i in $(seq 1 130); do
  curl -s -o /dev/null -w '%{http_code}\n' -H "$AUTH" $GW/api/v1/fraud-rules
done | sort | uniq -c
```

Expect a mix of `200` and `429`.

---

## 19. PowerShell appendix

The two calls where quoting differs:

```powershell
$GW = 'http://localhost:8080'
$login = Invoke-RestMethod -Uri "$GW/api/v1/auth/login" -Method Post `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"admin-change-me"}'
$H = @{ Authorization = "Bearer $($login.token)" }
$login | Format-List
```

```powershell
$body = @{
  accountId = $ACC; customerId = $CID
  amount = 25000.00; currency = 'EUR'; type = 'WITHDRAWAL'
  merchantId = 'merch-crypto-9'; merchantCategory = 'CRYPTO'
  countryCode = 'NG'; city = 'Lagos'; latitude = 6.5244; longitude = 3.3792
  deviceId = 'device-unknown-04'; channel = 'API'
} | ConvertTo-Json

$tx = Invoke-RestMethod -Uri "$GW/api/v1/transactions" -Method Post `
  -Headers $H -ContentType 'application/json' -Body $body
Start-Sleep 5
Invoke-RestMethod -Uri "$GW/api/v1/transactions/$($tx.id)" -Headers $H |
  Select-Object status, fraudScore, severity, decision
```

---

## 20. If a score doesn't match

| Symptom | Cause |
|---|---|
| Status stuck on `PENDING` | The Kafka round-trip didn't complete. Check consumer lag in Kafka UI and `docker compose logs fraud-detection-service`. |
| Transaction 1 scored 0 and you expected more | Correct. Novelty rules need prior history (`hasHistory = txCount > 0`), so a customer's first transaction cannot fire them. |
| Score higher than documented | You reused a `deviceId`/`merchantCategory` in a different order, or ran the sequence more than once — velocity, country-change and the running 30-day average are all stateful. Reset with `scripts/down.sh --volumes && scripts/up.sh`, or just register a fresh customer in §3. |
| Score lower than documented | A rule got disabled in §11 and not re-enabled. `curl -s $GW/api/v1/fraud-rules -H "$AUTH" \| jq '.[] \| {code,enabled}'`. |
| `MODEL_RISK` didn't fire on transaction 4 | Its threshold is 0.70 and the model lands at 0.8320 only if transactions 1–3 ran first, in order — the +0.6 "amount > 4× average" term depends on the running average being 4068.50. |
| `MODEL_RISK` fired on transactions 1–3 | Shouldn't happen: the model returns 0.0998 / 0.1978 / 0.4750 there, all under 0.70. If it fired, the customer had prior history — use a fresh customer. |
| Everything rule-only, `fraud_risk_degraded_total` climbing | risk-scoring-service is down (did you restart it after §17?). |

Full symptom index: [`troubleshooting.md`](troubleshooting.md).

---

## 21. Adding the screenshots to the README

Save the files as `docs/images/01-stack-healthy.png` … `16-circuit-breaker.png`, then paste
blocks like these into [`../README.md`](../README.md). Always include alt text — it renders if
the image fails and it's what screen readers announce.

A single hero shot right after the intro:

```markdown
![Distributed trace of one transaction crossing the API gateway, transaction service, Kafka,
fraud detection, and the gRPC call to risk scoring](docs/images/12-jaeger-trace.png)
```

A side-by-side pair (GitHub renders a 2-column table cleanly):

```markdown
| Legitimate transaction | Fraudulent transaction |
|---|---|
| ![Transaction completed with a fraud score of 35, severity MEDIUM, decision ALLOW](docs/images/05-transaction-allow.png) | ![Transaction blocked with a fraud score of 100, severity CRITICAL, decision BLOCK](docs/images/06-transaction-block.png) |
```

A collapsed gallery, so the README stays skimmable:

```markdown
<details>
<summary>More screenshots — alerts, audit trail, Kafka, Kibana, metrics</summary>

![Open fraud alerts with severity and primary reason](docs/images/07-alerts-list.png)
![Audit events for a single correlation id](docs/images/09-audit-trail.png)
![Kafka topics with partition counts and consumer lag](docs/images/13-kafka-ui-topics.png)
![Kibana fraud dashboard](docs/images/14-kibana-dashboard.png)

</details>
```

Keep each PNG under ~300 KB so the repo stays light — it is currently a 2.4 MB clone, and
screenshots are the easiest way to undo that. Crop to the content, and prefer PNG for
terminal/UI captures (sharp text) over JPEG.
