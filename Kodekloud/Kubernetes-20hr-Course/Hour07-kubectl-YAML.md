# Hour 7: kubectl Mastery, YAML Basics, Imperative vs Declarative

## 1. Explanation (Simple → Technical)

**Simple version:** `kubectl` is your walkie-talkie to the Kubernetes control room (the API server). You either **shout quick one-off orders** ("start a pod named nginx!") — that's **imperative** — or you **hand over a written blueprint** ("here's exactly what I want the kitchen to look like, go make it so") — that's **declarative**. Restaurants that scale don't run on shouted orders forever; they run on written recipes (YAML files) that anyone on the team can read, review, and re-cook identically every time.

**Technical version:**

- **kubectl** is a CLI client. It reads your local `~/.kube/config`, figures out which cluster/context you're pointed at, converts your command into an HTTP request (REST call), and sends it to the **kube-apiserver**. The API server validates/authenticates it, then reads/writes the desired object state to **etcd** (the cluster's database). kubectl never talks to etcd or nodes directly — the API server is the sole front door.

- **Anatomy of a Kubernetes YAML manifest** — every object you create is expressed with four top-level fields:
  - `apiVersion` — which API group/version this object belongs to (e.g. `v1`, `apps/v1`, `batch/v1`). Tells the API server how to parse/validate the rest.
  - `kind` — the type of object (`Pod`, `Deployment`, `Service`, `ConfigMap`, etc.).
  - `metadata` — identifying info: `name`, `namespace`, `labels`, `annotations`. This is how you find/select the object later.
  - `spec` — the **desired state** you're declaring ("I want 3 replicas of this container image"). This is the part you write.
  - `status` — the **observed/actual state**, filled in and continuously updated by Kubernetes controllers (you never write this yourself; it's read-only feedback).

  The entire Kubernetes model is: you write `spec`, controllers work to make `status` match `spec`. This is the **reconciliation loop**, and it's why declarative YAML is so powerful — Kubernetes keeps re-checking and re-correcting forever, not just once.

- **Imperative** commands tell Kubernetes *what action to take right now*:
  - `kubectl run nginx --image=nginx` — create a pod directly
  - `kubectl create deployment web --image=nginx --replicas=3`
  - `kubectl expose deployment web --port=80`
  - `kubectl scale deployment web --replicas=5`
  - `kubectl delete pod nginx`

  These are fast for learning/debugging but leave **no record** of what you did. If you scale imperatively in prod and don't update a file, the next `kubectl apply -f` from Git will silently revert your change — this is called **drift**.

- **Declarative** approach: you write the desired state into a `.yaml` file, then run `kubectl apply -f file.yaml`. Kubernetes computes the diff between what's in the file and what currently exists, and only changes what's necessary. Because the YAML file is the source of truth:
  - It's **git-trackable** — every change goes through a pull request, is reviewed, and has history (`git log` = your entire infrastructure's audit trail).
  - It's **reproducible** — spin up a new cluster, run `kubectl apply -f .` on the same files, get an identical environment.
  - It **merges** cleanly with other people's changes (declarative operations are closer to idempotent; running `apply` twice with the same file is a no-op).
  - This is the foundation of **GitOps**: Git is the single source of truth, and tools like ArgoCD/Flux continuously reconcile the live cluster to match what's committed — no human ever runs `kubectl apply` by hand in production.

  This is why production teams overwhelmingly prefer declarative: imperative commands are great for *exploration and learning*, declarative YAML + Git is what survives audits, onboarding, disaster recovery, and 3am incidents when nobody remembers what commands were run six months ago.

