# 🚢 DAY 6 — Health Probes: Readiness, Liveness & Startup

> *"A container that is running is not the same as a container that is healthy, and a container that is healthy is not the same as a container that is ready to serve traffic."*

Welcome to Day 6. You finished Days 1–5 (Pods, ReplicaSets, Deployments, Services, rolling updates). Today you teach Kubernetes how to *tell whether your app is alive, ready, and finished booting* — the difference between a self-healing platform and a pile of zombie containers silently dropping traffic.

---

## 1. ## LEARNING OBJECTIVES

By the end of Day 6 you will be able to:

1. Explain **why** probes exist and what problem each one solves.
2. Configure **Liveness** probes to restart deadlocked/hung containers.
3. Configure **Readiness** probes to gate Service traffic until a pod can actually serve it.
4. Configure **Startup** probes to protect slow-booting apps from premature liveness kills.
5. Choose the right **handler**: `httpGet`, `tcpSocket`, `exec`, or `grpc`.
6. Tune every **timing parameter**: `initialDelaySeconds`, `periodSeconds`, `timeoutSeconds`, `successThreshold`, `failureThreshold`.
7. Predict how probes interact with **Service endpoints** and **rolling updates**.
8. Diagnose the four classic probe failures: CrashLoopBackOff from aggressive liveness, traffic to not-ready pods, slow apps killed before startup, and wrong path/port.

---

## 2. ## 80/20 BREAKDOWN

The 20% that delivers 80% of real-world value:

| Priority | Topic | Why it matters | Defer? |
|----------|-------|----------------|--------|
| 🔴 MUST | Readiness probe gating Service traffic | Prevents 500s during deploys & boot | No |
| 🔴 MUST | Liveness probe + `failureThreshold` | Self-heals hung pods without nuking healthy ones | No |
| 🔴 MUST | Startup probe for slow apps | Stops liveness from killing JVM/migrations on boot | No |
| 🔴 MUST | `httpGet` handler + correct path/port | 90% of web apps use this | No |
| 🟠 HIGH | Liveness vs Readiness difference | #1 interview question; #1 production mistake | No |
| 🟠 HIGH | Timing params (period/timeout/threshold) | Misconfiguration = outages | No |
| 🟡 MED | `exec` & `tcpSocket` handlers | Non-HTTP workloads (DBs, queues) | Slightly |
| 🟢 LOW | `grpc` handler | Only if you run gRPC services | Yes, until needed |

> 💎 **Interview gold:**
> - *Liveness failing → container is **restarted**. Readiness failing → pod is **removed from Service endpoints** (NOT restarted).*
> - *A pod can be `Running` but **not `Ready`** — and that pod receives **zero** Service traffic.*
> - *Startup probe **disables** liveness and readiness until it succeeds once.*
> - *Never point a liveness probe at a dependency (DB/cache). Liveness must answer "is THIS process wedged?", not "is the world healthy?"*

---

## 3. ## CONCEPT EXPLANATIONS

### 3.0 Why probes matter

**Beginner explanation:** Kubernetes restarts containers that *crash* (process exits). But many failures are silent — a process is still running but stuck in a deadlock, an infinite loop, or out of file descriptors. It accepts no requests yet never exits. The kubelet can't see inside your app, so *you* must give it a health endpoint to poll. Probes are that contract.

**Analogy:** A night-shift security guard (kubelet) walks the building. He can see if a light is *on* (process running) but not whether the person inside has fainted. So you install a button the worker must press every 10 seconds (probe). No press → guard calls a medic (restart) or stops routing customers to that desk (remove from endpoints).

**Production use case:** A Node.js API leaks memory and locks its event loop after 6 hours. The process never exits, so without a liveness probe it stays "Running" forever, black-holing traffic. A liveness probe detects the hang and restarts it automatically at 3 AM with no pager.

**Common mistakes:**
- Assuming "Running" == "healthy". It does not.
- Adding only a liveness probe and skipping readiness (traffic hits booting pods).

**Best practice:** Define **readiness for every workload that serves traffic**, **liveness for anything that can hang**, **startup for anything slow to boot**.

```
   ┌──────────── kubelet ────────────┐
   │  polls every periodSeconds      │
   └───────┬─────────────┬───────────┘
           │             │
     liveness?      readiness?
           │             │
    fail → restart   fail → remove
    container        from endpoints
                     (no restart)
```

---

### 3.1 Liveness probe — restart on deadlock

**Beginner explanation:** "Is this container's process wedged?" If the liveness probe fails `failureThreshold` times in a row, the kubelet **kills and restarts the container** (the Pod stays, the container restarts; the `RESTARTS` counter increments).

**Analogy:** A heartbeat monitor. Flatline for too long → defibrillator (restart). It does NOT ask whether the patient can run a marathon — only whether they're alive.

**Production use case:** A Java service deadlocks on a thread-pool exhaustion bug. `/healthz` stops responding. Liveness fails 3× → container restarted, recovers in seconds.

