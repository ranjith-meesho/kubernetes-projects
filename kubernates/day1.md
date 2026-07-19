# 🚢 DAY 1 — KUBERNETES FUNDAMENTALS
### Architecture · Pods · Deployments · Services · kubectl

Welcome to Day 1. Today is the most important day of the entire 10 days — everything else (storage, networking, scaling, troubleshooting) is built on the four mental models you'll lock in today. Read slowly. I'll tell you exactly what to memorize and what to skip.

---

## 1. LEARNING OBJECTIVES

**What you will learn today:**
- What Kubernetes (K8s) actually *is* and the problem it solves
- The **control plane** vs **worker node** architecture (who does what)
- **Pods** — the smallest deployable unit
- **Deployments** — how you run Pods reliably at scale
- **Services** — how Pods talk to each other and the outside world
- **kubectl** — the command-line tool you'll live in

**Why it matters:**
- Every K8s interview opens with "Explain the architecture" and "What's the difference between a Pod, a Deployment, and a Service?" If you nail these, you've passed the first filter.
- In production, 80% of your daily work is `kubectl get/describe/logs` on Pods, Deployments, and Services.

**Where it's used:**
- Every cloud-native company. Netflix, Spotify, Airbnb, and basically every modern backend runs on K8s or something K8s-shaped.

**How it relates to later topics:**
- Day 2 (config) injects data *into* Pods. Day 3 (storage) attaches disks *to* Pods. Day 4–5 (networking/ingress) extends Services. Day 6–8 (probes/resources/scaling) make Deployments production-grade. **Pods and Deployments are the spine of all 10 days.**

---

## 2. 80/20 BREAKDOWN

**The critical 20% (gives 80% of value) — master these today:**

| Concept | Why it's in the vital 20% |
|---|---|
| **Pod** = wrapper around 1+ containers | Everything runs in a Pod. No exceptions. |
| **Deployment** = manages ReplicaSets = manages Pods | This is how you *actually* deploy apps. You almost never create bare Pods. |
| **Service** = stable network address for a set of Pods | Pods die and get new IPs constantly. Services give a permanent address. |
| **Desired state reconciliation** | The single most important *idea* in K8s. Understand this and everything clicks. |
| **kubectl get / describe / logs** | 80% of your debugging. |

