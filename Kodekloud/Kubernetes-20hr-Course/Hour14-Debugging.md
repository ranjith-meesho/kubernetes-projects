# Hour 14: Debugging, Logs, Events, Exec, Describe

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine your app is a patient in a hospital. `kubectl describe` is reading the **patient's chart and the nurse's notes** — what happened, when, what alarms went off (events, conditions, config). `kubectl logs` is **listening to what the patient is currently saying** — the app's own words about what it's doing (stdout/stderr). `kubectl exec` is the **doctor going in for a hands-on physical exam** — actually stepping inside the room to poke around interactively. `kubectl get events` is the **hospital-wide PA system** — announcements about everything happening across every room (pod), not just the one you're staring at.

A good doctor doesn't jump straight to surgery (exec) before reading the chart (describe). That's the #1 debugging mistake in Kubernetes: skipping straight to `logs` or `exec` before reading the Events section in `describe`.

**Technical version:**

- **`kubectl describe pod <name>`** — dumps the full object spec, current status, conditions (Ready, PodScheduled, ContainersReady), container statuses (waiting/running/terminated + exit codes), and — most importantly — an **Events** section at the bottom showing a chronological log of what the scheduler/kubelet/controllers did to this pod (scheduling failures, image pull failures, health check failures, OOM kills). **This should almost always be your first command** when something's wrong.
- **`kubectl logs <pod>`** — shows container **stdout/stderr**. Flags:
  - `-f` / `--follow` — stream logs live (like `tail -f`)
  - `--previous` (or `-p`) — shows logs from the **previous** (crashed) instance of the container. Critical for `CrashLoopBackOff`: once a container restarts, `kubectl logs` without `--previous` shows the *new* attempt's logs (often empty/too early), not the crash that just happened.
  - `-c <container>` — required for multi-container pods (e.g., a pod with an app container + a sidecar); without it, `kubectl` picks a default or errors out.
  - `--tail=N`, `--since=1h` — limit volume for chatty logs.
- **`kubectl exec -it <pod> -- <cmd>`** — opens an interactive shell (or runs a one-off command) inside a **running** container. Useless if the container isn't running (CrashLoopBackOff, ImagePullBackOff) — you can't exec into something that isn't up. Great for checking env vars, DNS resolution, file existence, connectivity (`curl`, `nslookup`) from inside the container's network namespace.
- **`kubectl get events`** — cluster/namespace-wide stream of events (not scoped to one pod). Use `--sort-by=.lastTimestamp` to see the most recent events first, and `-A` for all namespaces. Essential when the problem isn't "my pod" but something upstream (a node ran out of resources, an admission webhook rejected something, a quota was hit).
- **Common Pod status states to recognize immediately:**
  | Status | Meaning |
  |---|---|
  | `Pending` | Not yet scheduled to a node (resource constraints, node affinity, taints, PVC not bound) |
  | `ContainerCreating` | Scheduled, but image pull / volume mount / secret mount still in progress |
  | `CrashLoopBackOff` | Container starts, crashes, Kubernetes backs off before retrying, repeatedly |
  | `ImagePullBackOff` | Can't pull the container image (bad name/tag, private registry auth failure, rate limits) |
  | `OOMKilled` | Container exceeded its memory limit and the kernel killed it (exit code 137) |
  | `Evicted` | Node ran low on resources (memory/disk pressure) and the kubelet evicted the pod |
  | `Completed` | Container ran to completion with exit code 0 (normal for Jobs/init containers) |
  | `Error` | Container exited with a non-zero exit code |

## 2. Diagram

