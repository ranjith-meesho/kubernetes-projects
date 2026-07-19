# Hour 13: Horizontal Pod Autoscaler, Scaling

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine a restaurant on a normal Tuesday afternoon — 2 waitstaff are enough. Then a bus tour arrives and the dining room fills up. A smart **restaurant manager** watches how busy each waiter is (how "loaded" they are) and calls in more staff from the back room as tables fill, up to a maximum crew size. When the tour bus leaves and things quiet down, the manager sends extra staff home — but never below a minimum crew, because the doors are still open. That manager is the **Horizontal Pod Autoscaler (HPA)**.

**Manual scaling** is you, the engineer, deciding "we need more waiters" and physically walking into the back room to grab them — i.e., running `kubectl scale deployment checkout --replicas=10`. It works, but you have to be watching, and you have to react fast enough.

**Technical version:**
- **Manual scaling:** `kubectl scale deployment <name> --replicas=N` directly sets the replica count on a Deployment. Simple, but reactive and requires a human (or an external script) to notice load and act.
- **Horizontal Pod Autoscaler (HPA):** A Kubernetes controller that automatically adjusts the **replica count** of a Deployment/ReplicaSet/StatefulSet based on observed metrics — most commonly **CPU utilization %** or **memory utilization %**, but also **custom metrics** (queue length, requests/sec) via the custom metrics API.
- **Prerequisite — resource requests (ties back to Hour 10):** HPA computes utilization as `(current usage) / (requested amount)`. If a Pod has no `resources.requests.cpu` set, Kubernetes has no denominator, and HPA cannot calculate a percentage — it will show `<unknown>` for that metric.
- **metrics-server:** A cluster add-on that scrapes CPU/memory usage from the kubelet on each node via the `resource metrics API` (`metrics.k8s.io`). HPA queries metrics-server periodically (every 15 seconds by default) to get current usage.
- **HPA control loop mechanics:**
  1. HPA controller reads the current average CPU utilization % across all Pods in the target (via metrics-server).
  2. It compares this to the **target** utilization % you configured (e.g., 50%).
  3. It computes `desiredReplicas = ceil(currentReplicas * (currentMetricValue / desiredMetricValue))`.
  4. The result is clamped between `minReplicas` and `maxReplicas`.
  5. It updates the Deployment's `spec.replicas`, and the normal ReplicaSet controller creates/removes Pods.
  6. This loop repeats on a sync period (default ~15s), with a **stabilization window** to avoid flapping (scaling up and down repeatedly).
- **Vertical Pod Autoscaler (VPA)** — a *different* concept: instead of adding more Pods, VPA adjusts the CPU/memory **requests/limits of existing Pods** (making each Pod bigger or smaller). It typically requires restarting Pods to apply new resource values, so it's less seamless than HPA for live traffic.
- **Cluster Autoscaler (CA)** — scales the **nodes** themselves, not Pods. If HPA wants to create more Pods but the cluster has no room (nodes are full), Cluster Autoscaler adds new nodes to the cloud provider's node pool. Conversely, it removes underutilized nodes to save cost. HPA, VPA, and CA solve different layers of the same "not enough / too much capacity" problem and often work together: HPA scales Pods → Cluster Autoscaler scales Nodes to make room for those Pods.

## 2. Diagram

```
                     ┌─────────────────────┐
                     │   metrics-server     │
                     │ (scrapes kubelets)   │
                     └──────────┬───────────┘
                                │  CPU % / Memory %
                                ▼
                     ┌─────────────────────┐
                     │   HPA Controller     │
                     │ target: 50% CPU      │
                     │ min: 3   max: 15     │
                     └──────────┬───────────┘
                                │ adjusts replicas
                                ▼
      ┌─────────────────────────────────────────────────┐
      │                Deployment: checkout              │
      │                                                    │
      │  Low load:        [Pod][Pod][Pod]                 │
      │                    (3 replicas, ~min)              │
      │                                                    │
      │  High load (flash sale):                          │
      │  [Pod][Pod][Pod][Pod][Pod][Pod][Pod]...[Pod]        │
      │                    (up to 15 replicas, ~max)       │
      └─────────────────────────────────────────────────┘

Control loop (repeats every ~15s):
 measure CPU% → compare to target → compute desired replicas →
 clamp to [min,max] → apply stabilization window → update replicas → repeat
```