- **kubectl productivity toolkit:**
  - `kubectl get <resource>` — list objects (`-o wide`, `-o yaml`, `-o json` for more detail)
  - `kubectl describe <resource> <name>` — full details + recent Events (your #1 debugging tool)
  - `kubectl apply -f <file/dir>` — declarative create-or-update (upsert)
  - `kubectl create -f <file>` — declarative create, but **fails if the object already exists**
  - `kubectl delete -f <file>` or `kubectl delete <resource> <name>`
  - `kubectl logs <pod> [-c container] [-f]` — container stdout/stderr
  - `kubectl exec -it <pod> -- /bin/sh` — shell into a running container
  - `kubectl edit <resource> <name>` — open the live object in `$EDITOR`, save to apply changes immediately (imperative edit of a declarative object — use sparingly!)
  - `kubectl explain <resource>.<field>.<subfield>` — built-in API documentation, e.g. `kubectl explain pod.spec.containers`
  - `kubectl create deployment web --image=nginx --dry-run=client -o yaml > deploy.yaml` — generates a valid YAML skeleton **without actually creating anything**, saving you from typing YAML from memory
  - `kubectl config get-contexts` / `kubectl config use-context <name>` / `kubectl config set-context --current --namespace=<ns>` — switching clusters/namespaces safely
  - Aliases & autocomplete: `alias k=kubectl` + `source <(kubectl completion zsh)` turns 20 keystrokes into 2, which matters a lot once you're typing hundreds of commands a day.

## 2. Diagram

```
kubectl request flow:

  ┌──────────┐   HTTPS/REST    ┌────────────────┐   read/write   ┌───────┐
  │  kubectl │ ───────────────>│  kube-apiserver │───────────────>│ etcd  │
  │ (client) │<─────────────── │ (auth, validate)│<───────────────│ (DB)  │
  └──────────┘   JSON response └────────────────┘                └───────┘
       │
       ▼
  ~/.kube/config
  (which cluster? which context? which namespace? which creds?)


Imperative vs Declarative workflow:

IMPERATIVE                             DECLARATIVE
"Turn left, then right,                "Go to 123 Main St."
 then straight for 2 miles..."          (the map/GPS figures out the route,
                                         and re-routes if you get bumped off)

kubectl run/create/expose/scale   -->   write desired-state.yaml
     │                                        │
     ▼                                        ▼
 direct, one-time change                git commit -> PR review
     │                                        │
     ▼                                        ▼
 NOT recorded anywhere              kubectl apply -f . / ArgoCD sync
 (lost on the next apply/           (reconciled continuously,
  hard to reproduce)                  drift auto-corrected, full history)
```

## 3. Real-World Example

**GitOps team:** A platform team stores every Kubernetes manifest (Deployments, Services, ConfigMaps) in a Git repo, organized by environment (`staging/`, `prod/`). Their CI/CD pipeline runs `kubectl apply -f .` (or, more robustly, ArgoCD watches the repo and auto-syncs). When an engineer wants to change replica count, they open a PR, get it reviewed, merge to `main`, and ArgoCD automatically reconciles the live cluster to match — no one ever SSHes into a cluster to run commands by hand. If a node dies and the cluster gets rebuilt, `kubectl apply -f .` on the same repo reproduces the exact same environment. Git history *is* the change log for the entire infrastructure.

**Contrast — the junior engineer incident:** Under pressure during an incident, a junior engineer runs `kubectl scale deployment payment-service --replicas=10` directly against prod to handle a traffic spike, then `kubectl set image deployment payment-service payment-service=payment:v2.3.1-hotfix` to ship a quick fix. Both work — the incident is resolved. But neither change exists in any YAML file or Git commit. Three weeks later, another engineer runs the normal GitOps pipeline (`kubectl apply -f .` from the repo, which still says `replicas: 3` and image `v2.3.0`), and the "fix" and capacity boost silently vanish, causing a repeat outage. This is the textbook cost of imperative-only operations in production — and exactly why the declarative/GitOps model exists.

## 4. Hands-On Lab

**Goal:** Practice generating, applying, inspecting, and safely modifying manifests, and set up a faster kubectl workflow.

```bash
# 1. Imperative: quick pod for exploration
kubectl run nginx --image=nginx
kubectl get pods
```
Expected output:
```
NAME    READY   STATUS    RESTARTS   AGE
nginx   1/1     Running   0          5s
```

```bash
# 2. Generate a Deployment YAML instead of typing it from scratch
kubectl create deployment web --image=nginx --replicas=3 \
  --dry-run=client -o yaml > web-deployment.yaml

cat web-deployment.yaml
```
Expected: a full valid `apps/v1` Deployment manifest printed to the file — nothing was actually created in the cluster (`--dry-run=client` only renders locally).

```bash
# 3. Declarative: apply the generated file
kubectl apply -f web-deployment.yaml
kubectl get deployments
```
Expected output:
```
deployment.apps/web created

NAME   READY   UP-TO-DATE   AVAILABLE   AGE
web    3/3     3            3           10s
```

```bash
# 4. Try create on the same file (should fail — it already exists)
kubectl create -f web-deployment.yaml
```
Expected output:
```
Error from server (AlreadyExists): deployments.apps "web" already exists
```
Compare with re-running `kubectl apply -f web-deployment.yaml` — it succeeds silently with "unchanged" because apply upserts.

```bash
# 5. Explore the API structure without leaving the terminal
kubectl explain pod.spec.containers
kubectl explain deployment.spec.strategy
```
Expected: field-by-field documentation straight from the API server's OpenAPI schema.

```bash
# 6. Edit live (imperative edit of a declarative object — use carefully!)
kubectl edit deployment web
# change replicas: 3 -> 4 in the editor, save & quit
kubectl get deployment web
```

```bash
# 7. See everything running, across all namespaces
kubectl get all -A
```

```bash
# 8. Context and namespace management
kubectl config get-contexts
kubectl config use-context minikube
kubectl config set-context --current --namespace=default
```
Expected `get-contexts` output:
```
CURRENT   NAME       CLUSTER    AUTHINFO   NAMESPACE
*         minikube   minikube   minikube
```

```bash
# 9. Productivity: alias + autocomplete (add to ~/.zshrc)
echo 'alias k=kubectl' >> ~/.zshrc
echo 'source <(kubectl completion zsh)' >> ~/.zshrc
echo 'complete -F __start_kubectl k' >> ~/.zshrc
source ~/.zshrc

k get pods   # now works identically to `kubectl get pods`, with tab-completion
```

```bash
# Cleanup
kubectl delete -f web-deployment.yaml
kubectl delete pod nginx
```

**Troubleshooting:**
- `error: unable to recognize "web-deployment.yaml": no matches for kind` → check `apiVersion` matches the `kind` (e.g. `Deployment` needs `apps/v1`, not `v1`).
- `edit` opens a blank/error editor → set `export KUBE_EDITOR=vim` (or `nano`) in your shell profile.
- YAML "did not find expected key" errors → almost always an indentation/tab problem; YAML requires spaces, never tabs.

## 5. Common Mistakes

1. **Mixing imperative and declarative changes on the same object** — running `kubectl scale` or `kubectl edit` directly on an object that's also managed by a Git-tracked YAML file. The next `kubectl apply -f .` silently overwrites the manual change, causing confusing "my fix disappeared" incidents (drift).
2. **Not using `--dry-run=client -o yaml` before applying** — engineers hand-write YAML from memory, get a field wrong (e.g. `replica` instead of `replicas`), and only discover it after `apply` half-succeeds. Always generate a skeleton first, then edit it.
3. **Confusing `kubectl create -f` with `kubectl apply -f`** — `create` fails with `AlreadyExists` if the object is already there (fine for first-time setup, breaks CI re-runs); `apply` upserts (creates if missing, patches if present) and is idempotent, which is why almost all pipelines standardize on `apply`.
4. **YAML indentation errors** — using tabs instead of spaces, or misaligning list items under `containers:`, causes cryptic parser errors or, worse, YAML that parses but means something different than intended (e.g. a second container silently nested inside the first one's fields because of a 1-space indentation slip).
5. **Editing objects with `kubectl edit` in production and forgetting to update the source YAML/Git** — the live cluster and the Git repo now disagree, and nobody notices until the next full sync wipes the manual fix.
6. **Applying an entire directory (`kubectl apply -f .`) without reviewing what changed** — skipping `kubectl diff -f .` before apply means unintended deletions/modifications from stale files can slip into a shared cluster.

## 6. Interview Questions (with brief answers)

1. **What is the difference between imperative and declarative Kubernetes management?** — Imperative issues direct commands describing an action (`kubectl run`, `kubectl scale`) that execute once and leave no record; declarative describes the desired end state in YAML files applied via `kubectl apply -f`, which Kubernetes continuously reconciles toward, and which is git-trackable and reproducible.
2. **How do you quickly generate a YAML template for a new resource without writing it from scratch?** — Use an imperative command with `--dry-run=client -o yaml`, e.g. `kubectl create deployment web --image=nginx --dry-run=client -o yaml > deploy.yaml`; it renders valid YAML locally without touching the cluster, which you then edit and apply.
3. **What's the difference between `kubectl create -f` and `kubectl apply -f`?** — `create` only creates and errors out with `AlreadyExists` if the object already exists; `apply` is an upsert — it creates the object if absent or computes and applies a patch/diff if it already exists, making it safe to re-run (idempotent), which is why CI/CD pipelines use `apply`.
4. **What are the four core top-level fields of a Kubernetes manifest, and which one do you never write yourself?** — `apiVersion`, `kind`, `metadata`, `spec` are all authored by the user; `status` is populated and updated by Kubernetes controllers to reflect observed state — you should never hand-write it.
5. **Why do kubectl and the API server never talk to etcd's clients directly from outside?** — The API server is the single gatekeeper: it authenticates/authorizes every request, validates the object schema, and is the only component with etcd credentials/access; this centralizes security, auditing, and consistency, and is why `kubectl` (or any client) always goes through the API server, never around it.

## 7. Quiz (50 Questions)

**True/False:**
1. kubectl communicates directly with etcd. (F)
2. The `spec` field in a manifest represents the desired state. (T)
3. The `status` field is written by the user. (F)
4. `kubectl apply -f` is idempotent — running it twice with the same file causes no error. (T)
5. `kubectl create -f` will fail if the object already exists. (T)
6. Declarative configuration is generally preferred for production environments. (T)
7. `kubectl run` is a declarative command. (F)
8. YAML requires consistent spacing and does not allow tabs for indentation. (T)
9. GitOps means the live cluster state is the source of truth, not Git. (F)
10. `--dry-run=client` actually creates the object in the cluster. (F)

**Multiple Choice:**
11. Which field tells Kubernetes which API group/version to use? a) kind b) apiVersion c) metadata d) spec → (b)
12. Which command generates a YAML skeleton without creating anything? a) kubectl apply b) kubectl create --dry-run=client -o yaml c) kubectl edit d) kubectl describe → (b)
13. Which of these is an imperative command? a) kubectl apply -f file.yaml b) kubectl run nginx --image=nginx c) git commit d) argocd sync → (b)
14. What does `kubectl explain pod.spec.containers` show? a) Live pod logs b) Field-level API documentation c) Pod events d) Node capacity → (b)
15. Which command switches your active cluster/context? a) kubectl config use-context b) kubectl get contexts c) kubectl set-cluster d) kubectl switch → (a)
16. What happens if you run `kubectl create -f deploy.yaml` twice? a) Updates the object b) Errors with AlreadyExists c) Deletes and recreates d) No-op → (b)
17. Which tool commonly automates GitOps reconciliation? a) Helm b) ArgoCD c) Minikube d) kubeadm → (b)
18. What does the API server do before writing to etcd? a) Nothing, passes through b) Authenticate and validate the request c) Compile the YAML into Go binaries d) Restart nodes → (b)
19. Which flag opens a live object for direct in-place editing? a) kubectl patch b) kubectl edit c) kubectl apply d) kubectl describe → (b)
20. What is the risk of mixing imperative edits with declarative YAML management? a) Faster deploys b) Configuration drift c) Better security d) No risk → (b)

