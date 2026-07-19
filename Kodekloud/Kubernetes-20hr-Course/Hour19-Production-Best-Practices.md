# Hour 19: Production Best Practices, Common Mistakes, Cost Optimization

## 1. Explanation (Simple → Technical)

**Simple version:** You've now learned all the individual "kitchen tools" — pods, deployments, services, probes, autoscalers, RBAC, monitoring. Hour 19 is the head chef walking through the kitchen before a health inspection, checking: is the fire exit clear? Is food labeled and dated? Are staff scheduled sensibly, or is the restaurant paying 20 cooks to stand around on a slow Tuesday? This hour doesn't teach new syntax — it teaches **judgment**: which of the things you already learned are non-negotiable in production, and which mistakes quietly drain your budget or blow up during an incident.

**Technical version — the production-readiness checklist**, tying each item back to where you learned it:

1. **Always set resource requests/limits (Hour 10).** Without a `requests.cpu`/`requests.memory`, the scheduler can't bin-pack sanely, and without `limits`, one noisy pod can starve every neighbor on the node. Every production container should declare both.

2. **Always use liveness + readiness probes (Hour 11).** A pod that's alive-but-stuck will keep receiving traffic without a readiness probe, and a deadlocked process will run forever without a liveness probe. No probes = Kubernetes flying blind about your app's actual health.

3. **Never run as root; enforce Pod Security Standards (Hour 16).** `runAsNonRoot: true`, drop `ALL` capabilities, and use the `restricted` Pod Security Standard at the namespace level. A container escape from a root process is a cluster-wide incident; from a non-root, capability-dropped process, it's a much smaller blast radius.

4. **Use namespaces + RBAC for multi-tenancy (Hours 3, 16).** Namespaces are the wall between teams; RBAC is the lock on the door. Without both, "team A's intern" can `kubectl delete` team B's production deployment by accident — no malice required, just no fence.

5. **Use Ingress, not N LoadBalancers (Hour 6).** Every `type: LoadBalancer` Service provisions a real cloud load balancer — and cloud LBs cost money per-hour, per-service. Ten services behind ten LoadBalancers is 10x the LB bill; the same ten services behind one Ingress controller is one LB fronting smart, host/path-based routing.

6. **Use HPA + Cluster Autoscaler together for cost-efficient scaling (Hour 13).** HPA scales *pods* based on load; Cluster Autoscaler scales *nodes* to fit those pods. Use HPA alone and you'll eventually run out of node capacity and pods will sit `Pending`. Use nodes alone (fixed-size cluster) and you pay for peak capacity 24/7. Use both, and capacity breathes with demand.

7. **Use PodDisruptionBudgets (PDBs) — new concept.** A PDB tells Kubernetes "never voluntarily take down more than N (or less than N remaining) pods of this app at once." This matters during **voluntary disruptions**: node drains for upgrades, cluster autoscaler scale-downs, `kubectl drain`. Without a PDB, Kubernetes is free to evict every replica of your app simultaneously during a node upgrade — technically "the app will reschedule," but for the 30-90 seconds it takes new pods to start, you have a full outage. A minimal PDB:
   ```yaml
   apiVersion: policy/v1
   kind: PodDisruptionBudget
   metadata:
     name: web-pdb
   spec:
     minAvailable: 2          # or maxUnavailable: 1
     selector:
       matchLabels:
         app: web
   ```

8. **Readiness gates + tuned rolling update strategy for true zero-downtime (Hour 4).** `maxUnavailable`/`maxSurge` control how aggressively a rollout replaces pods; a readiness probe with a sensible `initialDelaySeconds` ensures traffic only hits pods that are actually ready. Get these wrong and "zero-downtime deployment" is a myth you believe until the day it isn't.

9. **Centralize logging/monitoring (ties to Hour 14).** `kubectl logs` is fine for one pod during development. In production with 50 replicas across 10 nodes, restarting and rescheduling, `kubectl logs` cannot answer "what happened across the fleet in the last hour." You need:
   - **Metrics:** Prometheus (scrapes) + Grafana (visualizes) — the de facto standard, deployable in one command via `kube-prometheus-stack` (Hour 17).
   - **Logs:** a log-aggregation stack — ELK (Elasticsearch/Logstash/Kibana) or the lighter, cheaper **Loki** + Grafana (Loki is often preferred for cost since it indexes labels, not full text).
   - Without these, your incident response is "SSH-adjacent archaeology" instead of one dashboard query.

