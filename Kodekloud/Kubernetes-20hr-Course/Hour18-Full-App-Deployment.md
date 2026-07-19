# Hour 18: Build a Complete Application Deployment From Scratch

## 1. Explanation (Simple → Technical)

**Simple version:** So far you've learned individual kitchen tools — a knife (Pod), a recipe card (Deployment), a waiter (Service), a signboard (Ingress), a spice rack (ConfigMap/Secret), a pantry (Volume), a health inspector (Probes), a hiring manager (HPA), and a keycard system (RBAC). Today you open the **entire restaurant**. You'll wire every tool together into one working meal: a customer walks in (browser hits Ingress), places an order (frontend calls backend `/api`), the kitchen cooks it using ingredients from the pantry (backend queries the database), and the food comes out — all while the restaurant can survive a chef quitting (pod crash), a dinner rush (HPA), and a menu update (rolling deploy).

**Technical version:** We are building a classic **3-tier application**:

1. **Frontend tier** — an Nginx pod serving static HTML/JS. Stateless, scales horizontally, receives all `/` traffic from the Ingress.
2. **Backend tier** — a Python Flask API serving JSON at `/api/*`. Stateless, reads/writes to Postgres, exposes `/healthz` (liveness) and `/ready` (readiness), scales via HPA based on CPU.
3. **Database tier** — a Postgres StatefulPod (single Deployment + PVC for this lesson; true HA Postgres uses a StatefulSet, covered as an extension). Stateful, holds persistent data on a PersistentVolumeClaim, exposed only inside the cluster via a ClusterIP Service — **never** exposed externally.

The pieces glue together like this:
- **Namespace** (`capstone-app`) — isolates all these resources from other lessons' leftovers.
- **ConfigMap** — non-secret backend config (e.g. `DB_HOST`, `DB_NAME`, `LOG_LEVEL`).
- **Secret** — the Postgres password, injected as an env var, never committed to Git in plaintext.
- **PVC** — durable disk for Postgres so a pod restart doesn't wipe your data.
- **Deployments + Services** — one pair per tier; Services give each tier a stable DNS name (`db-service`, `backend-service`, `frontend-service`) so pods never hardcode IPs.
- **Probes** — liveness restarts a stuck container; readiness keeps a not-yet-ready pod out of the Service's traffic rotation (critical during startup and rolling updates).
- **Resource requests/limits** — so the scheduler places pods sensibly and one greedy pod can't starve the node.
- **Ingress** — a single external entry point routing path-based traffic: `/` → frontend, `/api` → backend.
- **HPA** — watches backend CPU and adds replicas under load.

This is precisely the shape of real production systems — the names change (React instead of static HTML, Node instead of Flask, MySQL instead of Postgres) but the **topology is identical** in the vast majority of web applications running on Kubernetes today.

## 2. Diagram

```
                         Internet / Browser
                                │
                                ▼
                     ┌─────────────────────┐
                     │       Ingress        │   host: capstone.local
                     │  /      → frontend    │
                     │  /api   → backend     │
                     └─────────┬───────────┘
                     ┌─────────┴───────────┐
                     ▼                     ▼
        ┌───────────────────┐   ┌───────────────────────┐
        │ frontend-service   │   │  backend-service       │
        │   (ClusterIP)       │   │   (ClusterIP)           │
        └─────────┬─────────┘   └───────────┬───────────┘
                  ▼                          ▼
      ┌─────────────────────┐   ┌─────────────────────────┐
      │ frontend Deployment  │   │ backend Deployment        │
      │  (nginx, 2 replicas) │   │ (flask, 2-6 via HPA)      │
      │  serves static HTML  │   │ readiness: /ready         │
      │                       │   │ liveness:  /healthz       │
      └─────────────────────┘   │ env from ConfigMap+Secret  │
                                 └────────────┬─────────────┘
                                              ▼
                                 ┌─────────────────────────┐
                                 │  db-service (ClusterIP)   │
                                 └────────────┬─────────────┘
                                              ▼
                                 ┌─────────────────────────┐
                                 │ postgres Deployment       │
                                 │  1 replica + PVC          │
                                 │  (persistent volume)      │
                                 └─────────────────────────┘

           All of the above live inside Namespace: capstone-app
```

