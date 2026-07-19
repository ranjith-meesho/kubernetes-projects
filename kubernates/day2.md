# 🚢 DAY 2 — Organizing & Configuring Your Cluster: Namespaces, Labels, Selectors, ConfigMaps & Secrets

---

## 1. 🎯 LEARNING OBJECTIVES

By the end of Day 2 you will be able to:

| # | You'll learn | Why it matters | Where it's used in production |
|---|--------------|----------------|-------------------------------|
| 1 | **Namespaces** — virtual clusters inside one physical cluster | Isolation, multi-tenancy, quota & RBAC boundaries | Separating `dev`/`staging`/`prod`, per-team isolation, blast-radius control |
| 2 | **Labels** — key/value metadata attached to objects | The glue that connects objects loosely | Versioning (`app=web,version=v2`), cost allocation, GitOps tracking |
| 3 | **Selectors** — queries over labels | How Services find Pods, how Deployments own ReplicaSets | Service routing, canary/blue-green, `kubectl` filtering |
| 4 | **ConfigMaps** — non-sensitive config decoupled from images | Same image, different config per environment | DB hosts, feature flags, nginx.conf, app.properties |
| 5 | **Secrets** — base64-encoded sensitive data | Keeps passwords/tokens/keys out of images & manifests | DB passwords, API keys, TLS certs, registry creds |

### How this relates to Day 1
On Day 1 you built **Pods**, **Deployments**, and **Services** and drove them with `kubectl`. But you hardcoded everything: config lived inside the container image, and everything dumped into the `default` namespace. Day 2 is about **organization and configuration**:

```
Day 1:  [ Image + hardcoded config ] → Pod → Deployment → Service   (all in 'default')
Day 2:  Namespace(boundary) ─┬─ Labels/Selectors (how things find each other)
                             ├─ ConfigMap  (externalized non-secret config)
                             └─ Secret     (externalized sensitive config)
```

The single most important Day 1→Day 2 link: **a Service finds its Pods using a label selector.** That selector is a Day 2 concept you were already silently relying on.

---

## 2. ⚡ 80/20 BREAKDOWN

### The critical 20% you must master (delivers 80% of the value)

| Concept | Master this | kubectl muscle memory |
|---------|-------------|------------------------|
| Namespaces | `-n <ns>`, `--namespace`, default context | `kubectl get pods -n dev` / `kubectl config set-context --current --namespace=dev` |
| Labels | `key=value`, attach at create or after | `kubectl label pod nginx env=prod` |
| Selectors | equality (`=`,`!=`) & set-based (`in`,`notin`) | `kubectl get pods -l 'env in (prod,staging)'` |
| ConfigMap | env var **and** volume consumption | `kubectl create configmap app --from-literal=KEY=val` |
| Secret | base64, env & volume consumption | `kubectl create secret generic db --from-literal=PASS=...` |

### What you can safely DEFER
- ResourceQuotas & LimitRanges per namespace (Day on quotas/governance)
- Sealed Secrets / External Secrets Operator / Vault integration (covered when we do security hardening)
- `immutable: true` ConfigMaps/Secrets (a perf optimization — note it exists, move on)
- Encryption-at-rest config (`EncryptionConfiguration` on the API server — cluster-admin topic)
- Downward API (exposing pod metadata as env/volume — adjacent to ConfigMaps, defer)

### 💎 Interview gold
> **"Secrets are NOT encrypted by default — they are only base64-encoded, which is encoding, not encryption. Anyone with `get secret` RBAC or etcd access can read them. To actually protect them you enable encryption-at-rest in etcd, lock down RBAC, and/or use an external secret manager."**
>
> Saying this one sentence in an interview instantly signals you've operated Kubernetes in production, not just done a tutorial.

---

## 3. 📚 CONCEPT EXPLANATIONS

### 3.1 Namespaces

**Beginner explanation:** A Namespace is a logical partition inside a single physical cluster. It groups related objects (Pods, Services, ConfigMaps, etc.) and gives them a scope. Two objects can have the *same name* as long as they live in *different namespaces* — names must be unique only within a namespace.

**Real-world analogy:** A cluster is an apartment *building*. Namespaces are the *individual apartments*. Two apartments can each have a room called "kitchen" (same name, different apartment). You can put locks on doors (RBAC), meter the electricity per apartment (ResourceQuota), and one apartment flooding doesn't necessarily flood the others (isolation — though plumbing/network is shared unless you add NetworkPolicies).

**Default namespaces that ship with every cluster:**
- `default` — where objects go if you don't specify one (avoid using it in prod)
- `kube-system` — control plane components (CoreDNS, kube-proxy, etc.) — **don't touch**
- `kube-public` — world-readable, rarely used
- `kube-node-lease` — node heartbeat objects

**Production use case:** Isolate environments and teams: `team-payments-prod`, `team-payments-staging`, `observability`, `ingress-nginx`. Attach a `ResourceQuota` so the payments team can't accidentally consume the whole cluster, and `RBAC RoleBindings` so devs can only touch their own namespace.

**What is NOT namespaced (cluster-scoped):** Nodes, PersistentVolumes, Namespaces themselves, ClusterRoles, StorageClasses. Run `kubectl api-resources --namespaced=false` to see them all.