10. **Cost optimization, specifically:**
    - **Right-size requests.** Over-provisioned `requests` (e.g., asking for 2 CPU when you use 200m) forces the scheduler to reserve capacity you never use — you pay for phantom headroom. Use `kubectl top pods`, VPA in recommendation mode, or historical Prometheus data to set requests close to actual p95 usage.
    - **Spot/preemptible nodes for non-critical, interruption-tolerant workloads** (batch jobs, CI runners, stateless web tier with enough replicas) — spot nodes are typically 60-90% cheaper than on-demand, at the cost of possible reclaim with short notice.
    - **Cluster Autoscaler scaling nodes down when idle** — a cluster sized for Black Friday traffic, running at Black Friday size every Tuesday at 3 AM, is pure waste. Autoscaler should be scaling node groups down (not just up) as load drops.
    - **Namespace ResourceQuotas** to cap the total CPU/memory/object count a team's namespace can consume — this is the difference between "one team's bug causes a scaling loop that eats the whole cluster's budget" and "that team's namespace hit its quota and was safely capped."

## 2. Diagram — The Production Maturity Ladder

```
Level 4: Observability + GitOps + Cost Governance
         ┌─────────────────────────────────────────────┐
         │ Prometheus/Grafana, Loki/ELK, GitOps (ArgoCD/│
         │ Flux), Kubecost, chaos testing, SLOs         │
         └─────────────────────────────────────────────┘
                          ▲
Level 3: Resilience + Governance
         ┌─────────────────────────────────────────────┐
         │ HPA, Cluster Autoscaler, PodDisruptionBudget,│
         │ RBAC, NetworkPolicy, ResourceQuota/LimitRange│
         └─────────────────────────────────────────────┘
                          ▲
Level 2: Safety Basics
         ┌─────────────────────────────────────────────┐
         │ Liveness/Readiness probes, resource requests│
         │ /limits, ConfigMaps/Secrets, non-root users  │
         └─────────────────────────────────────────────┘
                          ▲
Level 1: Functional
         ┌─────────────────────────────────────────────┐
         │ Deployments + Services (replaces bare Pods)  │
         └─────────────────────────────────────────────┘
                          ▲
Level 0: "It Runs"
         ┌─────────────────────────────────────────────┐
         │ Bare Pods, no config, hope-based operations  │
         └─────────────────────────────────────────────┘

  Most tutorials stop at Level 1. Most outages happen because
  teams shipped Level 1 and called it Level 4.
```

**Analogy:** Running Kubernetes without these practices is like running a restaurant with the fire exit painted shut (no probes, no PDBs — nobody notices until the one night it matters) and staff paid by the hour but scheduled for a full year's shifts regardless of actual demand (no autoscaling, no right-sizing — you bleed money quietly, every single day). Both problems are invisible on a calm Tuesday. Both become catastrophic or catastrophically expensive on the one day — the fire, the traffic spike, the finance review — that really tests you.

## 3. Real-World Example — Incident Postmortem

**Title: "The Upgrade That Took Down Checkout"**

A mid-sized e-commerce platform's cloud provider scheduled a routine node OS patch. The platform team, following standard practice, ran `kubectl drain` on nodes one at a time ahead of the provider's maintenance window. The `checkout-service` Deployment had 3 replicas — but no PodDisruptionBudget.

The drain command, seeing no PDB constraint, evicted **all 3 replicas** because they happened to be co-located on the two nodes being drained in quick succession. New pods were scheduled onto other nodes, but image pull + app startup + readiness probe warm-up took 47 seconds. For those 47 seconds, `checkout-service` had **zero ready pods**. Every checkout request during that window returned 503s. It happened at 2:15 PM on a weekday — not the highest traffic period, but far from zero. The postmortem estimated ~1,800 failed checkout attempts and an unknown number of abandoned carts.

