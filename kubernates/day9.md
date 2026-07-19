# 🚢 DAY 9 — Troubleshooting Masterclass: Debugging Kubernetes Like an SRE

> **Capstone day.** You've built Pods, Deployments, Services, ConfigMaps, Volumes, and networking across Days 1–8. Today you learn to *break things* and *fix them fast*. This is the single most valuable skill in production AND the most common interview filter. Every command here is copy-paste ready with real output.

---

## 1. ## LEARNING OBJECTIVES

By the end of Day 9 you will be able to:

1. Apply a **systematic, repeatable debugging methodology** instead of guessing.
2. Diagnose and fix **CrashLoopBackOff** (the #1 production pod failure).
3. Diagnose and fix **Pending Pods** and **FailedScheduling** (resources, taints, affinity, PVC).
4. Diagnose and fix **ImagePullBackOff / ErrImagePull** (typos, private registries, auth).
5. Diagnose and fix **Service connectivity** problems (selector mismatch, no endpoints, wrong ports).
6. Debug **DNS resolution failures** inside the cluster.
7. Identify and remediate **OOMKilled** containers and resource-limit problems.
8. Master the full **kubectl debug toolkit**: `get`, `describe`, `logs`, `logs --previous`, `exec`, `events`, `top`, `port-forward`, and **ephemeral debug containers**.
9. Read **Pod status, exit codes, and reason strings** like a native language.
10. Answer the **Top 50 troubleshooting interview questions** with confidence.

---

## 2. ## 80/20 BREAKDOWN

| Priority | Topic | Why it matters | Master now or defer? |
|----------|-------|----------------|----------------------|
| 🔴 P0 | `describe` + `events` + `logs` workflow | 80% of every incident is solved here | **Master now** |
| 🔴 P0 | CrashLoopBackOff diagnosis | Most frequent pod failure in prod | **Master now** |
| 🔴 P0 | ImagePullBackOff | Most frequent *deploy-time* failure | **Master now** |
| 🔴 P0 | Pending / FailedScheduling | Capacity & scheduling reality | **Master now** |
| 🔴 P0 | Service → Endpoints → Pod chain | Root of nearly all "app unreachable" tickets | **Master now** |
| 🟠 P1 | OOMKilled & exit codes (137/143/1/2) | Memory tuning, interview gold | **Master now** |
| 🟠 P1 | DNS debugging (CoreDNS, ndots) | Subtle, high-impact outages | Master now |
| 🟡 P2 | `kubectl debug` ephemeral containers | Modern best practice (distroless) | Learn the pattern |
| 🟡 P2 | `kubectl top` / metrics-server | Needs add-on; useful not critical | Defer if no metrics-server |
| 🟢 P3 | Node-level debugging (`debug node/`) | Cluster-admin scope | Defer to ops track |

> **🏆 Interview gold:** Interviewers rarely ask "what is a Pod." They ask **"a pod is in CrashLoopBackOff — walk me through your debugging."** The 80/20 of passing the interview is the methodology (Section 3) + exit codes + the Service→Endpoints chain. Memorize the decision tree.

---

## 3. ## CONCEPT EXPLANATIONS

### 3.0 The Universal Debugging Methodology

Never start by editing YAML. Always **observe → narrow → confirm → fix → verify**. Work from the outside in: cluster → workload → pod → container → process.

```
                    ┌─────────────────────────────┐
                    │  SYMPTOM: "App is broken"    │
                    └───────────────┬─────────────┘
                                    │
                    ┌───────────────▼─────────────┐
                    │ kubectl get pods -o wide     │
                    │ What is the STATUS column?   │
                    └───────────────┬─────────────┘
        ┌────────────────┬──────────┼───────────┬──────────────────┐
        │                │          │           │                  │
   ┌────▼────┐    ┌──────▼─────┐ ┌──▼───┐  ┌────▼──────┐    ┌───────▼──────┐
   │ Pending │    │ImagePull   │ │Crash │  │  Running  │    │ Completed /  │
   │         │    │BackOff /   │ │Loop  │  │ but app   │    │ Error /      │
   │         │    │ErrImagePull│ │BackOff│ │ unreachable│   │ Terminating  │
   └────┬────┘    └──────┬─────┘ └──┬───┘  └────┬──────┘    └───────┬──────┘
        │                │          │           │                   │
        ▼                ▼          ▼           ▼                   ▼
  describe pod     describe pod  logs --prev  Is READY 1/1?     describe +
  → Events:        → Events:     describe →   → get endpoints   logs --prev
  Insufficient?    bad image?    Last State   → kubectl exec    → exit code?
  taint? PVC?      auth? typo?   exit code?   → curl svc/pod
        │                │          │           │                   │
        ▼                ▼          ▼           ▼                   ▼
   fix resources/   fix tag/      fix cmd/    fix selector/      fix probe/
   tolerations/     imagePull     env/probe/  port/DNS/          OOM/config
   PV provisioner   Secret        memory      networkpolicy
```

**The 4 commands that solve 80% of incidents (run them in this order):**

```bash
kubectl get pods -o wide            # status, restarts, node, IP
kubectl describe pod <pod>          # Events + container state + reasons
kubectl logs <pod> [--previous]     # what the app itself said
kubectl get events --sort-by=.lastTimestamp   # cluster-wide timeline
```

---

### 3.1 The kubectl Debug Toolkit (reference)

| Command | Purpose | Example |
|---------|---------|---------|
| `get` | List + status snapshot | `kubectl get pods -o wide` |
| `get -o yaml` | Full live spec + status | `kubectl get pod web -o yaml` |
| `describe` | Events + state + reasons | `kubectl describe pod web` |
| `logs` | Current container stdout/stderr | `kubectl logs web` |
| `logs --previous` | Logs of the **crashed** prior container | `kubectl logs web -p` |
| `logs -f` | Stream/follow | `kubectl logs web -f` |
| `logs -c <ctr>` | Specific container in multi-container pod | `kubectl logs web -c sidecar` |
| `exec` | Run a command inside a running container | `kubectl exec -it web -- sh` |
| `events` | Cluster/namespace event stream | `kubectl get events --sort-by=.lastTimestamp` |
| `top` | Live CPU/RAM (needs metrics-server) | `kubectl top pod`, `kubectl top node` |
| `port-forward` | Tunnel local port → pod/svc | `kubectl port-forward svc/web 8080:80` |
| `debug` (ephemeral) | Attach a debug container to a running pod | `kubectl debug -it web --image=busybox --target=app` |
| `debug` (copy) | Clone a pod with changed command | `kubectl debug web -it --copy-to=dbg --container=app -- sh` |
| `debug node/` | Privileged pod on a node's host namespace | `kubectl debug node/node1 -it --image=ubuntu` |

**Key flags worth memorizing:**

```bash
kubectl logs <pod> --previous              # -p : the container that crashed
kubectl logs <pod> --since=10m             # only recent
kubectl logs <pod> --tail=50               # last N lines
kubectl logs <pod> --all-containers=true   # every container
kubectl get events --field-selector involvedObject.name=<pod>
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].lastState.terminated.exitCode}'
```

**Pod STATUS values you must recognize:**

| Status | Meaning |
|--------|---------|
| `Pending` | Not yet scheduled OR image still pulling |
| `ContainerCreating` | Scheduled, setting up (volumes/network/image) |
| `Running` | At least one container started (NOT necessarily Ready) |
| `CrashLoopBackOff` | Container keeps exiting; kubelet backing off restarts |
| `ImagePullBackOff` / `ErrImagePull` | Cannot pull the image |
| `CreateContainerConfigError` | Missing ConfigMap/Secret referenced |
| `Error` | Container exited non-zero (and not restarting) |
| `OOMKilled` | Killed for exceeding memory limit (exit 137) |
| `Completed` | Container ran to success (exit 0) |
| `Terminating` | Being deleted (may be stuck on finalizers/grace period) |
| `Init:N/M` | Stuck in init container N of M |

**Container exit codes:**

| Code | Meaning |
|------|---------|
| `0` | Success / clean exit |
| `1` | General application error |
| `2` | Shell misuse / bad CLI args |
| `126` | Command found but not executable (permission) |
| `127` | Command not found (typo in `command:` / missing binary) |
| `137` | SIGKILL (128+9) — usually **OOMKilled** or `kubectl delete` past grace |
| `139` | SIGSEGV (128+11) — segfault |
| `143` | SIGTERM (128+15) — graceful shutdown signal |

---

### 3.2 CrashLoopBackOff

**Symptoms:** `RESTARTS` count climbing, `STATUS = CrashLoopBackOff`, app never stays up. The kubelet restarts, fails, and backs off exponentially (10s, 20s, 40s … capped at 5 min).

**Root causes (most → least common):**
- Application bug / unhandled exception on startup (exit 1).
- Bad `command:`/`args:` — binary not found (exit 127) or misuse (exit 2).
- Missing required env var / config / secret → app aborts.
- Failing **liveness probe** restarting a healthy-but-slow app.
- Can't reach a dependency (DB, cache) at boot and exits instead of retrying.
- **OOMKilled** repeatedly (exit 137) — too little memory.
- Wrong filesystem permissions / read-only FS.

**Diagnosis:**
```bash
kubectl get pod <pod> -o wide                  # confirm restarts
kubectl describe pod <pod>                      # Last State, Reason, exit code, probe events
kubectl logs <pod> --previous                   # THE crashed instance's logs (most important!)
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[*].lastState.terminated.reason}{"\n"}'
```
Look in `describe` for:
```
    Last State:     Terminated
      Reason:       Error
      Exit Code:    1
    Restart Count:  6
  Warning  Unhealthy  kubelet  Liveness probe failed: ...
```

**Resolution:**
- Exit 1 → fix the app / supply missing env/config (`describe` shows env, `logs -p` shows the stack trace).
- Exit 127 → fix `command:`/image entrypoint.
- Liveness-probe kills → raise `initialDelaySeconds` / `failureThreshold`, fix the probe path/port.
- Exit 137 → raise memory limit or fix the leak (see OOMKilled, §3.7).

**Prevention:**
- Add `startupProbe` for slow boots so liveness doesn't kill cold-start apps.
- Make the app **retry dependencies with backoff**, don't crash.
- Set realistic `resources.limits.memory`; load-test it.
- Validate config at deploy time; fail fast with a *clear* log line.

---

### 3.3 Pending Pods

**Symptoms:** `STATUS = Pending` indefinitely. Pod exists in the API but has no node (`NODE = <none>`).

**Root causes:**
- **Insufficient resources** — no node has enough free CPU/memory for the requests.
- **Taints** on nodes without matching **tolerations**.
- **Node affinity / nodeSelector** matches no node.
- **PVC pending** — no PV / no StorageClass / no dynamic provisioner.
- Pod **anti-affinity** can't be satisfied (e.g., 4 replicas, 3 nodes, `requiredDuringScheduling`).
- All nodes `NotReady` / cordoned, or cluster has 0 worker nodes.

**Diagnosis:**
```bash
kubectl describe pod <pod>           # Events: "0/3 nodes are available: Insufficient cpu"
kubectl get nodes                    # any NotReady / SchedulingDisabled?
kubectl describe nodes | grep -A5 Allocated      # node capacity vs allocated
kubectl get pvc                      # bound or Pending?
kubectl describe pvc <pvc>           # provisioner errors
```
Typical event:
```
Warning  FailedScheduling  default-scheduler
  0/3 nodes are available: 3 Insufficient memory. preemption: 0/3 nodes are available.
```

**Resolution:**
- Lower `resources.requests`, or add nodes / scale the cluster autoscaler.
- Add matching `tolerations` for tainted nodes.
- Fix `nodeSelector`/affinity labels, or label a node.
- Provide a StorageClass / install a CSI provisioner for the PVC.

**Prevention:** Right-size requests (use historical `kubectl top` data), keep cluster-autoscaler headroom, document node taints/labels.

---

### 3.4 FailedScheduling (deep dive)

`FailedScheduling` is the *event reason* behind most Pending pods. Read the exact predicate that failed:

```bash
kubectl describe pod <pod> | sed -n '/Events/,$p'
```
Common messages and meanings:
| Message fragment | Cause | Fix |
|---|---|---|
| `Insufficient cpu` / `Insufficient memory` | requests > free | reduce requests / add capacity |
| `node(s) had untolerated taint` | taint w/o toleration | add toleration |
| `didn't match Pod's node affinity/selector` | label mismatch | fix selector / label node |
| `had volume node affinity conflict` | PV is zone-locked to another node | match topology |
| `pod has unbound immediate PersistentVolumeClaims` | PVC not bound | fix StorageClass |
| `node(s) didn't have free ports` | hostPort conflict | change hostPort / use Service |

---

### 3.5 ImagePullBackOff / ErrImagePull

**Symptoms:** `STATUS = ErrImagePull` then `ImagePullBackOff` (kubelet backing off pull retries).

**Root causes:**
- **Typo** in image name or tag (`ngnix:latst`).
- Tag doesn't exist in the registry.
- **Private registry** without `imagePullSecrets`.
- Bad/expired registry credentials.
- Registry unreachable (network/firewall/airgap) or rate-limited (Docker Hub `toomanyrequests`).
- Wrong architecture (arm64 image on amd64 node) → `no matching manifest`.

**Diagnosis:**
```bash
kubectl describe pod <pod>     # Events show the precise pull error
```
```
Warning  Failed  kubelet  Failed to pull image "ngnix:latst":
  rpc error: ... pull access denied / not found / unauthorized
Warning  Failed  kubelet  Error: ErrImagePull
Normal   BackOff kubelet  Back-off pulling image "ngnix:latst"
```

**Resolution:**
- Fix the name/tag (`kubectl set image` or edit + reapply).
- For private images, create and attach a pull secret:
```bash
kubectl create secret docker-registry regcred \
  --docker-server=registry.example.com \
  --docker-username=USER --docker-password=PASS --docker-email=you@x.com
# then reference it in the pod spec under imagePullSecrets:
```
- For Docker Hub rate limits, authenticate or use a mirror/pull-through cache.

**Prevention:** Use immutable digests (`@sha256:…`) or specific tags (never blind `:latest`), pre-pull/mirror images, store pull secrets at the ServiceAccount level.

---

### 3.6 Service Connectivity (Service → Endpoints → Pod)

**Symptoms:** `curl svc` times out / connection refused; app "can't reach" another service.

**The golden chain — debug each link:**
```
Client → DNS → Service ClusterIP → kube-proxy rules → Endpoints → Pod IP:port → container listening
```

**Root causes:**
- **Selector mismatch** — Service `selector` labels don't match Pod labels → **no Endpoints**.
- `targetPort` ≠ container's actual listening port.
- App binds to `127.0.0.1` instead of `0.0.0.0` → unreachable from other pods.
- Pods not Ready (readiness probe failing) → removed from Endpoints.
- **NetworkPolicy** blocking the traffic.
- Wrong Service type/port mapping (`port` vs `targetPort` vs `nodePort`).

**Diagnosis (the decisive command):**
```bash
kubectl get endpoints <svc>          # EMPTY endpoints = selector/readiness problem
kubectl get endpointslices -l kubernetes.io/service-name=<svc>
kubectl describe svc <svc>           # confirm selector + ports
kubectl get pods --show-labels       # do labels match the selector?
# test from inside the cluster:
kubectl run tmp --rm -it --image=nicolaka/netshoot -- sh
  curl -v http://<svc>.<ns>.svc.cluster.local:<port>
  wget -qO- <pod-ip>:<targetPort>
```
If `ENDPOINTS` shows `<none>` → the Service selects zero ready pods. Fix labels or readiness first.

**Resolution:** Align Service `selector` with Pod labels; set `targetPort` to the real container port; make the app listen on `0.0.0.0`; fix readiness probe; relax/scope NetworkPolicy.

**Prevention:** Keep label conventions consistent, prefer named ports, add readiness probes, test connectivity with `netshoot` in CI/staging.

---

### 3.7 DNS Failures

**Symptoms:** App logs `could not resolve host`, `Name or service not known`; service-by-IP works but service-by-name fails.

**Root causes:** CoreDNS down/crashlooping, wrong `dnsPolicy`, broken `/etc/resolv.conf`, `ndots:5` causing slow/odd lookups, NetworkPolicy blocking UDP/53 to kube-system.

**Diagnosis:**
```bash
kubectl get pods -n kube-system -l k8s-app=kube-dns      # CoreDNS healthy?
kubectl logs -n kube-system -l k8s-app=kube-dns
kubectl run dns --rm -it --image=nicolaka/netshoot -- sh
  nslookup kubernetes.default
  nslookup <svc>.<ns>.svc.cluster.local
  cat /etc/resolv.conf            # nameserver should be the kube-dns ClusterIP (10.96.0.10)
```

**Resolution:** Restart/scale CoreDNS, fix `dnsPolicy: ClusterFirst`, allow egress to kube-dns in NetworkPolicy, use FQDNs to avoid `ndots` search-domain overhead.

**Prevention:** Run ≥2 CoreDNS replicas, add NodeLocal DNSCache for scale, monitor CoreDNS error/latency metrics.

---

### 3.8 OOMKilled

**Symptoms:** Container restarts; `describe` shows `Last State: Terminated, Reason: OOMKilled, Exit Code: 137`.

**Root causes:** memory `limit` too low, memory leak, JVM/Node heap not aware of the cgroup limit, large in-memory data.

**Diagnosis:**
```bash
kubectl describe pod <pod> | grep -A3 "Last State"
kubectl top pod <pod>                        # live usage vs limit
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].lastState.terminated.reason}'
```

**Resolution:** Raise `resources.limits.memory`, fix the leak, set runtime heap to the limit (e.g. `-XX:MaxRAMPercentage=75`, `--max-old-space-size`), set `requests`=`limits` for Guaranteed QoS on critical pods.

**Prevention:** Load-test memory, alert on `container_memory_working_set_bytes / limit > 0.9`, avoid unbounded caches.

---

## 4. ## HANDS-ON LABS

> Setup: any cluster (kind, minikube, k3s). All labs follow **break → diagnose → fix → verify**. Run in a scratch namespace: `kubectl create ns day9 && kubectl config set-context --current --namespace=day9`.

### Lab A — CrashLoopBackOff from a bad command

**Broken YAML** (`crash.yaml`):
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: crasher
spec:
  containers:
  - name: app
    image: busybox:1.36
    command: ["sh", "-c", "echo starting; exit 1"]   # always fails
```
```bash
kubectl apply -f crash.yaml
kubectl get pod crasher -w
```
**Output:**
```
NAME      READY   STATUS             RESTARTS      AGE
crasher   0/1     CrashLoopBackOff   3 (20s ago)   65s
```
**Diagnose:**
```bash
kubectl describe pod crasher | sed -n '/Last State/,/Events/p'
kubectl logs crasher --previous
```
```
    Last State:     Terminated
      Reason:       Error
      Exit Code:    1
--- logs ---
starting
```
Exit 1 + log shows it exits intentionally → it's the command.

**Fix:** make it stay up.
```bash
kubectl delete pod crasher
```
Change command to `["sh","-c","echo starting; sleep 3600"]`, reapply.
**Verify:** `kubectl get pod crasher` → `Running 1/1`, RESTARTS `0`.

---

### Lab B — ImagePullBackOff from a typo

**Broken YAML** (`badimage.yaml`):
```yaml
apiVersion: v1
kind: Pod
metadata: { name: typo }
spec:
  containers:
  - name: web
    image: ngnix:latst        # two typos: ngnix + latst
```
```bash
kubectl apply -f badimage.yaml
kubectl get pod typo
```
**Output:**
```
NAME   READY   STATUS             RESTARTS   AGE
typo   0/1     ImagePullBackOff   0          30s
```
**Diagnose:**
```bash
kubectl describe pod typo | grep -A6 Events
```
```
Warning  Failed   kubelet  Failed to pull image "ngnix:latst": ... not found
Warning  Failed   kubelet  Error: ErrImagePull
Normal   BackOff  kubelet  Back-off pulling image "ngnix:latst"
```
**Fix:**
```bash
kubectl set image pod/typo web=nginx:1.27
```
**Verify:** `kubectl get pod typo` → `Running`. (If `set image` on a bare pod doesn't re-pull cleanly, delete & reapply with corrected YAML.)

---

### Lab C — Pending from a huge resource request

**Broken YAML** (`huge.yaml`):
```yaml
apiVersion: v1
kind: Pod
metadata: { name: greedy }
spec:
  containers:
  - name: app
    image: nginx:1.27
    resources:
      requests:
        cpu: "200"          # 200 cores — no node has this
        memory: "500Gi"
```
```bash
kubectl apply -f huge.yaml
kubectl get pod greedy
```
**Output:**
```
NAME     READY   STATUS    RESTARTS   AGE
greedy   0/1     Pending   0          15s
```
**Diagnose:**
```bash
kubectl describe pod greedy | grep -A4 Events
kubectl describe nodes | grep -A5 "Allocatable"
```
```
Warning  FailedScheduling  default-scheduler
  0/1 nodes are available: 1 Insufficient cpu, 1 Insufficient memory.
```
**Fix:** lower requests to something the node has.
```yaml
    resources: { requests: { cpu: "100m", memory: "128Mi" } }
```
```bash
kubectl apply -f huge.yaml   # after editing
```
**Verify:** `kubectl get pod greedy` → `Running`, NODE assigned.

---

### Lab D — Service with no Endpoints (selector mismatch)

**Broken YAML** (`svc-mismatch.yaml`):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: web }
spec:
  replicas: 2
  selector: { matchLabels: { app: web } }
  template:
    metadata: { labels: { app: web } }       # pods are labeled app=web
    spec:
      containers:
      - name: nginx
        image: nginx:1.27
        ports: [{ containerPort: 80 }]
---
apiVersion: v1
kind: Service
metadata: { name: web-svc }
spec:
  selector: { app: webserver }               # MISMATCH: webserver != web
  ports: [{ port: 80, targetPort: 80 }]
```
```bash
kubectl apply -f svc-mismatch.yaml
kubectl get endpoints web-svc
```
**Output:**
```
NAME      ENDPOINTS   AGE
web-svc   <none>      20s
```
**Diagnose:**
```bash
kubectl describe svc web-svc | grep -i selector
kubectl get pods --show-labels
```
```
Selector:  app=webserver
... pods show: app=web         # they don't match!
```
**Fix:** correct the Service selector.
```bash
kubectl patch svc web-svc -p '{"spec":{"selector":{"app":"web"}}}'
```
**Verify:**
```bash
kubectl get endpoints web-svc      # now lists 2 pod IPs:80
kubectl run t --rm -it --image=nicolaka/netshoot -- curl -s web-svc
```

---

### Lab E — DNS failure debugging

**Break it** (simulate by scaling CoreDNS to 0, then resolving):
```bash
kubectl -n kube-system scale deploy coredns --replicas=0
kubectl run dns --rm -it --image=nicolaka/netshoot -- nslookup kubernetes.default
```
**Output:**
```
;; connection timed out; no servers could be reached
```
**Diagnose:**
```bash
kubectl get pods -n kube-system -l k8s-app=kube-dns      # 0 running!
kubectl get svc -n kube-system kube-dns                  # ClusterIP still there
```
**Fix:**
```bash
kubectl -n kube-system scale deploy coredns --replicas=2
kubectl get pods -n kube-system -l k8s-app=kube-dns -w   # wait Running
```
**Verify:**
```bash
kubectl run dns --rm -it --image=nicolaka/netshoot -- nslookup kubernetes.default
# Server: 10.96.0.10  Address: kubernetes.default.svc.cluster.local
```

---

### Lab F (bonus) — ephemeral debug container into a distroless pod

`distroless` images have no shell, so `exec -- sh` fails. Use `kubectl debug`:
```bash
kubectl run app --image=gcr.io/distroless/static-debian12 --command -- /sleepforever 2>/dev/null
# attach a tools container sharing the target's process namespace:
kubectl debug -it app --image=nicolaka/netshoot --target=app
# inside: ps aux, netstat -tlnp, curl localhost:8080 — sees the app's PID namespace
```

---

## 5. ## EXERCISES

1. **Symptom:** A Deployment shows `0/3` ready, all pods `CrashLoopBackOff`, RESTARTS=8. What is your exact first command, and what do you look for to distinguish an app bug from a liveness-probe kill?
2. **Symptom:** A new pod is `Pending` for 10 minutes. `describe` shows no `FailedScheduling` event at all — the pod just sits in `ContainerCreating`. What category of problem is this (hint: not scheduling) and which two `describe` sections do you read?
3. **Symptom:** `curl my-svc` from another pod returns `connection refused` (not timeout). Endpoints are populated. What does "refused vs timeout" tell you, and what do you check next?
4. **Symptom:** A pod runs fine but a sidecar container is `CrashLoopBackOff`. The pod is `1/2`. Which log command targets only the broken container?
5. **Symptom:** After a node reboot, several pods are stuck `Terminating` for an hour. What's the usual cause and how do you force-resolve it safely?

*(Try before reading the Troubleshooting table — answers are embedded in Sections 6 & 9.)*

---

## 6. ## TROUBLESHOOTING SECTION (consolidated reference)

| # | Symptom | Likely causes | First commands | Fix |
|---|---------|---------------|----------------|-----|
| 1 | `CrashLoopBackOff` | app bug, bad cmd, missing env, OOM, liveness kill | `logs -p`, `describe` (Last State/exit) | fix app/env/probe/memory |
| 2 | `ImagePullBackOff` | typo, missing tag, no pull secret, rate limit | `describe` → Events | fix tag / add `imagePullSecrets` |
| 3 | `Pending` | no resources, taints, affinity, PVC unbound | `describe` → FailedScheduling; `get nodes`; `get pvc` | reduce requests / tolerations / fix SC |
| 4 | `ContainerCreating` (stuck) | volume mount fail, image huge, CNI issue | `describe` → Events; `get events` | fix volume/secret/CNI |
| 5 | `CreateContainerConfigError` | missing ConfigMap/Secret/key | `describe` → Events | create the referenced object |
| 6 | `OOMKilled` (exit 137) | mem limit too low / leak | `describe` Last State; `top pod` | raise limit / fix leak |
| 7 | Service unreachable, `endpoints <none>` | selector mismatch, no ready pods | `get endpoints`; `get pods --show-labels` | align selector / fix readiness |
| 8 | Service `connection refused` | app binds 127.0.0.1, wrong targetPort | `exec` `netstat -tlnp`; `describe svc` | bind 0.0.0.0 / fix port |
| 9 | DNS `cannot resolve` | CoreDNS down, dnsPolicy, NetworkPolicy | CoreDNS pods/logs; `nslookup` via netshoot | restart CoreDNS / fix policy |
| 10 | `Init:0/1` stuck | init container failing/waiting on dependency | `logs <pod> -c <init>`; `describe` | fix init cmd/dependency |
| 11 | Pod `Running` but `0/1 READY` | readiness probe failing | `describe` → Readiness events; `logs` | fix probe path/port/delay |
| 12 | Pod stuck `Terminating` | finalizers, long grace, lost node | `describe`; `get pod -o yaml \| grep finalizers` | `delete --grace-period=0 --force` (last resort) |
| 13 | `Error` / `ContainerCannotRun` (127/126) | bad entrypoint, not executable | `logs -p`; `describe` | fix command/permissions |
| 14 | Node `NotReady` | kubelet down, disk/mem pressure | `describe node`; `get events` | fix kubelet/pressure; drain |

---

## 7. ## QUIZ SECTION

**MCQ**

**Q1.** A container shows `Exit Code: 137`. What is the most likely cause?
A) Command not found  B) Graceful shutdown  C) OOMKilled / SIGKILL  D) Image pull failure