**Common mistakes:**
- Forgetting `-n` and operating on the wrong namespace ("why is my pod missing?" — it's in another namespace).
- Assuming namespaces provide network isolation. They **do not** by default — Pods across namespaces can talk freely unless you add `NetworkPolicy`.
- Cross-namespace Service reference using just the short name. Use the FQDN: `service.namespace.svc.cluster.local`.

**Best practices:**
- One namespace per (team × environment) is a common, clean model.
- Always set your working namespace in your context to avoid `default`.
- Pair every prod namespace with a ResourceQuota and RBAC.

**ASCII diagram:**
```
                     ┌───────────────────────── CLUSTER ─────────────────────────┐
                     │                                                            │
   ┌── namespace: dev ──┐   ┌── namespace: prod ──┐   ┌── kube-system ──┐         │
   │  Pod: web          │   │  Pod: web           │   │  CoreDNS         │        │
   │  Svc: web          │   │  Svc: web           │   │  kube-proxy      │        │
   │  ConfigMap: app    │   │  ConfigMap: app     │   │  ...             │        │
   └────────────────────┘   └─────────────────────┘   └──────────────────┘       │
        same names OK because different namespaces                                │
                     └────────────────────────────────────────────────────────────┘
   Cluster-scoped (no namespace): Nodes, PersistentVolumes, StorageClasses, ClusterRoles
```

---

### 3.2 Labels & Selectors

**Beginner explanation:** A **Label** is a key/value pair you attach to any object (`app: web`, `env: prod`, `version: v2`). Labels are arbitrary, queryable metadata. A **Selector** is a query that filters objects by their labels. Labels + selectors are how loosely-coupled Kubernetes objects discover and bind to each other.

> **Labels vs Annotations:** Labels are for *identifying/selecting* (queryable, indexed, short). Annotations are for *non-identifying metadata* (build URLs, descriptions, tool config) and are **not** selectable.

**Real-world analogy:** Labels are like sticky notes / hashtags on items in a warehouse. The selector is the search query: "give me every box tagged `#fragile` AND `#zone-A`." You don't reference boxes by exact shelf address — you describe the *attributes* you want.

**Two selector flavors:**

1. **Equality-based:** `=`, `==`, `!=`
   - `env=prod`, `tier!=frontend`
2. **Set-based:** `in`, `notin`, `exists`
   - `env in (prod, staging)`, `tier notin (cache)`, `!canary` (key does not exist), `version` (key exists)

> ⚠️ **Service selectors and Job/older selectors support only equality-based.** Deployments/ReplicaSets use `matchLabels` (equality) and `matchExpressions` (set-based) under `spec.selector`.

**Production use case:**
- **Service → Pod routing:** A Service's `selector: {app: web}` sends traffic to every Pod labeled `app=web`.
- **Canary release:** Run `app=web,track=stable` (9 pods) and `app=web,track=canary` (1 pod). A Service selecting just `app=web` load-balances across both — 10% canary traffic.
- **Cost allocation / chargeback:** `team=payments`, `cost-center=1234`.

**Common mistakes:**
- **Selector ↔ template label mismatch** in a Deployment: `spec.selector.matchLabels` MUST match `spec.template.metadata.labels`, or the API rejects it (or it adopts the wrong pods).
- `spec.selector` on Deployments is **immutable** after creation — you cannot change it; you must recreate the Deployment.
- A Service selector that matches **zero** pods → the Service has no Endpoints → connection refused/timeout.
- Too-broad selectors accidentally adopting unrelated pods.

**Best practices:**
- Adopt the recommended common labels: `app.kubernetes.io/name`, `app.kubernetes.io/instance`, `app.kubernetes.io/version`, `app.kubernetes.io/component`, `app.kubernetes.io/part-of`, `app.kubernetes.io/managed-by`.
- Keep selector labels stable; use *additional* labels (like `version`) for the rolling/changing bits.

**ASCII diagram:**
```
   Service: web                Selector: app=web
        │                              │
        ▼ (matches)                    ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ Pod          │  │ Pod          │  │ Pod          │
   │ app=web      │  │ app=web      │  │ app=api  ✗   │  ← NOT selected
   │ track=stable │  │ track=canary │  │              │
   └──────────────┘  └──────────────┘  └──────────────┘
        ✔ matched         ✔ matched          (ignored)

   kubectl get pods -l 'app=web,track in (stable,canary)'  → first two pods
```

---

### 3.3 ConfigMaps

**Beginner explanation:** A ConfigMap stores **non-sensitive** configuration data as key/value pairs (or whole files). It decouples configuration from your container image so the *same* image runs in dev, staging, and prod with different settings. ConfigMaps live in etcd, are namespaced, and are limited to **1 MiB**.

**Real-world analogy:** A ConfigMap is the *settings menu* of an app. The app binary (image) ships once; the settings (volume, language, server URL) are chosen separately and injected at runtime. You don't recompile the app to change the language.

**Two ways to consume a ConfigMap:**

| Consumption method | How it looks | When to use | Live updates? |
|--------------------|--------------|-------------|---------------|
| **Environment variables** (`env` / `envFrom`) | `APP_COLOR=blue` in the process env | Simple scalar settings, 12-factor apps | ❌ **No** — env is set at container start; changes need a pod restart |
| **Mounted volume** | each key becomes a file under a mount path | Config files (nginx.conf, application.yml), large/structured config | ✅ **Yes** — kubelet syncs updated files (with delay), if not `subPath` |

**Why this matters (interview gold):** Env-var config is captured at process startup and is frozen. Volume-mounted config files get updated in place by the kubelet (eventually, ~1 minute sync), **but** the app must watch/reload the file — Kubernetes won't restart your process for you. And `subPath` mounts do **not** receive updates.

**Production use case:** Externalize `application.yml` for a Spring Boot service, mount it at `/config`, and point the app there. Store feature flags as env vars. Change the ConfigMap + trigger a rollout to roll new config.

**Common mistakes:**
- Putting secrets in a ConfigMap (passwords, tokens) — use a Secret.
- Expecting env vars to update live — they don't.
- Editing a ConfigMap and being confused that running pods don't change (env case) — you must restart: `kubectl rollout restart deployment/<name>`.
- Referencing a ConfigMap that doesn't exist in that namespace → pods stuck `CreateContainerConfigError`.

**Best practices:**
- Use `envFrom` to bulk-import keys; use a config-hash annotation (`checksum/config`) so a config change triggers a rollout (Helm/Kustomize do this for you).
- Keep one ConfigMap per app/concern, named clearly.
- Consider `immutable: true` for large, never-changing ConfigMaps (better API server performance, prevents accidental edits).

**ASCII diagram:**
```
   ConfigMap "app-config"            ┌─ as ENV VARS ──────────────────┐
   ┌─────────────────────┐          │ container env:                  │
   │ APP_COLOR = blue    │ ───────► │   APP_COLOR=blue                │  frozen at start
   │ APP_MODE  = prod    │          │   APP_MODE=prod                 │
   │ app.conf  = <file>  │          └─────────────────────────────────┘
   └─────────────────────┘
            │                        ┌─ as VOLUME (mounted at /etc/cfg)┐
            └──────────────────────► │ /etc/cfg/APP_COLOR  (file)      │  updates live
                                     │ /etc/cfg/app.conf   (file)      │  (no subPath)
                                     └─────────────────────────────────┘
```

---

### 3.4 Secrets

**Beginner explanation:** A Secret is just like a ConfigMap but intended for **sensitive** data (passwords, tokens, TLS keys, registry credentials). Values are stored **base64-encoded**, also namespaced, also ~1 MiB. Common types: `Opaque` (default, arbitrary), `kubernetes.io/dockerconfigjson` (registry pull creds), `kubernetes.io/tls` (TLS cert+key), `kubernetes.io/service-account-token`.

**⚠️ Why base64, NOT encryption (critical concept):**
- **base64 is encoding, not encryption.** It exists so that *binary* data (certs, keys) can be stored as text in YAML/JSON. `echo cGFzcw== | base64 -d` instantly reveals `pass`.
- By default Secrets are stored in **etcd in plaintext** (base64 decodes trivially). Anyone with etcd access or `get secrets` RBAC can read them.
- To *actually* protect them you must:
  1. Enable **encryption-at-rest** in etcd (`EncryptionConfiguration` with `aescbc`/`kms`).
  2. Lock down **RBAC** so few principals can `get`/`list` secrets.
  3. Optionally use an external manager (**Vault, AWS/GCP Secrets Manager, External Secrets Operator, Sealed Secrets**).

**Real-world analogy:** A Secret out of the box is like writing your password on a postcard in a simple cipher anyone can decode — it's *separated* from the app, but not *protected* in transit/at rest until you add a real lock (encryption + RBAC).

**Why Secrets exist at all if base64 is weak:** They still provide value — separation from images/manifests, separate RBAC surface, ability to mount via tmpfs (memory, not disk), and a hook point for encryption-at-rest and external managers. The *abstraction* is the security boundary; you harden the implementation.

**Two ways to consume a Secret (same as ConfigMap):**
- **Env vars** (`env`/`envFrom`) — values auto-decoded into the env (visible in `/proc/<pid>/environ`, logs risk). No live update.
- **Volume mount** — each key becomes a file, stored on a **tmpfs (RAM)** by default, never written to node disk. Live-updates (no `subPath`). Generally preferred for sensitive data.

**Production use case:** DB password injected as `DB_PASSWORD` env from a Secret; TLS cert mounted as files for an Ingress controller; `imagePullSecrets` for pulling from a private registry.

**Common mistakes:**
- Committing Secret YAML (with base64 values) to git thinking it's "encrypted." It's plaintext-equivalent. Use Sealed Secrets / SOPS instead.
- `--from-literal` then wondering why `kubectl get secret -o yaml` shows gibberish — that's base64, decode it.
- Double base64-encoding (encoding an already-base64 value).
- Logging env vars that contain secrets.

**Best practices:**
- Prefer volume mounts over env for the most sensitive values.
- Use `stringData:` in YAML so you write plaintext and Kubernetes base64-encodes for you (no manual encoding).
- Enable encryption-at-rest and tight RBAC; use external managers in real prod.
- Never commit raw Secret manifests; use Sealed Secrets / External Secrets / SOPS.

**ASCII diagram:**
```
   Secret "db-secret" (Opaque, base64-stored in etcd)
   ┌────────────────────────────┐
   │ DB_USER = YWRtaW4=  (admin) │
   │ DB_PASS = czNjcjN0  (s3cr3t)│
   └──────────┬─────────────────┘
              │
      ┌───────┴────────────────┐
      ▼ env (decoded)           ▼ volume (tmpfs/RAM)
   DB_USER=admin              /etc/db/DB_USER  (file: admin)
   DB_PASS=s3cr3t             /etc/db/DB_PASS  (file: s3cr3t)
   (visible in environ)       (preferred for sensitive data)

   ⚠ base64 ≠ encryption. Harden with: encryption-at-rest + RBAC + external manager.
```

---

## 4. 🧪 HANDS-ON LABS (read-along — simulate the output in your head)

> Assume a working cluster (minikube/kind/EKS) and `kubectl` configured, from Day 1.

### Lab 1 — Create & use a Namespace

```bash
# Imperative create
kubectl create namespace dev
```
Expected:
```
namespace/dev created
```

Declarative equivalent (`namespace.yaml`):
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: dev
  labels:
    team: platform
    environment: development
```
```bash
kubectl apply -f namespace.yaml
kubectl get namespaces
```
Expected:
```
NAME              STATUS   AGE
default           Active   10d
dev               Active   5s
kube-node-lease   Active   10d
kube-public       Active   10d
kube-system       Active   10d
```

Deploy into the namespace and set it as default for your context:
```bash
kubectl create deployment web --image=nginx -n dev
kubectl get pods -n dev
# Make 'dev' the default so you can drop -n
kubectl config set-context --current --namespace=dev
kubectl get pods            # now implicitly -n dev
```
Expected (after rollout):
```
NAME                   READY   STATUS    RESTARTS   AGE
web-5d9f...-abcde       1/1     Running   0          12s
```

---

### Lab 2 — Labels & Selectors

```bash
# Label an existing pod (replace POD with your pod name)
kubectl label pod web-5d9f...-abcde tier=frontend env=dev -n dev
# Show labels
kubectl get pods --show-labels -n dev
```
Expected:
```
NAME               READY   STATUS    RESTARTS   AGE   LABELS
web-5d9f...-abcde   1/1     Running   0          2m    app=web,env=dev,pod-template-hash=5d9f,tier=frontend
```

Select with equality and set-based queries:
```bash
kubectl get pods -l tier=frontend -n dev
kubectl get pods -l 'env in (dev,staging)' -n dev
kubectl get pods -l '!canary' -n dev          # pods WITHOUT a 'canary' label
```

Overwrite and remove labels:
```bash
kubectl label pod web-5d9f...-abcde tier=backend --overwrite -n dev   # change value
kubectl label pod web-5d9f...-abcde env- -n dev                       # remove 'env' (trailing dash)
```
Expected:
```
pod/web-5d9f...-abcde labeled
pod/web-5d9f...-abcde unlabeled
```

A Service binds Pods via selector (`web-svc.yaml`):
```yaml
apiVersion: v1
kind: Service
metadata:
  name: web
  namespace: dev
spec:
  selector:
    app: web          # ← MUST match the pods' labels
  ports:
    - port: 80
      targetPort: 80
```
```bash
kubectl apply -f web-svc.yaml
kubectl get endpoints web -n dev   # proves the selector matched real pods
```
Expected (selector matched):
```
NAME   ENDPOINTS         AGE
web    10.244.0.7:80     3s
```
If you see `ENDPOINTS   <none>` the selector matched **zero** pods — fix the labels.

---

### Lab 3 — ConfigMap consumed as ENV vars AND as a mounted volume

Create the ConfigMap (mix of literals and a file):
```bash
kubectl create configmap app-config \
  --from-literal=APP_COLOR=blue \
  --from-literal=APP_MODE=production \
  -n dev
```
Inspect:
```bash
kubectl get configmap app-config -n dev -o yaml
```
Expected (trimmed):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: dev
data:
  APP_COLOR: blue
  APP_MODE: production
```

Declarative ConfigMap with a config file (`app-config.yaml`):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: dev
data:
  APP_COLOR: blue
  APP_MODE: production
  app.properties: |
    server.port=8080
    log.level=INFO
```

Pod consuming it BOTH ways (`cm-pod.yaml`):
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: cm-demo
  namespace: dev
spec:
  containers:
    - name: app
      image: busybox
      command: ["sh", "-c", "env | grep APP_; echo '---'; cat /etc/config/app.properties; sleep 3600"]
      env:
        - name: APP_COLOR                 # single key as env
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: APP_COLOR
      envFrom:
        - configMapRef:                   # bulk-import ALL keys as env
            name: app-config
      volumeMounts:
        - name: config-vol                # mount as files
          mountPath: /etc/config
  volumes:
    - name: config-vol
      configMap:
        name: app-config
```
```bash
kubectl apply -f cm-pod.yaml
kubectl logs cm-demo -n dev
```
Expected:
```
APP_COLOR=blue
APP_MODE=production
---
server.port=8080
log.level=INFO
```
List the mounted files:
```bash
kubectl exec cm-demo -n dev -- ls /etc/config
```
Expected:
```
APP_COLOR
APP_MODE
app.properties
```

> 🔑 Note: env vars (`APP_COLOR`, `APP_MODE`) were frozen at start. The mounted files under `/etc/config` would update if you edit the ConfigMap (after the kubelet sync delay) — the env vars would not.

---

### Lab 4 — Secret consumed as ENV (and as a volume)

Create a generic Secret:
```bash
kubectl create secret generic db-secret \
  --from-literal=DB_USER=admin \
  --from-literal=DB_PASS=s3cr3t \
  -n dev
```
Look at the stored (base64) form:
```bash
kubectl get secret db-secret -n dev -o yaml
```
Expected (trimmed):
```yaml
apiVersion: v1
kind: Secret
type: Opaque
metadata:
  name: db-secret
  namespace: dev
data:
  DB_USER: YWRtaW4=      # base64("admin")
  DB_PASS: czNjcjN0      # base64("s3cr3t")
```
Prove base64 is just encoding:
```bash
echo 'czNjcjN0' | base64 --decode    # → s3cr3t   (NOT encrypted!)
```

Declarative Secret using `stringData` (you write plaintext, K8s encodes):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
  namespace: dev
type: Opaque
stringData:
  DB_USER: admin
  DB_PASS: s3cr3t
```

Pod consuming the Secret as ENV and as a tmpfs volume (`secret-pod.yaml`):
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: secret-demo
  namespace: dev
spec:
  containers:
    - name: app
      image: busybox
      command: ["sh", "-c", "echo USER=$DB_USER; echo '---'; cat /etc/secret/DB_PASS; echo; sleep 3600"]
      env:
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: DB_USER
      envFrom:
        - secretRef:
            name: db-secret
      volumeMounts:
        - name: secret-vol
          mountPath: /etc/secret
          readOnly: true
  volumes:
    - name: secret-vol
      secret:
        secretName: db-secret
```
```bash
kubectl apply -f secret-pod.yaml
kubectl logs secret-demo -n dev
```
Expected:
```
USER=admin
---
s3cr3t
```
Confirm the volume is tmpfs (RAM, not disk):
```bash
kubectl exec secret-demo -n dev -- mount | grep /etc/secret
```
Expected (something like):
```
tmpfs on /etc/secret type tmpfs (ro,relatime)
```

---

## 5. 🏋️ EXERCISES (do these from memory)

1. Create a namespace `staging` with labels `team=payments,environment=staging`, then deploy an `nginx` Deployment (3 replicas) into it. Verify the pods are only in `staging`.
2. Label two of those three pods `track=canary` and the other `track=stable`. Write the `kubectl` command to list only canary pods, and only stable pods.
3. Create a ConfigMap `web-config` from a literal `WELCOME_MSG="Hello K8s"` plus a file `index.html`. Mount it into an nginx pod at `/usr/share/nginx/html` and confirm the page serves your file.
4. Create a Secret `api-secret` holding `API_KEY=abc123` using `stringData`. Inject it as an env var into a busybox pod and print it. Then decode the stored value with `base64 -d` to prove it's not encrypted.
5. Create a Service in `staging` that selects `app=nginx`. Break it intentionally by changing the selector to `app=nginx-broken`, then run `kubectl get endpoints` to observe the empty endpoint list. Fix it.

---

## 6. 🔧 TROUBLESHOOTING SECTION

### 6.1 "My pod/Service disappeared" — wrong namespace
- **Symptoms:** `kubectl get pods` shows nothing (or wrong objects); a Service can't reach a backend that "definitely exists."
- **Root cause:** You're operating in `default` (or the wrong namespace); the object lives elsewhere. Or you tried a cross-namespace Service call using the short name.
- **Diagnosis:**
  ```bash
  kubectl get pods -A | grep myapp           # search ALL namespaces
  kubectl config view --minify | grep namespace   # what's my current namespace?
  ```
- **Resolution:** Add `-n <ns>`, or set the context default: `kubectl config set-context --current --namespace=<ns>`. For cross-namespace traffic use the FQDN `svc.<namespace>.svc.cluster.local`.

### 6.2 ConfigMap / Secret not found — pod won't start
- **Symptoms:** Pod stuck in `CreateContainerConfigError` (or `RunContainerError`); never reaches `Running`.
- **Root cause:** The referenced ConfigMap/Secret doesn't exist **in the pod's namespace**, or a referenced **key** is missing. (ConfigMaps/Secrets are namespaced — they don't cross namespaces.)
- **Diagnosis:**
  ```bash
  kubectl describe pod <pod> -n <ns>     # Events: "configmap 'x' not found" / "couldn't find key Y"
  kubectl get configmap,secret -n <ns>
  ```
- **Resolution:** Create the ConfigMap/Secret in the **same namespace**; fix the `key:` name; or mark the reference `optional: true` if it's genuinely optional. Then the pod self-heals (it keeps retrying).

### 6.3 Pod not picking up config changes
- **Symptoms:** You edited the ConfigMap/Secret but the running app still uses the old values.
- **Root cause:** (a) Env-var consumption is frozen at container start — it never updates. (b) Even with volume mounts, updates have a propagation delay and the app must re-read the file; `subPath` mounts never update.
- **Diagnosis:**
  ```bash
  kubectl get configmap app-config -n dev -o yaml      # confirm new values are stored
  kubectl exec <pod> -n dev -- env | grep APP_         # env still old? → env case
  kubectl exec <pod> -n dev -- cat /etc/config/key     # volume updated yet?
  ```
- **Resolution:** For env-var config, restart the workload: `kubectl rollout restart deployment/<name> -n dev`. Best practice: add a `checksum/config` annotation on the pod template so any config change triggers a rollout automatically. Avoid `subPath` if you want live file updates.

### 6.4 Service has no endpoints — label selector mismatch
- **Symptoms:** Connections to a Service time out / connection refused; `kubectl get endpoints <svc>` shows `<none>`.
- **Root cause:** The Service `spec.selector` doesn't match any pod's labels (typo, wrong key/value, or pods labeled differently). Also check the pods are actually `Ready`.
- **Diagnosis:**
  ```bash
  kubectl get endpoints <svc> -n <ns>                  # <none> = no match
  kubectl describe svc <svc> -n <ns>                   # see the Selector
  kubectl get pods -n <ns> --show-labels               # compare labels
  kubectl get pods -l app=web -n <ns>                  # does the selector return pods?
  ```
- **Resolution:** Align the Service selector with the pod labels (or fix the pod labels). Ensure pods pass readiness probes (un-Ready pods are excluded from endpoints).

### 6.5 (Bonus) Deployment rejected — selector ≠ template labels
- **Symptoms:** `kubectl apply` fails: `selector does not match template labels`.
- **Root cause:** `spec.selector.matchLabels` must equal `spec.template.metadata.labels`.
- **Diagnosis/Resolution:** Make them match. Remember `spec.selector` is **immutable** on an existing Deployment — to change it, delete and recreate.

---

## 7. 📝 QUIZ SECTION

**MCQ 1.** Kubernetes Secrets are, by default:
- A) AES-256 encrypted in etcd
- B) base64-encoded (not encrypted)
- C) Stored only in memory and never persisted
- D) Encrypted with the cluster CA

