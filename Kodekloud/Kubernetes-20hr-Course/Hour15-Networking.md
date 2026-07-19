# Hour 15: Networking Basics, Service Discovery

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine Kubernetes networking as a **city**. Every building (Pod) gets its own unique **street address** (IP address) — no two buildings share an address, and any building can walk/drive directly to any other building without needing a translator (NAT) at the door. But addresses change often — buildings get demolished and rebuilt (Pods die and reschedule) with brand new addresses. So the city maintains a **universal phone directory** (CoreDNS) — you look up a business by its *name* ("backend-service"), and the directory gives you the current address. You never memorize addresses; you just call the name.

Hour 5 gave you Services (stable virtual IPs in front of a shrinking/growing set of Pods) and Hour 6 gave you Ingress (routing external HTTP traffic in). Hour 15 zooms out to explain the plumbing underneath both: how Pods actually get IPs, how packets travel node-to-node, how a Service's virtual IP turns into real Pod IPs, and how DNS names resolve to all of it.

**Technical version — four pillars of the Kubernetes networking model:**

1. **Flat Pod network (the "IP-per-Pod" model).** Kubernetes requires that every Pod gets its own unique cluster-wide IP address, and that all Pods can reach all other Pods' IPs directly — no NAT required between Pods, even across nodes. This is fundamentally different from Docker's default "NAT + port-mapping per host" model. It's what lets a Pod on Node A call a Pod on Node B using its raw Pod IP as if they were on the same LAN.

2. **CNI plugins implement the flat network.** Kubernetes itself doesn't implement networking — it delegates to a **CNI (Container Network Interface)** plugin: Calico, Flannel, Cilium, Weave, etc. The CNI plugin is responsible for: assigning IPs to Pods, building the routes/overlay (VXLAN, IP-in-IP, or native BGP routing) so Pod-to-Pod traffic crosses node boundaries, and (for some plugins like Calico/Cilium) enforcing NetworkPolicies. Different CNI plugins make different trade-offs (overlay vs. no-overlay, eBPF vs iptables), but they all must satisfy the same flat-network contract.

3. **kube-proxy implements Service virtual IPs.** A Service gets a stable ClusterIP, but that IP doesn't correspond to any real interface — it's virtual. **kube-proxy**, running on every node, watches the API server for Services and Endpoints, then programs **iptables** rules (or **IPVS** rules in higher-performance mode) that intercept traffic to the Service IP:port and DNAT it to one of the healthy backing Pod IPs, load-balancing across them. This is a kernel-level rewrite — no user-space proxying of each packet happens in iptables/IPVS mode (older `userspace` mode did proxy in user space, but it's deprecated).

4. **CoreDNS provides internal service discovery.** CoreDNS runs as Pods in `kube-system` and is itself exposed via a Service (traditionally named `kube-dns`). Every Pod's `/etc/resolv.conf` is automatically configured (via kubelet) to point at the CoreDNS Service IP, with a search list including the Pod's namespace. This lets any Pod resolve `service-name.namespace.svc.cluster.local` — and, within the same namespace, just `service-name` — to the Service's ClusterIP.

**Service discovery: DNS-based vs environment-variable-based.**
- **DNS-based (primary, recommended):** Every Service automatically gets a DNS record: `<service-name>.<namespace>.svc.cluster.local`. Same-namespace callers can use the short name (`backend-service`); cross-namespace callers need at least `backend-service.prod` or the full FQDN. This works for Services created *before or after* your Pod started, since it's a live lookup each time.
- **Environment-variable-based (legacy, rarely used):** When a Pod starts, kubelet injects environment variables like `BACKEND_SERVICE_SERVICE_HOST` and `BACKEND_SERVICE_SERVICE_PORT` for every Service that existed *at Pod creation time*. Problems: order-dependent (a Service created after your Pod started won't have env vars injected — you'd need to restart the Pod), pollutes the Pod's environment with dozens of variables in large clusters, and doesn't update if the Service's IP changes. DNS solved all of this, so env vars are essentially a historical curiosity you should recognize but not rely on.

**Network Policies — the firewall (preview of Hour 16).** By default, Kubernetes networking is **default-allow**: any Pod can talk to any other Pod, in any namespace, with no restrictions. A **NetworkPolicy** resource acts like a firewall rule scoped to a set of Pods (via label selector) — but the moment *any* NetworkPolicy selects a Pod, that Pod becomes **default-deny** for the traffic direction (ingress/egress) covered by that policy, and only explicitly allowed traffic gets through. Policies are **additive** — multiple policies selecting the same Pod are OR'd together, not overridden. NetworkPolicy enforcement requires a CNI plugin that supports it (Calico, Cilium, Weave — not all plugins do; e.g., basic Flannel alone doesn't enforce policies). We'll go much deeper on this, plus RBAC and Pod security, in Hour 16.