**Q2.** `kubectl get endpoints my-svc` returns `<none>`. The most likely root cause is:
A) Wrong Service type  B) Selector doesn't match any ready pod  C) DNS is down  D) NodePort exhausted

**Q3.** Which command shows the logs of the *crashed* previous container instance?
A) `kubectl logs <pod>`  B) `kubectl logs <pod> -f`  C) `kubectl logs <pod> --previous`  D) `kubectl describe <pod>`

**Short answer**

**Q4.** Explain the difference between `connection refused` and `connection timed out` when curling a Service, and what each implies.

**Q5.** Why might a perfectly healthy app still end up in `CrashLoopBackOff`, and how do you fix it without touching the app code?

**Scenario**

**Q6.** A pod is `Running 1/1` and the Service has correct endpoints, yet other pods get a timeout connecting to it. `kubectl exec` into the pod shows the process is up. List the next 3 checks in order.

---

### Answers

**Q1 → C.** 137 = 128 + 9 (SIGKILL); almost always memory-limit OOMKilled (`describe` confirms `Reason: OOMKilled`).

**Q2 → B.** No Endpoints means the Service selector matches zero *ready* pods — either label mismatch or failing readiness probes. Compare `describe svc` selector with `get pods --show-labels`.

**Q3 → C.** `--previous` (`-p`) reads the prior, dead container — essential for crash loops since current logs may be empty.