```
Debugging Decision Tree: "My Pod Isn't Running"

                        kubectl get pods
                              │
                              ▼
                  ┌───────────────────────┐
                  │ What STATUS is shown?  │
                  └───────────────────────┘
       ┌───────────────┬───────────────┬───────────────┬────────────────┐
       ▼                ▼               ▼                ▼                ▼
   Pending        ImagePullBackOff  CrashLoopBackOff  ContainerCreating  OOMKilled/Evicted
       │                │               │                (stuck)             │
       ▼                ▼               ▼                ▼                ▼
 describe pod →   describe pod →   logs --previous   describe pod →     describe pod →
 read Events:     read Events:     -c <container> →  read Events:       check exit code
 - Insufficient    - "Failed to     read app crash    - Secret/ConfigMap  137 = OOM
   cpu/memory        pull image"    error/stack        not found          check resource
 - Node affinity/   - "unauthor-    trace              - PVC not bound    limits vs actual
   taints             ized"/"401"                      - Mount timeout    usage
 - PVC unbound      - Check image                                        (kubectl top pod)
 - Check quota:       name/tag &
   kubectl describe    registry
   resourcequota       credentials
   -n <namespace>      (imagePullSecrets)

  Still stuck? → kubectl get events -A --sort-by=.lastTimestamp
  (cluster-wide view: node pressure, admission webhook denials, quota exhaustion)

  Container IS running but misbehaving?
       │
       ▼
  kubectl exec -it <pod> -c <container> -- sh
  → check env vars, DNS (nslookup), connectivity (curl), files, processes
       │
       ▼
  Still unclear? → check related resources:
  - kubectl get endpoints <svc>   (Service has no healthy backends?)
  - kubectl get configmap/secret  (referenced object actually exists?)
  - kubectl describe resourcequota (namespace hit a limit?)
```

## 3. Real-World Example

**Incident: Production checkout-service pods stuck in `ImagePullBackOff` at 2 AM.**

