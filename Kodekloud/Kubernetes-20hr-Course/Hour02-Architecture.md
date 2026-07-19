# Hour 2: Kubernetes Architecture — Cluster, Control Plane, Worker Nodes

## 1. Explanation (Simple → Technical)

**Simple version:** Think of a Kubernetes **cluster** as a restaurant chain's head office plus its kitchens. The **control plane** is head office — it doesn't cook food, but it decides what gets cooked, where, and keeps a master ledger (like a notebook that never loses a page) of every order, every chef, every station. The **worker nodes** are the actual kitchens where food (containers) gets cooked. When you (the customer/`kubectl` user) place an order, you don't walk into the kitchen — you tell the head office receptionist (**API Server**), who writes it in the ledger (**etcd**), then the head office dispatcher (**Scheduler**) decides which kitchen has room, and a kitchen manager (**kubelet**) at that kitchen actually tells the chef (**container runtime**) to start cooking.

**Technical version:**

A **Cluster** = one or more **control plane nodes** + one or more **worker nodes**, working together as a single system.

**Control Plane responsibilities** (the "brain" — makes global decisions, doesn't run your app containers):
- **etcd** — a distributed, consistent key-value store. This is the *single source of truth* for the entire cluster: every Pod, Deployment, Service, Secret, ConfigMap, and Node state lives here. Nothing else touches etcd directly.
- **kube-apiserver** — the front door. It's a REST API server that validates and processes all requests (from `kubectl`, controllers, kubelets, everyone). It is the *only* component that reads/writes etcd directly. It also handles authentication, authorization (RBAC), and admission control.
- **kube-scheduler** — watches the API Server for newly created Pods that have no `nodeName` assigned yet, then decides *which worker node* they should run on, based on resource requests (CPU/memory), taints/tolerations, affinity rules, and available node capacity. It writes its decision back through the API Server (it never touches etcd itself, and never actually starts the container).
- **kube-controller-manager** — runs many **control loops** ("controllers") bundled into one binary, each continuously watching the API Server and reconciling actual state → desired state. Examples: Node Controller (notices when a node stops responding and marks it `NotReady`), Replication Controller / ReplicaSet Controller (ensures the right number of Pod replicas exist), Endpoint Controller (keeps Service endpoints in sync).
- **cloud-controller-manager** — a separate, optional component that talks to your cloud provider's API (AWS, GCP, Azure) to handle cloud-specific logic — e.g. provisioning a Load Balancer for a `Service type=LoadBalancer`, attaching persistent disks, or labeling nodes with cloud zone/region info. It decouples cloud vendor code from core Kubernetes.

**Worker Node responsibilities** (the "muscle" — actually runs your application containers):
- **kubelet** — an agent running on every worker node. It registers the node with the API Server, watches the API Server for Pods assigned to *its* node, and instructs the **container runtime** (via the CRI — Container Runtime Interface) to pull images and start/stop containers. It also reports node and Pod health/status back to the API Server.
- **kube-proxy** — maintains network rules (via iptables/IPVS) on each node so that Kubernetes **Services** can route traffic to the correct backend Pods, even as Pods come and go.
- **Container runtime** (containerd, CRI-O, etc.) — the actual software that pulls container images and runs containers, invoked by kubelet through the CRI standard.

**Request flow — what happens when you run `kubectl run nginx`:**
1. `kubectl` sends an HTTP request (as a REST call) to the **API Server**.
2. The **API Server** authenticates/authorizes the request, runs admission controllers, then **writes the new Pod object into etcd** (state: "desired, not yet scheduled").
3. The **Scheduler**, which is continuously watching the API Server for unscheduled Pods, sees this new Pod, runs its scheduling algorithm (filtering + scoring nodes), and picks a node. It then updates the Pod object (via the API Server) with the chosen `nodeName` — this update is again persisted to etcd.
4. The **kubelet** on that specific worker node is watching the API Server for Pods assigned to itself. It notices this new Pod, and calls the **container runtime** (via CRI) to pull the image and start the container.
5. **kube-proxy** on relevant nodes updates networking rules so the new Pod is reachable if it's fronted by a Service.
6. The kubelet reports the Pod's status (Running, Ready) back to the API Server, which persists it to etcd — so `kubectl get pods` now shows it as `Running`.

Key insight: **every single interaction goes through the API Server.** The Scheduler, Controller Manager, and kubelet never talk to etcd directly and never talk to each other directly — they all watch/write through the API Server. This is why the API Server is the most critical single point in the architecture.

## 2. Diagram

```
                         KUBERNETES CLUSTER
┌───────────────────────────────────────────────────────────────────┐
│                         CONTROL PLANE (Master)                     │
│                                                                     │
│   kubectl ──HTTP/REST──▶ ┌─────────────────┐                       │
│                          │   kube-apiserver │◀──────────────┐       │
│                          │  (front door,    │               │       │
│                          │  the ONLY talker │               │       │
│                          │   to etcd)       │               │       │
│                          └───────┬──────────┘               │       │
│                                  │  read/write               │       │
│                                  ▼                            │       │
│                          ┌─────────────────┐                 │       │
│                          │      etcd       │                 │       │
│                          │ (key-value store,│                 │       │
│                          │ single source    │                 │       │
│                          │  of truth)       │                 │       │
│                          └─────────────────┘                 │       │
│                                                                │       │
│         ┌───────────────────────┐   ┌──────────────────────┐ │       │
│         │   kube-scheduler       │   │ kube-controller-mgr  │ │       │
│         │ "which node for this   │   │ (Node, ReplicaSet,   │ │       │
│         │  unscheduled Pod?"     │   │  Endpoint controllers)│ │       │
│         └───────────┬────────────┘   └───────────┬───────────┘ │       │
│                     │   watches / writes via API Server        │       │
│                     └────────────────────┬───────────────────┘       │
│                                          │                             │
│         ┌──────────────────────┐        │                             │
│         │ cloud-controller-mgr  │────────┘                             │
│         │ (cloud LB, disks,     │  (optional, talks to cloud provider) │
│         │  node metadata)       │                                     │
│         └──────────────────────┘                                     │
└───────────────────────────────────────────────────────────────────┘
                                  │
                     watches for Pods assigned to it
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                         WORKER NODE                                 │
│                                                                     │
│   ┌───────────────┐        ┌───────────────┐                       │
│   │    kubelet     │──CRI──▶│ Container     │──▶ [ Pod: nginx ]     │
│   │ (talks only to │        │ Runtime       │    [ Pod: redis ]     │
│   │  API Server)   │        │ (containerd)  │                      │
│   └───────────────┘        └───────────────┘                       │
│                                                                     │
│   ┌───────────────┐                                                │
│   │  kube-proxy    │  maintains iptables/IPVS rules for Services   │
│   └───────────────┘                                                │
└───────────────────────────────────────────────────────────────────┘

Request flow:  kubectl → API Server → etcd (write) → Scheduler (assigns node)
               → API Server → etcd (update) → kubelet (on chosen node)
               → container runtime → container running
```

## 3. Real-World Example

**Scenario: "The Great etcd Outage" at a fictional food-delivery company, FastEats.**

FastEats runs its order-management microservices on a self-managed Kubernetes cluster. One night, the disk backing their 3-node etcd cluster fills up (etcd is very sensitive to disk latency and space — it's designed for small, fast key-value data, *not* for large blobs). Two of the three etcd members go into a crash loop.

What happens next is a masterclass in why understanding architecture matters:
- **Existing Pods keep running.** kubelet and the container runtime don't need etcd to keep already-running containers alive — they only need it for *new* instructions.
- **But nothing new can happen.** `kubectl get pods` starts timing out, because the API Server can't read/write to etcd (it lost quorum). New deployments hang. The Horizontal Pod Autoscaler can't scale up `order-service` for the Friday dinner rush because the Scheduler can't see or write new Pod objects — it all goes through the API Server, which is now failing.
- **The Node Controller can't respond to failures either.** If a worker node were to crash right now, the Node Controller (inside kube-controller-manager) wouldn't be able to persist a `NotReady` state or trigger Pod rescheduling elsewhere, because that also depends on the API Server/etcd path.
- **Root cause & fix:** the on-call engineer identifies disk pressure on etcd nodes (via `etcdctl endpoint status` and node disk metrics), frees up space, restores quorum (2 of 3 members healthy), and the API Server immediately starts serving again — the whole cluster "wakes up" because state was never lost, only unavailable.

**Lesson:** etcd isn't "just another database" you can treat casually — it is the cluster's brain's memory. Losing etcd quorum doesn't necessarily kill running workloads immediately, but it freezes *all* cluster-level decision-making (scheduling, scaling, healing) instantly. This is why production etcd clusters are given dedicated, fast SSD-backed nodes, monitored disk latency alerts, and regular backups (`etcdctl snapshot save`).

## 4. Hands-On Lab

**Goal:** Inspect and understand the actual control plane and worker node components running in your local cluster.

```bash
# Make sure your cluster is running (from Hour 1)
minikube start

# 1. See all nodes and their roles
kubectl get nodes -o wide

# 2. See ALL the control plane components as Pods (they run as Pods too!)
kubectl get pods -n kube-system

# 3. Look at componentstatuses (deprecated in newer k8s but still useful to know)
kubectl get componentstatuses
# NOTE: removed/deprecated in Kubernetes 1.19+ API in some distros;
# if it errors, that's expected — modern clusters expose component health
# via /healthz endpoints and metrics instead.

# 4. Deep-dive into a specific control plane pod, e.g. etcd
kubectl describe pod etcd-minikube -n kube-system

# 5. Deep-dive into the API server pod
kubectl describe pod kube-apiserver-minikube -n kube-system

# 6. Inspect a node in detail — capacity, conditions, allocated resources
kubectl describe node minikube

# 7. SSH into the minikube VM to look at components from the OS level
minikube ssh
  # once inside:
  sudo crictl ps                    # see running containers via container runtime
  sudo systemctl status kubelet     # kubelet runs as a systemd service, NOT a pod!
  sudo cat /etc/kubernetes/manifests/etcd.yaml   # static pod manifest for etcd
  exit

# 8. Watch live component logs
kubectl logs -n kube-system kube-scheduler-minikube
kubectl logs -n kube-system kube-controller-manager-minikube
```

**Expected output for `kubectl get pods -n kube-system` (abbreviated):**
```
NAME                               READY   STATUS    RESTARTS   AGE
coredns-...                        1/1     Running   0          10m
etcd-minikube                      1/1     Running   0          10m
kube-apiserver-minikube            1/1     Running   0          10m
kube-controller-manager-minikube   1/1     Running   0          10m
kube-proxy-...                     1/1     Running   0          10m
kube-scheduler-minikube            1/1     Running   0          10m
storage-provisioner                1/1     Running   0          10m
```

**Key observation:** in a minikube single-node setup, control plane components (etcd, apiserver, scheduler, controller-manager) run as **static Pods** managed directly by kubelet — while **kubelet itself runs as a plain systemd service on the host**, not as a Pod. This trips up almost everyone the first time.

**Troubleshooting tips:**
- `kubectl get componentstatuses` returns an error or empty → normal on modern clusters; check `kubectl get pods -n kube-system` instead.
- A control plane pod is `CrashLoopBackOff` → `kubectl logs <pod> -n kube-system --previous` to see why it died before restarting.
- `kubectl describe node` shows `Conditions: Ready=False` → check `kubelet` status via `minikube ssh` + `systemctl status kubelet`, and check disk/memory pressure conditions listed in the node's `Conditions` section.
- Can't reach the API server at all (`kubectl` hangs) → check if etcd pod is healthy first; API server depends entirely on etcd being reachable.

## 5. Common Mistakes

1. **Thinking kubelet runs on the control plane** — kubelet runs on **every** node, including control plane nodes (to manage the static pods there), but its main job is on **worker nodes**. People often think it's a control-plane-only component; it's actually the universal per-node agent.
2. **Confusing etcd with "just a database for your app data"** — etcd stores *cluster state* (Pods, Deployments, Secrets, ConfigMaps, Node status) — it is not meant to store your application's business data (orders, users, products). Using etcd like a general-purpose database will cause severe performance and stability issues.
3. **Believing the Scheduler actually starts the container** — the Scheduler's *only* job is deciding "which node." It never talks to the container runtime; that's kubelet's job, based on the Scheduler's decision recorded via the API Server.
4. **Assuming any component can write to etcd** — only the **API Server** talks to etcd. Even the Scheduler and Controller Manager go through the API Server, not etcd directly. This centralizes auth, validation, and consistency.
5. **Mixing up kube-controller-manager and cloud-controller-manager** — kube-controller-manager runs generic, cloud-agnostic control loops (Node, ReplicaSet, Endpoint controllers); cloud-controller-manager handles only cloud-provider-specific integrations (load balancers, volumes, node metadata) and doesn't even exist in bare-metal/minikube setups.
6. **Thinking a control plane node "runs your app"** — by default (via taints), control plane nodes are reserved for control plane components only; your application Pods are scheduled onto worker nodes.

## 6. Interview Questions (with brief answers)

1. **What is the only component that communicates directly with etcd?** — The kube-apiserver. All other components (scheduler, controller-manager, kubelet) go through the API Server.
2. **What does the kube-scheduler actually do — does it start containers?** — No. It only decides *which node* a Pod should run on, based on resource requests, taints/tolerations, and affinity rules. It never starts containers itself.
3. **What's the difference between kube-controller-manager and cloud-controller-manager?** — kube-controller-manager runs generic control loops (Node, ReplicaSet/Replication, Endpoint controllers) that work on any infrastructure; cloud-controller-manager handles cloud-provider-specific tasks (provisioning load balancers, attaching disks, node metadata) and is optional/absent on bare-metal clusters.
4. **What is etcd, and why is it critical?** — A distributed, consistent key-value store holding all cluster state. It's critical because it's the single source of truth; losing etcd quorum freezes all scheduling and control-loop decisions cluster-wide, even though already-running Pods keep working.
5. **Explain the role of kubelet and kube-proxy on a worker node.** — kubelet is the node agent that talks to the API Server, watches for Pods assigned to its node, and instructs the container runtime (via CRI) to run them, reporting status back. kube-proxy maintains network rules (iptables/IPVS) so Services can route traffic to the right Pods on that node.

## 7. Quiz (50 Questions)

**True/False:**
1. The API Server is the only component that talks directly to etcd. (T)
2. kubelet runs only on control plane nodes. (F)
3. etcd is meant to store application business data like user orders. (F)
4. The Scheduler decides which node a Pod runs on but doesn't start the container. (T)
5. kube-proxy manages network rules for Services. (T)
6. cloud-controller-manager exists and runs actively on every minikube cluster by default. (F)
7. Control plane nodes are typically tainted to prevent normal application Pods from being scheduled there. (T)
8. The Controller Manager talks to etcd directly to update Node status. (F)
9. A worker node needs a container runtime to actually run containers. (T)
10. If etcd loses quorum, already-running Pods are immediately killed. (F)

**Multiple Choice:**
11. Which component is the "front door" REST API for the cluster? a) etcd b) kube-apiserver c) kubelet d) kube-proxy → (b)
12. Which component decides which node a new Pod should run on? a) kubelet b) kube-proxy c) kube-scheduler d) etcd → (c)
13. Which component is responsible for pulling images and starting containers on a node? a) kube-scheduler b) container runtime (via kubelet) c) kube-apiserver d) etcd → (b)
14. Which of these is NOT a control loop inside kube-controller-manager? a) Node Controller b) ReplicaSet Controller c) Endpoint Controller d) Load Balancer Provisioning Controller → (d)
15. What does etcd store? a) Application logs b) Container images c) Cluster state (Pods, Secrets, ConfigMaps, Node status) d) User session data → (c)

