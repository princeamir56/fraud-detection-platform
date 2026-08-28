#!/usr/bin/env bash
# =============================================================================
# Deploy the platform to OpenShift.
#
# Builds the ../k8s base through the OpenShift overlay (Route instead of Ingress;
# application pods stripped of explicit runAsUser/fsGroup so the restricted-v2
# SCC assigns them) and pipes it to `oc apply`.
#
# Usage:  ./apply.sh
# Prereqs:
#   - `oc login ...` to your cluster, with a current project/permissions
#   - standalone `kustomize` on PATH (the base's ConfigMap generators read
#     canonical files outside deploy/openshift, needing --load-restrictor)
#   - the 9 images reachable by the cluster (see README.md "Images")
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

command -v oc >/dev/null 2>&1 || { echo "ERROR: 'oc' not found on PATH." >&2; exit 1; }
command -v kustomize >/dev/null 2>&1 || {
  echo "ERROR: standalone 'kustomize' required (oc/kubectl kustomize can't pass --load-restrictor)." >&2
  echo "       Install: https://kubectl.docs.kubernetes.io/installation/kustomize/" >&2
  exit 1
}
oc whoami >/dev/null 2>&1 || { echo "ERROR: not logged in — run 'oc login ...' first." >&2; exit 1; }

echo "==> rendering overlay + applying (Namespace is emitted first, so a re-run"
echo "    settles any resource that lost the initial ordering race)"
kustomize build --load-restrictor=LoadRestrictionsNone . | oc apply -f -

echo
echo "Done. Useful next steps:"
echo "  oc -n fraud-detection get pods -w"
echo "  oc -n fraud-detection get route api-gateway -o jsonpath='{.spec.host}{\"\\n\"}'"
echo "  # confirm the app pods really landed on restricted-v2:"
echo "  oc -n fraud-detection get pod -l app.kubernetes.io/component=gateway \\"
echo "     -o jsonpath='{.items[0].metadata.annotations.openshift\\.io/scc}{\"\\n\"}'"