**Q4.** *Refused* = something replied actively rejecting the port (host reachable, nothing listening there / wrong port). *Timeout* = packets dropped silently (NetworkPolicy, firewall, wrong IP, app not bound on the interface). Refused → fix port/binding; timeout → fix network path/policy.

**Q5.** A liveness probe with too-short `initialDelaySeconds` keeps killing a slow-starting app. Fix by adding a `startupProbe` or increasing `initialDelaySeconds`/`failureThreshold` — no app change needed.

**Q6.** (1) `kubectl exec` → `netstat -tlnp` to confirm it listens on `0.0.0.0:<targetPort>` not `127.0.0.1`; (2) verify Service `targetPort` matches that port (`describe svc`); (3) check for a NetworkPolicy blocking ingress (`kubectl get netpol`), then test pod-to-pod with `netshoot`.

---

## 8. ## CHALLENGE PROJECT — "The 3 AM Production Incident"

You're paged: **"Checkout service is down."** Apply the manifest below and diagnose the cascade *in order*. Multiple faults are layered.

`incident.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: checkout, labels: { app: checkout } }
spec:
  replicas: 3
  selector: { matchLabels: { app: checkout } }
  template:
    metadata: { labels: { app: checkout } }
    spec:
      containers:
      - name: api
        image: nginx:1.27-alpne          # FAULT 1: typo in tag
        ports: [{ containerPort: 8080 }] # FAULT 3: nginx listens on 80, not 8080
        env:
        - name: DB_HOST
          valueFrom:
            configMapKeyRef: { name: checkout-config, key: db_host }  # FAULT 2: CM missing
        resources:
          requests: { cpu: "100m", memory: "64Mi" }
          limits:   { memory: "16Mi" }   # FAULT 5: limit < request territory / OOM risk
        readinessProbe:
          httpGet: { path: /, port: 8080 } # tied to FAULT 3
---
apiVersion: v1
kind: Service
metadata: { name: checkout }
spec:
  selector: { app: checkout-svc }          # FAULT 4: selector mismatch
  ports: [{ port: 80, targetPort: 8080 }]
```