**MCQ 2.** A Service shows `ENDPOINTS <none>`. The MOST likely cause is:
- A) The namespace was deleted
- B) The Service `selector` matches no Ready pods
- C) ConfigMap not found
- D) The Service type is ClusterIP

**MCQ 3.** Which consumption method receives live updates when you edit the source object?
- A) ConfigMap as env var
- B) Secret as env var
- C) ConfigMap mounted as a volume (no subPath)
- D) ConfigMap mounted via subPath

**Short answer 4.** Explain why two Pods can share the same name in a cluster but a single namespace cannot contain two Pods with the same name.

**Short answer 5.** You changed a ConfigMap consumed as environment variables but the app still shows old values. Why, and how do you apply the new config?

**Scenario 6.** Your team runs `payments` in namespace `prod`. A new dev accidentally deployed test pods to `prod` and they're consuming resources. Describe (a) how to prevent this going forward, and (b) how cross-namespace Service calls should be addressed.

### ✅ Answers

1. **B.** base64 is encoding, not encryption. Anyone with `get secrets` RBAC or etcd access can decode them. Real protection = encryption-at-rest + RBAC + external manager.
2. **B.** No (Ready) pods match the selector → empty Endpoints → traffic fails. Verify labels with `--show-labels` and check readiness.
3. **C.** Volume-mounted ConfigMaps (without `subPath`) are synced by the kubelet. Env vars (A, B) are frozen at start; `subPath` (D) does not update.
4. Pod names must be unique **only within a namespace** because the name's true identity is `(namespace, name)`. The namespace is part of the object's scope, so `dev/web` and `prod/web` are distinct objects; but `dev/web` twice would collide.
5. Environment variables are injected when the container starts and are never refreshed — the running process holds the old values. Apply the change with `kubectl rollout restart deployment/<name>` (or add a `checksum/config` annotation so config changes auto-trigger a rollout).
6. (a) Use **RBAC** to restrict who can create objects in `prod` (devs get a Role only in their own namespace), and add a **ResourceQuota** to cap resource usage. Also enforce CI/CD/GitOps so manual `kubectl apply` to prod is blocked. (b) Cross-namespace calls must use the fully-qualified DNS name `service.namespace.svc.cluster.local` (e.g., `payments-api.prod.svc.cluster.local`), not the short name.