## 2. Diagram

```
                         ┌────────────────────────────┐
                         │   CoreDNS (kube-system)     │
                         │  backend-service.prod.svc.  │
                         │  cluster.local → 10.96.5.20 │
                         └─────────────┬──────────────┘
                                       │ (1) DNS lookup
                                       ▼
   Node A                                                Node B
 ┌───────────────────────────┐                  ┌───────────────────────────┐
 │  Pod: frontend             │                  │  Pod: backend-1            │
 │  IP: 10.244.1.5            │                  │  IP: 10.244.2.7             │
 │                            │                  └───────────────────────────┘
 │  curl backend-service.prod │
 │        │                   │                  ┌───────────────────────────┐
 │        ▼                   │                  │  Pod: backend-2            │
 │  Service VIP 10.96.5.20    │                  │  IP: 10.244.2.9             │
 │        │                   │                  └───────────────────────────┘
 │  (2) kube-proxy iptables/  │
 │      IPVS DNAT rule picks  │
 │      one healthy backend   │
 └────────────┬───────────────┘
              │ (3) rewritten dest IP: 10.244.2.7 (or .9)
              ▼
      ┌──────────────────────────────────────────┐
      │   CNI overlay / routed network             │
      │   (VXLAN tunnel, IP-in-IP, or BGP routes)   │
      │   carries the packet from Node A → Node B   │
      └──────────────────────────────────────────┘
              │
              ▼
        Pod backend-1 (or backend-2) receives the request
        directly on its Pod IP — no NAT needed at Pod level.

Legend:
 (1) CoreDNS resolves the Service name to its stable ClusterIP (the VIP).
 (2) kube-proxy already programmed iptables/IPVS rules from Endpoints objects,
     so the OS kernel rewrites the destination on the fly.
 (3) The CNI plugin's overlay/route network gets the packet across nodes
     using real Pod IPs — this is the "flat network" contract in action.
```

## 3. Real-World Example

A `frontend` Pod needs to talk to a `backend` Deployment running in the `prod` namespace. Instead of hardcoding an IP like `10.244.2.7` (which will change the moment that Pod is rescheduled, scaled, or updated), the frontend code calls:

```
http://backend-service.prod.svc.cluster.local:8080/api/orders
```

Here's what makes this resilient in production (think Meesho-scale order/catalog services):
- When `backend` Pods crash and get rescheduled onto different nodes with new IPs, the **Endpoints/EndpointSlice** object updates automatically, kube-proxy reprograms iptables/IPVS, and the Service's ClusterIP keeps routing correctly — the frontend's DNS lookup and code never change.
- When engineers scale `backend` from 3 to 10 replicas for a flash sale, no config changes anywhere — the Service just load-balances across more Endpoints.
- When a rolling update swaps `backend:v1` for `backend:v2`, old Pods disappear from Endpoints and new ones appear seamlessly, with the DNS name and virtual IP staying constant throughout.

This is precisely why hardcoded IPs are a production anti-pattern and DNS-based service discovery is the default expectation in any real Kubernetes deployment.

## 4. Hands-On Lab

**Goal:** Observe DNS resolution, cross-namespace service calls, kube-proxy's rule programming, and Endpoints objects firsthand.

