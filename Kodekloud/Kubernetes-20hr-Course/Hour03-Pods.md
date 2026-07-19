# Hour 3: Pods, Labels, Selectors, Namespaces

## 1. Explanation (Simple → Technical)

**Simple version:** A **Pod** is like a **shared apartment**. Roommates (containers) living in it share the same front door (network — one IP address) and the same common areas like the kitchen (storage volumes). They're tightly coupled — if the apartment building is torn down, everyone in it moves out together. A typical example: your main app "roommate" plus a "sidecar" roommate whose only job is to watch the app's logs and ship them elsewhere.

**Labels** are like **sticky notes** you attach to your apartment door: `env=prod`, `tier=frontend`, `team=payments`. Anyone walking the building can read the sticky notes without opening the door.

**Selectors** are how someone finds apartments with a certain sticky note — "give me every apartment labeled `env=prod`" — without needing to know each apartment's address. This is exactly how Services and Deployments find the right Pods to send traffic to or manage.

**Namespaces** are like **separate floors or wings in the same building** — Dev wing, Staging wing, Prod wing. Each wing can have its own rules, its own set of apartments, and residents on one floor normally don't interact with residents on another floor, even though it's the same physical building (cluster).

**Technical version:**
- A **Pod** is the **smallest deployable unit** in Kubernetes — not a container. A Pod wraps one or more containers that:
  - Share the same **network namespace** → same IP address and port space. Containers inside talk to each other via `localhost`.
  - Share the same **storage volumes** → can read/write the same files.
  - Are always scheduled together on the **same Node**, created together, and destroyed together.
- **Why not just use containers directly?** Some workloads need "helper" processes tightly bound to the main app — a log shipper, a proxy, a metrics exporter. Kubernetes models this as **multi-container Pods** (sidecar pattern) instead of forcing you to bundle everything into one container image.
- **Labels** are arbitrary key-value pairs attached to objects (Pods, Nodes, Services, etc.) in `metadata.labels`. They carry no built-in meaning to Kubernetes — you define the meaning (e.g., `env=prod`, `tier=frontend`, `version=v2`).
- **Selectors** are queries over labels. Controllers (Deployments, ReplicaSets) and Services use `selector:` fields to find which Pods they own/route to. This is how a Deployment "knows" which Pods belong to it, and how a Service "knows" which Pods to load-balance across — by label match, not by name or IP.
- **Namespaces** provide a way to divide cluster resources between multiple users, teams, or environments — a form of **virtual clustering** inside one physical cluster. Most object names must be unique **within** a namespace but can be reused **across** namespaces (e.g., a `frontend` Deployment can exist in both `dev` and `prod` namespaces).
  - **Default namespaces created automatically:**
    - `default` — where objects land if you don't specify a namespace.
    - `kube-system` — Kubernetes' own control-plane components (DNS, etc.).
    - `kube-public` — readable by all users, even unauthenticated ones; holds cluster info.
    - `kube-node-lease` — heartbeat/lease objects for Node health.
  - Not all objects are namespaced — Nodes and PersistentVolumes, for example, are cluster-scoped.

## 2. Diagram

```
Pod (single IP: 10.244.1.5)
┌───────────────────────────────────────────────┐
│  Pod: web-app-pod                              │
│  Labels: env=prod, tier=frontend               │
│                                                 │
│   ┌────────────────────┐  ┌──────────────────┐ │
│   │ Container: app      │  │ Container: logger│ │
│   │ (nginx)              │  │ (log-shipper)    │ │
│   │ port 80              │  │ reads same files │ │
│   └─────────┬────────────┘  └────────┬─────────┘ │
│             │  shared network NS      │           │
│             └───────────┬─────────────┘           │
│                         │                          │
│              Shared volume: /var/log               │
│         (both containers read/write here)          │
└───────────────────────────────────────────────┘
   Talk to each other via localhost. Same IP, same lifecycle.


Namespaces = isolated "rooms" within one cluster

┌───────────────────────────── Kubernetes Cluster ─────────────────────────────┐
│                                                                               │
│  ┌──────── namespace: dev ────────┐   ┌──────── namespace: staging ───────┐  │
│  │  Pod: web-app (env=dev)        │   │  Pod: web-app (env=staging)      │  │
│  │  Pod: db (env=dev)              │   │  Pod: db (env=staging)           │  │
│  └─────────────────────────────────┘   └────────────────────────────────────┘  │
│                                                                               │
│  ┌──────── namespace: prod ───────┐   ┌────── namespace: kube-system ─────┐  │
│  │  Pod: web-app (env=prod)       │   │  Pod: coredns                    │  │
│  │  Pod: db (env=prod)             │   │  Pod: kube-proxy                 │  │
│  └─────────────────────────────────┘   └────────────────────────────────────┘  │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
   Same names ("web-app", "db") can exist in every room without colliding.
```

