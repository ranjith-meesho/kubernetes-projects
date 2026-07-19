# Hour 20: Final Project, Mock Interview, 100 Kubernetes Interview Questions, Cheat Sheet, Command Reference, Roadmap

> **Companion files:** This capstone hour ships with standalone quick-reference files in the same folder, created alongside this one: `cheat-sheet.md`, `kubectl-100-commands.md`, `production-checklist.md`, `troubleshooting-flowchart.md`, and `roadmap.md`. Print/pin those for daily use — this file is the narrative wrap-up; those are the grab-and-go references.

This is the capstone hour of the **Kubernetes in 20 Hours (80/20 Rule)** course. There's no new K8s concept here — instead we consolidate everything from Hours 1–19 into a project you can point to, an interview you can walk into, and a roadmap for what comes after "done."

---

## 1. Final Project Brief

**Project name:** *Production-Hardened 3-Tier App via Helm*

**Goal:** Take the 3-tier application you built in Hour 18 (frontend → backend API → database) and re-deploy it as a single **Helm chart** (Hour 17) that meets every production hardening requirement from Hour 19 — resource limits, health probes, autoscaling, disruption budgets, RBAC, Ingress with TLS, externalized Secrets, and monitoring-readiness.

### Architecture to deliver

```
                        ┌─────────────────────┐
   Internet ──────────▶ │   Ingress (TLS)      │
                        └──────────┬───────────┘
                                   │
                     ┌─────────────┴─────────────┐
                     ▼                            ▼
           ┌───────────────────┐        ┌───────────────────┐
           │ Service: frontend │        │  Service: backend  │
           └─────────┬─────────┘        └─────────┬──────────┘
                     ▼                             ▼
        ┌─────────────────────────┐   ┌─────────────────────────┐
        │ Deployment: frontend    │   │ Deployment: backend      │
        │  HPA (2-10 replicas)    │   │  HPA (3-15 replicas)     │
        │  PDB (minAvailable=1)   │   │  PDB (minAvailable=2)    │
        │  probes + limits        │   │  probes + limits         │
        └─────────────────────────┘   └─────────┬────────────────┘
                                                  ▼
                                       ┌───────────────────────┐
                                       │ StatefulSet: database │
                                       │  PVC (persistent)      │
                                       │  Secret (creds)        │
                                       └───────────────────────┘

  Cross-cutting: ServiceAccount + RBAC Role/RoleBinding per component,
  ConfigMaps for non-secret config, NetworkPolicy restricting DB access
  to backend only, resource requests/limits everywhere, all values
  templated via Helm `values.yaml` (dev/staging/prod overrides).
```

### Deliverables

1. A Helm chart (`Chart.yaml`, `values.yaml`, `templates/`) that deploys all three tiers plus Ingress, Secrets, ConfigMaps, HPA, PDB, RBAC, and NetworkPolicy.
2. A `values-prod.yaml` overlay showing at least one setting that differs from dev (e.g., replica counts, resource sizes, Ingress host).
3. A short runbook (`RUNBOOK.md`) documenting: how to deploy, how to roll back, how to scale, and what to check first when something breaks.

### Acceptance Checklist (self-verify all ~15 before calling it done)

- [ ] 1. `helm install` succeeds cleanly on a fresh namespace with zero manual `kubectl` patches afterward.
- [ ] 2. `helm lint` and `helm template` both run with no errors or warnings.
- [ ] 3. Every container in every Deployment/StatefulSet has `resources.requests` **and** `resources.limits` set (no unbounded containers).
- [ ] 4. Every container has a `readinessProbe` and a `livenessProbe`; the database also has a `startupProbe` if it has a slow boot.
- [ ] 5. **App survives a `kubectl delete pod <backend-pod>` with zero dropped requests** — verified by running a load generator (`hey`/`ab`/`k6`) against the Ingress URL during the delete and confirming 0 non-2xx responses (Service + readiness probe routing traffic only to healthy replicas).
- [ ] 6. `helm upgrade` with a deliberately bad image tag (`myapp:does-not-exist`) auto-fails readiness, the rollout **stalls** without taking down existing healthy Pods, and `helm rollback` restores the previous good release.
- [ ] 7. **HPA scales up under synthetic load and back down after load stops** — observed via `kubectl get hpa -w` showing replica count rise then fall to `minReplicas` within the stabilization window.
- [ ] 8. A `PodDisruptionBudget` exists for every tier with >1 replica, and a `kubectl drain` on a node never violates `minAvailable`.
- [ ] 9. Secrets (DB password, API keys) are never present in `values.yaml`, ConfigMaps, or plain env vars in the chart — only referenced via `Secret` objects (ideally sourced from a secret manager, not committed to git).
- [ ] 10. Each component runs under its own `ServiceAccount` with a `Role`/`RoleBinding` scoped to only the permissions it needs (no component uses `cluster-admin` or the `default` ServiceAccount).
- [ ] 11. A `NetworkPolicy` restricts database access to the backend Pods only — verified by `kubectl exec` into the frontend Pod and confirming it **cannot** reach the database port.
- [ ] 12. Ingress serves TLS (even if via a self-signed cert / cert-manager staging issuer) and HTTP requests redirect to HTTPS.
- [ ] 13. All Pods expose a `/metrics` (or equivalent) endpoint and have the standard Prometheus scrape annotations/labels, even if no Prometheus is installed yet — i.e., the app is "monitoring-ready."
- [ ] 14. `kubectl top pods` shows real usage comfortably within the configured requests/limits under normal load (no immediate OOMKilled, no wildly over-provisioned requests wasting cluster capacity).
- [ ] 15. Deleting the entire release (`helm uninstall`) and reinstalling from scratch reproduces a fully working system with no manual intervention — proving the chart is truly declarative and idempotent.

