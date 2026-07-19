# 🚢 DAY 10 — Production Capstone: Building, Operating & Mastering a Full 3-Tier Application on Kubernetes

> **The Finale.** Everything from Days 1–9 — Pods, Deployments, Services, ConfigMaps, Secrets, Volumes, Probes, Resources, Ingress, HPA, Namespaces — converges here into ONE real, production-style application. You will build it, validate it, operate it, critique it, and then prove your mastery against 80+ interview/exam questions.

---

## 1. ## LEARNING OBJECTIVES (synthesize everything)

By the end of Day 10 you will be able to:

1. **Architect** a complete 3-tier application (frontend → backend → database) and justify every Kubernetes object you choose.
2. **Compose** production-grade YAML for every primitive: `Namespace`, `ConfigMap`, `Secret`, `PersistentVolumeClaim`, `StatefulSet`, `Deployment`, `Service` (ClusterIP + Headless), `Ingress`, and `HorizontalPodAutoscaler`.
3. **Wire dependencies correctly** — env-from-ConfigMap, env-from-Secret, volume mounts, service discovery via DNS, and ordered rollout.
4. **Apply** the three probe types (liveness / readiness / startup) with correct semantics, and set **requests/limits** that make scheduling and autoscaling work.
5. **Expose** the app to the outside world with **Ingress** (host + path routing + TLS).
6. **Autoscale** stateless tiers with **HPA v2** on CPU and memory.
7. **Operate** the running system: rolling update, rollback, scale, and incident response.
8. **Monitor** using the 4 golden signals, `metrics-server`, and Prometheus/Grafana.
9. **Critique** the design honestly — know what is production-ready and what you'd add (PDB, NetworkPolicy, RBAC, backups, HA database).
10. **Pass** a CKA/CKAD-style mixed assessment and answer the 50 most important interview questions across all 10 days.

**Synthesis map — which day each capstone piece comes from:**

| Capstone element | Comes from |
|---|---|
| Pods, labels, selectors | Day 1–2 |
| Deployments, ReplicaSets, rollouts | Day 3 |
| Services (ClusterIP/Headless/NodePort) | Day 4 |
| ConfigMaps & Secrets | Day 5 |
| Volumes, PV/PVC, StorageClass | Day 6 |
| Probes & resource management | Day 7 |
| Ingress & TLS | Day 8 |
| HPA & autoscaling, Namespaces, RBAC intro | Day 9 |
| **Putting it ALL together + ops + monitoring** | **Day 10 (today)** |

---

## 2. ## ARCHITECTURE OVERVIEW

We are building **"Shopfront"** — a classic 3-tier web app:

- **Frontend**: nginx serving a static React build, reverse-proxying `/api` to the backend.
- **Backend**: a Node.js/Express REST API that reads/writes PostgreSQL.
- **Database**: PostgreSQL with durable storage.

```
                                   INTERNET
                                       │
                                       │  https://shopfront.example.com
                                       ▼
                          ┌─────────────────────────┐
                          │   Ingress Controller     │  (nginx-ingress, runs in
                          │   (LoadBalancer Svc)     │   ingress-nginx namespace)
                          └────────────┬────────────┘
                                       │  Ingress object: TLS termination
                                       │  host + path routing
                          ┌────────────┴────────────────────────────┐
                          │  /        → frontend-svc:80              │
                          │  /api(/|$)→ backend-svc:8080             │
                          └────────────┬───────────────┬────────────┘
                                       │               │
                  NAMESPACE: shopfront │               │
   ┌───────────────────────────────────────────────────────────────────────────┐
   │                                   │               │                         │
   │   ┌───────────────────────────┐  │   ┌───────────────────────────┐         │
   │   │ Service: frontend-svc      │◄─┘   │ Service: backend-svc       │◄────────┤
   │   │ type: ClusterIP  :80       │      │ type: ClusterIP  :8080     │         │
   │   └────────────┬──────────────┘      └────────────┬──────────────┘         │
   │                │ selector app=frontend             │ selector app=backend   │
   │                ▼                                    ▼                        │
   │   ┌───────────────────────────┐      ┌───────────────────────────┐         │
   │   │ Deployment: frontend       │      │ Deployment: backend        │         │
   │   │  replicas: 2 (HPA 2–6)     │      │  replicas: 3 (HPA 3–10)    │         │
   │   │  image: nginx:1.27         │      │  image: shopfront-api:1.0  │         │
   │   │  probes: liveness/ready    │      │  probes: live/ready/start  │         │
   │   │  req: 100m/128Mi           │      │  req: 250m/256Mi           │         │
   │   │  limit:300m/256Mi          │      │  limit:500m/512Mi          │         │
   │   │  cfg: frontend-config (CM) │      │  env ← backend-config (CM) │         │
   │   └───────────────────────────┘      │  env ← db-credentials (Secret)       │
   │           ▲                           └────────────┬──────────────┘         │
   │           │ HPA(frontend)                          │  HPA(backend)          │
   │   ┌───────┴────────┐                  ┌────────────┴───────┐                │
   │   │ HPA: frontend  │                  │ HPA: backend       │                │
   │   │ cpu>60%, 2–6   │                  │ cpu>70% mem>75%    │                │
   │   └────────────────┘                  │ 3–10               │                │
   │                                       └────────────────────┘                │
   │                                                    │                         │
   │                                                    │ DNS: postgres-svc       │
   │                                                    ▼                         │
   │                          ┌───────────────────────────────────────┐          │
   │                          │ Service: postgres-svc (HEADLESS)       │          │
   │                          │ clusterIP: None  :5432                 │          │
   │                          └────────────────────┬──────────────────┘          │
   │                                               │ selector app=postgres        │
   │                                               ▼                              │
   │                          ┌───────────────────────────────────────┐          │
   │                          │ StatefulSet: postgres (replicas: 1)    │          │
   │                          │  image: postgres:16                    │          │
   │                          │  probe: readiness (pg_isready)         │          │
   │                          │  env ← db-credentials (Secret)         │          │
   │                          │  volumeClaimTemplate → PVC (10Gi)      │──────┐   │
   │                          └───────────────────────────────────────┘      │   │
   │                                                                          ▼   │
   │                                                       ┌──────────────────────┐
   │                                                       │ PersistentVolume      │
   │                                                       │ (dynamically provisioned)
   │                                                       │ standard StorageClass │
   │                                                       └──────────────────────┘
   │                                                                              │
   │   CONFIG/SECRET OBJECTS (mounted/env-injected as shown above):              │
   │   • ConfigMap: frontend-config   • ConfigMap: backend-config                │
   │   • Secret:    db-credentials                                               │
   └───────────────────────────────────────────────────────────────────────────┘
```

