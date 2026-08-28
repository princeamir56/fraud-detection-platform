#!/usr/bin/env bash
# Tear down everything apply.sh created. Deleting the namespace removes the
# PVCs (MySQL/Cassandra/Kafka/ES data) with it.
set -euo pipefail
cd "$(dirname "$0")"

command -v oc >/dev/null 2>&1 || { echo "ERROR: 'oc' not found on PATH." >&2; exit 1; }
command -v kustomize >/dev/null 2>&1 || { echo "ERROR: standalone 'kustomize' required." >&2; exit 1; }

kustomize build --load-restrictor=LoadRestrictionsNone . | oc delete --ignore-not-found -f -

echo "Deleted."