## 3. Real-World Example

This exact pattern — **Ingress → stateless frontend → stateless API → stateful datastore** — is how most companies structure their first production Kubernetes workloads, including services at Meesho, Swiggy, and Zomato. A checkout page (frontend), an orders API (backend), and an orders database (Postgres/MySQL) map almost 1:1 onto what you're building today.

Why it mirrors real production:
- **Separation of concerns**: frontend, API, and DB scale and fail independently. A traffic spike on the homepage doesn't need to touch the database tier.
- **Config vs Secret split**: real orgs enforce this via policy — DB hostnames and log levels go in ConfigMaps (visible in `kubectl describe`), passwords go in Secrets (base64-encoded, RBAC-restricted, often backed by Vault/External Secrets Operator in production).
- **Service DNS over IPs**: production backends never hardcode a database IP — IPs churn every time a pod reschedules. They use the Kubernetes Service DNS name (`db-service.capstone-app.svc.cluster.local`), exactly like you will here.
- **Probes gate traffic during deploys**: this is what makes rolling updates *actually* zero-downtime instead of just "usually fine." Without readiness probes, a real production rollout WILL serve 500s for a few seconds on every deploy.
- **HPA on the bottleneck tier only**: real teams autoscale the API layer aggressively (stateless, cheap to replicate) but scale the database far more conservatively (stateful, expensive, often vertically scaled or read-replicated instead).

## 4. Hands-On Lab

**Goal:** Deploy a working 3-tier app end-to-end, verify each layer, route traffic through Ingress, then perform a rolling update and a rollback.

> Note: A production team would likely package steps 4.2–4.9 as a single **Helm chart** (Hour 17) with `values.yaml` overrides per environment. We apply raw manifests here so every wire is visible; converting this into a chart is this hour's mini-project extension.

### 4.1 Prerequisites

```bash
# Ensure ingress addon is enabled (Minikube)
minikube addons enable ingress
minikube addons enable metrics-server   # required for HPA

kubectl get pods -n ingress-nginx        # confirm ingress controller is Running
kubectl get pods -n kube-system | grep metrics-server
```

### 4.2 Namespace

```yaml
# 00-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: capstone-app
```

```bash
kubectl apply -f 00-namespace.yaml
kubectl config set-context --current --namespace=capstone-app   # optional convenience
```

**Every manifest below uses `namespace: capstone-app` explicitly** — do not rely on `--current` context alone; explicit namespaces prevent an entire class of "why is my pod in `default`" bugs.

### 4.3 ConfigMap (backend config)

```yaml
# 01-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-config
  namespace: capstone-app
data:
  DB_HOST: "db-service"          # Service DNS name, NOT a hardcoded IP
  DB_PORT: "5432"
  DB_NAME: "appdb"
  LOG_LEVEL: "info"
```

### 4.4 Secret (DB password)

```bash
# Never commit plaintext secrets — generate declaratively:
kubectl create secret generic db-secret \
  --namespace=capstone-app \
  --from-literal=POSTGRES_USER=appuser \
  --from-literal=POSTGRES_PASSWORD=SuperSecretPass123 \
  --dry-run=client -o yaml > 02-secret.yaml
```

```yaml
# 02-secret.yaml (generated — shown for reference)
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
  namespace: capstone-app
type: Opaque
data:
  POSTGRES_USER: YXBwdXNlcg==
  POSTGRES_PASSWORD: U3VwZXJTZWNyZXRQYXNzMTIz
```

```bash
kubectl apply -f 01-configmap.yaml -f 02-secret.yaml
```

### 4.5 Database tier: PVC + Deployment + Service