**Data flow:** Browser → Ingress (TLS termination) → frontend nginx (`/`) OR backend API (`/api`) → backend resolves `postgres-svc` via in-cluster DNS → PostgreSQL writes to its PVC-backed volume.

---

## 3. ## DESIGN DECISIONS (rationale for every choice)

| Decision | Choice | Rationale |
|---|---|---|
| **Isolation** | Dedicated `shopfront` Namespace | Scopes names, RBAC, quotas, NetworkPolicies. Lets us delete the whole app with one `kubectl delete ns shopfront`. Avoids collisions with other teams in a shared cluster. |
| **Frontend exposure** | ClusterIP + Ingress (not NodePort/LB per service) | A single Ingress + one LoadBalancer (the controller) is far cheaper than a cloud LB per service. Ingress gives host/path routing and centralized TLS. |
| **Service type for tiers** | `ClusterIP` for frontend & backend | These should only be reachable inside the cluster; the Ingress is the only public door. ClusterIP is the default, most secure choice. |
| **DB Service** | **Headless** (`clusterIP: None`) | StatefulSets need stable network identity. A headless service gives each pod a stable DNS name (`postgres-0.postgres-svc.shopfront.svc.cluster.local`). The backend connects via the service name; clients get the pod IP directly. |
| **DB workload** | **StatefulSet** (not Deployment) | Databases need: stable pod identity, stable persistent storage tied to the pod (`volumeClaimTemplates`), and ordered, graceful scaling. A Deployment would give random pod names and shared/ambiguous storage semantics. **Note:** for true HA you'd run a replicated Postgres (operator); single-replica StatefulSet here is the *correct primitive* even at replicas=1. |
| **DB storage** | `volumeClaimTemplates` → dynamically provisioned PVC | Data must survive pod restarts/reschedules. The StatefulSet's template auto-creates a per-pod PVC bound to a PV via the StorageClass. `Retain`/`Delete` reclaim policy matters for safety. |
| **Probes — backend** | startup + readiness + liveness | **Startup** protects a slow-booting app from being killed early. **Readiness** removes a pod from the Service when it can't serve (e.g., DB down) without killing it. **Liveness** restarts a wedged process. All three have distinct jobs — using only liveness is a classic anti-pattern. |
| **Probes — frontend** | liveness + readiness | nginx boots fast; no startup probe needed. |
| **Probes — DB** | readiness via `pg_isready`; gentle liveness | A liveness probe that's too aggressive can restart a busy DB and cause an outage. Readiness is what matters so the backend doesn't route to a not-yet-ready DB. |
| **Requests vs Limits** | Requests = guaranteed/scheduling baseline; Limits = ceiling | Requests drive the scheduler and HPA math. Limits cap noisy neighbors. We set CPU limit ≥ request and **avoid** overly tight memory limits that cause OOMKills. We do **not** set a CPU limit equal to request unless we want Guaranteed QoS. |
| **QoS classes** | Backend/frontend: Burstable; DB: near-Guaranteed | Burstable lets stateless pods use spare CPU. DB gets tight request≈limit for predictable performance and higher eviction priority. |
| **Secret management** | `Secret` object for DB creds, injected as env | Keeps credentials out of images and ConfigMaps. **Caveat:** base Secrets are only base64-encoded, not encrypted at rest by default — in real prod use sealed-secrets / external-secrets / KMS encryption. |
| **Config** | `ConfigMap` for non-sensitive config | Decouples config from image; change config without rebuilding. |
| **HPA** | v2 on CPU (+ memory for backend), min/max bounded | Scales stateless tiers to load. **Never HPA a StatefulSet DB** — scaling a primary DB horizontally needs replication logic the HPA cannot provide. |
| **Rollout strategy** | RollingUpdate, `maxUnavailable: 0`, `maxSurge: 1` | Zero-downtime: bring up a new pod before removing an old one. |
| **Image tags** | Pinned (`postgres:16`, `nginx:1.27`) not `latest` | Reproducible deploys, predictable rollbacks. |

---

## 4. ## THE FULL CAPSTONE BUILD

> Apply in order. Each `kubectl apply` is idempotent. Keep all files in a folder `shopfront/` and you can re-apply the whole directory with `kubectl apply -f shopfront/`.

**Prerequisites check:**

```bash
kubectl version --short
kubectl get nodes
kubectl get pods -n kube-system | grep metrics-server   # required for HPA
kubectl get pods -n ingress-nginx                        # ingress controller installed?
```

Expected (example):
```
Server Version: v1.30.2
NAME       STATUS   ROLES           AGE   VERSION
node-1     Ready    control-plane   40d   v1.30.2
metrics-server-7d... 1/1 Running
ingress-nginx-controller-... 1/1 Running
```

If `metrics-server` is missing (kind/minikube):
```bash
# minikube
minikube addons enable metrics-server
minikube addons enable ingress
# kind / generic
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

---

### 4a. Namespace

`00-namespace.yaml`:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: shopfront
  labels:
    app.kubernetes.io/part-of: shopfront
    environment: production
---
# Optional but recommended: a ResourceQuota to bound the namespace
apiVersion: v1
kind: ResourceQuota
metadata:
  name: shopfront-quota
  namespace: shopfront
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
    limits.cpu: "8"
    limits.memory: 16Gi
    persistentvolumeclaims: "5"
---
# LimitRange: sane defaults so pods without explicit requests still get some
apiVersion: v1
kind: LimitRange
metadata:
  name: shopfront-defaults
  namespace: shopfront
spec:
  limits:
    - default:
        cpu: 300m
        memory: 256Mi
      defaultRequest:
        cpu: 100m
        memory: 128Mi
      type: Container
```

Apply & validate:
```bash
kubectl apply -f 00-namespace.yaml
kubectl get ns shopfront
kubectl config set-context --current --namespace=shopfront   # convenience
```
Expected:
```
namespace/shopfront created
resourcequota/shopfront-quota created
limitrange/shopfront-defaults created
NAME        STATUS   AGE
shopfront   Active   3s
```

---

### 4b. ConfigMaps & Secrets

`01-config.yaml`:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-config
  namespace: shopfront
data:
  # Non-sensitive backend config. DB host = headless service name.
  DB_HOST: "postgres-svc"
  DB_PORT: "5432"
  DB_NAME: "shopdb"
  APP_PORT: "8080"
  LOG_LEVEL: "info"
  NODE_ENV: "production"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: frontend-config
  namespace: shopfront