**Common mistakes:**
- Pointing liveness at a database. DB blip → all pods restart → cascading outage.
- `initialDelaySeconds` too short → kills app before it boots → CrashLoopBackOff. (Use a startup probe instead.)
- `timeoutSeconds: 1` on a GC-pausing JVM → false positives.

**Best practice:** Liveness should be *cheap, local, dependency-free*. Generous `failureThreshold` (≥3). Prefer a startup probe over a huge `initialDelaySeconds`.

```
liveness fails ×failureThreshold
        │
        ▼
  kubelet sends SIGTERM → grace → SIGKILL
        │
        ▼
  container RESTARTS (Pod survives, RESTARTS++)
```

---

### 3.2 Readiness probe — gate Service traffic

**Beginner explanation:** "Can this pod serve requests *right now*?" If readiness fails, the pod's IP is **removed from all Service Endpoints/EndpointSlices** — no new traffic is routed to it. The container is **NOT restarted**. When readiness passes again, the pod is re-added.

**Analogy:** A "Closed" sign on a checkout lane. The cashier is fine — they're just restocking. Customers (traffic) are diverted to open lanes. When done, flip to "Open".

**Production use case:** During a rolling update a new pod needs 20s to warm a cache before it can serve. Readiness keeps it out of the load balancer until the cache is hot, so users never hit a cold, slow, or erroring pod.

**Common mistakes:**
- No readiness probe → traffic hits the pod the instant the container starts, before the server binds → connection refused / 502.
- Making readiness depend on a *non-critical* dependency → entire fleet drops out of endpoints during a minor blip → self-inflicted outage.

**Best practice:** Readiness *may* check critical dependencies (e.g., DB the request path needs). Use it as a traffic valve during deploys, warmups, and graceful drains.

```
        Service (ClusterIP)
              │
   ┌──────────┼──────────┐
   ▼          ▼          ▼
 PodA       PodB       PodC
 Ready ✅   NOT Ready ❌  Ready ✅
   │          │           │
   IN      REMOVED       IN
 endpoints  from         endpoints
            endpoints
   ▲          (no traffic)  ▲
   └──── traffic flows ─────┘
   PodB still Running, still in ReplicaSet, NOT restarted
```

---

### 3.3 Startup probe — protect slow-starting apps

**Beginner explanation:** Some apps take 1–5 minutes to boot (JVM warmup, DB migrations, model loading). A startup probe runs **first and alone** — while it runs, **liveness and readiness probes are disabled**. Once the startup probe succeeds **once**, it never runs again and liveness/readiness take over. If it fails `failureThreshold` times, the container is killed.

**Analogy:** A car's "preheating glow plugs" light on a cold diesel. You don't rev the engine (liveness) until that light goes off. The startup probe is that light.

**Production use case:** A Spring Boot app needs up to 120s to start. Instead of a fragile `initialDelaySeconds: 120` on liveness, use a startup probe with `failureThreshold: 30, periodSeconds: 5` (= up to 150s budget). After boot, liveness uses a tight 10s period for fast hang detection.

**Common mistakes:**
- Skipping startup and cranking `initialDelaySeconds` huge on liveness — then hang detection is slow forever, even post-boot.
- Startup budget (`failureThreshold × periodSeconds`) smaller than worst-case boot → CrashLoopBackOff.

**Best practice:** Startup budget = worst-case boot time + margin. Keep liveness `periodSeconds` small (fast post-boot detection) since startup absorbs the slow boot.

```
 t=0   container starts
  │    [STARTUP probe runs; liveness+readiness OFF]
  │    fail, fail, fail... (within budget = OK)
  ▼
 startup SUCCEEDS once  ──►  startup never runs again
  │
  ▼
 [LIVENESS + READINESS now active]
```

---

### 3.4 The three handler types (+ gRPC)

Every probe uses exactly one handler:

| Handler | What it checks | Success criterion | Use when |
|---------|----------------|-------------------|----------|
| `httpGet` | HTTP GET to path:port | Status **200–399** | Web apps / REST / has a health endpoint |
| `tcpSocket` | Can open a TCP connection | Connection accepted | Databases, brokers, raw TCP (Redis, Postgres) |
| `exec` | Runs a command in container | **Exit code 0** | No network endpoint; check a file/process |
| `grpc` | gRPC Health Checking Protocol | `SERVING` status | gRPC services (GA since 1.27) |

**Beginner explanation:** `httpGet` is the workhorse — Kubernetes hits `http://<podIP>:<port><path>` and any 2xx/3xx = healthy. `tcpSocket` just checks "can I connect?" — good for things with no HTTP layer. `exec` runs *inside* the container (`cat /tmp/healthy`, `pg_isready`) — exit 0 = pass. `grpc` speaks the standard gRPC health protocol.

**Analogy:** httpGet = knocking and waiting for "come in"; tcpSocket = checking the door is unlocked; exec = walking in and checking a clipboard; grpc = a specialized intercom only gRPC apps answer.

**Production use cases:**
- `httpGet` → `/healthz` on your API.
- `tcpSocket` → port 5432 on Postgres.
- `exec` → `pg_isready -U app` or `cat /tmp/ready`.
- `grpc` → `grpc.health.v1.Health/Check`.