## 3. Real-World Example

A company like Meesho runs the **same microservices** — say `order-service`, `payment-service` — across multiple environments. Instead of running separate physical clusters for each environment (expensive, slow to provision), they create **namespaces**: `dev`, `staging`, `prod`. Engineers testing a new feature deploy into `dev` without any risk of touching real customer traffic in `prod`. RBAC rules restrict who can `kubectl apply` into `prod`.

Within `prod`, a `Service` named `order-service` doesn't hardcode the IP addresses of the Pods behind it (Pods get replaced constantly during deploys/scaling — their IPs are ephemeral). Instead, the Service has a **selector** like `app=order-service,tier=backend`. Any Pod carrying those labels — regardless of which ReplicaSet created it or when — automatically gets included in the Service's load-balancing pool. When a Deployment does a rolling update and creates new Pods with the same labels, the Service picks them up instantly; old Pods are dropped the moment they're deleted. This label-based indirection is the backbone of how Kubernetes achieves zero-downtime deployments and dynamic service discovery.

## 4. Hands-On Lab

**Goal:** Create a multi-container Pod, explore labels/selectors, and manage namespaces.

**Step 1 — Sample Pod YAML with 2 containers, a shared volume, and labels**

Save as `web-app-pod.yaml`:
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web-app-pod
  labels:
    env: prod
    tier: frontend
spec:
  volumes:
    - name: shared-logs
      emptyDir: {}
  containers:
    - name: app
      image: nginx:1.25
      ports:
        - containerPort: 80
      volumeMounts:
        - name: shared-logs
          mountPath: /var/log/nginx
    - name: logger
      image: busybox:1.36
      command: ["sh", "-c", "tail -f /var/log/nginx/access.log"]
      volumeMounts:
        - name: shared-logs
          mountPath: /var/log/nginx
```

**Step 2 — Create the Pod and inspect it**
```bash
kubectl apply -f web-app-pod.yaml

# List pods with their labels
kubectl get pods --show-labels
```

**Expected output:**
```
NAME          READY   STATUS    RESTARTS   AGE   LABELS
web-app-pod   2/2     Running   0          10s   env=prod,tier=frontend
```

**Step 3 — Use selectors**
```bash
# Find pods matching a label
kubectl get pods -l env=prod
kubectl get pods -l 'env=prod,tier=frontend'
kubectl get pods -l 'env!=prod'          # negation
kubectl get pods -l 'env in (prod,staging)'   # set-based
```

**Step 4 — Namespaces**
```bash
# List existing namespaces
kubectl get ns

# Create new namespaces
kubectl create namespace dev
kubectl create namespace staging
kubectl create namespace prod

# Run a pod inside a specific namespace
kubectl apply -f web-app-pod.yaml -n prod

# List pods in a specific namespace
kubectl get pods -n prod

# List pods across ALL namespaces
kubectl get pods -A