```yaml
# 03-db-pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: db-pvc
  namespace: capstone-app
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
---
# 04-db-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: capstone-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
          envFrom:
            - configMapRef:
                name: backend-config
            - secretRef:
                name: db-secret
          env:
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi
          volumeMounts:
            - name: db-storage
              mountPath: /var/lib/postgresql/data
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "appuser"]
            initialDelaySeconds: 5
            periodSeconds: 5
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", "appuser"]
            initialDelaySeconds: 15
            periodSeconds: 10
      volumes:
        - name: db-storage
          persistentVolumeClaim:
            claimName: db-pvc
---
# 05-db-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: db-service
  namespace: capstone-app
spec:
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
  # No "type" specified → defaults to ClusterIP: internal-only, by design
```

```bash
kubectl apply -f 03-db-pvc.yaml -f 04-db-deployment.yaml -f 05-db-service.yaml

# Verify layer 1 before moving on
kubectl get pvc -n capstone-app
kubectl get pods -n capstone-app -l app=postgres -w
```

**Expected output:**
```
NAME     STATUS   VOLUME   CAPACITY   ACCESS MODES   STORAGECLASS   AGE
db-pvc   Bound    pvc-xxxx  1Gi        RWO            standard       10s

NAME                        READY   STATUS    RESTARTS   AGE
postgres-7d9f8c9b7f-x2n4k   1/1     Running   0          20s
```

**Troubleshooting DB layer:**
- `PVC` stuck `Pending` → no default StorageClass. Run `kubectl get storageclass`; on Minikube one exists by default, on kind you may need `kind` with the `local-path-provisioner` add-on.
- Pod `CrashLoopBackOff` → `kubectl logs -n capstone-app deploy/postgres`; usually a bad `POSTGRES_PASSWORD` env or leftover corrupt data on a reused PVC.
- Readiness never true → exec into pod: `kubectl exec -it -n capstone-app deploy/postgres -- pg_isready`.

### 4.6 Backend tier: Deployment (with probes + resources) + Service

```yaml
# 06-backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: capstone-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
        - name: backend
          image: yourdockerhubuser/capstone-backend:v1
          ports:
            - containerPort: 5000
          envFrom:
            - configMapRef:
                name: backend-config
            - secretRef:
                name: db-secret
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 300m
              memory: 256Mi
          readinessProbe:
            httpGet:
              path: /ready
              port: 5000
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /healthz
              port: 5000
            initialDelaySeconds: 10
            periodSeconds: 10
            failureThreshold: 3
---
# 07-backend-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: backend-service
  namespace: capstone-app
spec:
  selector:
    app: backend
  ports:
    - port: 80
      targetPort: 5000
```

The Flask app's `/ready` handler should actively check its DB connection (e.g. `SELECT 1`) — that's what makes readiness meaningful here, not just "process is running."

```bash
kubectl apply -f 06-backend-deployment.yaml -f 07-backend-service.yaml
kubectl get pods -n capstone-app -l app=backend -w
```

**Expected output:**
```
NAME                       READY   STATUS    RESTARTS   AGE
backend-6f9c8d5b6c-abcde   1/1     Running   0          30s
backend-6f9c8d5b6c-fghij   1/1     Running   0          30s
```

**Verify backend can reach the database (before touching frontend):**
```bash
kubectl run tmp-curl --rm -it --image=curlimages/curl -n capstone-app -- \
  curl -s http://backend-service/api/health
```
**Expected:** `{"status":"ok","db":"connected"}`

**Troubleshooting backend layer:**
- `0/1 Ready` forever → readiness probe failing; check `kubectl describe pod` events for `Readiness probe failed: HTTP probe failed with statuscode: 500` — usually means DB connection is failing (check `DB_HOST` matches the Service name exactly, `db-service`, not `postgres` or `localhost`).
- `CrashLoopBackOff` → `kubectl logs` almost always shows a missing env var or a Python traceback on startup.
- Works via `kubectl exec` curl to `localhost:5000` but not via Service → check the Service's `selector` matches the pod's `labels` exactly (a common typo: `app: backend` vs `app: backend-api`).