**Common mistakes:**
- `exec` probes are *expensive* (fork a process every period) — avoid tight periods.
- `tcpSocket` only proves the port is open, not that the app behind it works (a deadlocked server still accepts the socket).
- `httpGet` honors `Host` header via `httpHeaders` — needed for vhost apps.

**Best practice:** Prefer `httpGet`/`grpc` for real liveness signal; reserve `tcpSocket` for non-HTTP; use `exec` sparingly.

```
httpGet:  kubelet --GET /healthz:8080--> [200] ✅
tcp:      kubelet --connect :5432-------> [SYN/ACK] ✅
exec:     kubelet --(in container) sh -c "pg_isready"--> exit 0 ✅
grpc:     kubelet --Health/Check :50051--> SERVING ✅
```

---

### 3.5 Timing parameters (all of them)

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `initialDelaySeconds` | 0 | Wait this long after container start before the **first** probe. |
| `periodSeconds` | 10 | Run the probe every N seconds. |
| `timeoutSeconds` | 1 | A single probe attempt is a failure if it takes longer than this. |
| `successThreshold` | 1 | Consecutive successes needed to be considered passing. Must be **1** for liveness & startup. |
| `failureThreshold` | 3 | Consecutive failures before the action fires (restart / remove from endpoints). |

**Beginner explanation:** Worst-case detection time for liveness ≈ `initialDelaySeconds + failureThreshold × periodSeconds` (plus up to `timeoutSeconds` per attempt). Startup budget = `failureThreshold × periodSeconds`.

**Analogy:** `periodSeconds` = how often you check; `timeoutSeconds` = how long you wait for an answer each check; `failureThreshold` = how many missed answers before you act; `initialDelaySeconds` = the grace period after opening before you start checking.

**Common mistakes:**
- `timeoutSeconds: 1` (default!) on a slow-ish endpoint → intermittent failures under load.
- Setting `successThreshold > 1` on liveness → rejected by the API server.
- Using `initialDelaySeconds` for boot when a startup probe is the right tool.

**Best practice:** Liveness: `periodSeconds: 10, failureThreshold: 3, timeoutSeconds: 2-5`. Readiness: tighter `failureThreshold` (1–2) so bad pods drain fast. Startup: size `failureThreshold × periodSeconds` ≥ worst-case boot.

```
 detection time (liveness) ≈ failureThreshold × periodSeconds
   3 × 10s = 30s before restart (+ up to timeoutSeconds each)
```

---

### 3.6 Liveness vs Readiness — the crucial difference

| | Liveness | Readiness |
|---|----------|-----------|
| Question | "Is the process wedged?" | "Can it serve traffic now?" |
| On failure | **Restart container** | **Remove from Service endpoints** |
| Restarts pod? | Yes | **No** |
| Affects traffic? | Indirectly (via restart) | **Directly** (gates endpoints) |
| Check dependencies? | **Never** | Sometimes (critical deps only) |
| Recovers by | Restarting | Passing again → re-added |

> 🧠 **Mnemonic:** **L**iveness = **L**ife support (restart). **R**eadiness = **R**outing (traffic gate).

---

## 4. ## HANDS-ON LABS

> Setup: any cluster (kind/minikube/EKS). Verify: `kubectl version --short` and `kubectl get nodes`.

### Lab 1 — Liveness `httpGet` that fails and restarts

We use a container that serves `/healthz` healthy for 10s, then returns 500 forever — forcing a restart.

```yaml
# lab1-liveness.yaml
apiVersion: v1
kind: Pod
metadata:
  name: liveness-demo
  labels: { app: liveness-demo }
spec:
  containers:
  - name: app
    image: registry.k8s.io/e2e-test-images/agnhost:2.40
    args: ["liveness"]                 # serves /healthz: 200 for 10s then 500
    ports: [{ containerPort: 8080 }]
    livenessProbe:
      httpGet:
        path: /healthz
        port: 8080
      initialDelaySeconds: 3
      periodSeconds: 5
      failureThreshold: 1
```

```bash
kubectl apply -f lab1-liveness.yaml
kubectl get pod liveness-demo -w
```

Expected — after ~15s the container fails and restarts:

```
NAME            READY   STATUS    RESTARTS   AGE
liveness-demo   1/1     Running   0          5s
liveness-demo   1/1     Running   1 (2s ago) 20s
liveness-demo   1/1     Running   2 (1s ago) 41s
```

Confirm with events:

```bash
kubectl describe pod liveness-demo | sed -n '/Events/,$p'
```

```
Events:
  Type     Reason     Age   From     Message
  ----     ------     ----  ----     -------
  Normal   Pulled     45s   kubelet  Successfully pulled image ...
  Normal   Created    45s   kubelet  Created container app
  Normal   Started    45s   kubelet  Started container app
  Warning  Unhealthy  16s   kubelet  Liveness probe failed: HTTP probe failed with statuscode: 500
  Normal   Killing    16s   kubelet  Container app failed liveness probe, will be restarted
```

