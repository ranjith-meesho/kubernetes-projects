# 🚢 DAY 7 — Resource Requests & Limits, QoS Classes, and Scheduling Basics

> Prerequisites: Days 1–6 (Pods, Deployments, Services, ConfigMaps/Secrets, basic `kubectl`).
> Today you learn how Kubernetes decides *where* a Pod runs and *how much* compute it is allowed to use — the single most impactful skill for both interviews and production stability.

---

## 1. ## LEARNING OBJECTIVES

By the end of Day 7 you will be able to:

1. Define **resource requests** and **resource limits** for CPU and memory, and explain how they differ.
2. Read and write **CPU units** (`m`, millicores) and **memory units** (`Mi`, `Gi`, `Ki`, and the decimal `M`/`G`).
3. Explain why **CPU is throttled** when it exceeds its limit but **memory triggers OOMKilled**.
4. Classify any Pod into one of the three **QoS classes** — Guaranteed, Burstable, BestEffort — and predict its **eviction order**.
5. Describe how the **kube-scheduler** uses requests to **filter** (predicates) and **score** (priorities) nodes.
6. Apply **LimitRange** (per-object defaults/bounds) and **ResourceQuota** (per-namespace caps) at a high level.
7. Diagnose `Pending` (insufficient CPU/memory), `OOMKilled`, CPU-throttling latency, and BestEffort eviction.

---

## 2. ## 80/20 BREAKDOWN

The 20% of concepts that deliver 80% of real-world and interview value.

| Priority | Concept | Why it matters | Defer? | Interview gold |
|----------|---------|----------------|--------|----------------|
| 🔥 Core | requests vs limits | Drives scheduling AND runtime caps | No | "Requests = scheduling guarantee, limits = runtime ceiling" |
| 🔥 Core | CPU throttle vs memory OOMKill | Explains 90% of prod incidents | No | "CPU is compressible, memory is not" |
| 🔥 Core | QoS classes & eviction order | Predicts who dies under node pressure | No | BestEffort → Burstable → Guaranteed |
| 🔥 Core | Scheduler filter→score | The "how does a Pod land on a node" question | No | Predicates then priorities; `requests` not `limits` used |
| 🟡 High | CPU/memory units (m, Mi, Gi) | Everyone gets this wrong once | No | 1000m = 1 vCPU; Mi=1024², M=1000² |
| 🟡 High | LimitRange | Defaults + per-pod bounds | No | Applies at admission, per namespace |
| 🟡 High | ResourceQuota | Namespace-wide caps | No | Forces requests/limits to be set |
| 🟢 Medium | `kubectl top` & metrics-server | Observability prerequisite | Slightly | top needs metrics-server installed |
| 🟢 Medium | Overcommit (sum limits > capacity) | Why limits ≠ reservation | Slightly | Only requests are reserved |
| ⚪ Later | Topology spread, affinity, taints | Advanced scheduling | Day 8+ | Mention you know they exist |

---

## 3. ## CONCEPT EXPLANATIONS

### 3.1 Requests vs Limits

**Beginner explanation.** A *request* is the amount of CPU/memory a container is **guaranteed**; the scheduler reserves it on a node before placing the Pod. A *limit* is the **maximum** the container may use at runtime. Requests answer "where can this Pod fit?"; limits answer "how much can it grab once running?".

**Analogy.** Booking a restaurant table. The **request** is the reservation — the restaurant holds a table for your party of 4 no matter what. The **limit** is the fire-code maximum — even if more friends show up, you can't exceed 6 at that table. The host (scheduler) uses your reservation size to decide which table to give you, not the fire-code max.

**Production use case.** A Java service needs ~512Mi at idle but spikes to 1Gi during GC. Set `requests.memory: 512Mi` (so it schedules reliably and the node reserves enough) and `limits.memory: 1Gi` (so a runaway leak gets OOMKilled instead of taking down the node and its neighbors).

**Common mistakes.**
- Setting only limits and no requests → request defaults to the limit (Guaranteed), or to 0 if no LimitRange → poor bin-packing or surprise QoS.
- `limits.cpu == requests.cpu` for a bursty web app → unnecessary throttling under load.
- No memory limit at all → one leaky Pod evicts everything on the node.

**Best practices.**
- Always set memory **request = limit** for predictable apps (avoids OOM surprises and gives Guaranteed-ish behavior).
- Set CPU **request** to steady-state usage; for latency-sensitive services consider **omitting the CPU limit** (or setting it generously) to avoid throttling.
- Base numbers on observed data (`kubectl top`, VPA recommendations, Prometheus), not guesses.

```
        REQUEST (reserved)              LIMIT (ceiling)
        ┌───────────────┐              ┌───────────────────────┐
 used → │███████████    │   may burst  │███████████████████    │
        └───────────────┘ ───────────► └───────────────────────┘
        scheduler reserves this        kernel enforces this max
```