```bash
# Setup: create two namespaces and a simple backend + frontend
kubectl create namespace prod
kubectl create namespace dev

kubectl -n prod create deployment backend --image=hashicorp/http-echo -- -text="hello from prod backend"
kubectl -n prod expose deployment backend --port=80 --target-port=5678 --name=backend-service

kubectl -n dev run frontend --image=busybox:1.36 --restart=Never -- sleep 3600
```

**Step 1 — Inspect DNS config inside a Pod:**
```bash
kubectl -n dev exec -it frontend -- cat /etc/resolv.conf
```
Expected output:
```
nameserver 10.96.0.10
search dev.svc.cluster.local svc.cluster.local cluster.local
options ndots:5
```
Note the `nameserver` points at the CoreDNS Service ClusterIP, and the `search` list only appends the Pod's *own* namespace (`dev`) — not `prod`. This is exactly why cross-namespace lookups need the extra suffix.

**Step 2 — nslookup the Service:**
```bash
kubectl -n dev exec -it frontend -- nslookup backend-service.prod.svc.cluster.local
```
Expected output:
```
Server:    10.96.0.10
Address:   10.96.0.10:53

Name:      backend-service.prod.svc.cluster.local
Address:   10.108.44.201
```

**Step 3 — Try the short name cross-namespace (this should fail or resolve wrong):**
```bash
kubectl -n dev exec -it frontend -- wget -qO- backend-service
# Expected: "wget: bad address 'backend-service'" — search list is `dev`, not `prod`,
# so it tries backend-service.dev.svc.cluster.local, which doesn't exist.

kubectl -n dev exec -it frontend -- wget -qO- backend-service.prod
# Expected: works! (search list appends .svc.cluster.local automatically)
# Output: hello from prod backend

kubectl -n dev exec -it frontend -- wget -qO- backend-service.prod.svc.cluster.local
# Expected: also works — full FQDN always works from anywhere.
```

**Step 4 — Same-namespace short name (should just work):**
```bash
kubectl -n prod run tester --image=busybox:1.36 --restart=Never -- sleep 3600
kubectl -n prod exec -it tester -- wget -qO- backend-service
# Expected: hello from prod backend   (short name resolves within same namespace)
```

**Step 5 — Inspect Endpoints backing the Service:**
```bash
kubectl -n prod get endpoints backend-service
```
Expected output:
```
NAME              ENDPOINTS           AGE
backend-service   10.244.1.8:5678     3m
```
Scale the Deployment and re-check to see the Endpoints list grow:
```bash
kubectl -n prod scale deployment backend --replicas=3
kubectl -n prod get endpoints backend-service
```
Expected: three IP:port pairs now listed — these are exactly what kube-proxy load-balances across.

**Step 6 (optional/advanced) — Inspect kube-proxy mode and iptables rules via minikube:**
```bash
kubectl -n kube-system get configmap kube-proxy -o yaml | grep mode
# Expected: mode: "iptables"   (or "ipvs" depending on cluster config)

minikube ssh
sudo iptables -t nat -L KUBE-SERVICES -n | grep <cluster-ip-of-backend-service>
# Expected: a rule jumping to a KUBE-SVC-XXXX chain, which further jumps to
# KUBE-SEP-XXXX chains — one per backing Pod — implementing the DNAT + load balancing.
exit
```

**Cleanup:**
```bash
kubectl delete namespace prod dev
```

## 5. Common Mistakes