**Stretch goals:** add a `HorizontalPodAutoscaler` driven by a custom metric (requests/sec) instead of CPU; add a `CronJob` for DB backups; wire the chart into a CI pipeline that runs `helm lint` + `helm template` + a `kubeval`/`kubeconform` schema check on every PR.

---

## 2. Mock Interview

**Role:** Junior DevOps / Cloud Engineer. 12 questions, mixing conceptual and scenario/behavioral, drawn across the whole course.

**Q1 (Interviewer): Walk me through what happens, end-to-end, when you run `kubectl apply -f deployment.yaml`.**
**A:** kubectl sends the YAML as a REST request to the API server, which authenticates and authorizes me, then validates and persists the desired state (a Deployment object) into etcd. The Deployment controller notices the new/changed object and creates a ReplicaSet; the ReplicaSet controller creates the requested number of Pod objects. The Scheduler watches for unscheduled Pods, picks a Node based on resource availability and constraints, and binds the Pod to it. The kubelet on that Node sees the binding, pulls the container image, and starts the container via the container runtime. Kube-proxy updates networking rules so Services can route to the new Pod once it passes its readiness probe.

**Q2: What's the difference between a Deployment, a ReplicaSet, and a Pod?**
**A:** A Pod is the smallest deployable unit — one or more containers sharing network/storage. A ReplicaSet ensures a specified number of identical Pod replicas are running at all times. A Deployment sits above the ReplicaSet and manages rollout strategy — rolling updates, rollback history, and versioning — by creating new ReplicaSets on each change and shifting traffic gradually.

**Q3: Your production app is returning 502s intermittently. Walk me through how you'd debug it.**
**A:** First, `kubectl get pods` to check Pod status — are they Running, CrashLoopBackOff, or flapping? Then `kubectl describe pod` for events (OOMKilled, failed probes, image pull errors) and `kubectl logs <pod> --previous` if it restarted. I'd check `kubectl get events --sort-by=.lastTimestamp` cluster-wide for node pressure or eviction. If Pods look healthy but requests still 502, I'd check the Service's endpoints (`kubectl get endpoints`) to confirm it's actually routing to Ready Pods, then check the Ingress controller's logs. 502 specifically often means the upstream (Pod) closed the connection — that points me toward readiness probe misconfiguration or the app crashing under load, so I'd correlate timing with `kubectl top pods` and any HPA scaling events.

**Q4: Explain the difference between a liveness probe and a readiness probe, and describe a real mistake you could make configuring them.**
**A:** Liveness answers "is this container alive, or should Kubernetes restart it?" Readiness answers "is this container ready to receive traffic right now?" A common, costly mistake is pointing the liveness probe at a heavy endpoint (e.g., one that checks the database), because if the DB is briefly slow, the app gets killed and restarted even though the app process itself is fine — this can cause a cascading restart storm exactly when the system is already under stress. Liveness should check "is the process alive," readiness should check "can I currently serve dependencies."

**Q5: When would you choose a ClusterIP Service vs a NodePort vs a LoadBalancer, and where does Ingress fit in?**
**A:** ClusterIP is for internal-only communication between services inside the cluster — the default, and the most common. NodePort exposes a port on every Node, mostly used for on-prem/bare-metal or debugging, since it's clunky and insecure for hosting via cloud LBs directly. LoadBalancer provisions a cloud provider's external load balancer, one per Service — fine for a handful of services but expensive and unwieldy at scale. Ingress sits above all of this: a single entry point (backed by one LoadBalancer/NodePort) that does host/path-based routing to many ClusterIP Services, plus TLS termination — so in practice, production traffic goes through Ingress → ClusterIP Services, and LoadBalancer/NodePort are reserved for special cases.