---

### 3.2 CPU vs Memory Behavior — Throttle vs OOMKill

**Beginner explanation.** CPU and memory are enforced *very differently*. CPU is a **compressible** resource: if a container hits its CPU limit, the kernel (via the CFS scheduler / cgroup `cpu.cfs_quota_us`) simply gives it fewer time slices — it runs **slower** but keeps living. Memory is **incompressible**: you can't "use memory more slowly." When a container exceeds its memory limit, the kernel's OOM killer terminates the process → the container is **OOMKilled** (exit code 137).

**Analogy.** CPU is a water tap with a flow regulator — turn it down and you still get water, just a thinner stream (throttling). Memory is a fixed-size bucket — overfill it and it bursts; there's no "pour slower" option (OOMKill).

**Production use case.** A batch job slows down (CPU throttle) under its CPU limit — annoying but survivable. The same job with a too-low memory limit gets killed mid-run and restarts forever (`CrashLoopBackOff` driven by repeated OOMKills). You raise the memory limit; CPU throttling you fix by raising the CPU limit or removing it.

**Common mistakes.**
- Blaming "CPU OOM" — CPU never OOMs; only memory does.
- Seeing exit code 137 and assuming a crash bug; it's almost always the OOM killer.
- Setting tight CPU limits on latency-critical services, then chasing p99 latency spikes that are pure throttling.

**Best practices.**
- Monitor `container_cpu_cfs_throttled_periods_total` (Prometheus) — high ratio = raise CPU limit.
- For memory, alert on container memory working set approaching the limit *before* OOMKill.
- Treat OOMKilled as "limit too low OR leak" — check both.

```
 CPU over limit              MEMORY over limit
 ───────────────            ──────────────────
   throttled  ⏳              OOMKilled  💀 (exit 137)
   process slows              process terminated
   stays alive                container restarted
   (compressible)             (incompressible)
```

---

### 3.3 Units — CPU (m) and Memory (Mi/Gi)

**Beginner explanation.**
- **CPU** is measured in *cores*. `1` = 1 vCPU/core. The suffix `m` means *millicores*: `1000m = 1` core, `500m = 0.5` core, `250m = ¼` core. You almost always use `m`.
- **Memory** uses *bytes* with suffixes. **Binary** (power of 1024): `Ki`, `Mi`, `Gi` — `1Mi = 1024 Ki = 1,048,576 bytes`. **Decimal** (power of 1000): `K`, `M`, `G` — `1M = 1,000,000 bytes`. Prefer the **binary** (`Mi`/`Gi`) form to match how the kernel actually measures RAM.

**Analogy.** `Mi` vs `M` is like a "kilobyte" meaning 1024 bytes (what your OS shows) vs 1000 bytes (what a disk vendor prints on the box). Same trap.

**Common mistakes.**
- Writing `500` for CPU thinking it means 500m — it actually means **500 cores** (your Pod will never schedule).
- Confusing `512M` (512 million bytes) with `512Mi` (537 million bytes) — small but real difference.

**Best practices.** Use `m` for CPU and `Mi`/`Gi` for memory, consistently across the org.

```
 CPU:   100m = 0.1 core   500m = 0.5 core   1000m = 1 = one vCPU
 MEM:   1Ki=1024 B   1Mi=1024 Ki   1Gi=1024 Mi   (binary, preferred)
        1K =1000 B   1M =1000 K    1G =1000 M    (decimal)
```

---

### 3.4 QoS Classes & Eviction Order

**Beginner explanation.** Kubernetes auto-assigns each Pod one of three Quality-of-Service classes based purely on its requests/limits. You don't set QoS directly — it's *derived*:

- **Guaranteed** — every container has **both** CPU and memory **requests == limits** (and both are set). Highest protection.
- **Burstable** — at least one container has a request or limit set, but it doesn't meet Guaranteed criteria. The common case.
- **BestEffort** — **no** requests or limits anywhere. Lowest protection.

When a node runs low on memory (node pressure), the kubelet **evicts** Pods to reclaim resources. Eviction order: **BestEffort first → Burstable (those most over their requests) → Guaranteed last**.

**Analogy.** Airline boarding/bumping. Guaranteed = first-class confirmed seat (last to be bumped). Burstable = economy with a seat assignment (bumped if oversold, worst offenders first). BestEffort = standby (first off the plane).

**Production use case.** Critical payment service → Guaranteed (request=limit). Stateless web frontend that can tolerate restarts → Burstable. A scratch debug Pod → BestEffort (fine to die first).

**Common mistakes.**
- Thinking you "set" QoS — you don't; you shape requests/limits and QoS follows.
- Believing Guaranteed Pods are *never* evicted — system OOM killer can still hit any Pod; Guaranteed is just *last* and *least likely*.