✅ **Takeaway:** Liveness failure → `Killing` → `RESTARTS` increments. Pod is NOT recreated; the *container* restarts.

---

### Lab 2 — Readiness probe removing a pod from endpoints

A Deployment behind a Service. We make one pod "not ready" by deleting its readiness file and watch it leave the Endpoints.

```yaml
# lab2-readiness.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ready-demo
spec:
  replicas: 3
  selector: { matchLabels: { app: ready-demo } }
  template:
    metadata: { labels: { app: ready-demo } }
    spec:
      containers:
      - name: app
        image: registry.k8s.io/e2e-test-images/agnhost:2.40
        args: ["netexec", "--http-port=8080"]
        ports: [{ containerPort: 8080 }]
        readinessProbe:
          exec:
            command: ["cat", "/tmp/ready"]
          initialDelaySeconds: 2
          periodSeconds: 3
          failureThreshold: 1
        lifecycle:
          postStart:
            exec: { command: ["sh","-c","touch /tmp/ready"] }  # ready at boot
---
apiVersion: v1
kind: Service
metadata: { name: ready-demo }
spec:
  selector: { app: ready-demo }
  ports: [{ port: 80, targetPort: 8080 }]
```

```bash
kubectl apply -f lab2-readiness.yaml
kubectl get pods -l app=ready-demo
kubectl get endpoints ready-demo
```

Expected — all 3 ready, 3 endpoint IPs:

```
NAME                          READY   STATUS    RESTARTS   AGE
ready-demo-7c9d8...-aaaaa     1/1     Running   0          20s
ready-demo-7c9d8...-bbbbb     1/1     Running   0          20s
ready-demo-7c9d8...-ccccc     1/1     Running   0          20s

NAME         ENDPOINTS                                   AGE
ready-demo   10.244.0.11:8080,10.244.0.12:8080,10.244.0.13:8080   20s
```

Now make one pod not ready:

```bash
POD=$(kubectl get pod -l app=ready-demo -o jsonpath='{.items[0].metadata.name}')
kubectl exec $POD -- rm /tmp/ready
kubectl get pod $POD                 # READY becomes 0/1
kubectl get endpoints ready-demo     # now only 2 IPs
```

Expected:

```
NAME                        READY   STATUS    RESTARTS   AGE
ready-demo-7c9d8...-aaaaa   0/1     Running   0          60s   <- Running but NOT Ready

NAME         ENDPOINTS                                AGE
ready-demo   10.244.0.12:8080,10.244.0.13:8080        60s    <- dropped to 2
```

Bring it back:

```bash
kubectl exec $POD -- touch /tmp/ready
kubectl get endpoints ready-demo     # back to 3 IPs, no restart
```

✅ **Takeaway:** `STATUS=Running`, `READY=0/1`, `RESTARTS=0`. Readiness only gates **endpoints**, never restarts.

---

### Lab 3 — Startup probe for a slow app

Simulate a 30s boot; startup probe absorbs it; liveness stays tight.

```yaml
# lab3-startup.yaml
apiVersion: v1
kind: Pod
metadata: { name: startup-demo }
spec:
  containers:
  - name: app
    image: busybox:1.36
    command: ["sh","-c","sleep 30; touch /tmp/started; sleep 3600"]
    startupProbe:
      exec: { command: ["cat","/tmp/started"] }
      periodSeconds: 5
      failureThreshold: 10          # budget = 5 × 10 = 50s > 30s boot ✅
    livenessProbe:
      exec: { command: ["cat","/tmp/started"] }
      periodSeconds: 10
      failureThreshold: 3           # tight, only active AFTER startup succeeds
```

```bash
kubectl apply -f lab3-startup.yaml
kubectl get pod startup-demo -w
```

Expected — stays `Running 0/1`... no restart during boot, then ready:

```
NAME           READY   STATUS    RESTARTS   AGE
startup-demo   0/1     Running   0          5s
startup-demo   0/1     Running   0          25s     <- liveness NOT firing (startup gating)
startup-demo   1/1     Running   0          35s     <- startup succeeded, liveness took over
```

Now shrink the budget to prove the failure mode (edit `failureThreshold: 3` → budget 15s < 30s boot) and reapply: the container restarts before booting → CrashLoopBackOff. Restore it after observing.

✅ **Takeaway:** During startup, liveness is disabled. Budget must exceed worst-case boot.

---

### Lab 4 — `exec` probe (and a `tcpSocket` bonus)

```yaml
# lab4-exec.yaml
apiVersion: v1
kind: Pod
metadata: { name: exec-demo }
spec:
  containers:
  - name: app
    image: busybox:1.36
    args: ["/bin/sh","-c","touch /tmp/healthy; sleep 30; rm /tmp/healthy; sleep 600"]
    livenessProbe:
      exec: { command: ["cat","/tmp/healthy"] }   # exit 0 while file exists
      initialDelaySeconds: 5
      periodSeconds: 5
      failureThreshold: 1
```

