# 🚢 DAY 5 — Ingress, Ingress Controllers & HTTP Routing

> **Prerequisites:** You finished Days 1–4. You can create Pods, Deployments, and Services, and you understand ClusterIP, NodePort, LoadBalancer, and how Kubernetes DNS resolves `service.namespace.svc.cluster.local`.

---

## 1. ## LEARNING OBJECTIVES

By the end of Day 5 you will be able to:

1. Explain **why Ingress exists** and why it is cheaper and saner than one LoadBalancer per service.
2. Distinguish the **Ingress resource** (declarative routing rules) from the **Ingress Controller** (the actual reverse proxy that enforces them).
3. Describe the **reverse proxy** model and how it underpins all Ingress traffic.
4. Configure **host-based routing** (`app.example.com` vs `api.example.com`).
5. Configure **path-based routing** (`/shop` vs `/api`) and pick the correct **`pathType`** (`Prefix`, `Exact`, `ImplementationSpecific`).
6. Terminate **TLS** at the Ingress using a Kubernetes `Secret`.
7. Use **IngressClass** to select a controller and set a **default backend**.
8. Understand where the **Gateway API** fits as the modern successor to Ingress.

---

## 2. ## 80/20 BREAKDOWN

The 20% of concepts that deliver 80% of real-world and interview value:

| Concept | Why it matters | Effort | Priority |
|---|---|---|---|
| Ingress vs Ingress Controller | The single most misunderstood point; nothing routes without a controller installed | Low | 🔴 CRITICAL |
| Path-based routing + `pathType` | Most common production pattern; `pathType` is a top interview trap | Medium | 🔴 CRITICAL |
| Host-based routing | Standard for multi-tenant / multi-app clusters | Low | 🔴 CRITICAL |
| TLS termination with Secrets | Every production Ingress terminates HTTPS | Medium | 🟠 HIGH |
| IngressClass | Decides which controller handles the rule | Low | 🟠 HIGH |
| Default backend & 404 behavior | #1 source of "why am I getting a 404" tickets | Low | 🟡 MEDIUM |
| Gateway API | The future; increasingly asked in senior interviews | Low (awareness) | 🟡 MEDIUM |

**Defer (not today):** annotations deep-dive (rewrite-target, rate limiting, auth), canary via Ingress, ExternalDNS automation, cert-manager + Let's Encrypt automation (we do *manual* self-signed TLS today), service mesh ingress gateways.

**🏆 Interview gold:**
- "An Ingress resource does nothing on its own — you must install an Ingress Controller."
- "Ingress is L7 (HTTP/HTTPS host & path); a Service of type LoadBalancer is L4 (IP:port)."
- "`pathType: Prefix` matches by path *segments*, not string prefix — `/foo` matches `/foo/bar` but NOT `/foobar`."
- "TLS is terminated at the Ingress controller; traffic to backend Pods is usually plain HTTP inside the cluster."

---

## 3. ## CONCEPT EXPLANATIONS

### 3.1 Why Ingress exists (vs many LoadBalancers)

**Beginner explanation:** On Day 4 you exposed a Service with `type: LoadBalancer`. On a cloud provider, each one provisions a *separate* external load balancer with its own public IP — and you pay for each. With 20 microservices you'd have 20 load balancers, 20 IPs, and 20 DNS records. Ingress lets you put **one** load balancer / entry point in front of the cluster and route HTTP traffic to many services based on the **hostname** and **URL path**.

**Analogy:** A LoadBalancer Service per app is like giving every department in a company its own street address and front door. An Ingress is the **building receptionist**: one address, one front door, and the receptionist reads the visitor's request ("I'm here for Accounting" / "I want the 3rd-floor café") and directs them to the right office.

**Production use case:** A SaaS company exposes `app.acme.com`, `api.acme.com`, and `admin.acme.com` through a single cloud load balancer and one Ingress controller, saving cost and centralizing TLS, logging, and rate limiting.

**Common mistakes:**
- Creating a LoadBalancer Service per microservice (cost explosion).
- Expecting Ingress to route TCP/UDP — Ingress is HTTP/HTTPS only (L7). For raw TCP use a LoadBalancer Service or the controller's TCP-passthrough feature.

**Best practices:** One Ingress controller per cluster (or per tier), terminate TLS centrally, and front it with a single cloud LB.

