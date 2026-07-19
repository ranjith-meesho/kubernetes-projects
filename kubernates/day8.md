# 🚢 DAY 8 — Scaling, Replica Management & the Horizontal Pod Autoscaler (HPA)

> "Provisioning for peak is how you go broke. Autoscaling for peak is how you stay up." — every SRE, eventually.

---

## 1. LEARNING OBJECTIVES

By the end of Day 8 you will be able to:

1. **Manually scale** a Deployment/ReplicaSet/StatefulSet using `kubectl scale` and declaratively via `replicas:`.
2. Explain **why CPU/memory resource requests are mandatory** for HPA to work.
3. Install and validate the **metrics-server**, and read pod/node metrics with `kubectl top`.
4. Describe the **HPA desiredReplicas algorithm** precisely, including the formula, tolerance, and rounding.
5. Configure HPA on **CPU utilization, memory, and custom/external metrics** (`autoscaling/v2`).
6. Tune **`min`/`max` replicas**, `scaleUp`/`scaleDown` policies, and the **stabilization window** to prevent flapping.
7. Distinguish **HPA vs VPA vs Cluster Autoscaler**, and know when to use each.
8. Diagnose the classic failures: `<unknown>` targets, "not scaling", thrashing, and "stuck at max".

---

## 2. 80/20 BREAKDOWN

The 20% of scaling knowledge that delivers 80% of real-world and interview value.

| Priority | Topic | Why it matters | Use it for |
|---------|-------|----------------|-----------|
| 🔥 MUST | Resource **requests** → HPA dependency | No requests = HPA shows `<unknown>` = no scaling | Prod + Interview |
| 🔥 MUST | metrics-server install & `kubectl top` | The data source for CPU/mem HPA | Prod + Interview |
| 🔥 MUST | `desiredReplicas` formula | The single most-asked HPA interview question | Interview gold |
| 🔥 MUST | `autoscaling/v2` HPA on CPU | The 90% case in production | Prod |
| ⭐ HIGH | `min`/`max` + `behavior` (stabilization) | Stops thrashing & runaway cost | Prod |
| ⭐ HIGH | Memory & custom metrics | Queue depth / RPS based scaling | Prod |
| ⭐ HIGH | HPA vs VPA vs CA | Architecture / design interviews | Interview gold |
| 📦 DEFER | VPA internals & recommender modes | Niche; mention only | Later |
| 📦 DEFER | KEDA event-driven scaling | Advanced; another day | Later |
| 📦 DEFER | Custom metrics adapter (Prometheus) plumbing | Deep ops topic | Later |

**Interview gold (memorize these):**
- The formula: `desiredReplicas = ceil(currentReplicas × (currentMetricValue / desiredMetricValue))`
- HPA **never** works without **requests** for CPU/memory utilization-based scaling.
- HPA default sync period is **15s**; default scale-down stabilization window is **300s (5 min)**, scale-up is **0s**.
- HPA, VPA (in update mode), and CA solve **different axes**: out (pods), up (pod size), and add-nodes respectively.

---

## 3. CONCEPT EXPLANATIONS

### 3.1 Manual Scaling (replicas)

**Beginner explanation:** A Deployment owns a ReplicaSet, and the ReplicaSet's job is to keep exactly `replicas` identical pods running. Change that number and the ReplicaSet creates or deletes pods to match.

**Analogy:** A restaurant manager (ReplicaSet) is told "keep 5 waiters on the floor." If 2 quit, they hire 2 more; if you say "now keep 8," they hire 3 more. The manager never reasons about *why* — they just match the target.

**Production use case:** Pre-scaling before a known event (a sale, a marketing push) where you don't want to wait for autoscaling reaction time. Also for stateless services during a planned drain of a node pool.

**Common mistakes:**
- Running `kubectl scale` on a Deployment that *also* has an HPA — the HPA will fight you and reset the count. (HPA owns `replicas` once attached.)
- Setting `replicas` in Git/manifests while HPA is active → GitOps tools like ArgoCD perpetually drift. Solution: drop `replicas` from the manifest when HPA manages it.

**Best practices:**
- For HPA-managed Deployments, **omit `replicas`** from the manifest (or ignore it in your GitOps diff).
- Keep stateless workloads at `replicas >= 2` for availability even before autoscaling.

```
        Deployment (replicas: 3)
              │ owns
              ▼
        ReplicaSet  ──────► reconcile loop: actual vs desired
         ┌────┴────┐
         ▼    ▼    ▼
       Pod  Pod  Pod      (3 = desired)
```

---

### 3.2 Why Resource Requests Are Required for HPA

**Beginner explanation:** Utilization-based HPA computes a *percentage*: "current CPU usage ÷ requested CPU." If a pod has **no CPU request**, there is no denominator, so the percentage is undefined → HPA reports `<unknown>` and refuses to scale.

**Analogy:** You can't calculate "I'm at 80% of my fuel tank" if the car never told you the tank's capacity. The **request** is the tank size.

