# Kubernetes Mastery Roadmap: 6-12 Months After This Course

You've completed the 20-hour course and understand core objects, workloads,
networking, storage, security basics, Helm, and built a 3-tier app. This
roadmap takes you from "competent operator" to "expert" through structured
stages, free resources, and hands-on mini-projects.

---

## Stage 0 (Weeks 1-2): Solidify the Fundamentals

- [ ] Re-build the 3-tier capstone app from this course from memory, no notes.
- [ ] Read the official [Kubernetes docs](https://kubernetes.io/docs/home/) concepts section end-to-end once.
- [ ] Install `kubectx`/`kubens` and practice fast context/namespace switching.
- **Free resource:** [Kubernetes.io official docs](https://kubernetes.io/docs/concepts/), [KillerCoda free playgrounds](https://killercoda.com/).

---

## Stage 1 (Months 1-2): CKAD Certification

CKAD (Certified Kubernetes Application Developer) matches what you just
learned — deepen it into exam-ready, muscle-memory skill.

- [ ] Practice imperative `kubectl` command generation (`--dry-run=client -o yaml`) until it's automatic.
- [ ] Master multi-container Pod patterns: sidecar, init container, ambassador, adapter.
- [ ] Practice under time pressure — CKAD is 2 hours, ~15-19 performance tasks.
- **Free resources:** [killer.sh CKAD simulator](https://killer.sh) (comes free with exam purchase, but also has trial), [KodeKloud CKAD course](https://kodekloud.com/) (some free content), [Kubernetes docs task pages](https://kubernetes.io/docs/tasks/).
- **Milestone:** Sit the CKAD exam (or a full mock under time pressure if budget-constrained).

---

## Stage 2 (Months 2-4): CKA Certification + Cloud Provider Hands-On

Move from "developer" to "cluster operator" — cluster setup, upgrades, etcd,
networking internals, troubleshooting control-plane issues.

- [ ] Build a cluster from scratch with `kubeadm` on local VMs (no managed service).
- [ ] Practice etcd backup/restore.
- [ ] Practice node maintenance: cordon, drain, upgrade, taint/toleration scenarios.
- [ ] Get hands-on with a **managed cloud Kubernetes free tier**:
  - [ ] **GKE**: use GCP's $300 free trial credit; run the free-tier Autopilot cluster.
  - [ ] **EKS**: use AWS free tier (EC2 t2.micro nodes) or `eksctl` with minimal node group.
  - [ ] **AKS**: use Azure's free trial credit; AKS control plane is free, pay only for nodes.
- [ ] Compare how each provider handles LoadBalancer Services, IngressClasses, and IAM-to-RBAC mapping.
- **Free resources:** [kubeadm docs](https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/), [killer.sh CKA simulator](https://killer.sh), [AWS/GCP/Azure free tier pages], [KodeKloud CKA labs].
- **Milestone:** Sit the CKA exam.

---

## Stage 3 (Months 4-5): GitOps with ArgoCD / FluxCD

Production Kubernetes is rarely managed with `kubectl apply` by hand — it's
Git-driven.

- [ ] Install ArgoCD on your own cluster (via Helm) and connect it to a Git repo.
- [ ] Set up an `Application` that auto-syncs manifests from Git.
- [ ] Practice a rollback via `git revert` and watch ArgoCD reconcile.
- [ ] Repeat the same exercise with FluxCD to compare philosophies (pull-based reconciliation in both, but different CRDs/UX).
- [ ] Implement an App-of-Apps or Kustomize-overlay pattern for multi-environment (dev/staging/prod) promotion.
- **Free resources:** [ArgoCD official docs + interactive tutorial](https://argo-cd.readthedocs.io/), [FluxCD docs](https://fluxcd.io/flux/get-started/), [Codefresh GitOps free guides].

---

## Stage 4 (Months 5-6): Helm Chart Authoring Depth

Go from "installing charts" to "designing them well."

- [ ] Write a Helm chart from scratch for a multi-component app (not `helm create` boilerplate left untouched).
- [ ] Use subcharts and `values.yaml` layering for a chart-of-charts.
- [ ] Write chart tests (`helm test`) and use `helm lint` in CI.
- [ ] Publish a chart to a personal Helm repo (GitHub Pages or OCI registry).
- [ ] Learn Helm hooks (pre-install, post-upgrade) for migration jobs.
- **Free resources:** [Helm official docs](https://helm.sh/docs/), [Helm chart best practices guide](https://helm.sh/docs/chart_best_practices/).

---

## Stage 5 (Months 6-8): Observability Deep Dive

- [ ] Deploy the `kube-prometheus-stack` Helm chart (Prometheus + Grafana + Alertmanager).
- [ ] Write custom PromQL queries for the golden signals (latency, traffic, errors, saturation).
- [ ] Build a Grafana dashboard from scratch for your 3-tier app.
- [ ] Instrument your app with OpenTelemetry SDK; export traces to Jaeger or Grafana Tempo.
- [ ] Set up Alertmanager routing rules and test a real alert firing end-to-end.
- **Free resources:** [Prometheus docs](https://prometheus.io/docs/), [Grafana learning journeys](https://grafana.com/tutorials/), [OpenTelemetry docs](https://opentelemetry.io/docs/), [PromLabs PromQL tutorial](https://promlabs.com/promql-cheat-sheet/).

---

## Stage 6 (Months 7-9): Service Mesh Basics

- [ ] Install Linkerd (simpler starting point) on a test cluster and enable mTLS between services.
- [ ] Observe Linkerd's built-in golden-metrics dashboard with zero app code changes.
- [ ] Install Istio on a separate cluster/namespace; configure a `VirtualService` + `DestinationRule` for traffic splitting (canary).
- [ ] Compare sidecar-injection overhead and complexity between Linkerd and Istio.
- [ ] Implement a fault-injection or retry policy experiment with Istio.
- **Free resources:** [Linkerd getting started](https://linkerd.io/getting-started/), [Istio official docs + hands-on tasks](https://istio.io/latest/docs/tasks/).

---

## Stage 7 (Months 9-12): Contribute to CNCF Open Source

- [ ] Pick a CNCF project relevant to your interests (Kubernetes core, ArgoCD, Prometheus, Helm, Linkerd, etc.).
- [ ] Start with `good first issue` / `help wanted` labeled issues.
- [ ] Join the project's Slack/Discord and attend a community meeting.
- [ ] Submit a documentation fix first (low risk, real contribution), then progress to a code fix.
- [ ] Aim for at least one merged PR by the end of the year.
- **Free resources:** [CNCF Landscape](https://landscape.cncf.io/), [goodfirstissue.dev](https://goodfirstissue.dev/), individual project `CONTRIBUTING.md` files.

---

## Mini-Projects (Increasing Difficulty)

1. **Static site on K8s** — Deploy an Nginx-served static site with a Deployment + Service + Ingress + TLS via cert-manager.
2. **Config-driven multi-env app** — One app, three overlays (dev/staging/prod) using Kustomize, differing only in ConfigMap/replica count.
3. **Stateful app with backups** — Deploy PostgreSQL via StatefulSet + PVC; write a CronJob that dumps and uploads backups to object storage.
4. **Autoscaling demo** — Deploy an app with an HPA driven by custom Prometheus metrics (not just CPU), and load-test it with `k6` or `hey`.
5. **GitOps pipeline** — Wire a full CI/CD flow: GitHub Actions builds/pushes image → ArgoCD auto-syncs to cluster on merge to main.
6. **Zero-downtime canary release** — Use Istio or Argo Rollouts to shift traffic 10%→50%→100% to a new version with automated rollback on error-rate spike.
7. **Multi-tenant namespace platform** — Build namespace-per-team isolation with ResourceQuotas, NetworkPolicies, and RBAC RoleBindings templated via a Helm chart.
8. **Full observability stack** — Instrument a microservice with OpenTelemetry, ship traces/metrics/logs to Prometheus+Tempo+Loki, and build one unified Grafana dashboard.
9. **Chaos engineering drill** — Use Chaos Mesh or `kubectl` node-drain/pod-delete scripts to simulate node failure and validate your PDBs/replica counts hold up.
10. **Custom Kubernetes controller** — Write a minimal operator (using `kubebuilder` or `client-go`) that watches a CRD and reconciles a real side effect (e.g., auto-creates a ConfigMap per custom resource).

---

## Suggested Cadence Summary

| Month | Focus |
|---|---|
| 1-2 | CKAD cert prep |
| 2-4 | CKA cert prep + cloud provider hands-on (EKS/GKE/AKS) |
| 4-5 | GitOps (ArgoCD/FluxCD) |
| 5-6 | Helm chart authoring depth |
| 6-8 | Observability (Prometheus/Grafana/OpenTelemetry) |
| 7-9 | Service mesh (Linkerd/Istio) |
| 9-12 | CNCF open-source contribution + capstone mini-projects |

Keep mini-projects running in parallel with certification/tooling stages
rather than sequentially — that's what actually builds "expert" intuition.