```bash
kubectl apply -f lab4-exec.yaml
kubectl get pod exec-demo -w
```

Expected — healthy 30s, then file removed → liveness fails (exit 1) → restart:

```
NAME        READY   STATUS    RESTARTS   AGE
exec-demo   1/1     Running   0          10s
exec-demo   1/1     Running   1 (1s ago) 38s
```

```bash
kubectl describe pod exec-demo | grep -A3 Unhealthy
# Warning  Unhealthy  ...  Liveness probe failed: cat: can't open '/tmp/healthy': No such file or directory
```

**tcpSocket bonus** — readiness on a raw port (e.g., redis):

```yaml
    readinessProbe:
      tcpSocket: { port: 6379 }
      periodSeconds: 5
```

✅ **Takeaway:** `exec` = exit code 0 passes; the failure message is the command's stderr. `tcpSocket` only proves the port accepts connections.

---

## 5. ## EXERCISES

1. **Restart tuning:** Modify Lab 1 so the container tolerates 3 consecutive failures before restarting and probes every 10s. Compute the worst-case detection time. *(Answer: ~30s + delay.)*
2. **Readiness via HTTP:** Replace Lab 2's `exec` readiness with an `httpGet` to `/healthz` on port 8080. Verify endpoints still drain when you scale the deployment to 0 and back.
3. **Startup budget math:** A JVM app boots in 90s worst case. Design a startup probe (`periodSeconds`, `failureThreshold`) with a 30s safety margin, plus a tight liveness probe. Write the YAML.
4. **gRPC probe:** Add a `grpc` liveness probe to a hypothetical gRPC pod on port 50051. Write the probe stanza (`grpc: { port: 50051 }`).
5. **Rolling-update guard:** In a 4-replica Deployment with `maxUnavailable: 0`, explain (in writing) why a *correct readiness probe* is required for a zero-downtime rollout, and what breaks without it.

---

## 6. ## TROUBLESHOOTING SECTION

### 🔥 6.1 CrashLoopBackOff from aggressive liveness
- **Symptoms:** `RESTARTS` climbing fast, `STATUS=CrashLoopBackOff`, app logs show it was killed mid-startup.
- **Root cause:** `initialDelaySeconds` too small or `failureThreshold`/`timeoutSeconds` too tight — liveness fires before the app finishes booting or during a normal GC pause.
- **Diagnosis:**
  ```bash
  kubectl describe pod <pod> | grep -A2 Unhealthy   # "Liveness probe failed"
  kubectl logs <pod> --previous                     # app was still booting
  ```
- **Resolution:** Add a **startup probe** sized to worst-case boot; loosen liveness (`failureThreshold: 3`, `timeoutSeconds: 3-5`). Don't paper over with a huge `initialDelaySeconds`.

### 🔥 6.2 Traffic to a not-ready pod (missing readiness)
- **Symptoms:** Intermittent 502/connection-refused during deploys; clients hit pods that just started.
- **Root cause:** No readiness probe → pod IP added to endpoints the moment the container starts, before the server binds the port / warms caches.
- **Diagnosis:**
  ```bash
  kubectl get endpoints <svc>      # contains IPs of pods that aren't serving yet
  kubectl get pods                 # READY 1/1 immediately even though app isn't up
  ```
- **Resolution:** Add a readiness probe hitting a real "ready" endpoint that only returns 200 once the server is actually serving and warm.

### 🔥 6.3 Slow app killed before startup completes
- **Symptoms:** Pod restarts at a fixed interval, never reaching Ready; logs cut off mid-migration.
- **Root cause:** No startup probe and liveness/`initialDelaySeconds` shorter than boot time; or startup budget (`failureThreshold × periodSeconds`) < boot time.
- **Diagnosis:**
  ```bash
  kubectl describe pod <pod> | grep -E "Startup|Liveness|Killing"
  # Startup probe failed... Container will be killed
  ```
- **Resolution:** Increase startup `failureThreshold × periodSeconds` to exceed worst-case boot with margin.

### 🔥 6.4 Probe path/port wrong
- **Symptoms:** Pod never Ready (readiness) or restart loop (liveness) even though the app is healthy when you `curl` it manually.
- **Root cause:** Probe `port` doesn't match `containerPort`, wrong `path`, wrong `scheme` (HTTP vs HTTPS), or app binds `127.0.0.1` instead of `0.0.0.0` (kubelet probes the pod IP, not localhost).
- **Diagnosis:**
  ```bash
  kubectl describe pod <pod> | grep -A2 Unhealthy   # "connection refused" / "404"
  kubectl exec <pod> -- wget -qO- 127.0.0.1:8080/healthz   # works locally?
  ```
- **Resolution:** Match `port`/`path`/`scheme` to the actual server; bind to `0.0.0.0`; set `scheme: HTTPS` if TLS. For HTTPS, `httpGet.scheme: HTTPS`.

---

## 7. ## QUIZ SECTION

**MCQ 1.** A readiness probe fails its threshold. What happens?
A) Container restarts  B) Pod is deleted  C) Pod IP removed from Service endpoints  D) Node is cordoned