**Your job:** restore the service. Diagnose top-down.

### Reference walkthrough

```bash
kubectl apply -f incident.yaml
kubectl get pods -o wide
```
Pods are `ImagePullBackOff`. Start there.

**Fault 1 — image tag typo.**
```bash
kubectl describe pod <p> | grep -A4 Events   # "Failed to pull nginx:1.27-alpne ... not found"
```
Fix: `1.27-alpne` → `1.27-alpine` (and bind to port 80).

**Fault 2 — missing ConfigMap.** After fixing the image, pods go `CreateContainerConfigError`.
```bash
kubectl describe pod <p>   # "configmap "checkout-config" not found"
kubectl create configmap checkout-config --from-literal=db_host=db.internal
```

**Fault 5 — OOMKilled.** Now pods start but flap `CrashLoopBackOff`, `Reason: OOMKilled`, exit 137 (16Mi is too tight for nginx under load).
```bash
kubectl describe pod <p> | grep -A2 "Last State"
```
Fix: raise `limits.memory` to `128Mi`.

**Fault 3 — wrong port / readiness.** Pods now `Running` but `0/3 READY`; readiness probes `/:8080` fail because nginx listens on 80.
```bash
kubectl describe pod <p> | grep -i readiness   # "Readiness probe failed: connection refused"
```
Fix: set `containerPort`, probe port, and Service `targetPort` all to `80`.

