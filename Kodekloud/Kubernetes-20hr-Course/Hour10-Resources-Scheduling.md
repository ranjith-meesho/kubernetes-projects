# Hour 10: Resource Requests, Limits, Scheduling

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine booking a table at a restaurant. When you reserve, you tell the host "we need a table for 4" — that's your **request**: the guaranteed minimum space held for you. But once seated, there's also a house rule: "you may order up to $50 of food" — that's your **limit**: the hard ceiling you cannot cross. If you try to order more, the kitchen simply refuses (throttles) or, if you sneak extra food and get caught (memory), the manager throws you out entirely (OOMKilled). And if the restaurant runs out of food during a shortage, diners are served based on their **priority tier** — guests who reserved exactly what they eat (Guaranteed) get fed first, casual walk-ins with no reservation (BestEffort) get kicked out first.

**Technical version:**
- **Requests** are the guaranteed minimum amount of CPU/memory a container needs. The **kube-scheduler** uses requests (not actual usage) to decide which node a Pod can fit on — it sums up all requests already placed on a node and only schedules a new Pod if the node's remaining **allocatable** capacity can cover the new Pod's requests.
- **Limits** are the hard ceiling a container cannot exceed.
  - **CPU limit exceeded** → the container is **throttled** (CPU is compressible; the kernel's CFS scheduler just slows it down, no crash).
  - **Memory limit exceeded** → the container is **OOMKilled** (memory is incompressible; the kernel cannot "slow down" memory usage, so it kills the process).
- **QoS (Quality of Service) classes** — Kubernetes derives a QoS class per Pod automatically based on requests/limits:
  1. **Guaranteed** — every container has requests == limits for both CPU and memory. Highest priority; last to be evicted under node pressure.
  2. **Burstable** — at least one container has a request set, but requests != limits (or not all resources/containers have both set). Medium priority.
  3. **BestEffort** — no requests or limits set at all. Lowest priority; first to be evicted/OOMKilled under node pressure.
- **Scheduling flow:** The Scheduler filters nodes (predicates: does the node have enough allocatable CPU/memory left after subtracting existing requests?), then scores/ranks the remaining nodes, and binds the Pod to the best one. If **no node** has enough free (allocatable − already-requested) resources, the Pod stays in **`Pending`** state, and `kubectl describe pod` will show a `FailedScheduling` event like `0/3 nodes are available: insufficient memory`.
- Note: **actual usage** can exceed requests temporarily (bursting) as long as the node has spare capacity and the container stays under its limit — this is normal and expected for Burstable pods.

## 2. Diagram

```
Node Allocatable Capacity: 4 CPU / 8Gi Memory
┌──────────────────────────────────────────────────────────┐
│  Pod A            Pod B            Pod C        Free      │
│  req: 1 CPU/2Gi   req: 1 CPU/2Gi   req: 1 CPU/2Gi          │
│  lim: 2 CPU/2Gi   lim: 1 CPU/4Gi   lim: 1 CPU/2Gi           │
│ [■■■■■■]         [■■■■■■]         [■■■■■■]      [■■]      │
│  (Burstable)      (Burstable)      (Guaranteed)  1 CPU/2Gi │
└──────────────────────────────────────────────────────────┘
   Scheduler placed A, B, C because sum(requests) = 3 CPU/6Gi
   fits within 4 CPU/8Gi allocatable. Pod D needing 2 CPU
   would NOT fit (only 1 CPU free) --> stays Pending.

Memory Limit Exceeded --> OOMKilled:
┌───────────────────────┐
│   Container            │   memory limit: 100Mi
│   usage: 40Mi -> 90Mi  │   usage climbing...
│   usage: 110Mi  X───── │   kernel cgroup OOM killer fires
└───────────────────────┘
           │
           v
   Container killed, kubelet restarts it (per restartPolicy)
   kubectl get pods shows:
   NAME      READY   STATUS      RESTARTS
   mem-pod   0/1     OOMKilled   1 (then 2, 3... CrashLoopBackOff)

CPU Limit Exceeded --> Throttled (NOT killed):
┌───────────────────────┐
│   Container            │   CPU limit: 500m
│   wants: 1000m CPU     │   kernel CFS quota caps it at 500m
│   result: slower, but  │   process keeps running, just delayed
│   still running         │
└───────────────────────┘
```

## 3. Real-World Example

At a mid-size company running a shared Kubernetes cluster, a data team deploys a `report-generator` Pod with **no resource requests or limits set** ("it'll be fine, it's a small job"). Because it has no requests, the Scheduler treats its footprint as effectively zero and happily stacks 15 more such Pods onto the same node alongside a latency-sensitive `checkout-service`. When the report job runs its nightly batch, it consumes massive CPU — since none of these Pods declared limits, the kernel shares CPU cycles proportionally, and `checkout-service` (which also forgot requests/limits) gets starved: this is the classic **noisy-neighbor problem**. Checkout API latency spikes from 50ms to 3s, and the on-call engineer spends an hour bisecting the issue before finding the culprit via `kubectl top pods`.

Contrast this with a properly-sized Pod: `checkout-service` is redeployed with `requests: {cpu: 500m, memory: 256Mi}` and `limits: {cpu: 1000m, memory: 512Mi}`. Now the Scheduler reserves guaranteed CPU/memory for it on every node it lands on, and even if `report-generator` bursts, `checkout-service`'s guaranteed share is protected by the kernel's cgroup CPU shares. Behavior becomes predictable, and Meesho-style production clusters enforce this via **LimitRange** and **ResourceQuota** objects at the namespace level so no team can skip setting requests/limits.

## 4. Hands-On Lab

**Goal:** Observe how the Scheduler allocates resources, and intentionally trigger an OOMKill.

**Step 1 — Deploy a Pod with requests/limits:**
```yaml
# sized-pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: sized-pod
spec:
  containers:
  - name: app
    image: nginx
    resources:
      requests:
        cpu: "250m"
        memory: "128Mi"
      limits:
        cpu: "500m"
        memory: "256Mi"
```

```bash
kubectl apply -f sized-pod.yaml
kubectl get pod sized-pod
```

**Step 2 — See allocated resources on the node:**
```bash
kubectl describe node minikube
```

**Expected output (excerpt):**
```
Allocated resources:
  (Total limits may be over 100 percent, i.e., overcommitted.)
  Resource           Requests     Limits
  --------           --------     ------
  cpu                250m (12%)   500m (25%)
  memory              128Mi (3%)   256Mi (6%)

Non-terminated Pods: (1 in total)
  Namespace   Name         CPU Requests   CPU Limits   Memory Requests   Memory Limits
  default     sized-pod    250m (12%)     500m (25%)   128Mi (3%)        256Mi (6%)
```

**Step 3 — Trigger an OOMKill on purpose:**
```yaml
# oom-pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: oom-pod
spec:
  containers:
  - name: memory-hog
    image: polinux/stress
    resources:
      requests:
        memory: "50Mi"
      limits:
        memory: "100Mi"     # deliberately low
    command: ["stress"]
    args: ["--vm", "1", "--vm-bytes", "300M", "--vm-hang", "1"]  # tries to allocate 300Mi
```

```bash
kubectl apply -f oom-pod.yaml
kubectl get pods -w
```

**Expected output:**
```
NAME      READY   STATUS      RESTARTS   AGE
oom-pod   0/1     Pending     0          2s
oom-pod   1/1     Running     0          5s
oom-pod   0/1     OOMKilled   1          8s
oom-pod   1/1     Running     1          20s
oom-pod   0/1     OOMKilled   2          23s
oom-pod   0/1     CrashLoopBackOff   2   35s
```

**Step 4 — Confirm the reason:**
```bash
kubectl describe pod oom-pod
```

**Expected output (excerpt):**
```
Last State:     Terminated
  Reason:       OOMKilled
  Exit Code:    137
```

**Troubleshooting:**
- If it never OOMKills, increase `--vm-bytes` beyond the limit, or lower the `memory` limit further.
- `Exit Code: 137` = 128 + 9 (SIGKILL) — a strong signal of OOMKill; also check `dmesg`/kubelet logs on the node for `Memory cgroup out of memory` if debugging a real cluster.
- Clean up: `kubectl delete pod sized-pod oom-pod`

## 5. Common Mistakes

1. **Not setting requests/limits at all** — the Pod becomes BestEffort, the Scheduler can massively overcommit the node (since it thinks the Pod needs "0"), leading to unpredictable scheduling and noisy-neighbor issues.
2. **Setting limits without requests** — Kubernetes actually defaults the request to equal the limit in this case, which can silently make Pods harder to schedule (they now reserve the full limit) — surprising if you intended requests to be lower.
3. **Setting requests too high "to be safe"** — wastes cluster capacity because the Scheduler reserves that much on a node even if actual usage is far lower, causing fewer Pods to fit and higher infrastructure cost.
4. **Confusing CPU throttling (soft) with memory OOMKill (hard)** — exceeding a CPU limit only slows the container down (compressible resource); exceeding a memory limit kills the container outright (incompressible resource). Engineers sometimes panic about CPU limits causing crashes, when in fact only memory limits do.
5. **Assuming requests reflect real-time usage** — the Scheduler only looks at declared requests at scheduling time, not live metrics; a Pod can still be starved after scheduling if it and its neighbors all burst simultaneously past their requests.
6. **Forgetting namespace-level guardrails** — without a `LimitRange` (default requests/limits) or `ResourceQuota` (namespace-wide caps), teams can accidentally deploy unsized Pods that destabilize shared clusters.

## 6. Interview Questions (with brief answers)

1. **What happens when a container exceeds its memory limit?** — The kernel's cgroup OOM killer terminates the container (SIGKILL, exit code 137); Kubernetes reports the Pod status as `OOMKilled` and restarts it according to `restartPolicy`, potentially leading to `CrashLoopBackOff` if it keeps happening.
2. **What happens when a container exceeds its CPU limit?** — It is throttled by the kernel's CFS quota mechanism — it keeps running but is allotted less CPU time, causing slower performance, not a crash.
3. **What is a QoS class, and what are the three types?** — A priority tier Kubernetes derives from a Pod's requests/limits, used to decide eviction order under node resource pressure. **Guaranteed** (requests == limits, all resources) is evicted last; **Burstable** (some requests set) is evicted next; **BestEffort** (no requests/limits) is evicted first.
4. **How does the Scheduler decide where to place a Pod based on resources?** — It sums the resource requests of all Pods already on each node, compares against that node's allocatable capacity, filters out nodes that don't have enough headroom for the new Pod's requests, then scores/ranks the remaining candidates and binds the Pod to the best one.
5. **What happens if no node has enough resources for a Pod?** — The Pod remains in `Pending` state; `kubectl describe pod` shows a `FailedScheduling` event (e.g., "0/3 nodes are available: insufficient cpu"), and it stays there until capacity frees up or is added (e.g., cluster autoscaler adds a node).

## 7. Quiz (50 Questions)

**True/False:**
1. Resource requests are used by the Scheduler to decide Pod placement. (T)
2. Exceeding a memory limit results in the container being throttled, not killed. (F)
3. Exceeding a CPU limit results in the container being killed. (F)
4. A Pod with no requests or limits set is classified as BestEffort QoS. (T)
5. Guaranteed QoS requires requests to equal limits for both CPU and memory. (T)
6. If a node lacks enough allocatable resources, the Pod is deleted automatically. (F)
7. CPU is a compressible resource; memory is not. (T)
8. `kubectl describe node` shows allocated resources per Pod. (T)
9. An OOMKilled container always has exit code 137. (T)
10. Setting only a limit (no request) means Kubernetes defaults the request to match the limit. (T)
11. The Scheduler considers live/current usage, not just declared requests, when placing new Pods. (F)
12. BestEffort Pods are the first to be evicted under node memory pressure. (T)
13. A Burstable Pod can use more CPU than its request if the node has spare capacity. (T)
14. ResourceQuota limits resources at the individual Pod level, not the namespace level. (F)
15. LimitRange can supply default requests/limits when a Pod doesn't specify them. (T)

**Multiple Choice:**
16. What does the Scheduler use to decide if a Pod fits on a node? a) Actual live usage b) Resource requests c) Resource limits d) Pod name → (b)
17. What happens when a container exceeds its memory limit? a) Throttled b) OOMKilled c) Ignored d) Automatically rescheduled → (b)
18. What happens when a container exceeds its CPU limit? a) OOMKilled b) Throttled c) Node crashes d) Pod deleted → (b)
19. Which QoS class has the highest priority (last evicted)? a) BestEffort b) Burstable c) Guaranteed d) Unknown → (c)
20. Which QoS class results from setting no requests or limits at all? a) Guaranteed b) Burstable c) BestEffort d) None assigned → (c)
21. What state does a Pod enter if no node has enough resources? a) Failed b) CrashLoopBackOff c) Pending d) Terminating → (c)
22. What exit code typically indicates an OOMKill? a) 0 b) 1 c) 137 d) 255 → (c)
23. Which Kubernetes object sets default requests/limits per namespace? a) ResourceQuota b) LimitRange c) NetworkPolicy d) PodDisruptionBudget → (b)
24. Which Kubernetes object caps total resource consumption across a namespace? a) LimitRange b) ResourceQuota c) HorizontalPodAutoscaler d) PriorityClass → (b)
25. What command shows how much of a node's capacity is allocated? a) kubectl get node b) kubectl top node c) kubectl describe node d) kubectl logs node → (c)

