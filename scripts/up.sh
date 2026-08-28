#!/usr/bin/env bash
# =============================================================================
# Fraud Detection Platform — one-command local launch (Linux / macOS / Git Bash).
#
#   scripts/up.sh                 lean stack: infra + Jaeger + the nine services
#   scripts/up.sh --full          also starts Kibana and Kafka UI (~2 GB more)
#   scripts/up.sh --no-build      reuse existing images, skip the image builds
#   scripts/up.sh --recreate      force-recreate containers (config changed)
#
# Runnable from anywhere in the repo. Before launching it checks the things that
# actually break a first run on an unfamiliar machine — Docker daemon reachable,
# Compose v2, enough memory allocated, host ports free — and names the exact fix
# for each. Then it waits for every container to report healthy and prints the
# endpoints.
#
# Windows PowerShell users: run scripts\up.ps1 instead.
# =============================================================================
set -euo pipefail

PROJECT_NAME="fraud-detection-platform"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-600}"

# Memory floors (GiB) — warnings, not hard failures: the daemon may report an
# unhelpful value on exotic setups and we would rather try than refuse.
MIN_MEM_GIB_LEAN=8
MIN_MEM_GIB_FULL=12

FULL=0
BUILD=1
RECREATE=0

# ------------------------------------------------------------------ helpers --
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  BOLD=$'\033[1m'; RED=$'\033[31m'; YELLOW=$'\033[33m'; GREEN=$'\033[32m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
  BOLD=''; RED=''; YELLOW=''; GREEN=''; DIM=''; RESET=''
fi

info()  { printf '%s\n' "$*"; }
ok()    { printf '%s  ok%s  %s\n' "$GREEN" "$RESET" "$*"; }
warn()  { printf '%swarn%s  %s\n' "$YELLOW" "$RESET" "$*" >&2; }
fail()  { printf '%sfail%s  %s\n' "$RED" "$RESET" "$*" >&2; }
die()   { fail "$*"; exit 1; }

usage() {
  # Echo this file's leading comment block (minus the ==== rules) as the help text.
  awk 'NR>1 && /^#/ { sub(/^# ?/, ""); if ($0 !~ /^=+$/) print; next } NR>1 { exit }' "$0"
  exit 0
}

# ------------------------------------------------------------------- args ----
while [ $# -gt 0 ]; do
  case "$1" in
    --full)       FULL=1 ;;
    --no-build)   BUILD=0 ;;
    --recreate)   RECREATE=1 ;;
    -h|--help)    usage ;;
    *)            die "unknown option: $1  (try --help)" ;;
  esac
  shift
done

# --------------------------------------------------- locate the compose dir --
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$REPO_ROOT/deploy/docker"
[ -f "$COMPOSE_DIR/docker-compose.yml" ] || die "cannot find deploy/docker/docker-compose.yml under $REPO_ROOT"

# Run from the compose directory rather than passing -f/--project-directory:
# Compose then picks up docker-compose.yml and .env itself, and we avoid the
# path-translation quirks of Git Bash on Windows.
cd "$COMPOSE_DIR"

# The consoles profile must be passed to every subcommand that should see those
# two containers — including `down` — or Compose treats them as out of scope.
# Expressed as a branch rather than an array because expanding an empty array
# under `set -u` is an error on the bash 3.2 that macOS still ships.
compose() {
  if [ "$FULL" -eq 1 ]; then
    docker compose -p "$PROJECT_NAME" --profile consoles "$@"
  else
    docker compose -p "$PROJECT_NAME" "$@"
  fi
}

# --------------------------------------------------------------- preflight ---
info "${BOLD}Preflight${RESET}"

command -v docker >/dev/null 2>&1 \
  || die "docker not found on PATH. Install Docker Desktop (Windows/macOS) or Docker Engine 23+ (Linux)."

docker info >/dev/null 2>&1 \
  || die "the Docker daemon is not reachable. Start Docker Desktop (or 'sudo systemctl start docker') and retry."
ok "Docker daemon reachable"

# Compose v2 is required: docker-compose.yml uses `condition:
# service_completed_successfully` and top-level `name:`, neither of which the
# legacy Python docker-compose understands.
COMPOSE_VERSION="$(docker compose version --short 2>/dev/null || true)"
[ -n "$COMPOSE_VERSION" ] \
  || die "'docker compose' (v2) is unavailable. The legacy 'docker-compose' binary cannot run this file — upgrade Docker Desktop, or install the docker-compose-plugin package."
case "$COMPOSE_VERSION" in
  1.*) die "Compose $COMPOSE_VERSION is too old; v2.0+ is required." ;;
esac
ok "Docker Compose v$COMPOSE_VERSION"