**Short Answer:**
21. What are the four top-level fields common to nearly every Kubernetes manifest?
22. Why is `status` never written by the user?
23. What is the main advantage of storing manifests in Git?
24. What command lists all resources across all namespaces?
25. What's the difference between `kubectl logs` and `kubectl exec`?
26. Why does `kubectl explain` not require internet access?
27. What is configuration drift?
28. What does the acronym GitOps emphasize as the source of truth?
29. Why is `kubectl apply` considered idempotent?
30. What shell feature makes typing `kubectl` commands faster and less error-prone?

**Scenario-Based:**
31. Your teammate ran `kubectl scale deployment web --replicas=10` directly in prod during an incident. What could go wrong later, and what should they do instead?
32. You need to create a Service manifest but don't remember the exact YAML syntax. What's the fastest correct way to get a working template?
33. A CI pipeline step runs `kubectl create -f deploy.yaml` and it now fails every second run. What's the bug, and what's the fix?
34. You inherit a cluster with no documentation and no Git repo of manifests. How would you begin recovering the desired-state definitions?
35. A file has a Deployment manifest with 2-space indentation in most places but a stray tab on one line — what will likely happen when you `kubectl apply -f` it?
36. Your team wants zero manual `kubectl apply` commands run against production. What tool/pattern should you introduce?
37. You need to quickly check why a Pod is stuck in `Pending`. Which single kubectl command gives you the most diagnostic info?
38. Two engineers both edit the same Deployment — one via `kubectl edit`, another via a Git-committed YAML applied minutes later. What happens to the first engineer's change?
39. You're new to the `batch/v1` API group and unsure what fields a `CronJob.spec` supports. What command answers this without leaving the terminal?
40. Your kubectl is pointed at the wrong cluster and you're worried about running commands against production by accident. What should you check and fix first?