**Best practices.** Make truly critical workloads Guaranteed; keep everything else Burstable with sensible requests. Avoid BestEffort in production except for ephemeral/debug Pods.

```
 NODE UNDER MEMORY PRESSURE — eviction order
 ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
 │  BestEffort  │ │  Burstable   │ │  Guaranteed  │
 │  evicted 1st │→│  evicted 2nd │→│  evicted last│
 └──────────────┘ └──────────────┘ └──────────────┘
```

---

### 3.5 Scheduling Basics — Filter then Score

**Beginner explanation.** The **kube-scheduler** watches for Pods with no `nodeName` and assigns each to a node in two phases:

1. **Filtering (predicates).** Eliminate nodes that *can't* run the Pod. Key check: does the node have enough **allocatable** CPU/memory left to satisfy the Pod's **requests**? (Also taints/tolerations, node selectors, affinity, volume topology.) Only requests matter here — **limits are ignored by the scheduler**.
2. **Scoring (priorities).** Among surviving (feasible) nodes, rank them. Plugins like `NodeResourcesFit` (least-allocated vs most-allocated), spreading, affinity weights produce a score 0–100. Highest score wins; ties broken randomly.

The Pod is then **bound** to the winning node.

**Analogy.** Hiring. Filtering = the resume screen ("must have the visa, must meet minimum requirements") — fail any and you're out. Scoring = ranking the qualified candidates to pick the best fit.

**Production use case.** You bump a Deployment's `requests.memory` from 256Mi to 4Gi. Suddenly Pods sit `Pending` — no node has 4Gi *allocatable* free, so filtering removes every node. The fix is right-sizing requests or adding capacity.

**Common mistakes.**
- Thinking the scheduler uses *limits* or *actual usage* — it uses **requests** vs **allocatable**.
- Forgetting **allocatable < capacity** (kube-reserved + system-reserved + eviction threshold are subtracted).
- Setting huge requests "to be safe" → unschedulable Pods and wasted capacity.

**Best practices.** Right-size requests to real steady-state usage; use `kubectl describe node` to see allocatable vs allocated.

```
 PENDING POD
     │
     ▼
 ┌──────────── FILTER (predicates) ───────────┐
 │ node fits requests? taints? selector? ...   │
 │  n1 ✗(no cpu)   n2 ✓   n3 ✓   n4 ✗(taint)   │
 └─────────────────────────────────────────────┘
                     │ feasible: n2, n3
                     ▼
 ┌──────────── SCORE (priorities) ─────────────┐
 │  n2 → 72        n3 → 88   ← winner           │
 └─────────────────────────────────────────────┘
                     │ bind
                     ▼   Pod → n3
```

---

### 3.6 LimitRange & ResourceQuota (Overview)