---

## 8. 🚀 CHALLENGE PROJECT — Multi-environment configured web app

**Scenario (production-realistic):** You run a stateless web API image `myapi:1.0` that reads:
- `APP_ENV`, `LOG_LEVEL` from non-sensitive config,
- `DB_PASSWORD` from a secret,
- a mounted `application.yml` config file.

Deliver this in a dedicated `staging` namespace, fronted by a Service, with config externalized so the *same image* can later run in `prod` with different values. Bonus: ensure a config change triggers a safe rollout.

### Reference solution

```yaml
# 1) Namespace
apiVersion: v1
kind: Namespace
metadata:
  name: staging
  labels: { team: backend, environment: staging }
---
# 2) ConfigMap (non-sensitive)
apiVersion: v1
kind: ConfigMap
metadata: { name: myapi-config, namespace: staging }
data:
  APP_ENV: staging
  LOG_LEVEL: DEBUG
  application.yml: |
    server:
      port: 8080
    feature:
      newCheckout: true
---
# 3) Secret (sensitive) — stringData lets you write plaintext
apiVersion: v1
kind: Secret
metadata: { name: myapi-secret, namespace: staging }
type: Opaque
stringData:
  DB_PASSWORD: St@gingP@ss123
---
# 4) Deployment consuming both
apiVersion: apps/v1
kind: Deployment
metadata: { name: myapi, namespace: staging }
spec:
  replicas: 3
  selector:
    matchLabels: { app: myapi }
  template:
    metadata:
      labels: { app: myapi, version: v1 }
      annotations:
        checksum/config: "REPLACE_WITH_HASH"   # change → triggers rollout
    spec:
      containers:
        - name: myapi
          image: nginx          # stand-in for myapi:1.0
          ports: [{ containerPort: 8080 }]
          envFrom:
            - configMapRef: { name: myapi-config }     # APP_ENV, LOG_LEVEL
          env:
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef: { name: myapi-secret, key: DB_PASSWORD }
          volumeMounts:
            - name: cfg
              mountPath: /etc/myapi
      volumes:
        - name: cfg
          configMap:
            name: myapi-config
            items:
              - key: application.yml
                path: application.yml
---
# 5) Service
apiVersion: v1
kind: Service
metadata: { name: myapi, namespace: staging }
spec:
  selector: { app: myapi }
  ports: [{ port: 80, targetPort: 8080 }]
```

