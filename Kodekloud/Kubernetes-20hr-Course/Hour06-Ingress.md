# Hour 6: Ingress, Ingress Controller, DNS

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine an office building with 10 different companies, each on its own floor. The bad way to handle visitors is to give **every company its own street entrance** with its own doorman — expensive to build, expensive to staff. The smart way is **one lobby with a single receptionist**. A visitor walks in and says "I'm here for Acme Corp, 3rd floor" or "I have a delivery for the API department," and the receptionist routes them to the right floor/department. That receptionist is an **Ingress**. The building's actual reception desk staff who read the sign-in sheet and know where to send people is the **Ingress Controller**. The sign-in sheet/rules themselves ("Acme Corp → 3rd floor," "Deliveries → loading dock") is the **Ingress resource**.

In Hour 5, you learned that a `Service` of type `LoadBalancer` gets its own external IP from the cloud provider. That's fine for one app — but if you have 10 apps, you'd need 10 cloud LoadBalancers, and **each one costs money** (cloud providers charge per LoadBalancer, often $15-20+/month each). That's wasteful when all 10 apps could share a single public entry point and be routed by hostname or URL path.

**Technical version:**
- **Ingress** is a Kubernetes API object that defines **HTTP/HTTPS routing rules** — "if the request's Host header is `api.example.com`, send it to `Service A`; if it's `app.example.com`, send it to `Service B`; if the path is `/checkout`, send it to `Service C`."
- **Ingress does nothing by itself.** It's just a declarative set of rules sitting in etcd. You need an **Ingress Controller** — an actual running pod (usually a reverse proxy like **NGINX**, **Traefik**, **HAProxy**, or a cloud-managed one like **AWS ALB Ingress Controller** or **GKE Ingress**) that watches the API server for Ingress objects and configures itself (reloads NGINX config, etc.) to actually implement those rules.
- This is the single most common source of confusion for beginners: **creating an Ingress YAML with no controller installed does absolutely nothing.** The Ingress object will just sit there, unrouted, often with no external `ADDRESS` assigned.
- **Path-based routing:** one host, multiple paths. `example.com/api` → `api-service`, `example.com/app` → `frontend-service`. Configured via `path` + `pathType` (`Prefix`, `Exact`, `ImplementationSpecific`) in the Ingress spec.
- **Host-based routing:** different subdomains/domains route to entirely different services. `api.example.com` → `api-service`, `shop.example.com` → `shop-service`. Configured via the `host` field per rule.
- **TLS termination:** the Ingress Controller can terminate HTTPS at the edge — it holds the TLS certificate (often stored as a Kubernetes `Secret` of type `kubernetes.io/tls`), decrypts incoming HTTPS traffic, and forwards plain HTTP internally to the Service/Pod. This means your app pods don't need to handle certificates themselves. Tools like **cert-manager** automate issuing/renewing these certs (e.g., via Let's Encrypt) — mentioned here, covered in more depth later in real projects.
- **Cluster DNS tie-back (Hour 5 → Hour 6):** Once the Ingress Controller decides "this goes to `api-service`," it still needs to resolve that Service to Pod IPs. This happens through **CoreDNS**, the cluster's internal DNS server. Every Service automatically gets a DNS name: `<service-name>.<namespace>.svc.cluster.local`. So `api-service` in namespace `shop` becomes `api-service.shop.svc.cluster.local`, which CoreDNS resolves to the Service's cluster IP, which `kube-proxy` (from Hour 5) then load-balances across the backing Pods' IPs via iptables/IPVS rules. Ingress handles "which Service for this URL," and cluster DNS + kube-proxy handle "how do I actually reach that Service."
- **External DNS vs internal DNS:** the `api.example.com` hostname in your Ingress rule is **external DNS** — it must point (via an A/CNAME record with your domain registrar or in `/etc/hosts` for local testing) to the IP of the Ingress Controller's LoadBalancer/NodePort. This is separate from CoreDNS, which only resolves names *inside* the cluster.

## 2. Diagram