# BuildKit carries the shared Maven cache mount in the Dockerfiles. It is the
# default in every supported Docker version, but an explicit opt-out breaks builds.
if [ "$BUILD" -eq 1 ] && [ "${DOCKER_BUILDKIT:-1}" = "0" ]; then
  die "DOCKER_BUILDKIT=0 is set, but the service Dockerfiles need BuildKit for the shared Maven cache. Unset it and retry."
fi

# Memory: the single most common cause of containers dying with exit code 137.
MEM_BYTES="$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)"
MIN_MEM_GIB=$MIN_MEM_GIB_LEAN
[ "$FULL" -eq 1 ] && MIN_MEM_GIB=$MIN_MEM_GIB_FULL
if [ "$MEM_BYTES" -gt 0 ] 2>/dev/null; then
  MEM_GIB=$(( MEM_BYTES / 1073741824 ))
  if [ "$MEM_GIB" -lt "$MIN_MEM_GIB" ]; then
    warn "Docker has ${MEM_GIB} GiB of memory; this stack wants at least ${MIN_MEM_GIB} GiB."
    warn "  Containers may be OOM-killed (exit code 137). Raise it in"
    warn "  Docker Desktop -> Settings -> Resources -> Memory, or drop --full to skip the consoles."
  else
    ok "Docker memory: ${MEM_GIB} GiB (floor ${MIN_MEM_GIB} GiB)"
  fi
else
  warn "could not read the daemon's memory allocation; skipping that check"
fi

# Host ports. Skipped when our own stack is already up, since those containers
# legitimately hold the ports.
ALREADY_UP="$(compose ps -q 2>/dev/null | tr -d '[:space:]')"
if [ -n "$ALREADY_UP" ]; then
  info "${DIM}      stack already running — skipping the port scan${RESET}"
else
  port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

  # "port:VAR_TO_OVERRIDE:what listens there"
  PORT_CHECKS=(
    "${MYSQL_PORT:-3306}:MYSQL_PORT:MySQL"
    "${CASSANDRA_PORT:-9042}:CASSANDRA_PORT:Cassandra"
    "${KAFKA_INTERNAL_PORT:-9092}:KAFKA_INTERNAL_PORT:Kafka (in-network listener)"
    "${KAFKA_HOST_PORT:-29092}:KAFKA_HOST_PORT:Kafka (host listener)"
    "${SCHEMA_REGISTRY_PORT:-8090}:SCHEMA_REGISTRY_PORT:Schema Registry"
    "${ES_PORT:-9200}:ES_PORT:Elasticsearch"
    "${OTLP_HTTP_PORT:-4318}:OTLP_HTTP_PORT:OTel Collector (HTTP)"
    "${OTLP_GRPC_PORT:-4317}:OTLP_GRPC_PORT:OTel Collector (gRPC)"
    "${JAEGER_UI_PORT:-16686}:JAEGER_UI_PORT:Jaeger UI"
    "${GATEWAY_PORT:-8080}:GATEWAY_PORT:api-gateway"
    "${CUSTOMER_PORT:-8081}:CUSTOMER_PORT:customer-service"
    "${ACCOUNT_PORT:-8082}:ACCOUNT_PORT:account-service"
    "${TRANSACTION_PORT:-8083}:TRANSACTION_PORT:transaction-service"
    "${FRAUD_PORT:-8084}:FRAUD_PORT:fraud-detection-service"
    "${RISK_HTTP_PORT:-8085}:RISK_HTTP_PORT:risk-scoring-service (REST)"
    "${RISK_GRPC_PORT:-9095}:RISK_GRPC_PORT:risk-scoring-service (gRPC)"
    "${ALERT_PORT:-8086}:ALERT_PORT:alert-service"
    "${NOTIFICATION_PORT:-8087}:NOTIFICATION_PORT:notification-service"
    "${AUDIT_PORT:-8088}:AUDIT_PORT:audit-service"
  )
  [ "$FULL" -eq 1 ] && PORT_CHECKS+=(
    "${KIBANA_PORT:-5601}:KIBANA_PORT:Kibana"
    "${KAFKA_UI_PORT:-8100}:KAFKA_UI_PORT:Kafka UI"
  )

  CONFLICTS=0
  for entry in "${PORT_CHECKS[@]}"; do
    port="${entry%%:*}"; rest="${entry#*:}"; var="${rest%%:*}"; what="${rest#*:}"
    if port_in_use "$port"; then
      CONFLICTS=$((CONFLICTS + 1))
      fail "port $port is already in use (needed by $what)"
      info "        fix: add ${BOLD}${var}=<free port>${RESET} to deploy/docker/.env"
    fi
  done
  if [ "$CONFLICTS" -gt 0 ]; then
    die "$CONFLICTS port conflict(s). Free the ports or override them as shown above, then retry."
  fi
  ok "all host ports free"
fi

if [ -f .env ]; then
  ok ".env found (overrides the compose defaults)"