```
  WITHOUT Ingress                          WITH Ingress
  (one LB per service)                     (one LB, L7 routing)

  Internet                                 Internet
   │  │  │                                    │
   ▼  ▼  ▼                                    ▼
  LB LB LB   ← 3 IPs, 3 bills           [ Cloud LB ]  ← 1 IP
   │  │  │                                    │
   ▼  ▼  ▼                              [ Ingress Controller ]
  svcA svcB svcC                          │      │      │
                                        svcA   svcB   svcC
```

---

### 3.2 Ingress *resource* vs Ingress *controller*

**Beginner explanation:** This is the #1 gotcha. The **Ingress resource** is just a YAML object stored in etcd that says *"send `api.acme.com` to the `api` Service."* By itself it does **nothing** — there's no proxy reading it. The **Ingress Controller** is a real running Pod (typically NGINX, HAProxy, Traefik, or a cloud controller) that *watches* Ingress resources and reconfigures itself to actually route traffic. **No controller installed ⇒ your Ingress is inert.**

**Analogy:** The Ingress resource is the **sheet music**. The Ingress Controller is the **musician** who reads it and plays. Sheet music with no musician produces no sound.

**Production use case:** You install `ingress-nginx` via Helm; it deploys a controller Deployment + a LoadBalancer Service. From then on, every Ingress object you create is picked up automatically.

**Common mistakes:**
- Creating an Ingress and wondering why nothing works — no controller is installed.
- Installing two controllers without setting `ingressClassName`, causing both to fight over the same Ingress.

**Best practices:** Always install a controller first; always set `spec.ingressClassName`.

```
  ┌─────────────────┐  watches   ┌──────────────────────┐
  │ Ingress object  │◀───────────│  Ingress Controller  │
  │ (rules in etcd) │            │  (NGINX Pod, running)│
  └─────────────────┘            └──────────┬───────────┘
        declarative                         │ reconfigures itself
                                            ▼
                                     routes live traffic → Services → Pods
```

---

### 3.3 Reverse proxy concept

**Beginner explanation:** A **forward proxy** sits in front of *clients* (hides who is browsing). A **reverse proxy** sits in front of *servers* — clients connect to it, and it forwards requests to the right backend, then returns the response. An Ingress controller is a reverse proxy: clients hit it, it inspects the HTTP `Host` header and path, and proxies to the matching Service.

**Analogy:** A hotel concierge (reverse proxy) takes all guest requests at the desk and dispatches them to housekeeping, the kitchen, or the spa. Guests never talk to those departments directly.

**Production use case:** The reverse proxy also handles TLS termination, sets `X-Forwarded-For`/`X-Forwarded-Proto` headers, does load balancing across Pod replicas, and can add gzip, caching, and rate limits.

**Common mistakes:** Forgetting that backend apps now see the proxy's IP — read the client IP from `X-Forwarded-For`, not the socket.

**Best practices:** Trust and propagate `X-Forwarded-*` headers; configure `externalTrafficPolicy: Local` if you need true client IPs at the controller.

```
  Client ──HTTP──▶ [ Reverse Proxy / Ingress Ctrl ] ──HTTP──▶ Backend Pod
                    inspects Host + Path,
                    terminates TLS, adds headers
```

---

### 3.4 Host-based routing

**Beginner explanation:** Route based on the HTTP `Host` header (the domain). `shop.example.com` → shop Service, `api.example.com` → api Service — all on the same IP.

**Analogy:** Same building, different company nameplates by the door. The receptionist reads the nameplate (host) you ask for.

**Production use case:** Multi-tenant platforms where each tenant gets a subdomain pointing at the same cluster IP.

**Common mistakes:** DNS for all hosts must resolve to the controller's external IP; forgetting this means the browser never reaches the cluster.

**Best practices:** Use wildcard DNS (`*.example.com`) pointing at the LB, and a wildcard TLS cert.

```
  Host: shop.example.com ─┐
  Host: api.example.com  ─┤──▶ [Ingress Ctrl] ──┬─▶ shop-svc
  Host: admin.example.com─┘                     ├─▶ api-svc
                                                └─▶ admin-svc
```

---

### 3.5 Path-based routing

**Beginner explanation:** Same host, route by URL path. `acme.com/shop` → shop, `acme.com/api` → api.

**Analogy:** One company, one front door, signs inside pointing "Sales →" and "Support →".

**Production use case:** A single domain serving a frontend at `/` and an API at `/api`.