```
                         INTERNET
                            │
                 DNS: api.shop.com  ─┐
                 DNS: shop.com      ─┤  (both resolve to the SAME IP)
                            │        │
                            ▼        ▼
                 ┌───────────────────────────────┐
                 │   Ingress Controller (NGINX)   │   <- one LoadBalancer Service
                 │   (reads Ingress rules, proxies)│      (only ONE external IP needed!)
                 └───────────────┬────────────────┘
                                 │
              reads rules from: │
                 ┌───────────────────────────────┐
                 │         Ingress Resource        │
                 │  host: api.shop.com  path: /   │──────┐
                 │  host: shop.com      path: /   │──┐   │
                 │  host: shop.com   path: /admin │  │   │
                 └───────────────────────────────┘  │   │
                                                      │   │
                     ┌────────────────────────────────┘   │
                     ▼                                    ▼
          ┌─────────────────────┐              ┌─────────────────────┐
          │  frontend-service    │              │   api-service        │
          │  (ClusterIP)         │              │   (ClusterIP)         │
          └──────────┬──────────┘              └──────────┬──────────┘
                     │  CoreDNS resolves:                  │  CoreDNS resolves:
                     │  frontend-service.shop               │  api-service.shop
                     │  .svc.cluster.local                  │  .svc.cluster.local
                     ▼                                       ▼
          ┌─────────────────────┐              ┌─────────────────────┐
          │  Frontend Pods        │              │   API Pods            │
          └─────────────────────┘              └─────────────────────┘

   shop.com/admin ─────────────────────────────────► admin-service (path-based)
```

## 3. Real-World Example

An e-commerce company runs two apps: a **React storefront** and a **Node.js API backend**. The naive approach is two `LoadBalancer` Services — two separate cloud load balancers, two separate monthly bills, two IPs to manage in DNS.

Instead, they deploy **one NGINX Ingress Controller** (which itself is backed by a single `LoadBalancer` Service — the *only* one they pay for) and create **one Ingress resource** with two rules:
- `Host: shop.com` → routes to `frontend-service`
- `Host: api.shop.com` → routes to `backend-service`