1. **Assuming a Pod IP is stable long-term.** Pod IPs are ephemeral — they change on every reschedule, restart, or rolling update. Never hardcode a Pod IP in config or code; always go through a Service name.
2. **Calling a service from another namespace without the namespace suffix.** As shown in the lab, `/etc/resolv.conf`'s search list only includes your *own* namespace by default, so `curl backend-service` from a different namespace silently fails (or worse, resolves to a same-named Service in your own namespace by mistake) — always use `service.namespace` or the full FQDN cross-namespace.
3. **Not realizing NetworkPolicies are additive and "default-deny-once-any-policy-selects-a-pod."** Engineers sometimes add a narrow NetworkPolicy expecting it to be the *only* rule, not realizing that (a) once a Pod is selected by any policy, all traffic not explicitly allowed by *some* policy is denied, and (b) multiple matching policies are OR'd together, not merged with AND/override semantics — this causes both "why is my Pod suddenly unreachable" and "why is this traffic still allowed" surprises.
4. **Confusing CNI plugin responsibilities with kube-proxy's.** The CNI plugin assigns Pod IPs and moves packets between Pods/nodes (the "flat network" fabric); kube-proxy has nothing to do with Pod IP assignment — it only programs rules to redirect traffic aimed at *Service* virtual IPs to real Pod IPs. If Pod-to-Pod direct communication is broken, look at the CNI plugin/overlay; if Service-IP traffic isn't reaching Pods, look at kube-proxy and Endpoints.
5. **Forgetting that DNS lookups have a search-list cost (`ndots:5`).** Using a fully-qualified name with a trailing dot (`backend-service.prod.svc.cluster.local.`) skips the search list and is faster/more predictable — heavy `ndots:5` resolution is a known source of latency and DNS load in large clusters.
6. **Assuming Services load-balance perfectly evenly.** iptables-mode kube-proxy uses random selection per connection (not true round-robin), which can skew distribution with few backends or long-lived connections; IPVS mode offers better algorithms (round-robin, least-connection, etc.) for high-traffic Services.

## 6. Interview Questions (with brief answers)

1. **How does service discovery work in Kubernetes?** — Primarily via DNS: CoreDNS watches Services/Endpoints and creates records like `service.namespace.svc.cluster.local`, resolving to the Service's stable ClusterIP. kube-proxy then load-balances that ClusterIP's traffic across the Pods listed in the Service's Endpoints/EndpointSlice objects. A legacy, less-reliable method injects environment variables at Pod startup, but it's order-dependent and rarely used today.
2. **What is a CNI plugin, and why does Kubernetes need one?** — Container Network Interface plugin (e.g., Calico, Flannel, Cilium) is what actually implements Kubernetes' networking model: assigning each Pod a unique IP and ensuring Pod-to-Pod traffic can flow across nodes without NAT. Kubernetes defines the *contract* (flat, NAT-less Pod network) but delegates the *implementation* to the CNI plugin, which is why different clusters can use different networking backends interchangeably.
3. **What's the difference between kube-proxy and a CNI plugin?** — The CNI plugin handles Pod IP assignment and Pod-to-Pod packet delivery (the network fabric). kube-proxy handles Service virtual-IP translation — it programs iptables/IPVS rules so traffic to a Service's ClusterIP gets DNAT'd to one of the Service's backing Pod IPs. They solve different, complementary problems.
4. **Why would `curl my-service` fail from a different namespace but work from the same namespace?** — Because a Pod's `/etc/resolv.conf` search list only appends the Pod's own namespace by default (plus `svc.cluster.local`, `cluster.local`). A bare short name only resolves against Services in your own namespace unless you add the target namespace (`my-service.other-ns`) or use the full FQDN.
5. **What happens to network traffic between two Pods when a NetworkPolicy is applied?** — If no NetworkPolicy selects a Pod, all traffic is allowed by default. The instant any NetworkPolicy's selector matches a Pod, that Pod's traffic (for the covered direction — ingress and/or egress) becomes default-deny, and only traffic explicitly permitted by at least one matching policy's rules is allowed. Multiple policies on the same Pod are combined additively (OR), not restrictively (AND).

## 7. Quiz (50 Questions)

