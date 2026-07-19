# 🚢 DAY 4 — Kubernetes Networking, Services & DNS

> Prereqs: You finished Days 1–3 (Pods, ReplicaSets, Deployments, labels/selectors, namespaces). Today we connect Pods together and to the outside world.

---

## 1. LEARNING OBJECTIVES

By the end of Day 4 you will be able to:

1. Explain the **Kubernetes flat networking model** and its 4 fundamental rules.
2. Create and choose between **ClusterIP, NodePort, and LoadBalancer** Services.
3. Describe a **headless Service** and when you need one (StatefulSets, direct pod addressing).
4. Explain how **kube-proxy** programs **iptables / IPVS** to load-balance traffic to a Service.
5. Understand **Endpoints / EndpointSlices** and how a Service knows which Pods are healthy.
6. Use **CoreDNS** and the FQDN scheme `<svc>.<ns>.svc.cluster.local` for **service discovery**.
7. Resolve services **across namespaces**.
8. Troubleshoot the most common networking failures (no endpoints, DNS down, wrong targetPort, NodePort unreachable).

---

## 2. 80/20 BREAKDOWN

| Priority | Topic | Why it matters | Interview gold |
|----------|-------|----------------|----------------|
| 🔴 MUST | ClusterIP Service + selectors | Default internal LB; 90% of all Services | "What happens when a Service has no matching pods?" |
| 🔴 MUST | DNS / FQDN `svc.ns.svc.cluster.local` | How microservices find each other | "How does pod A reach service B in another namespace?" |
| 🔴 MUST | Service vs Pod IP stability | Pods are ephemeral; Services are stable | "Why not just use Pod IPs?" |
| 🔴 MUST | Endpoints / EndpointSlices | Glue between Service and Pods; readiness | "Service exists but no traffic — why?" |
| 🟠 HIGH | NodePort | Dev/on-prem external access | "Default NodePort range?" → 30000–32767 |
| 🟠 HIGH | LoadBalancer | Cloud production external access | "What provisions the LB?" → cloud-controller-manager |
| 🟠 HIGH | kube-proxy (iptables/IPVS) | The thing that actually routes Service traffic | "Is kube-proxy a proxy in the data path?" → No (iptables mode) |
| 🟡 MEDIUM | Headless Service | StatefulSet pod-level DNS | "clusterIP: None — what does it do?" |
| 🟢 DEFER | Network Policies | Security; later lesson | — |
| 🟢 DEFER | Ingress / Gateway API | Day 5 topic | — |
| 🟢 DEFER | CNI plugin internals (Calico/Cilium) | Deep networking | — |

**The 20% that gives 80%:** ClusterIP + selectors + DNS FQDN + Endpoints. Master these four and most networking problems become obvious.

---

## 3. CONCEPT EXPLANATIONS

### 3.1 The Kubernetes Networking Model — the 4 Rules

**Beginner explanation:** Kubernetes imposes a deliberately simple network contract. Every Pod gets its own IP address from a cluster-wide flat network, and everything can talk to everything by IP without NAT (Network Address Translation). The CNI plugin (Calico, Cilium, Flannel, cloud VPC CNI) implements this contract.

**The 4 rules:**
1. **Every Pod gets a unique IP** (the "IP-per-Pod" model). Containers in the same Pod share that IP and talk over `localhost`.
2. **Pods on a node can communicate with all Pods on all nodes without NAT.** It's a flat L3 network.
3. **Agents on a node (kubelet, system daemons) can communicate with all Pods on that node.**
4. **The IP a Pod sees itself as is the same IP others use to reach it** (no source NAT inside the cluster for pod-to-pod).

**Analogy:** Think of the cluster as an open-plan office. Every employee (Pod) has a desk phone with a direct extension (Pod IP). Anyone can dial anyone directly — no operator, no translation. But people get hired and fired constantly (Pods are ephemeral), so you don't memorize extensions. Instead you call the **department** (Service), and a receptionist (kube-proxy) routes you to whoever is currently working in that department.

**Production use case:** A `payments` pod calls `fraud-check` by Service name. Pod IPs churn on every deploy; the Service name is the stable contract.

**Common mistakes:**
- Hardcoding Pod IPs in config. Pod IPs change on every restart/reschedule.
- Assuming Pods can't reach each other across nodes (they can, by design).
- Forgetting the CNI is what makes rule 2 real — a broken CNI = no pod networking.

**Best practices:** Always talk to Services, never Pod IPs. Pick a CNI that matches your needs (Calico for NetworkPolicy, Cilium for eBPF/observability).