**What you can DEFER (don't waste time today):**
- etcd internals, the Raft consensus algorithm
- CNI plugin internals (Calico vs Cilium)
- Custom controllers / operators
- StatefulSets, DaemonSets, Jobs (later)
- RBAC, admission controllers, the full API machinery

> 🎯 **Interview gold:** If asked "What's the core idea behind Kubernetes?" the answer is **declarative desired-state reconciliation** — you tell K8s *what* you want, not *how* to get there, and controllers continuously work to make reality match your declaration.

---

## 3. CONCEPT EXPLANATIONS

### 3.1 What is Kubernetes? (First Principles)

**Beginner explanation:**
Imagine you have 50 application containers (Docker containers) that need to run across 10 servers. You need to: place them on servers, restart them when they crash, replace them when a server dies, load-balance traffic to them, roll out new versions without downtime, and scale them up on Black Friday.

Doing this by hand = impossible. **Kubernetes is an orchestrator** — software that does all of this automatically. You declare "I want 5 copies of my app running" and K8s makes it true and *keeps* it true.

**Real-world analogy:**
Kubernetes is like a **shipping port manager**. You (the captain) say "I need 5 containers of cargo delivered and kept at the dock." The port manager figures out which cranes (nodes) to use, replaces any container that falls in the water, and reroutes work when a crane breaks. You never micromanage cranes — you state the goal.

**The core mechanism — the reconciliation loop:**

```
   YOU declare desired state          KUBERNETES enforces it
   ┌─────────────────────┐           ┌──────────────────────┐
   │ "I want 5 replicas  │           │  Controller loop:     │
   │  of nginx running"  │ ───────►  │  while (true) {        │
   └─────────────────────┘           │    actual = observe() │
                                      │    if actual != desired│
            ▲                         │       fix()           │
            │                         │  }                    │
            │  reality drifts         └──────────┬───────────┘
            │  (a pod crashes)                   │
            └────────────────────────────────────┘
                         self-healing
```

- **Common mistake:** Thinking K8s "runs commands." It doesn't. It runs *control loops* that constantly compare desired vs actual and fix drift. This is why a deleted Pod magically comes back.
- **Best practice:** Always think declaratively. Write YAML describing the end state; don't script step-by-step actions.

---

### 3.2 Cluster Architecture

A Kubernetes **cluster** = a set of machines (nodes) split into two roles: the **Control Plane** (the brain) and **Worker Nodes** (the muscle, where your apps actually run).

```
┌──────────────────────── KUBERNETES CLUSTER ────────────────────────────┐
│                                                                         │
│   ┌─────────────── CONTROL PLANE (the brain) ──────────────────┐        │
│   │                                                            │        │
│   │   ┌──────────────┐   ┌───────────┐   ┌────────────────┐    │        │
│   │   │  API Server  │◄─►│   etcd    │   │   Scheduler    │    │        │
│   │   │ (front door) │   │ (database)│   │ (places pods)  │    │        │
│   │   └──────┬───────┘   └───────────┘   └────────────────┘    │        │
│   │          │           ┌──────────────────────────┐         │        │
│   │          │           │ Controller Manager       │         │        │
│   │          │           │ (runs reconcile loops)   │         │        │
│   │          │           └──────────────────────────┘         │        │
│   └──────────┼─────────────────────────────────────────────────┘        │
│              │ (all communication goes THROUGH the API server)           │
│   ┌──────────┼──────────────┐   ┌──────────────────────────┐            │
│   │  WORKER NODE 1          │   │  WORKER NODE 2            │            │
│   │  ┌──────────────────┐   │   │  ┌──────────────────┐     │            │
│   │  │ kubelet (agent)  │   │   │  │ kubelet (agent)  │     │            │
│   │  ├──────────────────┤   │   │  ├──────────────────┤     │            │
│   │  │ kube-proxy (net) │   │   │  │ kube-proxy (net) │     │            │
│   │  ├──────────────────┤   │   │  ├──────────────────┤     │            │
│   │  │ Container Runtime│   │   │  │ Container Runtime│     │            │
│   │  │  ┌────┐  ┌────┐  │   │   │  │  ┌────┐  ┌────┐  │     │            │
│   │  │  │Pod │  │Pod │  │   │   │  │  │Pod │  │Pod │  │     │            │
│   │  │  └────┘  └────┘  │   │   │  │  └────┘  └────┘  │     │            │
│   │  └──────────────────┘   │   │  └──────────────────┘     │            │
│   └─────────────────────────┘   └──────────────────────────┘            │
└─────────────────────────────────────────────────────────────────────────┘
```

**Control Plane components (memorize these — interview staple):**

| Component | Plain-English job | Analogy |
|---|---|---|
| **kube-apiserver** | The front door. *Every* request (from kubectl, controllers, nodes) goes through it. The only component that talks to etcd. | Reception desk — all traffic passes through |
| **etcd** | The cluster's database. Stores the *entire* desired + actual state as key-value data. | The port's logbook / source of truth |
| **kube-scheduler** | Decides *which node* a new Pod runs on (based on resources, constraints). | Air-traffic controller assigning runways |
| **kube-controller-manager** | Runs the reconcile loops (e.g., "make sure 5 replicas exist"). | The floor managers enforcing the plan |
| *(cloud-controller-manager)* | Talks to the cloud provider (load balancers, disks). | Liaison to the building landlord |

**Worker Node components:**

| Component | Plain-English job | Analogy |
|---|---|---|
| **kubelet** | The node's agent. Talks to the API server, starts/stops containers, reports health. | Foreman on each crane |
| **kube-proxy** | Maintains network rules so Services route traffic to the right Pods. | Mailroom routing letters |
| **Container Runtime** (containerd/CRI-O) | Actually runs the containers. | The crane engine itself |

> 🎯 **Interview gold — the request flow.** When you run `kubectl apply -f deployment.yaml`:
> 1. kubectl → **API server** (authenticates, validates)
> 2. API server → writes desired state to **etcd**
> 3. **Controller manager** notices "Deployment wants 3 Pods, 0 exist" → creates Pod objects in etcd
> 4. **Scheduler** sees unscheduled Pods → assigns each to a node
> 5. **kubelet** on that node sees "a Pod is assigned to me" → tells the **container runtime** to pull the image and start containers
> 6. **kube-proxy** wires up networking
>
> Being able to recite this flow cleanly is a strong-hire signal.

- **Common mistake:** Saying "the scheduler runs the Pods." No — the scheduler only *decides placement*. The **kubelet** runs them.
- **Common mistake:** Thinking components talk to each other directly. They don't — **everything goes through the API server** (hub-and-spoke). etcd is touched *only* by the API server.
- **Best practice (production):** In managed K8s (EKS/GKE/AKS), the cloud provider runs and patches the control plane for you. You only manage worker nodes and workloads.

---

### 3.3 Pods — the atomic unit

**Beginner explanation:**
A **Pod** is the smallest thing K8s can deploy. It's a wrapper around **one or more containers** that share:
- the same **network** (same IP address, same `localhost`)
- the same **storage volumes**
- the same lifecycle (created and destroyed together)

99% of the time a Pod = **one container**. Multi-container Pods are for "sidecar" patterns (e.g., a logging agent next to your app).

**Real-world analogy:**
A Pod is like an **apartment**. Usually one tenant (container) lives there, but roommates (sidecars) share the same address (IP), the same kitchen (volumes), and move out together. Each apartment has its own address, but if the building is demolished, a *new* apartment is built with a *new* address — you can't rely on it staying the same.

```
        ┌──────────── POD (one IP: 10.244.1.7) ─────────────┐
        │                                                   │
        │   ┌───────────────┐      ┌────────────────────┐   │
        │   │  app container │      │  sidecar container │   │
        │   │   (port 8080)  │      │  (log shipper)     │   │
        │   └───────┬────────┘      └─────────┬──────────┘   │
        │           │   shared localhost + volumes           │
        │           └──────────────┬──────────┘              │
        │                  ┌────────────────┐                │
        │                  │ shared volume  │                │
        │                  └────────────────┘                │
        └───────────────────────────────────────────────────┘
```

**The most important Pod fact:** Pods are **ephemeral and mortal**. They are *not* self-healing on their own. If a Pod's node dies, that Pod is gone forever — a bare Pod does **not** get recreated. This is *why* Deployments exist.

- **Common mistake:** Putting multiple *unrelated* apps in one Pod. Rule of thumb: one Pod = one "main" process. Use sidecars only for tightly-coupled helpers.
- **Common mistake:** Relying on a Pod's IP address. It changes every time the Pod restarts.
- **Best practice:** Never create bare Pods in production. Always use a controller (Deployment) that recreates them.

---

### 3.4 Deployments — running Pods reliably

**Beginner explanation:**
A **Deployment** is a controller that manages Pods for you. You tell it "I want **3 replicas** of this app," and it:
- Creates a **ReplicaSet**, which creates and maintains exactly 3 Pods
- **Self-heals** — if a Pod dies, it makes a new one to get back to 3
- **Scales** — change replicas to 10, it adds 7
- **Rolling updates** — deploy a new image version with zero downtime, replacing Pods gradually
- **Rollback** — if a new version is broken, revert to the previous one

```
   Deployment  (you manage this)
        │  "I want 3 replicas of image v2"
        ▼
   ReplicaSet  (Deployment creates/manages this)
        │  "ensure exactly 3 Pods exist"
        ├──────────┬──────────┐
        ▼          ▼          ▼
      Pod        Pod        Pod      ◄── if one dies, ReplicaSet makes a new one
     (v2)       (v2)       (v2)
```

**Why the extra layer (ReplicaSet)?** The Deployment manages *ReplicaSets* so it can do rolling updates: during a rollout it spins up a **new** ReplicaSet (v2) while scaling down the **old** one (v1), Pod by Pod. That's how you get zero-downtime deploys and instant rollbacks (the old ReplicaSet is kept around).

```
ROLLING UPDATE (v1 → v2), zero downtime:

  step 1:  RS-v1: [P][P][P]      RS-v2: [ ]
  step 2:  RS-v1: [P][P]         RS-v2: [P]
  step 3:  RS-v1: [P]            RS-v2: [P][P]
  step 4:  RS-v1: [ ]            RS-v2: [P][P][P]   ✅ done
           (old RS kept at 0 replicas → instant rollback)
```

**Real-world analogy:**
A Deployment is a **thermostat**. You set "keep it at 3 Pods." It constantly senses the room and turns the heat on/off (creates/deletes Pods) to hold the target. A rolling update is like swapping out radiators one at a time so the room never goes cold.

**Production use case:** Your stateless web API. You run it as a Deployment with 3–10 replicas, push a new image, and K8s rolls it out gradually while health-checking each new Pod.

- **Common mistake:** Editing Pods directly. The Deployment will revert your change (reconciliation!). Edit the *Deployment*.
- **Common mistake:** Using Deployments for *stateful* apps (databases). Use **StatefulSet** for those (later topic).
- **Best practice:** Treat Deployments as the default for stateless apps. Always set resource requests/limits and probes (Days 6–7) for production.

---

### 3.5 Services — stable networking

**The problem Services solve:** Pods are mortal and their IPs change. If Pod A wants to talk to Pod B, and B's IP changes every restart, how does A find B? And if there are 3 replicas of B, which one does A talk to?

**Beginner explanation:**
A **Service** is a **stable, permanent network address** (a virtual IP + DNS name) that sits in front of a *set* of Pods and **load-balances** traffic across them. Even as the backing Pods die and get replaced, the Service IP/name stays constant.

**How does a Service know which Pods to target?** Through **labels and selectors**. The Service says `selector: app=web`, and any Pod labeled `app=web` automatically becomes a backend. (This is why Day 2's labels topic matters so much.)

```
                      ┌────────────────────────────┐
   client ──────────► │  Service: "web-svc"        │
                      │  stable IP: 10.96.0.10     │
                      │  DNS: web-svc.default.svc  │
                      │  selector: app=web         │
                      └─────────────┬──────────────┘
                       load-balances│ to Pods with label app=web
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                     ▼
         ┌─────────┐          ┌─────────┐          ┌─────────┐
         │ Pod     │          │ Pod     │          │ Pod     │
         │ app=web │          │ app=web │          │ app=web │
         │10.244.1.5│         │10.244.2.8│         │10.244.3.2│
         └─────────┘          └─────────┘          └─────────┘
         (IPs change on restart — Service IP never does)
```

**The 3 Service types you must know today** (Day 4 goes deeper):

| Type | What it does | When to use |
|---|---|---|
| **ClusterIP** (default) | Internal-only virtual IP. Reachable *inside* the cluster only. | Pod-to-Pod / service-to-service comms (e.g., API → database) |
| **NodePort** | Opens a port (30000–32767) on *every* node's IP. External access via `<NodeIP>:<NodePort>`. | Quick external access, dev/testing |
| **LoadBalancer** | Provisions a cloud load balancer with a real external IP. | Production external access on cloud |

**Real-world analogy:**
A Service is a **company's main phone number**. Employees (Pods) come and go, sit at different desks (IPs), but you always dial the same number, and the switchboard (Service) routes you to whoever's available. ClusterIP = internal extension. NodePort = direct outside line on a specific door. LoadBalancer = the public 1-800 number.

> 🎯 **Interview gold:** "Why can't I just use Pod IPs directly?" → Because Pods are ephemeral; their IPs are unstable. Services provide a **stable virtual IP + DNS name + load balancing** decoupled from individual Pod lifecycles. The decoupling is done via **label selectors**.

- **Common mistake:** Service selector doesn't match Pod labels → Service has **no endpoints** → connection refused/timeout. This is the #1 Service bug. (Diagnose with `kubectl get endpoints <svc>`.)
- **Common mistake:** Expecting NodePort to be production-grade external access. It's clunky (high ports, exposes nodes). Use LoadBalancer/Ingress in prod.
- **Best practice:** Use **DNS names**, not IPs. Inside the cluster, `web-svc` (same namespace) or `web-svc.default.svc.cluster.local` (FQDN) resolves to the Service.

---

### 3.6 kubectl — your cockpit

**kubectl** ("cube-control" / "cube-cuttle") is the CLI that sends requests to the API server. The universal pattern:

```
kubectl  <verb>     <resource-type>  <name>      [flags]
         get        pods             web-abc123  -n default
         describe   deployment       web
         logs       pod/web-abc123   -f
         delete     service          web-svc
```

**The 6 commands that are 80% of your daily life:**

```bash
kubectl get        # list resources (the "what exists?" command)
kubectl describe   # deep details + events (the "why is it broken?" command)
kubectl logs       # container logs (the "what did the app say?" command)
kubectl exec       # run a command inside a container (the "let me poke around" command)
kubectl apply      # create/update from YAML (the "make it so" command)
kubectl delete     # remove resources
```

- **Best practice:** `kubectl apply -f file.yaml` (declarative) over `kubectl create` (imperative) for anything you keep. Imperative commands (`kubectl run`, `kubectl create deployment`) are great for *learning* and for generating YAML fast (with `--dry-run=client -o yaml`).

---

## 4. HANDS-ON LABS (read-along walkthroughs)

> You're in reading mode, so I'll show every command, the **exact YAML**, and the **expected output** so you can simulate it mentally. When you spin up minikube/Killercoda later, these run as-is.

### Lab 1 — Your first Pod (imperative, then inspect)

```bash
# Create a single nginx Pod
kubectl run mypod --image=nginx

# Expected:
# pod/mypod created

# See it
kubectl get pods
# NAME    READY   STATUS    RESTARTS   AGE
# mypod   1/1     Running   0          10s
```

Read the columns: **READY 1/1** = 1 of 1 containers ready. **STATUS Running** = healthy. **RESTARTS** = how many times it crashed.

```bash
# Deep inspection — the single most useful debug command
kubectl describe pod mypod
# (shows: node it's on, IP, image, events at the bottom — read events FIRST when debugging)

# Logs
kubectl logs mypod

# Get a shell inside the container
kubectl exec -it mypod -- /bin/bash
#   then inside:  curl localhost   → you'll see nginx's welcome HTML
#   exit

# Clean up
kubectl delete pod mypod
# pod "mypod" deleted
```

**Key lesson:** Delete that Pod and it's **gone for good** — nothing recreates it. That's the Pod's mortality. Now contrast with a Deployment ↓.

---

### Lab 2 — A Deployment (the right way) via YAML

Save as `web-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
  labels:
    app: web
spec:
  replicas: 3                 # desired state: 3 Pods
  selector:
    matchLabels:
      app: web                # this Deployment manages Pods labeled app=web
  template:                   # the Pod "blueprint"
    metadata:
      labels:
        app: web              # Pods get this label (MUST match selector above)
    spec:
      containers:
        - name: nginx
          image: nginx:1.25
          ports:
            - containerPort: 80
```

```bash
kubectl apply -f web-deployment.yaml
# deployment.apps/web created

kubectl get deployments
# NAME   READY   UP-TO-DATE   AVAILABLE   AGE
# web    3/3     3            3           15s

kubectl get pods
# NAME                   READY   STATUS    RESTARTS   AGE
# web-6f8c4d9b7-2xk9p    1/1     Running   0          15s
# web-6f8c4d9b7-7nq4m    1/1     Running   0          15s
# web-6f8c4d9b7-jp2vt    1/1     Running   0          15s
```

**Now watch self-healing** — delete a Pod and a new one instantly appears:

```bash
kubectl delete pod web-6f8c4d9b7-2xk9p
# pod "...2xk9p" deleted

kubectl get pods
# NAME                   READY   STATUS    RESTARTS   AGE
# web-6f8c4d9b7-7nq4m    1/1     Running   0          60s
# web-6f8c4d9b7-jp2vt    1/1     Running   0          60s
# web-6f8c4d9b7-x8w2k    1/1     Running   0          3s   ◄── BRAND NEW, auto-created
```

That `x8w2k` Pod is reconciliation in action. **This is the single most important thing to *feel* on Day 1.**

**Scaling:**
```bash
kubectl scale deployment web --replicas=5
kubectl get pods        # now 5 Pods

# Rolling update to a new image:
kubectl set image deployment/web nginx=nginx:1.26
kubectl rollout status deployment/web
# deployment "web" successfully rolled out

# Rollback if it broke:
kubectl rollout undo deployment/web
```

---

### Lab 3 — Expose the Deployment with a Service

Save as `web-service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: web-svc
spec:
  type: ClusterIP          # internal stable IP (default)
  selector:
    app: web               # routes to Pods labeled app=web  ◄── MUST match Pod labels
  ports:
    - port: 80             # the Service's port
      targetPort: 80       # the container's port
```

```bash
kubectl apply -f web-service.yaml
# service/web-svc created

kubectl get svc
# NAME      TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)   AGE
# web-svc   ClusterIP   10.96.142.50   <none>        80/TCP    5s

# THE critical check — does the Service have backends?
kubectl get endpoints web-svc
# NAME      ENDPOINTS                                      AGE
# web-svc   10.244.1.5:80,10.244.2.8:80,10.244.3.2:80     5s
#           ▲ three Pod IPs = selector matched. GOOD.

# Test from inside the cluster:
kubectl run tester --image=busybox -it --rm -- wget -qO- web-svc
#   → returns nginx's HTML, load-balanced across the 3 Pods
```

**Validation checklist for any Service:**
1. `kubectl get svc` → does it have a CLUSTER-IP?
2. `kubectl get endpoints <svc>` → **does it list Pod IPs?** (empty = broken selector)
3. Test connectivity from a temporary Pod.

---

## 5. EXERCISES (apply it yourself — answers in your head for now)

1. Write the YAML for a Deployment named `api` running `httpd:2.4`, 4 replicas, label `app=api`, container port 80.
2. Write a ClusterIP Service named `api-svc` that targets it.
3. You run `kubectl get endpoints api-svc` and it shows `<none>`. List two possible causes.
4. Without using `kubectl scale`, how would you change replicas from 4 to 6? (Two ways.)
5. Explain in one sentence why deleting a Deployment-managed Pod behaves differently from deleting a bare Pod.

*(Try these before reading the troubleshooting/quiz sections — they reinforce the same ideas.)*

---

## 6. TROUBLESHOOTING SECTION

The universal debugging flow: **`get` → `describe` (read EVENTS) → `logs` → `exec`**.

### Failure A: Pod stuck in `Pending`
- **Symptoms:** `kubectl get pods` shows `STATUS Pending` forever.
- **Root cause:** Scheduler can't place it — usually no node has enough CPU/memory, or a scheduling constraint can't be met.
- **Diagnosis:** `kubectl describe pod <name>` → read the **Events** at the bottom: `0/3 nodes are available: insufficient cpu`.
- **Resolution:** Lower resource requests, add nodes, or fix the constraint.

### Failure B: `ImagePullBackOff` / `ErrImagePull`
- **Symptoms:** `STATUS ImagePullBackOff`.
- **Root cause:** K8s can't pull the image — typo in image name/tag, image doesn't exist, or private registry without credentials.
- **Diagnosis:** `kubectl describe pod <name>` → Events: `Failed to pull image "ngnix:1.25": not found`.
- **Resolution:** Fix the image name/tag; for private registries, add an `imagePullSecret`.

### Failure C: `CrashLoopBackOff`
- **Symptoms:** `STATUS CrashLoopBackOff`, RESTARTS climbing.
- **Root cause:** The container starts then **exits/crashes** repeatedly (bad command, missing config, app error). K8s restarts it with increasing backoff delay.
- **Diagnosis:** `kubectl logs <name>` (and `kubectl logs <name> --previous` to see the *crashed* instance's logs).
- **Resolution:** Fix the app/config/command causing the exit.

### Failure D: Service returns connection refused / times out
- **Symptoms:** Can't reach the app through the Service.
- **Root cause:** Selector doesn't match Pod labels → **no endpoints**. Or wrong `targetPort`.
- **Diagnosis:** `kubectl get endpoints <svc>` → if `<none>`, your selector is wrong.
- **Resolution:** Make the Service `selector` exactly match the Pod `labels`; verify `targetPort` = the container's actual port.

> 🛠️ **Memorize this reflex:** *Pod problem?* → `describe` + `logs`. *Service problem?* → `get endpoints`.

---

## 7. QUIZ SECTION

**Multiple choice:**

**Q1.** Which component actually starts containers on a node?
A) scheduler  B) kubelet  C) etcd  D) API server