**True/False:**
1. Every Pod in Kubernetes gets its own unique cluster-wide IP address. (T)
2. NAT is required for one Pod to talk to another Pod on a different node. (F)
3. kube-proxy is responsible for assigning IP addresses to Pods. (F)
4. CoreDNS runs as Pods inside the `kube-system` namespace. (T)
5. DNS-based service discovery is the primary/recommended method in Kubernetes. (T)
6. Environment-variable-based service discovery updates automatically if a Service's IP changes. (F)
7. A Service's ClusterIP is a real IP bound to a network interface. (F)
8. CNI stands for Container Network Interface. (T)
9. By default, Kubernetes networking allows all Pods to talk to all other Pods. (T)
10. Once a NetworkPolicy selects a Pod, only explicitly allowed traffic to/from it is permitted (for the covered direction). (T)
11. IPVS is an alternative kube-proxy mode to iptables. (T)
12. All CNI plugins support NetworkPolicy enforcement. (F)
13. A Pod's `/etc/resolv.conf` search list includes every namespace in the cluster by default. (F)
14. Short-name Service lookups (e.g., `curl backend-service`) work across namespaces without modification. (F)
15. The full FQDN for a Service follows the pattern `service.namespace.svc.cluster.local`. (T)

**Multiple Choice:**
16. Which component programs iptables/IPVS rules for Service virtual IPs? a) CoreDNS b) kube-proxy c) kubelet d) CNI plugin → (b)
17. Which of these is a CNI plugin? a) CoreDNS b) Calico c) kube-proxy d) etcd → (b)
18. What does CoreDNS primarily provide? a) Pod scheduling b) Internal name resolution/service discovery c) Storage provisioning d) Node health checks → (b)
19. Which service discovery method is legacy and order-dependent? a) DNS-based b) Environment-variable-based c) NetworkPolicy-based d) Ingress-based → (b)
20. What is the default behavior of Kubernetes Pod-to-Pod networking absent any NetworkPolicy? a) Deny all b) Allow all c) Allow only same-namespace d) Requires manual routes → (b)
21. Which object lists the actual Pod IPs backing a Service? a) Ingress b) ConfigMap c) Endpoints/EndpointSlice d) NetworkPolicy → (c)
22. What is the purpose of `ndots:5` in a Pod's resolv.conf? a) Limits Pod count b) Controls DNS search-list behavior c) Sets IP TTL d) Configures CNI overlay → (b)
23. Which of these is NOT a responsibility of kube-proxy? a) Programming iptables/IPVS rules b) DNAT-ing Service VIP traffic c) Assigning Pod IP addresses d) Load-balancing across Endpoints → (c)
24. From namespace `dev`, which of these reliably reaches a Service named `api` in namespace `prod`? a) `curl api` b) `curl api.prod` c) `curl api.dev` d) `curl api.default` → (b)
25. What kind of network model does Kubernetes require CNI plugins to implement? a) NAT-heavy, per-host b) Flat, NAT-less, unique Pod IPs c) Hierarchical VLAN-only d) Broadcast-only → (b)

**Short Answer:**
26. What Kubernetes networking rule means "every Pod can reach every other Pod's IP directly"?
27. Name three example CNI plugins.
28. What two rule-based mechanisms can kube-proxy use to implement Service routing?
29. What DNS name format does a Kubernetes Service automatically get?
30. Why is environment-variable-based service discovery considered legacy?
31. What happens to Pod-to-Pod traffic the instant a NetworkPolicy selects a Pod?
32. Are multiple NetworkPolicies selecting the same Pod combined additively or restrictively?
33. What file inside a Pod shows its DNS resolver configuration?
34. What command lists the Pod IPs currently backing a Service?
35. Why shouldn't you hardcode a Pod's IP address in application config?

**Scenario-Based:**
36. Your frontend Pod in namespace `dev` calls `http://payments` and gets "host not found," but `payments` Service actually exists in namespace `finance`. What's wrong and how do you fix it?
37. A teammate says "Kubernetes networking is basically Docker's networking, just bigger." Correct this misunderstanding.
38. After deploying a new NetworkPolicy meant to restrict egress from `payment-service`, your team notices ingress traffic to unrelated Pods is now also being blocked. What likely went wrong?
39. Your Service is receiving traffic, but it's landing almost entirely on one of five backend Pods instead of being spread evenly. What kube-proxy detail explains this, and how might you mitigate it?
40. You migrate from Flannel to Calico as your CNI plugin specifically to gain NetworkPolicy enforcement. Explain why this migration was necessary.