**Production use case:** Every Deployment you intend to autoscale on CPU/memory **must** declare `resources.requests.cpu` (and `.memory` for memory-based HPA).

**Common mistakes:**
- Setting `limits` but forgetting `requests`. (Kubernetes copies limits→requests in some cases, but never rely on it; be explicit.)
- A multi-container pod where only one container has a request → utilization math gets confused. Set requests on every container that matters.

**Best practices:**
- Always set `requests` explicitly on the containers HPA will measure.
- Right-size the request: too low → HPA over-reacts (everything looks like 300%); too high → HPA under-reacts.

```
 utilization% = (actual usage across pods) / (sum of requests)
                                                     ▲
                                                     └── if request missing → <unknown>
```

---

### 3.3 metrics-server

**Beginner explanation:** The metrics-server is a lightweight cluster add-on that scrapes the kubelet's `/metrics/resource` endpoint on every node and exposes **current** CPU/memory usage through the Metrics API (`metrics.k8s.io`). The HPA controller and `kubectl top` both read from it. It is **not** a long-term store — it only keeps the latest snapshot (~last 15s, in-memory).

**Analogy:** A live dashboard speedometer, not a trip logbook. It tells you "going 60 right now," not "your average over the trip."

**Production use case:** Mandatory dependency for CPU/memory HPA and for `kubectl top`. Managed clusters (EKS/GKE/AKS) often need it installed or enabled separately.

**Common mistakes:**
- Forgetting it's installed → HPA targets show `<unknown>`.
- TLS errors on kubelet (self-signed certs) → metrics-server crashloops. Common dev/kubeadm fix: `--kubelet-insecure-tls` (do **not** use in prod; use proper certs).

**Best practices:**
- Run it HA in large clusters; monitor its pod health as a critical dependency.
- Never use metrics-server for alerting/dashboards — use Prometheus for history.

```
   kubelet (/metrics/resource) ──┐
   kubelet (/metrics/resource) ──┼──► metrics-server ──► metrics.k8s.io API
   kubelet (/metrics/resource) ──┘                          │
                                                  ┌──────────┴───────────┐
                                            kubectl top            HPA controller
```

---

### 3.4 The HPA Algorithm (desiredReplicas formula)

**Beginner explanation:** Every sync (default 15s) the HPA controller pulls current metrics, computes a desired replica count, and updates the Deployment.

**The core formula:**

```
desiredReplicas = ceil( currentReplicas × ( currentMetricValue / desiredMetricValue ) )
```

- `currentMetricValue` = average across all *ready* pods.
- `desiredMetricValue` = your target (e.g. 50% utilization, or 100 RPS).
- `ceil()` rounds **up** — so HPA scales up aggressively and conservatively avoids under-provisioning.

**Worked example:**
- Target CPU utilization = 50%. Current replicas = 3. Current average utilization = 90%.
- `desired = ceil(3 × (90/50)) = ceil(5.4) = 6` pods.

After scaling to 6, if usage redistributes to ~45%, `ceil(6 × 45/50) = ceil(5.4) = 6` → stable.

**Tolerance:** HPA ignores changes within a default **±10% tolerance** (`--horizontal-pod-autoscaler-tolerance=0.1`). If the ratio is between 0.9 and 1.1, it does nothing. This prevents micro-adjustments.

**Analogy:** A thermostat with a dead-band: it won't kick the AC on for a 0.5° change, only for a meaningful deviation.

**Common mistakes:**
- Expecting instant reaction — there's the 15s sync + metrics lag + pod startup time.
- Forgetting unready/just-started pods are handled specially (their metrics are discounted to avoid over-scaling during warm-up).

**Best practices:**
- Pick a target utilization that leaves headroom for the scale-up reaction window (often 50–70%, not 90%).

```
 every 15s:
   ratio = currentMetric / targetMetric
   if 0.9 <= ratio <= 1.1:  do nothing (tolerance)
   else: desired = ceil(currentReplicas * ratio)
         clamp(desired, minReplicas, maxReplicas)
         apply behavior policies (stabilization, rate limits)
```

---

### 3.5 Target Utilization & min/max Replicas

**Beginner explanation:** `targetCPUUtilizationPercentage` (or `averageUtilization` in v2) is the steady-state you want each pod to sit at. `minReplicas`/`maxReplicas` are hard floors and ceilings — HPA will never go below min or above max no matter what the formula says.

**Analogy:** Cruise control with a minimum and maximum speed governor.

**Production use case:** `min` guarantees availability (never drop to 0 for user-facing apps); `max` is your cost guardrail and a protection against runaway scale (e.g., a metrics bug demanding 9000 pods).

**Common mistakes:**
- `max` set too low → traffic spike can't be absorbed (stuck at max).
- `min: 1` for a critical service → a single pod restart = downtime. Use `min: 2+`.

**Best practices:**
- Set `max` to what your Cluster Autoscaler / node budget can actually schedule.
- Use a `min` ≥ 2 for any production user-facing workload.

---