**Fault 4 — Service selector mismatch.** Even after pods are Ready, `curl checkout` fails; endpoints empty.
```bash
kubectl get endpoints checkout      # <none>
```
Fix: selector `app: checkout-svc` → `app: checkout`.

**Verify recovery:**
```bash
kubectl get pods                    # 3/3 Running, READY 1/1
kubectl get endpoints checkout      # 3 pod IPs:80
kubectl run t --rm -it --image=nicolaka/netshoot -- curl -s checkout | head
```
**Lesson:** fix faults in dependency order (image → config → resources → readiness → service wiring). Each fix surfaces the next symptom — that layering *is* real incident response.

---

## 9. ## KNOWLEDGE CHECK

You're ready for Day 10 if you can, without notes:

- [ ] Name the 4 commands that solve 80% of incidents, in order.
- [ ] Explain `logs` vs `logs --previous` and when each is essential.
- [ ] Map exit codes 0, 1, 127, 137, 143 to their meanings.
- [ ] Diagnose an empty-Endpoints Service in two commands.
- [ ] Distinguish `connection refused` from `timed out`.
- [ ] Read a `FailedScheduling` event and name the predicate that failed.
- [ ] Use `kubectl debug` to get a shell into a distroless pod.
- [ ] Identify OOMKilled from `describe` and fix it two ways.
- [ ] Debug DNS with `nslookup` + CoreDNS pod/log checks.

