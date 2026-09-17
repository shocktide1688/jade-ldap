# Jade LDAP on k3s

This directory contains the non-secret workload definition for the independent
Jade LDAP server. Runtime secrets are created directly in the `jade-ldap`
namespace and must not be committed.

## Exposed endpoints

- Admin UI: `http://<node-ip>:30173`
- Health/API: `http://<node-ip>:31080`
- LDAP and StartTLS: `<node-ip>:31389`
- LDAPS: `<node-ip>:31636`

The backend runs two replicas against one PostgreSQL directory store. Both
replicas mount the same Kubernetes-managed JKS certificate so LDAP clients see
one certificate regardless of the selected pod.

The backend image is a GraalVM/Mandrel native executable built by Drone from
`Dockerfile.ldap`. The runtime image does not contain a JVM, and deployment
manifests pin the immutable full Git SHA image tag produced by CI.

## Required secrets

Create these secrets before applying `jade-ldap.yaml`:

- `jade-ldap-secrets`: PostgreSQL, LDAP admin, JWT, crypto, and JKS passwords.
- `jade-ldap-tls`: `ldap-keystore.jks`.
- `gitea-registry`: registry pull credentials for the private images.

Generate or retrieve the values from a controlled credential store. Do not put
literal credentials in this repository.

## Deploy and verify

```bash
kubectl apply -f deploy/k3s/jade-ldap.yaml
kubectl -n jade-ldap rollout status statefulset/postgres --timeout=300s
kubectl -n jade-ldap rollout status statefulset/redis --timeout=300s
kubectl -n jade-ldap rollout status deployment/jade-ldap --timeout=600s
kubectl -n jade-ldap rollout status deployment/jade-ldap-frontend --timeout=300s
kubectl -n jade-ldap get pod,pvc,service
```

The PVCs intentionally use the cluster's `local-path` storage class. This is
suitable for the current single-node k3s environment; move PostgreSQL to
replicated storage or an external managed database before adding nodes.

## Automated delivery

The `main` branch is delivered through Drone and Argo CD:

1. Drone runs the backend and frontend quality gates.
2. Drone builds and pushes both images with the full source commit SHA.
3. Drone updates `apps/jade-ldap/app.yaml` in the separate
   `reqpilot-admin/jade-ldap-gitops` repository.
4. Argo CD watches that repository and automatically syncs the `jade-ldap`
   application with pruning and self-healing enabled.

`jade-ldap-application.yaml` is the one-time Argo CD bootstrap manifest. Apply
it directly only when recreating the Argo CD Application; normal releases must
flow through the GitOps repository rather than direct `kubectl apply` calls.