```
            FLAT POD NETWORK (e.g. 10.244.0.0/16)
 ┌──────────────── Node A ────────────────┐   ┌──────────────── Node B ────────────────┐
 │  Pod1            Pod2                    │   │  Pod3            Pod4                    │
 │  10.244.1.5      10.244.1.6             │   │  10.244.2.7      10.244.2.8             │
 │     │                │                  │   │     │                │                  │
 └─────┼────────────────┼──────────────────┘   └─────┼────────────────┼──────────────────┘
       └──────── any pod can reach any pod by IP, no NAT ──────────────┘
```

---

### 3.2 ClusterIP Service (the default)

**Beginner explanation:** A ClusterIP Service gives a **stable virtual IP** (and DNS name) that load-balances to a set of Pods selected by labels. It's reachable **only from inside the cluster**. This is the default `type`.

**Analogy:** The department's internal extension. Reachable from inside the office, not from the street.

**Production use case:** Internal microservice-to-microservice calls (e.g., `order-service` → `inventory-service`).

**Common mistakes:**
- `selector` labels don't match the Pod labels → Service has **zero endpoints** → connection refused/timeout.
- `targetPort` doesn't match the container's listening port.
- Trying to `curl` a ClusterIP from your laptop (it's cluster-internal only).

**Best practices:** Name ports; keep `port` (service port) and `targetPort` (container port) explicit; rely on DNS, not the IP.

```
   Client Pod ──► ClusterIP 10.96.0.10:80 ──► [iptables/IPVS rules] ──► one of:
                                                                  Pod 10.244.1.5:8080
                                                                  Pod 10.244.2.7:8080
```

---

### 3.3 NodePort Service

**Beginner explanation:** A NodePort opens the **same port on every node** (in range **30000–32767**) and forwards it to a ClusterIP behind the scenes. External clients hit `<AnyNodeIP>:<nodePort>`. NodePort is a *superset* of ClusterIP (you still get the ClusterIP too).

**Analogy:** A side door with the same lock number on every building entrance; knock on any door and you reach the department.

**Production use case:** Bare-metal/on-prem clusters with an external load balancer in front of nodes, or quick dev access on minikube/kind.

**Common mistakes:**
- Expecting a "random low port" — it's high (30000+).
- Firewall/security group blocks the NodePort.
- Using NodePort directly in production for many services (port sprawl, no TLS, no virtual hosting — use Ingress instead).

**Best practices:** Don't expose dozens of NodePorts; put a real LB or Ingress in front. Let Kubernetes auto-assign the nodePort unless you have a reason to pin it.

```
  Internet ──► NodeA:31000 ─┐
              NodeB:31000 ─┼─► ClusterIP ─► Pods
              NodeC:31000 ─┘
```

---

### 3.4 LoadBalancer Service

**Beginner explanation:** On a cloud provider, `type: LoadBalancer` asks the **cloud-controller-manager** to provision an external L4 load balancer (AWS NLB/ELB, GCP LB, Azure LB) with a public IP that forwards to the Service. It builds on top of NodePort.

**Analogy:** Hiring a professional front-desk service with a public street address that routes callers inside.

**Production use case:** Public-facing entry point for a service in a managed cloud cluster (EKS/GKE/AKS). For HTTP with host/path routing, usually fronted by Ingress instead of one LB per service.

**Common mistakes:**
- Expecting an external IP on bare metal/minikube without help. On minikube run `minikube tunnel`; on bare metal use **MetalLB**.
- `EXTERNAL-IP` stuck in `<pending>` → no cloud LB controller, or quota/subnet/permissions issue.
- One LoadBalancer per microservice = expensive. Consolidate behind Ingress.

**Best practices:** Use cloud annotations (e.g., internal LB, SSL) via `metadata.annotations`. Prefer Ingress/Gateway for HTTP(S).

```
  Internet ──► Cloud LB (public IP 34.x.x.x) ──► NodePort on each node ──► ClusterIP ──► Pods
```

---

### 3.5 Headless Service (`clusterIP: None`)

**Beginner explanation:** A headless Service has **no virtual IP** and **no load balancing**. Instead, DNS returns the **individual Pod IPs** (A/AAAA records) directly. Used when the client needs to address Pods individually.

**Analogy:** Instead of one department extension, you get the phone book listing every person's direct line.

**Production use case:** **StatefulSets** — databases like Cassandra, Kafka, Elasticsearch where each replica has a stable identity (`pod-0.mysvc.ns.svc.cluster.local`). Also for client-side load balancing.

**Common mistakes:**
- Expecting a single stable IP — there isn't one.
- Forgetting that DNS now returns multiple records; the client must handle them.

**Best practices:** Pair with a StatefulSet + `serviceName`. Each pod gets `<pod-name>.<svc>.<ns>.svc.cluster.local`.

```
  DNS query: mydb.default.svc.cluster.local
     ├─► 10.244.1.5  (mydb-0)
     ├─► 10.244.2.7  (mydb-1)
     └─► 10.244.3.9  (mydb-2)   ← client picks/connects directly, no VIP
```

---

### 3.6 kube-proxy — how Service routing actually works

**Beginner explanation:** A Service's ClusterIP is **virtual** — no process listens on it. **kube-proxy** runs on every node and programs the kernel so that packets to the ClusterIP get **DNAT'd** (destination-rewritten) to a real Pod IP. In **iptables mode** (default), it writes iptables rules; in **IPVS mode** it uses the kernel's IPVS load balancer (better at scale, more algorithms). In either case kube-proxy is **not in the data path** — the kernel does the work. (Newer clusters may use `nftables` mode or Cilium's eBPF replacing kube-proxy entirely.)