Both `shop.com` and `api.shop.com` DNS records point to the **same** external IP (the Ingress Controller's LoadBalancer IP). The Ingress Controller inspects the `Host` header of each incoming request and forwards it to the correct backend Service internally. Result: **one LoadBalancer instead of N**, centralized TLS termination (one certificate setup point, or one per host via SNI), and a single place to add rate-limiting, auth, or redirects for all apps. This is exactly the pattern used by companies like Meesho, Swiggy, and virtually all multi-service platforms — dozens or hundreds of internal Services, but only a handful of Ingress Controllers/LoadBalancers at the edge.

## 4. Hands-On Lab

**Goal:** Install an Ingress Controller, deploy two apps, and route traffic to both via path-based rules on a single Ingress.

```bash
# 1. Enable the NGINX Ingress addon on Minikube
minikube addons enable ingress

# Verify the controller pod is running (may take 1-2 min)
kubectl get pods -n ingress-nginx
```

**Expected output:**
```
NAME                                        READY   STATUS      RESTARTS   AGE
ingress-nginx-admission-create--1-xxxxx     0/1     Completed   0          60s
ingress-nginx-controller-xxxxxxxxxx-xxxxx   1/1     Running     0          60s
```

```bash
# 2. Deploy two simple demo apps
kubectl create deployment app-one --image=hashicorp/http-echo -- -text="Hello from App One"
kubectl create deployment app-two --image=hashicorp/http-echo -- -text="Hello from App Two"

kubectl expose deployment app-one --port=5678
kubectl expose deployment app-two --port=5678

kubectl get pods,svc
```

```bash
# 3. Create the Ingress with path-based rules
cat <<'EOF' > ingress-demo.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: demo-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  rules:
  - host: demo.local
    http:
      paths:
      - path: /one
        pathType: Prefix
        backend:
          service:
            name: app-one
            port:
              number: 5678
      - path: /two
        pathType: Prefix
        backend:
          service:
            name: app-two
            port:
              number: 5678
EOF

kubectl apply -f ingress-demo.yaml
kubectl get ingress
```

**Expected output:**
```
NAME           CLASS   HOSTS        ADDRESS        PORTS   AGE
demo-ingress   nginx   demo.local   192.168.49.2   80      45s
```

```bash
# 4. Point demo.local to the Ingress IP (local testing only)
echo "$(minikube ip) demo.local" | sudo tee -a /etc/hosts

# 5. Test with curl
curl demo.local/one
curl demo.local/two
```

**Expected output:**
```
Hello from App One
Hello from App Two
```

**Troubleshooting:**
- **Ingress has no `ADDRESS`** — the controller isn't running yet, or wasn't installed at all. Run `kubectl get pods -n ingress-nginx` and wait for `Running`; on some setups, run `minikube tunnel` in a separate terminal to expose the LoadBalancer IP.
- **`curl: (7) Failed to connect`** — `/etc/hosts` wasn't updated, or you used the wrong IP. Re-run `minikube ip` and confirm it matches the entry in `/etc/hosts`.
- **404 from "default backend"** — your request's `Host` header didn't match any rule in the Ingress (e.g., you curled the IP directly instead of `demo.local`), or the path didn't match `pathType`. Always test with `-H "Host: demo.local"` if not using `/etc/hosts`: `curl -H "Host: demo.local" http://<ingress-ip>/one`.
- **404 even with correct host/path** — check `pathType`. `Exact` requires an exact match; `Prefix` matches sub-paths. Also check the `rewrite-target` annotation — if your backend app doesn't expect the path to be rewritten to `/`, you'll get a 404 from the app itself.

## 5. Common Mistakes

1. **Creating an Ingress resource without installing an Ingress Controller.** The YAML applies successfully (no error!) but nothing happens — no address, no routing — because there's no controller watching for it. Always confirm a controller (`ingress-nginx`, Traefik, etc.) is running first.
2. **Forgetting to update `/etc/hosts` (or real DNS) when testing host-based rules locally.** Without it, `curl demo.local` fails to resolve, or resolves to the wrong IP, and people mistakenly blame the Ingress config.
3. **Path rewrite rules causing 404s.** Using `nginx.ingress.kubernetes.io/rewrite-target: /` rewrites `/one/foo` to `/foo` before forwarding — if the backend app actually expects `/one/foo`, it now 404s. Rewrite annotations must match what the backend expects.
4. **Mismatched Service port names/numbers.** If the Ingress backend references `port: number: 8080` but the Service actually listens on `5678`, or if using named ports (`port: name: http`) that don't exist on the Service, the controller can't route and returns a 502/503.
5. **Forgetting `ingressClassName`** (or the older `kubernetes.io/ingress.class` annotation) when multiple Ingress Controllers exist in a cluster — the Ingress may not bind to the controller you expect, or no controller claims it at all.
6. **Assuming Ingress handles Layer 4 (TCP/UDP).** Ingress is HTTP/HTTPS (Layer 7) only. For raw TCP/UDP traffic you need a `LoadBalancer` Service or special controller configuration (e.g., NGINX's TCP/UDP ConfigMap).

## 6. Interview Questions (with brief answers)

1. **What is the difference between an Ingress and a LoadBalancer Service?** — A `LoadBalancer` Service provisions one cloud load balancer with one external IP per Service, working at L4 (TCP/UDP). An Ingress is an L7 (HTTP/HTTPS) routing ruleset that lets many Services share a single entry point/LoadBalancer, routing by host and path.
2. **What is an Ingress Controller, and why is it required?** — It's the actual software (e.g., NGINX, Traefik) running as pods in the cluster that watches Ingress objects via the API server and configures itself (e.g., generates NGINX config, reloads) to implement the routing rules. Without it, Ingress resources are just inert data — no traffic is ever actually routed.
3. **How does path-based routing differ from host-based routing in Ingress?** — Path-based routing sends traffic to different Services based on the URL path under the same hostname (e.g., `shop.com/api` vs `shop.com/app`). Host-based routing uses the `Host` header/domain itself to differentiate (e.g., `api.shop.com` vs `shop.com`), each mapping to different Services.
4. **How does TLS termination work with Ingress?** — The Ingress Controller holds a TLS certificate (usually via a `kubernetes.io/tls` Secret referenced in the Ingress's `tls` section), decrypts incoming HTTPS at the edge, and forwards unencrypted HTTP to the backend Service/Pods internally, centralizing certificate management instead of requiring every app to handle TLS itself.
5. **How does cluster DNS (CoreDNS) fit into the Ingress traffic path?** — After the Ingress Controller decides which Service should handle a request, it (like anything else in the cluster) resolves that Service via CoreDNS using the name `<service>.<namespace>.svc.cluster.local`, which returns the Service's stable ClusterIP; `kube-proxy` then load-balances to the underlying Pod IPs. Ingress handles "which Service," cluster DNS + kube-proxy handle "how to reach it."

## 7. Quiz (50 Questions)

**True/False:**
1. An Ingress resource works without any Ingress Controller installed. (F)
2. NGINX and Traefik are examples of Ingress Controllers. (T)
3. Ingress operates at Layer 7 (HTTP/HTTPS). (T)
4. You need a separate cloud LoadBalancer for every app if you use Ingress correctly. (F)
5. Host-based routing uses the `Host` header to decide which Service handles a request. (T)
6. Path-based routing can send `/api` and `/app` under the same domain to different Services. (T)
7. CoreDNS is responsible for resolving Service names inside the cluster. (T)
8. TLS termination at the Ingress Controller means backend Pods must also decrypt HTTPS themselves. (F)
9. `minikube addons enable ingress` installs the NGINX Ingress Controller on Minikube. (T)
10. An Ingress resource can route both TCP and HTTP traffic natively out of the box. (F)

**Multiple Choice:**
11. What does an Ingress Controller do? a) Stores Ingress YAML in etcd b) Implements the routing rules defined in Ingress resources c) Replaces kube-proxy d) Assigns Pod IPs → (b)
12. Which field differentiates rules for `api.example.com` vs `app.example.com`? a) path b) host c) pathType d) namespace → (b)
13. What does `pathType: Prefix` mean? a) Exact match only b) Matches the path and any sub-paths c) Matches based on regex only d) Ignores the path entirely → (b)
14. Where is a TLS certificate typically stored for Ingress? a) ConfigMap b) Secret of type kubernetes.io/tls c) Environment variable d) Ingress annotation directly → (b)
15. What is the internal DNS format for a Kubernetes Service? a) service.namespace.svc.cluster.local b) namespace.service.k8s.local c) service://namespace d) svc.service.local → (a)
16. Which component actually reloads its config when an Ingress resource changes (for NGINX Ingress)? a) kube-scheduler b) CoreDNS c) The NGINX Ingress Controller pod d) etcd → (c)
17. What happens if you apply an Ingress YAML with no controller running? a) Error at apply time b) It silently does nothing / gets no address c) It automatically creates a controller d) Traffic is dropped with a 500 → (b)
18. Which is NOT typically an Ingress Controller? a) Traefik b) HAProxy Ingress c) kube-proxy d) NGINX Ingress → (c)
19. What does SNI-based routing at the TLS layer allow? a) Multiple domains to share one IP with per-host certificates b) Removing the need for DNS c) Bypassing the Ingress Controller d) Encrypting traffic inside the cluster only → (a)
20. What tool is commonly used to automate certificate issuance/renewal for Ingress? a) kubeadm b) cert-manager c) kube-proxy d) CoreDNS → (b)