**Short Answer:**
26. In one sentence, define a "resource request."
27. In one sentence, define a "resource limit."
28. Why is memory called an "incompressible" resource?
29. Why is CPU called a "compressible" resource?
30. What field in `kubectl describe pod` output tells you a container was OOMKilled?
31. What is the restaurant analogy for a resource request?
32. What is the restaurant analogy for a resource limit?
33. What is the restaurant analogy for QoS class priority?
34. Why might setting requests too high waste cluster capacity?
35. What happens to a Pod's requests if only a limit is specified?

**Scenario-Based:**
36. A Pod has no requests/limits and is co-located with a latency-sensitive service; the node experiences CPU contention. What's likely happening and how would you fix it?
37. Your Pod keeps restarting with status `OOMKilled`. What are your first two troubleshooting steps?
38. A new Pod stays `Pending` for 10 minutes. What commands do you run to diagnose why, and what might you find?
39. Your team wants to guarantee a critical Pod is the last one evicted during a node memory shortage. What QoS class should you target, and how?
40. A batch job needs to burst above its normal CPU usage occasionally but shouldn't be guaranteed extra capacity at all times. What QoS class fits, and how would you configure requests/limits?

**Fill in the Blank:**
41. The Scheduler uses resource ______ (not usage) to decide Pod placement.
42. A container that exceeds its CPU limit is ______, not killed.
43. A container that exceeds its memory limit is ______.
44. The three QoS classes are Guaranteed, Burstable, and ______.
45. A Pod with requests == limits for all containers and resources is classified as ______ QoS.

