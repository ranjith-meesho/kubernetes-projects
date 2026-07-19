# Hour 5: Services, ClusterIP, NodePort, LoadBalancer

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine an office building full of workers (Pods) who each have their own personal phone extension. The problem: workers quit, get replaced, or move desks constantly — their extensions keep changing. Customers calling in would have no idea who to call today. So the office installs a **reception desk** with one fixed, published phone number. Customers always call that one number, and the receptionist forwards the call to *whichever* worker is currently on duty and available. That receptionist is a **Service**.

**Technical version:**
- **Pods are ephemeral.** When a Pod dies (crash, node failure, rolling update, scale-down), Kubernetes creates a *new* Pod with a *new* IP address. If other apps hard-coded that old Pod IP, they'd break constantly.
- A **Service** is a stable abstraction: a fixed **virtual IP (ClusterIP)** and a fixed **DNS name** that never change, even as the Pods behind it come and go.
- A Service finds its Pods using **label selectors** — it doesn't care about individual Pod names/IPs, only about Pods matching `app: my-app` (or whatever labels you chose). This decoupling is the same idea as Deployments matching Pods by labels (Hour 3/4).
- Under the hood, **kube-proxy** (running on every node) watches the API server for Service and Endpoint changes and programs iptables/IPVS rules so that traffic sent to the Service's virtual IP gets transparently load-balanced (round-robin by default) across all healthy matching Pods.
- **The 3 main Service types** (`spec.type`):
  1. **ClusterIP** (default) — gets a virtual IP reachable *only from inside the cluster*. Used for internal service-to-service communication (e.g., backend calling database-proxy). This is the most common type — most Services should be this unless you specifically need external access.
  2. **NodePort** — in addition to a ClusterIP, opens a **static port (30000–32767)** on *every node's* IP. Anyone who can reach any node's IP on that port reaches the Service. Handy for local dev/testing (Minikube) or quick demos, but rarely used in production directly.
  3. **LoadBalancer** — builds on NodePort by additionally asking the cloud provider (AWS/GCP/Azure) to provision a real **external load balancer** (e.g., an AWS ELB) with a public IP/DNS that forwards to the NodePort on all nodes. This is the standard way to expose a service to the public internet in production cloud clusters.
  4. **ExternalName** — a special case with no proxying at all; it just returns a **CNAME** DNS record pointing to an external hostname (e.g., mapping `my-db.svc.cluster.local` to `mydb.rds.amazonaws.com`). Useful for referencing external services with an in-cluster-style name.
- In production, `LoadBalancer` (or an **Ingress**, covered later) is preferred over raw `NodePort` because NodePort exposes a high, non-standard port on every node and doesn't give you a clean single entry point, TLS termination, or host/path-based routing.

## 2. Diagram

```
                     Service = Reception Desk (stable phone number)
                     ┌─────────────────────────────────┐
   Client Request -->│   Service: my-app (ClusterIP)     │
                     │   Virtual IP: 10.96.10.5:80       │
                     │   selector: app=my-app            │
                     └───────────────┬───────────────────┘
                                     │  kube-proxy load-balances
                     ┌───────────────┼────────────────────┐
                     ▼               ▼                    ▼
              ┌───────────┐   ┌───────────┐        ┌───────────┐
              │  Pod A    │   │  Pod B    │        │  Pod C    │
              │10.244.1.5 │   │10.244.2.7 │        │10.244.3.9 │
              │app=my-app │   │app=my-app │        │app=my-app │
              └───────────┘   └───────────┘        └───────────┘
              (Pod IPs change every time a Pod restarts —
               the Service's virtual IP + DNS name NEVER change)


Service Types — Reachability Comparison
┌─────────────────────────────────────────────────────────────────┐
│ ClusterIP (default)                                              │
│   Internet ──X (blocked)                                         │
│   Inside cluster ──> Service VIP:port ──> Pods                   │
│   Use: internal microservice-to-microservice traffic             │
├─────────────────────────────────────────────────────────────────┤
│ NodePort                                                          │
│   Internet/User ──> <AnyNodeIP>:30080 ──> Service VIP ──> Pods   │
│   Reachable via EVERY node's IP on the same static port          │
│   Use: dev/test quick external access                            │
├─────────────────────────────────────────────────────────────────┤
│ LoadBalancer                                                     │
│   Internet ──> Cloud LB (public IP/DNS) ──> NodePort (all nodes) │
│                 ──> Service VIP ──> Pods                          │
│   Use: production external access                                │
└─────────────────────────────────────────────────────────────────┘
```