else
  info "${DIM}      no .env — using the dev defaults baked into docker-compose.yml${RESET}"
fi

# ------------------------------------------------------------------ launch ---
UP_ARGS=(up -d)
[ "$BUILD" -eq 1 ] && UP_ARGS+=(--build)
[ "$RECREATE" -eq 1 ] && UP_ARGS+=(--force-recreate)

info ""
if [ "$FULL" -eq 1 ]; then
  info "${BOLD}Starting the full stack (with consoles)${RESET}"
else
  info "${BOLD}Starting the lean stack${RESET}  ${DIM}(--full adds Kibana + Kafka UI)${RESET}"
fi
[ "$BUILD" -eq 1 ] && info "${DIM}First build compiles nine images from source; expect several minutes.${RESET}"
info ""

compose "${UP_ARGS[@]}"

# ------------------------------------------------------- wait for health ----
info ""
info "${BOLD}Waiting for containers to become healthy${RESET} ${DIM}(timeout ${HEALTH_TIMEOUT_SECONDS}s)${RESET}"

deadline=$(( SECONDS + HEALTH_TIMEOUT_SECONDS ))
last_report=""
while :; do
  pending=""
  broken=""
  while read -r id; do
    [ -n "$id" ] || continue
    # name status health exitcode — .State.Health is absent when no healthcheck.
    read -r name status health exitcode <<<"$(docker inspect \
      --format '{{.Name}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} {{.State.ExitCode}}' \
      "$id" 2>/dev/null || echo "? unknown none 0")"
    name="${name#/}"
    case "$status:$health" in
      running:healthy|running:none) ;;                       # ready
      running:starting)  pending="$pending $name" ;;
      running:unhealthy) pending="$pending $name(unhealthy)" ;;
      exited:*)
        # cassandra-init is a one-shot: exit 0 is success, anything else is not.
        [ "$exitcode" = "0" ] || broken="$broken $name(exit $exitcode)"
        ;;
      *) pending="$pending $name($status)" ;;
    esac
  done <<< "$(compose ps -q)"

  if [ -n "$broken" ]; then
    info ""
    fail "container(s) failed:$broken"
    info "        logs:  ${BOLD}docker compose -p $PROJECT_NAME logs --tail=80${RESET}"
    info "        exit code 137 means out of memory — raise Docker's memory allocation."
    exit 1
  fi

  if [ -z "$pending" ]; then
    info ""
    ok "all containers healthy"
    break
  fi

  if [ "$SECONDS" -ge "$deadline" ]; then
    info ""
    fail "timed out after ${HEALTH_TIMEOUT_SECONDS}s; still not ready:$pending"
    info "        status: ${BOLD}docker compose -p $PROJECT_NAME ps${RESET}"
    info "        logs:   ${BOLD}docker compose -p $PROJECT_NAME logs --tail=80${RESET}"
    exit 1
  fi

  if [ "$pending" != "$last_report" ]; then
    info "${DIM}      waiting for:$pending${RESET}"
    last_report="$pending"
  fi
  sleep 5
done

# ---------------------------------------------------------------- endpoints --
# Ask Compose for the actually-published ports so the table stays correct under
# any .env override.
published() { compose port "$1" "$2" 2>/dev/null | sed 's/^.*://' || true; }

info ""
info "${BOLD}Endpoints${RESET}"
printf '  %-24s http://localhost:%s\n' "api-gateway"        "$(published api-gateway 8080)"
printf '  %-24s http://localhost:%s/swagger-ui.html\n' "transaction-service" "$(published transaction-service 8083)"
printf '  %-24s http://localhost:%s\n' "Jaeger (traces)"    "$(published jaeger 16686)"
printf '  %-24s http://localhost:%s/subjects\n' "Schema Registry"  "$(published schema-registry 8081)"
printf '  %-24s http://localhost:%s\n' "Elasticsearch"      "$(published elasticsearch 9200)"
if [ "$FULL" -eq 1 ]; then
  printf '  %-24s http://localhost:%s\n' "Kibana"   "$(published kibana 5601)"
  printf '  %-24s http://localhost:%s\n' "Kafka UI" "$(published kafka-ui 8080)"
else
  info "  ${DIM}Kibana / Kafka UI      not started — re-run with --full${RESET}"
fi

info ""
info "${BOLD}Next${RESET}"
info "  Get a token:  curl -s localhost:$(published api-gateway 8080)/api/v1/auth/login \\"
info "                  -H 'Content-Type: application/json' \\"
info "                  -d '{\"username\":\"${ADMIN_USERNAME:-admin}\",\"password\":\"${ADMIN_PASSWORD:-admin-change-me}\"}'"
info "  Tear down:    scripts/down.sh          ${DIM}(add --volumes to wipe the data)${RESET}"
