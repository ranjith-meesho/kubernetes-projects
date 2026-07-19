# Hour 11: Health Checks, Liveness Probes, Readiness Probes, Startup Probes

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine a call center with employees (containers). The manager (kubelet) needs to answer three questions about each employee, constantly:

1. **Liveness — "Is this employee conscious?"** If an employee has collapsed at their desk (deadlocked, frozen, unresponsive) and isn't going to recover on their own, the manager doesn't wait around — they **replace the employee** (restart the container).
2. **Readiness — "Is this employee ready to take a customer call *right now*?"** Maybe they're conscious but stepped away to grab water, or they're temporarily overwhelmed. The manager doesn't fire them — they just **stop routing calls to that desk** until the employee waves and says "I'm back." (Pod is removed from Service endpoints, but NOT restarted.)
3. **Startup — "This is a new hire's first day — give them extra onboarding time."** New employees might legitimately take longer to get set up (loading manuals, logging into systems) before they can be judged on liveness/readiness at all. Without this grace period, an impatient manager might fire a new hire on day one just because they hadn't finished onboarding yet.

**Technical version:**

Kubernetes doesn't know if your application code inside a container is actually healthy — it only knows if the *process* is running. A container can be running (PID exists) while the app inside is deadlocked, stuck in an infinite loop, or unable to serve requests. **Probes** let you tell the kubelet how to actually check application health.

- **Liveness Probe** — Answers "should this container be restarted?" If it fails `failureThreshold` times in a row, the **kubelet kills and restarts the container** (subject to the pod's `restartPolicy`). Use this to recover from unrecoverable states (deadlocks, memory leaks that hang the app).
- **Readiness Probe** — Answers "should this pod receive traffic?" If it fails, the pod's IP is **removed from the Endpoints/EndpointSlice objects** backing any Service that selects it. The container keeps running — nothing is restarted. Once it passes again, it's added back. Use this for temporary unavailability (warming caches, waiting on a dependency, graceful shutdown draining).
- **Startup Probe** — Runs first, before liveness/readiness probes are allowed to execute. While the startup probe hasn't yet succeeded, liveness and readiness checks are **disabled** (or rather, kubelet doesn't act on them / holds off). This gives slow-starting apps room to boot without being killed prematurely by an impatient liveness probe. Once the startup probe succeeds once, it's done forever for that container's lifetime — liveness/readiness take over.

**Probe mechanisms** (how the check is actually performed):

| Mechanism | How it works | Example use |
|---|---|---|
| `httpGet` | kubelet sends an HTTP GET to a path/port inside the container; any response in `200-399` is success | REST APIs, web servers exposing `/healthz` |
| `tcpSocket` | kubelet attempts to open a TCP connection to a port; connection success = healthy | Databases, TCP servers without an HTTP layer |
| `exec` | kubelet runs a command inside the container; exit code `0` = success | Custom health scripts, CLI tools with a health subcommand |
| `grpc` (newer, stable in 1.27+) | kubelet calls the standard gRPC health-checking protocol | gRPC microservices |

**Key tuning fields** (apply to all three probe types):

| Field | Meaning | Default |
|---|---|---|
| `initialDelaySeconds` | Wait this long after container start before the *first* probe | 0 |
| `periodSeconds` | How often to run the probe | 10 |
| `timeoutSeconds` | How long to wait for a probe response before counting it as failed | 1 |
| `failureThreshold` | Consecutive failures needed to mark Unhealthy (triggers restart for liveness, removal from endpoints for readiness) | 3 |
| `successThreshold` | Consecutive successes needed to mark Healthy again (must be 1 for liveness/startup) | 1 |

For a **startup probe**, `failureThreshold × periodSeconds` effectively becomes your maximum allowed boot time — e.g. `failureThreshold: 30` and `periodSeconds: 10` gives the app up to 300 seconds to start before it's considered failed and the container is killed.

## 2. Diagram