**Common mistakes:** Backend app expects `/users` but receives `/api/users`. You need a **rewrite annotation** (e.g. `nginx.ingress.kubernetes.io/rewrite-target`) to strip the prefix — Ingress does NOT rewrite by default.

**Best practices:** Design backends to be prefix-aware, or use rewrite annotations deliberately and test them.

```
  acme.com/shop/*  ─┐
  acme.com/api/*   ─┤──▶ [Ingress Ctrl] ──┬─▶ shop-svc
  acme.com/        ─┘                     ├─▶ api-svc
                                          └─▶ frontend-svc (catch-all)
```

---

### 3.6 `pathType` — Prefix / Exact / ImplementationSpecific

**Beginner explanation:** Every path rule MUST declare a `pathType`. There are three:

| `pathType` | Meaning |
|---|---|
| `Prefix` | Matches by URL **path segments** split on `/`. `/foo` matches `/foo` and `/foo/bar`, but **not** `/foobar`. |
| `Exact` | Matches the URL path **exactly**, case-sensitive. `/foo` matches only `/foo` (not `/foo/`). |
| `ImplementationSpecific` | Matching is delegated to the controller (e.g. NGINX regex). Behavior varies by controller — avoid unless you need it. |

**Analogy:** `Exact` = "must say the exact magic word." `Prefix` = "say a word that starts the right phrase, on word boundaries." `ImplementationSpecific` = "the bouncer decides by his own rules."

**Production use case:** Use `Prefix` with `/` for catch-all frontends and `/api` for the API; reserve `Exact` for single endpoints like a health check.