**Fill in the Blank:**
41. The Kubernetes networking model requires that Pod-to-Pod communication needs no ______.
42. ______ is the cluster add-on that provides internal DNS-based service discovery.
43. The DNS suffix for a Service inside the cluster is `svc.______`.
44. kube-proxy's two main modes for programming rules are iptables and ______.
45. A NetworkPolicy acts like a ______ for Pod-to-Pod traffic.

**Conceptual Deep-Dive:**
46. Why does Kubernetes delegate networking implementation to CNI plugins instead of building it in natively?
47. Explain why a Service's ClusterIP is described as "virtual" rather than a real interface address.
48. How does the combination of Endpoints/EndpointSlice + kube-proxy allow a Service to keep working seamlessly through a rolling update?
49. Why is DNS-based discovery considered more robust than environment-variable-based discovery when Services are created dynamically?
50. How does the "default-allow-until-a-policy-exists" model of Kubernetes networking create a security responsibility that Hour 16 will address in depth?

---

## 8. Hour 15 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Flat Pod network** | Every Pod gets a unique cluster IP; Pod-to-Pod traffic needs no NAT, even across nodes |
| **CNI plugin** | Implements the flat network (Calico, Flannel, Cilium) — assigns Pod IPs, builds overlay/routed fabric, may enforce NetworkPolicy |
| **kube-proxy** | Programs iptables/IPVS rules to DNAT Service ClusterIP traffic to real Pod IPs from Endpoints/EndpointSlice |
| **CoreDNS** | Cluster DNS add-on; resolves `service.namespace.svc.cluster.local` to the Service's ClusterIP |
| **DNS-based discovery** | Primary method; short name works same-namespace, needs `service.namespace` or full FQDN cross-namespace |
| **Env-var discovery** | Legacy, order-dependent, injected only at Pod start — avoid relying on it |
| **NetworkPolicy** | Default-allow until a policy selects a Pod, then default-deny for that traffic direction; policies are additive (foreshadows Hour 16) |
| **Mental model** | Kubernetes networking = a city: Pods are buildings with unique addresses (IPs); CoreDNS is the universal phone directory (names → addresses) |

**Mnemonic:** *"CoKeD"* — **C**NI builds the flat network → **k**ube-proxy routes Service VIPs → **e**ndpoints track real Pod IPs → **D**NS (CoreDNS) lets everyone call by name.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Cluster Networking](https://kubernetes.io/docs/concepts/cluster-administration/networking/) — the canonical explanation of the Pod networking model and CNI requirements
- [Kubernetes Official Docs — Service](https://kubernetes.io/docs/concepts/services-networking/service/) and [DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/) — deep dive on Service discovery mechanics
- [CoreDNS Official Docs](https://coredns.io/manual/toc/) — how CoreDNS resolves cluster DNS and how to customize it
- [Kubernetes Official Docs — Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/) — preview reading before Hour 16
- YouTube: "TechWorld with Nana — Kubernetes Networking Explained" (free, strong visuals on CNI/kube-proxy/CoreDNS)

**Mini-Project for Hour 15 (30–40 min):**
- Create two namespaces, `shop` and `checkout`. Deploy a simple HTTP echo Deployment + Service in each (reuse the `hashicorp/http-echo` pattern from the lab).
- From a test Pod in `shop`, practice calling the `checkout` Service using: (a) the bare short name (should fail), (b) `service.checkout` (should work), (c) the full FQDN `service.checkout.svc.cluster.local` (should work).
- Run `kubectl get endpoints` in both namespaces while scaling replicas up/down, and observe how the Endpoints list changes live — tying this back to how kube-proxy would reprogram its rules.
- Bonus: write a minimal `NetworkPolicy` that denies all ingress to the `checkout` namespace by default, then add a second policy that explicitly allows traffic only from Pods labeled `app=shop` — confirm the additive default-deny-once-selected behavior firsthand, setting up for Hour 16.