**Short Answer:**
21. In one sentence, explain why Ingress is more cost-effective than one LoadBalancer Service per app.
22. What's the difference between an Ingress resource and an Ingress Controller?
23. What command enables the NGINX Ingress addon on Minikube?
24. What annotation is often used to rewrite request paths before forwarding to a backend?
25. What DNS name would CoreDNS use to resolve a Service named `cart` in namespace `shop`?
26. Why must you edit `/etc/hosts` when testing Ingress host rules locally?
27. What HTTP status code often indicates a request didn't match any Ingress rule?
28. What's the role of `ingressClassName` in an Ingress spec?
29. What layer of the OSI model does Ingress operate at, and why does that matter for TCP/UDP traffic?
30. Name one Ingress Controller other than NGINX.

**Scenario-Based:**
31. Your company has 15 microservices, and finance complains about the cloud bill for load balancers. What Kubernetes feature would you propose, and why?
32. You applied an Ingress YAML, but `kubectl get ingress` shows no `ADDRESS`. What do you check first?
33. Two teams want `api.company.com` and `admin.company.com` to route to different backend Services through the same public IP. What Ingress feature enables this?
34. A junior engineer created an Ingress but forgot to install a controller, then says "Kubernetes is broken." How do you explain what's actually happening?
35. Your Ingress rewrites `/orders` to `/`, but the backend app expects requests at `/orders`. What will likely happen, and how do you fix it?
36. You need to terminate TLS for `shop.com` at the edge and forward plain HTTP internally. What Kubernetes object holds the certificate, and where is it referenced?
37. A request to `shop.com/random-path` returns a 404 from something called the "default backend." What does that mean?
38. Your API and frontend are both healthy per `kubectl get pods`, but curling through Ingress returns 502. What's a likely mismatch to check?
39. You want to test Ingress locally without owning a real domain. What's the standard workaround?
40. Management wants zero application code changes but still wants HTTPS enforced at the edge. What Ingress capability delivers this?