**Short Answer:**
16. What is the single source of truth in a Kubernetes cluster?
17. Name the two main categories of nodes in a cluster.
18. Which component watches the API Server for unscheduled Pods?
19. What CRI stands for and why it matters to kubelet.
20. What happens to `kubectl get pods` if the API Server can't reach etcd?
21. Why is the API Server described as a "gatekeeper" for the cluster?
22. What's the difference between "desired state" and "actual state" in the context of controllers?
23. Name one thing the Node Controller does.
24. Why don't the Scheduler and Controller Manager talk to etcd directly?
25. What role does kube-proxy play when a Pod behind a Service dies and a new one is created?

**Scenario-Based:**
26. Your `kubectl get pods` command hangs indefinitely. Which control plane component would you check first, and why?
27. A new Pod stays in `Pending` state forever. Which component's logs would you check, and what might be the cause?
28. Your Node Controller marks a healthy node as `NotReady`. What's a possible root cause unrelated to the node itself?
29. Your team wants Load Balancers to be automatically provisioned in AWS whenever a Service of type LoadBalancer is created. Which component enables this?
30. During an incident, someone suggests "let's just write directly to etcd to fix a stuck Pod." Why is this a bad idea?

**Fill in the Blank:**
31. The ______ is the only component that reads/writes directly to etcd.
32. ______ is the per-node agent that instructs the container runtime to start/stop containers.
33. The ______ decides which node a Pod should be scheduled on based on resource requests.
34. ______ maintains iptables/IPVS rules so Services route traffic correctly.
35. The ______ component handles cloud-provider-specific tasks like provisioning load balancers.