## 3. Real-World Example

An **e-commerce checkout service** normally runs with 3 replicas handling steady traffic. A **flash sale** starts at 12:00 PM — traffic to `/checkout` spikes 8x within minutes. CPU utilization on the existing Pods jumps from 30% to 90%, well above the HPA's target of 50%. The HPA controller notices this on its next sync, calculates it needs roughly 5-6x more capacity, and scales the Deployment from 3 → 15 Pods (hitting the configured `maxReplicas: 15`) within a couple of minutes. Customers experience no slowdown. Once the flash sale ends and traffic drops back to normal, average CPU falls below target, and the HPA gradually scales the Deployment back down to 3 Pods (respecting `minReplicas: 3` and the scale-down stabilization window so it doesn't yo-yo). No engineer touched `kubectl` during the entire event — this is exactly how Meesho, Amazon, and Flipkart handle sale-day traffic spikes.

## 4. Hands-On Lab

**Goal:** Install metrics-server, deploy an app with resource requests, create an HPA, generate load, and watch it scale up and back down.

```bash
# 1. Enable metrics-server on minikube
minikube addons enable metrics-server

# Verify it's running (may take ~1 min to become Ready)
kubectl get pods -n kube-system | grep metrics-server
kubectl top nodes     # should show CPU/memory usage once ready
```

**Expected output for `kubectl top nodes`:**
```
NAME       CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
minikube   250m         12%    1200Mi          30%
```

```bash
# 2. Deploy an app WITH resource requests (required for HPA math)
cat <<EOF > php-apache.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: php-apache
spec:
  selector:
    matchLabels:
      run: php-apache
  replicas: 1
  template:
    metadata:
      labels:
        run: php-apache
    spec:
      containers:
      - name: php-apache
        image: registry.k8s.io/hpa-example
        ports:
        - containerPort: 80
        resources:
          requests:
            cpu: 200m
          limits:
            cpu: 500m
---
apiVersion: v1
kind: Service
metadata:
  name: php-apache
spec:
  ports:
  - port: 80
  selector:
    run: php-apache
EOF

kubectl apply -f php-apache.yaml

# 3. Create the HPA - option A: imperative
kubectl autoscale deployment php-apache --cpu-percent=50 --min=1 --max=10

# option B: declarative YAML (equivalent)
cat <<EOF > hpa.yaml
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
EOF
# kubectl apply -f hpa.yaml   # (use either autoscale OR this, not both)

kubectl get hpa
```

**Expected output for `kubectl get hpa` (before load):**
```
NAME         REFERENCE               TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
php-apache   Deployment/php-apache   0%/50%    1         10        1          30s
```

```bash
# 4. Generate load in a separate terminal
kubectl run load-generator --rm -it --image=busybox:1.28 --restart=Never -- \
  /bin/sh -c "while true; do wget -q -O- http://php-apache; done"
```

```bash
# 5. Watch the HPA react (in another terminal)
kubectl get hpa php-apache --watch
```

**Expected output while under load:**
```
NAME         REFERENCE               TARGETS     MINPODS   MAXPODS   REPLICAS   AGE
php-apache   Deployment/php-apache   250%/50%    1         10        1          2m
php-apache   Deployment/php-apache   250%/50%    1         10        4          2m30s
php-apache   Deployment/php-apache   117%/50%    1         10        7          3m
php-apache   Deployment/php-apache   45%/50%     1         10        7          4m
```

```bash
# 6. Stop the load generator (Ctrl+C, then delete the pod if still running)
kubectl delete pod load-generator --ignore-not-found

# 7. Watch it scale back down (can take several minutes due to stabilization window)
kubectl get hpa php-apache --watch
```