**Beginner explanation.**
- **LimitRange** is a **per-namespace** policy applied at admission to **individual objects** (Pods/containers/PVCs). It can set **default** requests/limits (so Pods without them inherit values), and enforce **min/max** per container and **default request/limit ratios**. It prevents both BestEffort Pods (by injecting defaults) and absurdly large single containers.
- **ResourceQuota** is a **per-namespace aggregate cap**: total `requests.cpu`, `limits.memory`, number of Pods, etc., summed across the whole namespace. Once the quota is hit, new objects are rejected. It often *requires* every Pod to declare requests/limits (otherwise the quota can't be computed).

**Analogy.** LimitRange = the per-dish portion rules in a buffet ("each plate min 1 / max 3 scoops"). ResourceQuota = the total food budget for the whole party ("this table may consume at most 50 scoops total").

**Production use case.** A shared cluster gives team-A a namespace with a ResourceQuota of `requests.cpu: 20, limits.memory: 64Gi` and a LimitRange defaulting any Pod to `100m/128Mi` requests. Teams can't accidentally starve the cluster, and forgotten requests get sane defaults.

**Common mistakes.**
- Adding a ResourceQuota with no LimitRange → Pods without explicit requests/limits get **rejected** ("must specify requests").
- Setting LimitRange min higher than what tiny sidecars need → sidecars fail admission.

**Best practices.** Deploy LimitRange + ResourceQuota together per namespace; document defaults.

```
 NAMESPACE: team-a
 ┌─────────────────────────────────────────────┐
 │ LimitRange   → per-Pod defaults & min/max    │
 │ ResourceQuota→ Σ requests/limits ≤ cap       │
 │   Pod1  Pod2  Pod3 ...  (each within range,  │
 │                         sum within quota)    │
 └─────────────────────────────────────────────┘
```

---

## 4. ## HANDS-ON LABS

> Assumes a working cluster (`minikube`, `kind`, or any). Verify: `kubectl version --short` and `kubectl get nodes`.

### Lab 1 — Set requests and limits, then verify

Create `lab1-resources.yaml`:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: resource-demo
spec:
  containers:
  - name: app
    image: nginx:1.27
    resources:
      requests:
        cpu: "250m"
        memory: "128Mi"
      limits:
        cpu: "500m"
        memory: "256Mi"
```

```bash
kubectl apply -f lab1-resources.yaml
kubectl get pod resource-demo -o jsonpath='{.spec.containers[0].resources}{"\n"}'
```

Expected output:

```
{"limits":{"cpu":"500m","memory":"256Mi"},"requests":{"cpu":"250m","memory":"128Mi"}}
```

Confirm enforcement is wired into the container runtime:

```bash
kubectl describe pod resource-demo | grep -A4 "Limits\|Requests"
```

```
    Limits:
      cpu:     500m
      memory:  256Mi
    Requests:
      cpu:     250m
      memory:  128Mi
```

---

### Lab 2 — Pod stuck `Pending` due to insufficient requests

Request an impossible amount of memory so no node can satisfy filtering. Create `lab2-pending.yaml`:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: too-big
spec:
  containers:
  - name: app
    image: nginx:1.27
    resources:
      requests:
        memory: "1000Gi"   # intentionally larger than any node
        cpu: "200m"
```

```bash
kubectl apply -f lab2-pending.yaml
kubectl get pod too-big
```

Expected:

```
NAME      READY   STATUS    RESTARTS   AGE
too-big   0/1     Pending   0          10s
```

See *why*:

```bash
kubectl describe pod too-big | grep -A6 Events
```

```
Events:
  Type     Reason            Age   From               Message
  ----     ------            ----  ----               -------
  Warning  FailedScheduling  12s   default-scheduler  0/1 nodes are available:
           1 Insufficient memory. preemption: 0/1 nodes are available:
           1 No preemption victims found for incoming pod.
```

Cleanup: `kubectl delete pod too-big`.

---

### Lab 3 — Trigger OOMKilled with a low memory limit

Create `lab3-oom.yaml` — a container that allocates ~250Mi but is limited to 100Mi:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: oom-demo
spec:
  restartPolicy: Never
  containers:
  - name: hog
    image: polinux/stress
    command: ["stress"]
    args: ["--vm", "1", "--vm-bytes", "250M", "--vm-hang", "1"]
    resources:
      limits:
        memory: "100Mi"
      requests:
        memory: "100Mi"
```

```bash
kubectl apply -f lab3-oom.yaml
sleep 10
kubectl get pod oom-demo
```

Expected:

```
NAME       READY   STATUS      RESTARTS   AGE
oom-demo   0/1     OOMKilled   0          12s
```

Confirm exit code 137 and the OOM reason:

```bash
kubectl describe pod oom-demo | grep -A5 "Last State\|State"
```

```
    State:          Terminated
      Reason:       OOMKilled
      Exit Code:    137
```

> Exit 137 = 128 + 9 (SIGKILL from the OOM killer). Cleanup: `kubectl delete pod oom-demo`.

---

### Lab 4 — Inspect QoS class for each class

Apply three Pods, one per class. Create `lab4-qos.yaml`:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: qos-guaranteed
spec:
  containers:
  - name: app
    image: nginx:1.27
    resources:
      requests: { cpu: "200m", memory: "200Mi" }
      limits:   { cpu: "200m", memory: "200Mi" }   # request == limit → Guaranteed
---
apiVersion: v1
kind: Pod
metadata:
  name: qos-burstable
spec:
  containers:
  - name: app
    image: nginx:1.27
    resources:
      requests: { cpu: "100m", memory: "100Mi" }
      limits:   { cpu: "300m", memory: "300Mi" }   # request < limit → Burstable
---
apiVersion: v1
kind: Pod
metadata:
  name: qos-besteffort
spec:
  containers:
  - name: app
    image: nginx:1.27                              # no requests/limits → BestEffort
```

```bash
kubectl apply -f lab4-qos.yaml
kubectl get pods -o custom-columns=NAME:.metadata.name,QOS:.status.qosClass
```

Expected:

```
NAME             QOS
qos-besteffort   BestEffort
qos-burstable    Burstable
qos-guaranteed   Guaranteed
```

Cleanup: `kubectl delete -f lab4-qos.yaml`.

---

### Lab 5 — `kubectl top` (requires metrics-server)

```bash
# On minikube:
minikube addons enable metrics-server
# On kind/others: kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Wait ~30–60s for metrics to populate, then:
kubectl top nodes
kubectl top pods
```

Expected (numbers vary):

```
NAME       CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
minikube   312m         15%    1450Mi          37%

NAME             CPU(cores)   MEMORY(bytes)
qos-guaranteed   1m           7Mi
qos-burstable    1m           6Mi
```

> If you see `error: Metrics API not available`, metrics-server isn't ready yet — wait or check `kubectl get deploy metrics-server -n kube-system`.

---

### Lab 6 (bonus) — LimitRange + ResourceQuota in a namespace

```yaml
apiVersion: v1
kind: Namespace
metadata: { name: team-a }
---
apiVersion: v1
kind: LimitRange
metadata: { name: defaults, namespace: team-a }
spec:
  limits:
  - type: Container
    default:        { cpu: "500m", memory: "256Mi" }   # default LIMIT
    defaultRequest: { cpu: "100m", memory: "128Mi" }   # default REQUEST
    max:            { cpu: "2",    memory: "1Gi" }
    min:            { cpu: "50m",  memory: "32Mi" }
---
apiVersion: v1
kind: ResourceQuota
metadata: { name: budget, namespace: team-a }
spec:
  hard:
    requests.cpu: "2"
    requests.memory: 2Gi
    limits.cpu: "4"
    limits.memory: 4Gi
    pods: "10"
```

```bash
kubectl apply -f lab6-quota.yaml
kubectl run nodefaults --image=nginx:1.27 -n team-a
kubectl get pod nodefaults -n team-a -o jsonpath='{.spec.containers[0].resources}{"\n"}'
kubectl describe resourcequota budget -n team-a
```

Expected — the Pod inherited LimitRange defaults, and the quota shows usage:

```
{"limits":{"cpu":"500m","memory":"256Mi"},"requests":{"cpu":"100m","memory":"128Mi"}}

Resource          Used   Hard
--------          ----   ----
limits.cpu        500m   4
limits.memory     256Mi  4Gi
pods              1      10
requests.cpu      100m   2
requests.memory   128Mi  2Gi
```

Cleanup: `kubectl delete ns team-a`.

---

## 5. ## EXERCISES

1. Write a Deployment with 3 replicas of `nginx`, each requesting `100m`/`128Mi` and limited to `200m`/`256Mi`. Verify total reserved CPU across replicas equals `300m`.
2. Create a Pod that you predict will be **Burstable**, apply it, and confirm its QoS via `-o custom-columns`. Then modify it to become **Guaranteed** without changing the limits.
3. Force a `Pending` Pod by requesting more CPU than any single node's allocatable, and read the exact `FailedScheduling` message. Record the allocatable CPU from `kubectl describe node`.
4. Reproduce an `OOMKilled` using `polinux/stress`, then raise the memory limit just enough to make it run successfully. Capture both the failing and passing exit states.
5. In a new namespace, apply a ResourceQuota **without** a LimitRange, then try `kubectl run` with no resources. Observe the rejection, then add a LimitRange and retry successfully.

---

## 6. ## TROUBLESHOOTING SECTION

### Issue 1 — Pod `Pending` due to insufficient CPU/memory
- **Symptoms:** Pod stuck `Pending`; never schedules.
- **Root cause:** No node's *allocatable* CPU/memory can satisfy the Pod's **requests** (filtering removes all nodes).
- **Diagnosis:**
  ```bash
  kubectl describe pod <pod> | grep -A6 Events     # "Insufficient cpu/memory"
  kubectl describe node <node> | grep -A6 Allocated
  ```
- **Resolution:** Lower the Pod's requests to fit, add/scale nodes, or use cluster-autoscaler. Verify allocatable, not capacity.

### Issue 2 — Container `OOMKilled` (exit 137)
- **Symptoms:** Container restarts repeatedly; `Reason: OOMKilled`, `Exit Code: 137`; possible `CrashLoopBackOff`.
- **Root cause:** Process working set exceeded `limits.memory`; kernel OOM killer terminated it.
- **Diagnosis:**
  ```bash
  kubectl describe pod <pod> | grep -A5 "Last State"
  kubectl top pod <pod>                      # current usage vs limit
  ```
- **Resolution:** Raise `limits.memory` to cover real peak (set request=limit for stability) **and** investigate leaks/GC tuning. Don't blindly raise without checking for a leak.

### Issue 3 — CPU throttling hurting latency
- **Symptoms:** p95/p99 latency spikes under load while CPU usage sits *at* the limit; app isn't crashing.
- **Root cause:** Container hit `limits.cpu`; CFS throttles it (`cpu.cfs_quota_us`), starving threads of CPU time.
- **Diagnosis:**
  ```bash
  # Prometheus / metrics:
  rate(container_cpu_cfs_throttled_periods_total[5m])
      / rate(container_cpu_cfs_periods_total[5m])      # high ratio = heavy throttle
  ```
- **Resolution:** Raise `limits.cpu`, or remove the CPU limit for latency-critical services (keep the request). Right-size based on observed peak.

### Issue 4 — BestEffort Pod evicted first under node pressure
- **Symptoms:** Under memory pressure, a Pod is `Evicted` while others survive; it had no requests/limits.
- **Root cause:** BestEffort QoS is first in the kubelet eviction order when the node hits memory pressure thresholds.
- **Diagnosis:**
  ```bash
  kubectl get pod <pod> -o jsonpath='{.status.qosClass}{"\n"}'   # BestEffort
  kubectl describe node <node> | grep -i pressure                # MemoryPressure True
  kubectl get events --field-selector reason=Evicted
  ```
- **Resolution:** Add requests/limits to promote it to Burstable/Guaranteed; add node capacity; raise eviction thresholds only with care.

---

## 7. ## QUIZ SECTION

**MCQ 1.** Which resource does the kube-scheduler use to decide if a Pod fits on a node?
A) limits  B) actual usage  C) requests  D) QoS class