### 4.7 Frontend tier: Deployment + Service

```yaml
# 08-frontend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  namespace: capstone-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      containers:
        - name: frontend
          image: nginx:1.27-alpine
          ports:
            - containerPort: 80
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 3
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 10
            periodSeconds: 10
---
# 09-frontend-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: frontend-service
  namespace: capstone-app
spec:
  selector:
    app: frontend
  ports:
    - port: 80
      targetPort: 80
```

```bash
kubectl apply -f 08-frontend-deployment.yaml -f 09-frontend-service.yaml
kubectl get pods -n capstone-app -l app=frontend
```

### 4.8 Ingress (path-based routing)

```yaml
# 10-ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: capstone-ingress
  namespace: capstone-app
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  ingressClassName: nginx
  rules:
    - host: capstone.local
      http:
        paths:
          - path: /api(/|$)(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: backend-service
                port:
                  number: 80
          - path: /()(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: frontend-service
                port:
                  number: 80
```

```bash
kubectl apply -f 10-ingress.yaml

# Point capstone.local at the ingress IP
echo "$(minikube ip) capstone.local" | sudo tee -a /etc/hosts

kubectl get ingress -n capstone-app
```

**End-to-end test:**
```bash
curl http://capstone.local/                # → frontend HTML
curl http://capstone.local/api/health      # → {"status":"ok","db":"connected"}
```
Open `http://capstone.local` in a browser and confirm the page loads and any "fetch data" button successfully calls `/api`.

**Troubleshooting Ingress:**
- `404` from Ingress controller default backend → `ingressClassName` mismatch, or the Service name/port in the Ingress doesn't match reality. `kubectl describe ingress capstone-ingress -n capstone-app` shows resolved backends.
- `/api` works but returns frontend's HTML → rewrite-target regex groups are off; verify the `(/|$)(.*)` capture groups exactly as above (this is the single most common Ingress typo).
- Browser can't resolve `capstone.local` → confirm `/etc/hosts` entry and that you used `minikube ip`, not `127.0.0.1`, when using the Docker driver.

### 4.9 HPA on the backend

```yaml
# 11-backend-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: capstone-app
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 2
  maxReplicas: 6
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
```

```bash
kubectl apply -f 11-backend-hpa.yaml
kubectl get hpa -n capstone-app -w
```

**Load test to trigger scale-out:**
```bash
kubectl run load-gen --rm -it --image=busybox -n capstone-app -- \
  /bin/sh -c "while true; do wget -q -O- http://backend-service/api/health; done"
```
Watch `kubectl get hpa -n capstone-app` — `REPLICAS` should climb from 2 toward 6 as `TARGET` CPU% rises past 60%, then scale back down a few minutes after you stop the load generator.

### 4.10 Rolling update and rollback (ties back to Hour 4)

```bash
# Simulate shipping v2 of the backend
kubectl set image deployment/backend backend=yourdockerhubuser/capstone-backend:v2 -n capstone-app

# Watch the rollout — old pods drain only as new ones pass readiness
kubectl rollout status deployment/backend -n capstone-app
```

**Expected output:**
```
Waiting for deployment "backend" rollout to finish: 1 out of 2 new replicas have been updated...
deployment "backend" successfully rolled out
```

Simulate a bad release and roll back:
```bash
kubectl set image deployment/backend backend=yourdockerhubuser/capstone-backend:v3-broken -n capstone-app
kubectl rollout status deployment/backend -n capstone-app   # observe it stall/fail readiness

kubectl rollout undo deployment/backend -n capstone-app     # instant rollback to v2
kubectl rollout history deployment/backend -n capstone-app  # confirm revision history
```