**Fill in the Blank:**
41. The Kubernetes CLI client that talks to the API server is called ______.
42. The four top-level manifest fields are apiVersion, kind, metadata, and ______.
43. ______ is the field Kubernetes controllers use to report actual observed state.
44. The flag `--dry-run=client -o yaml` is used to ______ without creating the object.
45. ______ is the practice of using Git as the single source of truth for infrastructure state.
46. `kubectl config use-context` switches the active ______.
47. The shell alias `alias k=______` is a common productivity shortcut.
48. `kubectl create -f` fails with a(n) ______ error if the object already exists.
49. `kubectl ______` opens an object for live in-place editing in your default editor.
50. `kubectl ______ pod.spec.containers` prints field-level API documentation.

**Conceptual Deep-Dive:**
*(folded into short-answer/scenario sections above to keep exactly 50 total)*

## 8. Hour 7 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **kubectl's role** | CLI client that translates commands into REST calls to the kube-apiserver; never talks to etcd or nodes directly |
| **YAML anatomy** | `apiVersion` (which API), `kind` (what type), `metadata` (name/labels/namespace), `spec` (desired state you write), `status` (observed state, controller-written) |
| **Imperative** | Direct one-off commands (`run`, `create`, `expose`, `scale`) — fast, but unrecorded and easy to lose |
| **Declarative** | Desired state in YAML files + `kubectl apply -f` — git-trackable, reproducible, reconciled continuously |
| **Why prod prefers declarative** | Auditability, reproducibility, drift prevention, GitOps automation (ArgoCD/Flux) |
| **create vs apply** | `create` fails if exists; `apply` upserts and is idempotent |
| **Fast YAML generation** | `kubectl create ... --dry-run=client -o yaml > file.yaml` |
| **Debug/inspect toolkit** | `get`, `describe`, `logs`, `exec`, `explain`, `edit` |
| **Context management** | `kubectl config get-contexts` / `use-context` / `set-context --current --namespace=` |
| **Productivity** | `alias k=kubectl` + shell autocompletion |