```
Container Lifecycle with All Three Probes
──────────────────────────────────────────

  Container starts
        │
        ▼
 ┌─────────────────────┐
 │   STARTUP PROBE      │   Liveness & Readiness probes are PAUSED
 │  (runs repeatedly     │   while startup probe has not yet succeeded
 │   until success or    │
 │   failureThreshold)   │
 └──────────┬───────────┘
            │ succeeds once
            ▼
 ┌─────────────────────────────────────────────┐
 │        LIVENESS & READINESS now active        │
 │        (run continuously, in parallel)        │
 └──────────────┬────────────────┬──────────────┘
                │                │
     ┌──────────▼───────┐  ┌─────▼──────────────┐
     │  LIVENESS PROBE   │  │  READINESS PROBE    │
     │  "Is it alive?"   │  │  "Is it ready?"      │
     └──────────┬───────┘  └─────┬──────────────┘
                │                │
        fails N times        fails N times
                │                │
                ▼                ▼
     ┌─────────────────┐  ┌──────────────────────┐
     │ kubelet KILLS &   │  │ Pod IP REMOVED from   │
     │ RESTARTS container│  │ Service Endpoints /   │
     │ (RESTARTS++ in    │  │ EndpointSlice          │
     │  kubectl get pods)│  │ (Pod stays Running,    │
     │                   │  │  just gets no traffic) │
     └─────────────────┘  └──────────────────────┘

Startup too slow (no startup probe defined) + slow app + tight liveness delay:
   Container starts → app still booting → liveness probe fires too early →
   fails → kubelet restarts → app never finishes booting → CrashLoopBackOff
```

## 3. Real-World Example

**Scenario A — Slow cache warm-up app:** A catalog service loads a 2GB in-memory product cache on startup, taking ~60 seconds before it can respond to any request. The team originally configured only a liveness probe with `initialDelaySeconds: 10`. Result: liveness starts probing at 10s, the app isn't ready yet, probe fails repeatedly, kubelet kills the container at ~30s (before cache load finishes), and it restarts — forever. This is a classic **CrashLoopBackOff caused by a missing startup probe**. The fix: add a `startupProbe` with `failureThreshold: 12, periodSeconds: 5` (60s budget) hitting the same health endpoint. Liveness/readiness are held off until the cache finishes loading and the startup probe passes once.

**Scenario B — Database connection blip:** An order-service pod's readiness probe hits `/ready`, which internally pings its Postgres connection pool. The database has a brief failover (a few seconds) during a maintenance event. The readiness probe fails for that window — Kubernetes correctly pulls the pod out of the Service's Endpoints so no user traffic is routed to a pod that can't reach the DB. Crucially, the **liveness probe is separate** and only checks "is the HTTP server thread responding," so it keeps passing — the pod is never restarted, no traffic is dropped to a broken pod, and once the DB failover completes, readiness passes again and traffic resumes automatically. This is exactly why liveness and readiness should check *different* things.

## 4. Hands-On Lab

**Goal:** Deploy a pod with liveness + readiness httpGet probes, break readiness (observe removal from Service endpoints, no restart), then break liveness (observe restarts).

We'll use `nginx` with a toggle-able file trick isn't ideal for httpGet, so instead we use the well-known `agnhost` test image, which exposes controllable health endpoints.

```bash
# 1. Create the pod with liveness + readiness probes
cat <<'EOF' > probe-pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: probe-demo
  labels:
    app: probe-demo
spec:
  containers:
  - name: agnhost
    image: registry.k8s.io/e2e-test-images/agnhost:2.40
    args:
    - liveness
    ports:
    - containerPort: 8080
    livenessProbe:
      httpGet:
        path: /healthz
        port: 8080
      initialDelaySeconds: 5
      periodSeconds: 5
      failureThreshold: 3
    readinessProbe:
      httpGet:
        path: /healthz
        port: 8080
      initialDelaySeconds: 2
      periodSeconds: 5
      failureThreshold: 2
EOF

kubectl apply -f probe-pod.yaml

# 2. Expose it via a Service so we can watch endpoints
kubectl expose pod probe-demo --port=80 --target-port=8080 --name=probe-demo-svc

kubectl get pods -w
```

**Expected initial state:**
```
NAME          READY   STATUS    RESTARTS   AGE
probe-demo    1/1     Running   0          15s
```
```
kubectl get endpoints probe-demo-svc
NAME              ENDPOINTS           AGE
probe-demo-svc    10.244.0.7:8080     30s
```