**Expected output after load stops (scale-down is slower/more conservative by design):**
```
NAME         REFERENCE               TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
php-apache   Deployment/php-apache   0%/50%    1         10        7          6m
php-apache   Deployment/php-apache   0%/50%    1         10        1          11m
```

**Troubleshooting:**
- `kubectl get hpa` shows `<unknown>/50%` → metrics-server isn't ready yet, or the Deployment has no CPU `requests` set.
- Load generator pod stuck `Pending` → check `kubectl describe pod load-generator` for scheduling issues (resource pressure on minikube's single node).

## 5. Common Mistakes

1. **No resource requests set on the Pod spec.** HPA needs `requests.cpu` (or memory) to compute a percentage. Without it, `kubectl get hpa` shows `<unknown>` forever and never scales.
2. **metrics-server not installed/enabled.** On minikube this means forgetting `minikube addons enable metrics-server`; on a real cluster it means the metrics-server Deployment was never deployed. Symptom: `unable to get metrics for resource cpu` in `kubectl describe hpa`.
3. **min/max replicas set too tight, causing thrashing.** E.g., `min: 1, max: 2` with a bursty workload can cause rapid scale up/down cycles ("flapping") that disrupt connections and waste churn. Set a realistic range with headroom, and rely on the **stabilization window** (`behavior.scaleDown.stabilizationWindowSeconds` in `autoscaling/v2`) to smooth out short-lived spikes/dips before acting.
4. **Scaling triggers too aggressive/frequent.** Reacting to every 15-second metric blip causes needless churn. The stabilization window (default 300s for scale-down, 0s for scale-up in newer versions) makes the HPA look at a window of recent recommendations and pick the safest one, rather than reacting instantly to noise.
5. **Forgetting that HPA changes `replicas` but something else (e.g., a GitOps tool) keeps resetting it.** If ArgoCD/Flux syncs the Deployment manifest with a hardcoded `replicas: 3`, it will fight the HPA. Exclude `spec.replicas` from sync (e.g., ArgoCD's `ignoreDifferences`) when HPA manages it.
6. **Confusing HPA with VPA or Cluster Autoscaler and expecting one to do the other's job.** HPA adds Pods; VPA resizes existing Pods; Cluster Autoscaler adds Nodes. If nodes are full, HPA can create Pods that stay `Pending` forever unless Cluster Autoscaler (or manual capacity) is also in play.

## 6. Interview Questions (with brief answers)

1. **How does HPA decide when to scale?** — It periodically (default every 15s) polls metrics-server for the current average utilization of the target metric (e.g., CPU%) across Pods, compares it to the target you configured, computes `desiredReplicas = ceil(currentReplicas * currentMetric/desiredMetric)`, clamps it to `[minReplicas, maxReplicas]`, and applies it — subject to a stabilization window that prevents rapid flapping.
2. **HPA vs VPA vs Cluster Autoscaler — what's the difference?** — HPA scales the *number* of Pods (horizontal). VPA scales the *size* (CPU/memory requests) of existing Pods (vertical), usually requiring a Pod restart. Cluster Autoscaler scales the number of *Nodes* in the cluster so there's physical capacity for Pods to be scheduled on. They operate at different layers and can be combined (though HPA + VPA on the same metric can conflict).
3. **Why does HPA require resource requests to be set?** — Utilization is calculated as a percentage of the requested amount (`usage / request`). Without a request, there's no baseline to divide by, so Kubernetes cannot compute a meaningful percentage — the metric shows as `<unknown>`.
4. **What is metrics-server and why is it required for HPA (CPU/memory based)?** — It's a cluster add-on that collects resource usage (CPU/memory) from each node's kubelet and exposes it via the `metrics.k8s.io` API. HPA (for the default `Resource` metric type) queries this API to get current utilization; without it, CPU/memory-based HPA cannot function (though custom/external metrics via other adapters can still work without metrics-server).
5. **What's the difference between manual scaling and HPA, and when would you still use manual scaling?** — Manual scaling (`kubectl scale`) is a one-time, human-triggered change to replica count; HPA continuously and automatically adjusts replicas based on live metrics. You'd still use manual scaling for predictable, scheduled events where you want a guaranteed floor ahead of time (e.g., pre-scaling before a known flash sale starts) or when debugging, since HPA will eventually override manual changes on its next reconcile if it's active.

## 7. Quiz (50 Questions)

**True/False:**
1. HPA can scale a Deployment based on CPU utilization percentage. (T)
2. HPA changes the size (CPU/memory limits) of existing Pods. (F)
3. metrics-server is required for CPU/memory-based HPA to function. (T)
4. Manual scaling with `kubectl scale` is automatic and reacts to live traffic. (F)
5. HPA requires resource requests to be set on the target Pods for CPU/memory metrics. (T)
6. Vertical Pod Autoscaler adds more Pod replicas. (F)
7. Cluster Autoscaler adds or removes Nodes based on scheduling pressure. (T)
8. The HPA control loop checks metrics only once, at creation time. (F)
9. HPA respects a configured minReplicas floor and maxReplicas ceiling. (T)
10. A stabilization window helps prevent rapid scale up/down flapping. (T)
11. `minikube addons enable metrics-server` installs the metrics-server add-on. (T)
12. HPA can only use CPU as a metric, never memory or custom metrics. (F)
13. If metrics-server isn't running, `kubectl get hpa` will show `<unknown>` for targets. (T)
14. HPA and Cluster Autoscaler always must be used together. (F)
15. Scale-down typically happens more conservatively/slowly than scale-up by default. (T)

**Multiple Choice:**
16. What command imperatively creates an HPA? a) `kubectl scale` b) `kubectl autoscale` c) `kubectl expose` d) `kubectl top` → (b)
17. What does HPA use to calculate CPU utilization %? a) Node capacity only b) usage divided by requested amount c) usage divided by limit d) a fixed constant → (b)
18. Which component collects CPU/memory usage data for HPA? a) kube-scheduler b) metrics-server c) etcd d) kube-proxy → (b)
19. What does VPA adjust? a) Number of replicas b) Node count c) Pod resource requests/limits d) Service endpoints → (c)
20. What does Cluster Autoscaler scale? a) Pods b) Containers c) Nodes d) Namespaces → (c)
21. What happens if you set an HPA target CPU of 50% but never set resource requests? a) HPA scales perfectly b) HPA shows <unknown> and can't scale c) HPA defaults to 100% requests d) Pods crash → (b)
22. Which API group provides HPA's `HorizontalPodAutoscaler` resource in modern manifests? a) autoscaling/v1 only b) autoscaling/v2 c) apps/v1 d) batch/v1 → (b)
23. What prevents HPA from rapidly flapping replicas up and down? a) minReplicas b) resource limits c) stabilization window d) node affinity → (c)
24. In the formula `desiredReplicas = ceil(currentReplicas * currentMetric/desiredMetric)`, if currentMetric=100% and desiredMetric=50% with 2 current replicas, what's the result? a) 1 b) 2 c) 4 d) 8 → (c)
25. Which of these is NOT true about manual scaling? a) It's triggered by a human/script b) It reacts instantly to live load automatically c) It uses `kubectl scale` d) It sets a static replica count → (b)