**MCQ 2.** While a **startup** probe is running, which probes are active?
A) Liveness only  B) Readiness only  C) Both liveness and readiness  D) Neither

**MCQ 3.** Which value is **invalid** for a liveness probe?
A) `failureThreshold: 5`  B) `successThreshold: 2`  C) `periodSeconds: 30`  D) `timeoutSeconds: 4`

**Short 1.** In one sentence, why should a liveness probe never check an external database?

**Short 2.** Give the formula for the worst-case time before a liveness failure triggers a restart.

**Scenario.** A Deployment of 3 replicas does a rolling update with `maxUnavailable: 0, maxSurge: 1`. The app takes 25s to warm up but has *no readiness probe*. Describe what users experience and how to fix it.

---

### ✅ Answers
- **MCQ 1: C.** Readiness gates endpoints; it never restarts.
- **MCQ 2: D.** Startup disables both until it succeeds once.
- **MCQ 3: B.** `successThreshold` must be `1` for liveness (and startup).
- **Short 1:** Because a DB blip would fail every pod's liveness simultaneously, restarting the entire fleet and turning a dependency hiccup into a self-inflicted outage.
- **Short 2:** `initialDelaySeconds + (failureThreshold × periodSeconds)` (plus up to `timeoutSeconds` per attempt).
- **Scenario:** Each new pod is added to endpoints instantly and receives traffic during its 25s warmup → users get 502s/timeouts/slow responses. Worse, with `maxUnavailable: 0` Kubernetes thinks the rollout is healthy because pods report Ready immediately. **Fix:** add a readiness probe that only passes after warmup; now the new pod stays out of endpoints until ready, and the rollout waits for it → true zero-downtime.

---

## 8. ## CHALLENGE PROJECT

**Goal:** Take a slow-booting web API and make it production-ready with all three probes correctly tuned.

**Requirements:**
- App boots in up to 60s (migrations + cache warm).
- Detect hangs within ~30s after boot.
- Zero traffic to a pod until it can actually serve.
- Survive a 5s dependency blip without restarting.
- Rolling update with `maxUnavailable: 0`.

Try it yourself before peeking. ⤵️

<details>
<summary>📦 Reference solution</summary>

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: prod-api }
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate: { maxUnavailable: 0, maxSurge: 1 }
  selector: { matchLabels: { app: prod-api } }
  template:
    metadata: { labels: { app: prod-api } }
    spec:
      containers:
      - name: api
        image: my-registry/prod-api:1.0.0
        ports: [{ containerPort: 8080 }]

        # Absorbs the 60s boot; budget = 5 × 18 = 90s (60s + margin)
        startupProbe:
          httpGet: { path: /healthz, port: 8080 }
          periodSeconds: 5
          failureThreshold: 18
          timeoutSeconds: 3

        # Tight hang detection post-boot; ~30s worst case; cheap & dependency-free
        livenessProbe:
          httpGet: { path: /healthz, port: 8080 }
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 3
          successThreshold: 1

        # Gates traffic; checks critical deps; drains fast (10s) on trouble
        readinessProbe:
          httpGet: { path: /ready, port: 8080 }
          periodSeconds: 5
          failureThreshold: 2
          timeoutSeconds: 3
          successThreshold: 1
---
apiVersion: v1
kind: Service
metadata: { name: prod-api }
spec:
  selector: { app: prod-api }
  ports: [{ port: 80, targetPort: 8080 }]
```

**Why it works:**
- `/healthz` (liveness+startup) is local-only → a 5s dependency blip does NOT trigger restarts.
- `/ready` (readiness) checks critical deps → a struggling pod drains within ~10s but isn't killed.
- Startup budget (90s) > boot (60s) → no premature kill.
- `maxUnavailable: 0` + readiness → rollout waits for each new pod to be Ready before retiring an old one → zero downtime.
</details>

---

## 9. ## KNOWLEDGE CHECK

You're ready for Day 7 if you can, without looking:
- [ ] State what each probe does **on failure** (restart / remove from endpoints / kill during boot).
- [ ] Name all four handlers and a use case for each.
- [ ] List all five timing parameters and their defaults.
- [ ] Explain why `successThreshold` must be 1 for liveness.
- [ ] Describe how readiness drives Service endpoints during a rolling update.
- [ ] Diagnose CrashLoopBackOff caused by an aggressive liveness probe.
- [ ] Size a startup probe budget for a 90s-boot app.

---

## 10. ## CHEAT SHEET

```yaml
# Full probe template (all params shown)
livenessProbe:                 # restart on failure
  httpGet: { path: /healthz, port: 8080, scheme: HTTP }
  initialDelaySeconds: 0
  periodSeconds: 10
  timeoutSeconds: 1
  successThreshold: 1          # MUST be 1 for liveness & startup
  failureThreshold: 3
readinessProbe:                # remove from endpoints on failure
  tcpSocket: { port: 8080 }
  periodSeconds: 5
  failureThreshold: 2