### 3.6 Scaling on Memory & Custom Metrics

**Beginner explanation (memory):** Same formula, but the metric is memory utilization vs memory request. ⚠️ Memory is tricky — many apps (JVM, Go with GC) **don't release memory back**, so memory rarely drops → HPA scales up but won't scale down. Prefer CPU or custom metrics for elastic scaling; use memory HPA only when memory genuinely tracks load.

**Beginner explanation (custom/external):** With `autoscaling/v2` you can scale on:
- **Pods metrics** — an average per-pod metric (e.g., `http_requests_per_second`).
- **Object metrics** — a metric describing a single object (e.g., Ingress RPS).
- **External metrics** — outside the cluster (e.g., SQS queue depth, Kafka lag, Pub/Sub backlog).

These require a **metrics adapter** (e.g., Prometheus Adapter, or KEDA for event sources) that exposes `custom.metrics.k8s.io` / `external.metrics.k8s.io`.

**Analogy:** CPU scaling = scaling a call center by how busy agents are. Queue-depth scaling = scaling by how many callers are *waiting* — often a better signal of demand.

**Production use case:** A worker consuming an SQS/Kafka queue scales on **queue backlog**, not CPU — because a backed-up queue may not be CPU-bound at all.

**Common mistakes:**
- Trying custom metrics without installing an adapter → `<unknown>`.
- Memory HPA that never scales down and you blame the HPA (it's the app's memory behavior).

**Best practices:**
- For event-driven workloads, prefer **KEDA** (scale-to-zero, native sources).
- Always pair external-metric scaling with a sane `max` and stabilization to avoid stampedes.

```
 custom.metrics.k8s.io  ◄── Prometheus Adapter ◄── Prometheus ◄── app /metrics
 external.metrics.k8s.io ◄── KEDA / adapter     ◄── SQS / Kafka / PubSub
```

---

### 3.7 scaleUp / scaleDown Behavior & Stabilization Window