**Step 3 — Break readiness only** (agnhost's `liveness` mode returns 500 for `/healthz` after 10s automatically in some versions; alternatively, exec into it to simulate — for a controlled demo, use a simple busybox app instead):

```bash
# Simpler controlled alternative using busybox + a flag file:
kubectl exec probe-demo -- rm -f /tmp/healthy   # if using the standard k8s liveness-exec example image
```

After the readiness probe's `failureThreshold` (2 × periodSeconds 5s = ~10s) is exceeded:
```
kubectl get pods
NAME          READY   STATUS    RESTARTS   AGE
probe-demo    0/1     Running   0          45s     <-- READY flips to 0/1, STATUS stays Running

kubectl get endpoints probe-demo-svc
NAME              ENDPOINTS   AGE
probe-demo-svc                55s              <-- endpoint removed, no traffic routed

kubectl describe pod probe-demo
...
Events:
  Type     Reason     Age   From     Message
  ----     ------     ----  ----     -------
  Warning  Unhealthy  10s   kubelet  Readiness probe failed: HTTP probe failed with statuscode: 500
```

Notice: **RESTARTS is still 0** — the container was never killed, it was just pulled out of load balancing. This is the readiness contract.

**Step 4 — Now break liveness too** (restore health so readiness passes again, then break the liveness path specifically, or wait for the same failure to also breach liveness's `failureThreshold: 3`, ~15s):

```
kubectl get pods -w
NAME          READY   STATUS             RESTARTS      AGE
probe-demo    0/1     Running            0             70s
probe-demo    0/1     Running            1 (5s ago)    85s   <-- kubelet restarted the container
probe-demo    1/1     Running            1 (5s ago)    95s   <-- back to healthy after restart

kubectl describe pod probe-demo
...
Events:
  Type     Reason     Age   From     Message
  ----     ------     ----  ----     -------
  Warning  Unhealthy  20s   kubelet  Liveness probe failed: HTTP probe failed with statuscode: 500
  Normal   Killing    20s   kubelet  Container agnhost failed liveness probe, will be restarted
  Normal   Pulled     19s   kubelet  Container image already present on machine
  Normal   Created    19s   kubelet  Created container agnhost
  Normal   Started    19s   kubelet  Started container agnhost
```

**Key observation:** the **RESTARTS column increments** and you see `Killing` + `Started` events — this is the liveness-triggered restart, fundamentally different from the readiness case where the pod just silently drops out of `kubectl get endpoints`.

```bash
# Cleanup
kubectl delete pod probe-demo
kubectl delete service probe-demo-svc
```

## 5. Common Mistakes

1. **Using the exact same endpoint/logic for liveness and readiness.** If `/healthz` checks downstream dependencies (DB, cache, third-party API) and you point *both* probes at it, a slow dependency causes the pod to fail liveness too — kubelet restarts a perfectly healthy container process, which does nothing to fix the slow dependency and can cause a **cascading restart storm** across all replicas simultaneously.
2. **Too-aggressive `failureThreshold`/`periodSeconds` causing flapping.** E.g. `periodSeconds: 2, failureThreshold: 1` means a single slow GC pause or one dropped packet triggers a restart or an endpoint removal. This creates "flapping" — pods rapidly bouncing in and out of Service endpoints or restarting under normal transient load, which is worse than no probe at all.
3. **Missing a startup probe for slow-booting apps.** Apps that load large caches, run DB migrations, or do JIT warm-up on startup need time before liveness should even begin checking. Without a `startupProbe` (or a very generous `initialDelaySeconds` on liveness, which is a worse workaround), you get **CrashLoopBackOff** — the app is killed right as it's about to become healthy, restarts, and never gets there.
4. **A liveness probe that depends on external services.** If liveness checks "can I reach the database," a database blip or network partition causes *every* pod's liveness to fail at once, and kubelet restarts all of them simultaneously — right when you need stability the most. External dependency checks belong in **readiness**, not liveness.
5. **Forgetting `timeoutSeconds` under load.** Default `timeoutSeconds: 1` can be too short for an app under heavy CPU load — the app is fine but the probe response is delayed past 1s, counted as a failure, contributing to unwanted restarts.
6. **Setting `successThreshold` on liveness/startup to anything other than 1.** Kubernetes disallows this (must be 1) but people sometimes try to use it defensively — resulting in configuration errors deployed to a cluster.

## 6. Interview Questions (with brief answers)

1. **What's the difference between a liveness probe and a readiness probe?** — Liveness answers "should this container be restarted?" — on failure, kubelet kills and restarts the container. Readiness answers "should this pod receive traffic?" — on failure, the pod is removed from the Service's Endpoints/EndpointSlice but is left running untouched. Liveness fixes unrecoverable hangs; readiness handles temporary unavailability without punishing the container.
2. **Why use a startup probe instead of just increasing `initialDelaySeconds` on the liveness probe?** — `initialDelaySeconds` is a fixed, one-size-fits-all wait that either wastes time for fast starts or is too short for occasional slow starts (e.g. cold cache vs warm cache). A `startupProbe` actively polls until the app is actually ready, up to `failureThreshold × periodSeconds`, and once satisfied, hands off cleanly to liveness/readiness — giving accurate, adaptive timing instead of a guess.
3. **What happens if you don't define a readiness probe at all?** — Kubernetes considers the pod ready as soon as the container starts (or immediately, in older versions once running), so traffic can be routed to it before the app inside is actually able to handle requests — causing failed requests during rollout or startup.
4. **What are the three probe mechanisms Kubernetes supports, and when would you use `exec` over `httpGet`?** — `httpGet`, `tcpSocket`, and `exec` (plus `grpc`). Use `exec` when the app has no HTTP endpoint to check, e.g. a CLI-only process, a legacy app, or when health requires running a custom script (checking a lock file, a queue depth, disk space) that isn't exposed over the network.
5. **A pod keeps restarting with `CrashLoopBackOff` right around the time it should be finishing startup. What would you check first?** — Check `kubectl describe pod` events for `Liveness probe failed` messages and compare the failure timing against the app's actual startup time; if the app legitimately needs longer, add or extend a `startupProbe` rather than tweaking liveness `initialDelaySeconds`. Also verify the probe endpoint itself doesn't depend on something not ready yet (e.g. DB migration not finished).

## 7. Quiz (50 Questions)

**True/False:**
1. A failed liveness probe causes the kubelet to restart the container. (T)
2. A failed readiness probe causes the pod to be deleted. (F)
3. A failed readiness probe removes the pod from Service Endpoints. (T)
4. Startup probes run continuously for the entire lifetime of the container. (F)
5. While a startup probe hasn't succeeded, liveness and readiness probes are not acted upon. (T)
6. `httpGet` probes consider any HTTP status code from 200-399 a success. (T)
7. `exec` probes consider exit code 0 a success. (T)
8. `successThreshold` for a liveness probe must be 1. (T)
9. The default `periodSeconds` for a probe is 10. (T)
10. A container that fails its liveness probe is still counted as "Ready" until it restarts. (F)

**Multiple Choice:**
11. Which probe type removes a pod from load balancing without restarting it? a) Liveness b) Readiness c) Startup d) None → (b)
12. Which field controls how many consecutive failures are needed before a probe is considered failed? a) periodSeconds b) timeoutSeconds c) failureThreshold d) successThreshold → (c)
13. What's the primary purpose of a startup probe? a) Check DB connectivity b) Delay liveness/readiness checks until slow-starting apps are ready c) Replace liveness entirely d) Scale replicas → (b)
14. Which mechanism opens a raw connection to a port to check health? a) httpGet b) exec c) tcpSocket d) grpc → (c)
15. What does the RESTARTS column in `kubectl get pods` increment on? a) Readiness failure b) Liveness-triggered restart c) Service deletion d) Node reboot only → (b)