data:
  # nginx config: serve static, proxy /api to backend service
  default.conf: |
    server {
      listen 80;
      server_name _;
      root /usr/share/nginx/html;
      index index.html;

      location / {
        try_files $uri $uri/ /index.html;
      }

      location /api/ {
        proxy_pass http://backend-svc:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
      }

      # readiness/liveness target
      location /healthz {
        return 200 'ok';
        add_header Content-Type text/plain;
      }
    }
```

`02-secret.yaml`:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
  namespace: shopfront
type: Opaque
stringData:                 # stringData lets us write plaintext; k8s base64-encodes it
  POSTGRES_USER: "shopuser"
  POSTGRES_PASSWORD: "S3cureP@ss-change-me"
  DB_USER: "shopuser"        # consumed by backend
  DB_PASSWORD: "S3cureP@ss-change-me"
```

> **Best practice:** Generate secrets imperatively so plaintext isn't in git, or use a sealed-secrets controller:
> ```bash
> kubectl create secret generic db-credentials -n shopfront \
>   --from-literal=POSTGRES_USER=shopuser \
>   --from-literal=POSTGRES_PASSWORD='S3cureP@ss-change-me' \
>   --from-literal=DB_USER=shopuser \
>   --from-literal=DB_PASSWORD='S3cureP@ss-change-me' \
>   --dry-run=client -o yaml | kubectl apply -f -
> ```

Apply & validate:
```bash
kubectl apply -f 01-config.yaml -f 02-secret.yaml
kubectl get configmap,secret -n shopfront
kubectl get secret db-credentials -n shopfront -o jsonpath='{.data.DB_USER}' | base64 -d
```
Expected:
```
configmap/backend-config created
configmap/frontend-config created
secret/db-credentials created
NAME                        DATA   AGE
configmap/backend-config    6      4s
configmap/frontend-config   1      4s
NAME                 TYPE     DATA   AGE
secret/db-credentials Opaque   4      4s
shopuser
```

---

### 4c. Database — Headless Service + StatefulSet (with PVC via volumeClaimTemplates)

`03-postgres.yaml`:
```yaml
# Headless service: stable DNS identity for the StatefulSet
apiVersion: v1
kind: Service
metadata:
  name: postgres-svc
  namespace: shopfront
  labels:
    app: postgres
spec:
  clusterIP: None            # <-- HEADLESS
  selector:
    app: postgres
  ports:
    - name: postgres
      port: 5432
      targetPort: 5432
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: shopfront
spec:
  serviceName: postgres-svc   # must match the headless service
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      terminationGracePeriodSeconds: 30
      securityContext:
        fsGroup: 999          # postgres user gid, so it can write the volume
      containers:
        - name: postgres
          image: postgres:16
          ports:
            - containerPort: 5432
              name: postgres
          envFrom:
            - secretRef:
                name: db-credentials
          env:
            - name: POSTGRES_DB
              value: shopdb
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata   # subdir avoids lost+found issues
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"     # request≈limit → near-Guaranteed QoS
          readinessProbe:
            exec:
              command: ["sh", "-c", "pg_isready -U $POSTGRES_USER -d shopdb"]
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 6
          livenessProbe:
            exec:
              command: ["sh", "-c", "pg_isready -U $POSTGRES_USER -d shopdb"]
            initialDelaySeconds: 30
            periodSeconds: 15        # gentle — don't restart a busy DB
            timeoutSeconds: 5
            failureThreshold: 6
          volumeMounts:
            - name: pgdata
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: pgdata
      spec:
        accessModes: ["ReadWriteOnce"]
        # storageClassName: standard   # uncomment to pin a class
        resources:
          requests:
            storage: 10Gi
```

Apply & validate:
```bash
kubectl apply -f 03-postgres.yaml
kubectl rollout status statefulset/postgres -n shopfront --timeout=180s
kubectl get pvc -n shopfront
kubectl get pods -n shopfront -l app=postgres -o wide
```
Expected:
```
service/postgres-svc created
statefulset.apps/postgres created
statefulset rolling update complete 1 pods...
NAME              STATUS   VOLUME        CAPACITY   ACCESS MODES   STORAGECLASS   AGE
pgdata-postgres-0 Bound    pvc-9f3a...   10Gi       RWO            standard       30s
NAME         READY   STATUS    RESTARTS   AGE   IP           NODE
postgres-0   1/1     Running   0          45s   10.244.1.12  node-1
```

Verify DB is actually serving:
```bash
kubectl exec -n shopfront postgres-0 -- pg_isready -U shopuser -d shopdb
# expect: /var/run/postgresql:5432 - accepting connections
kubectl exec -n shopfront postgres-0 -- psql -U shopuser -d shopdb -c '\l'
```

---

### 4d. Backend API — Deployment (probes, resources, env from ConfigMap + Secret) + Service

> The image `shopfront-api:1.0` is a placeholder for your Node/Express app exposing `/api/health`, `/api/ready`, and `/api/products`. Substitute any image that reads `DB_*` env vars and listens on `APP_PORT`.

`04-backend.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: shopfront
  labels:
    app: backend
spec:
  replicas: 3
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
        app.kubernetes.io/part-of: shopfront
    spec:
      containers:
        - name: backend
          image: shopfront-api:1.0
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
              name: http
          envFrom:
            - configMapRef:
                name: backend-config
            - secretRef:
                name: db-credentials
          resources:
            requests:
              cpu: "250m"
              memory: "256Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"
          startupProbe:                # protects slow boot (e.g. DB migrations)
            httpGet:
              path: /api/health
              port: 8080
            failureThreshold: 30
            periodSeconds: 5           # up to 150s to start before liveness kicks in
          readinessProbe:              # gate traffic; fails if DB unreachable
            httpGet:
              path: /api/ready
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          livenessProbe:               # restart if wedged
            httpGet:
              path: /api/health
              port: 8080
            initialDelaySeconds: 0     # startupProbe already covered boot
            periodSeconds: 15
            timeoutSeconds: 3
            failureThreshold: 3
          securityContext:
            allowPrivilegeEscalation: false
            runAsNonRoot: true
            runAsUser: 1000
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
          volumeMounts:
            - name: tmp
              mountPath: /tmp
      volumes:
        - name: tmp
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: backend-svc
  namespace: shopfront
  labels:
    app: backend
spec:
  type: ClusterIP
  selector:
    app: backend
  ports:
    - name: http
      port: 8080
      targetPort: 8080
```

Apply & validate:
```bash
kubectl apply -f 04-backend.yaml
kubectl rollout status deployment/backend -n shopfront
kubectl get deploy,po,svc -n shopfront -l app=backend
kubectl get endpoints backend-svc -n shopfront
```
Expected:
```
deployment.apps/backend created
service/backend-svc created
deployment "backend" successfully rolled out
NAME              READY   UP-TO-DATE   AVAILABLE
deployment/backend 3/3    3            3
NAME            ENDPOINTS                                AGE
backend-svc     10.244.1.20:8080,10.244.2.21:8080,...   20s
```