**Conceptual Deep-Dive:**
36. Why is it architecturally important that only the API Server talks to etcd, rather than every component talking to etcd independently?
37. Explain why "already-running Pods survive an etcd outage" but "no new scheduling can happen."
38. Why are control plane components in minikube run as static Pods, while kubelet itself is a systemd service?
39. What would happen to a cluster if the kube-controller-manager crashed but the API Server and etcd stayed healthy?
40. Why does Kubernetes separate "deciding where to run a Pod" (Scheduler) from "actually running it" (kubelet + runtime) into two different components instead of one?

**Command Practice:**
41. Write the command to list all Pods in the `kube-system` namespace.
42. Write the command to describe a node named `minikube` and see its conditions.
43. Write the command to SSH into the minikube VM.
44. Write the command to view logs of the `kube-scheduler-minikube` pod.
45. Write the command (inside minikube ssh) to check kubelet's systemd service status.

**Reflection:**
46. In your own words, explain the "head office vs kitchen" analogy for control plane vs worker nodes.
47. Which control plane component surprised you the most in terms of its narrow responsibility?
48. Before today, did you assume the Scheduler actually starts containers? How does knowing the truth change your mental model?
49. Why do you think Kubernetes designers chose to centralize all etcd access through a single component (the API Server) instead of allowing direct access?
50. What questions do you still have about the control plane before we move to Hour 3 (Pods and workloads)?

