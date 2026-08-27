#!/usr/bin/env bash
# Tear down everything apply.sh created. PVCs (MySQL/Cassandra/Kafka/ES data)
# are deleted with the namespace.
set -euo pipefail
cd "$(dirname "$0")"
NS=fraud-detection

kubectl delete -f networking/ --ignore-not-found
kubectl delete -f apps/ --ignore-not-found
kubectl delete -f infra/ --ignore-not-found
kubectl -n "$NS" delete configmap mysql-init otel-collector-config cassandra-schema --ignore-not-found
kubectl delete -f config.yaml --ignore-not-found
# Deleting the namespace removes any leftover PVCs/PVBoundClaims.
kubectl delete -f namespace.yaml --ignore-not-found

echo "Deleted."
