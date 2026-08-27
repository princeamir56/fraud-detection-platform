# OpenShift deployment

A thin [kustomize](https://kustomize.io) overlay on [`../k8s`](../k8s) that makes
the platform run cleanly on OpenShift. Two deltas, nothing forked:

| Concern | Vanilla k8s (`../k8s`) | OpenShift (this overlay) |
|---------|------------------------|--------------------------|
| External entry | `Ingress` (ingressClassName `nginx`) | `Route` (`route.openshift.io/v1`, edge TLS) |
| Pod UID / fsGroup | explicit `runAsUser: 1001`, `fsGroup: 0` | **removed** — the `restricted-v2` SCC assigns them |

Everything else (the 9 Deployments/Services, the dev infra, HPAs, PDBs,
NetworkPolicies, the three file-based ConfigMaps) is inherited verbatim from the
base, so there is no drift to maintain.

## Why remove `runAsUser` / `fsGroup`?

OpenShift admits pods through a **Security Context Constraint**. The default,
`restricted-v2`, does not let a workload pick its own UID — it *assigns* one from
the project's `openshift.io/sa.scc.uid-range` annotation and rejects pods that
hardcode a UID outside that range. Our images are built arbitrary-UID safe
(non-root, primary group `0`, `chmod g=u` on writable paths), so the right move
is to **say nothing about the UID** and let the SCC fill it in. The overlay's
JSON patch strips `runAsUser` and `fsGroup` from the gateway/backend pods; the
rest of the hardened context is already `restricted-v2`-shaped and stays:

- `runAsNonRoot: true`, `allowPrivilegeEscalation: false`
- `readOnlyRootFilesystem: true` (writable `/tmp` `emptyDir` only)
- all capabilities dropped, `seccompProfile: RuntimeDefault`
- `automountServiceAccountToken: false`

The application workloads therefore need **no SCC grants** — the whole point.
Verify after rollout:

```bash
oc -n fraud-detection get pod -l app.kubernetes.io/component=gateway \
  -o jsonpath='{.items[0].metadata.annotations.openshift\.io/scc}{"\n"}'
# -> restricted-v2
```

## Prerequisites

1. `oc login ...` to your cluster (CRC / ROSA / ARO / self-managed).
2. Standalone **`kustomize`** on PATH — `oc kustomize` can't pass
   `--load-restrictor`, which the base's ConfigMap generators require (they read
   the canonical `schema.cql`, MySQL init, and OTel config that the
   docker-compose stack also uses).
3. The 9 images reachable by the cluster — see **Images** below.

## Images

The manifests reference `frauddetect/<svc>:1.0.0`. Make those pullable by the
cluster. Simplest is the **internal registry**:

```bash
# one-time: expose + log in to the internal registry
oc registry login
REG=$(oc registry info)                 # e.g. default-route-openshift-image-registry.apps.<cluster>
oc new-project fraud-detection 2>/dev/null || true

for s in api-gateway customer-service account-service transaction-service \
         fraud-detection-service risk-scoring-service alert-service \
         notification-service audit-service; do
  docker build -f "services/$s/Dockerfile" -t "frauddetect/$s:1.0.0" .        # needs JDK 21
  docker tag  "frauddetect/$s:1.0.0" "$REG/fraud-detection/$s:1.0.0"
  docker push "$REG/fraud-detection/$s:1.0.0"
done
```

Then point the overlay at the pushed images by adding an `images:` block to
[`kustomization.yaml`](kustomization.yaml) (kustomize rewrites every reference):

```yaml
images:
  - name: frauddetect/api-gateway
    newName: image-registry.openshift-image-registry.svc:5000/fraud-detection/api-gateway
  # ...one entry per service
```

(Using the in-cluster registry service name `image-registry.openshift-image-registry.svc:5000`
means pods pull without leaving the cluster.)

## Deploy

```bash
./apply.sh
```

or directly:

```bash
kustomize build --load-restrictor=LoadRestrictionsNone deploy/openshift | oc apply -f -
```

Get the API URL and watch rollout:

```bash
oc -n fraud-detection get pods -w
oc -n fraud-detection get route api-gateway -o jsonpath='https://{.spec.host}{"\n"}'
```

## Dev infra on OpenShift ⚠️

The bundled `infra/` (MySQL, Cassandra, Kafka, Schema Registry, Elasticsearch,
Kibana, Jaeger, OTel Collector) is **single-node dev/demo** and is *not*
`restricted-v2`-clean: the MySQL/Kafka/Cassandra community images start as root
and set explicit `fsGroup`s. On OpenShift you have two paths:

- **Recommended — operators / managed services.** Run stateful backends via their
  operators (AMQ Streams for Kafka, a MySQL/Cassandra operator, Elasticsearch via
  ECK/OpenSearch) or managed offerings, and deploy only the application workloads
  from this overlay. This is the production posture.
- **Throwaway demo only.** Grant the broader SCC to the project's default
  ServiceAccount so the community infra images can start:

  ```bash
  oc -n fraud-detection adm policy add-scc-to-user anyuid -z default
  ```

  This widens `default` for the whole project (the app pods don't need it — they
  stay `restricted-v2` by their own context, but the grant makes `anyuid`
  *available*). Use a disposable project, never a shared cluster.

## Teardown

```bash
./delete.sh
```

## Files

| Path | Purpose |
|------|---------|
| `kustomization.yaml` | Overlay: base `../k8s`, delete Ingress, add Route, patch app SCC context |
| `route.yaml` | Edge-TLS Routes for the gateway (+ Kibana/Jaeger dev UIs) |
| `apply.sh` / `delete.sh` | Turnkey deploy/teardown via `oc` |

See also [`../../docs/openshift.md`](../../docs/openshift.md) for the fuller
writeup and [`../../docs/security.md`](../../docs/security.md) for the platform
security model.