**Q2.** What does a Deployment directly manage?
A) Pods  B) Services  C) ReplicaSets  D) Nodes

**Q3.** A Service knows which Pods to route to via:
A) Pod IPs  B) labels & selectors  C) node names  D) namespaces

**Short answer:**

**Q4.** Why are Pod IPs unsuitable for stable communication?
**Q5.** What's the difference between ClusterIP and NodePort?

**Scenario:**

**Q6.** You deploy a Service, but `kubectl get endpoints` shows `<none>`. The Pods are Running. What's the most likely cause and how do you confirm it?

---

### ▶ Quiz Answers & Explanations

**A1: B — kubelet.** The scheduler only *decides placement*; the kubelet on the chosen node instructs the container runtime to run the containers. Classic trap answer is "scheduler."

**A2: C — ReplicaSets.** A Deployment manages ReplicaSets, which manage Pods. This indirection enables rolling updates and rollbacks. Many candidates say "Pods" — partially right, but the *direct* child is the ReplicaSet.

**A3: B — labels & selectors.** The Service's `selector` matches Pod `labels`; matching Pods become endpoints. Decoupled from IPs (which are unstable) and lifecycles.

**A4:** Pods are ephemeral — when a Pod restarts or its node dies, it gets a new IP. Code/configs pointing at a fixed Pod IP break. Services provide a stable virtual IP + DNS name that survives Pod churn.