---

## 10. ## CHEAT SHEET — Troubleshooting Arsenal

```bash
# ── TRIAGE (always start here) ──────────────────────────────
kubectl get pods -o wide
kubectl get events --sort-by=.lastTimestamp | tail -30
kubectl describe pod <pod>
kubectl logs <pod> --previous            # crashed instance
kubectl logs <pod> -f --tail=100

# ── STATE & EXIT CODES ──────────────────────────────────────
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].state}{"\n"}'
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].lastState.terminated.exitCode}{"\n"}'
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].lastState.terminated.reason}{"\n"}'

# ── INSIDE THE CONTAINER ────────────────────────────────────
kubectl exec -it <pod> -- sh
kubectl exec -it <pod> -c <ctr> -- env
kubectl debug -it <pod> --image=nicolaka/netshoot --target=<ctr>   # distroless/no-shell
kubectl debug node/<node> -it --image=ubuntu                        # node host ns

# ── SERVICE / NETWORK ───────────────────────────────────────
kubectl get endpoints <svc>
kubectl describe svc <svc>
kubectl get pods --show-labels
kubectl get netpol
kubectl run t --rm -it --image=nicolaka/netshoot -- sh   # curl, nslookup, netstat, dig

# ── SCHEDULING / CAPACITY ───────────────────────────────────
kubectl get nodes
kubectl describe node <node> | grep -A6 Allocated
kubectl get pvc; kubectl describe pvc <pvc>
kubectl top pod; kubectl top node          # needs metrics-server

# ── NETWORK TUNNEL ──────────────────────────────────────────
kubectl port-forward <pod> 8080:80
kubectl port-forward svc/<svc> 8080:80

# ── LAST RESORT ─────────────────────────────────────────────
kubectl delete pod <pod> --grace-period=0 --force   # stuck Terminating
```

