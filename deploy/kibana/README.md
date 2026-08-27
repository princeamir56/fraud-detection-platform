# Kibana saved objects

Pre-built **data views, saved searches, visualizations, and dashboards** for the
platform's four Elasticsearch indices. Import them and you land straight in
working dashboards instead of building views by hand.

## What's here

| File | Purpose |
|------|---------|
| `saved-objects.ndjson` | The saved objects (import this) |
| `generate-saved-objects.mjs` | Regenerates the NDJSON from the index field lists |
| `import-saved-objects.sh` | Imports via the Kibana Saved Objects `_import` API |

### Indices covered

| Index | Written by | Time field | Data view |
|-------|-----------|------------|-----------|
| `transactions` | fraud-detection-service | `occurredAt` | ✔ |
| `fraud-events` | fraud-detection-service | `occurredAt` | ✔ |
| `alerts` | alert-service | `createdAt` | ✔ |
| `audit-events` | audit-service | `occurredAt` | ✔ |

### Dashboards

- **Fraud Operations** — transaction & fraud-event totals, average score, open
  alerts, severity mix (`fraud-events.severity`), decision mix
  (`transactions.decision` = ALLOW/REVIEW/BLOCK), top triggered rules
  (`triggeredRuleCodes`), and fraud events over time stacked by severity.
- **Audit & Security** — audit-event volume, breakdown by `eventType` and
  `severity`, and activity over time.

### Saved searches (Discover)

- **Fraud events — HIGH & CRITICAL**
- **Transactions — blocked** (`decision: "BLOCK"`)
- **Alerts — open** (`status: "OPEN"`)

## Import

Bring the stack up first (`deploy/docker` or `deploy/k8s`) so Kibana is
reachable, then:

```bash
./import-saved-objects.sh
```

Defaults to `http://localhost:5601` (the compose port mapping). For a different
host or a secured Kibana:

```bash
KIBANA_URL=https://kibana.example.com KIBANA_USER=elastic KIBANA_PASSWORD=… ./import-saved-objects.sh
```

**Alternatives**

- **Kibana UI:** Stack Management → Saved Objects → Import → pick
  `saved-objects.ndjson` → *Automatically overwrite conflicts*.
- **curl:**
  ```bash
  curl -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
    -H "kbn-xsrf: true" --form file=@saved-objects.ndjson
  ```

Import any time — the objects don't require the indices to exist yet. Panels
populate once transactions start flowing (the services create the indices with
explicit mappings at startup; see each service's
`resources/elasticsearch/*-index.json`). If Discover shows no fields for a data
view, open it under Stack Management → Data Views and *Refresh field list*.

## Regenerating

The NDJSON is generated so the field lists stay in lockstep with the index
mappings (the single source of truth). After changing a mapping or adding a
panel, edit `generate-saved-objects.mjs` and:

```bash
node generate-saved-objects.mjs
```

## Version note

Targeted at **Kibana 8.x / 9.x** (the stack pins Elastic 9.0.x). Visualizations
use the classic **aggregation-based** saved-object shape, which is stable and
import-portable across those versions. The `_import` API imports valid objects
and reports per-object errors without rolling the rest back, so even if a future
Kibana rejects one visualization, the data views and saved searches still import
and the dashboards can be rebuilt on top of them.