**A5:** ClusterIP gives an internal-only virtual IP reachable from inside the cluster (Pod-to-Pod). NodePort additionally opens a high port (30000–32767) on every node's IP for external access. NodePort is a *superset* — it still has a ClusterIP underneath.

**A6:** The Service `selector` doesn't match the Pods' `labels` (most common), or the Pods aren't actually ready. Confirm: `kubectl get pods --show-labels` and compare to the Service's selector (`kubectl describe svc <name>`). Fix the mismatch and endpoints populate automatically.

---

## 8. CHALLENGE PROJECT — "Mini Production Web Tier"

**Scenario:** You're the on-call engineer. Deploy a resilient, load-balanced web tier and prove it self-heals.

**Requirements (write the YAML + commands):**
1. A Deployment `shop-web` running `nginx:1.25`, **3 replicas**, labels `app=shop,tier=web`.
2. A ClusterIP Service `shop-web-svc` targeting it on port 80.
3. Prove self-healing: delete one Pod, show a replacement appears.
4. Scale to 5 replicas, then do a rolling update to `nginx:1.26`.
5. Verify the Service has 5 endpoints after scaling.

### ▶ Reference Solution

```yaml
# shop.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: shop-web
  labels: { app: shop, tier: web }
spec:
  replicas: 3
  selector:
    matchLabels: { app: shop, tier: web }
  template:
    metadata:
      labels: { app: shop, tier: web }
    spec:
      containers:
        - name: nginx
          image: nginx:1.25
          ports: [{ containerPort: 80 }]
---
apiVersion: v1
kind: Service
metadata:
  name: shop-web-svc
spec:
  type: ClusterIP
  selector: { app: shop, tier: web }
  ports: [{ port: 80, targetPort: 80 }]
```