**Troubleshooting rollout:**
- Rollout "stuck" at `1 out of 2 updated` → new pod is failing readiness; `kubectl describe pod` on the new replica to see why (this is the readiness probe doing its job — protecting live traffic from a bad build).
- `rollout undo` did nothing → check `revisionHistoryLimit` wasn't set to `0` and that you didn't `kubectl apply` the same broken manifest again, which creates a new revision instead of reverting.

## 5. Common Mistakes

1. **Deploying frontend before backend/DB are ready.** The frontend pod will start fine (it doesn't depend on anything at *pod* level), but users hitting `/api` will get connection errors or 502s until the backend and DB stabilize. Always apply and verify bottom-up: DB → backend → frontend → Ingress, exactly as this lab did.
2. **Missing or misconfigured readiness probes.** Without a readiness probe (or with a shallow one that just checks "process alive"), the Service sends traffic to pods before they can actually serve requests — e.g., before the backend has established its DB connection pool. This causes a wave of errors on every scale-up and every rolling update.
3. **Hardcoding the DB host/IP instead of the Service DNS name.** Pod IPs are ephemeral — they change on every reschedule. Always point `DB_HOST` at the Service name (`db-service`), which is stable for the Service's lifetime and resolves via cluster DNS.
4. **Inconsistent namespaces across manifests.** Forgetting `namespace: capstone-app` on even one manifest (or applying it while your `kubectl` context points at `default`) silently creates orphaned resources that can't find each other — Services can't select pods in a different namespace by default, leading to "it works when I test manually but not through the Service" confusion.
5. **No resource requests/limits on the database.** An un-bounded Postgres pod can be evicted under node pressure or starve the backend pods on the same node, causing cascading failures that look like "random" backend timeouts.
6. **Treating the Ingress path rewrite as an afterthought.** Getting the regex/rewrite-target wrong is the single most time-consuming bug in multi-service Ingress setups — always test each backend Service directly (via `kubectl port-forward` or an internal curl pod) before blaming the Ingress.

## 6. Interview Questions (with brief answers)

1. **Walk me through how you'd deploy a 3-tier app on Kubernetes from scratch.** — Namespace first for isolation; ConfigMap/Secret for config and credentials; database Deployment+PVC+Service and verify it's ready; backend Deployment+Service with liveness/readiness probes wired to real DB health, verified independently via a debug pod; frontend Deployment+Service; then an Ingress tying `/` and `/api` to the right Services; finally an HPA on the stateless tier that's likely to be the bottleneck. Verify each layer before adding the next.
2. **Why does the backend need to talk to the database via a Service name instead of a Pod IP?** — Pod IPs change every time a pod is rescheduled (crash, node drain, rolling update); a Service provides a stable virtual IP and DNS name that's resolved via `kube-dns`/CoreDNS and load-balanced across whichever pods currently match its selector.
3. **Your rolling update to the backend is causing a brief spike in 502 errors from the Ingress. What's likely misconfigured, and how do you fix it?** — Most likely missing/weak readiness probes, so the Service routes traffic to new pods before they can actually serve requests, or `maxUnavailable`/`maxSurge` on the Deployment strategy is too aggressive. Fix: add a readiness probe that checks real dependency health (e.g. DB connectivity) and tune the rolling update strategy to keep more old pods available during the transition.
4. **How would you securely provide the database password to the backend without putting it in the container image or a ConfigMap?** — Store it in a Kubernetes Secret, inject via `envFrom`/`secretRef` or as a mounted file, restrict access with RBAC (only the backend's ServiceAccount and admins can read it), and in a real production system back it with a secrets manager (Vault, AWS Secrets Manager) via something like External Secrets Operator rather than raw `kubectl create secret`.
5. **How would you scale this app to handle 10x traffic, and what would you NOT scale the same way?** — Scale the stateless frontend and backend horizontally via HPA (cheap, fast, safe) and consider a CDN in front of the frontend. Do NOT horizontally scale the single Postgres Deployment the same way — that requires either vertical scaling, read replicas, or a managed database service, since naively running multiple Postgres pods against the same PVC will corrupt data (`ReadWriteOnce` volumes also physically prevent this).

## 7. Quiz (50 Questions)

**True/False:**
1. A Service should always be created before the Deployment that depends on it, or DNS resolution may briefly fail on startup. (T, best practice — though Kubernetes DNS will eventually resolve once both exist)
2. The database tier in this lab is exposed externally via a LoadBalancer Service. (F — ClusterIP, internal only)
3. Readiness probes control whether a pod receives traffic from a Service. (T)
4. Liveness probes control whether a pod receives traffic from a Service. (F — liveness controls restarts)
5. A PVC ensures data survives a Pod restart. (T)
6. Hardcoding a database Pod's IP address in the backend's config is a best practice. (F)
7. ConfigMaps should be used to store database passwords. (F — use Secrets)
8. An Ingress can route different URL paths to different backend Services. (T)
9. HPA can scale a Deployment based on CPU utilization. (T)
10. `kubectl rollout undo` redeploys the exact previous ReplicaSet's Pod template. (T)
11. Namespace mismatches between a Service and its target Pods will still allow traffic to route correctly. (F)
12. Resource `limits` prevent a container from exceeding a set amount of CPU/memory. (T)
13. A Deployment's rolling update strategy can be tuned with `maxSurge` and `maxUnavailable`. (T)
14. Secrets in Kubernetes are encrypted at rest by default in every cluster with no extra configuration. (F — depends on cluster config/EncryptionConfiguration)
15. It's safe to scale a single-Postgres-pod-with-PVC setup horizontally to 3 replicas for more throughput. (F)

**Multiple Choice:**
16. Which resource type gives a stable DNS name to a set of Pods? a) ConfigMap b) Service c) Ingress d) PVC → (b)
17. In this lab's Ingress, `/api` traffic is routed to: a) frontend-service b) db-service c) backend-service d) postgres → (c)
18. What does a readiness probe failure do to a running Pod? a) Restarts it b) Deletes it c) Removes it from Service endpoints until it passes d) Nothing → (c)
19. What does a liveness probe failure do? a) Removes from Service endpoints only b) Restarts the container c) Scales down the Deployment d) Deletes the PVC → (b)
20. Which object should hold the database password? a) ConfigMap b) Secret c) Ingress annotation d) Pod label → (b)
21. What triggers HPA to add more backend replicas in this lab? a) Memory usage b) Number of Ingress rules c) CPU utilization crossing 60% d) PVC size → (c)
22. Which command instantly reverts a bad rolling update? a) kubectl delete deployment b) kubectl rollout undo c) kubectl scale --replicas=0 d) kubectl edit configmap → (b)
23. What access mode did we use for the Postgres PVC? a) ReadWriteMany b) ReadOnlyMany c) ReadWriteOnce d) ReadWriteOncePod → (c)
24. Why must the database Service NOT be of type LoadBalancer/NodePort in this lab? a) It costs more b) It would expose the DB directly to the internet, a security risk c) LoadBalancer doesn't support TCP d) It's a Kubernetes technical limitation → (b)
25. What is the correct order to apply manifests for this capstone? a) Ingress → Frontend → Backend → DB b) DB → Backend → Frontend → Ingress c) Frontend → DB → Ingress → Backend d) Order doesn't matter → (b)
26. Which field ties a Service to the correct set of Pods? a) metadata.name b) spec.selector matching Pod labels c) spec.replicas d) spec.template → (b)
27. What does `envFrom: configMapRef` do? a) Mounts a ConfigMap as a volume b) Injects all ConfigMap keys as environment variables c) Creates a new ConfigMap d) Deletes the ConfigMap → (b)
28. Which probe type is most appropriate for checking "can this backend Pod reach the database"? a) livenessProbe only b) readinessProbe (ideally checking real DB connectivity) c) startupProbe only d) None needed → (b)
29. What happens if you forget to specify `namespace:` on a manifest while your context is set to `default`? a) It fails validation b) It's created in `default`, orphaned from the rest of the app c) It's automatically moved to the right namespace d) Kubernetes rejects it → (b)
30. In the Ingress path rewrite used here, what does the regex capture group `(.*)` combined with `rewrite-target: /$2` achieve? a) Nothing, it's optional b) Strips the matched path prefix before forwarding to the backend c) Adds a prefix to all requests d) Blocks all traffic → (b)