**Analogy:** kube-proxy is the building manager who updates the call-routing rules in the phone switch. Once configured, calls route automatically through the switch (kernel) — the manager isn't on every call.

**Production use case:** IPVS mode for clusters with thousands of Services to avoid the O(n) iptables rule-chain slowdown.

**Common mistakes:**
- Thinking kube-proxy proxies each connection (it doesn't, in iptables/IPVS mode).
- Blaming kube-proxy for app errors when the real issue is no endpoints.

**Best practices:** Use IPVS at large scale. Understand `externalTrafficPolicy: Local` preserves client source IP but can cause uneven balancing.

```
  Packet ─► dst=10.96.0.10:80 ─► [iptables PREROUTING/KUBE-SERVICES]
                                  random pick among endpoints
                                  DNAT ─► dst=10.244.2.7:8080 ─► real Pod
```

---

### 3.7 Endpoints / EndpointSlices

**Beginner explanation:** When you create a Service with a selector, the **endpoint controller** finds all **Ready** Pods matching the selector and records their IP:port into an **Endpoints** object (legacy) and **EndpointSlices** (modern, scalable, default since 1.19+). kube-proxy reads these to build routing rules. **Only Pods passing their readiness probe become endpoints.**

**Analogy:** The live roster of who's actually at their desk right now. If you're on a coffee break (failing readiness), you're off the roster and get no calls.

**Production use case:** Rolling deploys: as new pods become Ready they're added; failing pods are removed — zero traffic to broken pods (if probes are correct).

**Common mistakes:**
- No readiness probe → traffic sent to pods that aren't ready.
- Selector mismatch → empty Endpoints → no traffic.

**Best practices:** Always define a **readinessProbe**. Use EndpointSlices (automatic). Check `kubectl get endpoints <svc>` first when debugging.

```
 Service(selector app=web) ──► EndpointSlice ──► [10.244.1.5:8080, 10.244.2.7:8080]
                                                  (only READY pods listed)
```

---

### 3.8 CoreDNS & DNS naming / service discovery

**Beginner explanation:** **CoreDNS** runs as Pods (in `kube-system`) and is the cluster DNS server. Every Pod's `/etc/resolv.conf` points at the CoreDNS ClusterIP (commonly `10.96.0.10`). Services get DNS names so you never need IPs.

**The FQDN scheme:**
```
<service>.<namespace>.svc.cluster.local
```
- Same namespace: just `<service>` works (search domain auto-appends).
- Other namespace: `<service>.<namespace>` or full FQDN.
- Pods (with hostname): `<pod-ip-dashed>.<namespace>.pod.cluster.local`.

**Analogy:** CoreDNS is the company directory. You look up "payments" and it tells you the current extension. Cross-department you say "payments.finance".

**Production use case:** `http://inventory.default.svc.cluster.local:8080` — stable across all pod churn.

**Common mistakes:**
- Cross-namespace call using bare `<service>` (resolves in the *caller's* namespace, fails).
- CoreDNS pods crashed / NetworkPolicy blocking UDP 53 → all DNS fails.
- Forgetting the port (DNS resolves the name, not the port).

**Best practices:** Use full FQDN in cross-namespace calls; monitor CoreDNS; don't block port 53.

```
 Pod /etc/resolv.conf:
   nameserver 10.96.0.10
   search default.svc.cluster.local svc.cluster.local cluster.local
 → "curl inventory"  expands to inventory.default.svc.cluster.local
```

---

## 4. HANDS-ON LABS

> Assumes a running cluster (minikube/kind/managed). Verify: `kubectl get nodes`.

### Lab 1 — ClusterIP + DNS resolution test

**1. Deployment + ClusterIP Service:**

```yaml
# web.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3
  selector:
    matchLabels: { app: web }
  template:
    metadata:
      labels: { app: web }
    spec:
      containers:
      - name: web
        image: hashicorp/http-echo
        args: ["-text=hello from web", "-listen=:8080"]
        ports:
        - containerPort: 8080
        readinessProbe:
          httpGet: { path: /, port: 8080 }
          initialDelaySeconds: 2
---
apiVersion: v1
kind: Service
metadata:
  name: web
spec:
  selector:
    app: web
  ports:
  - name: http
    port: 80          # Service port
    targetPort: 8080  # container port
```

```bash
kubectl apply -f web.yaml
kubectl get svc web
```
Expected:
```
NAME   TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)   AGE
web    ClusterIP   10.96.140.21   <none>        80/TCP    5s
```

**2. Test from a throwaway pod:**
```bash
kubectl run tester --rm -it --image=busybox:1.36 --restart=Never -- sh
```
Inside the pod:
```sh
/ # nslookup web
Server:    10.96.0.10
Address:   10.96.0.10:53
Name:      web.default.svc.cluster.local
Address:   10.96.140.21

/ # wget -qO- web:80
hello from web
/ # exit
```
✅ DNS resolved the short name `web` and the ClusterIP load-balanced to a pod.

---

### Lab 2 — NodePort access

```yaml
# web-nodeport.yaml
apiVersion: v1
kind: Service
metadata:
  name: web-np
spec:
  type: NodePort
  selector:
    app: web
  ports:
  - port: 80
    targetPort: 8080
    nodePort: 31000   # optional; omit to auto-assign 30000-32767
```
```bash
kubectl apply -f web-nodeport.yaml
kubectl get svc web-np
```
Expected:
```
NAME     TYPE       CLUSTER-IP     EXTERNAL-IP   PORT(S)        AGE
web-np   NodePort   10.96.55.3     <none>        80:31000/TCP   3s
```
Access it:
```bash
# minikube:
minikube service web-np --url
# → http://192.168.49.2:31000
curl $(minikube service web-np --url)
# hello from web

# generic: any node IP
kubectl get nodes -o wide   # grab a node INTERNAL-IP
curl http://<NODE_IP>:31000
```

---

### Lab 3 — LoadBalancer (cloud / minikube tunnel)

```yaml
# web-lb.yaml
apiVersion: v1
kind: Service
metadata:
  name: web-lb
spec:
  type: LoadBalancer
  selector:
    app: web
  ports:
  - port: 80
    targetPort: 8080
```
```bash
kubectl apply -f web-lb.yaml
kubectl get svc web-lb
```
On a cloud (EKS/GKE/AKS) after ~1 min:
```
NAME     TYPE           CLUSTER-IP    EXTERNAL-IP        PORT(S)        AGE
web-lb   LoadBalancer   10.96.77.2    a1b2c3.elb.aws...  80:32100/TCP   60s
```
On **minikube** EXTERNAL-IP stays `<pending>` until you run a tunnel:
```bash
# In a SEPARATE terminal (keep it running, may prompt for sudo):
minikube tunnel
# back in main terminal:
kubectl get svc web-lb
# EXTERNAL-IP now shows e.g. 127.0.0.1 or an assigned IP
curl http://<EXTERNAL-IP>:80
# hello from web
```
> Bare metal? Install **MetalLB** to get LoadBalancer IPs.

---

### Lab 4 — Cross-namespace DNS

```bash
kubectl create namespace team-a
kubectl create namespace team-b

# Run web Deployment+Service in team-a
kubectl apply -n team-a -f web.yaml

# Tester pod in team-b
kubectl run tester -n team-b --rm -it --image=busybox:1.36 --restart=Never -- sh
```
Inside (note: bare `web` would FAIL here — resolves in team-b):
```sh
/ # wget -qO- web                      # FAILS: bad address 'web'
wget: bad address 'web'

/ # wget -qO- web.team-a               # works (namespace-qualified)
hello from web

/ # wget -qO- web.team-a.svc.cluster.local   # full FQDN, always works
hello from web
/ # exit
```
✅ Cross-namespace requires `<svc>.<ns>` or full FQDN.

---

### Lab 5 — Inspecting Endpoints / EndpointSlices

```bash
kubectl get endpoints web
```
Expected (one entry per Ready pod):
```
NAME   ENDPOINTS                                      AGE
web    10.244.1.5:8080,10.244.2.7:8080,10.244.0.4:8080  10m
```
Modern view:
```bash
kubectl get endpointslices -l kubernetes.io/service-name=web
kubectl describe endpointslice -l kubernetes.io/service-name=web
```
**Now break it** — scale to 0 and watch endpoints vanish:
```bash
kubectl scale deployment web --replicas=0
kubectl get endpoints web
# NAME   ENDPOINTS   AGE
# web    <none>      11m   ← no backends! traffic would fail
kubectl scale deployment web --replicas=3   # restore
```

---

### Lab 6 — Headless Service (bonus)

```yaml
# headless.yaml
apiVersion: v1
kind: Service
metadata:
  name: web-headless
spec:
  clusterIP: None        # <-- headless
  selector:
    app: web
  ports:
  - port: 8080
    targetPort: 8080
```
```bash
kubectl apply -f headless.yaml
kubectl run tester --rm -it --image=busybox:1.36 --restart=Never -- \
  nslookup web-headless
```
Expected — returns ALL pod IPs (no single VIP):
```
Name:   web-headless.default.svc.cluster.local
Address: 10.244.1.5
Address: 10.244.2.7
Address: 10.244.0.4
```

---

## 5. EXERCISES

1. Create a Deployment `cache` (redis:7) with 2 replicas and a ClusterIP Service `cache` on port 6379. From a busybox pod, resolve `cache` with `nslookup` and connect with `nc -zv cache 6379`.
2. Change the `web` Service `targetPort` to a wrong value (e.g., 9999). Apply it, then `curl` from a tester pod. Observe the failure, then explain in one sentence why endpoints still exist but traffic fails.
3. Expose `web` as NodePort, then find which node port was assigned and curl it from outside the cluster.
4. Create two namespaces `app` and `db`. Put a Service `postgres` in `db`. From a pod in `app`, write the three forms of the DNS name (one that fails, two that work).
5. Convert the `web` Service to headless and use `nslookup` to confirm you now get individual Pod IPs. Then explain when you'd want this over a normal ClusterIP.

---

## 6. TROUBLESHOOTING SECTION

### Issue 1 — DNS not resolving
- **Symptoms:** `nslookup web` → `server can't find` / `bad address`; apps log "no such host".
- **Root cause:** CoreDNS pods down, wrong `resolv.conf`, NetworkPolicy blocking UDP/TCP 53, or using a bare name across namespaces.
- **Diagnosis:**
  ```bash
  kubectl get pods -n kube-system -l k8s-app=kube-dns
  kubectl logs -n kube-system -l k8s-app=kube-dns
  kubectl run dnsutils --rm -it --image=busybox:1.36 --restart=Never -- cat /etc/resolv.conf
  kubectl run dnsutils --rm -it --image=busybox:1.36 --restart=Never -- nslookup kubernetes.default
  ```
- **Resolution:** Restart CoreDNS (`kubectl rollout restart deploy coredns -n kube-system`); allow port 53 in NetworkPolicy; use `<svc>.<ns>.svc.cluster.local` for cross-namespace.

### Issue 2 — Service has no endpoints
- **Symptoms:** `curl` to Service times out / connection refused; `kubectl get endpoints <svc>` shows `<none>`.
- **Root cause:** Service `selector` doesn't match Pod labels, OR no pod is **Ready** (failing readiness probe), OR 0 replicas.
- **Diagnosis:**
  ```bash
  kubectl get endpoints <svc>
  kubectl describe svc <svc> | grep -i selector
  kubectl get pods --show-labels
  kubectl get pods                 # check READY column
  ```
- **Resolution:** Align Service selector with Pod labels; fix the readiness probe; scale replicas up.

### Issue 3 — NodePort unreachable from outside
- **Symptoms:** `curl <nodeIP>:<nodePort>` hangs/refused, but it works inside the cluster.
- **Root cause:** Cloud security group / firewall blocks the port; you used an unreachable node IP; `externalTrafficPolicy: Local` and no pod on that node.
- **Diagnosis:**
  ```bash
  kubectl get svc <svc> -o wide
  kubectl get nodes -o wide        # get reachable external/internal IP
  # from a node: curl localhost:<nodePort>
  ```
- **Resolution:** Open the NodePort (30000–32767) in firewall/SG; use a reachable node IP; with `Local` policy ensure a pod runs on the targeted node (or use `Cluster`).

### Issue 4 — Wrong targetPort (traffic refused despite endpoints)
- **Symptoms:** Endpoints exist, DNS resolves, but `curl svc` returns connection refused/timeout.
- **Root cause:** `targetPort` ≠ the port the container actually listens on.
- **Diagnosis:**
  ```bash
  kubectl get svc <svc> -o jsonpath='{.spec.ports[*].targetPort}'
  kubectl describe pod <pod> | grep -i port          # container port
  # exec into pod and check listening sockets:
  kubectl exec <pod> -- netstat -tlnp 2>/dev/null || kubectl exec <pod> -- ss -tlnp
  ```
- **Resolution:** Set `targetPort` to the real container listen port; re-apply.

### Issue 5 — Cross-namespace name resolution fails
- **Symptoms:** App in namespace A can't reach `service` in namespace B.
- **Root cause:** Used bare `<service>` which appends the *caller's* search domain (namespace A).
- **Diagnosis:**
  ```bash
  kubectl run t -n A --rm -it --image=busybox:1.36 --restart=Never -- nslookup service          # fails
  kubectl run t -n A --rm -it --image=busybox:1.36 --restart=Never -- nslookup service.B        # works
  ```
- **Resolution:** Use `service.B` or `service.B.svc.cluster.local`.

---

## 7. QUIZ SECTION

**MCQ**

Q1. What is the default Service `type`?
- A) NodePort  B) ClusterIP  C) LoadBalancer  D) ExternalName