Apply and verify:
```bash
kubectl apply -f challenge.yaml
kubectl get pods -n staging --show-labels
kubectl get endpoints myapi -n staging          # should list 3 IPs
kubectl exec deploy/myapi -n staging -- env | grep -E 'APP_ENV|LOG_LEVEL|DB_PASSWORD'
kubectl exec deploy/myapi -n staging -- cat /etc/myapi/application.yml
# Roll new config:
kubectl create configmap myapi-config -n staging --from-literal=LOG_LEVEL=INFO --dry-run=client -o yaml | kubectl apply -f -
kubectl rollout restart deployment/myapi -n staging
```
**Why this is production-grade:** image is config-free (portable across envs), secrets are separated from non-secret config, the Service binds by a stable `app` label while `version` varies for rollouts, and the `checksum/config` annotation pattern guarantees config changes don't silently fail to deploy.

---

## 9. ✅ KNOWLEDGE CHECK (gut-check — answer out loud)

- Can two namespaces contain a Service called `web`? Why?
- Do namespaces isolate network traffic by default? What gives you network isolation?
- What's the difference between `spec.selector.matchLabels` and `spec.template.metadata.labels` in a Deployment, and what happens if they disagree?
- Name the two ways to consume a ConfigMap and which one updates live.
- Is base64 encryption? What three things actually protect Secrets?
- Why is a Secret volume mounted on tmpfs and why does that matter?
- Your Service has no endpoints — what's the first command you run?
- After editing an env-var ConfigMap, what command makes pods pick it up?

