# OpenShift deployment

The OpenShift deployment is a **thin kustomize overlay** on [`deploy/k8s`](../deploy/k8s)
— nothing is forked. It changes exactly two things and inherits everything else
(the 9 Deployments/Services, dev infra, HPAs, PDBs, NetworkPolicies, the three
file-based ConfigMaps) verbatim from the base. Files live in
[`deploy/openshift`](../deploy/openshift).

| Concern | Vanilla k8s | OpenShift overlay |
|---------|-------------|-------------------|
| External entry | `Ingress` (nginx) | `Route` (`route.openshift.io/v1`, edge TLS) |
| Pod UID / fsGroup | explicit `runAsUser: 1001`, `fsGroup: 0` | **removed** — `restricted-v2` SCC assigns them |

## Why remove `runAsUser` / `fsGroup`?

OpenShift admits pods through a **Security Context Constraint**. The default
`restricted-v2` does **not** let a workload choose its own UID — it *assigns* one
from the project's `openshift.io/sa.scc.uid-range` and rejects pods that hardcode a
UID outside that range. The images are built **arbitrary-UID safe** (non-root,
primary group `0`, `chmod g=u` on writable paths), so the correct move is to say
nothing about the UID and let the SCC fill it in. The rest of the hardened context
is already `restricted-v2`-shaped and stays: `runAsNonRoot`, `allowPrivilegeEscalation:
false`, `readOnlyRootFilesystem`, all caps dropped, `seccompProfile: RuntimeDefault`,
`automountServiceAccountToken: false`.

The application workloads therefore need **no SCC grants** — that's the whole point.

## How the overlay does it

[`kustomization.yaml`](../deploy/openshift/kustomization.yaml) applies two patches:

```yaml
resources: [../k8s, route.yaml]
patches:
  # 1. Drop the nginx Ingress (Route replaces it)
  - target: { kind: Ingress, name: fraud-platform }
    patch: |-
      $patch: delete
      apiVersion: networking.k8s.io/v1
      kind: Ingress
      metadata: { name: fraud-platform }
  # 2. Strip UID/fsGroup from the APP pods only (component in {gateway,backend});
  #    infra Deployments don't carry those fields, so the labelSelector avoids
  #    remove-failures on them.
  - target:
      kind: Deployment
      labelSelector: "app.kubernetes.io/component in (gateway,backend)"
    patch: |-
      - op: remove
        path: /spec/template/spec/securityContext/runAsUser
      - op: remove
        path: /spec/template/spec/securityContext/fsGroup
```

[`route.yaml`](../deploy/openshift/route.yaml) adds edge-TLS Routes for the gateway
(HTTPS with HTTP→HTTPS redirect) and the Kibana/Jaeger dev UIs.

## Images

Manifests reference `frauddetect/<svc>:1.0.0`; make them pullable by the cluster.
Simplest is the **internal registry**:

```bash
oc registry login
REG=$(oc registry info)                     # external route of the registry
oc new-project fraud-detection 2>/dev/null || true
for s in api-gateway customer-service account-service transaction-service \
         fraud-detection-service risk-scoring-service alert-service \
         notification-service audit-service; do
  docker build -f "services/$s/Dockerfile" -t "frauddetect/$s:1.0.0" .   # needs JDK 21
  docker tag  "frauddetect/$s:1.0.0" "$REG/fraud-detection/$s:1.0.0"
  docker push "$REG/fraud-detection/$s:1.0.0"
done
```

Then point the overlay at the pushed images via an `images:` block in
`kustomization.yaml` (kustomize rewrites every reference to the in-cluster registry
service `image-registry.openshift-image-registry.svc:5000/fraud-detection/<svc>`, so
pods pull without leaving the cluster).

## Deploy

```bash
cd deploy/openshift && ./apply.sh
```

or directly (keep the load-restrictor flag — the base's ConfigMap generators read
canonical files outside its dir, and `oc kustomize` can't pass the flag, so use
standalone `kustomize`):

```bash
kustomize build --load-restrictor=LoadRestrictionsNone deploy/openshift | oc apply -f -
```

Get the API URL and verify the app pods really landed on `restricted-v2`:

```bash
oc -n fraud-detection get route api-gateway -o jsonpath='https://{.spec.host}{"\n"}'
oc -n fraud-detection get pod -l app.kubernetes.io/component=gateway \
  -o jsonpath='{.items[0].metadata.annotations.openshift\.io/scc}{"\n"}'   # -> restricted-v2
```

## ⚠️ Dev infra on OpenShift

The bundled `infra/` is single-node dev/demo and is **not** `restricted-v2`-clean —
the MySQL/Kafka/Cassandra community images start as root / set explicit `fsGroup`s.
Two paths:

- **Recommended (production posture):** run stateful backends via operators (AMQ
  Streams for Kafka, a MySQL/Cassandra operator, ECK/OpenSearch for Elasticsearch)
  or managed services, and deploy **only** the application workloads from this
  overlay — they stay `restricted-v2` with no grants.
- **Throwaway demo only:** grant a broader SCC to the project's default
  ServiceAccount so the community infra images can start:
  ```bash
  oc -n fraud-detection adm policy add-scc-to-user anyuid -z default
  ```
  Use a disposable project, never a shared cluster. (The app pods don't need this —
  they keep `restricted-v2` by their own context.)

## Teardown

```bash
cd deploy/openshift && ./delete.sh
```

See [`security.md`](security.md) for the platform security model.