```bash
kubectl apply -f shop.yaml
kubectl get pods -l app=shop                 # 3 running
kubectl delete pod <one-pod-name>            # self-heal test
kubectl get pods -l app=shop                 # back to 3, one brand new
kubectl scale deployment shop-web --replicas=5
kubectl set image deployment/shop-web nginx=nginx:1.26
kubectl rollout status deployment/shop-web
kubectl get endpoints shop-web-svc           # 5 IPs listed
```
**Why these choices:** matching `selector`/labels everywhere; ClusterIP because a web tier is usually fronted by an Ingress (Day 5) rather than exposed directly; 3+ replicas for availability; rolling update for zero downtime.

---

## 9. KNOWLEDGE CHECK

Before the 50 questions, gut-check yourself:
- Can you draw the control-plane/worker architecture from memory?
- Can you explain *why* a Deployment-managed Pod comes back but a bare Pod doesn't?
- Can you explain how a Service finds its Pods?
- Do you know the first command to run when a Pod is broken? (`describe` → events) When a Service is broken? (`get endpoints`)

---

## 10. CHEAT SHEET

```bash
# ── INSPECT ──
kubectl get pods/deploy/svc/nodes [-A] [-o wide] [--show-labels] [-l app=web]
kubectl describe <type> <name>        # deep info + EVENTS (read these first)
kubectl logs <pod> [-f] [--previous]  # app output; --previous = last crash
kubectl exec -it <pod> -- sh          # shell inside container
kubectl get endpoints <svc>           # ⭐ does the Service have backends?
kubectl get events --sort-by=.lastTimestamp

# ── CREATE / CHANGE ──
kubectl apply -f file.yaml            # declarative (preferred)
kubectl run NAME --image=IMG          # quick imperative pod
kubectl create deployment NAME --image=IMG --replicas=3
kubectl scale deployment NAME --replicas=5
kubectl set image deploy/NAME C=IMG:TAG
kubectl rollout status|undo|history deployment/NAME
kubectl delete -f file.yaml

# ── YAML GENERATION TRICK (huge time-saver) ──
kubectl create deployment web --image=nginx --dry-run=client -o yaml > web.yaml
```