---

## 10. 📋 CHEAT SHEET

### Namespaces
```bash
kubectl create namespace dev
kubectl get ns
kubectl get pods -n dev                # operate in a namespace
kubectl get pods -A                    # all namespaces
kubectl config set-context --current --namespace=dev   # set default ns
kubectl api-resources --namespaced=false               # cluster-scoped kinds
```

### Labels & Selectors
```bash
kubectl label pod NAME env=prod                  # add
kubectl label pod NAME env=staging --overwrite   # change
kubectl label pod NAME env-                       # remove
kubectl get pods --show-labels
kubectl get pods -l env=prod
kubectl get pods -l 'env in (prod,staging),tier!=cache'
kubectl get pods -l '!canary'                     # key absent
```

### ConfigMaps
```bash
kubectl create configmap app --from-literal=K=V
kubectl create configmap app --from-file=app.conf
kubectl create configmap app --from-env-file=.env
kubectl get cm app -o yaml
```
```yaml
# env (single key)            # env (all keys)           # volume
env:                          envFrom:                   volumes:
- name: K                     - configMapRef:            - name: cfg
  valueFrom:                      name: app                configMap:
    configMapKeyRef:                                         name: app
      name: app                                          volumeMounts:
      key: K                                             - name: cfg
                                                           mountPath: /etc/cfg
```