**Q6: Tell me about a time — real or from your training — you had to decide whether to scale a service horizontally or vertically. What factors did you weigh?**
**A:** During Hour 13's HPA lab, the scenario was a checkout service with CPU spikes during flash sales. I favored horizontal scaling (HPA) because the workload was stateless and could be load-balanced across many small Pods, and horizontal scaling doesn't require a restart, so no user-facing disruption. Vertical scaling (VPA) would have required recreating the Pod with new resource values, causing a blip, and it hits a hard ceiling (the biggest Node available), whereas horizontal scaling degrades more gracefully and can also shrink back down automatically after the spike, saving cost. I'd reach for vertical scaling instead for something like a stateful, hard-to-replicate singleton process, or as a one-time "right-sizing" exercise rather than a live scaling response.

**Q7: What's the difference between a resource *request* and a resource *limit*, and what happens if you set a memory limit too low?**
**A:** A request is what the scheduler guarantees is reserved for the container when placing it on a Node — it affects scheduling, not runtime enforcement. A limit is the hard ceiling the container cannot exceed at runtime. If a container exceeds its memory limit, the kernel OOM-kills it and Kubernetes reports `OOMKilled`, restarting it per the restart policy. Setting the memory limit too low causes repeated OOMKills under normal load — visible as a CrashLoopBackOff with `OOMKilled` as the last-state reason in `kubectl describe pod`.

**Q8: How would you securely inject a database password into a Pod without putting it in the image or a plain ConfigMap?**
**A:** Store it as a Kubernetes `Secret` (ideally sourced from an external secret manager like Vault/AWS Secrets Manager via something like External Secrets Operator, rather than committed to git as raw base64). Mount it into the Pod either as an environment variable via `secretKeyRef` or as a mounted file via a volume — I generally prefer mounted files for highly sensitive values since env vars can leak via `kubectl describe`, process listings, or crash dumps more easily. I'd also restrict who can `kubectl get secret` via RBAC, and enable encryption-at-rest for etcd so the Secret isn't stored in plaintext on disk.

**Q9: What's the security tradeoff between giving a ServiceAccount `cluster-admin` versus a tightly scoped Role, and when (if ever) is broad access justified?**
**A:** `cluster-admin` is convenient — nothing ever breaks due to missing permissions — but it means a compromised Pod or a bug in that workload's controller can read/modify/delete anything cluster-wide, including other teams' Secrets. A tightly scoped Role limits blast radius to exactly what the workload needs (e.g., read-only on ConfigMaps in its own namespace). Broad access is occasionally justified for cluster-level infrastructure controllers (e.g., an Ingress controller or a monitoring agent that legitimately needs to watch resources cluster-wide) — but even then, I'd prefer a scoped `ClusterRole` naming specific resources/verbs rather than a blanket `cluster-admin` binding.

**Q10: Explain what a PodDisruptionBudget is for, and give a concrete scenario where not having one causes an outage.**
**A:** A PDB caps how many Pods of a given app can be *voluntarily* disrupted at once (node drains, cluster upgrades, `kubectl drain`) — it doesn't protect against involuntary disruption like a crash. Scenario: you have 3 replicas of a critical service, no PDB, and a cluster upgrade drains 2 nodes back-to-back to save time. If those 2 nodes happen to host 2 of your 3 Pods, you're down to 1 Pod handling all traffic — possibly overwhelmed — while the upgrade proceeds without pausing. A PDB with `minAvailable: 2` forces the drain to wait until replacement Pods are Ready elsewhere before evicting more, preventing the capacity cliff.

**Q11: You're asked to reduce the cluster's cloud bill by 30% without hurting reliability. What do you look at first?**
**A:** First, `kubectl top pods` / a metrics dashboard across the fleet to find services whose resource *requests* are far above their actual usage — over-provisioned requests waste bin-packing capacity and force the Cluster Autoscaler to keep extra Nodes around even when real usage is low. I'd right-size requests/limits based on observed p95 usage, not guesses. Second, I'd check whether HPA `minReplicas` is set higher than necessary for off-peak hours. Third, I'd look at whether workloads could use cheaper Node types (spot/preemptible instances) for fault-tolerant, stateless workloads — with PDBs in place so this doesn't hurt reliability. I'd avoid cutting limits so tight that OOMKills or throttling start appearing, and I'd validate any change in staging with a load test before rolling to prod.

**Q12: Why do you want to work in DevOps/Cloud Engineering, and how does this course fit into your longer-term goals?**
**A:** I like the systems-thinking side of engineering — not just "does the code work" but "does the whole thing stay up, scale, and recover on its own at 3am without me." This course gave me hands-on reps across the full stack: from why containers exist, through architecture, workloads, networking, storage, security, and finally packaging and hardening a real app with Helm. My goal now is to get CKA-certified, get real experience on a managed cloud Kubernetes service, and grow into GitOps and observability — this project and this interview prep are my proof that I can go from zero to a production-minded deployment, not just memorized commands.