Q2. The valid NodePort range is:
- A) 1–1024  B) 8000–9000  C) 30000–32767  D) 40000–50000

Q3. In iptables mode, kube-proxy:
- A) Proxies every connection in userspace
- B) Is in the data path for each packet
- C) Programs kernel iptables rules; the kernel does the routing
- D) Replaces CoreDNS

**Short answer**

Q4. Why should you connect to Services by DNS name instead of Pod IP?

Q5. What does `clusterIP: None` create and when is it used?

**Scenario**

Q6. `order-svc` in namespace `prod` calls `inventory` in namespace `prod` successfully, but the same code in namespace `staging` calling `inventory` in `prod` fails with "no such host". What's wrong and how do you fix it?

---

### Answers

A1. **B** — ClusterIP. A2. **C** — 30000–32767. A3. **C** — kube-proxy writes iptables rules; the kernel routes.

A4. Pod IPs are ephemeral and change on every reschedule/deploy. A Service provides a **stable virtual IP and DNS name** plus load balancing across healthy pods, decoupling callers from pod churn.

A5. A **headless Service** — no virtual IP, no load balancing. DNS returns the individual Pod IPs. Used for StatefulSets (stable per-pod identity like `pod-0.svc...`) and client-side load balancing.

A6. The `staging` code likely uses the bare name `inventory`, which resolves within `staging` (the caller's namespace), not `prod`. The pod has search domain `staging.svc.cluster.local`, so `inventory` → `inventory.staging.svc.cluster.local` which doesn't exist. **Fix:** use `inventory.prod` or `inventory.prod.svc.cluster.local`.

---

## 8. CHALLENGE PROJECT — Multi-tier app with service-to-service DNS

**Goal:** Deploy a 3-tier app where each tier talks to the next **by Service DNS name** across the cluster. Frontend → API → Redis.

**Requirements:**
1. `redis` Deployment (1 replica) + ClusterIP Service `redis:6379`.
2. `api` Deployment (2 replicas) that reaches Redis at `redis:6379` (env `REDIS_HOST=redis`). ClusterIP `api:8080`.
3. `frontend` Deployment (2 replicas) that calls `http://api:8080`. NodePort so you can hit it from outside.
4. Prove: frontend → api → redis all resolve by DNS; show endpoints for each.

### Reference solution

```yaml
# multi-tier.yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: redis }
spec:
  replicas: 1
  selector: { matchLabels: { app: redis } }
  template:
    metadata: { labels: { app: redis } }
    spec:
      containers:
      - name: redis
        image: redis:7
        ports: [{ containerPort: 6379 }]
        readinessProbe:
          tcpSocket: { port: 6379 }
---
apiVersion: v1
kind: Service
metadata: { name: redis }
spec:
  selector: { app: redis }
  ports: [{ port: 6379, targetPort: 6379 }]
---
apiVersion: apps/v1
kind: Deployment
metadata: { name: api }
spec:
  replicas: 2
  selector: { matchLabels: { app: api } }
  template:
    metadata: { labels: { app: api } }
    spec:
      containers:
      - name: api
        image: hashicorp/http-echo
        args: ["-text=api OK (talks to redis)", "-listen=:8080"]
        env:
        - { name: REDIS_HOST, value: "redis" }   # <-- DNS name, not IP
        ports: [{ containerPort: 8080 }]
        readinessProbe:
          httpGet: { path: /, port: 8080 }
---
apiVersion: v1
kind: Service
metadata: { name: api }
spec:
  selector: { app: api }
  ports: [{ port: 8080, targetPort: 8080 }]
---
apiVersion: apps/v1
kind: Deployment
metadata: { name: frontend }
spec:
  replicas: 2
  selector: { matchLabels: { app: frontend } }
  template:
    metadata: { labels: { app: frontend } }
    spec:
      containers:
      - name: frontend
        image: hashicorp/http-echo
        args: ["-text=frontend OK (calls api)", "-listen=:80"]
        ports: [{ containerPort: 80 }]
        readinessProbe:
          httpGet: { path: /, port: 80 }
---
apiVersion: v1
kind: Service
metadata: { name: frontend }
spec:
  type: NodePort
  selector: { app: frontend }
  ports: [{ port: 80, targetPort: 80, nodePort: 31080 }]
```

**Verify:**
```bash
kubectl apply -f multi-tier.yaml
kubectl get pods,svc,endpoints

# DNS chain proof from a tester pod:
kubectl run t --rm -it --image=busybox:1.36 --restart=Never -- sh -c '
  nslookup redis;
  nslookup api;
  wget -qO- api:8080;
'
# Outside access:
curl http://$(minikube ip):31080      # frontend OK (calls api)
```
Expected: each `nslookup` returns the Service ClusterIP; `wget api:8080` returns "api OK"; the NodePort curl returns the frontend message. Each `kubectl get endpoints` shows the Ready pod IPs per tier.

---

## 9. KNOWLEDGE CHECK

You're ready for Day 5 if you can, without looking:
- [ ] State the 4 networking rules and why Pod IPs aren't used directly.
- [ ] Pick the right Service type for: internal call / dev external / cloud public / per-pod DB identity.
- [ ] Write the FQDN for service `auth` in namespace `iam`.
- [ ] Explain what `kubectl get endpoints <svc>` showing `<none>` means and the two top causes.
- [ ] Explain kube-proxy's role and that it's not in the per-packet data path (iptables mode).
- [ ] Run the cross-namespace DNS lab and explain why the bare name fails.

---

## 10. CHEAT SHEET

```bash
# Services
kubectl get svc -A                              # all services
kubectl get svc <svc> -o wide
kubectl describe svc <svc>                       # selector, ports, endpoints
kubectl expose deploy web --port=80 --target-port=8080            # quick ClusterIP
kubectl expose deploy web --type=NodePort --port=80 --target-port=8080
kubectl expose deploy web --type=LoadBalancer --port=80 --target-port=8080

# Endpoints
kubectl get endpoints <svc>
kubectl get endpointslices -l kubernetes.io/service-name=<svc>

# DNS / debug pod
kubectl run t --rm -it --image=busybox:1.36 --restart=Never -- sh
nslookup <svc>                                   # same ns
nslookup <svc>.<ns>.svc.cluster.local            # any ns
wget -qO- <svc>:<port>
cat /etc/resolv.conf

# CoreDNS
kubectl get pods -n kube-system -l k8s-app=kube-dns
kubectl logs -n kube-system -l k8s-app=kube-dns
kubectl rollout restart deploy coredns -n kube-system

# kube-proxy
kubectl get pods -n kube-system -l k8s-app=kube-proxy
kubectl get cm kube-proxy -n kube-system -o yaml | grep mode

# minikube external access
minikube service <svc> --url
minikube tunnel        # for LoadBalancer
```

**FQDN:** `<service>.<namespace>.svc.cluster.local` · **NodePort range:** 30000–32767 · **Headless:** `clusterIP: None`

| Type | Reachable from | Gets | Builds on |
|------|----------------|------|-----------|
| ClusterIP | inside cluster | virtual IP + DNS | — |
| NodePort | node IP:port | port on every node | ClusterIP |
| LoadBalancer | internet | cloud LB + ext IP | NodePort |
| Headless | inside (per-pod) | pod IPs via DNS | — |

---

## 11. INTERVIEW PREPARATION

**Framing tips:**
- Always anchor answers on the **stability problem**: pods are ephemeral, Services are stable. This single insight explains ClusterIP, DNS, and Endpoints.
- When asked "how does traffic reach a pod," walk the chain: **DNS → ClusterIP → kube-proxy iptables/IPVS → EndpointSlice → Pod**.
- Distinguish **control plane vs data plane**: kube-proxy/endpoint-controller configure; the kernel routes.
- For "no endpoints" questions, immediately mention **selector mismatch** and **readiness probe**.
- Know the numbers cold: NodePort 30000–32767; CoreDNS at the `.10` of the service CIDR; DNS suffix `svc.cluster.local`.

**30-second whiteboard answer for "explain k8s networking":**
> "Every pod gets a routable IP on a flat network — any pod can reach any pod without NAT. Since pods are ephemeral, we put a Service in front: a stable virtual IP and DNS name that load-balances to the pods matching its label selector. The endpoint controller keeps a live list of Ready pod IPs in an EndpointSlice; kube-proxy turns that into iptables/IPVS rules so packets to the ClusterIP get DNAT'd to a real pod. CoreDNS maps the Service name to its ClusterIP using `svc.ns.svc.cluster.local`. ClusterIP is internal, NodePort opens a port on every node, and LoadBalancer asks the cloud for an external LB."

---

## 12. 🎓 TOP 50 QUESTIONS

### Fundamentals (15)
1. What are the 4 rules of the Kubernetes networking model?
2. Why does Kubernetes give every Pod its own IP?
3. Why use a Service instead of connecting to Pod IPs directly?
4. What is the default Service type?
5. What is a ClusterIP and where is it reachable from?
6. What is a NodePort and what is its port range?
7. What is a LoadBalancer Service and what provisions the external LB?
8. What is a headless Service (`clusterIP: None`)?
9. What is the FQDN format for a Service?
10. What is CoreDNS and where does it run?
11. What's the difference between `port` and `targetPort` in a Service?
12. What is an Endpoints object vs an EndpointSlice?
13. What is kube-proxy and on which nodes does it run?
14. Name two kube-proxy data-plane modes and the difference.
15. What is the ExternalName Service type used for?

### Practical (10)
16. How do you create a ClusterIP for a Deployment in one command?
17. How do you check which pods back a Service?
18. How do you test DNS resolution from inside the cluster?
19. How do you access a NodePort service on minikube?
20. How do you get a LoadBalancer IP on minikube?
21. How do you view a pod's DNS config?
22. How do you restart CoreDNS safely?
23. How do you check the kube-proxy mode in use?
24. How do you expose a Deployment as NodePort with a pinned port?
25. How do you list all Services across all namespaces?

### Scenario (10)
26. A microservice can't reach `db` in another namespace. What DNS name should it use?
27. You need stable, individual DNS names for each replica of a Kafka cluster. Which Service?
28. You have 5000 Services and notice latency in rule evaluation. What kube-proxy change helps?
29. You must preserve the client's source IP at the Service. What setting do you use and what's the tradeoff?
30. A public-facing app on EKS needs a single external IP. Which Service type?
31. On bare metal you need LoadBalancer behavior. What do you install?
32. A deploy rolls out but briefly serves errors during cutover. What probe likely is missing/misconfigured?
33. Two services must talk only internally and never be exposed. Which type?
34. You want one external entry point doing host/path routing for 20 HTTP services. What should you use instead of 20 LoadBalancers?
35. A Service's ClusterIP doesn't respond but pods are healthy individually. First two things to check?

### Troubleshooting (10)
36. `kubectl get endpoints svc` shows `<none>`. List causes.
37. DNS works for `kubernetes.default` but not your service. What does that tell you?
38. NodePort works inside the cluster but not from your laptop. Likely cause?
39. Endpoints exist and DNS resolves, but curl is refused. Likely cause?
40. CoreDNS pods are CrashLooping. How do you investigate?
41. A NetworkPolicy was added and now all DNS fails. What did it likely block?
42. `EXTERNAL-IP` is stuck `<pending>` for a LoadBalancer. Causes?
43. With `externalTrafficPolicy: Local`, some node IPs don't respond. Why?
44. How do you confirm a container is actually listening on `targetPort`?
45. How do you tell if a pod is failing readiness and thus excluded from endpoints?

### Interview (5)
46. Walk through the full path of a packet from one pod to a Service-backed pod.
47. Is kube-proxy in the data path? Explain.
48. Control plane vs data plane in Kubernetes networking — give examples of each.
49. Compare iptables vs IPVS mode at scale.
50. How would you design networking for a multi-team cluster with internal and public services?

> Practice: answer 46–50 out loud in under 60 seconds each.

---

## 13. FREE RESOURCES

| Resource | Type | Best for |
|----------|------|----------|
| kubernetes.io/docs/concepts/services-networking/ | Official docs | Authoritative Service/DNS reference |
| kubernetes.io/docs/concepts/services-networking/dns-pod-service/ | Docs | DNS naming rules |
| kubernetes.io/docs/tasks/administer-cluster/dns-debugging-resolution/ | Docs | DNS troubleshooting recipe |
| killercoda.com Kubernetes scenarios | Interactive | Free in-browser labs |
| kube-proxy / EndpointSlice KEPs on github.com/kubernetes | Source | Deep internals |
| "Kubernetes Networking" — official YouTube (KubeCon talks) | Video | Visual mental model |

**Docs reading plan (90 min):**
1. Services concept page — 25 min (types, ports, selectors).
2. DNS for Services and Pods — 20 min (FQDN, search domains).
3. EndpointSlices page — 15 min.
4. Virtual IPs & service proxies (kube-proxy modes) — 20 min.
5. DNS debugging task page — 10 min.

**Must-do:** Run Labs 1, 4, and 5 (ClusterIP+DNS, cross-namespace, endpoints) on a real cluster.
**Must-read:** The Services concept page and the DNS for Pods/Services page.
**Must-memorize:** FQDN format, NodePort range, Service type comparison table.

**Highest-ROI activity:** Complete the **Challenge Project**, then deliberately break it (selector mismatch, wrong targetPort, bare cross-namespace name) and fix each using the Troubleshooting section. That single exercise covers ~80% of real and interview networking questions.

---

## 14. NEXT STEPS

**Active recall (do before moving on — no notes):**
1. Write the FQDN for service `auth` in namespace `iam`. (Answer: `auth.iam.svc.cluster.local`)
2. List the three Service types from least to most external exposure. (ClusterIP → NodePort → LoadBalancer)
3. From memory, name the two top causes of a Service with `<none>` endpoints. (selector mismatch; no Ready pod / readiness failing)
4. Explain in one sentence why kube-proxy is not in the per-packet data path in iptables mode.
5. Redeploy the multi-tier challenge from scratch without looking at the YAML.

Once you can do all five smoothly:

**➡️ Continue to Day 5 — Ingress, Ingress Controllers, TLS, and the Gateway API.**
