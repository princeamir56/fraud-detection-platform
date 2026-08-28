#!/usr/bin/env bash
# =============================================================================
# Fraud Detection Platform — tear down the local stack.
#
#   scripts/down.sh              stop and remove containers and the network
#   scripts/down.sh --volumes    also delete the MySQL / Cassandra / ES / Kafka
#                                volumes (next start re-initialises from scratch)
#
# Always passes --profile consoles so Kibana and Kafka UI are removed too;
# without it Compose considers profiled services out of scope and leaves them
# running. Runnable from anywhere in the repo.
# =============================================================================
set -euo pipefail

PROJECT_NAME="fraud-detection-platform"
WIPE=0

while [ $# -gt 0 ]; do
  case "$1" in
    -v|--volumes) WIPE=1 ;;
    -h|--help)    awk 'NR>1 && /^#/ { sub(/^# ?/, ""); if ($0 !~ /^=+$/) print; next } NR>1 { exit }' "$0"; exit 0 ;;
    *)            printf 'unknown option: %s (try --help)\n' "$1" >&2; exit 1 ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/deploy/docker"
[ -f "$COMPOSE_DIR/docker-compose.yml" ] || { echo "cannot find deploy/docker/docker-compose.yml" >&2; exit 1; }
cd "$COMPOSE_DIR"

command -v docker >/dev/null 2>&1 || { echo "docker not found on PATH." >&2; exit 1; }
docker info >/dev/null 2>&1 || {
  echo "the Docker daemon is not reachable, so there is nothing to stop. Start Docker Desktop (or 'sudo systemctl start docker') first if you expected containers to be running." >&2
  exit 1
}

DOWN_ARGS=(down --remove-orphans)
if [ "$WIPE" -eq 1 ]; then
  DOWN_ARGS+=(--volumes)
  echo "Removing containers AND volumes — all MySQL, Cassandra, Elasticsearch and Kafka data will be lost."
fi

docker compose -p "$PROJECT_NAME" --profile consoles "${DOWN_ARGS[@]}"
echo "Stack down."