# Set a default namespace for your current context (avoids typing -n every time)
kubectl config set-context --current --namespace=prod
```

**Expected output for `kubectl get ns`:**
```
NAME              STATUS   AGE
default           Active   10d
kube-node-lease   Active   10d
kube-public       Active   10d
kube-system       Active   10d
prod              Active   5s
staging           Active   5s
dev               Active   5s
```

**Troubleshooting:**
- Pod stuck in `ContainerCreating` → `kubectl describe pod web-app-pod` to see events (often image pull issues).
- Forgot which namespace you're in → `kubectl config view --minify | grep namespace`.
- `kubectl get pods` shows nothing but you know you created a Pod → you probably created it in a different namespace; try `kubectl get pods -A`.

## 5. Common Mistakes

1. **Cramming multiple unrelated apps into one Pod** — e.g., putting a frontend and an unrelated backend database in the same Pod because "it's easier." Pods should only group containers that truly need to share network/storage and scale/die together (sidecar pattern), not unrelated services.
2. **Forgetting the `-n`/`--namespace` flag** — running `kubectl apply -f app.yaml` without specifying a namespace silently creates the resource in `default`, leading to confusion later when "it's not showing up" (it's just in the wrong namespace).
3. **Inconsistent label naming/casing** — using `Env=Prod` in one manifest and `env=production` in another. Selectors are exact-match and case-sensitive, so inconsistent labels silently break Service routing and Deployment ownership.
4. **Deleting a namespace without realizing it cascades** — `kubectl delete namespace staging` deletes **every single object** inside it (Pods, Services, ConfigMaps, Secrets, PVCs) with no separate confirmation per object. This is one of the most common ways to accidentally wipe out an entire environment.
5. **Assuming containers in different Pods can share `localhost`** — only containers within the **same Pod** share a network namespace. Two Pods, even on the same Node, need a Service or Pod IP to talk to each other — never `localhost`.
6. **Not setting resource limits per container in a multi-container Pod** — one noisy container (e.g., a badly behaved sidecar) can starve CPU/memory from the main app container since they share the Pod's Node resources.

## 6. Interview Questions (with brief answers)

1. **What is the smallest deployable unit in Kubernetes, and is it the same as a container?** — A Pod. No — a Pod is a wrapper that can hold one or more containers that share network and storage; you never deploy a bare container directly.
2. **Why would you put more than one container in a Pod?** — For tightly-coupled helper processes (sidecar pattern) like log shippers, service meshes (Envoy proxies), or init/setup tasks that need to share the app's filesystem or network namespace.
3. **How does a Kubernetes Service know which Pods to send traffic to?** — Via a label selector defined in the Service spec; any Pod whose labels match the selector is automatically added to the Service's endpoint list.
4. **What happens to all the Pods/Services/ConfigMaps inside a namespace when you delete that namespace?** — They are all deleted along with it — namespace deletion cascades to every namespaced object inside.
5. **Name three default namespaces created when a cluster is provisioned.** — `default`, `kube-system`, `kube-public` (also `kube-node-lease`).

## 7. Quiz (50 Questions)

**True/False:**
1. A Pod can contain more than one container. (T)
2. Containers in the same Pod get separate IP addresses. (F)
3. Labels have built-in meaning that Kubernetes enforces automatically. (F)
4. Selectors are how Services and Deployments find the Pods they manage. (T)
5. Namespaces provide full hardware-level isolation like separate VMs. (F)
6. Two Pods in different namespaces can have the same name. (T)
7. Deleting a namespace only deletes the namespace object, not what's inside it. (F)
8. `kube-system` is where Kubernetes' own control-plane pods typically run. (T)
9. Containers within a Pod can communicate via `localhost`. (T)
10. You must always specify a namespace, otherwise `kubectl apply` fails. (F)

**Multiple Choice:**
11. What is the smallest deployable unit in Kubernetes? a) Container b) Node c) Pod d) Namespace → (c)
12. Which of the following is shared between containers in the same Pod? a) Nothing b) Network namespace & optionally volumes c) Separate IPs d) Separate Nodes always → (b)
13. How does a Service find the Pods it should route traffic to? a) By Pod name b) By label selector c) By IP address hardcoded in YAML d) By container image name → (b)
14. Which namespace is used by default if none is specified? a) kube-system b) kube-public c) default d) kube-node-lease → (c)
15. What command shows Pods with their labels? a) `kubectl get pods -v` b) `kubectl get pods --show-labels` c) `kubectl describe labels` d) `kubectl labels get pods` → (b)

**Short Answer:**
16. What is the "shared apartment" analogy describing in this lesson?
17. What two things do containers in the same Pod typically share?
18. What is the difference between a label and a selector?
19. Give an example of a key-value label pair you might use for an environment.
20. Why might you use a sidecar container instead of building logging directly into your main app's image?
21. What command lists all namespaces in a cluster?
22. What command lists Pods across every namespace at once?
23. What flag would you add to `kubectl get pods` to filter by the label `tier=backend`?
24. What is a Namespace in Kubernetes, conceptually?
25. Name one object type that is NOT namespaced (i.e., cluster-scoped).

**Scenario-Based:**
26. Your team wants dev, staging, and prod environments in one cluster without them interfering. What Kubernetes feature addresses this directly?
27. A Deployment isn't picking up some Pods you'd expect it to manage. What's the first thing you'd check regarding labels?
28. Someone on your team runs `kubectl delete ns staging` thinking it will just remove an empty folder-like object. What actually happens, and how would you prevent this mistake going forward?
29. You have a Pod with a main app container and a sidecar that tails logs. The sidecar keeps consuming excessive CPU and starving the app. What did the team forget to configure?
30. Two engineers each deploy a Pod named `redis` — one in `dev` and one in `staging` — and are surprised it works without conflict. Why does this work?

**Fill in the Blank:**
31. The smallest deployable unit in Kubernetes is called a ______.
32. Containers in the same Pod share the same ______ namespace, meaning they get one IP address.
33. Key-value metadata attached to objects like Pods are called ______.
34. A query used to find objects that match certain labels is called a ______.
35. If you don't specify a namespace, resources are created in the ______ namespace.

**Conceptual Deep-Dive:**
36. Why does Kubernetes model multi-container groupings as Pods instead of just letting you run several processes inside one container?
37. Why are label selectors preferred over hardcoding Pod names or IPs when wiring up Services?
38. What's the architectural benefit of Namespaces for multi-tenancy versus running entirely separate physical clusters per environment?
39. Why is it important that Pod IPs are considered ephemeral, and how do selectors/Services solve the problem this creates?
40. What risk does an inconsistent labeling convention introduce across a growing number of microservices and teams?

**Command Practice:**
41. Write the command to create a namespace called `qa`.
42. Write the command to list only Pods labeled `tier=frontend` in the `prod` namespace.
43. Write the command to apply `pod.yaml` directly into the `staging` namespace.
44. Write the command to set your kubectl context's default namespace to `dev`.
45. Write the command to describe a Pod named `web-app-pod` to debug why it's not starting.

**Reflection:**
46. In your own words, explain the "shared apartment" analogy for Pods and why roommates (containers) still keep their own separate stuff (images/processes).
47. Which part of Labels vs Selectors felt most confusing, and why?
48. Can you think of a real app you use where a sidecar container (like a log shipper or proxy) might be running alongside the main app?
49. Why do you think Kubernetes chose NOT to give Namespaces true hardware-level isolation (like separate VMs)?
50. What questions do you still have about Pods, Labels, or Namespaces before we move to Hour 4 (Deployments & ReplicaSets)?

---

## 8. Hour 3 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Pod** | Smallest deployable unit; wraps 1+ containers sharing network (IP) and optionally storage |
| **Multi-container Pods** | Used for tightly-coupled sidecar patterns (logging, proxies, metrics) — not unrelated apps |
| **Labels** | Arbitrary key-value metadata tags (e.g., `env=prod`, `tier=frontend`) attached to objects |
| **Selectors** | Queries over labels used by Services/Deployments/ReplicaSets to find the Pods they manage |
| **Namespaces** | Virtual clusters inside one physical cluster; enable multi-tenancy/environment separation |
| **Default namespaces** | `default`, `kube-system`, `kube-public`, `kube-node-lease` |
| **Danger zone** | Deleting a namespace cascades and deletes everything inside it |
| **Lab outcome** | You created a 2-container Pod with a shared volume, applied it across namespaces, and used selectors to filter Pods |

**Mnemonic:** *"PLSN"* — **P**od (shared apartment) → **L**abels (sticky notes) → **S**electors (find by sticky note) → **N**amespaces (separate floors/wings).

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Pods](https://kubernetes.io/docs/concepts/workloads/pods/)
- [Kubernetes Official Docs — Labels and Selectors](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/)
- [Kubernetes Official Docs — Namespaces](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)
- [KodeKloud Kubernetes for Beginners](https://kodekloud.com/courses/kubernetes-for-the-absolute-beginners-hands-on)
- YouTube: "TechWorld with Nana — Kubernetes Pods Explained" (free, excellent visuals)

**Mini-Project for Hour 3 (optional, 20 min):**
- Create three namespaces: `dev`, `staging`, `prod`.
- Deploy the same `web-app-pod.yaml` (2-container Pod with a shared `emptyDir` volume) into all three namespaces, but change the `env` label value to match each namespace (`env=dev`, `env=staging`, `env=prod`).
- Run `kubectl get pods -A --show-labels` and confirm you can see all three, distinguished only by namespace and label.
- Practice selecting: `kubectl get pods -A -l env=staging`. Then delete just the `staging` namespace and observe that its Pod disappears while `dev` and `prod` remain untouched — reinforcing namespace isolation and cascading deletes.