**Short Answer:**
31. Why is the database deployed and verified before the backend in this lab's dependency order?
32. What's the difference between a ConfigMap and a Secret, and why does the DB password belong in the latter?
33. Why does the backend's `DB_HOST` value point to a Service name rather than a Pod name or IP?
34. What would happen to end users during a rolling update if the backend had no readiness probe at all?
35. Why is the database Service typically the only Service in this stack that should never be `type: LoadBalancer`?
36. What's the purpose of setting both `requests` and `limits` on every container in this lab?
37. Why does HPA target the backend Deployment specifically, and not the database Deployment?
38. What does `kubectl rollout status` block on, and why is that useful in CI/CD pipelines?
39. What real risk does hardcoding a plaintext password in a ConfigMap introduce that a Secret mitigates (even though Secrets are only base64-encoded, not encrypted, by default)?
40. Why should you test each Service independently (e.g., via a debug curl Pod) before troubleshooting the Ingress layer?

**Scenario-Based:**
41. Your Ingress returns a 404 for `/api/health` but `curl` directly to `backend-service` from inside the cluster works fine. Where do you look first?
42. After a rolling update, `kubectl get pods` shows the new backend ReplicaSet stuck at "1/2 Ready" indefinitely. What are the top two likely causes?
43. A teammate wants to add a 4th "search" microservice to this stack. What existing patterns (Service, ConfigMap, probes, Ingress path) should they reuse, and what's new?
44. Your HPA never scales beyond 2 replicas even under heavy load. What two things would you check first?
45. Someone accidentally deleted the `db-pvc` PersistentVolumeClaim. What happens to your data, and how could better practices have prevented data loss?
46. Your team wants to deploy this app to a `staging` namespace identical to `capstone-app`. What's the fastest correct way to do that without manually rewriting every YAML file's namespace field?
47. A security review flags that the `db-secret` Secret is readable by every ServiceAccount in the namespace. What Kubernetes feature (from earlier hours) fixes this?
48. Your frontend loads but every API call from the browser fails with CORS errors, even though `curl http://capstone.local/api/health` works. Is this a Kubernetes-layer problem or an application-layer problem, and how do you tell?
49. Load testing shows the database becomes the bottleneck before the backend does. What are your options, given that you can't just add PVC replicas?
50. You need zero-downtime even during a full cluster node drain (not just a rolling update). What Deployment/Pod settings from earlier hours (Hour 4, Hour 8-ish topics) become relevant here?