### Secrets
```bash
kubectl create secret generic db --from-literal=PASS=s3cr3t
kubectl create secret tls tls-cert --cert=tls.crt --key=tls.key
kubectl create secret docker-registry reg --docker-server=... --docker-username=... --docker-password=...
kubectl get secret db -o jsonpath='{.data.PASS}' | base64 -d   # decode
```
```yaml
type: Opaque
stringData:            # write plaintext; K8s base64-encodes
  PASS: s3cr3t
# consume: secretKeyRef / secretRef / volume(secret:)
```

### Key concepts (one-liners)
- Names unique **per namespace**; namespaces don't isolate network by default (use NetworkPolicy).
- Service → Pods via **label selector**; no match ⇒ empty Endpoints.
- `spec.selector` of a Deployment is **immutable**.
- Env config = **frozen at start**; volume config = **live-updates** (not `subPath`).
- **Secrets = base64, not encrypted.** Harden with encryption-at-rest + RBAC + external manager.

---

## 11. 💼 INTERVIEW PREPARATION

**Beginner**
- *Q: What is a namespace?* A virtual cluster partition scoping objects; names are unique within it; used for env/team isolation, RBAC, and quotas.
- *Q: ConfigMap vs Secret?* Both externalize config as key/values; ConfigMap = non-sensitive plaintext, Secret = sensitive, base64-encoded, can be mounted on tmpfs and gated by RBAC.

**Intermediate**
- *Q: How does a Service know which Pods to route to?* Via its `spec.selector` matching Pod labels; matched Ready pods become the Service's Endpoints.
- *Q: Two ways to consume config and the update behavior?* Env vars (frozen at start) and volume mounts (live-sync, except `subPath`). For env changes do `rollout restart`.

**Scenario**
- *Q: A pod is stuck in `CreateContainerConfigError`. Walk me through it.* `describe pod` → event says configmap/secret/key not found → it's missing in that namespace or the key name is wrong → create it in the same namespace / fix key → pod self-heals.
- *Q: Canary with labels?* Stable pods `track=stable`, canary `track=canary`, both `app=web`; Service selects only `app=web` so it load-balances across both, giving you a traffic split by replica ratio.

**Production**
- *Q: How do you secure Secrets in production?* base64 isn't security — enable etcd **encryption-at-rest** (KMS), tighten **RBAC** on `secrets`, mount via tmpfs, avoid putting secrets in env where possible, and use **External Secrets Operator / Vault / Sealed Secrets**; never commit raw Secret YAML.
- *Q: How do you guarantee a config change actually rolls out?* Add a `checksum/config` annotation derived from the ConfigMap/Secret content to the pod template (Helm/Kustomize do this); any change mutates the template → triggers a rolling update.

---

## 12. 🎓 TOP 50 QUESTIONS

### Fundamentals (1–15)
1. What is a Kubernetes namespace and what problem does it solve?
2. Which four namespaces ship by default and what is each for?
3. Name three object kinds that are cluster-scoped (not namespaced).
4. Do namespaces provide network isolation by default? What does?
5. What is a label and how does it differ from an annotation?
6. What is a selector and what two types exist?
7. Give an example of an equality-based and a set-based selector.
8. How does a Service use selectors to find Pods?
9. What is a ConfigMap and what is its size limit?
10. What are the two primary ways to consume a ConfigMap?
11. What is a Secret and how is its data stored by default?
12. Why is base64 not a security mechanism?
13. List three Secret types and their purposes.
14. What is the difference between `data` and `stringData` in a Secret?
15. What are the Kubernetes recommended common labels (`app.kubernetes.io/*`)?