**MCQ 2.** A container exceeds its CPU limit. What happens?
A) OOMKilled  B) Evicted  C) CPU throttled (slowed)  D) Pod deleted

**MCQ 3.** A Pod has `requests.cpu=100m, limits.cpu=200m, requests.memory=limits.memory=256Mi`. Its QoS class is:
A) Guaranteed  B) Burstable  C) BestEffort  D) Undefined

**Short 1.** Explain why memory is "incompressible" and CPU is "compressible."

**Short 2.** What is the eviction order of QoS classes under node memory pressure?

**Scenario.** A team's Deployment schedules fine on a 4-CPU node test cluster but goes `Pending` in production. The only change was bumping `requests.cpu` from `500m` to `3`. Production nodes are 2-CPU. Explain the failure and give two fixes.

---

### Quiz Answers

- **MCQ 1 → C.** The scheduler reserves and filters on **requests**; limits and live usage are not used for placement.
- **MCQ 2 → C.** CPU is compressible → throttled, not killed.
- **MCQ 3 → B.** Burstable: memory matches Guaranteed criteria but CPU request ≠ limit, so the Pod is not Guaranteed.
- **Short 1.** A process can run with fewer CPU time slices (compressible) so the kernel just slows it. It cannot run with "less memory than it allocated" — pages either exist or don't — so the only recourse when over the limit is to kill the process (incompressible).
- **Short 2.** BestEffort first → Burstable next (most over their requests first) → Guaranteed last.
- **Scenario.** A `3` CPU request can't fit on 2-CPU nodes — *allocatable* is even less than 2 after system/kube reservations — so filtering removes every node and the Pod stays `Pending`. **Fixes:** (1) lower `requests.cpu` to fit real usage (e.g. `500m`–`1`); (2) use larger nodes or enable cluster-autoscaler to add capacity.