## 3. Real-World Example

Picture Meesho's backend split into microservices: `order-service`, `payment-service`, and `frontend-gateway`.

- `order-service` needs to call `payment-service` internally to verify a transaction. It does **not** need to be reachable from the internet — exposing it publicly would be a security risk. So `payment-service` is exposed as a **ClusterIP** Service, and `order-service` simply calls it by DNS name: `http://payment-service.default.svc.cluster.local`. Kubernetes' internal DNS (CoreDNS) resolves that name to the Service's stable virtual IP, which load-balances across all healthy `payment-service` Pods — no matter how many times those Pods restart or get rescheduled.
- `frontend-gateway`, on the other hand, is the public-facing entry point that users' browsers/apps hit directly. It's exposed as a **LoadBalancer** Service, which provisions a cloud load balancer (e.g., an AWS Network Load Balancer) with a public IP/DNS name that customers actually connect to (often fronted further by a CDN or Ingress in real setups).

This pattern — internal services on ClusterIP, only the edge/gateway on LoadBalancer (or Ingress) — is standard practice at almost every company running microservices on Kubernetes.

## 4. Hands-On Lab

**Goal:** Deploy an app, expose it as ClusterIP and then NodePort, and verify access both from inside and outside the cluster.

```bash
# 1. Create a simple deployment (nginx) with 3 replicas
kubectl create deployment web --image=nginx --replicas=3

# Verify pods are running with different IPs
kubectl get pods -o wide
```

**Expected output:**
```
NAME                   READY   STATUS    RESTARTS   AGE   IP            NODE
web-6d4c9d7f9c-abcde   1/1     Running   0          10s   10.244.0.5    minikube
web-6d4c9d7f9c-fghij   1/1     Running   0          10s   10.244.0.6    minikube
web-6d4c9d7f9c-klmno   1/1     Running   0          10s   10.244.0.7    minikube
```

```bash
# 2. Expose it as a ClusterIP Service (default type)
kubectl expose deployment web --port=80 --target-port=80 --name=web-clusterip

kubectl get svc web-clusterip
```

**Expected output:**
```
NAME            TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)   AGE
web-clusterip   ClusterIP   10.96.45.112   <none>        80/TCP    5s
```

```bash
# 3. Curl it from INSIDE the cluster (ClusterIP is not reachable from your laptop directly)
kubectl run tmp-shell --rm -it --image=busybox -- /bin/sh
# inside the temporary pod's shell:
wget -qO- http://web-clusterip.default.svc.cluster.local
wget -qO- http://10.96.45.112   # same result, using the virtual IP directly
exit
```

**Expected output:** the default nginx welcome page HTML (`<html>...Welcome to nginx!...</html>`).

```bash
# 4. Now expose the SAME deployment as NodePort for external access
kubectl expose deployment web --port=80 --target-port=80 --type=NodePort --name=web-nodeport

kubectl get svc web-nodeport
```

**Expected output:**
```
NAME          TYPE       CLUSTER-IP     EXTERNAL-IP   PORT(S)        AGE
web-nodeport  NodePort   10.96.88.201   <none>        80:31547/TCP   3s
```

```bash
# 5. Access it from your machine via Minikube's helper (opens/prints a reachable URL)
minikube service web-nodeport --url
# Example output: http://192.168.49.2:31547

curl http://192.168.49.2:31547
```

**Expected output:** same nginx welcome page, now reachable from your host machine (not just inside the cluster).