### Practical (16–25)
16. Write a command to create namespace `dev` and set it as your default.
17. Write a command to list all pods across every namespace.
18. Write a command to add label `tier=frontend` to a pod, then remove it.
19. Write a set-based selector for pods where `env` is `prod` or `staging` and `tier` is not `cache`.
20. Create a ConfigMap from a literal and from a file in one command each.
21. Write the YAML to inject one ConfigMap key as an env var.
22. Write the YAML to mount a whole ConfigMap as files at `/etc/config`.
23. Create a generic Secret from two literals and decode one value.
24. Write the YAML to inject a Secret key as an env var via `secretKeyRef`.
25. Write a Service that selects `app=web` on port 80 → targetPort 8080.

### Scenario (26–35)
26. Same image must run in dev/staging/prod with different DB hosts — design it.
27. Implement a 10% canary using only labels and a single Service.
28. You must rotate a DB password with zero downtime — outline the steps.
29. Restrict a dev team to only their namespace — what objects do you use?
30. Cross-namespace call from `app` ns to `payments-api` in `prod` — what address?
31. Mount only one key of a multi-key ConfigMap as a single file — how?
32. Bulk-import all keys of a ConfigMap and a Secret as env vars — how?
33. Make a config change auto-trigger a rolling update — what pattern?
34. Choose env-var vs volume for a TLS private key and justify it.
35. Prevent accidental edits to a large static ConfigMap — what field?

### Troubleshooting (36–45)
36. `kubectl get pods` shows nothing though you just deployed — diagnose.
37. Pod stuck in `CreateContainerConfigError` — likely causes and fix.
38. Service returns connection-refused; `get endpoints` shows `<none>` — why?
39. You edited an env-var ConfigMap but pods show old values — why & fix?
40. Volume-mounted config doesn't update — what two causes?
41. `apply` fails with "selector does not match template labels" — fix.
42. You can't change a Deployment's `spec.selector` — why, and what's the workaround?
43. A Secret value looks like gibberish in `-o yaml` — what is it and how to read it?
44. Cross-namespace Service call fails using the short name — fix.
45. Pods in `prod` consuming all resources from a rogue deploy — prevent it.

### Interview (46–50)
46. Explain how Secrets can be made genuinely secure in production.
47. Compare consuming config as env vars vs mounted volumes (tradeoffs).
48. Why are labels/selectors fundamental to Kubernetes' loose coupling?
49. When would you choose multiple namespaces vs multiple clusters?
50. Describe the `checksum/config` rollout pattern and why it's needed.

---

## 13. 📖 FREE RESOURCES

| Resource | Type | Difficulty | Time | Why | Priority |
|----------|------|-----------|------|-----|----------|
| Kubernetes Docs — Namespaces | Docs | Beginner | 20m | Authoritative, concise | ⭐⭐⭐ Must |
| Kubernetes Docs — Labels & Selectors | Docs | Beginner | 25m | Exact selector syntax | ⭐⭐⭐ Must |
| Kubernetes Docs — ConfigMaps | Docs | Beginner | 30m | All consumption patterns | ⭐⭐⭐ Must |
| Kubernetes Docs — Secrets | Docs | Intermediate | 35m | Types + security caveats | ⭐⭐⭐ Must |
| Kubernetes Docs — Encryption at Rest | Docs | Advanced | 25m | Real secret hardening | ⭐⭐ Should |
| "Kubernetes the Hard Way" (Kelsey Hightower, GitHub) | Lab | Advanced | 4h | Deep mental model | ⭐ Later |
| TechWorld with Nana — K8s tutorial (YouTube) | Video | Beginner | 1h | ConfigMap/Secret visuals | ⭐⭐ Should |
| killercoda.com K8s scenarios | Practice | Beginner→Int | 1h | Free browser cluster | ⭐⭐⭐ Must |
| External Secrets Operator docs | Docs | Advanced | 30m | Prod secret management | ⭐ Later |
| CKA/CKAD curriculum (cncf) | Reference | Intermediate | 20m | Exam-aligned scope | ⭐⭐ Should |

**Official docs reading plan (in order):** Namespaces → Labels and Selectors → ConfigMaps → Secrets → (skim) Encryption at Rest. Budget ~2 hours total.

- **Must-Read:** K8s Docs — ConfigMaps & Secrets pages (consumption + security notes).
- **Must-Watch:** TechWorld with Nana ConfigMap/Secret segment.
- **Must-Practice:** killercoda "ConfigMaps", "Secrets", and "Namespaces" scenarios — type the commands.
- **Must-Memorize:** the 5 one-liner key concepts in the Cheat Sheet (§10).

**Highest-ROI single resource:** The official **Secrets** doc — it cements the base64-vs-encryption truth (your interview gold) and both consumption methods in one read.

---

## 14. ➡️ NEXT STEPS

Before moving on, do **active recall** (close this file and answer §9 Knowledge Check + the §11 production questions out loud, then write the Lab 3/Lab 4 YAML from memory). If you can explain *why Secrets are base64 not encrypted* and *why env-var config doesn't live-update*, you've got Day 2.

When you're ready, type **"Continue to Day 3"**.
