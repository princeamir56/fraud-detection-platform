#!/usr/bin/env bash
# =============================================================================
# Turnkey apply for the full platform on a local cluster (kind/minikube).
# No kustomize flags needed: the three file-based ConfigMaps are created with
# `kubectl create --from-file` straight from their canonical sources, so there
# is a single source of truth and no drift with the docker-compose stack.
#
# Usage:  ./apply.sh
# Prereqs: kubectl context pointing at your dev cluster; images built and
#          loaded into the cluster (see README.md), metrics-server for HPA,
#          an ingress-nginx controller for the Ingress.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"
NS=fraud-detection

echo "==> namespace + shared config/secrets"
kubectl apply -f namespace.yaml
kubectl apply -f config.yaml

echo "==> file-based ConfigMaps (canonical sources)"
kubectl -n "$NS" create configmap mysql-init \
  --from-file=01-init.sh=../docker/mysql/init/01-init.sh \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NS" create configmap otel-collector-config \
  --from-file=otel-collector-config.yaml=../docker/otel-collector-config.yaml \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NS" create configmap cassandra-schema \
  --from-file=schema.cql=../../services/fraud-detection-service/src/main/resources/cassandra/schema.cql \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> dev infra"
kubectl apply -f infra/

echo "==> application workloads"
kubectl apply -f apps/

echo "==> networking / policy"
kubectl apply -f networking/

echo
echo "Done. Watch rollout with:"
echo "  kubectl -n $NS get pods -w"