**Root cause:** No PDB meant the cluster's own routine maintenance was treated as an unconstrained disruption. **Fix:** added `minAvailable: 2` PDBs to every customer-facing Deployment, and required a PDB as a merge-gate in the Helm chart's CI lint step going forward.

**Companion cost incident, same company, discovered during the same audit:** The `checkout-service` pods requested `2 CPU / 4Gi memory` each "to be safe," but Prometheus history showed p95 usage of `250m CPU / 600Mi memory`. The cluster autoscaler, honoring those inflated requests, kept 6 extra nodes running around the clock that were never actually needed. Right-sizing the requests to match real usage let Cluster Autoscaler scale the node group down, cutting compute spend by roughly 35% with no change in application performance.

## 4. Hands-On Lab

**Goal:** Retrofit a Deployment from an earlier hour with production-grade guardrails: a PodDisruptionBudget, and a namespace-level ResourceQuota + LimitRange — then prove the quota actually blocks over-consumption.

```bash
# Use (or recreate) the nginx deployment from earlier hours
kubectl create namespace prod-demo
kubectl -n prod-demo create deployment web --image=nginx --replicas=3
kubectl -n prod-demo set resources deployment/web \
  --requests=cpu=100m,memory=128Mi --limits=cpu=200m,memory=256Mi
```

**Step 1 — Add a PodDisruptionBudget:**
```bash
cat <<EOF | kubectl apply -f -
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: web-pdb
  namespace: prod-demo
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: web
EOF

kubectl -n prod-demo get pdb
```
**Expected output:**
```
NAME      MIN AVAILABLE   MAX UNAVAILABLE   ALLOWED DISRUPTIONS   AGE
web-pdb   2               N/A               1                     5s
```
Try draining a node that hosts these pods (`kubectl drain <node> --ignore-daemonsets`) — the drain will now respect the PDB and evict pods only as long as at least 2 stay available, rather than evicting all 3 at once.

**Step 2 — Add a ResourceQuota and LimitRange to the namespace:**
```bash
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ResourceQuota
metadata:
  name: prod-demo-quota
  namespace: prod-demo
spec:
  hard:
    requests.cpu: "1"
    requests.memory: 1Gi
    limits.cpu: "2"
    limits.memory: 2Gi
    pods: "10"
---
apiVersion: v1
kind: LimitRange
metadata:
  name: prod-demo-limits
  namespace: prod-demo
spec:
  limits:
  - type: Container
    default:
      cpu: 200m
      memory: 256Mi
    defaultRequest:
      cpu: 100m
      memory: 128Mi
    max:
      cpu: 500m
      memory: 512Mi
EOF

kubectl -n prod-demo describe resourcequota prod-demo-quota
```

**Step 3 — Demonstrate the quota blocking over-consumption:**
```bash
# Current usage: 3 replicas x 100m/128Mi requests = 300m/384Mi of the 1 CPU / 1Gi quota.
# Try scaling to a size that would exceed the quota.
kubectl -n prod-demo scale deployment/web --replicas=12
kubectl -n prod-demo get events --sort-by=.lastTimestamp | tail -5
```
**Expected output (truncated):**
```
Warning  FailedCreate  replicaset-controller  Error creating: pods "web-xxxxx" is forbidden:
exceeded quota: prod-demo-quota, requested: requests.cpu=100m, used: requests.cpu=1,
limited: requests.cpu=1
```
Only as many pods as fit under `requests.cpu: "1"` (10 pods at 100m each) will actually come up; the rest sit blocked with a clear quota-exceeded event — proving the quota is a real, enforced ceiling, not just documentation.

```bash
kubectl -n prod-demo get deployment web
# DESIRED will show 12, but AVAILABLE will cap out below that until quota/limits are raised
```

