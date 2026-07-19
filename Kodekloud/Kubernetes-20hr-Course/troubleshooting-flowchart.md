# Kubernetes Troubleshooting Flowcharts

Quick-reference decision trees for the three most common classes of Kubernetes
incidents. At every decision node, the command to run is listed immediately
below it.

---

## 1. Pod Not Running

```
                        ┌────────────────────────────┐
                        │  kubectl get pods           │
                        │  (check STATUS column)      │
                        └──────────────┬─────────────┘
                                        │
        ┌──────────────┬───────────────┼───────────────┬───────────────────┐
        ▼               ▼               ▼               ▼                   ▼
    "Pending"     "ImagePullBackOff" "CrashLoopBackOff" "ContainerCreating" "OOMKilled"/"Evicted"
        │           / "ErrImagePull"        │            (stuck > 1-2 min)         │
        │                  │                │                   │                 │
        ▼                  ▼                ▼                   ▼                 ▼
   See 1A            See 1B            See 1C              See 1D            See 1E
```

### 1A. Pending

```
kubectl describe pod <pod>   →  read "Events" section at the bottom
        │
        ├─ "Insufficient cpu/memory"
        │      → Node(s) lack capacity for the requested resources.
        │      → kubectl top nodes ; kubectl describe nodes | grep -A5 Allocated
        │      → Fix: lower requests, add nodes, or enable cluster-autoscaler.
        │
        ├─ "0/N nodes are available: node(s) had taint..."
        │      → Missing toleration for a tainted node, or no matching nodeSelector/affinity.
        │      → kubectl get nodes -o json | jq '.items[].spec.taints'
        │      → Fix: add toleration or fix nodeSelector/affinity rules.
        │
        ├─ "No PersistentVolume/PVC bound"
        │      → kubectl get pvc  (check STATUS = Pending)
        │      → kubectl describe pvc <name>
        │      → Fix: check StorageClass exists and provisioner is healthy.
        │
        └─ Scheduler simply hasn't run yet / no events
               → kubectl get events -n <ns> --sort-by=.lastTimestamp
               → Check scheduler pod health: kubectl get pods -n kube-system -l component=kube-scheduler
```

### 1B. ImagePullBackOff / ErrImagePull

```
kubectl describe pod <pod>   →  read Events for the exact pull error
        │
        ├─ "manifest unknown" / "not found"
        │      → Wrong image name or tag typo.
        │      → Fix the image field in the manifest.
        │
        ├─ "unauthorized" / "pull access denied"
        │      → Missing or wrong imagePullSecret for a private registry.
        │      → kubectl get secret <regcred> -o yaml
        │      → Fix: create/attach correct docker-registry secret, reference in spec.imagePullSecrets.
        │
        └─ "connection timed out" / DNS error
               → Node cannot reach the registry (network/firewall/proxy issue).
               → kubectl exec -it <another-pod> -- curl -v <registry-url>
```

### 1C. CrashLoopBackOff

```
kubectl logs <pod> --previous        → see why the last instance crashed
kubectl describe pod <pod>           → check exit code + restart count
        │
        ├─ Exit code 1 / app-level error in logs
        │      → Application bug or bad config/env var.
        │      → Fix app or ConfigMap/Secret; kubectl logs -f for real-time confirmation.
        │
        ├─ Exit code 137
        │      → Killed by OOM or livenessProbe failure. See 1E for OOM path.
        │      → If liveness: kubectl describe pod <pod> | grep -A5 Liveness
        │      → Fix: increase probe timeout/failureThreshold or fix slow startup (add startupProbe).
        │
        └─ Exit code 0 but keeps restarting
               → Main process exits immediately (e.g., missing foreground command).
               → Check container command/entrypoint in the image and manifest.
```

### 1D. ContainerCreating Stuck

```
kubectl describe pod <pod>   → check Events
        │
        ├─ "FailedMount" / "Unable to attach or mount volumes"
        │      → PVC not bound, or CSI driver issue, or volume attached to another node.
        │      → kubectl get pvc,pv
        │      → kubectl describe pv <name>
        │
        ├─ "FailedCreatePodSandBox" / CNI errors
        │      → Networking plugin issue on the node.
        │      → kubectl get pods -n kube-system -o wide | grep <node-cni-pod>
        │      → Check CNI daemonset (calico/flannel/cilium) logs on that node.
        │
        └─ Secret/ConfigMap volume not found
               → kubectl get configmap/secret <name> -n <namespace>
               → Fix: create the missing object or fix the reference name.
```

### 1E. OOMKilled / Evicted

```
kubectl describe pod <pod>   → look for "OOMKilled" (state) or "Evicted" (status/reason)
        │
        ├─ OOMKilled
        │      → Container exceeded its memory limit.
        │      → kubectl top pod <pod> --containers   (check usage trend before crash, if still running)
        │      → Fix: raise memory limit, fix app memory leak, or right-size based on profiling.
        │
        └─ Evicted
               → Node ran out of memory/disk pressure; kubelet evicted low-priority pods.
               → kubectl describe node <node> | grep -A5 Conditions
               → Fix: free node disk space, add nodes, set appropriate priorityClass/QoS, add PDBs.
```

---

## 2. Pod Running but Not Receiving Traffic