**Short Answer:**
16. In one sentence, explain why liveness and readiness should usually check different things.
17. What object is updated when a pod fails its readiness probe?
18. What command shows probe-related events for a pod?
19. Why might a startup probe be necessary even if the app "usually" starts fast?
20. What's the effective maximum startup time formula using `failureThreshold` and `periodSeconds`?
21. What happens to in-flight requests to a pod that just failed readiness?
22. Why is `timeoutSeconds` important under CPU load?
23. Name the four probe mechanisms available in modern Kubernetes.
24. Why shouldn't a liveness probe depend on an external database?
25. What's the risk of setting `failureThreshold: 1` with a very short `periodSeconds`?

**Scenario-Based:**
26. Your app takes 90 seconds to load ML models on startup. What probe configuration would you add?
27. A dependency (Redis) goes down for 30 seconds across your whole fleet. Your liveness probe checks Redis connectivity. What happens to your deployment, and how would you fix the probe design?
28. QA reports that during rolling updates, users briefly see 502 errors from new pods. What probe is likely missing or misconfigured?
29. A pod shows `READY 0/1` but `STATUS Running` and `RESTARTS 0`. What's the most likely explanation?
30. A pod shows increasing RESTARTS and events say "Liveness probe failed." What are two possible root causes (one app-side, one probe-config-side)?

**Fill in the Blank:**
31. A failed liveness probe results in the kubelet _______ the container.
32. A failed readiness probe results in the pod being removed from _______.
33. The probe that delays other probes during slow startup is called the _______ probe.
34. The field that sets how often a probe runs is _______.
35. The field that sets how many consecutive successes are needed to mark a probe healthy again is _______.