**Beginner explanation:** Raw formula output can be jittery. The `behavior` field (in `autoscaling/v2`) lets you rate-limit and smooth scaling:
- **Stabilization window:** HPA looks back over a window and uses the *most stabilizing* recommendation. For scale-**down**, it takes the **highest** desired-replica recommendation in the window (so it won't shrink on a brief dip). Default scale-down window = **300s**; scale-up = **0s** (react instantly).
- **Policies:** Limit how fast you grow/shrink (e.g., "max +100% or +4 pods per 60s").

**Analogy:** Tapping the brakes gently instead of slamming them — and waiting to be *sure* traffic dropped before sending workers home, but ramping up the instant a rush hits.

**Production use case:** Bursty traffic (spikes that vanish in seconds) — a long scale-down window prevents you from killing pods you'll immediately need again (thrashing).

**Common mistakes:**
- Aggressive scale-down (short window) → flapping and dropped requests during transient dips.
- Aggressive scale-up policy with a low `max` and slow pod startup → still can't keep up.

**Best practices:**
- Keep scale-up fast (small/zero stabilization) and scale-down slow (≥300s).
- Cap scale-up rate so you don't stampede the scheduler / overwhelm downstream DBs.

```
 behavior:
   scaleUp:   stabilizationWindowSeconds: 0    # react now
              policies: [+100% / 60s, +4 pods / 60s]  (selectPolicy: Max)
   scaleDown: stabilizationWindowSeconds: 300  # wait, be sure
              policies: [-50% / 60s]  (selectPolicy: Max → least aggressive shrink)
```

---

### 3.8 VPA vs HPA

**Beginner explanation:** HPA changes the **number** of pods (scale **out/in**). VPA (Vertical Pod Autoscaler) changes the **size** of each pod — it recommends/sets CPU & memory **requests**. VPA in `Auto`/`Recreate` mode evicts and recreates pods with new requests.

**Analogy:** HPA = hire more identical workers. VPA = give each worker a bigger desk and more tools.

**⚠️ Conflict:** Don't run HPA and VPA on the **same resource metric** (e.g., both on CPU) — they fight. Safe combo: VPA for memory requests + HPA on CPU, or VPA in *recommendation-only* mode.

**Production use case:** VPA for right-sizing batch jobs or single-replica stateful workloads where you can't scale out. HPA for elastic stateless services.

**Best practices:**
- VPA `Off`/recommender mode is great for *discovering* correct requests, even if you apply them manually.

```
        HPA  →  more pods   ───►  ▢ ▢ ▢ ▢ ▢   (horizontal)
        VPA  →  bigger pods  ───►  ▣           (vertical)
                                    ▣
```

---

### 3.9 Cluster Autoscaler (CA) Overview

**Beginner explanation:** HPA/VPA change *pods*. But if there's no room on existing **nodes** to schedule new pods, they sit `Pending`. The **Cluster Autoscaler** watches for unschedulable (Pending) pods and **adds nodes**; it also removes underutilized nodes to save cost.

**Analogy:** HPA hires more waiters; CA builds more tables (and tears down empty rooms) so the waiters have somewhere to work.

**The full chain:** Traffic ↑ → HPA adds pods → pods Pending (no capacity) → CA adds a node → pods schedule.

**Production use case:** Any autoscaling cluster on a cloud provider. On AWS, **Karpenter** is the modern, faster alternative to the classic Cluster Autoscaler.

**Common mistakes:**
- HPA `max` set higher than the node group can ever provide → pods stuck Pending.
- No `requests` → CA can't reason about how many nodes are needed (it uses requests to bin-pack).

**Best practices:**
- Align HPA `max`, node-group `max`, and resource requests so the chain actually completes.

```
 [HPA] adds pods ─► pods Pending (insufficient cpu/mem)
                         │
                         ▼
 [Cluster Autoscaler] sees Pending ─► provisions Node ─► pods schedule
                         │
                         ▼
            (later) Node underutilized ─► CA drains & removes it
```

---

## 4. HANDS-ON LABS

> Prereqs: a running cluster (kind/minikube/managed) from Days 1–7, and `kubectl` configured. Use namespace `day8`:
> `kubectl create namespace day8` and `kubectl config set-context --current --namespace=day8`

### Lab 1 — Manual Scaling

```bash
# Create a Deployment
kubectl create deployment web --image=nginx:1.27 --replicas=3
kubectl get pods -l app=web
```

Expected:
```
NAME                   READY   STATUS    RESTARTS   AGE
web-7d9c8f7b6d-2k4lp   1/1     Running   0          12s
web-7d9c8f7b6d-9xq2v   1/1     Running   0          12s
web-7d9c8f7b6d-pm7rt   1/1     Running   0          12s
```

```bash
# Scale up imperatively
kubectl scale deployment web --replicas=5
kubectl get deploy web
```

Expected:
```
NAME   READY   UP-TO-DATE   AVAILABLE   AGE
web    5/5     5            5           45s
```

```bash
# Conditional scale (only if current is 5)
kubectl scale deployment web --current-replicas=5 --replicas=2
# Scale to zero (valid for stateless during maintenance)
kubectl scale deployment web --replicas=0
kubectl scale deployment web --replicas=3   # bring it back
```

---

### Lab 2 — Install metrics-server (NOTE)

> **Note:** Managed clusters may already have it. For kind/minikube/kubeadm, install the official manifest. The `--kubelet-insecure-tls` flag is a **dev-only** workaround for self-signed kubelet certs.

```bash
# minikube shortcut
minikube addons enable metrics-server

# OR generic install
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

For kind/dev clusters, patch in the insecure TLS flag (NOT for production):
```bash
kubectl patch -n kube-system deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
```

Verify:
```bash
kubectl -n kube-system rollout status deployment metrics-server
kubectl top nodes
kubectl top pods
```

Expected (after ~30–60s of warm-up):
```
NAME           CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
kind-control   142m         3%     1180Mi          30%

NAME                   CPU(cores)   MEMORY(bytes)
web-7d9c8f7b6d-2k4lp   0m           3Mi
```

> If `kubectl top` says `error: Metrics API not available`, wait 60s or check the metrics-server pod logs.

---

### Lab 3 — HPA on CPU with a Load Generator

**Step 1 — a Deployment WITH requests (mandatory) + Service:**

```yaml
# php-apache.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: php-apache
spec:
  replicas: 1
  selector:
    matchLabels: { app: php-apache }
  template:
    metadata:
      labels: { app: php-apache }
    spec:
      containers:
        - name: php-apache
          image: registry.k8s.io/hpa-example   # CPU-burning PHP demo
          ports:
            - containerPort: 80
          resources:
            requests:
              cpu: 200m          # ◄── REQUIRED for HPA
              memory: 64Mi
            limits:
              cpu: 500m
              memory: 128Mi
---
apiVersion: v1
kind: Service
metadata:
  name: php-apache
spec:
  selector: { app: php-apache }
  ports:
    - port: 80
```

```bash
kubectl apply -f php-apache.yaml
```

**Step 2 — create the HPA (autoscaling/v2):**

```yaml
# php-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: php-apache
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: php-apache
  minReplicas: 1
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```

```bash
kubectl apply -f php-hpa.yaml
# (Quick imperative alternative:)
# kubectl autoscale deployment php-apache --cpu-percent=50 --min=1 --max=10
```

```bash
kubectl get hpa
```

Expected (idle):
```
NAME         REFERENCE               TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
php-apache   Deployment/php-apache   cpu: 0%/50%     1         10        1          20s
```

**Step 3 — generate load (run in a SEPARATE terminal):**

```bash
kubectl run -it --rm load-gen --image=busybox:1.36 --restart=Never -- \
  /bin/sh -c "while true; do wget -q -O- http://php-apache; done"
```

---

### Lab 4 — Observe Scale Up, then Scale Down

In the first terminal, watch the HPA:
```bash
kubectl get hpa php-apache --watch
```

Expected as load ramps:
```
NAME         REFERENCE               TARGETS          MINPODS   MAXPODS   REPLICAS   AGE
php-apache   Deployment/php-apache   cpu: 0%/50%      1         10        1          2m
php-apache   Deployment/php-apache   cpu: 250%/50%    1         10        1          3m
php-apache   Deployment/php-apache   cpu: 250%/50%    1         10        4          3m
php-apache   Deployment/php-apache   cpu: 92%/50%     1         10        7          4m
php-apache   Deployment/php-apache   cpu: 49%/50%     1         10        7          5m
```

Inspect the decision-making:
```bash
kubectl describe hpa php-apache
```

Expected (abridged):
```
Name:               php-apache
Reference:          Deployment/php-apache
Metrics:            ( current / target )
  resource cpu on pods (as a percentage of request):  49% (98m) / 50%
Min replicas:       1
Max replicas:       10
Deployment pods:    7 current / 7 desired
Conditions:
  Type            Status  Reason              Message
  ----            ------  ------              -------
  AbleToScale     True    ReadyForNewScale    recommended size matches current size
  ScalingActive   True    ValidMetricFound    the HPA was able to compute the replica count
  ScalingLimited  False   DesiredWithinRange  the desired count is within the acceptable range
Events:
  Type    Reason             Age   From                       Message
  ----    ------             ----  ----                       -------
  Normal  SuccessfulRescale  3m    horizontal-pod-autoscaler  New size: 4; reason: cpu resource utilization above target
  Normal  SuccessfulRescale  2m    horizontal-pod-autoscaler  New size: 7; reason: cpu resource utilization above target
```

**Now stop the load** (Ctrl-C in the load-gen terminal). Watch the scale-down — note it takes ~5 minutes (default stabilization window):

```
php-apache   Deployment/php-apache   cpu: 0%/50%   1   10   7   8m
php-apache   Deployment/php-apache   cpu: 0%/50%   1   10   7   12m  ← still 7, waiting out 300s window
php-apache   Deployment/php-apache   cpu: 0%/50%   1   10   1   13m  ← scaled back to min
```

Cleanup:
```bash
kubectl delete -f php-hpa.yaml -f php-apache.yaml
```

---

## 5. EXERCISES

1. **Manual:** Create a Deployment `cache` (image `redis:7`, requests `cpu:100m`) at 2 replicas. Scale to 6, then to 0, then back to 4. Confirm each with `kubectl get deploy`.
2. **HPA basics:** Attach an HPA to `cache` targeting 60% CPU, min 2, max 8, using the imperative `kubectl autoscale`. Then export it to YAML (`kubectl get hpa cache -o yaml`) and identify the API version it used.
3. **Memory HPA:** Write an `autoscaling/v2` HPA that scales on **memory** `averageUtilization: 70` instead of CPU. Explain in one sentence why memory may not scale *down*.
4. **Behavior tuning:** Add a `behavior` block to an HPA so that scale-up can at most double every 30s, and scale-down waits 600s. Write the full YAML.
5. **Diagnosis drill:** Deliberately create a Deployment with **no resource requests**, attach a CPU HPA, and run `kubectl get hpa`. Record the TARGETS column and explain the output.

---

## 6. TROUBLESHOOTING SECTION

### 6.1 HPA TARGETS show `<unknown>`

- **Symptoms:** `kubectl get hpa` shows `cpu: <unknown>/50%`; no scaling happens.
- **Root cause:** Either (a) **metrics-server not installed/healthy**, or (b) the target pods have **no CPU/memory requests**.
- **Diagnosis:**
  ```bash
  kubectl top pods                 # if this errors → metrics-server problem
  kubectl -n kube-system get deploy metrics-server
  kubectl describe hpa <name>      # look for "failed to get cpu utilization: missing request for cpu"
  kubectl get deploy <name> -o jsonpath='{.spec.template.spec.containers[*].resources}'
  ```
- **Resolution:** Install/repair metrics-server (Lab 2). Add `resources.requests.cpu` to every relevant container and re-roll the Deployment.

### 6.2 HPA exists but does NOT scale

- **Symptoms:** Metrics are visible (e.g., `cpu: 180%/50%`) but `REPLICAS` never increases.
- **Root cause:** Already at `maxReplicas`; or `ScalingActive=False`; or the controller can't update the target (RBAC / wrong `scaleTargetRef`); or within the ±10% tolerance.
- **Diagnosis:**
  ```bash
  kubectl describe hpa <name>      # check Conditions + Events
  kubectl get hpa <name>           # is REPLICAS already == MAXPODS?
  ```
  Look for `ScalingLimited True TooManyReplicas` or `ScalingActive False`.
- **Resolution:** Raise `maxReplicas` if maxed; fix `scaleTargetRef` name/kind; ensure the HPA controller has RBAC to `scale` the resource; confirm the ratio actually exceeds tolerance.

### 6.3 Flapping / thrashing (replicas bouncing up and down)

- **Symptoms:** Replica count oscillates rapidly; pods constantly created/killed; dropped requests on dips.
- **Root cause:** Bursty metric + too-short scale-down stabilization window; target utilization too close to actual; no `behavior` rate limits.
- **Diagnosis:**
  ```bash
  kubectl describe hpa <name>      # many SuccessfulRescale events close together
  kubectl get events --sort-by=.lastTimestamp
  ```
- **Resolution:** Increase `scaleDown.stabilizationWindowSeconds` (e.g., 300–600s), add scale-down rate policies, raise headroom (lower target util), and keep scale-up fast.

### 6.4 Stuck at maxReplicas (load still high)

- **Symptoms:** `REPLICAS == MAXPODS`, TARGETS still way over target (e.g., `cpu: 220%/50%`), latency high.
- **Root cause:** `maxReplicas` too low for the load; OR new pods stuck `Pending` (no node capacity) so the new replicas never become ready.
- **Diagnosis:**
  ```bash
  kubectl describe hpa <name>      # ScalingLimited True / TooManyReplicas
  kubectl get pods -l app=<name>   # any Pending?
  kubectl describe pod <pending-pod>   # "0/3 nodes available: insufficient cpu"
  ```
- **Resolution:** Raise `maxReplicas`; ensure Cluster Autoscaler/Karpenter can add nodes; right-size requests so pods actually fit; investigate downstream bottlenecks (DB) that no amount of pods will fix.

---

## 7. QUIZ SECTION

**MCQ 1.** What is the HPA desiredReplicas formula?
- A) `currentReplicas + (currentMetric − targetMetric)`
- B) `ceil(currentReplicas × (currentMetric / targetMetric))`
- C) `floor(targetMetric / currentMetric)`
- D) `maxReplicas × (currentMetric / targetMetric)`

**MCQ 2.** A CPU-based HPA shows `cpu: <unknown>/50%`. The most likely cause is:
- A) maxReplicas is too low
- B) The Deployment has no CPU resource request and/or metrics-server is missing
- C) The stabilization window is too long
- D) The Service has no endpoints

**MCQ 3.** By default, the scale-**down** stabilization window is:
- A) 0 seconds
- B) 15 seconds
- C) 60 seconds
- D) 300 seconds

**Short 1.** In one sentence, what does metrics-server provide and what is it *not* suitable for?

**Short 2.** Why should you avoid running HPA and VPA on the *same* CPU metric?

**Scenario.** Your checkout service runs at 3 replicas, target CPU 50%, max 10. A flash sale drives average CPU to 200%. Pods take ~40s to become ready, and your node group can't add nodes. What happens, and what two changes do you make?

---

### Answers

- **MCQ 1: B.** `ceil(currentReplicas × (currentMetric / targetMetric))`.
- **MCQ 2: B.** No request → no denominator; or metrics-server isn't serving the Metrics API.
- **MCQ 3: D.** 300 seconds (scale-up default is 0s).
- **Short 1:** metrics-server scrapes kubelets and serves *current* CPU/mem via the Metrics API for `kubectl top` and HPA; it is **not** a historical/alerting store — use Prometheus for that.
- **Short 2:** Both would try to react to the same CPU signal — VPA changing requests while HPA scales pod count — causing conflicting, oscillating decisions; split the axes (e.g., VPA on memory, HPA on CPU) or run VPA in recommendation-only mode.
- **Scenario:** HPA computes `ceil(3 × 200/50) = 12`, clamps to `max=10`, but the new pods go `Pending` because no nodes are available — so effective capacity stays at 3 and the service stays overloaded (stuck-at-max + Pending). **Changes:** (1) raise `maxReplicas` *and* enable Cluster Autoscaler/Karpenter so nodes can be added; (2) pre-scale before the sale and/or set fast scaleUp behavior + smaller pods that fit existing nodes, and reduce target utilization for more headroom given the 40s warm-up.

---

## 8. CHALLENGE PROJECT

**Goal:** Autoscale a web app to survive a traffic spike — correct requests, an HPA with tuned behavior, and observed scale up/down.

**Requirements:**
1. Deploy a CPU-bound web app with explicit `requests` and `limits`.
2. HPA: CPU target 50%, `min: 2`, `max: 12`, with `behavior` that scales up fast and scales down slowly.
3. Drive load and capture: `kubectl get hpa --watch` and `kubectl describe hpa` showing a `SuccessfulRescale` event.
4. Stop load and confirm it scales back to `min` only after the stabilization window.

### Reference Solution

```yaml
# challenge.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spike-web
spec:
  replicas: 2                        # omit/keep low; HPA takes over
  selector: { matchLabels: { app: spike-web } }
  template:
    metadata: { labels: { app: spike-web } }
    spec:
      containers:
        - name: web
          image: registry.k8s.io/hpa-example
          ports: [{ containerPort: 80 }]
          resources:
            requests: { cpu: 200m, memory: 64Mi }   # HPA dependency
            limits:   { cpu: 500m, memory: 128Mi }
---
apiVersion: v1
kind: Service
metadata: { name: spike-web }
spec:
  selector: { app: spike-web }
  ports: [{ port: 80 }]
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: spike-web
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: spike-web }
  minReplicas: 2
  maxReplicas: 12
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 50 }
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0           # react immediately
      policies:
        - type: Percent
          value: 100                          # up to double
          periodSeconds: 30
        - type: Pods
          value: 4                            # or +4 pods
          periodSeconds: 30
      selectPolicy: Max
    scaleDown:
      stabilizationWindowSeconds: 600          # be sure before shrinking
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
```

```bash
kubectl apply -f challenge.yaml
kubectl get hpa spike-web --watch       # terminal 1
# terminal 2 — load
kubectl run -it --rm load --image=busybox:1.36 --restart=Never -- \
  /bin/sh -c "while true; do wget -q -O- http://spike-web; done"