```bash
# 6. Inspect Service details, including Endpoints (the actual Pod IPs it load-balances to)
kubectl describe svc web-clusterip
```

**Expected output (key fields):**
```
Name:              web-clusterip
Selector:          app=web
Type:              ClusterIP
IP:                10.96.45.112
Port:              <unset>  80/TCP
Endpoints:         10.244.0.5:80,10.244.0.6:80,10.244.0.7:80
```
`Endpoints` is the live list of Pod IPs currently matching the selector — this is what kube-proxy actually load-balances across. If this list is empty, something is wrong with your selector/labels (see Common Mistakes).

```bash
# 7. YAML equivalent (declarative — preferred for real projects)
cat <<EOF > web-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: web-clusterip-yaml
spec:
  type: ClusterIP
  selector:
    app: web
  ports:
    - port: 80
      targetPort: 80
EOF
kubectl apply -f web-service.yaml
```

**How DNS resolution works:** Every Service automatically gets a DNS record from CoreDNS in the form:
```
<service-name>.<namespace>.svc.cluster.local
```
e.g., `web-clusterip.default.svc.cluster.local`. Pods in the *same* namespace can also just use the short name `web-clusterip`, because their DNS search path already includes `default.svc.cluster.local`. This is why microservices can call each other by simple, stable names instead of ever hardcoding IPs.

**Cleanup:**
```bash
kubectl delete svc web-clusterip web-nodeport web-clusterip-yaml
kubectl delete deployment web
```

## 5. Common Mistakes

1. **Selector labels don't match Pod labels.** If your Service's `selector: app: web` doesn't exactly match the Pods' `labels: app: web` (typo, different key, different value), the Service has **zero Endpoints** — `kubectl describe svc` shows `Endpoints: <none>` and every request times out, even though the Pods are perfectly healthy.
2. **Using NodePort as the production solution.** NodePort exposes a random-looking high port (30000-32767) on *every node's* IP — it's fine for quick testing but lacks TLS termination, clean URLs, and a single stable entry point. Production traffic should go through a `LoadBalancer` Service or (more commonly) an **Ingress** sitting in front of ClusterIP Services.
3. **Assuming a Service load-balances across nodes, not Pods.** A Service load-balances traffic across the *Pods* matching its selector, regardless of which node they're on. It's not "one Service per node" — a single Service can have Pods spread across many nodes and will send traffic to all of them.
4. **Confusing a Service's ClusterIP with a Pod's IP.** The ClusterIP is a *virtual* IP — it doesn't belong to any actual network interface and won't show up if you `ping` it in some setups; it exists purely as an iptables/IPVS rule that kube-proxy programs to redirect traffic to real Pod IPs. Don't expect it to behave like a normal host IP.
5. **Forgetting `targetPort` vs `port` vs `nodePort`.** `port` is what clients hit on the Service; `targetPort` is the actual container port the Pod is listening on; `nodePort` is the static port opened on the node. Mixing these up (e.g., setting `targetPort` to the Service's port instead of the container's actual listening port) causes connection resets.
6. **Expecting the Service to appear immediately after Pods start.** If Pods aren't `Ready` yet (failing readiness probes), they won't show up in `Endpoints` even if the Service and selector are configured correctly — always check Pod readiness first when debugging "Service isn't working."

## 6. Interview Questions (with brief answers)