**Mnemonic:** *"GAME"* — **G**it is truth (declarative/GitOps) vs **A**d-hoc commands (imperative); **A**pply upserts, **M**etadata names it, **E**xplain teaches you the API without leaving the terminal.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes kubectl Cheat Sheet (official)](https://kubernetes.io/docs/reference/kubectl/cheatsheet/) — bookmark this, you'll use it constantly
- [kubectl Command Reference](https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands) — full flag-by-flag docs
- [Kubernetes API Conventions & Object Reference](https://kubernetes.io/docs/reference/kubernetes-api/) — the authoritative source for every field in `spec`
- [Declarative Management of Kubernetes Objects Using Configuration Files (official guide)](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/declarative-config/)
- YouTube: "TechWorld with Nana — kubectl & YAML Explained"

**Mini-Project for Hour 7 (30-45 min):**
Pick 3 imperative commands you'd normally run, convert each into an equivalent YAML file, and apply them declaratively:
1. `kubectl run nginx --image=nginx` → generate with `--dry-run=client -o yaml`, save as `nginx-pod.yaml`, apply it.
2. `kubectl create deployment api --image=httpd --replicas=2` → save as `api-deployment.yaml`, apply it.
3. `kubectl expose deployment api --port=80 --target-port=80` → generate the equivalent Service YAML (`kubectl expose ... --dry-run=client -o yaml`), save as `api-service.yaml`, apply it.

Then delete all three objects, re-run `kubectl apply -f .` on the folder, and confirm the exact same environment comes back — proving to yourself why declarative YAML + Git is the production standard.