---

## 8. ## CHALLENGE PROJECT — Right-size a multi-container app & set namespace quotas

**Goal.** You inherit a `web + sidecar (log shipper) + cache` workload running with **no** resource settings in a shared cluster. Right-size each container, set appropriate QoS, and add namespace governance.

**Requirements.**
1. Namespace `shop`.
2. `web` (Node app): observed steady ~`200m`/`300Mi`, peaks `500m`/`450Mi`. Latency-sensitive.
3. `log-shipper` sidecar: tiny, ~`20m`/`32Mi`, can tolerate throttling.
4. `cache` (redis): must be stable/predictable (Guaranteed).
5. Add a LimitRange (defaults + min/max) and ResourceQuota for the namespace.
6. Verify QoS classes and that the quota tracks usage.

### Reference Solution

```yaml
apiVersion: v1
kind: Namespace
metadata: { name: shop }
---
apiVersion: v1
kind: LimitRange
metadata: { name: shop-defaults, namespace: shop }
spec:
  limits:
  - type: Container
    default:        { cpu: "300m", memory: "256Mi" }
    defaultRequest: { cpu: "100m", memory: "128Mi" }
    min:            { cpu: "10m",  memory: "16Mi" }
    max:            { cpu: "1",    memory: "1Gi" }
---
apiVersion: v1
kind: ResourceQuota
metadata: { name: shop-quota, namespace: shop }
spec:
  hard:
    requests.cpu: "2"
    requests.memory: 2Gi
    limits.cpu: "4"
    limits.memory: 4Gi
    pods: "20"
---
apiVersion: apps/v1
kind: Deployment
metadata: { name: shop-app, namespace: shop }
spec:
  replicas: 2
  selector: { matchLabels: { app: shop } }
  template:
    metadata: { labels: { app: shop } }
    spec:
      containers:
      - name: web                       # Burstable: latency-sensitive, room to burst
        image: nginx:1.27
        resources:
          requests: { cpu: "200m", memory: "300Mi" }
          limits:   { cpu: "500m", memory: "450Mi" }
      - name: log-shipper               # Burstable: tiny, throttle-tolerant
        image: busybox:1.36
        command: ["sh","-c","while true; do sleep 30; done"]
        resources:
          requests: { cpu: "20m", memory: "32Mi" }
          limits:   { cpu: "50m", memory: "64Mi" }
      - name: cache                      # Guaranteed: request == limit
        image: redis:7-alpine
        resources:
          requests: { cpu: "250m", memory: "256Mi" }
          limits:   { cpu: "250m", memory: "256Mi" }
```