startupProbe:                  # disables liveness+readiness until first success
  exec: { command: ["cat","/tmp/started"] }
  periodSeconds: 5
  failureThreshold: 18         # budget = period × threshold
grpcProbe_example:
  grpc: { port: 50051 }        # gRPC Health Checking Protocol
```

```bash
# Commands
kubectl describe pod <pod> | sed -n '/Events/,$p'      # see probe events
kubectl describe pod <pod> | grep -A2 Unhealthy        # failure reason
kubectl get pod <pod>                                  # READY col = readiness
kubectl get endpoints <svc>                            # who gets traffic
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].restartCount}'
kubectl logs <pod> --previous                          # logs from killed container
```

| Probe | Failure action | Restarts? | Gates traffic? | Check deps? |
|-------|----------------|-----------|----------------|-------------|
| Liveness | Restart container | ✅ | No | ❌ Never |
| Readiness | Remove from endpoints | ❌ | ✅ | Sometimes |
| Startup | Kill container | ✅ | No (disables others) | ❌ |

---

## 11. ## INTERVIEW PREPARATION

**How to answer the classic "liveness vs readiness" question:** Lead with the *failure action* (restart vs remove-from-endpoints), then the *purpose* (recover a wedged process vs gate traffic), then the *cardinal rule* (liveness must be dependency-free; readiness may check critical deps). Mention startup as the modern fix for slow boots.

**Talking points that signal seniority:**
- "A pod can be `Running` but not `Ready` — and gets zero Service traffic."
- "Startup probes replaced the `initialDelaySeconds` hack; they disable liveness/readiness until the first success."
- "I never point liveness at a database — that turns a dependency blip into a fleet-wide restart storm."
- "With `maxUnavailable: 0`, a correct readiness probe is what makes rollouts truly zero-downtime."
- "EndpointSlices, not the pod, are the source of truth for what receives traffic."

---

## 12. ## 🎓 TOP 50 QUESTIONS

### Fundamentals (15)
1. What problem do health probes solve that crash-detection alone doesn't?
2. What are the three probe types in Kubernetes?
3. What does a liveness probe do on failure?
4. What does a readiness probe do on failure?
5. What does a startup probe do, and what does it disable?
6. Name the four probe handlers.
7. What HTTP status range counts as a successful `httpGet` probe?
8. What does `exec` consider a success?
9. What does `tcpSocket` actually verify?
10. List the five timing parameters and their defaults.
11. Why must `successThreshold` be 1 for liveness?
12. What's the difference between `periodSeconds` and `timeoutSeconds`?
13. Does a readiness failure increment the `RESTARTS` counter?
14. Can a pod be `Running` and not `Ready` at the same time?
15. What is the gRPC probe and which standard does it use?

### Practical (10)
16. Write a liveness `httpGet` probe with a 30s detection window.
17. Write a readiness `exec` probe checking `/tmp/ready`.
18. Write a startup probe with a 90s boot budget.
19. How do you set the `Host` header in an HTTP probe?
20. How do you probe an HTTPS endpoint?
21. Which `kubectl` command shows probe failure events?
22. How do you read logs from a container that was just killed by liveness?
23. How do you check which pods a Service is currently routing to?
24. How do you make a liveness probe more tolerant of GC pauses?
25. Write a `tcpSocket` readiness probe for Redis on 6379.

### Scenario (10)
26. App boots in 2 minutes — which probe and how do you size it?
27. Your readiness probe checks the DB and the DB hiccups — what happens to traffic, and is this good?
28. You see 502s only during deploys — likely cause?
29. `maxUnavailable: 0` rollout finishes but users get errors — why?
30. A pod shows `READY 0/1` but `RESTARTS 0` — what's happening?
31. CPU-starved node makes probes time out — what's the blast radius and fix?
32. You want a pod to gracefully drain before shutdown — how do probes help?
33. A liveness probe on `/` returns 200 even when the app is wedged — why might that be wrong?
34. Two containers in one pod — one is unhealthy. What restarts?
35. You add a readiness probe and now rollouts are slower — explain the trade-off.

### Troubleshooting (10)
36. CrashLoopBackOff right after adding a liveness probe — first thing you check?
37. App is healthy via `curl` inside the container but probe fails — top suspects?
38. Probe says "connection refused" — what does that usually mean?
39. Probe says "404" — what's wrong?
40. Endpoints list a pod IP but requests fail — diagnose.
41. Startup probe keeps failing on a slow app — fix?
42. Liveness restarts spike when traffic spikes — why and fix?
43. `kubectl describe` shows "Liveness probe failed: HTTP probe failed with statuscode: 500" — interpretation?
44. Pod never becomes Ready, app binds 127.0.0.1 — what's the issue?
45. Probe `timeoutSeconds: 1` causing flapping — remedy?

### Interview (5)
46. Explain liveness vs readiness to a junior engineer in 30 seconds.
47. When would you NOT add a liveness probe at all?
48. How do probes enable zero-downtime deployments?
49. Why is pointing liveness at a dependency an anti-pattern? Give the failure scenario.
50. Walk through exactly what the kubelet does when a startup probe finally succeeds.

<details>
<summary>Answer keys (condensed)</summary>

1. Silent hangs/deadlocks where process runs but can't serve. 2. Liveness, readiness, startup. 3. Restarts the container. 4. Removes pod IP from Service endpoints. 5. Probes boot health; disables liveness+readiness until first success. 6. httpGet, tcpSocket, exec, grpc. 7. 200–399. 8. Exit code 0. 9. That a TCP connection can be opened (port accepting). 10. initialDelay(0), period(10), timeout(1), successThreshold(1), failureThreshold(3). 11. Restart/boot are binary — no value in requiring repeated successes; API rejects >1. 12. period = interval between probes; timeout = max wait per attempt. 13. No. 14. Yes. 15. gRPC Health Checking Protocol (`grpc.health.v1.Health/Check`), expects `SERVING`.
16. `httpGet:{path:/healthz,port:8080}; periodSeconds:10; failureThreshold:3`. 17. `exec:{command:["cat","/tmp/ready"]}`. 18. `periodSeconds:5; failureThreshold:18`. 19. `httpHeaders:[{name:Host,value:example.com}]`. 20. `httpGet.scheme: HTTPS`. 21. `kubectl describe pod` (Events) or `grep Unhealthy`. 22. `kubectl logs <pod> --previous`. 23. `kubectl get endpoints <svc>` / `get endpointslices`. 24. Increase `timeoutSeconds` and `failureThreshold`. 25. `tcpSocket:{port:6379}`.
26. Startup probe; `failureThreshold × periodSeconds` > 120s with margin. 27. All pods drop from endpoints → outage; bad unless DB is truly required for *every* request. 28. Missing/incorrect readiness; new pods get traffic before serving. 29. Pods report Ready too early (no real readiness gate). 30. Readiness failing — Running, in ReplicaSet, out of endpoints, not restarted. 31. Probes time out fleet-wide → mass restarts/endpoint drops; raise timeout/threshold, fix resource requests. 32. `preStop` + readiness failing drains traffic before SIGTERM. 33. `/` may not exercise the wedged code path; use a real health endpoint. 34. Only the unhealthy container restarts. 35. Rollout waits for readiness → safer but slower; correct trade-off.
36. `initialDelaySeconds`/startup budget vs boot time. 37. Wrong port/path/scheme, app on 127.0.0.1, probe hits pod IP. 38. Nothing listening on that port (not bound / wrong port). 39. Wrong path. 40. App not actually serving on targetPort, or bound to localhost. 41. Increase budget. 42. Probes time out under load; raise timeout/threshold, fix CPU requests. 43. App returned 500 → liveness failed → restart. 44. kubelet probes pod IP, not localhost — bind 0.0.0.0. 45. Raise `timeoutSeconds`.
46. "Liveness = restart if wedged; readiness = stop sending traffic until it can serve." 47. When the process reliably crashes on failure (exit) — restart already handled; a bad liveness only adds risk. 48. Readiness keeps new pods out of endpoints until serving; rollout waits → no error window. 49. A dependency blip fails liveness on every pod at once → fleet restart storm → outage amplification. 50. Startup never runs again; kubelet activates liveness and readiness; pod becomes eligible for endpoints once readiness passes.
</details>

---

## 13. ## FREE RESOURCES

| Resource | Type | Link |
|----------|------|------|
| K8s Docs — Configure Liveness/Readiness/Startup Probes | Official | kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes |
| K8s Docs — Pod Lifecycle (probes section) | Official | kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle |
| API Reference — `Probe` v1 | Official | kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#lifecycle |
| gRPC health probes | Official | kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#define-a-grpc-liveness-probe |
| agnhost test image (used in labs) | Tool | github.com/kubernetes/kubernetes/tree/master/test/images/agnhost |
| "Kubernetes Probes" — Learnk8s blog | Deep dive | learnk8s.io |

**Docs reading plan (45 min):**
1. Pod Lifecycle → "Container probes" (10 min) — concepts.
2. Configure Probes task page (20 min) — copy/run the examples.
3. Probe v1 API reference (15 min) — every field & default.

**Must-Read:** "Configure Liveness, Readiness and Startup Probes" task page.
**Must-Watch:** Any conference talk titled "Kubernetes Probes done right".
**Must-Do:** Labs 1–3 above, then the Challenge Project.

**Highest-ROI:** The single task page + Labs 1 and 2. They cover the 80% you'll use daily: readiness gating traffic and liveness restarting hangs.

---

## 14. ## NEXT STEPS

**Active recall (do this now, no notes):**
1. From memory, write the failure action of each of the three probes.
2. Recite the five timing parameters and their defaults.
3. Write — without looking — a Deployment with all three probes for a 60s-boot app.
4. Explain to a rubber duck why liveness must never check a database.
5. Run Lab 2 again and confirm endpoints change without restarts.

If you nailed all five, you've mastered Day 6.

➡️ **Continue to Day 7.**