**Fill in the Blank:**
41. Ingress rules are enforced by the Ingress ______, not the Ingress resource itself.
42. Routing traffic based on the URL path is called ______-based routing.
43. Routing traffic based on the domain name is called ______-based routing.
44. The cluster's internal DNS server that resolves Service names is called ______.
45. The full internal DNS name format for a Service is `<service>.<namespace>.svc.______`.
46. Terminating HTTPS at the Ingress Controller and forwarding plain HTTP internally is called TLS ______.
47. The Minikube command to enable the built-in Ingress Controller is `minikube addons ______ ingress`.
48. A Kubernetes object of type `kubernetes.io/tls` is used to store a ______ and private key.
49. If no Ingress Controller matches an Ingress's class, the Ingress will have no ______ assigned.
50. The tool commonly used to automate Let's Encrypt certificate issuance in clusters is ______.

**Conceptual Deep-Dive:**
_(Continuing short-answer style, folded into the 50 above.)_

---

## 8. Hour 6 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Why Ingress exists** | Avoids needing one costly cloud LoadBalancer per app; multiple apps share one entry point |
| **Ingress resource** | Declarative routing rules (host/path → Service); does nothing on its own |
| **Ingress Controller** | The actual proxy (NGINX, Traefik, etc.) that reads and implements Ingress rules |
| **Path-based routing** | Same host, different paths → different Services (`/api`, `/app`) |
| **Host-based routing** | Different hostnames → different Services (`api.example.com`, `app.example.com`) |
| **TLS termination** | Ingress Controller decrypts HTTPS at the edge; internal traffic is plain HTTP |
| **CoreDNS tie-back** | Resolves `service.namespace.svc.cluster.local` to ClusterIP; kube-proxy handles the rest |
| **Mental model** | Ingress Controller = building receptionist; Ingress rules = the sign-in sheet; LoadBalancer-per-app = wasteful separate entrances |
| **Lab outcome** | You deployed two apps and routed both through one Ingress with path-based rules |

**Mnemonic:** *"RICH"* — **R**ules live in the Ingress resource, **I**mplementation happens in the Ingress Controller, **C**oreDNS resolves Service names internally, **H**ost/path decide where traffic goes.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)
- [Kubernetes Official Docs — Ingress Controllers](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/)
- [NGINX Ingress Controller Docs](https://kubernetes.github.io/ingress-nginx/)
- [CoreDNS Documentation](https://coredns.io/manual/toc/)
- YouTube: "TechWorld with Nana — Ingress Explained" (free, excellent visuals)

**Mini-Project for Hour 6 (30-45 min):**
- Deploy two distinct apps (reuse `app-one`/`app-two` from the lab, or swap in real containers like an nginx welcome page and a simple Node.js API).
- Create a single Ingress resource with **path-based routing**: `/shop` → frontend Service, `/api` → backend Service.
- Add a second host rule (`shop.local`) pointing to the frontend, and confirm both `curl shop.local/api` and `curl demo.local/api` behave as expected based on your rules.
- Bonus: add a self-signed TLS Secret and enable TLS termination on the Ingress, then curl with `-k` to confirm HTTPS works end-to-end.