```bash
kubectl apply -f challenge.yaml
kubectl get pods -n shop -o custom-columns=NAME:.metadata.name,QOS:.status.qosClass
kubectl describe resourcequota shop-quota -n shop
```

**Discussion.** The **Pod** QoS is Burstable here (because `web` and `log-shipper` aren't request==limit), even though `cache` alone would be Guaranteed — QoS is per-Pod, derived from *all* containers. To make the whole Pod Guaranteed, every container would need request==limit for both CPU and memory. Web omits a tight CPU cap relative to request to avoid throttling p99; the sidecar is intentionally cheap; cache is locked down for predictability.

---

## 9. ## KNOWLEDGE CHECK

You should now be able to answer "yes" to all of these:

- [ ] Can I explain requests vs limits in one sentence each?
- [ ] Do I know which one the scheduler uses (requests) and which the kernel enforces (limits)?
- [ ] Can I predict throttle vs OOMKill for CPU vs memory?
- [ ] Can I write `m`, `Mi`, `Gi` correctly and explain `Mi` vs `M`?
- [ ] Can I classify any Pod's QoS and state the eviction order?
- [ ] Can I describe the scheduler's filter→score flow?
- [ ] Do I know what LimitRange and ResourceQuota each do, and why they pair up?
- [ ] Can I diagnose Pending, OOMKilled, throttling, and eviction from `kubectl` output?

---

## 10. ## CHEAT SHEET

```bash
# View a pod's resources & QoS
kubectl get pod <p> -o jsonpath='{.spec.containers[*].resources}{"\n"}'
kubectl get pod <p> -o jsonpath='{.status.qosClass}{"\n"}'
kubectl get pods -o custom-columns=NAME:.metadata.name,QOS:.status.qosClass

# Why is it pending? / OOM?
kubectl describe pod <p> | grep -A6 Events
kubectl describe pod <p> | grep -A5 "Last State"     # Reason: OOMKilled, Exit 137

# Node capacity vs allocatable vs allocated
kubectl describe node <n> | grep -A6 "Allocatable\|Allocated"

# Live usage (needs metrics-server)
kubectl top nodes
kubectl top pods -A --sort-by=memory

# Namespace governance
kubectl get limitrange,resourcequota -n <ns>
kubectl describe resourcequota <q> -n <ns>
```

| Concept | Rule of thumb |
|---------|---------------|
| Scheduler uses | **requests** vs node **allocatable** |
| Kernel enforces | **limits** (CPU throttle / memory OOMKill) |
| CPU over limit | throttled (slower, alive) |
| Memory over limit | OOMKilled (exit 137) |
| Guaranteed | every container req==limit (cpu+mem) |
| Burstable | something set, not Guaranteed |
| BestEffort | nothing set |
| Eviction order | BestEffort → Burstable → Guaranteed |
| CPU unit | 1000m = 1 core |
| Memory unit | 1Gi = 1024Mi (binary, preferred) |

---

## 11. ## INTERVIEW PREPARATION

**How to frame answers.** Always anchor on two axes: (1) *requests vs limits* and (2) *CPU compressible vs memory incompressible*. Most resource questions decompose into these.

**Talking points that impress:**
- "The scheduler reserves **requests**, not limits — that's why sum of limits can exceed node capacity (overcommit), but sum of *requests* can't."
- "QoS is **derived**, not declared — I shape requests/limits and Kubernetes computes the class."
- "For latency-critical services I often **drop the CPU limit** but keep the request, to avoid CFS throttling on p99."
- "Memory I usually set **request == limit** so behavior is deterministic and OOM is predictable."
- "Allocatable < capacity because of kube/system-reserved and eviction thresholds — I always check allocatable when debugging Pending."

**Red flags interviewers listen for:** saying CPU can OOM, claiming the scheduler uses limits, or thinking Guaranteed Pods are never killed.

---

## 12. ## 🎓 TOP 50 QUESTIONS

### Fundamentals (15)
1. What is a resource request? A resource limit? How do they differ?
2. Which does the scheduler use for placement — requests or limits?
3. What does `500m` CPU mean? What does `1` mean?
4. What's the difference between `Mi` and `M`?
5. What happens when a container exceeds its CPU limit?
6. What happens when a container exceeds its memory limit?
7. What exit code indicates OOMKilled, and why that number?
8. Name the three QoS classes and how each is determined.
9. What is the eviction order under node memory pressure?
10. Why is CPU "compressible" and memory "incompressible"?
11. What is node *allocatable* and how does it differ from *capacity*?
12. Can the sum of limits exceed node capacity? Can the sum of requests?
13. What is a LimitRange and what scope does it apply to?
14. What is a ResourceQuota and what scope does it apply to?
15. If you set only limits and no requests, what happens to the request value?

### Practical (10)
16. Write YAML setting requests `100m/128Mi` and limits `500m/256Mi`.
17. How do you check a Pod's QoS class via `kubectl`?
18. How do you see why a Pod is `Pending`?
19. How do you confirm a container was OOMKilled?
20. What command shows live CPU/memory per Pod, and what must be installed?
21. How do you view a node's allocatable and currently-allocated resources?
22. How do you make a Pod Guaranteed?
23. How would you set namespace defaults so Pods are never BestEffort?
24. How do you inspect ResourceQuota usage vs limits?
25. How do you sort `kubectl top pods` by memory across all namespaces?

### Scenario (10)
26. A Pod is `Pending` after you raised requests — what do you check first?
27. p99 latency spikes but the app never crashes and CPU sits at the limit — why?
28. A batch job restarts forever with exit 137 — what's happening and how do you fix it?
29. Under node pressure, a debug Pod dies first — why, and how to protect a critical Pod?
30. You add a ResourceQuota and suddenly new Pods are rejected — why?
31. A team requests `8Gi` "to be safe" on 4Gi nodes — what's the consequence?
32. You need a critical DB Pod to survive eviction as long as possible — what QoS and how?
33. A sidecar makes the whole Pod Burstable despite the main container being Guaranteed-shaped — why?
34. Latency-sensitive API: should you set a CPU limit? Justify.
35. Two namespaces fight over cluster CPU — how do you fence them off?

### Troubleshooting (10)
36. `FailedScheduling: Insufficient memory` — diagnosis steps?
37. `OOMKilled` with no obvious leak — what else to check (GC, working set)?
38. High `container_cpu_cfs_throttled_periods_total` ratio — meaning and fix?
39. `Evicted` Pods with `MemoryPressure` on the node — root cause and fix?
40. `kubectl top` returns "Metrics API not available" — cause and fix?
41. Pod schedules in staging but Pending in prod — what differs?
42. Pod request==limit memory but still OOMKilled — is that possible? Why?
43. Quota shows `requests.cpu` used but Pods say Pending — reconcile this.
44. A LimitRange `min` blocks a tiny sidecar from admission — fix?
45. After raising memory limit, OOM stops but node now over-committed — what's the risk?

### Interview (5)
46. Explain the full scheduler flow from a Pending Pod to a bound Pod.
47. Walk through how QoS affects both eviction and OOM-killer scoring.
48. When would you intentionally leave a container BestEffort?
49. Design a namespace governance policy for a multi-team shared cluster.
50. Defend "request == limit for memory, no CPU limit" as a default policy.

---

## 13. ## FREE RESOURCES

| Resource | Type | Focus |
|----------|------|-------|
| kubernetes.io — Managing Resources for Containers | Docs | requests/limits, units |
| kubernetes.io — Configure QoS for Pods | Docs | QoS classes |
| kubernetes.io — Kubernetes Scheduler | Docs | filter/score |
| kubernetes.io — Resource Quotas / Limit Ranges | Docs | namespace governance |
| kubernetes.io — Node-pressure Eviction | Docs | eviction order, thresholds |
| metrics-server (GitHub) | Tool | `kubectl top` backend |
| Vertical Pod Autoscaler (GitHub) | Tool | right-sizing recommendations |
| killercoda / play-with-k8s | Lab | free browser clusters |

**Docs reading plan (≈60 min):** (1) Managing Resources for Containers → (2) Configure QoS → (3) Node-pressure Eviction → (4) Resource Quotas + Limit Ranges → (5) skim Scheduler overview.

- **Must-read:** "Managing Resources for Containers" (the foundation for everything today).
- **Must-do:** Labs 2, 3, 4 (Pending, OOMKilled, QoS) — they cement the mental model.
- **Must-watch:** Any "Kubernetes resource requests vs limits" conference talk (CNCF/KubeCon on YouTube).
- **Highest ROI:** Internalize "requests = scheduling, limits = runtime; CPU throttles, memory OOMKills." That single sentence answers most interview and incident questions.

---

## 14. ## NEXT STEPS

**Active recall (do before moving on — close this file and answer aloud):**
1. State requests vs limits in one sentence each.
2. CPU over limit vs memory over limit — what happens to each?
3. Classify a Pod with `req.cpu=100m, lim.cpu=200m, req.mem=lim.mem=256Mi`. (Answer: Burstable.)
4. Give the QoS eviction order.
5. Describe the scheduler's two phases and which resource drives them.
6. What's the difference between LimitRange and ResourceQuota?

If you answered all six confidently, you've mastered Day 7.

➡️ **Continue to Day 8.**