# stop load (Ctrl-C), confirm scale-down after ~10 min
kubectl describe hpa spike-web
kubectl delete -f challenge.yaml
```

**Success criteria:** Replicas climb quickly past 2 toward (or to) 12 under load, `SuccessfulRescale` events appear, and after stopping load replicas drop back to 2 only after the 600s window.

---

## 9. KNOWLEDGE CHECK

You should now be able to answer YES to all:
- [ ] Can I write the desiredReplicas formula from memory (with `ceil`)?
- [ ] Do I know *why* requests are mandatory for utilization HPA?
- [ ] Can I install/verify metrics-server and read `kubectl top`?
- [ ] Can I author an `autoscaling/v2` HPA on CPU, memory, and (conceptually) custom metrics?
- [ ] Can I explain and tune the scale-up/scale-down stabilization windows?
- [ ] Can I distinguish HPA, VPA, and Cluster Autoscaler and where each acts?
- [ ] Can I diagnose `<unknown>`, not-scaling, flapping, and stuck-at-max?

---

## 10. CHEAT SHEET

```bash
# MANUAL SCALING
kubectl scale deployment <d> --replicas=5
kubectl scale deployment <d> --current-replicas=5 --replicas=2   # conditional
kubectl scale deployment <d> --replicas=0                        # scale to zero

