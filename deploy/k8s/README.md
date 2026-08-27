# Kubernetes deployment

Manifests for running the full fraud-detection platform on a local cluster
(kind / minikube / k3d). All resources use **GA APIs** only: `apps/v1`, `v1`,
`batch/v1`, `networking.k8s.io/v1`, `autoscaling/v2`, `policy/v1`.

> The bundled `infra/` manifests (MySQL, Cassandra, Kafka, Schema Registry,
> Elasticsearch, Kibana, Jaeger, OTel Collector) are **single-node, dev/demo
> only**. In production, run stateful infra via operators or managed services
> and deploy only the application workloads + networking. For OpenShift, see
> [`../openshift`](../openshift).

## Layout

| Path | Contents |
|------|----------|
| `namespace.yaml` | `fraud-detection` namespace (PSA `warn: restricted`) |
| `config.yaml` | `platform-config` ConfigMap + `platform-secrets` Secret (dev-only) |
| `apps/` | Deployment + Service per service (9 total) |
| `infra/` | Dev single-node backing services |
| `networking/` | Ingress, HPA (autoscaling/v2), PodDisruptionBudgets, NetworkPolicies |
| `kustomization.yaml` | Ties it together + generates 3 file-based ConfigMaps |
| `apply.sh` / `delete.sh` | Turnkey apply/teardown |

Every service reuses the Spring **`docker` profile** (`SPRING_PROFILES_ACTIVE=docker`
in `platform-config`): it hardcodes in-cluster DNS hostnames (`mysql`, `kafka`,
`cassandra`, `schema-registry`, `elasticsearch`, `otel-collector`,
`risk-scoring-service`) that exactly match the Service names here, so no separate
`kubernetes` profile is needed. Only `JWT_SECRET`, `MYSQL_USER`, `MYSQL_PASSWORD`,
and the customer-service `ADMIN_*` come from `platform-secrets`.

## Prerequisites

1. A cluster and `kubectl` context (kind/minikube/k3d).
2. **Images built and loaded into the cluster.** From the repo root:
   ```bash
   # build all 9 images (requires JDK 21 — see docs/technology-versions.md)
   for s in api-gateway customer-service account-service transaction-service \
            fraud-detection-service risk-scoring-service alert-service \
            notification-service audit-service; do
     docker build -f "services/$s/Dockerfile" -t "frauddetect/$s:1.0.0" .
   done
   # then load into the cluster, e.g. for kind:
   for s in api-gateway customer-service account-service transaction-service \
            fraud-detection-service risk-scoring-service alert-service \
            notification-service audit-service; do
     kind load docker-image "frauddetect/$s:1.0.0"
   done
   ```
   (minikube: `minikube image load frauddetect/$s:1.0.0`.)
3. `metrics-server` installed (for the HPAs).
4. An `ingress-nginx` controller (for the Ingress).
5. Elasticsearch may need `vm.max_map_count=262144` on the node
   (`minikube ssh -- sudo sysctl -w vm.max_map_count=262144`).

## Deploy

```bash
./apply.sh
```

or with kustomize (note the load-restrictor flag — the ConfigMap generators read
canonical files outside this directory):

```bash
kustomize build --load-restrictor=LoadRestrictionsNone deploy/k8s | kubectl apply -f -
```

Watch it come up:

```bash
kubectl -n fraud-detection get pods -w
```

Infra becomes ready first (MySQL/Kafka/Cassandra/ES health gate their probes);
the `cassandra-schema-init` Job applies `schema.cql`; then the services pass
their startup probes. First boot can take a few minutes on a laptop cluster.

## Access

Add to `/etc/hosts` (point at your ingress IP, e.g. `127.0.0.1` for kind):

```
127.0.0.1  fraud.local kibana.fraud.local jaeger.fraud.local
```

| URL | What |
|-----|------|
| `http://fraud.local/api/v1/...` | Platform API via the gateway |
| `http://fraud.local/api/v1/auth/login` | Obtain a JWT (public) |
| `http://kibana.fraud.local` | Kibana |
| `http://jaeger.fraud.local` | Jaeger traces |

Port-forward alternative (no ingress):

```bash
kubectl -n fraud-detection port-forward svc/api-gateway 8080:8080
```

## Teardown

```bash
./delete.sh
```

## Security posture

- Every application pod runs **non-root** (UID 1001, group 0), `runAsNonRoot: true`,
  `readOnlyRootFilesystem: true` (writable `/tmp` emptyDir only), all Linux
  capabilities dropped, `allowPrivilegeEscalation: false`, seccomp `RuntimeDefault`,
  and `automountServiceAccountToken: false`.
- `NetworkPolicy` denies all ingress by default, permits east-west within the
  namespace, and allows external traffic only to `api-gateway:8080`.
- Secrets are **dev-only placeholders** — replace with a real secrets manager
  (External Secrets Operator, Sealed Secrets, Vault) before any shared use.
  See [`../../docs/security.md`](../../docs/security.md).