**Key concepts:** desired-state reconciliation · Pods are mortal · Deployment→ReplicaSet→Pod · Service = stable IP + DNS + LB via label selectors · everything flows through the API server.

**YAML skeleton to memorize:** `apiVersion → kind → metadata → spec`. For Deployments, the trio that must agree: `spec.selector.matchLabels` = `spec.template.metadata.labels` = Service `spec.selector`.

---

## 11. INTERVIEW PREPARATION

**Beginner:**
- *What is Kubernetes and what problem does it solve?* → Container orchestrator; automates deployment, scaling, healing, and networking of containers via declarative desired-state reconciliation.
- *Pod vs container?* → A Pod wraps one or more containers sharing network + storage; it's K8s's smallest deployable unit.

**Intermediate:**
- *Deployment vs ReplicaSet vs Pod?* → Deployment manages ReplicaSets (enabling rolling updates/rollback); ReplicaSet ensures N Pod replicas; Pod runs the containers.
- *How does a Service load balance?* → Via label selector → endpoints (Pod IPs); kube-proxy programs the node's network rules to distribute traffic.

**Scenario:**
- *A new image deploy broke prod. How do you recover instantly?* → `kubectl rollout undo deployment/<name>` — the old ReplicaSet is retained at 0 replicas, so rollback is immediate.