**Decision flow:** `get pods` → read STATUS → `describe` (Events + Last State) → `logs -p` → narrow to category → fix the *first* link in the chain → verify with `get endpoints`/`curl`.

---

## 11. ## INTERVIEW PREPARATION

**How interviewers test troubleshooting:**
1. They give a symptom ("pod won't start") and watch your *process*, not the answer.
2. They probe exit codes and the meaning of statuses.
3. They drill the Service→Endpoints→Pod chain.

**The framework to say out loud:**
> "First `kubectl get pods -o wide` to read the status. Based on the status I branch: Pending → describe for FailedScheduling; ImagePullBackOff → describe Events for the pull error; CrashLoop → `logs --previous` plus the Last State exit code; Running-but-unreachable → check `get endpoints`, then exec/curl. I always fix the first broken link and verify before moving on."

**Phrases that signal seniority:** "I'd check the *previous* container's logs", "an empty Endpoints means selector or readiness", "137 means OOMKilled, let me confirm in describe", "is it *refused* or *timeout*? that changes everything", "I'd attach an ephemeral debug container since it's distroless."

**Common traps:** forgetting `--previous` on crash loops; editing YAML before reading Events; confusing `port` (Service) vs `targetPort` (container); assuming Running == Ready.

---

## 12. ## 🎓 TOP 50 QUESTIONS