1. **How does a Service find its Pods?** — Via **label selectors**. The Service watches the API server for all Pods whose labels match its `spec.selector`, and continuously updates its list of `Endpoints` (Pod IP:port pairs) as Pods come and go.
2. **What's the difference between NodePort and LoadBalancer?** — NodePort opens a static high port on every node's IP and requires clients to know a node IP; it works everywhere including bare-metal but isn't a clean public entry point. LoadBalancer additionally provisions a real external load balancer from the cloud provider with its own public IP/DNS, and is the standard way to expose services externally in production (LoadBalancer is built on top of NodePort under the hood).
3. **Why don't we just use Pod IPs directly instead of Services?** — Pod IPs are ephemeral; they change whenever a Pod is rescheduled, restarted, or scaled. A Service provides a stable virtual IP and DNS name so consumers never need to track individual Pod lifecycles.
4. **What component actually implements the Service's load balancing?** — **kube-proxy**, running on every node, watches Service/Endpoint objects and programs iptables (or IPVS) rules to route/load-balance traffic to matching Pod IPs. (Some CNIs like Cilium can replace this with eBPF instead of iptables.)
5. **What does it mean if `kubectl describe svc` shows `Endpoints: <none>`?** — It means no running/ready Pods currently match the Service's selector — usually caused by a label mismatch, Pods not passing readiness probes, or the Deployment/Pods not existing yet. Traffic to the Service will simply fail/time out until this is fixed.

## 7. Quiz (50 Questions)

**True/False:**
1. Pod IPs are stable and never change. (F)
2. A Service provides a stable virtual IP even as Pods restart. (T)
3. ClusterIP is the default Service type. (T)
4. NodePort Services are only reachable from inside the cluster. (F)
5. LoadBalancer Services require a cloud provider integration to get a real external IP. (T)
6. Services find Pods using label selectors. (T)
7. kube-proxy is responsible for programming load-balancing rules for Services. (T)
8. A single Service can load-balance across Pods running on different nodes. (T)
9. ExternalName Services proxy traffic through kube-proxy like ClusterIP does. (F)
10. `Endpoints: <none>` in `kubectl describe svc` means everything is working fine. (F)

**Multiple Choice:**
11. What is the default Service type in Kubernetes? a) NodePort b) LoadBalancer c) ClusterIP d) ExternalName → (c)
12. Which Service type opens a static port on every node? a) ClusterIP b) NodePort c) ExternalName d) Ingress → (b)
13. Which Service type provisions a real external cloud load balancer? a) ClusterIP b) NodePort c) LoadBalancer d) Headless → (c)
14. What determines which Pods a Service routes traffic to? a) Pod name b) Node name c) Label selector d) Container image → (c)
15. What is the valid port range for NodePort by default? a) 1-1024 b) 3000-4000 c) 30000-32767 d) 8000-9000 → (c)
16. Which component actually load-balances traffic to Service Endpoints on each node? a) kubelet b) kube-scheduler c) kube-proxy d) etcd → (c)
17. What DNS suffix format does a Service get inside the cluster? a) svc.local.cluster b) `<name>.<namespace>.svc.cluster.local` c) `<namespace>.<name>.k8s.local` d) cluster.svc.local → (b)
18. What field maps the Service's port to the container's actual listening port? a) nodePort b) targetPort c) protocol d) clusterIP → (b)
19. Which Service type is best for referencing an external hostname via DNS CNAME? a) NodePort b) ExternalName c) LoadBalancer d) ClusterIP → (b)
20. What happens if selector labels on a Service don't match any Pod? a) Random Pod is picked b) Service gets no Endpoints c) Cluster crashes d) Service auto-fixes the label → (b)

**Short Answer:**
21. Why do Pods need Services instead of clients just tracking Pod IPs directly?
22. What is a virtual IP (ClusterIP) and why isn't it assigned to a real network interface?
23. Explain the receptionist analogy for a Service in your own words.
24. What is the purpose of the `Endpoints` object associated with a Service?
25. Why is NodePort generally discouraged for production external exposure?
26. What command exposes an existing Deployment as a Service quickly from the CLI?
27. What is the full DNS name format Kubernetes assigns to a Service?
28. What's the difference between `port` and `targetPort` in a Service spec?
29. How does LoadBalancer relate to NodePort under the hood?
30. Why might `Endpoints: <none>` appear even though your Pods are Running?