---

## 8. Hour 2 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Cluster** | A set of machines (nodes) — control plane + worker nodes — working together as one system |
| **Control Plane** | The "brain": makes global decisions (scheduling, scaling, healing) but doesn't run your app containers |
| **Worker Node** | The "muscle": actually runs application Pods via kubelet + container runtime |
| **etcd** | Distributed key-value store; the single source of truth for all cluster state |
| **kube-apiserver** | The only component that talks directly to etcd; front door for all requests |
| **kube-scheduler** | Decides *which node* a Pod runs on, based on resource requests/constraints — never starts containers |
| **kube-controller-manager** | Runs control loops (Node, ReplicaSet, Endpoint controllers) that reconcile actual vs desired state |
| **cloud-controller-manager** | Optional; handles cloud-specific tasks (load balancers, disks, node metadata) |
| **kubelet** | Per-node agent; watches API Server for Pods assigned to its node, manages containers via CRI |
| **kube-proxy** | Maintains network rules on each node so Services route traffic to the right Pods |
| **Request flow** | kubectl → API Server → etcd → Scheduler (assigns node) → API Server → etcd → kubelet → container runtime |

**Mnemonic:** *"A-E-S-C-K"* — **A**PI Server (front door) → **E**tcd (memory) → **S**cheduler (decides where) → **C**ontroller Manager (keeps promises) → **K**ubelet (does the work). Remember: "**A**ll **E**vents **S**tart **C**ontrolled **K**ubelets."

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Cluster Architecture](https://kubernetes.io/docs/concepts/architecture/) (the canonical reference for everything in this lesson)
- [Kubernetes Official Docs — Nodes](https://kubernetes.io/docs/concepts/architecture/nodes/)
- [Kubernetes Official Docs — etcd](https://kubernetes.io/docs/concepts/overview/components/#etcd)
- [Kubernetes the Hard Way (Kelsey Hightower)](https://github.com/kelseyhightower/kubernetes-the-hard-way) — builds every control plane component manually, the best way to internalize this architecture
- [etcd Official Docs](https://etcd.io/docs/latest/) — for understanding the key-value store powering Kubernetes state

**Mini-Project for Hour 2 (20 min):**
- Run `minikube ssh`, then `sudo crictl ps` to see every container the runtime is managing on your node — including the control plane component containers themselves (etcd, apiserver, scheduler, controller-manager all run as containers under the hood).
- Then run `sudo cat /etc/kubernetes/manifests/kube-scheduler.yaml` to see the actual static Pod manifest kubelet uses to launch the Scheduler. Compare it to `kubectl describe pod kube-scheduler-minikube -n kube-system` output from outside the VM — notice they describe the exact same object from two different vantage points (filesystem manifest vs API Server view).
