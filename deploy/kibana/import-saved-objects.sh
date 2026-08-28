#!/usr/bin/env bash
# =============================================================================
# Import the platform's Kibana saved objects (data views, saved searches,
# visualizations, dashboards) via the Saved Objects _import API.
#
#   ./import-saved-objects.sh
#
# Env:
#   KIBANA_URL       default http://localhost:5601   (compose maps Kibana here)
#   KIBANA_USER      optional — set both to send HTTP basic auth
#   KIBANA_PASSWORD  optional
#
# The dev docker-compose stack runs Kibana with security disabled, so no auth is
# needed there. `overwrite=true` makes this idempotent — re-running updates the
# objects in place.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
NDJSON="saved-objects.ndjson"
[ -f "$NDJSON" ] || { echo "ERROR: $NDJSON not found (run: node generate-saved-objects.mjs)" >&2; exit 1; }

AUTH=()
if [ -n "${KIBANA_USER:-}" ] && [ -n "${KIBANA_PASSWORD:-}" ]; then
  AUTH=(-u "${KIBANA_USER}:${KIBANA_PASSWORD}")
fi

echo "==> waiting for Kibana at ${KIBANA_URL} to be available"
for i in $(seq 1 60); do
  status="$(curl -fsS "${AUTH[@]}" "${KIBANA_URL}/api/status" 2>/dev/null || true)"
  case "$status" in
    *'"level":"available"'*) echo "    Kibana is available"; break ;;
  esac
  if [ "$i" -eq 60 ]; then echo "ERROR: Kibana not available after ~5 min" >&2; exit 1; fi
  sleep 5
done

echo "==> importing $NDJSON (overwrite=true)"
resp="$(curl -sS -X POST "${AUTH[@]}" \
  "${KIBANA_URL}/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@"${NDJSON}")"

echo "$resp"

# Summarize / set exit status. Prefer python3 for a clean report; fall back to grep.
if command -v python3 >/dev/null 2>&1; then
  echo "$resp" | python3 -c '
import json,sys
d=json.load(sys.stdin)
print(f"\nimported: {d.get(\"successCount\",0)}  success={d.get(\"success\")}")
for e in d.get("errors",[]) or []:
    print(f"  ERROR {e.get(\"type\")}:{e.get(\"id\")} -> {e.get(\"error\",{}).get(\"type\")}")
sys.exit(0 if d.get("success") else 1)
'
else
  case "$resp" in
    *'"success":true'*) echo; echo "Import succeeded." ;;
    *) echo; echo "Import reported errors (see response above)."; exit 1 ;;
  esac
fi

echo
echo "Open Kibana -> Analytics -> Dashboard:  'Fraud Operations' and 'Audit & Security'."