### Fundamentals (15)
1. What are the 4 first commands you run for any pod issue?
2. Difference between `kubectl logs` and `kubectl logs --previous`?
3. What does Pod status `Pending` mean? Name two distinct causes.
4. What does `CrashLoopBackOff` literally describe?
5. Difference between `ErrImagePull` and `ImagePullBackOff`?
6. What does exit code 137 mean? 127? 143? 1?
7. What's the difference between `Running` and `Ready`?
8. What does an empty `kubectl get endpoints` indicate?
9. What is `CreateContainerConfigError`?
10. What does `describe pod` show that `logs` does not?
11. Difference between liveness, readiness, and startup probes in failure behavior?
12. What is `OOMKilled` and which exit code accompanies it?
13. What's the backoff behavior of a crash loop (timing)?
14. What does `kubectl get events --sort-by=.lastTimestamp` give you?
15. What is the Service→Endpoints→Pod chain?

### Practical (10)
16. How do you get logs of a specific container in a multi-container pod?
17. How do you shell into a pod that has no shell (distroless)?
18. How do you check a node's available vs allocated resources?
19. How do you create a docker-registry pull secret?
20. How do you test DNS resolution from inside the cluster?
21. How do you port-forward a Service to your laptop?
22. How do you find the exit code via jsonpath?
23. How do you list pod labels to compare against a Service selector?
24. How do you force-delete a stuck Terminating pod?
25. How do you view live CPU/memory of pods?

### Scenario (10)
26. Pod `Pending` with `Insufficient cpu` — what do you do?
27. All replicas `ImagePullBackOff` after a deploy — first step?
28. Pod `Running 1/1` but Service times out — debug order?
29. Service has endpoints but `connection refused` — cause?
30. App `CrashLoopBackOff` only in prod, not dev — likely cause?
31. New pod stuck `ContainerCreating` 5 min — where do you look?
32. `Init:0/2` forever — how do you debug init containers?
33. Pods OOMKilled under load only — fix without raising limits unnecessarily?
34. DNS works for IPs but not service names — what's broken?
35. After scaling to 10 replicas, 3 stay Pending with anti-affinity — why?

### Troubleshooting (10)
36. How do you distinguish an app crash from a liveness-probe kill?
37. How do you tell a selector mismatch from a readiness failure (both → no traffic)?
38. `targetPort` vs `port` vs `nodePort` — which causes "refused"?
39. How do you confirm a container binds `0.0.0.0` not `127.0.0.1`?
40. How do you debug a NetworkPolicy blocking traffic?
41. How do you check why a PVC is `Pending`?
42. How do you read which scheduling predicate failed?
43. How do you debug a node that's `NotReady`?
44. How do you capture logs that are already gone after a restart?
45. How do you debug intermittent 5xx behind a Service (some pods bad)?

### Interview (5)
46. "Walk me through debugging a CrashLoopBackOff." (Expect the full methodology.)
47. "A service is down at 3 AM — what's your first 60 seconds?"
48. "Why is Running != Ready, and why does it matter for traffic?"
49. "How do ephemeral debug containers change distroless debugging?"
50. "Describe a layered incident you'd expect and how you'd peel it apart."

> **Self-grade:** confidently answer 45/50 before Day 10. Speak the *process*, not just the command.

---

## 13. ## FREE RESOURCES

| Resource | Type | Best for |
|----------|------|----------|
| kubernetes.io — *Debug Running Pods* | Official docs | exec, debug, ephemeral containers |
| kubernetes.io — *Debug Services* | Official docs | the endpoints/DNS chain |
| kubernetes.io — *Determine Reason for Pod Failure* | Official docs | exit codes, termination reasons |
| `nicolaka/netshoot` (GitHub) | Tool image | in-cluster network/DNS debugging |
| `kubectl debug` docs | Official docs | ephemeral + node debugging |
| killercoda.com Kubernetes scenarios | Interactive labs | free break/fix practice |
| CKA/CKAD curriculum (CNCF) | Reference | troubleshooting domain weighting |
| metrics-server (GitHub) | Add-on | enabling `kubectl top` |

**Docs reading plan (90 min):**
1. *Debug Running Pods* → practice `exec` + `debug` (30 min).
2. *Debug Services* → reproduce Lab D end-to-end (30 min).
3. *Pod Lifecycle* (states + probes) → map every status you saw today (30 min).

**Must-do:** Complete all 6 labs (A–F) yourself.
**Must-know:** exit codes + the Service→Endpoints chain + `logs --previous`.
**Must-build:** the Challenge Project incident from scratch and time your recovery.

**Highest-ROI:** Memorize the decision tree in §3.0 and the exit-code table — they appear in nearly every interview and incident.

---

## 14. ## NEXT STEPS

**Active recall (do now, no peeking):**
1. Recite the 4 triage commands in order.
2. From memory, name the cause of exit 137 and two fixes.
3. Explain in one sentence how to find an empty-Endpoints root cause.
4. State the difference between `refused` and `timeout`.
5. Describe how `kubectl debug` helps with a distroless image.

**Then reinforce:**
- Re-run Lab E (DNS) blindfolded — only look at output, not instructions.
- Rebuild the Challenge incident and beat your recovery time.
- Teach the decision tree to a peer (best retention test).

**✅ When you can answer 45/50 questions and recover the Challenge incident unaided — Continue to Day 10.**