**Common mistakes:**
- Assuming `Prefix` is a string prefix — `/foo` does NOT match `/foobar`.
- Omitting `pathType` (it's required in the `networking.k8s.io/v1` API and the resource will be rejected).

**Best practices:** Default to `Prefix`. Use `Exact` sparingly.

```
  pathType: Prefix, path: /foo
     /foo        ✅
     /foo/       ✅
     /foo/bar    ✅
     /foobar     ❌  ← segment boundary, not string prefix!
```

---

### 3.7 TLS termination with Secrets

**Beginner explanation:** "TLS termination" means the Ingress controller **decrypts HTTPS at the edge**; traffic to backend Pods inside the cluster is then plain HTTP. The cert + key live in a Kubernetes `Secret` of type `kubernetes.io/tls`, referenced from `spec.tls`.

**Analogy:** A mailroom that opens all sealed envelopes (decrypts) at the building entrance and hands plain letters to each office.

**Production use case:** One wildcard cert in a Secret terminates HTTPS for all subdomains; backends stay HTTP, simplifying app code.

**Common mistakes:**
- Referencing a Secret that doesn't exist / is in the wrong namespace (must be same namespace as the Ingress).
- Using a Secret that isn't `type: kubernetes.io/tls` or has mismatched CN/SAN vs the host.

**Best practices:** Use cert-manager + Let's Encrypt in production for auto-renewal; keep the TLS Secret in the same namespace as the Ingress.

```
  Client ──HTTPS (443, encrypted)──▶ [Ingress Ctrl decrypts]
                                          │  (TLS Secret: cert+key)
                                          ▼
                                     ──HTTP (plain)──▶ Backend Pod
```

---

### 3.8 IngressClass

**Beginner explanation:** A cluster can have multiple controllers (e.g. nginx + an internal one). `IngressClass` is a cluster object naming a controller; an Ingress picks one via `spec.ingressClassName`. One IngressClass can be the **default** (annotation `ingressclass.kubernetes.io/is-default-class: "true"`) for Ingresses that omit the field.

**Analogy:** Choosing which courier company (FedEx vs DHL) handles your package by writing their name on the label.

**Production use case:** Public-facing `nginx-external` IngressClass plus an `nginx-internal` for internal-only services.

**Common mistakes:** Omitting `ingressClassName` with no default class set ⇒ no controller claims the Ingress ⇒ silent no-op.

**Best practices:** Always set `ingressClassName` explicitly.

---

### 3.9 Default backend

**Beginner explanation:** The **default backend** handles requests that match **no** rule (wrong host or unknown path). NGINX ships a default backend returning `404 - default backend`. You can override it with `spec.defaultBackend`.

**Analogy:** The "lost and found / general inquiries" desk for visitors who don't match any department.

**Production use case:** A custom 404 page or a redirect-to-marketing-site backend.

**Common mistakes:** Seeing `default backend - 404` and thinking the controller is broken — it actually means *no rule matched* (usually a host/path typo).

**Best practices:** Set a friendly custom default backend in production.

---

### 3.10 Gateway API — the future

**Beginner explanation:** The **Gateway API** is the successor to Ingress. Ingress is simple but limited (HTTP-centric, annotation-heavy for advanced features). Gateway API splits responsibilities across role-oriented resources: `GatewayClass` (infra), `Gateway` (listener/IP/port owned by ops), and `HTTPRoute`/`TCPRoute`/`GRPCRoute` (routing owned by app teams). It's more expressive (traffic splitting, header matching, cross-namespace refs) and portable across implementations.

**Analogy:** Ingress is a single overloaded form; Gateway API is a set of clean, role-separated forms — one for the building owner (Gateway), one for each tenant (HTTPRoute).

**Production use case:** New platforms adopt Gateway API for native traffic splitting/canary and multi-team ownership without vendor annotations.

**Best practices for now:** Know it exists, know the three core resources, prefer it for greenfield. Ingress remains everywhere and is still interview-default.

```
  Ingress (today)              Gateway API (future)
  ┌──────────┐                 GatewayClass  (who: infra provider)
  │ Ingress  │  ──evolves──▶   Gateway       (who: cluster ops)
  └──────────┘                 HTTPRoute      (who: app team)
```

---

## 4. ## HANDS-ON LABS

> Assumption: `kubectl` works and you have minikube (or any cluster). All YAML can be applied with `kubectl apply -f <file>`.

### LAB 1 — Install / enable an Ingress Controller

**minikube (easiest):**
```bash
minikube addons enable ingress
```
Expected:
```
💡  ingress is an addon maintained by Kubernetes...
🌟  The 'ingress' addon is enabled
```
Verify the controller is running:
```bash
kubectl get pods -n ingress-nginx
```
Expected:
```
NAME                                        READY   STATUS      RESTARTS   AGE
ingress-nginx-admission-create-xxxxx        0/1     Completed   0          60s
ingress-nginx-admission-patch-xxxxx         0/1     Completed   0          60s
ingress-nginx-controller-66b9c8c8c9-abcde   1/1     Running     0          60s
```
Confirm the IngressClass exists:
```bash
kubectl get ingressclass
```
Expected:
```
NAME    CONTROLLER             PARAMETERS   AGE
nginx   k8s.io/ingress-nginx   <none>       2m
```

**Generic / kind cluster (Helm):**
```bash
helm upgrade --install ingress-nginx ingress-nginx \
  --repo https://kubernetes.github.io/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

---

### LAB 2 — Path-based routing to two services

Deploy two simple apps (`shop` and `api`) using the echo image so we can see which one answers:

```yaml
# apps.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: shop
spec:
  replicas: 1
  selector: { matchLabels: { app: shop } }
  template:
    metadata: { labels: { app: shop } }
    spec:
      containers:
        - name: shop
          image: hashicorp/http-echo:0.2.3
          args: ["-text=Hello from SHOP", "-listen=:5678"]
          ports: [{ containerPort: 5678 }]
---
apiVersion: v1
kind: Service
metadata:
  name: shop-svc
spec:
  selector: { app: shop }
  ports: [{ port: 80, targetPort: 5678 }]
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api
spec:
  replicas: 1
  selector: { matchLabels: { app: api } }
  template:
    metadata: { labels: { app: api } }
    spec:
      containers:
        - name: api
          image: hashicorp/http-echo:0.2.3
          args: ["-text=Hello from API", "-listen=:5678"]
          ports: [{ containerPort: 5678 }]
---
apiVersion: v1
kind: Service
metadata:
  name: api-svc
spec:
  selector: { app: api }
  ports: [{ port: 80, targetPort: 5678 }]
```

Now the Ingress with path routing:

```yaml
# ingress-path.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: path-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  rules:
    - host: demo.local
      http:
        paths:
          - path: /shop
            pathType: Prefix
            backend:
              service:
                name: shop-svc
                port:
                  number: 80
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-svc
                port:
                  number: 80
```

Apply and inspect:
```bash
kubectl apply -f apps.yaml
kubectl apply -f ingress-path.yaml
kubectl get ingress
```
Expected:
```
NAME           CLASS   HOSTS        ADDRESS        PORTS   AGE
path-ingress   nginx   demo.local   192.168.49.2   80      20s
```

Test (point `demo.local` at the controller IP). On minikube use `minikube ip`:
```bash
IP=$(minikube ip)
curl -H "Host: demo.local" http://$IP/shop
curl -H "Host: demo.local" http://$IP/api
```
Expected:
```
Hello from SHOP
Hello from API
```

---

### LAB 3 — Host-based routing

Reuse `shop` and `api`. Route by hostname instead of path:

```yaml
# ingress-host.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: host-ingress
spec:
  ingressClassName: nginx
  rules:
    - host: shop.demo.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: { name: shop-svc, port: { number: 80 } }
    - host: api.demo.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: { name: api-svc, port: { number: 80 } }
```

```bash
kubectl apply -f ingress-host.yaml
IP=$(minikube ip)
curl -H "Host: shop.demo.local" http://$IP/
curl -H "Host: api.demo.local"  http://$IP/
```
Expected:
```
Hello from SHOP
Hello from API
```

> Tip: to use real hostnames in your browser, add to `/etc/hosts`:
> ```
> 192.168.49.2  shop.demo.local api.demo.local demo.local
> ```

---

### LAB 4 — TLS termination with a self-signed cert Secret

1. Generate a self-signed cert/key for `secure.demo.local`:
```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=secure.demo.local/O=demo" \
  -addext "subjectAltName=DNS:secure.demo.local"
```
Expected:
```
Generating a RSA private key
.......+++++
writing new private key to 'tls.key'
-----
```

2. Create the TLS Secret:
```bash
kubectl create secret tls demo-tls \
  --cert=tls.crt --key=tls.key
```
Expected:
```
secret/demo-tls created
```
Verify type:
```bash
kubectl get secret demo-tls -o jsonpath='{.type}'; echo
```
Expected:
```
kubernetes.io/tls
```

3. TLS Ingress:
```yaml
# ingress-tls.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: tls-ingress
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - secure.demo.local
      secretName: demo-tls
  rules:
    - host: secure.demo.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: { name: shop-svc, port: { number: 80 } }
```

```bash
kubectl apply -f ingress-tls.yaml
IP=$(minikube ip)
curl -k --resolve secure.demo.local:443:$IP https://secure.demo.local/
```
Expected (`-k` skips trust check since it's self-signed):
```
Hello from SHOP
```
Inspect the served cert:
```bash
curl -kv --resolve secure.demo.local:443:$IP https://secure.demo.local/ 2>&1 | grep "subject:"
```
Expected:
```
*  subject: CN=secure.demo.local; O=demo
```

---

## 5. ## EXERCISES

1. Add a third service `admin` and route `demo.local/admin` to it with `pathType: Prefix`. Verify with `curl`.
2. Change the `/api` rule to `pathType: Exact` and observe how `curl .../api/users` now behaves vs `curl .../api`.
3. Add a `spec.defaultBackend` pointing at `shop-svc` and confirm that an unmatched path no longer returns the NGINX 404.
4. Create a wildcard-style setup: route `tenant1.demo.local` and `tenant2.demo.local` to two different services, using host-based routing.
5. Convert LAB 2 into the **Gateway API** equivalent (a `Gateway` + two `HTTPRoute`s). (Requires Gateway API CRDs installed — research-only if not available.)

---

## 6. ## TROUBLESHOOTING SECTION

### Issue 1 — 404 "default backend - 404" from the Ingress
- **Symptoms:** `curl` returns `404 Not Found` with body `default backend - 404`.
- **Root cause:** No rule matched — usually a wrong `Host` header, wrong path, or wrong `pathType`.
- **Diagnosis:**
  ```bash
  kubectl describe ingress <name>      # check Host/Path columns
  kubectl get ingress <name> -o yaml   # confirm host & paths
  ```
  Re-run `curl` and confirm the `-H "Host:"` exactly matches a rule host.
- **Resolution:** Fix the host/path typo or `pathType`; ensure DNS/Host header matches a defined rule.

### Issue 2 — Ingress created but nothing routes (controller not installed)
- **Symptoms:** `kubectl get ingress` shows no `ADDRESS`; `curl` connection refused/times out.
- **Root cause:** No Ingress controller is running, or no IngressClass claims the Ingress.
- **Diagnosis:**
  ```bash
  kubectl get pods -A | grep ingress
  kubectl get ingressclass
  kubectl describe ingress <name>   # look for "no matching IngressClass"
  ```
- **Resolution:** Install a controller (`minikube addons enable ingress` or Helm) and set `spec.ingressClassName: nginx`.

### Issue 3 — Wrong `pathType` (path matches unexpectedly or not at all)
- **Symptoms:** `/foobar` unexpectedly hits the `/foo` rule, OR `/foo/` returns 404.
- **Root cause:** Misunderstanding `Prefix` (segment-based) vs `Exact` (literal).
- **Diagnosis:**
  ```bash
  kubectl get ingress <name> -o jsonpath='{.spec.rules[*].http.paths[*].pathType}'; echo
  ```
- **Resolution:** Use `Prefix` for tree matching, `Exact` only for one literal path. Remember `Prefix /foo` does NOT match `/foobar`.

### Issue 4 — TLS Secret missing or wrong
- **Symptoms:** Browser shows fake/`Kubernetes Ingress Controller Fake Certificate`, or controller logs `secret not found`.
- **Root cause:** Secret name/namespace mismatch, wrong Secret type, or cert CN/SAN doesn't match the host.
- **Diagnosis:**
  ```bash
  kubectl get secret <secretName>                       # exists? in Ingress's namespace?
  kubectl get secret <secretName> -o jsonpath='{.type}' # must be kubernetes.io/tls
  kubectl logs -n ingress-nginx deploy/ingress-nginx-controller | grep -i tls
  ```
- **Resolution:** Create the Secret in the **same namespace** as the Ingress, type `kubernetes.io/tls`, with CN/SAN matching the host in `spec.tls.hosts`.

### Issue 5 — Service backend not found / 503
- **Symptoms:** `503 Service Temporarily Unavailable` from the controller.
- **Root cause:** The referenced Service name/port is wrong, or the Service has no healthy endpoints (selector mismatch / Pods down).
- **Diagnosis:**
  ```bash
  kubectl get svc <name>
  kubectl get endpoints <name>     # empty = no backing Pods
  kubectl describe ingress <name>  # check backend service name & port
  ```
- **Resolution:** Fix the Service name/port in the Ingress; ensure Service selector matches Pod labels and Pods are Running.

---

## 7. ## QUIZ SECTION

**MCQ**

1. What happens when you create an Ingress resource but no Ingress controller is installed?
   a) Kubernetes auto-installs nginx  b) Traffic is routed by kube-proxy  c) Nothing — the resource is inert  d) The API rejects it

2. With `pathType: Prefix` and `path: /app`, which request matches?
   a) `/application`  b) `/app/v1`  c) `/myapp`  d) none

3. Where is TLS terminated in a standard Ingress setup?
   a) At each backend Pod  b) At the Ingress controller  c) At kube-proxy  d) At CoreDNS

**Short answer**

4. In one sentence, explain the difference between an Ingress resource and an Ingress controller.
5. What field selects which controller handles an Ingress, and what happens if it's omitted with no default class?

**Scenario**

6. You have `app.acme.com`, `api.acme.com`, and `admin.acme.com`, all needing HTTPS on one IP, with cost as a concern. Describe your approach.

---

### Answers

1. **c** — An Ingress does nothing without a controller watching it.
2. **b** — `Prefix` matches on path segments: `/app/v1` matches; `/application` and `/myapp` do not.
3. **b** — At the Ingress controller; backend traffic is typically plain HTTP.
4. The Ingress *resource* declares routing rules (data); the *controller* is the running reverse proxy that reads those rules and routes real traffic.
5. `spec.ingressClassName`. If omitted and no IngressClass is marked default, no controller claims the Ingress and it does nothing.
6. Install one Ingress controller behind a single cloud LoadBalancer (one IP/bill). Use host-based routing with three rules to the three Services, and a `spec.tls` block (ideally a wildcard `*.acme.com` cert via cert-manager) to terminate HTTPS centrally.

---

## 8. ## CHALLENGE PROJECT

**Goal:** Serve a `frontend`, `api`, and `admin` app under a single host `myapp.local` over HTTPS.
- `myapp.local/` → frontend
- `myapp.local/api` → api
- `myapp.local/admin` → admin
- TLS terminated at the Ingress with a self-signed cert.

**Reference solution:**

```yaml
# challenge.yaml — assumes frontend-svc, api-svc, admin-svc :80 exist
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: myapp-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  tls:
    - hosts: ["myapp.local"]
      secretName: myapp-tls
  rules:
    - host: myapp.local
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend: { service: { name: api-svc,      port: { number: 80 } } }
          - path: /admin
            pathType: Prefix
            backend: { service: { name: admin-svc,    port: { number: 80 } } }
          - path: /
            pathType: Prefix
            backend: { service: { name: frontend-svc, port: { number: 80 } } }
```

Setup:
```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=myapp.local" -addext "subjectAltName=DNS:myapp.local"
kubectl create secret tls myapp-tls --cert=tls.crt --key=tls.key
kubectl apply -f challenge.yaml

IP=$(minikube ip)
curl -k --resolve myapp.local:443:$IP https://myapp.local/
curl -k --resolve myapp.local:443:$IP https://myapp.local/api
curl -k --resolve myapp.local:443:$IP https://myapp.local/admin
```

> Note: order the more-specific paths (`/api`, `/admin`) before the catch-all `/`. NGINX matches longest-prefix, but explicit ordering keeps intent clear.

---

## 9. ## KNOWLEDGE CHECK

You're ready for Day 6 if you can, without notes:
- [ ] Explain why Ingress beats one LoadBalancer per service.
- [ ] State the difference between an Ingress resource and a controller — and why a controller must be installed.
- [ ] Write a host-based and a path-based Ingress from memory.
- [ ] Explain `Prefix` vs `Exact` and the `/foo` vs `/foobar` trap.
- [ ] Create a `kubernetes.io/tls` Secret and wire it into `spec.tls`.
- [ ] Diagnose a "default backend - 404" and a 503.
- [ ] Name the three core Gateway API resources.

---

## 10. ## CHEAT SHEET

```bash
# Controller
minikube addons enable ingress
kubectl get pods -n ingress-nginx
kubectl get ingressclass

# Ingress CRUD
kubectl get ingress [-A]
kubectl describe ingress <name>
kubectl get ingress <name> -o yaml
kubectl delete ingress <name>

# TLS Secret
kubectl create secret tls <name> --cert=tls.crt --key=tls.key
kubectl get secret <name> -o jsonpath='{.type}'

# Debug
kubectl get endpoints <svc>                       # empty = no backends
kubectl logs -n ingress-nginx deploy/ingress-nginx-controller
curl -H "Host: demo.local" http://$(minikube ip)/path
curl -k --resolve host:443:$(minikube ip) https://host/
```

| Field | Purpose |
|---|---|
| `spec.ingressClassName` | Which controller handles this Ingress |
| `spec.rules[].host` | Host-based routing |
| `spec.rules[].http.paths[].path` + `pathType` | Path-based routing |
| `spec.tls[].secretName` | TLS cert Secret for HTTPS |
| `spec.defaultBackend` | Catch-all for unmatched requests |

---

## 11. ## INTERVIEW PREPARATION

**Talking points that signal seniority:**
- "Ingress is L7 HTTP routing; for L4 TCP/UDP you still need a LoadBalancer/NodePort or controller TCP passthrough."
- "An Ingress resource is declarative config; the controller is the reconciling reverse proxy. Always install one and set `ingressClassName`."
- "TLS terminates at the controller; intra-cluster hops are plain HTTP unless you run mTLS via a service mesh."
- "`pathType: Prefix` is segment-based, not string-based — common bug source."
- "Cross-cutting features (rewrite, auth, rate limit) are controller annotations in Ingress; Gateway API standardizes much of this into typed fields."
- "Gateway API is the successor — role-oriented (GatewayClass/Gateway/HTTPRoute), better for canary and multi-team ownership."

---

## 12. ## 🎓 TOP 50 QUESTIONS

### Fundamentals (15)
1. What is a Kubernetes Ingress?
2. Why use Ingress instead of one LoadBalancer per service?
3. What is the difference between an Ingress resource and an Ingress controller?
4. Name three common Ingress controllers.
5. What OSI layer does Ingress operate at, and how does that differ from a LoadBalancer Service?
6. What is a reverse proxy and how does it relate to Ingress?
7. What is host-based routing?
8. What is path-based routing?
9. What are the three `pathType` values?
10. How does `Prefix` matching differ from a literal string prefix?
11. What is an IngressClass and `ingressClassName`?
12. What is a default backend?
13. What Secret type is used for Ingress TLS?
14. Where is TLS terminated in a standard Ingress setup?
15. What is the Gateway API and why does it exist?

### Practical (10)
16. Write the `kubectl` command to enable the minikube ingress addon.
17. How do you create a TLS Secret from a cert and key?
18. How do you list all Ingresses across namespaces?
19. How do you check which IngressClass exists in a cluster?
20. Write a minimal path-based Ingress YAML.
21. Write a minimal host-based Ingress YAML.
22. How do you reference a TLS Secret in an Ingress?
23. How do you test a host-routed Ingress with `curl` without DNS?
24. How do you view the NGINX controller logs?
25. How do you confirm a Service has healthy backends?

### Scenario (10)
26. Three apps, one domain, HTTPS, minimize cost — design it.
27. Each tenant needs a subdomain on one IP — approach?
28. Backend expects `/users` but the public path is `/api/users` — what do you add?
29. You need both an internal and external entry point — how?
30. You must serve a custom 404 page for unknown paths — how?
31. You need true client IPs at the backend — what do you configure?
32. You want canary traffic splitting — Ingress annotations vs Gateway API — which and why?
33. A wildcard cert must cover all subdomains — how do you wire it?
34. Migrating from per-service LoadBalancers to Ingress — what changes for DNS?
35. You need TCP (non-HTTP) exposure — is Ingress the right tool?

### Troubleshooting (10)
36. `curl` returns "default backend - 404" — what's wrong and how do you check?
37. Ingress shows no ADDRESS — likely cause?
38. `/foobar` unexpectedly matches the `/foo` rule — explain.
39. `/foo/` returns 404 with `Exact` — why?
40. Browser shows the fake Kubernetes certificate — diagnose.
41. Controller logs "secret not found" — fix?
42. 503 from the controller — what do you inspect first?
43. Two controllers both react to one Ingress — cause and fix?
44. TLS works for one host but not another in the same Ingress — likely cause?
45. Rewrite annotation seems ignored — what would you verify?

### Interview (5)
46. Explain Ingress to a non-technical stakeholder using an analogy.
47. Compare Ingress and Gateway API; when would you choose each?
48. What are the security considerations of terminating TLS at the Ingress?
49. How would you achieve zero-downtime cert rotation?
50. Walk through, end to end, a request from browser HTTPS to backend Pod.

> Brief answer keys — 1: L7 HTTP routing object. 2: cost/centralized TLS/single IP. 3: resource = rules, controller = proxy. 9: Prefix/Exact/ImplementationSpecific. 10: segment-based not string. 13: `kubernetes.io/tls`. 14: at the controller. 28: `rewrite-target` annotation. 36: no rule matched — check Host/path/pathType. 38: Prefix is segment-based but `/foobar` should NOT match — re-check rule/pathType. 50: DNS→LB→controller (TLS terminate, read Host+path)→Service→Endpoints→Pod.

---

## 13. ## FREE RESOURCES

| Resource | Type | Why |
|---|---|---|
| Kubernetes Ingress docs | Official docs | Canonical reference & API fields |
| ingress-nginx user guide | Official docs | Annotations, TLS, examples |
| Gateway API docs (gateway-api.sigs.k8s.io) | Official docs | The successor model |
| kubernetes.io Ingress tutorial (minikube) | Tutorial | Hands-on path routing |
| "Kubernetes Ingress Explained" videos (e.g. TechWorld with Nana) | Video | Visual reinforcement |
| killercoda / Play with Kubernetes | Interactive lab | Free browser cluster |

**Docs reading plan (this week):**
1. Read the official **Ingress** concept page top to bottom.
2. Skim the **ingress-nginx** annotations index (just headings).
3. Read the **Gateway API** "Introduction" + "API concepts" pages.

**Must-read:** Official Kubernetes Ingress concept page.
**Must-watch:** A 20-min Ingress walkthrough video that builds path + host + TLS.
**Must-do:** Labs 2–4 in this file, then the Challenge Project.

**Highest-ROI:** Internalize "resource vs controller" + `pathType: Prefix` semantics + manual TLS Secret wiring. These three cover most interview questions and most production 404/503/TLS tickets.

---

## 14. ## NEXT STEPS

**Active recall (do before moving on):**
- Close this file and, from memory, write a host-based AND a path-based Ingress YAML.
- Explain out loud, in 30 seconds, why an Ingress does nothing without a controller.
- Recreate the TLS lab from scratch without copy-pasting.
- Diagnose a "default backend - 404" by listing the three things you'd check.

When all four feel automatic, **Continue to Day 6**.