An on-call engineer at an e-commerce company (imagine Meesho's `payment-service` deploy) gets paged: new pods for `checkout-service` won't start after a routine deploy. Here's the exact toolkit walk-through:

1. **`kubectl get pods -n checkout`** → sees `checkout-service-7d9f8-xk2p1   0/1   ImagePullBackOff   0   4m`. Status alone tells us it's an image problem, not app logic.
2. **`kubectl describe pod checkout-service-7d9f8-xk2p1 -n checkout`** → scrolls to the **Events** section at the bottom:
   ```
   Warning  Failed   3m (x8 over 4m)  kubelet  Failed to pull image "registry.internal.co/checkout:v2.3.1":
   rpc error: code = Unknown desc = failed to authorize: 401 Unauthorized
   ```
   This immediately rules out a typo in the image name — it's an **auth** problem, not a "does this image exist" problem.
3. Engineer checks the registry credential: **`kubectl get secret regcred -n checkout -o yaml`** and decodes it — turns out the `imagePullSecret` token was rotated by the security team the day before and the cluster's copy of the secret was never updated. Classic expired-credential incident.
4. **`kubectl logs`** is not useful here — the container never started, so there's nothing to log. **`kubectl exec`** is also impossible — you can't exec into a container that was never created.
5. Fix: update the `regcred` Secret with a fresh registry token (`kubectl create secret docker-registry regcred --docker-server=... --docker-username=... --docker-password=... -n checkout --dry-run=client -o yaml | kubectl apply -f -`), then `kubectl delete pod` to force a retry (or wait for the backoff timer).
6. **`kubectl get pods -w`** confirms pods flip to `Running` within seconds once the new credential is in place.

**Lesson:** `describe` → Events told us the *exact* cause in one command. No time wasted guessing or trying to exec into a nonexistent container.

## 4. Hands-On Lab

**Goal:** Deliberately break a pod three different ways and diagnose each using only `describe`, `logs`, and `get events` — no peeking at the YAML to "cheat."

### Break #1: Bad image name → `ImagePullBackOff`

```bash
kubectl run broken-image --image=nginx:this-tag-does-not-exist-xyz
kubectl get pods
```
**Expected output:**
```
NAME            READY   STATUS             RESTARTS   AGE
broken-image    0/1     ImagePullBackOff   0          30s
```
```bash
kubectl describe pod broken-image
```
**Expected Events:**
```
Warning  Failed   10s (x3 over 40s)  kubelet  Failed to pull image "nginx:this-tag-does-not-exist-xyz":
rpc error: code = NotFound desc = manifest unknown
Warning  BackOff  5s (x2 over 20s)   kubelet  Back-off pulling image "nginx:this-tag-does-not-exist-xyz"
```
Diagnosis: bad tag/registry — fix the image reference.

### Break #2: Command that exits immediately → `CrashLoopBackOff`

```bash
kubectl run broken-crash --image=busybox --restart=Always -- /bin/sh -c "echo 'dying now' && exit 1"
kubectl get pods -w
```
**Expected output (after a minute or two):**
```
NAME            READY   STATUS             RESTARTS   AGE
broken-crash    0/1     CrashLoopBackOff   4          90s
```
```bash
kubectl logs broken-crash              # may show nothing useful — it's the NEW attempt
kubectl logs broken-crash --previous   # shows the actual crash
```
**Expected `--previous` output:**
```
dying now
```
```bash
kubectl describe pod broken-crash
```
**Expected relevant fields:**
```
Last State:     Terminated
  Reason:       Error
  Exit Code:    1
Events:
  Warning  BackOff  5s (x6 over 60s)  kubelet  Back-off restarting failed container
```
Diagnosis: container's own command exits with code 1 by design — this simulates an app crashing on startup (missing env var, failed DB connection, etc).

### Break #3: Memory limit too low → `OOMKilled`

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: broken-oom
spec:
  containers:
  - name: hog
    image: polinux/stress
    resources:
      limits:
        memory: "20Mi"
    command: ["stress"]
    args: ["--vm", "1", "--vm-bytes", "150M", "--vm-hang", "1"]
EOF
kubectl get pods -w
```
**Expected output:**
```
NAME         READY   STATUS      RESTARTS   AGE
broken-oom   0/1     OOMKilled   1          20s
```
```bash
kubectl describe pod broken-oom
```
**Expected relevant fields:**
```
Last State:     Terminated
  Reason:       OOMKilled
  Exit Code:    137
```
Diagnosis: exit code 137 = SIGKILL from OOM. Fix: raise the memory limit or reduce the app's actual usage.

**Cleanup:**
```bash
kubectl delete pod broken-image broken-crash broken-oom
```

## 5. Common Mistakes

1. **Jumping straight to `logs` or `exec` without checking `describe`/Events first.** Events often tell you the exact cause (scheduling failure, image pull error, OOM) in one shot — skipping it wastes time guessing.
2. **Not using `--previous` on a crashed container.** After a crash, `kubectl logs` (without `-p`) shows the *current* (often brand-new, empty) attempt, not the crash you're investigating — leading people to wrongly conclude "there are no logs, so no error happened."
3. **Ignoring exit codes.** Exit code 137 (OOMKilled/SIGKILL), 1 (generic app error), 143 (SIGTERM) all point to different root causes — treating every crash the same wastes investigation time.
4. **Only looking at pod-level events, never running `kubectl get events --sort-by=.lastTimestamp` cluster-wide.** Some problems (node disk pressure, quota exhaustion, webhook denials) don't show up clearly when you're staring at a single pod's describe output.
5. **Trying to `kubectl exec` into a pod that was never actually created/running** (e.g., stuck in `ImagePullBackOff` or `Pending`) — exec only works on already-running containers.
6. **Forgetting `-c <container>` in multi-container pods**, leading to confusion about whose logs you're actually reading (app container vs sidecar/init container).

## 6. Interview Questions (with brief answers)

1. **A pod is stuck in `Pending` — how do you debug it?** — Run `kubectl describe pod <name>` and read the Events section. Common causes: insufficient CPU/memory on any node (`Insufficient cpu`), no node matches nodeSelector/affinity/taints, an unbound PersistentVolumeClaim, or a namespace ResourceQuota being exceeded (`kubectl describe resourcequota -n <ns>`).
2. **What's the difference between `CrashLoopBackOff` and `ImagePullBackOff`?** — `ImagePullBackOff` means Kubernetes could never even start the container because it failed to pull the image (bad name/tag, registry auth failure). `CrashLoopBackOff` means the image *was* pulled and the container *did* start, but it keeps exiting/crashing shortly after, so Kubernetes keeps retrying with an exponential backoff delay.
3. **Why would `kubectl logs` show nothing for a container that just crashed?** — Because by the time you run the command, the container has already been restarted and you're viewing the logs of the new (fresh) attempt, not the crashed one. Use `kubectl logs <pod> --previous` to see the last terminated instance's output.
4. **What does exit code 137 mean, and how do you confirm it via `kubectl`?** — 137 = 128 + 9 (SIGKILL), almost always from the OOM killer terminating the container for exceeding its memory limit. Confirm via `kubectl describe pod <name>` — look for `Last State: Terminated, Reason: OOMKilled, Exit Code: 137`.
5. **When would you use `kubectl get events` instead of `kubectl describe pod`?** — When the issue isn't isolated to one pod, or you suspect a cluster-wide/node-level cause (disk pressure, quota exhaustion, failed admission webhook, eviction sweep). `kubectl get events -A --sort-by=.lastTimestamp` gives the chronological, cluster-wide picture that a single pod's describe output won't show.

## 7. Quiz (50 Questions)

**True/False:**
1. `kubectl describe pod` shows an Events section. (T)
2. `kubectl logs` without `--previous` always shows the crash that just happened. (F)
3. `kubectl exec` can be used on a pod stuck in `ImagePullBackOff`. (F)
4. `ImagePullBackOff` means the image was pulled but the app crashed. (F)
5. Exit code 137 typically indicates an OOM kill. (T)
6. `kubectl get events` can be scoped cluster-wide with `-A`. (T)
7. `CrashLoopBackOff` means Kubernetes gives up trying to restart the container after one failure. (F)
8. A pod in `Pending` state has definitely been scheduled to a node. (F)
9. `-c <container>` is required when running `kubectl logs` against a multi-container pod without a default. (T)
10. `Completed` status generally means the container exited with code 0. (T)
11. `Evicted` pods are removed due to node resource pressure. (T)
12. `kubectl describe` shows resource requests/limits for a pod's containers. (T)
13. You should always run `kubectl exec` before `kubectl describe` when debugging. (F)
14. `--sort-by=.lastTimestamp` on `kubectl get events` sorts oldest-first. (F)
15. A ResourceQuota being exceeded can cause a pod to stay `Pending`. (T)

**Multiple Choice:**
16. Which command should typically be run FIRST when debugging a broken pod? a) kubectl exec b) kubectl logs c) kubectl describe pod d) kubectl delete pod → (c)
17. What flag shows logs from a crashed container's previous instance? a) --old b) --previous c) --last d) --history → (b)
18. Exit code 137 corresponds to which signal? a) SIGTERM b) SIGKILL c) SIGINT d) SIGHUP → (b)
19. Which status indicates the scheduler could not place the pod on any node? a) ContainerCreating b) CrashLoopBackOff c) Pending d) Completed → (c)
20. What's a likely cause of `ImagePullBackOff`? a) Out of memory b) Bad image tag or registry auth failure c) Missing ConfigMap d) Failed liveness probe → (b)
21. Which command lets you get an interactive shell inside a running container? a) kubectl attach b) kubectl exec -it c) kubectl port-forward d) kubectl cp → (b)
22. What does `kubectl get events --sort-by=.lastTimestamp` help you see? a) Only pod restarts b) Chronological order of cluster-wide events c) CPU usage d) Node taints only → (b)
23. Which status is expected for a Job's pod that ran successfully? a) Error b) Completed c) CrashLoopBackOff d) Evicted → (b)
24. What typically causes `Evicted` pods? a) Bad image name b) Node disk/memory pressure c) DNS failure d) RBAC denial → (b)
25. In a multi-container pod, what flag specifies which container's logs to view? a) -n b) -c c) -p d) -f → (b)

**Short Answer:**
26. What is the first command you should run when a pod is misbehaving, and why?
27. Why might `kubectl logs` show an empty result right after a crash?
28. What section of `kubectl describe pod` output is most useful for diagnosis?
29. Name two possible causes for a pod stuck in `Pending`.
30. What is the difference between `Error` and `OOMKilled` container states?
31. Why can't you `kubectl exec` into a pod in `ImagePullBackOff`?
32. What does the `-f` flag do on `kubectl logs`?
33. What's the purpose of `kubectl get events -A`?
34. What does exit code 1 typically suggest versus exit code 137?
35. How would you check if a namespace's ResourceQuota is causing scheduling failures?

**Scenario-Based:**
36. A pod shows `0/1 Running` with high restart count — what commands do you run in what order?
37. Your team deployed a new image tag and pods are stuck `ImagePullBackOff` — what's your first hypothesis and how do you confirm it?
38. A pod is `ContainerCreating` for 10 minutes and never progresses — what would you check?
39. A Service has zero traffic reaching pods, but the pods show `Running` — what do you check beyond the pod itself?
40. Your container's logs show nothing at all, even before a crash — what native Kubernetes reason could explain this (hint: sidecar/init container)?
41. An engineer says "I checked logs, there's nothing there, must not be an error" right after a `CrashLoopBackOff`. What's wrong with their approach?
42. A pod was working fine yesterday but today it's `Pending` — nothing in the manifest changed. What cluster-level cause might explain this?
43. You suspect a bad registry credential is causing failures across multiple pods/namespaces. What command would confirm this cluster-wide quickly?
44. During an incident, `kubectl describe pod` shows `Insufficient memory` in Events. What are two ways to resolve this?
45. A teammate wants to "just restart the pod" to fix a `CrashLoopBackOff` without investigating. Why is this risky?

**Fill in the Blank:**
46. The flag to view logs from a crashed container's last run is ______.
47. Exit code ______ generally indicates the OOM killer terminated the container.
48. ______ status means Kubernetes could not pull the container image.
49. The command ______ shows a cluster-wide, timestamp-sorted view of what's happening.
50. The mnemonic for the debugging toolkit order is ______.

---

## 8. Hour 14 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **`kubectl describe`** | First command to run — shows Events, conditions, config, exit codes |
| **`kubectl logs`** | App-level stdout/stderr; use `-f` to follow, `--previous` for crashed containers, `-c` for multi-container pods |
| **`kubectl exec`** | Interactive shell into a *running* container — useless if the container never started |
| **`kubectl get events`** | Cluster-wide event stream; use `--sort-by=.lastTimestamp` and `-A` when pod-level view isn't enough |
| **Status states to recognize** | Pending, ContainerCreating, CrashLoopBackOff, ImagePullBackOff, OOMKilled, Evicted, Completed, Error |
| **Exit codes matter** | 137 = OOMKilled (SIGKILL), 1 = generic app error, 0 = success |
| **Debugging order** | get pods → describe pod (Events) → logs (--previous if crashed) → exec → check related resources (Service endpoints, ConfigMap/Secret, quotas) |
| **Mental model** | describe = patient's chart/nurse's notes; logs = what the patient is saying now; exec = doctor's hands-on exam |

**Mnemonic:** *"DLEE"* — **D**escribe (chart/events first) → **L**ogs (what's it saying, `--previous` if crashed) → **E**xec (hands-on exam, only if running) → **E**vents (cluster-wide, when pod view isn't enough).

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Debug Pods](https://kubernetes.io/docs/tasks/debug/debug-application/debug-pods/)
- [Kubernetes Official Docs — Debug Running Pods](https://kubernetes.io/docs/tasks/debug/debug-application/debug-running-pod/)
- [Kubernetes Official Docs — Application Introspection and Debugging](https://kubernetes.io/docs/tasks/debug/debug-application/)
- [Kubernetes Official Docs — Troubleshoot Applications](https://kubernetes.io/docs/tasks/debug/debug-application/)
- YouTube: "TechWorld with Nana — Kubernetes Troubleshooting" (free, practical walk-throughs)

**Mini-Project for Hour 14 (30 min):**
- Deliberately break an app three different ways (reuse or invent variations of the three lab breaks: bad image, immediate-exit command, undersized memory limit) in a test namespace.
- Hand your cluster (or a `kubectl` transcript) to a friend — or come back to it yourself tomorrow with fresh eyes — and diagnose each broken pod **blind**, using only `describe`, `logs`, and `get events`, without looking at the original manifest.
- Time yourself: can you correctly identify root cause for all three in under 5 minutes total, using only the DLEE toolkit?