**Production:**
- *Walk me through what happens when you `kubectl apply` a Deployment.* → (the 6-step API-server → etcd → controller → scheduler → kubelet → kube-proxy flow from §3.2).

---

## 12. 🎓 TOP 50 QUESTIONS

### Category 1 — Fundamentals (15)
1. What is Kubernetes in one sentence?
2. Define a Pod.
3. What is the smallest deployable unit in K8s?
4. Name the four main control-plane components.
5. What is etcd and what does it store?
6. What does the kube-scheduler do (and not do)?
7. What is the kubelet's role?
8. What does kube-proxy do?
9. What is a ReplicaSet?
10. What is a Deployment and what does it add over a ReplicaSet?
11. What is a Service and why is it needed?
12. Define declarative desired-state reconciliation.
13. Why are Pods called "ephemeral"?
14. What are labels and selectors used for?
15. What is the container runtime, and name one.

### Category 2 — Practical (10)
16. Command to list all pods in all namespaces with extra detail?
17. How do you create a Deployment imperatively with 3 replicas?
18. How do you generate Deployment YAML without applying it?
19. Command to scale a Deployment to 5?
20. How do you view a Pod's logs, including from its previous crash?
21. How do you get a shell inside a running container?
22. How do you change the image of a running Deployment?
23. How do you check whether a Service has backends?
24. How do you roll back a bad Deployment?
25. What's the difference between `kubectl apply` and `kubectl create`?

