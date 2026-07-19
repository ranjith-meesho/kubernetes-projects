# Kubernetes Production Deployment Checklist

## Resource Management

- [ ] CPU and memory **requests** set on every container.
- [ ] CPU and memory **limits** set on every container (or deliberately omitted with rationale).
- [ ] Requests/limits validated against real usage via load testing or historical `kubectl top` data.
- [ ] Namespace-level `ResourceQuota` defined to cap total consumption.
- [ ] `LimitRange` defined to set sane defaults for teams that forget to set requests/limits.
- [ ] QoS class (Guaranteed/Burstable/BestEffort) understood and intentional per workload.

## Health & Resilience

- [ ] `readinessProbe` configured so traffic only reaches ready Pods.
- [ ] `livenessProbe` configured to restart truly stuck containers.
- [ ] `startupProbe` used for slow-starting apps to avoid premature liveness kills.
- [ ] `terminationGracePeriodSeconds` tuned to allow graceful shutdown (drain connections).
- [ ] `preStop` hook implemented where graceful drain logic is needed.
- [ ] `PodDisruptionBudget` defined to limit voluntary disruptions during node drains/upgrades.
- [ ] Minimum of 2-3 replicas for any critical service (no single point of failure).
- [ ] Pod anti-affinity / topology spread constraints set to avoid all replicas on one node/zone.

## Security

- [ ] Containers run as non-root (`runAsNonRoot: true`, explicit `runAsUser`).
- [ ] `readOnlyRootFilesystem: true` set where possible.
- [ ] Unnecessary Linux capabilities dropped (`drop: ["ALL"]`, add back only what's needed).
- [ ] `allowPrivilegeEscalation: false` set on all containers.
- [ ] No `privileged: true` containers unless absolutely required and justified.
- [ ] Secrets stored in `Secret` objects (or external vault), never hardcoded in images/env in plain manifests.
- [ ] RBAC roles scoped to least privilege (no blanket `cluster-admin` for workloads).
- [ ] ServiceAccount tokens not auto-mounted unless the Pod needs API access (`automountServiceAccountToken: false`).
- [ ] Image scanning integrated into CI (no critical CVEs in production images).
- [ ] Images pinned to immutable digests/tags, not `:latest`.
- [ ] NetworkPolicies defined to restrict Pod-to-Pod traffic (default-deny + explicit allow).

## Networking

- [ ] Services use correct type (ClusterIP/NodePort/LoadBalancer) intentionally, not by default.
- [ ] Ingress configured with TLS termination and valid certificates (cert-manager or equivalent).
- [ ] DNS resolution tested from within cluster (CoreDNS healthy, correct service names used).
- [ ] Rate limiting / timeouts configured at Ingress or service-mesh layer for public endpoints.
- [ ] Egress rules reviewed if NetworkPolicies restrict outbound traffic.

## Storage

- [ ] StatefulSets/PVCs use an appropriate `StorageClass` with correct reclaim policy.
- [ ] Backup strategy in place for persistent volumes (snapshotting or application-level backups).
- [ ] Storage capacity monitored with alerts before volumes fill up.
- [ ] Access modes (RWO/RWX/ROX) validated against actual multi-pod access needs.

## Observability

- [ ] Centralized logging pipeline in place (e.g., Fluentd/Fluent Bit → Loki/ELK).
- [ ] Metrics exported and scraped (Prometheus) for app and infra layers.
- [ ] Dashboards built (Grafana) for latency, error rate, saturation (the "golden signals").
- [ ] Alerting rules configured for critical failure conditions (crash loops, high error rate, resource exhaustion).
- [ ] Distributed tracing enabled for multi-service request flows (OpenTelemetry/Jaeger).
- [ ] `kubectl` events and audit logs retained/shipped for post-incident analysis.

## Cost Optimization

- [ ] Cluster autoscaler (or Karpenter) enabled to match node capacity to demand.
- [ ] HPA configured for workloads with variable traffic to avoid over-provisioning.
- [ ] Idle/unused resources (orphaned PVCs, old ReplicaSets, unused LoadBalancers) periodically cleaned up.
- [ ] Spot/preemptible nodes used for fault-tolerant, non-critical batch workloads.
- [ ] Resource requests right-sized regularly using historical usage data (avoid over-requesting).

## Deployment Strategy

- [ ] Rolling update strategy configured with sane `maxSurge`/`maxUnavailable`.
- [ ] Canary or blue-green rollout process available for high-risk releases.
- [ ] Rollback procedure tested (`kubectl rollout undo` or GitOps revert) before go-live.
- [ ] CI/CD pipeline enforces manifest validation/linting (e.g., kubeval, conftest) before apply.
- [ ] GitOps (ArgoCD/FluxCD) or equivalent used so cluster state matches version-controlled source of truth.

## Disaster Recovery

- [ ] etcd backups scheduled and restore procedure tested (for self-managed control planes).
- [ ] Multi-AZ (or multi-region) deployment for critical workloads.
- [ ] Documented runbooks for common failure scenarios (node loss, region outage, bad deploy).
- [ ] Regular disaster-recovery drills / chaos testing performed.
- [ ] RTO/RPO targets defined and validated against actual recovery capability.