**Short Answer:**
26. What metric does HPA use by default when you run `kubectl autoscale deployment X --cpu-percent=50`?
27. Why must Pods have `resources.requests.cpu` defined for CPU-based HPA to work?
28. What command enables metrics-server on minikube?
29. What is the default HPA sync/check interval (roughly)?
30. What is the difference between HPA's scale-up and scale-down responsiveness by default?
31. Name one type of metric besides CPU that HPA can use.
32. What command lets you watch HPA status live?
33. What field in the HPA spec sets the floor on replica count?
34. What field in the HPA spec sets the ceiling on replica count?
35. What tool/command can generate simple HTTP load against a service for testing HPA?

**Scenario-Based:**
36. Your HPA shows `<unknown>/50%` under TARGETS — what are the two most likely causes?
37. Your checkout service needs to handle a scheduled flash sale you know about in advance — should you rely purely on HPA, or also consider manual pre-scaling? Why?
38. Your HPA min/max is set to `min:1, max:2` and it keeps flapping between 1 and 2 replicas during normal traffic. What's the likely fix?
39. Your cluster's Nodes are all at capacity, and the HPA wants to scale a Deployment from 5 to 12 Pods, but new Pods stay Pending. What's missing?
40. Your GitOps tool keeps resetting your Deployment's replica count to 3, undoing the HPA's scaling decisions. What should you configure?