**Conceptual Deep-Dive:**
46. Why does Kubernetes treat CPU and memory limit violations differently (throttle vs kill)?
47. How does QoS class influence eviction order during node resource pressure?
48. Why can a node be "overcommitted" on limits but not on requests?
49. What's the risk of relying only on limits without setting requests?
50. How would a cluster autoscaler interact with Pods stuck in `Pending` due to insufficient resources?

---

## 8. Hour 10 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Request** | Guaranteed minimum resource reserved; used by Scheduler for placement decisions |
| **Limit** | Hard ceiling a container cannot exceed |
| **CPU over limit** | Throttled (compressible resource, container keeps running, just slower) |
| **Memory over limit** | OOMKilled (incompressible resource, kernel kills the process, exit code 137) |
| **QoS: Guaranteed** | requests == limits for all resources/containers; evicted last |
| **QoS: Burstable** | at least one request set, requests != limits; evicted second |
| **QoS: BestEffort** | no requests/limits at all; evicted first |
| **No node fits** | Pod stays `Pending`; check events via `kubectl describe pod` |
| **Guardrails** | LimitRange (defaults) + ResourceQuota (namespace caps) prevent unsized Pods |
| **Mental model** | Requests = reserved table size; limits = max food order; QoS = priority tier when food runs out |

**Mnemonic:** *"RL-Q-P"* — **R**equests reserve, **L**imits restrain, **Q**oS prioritizes, **P**ending happens when nothing fits.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Managing Resources for Containers](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/) (the canonical reference for requests/limits)
- [Kubernetes Official Docs — Resource Quality of Service](https://kubernetes.io/docs/tasks/configure-pod-container/quality-service-pod/)
- [Kubernetes Official Docs — Resource Quotas](https://kubernetes.io/docs/concepts/policy/resource-quotas/)
- [Kubernetes Official Docs — Limit Ranges](https://kubernetes.io/docs/concepts/policy/limit-range/)
- YouTube: "TechWorld with Nana — Kubernetes Resource Requests and Limits Explained" (free, excellent visuals)

**Mini-Project for Hour 10 (optional, 20 min):**
- Deploy a Pod with a deliberately tiny memory limit (e.g., `64Mi`) running a memory-hungry `stress` command that tries to allocate more than that. Confirm via `kubectl get pods` and `kubectl describe pod` that it gets `OOMKilled` with exit code 137. Then "fix" it by raising the memory limit (and setting a matching request for Guaranteed QoS) so the same workload runs successfully without restarts. Compare `kubectl describe pod` output (QoS class field) before and after your fix.