---

### 4e. Frontend — Deployment + Service (nginx config mounted from ConfigMap)

`05-frontend.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  namespace: shopfront
  labels:
    app: frontend
spec:
  replicas: 2
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
        app.kubernetes.io/part-of: shopfront
    spec:
      containers:
        - name: frontend
          image: nginx:1.27
          ports:
            - containerPort: 80
              name: http
          resources:
            requests:
              cpu: "100m"
              memory: "128Mi"
            limits:
              cpu: "300m"
              memory: "256Mi"
          readinessProbe:
            httpGet:
              path: /healthz
              port: 80
            initialDelaySeconds: 3
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /healthz
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 15
          volumeMounts:
            - name: nginx-conf
              mountPath: /etc/nginx/conf.d/default.conf
              subPath: default.conf
      volumes:
        - name: nginx-conf
          configMap:
            name: frontend-config
            items:
              - key: default.conf
                path: default.conf
---
apiVersion: v1
kind: Service
metadata:
  name: frontend-svc
  namespace: shopfront
  labels:
    app: frontend
spec:
  type: ClusterIP
  selector:
    app: frontend
  ports:
    - name: http
      port: 80
      targetPort: 80
```

Apply & validate:
```bash
kubectl apply -f 05-frontend.yaml
kubectl rollout status deployment/frontend -n shopfront
kubectl get endpoints frontend-svc -n shopfront
```
Expected:
```
deployment.apps/frontend created
service/frontend-svc created
deployment "frontend" successfully rolled out
NAME           ENDPOINTS                       AGE
frontend-svc   10.244.1.30:80,10.244.2.31:80   15s
```

---

### 4f. Ingress — host + path routing + TLS

First create the TLS secret (self-signed for demo; use cert-manager in prod):
```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=shopfront.example.com/O=shopfront"
kubectl create secret tls shopfront-tls -n shopfront \
  --cert=tls.crt --key=tls.key
```