**Fill in the Blank:**
41. HPA stands for ______ ______ ______.
42. VPA stands for ______ ______ ______.
43. The Kubernetes add-on that exposes CPU/memory metrics for HPA is called ______.
44. The imperative command to create an HPA targeting 50% CPU is `kubectl ______ deployment <name> --cpu-percent=50`.
45. The HPA field that prevents flapping by smoothing recent recommendations is called the ______ ______.

**Conceptual Deep-Dive:**
46. Explain why HPA, VPA, and Cluster Autoscaler solve different problems even though they're all "autoscaling."
47. Why is scale-down usually more conservative (slower) than scale-up in HPA's default behavior?
48. How does the restaurant manager analogy map to HPA's min/max replica bounds?
49. What could go wrong if you set `maxReplicas` far higher than your cluster's actual node capacity?
50. In your own words, walk through the full HPA control loop from metric collection to replica count change.

---

## 8. Hour 13 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Manual scaling** | `kubectl scale deployment X --replicas=N` — human/script-triggered, static, reactive |
| **HPA** | Automatically adjusts replica count based on observed CPU%, memory%, or custom metrics |
| **Prerequisite** | Resource `requests` must be set (Hour 10) — HPA needs them to compute utilization % |
| **metrics-server** | Add-on that feeds CPU/memory usage data to the HPA controller |
| **Control loop** | Measure → compare to target → compute desired replicas → clamp to min/max → apply (with stabilization window) |
| **VPA (different!)** | Resizes existing Pods' resource requests/limits, doesn't add more Pods |
| **Cluster Autoscaler (different!)** | Scales Nodes so there's physical capacity for Pods to land on |
| **Mental model** | HPA = restaurant manager calling in/sending home waitstaff based on how busy the dining room is, within a min/max crew size |
| **Lab outcome** | You installed metrics-server, created an HPA, generated load, and watched replicas scale up then back down |

**Mnemonic:** *"MMSS"* — **M**etrics-server feeds data → **M**easure against target → **S**cale within min/max → **S**tabilize to avoid flapping.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Horizontal Pod Autoscaling](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/) (the definitive reference, including the `autoscaling/v2` API and scaling behavior/stabilization windows)
- [Kubernetes Official Docs — HPA Walkthrough](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/) (the exact `php-apache` example used in this lab)
- [metrics-server GitHub](https://github.com/kubernetes-sigs/metrics-server) — installation and troubleshooting details
- [Kubernetes Official Docs — Vertical Pod Autoscaler concept](https://kubernetes.io/docs/concepts/workloads/autoscaling/) (overview of HPA/VPA/CA side by side)
- YouTube: "TechWorld with Nana — Kubernetes HPA Explained" (free, visual walkthrough)

**Mini-Project for Hour 13 (30 min):**
- Deploy the `php-apache` example from this lab (or your own app with resource requests set) on minikube with metrics-server enabled.
- Create an HPA with `min=1, max=6, target CPU=50%`.
- In one terminal, run the busybox `load-generator` loop against the service; in another, run `kubectl get hpa --watch` and `kubectl get pods --watch` side by side.
- Record (screenshot or notes) the replica count at t=0, t=1min, t=3min while load is running, then stop the load and record how long it takes to scale back down to `minReplicas`. Compare scale-up speed vs scale-down speed and note why they differ (stabilization window).