### Category 3 — Scenario (10)
26. You need internal-only comms between two services. Which Service type?
27. You need external access on a cloud provider for a web app. Which Service type and why?
28. A Deployment-managed Pod is deleted manually. What happens and why?
29. You edit a Pod directly; minutes later your change is gone. Why?
30. You must deploy a new version with zero downtime. What mechanism?
31. Why prefer Deployments over bare Pods in production?
32. You want only specific Pods behind a Service. How do you control that?
33. How would you safely test a risky image and revert fast if it fails?
34. Your team wants 10 replicas during a sale and 3 after. How?
35. Should you run a database as a Deployment? Why or why not?

### Category 4 — Troubleshooting (10)
36. Pod stuck in `Pending` — likely cause and first command?
37. `ImagePullBackOff` — causes and diagnosis?
38. `CrashLoopBackOff` — what does it mean and how do you debug?
39. Service unreachable, `endpoints` shows `<none>` — cause and fix?
40. App reachable on Pod IP but not via Service — what to check?
41. Where do you look first for *why* a Pod won't start? (which command/section)
42. How do you see cluster-level events sorted by time?
43. Pod is `Running` but app returns errors — where do you look?
44. You suspect a selector/label mismatch. How do you confirm it?
45. A rollout is stuck. Which command shows its status/history?

### Category 5 — Interview (5)
46. Walk through everything that happens after `kubectl apply -f deploy.yaml`.
47. Explain the difference between Deployment, ReplicaSet, and Pod and why the layering exists.
48. Why does Kubernetes use a control-loop model instead of running imperative commands?
49. Explain how Services decouple clients from ephemeral Pods.
50. Compare ClusterIP, NodePort, and LoadBalancer and when you'd use each.

---

## 13. FREE RESOURCES (Day 1)

### Essential (highest ROI — start here)
| Resource | Type | Difficulty | Time | Why it's valuable | Priority |
|---|---|---|---|---|---|
| **Kubernetes.io – "Kubernetes Basics" interactive tutorial** | Official interactive | Beginner | 1 hr | Browser-based, no install; covers exactly today's topics with a live cluster | **Critical** |
| **Kubernetes.io Concepts: Overview, Pods, Deployments, Service** | Official docs | Beginner | 1.5 hr | The canonical source; interviewers expect doc-accurate definitions | **Critical** |
| **TechWorld with Nana – "Kubernetes Tutorial for Beginners"** (YouTube) | Video | Beginner | ~1 hr | Best free visual explanation of architecture + components | **Critical** |
| **Killercoda – Kubernetes scenarios** | Interactive lab | Beginner | 30–45 min | Free in-browser real clusters | **Recommended** |
| **kubectl Cheat Sheet (official)** | Reference | All | 15 min | The command reference you'll keep open daily | **Recommended** |

### Official Documentation Reading Plan (in order)
1. *Overview → What is Kubernetes* (skim) — 10 min
2. *Concepts → Cluster Architecture → Components* (read carefully) — 20 min
3. *Workloads → Pods → Pod Overview* — 20 min
4. *Workloads → Deployments* (read intro + "Creating a Deployment" + "Updating") — 25 min
5. *Service, Load Balancing → Service* (read intro + ClusterIP/NodePort/LoadBalancer) — 25 min
- **Skip for now:** Pod lifecycle deep-dive, init containers, topology spread, EndpointSlices internals.

### Must-Read
Official "Cluster Architecture → Components" page.

### Must-Watch
TechWorld with Nana — the architecture segment (control plane vs nodes).

### Must-Practice
Killercoda "Kubernetes Basics" — recreate today's Labs 2 & 3 (Deployment + Service + self-heal test).

### Must-Memorize
- The 6 kubectl commands (`get/describe/logs/exec/apply/delete`)
- `Deployment → ReplicaSet → Pod` chain
- The label/selector matching rule (Deployment selector = Pod labels = Service selector)
- First-response debug reflexes (`describe`/`logs` for Pods; `get endpoints` for Services)

### 🏆 Highest-ROI single resource (if you have only 30 extra minutes today)
**The official "Kubernetes Basics" interactive tutorial** (kubernetes.io/docs/tutorials/kubernetes-basics) — gives you a live cluster in the browser and walks you through deploy → explore → expose → scale → update, mirroring today's labs exactly.

### Resource Quality Ratings (top pick)
*Official Kubernetes Basics tutorial:* Practicality **9** · Beginner-friendliness **10** · Interview value **8** · Production-readiness **7**

---

## NEXT STEPS

1. Work through **Active Recall** (the 50 questions) with your mentor — one at a time, scored 1–10.
2. Get your **Daily Mastery Assessment** (score, %, strengths, gaps, revision plan, readiness).
3. Only then type **"Continue to Day 2"** (Namespaces · Labels · Selectors · ConfigMaps · Secrets).