**Fill in the Blank:**
51. The Kubernetes object that gives Postgres durable disk storage across Pod restarts is called a ______.
52. The field that determines which Pods a Service routes traffic to is `spec.______`.
53. The probe that removes a Pod from a Service's endpoint list without restarting it is the ______ probe.
54. The command used to instantly revert a Deployment to its previous ReplicaSet is `kubectl ______ ______`.
55. The Ingress annotation used in this lab to strip path prefixes before forwarding is `nginx.ingress.kubernetes.io/______`.

*(Note: items 51-55 extend the standard "Fill in the Blank" section for this capstone's integration focus.)*

**Command Practice:**
56. Write the command to apply all manifests in order for the database tier only (PVC, Deployment, Service).
57. Write the command to check the rollout status of the `backend` Deployment in the `capstone-app` namespace.
58. Write the command to watch the HPA live while a load test runs.
59. Write the command to view the last 5 revisions of the `backend` Deployment's rollout history.
60. Write the command to exec into a running Postgres Pod and check `pg_isready`.

*(Note: numbered continuously from the Command Practice style of Hour 1; treat 56-60 as this section's set.)*

**Reflection:**
61. Which single manifest in this capstone took you the longest to get right, and why?
62. If you had to explain this whole architecture to a non-technical stakeholder in 3 sentences, what would you say?
63. What would you change about this design before actually running it in production (think: HA Postgres, TLS on Ingress, secrets management)?
64. Which earlier hour's concept (1-17) do you now understand better because you saw it "in context" here?
65. What questions do you still have about tying multiple services together before we move to the next hour?

## 8. Hour 18 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Dependency order** | Deploy and verify bottom-up: Namespace → Config/Secret → DB (+PVC) → Backend → Frontend → Ingress → HPA |
| **Config vs Secret** | Non-sensitive settings (DB_HOST, LOG_LEVEL) → ConfigMap; credentials (DB password) → Secret |
| **Service DNS, not IPs** | Every tier talks to the next via a stable Service name (`db-service`, `backend-service`), never a Pod IP |
| **Probes gate traffic** | Readiness keeps unready Pods out of rotation; liveness restarts stuck containers; both are essential for safe rolling updates |
| **Resource requests/limits** | Set on every container so the scheduler places Pods well and no single Pod starves its node |
| **Ingress path routing** | One entry point, `/` → frontend, `/api` → backend, via path-based rules and rewrite-target |
| **HPA placement** | Autoscale the stateless bottleneck tier (backend), not the stateful tier (database) |
| **Rolling update + rollback** | `kubectl set image` for updates, `kubectl rollout status` to watch, `kubectl rollout undo` for instant recovery |
| **Namespace consistency** | Every manifest explicitly namespaced — the #1 source of "resources can't find each other" bugs |
| **Lab outcome** | A working 3-tier app reachable via Ingress, surviving pod crashes, load spikes, and bad deploys |

**Mnemonic:** *"CRISP-DNH"* — **C**onfig split from **S**ecrets, **R**eadiness gates traffic, **I**ngress routes paths, **S**cale the stateless tier, **P**robes protect rollouts, **D**NS not IPs, **N**amespace everywhere, **H**PA on the bottleneck.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Example: Deploying WordPress and MySQL with PVs](https://kubernetes.io/docs/tutorials/stateful-application/mysql-wordpress-persistent-volume/) — the canonical multi-tier example
- [Ingress-NGINX Official Docs — Rewrite examples](https://kubernetes.github.io/ingress-nginx/examples/rewrite/)
- [Kubernetes Patterns (Bilgin Ibryam, free O'Reilly chapters online)](https://k8spatterns.io/)
- [KodeKloud — Kubernetes for the Absolute Beginners](https://kodekloud.com/courses/kubernetes-for-the-absolute-beginners-hands-on) (revisit Services/Ingress modules with fresh eyes)
- [12 Factor App](https://12factor.net/) — the philosophy behind config/secret separation and stateless services you applied today

**Mini-Project for Hour 18 (60-90 min):**
Extend the capstone with a **4th component**: a Redis caching layer sitting between the backend and Postgres.
1. Deploy Redis via its own Deployment + Service (`redis-service`), no PVC needed for a simple cache.
2. Update the backend's `/api/health` (or a new `/api/data`) endpoint to check Redis first, falling back to Postgres on a cache miss, and populate Redis on read.
3. Add `REDIS_HOST: redis-service` to the existing `backend-config` ConfigMap — no new Secret needed since Redis has no auth in this exercise.
4. Re-run the rolling update drill (Section 4.10) and confirm readiness probes still gate traffic correctly with the new dependency in play.
5. **Stretch goal:** package the entire 4-component app (frontend, backend, db, redis) as a single Helm chart using what you learned in Hour 17, with `values.yaml` toggles for replica counts and image tags per environment.