```
                    ┌────────────────────────────────────┐
                    │ kubectl get pods -o wide             │
                    │ Pod shows "Running" but app unreachable│
                    └───────────────────┬────────────────┘
                                         ▼
                    kubectl get pods <pod> -o jsonpath='{.status.conditions}'
                                         │
        ┌────────────────────────────────┼─────────────────────────────────┐
        ▼                                ▼                                   ▼
  Readiness failing               Reaches Service?                  Reaches Ingress?
  (READY column shows 0/1)        test with port-forward            test from outside
        │                                │                                   │
        ▼                                ▼                                   ▼
     See 2A                          See 2B                              See 2D
```

### 2A. Readiness Probe Failing

```
kubectl describe pod <pod>   → check "Readiness" section + Events for probe failures
        │
        ├─ Probe hits wrong path/port
        │      → Fix readinessProbe.httpGet.path/port to match actual app listener.
        │
        ├─ App takes long to warm up
        │      → Increase initialDelaySeconds or add a startupProbe.
        │
        └─ App genuinely unhealthy (DB connection, dependency down)
               → kubectl logs <pod> ; kubectl exec -it <pod> -- curl localhost:<port>/health
               → Fix the underlying dependency/config issue.
```

### 2B. Service Not Routing Correctly

```
kubectl get endpoints <service-name>
        │
        ├─ ENDPOINTS column is empty  <──── most common cause
        │      → Service selector doesn't match Pod labels.
        │      → kubectl get pods --show-labels
        │      → kubectl get svc <name> -o jsonpath='{.spec.selector}'
        │      → Fix: align selector labels with Pod template labels.
        │
        ├─ Endpoints present but still unreachable
        │      → Test directly: kubectl port-forward svc/<name> 8080:80
        │      → If port-forward works but external access doesn't → check Service type/NodePort/LB.
        │      → If port-forward fails too → app not actually listening on declared containerPort.
        │
        └─ NetworkPolicy suspected
               → kubectl get networkpolicy -n <namespace>
               → kubectl describe networkpolicy <name>
               → Temporarily test: kubectl exec into a debug pod in same ns and curl the target Pod IP.
               → Fix: add an explicit allow rule for the required ingress/egress traffic.
```

### 2C. NetworkPolicy Blocking Traffic

```
Symptom: connections time out (not "connection refused")
        │
        ▼
kubectl get networkpolicy -A
kubectl describe networkpolicy <policy> -n <namespace>
        │
        ├─ Default-deny policy present with no matching allow rule for source Pod/namespace
        │      → Add ingress rule with correct podSelector/namespaceSelector + port.
        │
        └─ Egress blocked (Pod can't reach dependency, e.g., DB)
               → Check egress rules block DNS (port 53) or the dependency's port.
               → Add explicit egress allow rule.
```

### 2D. Ingress Misconfigured

```
kubectl describe ingress <name>
        │
        ├─ No ADDRESS assigned
        │      → Ingress controller not running or not watching this class.
        │      → kubectl get pods -n <ingress-namespace>
        │      → Check ingressClassName matches an installed controller.
        │
        ├─ Backend shows "<error: endpoints not available>"
        │      → Underlying Service/Endpoints issue → go back to 2B.
        │
        ├─ 404 from Ingress controller
        │      → host/path rule mismatch; check exact host header and path type (Prefix/Exact).
        │
        └─ 502/504 from Ingress controller
               → Backend Pod reachable but erroring/timing out; check readiness + app logs.
               → kubectl logs -n <ingress-namespace> <ingress-controller-pod>
```

---

## 3. Deployment Rollout Stuck

```
kubectl rollout status deployment/<name>
        │
        ▼
kubectl describe deployment <name>   → check Conditions + Events
        │
        ├─ "ProgressDeadlineExceeded"
        │      → New ReplicaSet's Pods aren't becoming Ready in time.
        │      → kubectl get rs -l app=<name>   (compare DESIRED/CURRENT/READY)
        │      → kubectl describe pod <new-pod>   → likely a probe/crash issue → go to Section 1 or 2A.
        │
        ├─ maxUnavailable/maxSurge misconfigured with too few replicas
        │      → kubectl get deploy <name> -o yaml | grep -A3 strategy
        │      → Fix: adjust strategy or increase replica count temporarily.
        │
        ├─ PodDisruptionBudget blocking eviction of old Pods
        │      → kubectl get pdb
        │      → Fix: verify PDB minAvailable/maxUnavailable isn't too strict for the rollout.
        │
        ├─ Insufficient cluster resources for new + old Pods to coexist during rollout
        │      → kubectl describe nodes | grep -A5 Allocated
        │      → Fix: free capacity, use recreate strategy, or scale down before rollout.
        │
        └─ Image pull or config error on new revision
               → kubectl get rs -l app=<name>  → find new RS → kubectl describe pod for its Pods.
               → Fix root cause, then: kubectl rollout undo deployment/<name>  (to unblock immediately)
```

---

## Quick Command Reference

| Question | Command |
|---|---|
| Why is this Pod not scheduled? | `kubectl describe pod <pod>` (Events) |
| Why did the container crash? | `kubectl logs <pod> --previous` |
| Is the Service pointing at any Pods? | `kubectl get endpoints <svc>` |
| Are labels aligned? | `kubectl get pods --show-labels` + `kubectl get svc <svc> -o yaml` |
| Is a NetworkPolicy blocking me? | `kubectl get networkpolicy -A` |
| Is the rollout stuck or progressing? | `kubectl rollout status deployment/<name>` |
| Undo a bad deploy right now | `kubectl rollout undo deployment/<name>` |
| Node resource pressure? | `kubectl describe node <node> \| grep -A5 Conditions` |