`06-ingress.yaml`:
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: shopfront-ingress
  namespace: shopfront
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/use-regex: "true"
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - shopfront.example.com
      secretName: shopfront-tls
  rules:
    - host: shopfront.example.com
      http:
        paths:
          - path: /api(/|$)(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: backend-svc
                port:
                  number: 8080
          - path: /()(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: frontend-svc
                port:
                  number: 80
```

> **Note:** the frontend nginx already proxies `/api` to the backend, so you could route everything to the frontend. The dual-path Ingress above is shown to demonstrate path-based routing at the Ingress layer — pick one strategy in real life. The simpler production-clean version routes `/` → frontend only and lets nginx handle `/api`.

Apply & validate:
```bash
kubectl apply -f 06-ingress.yaml
kubectl get ingress -n shopfront
kubectl describe ingress shopfront-ingress -n shopfront | sed -n '1,30p'
```
Expected:
```
ingress.networking.k8s.io/shopfront-ingress created
NAME                CLASS   HOSTS                    ADDRESS        PORTS     AGE
shopfront-ingress   nginx   shopfront.example.com    192.168.49.2   80, 443   10s
```

Map the host locally (until DNS exists):
```bash
echo "$(kubectl get ingress shopfront-ingress -n shopfront -o jsonpath='{.status.loadBalancer.ingress[0].ip}')  shopfront.example.com" | sudo tee -a /etc/hosts
```

---

### 4g. HPA — backend (CPU + memory) and frontend (CPU)

`07-hpa.yaml`:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: shopfront
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 75
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300   # avoid flapping
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: frontend-hpa
  namespace: shopfront
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: frontend
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

Apply & validate:
```bash
kubectl apply -f 07-hpa.yaml
kubectl get hpa -n shopfront
```
Expected (after metrics populate, ~60s):
```
horizontalpodautoscaler.autoscaling/backend-hpa created
horizontalpodautoscaler.autoscaling/frontend-hpa created
NAME           REFERENCE             TARGETS                      MINPODS   MAXPODS   REPLICAS
backend-hpa    Deployment/backend    cpu: 12%/70%, mem: 30%/75%   3         10        3
frontend-hpa   Deployment/frontend   cpu: 5%/60%                  2         6         2
```

> If `TARGETS` shows `<unknown>`, metrics-server isn't ready yet — wait or check `kubectl top pods -n shopfront`.

---

## 5. ## VALIDATION & SMOKE TEST (end-to-end)

```bash
# 1. Everything Running / Ready
kubectl get all -n shopfront
```
Expected (abridged):
```
NAME                            READY   STATUS    RESTARTS   AGE
pod/backend-xxxx                1/1     Running   0          3m
pod/backend-yyyy                1/1     Running   0          3m
pod/backend-zzzz                1/1     Running   0          3m
pod/frontend-aaaa               1/1     Running   0          2m
pod/frontend-bbbb               1/1     Running   0          2m
pod/postgres-0                  1/1     Running   0          5m
```

```bash
# 2. DNS resolution between tiers
kubectl run dnsutils --rm -it --restart=Never -n shopfront \
  --image=busybox:1.36 -- nslookup postgres-svc
```
Expected:
```
Name:      postgres-svc.shopfront.svc.cluster.local
Address 1: 10.244.1.12 postgres-0.postgres-svc.shopfront.svc.cluster.local
```

```bash
# 3. Backend → DB connectivity (from inside a backend pod)
POD=$(kubectl get po -n shopfront -l app=backend -o name | head -1)
kubectl exec -n shopfront $POD -- sh -c 'nc -zv $DB_HOST $DB_PORT'
# expect: postgres-svc (10.244.x.x:5432) open

# 4. Frontend → Backend service
kubectl exec -n shopfront $POD -- wget -qO- http://backend-svc:8080/api/health
# expect: {"status":"ok"}  (or your app's health payload)

# 5. Full path through the Ingress + TLS
curl -k https://shopfront.example.com/healthz
curl -k https://shopfront.example.com/api/products
```
Expected:
```
ok
[{"id":1,"name":"Widget"}, ...]
```

```bash
# 6. Probe sanity
kubectl get pods -n shopfront -o wide
kubectl describe pod $POD -n shopfront | grep -A2 -E "Liveness|Readiness|Startup"

# 7. HPA live
kubectl top pods -n shopfront
kubectl get hpa -n shopfront

# 8. Data durability test — delete the DB pod, data must survive
kubectl delete pod postgres-0 -n shopfront
kubectl rollout status statefulset/postgres -n shopfront
kubectl exec -n shopfront postgres-0 -- psql -U shopuser -d shopdb -c '\dt'
# tables still present → PVC survived the pod restart ✓
```

---

## 6. ## OPERATIONS PLAYBOOK

### Rolling update (deploy backend v1.1)
```bash
kubectl set image deployment/backend backend=shopfront-api:1.1 -n shopfront
kubectl rollout status deployment/backend -n shopfront
kubectl rollout history deployment/backend -n shopfront
```
Because `maxUnavailable: 0`, capacity never drops below 3 during the rollout. Readiness gates traffic to new pods.

### Rollback
```bash
kubectl rollout undo deployment/backend -n shopfront                 # previous revision
kubectl rollout undo deployment/backend -n shopfront --to-revision=2 # specific
kubectl rollout status deployment/backend -n shopfront
```

### Manual scale (overrides HPA only momentarily — HPA will re-reconcile)
```bash
kubectl scale deployment/frontend --replicas=4 -n shopfront
# To truly change limits, edit the HPA min/max:
kubectl patch hpa backend-hpa -n shopfront --type merge -p '{"spec":{"maxReplicas":15}}'
```

### Restart a tier (e.g., to pick up new Secret/ConfigMap values)
```bash
kubectl rollout restart deployment/backend -n shopfront
```
> ConfigMaps mounted as volumes update in-place (eventually); env-injected values require a pod restart. That's why we `rollout restart` after changing the Secret.

### Common incident response

| Symptom | Likely cause | Action |
|---|---|---|
| Pod `CrashLoopBackOff` | App error, bad config, failing liveness | `kubectl logs <pod> -p`; `kubectl describe pod`; check env/probe |
| Pod `ImagePullBackOff` | Wrong tag / no pull secret | `kubectl describe pod`; fix image or add `imagePullSecrets` |
| Pod `Pending` | No node fits requests / unbound PVC | `kubectl describe pod` → check Events; lower requests or add capacity/PVC |
| Endpoints empty | Selector mismatch or readiness failing | `kubectl get endpoints svc`; `kubectl describe svc`; check readiness |
| 503 from Ingress | Backend not ready / wrong service port | check `kubectl get endpoints`, Ingress backend port |
| `OOMKilled` (exit 137) | Memory limit too low | raise memory limit, fix leak |
| HPA `<unknown>` targets | metrics-server down / no requests set | fix metrics-server; ensure `resources.requests` present |
| DB data lost on restart | Using `emptyDir` instead of PVC | use the StatefulSet+PVC (as built here) |

Triage one-liners:
```bash
kubectl get events -n shopfront --sort-by=.lastTimestamp | tail -20
kubectl logs deploy/backend -n shopfront --tail=100
kubectl describe pod <pod> -n shopfront
kubectl get pods -n shopfront -o wide
```

---

## 7. ## MONITORING CONSIDERATIONS

**The 4 Golden Signals (SRE):**

| Signal | What to watch | For Shopfront |
|---|---|---|
| **Latency** | request duration, p50/p95/p99 | API `/api/*` latency, DB query time |
| **Traffic** | requests/sec, connections | Ingress RPS, backend QPS |
| **Errors** | 5xx rate, failed probes, restarts | nginx 5xx, backend 500s, CrashLoops |
| **Saturation** | CPU/mem/disk/IO utilization | pod CPU vs request, PVC fill %, HPA pressure |

**Tooling layers:**
- **metrics-server** — required for `kubectl top` and HPA. Resource metrics only (CPU/mem). Not for alerting/history.
- **Prometheus** — scrapes app `/metrics` (expose via prometheus-client), kube-state-metrics, node-exporter; stores time series; powers alerts via Alertmanager.
- **Grafana** — dashboards on top of Prometheus (per-tier CPU/mem, request rate, error rate, PVC usage, HPA replica count).
- **Logging** — ship stdout/stderr to Loki/ELK; never write logs to the pod filesystem (ephemeral).

**Alerts you'd set:**
- Backend 5xx rate > 1% for 5m → page.
- Pod restart count increasing (CrashLoop) → page.
- HPA at `maxReplicas` for >10m → capacity warning.
- PVC usage > 80% → warning; > 90% → page (DB disk full = outage).
- Postgres `pg_isready` failing / not ready > 2m → page.
- Node `MemoryPressure`/`DiskPressure` → warning.
- Certificate expiry < 14 days → warning.

```bash
kubectl top nodes
kubectl top pods -n shopfront
# Expose app metrics, then a Prometheus ServiceMonitor (if using prometheus-operator) scrapes /metrics.
```

---

## 8. ## ARCHITECTURE REVIEW (critique)

**What IS production-ready in this build:**
- Namespace isolation + ResourceQuota + LimitRange.
- Pinned images, RollingUpdate with `maxUnavailable: 0`.
- All three probe types, correctly differentiated.
- Requests *and* limits on every container; sensible QoS.
- Config/secret separation; secrets not baked into images.
- Durable DB storage via StatefulSet + volumeClaimTemplates.
- TLS-terminating Ingress; ClusterIP-only internal services.
- HPA on stateless tiers with anti-flap behavior.
- Hardened backend securityContext (non-root, read-only FS, dropped caps).

**What you MUST add for real production:**

| Gap | Add | Why |
|---|---|---|
| Voluntary-disruption protection | **PodDisruptionBudget** (`minAvailable: 2` for backend) | Node drains/upgrades won't take all replicas down at once |
| Network segmentation | **NetworkPolicy** | Default-deny; only frontend→backend→postgres allowed. Limits blast radius |
| Least privilege | **RBAC + dedicated ServiceAccount** per workload | No workload should use the default SA with broad rights |
| Secret safety | **Sealed-Secrets / External-Secrets / KMS encryption-at-rest** | Base64 ≠ encryption |
| DB HA | **Replicated Postgres** (CloudNativePG/Zalando/Crunchy operator) or managed RDS | Single replica = single point of failure |
| Backups | **Scheduled `pg_dump`/WAL archiving via CronJob + offsite storage** | StatefulSet protects against pod loss, NOT against data corruption/deletion |
| Spread | **topologySpreadConstraints / podAntiAffinity** | Don't put all replicas on one node/zone |
| Certs | **cert-manager + Let's Encrypt** | Automated TLS issuance/rotation |
| Supply chain | **image scanning, signed images, admission policy (Kyverno/OPA)** | Block vulnerable/unsigned images |
| Observability | **Prometheus + Grafana + Loki + tracing (OTel)** | metrics-server alone is insufficient |
| GitOps | **ArgoCD/Flux** | Declarative, auditable deploys instead of `kubectl apply` by hand |

**Example PDB + NetworkPolicy to add:**
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: { name: backend-pdb, namespace: shopfront }
spec:
  minAvailable: 2
  selector: { matchLabels: { app: backend } }
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: postgres-allow-backend, namespace: shopfront }
spec:
  podSelector: { matchLabels: { app: postgres } }
  policyTypes: [Ingress]
  ingress:
    - from:
        - podSelector: { matchLabels: { app: backend } }
      ports:
        - { protocol: TCP, port: 5432 }
```

---

## 9. ## FULL-COURSE INTERVIEW REVIEW (30 key questions, Days 1–10)

1. **What is a Pod, and why not just run containers?** Smallest deployable unit; one or more containers sharing network namespace + volumes. K8s schedules Pods, not containers, enabling sidecars and shared lifecycle.
2. **Deployment vs ReplicaSet vs StatefulSet vs DaemonSet?** Deployment manages stateless ReplicaSets with rollout/rollback; ReplicaSet just maintains replica count; StatefulSet gives stable identity+storage+ordering for stateful apps; DaemonSet runs one pod per node.
3. **How does a Service find its Pods?** Via label selector → EndpointSlice of ready pod IPs; kube-proxy programs iptables/IPVS for the stable ClusterIP.
4. **ClusterIP vs NodePort vs LoadBalancer vs Headless?** Internal-only; node-port-exposed; cloud-LB; no clusterIP (returns pod IPs, used with StatefulSets).
5. **ConfigMap vs Secret?** Both decouple config from image; Secret is for sensitive data, base64-encoded (encrypt at rest separately), can be mounted or env-injected.
6. **emptyDir vs hostPath vs PVC?** Ephemeral pod-scoped; node path (avoid); durable cluster storage abstracted via PV/PVC + StorageClass.
7. **PV vs PVC vs StorageClass?** PV is the actual storage; PVC is a request/claim; StorageClass enables dynamic provisioning.
8. **Liveness vs Readiness vs Startup probe?** Restart-if-failing; remove-from-Service-if-failing; protect-slow-boot. Distinct purposes — don't conflate.
9. **Requests vs Limits and QoS classes?** Requests = scheduling guarantee; Limits = ceiling. Guaranteed (req=limit all), Burstable (some set), BestEffort (none) — affects eviction order.
10. **What causes OOMKilled / exit 137?** Container exceeded memory limit; kernel OOM-killer terminates it.
11. **How does HPA decide replica count?** `desired = ceil(current * currentMetric / targetMetric)`, bounded by min/max, on resource metrics from metrics-server.
12. **Why can't you HPA a StatefulSet database?** Horizontal scaling of a primary DB requires replication/sharding logic HPA can't provide; you'd corrupt or split data.
13. **Ingress vs Service?** Service is L4 cluster networking; Ingress is L7 HTTP routing (host/path/TLS) fronted by an Ingress controller.
14. **What does an Ingress controller actually do?** Watches Ingress objects and configures a real proxy (nginx/HAProxy/Envoy) to route traffic.
15. **How does in-cluster DNS work?** CoreDNS resolves `svc.ns.svc.cluster.local`; pods get the cluster DNS as resolver. Headless services resolve to pod IPs.
16. **RollingUpdate parameters?** `maxUnavailable` (how many can be down) and `maxSurge` (extra pods allowed). `0/1` = zero-downtime.
17. **How do rollbacks work?** Deployment keeps revision history of ReplicaSets; `rollout undo` scales the old RS back up.
18. **Namespaces — what do they isolate?** Names, RBAC scope, quotas, network policies, default service DNS — but NOT nodes or cluster-scoped objects.
19. **What's a Headless service for StatefulSets?** Provides stable per-pod DNS identity needed for ordinal pods.
20. **What's the difference between `kubectl apply` and `create`?** `apply` is declarative/idempotent (merges, tracks last-applied); `create` is imperative and errors if it exists.
21. **How do you inject secrets safely?** Env or volume from a Secret; restrict via RBAC; encrypt at rest; rotate; consider external secret stores.
22. **What is the control plane made of?** API server, etcd, scheduler, controller-manager (+ cloud-controller-manager). Workers run kubelet, kube-proxy, container runtime.
23. **How does the scheduler place a Pod?** Filters nodes (requests, taints, affinity, selectors) then scores them; binds the pod to the best node.
24. **Taints & tolerations vs node affinity?** Taints repel pods unless tolerated (node-side); affinity attracts/requires pods toward nodes (pod-side).
25. **What is a PodDisruptionBudget?** Limits voluntary disruptions (drains) so a minimum stay available.
26. **NetworkPolicy default behavior?** Without any policy, all traffic is allowed; once a pod is selected by a policy, only matching traffic is permitted (default-deny per direction).
27. **How would you do zero-downtime config change?** Update ConfigMap/Secret, then `rollout restart`; readiness gating ensures only ready new pods get traffic.
28. **What metrics does metrics-server provide and not provide?** Live CPU/memory for `top`/HPA; NOT history, custom, or business metrics (that's Prometheus).
29. **How do you debug a Pending pod?** `kubectl describe pod` → Events: insufficient resources, unbound PVC, taints, affinity.
30. **Design a 3-tier app on K8s (this capstone).** Ingress→frontend(ClusterIP/Deploy)→backend(ClusterIP/Deploy, HPA, probes)→DB(headless/StatefulSet+PVC); ConfigMaps/Secrets injected; namespace-scoped; add PDB/NetworkPolicy/RBAC for prod.

---

## 10. ## 🎓 TOP 50 QUESTIONS (all 10 days)

### Fundamentals (15)
1. What is the smallest deployable unit in Kubernetes? → A Pod.
2. What keeps a Deployment's replica count stable? → Its ReplicaSet (managed by the Deployment controller).
3. Which object gives stable network identity + storage to stateful apps? → StatefulSet.
4. What runs exactly one pod per node? → DaemonSet.
5. Default Service type? → ClusterIP.
6. Service type for stable per-pod DNS? → Headless (`clusterIP: None`).
7. Encrypted by default at rest? ConfigMap or Secret? → Neither by default; Secrets are only base64-encoded.
8. Which volume is ephemeral and pod-scoped? → emptyDir.
9. What enables dynamic PV provisioning? → StorageClass.
10. Which probe protects a slow-starting container? → Startup probe.
11. Which probe gates Service traffic? → Readiness probe.
12. What does the scheduler use to place pods? → Resource **requests** (+ affinity/taints).
13. QoS class when request == limit for all resources? → Guaranteed.
14. Default API group for Deployment? → `apps/v1`.
15. What resolves `service.namespace.svc.cluster.local`? → CoreDNS.

### Practical (10)
16. Command to set a new image on a Deployment? → `kubectl set image deploy/backend backend=img:tag -n ns`.
17. Watch a rollout? → `kubectl rollout status deploy/backend -n ns`.
18. Roll back to previous version? → `kubectl rollout undo deploy/backend -n ns`.
19. Create a Secret without plaintext in git? → `kubectl create secret generic ... --dry-run=client -o yaml | kubectl apply -f -`.
20. See pod CPU/memory usage? → `kubectl top pods -n ns`.
21. Scale a Deployment to 5? → `kubectl scale deploy/frontend --replicas=5 -n ns`.
22. Exec into a pod? → `kubectl exec -it <pod> -n ns -- sh`.
23. View previous container logs after a crash? → `kubectl logs <pod> -p -n ns`.
24. Port-forward a service locally? → `kubectl port-forward svc/backend-svc 8080:8080 -n ns`.
25. Apply an entire directory of manifests? → `kubectl apply -f shopfront/`.

### Scenario (10)
26. You need zero-downtime deploys — which strategy/params? → RollingUpdate, `maxUnavailable: 0`, `maxSurge: 1`, with readiness probes.
27. DB must keep data through pod restarts — what do you use? → StatefulSet + volumeClaimTemplates (PVC), not emptyDir.
28. App boots slowly and gets killed — fix? → Add a Startup probe with a high `failureThreshold`.
29. Traffic spikes daily — handle automatically? → HPA v2 on CPU/memory with min/max.
30. Expose two services under one domain by path — what object? → Ingress with path rules.
31. Keep DB reachable only from backend — what? → NetworkPolicy (ingress from `app=backend` on 5432).
32. Ensure cluster upgrades don't take all backend pods down → PodDisruptionBudget `minAvailable`.
33. Rotate a DB password without downtime → update Secret, `rollout restart` backend (readiness gates traffic).
34. Same app, two environments in one cluster → two Namespaces (+ quotas/policies).
35. Sidecar needs to share files with main container → shared `emptyDir` volume mounted in both.

### Troubleshooting (10)
36. Pod `ImagePullBackOff` → wrong tag/registry/credentials; `kubectl describe pod` Events.
37. Pod `CrashLoopBackOff` → app/config error or failing liveness; `kubectl logs -p`, check env & probe.
38. Pod stuck `Pending` → unschedulable: insufficient requests, unbound PVC, taints; `describe` Events.
39. Service has no endpoints → selector/label mismatch or all pods not Ready.
40. Ingress returns 503 → backend has no ready endpoints or wrong service port.
41. `OOMKilled` (137) → memory limit too low or leak; raise limit / fix app.
42. HPA shows `<unknown>` → metrics-server down or no `resources.requests` set.
43. DB pod restarts under load → liveness probe too aggressive; loosen period/threshold.
44. ConfigMap change not picked up → env-injected values need pod restart (`rollout restart`).
45. `Error: exceeded quota` → ResourceQuota hit; lower requests or raise quota.

### Interview (5)
46. Walk me through what happens from `kubectl apply` to a running Pod. → API server validates & stores in etcd → controllers create ReplicaSet/Pods → scheduler binds to a node → kubelet pulls image & starts container → probes gate readiness → Service endpoints update.
47. How do you achieve high availability for a stateful database on K8s? → Replicated Postgres via an operator (or managed DB), multi-replica StatefulSet with anti-affinity across zones, automated backups/WAL archiving, PDB.
48. Design for least privilege across the cluster. → Per-workload ServiceAccounts, scoped RBAC Roles/Bindings, NetworkPolicies default-deny, no privileged containers, read-only root FS, admission policies.
49. How do requests/limits interact with HPA and the scheduler? → Scheduler packs by requests; HPA utilization is measured against requests; limits cap usage and define QoS/eviction order.
50. Explain the full Day-1-to-10 stack you'd deploy for a production web app. → The Shopfront architecture: namespaced 3-tier app, Ingress+TLS, ClusterIP services, HPA'd stateless tiers with full probes & resources, StatefulSet+PVC DB, ConfigMaps/Secrets, plus PDB/NetworkPolicy/RBAC/backups/monitoring/GitOps.

---

## 11. ## FINAL MASTERY ASSESSMENT

**Self-rating rubric (score each 1–5):**

| Day | Topic | Can explain | Can build from scratch | Can debug |
|---|---|---|---|---|
| 1 | Architecture, kubectl, Pods | ☐ | ☐ | ☐ |
| 2 | Pods, labels, namespaces | ☐ | ☐ | ☐ |
| 3 | Deployments, ReplicaSets, rollouts | ☐ | ☐ | ☐ |
| 4 | Services & networking | ☐ | ☐ | ☐ |
| 5 | ConfigMaps & Secrets | ☐ | ☐ | ☐ |
| 6 | Volumes, PV/PVC, StorageClass | ☐ | ☐ | ☐ |
| 7 | Probes & resource management | ☐ | ☐ | ☐ |
| 8 | Ingress & TLS | ☐ | ☐ | ☐ |
| 9 | HPA, namespaces, RBAC | ☐ | ☐ | ☐ |
| 10 | Full capstone + ops + monitoring | ☐ | ☐ | ☐ |

**Readiness checklist — you are ready when you can, without notes:**
- [ ] Stand up the entire Shopfront stack in correct order and explain each step.
- [ ] Diagnose CrashLoop / ImagePull / Pending / empty-endpoints / 503 in under 3 minutes.
- [ ] Choose StatefulSet vs Deployment and justify it.
- [ ] Configure all three probes correctly.
- [ ] Set requests/limits and predict the QoS class.
- [ ] Write an Ingress with host+path+TLS.
- [ ] Write and reason about an HPA.
- [ ] List the prod gaps (PDB, NetworkPolicy, RBAC, backups, HA DB).

**If weak in an area, review:**
- Networking/Services/Ingress (Days 4, 8) → practice DNS + endpoints + `port-forward`.
- Storage (Day 6) → do the PVC durability test repeatedly.
- Probes/resources (Day 7) → break a probe on purpose and watch behavior.
- HPA (Day 9) → load-test with `kubectl run -it load --image=busybox -- /bin/sh -c "while true; do wget -q -O- http://backend-svc:8080/api/health; done"`.

**CKA / CKAD readiness:**
- **CKAD** (developer): you're well-covered — focus on speed, `kubectl` imperative generation (`--dry-run=client -o yaml`), probes, multi-container pods, configmaps/secrets, jobs/cronjobs.
- **CKA** (admin): add cluster ops — `kubeadm` install/upgrade, etcd backup/restore, RBAC depth, node maintenance/drain, troubleshooting kubelet/control plane, NetworkPolicy. Practice on **killer.sh** (free session included with exam).
- Both exams are hands-on and time-boxed: practice `alias k=kubectl`, `export do="--dry-run=client -o yaml"`, and fast context switching.

---

## 12. ## MASTER CHEAT SHEET

```bash
# ---- Setup / context ----
alias k=kubectl
export do="--dry-run=client -o yaml"
k config set-context --current --namespace=shopfront

# ---- Inspect ----
k get all -n shopfront
k get po,svc,deploy,sts,pvc,ing,hpa,cm,secret -n shopfront
k get po -o wide -n shopfront
k describe pod <pod> -n shopfront
k get events -n shopfront --sort-by=.lastTimestamp | tail -20
k top pods -n shopfront ; k top nodes
k get endpoints <svc> -n shopfront

# ---- Imperative generators (great for exams) ----
k create ns demo $do
k create deploy web --image=nginx --replicas=3 $do
k expose deploy web --port=80 --target-port=80 $do
k create cm app-cfg --from-literal=KEY=val $do
k create secret generic db --from-literal=PASSWORD=xyz $do
k create ingress web --rule="host/path*=svc:80" $do
k autoscale deploy web --min=2 --max=6 --cpu-percent=70 $do
k run tmp --image=busybox -it --rm --restart=Never -- sh

# ---- Rollouts ----
k set image deploy/backend backend=shopfront-api:1.1 -n shopfront
k rollout status deploy/backend -n shopfront
k rollout history deploy/backend -n shopfront
k rollout undo deploy/backend -n shopfront [--to-revision=N]
k rollout restart deploy/backend -n shopfront

# ---- Scale ----
k scale deploy/frontend --replicas=4 -n shopfront
k patch hpa backend-hpa -n shopfront --type merge -p '{"spec":{"maxReplicas":15}}'

# ---- Debug ----
k logs <pod> -n shopfront [-p] [--tail=100] [-c container]
k exec -it <pod> -n shopfront -- sh
k port-forward svc/backend-svc 8080:8080 -n shopfront
k get pod <pod> -n shopfront -o yaml
```

**YAML patterns (memorize the shapes):**

```yaml
# Probe trio
startupProbe:   { httpGet: {path: /health, port: 8080}, failureThreshold: 30, periodSeconds: 5 }
readinessProbe: { httpGet: {path: /ready,  port: 8080}, periodSeconds: 10 }
livenessProbe:  { httpGet: {path: /health, port: 8080}, periodSeconds: 15 }

# Resources
resources:
  requests: { cpu: 250m, memory: 256Mi }
  limits:   { cpu: 500m, memory: 512Mi }

# Env from ConfigMap + Secret
envFrom:
  - configMapRef: { name: backend-config }
  - secretRef:    { name: db-credentials }

# StatefulSet storage
volumeClaimTemplates:
  - metadata: { name: data }
    spec: { accessModes: [ReadWriteOnce], resources: { requests: { storage: 10Gi } } }

# Zero-downtime rollout
strategy: { type: RollingUpdate, rollingUpdate: { maxUnavailable: 0, maxSurge: 1 } }

# Headless service
spec: { clusterIP: None, selector: { app: postgres }, ports: [{ port: 5432 }] }
```

**Cleanup the whole capstone:**
```bash
kubectl delete namespace shopfront   # removes everything namespace-scoped
```

---

## 13. ## FREE RESOURCES (capstone-level)

| Resource | Type | What it's best for |
|---|---|---|
| **kubernetes.io/docs** | Docs | The canonical reference — concepts + task guides |
| **killer.sh** | Exam sim | Free CKA/CKAD simulator session with the exam — hardest practice available |
| **kodekloud (free tiers / KillerCoda)** | Labs | Browser-based hands-on scenarios, no setup |
| **killercoda.com** | Interactive labs | Free K8s playgrounds & scenarios |
| **github.com/dgkanatsios/CKAD-exercises** | Practice | Curated CKAD task list with solutions |
| **github.com/walidshaari/Kubernetes-Certified-Administrator** | Practice | CKA prep curriculum |
| **CNCF "Kubernetes the Hard Way" (Kelsey Hightower)** | Repo | Deep understanding of cluster internals |
| **github.com/GoogleCloudPlatform/microservices-demo** | Prod repo | Real multi-service app architecture to study |
| **prometheus.io / grafana.com docs** | Docs | Monitoring stack |
| **"Kubernetes Up & Running" (free chapters), "The Kubernetes Book"** | Books | Conceptual grounding |
| **CNCF Slack / r/kubernetes** | Community | Troubleshooting & patterns |

**Highest ROI (do these first):**
1. **killer.sh + KillerCoda** — hands-on speed is what passes exams and interviews.
2. **CKAD-exercises repo** — drill until imperative `kubectl` is muscle memory.
3. **microservices-demo** — read a real production-grade manifest set end to end.

---

## 14. ## 🎓 CONGRATULATIONS / NEXT STEPS

**You did it.** In 10 days you went from "what is a Pod?" to architecting, building, operating, monitoring, and critiquing a complete production-style 3-tier application on Kubernetes — and you can defend every decision in an interview.

**Where to go next (pick your track):**

- **Certify:** Schedule **CKAD** (you're ready) and then **CKA**. Use your free killer.sh session.
- **GitOps:** Learn **ArgoCD** or **Flux** — stop `kubectl apply`-ing by hand; deploy from git.
- **Packaging:** Learn **Helm** (templated, versioned releases) and **Kustomize** (overlays per environment).
- **Service mesh:** **Istio / Linkerd** — mTLS, traffic shifting, observability between services.
- **Security:** **OPA/Gatekeeper or Kyverno** (policy), **Falco** (runtime), image signing (cosign).
- **Observability:** Build the full **Prometheus + Grafana + Loki + OpenTelemetry** stack on Shopfront.
- **Operators:** Run a **Postgres operator** (CloudNativePG) to turn the single DB into real HA.
- **Platform engineering:** **Crossplane**, internal developer platforms, multi-cluster (Cluster API).
- **Production hardening:** Implement everything from §8 on Shopfront — PDB, NetworkPolicy, RBAC, backups, anti-affinity.

**Your capstone is the perfect portfolio piece.** Put the `shopfront/` manifests in a public repo, add the architecture diagram, document the design decisions, and you have a concrete artifact to show in any Kubernetes interview.

Keep building. Keep breaking things on purpose. That's how mastery sticks. 🚢