**Conceptual Deep-Dive:**
36. Why does Kubernetes need application-level health checks when it can already see that a process/PID is running?
37. Explain the "employee" analogy for liveness vs readiness vs startup in your own words.
38. Why must `successThreshold` be 1 for liveness and startup probes but can be >1 for readiness?
39. How does a startup probe prevent a "restart loop" (CrashLoopBackOff) for slow-booting apps?
40. Why is it good practice to expose separate `/healthz` and `/ready` endpoints instead of one shared endpoint?

**Command Practice:**
41. Write a `livenessProbe` YAML snippet using `httpGet` on path `/healthz` port `8080`.
42. Write a `readinessProbe` YAML snippet using `tcpSocket` on port `5432`.
43. What `kubectl` command shows the current Endpoints backing a Service?
44. What `kubectl` command shows recent probe failure events for a specific pod?
45. Write an `exec`-based liveness probe that checks for the existence of `/tmp/healthy`.

**Reflection:**
46. Which probe type do you think is most commonly misconfigured in real production systems, and why?
47. What part of the liveness vs readiness distinction was most confusing to you before this lesson?
48. Can you think of an app you've used that likely suffered from a missing readiness probe (brief errors right after deploy)?
49. Why might overly strict health checks be just as dangerous as no health checks at all?
50. What questions do you still have about probes before moving to the next hour's topic?

---

## 8. Hour 11 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Liveness Probe** | "Is it alive?" — on repeated failure, kubelet **restarts** the container |
| **Readiness Probe** | "Is it ready for traffic?" — on failure, pod is **removed from Service endpoints**, NOT restarted |
| **Startup Probe** | Delays liveness/readiness checks until slow-starting apps finish booting, preventing premature kills |
| **Probe mechanisms** | `httpGet` (2xx-3xx = pass), `tcpSocket` (connects = pass), `exec` (exit code 0 = pass), `grpc` (health protocol) |
| **Tuning fields** | `initialDelaySeconds`, `periodSeconds`, `timeoutSeconds`, `failureThreshold`, `successThreshold` |
| **Golden rule** | Liveness and readiness should check *different* things — liveness = internal process health, readiness = ability to serve traffic (can depend on dependencies) |
| **Symptom of missing startup probe** | CrashLoopBackOff during otherwise-normal slow boot |
| **Symptom of readiness working correctly** | `READY 0/1`, `STATUS Running`, `RESTARTS 0`, pod missing from `kubectl get endpoints` |

**Mnemonic:** *"SLR — Start, then Live, then Ready"* — **S**tartup gates everything first; **L**iveness decides *replace or keep*; **R**eadiness decides *route traffic or not*, without ever replacing.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Liveness, Readiness, and Startup Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Kubernetes Official Docs — Pod Lifecycle (Container Probes section)](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#container-probes)
- [Kubernetes Official Docs — Configure Liveness/Startup Probes with gRPC](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#define-a-grpc-liveness-probe)
- YouTube: "TechWorld with Nana — Kubernetes Health Checks Explained" (free, excellent visuals)
- [KodeKloud Kubernetes for Beginners — Probes section](https://kodekloud.com/courses/kubernetes-for-the-absolute-beginners-hands-on)

**Mini-Project for Hour 11 (30-45 min):**
Build a tiny web app (Node/Express, Python/Flask, or Go — whatever you know) with three endpoints:
- `/healthz` — returns 200 always, except when you manually flip an in-memory flag to simulate a hang (used for liveness).
- `/ready` — returns 200 only after a simulated 20-second "startup delay" has passed, and returns 503 for a configurable window afterward to simulate a dependency blip (used for readiness).
- `/started` — returns 200 only after the same 20-second startup delay (used for the startup probe).

Containerize it, push it to a local registry or load it into Minikube/Kind, and write a Deployment manifest wiring up `startupProbe` → `/started`, `livenessProbe` → `/healthz`, `readinessProbe` → `/ready`. Deploy it, then:
1. Watch `kubectl get pods -w` during the first 20 seconds and confirm it doesn't restart despite not being ready yet.
2. Trigger the `/ready` 503 window and confirm it disappears from `kubectl get endpoints` without restarting.
3. Trigger the `/healthz` hang flag and confirm it gets restarted (RESTARTS increments, `Killing`/`Started` events appear).