---

## 3. 100 Kubernetes Interview Questions

*Ordered foundational → advanced, spanning every hour of the course. First 20 include a short model answer in parentheses.*

**Fundamentals & Containers (1–8)**
1. What problem does Kubernetes solve? (Automates scheduling, healing, scaling, and deploying containers across many machines.)
2. What is the difference between a container and a virtual machine? (Containers share the host kernel — lightweight; VMs virtualize hardware and run a full guest OS each — heavy.)
3. Is Kubernetes the same as Docker? (No — Docker builds/runs containers; Kubernetes orchestrates containers, regardless of which runtime built them.)
4. What is containerd and how does it relate to Docker? (containerd is a lightweight container runtime; Docker Engine itself uses containerd under the hood.)
5. What is "K8s" short for and where does the name come from? (Kubernetes, from the Greek for "helmsman/pilot" — "8" replaces the 8 letters between K and s.)
6. What was Kubernetes inspired by? (Google's internal cluster manager, Borg.)
7. Name three core benefits Kubernetes provides over manual container management. (Self-healing, autoscaling, zero-downtime rolling deployments.)
8. Why might a small single-service app *not* need Kubernetes? (The orchestration overhead outweighs the benefit when there's nothing to orchestrate/scale/heal across.)

**Architecture (9–16)**
9. What are the main components of the Kubernetes control plane? (API server, etcd, scheduler, controller manager, and cloud-controller-manager.)
10. What is etcd and why is it critical? (A distributed key-value store holding all cluster state; losing it means losing the cluster's brain.)
11. What does the kube-apiserver do? (The single entry point for all cluster operations — authenticates, validates, and persists requests.)
12. What is the role of the kube-scheduler? (Assigns unscheduled Pods to Nodes based on resource needs and constraints.)
13. What does the kube-controller-manager do? (Runs control loops — e.g., the Deployment, ReplicaSet, and Node controllers — that reconcile actual state to desired state.)
14. What runs on every worker Node? (kubelet, kube-proxy, and a container runtime.)
15. What is the kubelet's job? (Talks to the API server, ensures containers described in Pod specs are running on its Node.)
16. What is kube-proxy responsible for? (Maintains network rules on Nodes for Service-to-Pod traffic routing.)

**Pods, Labels, Namespaces (17–24)**
17. What is a Pod? (The smallest deployable unit — one or more containers sharing network and storage.)
18. Why would you put more than one container in a Pod? (Sidecar patterns — e.g., a log shipper or proxy tightly coupled to the main container's lifecycle.)
19. What are Labels used for? (Arbitrary key-value tags for identifying and selecting groups of objects.)
20. What is a Selector and how does it relate to Labels? (A query that matches objects by their Labels — e.g., how a Service finds its Pods.)
21. What is a Namespace and why use one?
22. What happens to Pods if their Node dies — does Kubernetes recreate them on the same Node?
23. What is the difference between a bare Pod and a Pod managed by a Deployment?
24. How do you view all Pods across all Namespaces in one command?

**Deployments, ReplicaSets, Rollouts (25–32)**
25. What is a ReplicaSet and what does it guarantee?
26. What does a Deployment add on top of a ReplicaSet?
27. How does a rolling update work under the hood?
28. How do you roll back a bad Deployment to the previous version?
29. What's the difference between `maxUnavailable` and `maxSurge` in a rolling update strategy?
30. How would you force a zero-downtime deployment even during a rollout of a breaking change?
31. What is a StatefulSet and when would you use it instead of a Deployment?
32. What is a DaemonSet used for?

**Services & Networking (33–40)**
33. What are the four main Service types and how do they differ?
34. How does a Service find which Pods to send traffic to?
35. What is a headless Service and when is it used?
36. Why can Pod IPs not be relied upon directly by other Pods?
37. What is the role of CoreDNS in a cluster?
38. Explain what happens, network-wise, from a request hitting an Ingress to reaching a Pod.
39. What's the difference between an Ingress resource and an Ingress Controller?
40. What is a NetworkPolicy and what does it control?

**kubectl, YAML, Config (41–48)**
41. What's the difference between imperative and declarative kubectl usage?
42. What does `kubectl apply` do differently from `kubectl create`?
43. How do you view the YAML definition of a live object?
44. What is a `kubeconfig` file and what does it contain?
45. How do you switch between multiple cluster contexts?
46. What does `kubectl diff` do and why is it useful before applying changes?
47. What is a dry run and how do you perform one?
48. How do you generate a YAML template quickly without writing it from scratch?

**ConfigMaps, Secrets, Storage (49–56)**
49. What is a ConfigMap used for, and what should never go in one?
50. What is a Secret, and how is it different from a ConfigMap at rest?
51. Are Kubernetes Secrets encrypted by default? What has to be configured for that to be true?
52. How do you inject a ConfigMap value into a Pod as an environment variable vs. as a mounted file?
53. What is a PersistentVolume (PV) and a PersistentVolumeClaim (PVC), and how do they relate?
54. What is a StorageClass and what does it enable?
55. What's the difference between `ReadWriteOnce`, `ReadWriteMany`, and `ReadOnlyMany` access modes?
56. What happens to a PVC's data if the Pod using it is deleted?

**Resource Management & Scheduling (57–62)**
57. What's the difference between a resource request and a resource limit?
58. What happens when a container exceeds its memory limit? Its CPU limit?
59. What is a LimitRange and what problem does it solve?
60. What is a ResourceQuota and at what scope does it apply?
61. What are Node affinity, Pod affinity, and taints/tolerations used for?
62. Why might a Pod stay in `Pending` state indefinitely, resource-wise?

**Probes & Health (63–68)**
63. What is the difference between a liveness probe, a readiness probe, and a startup probe?
64. What happens when a readiness probe fails, versus when a liveness probe fails?
65. Why is a startup probe useful for slow-booting applications?
66. What's a common misconfiguration mistake with liveness probes that can cause cascading outages?
67. What are the three probe mechanisms Kubernetes supports (HTTP GET, TCP socket, exec command)?
68. How do `initialDelaySeconds`, `periodSeconds`, and `failureThreshold` interact in a probe definition?

**Jobs, CronJobs, Autoscaling (69–76)**
69. What is a Job and how does it differ from a Deployment?
70. What does `completions` and `parallelism` control on a Job?
71. What is a CronJob and what Job semantics does it inherit?
72. What happens if a CronJob's previous run is still executing when the next scheduled time arrives?
73. What is the Horizontal Pod Autoscaler (HPA) and what metric does it use by default?
74. Why does HPA require resource *requests* to be set to function correctly?
75. What is the difference between HPA, VPA, and Cluster Autoscaler?
76. What is a stabilization window in HPA and why does it exist?

**Debugging & Observability (77–83)**
77. What's your step-by-step process when a Pod is stuck in `CrashLoopBackOff`?
78. How do you view logs from a previous (crashed) container instance?
79. What does `kubectl describe pod` show that `kubectl get pod` doesn't?
80. How do you get a shell inside a running container for live debugging?
81. What does `ImagePullBackOff` indicate, and what are its common root causes?
82. How would you diagnose a Pod that is `Running` but never becomes `Ready`?
83. What tool/command lets you see live resource usage per Pod/Node?

**Security & RBAC (84–89)**
84. What is RBAC and what are its four main objects (Role, ClusterRole, RoleBinding, ClusterRoleBinding)?
85. What's the difference between a Role and a ClusterRole?
86. What is a ServiceAccount and how is it different from a User in Kubernetes?
87. Why is running containers as root inside a Pod discouraged, and how do you prevent it?
88. What is a PodSecurityStandard/PodSecurityAdmission and what does it enforce?
89. What's the principle of least privilege as applied to a Kubernetes ServiceAccount?

**Helm (90–94)**
90. What problem does Helm solve that raw YAML manifests don't?
91. What are the three main parts of a Helm chart's structure?
92. How does `helm upgrade` differ from re-running `helm install`?
93. How do you roll back a bad Helm release?
94. What is a Helm values override file used for, and why keep separate files per environment?

**Production Practices & Capstone (95–100)**
95. What does it mean for an application to be "production-hardened" in a Kubernetes context — name at least five concrete practices.
96. What is a PodDisruptionBudget and what specific failure scenario does it prevent?
97. Why should Secrets never be committed to source control, even base64-encoded?
98. How would you design a rollback strategy that's safe even if the bad version already passed its readiness probe?
99. What would you check first if you were asked to cut a cluster's cloud cost by 30% without hurting reliability?
100. If you could only pick three Kubernetes practices to explain "why this project is production-ready" in an interview, which three would you pick and why?

---

## 4. One-Page Cheat Sheet

*(Full standalone version: `cheat-sheet.md` in this folder. Condensed here for convenience.)*

**Core object hierarchy:**
```
Cluster
 └── Node (worker machine)
      └── Pod (smallest deployable unit)
           └── Container (the actual running process)
```

**Essential kubectl verbs:**
| Verb | Purpose |
|---|---|
| `get` | List objects |
| `describe` | Detailed object info + events |
| `apply` | Declaratively create/update from YAML |
| `create` | Imperatively create an object |
| `delete` | Remove an object |
| `logs` | View container stdout/stderr |
| `exec` | Run a command inside a container |
| `scale` | Change replica count |
| `rollout` | Manage/inspect Deployment rollouts |
| `port-forward` | Tunnel a local port to a Pod/Service |

**Key object relationships:**
```
Deployment ──creates──▶ ReplicaSet ──creates──▶ Pod(s)
Service ──selects (via labels)──▶ Pod(s)
Ingress ──routes to──▶ Service ──▶ Pod(s)
PVC ──binds to──▶ PV ──backed by──▶ actual storage (disk/cloud volume)
ServiceAccount ──bound via RoleBinding──▶ Role ──grants──▶ permissions on Resources
HPA ──watches metrics of──▶ Deployment ──adjusts──▶ replica count
```

**"Always do this in production" — Top 10 (from Hour 19):**
1. Set resource `requests` **and** `limits` on every container.
2. Configure liveness, readiness, and (if needed) startup probes on every container.
3. Never store Secrets in plain YAML/ConfigMaps or commit them to git.
4. Run workloads under scoped ServiceAccounts — never `cluster-admin` for apps.
5. Define a `PodDisruptionBudget` for every multi-replica workload.
6. Use HPA with sane `min`/`max` replicas instead of hard-coding a fixed count.
7. Terminate TLS at Ingress; never serve plaintext HTTP to the internet.
8. Apply `NetworkPolicy` to restrict which Pods can talk to which.
9. Tag images with immutable versions — never deploy `:latest` to prod.
10. Package and version everything as a Helm chart (or equivalent) — no ad-hoc `kubectl apply` in prod.

---

## 5. Command Reference: Top 40 kubectl Commands

*(The full 100-command reference lives in `kubectl-100-commands.md` in this folder — these are the 40 you'll reach for daily.)*

**Cluster Info**
1. `kubectl cluster-info` — show control plane and DNS endpoint addresses.
2. `kubectl version --client` — check the installed kubectl client version.
3. `kubectl get nodes` — list all Nodes and their status.
4. `kubectl describe node <name>` — detailed Node info, capacity, and events.
5. `kubectl top nodes` — live CPU/memory usage per Node (needs metrics-server).

**Get / Describe**
6. `kubectl get pods` — list Pods in the current namespace.
7. `kubectl get pods -A` — list Pods across all namespaces.
8. `kubectl get pods -o wide` — list Pods with Node/IP columns included.
9. `kubectl get all` — list most common objects in the current namespace.
10. `kubectl describe pod <name>` — full detail + events for a Pod.
11. `kubectl get deployments` — list Deployments and their replica status.
12. `kubectl get svc` — list Services and their cluster IPs/ports.
13. `kubectl get ingress` — list Ingress resources and their hosts.
14. `kubectl get pvc` — list PersistentVolumeClaims and binding status.
15. `kubectl get events --sort-by=.lastTimestamp` — cluster events, newest last.

**Apply / Create / Delete**
16. `kubectl apply -f <file.yaml>` — declaratively create/update from a manifest.
17. `kubectl create -f <file.yaml>` — imperatively create from a manifest.
18. `kubectl delete -f <file.yaml>` — delete everything defined in a manifest.
19. `kubectl delete pod <name>` — delete a single Pod (it will be recreated if managed).
20. `kubectl apply -f <dir>/ --dry-run=client` — validate manifests without applying.
21. `kubectl diff -f <file.yaml>` — show what would change before applying.
22. `kubectl edit deployment <name>` — live-edit an object in your default editor.

**Debugging: Logs / Exec / Events**
23. `kubectl logs <pod>` — view container stdout/stderr.
24. `kubectl logs <pod> --previous` — view logs from the last crashed instance.
25. `kubectl logs -f <pod>` — stream/follow logs live.
26. `kubectl exec -it <pod> -- /bin/sh` — get an interactive shell inside a container.
27. `kubectl describe pod <name>` — see probe failures, image pull errors, and OOMKilled reasons.
28. `kubectl get events -n <namespace>` — namespace-scoped event stream.
29. `kubectl top pods` — live CPU/memory usage per Pod.

**Scaling / Rollout**
30. `kubectl scale deployment <name> --replicas=5` — manually set replica count.
31. `kubectl rollout status deployment <name>` — watch a rollout progress live.
32. `kubectl rollout history deployment <name>` — view revision history.
33. `kubectl rollout undo deployment <name>` — roll back to the previous revision.
34. `kubectl rollout restart deployment <name>` — force a rolling restart of all Pods.
35. `kubectl autoscale deployment <name> --min=2 --max=10 --cpu-percent=50` — create an HPA imperatively.

**Config / Context / Namespaces**
36. `kubectl config get-contexts` — list available cluster contexts.
37. `kubectl config use-context <name>` — switch active cluster context.
38. `kubectl config set-context --current --namespace=<ns>` — set default namespace for the current context.
39. `kubectl create namespace <name>` — create a new namespace.
40. `kubectl get namespaces` — list all namespaces in the cluster.

---

## 6. Production Deployment Checklist

*(Condensed from Hour 19; full version in `production-checklist.md`.)*

- [ ] Resource `requests` and `limits` set on every container.
- [ ] Liveness, readiness, and startup probes configured appropriately (not pointed at heavy/dependency-checking endpoints for liveness).
- [ ] Images pinned to immutable tags/digests — no `:latest` in production manifests.
- [ ] Secrets sourced from a secret manager, never committed to git, never in plain ConfigMaps.
- [ ] Each workload runs under its own scoped ServiceAccount with least-privilege RBAC.
- [ ] `NetworkPolicy` restricts traffic between tiers (e.g., only backend can reach the database).
- [ ] `PodDisruptionBudget` defined for every workload with more than one replica.
- [ ] HPA configured with sensible `minReplicas`/`maxReplicas`, not a static replica count.
- [ ] Ingress terminates TLS; HTTP traffic redirects to HTTPS.
- [ ] Pods expose metrics in a scrape-able format (Prometheus-compatible) even before a monitoring stack is wired up.
- [ ] Rolling update strategy configured (`maxUnavailable`/`maxSurge`) to avoid downtime during deploys.
- [ ] `helm rollback` (or equivalent) tested and confirmed to work before go-live, not just assumed.
- [ ] Persistent data (databases, uploads) backed by PVCs with a tested backup/restore process.
- [ ] Node-level resilience: PodAntiAffinity or topology spread constraints so replicas aren't all on one Node.
- [ ] ResourceQuota and LimitRange set at the namespace level to prevent one team/app from starving others.
- [ ] Alerting configured for the failure modes that actually matter (CrashLoopBackOff, high error rate, PVC nearly full) — not just "CPU high."
- [ ] Logs are shipped off-cluster (not relying solely on `kubectl logs`, which is lost when a Pod is gone).
- [ ] A documented, tested runbook exists for "what to check first" during an incident.
- [ ] Load-tested at expected peak traffic before launch, including a HPA scale-up/scale-down observation.
- [ ] Disaster-recovery basics validated: cluster/namespace can be rebuilt from Helm charts + backups alone, with no tribal knowledge required.

---

## 7. Troubleshooting Flowchart: "My Pod Isn't Working"

*(Full version with more branches in `troubleshooting-flowchart.md`; core decision tree below, drawing from Hour 14's debugging workflow.)*

```
                         ┌───────────────────────────┐
                         │   kubectl get pods         │
                         │   What is the STATUS?      │
                         └─────────────┬─────────────┘
                                       │
     ┌───────────────┬────────────────┼────────────────┬───────────────────┐
     ▼                ▼                ▼                ▼                   ▼
  Pending      ImagePullBackOff  CrashLoopBackOff  Running but        OOMKilled
     │                │                │           not READY          (seen via
     ▼                ▼                ▼                │             describe)
 kubectl          Check image      kubectl logs          ▼                 │
 describe pod     name/tag spelling <pod> --previous  kubectl              ▼
 → check Events    is correct.     → app crash reason  describe pod    Container used
     │             Check registry   in stack trace.    → check          more memory
     ▼             auth/imagePull  Common causes:      readinessProbe   than its limit.
 "Insufficient     Secrets exist    - bad config/env    config & recent  Fix: raise the
  cpu/memory"       and are         - missing           probe failures. memory limit if
  → not enough      attached to     dependency          Common cause:   genuinely needed,
  Node capacity.    the             (DB unreachable)     probe endpoint  OR find/fix a
  Fix: scale        ServiceAccount. - OOMKilled (see     too strict, or  memory leak if
  cluster/nodes,     Fix: correct   OOMKilled branch)    dependency      usage grows
  or lower           the image ref  - app bug on         (DB/cache)      unboundedly over
  requests.          or add         startup.             not yet         time. Check
     │               registry       Fix: fix the         reachable.      kubectl top pod
     ▼               credentials.   root cause, then      Fix: fix the   trend over time.
 "Unschedulable:        │           kubectl delete pod    dependency or      │
  node affinity/        ▼           to retry, or          loosen probe        ▼
  taints"          Retry pull       kubectl rollout        thresholds.    kubectl describe
  → check node      via kubectl     restart deployment.        │          pod → confirm
  labels/taints     delete pod          │                      ▼          "OOMKilled" in
  match Pod spec.   once fixed.         ▼                 kubectl logs    Last State reason.
                                    Still looping?         <pod> to see
                                    → exec into a          app-level
                                    debug Pod with the     readiness
                                    same image to          failures.
                                    reproduce manually.
```

---

## 8. Roadmap: Becoming a Kubernetes Expert After These 20 Hours

This course covers the 80/20 — the concepts that show up constantly. Real depth comes from sustained, staged practice:

**Month 1–2: Certification-driven depth (CKA)**
- Goal: pass the **Certified Kubernetes Administrator (CKA)** exam — it forces hands-on speed with the exact skills used daily (troubleshooting, RBAC, networking, cluster admin tasks).
- Free resources: [Kubernetes.io official docs](https://kubernetes.io/docs/home/), [killer.sh CKA simulator (free with registration)](https://killer.sh/), [Kubernetes the Hard Way (Kelsey Hightower)](https://github.com/kelseyhightower/kubernetes-the-hard-way).

**Month 2–3: Real managed Kubernetes on a cloud provider**
- Goal: run a real workload on **EKS, GKE, or AKS** — learn the cloud-specific glue (IAM ↔ RBAC, cloud load balancers, managed node pools, cluster autoscaler) that local Minikube/Kind can't teach.
- Free resources: [AWS EKS Workshop](https://www.eksworkshop.com/), [Google Cloud Skills Boost free GKE labs](https://www.cloudskillsboost.google/), most clouds offer a free tier/credits for a small cluster.

**Month 3–4: GitOps (ArgoCD / FluxCD)**
- Goal: stop running `kubectl apply`/`helm upgrade` by hand — manage cluster state declaratively from a git repo, with automatic drift detection and sync.
- Free resources: [ArgoCD official docs + "Getting Started"](https://argo-cd.readthedocs.io/), [FluxCD docs](https://fluxcd.io/flux/get-started/), CNCF's free "Argo Project" webinars on YouTube.

**Month 4–6: Service mesh + full observability deep-dive**
- Goal: add **Istio or Linkerd** for mTLS, traffic shaping, and canary releases; build a full **Prometheus + Grafana + OpenTelemetry** stack for metrics, dashboards, and distributed tracing.
- Free resources: [Istio "Getting Started"](https://istio.io/latest/docs/setup/getting-started/), [Linkerd docs (lighter-weight alternative)](https://linkerd.io/2/getting-started/), [Prometheus docs](https://prometheus.io/docs/introduction/overview/), [Grafana free Cloud tier](https://grafana.com/products/cloud/), [OpenTelemetry docs](https://opentelemetry.io/docs/).

**Month 6+: Contribute to a CNCF open-source project**
- Goal: pick a CNCF project you've actually used (Prometheus, ArgoCD, cert-manager, etc.) and fix a "good first issue" — this is where real, durable expertise comes from.
- Free resources: [CNCF Landscape](https://landscape.cncf.io/), [goodfirstissue.dev](https://goodfirstissue.dev/), each project's `CONTRIBUTING.md`.

*(Full staged version with more granular milestones: `roadmap.md` in this folder.)*

---

## 9. Mini-Projects List

Increasing difficulty, each reinforcing a different slice of the course:

1. **Deploy a 3-tier app** (frontend, backend, database) with plain manifests — no Helm yet. Reinforces Hours 3–9.
2. **Add health probes and resource limits** to the project above and deliberately break each one (bad probe path, too-low memory limit) to see the failure modes from Hour 11 and 10.
3. **Wire up an HPA** against a load-testing script (`k6`/`hey`) and watch it scale up and back down — Hour 13.
4. **Convert the 3-tier app into a Helm chart** with dev/staging/prod values overlays — Hour 17.
5. **Add RBAC and NetworkPolicies** so each tier has least-privilege access and the database is unreachable except from the backend — Hour 16.
6. **Set up a full observability stack** (Prometheus + Grafana) scraping your app's metrics endpoint and build one dashboard with request rate, error rate, and latency.
7. **Build a CI/CD pipeline with ArgoCD** that auto-syncs your Helm chart from a git repo on every merge to main, including automatic rollback on failed health checks.
8. **Implement a service mesh canary deployment** with Istio or Linkerd — ship a v2 of the backend to 10% of traffic, verify metrics look healthy, then shift to 100%.
9. **Chaos-test your cluster** — randomly kill Pods (`kube-monkey` or a simple cron script) and Nodes, and confirm PDBs/HPA/probes keep the app available throughout.
10. **Multi-cluster / disaster recovery drill** — back up your Helm chart + PVC data, tear down the entire cluster, and rebuild it from scratch on a fresh cluster within a set time budget (e.g., 30 minutes), proving true infrastructure-as-code discipline.

---

**Course complete.** You've gone from "why does Kubernetes exist" in Hour 1 to shipping a production-hardened, Helm-packaged, RBAC-scoped, autoscaling 3-tier application with a tested rollback story — and you now have 100 interview questions, a mock interview transcript, and a staged 6-month roadmap to keep going. The cheat sheet, command reference, production checklist, troubleshooting flowchart, and roadmap files in this folder are your quick-access companions going forward.