# METRICS
kubectl top nodes
kubectl top pods
kubectl top pod <p> --containers

# HPA (imperative)
kubectl autoscale deployment <d> --cpu-percent=50 --min=2 --max=10

# HPA (inspect)
kubectl get hpa
kubectl get hpa <h> --watch
kubectl describe hpa <h>
kubectl get hpa <h> -o yaml

# CLEANUP
kubectl delete hpa <h>
```

**Key numbers:** sync period **15s** · tolerance **±10%** · scale-up window **0s** · scale-down window **300s**.

**Formula:** `desiredReplicas = ceil(currentReplicas × currentMetric / targetMetric)`

**API versions:** prefer `autoscaling/v2` (CPU + mem + custom + behavior). `autoscaling/v1` = CPU-only, no behavior.

**Autoscaler matrix:**
| Tool | Changes | Axis | Needs |
|------|---------|------|-------|
| HPA | pod count | out/in | requests + metrics-server (or adapter) |
| VPA | pod requests | up/down (size) | VPA components |
| Cluster Autoscaler / Karpenter | node count | cluster capacity | cloud node groups + requests |

---

## 11. INTERVIEW PREPARATION

**How to talk about HPA in an interview (the 60-second answer):**
"HPA horizontally scales pods by comparing a current metric to a target. It pulls metrics every ~15s from metrics-server (for CPU/mem) or a metrics adapter (for custom/external), then computes `desiredReplicas = ceil(currentReplicas × current/target)`, clamps to min/max, and applies behavior rate-limits and stabilization windows. CPU/memory HPA *requires* resource requests because utilization is usage divided by request. To avoid thrashing, scale-up is fast (0s window) and scale-down is conservative (300s default)."

**Whiteboard the autoscaling stack:** requests → metrics-server → HPA (pods) → Pending → Cluster Autoscaler (nodes). Knowing this end-to-end chain separates senior from junior answers.

**Common follow-ups & crisp answers:**
- *"Why is my HPA at `<unknown>`?"* → no requests or no metrics-server.
- *"HPA vs VPA?"* → count vs size; don't run both on the same metric.
- *"Can HPA scale to zero?"* → not natively (min ≥ 1); use **KEDA** for scale-to-zero.
- *"Why ceil?"* → bias toward enough capacity, never under-provision.

---

## 12. 🎓 TOP 50 QUESTIONS

### Fundamentals (15)
1. What does HPA scale, and along which axis?
2. State the HPA desiredReplicas formula and why it uses `ceil`.
3. Why are resource requests mandatory for utilization-based HPA?
4. What is metrics-server and what API does it expose?
5. What's the difference between `kubectl top` data and Prometheus data?
6. What is the default HPA sync/reconcile period?
7. What is the HPA tolerance and what does it prevent?
8. Default stabilization windows for scale-up vs scale-down?
9. Difference between `autoscaling/v1` and `autoscaling/v2`?
10. What metric types can `autoscaling/v2` use (Resource, Pods, Object, External)?
11. What is `scaleTargetRef` and what can it point to?
12. What's the difference between `averageUtilization` and `averageValue` targets?
13. Can HPA scale a StatefulSet? A bare ReplicaSet?
14. What happens to `replicas` in the manifest once an HPA manages the Deployment?
15. Can HPA scale to zero replicas natively? If not, what does?

### Practical (10)
16. Write the imperative command to autoscale a deployment on 70% CPU, min 3, max 15.
17. How do you watch HPA decisions live?
18. How do you find why an HPA isn't scaling?
19. Write a `behavior` block that doubles every 30s on scale-up and waits 600s on scale-down.
20. How do you scale a deployment to zero and back?
21. How do you install metrics-server on minikube vs a generic cluster?
22. How do you read per-container CPU usage of a pod?
23. Write an `autoscaling/v2` HPA targeting memory at 75%.
24. How do you conditionally scale only if currently at N replicas?
25. How do you export an existing HPA to YAML for GitOps?

### Scenario (10)
26. Traffic spikes 4×; HPA hits max and pods go Pending — what's happening and the fix?
27. Replicas oscillate every minute — diagnose and fix.
28. Memory HPA scales up but never down — why, and what would you change?
29. A worker is CPU-idle but the queue is backing up — what scaling signal should you use?
30. ArgoCD keeps reverting your replica count — root cause and fix?
31. You need scale-to-zero for a rarely-used internal tool — what do you use?
32. New pods take 90s to warm up; HPA over-scales during ramp — what helps?
33. You must guarantee no downtime during node drains — min replicas setting?
34. HPA on CPU and VPA on CPU are both enabled — what goes wrong?
35. Pre-sale you expect 10× traffic in 2 minutes — autoscale or pre-scale? Why both?

### Troubleshooting (10)
36. `kubectl get hpa` shows `<unknown>/50%` — checklist?
37. `kubectl top pods` returns "Metrics API not available" — causes?
38. metrics-server pod is CrashLooping with TLS errors — fix (and the prod caveat)?
39. HPA `ScalingActive=False` — what does it mean?
40. `ScalingLimited=True, reason TooManyReplicas` — meaning and fix?
41. Pods Pending after scale-up — diagnose the node-capacity angle.
42. HPA computes a huge desired count from a metrics glitch — what guards you?
43. A multi-container pod gives weird utilization — likely cause?
44. HPA events show no `SuccessfulRescale` despite high CPU — where do you look?
45. Scale-down is "too slow" per the team — which knob, and the tradeoff?

### Interview (5)
46. Walk through the full autoscaling chain from request to new node.
47. Compare HPA, VPA, and Cluster Autoscaler with a clear "when to use each."
48. How does HPA handle unready/just-started pods in its calculation?
49. Why might you scale on a custom/external metric instead of CPU? Give an example.
50. Design autoscaling for a bursty, latency-sensitive API with a downstream DB bottleneck — what do you tune and what does scaling NOT fix?

---

## 13. FREE RESOURCES

| Resource | Type | What it's best for |
|----------|------|--------------------|
| Kubernetes Docs — HPA Walkthrough | Official tutorial | The canonical CPU HPA lab (php-apache) |
| Kubernetes Docs — HPA Concepts | Official reference | Algorithm, tolerance, behavior fields |
| metrics-server (sigs.k8s.io) GitHub | Repo/README | Install, flags, troubleshooting |
| VPA (autoscaler repo) README | Repo | VPA modes and caveats |
| Cluster Autoscaler / Karpenter docs | Official | Node-level scaling |
| KEDA docs (keda.sh) | Official | Event-driven & scale-to-zero |

**Docs reading plan (1 hour):**
1. (15m) HPA Walkthrough — do the php-apache lab end to end.
2. (20m) HPA Concepts — read the algorithm + behavior section twice.
3. (10m) metrics-server README — install & TLS troubleshooting.
4. (15m) Skim VPA, Cluster Autoscaler, and KEDA intros for the mental model.

**Must-do:** Run Labs 3 & 4 yourself and watch a real scale up/down cycle.
**Must-know:** The desiredReplicas formula + why requests are required.
**Must-avoid:** Running `kubectl scale` against an HPA-managed Deployment in prod.

**Highest-ROI activity:** Reproduce the `<unknown>` failure (Exercise 5) and fix it — this single drill cements requests + metrics-server, the two most common real-world HPA bugs.

---

## 14. NEXT STEPS

**Active recall (do before moving on — no notes):**
1. Write the desiredReplicas formula from memory.
2. List the two prerequisites for CPU-based HPA.
3. State the default scale-up vs scale-down stabilization windows.
4. Explain the difference between HPA, VPA, and Cluster Autoscaler in one sentence each.
5. From memory, name the four classic HPA failure modes and their root causes.

If you can do all five without peeking, you've mastered Day 8.

➡️ **Continue to Day 9.**