**Where to look for free monitoring:** deploy `kube-prometheus-stack` via Helm (introduced in Hour 17) to get Prometheus + Grafana + Alertmanager pre-wired with Kubernetes dashboards in one install:
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install monitoring prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
```
This is also where you'd watch, in real time, the requests-vs-actual-usage gap that drives the right-sizing decisions in the cost section above.

**Cleanup:**
```bash
kubectl delete namespace prod-demo
```

## 5. Common Mistakes

1. **No resource requests/limits.** The scheduler bin-packs blindly, and a single runaway container can starve every other pod on its node ("noisy neighbor"). This is the single most common gap between a tutorial cluster and a production one.
2. **No PodDisruptionBudget during cluster/node upgrades.** As in the postmortem above, "the pods will reschedule eventually" is not the same as "the service stayed up." Voluntary disruptions (drains, autoscaler scale-downs) will happily take out 100% of your replicas at once without a PDB.
3. **Using `latest` (or no) image tags in production.** `latest` means you can't reliably know what's actually running, rollbacks become guesswork, and a routine pod reschedule can silently pull a newer, untested image. Always pin explicit, immutable version tags (ideally by digest).
4. **Storing secrets in plain ConfigMaps (or unencrypted Secrets).** Kubernetes `Secret` objects are only base64-encoded, not encrypted, by default at the API level unless encryption-at-rest is configured — and ConfigMaps offer zero protection at all. Real secrets need a secrets manager (Vault, cloud KMS-backed secrets) or at minimum enabled etcd encryption, not a ConfigMap.
5. **Ignoring the Cluster Autoscaler and over-paying for idle capacity.** A cluster sized for peak traffic that never scales back down during off-peak hours is paying 24/7 for a handful of busy hours. This is one of the largest, most fixable line items in most companies' cloud bills.
6. **No namespace ResourceQuotas, letting one team starve others.** In a shared cluster, one team's misconfigured HPA or memory leak can consume all available cluster capacity, starving every other team — quotas turn "everyone's problem" into "that team's contained problem."

## 6. Interview Questions (with brief answers)

1. **What would you check before calling a cluster "production-ready"?** — Resource requests/limits on every container; liveness/readiness probes; non-root/Pod Security Standards enforced; RBAC and namespace isolation for multi-tenancy; PodDisruptionBudgets on all critical Deployments; HPA + Cluster Autoscaler configured; centralized logging/metrics (Prometheus/Grafana, Loki/ELK) rather than relying on `kubectl logs`; ResourceQuotas per namespace; pinned (non-`latest`) image tags; secrets in a real secrets manager, not ConfigMaps.
2. **How would you reduce a Kubernetes cluster's cloud bill?** — Right-size resource requests using real usage data (Prometheus/VPA) instead of guessed values; move interruption-tolerant workloads to spot/preemptible nodes; ensure Cluster Autoscaler scales nodes *down* during low-traffic periods, not just up; consolidate many `LoadBalancer` Services behind a single Ingress controller; set namespace ResourceQuotas to prevent runaway over-provisioning; use tools like Kubecost to find and eliminate specific waste (idle PVCs, over-requested namespaces, orphaned resources).
3. **Why is a PodDisruptionBudget necessary if Kubernetes already reschedules pods automatically?** — Rescheduling happens *after* eviction, and there's a gap (image pull, startup, readiness warm-up) during which the evicted capacity is gone. Without a PDB, voluntary disruptions (node drains, autoscaler scale-down) can evict all replicas of an app simultaneously, causing a real outage during that gap. A PDB caps how many replicas can be evicted at once, guaranteeing a minimum of always-available capacity.
4. **What's the difference between a ResourceQuota and a LimitRange?** — A ResourceQuota caps the *total* resource consumption (and object counts) for an entire namespace. A LimitRange sets *per-container/per-pod* default, minimum, and maximum resource values within that namespace. They're complementary: LimitRange shapes individual containers; ResourceQuota caps the aggregate.
5. **Why is `kubectl logs` insufficient for production debugging, and what replaces it?** — It only shows logs for one pod at a time, doesn't persist logs after a pod is deleted/rescheduled, and can't correlate events across dozens or hundreds of replicas. Production setups replace it with centralized log aggregation (Loki or the ELK stack) paired with metrics/dashboards (Prometheus + Grafana), so incidents can be investigated with cross-fleet queries and correlated with metrics, not one pod's log stream at a time.

## 7. Quiz (50 Questions)

**True/False:**
1. Setting resource requests and limits is optional for production workloads. (F)
2. A PodDisruptionBudget protects against voluntary disruptions like node drains. (T)
3. Cluster Autoscaler and HPA solve the same problem. (F)
4. Running containers as root is safe as long as RBAC is configured. (F)
5. `kubectl logs` scales well for debugging incidents across hundreds of pods. (F)
6. Using `latest` as an image tag makes rollbacks harder to reason about. (T)
7. ConfigMaps encrypt their contents by default. (F)
8. A ResourceQuota is applied at the namespace level. (T)
9. Ingress can reduce cloud costs compared to many LoadBalancer Services. (T)
10. Spot/preemptible nodes are appropriate for every workload, including stateful databases. (F)
11. LimitRange sets defaults and bounds for individual containers within a namespace. (T)
12. Cluster Autoscaler should only ever scale nodes up, never down. (F)
13. Pod Security Standards can enforce that containers don't run as root. (T)
14. Readiness probes affect whether a pod receives traffic from a Service. (T)
15. Liveness probes affect whether a pod receives traffic from a Service. (F)

**Multiple Choice:**
16. What does a PodDisruptionBudget primarily protect against? a) Node hardware failure b) Voluntary disruptions like drains/autoscaler scale-down c) DNS outages d) Image pull errors → (b)
17. Which object caps total CPU/memory usage for an entire namespace? a) LimitRange b) ResourceQuota c) PodDisruptionBudget d) NetworkPolicy → (b)
18. Which is the most common root cause of "noisy neighbor" issues on a node? a) Too many namespaces b) Missing resource requests/limits c) Too many Services d) Using Ingress → (b)
19. Which tool pairing gives cost-efficient elastic scaling? a) HPA + Cluster Autoscaler b) RBAC + NetworkPolicy c) ConfigMap + Secret d) PDB + LimitRange → (a)
20. What's the main risk of using the `latest` image tag in production? a) Slower pulls b) Unpredictable, hard-to-roll-back deployments c) Higher CPU usage d) Breaks readiness probes → (b)
21. Which stack, introduced in Hour 17, gives Prometheus + Grafana in one Helm install? a) ELK b) kube-prometheus-stack c) Loki-stack d) Kubecost → (b)
22. Which is a lighter-weight alternative to ELK for log aggregation, often preferred for cost? a) Fluentd b) Loki c) Jaeger d) Falco → (b)
23. What happens to pods exceeding a namespace's ResourceQuota on creation? a) They run with throttled CPU b) They are created but killed later c) Creation is forbidden with a quota-exceeded event d) They are moved to another namespace → (c)
24. Which of these should be used to store production secrets instead of a ConfigMap? a) A Deployment annotation b) A dedicated secrets manager (e.g., Vault, cloud KMS) c) An environment variable file in the image d) A public Git repo → (b)
25. What's the effect of using Ingress instead of multiple LoadBalancer Services? a) Slower routing b) Fewer provisioned cloud load balancers, lower cost c) No effect on cost d) Loss of TLS support → (b)

**Short Answer:**
26. Why does a missing resource `limit` risk starving other pods on the same node?
27. What specific failure does a PodDisruptionBudget prevent during a node drain?
28. Why is right-sizing resource requests a cost issue, not just a performance issue?
29. What's the difference between a voluntary and involuntary disruption in Kubernetes?
30. Why are spot/preemptible nodes risky for stateful workloads?
31. What's one reason `kubectl logs` fails as a production debugging tool at scale?
32. What does `runAsNonRoot: true` protect against?
33. Why might a team's uncapped namespace hurt other teams sharing the cluster?
34. What's the practical difference between a ResourceQuota and a LimitRange?
35. Why is Cluster Autoscaler scaling *down* just as important as scaling up, cost-wise?

**Scenario-Based:**
36. Your `checkout-service` went down for 45 seconds during a routine node OS patch. What's the most likely missing safeguard, and how do you fix it?
37. Finance flags that your Kubernetes bill tripled this quarter with no traffic growth. What are the first three things you'd check?
38. A teammate wants to deploy a new service without setting resource requests "to save time." What's your response?
39. You're auditing a namespace and find every container running as root with `latest` tags and secrets stored as ConfigMaps. What do you fix first, and why?
40. Your cluster has HPA configured but pods are stuck `Pending` during a traffic spike. What's likely missing?

**Fill in the Blank:**
41. A ______ caps the total resource consumption for an entire namespace.
42. A ______ ensures a minimum number of pods stay available during voluntary disruptions.
43. ______ nodes are cheaper but can be reclaimed by the cloud provider with short notice.
44. The Pod Security Standard that enforces the strictest security posture is called ______.
45. ______ + Grafana is the standard open-source metrics and visualization stack for Kubernetes.

**Conceptual Deep-Dive:**
46. Why does "the pod will just get rescheduled" not fully solve the availability problem during node drains?
47. Explain how missing resource requests can cascade into a bad Cluster Autoscaler scaling decision.
48. Why is centralized logging described as necessary once you pass "a handful of replicas," rather than always necessary from day one?
49. How do RBAC and namespaces work together (not independently) to provide multi-tenancy?
50. Why is "it works in the demo cluster" not the same as "it's production-ready"?

---

## 8. Hour 19 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Resource requests/limits** | Prevent noisy neighbors and enable correct scheduling/autoscaling decisions |
| **Probes** | Readiness gates traffic; liveness triggers restarts; both required for real self-healing |
| **Security** | Non-root containers + Pod Security Standards shrink the blast radius of any compromise |
| **Multi-tenancy** | Namespaces + RBAC together, not separately, are what actually isolate teams |
| **Ingress over many LBs** | Fewer provisioned cloud load balancers = lower recurring cost |
| **HPA + Cluster Autoscaler** | Pods scale with load; nodes scale with pods — use both, not one |
| **PodDisruptionBudget** | Protects availability during voluntary disruptions like node drains/upgrades |
| **Rolling update tuning** | `maxUnavailable`/`maxSurge` + readiness probes = actual zero-downtime deploys |
| **Centralized observability** | Prometheus/Grafana + Loki/ELK replace `kubectl logs` at any real scale |
| **Cost optimization** | Right-size requests, use spot nodes for tolerant workloads, scale nodes down when idle, enforce ResourceQuotas |

**Mnemonic:** *"CRISP-COST"* — **C**ompute limits set, **R**eadiness/liveness probes on, **I**solation via RBAC/namespaces, **S**ecurity as non-root, **P**DBs for disruptions, **C**luster Autoscaler tuned, **O**bservability centralized, **S**pot nodes for tolerant loads, **T**enant quotas enforced.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Production Best Practices / Configuration Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [Kubernetes Docs — Pod Disruption Budgets](https://kubernetes.io/docs/tasks/run-application/configure-pdb/)
- [Kubernetes Docs — Resource Quotas](https://kubernetes.io/docs/concepts/policy/resource-quotas/)
- [kube-prometheus-stack (Helm chart)](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack) — free, self-hosted Prometheus + Grafana + Alertmanager
- [Kubecost](https://www.kubecost.com/) — free tier for real-time Kubernetes cost visibility and right-sizing recommendations
- [Grafana Loki](https://grafana.com/oss/loki/) — cost-efficient log aggregation alternative to ELK

**Mini-Project for Hour 19 (60-90 min):**
Pick one Deployment from an earlier hour (e.g., the `web`/`nginx` app used across Hours 4-13). Audit it against the full checklist in Section 1 and fix every gap you find:
1. Add resource requests/limits if missing.
2. Add/verify liveness and readiness probes.
3. Confirm it runs as non-root and add a Pod Security Standard label to its namespace.
4. Confirm the namespace has RBAC roles scoped appropriately (not cluster-admin for everything).
5. If it's exposed via `type: LoadBalancer`, convert it to Ingress.
6. Add a PodDisruptionBudget with `minAvailable` set sensibly for its replica count.
7. Add a ResourceQuota + LimitRange to its namespace.
8. Point it at a Prometheus/Grafana dashboard (from `kube-prometheus-stack`) instead of relying on `kubectl logs`.

Write down every gap you found before fixing it — that list *is* your personal "common mistakes" checklist for every cluster you touch going forward.