**Scenario-Based:**
31. Your `order-service` needs to call `payment-service` internally only — which Service type should you use, and why?
32. You need to expose a customer-facing web app to the public internet on a cloud-managed cluster — which Service type is most appropriate?
33. You're testing locally on Minikube and want quick external access without setting up a cloud LB — which type do you reach for?
34. A teammate says "the Service isn't working" and `kubectl get pods` shows all Pods Running — what's the first thing you check?
35. Your company wants a single entry point with TLS and path-based routing across many Services — is a plain LoadBalancer Service enough? What's usually layered on top?
36. You changed a Pod's label from `app: web` to `app: webapp` but forgot to update the Service's selector — what breaks and why?
37. A Service's ClusterIP is `10.96.45.112` but curling it from your laptop directly fails — why, and how should you actually test it?
38. Two Pods behind the same Service are on two different nodes — will the Service still load-balance across both? Why?
39. You need an in-cluster name that always points to an external managed database's hostname — what Service type fits and why?
40. After scaling a Deployment from 3 to 6 replicas, does the Service configuration need to change? Why or why not?

**Fill in the Blank:**
41. The default Service type, reachable only within the cluster, is ______.
42. ______ opens a static port on every node's IP for external access.
43. ______ Services ask the cloud provider to create a real external load balancer.
44. Kubernetes uses ______ (component) to program iptables/IPVS rules for Service load balancing.
45. A Service locates matching Pods using ______ ______.

**Conceptual Deep-Dive:**
46. Why is decoupling "what serves the traffic" (Pods) from "where clients connect" (Service) considered a core Kubernetes design principle?
47. How does the label-selector mechanism used by Services mirror the mechanism Deployments use to manage ReplicaSets/Pods?
48. Why does Kubernetes prefer round-robin load balancing at the Service layer instead of, say, "sticky sessions" by default?
49. What tradeoffs does ExternalName's lack of proxying (just DNS CNAME) introduce compared to ClusterIP?
50. Why might a company use Ingress instead of exposing dozens of individual LoadBalancer Services?

---

## 8. Hour 5 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Why Services exist** | Pod IPs are ephemeral; Services provide a stable virtual IP + DNS name |
| **How Services find Pods** | Label selectors match Service to Pods; matches tracked as `Endpoints` |
| **ClusterIP** | Default type; internal-only virtual IP for service-to-service traffic |
| **NodePort** | Static port (30000-32767) opened on every node; good for dev/test external access |
| **LoadBalancer** | Provisions a real cloud load balancer; standard for production external access |
| **ExternalName** | No proxying — just a DNS CNAME to an external hostname |
| **Load balancing engine** | kube-proxy programs iptables/IPVS rules on every node |
| **DNS name format** | `<service-name>.<namespace>.svc.cluster.local` (short name works within same namespace) |
| **Mental model** | Service = reception desk with one phone number; Pods = rotating workers behind it |

**Mnemonic:** *"CNL-E"* — **C**lusterIP (internal default) → **N**odePort (static port, all nodes) → **L**oadBalancer (cloud LB, production) → **E**xternalName (DNS CNAME, no proxy).

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Service](https://kubernetes.io/docs/concepts/services-networking/service/) (the authoritative reference for all Service types and fields)
- [Kubernetes Official Docs — Connecting Applications with Services](https://kubernetes.io/docs/tutorials/services/connect-applications-service/)
- [Kubernetes Official Docs — DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
- KodeKloud: "Kubernetes Services" module (hands-on labs in-browser)
- YouTube: "TechWorld with Nana — Kubernetes Services Explained" (free, visual walkthrough of ClusterIP/NodePort/LoadBalancer)

**Mini-Project for Hour 5 (20-30 min):**
- Take a single Deployment (e.g., `nginx` with 3 replicas) and expose it **three different ways simultaneously**: a ClusterIP Service, a NodePort Service, and (if you have access to a real cloud cluster, or just write the YAML if not) a LoadBalancer Service.
- For each, document: (a) what IP/hostname you'd use to reach it, (b) from where it's reachable (inside cluster only? any node? public internet?), and (c) one real-world scenario where you'd choose that type.
- Bonus: kill one of the 3 Pods (`kubectl delete pod <name>`) while continuously curling the Service, and observe that requests keep succeeding (the Service routes around the dead Pod) even though its replacement gets a brand-new IP.
