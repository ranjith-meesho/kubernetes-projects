# 20-Day SRE & Observability Mastery Plan (150 Hours)

**Goal:** Become proficient in Kubernetes, Observability (VictoriaMetrics, OpenTelemetry), Logging, Debugging, and SRE.
**Budget:** 150 hours over 20 days = **7.5 focused hours/day**.
**Method:** 80/20 prioritization + meta-learning (active recall, spaced repetition, interleaving, deliberate practice).
**Platform:** KodeKloud (hands-on labs + playgrounds).

---

## How to Use This Plan (Meta-Learning Operating System)

The 80/20 rule drives topic selection: ~20% of concepts (control loops, the 3 pillars, PromQL, SLOs, the debugging method) explain ~80% of real SRE work. Every day is built around **doing**, not reading.

**The 5 fast-learning levers used every day:**
1. **Active recall** — Close the notes, answer the 50 questions from memory. Retrieval beats re-reading 3:1.
2. **Spaced repetition** — Re-test yesterday's questions each morning; put missed ones into an Anki deck (review daily).
3. **Interleaving** — Mix labs and topics rather than blocking; it feels harder but sticks better.
4. **Deliberate practice** — Target the edge of your ability. When a lab feels easy, break it on purpose and fix it.
5. **Feynman technique** — At day's end, explain the day's hardest concept out loud in plain language. Gaps = tomorrow's review.

**Daily learning ritual (baked into every schedule below):**
- **Prime (10 min):** Skim the day's topics and questions first — prime the brain for what to catch.
- **Learn → Do → Recall:** Never learn a concept without immediately labbing it, then recalling it.
- **The 10-minute rule for stuck states:** If blocked >10 min in a lab, read the solution, then redo from scratch.
- **End-of-day export:** Write 3 sentences on what you learned (generation effect) and 1 thing still fuzzy.

---

## Master Daily Schedule Template (5:00 AM start, 7-hour sleep)

| Time | Block | Purpose |
|------|-------|---------|
| 05:00–05:20 | Wake, hydrate, 10-min movement | Alertness spike |
| 05:20–05:50 | **Spaced review** — re-answer yesterday's toughest 10 questions | Retrieval + spacing |
| 05:50–06:00 | Prime today's topics & questions | Priming |
| 06:00–08:00 | **Deep Study Block 1** (theory + notes, phone off) | Peak focus window |
| 08:00–08:30 | Breakfast + walk | Consolidation |
| 08:30–10:30 | **Hands-on Lab Block 1** (KodeKloud) | Deliberate practice |
| 10:30–10:45 | Break (no screens) | Diffuse mode |
| 10:45–12:15 | **Deep Study Block 2** + targeted labs | Focus |
| 12:15–13:15 | Lunch + rest / short nap | Consolidation |
| 13:15–15:15 | **Hands-on Lab Block 2** (KodeKloud) | Practice/interleave |
| 15:15–15:30 | Break | Recovery |
| 15:30–16:30 | **Active recall — 50 questions** (closed notes) | Retrieval |
| 16:30–17:00 | Feynman explain + Anki carding + end-of-day export | Consolidation |
| 17:00–22:00 | Dinner, life, decompress (no study after 20:30) | Recovery |
| 22:00–05:00 | **Sleep (7 hours)** | Non-negotiable — sleep = memory |

**Study time = 2h + 2h + 1.5h + 2h = 7.5h/day.** Adjust break lengths, not sleep.

---

## Curriculum Map (80/20 Sequencing)

| Days | Block | Why it's in the vital 20% |
|------|-------|---------------------------|
| 1–6 | **Kubernetes core** | The substrate everything else runs and is observed on |
| 7–9 | **Metrics & VictoriaMetrics** | Metrics are the cheapest, highest-signal pillar |
| 10–11 | **OpenTelemetry** | The vendor-neutral standard for traces/metrics/logs |
| 12–13 | **Logging** | Event-level truth for debugging |
| 14–15 | **Debugging** | The skill that turns signals into fixes |
| 16–19 | **SRE practice** | SLOs, toil, incidents, reliability — the job itself |
| 20 | **Capstone** | Integrate the full stack end-to-end |

---

# DAY 1 — Kubernetes Fundamentals & Architecture

### Topics & Skills
- Why orchestration exists; containers vs VMs; K8s vs Docker
- Cluster architecture: control plane (kube-apiserver, etcd, scheduler, controller-manager, cloud-controller-manager) and node components (kubelet, kube-proxy, container runtime)
- The **declarative model** and the **reconciliation control loop** (desired vs actual state) — the single most important K8s mental model
- `kubectl` basics: `get`, `describe`, `explain`, `-o yaml/wide`, contexts, namespaces
- Anatomy of a manifest: apiVersion, kind, metadata, spec, status
- The API server as the single source of truth; everything talks to it

### KodeKloud Labs
- **Kubernetes for the Absolute Beginners** — sections 1–2 (Core Concepts intro)
- **CKA course**: "Cluster Architecture" lab set
- **kubectl playground** — free-run `kubectl get componentstatuses`, explore `--all-namespaces`

### 50 Questions & Answers
1. **What problem does Kubernetes solve?** Automated deployment, scaling, healing, and management of containerized apps across a cluster.
2. **Container vs VM?** Containers share the host kernel and isolate at the process level; VMs virtualize hardware with a full guest OS — containers are lighter and faster.
3. **Is Kubernetes a replacement for Docker?** No; it orchestrates containers built by runtimes. Docker Engine's shim was deprecated (dockershim), but Docker images still run fine (OCI).
4. **Two halves of a cluster?** Control plane (brains) and worker nodes (muscle).
5. **What does kube-apiserver do?** Exposes the REST API, validates/authenticates requests, and is the only component that talks to etcd.
6. **What is etcd?** A distributed, consistent key-value store holding all cluster state.
7. **Role of the scheduler?** Assigns unscheduled Pods to nodes based on resources, constraints, affinity, taints.
8. **Role of controller-manager?** Runs controllers (Node, ReplicaSet, Deployment, etc.) that drive actual state toward desired state.
9. **What is kubelet?** Node agent that ensures containers described in PodSpecs are running and healthy.
10. **What is kube-proxy?** Maintains network rules on nodes to implement Service networking.
11. **What is the container runtime?** Software that runs containers (containerd, CRI-O) via the CRI interface.
12. **Declarative vs imperative?** Declarative = describe desired end state (YAML `apply`); imperative = issue step commands (`kubectl create/run`). K8s prefers declarative.
13. **What is the reconciliation loop?** Controllers continuously compare desired vs actual state and act to close the gap.
14. **What is desired state?** The state you declare in manifests, stored in etcd.
15. **Where is cluster state stored?** In etcd.
16. **Four required top-level manifest fields?** apiVersion, kind, metadata, spec.
17. **What does `status` represent?** The observed/actual state, populated by the system (not you).
18. **`kubectl apply` vs `create`?** `apply` is declarative and idempotent (merges/updates); `create` fails if the object exists.
19. **How to see all resource types?** `kubectl api-resources`.
20. **How to learn a field's schema?** `kubectl explain <resource>.spec...`.
21. **What is a namespace?** A virtual cluster partition for scoping and isolating resources.
22. **Default namespaces?** default, kube-system, kube-public, kube-node-lease.
23. **How to set a namespace for all commands?** `kubectl config set-context --current --namespace=<ns>`.
24. **What is a kubeconfig context?** A named bundle of cluster + user + namespace.
25. **How does the apiserver authenticate?** Certificates, bearer tokens, or OIDC, then authorization (RBAC) and admission.
26. **What are admission controllers?** Plugins that intercept API requests after authz to validate/mutate objects.
27. **Why is the API server stateless?** All state lives in etcd; apiservers can scale horizontally.
28. **What talks directly to etcd?** Only kube-apiserver.
29. **What is a control loop's period?** Continuous/event-driven watch, not a fixed poll.
30. **What is cloud-controller-manager?** Integrates cluster with cloud provider APIs (load balancers, nodes, routes).
31. **What is a node's status made of?** Conditions (Ready, MemoryPressure...), capacity, allocatable, addresses.
32. **`kubectl get -o wide`?** Adds extra columns like node, IP.
33. **`kubectl describe` vs `get -o yaml`?** `describe` gives a human-readable summary incl. events; `-o yaml` gives raw object.
34. **How to see cluster events?** `kubectl get events --sort-by=.lastTimestamp`.
35. **What is the CRI?** Container Runtime Interface — gRPC contract between kubelet and runtime.
36. **CNI?** Container Network Interface — plugin standard for pod networking.
37. **CSI?** Container Storage Interface — plugin standard for storage.
38. **What ensures HA of the control plane?** Multiple apiservers + a quorum (odd number) of etcd members.
39. **etcd quorum for 3 nodes?** 2 (tolerates 1 failure).
40. **What is a manifest's `metadata.labels` for?** Key/value tags used by selectors to group objects.
41. **Annotations vs labels?** Labels are for selection/queries; annotations hold non-identifying metadata.
42. **How does self-healing work?** Controllers recreate/reschedule resources when actual drifts from desired.
43. **Is the scheduler responsible for running the pod?** No — it only binds pod to node; kubelet runs it.
44. **What port does the apiserver typically serve?** 6443 (HTTPS).
45. **How to check control-plane component health?** `kubectl get componentstatuses` / check static pods in kube-system.
46. **What are static pods?** Pods managed directly by kubelet from a manifest dir, not the apiserver.
47. **What runs the control plane in kubeadm clusters?** Static pods on control-plane nodes.
48. **What is idempotency and why does it matter?** Same operation yields same result; enables safe re-apply and reconciliation.
49. **What's the smallest deployable unit?** A Pod (not a container).
50. **One sentence: what is Kubernetes?** A declarative, self-healing control system that keeps your containerized workloads matching the state you declared.

### Day 1 Schedule (fills the master template)
- **Study 1 (06:00–08:00):** Architecture + control loop; draw the diagram from memory 3×.
- **Lab 1 (08:30–10:30):** KodeKloud Cluster Architecture + kubectl basics.
- **Study 2 (10:45–12:15):** Manifest anatomy, `explain`, namespaces.
- **Lab 2 (13:15–15:15):** Explore a running cluster; `describe` everything in kube-system.
- **Recall (15:30–16:30):** 50 questions closed-book.

### Fast-Learning Tips (Day 1)
- Draw the architecture diagram from memory until you can do it in <2 min — it's your map for 6 days.
- Say the reconciliation loop out loud: "desired → observe → diff → act → repeat."
- Add every kubectl flag you use to a personal cheat-sheet file; grep it later.

---

# DAY 2 — Pods, Controllers & Workloads

### Topics & Skills
- Pod lifecycle & phases; multi-container pods; init containers; sidecar pattern
- ReplicaSets and how Deployments manage them
- **Deployments**: rolling updates, rollbacks, `maxSurge`/`maxUnavailable`, revision history
- DaemonSets, StatefulSets (identity, stable storage, ordered rollout), Jobs & CronJobs
- Labels, selectors, and how controllers own pods
- `kubectl` workload ops: scale, set image, rollout status/undo

### KodeKloud Labs
- **CKA/Beginners:** Pods, ReplicaSets, Deployments labs
- **Rolling Updates & Rollbacks** lab
- **DaemonSets, Jobs, CronJobs** labs; **StatefulSets** lab

### 50 Questions & Answers
1. **What is a Pod?** The smallest deployable unit — one or more containers sharing network and storage.
2. **Do containers in a pod share an IP?** Yes — same network namespace and localhost.
3. **Pod phases?** Pending, Running, Succeeded, Failed, Unknown.
4. **What is an init container?** Runs to completion before app containers start; used for setup/wait logic.
5. **Sidecar pattern?** A helper container alongside the main app (e.g., log shipper, proxy).
6. **Why rarely create bare Pods?** No self-healing/rescheduling; use a controller.
7. **What does a ReplicaSet guarantee?** A specified number of identical pod replicas are running.
8. **How does a ReplicaSet find its pods?** Via a label selector.
9. **Deployment vs ReplicaSet?** Deployment manages ReplicaSets to enable declarative updates/rollbacks.
10. **What happens on a Deployment image change?** A new ReplicaSet is created and scaled up while the old scales down (rolling update).
11. **maxSurge?** Max pods above desired count allowed during update.
12. **maxUnavailable?** Max pods that can be unavailable during update.
13. **How to roll back?** `kubectl rollout undo deployment/<name>`.
14. **How to check rollout progress?** `kubectl rollout status deployment/<name>`.
15. **How to pause/resume a rollout?** `kubectl rollout pause/resume`.
16. **What stores rollout history?** Old ReplicaSets (limited by `revisionHistoryLimit`).
17. **Recreate strategy?** Kills all old pods before creating new — causes downtime.
18. **What is a DaemonSet?** Ensures a copy of a pod runs on every (or selected) node.
19. **DaemonSet use cases?** Node agents: log collectors, CNI, node exporters.
20. **What is a StatefulSet?** Manages stateful apps with stable identities and storage.
21. **StatefulSet pod naming?** Ordinal and stable: `name-0`, `name-1`...
22. **StatefulSet storage?** Each pod gets its own PVC via volumeClaimTemplates.
23. **StatefulSet rollout order?** Ordered, one at a time (by default).
24. **What is a headless Service?** `clusterIP: None` — gives stable DNS per pod for StatefulSets.
25. **What is a Job?** Runs pods to successful completion (batch work).
26. **completions vs parallelism?** completions = total successful pods needed; parallelism = concurrent pods.
27. **What is a CronJob?** Creates Jobs on a schedule (cron syntax).
28. **CronJob concurrencyPolicy?** Allow/Forbid/Replace overlapping runs.
29. **How to scale a Deployment?** `kubectl scale deployment/<name> --replicas=N`.
30. **How to update image imperatively?** `kubectl set image deployment/<name> c=img:tag`.
31. **What owns a pod?** An ownerReference to its controller (e.g., ReplicaSet).
32. **What happens if you delete a pod owned by a ReplicaSet?** A replacement is created.
33. **What if you delete the ReplicaSet?** Pods are garbage-collected (unless orphaned).
34. **restartPolicy for Deployments?** Always.
35. **restartPolicy for Jobs?** OnFailure or Never.
36. **What is a pod's terminationGracePeriod?** Time given to shut down before SIGKILL (default 30s).
37. **What signal is sent first on termination?** SIGTERM.
38. **What is a preStop hook?** A container lifecycle hook run before SIGTERM/termination.
39. **How does a rolling update avoid downtime?** Combined with readiness probes and surge/unavailable limits.
40. **selector immutability?** A Deployment's selector is immutable after creation.
41. **What is `revisionHistoryLimit` default?** 10.
42. **How to see ReplicaSets behind a Deployment?** `kubectl get rs -l app=<label>`.
43. **Job backoffLimit?** Number of retries before marking a Job failed.
44. **CronJob startingDeadlineSeconds?** Deadline to start if missed schedule.
45. **Difference: DaemonSet vs Deployment scheduling?** DaemonSet targets nodes, not a replica count.
46. **How to run a one-off debug pod?** `kubectl run tmp --image=busybox -it --rm -- sh`.
47. **What is a pod template?** The spec used by controllers to stamp out pods.
48. **What triggers a new ReplicaSet?** A change to the pod template (e.g., image, env).
49. **How to limit CronJob history?** successfulJobsHistoryLimit / failedJobsHistoryLimit.
50. **When StatefulSet vs Deployment?** StatefulSet for stable identity/storage (DBs, queues); Deployment for stateless apps.

### Day 2 Schedule
- **Study 1:** Pods, init/sidecar, lifecycle. **Lab 1:** Pods + ReplicaSets + Deployments.
- **Study 2:** Rolling updates, StatefulSets, Jobs. **Lab 2:** Rollout/rollback + CronJob + StatefulSet.
- **Recall:** 50 questions; **Feynman:** explain a rolling update to an imaginary teammate.

### Fast-Learning Tips (Day 2)
- Deliberately break a rollout (bad image tag) and watch it stall — read the events; this is real-world muscle.
- Interleave: alternate between creating a Deployment and a StatefulSet so you *feel* the difference.

---

# DAY 3 — Services, Networking & Ingress

### Topics & Skills
- Pod networking model (every pod gets a routable IP; flat network)
- **Services**: ClusterIP, NodePort, LoadBalancer, ExternalName; Endpoints/EndpointSlices
- Service discovery via cluster DNS (CoreDNS); FQDN format
- **Ingress** + Ingress controllers; path/host routing; TLS
- Network basics: kube-proxy modes (iptables/IPVS)
- Intro to NetworkPolicies (default-allow → restrict)

### KodeKloud Labs
- **Services** labs (ClusterIP/NodePort)
- **Networking** section: CoreDNS, kube-proxy
- **Ingress** labs; **Network Policies** lab

### 50 Questions & Answers
1. **Why do we need Services?** Pods are ephemeral with changing IPs; Services give a stable virtual endpoint.
2. **What is a ClusterIP Service?** Default type; a stable in-cluster virtual IP load-balancing to pods.
3. **NodePort?** Exposes the Service on a static port on every node (30000–32767).
4. **LoadBalancer?** Provisions an external cloud LB pointing to the Service.
5. **ExternalName?** Maps a Service to an external DNS name via CNAME.
6. **How does a Service select pods?** Via a label selector matching pod labels.
7. **What are Endpoints?** The list of pod IP:port backing a Service.
8. **EndpointSlices?** Scalable replacement for Endpoints, sharded for large sets.
9. **What is a headless Service?** clusterIP: None; returns pod IPs directly via DNS.
10. **Cluster DNS provider?** CoreDNS.
11. **Service FQDN format?** `<svc>.<namespace>.svc.cluster.local`.
12. **How does kube-proxy implement Services?** Programs iptables/IPVS rules to DNAT to pod IPs.
13. **iptables vs IPVS mode?** IPVS scales better with many services and offers more LB algorithms.
14. **What is targetPort?** The pod's container port the Service forwards to.
15. **port vs targetPort?** port = Service's port; targetPort = pod's port.
16. **What is a session affinity option?** `sessionAffinity: ClientIP`.
17. **Why can't NodePort alone do host routing?** It's L4; use Ingress for L7 host/path routing.
18. **What is Ingress?** An API object defining L7 HTTP(S) routing rules to Services.
19. **Does Ingress work without a controller?** No — you need an Ingress controller (nginx, Traefik, etc.).
20. **Ingress path types?** Exact, Prefix, ImplementationSpecific.
21. **How does Ingress do TLS?** References a TLS Secret with cert/key per host.
22. **Host-based routing example?** Route `api.example.com` → api-svc, `web.example.com` → web-svc.
23. **What is a default backend?** Where unmatched requests go.
24. **What load-balances to Ingress itself?** An external LB/NodePort in front of the controller.
25. **Pod-to-pod communication requirement?** All pods can reach each other without NAT (flat network).
26. **Who implements the pod network?** The CNI plugin (Calico, Cilium, Flannel...).
27. **Default NetworkPolicy behavior?** All traffic allowed until a policy selects the pod.
28. **What happens once a pod is selected by a policy?** Only explicitly allowed traffic is permitted (default-deny for that direction).
29. **Ingress vs egress in NetworkPolicy?** Ingress = incoming to pod; egress = outgoing from pod.
30. **How to allow only namespace-internal traffic?** namespaceSelector in the policy.
31. **How to test DNS from a pod?** `nslookup <svc>` from a debug pod.
32. **What resolves `kubernetes.default`?** The API server Service via CoreDNS.
33. **How to expose a Deployment quickly?** `kubectl expose deployment <name> --port=80 --target-port=8080`.
34. **Can a Service exist without pods?** Yes (empty Endpoints) or manually managed Endpoints.
35. **What is ExternalTrafficPolicy: Local?** Preserves client source IP, routes only to node-local pods.
36. **Why use IPVS for large clusters?** O(1) rule lookup vs iptables' sequential chains.
37. **What is a ClusterIP range?** The service CIDR configured on the apiserver.
38. **How do you reach a Service across namespaces?** Use the FQDN with the namespace.
39. **What is CoreDNS configured by?** A ConfigMap (Corefile).
40. **What is an Ingress class?** Selects which controller handles the Ingress (`ingressClassName`).
41. **Can one Ingress front many Services?** Yes, via multiple rules/paths.
42. **What L4 protocols do Services support?** TCP, UDP, SCTP.
43. **NodePort accessibility?** Any node IP + the allocated port.
44. **Why prefer LoadBalancer over NodePort in cloud?** Managed external IP, health checks, no port juggling.
45. **What are readiness probes' role in Services?** Only Ready pods receive Service traffic.
46. **What removes a pod from Endpoints?** Failing readiness or termination.
47. **How to see a Service's backends?** `kubectl get endpoints <svc>` / `kubectl get endpointslices`.
48. **What is Gateway API?** A newer, more expressive successor to Ingress for L4/L7 routing.
49. **Can NetworkPolicies block DNS?** Yes if egress to kube-dns isn't allowed — a common gotcha.
50. **One-liner: Service vs Ingress?** Service = stable L4 endpoint inside the cluster; Ingress = L7 HTTP router exposing Services externally.

### Day 3 Schedule
- **Study 1:** Service types + DNS. **Lab 1:** ClusterIP/NodePort + DNS resolution.
- **Study 2:** Ingress + NetworkPolicy. **Lab 2:** Ingress host/path + a default-deny policy.
- **Recall:** 50 questions; **Feynman:** trace a request from browser → Ingress → Service → Pod.

### Fast-Learning Tips (Day 3)
- Trace one packet end-to-end and narrate every hop; networking sticks when it's a story, not a list.
- Break DNS on purpose (delete CoreDNS pods) and observe failures — you'll never forget how much depends on it.

---

# DAY 4 — Configuration, Secrets & Storage

### Topics & Skills
- ConfigMaps: env vars, mounted files, `envFrom`
- Secrets: types, base64 (not encryption!), mounting, encryption-at-rest concept
- Volumes: emptyDir, hostPath; the difference between ephemeral and persistent
- **PersistentVolumes (PV), PersistentVolumeClaims (PVC), StorageClasses**, dynamic provisioning
- Access modes (RWO/ROX/RWX), reclaim policies (Retain/Delete)
- Env injection via downward API

### KodeKloud Labs
- **ConfigMaps** and **Secrets** labs
- **Volumes / Persistent Volumes / PVC** labs
- **StorageClass & dynamic provisioning** lab

### 50 Questions & Answers
1. **What is a ConfigMap?** An object storing non-secret config as key-value pairs.
2. **Three ways to consume a ConfigMap?** Env vars, `envFrom`, and mounted volume files.
3. **What is a Secret?** An object for sensitive data, stored base64-encoded.
4. **Is base64 encryption?** No — it's encoding; anyone can decode it.
5. **How to encrypt Secrets at rest?** Enable etcd encryption (EncryptionConfiguration) with a provider (e.g., KMS).
6. **Secret types?** Opaque, kubernetes.io/dockerconfigjson, tls, service-account-token, etc.
7. **How to mount a Secret as files?** As a volume; each key becomes a file.
8. **What is emptyDir?** An ephemeral volume tied to the pod's lifetime.
9. **hostPath risks?** Ties pod to a node's filesystem; security and portability concerns.
10. **What is a PersistentVolume?** A cluster storage resource provisioned by admin or dynamically.
11. **What is a PVC?** A user's request for storage (size + access mode) that binds to a PV.
12. **What is a StorageClass?** A template describing how to dynamically provision PVs.
13. **Dynamic vs static provisioning?** Dynamic auto-creates PVs via StorageClass; static uses pre-created PVs.
14. **RWO?** ReadWriteOnce — mounted read-write by one node.
15. **ROX?** ReadOnlyMany — read-only by many nodes.
16. **RWX?** ReadWriteMany — read-write by many nodes (needs supporting backend).
17. **RWOP?** ReadWriteOncePod — single pod exclusive access.
18. **Reclaim policy Retain?** PV kept after PVC deletion (manual cleanup).
19. **Reclaim policy Delete?** Underlying storage deleted with PVC.
20. **How does a PVC bind to a PV?** By matching size, access mode, and StorageClass.
21. **What is a volumeMount?** Where a volume appears inside a container's filesystem.
22. **What's the difference between volumes and volumeMounts?** volumes declare storage; volumeMounts attach them to a container path.
23. **Downward API?** Exposes pod/container metadata (name, namespace, labels) as env/files.
24. **How to update a ConfigMap in a running pod?** Mounted files auto-update (eventually); env vars require restart.
25. **How to reference one ConfigMap key as env?** `valueFrom.configMapKeyRef`.
26. **How to reference a Secret key as env?** `valueFrom.secretKeyRef`.
27. **What is `envFrom`?** Injects all keys of a ConfigMap/Secret as env vars.
28. **How to make a Secret optional?** `optional: true` in the reference.
29. **What is a projected volume?** Combines Secrets, ConfigMaps, downward API, tokens into one mount.
30. **subPath use?** Mount a single file/dir from a volume without hiding the whole directory.
31. **What is a CSI driver?** Vendor plugin implementing storage provisioning via the CSI standard.
32. **What is volumeBindingMode: WaitForFirstConsumer?** Delays PV binding until a pod using the PVC is scheduled (topology-aware).
33. **Default StorageClass?** Annotated as default; used when PVC omits storageClassName.
34. **How to expand a PVC?** Set `allowVolumeExpansion: true` in StorageClass and edit PVC size.
35. **What is an ephemeral inline volume?** A volume defined inline in the pod for short-lived data.
36. **Why not store DB data in emptyDir?** It's deleted when the pod dies.
37. **How to consume a TLS Secret in Ingress?** Reference it in `spec.tls`.
38. **How to create a Secret from a file?** `kubectl create secret generic x --from-file=path`.
39. **How to create a ConfigMap from literals?** `kubectl create configmap x --from-literal=k=v`.
40. **Are Secrets namespaced?** Yes — same namespace as the consuming pod.
41. **What is immutability for ConfigMaps/Secrets?** `immutable: true` prevents updates and improves performance.
42. **Where are mounted Secrets stored on the node?** In tmpfs (memory), not disk.
43. **Access mode enforcement?** Depends on the storage backend; K8s doesn't enforce content.
44. **What binds first: PV or PVC?** They bind to each other; the PVC drives the request.
45. **Can multiple PVCs bind to one PV?** No — one-to-one binding.
46. **What is `persistentVolumeReclaimPolicy: Recycle`?** Deprecated scrub-and-reuse policy.
47. **How does a StatefulSet get storage?** volumeClaimTemplates create a PVC per pod.
48. **What happens to a StatefulSet PVC when the pod is deleted?** It persists (retained) by default.
49. **Difference: ConfigMap vs Secret in etcd?** Both in etcd; only Secrets are (optionally) encrypted and base64-encoded.
50. **One-liner storage flow?** Pod → PVC (request) → StorageClass (provisioner) → PV (actual storage) → backend disk.

### Day 4 Schedule
- **Study 1:** ConfigMaps + Secrets. **Lab 1:** Inject config three ways; mount a Secret.
- **Study 2:** PV/PVC/StorageClass. **Lab 2:** Dynamic provisioning + attach to a StatefulSet.
- **Recall:** 50 questions; **Feynman:** explain the PVC→PV binding dance.

### Fast-Learning Tips (Day 4)
- Decode a Secret with `base64 -d` to *viscerally* learn it isn't encryption.
- Draw the storage object graph (Pod→PVC→PV→SC→disk) once; reuse it whenever storage confuses you.

---

# DAY 5 — Scheduling, Resources & Autoscaling

### Topics & Skills
- Resource requests/limits; QoS classes (Guaranteed, Burstable, BestEffort)
- Scheduling controls: nodeSelector, node/pod affinity & anti-affinity, taints & tolerations, topology spread
- Eviction, OOMKills, and the scheduler's filter/score phases
- **HPA** (Horizontal Pod Autoscaler), metrics-server, **VPA** concept, **Cluster Autoscaler** concept
- PodDisruptionBudgets and priority/preemption

### KodeKloud Labs
- **Scheduling** section: manual scheduling, labels, taints/tolerations, affinity
- **Resource limits** lab
- **HPA / metrics-server** lab

### 50 Questions & Answers
1. **What is a resource request?** The minimum guaranteed CPU/memory used for scheduling decisions.
2. **What is a resource limit?** The hard cap a container may use.
3. **What happens if a container exceeds its memory limit?** It's OOMKilled.
4. **What happens if it exceeds CPU limit?** It's throttled (not killed).
5. **QoS class Guaranteed?** requests == limits for all resources.
6. **QoS class Burstable?** Has requests but not equal-to limits.
7. **QoS class BestEffort?** No requests or limits set.
8. **Which QoS is evicted first?** BestEffort, then Burstable, then Guaranteed.
9. **How is CPU measured?** In cores/millicores (500m = 0.5 CPU).
10. **How is memory measured?** Bytes with suffixes (Mi, Gi).
11. **What is nodeSelector?** Simplest node constraint via label matching.
12. **Node affinity vs nodeSelector?** Affinity is more expressive (required/preferred, operators).
13. **requiredDuringScheduling...?** Hard rule; pod won't schedule if unmet.
14. **preferredDuringScheduling...?** Soft preference; scheduler tries but won't block.
15. **Pod affinity?** Schedule pods near other pods (topology).
16. **Pod anti-affinity?** Keep pods apart (e.g., spread replicas across nodes).
17. **What is a taint?** A node marking that repels pods lacking a matching toleration.
18. **What is a toleration?** A pod's permission to schedule onto a tainted node.
19. **Taint effects?** NoSchedule, PreferNoSchedule, NoExecute.
20. **NoExecute effect?** Evicts already-running pods without the toleration.
21. **Why taint control-plane nodes?** To keep workloads off them by default.
22. **Topology spread constraints?** Evenly distribute pods across zones/nodes (maxSkew).
23. **Scheduler phases?** Filtering (feasible nodes) then Scoring (best node).
24. **What is preemption?** Evicting lower-priority pods to fit a higher-priority pending pod.
25. **What is a PriorityClass?** Assigns scheduling priority to pods.
26. **What is a PodDisruptionBudget?** Limits voluntary disruptions (min available / max unavailable).
27. **Does a PDB protect against node crashes?** No — only voluntary disruptions (drains, upgrades).
28. **What is metrics-server?** Cluster-wide resource metrics source for HPA and `kubectl top`.
29. **What does HPA scale?** Replica count based on observed metrics vs target.
30. **HPA default metric?** CPU utilization (can use memory/custom/external).
31. **HPA formula?** desiredReplicas = ceil(current × currentMetric / targetMetric).
32. **What is VPA?** Vertical Pod Autoscaler — adjusts requests/limits (not replica count).
33. **Can HPA and VPA both target CPU?** Not simultaneously on the same metric — they conflict.
34. **Cluster Autoscaler?** Adds/removes nodes based on pending pods and utilization.
35. **What blocks node scale-down?** Pods without controllers, PDBs, local storage, restrictive affinity.
36. **How to see node resource usage?** `kubectl top nodes` / `kubectl describe node`.
37. **What is allocatable vs capacity?** Allocatable = capacity minus system-reserved.
38. **What triggers node-pressure eviction?** Memory/disk/PID pressure thresholds on kubelet.
39. **What is `kubectl cordon`?** Marks a node unschedulable.
40. **What is `kubectl drain`?** Evicts pods and cordons a node (for maintenance).
41. **What honors a PDB during drain?** The eviction API.
42. **How to schedule a pod to a specific node manually?** `nodeName` field (bypasses scheduler).
43. **Why avoid setting only limits, no requests?** K8s sets requests=limits, may over-reserve or mislead QoS.
44. **What is CPU throttling's symptom?** Latency spikes without OOM.
45. **How to right-size requests?** Observe real usage (metrics) and set requests near p50–p90.
46. **What is a stabilization window in HPA?** Delay to prevent flapping on scale-down.
47. **Custom metrics for HPA source?** Custom Metrics API (e.g., Prometheus Adapter).
48. **External metrics example?** Queue length from a cloud service.
49. **Why can over-committing be fine?** Requests reserve; actual usage may be lower, improving density.
50. **One-liner scaling stack?** metrics-server → HPA (pods) + Cluster Autoscaler (nodes); VPA tunes per-pod resources.

### Day 5 Schedule
- **Study 1:** Requests/limits/QoS + affinity/taints. **Lab 1:** Taints/tolerations + affinity scheduling.
- **Study 2:** Autoscaling stack. **Lab 2:** Deploy metrics-server + HPA; load-test to trigger scale.
- **Recall:** 50 questions; **Feynman:** explain why a pod is Pending.

### Fast-Learning Tips (Day 5)
- Force a `Pending` pod (impossible affinity) and diagnose via `describe` events — scheduling clicks when you debug it.
- Load-test an HPA and watch replicas climb; seeing autoscaling live beats reading the formula.

---

# DAY 6 — Security (RBAC), Health & Troubleshooting

### Topics & Skills
- Authn vs authz; **RBAC**: Roles, ClusterRoles, RoleBindings, ClusterRoleBindings
- ServiceAccounts and token projection
- **Probes**: liveness, readiness, startup — the reliability foundation
- SecurityContext, Pod Security Standards (privileged/baseline/restricted) concept
- **Troubleshooting workflow**: `describe`, `logs`, `events`, `exec`, `kubectl debug`, crashloop diagnosis
- Consolidation + mini mock (CKA-style) for the week

### KodeKloud Labs
- **RBAC** and **ServiceAccounts** labs
- **Readiness/Liveness Probes** lab
- **Troubleshooting** section (application & control-plane failure labs)

### 50 Questions & Answers
1. **Authn vs authz?** Authn = who you are; authz = what you're allowed to do.
2. **What is RBAC?** Role-Based Access Control — permissions via roles bound to subjects.
3. **Role vs ClusterRole?** Role is namespaced; ClusterRole is cluster-wide/cluster-scoped resources.
4. **RoleBinding vs ClusterRoleBinding?** RoleBinding grants within a namespace; ClusterRoleBinding grants cluster-wide.
5. **Can a RoleBinding reference a ClusterRole?** Yes — grants that ClusterRole's permissions in one namespace.
6. **What are RBAC verbs?** get, list, watch, create, update, patch, delete, etc.
7. **What is a subject?** A user, group, or ServiceAccount a binding grants to.
8. **What is a ServiceAccount?** An identity for processes in pods to call the API.
9. **Default ServiceAccount?** Each namespace has one; auto-mounted unless disabled.
10. **How are SA tokens delivered now?** Projected, short-lived, auto-rotated tokens (TokenRequest API).
11. **How to test your permissions?** `kubectl auth can-i <verb> <resource>`.
12. **Principle of least privilege?** Grant only the minimum permissions needed.
13. **What is a liveness probe?** Checks if a container is alive; failure restarts it.
14. **Readiness probe?** Checks if a container can serve traffic; failure removes it from Endpoints.
15. **Startup probe?** Guards slow-starting apps; disables liveness/readiness until it passes.
16. **Probe types?** httpGet, tcpSocket, exec, grpc.
17. **Key probe params?** initialDelaySeconds, periodSeconds, timeoutSeconds, failureThreshold, successThreshold.
18. **Liveness misconfiguration risk?** Too-aggressive probe causes restart loops.
19. **Readiness during rollout role?** Ensures new pods get traffic only when ready → zero-downtime.
20. **What is CrashLoopBackOff?** Container repeatedly crashing; kubelet backs off restarts exponentially.
21. **First step to diagnose CrashLoopBackOff?** `kubectl logs <pod> --previous`.
22. **ImagePullBackOff cause?** Bad image name/tag, registry auth, or network.
23. **How to see why a pod won't schedule?** `kubectl describe pod` → Events.
24. **How to get logs of a multi-container pod?** `kubectl logs <pod> -c <container>`.
25. **How to exec into a container?** `kubectl exec -it <pod> -- sh`.
26. **What is `kubectl debug`?** Attaches an ephemeral debug container to a running pod.
27. **Why use ephemeral containers?** Debug distroless/minimal images lacking a shell.
28. **What is a SecurityContext?** Pod/container security settings (runAsUser, capabilities, readOnlyRootFilesystem).
29. **What is runAsNonRoot?** Prevents container from running as root.
30. **What are Linux capabilities?** Fine-grained root privileges you can add/drop.
31. **Pod Security Standards levels?** Privileged, Baseline, Restricted.
32. **What replaced PodSecurityPolicy?** Pod Security Admission (namespace labels).
33. **How to drop all capabilities?** `capabilities.drop: ["ALL"]`.
34. **What is readOnlyRootFilesystem?** Makes the container FS read-only, reducing attack surface.
35. **How to restrict a SA's API access?** Bind it to a minimal Role.
36. **What is automountServiceAccountToken: false?** Disables auto-mounting SA token into pods.
37. **How to see events cluster-wide?** `kubectl get events -A --sort-by=.lastTimestamp`.
38. **What does `kubectl describe` add over logs?** State, restarts, probe results, events, mounts.
39. **How to find a pod's node?** `kubectl get pod -o wide`.
40. **How to check kubelet on a node?** `systemctl status kubelet` / journalctl.
41. **Where do control-plane static pod manifests live?** `/etc/kubernetes/manifests`.
42. **Common cause of NotReady node?** kubelet/CNI/container-runtime failure.
43. **How to check DNS issues?** Exec a pod and nslookup a Service.
44. **What is an OOMKilled exit?** Exit code 137 (SIGKILL from OOM).
45. **How to see resource usage of pods?** `kubectl top pods`.
46. **What causes readiness to flap?** Overloaded app or too-strict thresholds.
47. **How to safely test RBAC as a SA?** `kubectl auth can-i ... --as=system:serviceaccount:ns:sa`.
48. **What is aggregation in ClusterRoles?** Combine multiple ClusterRoles via aggregationRule labels.
49. **The universal troubleshooting order?** describe → events → logs (--previous) → exec/debug → check node/network.
50. **One-liner probes?** Liveness = restart if broken; Readiness = route traffic only when ready; Startup = wait for slow boot.

### Day 6 Schedule
- **Study 1:** RBAC + ServiceAccounts. **Lab 1:** Build a least-privilege Role + test with `can-i`.
- **Study 2:** Probes + troubleshooting workflow. **Lab 2:** Fix a CrashLoopBackOff and an ImagePullBackOff.
- **Recall:** 50 questions; **Mock:** 45-min timed CKA-style troubleshooting set.

### Fast-Learning Tips (Day 6)
- Build a "troubleshooting flowchart" card and use it on every broken lab this week — it becomes reflex.
- Teach RBAC by role-playing: "I am SA X in namespace Y — can I delete pods? Why/why not?"

---

# DAY 7 — Observability Foundations & Prometheus

### Topics & Skills
- Monitoring vs observability; the **three pillars** (metrics, logs, traces) and when to use each
- The four "golden signals" (latency, traffic, errors, saturation) and RED/USE methods
- Metric types: counter, gauge, histogram, summary
- **Prometheus** architecture: pull model, scraping, service discovery, TSDB, exposition format
- Labels/cardinality; instrumenting an app with a client library
- Node exporter, kube-state-metrics, and scrape configs

### KodeKloud Labs
- **Prometheus Certified Associate (PCA)** course labs
- **Prometheus fundamentals** playground: run Prometheus, scrape a target
- Instrument a sample app and expose `/metrics`

### 50 Questions & Answers
1. **Monitoring vs observability?** Monitoring watches known failure modes; observability lets you ask new questions about unknown states from telemetry.
2. **Three pillars?** Metrics, logs, traces.
3. **When to use metrics?** Cheap, aggregatable trends and alerting (is something wrong?).
4. **When to use traces?** Following a request across services (where is it slow?).
5. **When to use logs?** Detailed per-event context (what exactly happened?).
6. **Four golden signals?** Latency, traffic, errors, saturation.
7. **RED method?** Rate, Errors, Duration — for request-driven services.
8. **USE method?** Utilization, Saturation, Errors — for resources.
9. **Prometheus data model?** Time series identified by metric name + label set.
10. **Pull vs push?** Prometheus pulls (scrapes) targets over HTTP; Pushgateway is for short-lived jobs.
11. **Counter?** Monotonically increasing value (resets to 0 on restart).
12. **Gauge?** A value that can go up and down (e.g., memory).
13. **Histogram?** Buckets of observations + sum + count; enables quantile estimation.
14. **Summary?** Client-side computed quantiles + sum + count.
15. **Histogram vs summary tradeoff?** Histograms aggregate across instances; summaries don't but are exact per-instance.
16. **What is exposition format?** Plain-text `metric{labels} value` lines at `/metrics`.
17. **What is a scrape?** Prometheus HTTP GET of a target's metrics endpoint at an interval.
18. **What is service discovery?** Auto-finding targets (Kubernetes SD, file SD, etc.).
19. **What is a label?** A key/value dimension differentiating time series.
20. **What is cardinality?** Number of unique time series; high cardinality kills performance.
21. **A cardinality anti-pattern?** Putting user IDs, emails, or request IDs in labels.
22. **What is `rate()`?** Per-second average increase of a counter over a range.
23. **Why use rate on counters, not raw values?** Counters only increase; rate gives meaningful throughput.
24. **What is `irate()`?** Instant rate using the last two samples (spiky).
25. **What is a range vector?** A series of samples over a time window (e.g., `[5m]`).
26. **Instant vector?** A single sample per series at one timestamp.
27. **What is node_exporter?** Exposes host/OS-level metrics.
28. **kube-state-metrics?** Exposes K8s object state as metrics (deployment replicas, pod status).
29. **cAdvisor?** Container resource usage metrics (via kubelet).
30. **What is a scrape_config?** Config block defining how/where to scrape.
31. **What is relabeling?** Rewriting/filtering labels before or after scraping.
32. **Default scrape interval?** Commonly 15s–1m (configurable).
33. **What is the TSDB?** Prometheus's local time-series database (blocks + WAL).
34. **Prometheus retention default?** ~15 days locally (configurable).
35. **Why is local storage a limit?** Not designed for long-term/horizontal scale — needs remote storage.
36. **What is remote_write?** Streams samples to an external store (e.g., VictoriaMetrics).
37. **What is a target's `up` metric?** 1 if scrape succeeded, 0 if failed.
38. **How to compute error rate?** `rate(errors_total[5m]) / rate(requests_total[5m])`.
39. **What is a recording rule?** Precomputes expensive queries into new series.
40. **What is Alertmanager?** Handles alert routing, grouping, silencing, notifications.
41. **How does Prometheus fire an alert?** Evaluates alerting rules; sends firing alerts to Alertmanager.
42. **What is `for:` in an alert rule?** Duration a condition must hold before firing (reduces flapping).
43. **What is a bucket in a histogram?** Cumulative count of observations ≤ a boundary (`le`).
44. **How to estimate p95 latency?** `histogram_quantile(0.95, sum(rate(bucket[5m])) by (le))`.
45. **Why aggregate before histogram_quantile?** To combine buckets across instances correctly.
46. **What labels does Prometheus add automatically?** `job`, `instance`.
47. **What is federation?** One Prometheus scraping aggregated metrics from another.
48. **Why is pull model good for K8s?** Health of scrape = liveness signal; easy SD; no client push config.
49. **What's a good alert philosophy?** Alert on symptoms (SLO burn), not every cause.
50. **One-liner Prometheus?** A pull-based metrics system storing labeled time series, queried with PromQL and alerted via Alertmanager.

### Day 7 Schedule
- **Study 1:** Pillars, golden signals, metric types. **Lab 1:** Run Prometheus, scrape node_exporter.
- **Study 2:** Labels/cardinality, scrape configs. **Lab 2:** Instrument an app, expose `/metrics`, scrape it.
- **Recall:** 50 questions; **Feynman:** explain counter vs gauge vs histogram with real examples.

### Fast-Learning Tips (Day 7)
- Blow up cardinality on purpose (add a random label) and watch series count explode — the lesson sticks.
- Memorize the 4 golden signals as a chant; you'll map every future dashboard to them.

---

# DAY 8 — VictoriaMetrics Deep Dive

### Topics & Skills
- Why VictoriaMetrics (VM): Prometheus-compatible, high performance, better compression, long-term storage
- Single-node vs **cluster** VM (vmstorage, vminsert, vmselect); scaling model
- Ingestion: `remote_write` from Prometheus, **vmagent** (scraping + buffering + relabeling)
- **MetricsQL** (superset of PromQL) — extra functions and gotchas
- Storage/retention, deduplication, downsampling; cardinality handling
- **vmalert** (recording/alerting rules) and Alertmanager integration; **VMAgent/VMOperator** on K8s

### KodeKloud Labs
- No dedicated VM course on all plans → use the **Prometheus playground** + a personal VM lab:
  - Deploy single-node VM (Docker/Helm) and point Prometheus `remote_write` at it
  - Deploy **vmagent** to scrape targets directly
  - Query via VM's Grafana datasource using MetricsQL
- Optional: KodeKloud **Helm** lab to install the `victoria-metrics-k8s-stack` chart

### 50 Questions & Answers
1. **What is VictoriaMetrics?** A fast, cost-efficient, Prometheus-compatible time-series database and monitoring stack.
2. **Is VM a drop-in for Prometheus storage?** Yes — supports remote_write and PromQL/MetricsQL.
3. **Key VM advantage over vanilla Prometheus?** Better compression, lower RAM, long-term storage, and horizontal scale.
4. **Single-node vs cluster VM?** Single-node is one binary; cluster splits into vminsert/vmstorage/vmselect for scale.
5. **What does vminsert do?** Accepts incoming data and routes/shards it to vmstorage nodes.
6. **What does vmstorage do?** Stores data and returns it for queries; holds the actual TSDB.
7. **What does vmselect do?** Executes queries by fetching from vmstorage nodes and merging results.
8. **How does VM cluster shard data?** vminsert consistently hashes series across vmstorage nodes.
9. **Is vmstorage stateful?** Yes — it holds data; scale by adding nodes (share-nothing).
10. **What is vmagent?** A lightweight agent that scrapes/receives metrics, buffers, relabels, and forwards them.
11. **Why use vmagent over Prometheus for scraping?** Lower resource use, on-disk buffering, multi-target replication.
12. **What is MetricsQL?** VM's query language — a backward-compatible superset of PromQL.
13. **Name a MetricsQL convenience?** `rate()` handling of counter resets/gaps, `default`, `keep_metric_names`, rollup functions.
14. **Does MetricsQL run existing PromQL?** Yes, with some improved default behaviors.
15. **What is deduplication in VM?** Removing duplicate samples (e.g., from HA Prometheus pairs) via `-dedup.minScrapeInterval`.
16. **Why run two vmagents/Prometheis?** HA ingestion; VM dedup merges them.
17. **What is downsampling in VM?** Reducing resolution of old data to save space (enterprise/config feature).
18. **How does VM handle high cardinality?** More efficiently than Prometheus, but limits still apply; use cardinality explorer.
19. **What is the cardinality explorer?** A VM UI/API to find the highest-cardinality metrics and labels.
20. **How does Prometheus send data to VM?** Via `remote_write` to `/api/v1/write`.
21. **VM native ingestion protocols?** Prometheus remote_write, Influx, Graphite, OpenTSDB, CSV, and its own.
22. **What is vmalert?** Component that evaluates recording/alerting rules against VM and sends alerts to Alertmanager.
23. **Does VM include Alertmanager?** No — vmalert integrates with the standard Alertmanager.
24. **What is the retention flag?** `-retentionPeriod` sets how long data is kept.
25. **Why is VM cheaper to run?** Better compression + lower RAM/IO per active time series.
26. **What is an "active time series"?** A series receiving samples recently; the primary sizing metric.
27. **How to scale query throughput?** Add vmselect nodes (stateless).
28. **How to scale ingestion?** Add vminsert nodes (stateless).
29. **How to scale storage/capacity?** Add vmstorage nodes.
30. **What replication does VM cluster offer?** `-replicationFactor` on vminsert for redundancy.
31. **Is VM query-compatible with Grafana?** Yes — use the Prometheus datasource type pointing at vmselect/VM.
32. **What is vmui?** VM's built-in web UI for queries and troubleshooting.
33. **What is `keep_metric_names`?** MetricsQL modifier to preserve names through transforming functions.
34. **How does VM store data on disk?** Time-partitioned, heavily compressed columnar-ish blocks.
35. **What is `-search.maxQueryDuration`?** Guardrail limiting long-running queries.
36. **Common VM K8s deployment?** Helm `victoria-metrics-k8s-stack` or the VM Operator (VMCluster, VMAgent CRDs).
37. **What is the VM Operator?** Kubernetes operator managing VM components via CRDs.
38. **VMServiceScrape CRD?** VM's equivalent of Prometheus Operator's ServiceMonitor.
39. **Migration path from Prometheus?** Point remote_write to VM, then use VM as the Grafana datasource.
40. **Can VM replace Thanos/Cortex?** Yes — it's an alternative long-term/scalable store.
41. **What is `vmbackup`/`vmrestore`?** Tools for snapshot backups and restores of vmstorage.
42. **Does VM support multitenancy?** Yes — cluster version via tenant IDs in the URL path.
43. **How is a tenant expressed?** `/insert/<accountID>/...` and `/select/<accountID>/...`.
44. **What causes slow VM queries?** High cardinality, huge time ranges, unindexed heavy regex label matches.
45. **How to reduce ingestion cost?** Relabel/drop unneeded metrics/labels at vmagent.
46. **What is stream aggregation in vmagent?** Aggregating/relabeling samples before storage to cut cardinality.
47. **Where to see per-metric cost?** Cardinality explorer + `/api/v1/status/tsdb`.
48. **How does VM handle counter resets in rate()?** Detects resets and computes correct increase (like Prometheus but more robust to gaps).
49. **Is MetricsQL required?** No — PromQL works; MetricsQL just adds power.
50. **One-liner VM?** A Prometheus-compatible, horizontally scalable, high-compression TSDB with vmagent (ingest), vminsert/vmstorage/vmselect (cluster), and vmalert (rules).

### Day 8 Schedule
- **Study 1:** VM architecture + ingestion model. **Lab 1:** Single-node VM + Prometheus remote_write into it.
- **Study 2:** MetricsQL + vmagent + retention. **Lab 2:** Deploy vmagent to scrape; query in Grafana; run cardinality explorer.
- **Recall:** 50 questions; **Feynman:** explain how a cluster VM scales ingest, storage, and query independently.

### Fast-Learning Tips (Day 8)
- Map each VM component to its Prometheus counterpart (vmagent≈scraper, vmstorage≈TSDB) — anchoring to known concepts accelerates learning.
- Since VM course coverage is thin, build one real pipeline and re-derive the docs from your running system.

---

# DAY 9 — PromQL/MetricsQL, Grafana & Alerting

### Topics & Skills
- PromQL/MetricsQL mastery: selectors, matchers, operators, aggregations (`sum/avg/max by`), functions
- Rate/increase, histogram_quantile, `topk`, `predict_linear`, offset, subqueries
- **Grafana**: datasources, dashboards, variables/templating, panels, transformations
- Alerting: rule design, `for`, labels/annotations, routing, silences, inhibition
- SLO-oriented alerting preview (burn-rate) — bridges into SRE block

### KodeKloud Labs
- **Prometheus (PCA)** PromQL and alerting labs
- **Grafana** lab / playground: build a golden-signals dashboard
- Wire **vmalert/Alertmanager** to fire a test alert

### 50 Questions & Answers
1. **What does a bare metric name return?** An instant vector of all matching series' latest samples.
2. **Label matcher operators?** `=`, `!=`, `=~` (regex), `!~` (negated regex).
3. **What is an instant vector?** One value per series at a timestamp.
4. **Range vector?** Multiple samples per series over `[duration]`.
5. **When must you use a range vector?** With functions like `rate()`, `increase()`, `avg_over_time()`.
6. **`sum by (label)` meaning?** Aggregate summing, grouping by that label.
7. **`sum without (label)`?** Aggregate keeping all labels except the listed one.
8. **`rate()` vs `increase()`?** rate = per-second; increase = total over the window.
9. **`topk(3, expr)`?** The 3 highest series by value.
10. **`bottomk`?** The lowest-k series.
11. **`count()` vs `count_values()`?** count = number of series; count_values counts by value.
12. **`histogram_quantile(0.99, ...)`?** Estimates the 99th percentile from histogram buckets.
13. **Why `sum(rate(bucket[5m])) by (le)`?** To aggregate buckets across instances before quantile.
14. **`predict_linear()` use?** Forecast a value (e.g., disk full in 4h) via linear regression.
15. **`offset 1h`?** Shifts the query back in time by 1h (compare to past).
16. **What is a subquery?** A range query over the result of an instant query: `expr[5m:30s]`.
17. **Vector matching one-to-one?** Series matched by identical label sets.
18. **`on()` / `ignoring()`?** Control which labels are used for matching in binary ops.
19. **`group_left` / `group_right`?** Many-to-one matching for label enrichment.
20. **How to compute error ratio?** `sum(rate(errs[5m])) / sum(rate(reqs[5m]))`.
21. **`absent()` use?** Alert when a series is missing (e.g., target down).
22. **`clamp_max/min`?** Bound values to a ceiling/floor.
23. **`increase()` caveat?** Extrapolates; small windows can be inaccurate.
24. **What is a recording rule for?** Precompute costly expressions for dashboards/alerts.
25. **Alerting rule anatomy?** expr, `for`, labels, annotations.
26. **What does `for: 5m` do?** Condition must be true 5m before firing.
27. **Alert labels role?** Used for routing/grouping in Alertmanager.
28. **Alert annotations role?** Human context (summary, description, runbook link).
29. **What is Alertmanager grouping?** Bundles related alerts into one notification.
30. **What is a silence?** Temporarily mute matching alerts.
31. **What is inhibition?** Suppress lower-priority alerts when a higher one fires.
32. **What is a route tree?** Hierarchical matching for where alerts are sent.
33. **What is a receiver?** A notification integration (Slack, PagerDuty, email).
34. **Grafana datasource for VM?** Prometheus type pointed at VM's query endpoint.
35. **Grafana template variable?** A dynamic dashboard filter (e.g., `$namespace`).
36. **`label_values()` in Grafana?** Populates a variable from label values.
37. **What is a Grafana transformation?** Client-side reshaping/joining of query results.
38. **Panel types?** Time series, stat, gauge, table, heatmap, bar.
39. **How to build a golden-signals dashboard?** Panels for rate (traffic), error ratio, latency quantiles, saturation.
40. **What is a threshold in a panel?** Value bands for color/alert visualization.
41. **Why annotate deploys on graphs?** Correlate metric changes with releases.
42. **What is burn rate?** Rate of consuming an error budget relative to SLO.
43. **Multi-window burn-rate alert idea?** Fast (short window, high burn) + slow (long window) to balance sensitivity/noise.
44. **What is `avg_over_time()`?** Average of samples over a range window.
45. **`max_over_time()` use?** Peak within a window (e.g., saturation).
46. **Why avoid alerting on raw CPU?** It's a cause, not a symptom; alert on SLO impact.
47. **What is a dead-man's switch alert?** Always-firing alert whose absence signals the pipeline is broken.
48. **How to test an alert fires?** Force the condition (load/kill) and watch vmalert → Alertmanager.
49. **How to reduce alert fatigue?** Symptom-based alerts, good `for`, grouping, inhibition, SLO burn rates.
50. **One-liner querying?** Select series → apply range function (rate) → aggregate (sum by) → visualize/alert.

### Day 9 Schedule
- **Study 1:** PromQL/MetricsQL functions + matching. **Lab 1:** Write 15 queries (rate, quantiles, topk, error ratio).
- **Study 2:** Grafana + alerting design. **Lab 2:** Build golden-signals dashboard; wire a firing alert end-to-end.
- **Recall:** 50 questions; **Feynman:** derive a p99 latency query from scratch and explain each part.

### Fast-Learning Tips (Day 9)
- Rebuild the same dashboard twice — once by copy, once from memory. The second time is where learning happens.
- Keep a personal PromQL snippet library; every query you write once should be reusable forever.

---

# DAY 10 — OpenTelemetry Fundamentals (Traces)

### Topics & Skills
- What OpenTelemetry (OTel) is: vendor-neutral standard + SDKs for traces, metrics, logs
- Core concepts: **traces, spans, span context, trace/span IDs, parent-child, attributes, events, links, status**
- **Context propagation** (W3C traceparent header) across services
- Signals overview: how OTel unifies all three pillars
- Semantic conventions (standardized attribute names)
- Auto- vs manual instrumentation; the SDK pipeline (API → SDK → exporter)

### KodeKloud Labs
- Search KodeKloud for **OpenTelemetry** / **Observability** courses; use the playground if no dedicated lab
- Personal lab: instrument a two-service app (e.g., Python/Go), generate traces, export to console then to a collector
- Visualize traces in **Jaeger** (run via Docker)

### 50 Questions & Answers
1. **What is OpenTelemetry?** A CNCF vendor-neutral standard and toolset for generating/collecting telemetry (traces, metrics, logs).
2. **Why does OTel exist?** To avoid vendor lock-in and standardize instrumentation across languages/backends.
3. **What is a trace?** The end-to-end journey of a request through a system.
4. **What is a span?** A single unit of work within a trace (an operation with start/end time).
5. **Root span?** The first span of a trace, with no parent.
6. **What identifies a trace?** A trace ID (shared by all its spans).
7. **What identifies a span?** A span ID (unique within the trace).
8. **What is span context?** The immutable IDs + flags propagated to link spans across boundaries.
9. **Parent-child spans?** A child span is caused by/nested within a parent operation.
10. **What are span attributes?** Key-value metadata describing the operation (http.method, db.system).
11. **What is a span event?** A timestamped log-like annotation within a span.
12. **What is a span link?** A reference connecting spans across different traces (e.g., batch processing).
13. **Span status values?** Unset, Ok, Error.
14. **What is context propagation?** Passing trace context across service boundaries so spans join one trace.
15. **W3C traceparent format?** `version-traceid-spanid-flags` HTTP header.
16. **What is a propagator?** Component that injects/extracts context into/from carriers (headers).
17. **Baggage?** Key-value context propagated alongside trace context for app use.
18. **Signals in OTel?** Traces, metrics, logs.
19. **What is the OTel API vs SDK?** API defines interfaces (safe no-op if unconfigured); SDK is the implementation.
20. **What is an exporter?** Sends telemetry to a backend (OTLP, Jaeger, Prometheus).
21. **What is OTLP?** OpenTelemetry Protocol — the native gRPC/HTTP wire format.
22. **What is a span processor?** Batches/processes spans before export (e.g., BatchSpanProcessor).
23. **What is a TracerProvider?** Factory that creates tracers and holds processors/exporters.
24. **What is a Tracer?** Creates spans for a given instrumentation scope.
25. **Auto-instrumentation?** Libraries/agents that add spans without code changes.
26. **Manual instrumentation?** Explicitly starting/ending spans in code.
27. **Semantic conventions?** Standardized attribute keys/values for consistency across tools.
28. **What is a Resource?** Attributes describing the entity producing telemetry (service.name, host).
29. **Why is service.name critical?** It identifies the service across all signals/backends.
30. **What is sampling?** Deciding which traces to record/export to control volume.
31. **Head-based sampling?** Decision made at trace start.
32. **Tail-based sampling?** Decision after the full trace is seen (e.g., keep errors/slow traces).
33. **Where is tail sampling done?** In the Collector (needs full-trace buffering).
34. **AlwaysOn/AlwaysOff samplers?** Record all / record none.
35. **ParentBased sampler?** Respects the parent's sampling decision.
36. **TraceIdRatioBased sampler?** Samples a fixed fraction of traces.
37. **What is instrumentation scope?** The library/module a tracer/meter belongs to.
38. **What is a Meter (metrics API)?** Creates instruments (counter, histogram, gauge).
39. **How do logs fit in OTel?** Log records correlated with trace/span IDs.
40. **What is trace-log correlation?** Injecting trace_id/span_id into logs to jump between pillars.
41. **What backends accept OTLP?** Jaeger, Tempo, many APMs, and the Collector.
42. **Can OTel export metrics to Prometheus/VM?** Yes — via Prometheus exporter or OTLP→Collector→remote_write.
43. **What is Jaeger?** A distributed tracing backend and UI.
44. **What is a critical path in a trace?** The sequence of spans determining total latency.
45. **How to find a slow dependency?** Inspect span durations in the trace waterfall.
46. **What is context leakage risk?** Losing context across async/threads breaks the trace.
47. **How to propagate context in async code?** Use the language's context mechanisms/scopes correctly.
48. **Why prefer BatchSpanProcessor in prod?** Reduces export overhead vs per-span export.
49. **Difference: OTel vs Jaeger client?** OTel is the standard replacing legacy per-vendor clients (OpenTracing/OpenCensus merged into it).
50. **One-liner OTel traces?** A standardized way to record request journeys as parent-child spans, propagate context across services, and export to any backend.

### Day 10 Schedule
- **Study 1:** Traces/spans/context concepts. **Lab 1:** Instrument a service, export spans to console.
- **Study 2:** Propagation, sampling, semantic conventions. **Lab 2:** Two-service app → propagate context → view trace in Jaeger.
- **Recall:** 50 questions; **Feynman:** explain how a trace ID travels from service A to B.

### Fast-Learning Tips (Day 10)
- Look at a real trace waterfall before reading theory — the visual makes spans/parents obvious.
- Deliberately drop the traceparent header and watch the trace fracture into two — you'll remember propagation forever.

---

# DAY 11 — OpenTelemetry Collector & Pipelines

### Topics & Skills
- **OTel Collector** architecture: receivers → processors → exporters → **pipelines**; extensions
- Deployment patterns: **agent** (per-node/sidecar) vs **gateway** (central)
- Key processors: batch, memory_limiter, resource, attributes, filter, **tail_sampling**
- Receivers (otlp, prometheus, filelog) and exporters (otlp, prometheus/remote_write to VM, loki, jaeger/tempo)
- Collector on Kubernetes via the **OpenTelemetry Operator** (auto-instrumentation injection)
- Building an end-to-end pipeline: app → collector → VM (metrics) + Jaeger (traces) + Loki (logs)

### KodeKloud Labs
- KodeKloud **OpenTelemetry / Observability** labs if available; else Docker-based personal lab
- Personal lab: run **otelcol-contrib**, define a config with all three signal pipelines
- Send OTLP from the Day-10 app; export metrics to VictoriaMetrics via remote_write; traces to Jaeger

### 50 Questions & Answers
1. **What is the OTel Collector?** A vendor-agnostic proxy/agent that receives, processes, and exports telemetry.
2. **Why use a Collector?** Decouples app from backends, centralizes processing, batching, sampling, and routing.
3. **Three pipeline stages?** Receivers → processors → exporters.
4. **What is a receiver?** Ingests data (e.g., otlp, prometheus, filelog, kafka).
5. **What is a processor?** Transforms/filters/batches data in the pipeline.
6. **What is an exporter?** Sends data to a backend (otlp, prometheusremotewrite, loki, jaeger).
7. **What is an extension?** Non-pipeline capabilities (health_check, pprof, zpages).
8. **What is a pipeline?** A named chain (per signal) of receivers→processors→exporters.
9. **Can one Collector run multiple pipelines?** Yes — separate pipelines for traces, metrics, logs.
10. **Agent vs gateway pattern?** Agent runs close to the app (node/sidecar); gateway is a central cluster.
11. **Why a gateway?** Central sampling, aggregation, egress control, and backend fan-out.
12. **Why an agent/sidecar?** Local collection, offloading the app quickly, adding host metadata.
13. **What is the batch processor?** Groups telemetry to reduce export calls — recommended always.
14. **What is memory_limiter?** Prevents OOM by applying backpressure/dropping under memory pressure.
15. **Order of memory_limiter?** Should be first among processors.
16. **What is the resource processor?** Adds/modifies resource attributes (e.g., service.name).
17. **What is the attributes processor?** Adds/updates/deletes/hashes span/log attributes.
18. **What is the filter processor?** Drops telemetry matching conditions (reduce cost/noise).
19. **What is tail_sampling?** Samples full traces based on properties (errors, latency) after buffering.
20. **Why can't tail sampling be per-span?** It needs the complete trace to decide.
21. **What is the otlp receiver?** Accepts OTLP over gRPC (4317) and HTTP (4318).
22. **Default OTLP ports?** 4317 (gRPC), 4318 (HTTP).
23. **prometheus receiver role?** Scrapes Prometheus targets into the Collector.
24. **prometheusremotewrite exporter?** Sends metrics to Prometheus-compatible stores like VictoriaMetrics.
25. **How to send metrics to VM?** Use prometheusremotewrite exporter → VM `/api/v1/write`.
26. **loki exporter?** Ships logs to Loki.
27. **How to send traces to Jaeger/Tempo?** otlp exporter to their OTLP endpoint.
28. **What is otelcol vs otelcol-contrib?** Core has stable components; contrib bundles many community components.
29. **How is the Collector configured?** A YAML with receivers/processors/exporters/extensions/service sections.
30. **What activates a pipeline?** Listing components under `service.pipelines`.
31. **A component defined but not in service?** It's inactive (ignored).
32. **What is the OTel Operator?** K8s operator managing Collectors and auto-instrumentation injection.
33. **Auto-instrumentation injection?** Operator injects language agents into pods via annotations.
34. **What is an Instrumentation CR?** Defines which auto-instrumentation/config to inject.
35. **Collector as DaemonSet?** Agent mode — one per node for local collection.
36. **Collector as Deployment?** Gateway mode — scalable central processing.
37. **How to secure OTLP?** TLS + auth (e.g., bearer token/mTLS) on receivers.
38. **How to reduce trace cost at the Collector?** tail_sampling + filter + probabilistic sampling.
39. **How to enrich K8s metadata?** k8sattributes processor adds pod/namespace/node labels.
40. **What does the k8sattributes processor need?** RBAC to read pod metadata from the API.
41. **What is backpressure in the Collector?** Slowing intake when exporters can't keep up (queues + memory_limiter).
42. **Sending queue/retry?** Exporters buffer and retry on backend failures.
43. **What is fan-out?** One pipeline exporting to multiple backends simultaneously.
44. **How to route by attribute?** routing processor/connector directs telemetry to different pipelines.
45. **What is a connector?** Bridges pipelines (e.g., spanmetrics connector derives metrics from spans).
46. **spanmetrics connector use?** Generate RED metrics from traces automatically.
47. **How to health-check the Collector?** health_check extension endpoint.
48. **zpages extension?** In-process debug pages for live pipeline diagnostics.
49. **Why put a Collector between app and VM/Jaeger?** Central control, resilience, and backend independence.
50. **One-liner Collector?** A configurable telemetry pipeline (receivers→processors→exporters) that ingests, transforms, samples, and routes traces/metrics/logs to any backend.

### Day 11 Schedule
- **Study 1:** Collector architecture + processors. **Lab 1:** Run otelcol-contrib with a traces pipeline → Jaeger.
- **Study 2:** Gateway/agent + exporters + operator. **Lab 2:** Add metrics pipeline → VictoriaMetrics; add k8sattributes.
- **Recall:** 50 questions; **Feynman:** diagram app→collector→(VM+Jaeger) and narrate each hop.

### Fast-Learning Tips (Day 11)
- Treat the Collector config as Lego: build the smallest working pipeline, then add one processor at a time.
- Break the exporter (wrong endpoint) and watch the queue/retry + memory_limiter behavior — that's production reality.

---

# DAY 12 — Logging Fundamentals & Structured Logging

### Topics & Skills
- Why logs matter; the difference between logs, events, and metrics
- **Structured logging** (JSON) vs unstructured; log levels (DEBUG/INFO/WARN/ERROR/FATAL)
- Correlation: injecting trace_id/span_id, request IDs; contextual fields
- Kubernetes logging model: stdout/stderr, container log files, log rotation, node-level logging
- The **12-factor logs** principle (treat logs as event streams)
- Cost/volume control: sampling, levels, cardinality of log fields; PII/sensitive-data hygiene

### KodeKloud Labs
- KodeKloud **Kubernetes logging** lab (`kubectl logs`, multi-container, `--previous`)
- Personal lab: emit structured JSON logs from an app; ship stdout via a collector
- Explore log rotation and node log paths on a cluster node

### 50 Questions & Answers
1. **What is a log?** A timestamped record of a discrete event.
2. **Logs vs metrics?** Logs are high-detail per-event; metrics are aggregated numeric trends.
3. **Structured logging?** Emitting logs as machine-parseable key-value (usually JSON).
4. **Why structured over plain text?** Reliable parsing, filtering, and querying at scale.
5. **Common log levels?** TRACE, DEBUG, INFO, WARN, ERROR, FATAL.
6. **When to use ERROR?** For failures needing attention, not expected conditions.
7. **When to use DEBUG?** Detailed diagnostics, usually off in production.
8. **Why include trace_id in logs?** To correlate logs with traces (pillar-jumping).
9. **What is a correlation/request ID?** An ID threaded through a request's logs across services.
10. **Where should K8s apps log?** To stdout/stderr, not files (12-factor).
11. **Why stdout/stderr?** The platform captures and routes streams; app stays stateless.
12. **Where does the kubelet store container logs?** On the node under `/var/log/pods` (or `/var/log/containers` symlinks).
13. **What handles log rotation?** The container runtime / kubelet (size/count limits).
14. **What happens to logs when a pod is deleted?** Node log files go with it — ship them off-node.
15. **What is node-level logging?** A per-node agent (DaemonSet) tailing container logs.
16. **12-factor logs principle?** Treat logs as an event stream; don't manage files in-app.
17. **What is log aggregation?** Collecting logs from all sources into a central searchable store.
18. **Why avoid high-cardinality log fields as index keys?** Storage/perf blow-up (like metrics).
19. **How to control log volume?** Right log levels, sampling, dropping noisy lines.
20. **What is log sampling?** Keeping a subset of repetitive logs to cut cost.
21. **PII risk in logs?** Leaking secrets/personal data; must scrub/redact.
22. **How to redact sensitive fields?** Filter/mask at the app or collector before storage.
23. **What is a structured logger?** A library emitting key-value logs (zap, logrus, slog, pino).
24. **Timestamp best practice?** UTC, ISO-8601/RFC3339, consistent across services.
25. **Why consistent field names?** Enables cross-service queries (align with semantic conventions).
26. **`kubectl logs -f`?** Streams (follows) logs live.
27. **`kubectl logs --previous`?** Logs from the previous (crashed) container instance.
28. **Logs from a specific container?** `-c <container>`.
29. **Logs from all pods of a label?** `kubectl logs -l app=x --all-containers`.
30. **Why not rely on `kubectl logs` in prod?** Ephemeral, node-local, no search/retention — need aggregation.
31. **What is multiline log handling?** Joining stack traces spanning lines into one event.
32. **Where to do multiline joining?** At the collector (e.g., filelog multiline config).
33. **What is a log severity mapping?** Normalizing varied level strings to a standard set.
34. **What is contextual logging?** Attaching request-scoped fields (user, route, trace_id) automatically.
35. **Difference: event vs log line?** An event is the thing that happened; a log line is one representation of it.
36. **What is a dead-letter for logs?** A place for logs that fail parsing/routing.
37. **Structured logs and OTel?** OTel log records carry attributes + trace correlation.
38. **How to reduce ERROR noise?** Fix root causes; downgrade expected conditions to WARN/INFO.
39. **What is log-based metrics?** Deriving counters/gauges from log patterns (last resort vs real metrics).
40. **Why prefer metrics over log-based counting?** Cheaper, faster, lower cardinality.
41. **What is back-pressure in log pipelines?** Slowing/buffering when the store can't keep up.
42. **What is at-least-once delivery?** Logs may be duplicated but not lost on retry.
43. **How to handle bursty logging?** Buffering + rate limiting at the agent.
44. **Where to enforce retention?** In the log store (by index/age/size).
45. **Why separate hot vs cold log storage?** Cost — recent logs fast/expensive, old logs cheap/slow.
46. **What is a log schema?** An agreed set of fields/types for consistency.
47. **Trace vs log for latency?** Trace shows *where* time went; logs give *what* happened at a step.
48. **What is structured event logging (wide events)?** Rich single events with many attributes — powerful for observability.
49. **Golden rule of prod logging?** Structured + leveled + correlated + volume-controlled + PII-safe.
50. **One-liner logging?** Emit structured, leveled, trace-correlated events to stdout; let the platform collect, ship, and store them centrally.

### Day 12 Schedule
- **Study 1:** Structured logging + levels + correlation. **Lab 1:** Emit JSON logs with trace_id from an app.
- **Study 2:** K8s logging model + volume/PII control. **Lab 2:** Inspect node log paths; ship stdout via a collector.
- **Recall:** 50 questions; **Feynman:** explain why K8s apps log to stdout.

### Fast-Learning Tips (Day 12)
- Convert one plain-text log line into a JSON event and query both — feel why structure wins.
- Add trace_id to logs today so tomorrow's debugging lets you jump log↔trace instantly.

---

# DAY 13 — Log Aggregation Pipelines (Loki / ELK / Fluent Bit)

### Topics & Skills
- Aggregation architecture: **collect → parse/transform → store → query/visualize**
- Collectors/shippers: **Fluent Bit**, Fluentd, Vector, OTel filelog receiver
- Stores: **Grafana Loki** (label-indexed) vs **Elasticsearch** (full-text) — tradeoffs
- **LogQL** (Loki query language) basics; label vs line filters; metric queries from logs
- Kubernetes deployment: DaemonSet shippers, enrichment with pod metadata
- Correlating logs↔metrics↔traces in Grafana (unified observability)

### KodeKloud Labs
- KodeKloud **EFK/Logging on Kubernetes** lab if available; else personal Docker/Helm lab
- Personal lab: deploy **Loki + Grafana + Fluent Bit** (or Promtail) via Helm; query with LogQL
- Correlate a trace (Jaeger/Tempo) with its logs (Loki) in Grafana using trace_id

### 50 Questions & Answers
1. **Four stages of a log pipeline?** Collect → parse/transform → store → query/visualize.
2. **What is Fluent Bit?** A lightweight, fast log/metric collector and forwarder.
3. **Fluent Bit vs Fluentd?** Fluent Bit is lighter (C, low memory); Fluentd is heavier with more plugins.
4. **What is Vector?** A high-performance observability data pipeline (logs/metrics) in Rust.
5. **What is Promtail?** Loki's agent that tails logs and attaches labels (now succeeded by Grafana Alloy).
6. **What is Grafana Loki?** A log store that indexes labels (not full text) for cost efficiency.
7. **Loki vs Elasticsearch core difference?** Loki indexes labels only; ES indexes full text.
8. **Loki tradeoff?** Cheaper storage but queries scan log content within label streams.
9. **When choose ES?** Heavy full-text search/analytics needs.
10. **What is a Loki stream?** A unique set of labels + its ordered log lines.
11. **Why keep Loki label cardinality low?** High cardinality creates too many streams and degrades performance.
12. **What is LogQL?** Loki's query language: a log stream selector plus filters/metrics.
13. **LogQL stream selector example?** `{namespace="prod", app="api"}`.
14. **LogQL line filter?** `|= "error"`, `!= "debug"`, `|~ "regex"`.
15. **LogQL parser example?** `| json` or `| logfmt` to extract fields.
16. **LogQL metric query?** `rate({app="api"} |= "error" [5m])` — counts matching lines.
17. **How to graph error rate from logs?** LogQL `count_over_time`/`rate` on filtered streams.
18. **What labels should logs get in K8s?** namespace, pod, container, app — from metadata, not log content.
19. **What enriches K8s log metadata?** The Kubernetes filter (Fluent Bit) or k8sattributes (OTel).
20. **How are logs collected in K8s?** DaemonSet agents tail `/var/log/containers/*.log`.
21. **What is a tail input?** Reading log files line-by-line as they grow.
22. **What is a parser in Fluent Bit?** Extracts structured fields from raw lines (json/regex).
23. **What is the Fluent Bit routing model?** Inputs → parsers → filters → outputs via tags/matches.
24. **What is buffering in shippers?** Memory/filesystem queues to survive backend outages.
25. **At-least-once vs exactly-once?** Log pipelines are typically at-least-once (possible dupes).
26. **What is backpressure handling?** Pause/buffer intake when outputs lag.
27. **What is the EFK stack?** Elasticsearch + Fluentd/Fluent Bit + Kibana.
28. **What is the PLG stack?** Promtail + Loki + Grafana.
29. **Where to parse logs — app or pipeline?** Prefer app-side structured logs; pipeline parses as fallback.
30. **How to correlate logs and traces?** Shared trace_id + Grafana derived fields linking to the trace.
31. **What is a Grafana derived field?** A regex that turns a log field (trace_id) into a clickable trace link.
32. **What is a data link?** Grafana link from one panel/field to another view.
33. **How to reduce storage cost?** Drop noisy logs, sample, tune retention, and cap labels.
34. **What is index vs chunk in Loki?** Index maps labels→streams; chunks store compressed log content.
35. **Loki components (microservices mode)?** distributor, ingester, querier, query-frontend, compactor.
36. **What does the distributor do?** Validates and forwards incoming logs to ingesters.
37. **What does the ingester do?** Builds and flushes chunks to object storage.
38. **What does the querier do?** Executes LogQL by reading index + chunks.
39. **Where does Loki store chunks?** Object storage (S3/GCS) or filesystem.
40. **What is retention enforcement in Loki?** Compactor applies retention/deletion.
41. **How to handle multiline stack traces?** Multiline parser joins them before storage.
42. **What is a stream label explosion?** Adding high-cardinality labels (pod hash, request id) → too many streams.
43. **Should request_id be a Loki label?** No — keep it in the log line, filter with `|~`.
44. **How to alert on logs?** Loki ruler / LogQL metric queries feeding Alertmanager.
45. **What is Grafana Alloy?** Grafana's unified collector (successor merging agent/Promtail/OTel).
46. **Can OTel Collector ship logs to Loki?** Yes — filelog receiver → loki exporter (or OTLP).
47. **Full-stack correlation goal?** From a metric spike → drill to traces → drill to logs, all in Grafana.
48. **Why not index everything (ES-style) always?** Cost and write amplification at high volume.
49. **Best practice for K8s logs?** Structured app logs → DaemonSet shipper → label with metadata → Loki → Grafana.
50. **One-liner aggregation?** Ship container stdout via node agents, label with K8s metadata, store in a log backend, and query/correlate in Grafana.

### Day 13 Schedule
- **Study 1:** Pipeline stages + shippers + store tradeoffs. **Lab 1:** Deploy Loki + Grafana + Fluent Bit (Helm).
- **Study 2:** LogQL + K8s metadata + correlation. **Lab 2:** Write LogQL queries; link logs↔traces via trace_id in Grafana.
- **Recall:** 50 questions; **Feynman:** explain Loki's label-index tradeoff vs Elasticsearch.

### Fast-Learning Tips (Day 13)
- Do one real "metric spike → trace → log line" drill; that single workflow is the whole point of observability.
- Purposely add a bad high-cardinality label in Loki and watch performance degrade — cardinality discipline internalized.

---

# DAY 14 — Debugging Methodology for Distributed Systems

### Topics & Skills
- A **systematic debugging method**: observe → hypothesize → test → narrow → fix → verify → document
- Using the three pillars together: metrics (what/when) → traces (where) → logs (why)
- Reasoning about failure modes: latency, errors, saturation, cascading failures, retries/timeouts, thundering herd
- Differentiating symptoms vs root cause; correlation vs causation; the "5 Whys"
- Bisection, binary search in space (which service) and time (which deploy)
- USE/RED as a triage checklist; reading dashboards under pressure

### KodeKloud Labs
- KodeKloud **Kubernetes Troubleshooting** labs (app + cluster failures)
- Personal lab: inject faults (latency, 500s, OOM) into the Day 11–13 stack and diagnose via metrics→traces→logs
- Practice `kubectl debug`, ephemeral containers, port-forward, and exec-based diagnosis

### 50 Questions & Answers
1. **First step in any incident?** Establish what "normal" is and what changed (scope + timeline).
2. **The core debugging loop?** Observe → hypothesize → test → narrow → fix → verify → document.
3. **Symptom vs root cause?** Symptom is the visible effect; root cause is why it happens.
4. **Which pillar answers "what/when"?** Metrics.
5. **Which answers "where"?** Traces.
6. **Which answers "why"?** Logs.
7. **What is the 5 Whys?** Iteratively asking "why" to reach root cause.
8. **Correlation vs causation?** Co-occurrence isn't cause; verify with a test/change.
9. **What is bisection in time?** Narrowing to the deploy/config change that introduced the issue.
10. **What is bisection in space?** Narrowing to the failing service/component.
11. **What is a cascading failure?** One component's failure overloading others, spreading collapse.
12. **What causes retry storms?** Aggressive retries amplifying load during failures.
13. **Mitigation for retry storms?** Backoff + jitter, circuit breakers, retry budgets.
14. **What is a thundering herd?** Many clients hitting a resource simultaneously (e.g., cache expiry).
15. **Thundering herd fix?** Jitter, request coalescing, staggered expiry.
16. **What is a circuit breaker?** Stops calls to a failing dependency to allow recovery.
17. **What is a timeout's role?** Bounds waiting so failures don't hang and cascade.
18. **What is saturation?** How "full" a resource is (queues, CPU, connections).
19. **Latency triage first question?** Is it all requests or a subset (endpoint/tenant/region)?
20. **How to find the slow service in a trace?** Longest span on the critical path.
21. **How to confirm a hypothesis?** Change one variable and observe the predicted effect.
22. **Why change one variable at a time?** To attribute cause unambiguously.
23. **What is a red herring?** A correlated-but-unrelated signal that misleads.
24. **What is tail latency?** High-percentile (p99) latency affecting a minority of requests.
25. **Why does p99 matter more than avg?** Averages hide user-facing worst cases.
26. **What is a hotspot?** A disproportionately loaded shard/instance/key.
27. **How to detect a memory leak?** Steadily rising memory + eventual OOMKills.
28. **How to detect CPU throttling in K8s?** Latency up with `container_cpu_cfs_throttled` increasing.
29. **How to spot a noisy neighbor?** Contention correlating with another workload on the same node.
30. **What is blast radius?** The scope of impact of a failure/change.
31. **How to reduce blast radius?** Canaries, cell/shard isolation, feature flags, gradual rollout.
32. **What is a canary?** Releasing to a small subset first to catch regressions.
33. **What is a rollback-first mindset?** Restore service by reverting, then investigate.
34. **Mitigate vs fix?** Mitigate stops user pain now; fix addresses root cause after.
35. **What is a runbook?** Step-by-step guidance for a known alert/scenario.
36. **How to debug intermittent issues?** Increase sampling/logging, add tracing, reproduce under load.
37. **What is chaos testing's debugging value?** Reveals failure modes before they happen in prod.
38. **How to debug DNS in K8s?** Exec pod → nslookup; check CoreDNS + NetworkPolicies.
39. **How to debug a stuck rollout?** `rollout status`, events, readiness probes, image/pull errors.
40. **How to debug intermittent 5xx?** Trace error requests, inspect logs by trace_id, check dependency saturation.
41. **What is a dependency graph?** Map of service calls; essential for reasoning about cascades.
42. **How to detect a bad deploy fast?** Deploy annotations on dashboards + SLO burn alerts.
43. **What is a "known-good" comparison?** Diffing behavior/config against a healthy baseline/region.
44. **How to avoid confirmation bias?** Try to disprove your hypothesis, not just confirm it.
45. **What to capture during an incident?** Timeline, actions, signals, decisions (for the postmortem).
46. **What is time-to-detect (TTD)?** Time from failure start to detection.
47. **What is time-to-mitigate (TTM)?** Time from detection to restored service.
48. **How does good observability shrink MTTR?** Faster localization via correlated pillars.
49. **What's the debugging anti-pattern?** Randomly changing things without hypotheses ("shotgun debugging").
50. **One-liner method?** Start from the symptom, use metrics→traces→logs to localize, test one hypothesis at a time, mitigate fast, then fix and document.

### Day 14 Schedule
- **Study 1:** Debugging method + failure modes. **Lab 1:** Inject 500s/latency into your stack; localize via dashboards.
- **Study 2:** Cascades, retries, blast radius. **Lab 2:** Trace an error to its root cause using trace_id→logs.
- **Recall:** 50 questions; **Feynman:** narrate a full metric→trace→log investigation aloud.

### Fast-Learning Tips (Day 14)
- Keep a written hypothesis log for every bug — forcing explicit predictions kills shotgun debugging.
- Time yourself: how fast can you localize an injected fault? Track TTD/TTM to make improvement measurable.

---

# DAY 15 — Hands-On Kubernetes & Application Debugging

### Topics & Skills
- Deep `kubectl` debugging: `describe`, events, `logs --previous`, `exec`, `debug` (ephemeral + node), `cp`, `port-forward`
- Diagnosing common failures: CrashLoopBackOff, ImagePullBackOff, OOMKilled, Pending, Evicted, readiness failures, DNS, RBAC-denied
- Node-level debugging: `kubectl debug node/`, checking kubelet/runtime, disk/PID pressure
- Networking debugging: Service/Endpoints, NetworkPolicy, connectivity tests
- Performance: `kubectl top`, throttling, resource right-sizing from real usage
- Putting it together: full incident sim on your observability stack

### KodeKloud Labs
- KodeKloud **CKA Troubleshooting** section (application, control-plane, worker-node, networking failures)
- **Lightning Labs / Mock Exams** troubleshooting questions (timed)
- Personal lab: break your Day 11–13 stack five different ways and fix each under time pressure

### 50 Questions & Answers
1. **CrashLoopBackOff meaning?** Container keeps crashing; kubelet backs off restarts.
2. **First command for CrashLoopBackOff?** `kubectl logs <pod> --previous`.
3. **Common CrashLoop causes?** Bad command, missing config/secret, failing dependency, panics, OOM.
4. **ImagePullBackOff causes?** Wrong image/tag, private registry auth, network/registry down.
5. **How to fix registry auth?** Create an imagePullSecret and reference it in the pod/SA.
6. **OOMKilled exit code?** 137.
7. **How to confirm OOMKilled?** `kubectl describe pod` → Last State: OOMKilled.
8. **Fix for OOMKilled?** Raise memory limit or fix the leak/right-size.
9. **Pending pod causes?** Insufficient resources, unschedulable (affinity/taints), no matching PV.
10. **How to diagnose Pending?** `kubectl describe pod` → Events (FailedScheduling).
11. **Evicted pod cause?** Node resource pressure (memory/disk).
12. **How to see eviction reason?** `kubectl describe pod` / node conditions.
13. **Readiness failing symptom?** Pod Running but not Ready; no Service traffic.
14. **How to debug readiness?** Check probe config + `kubectl describe` probe events + hit endpoint via exec.
15. **`kubectl exec` use?** Run commands inside a container for live inspection.
16. **When does exec fail?** No shell in image (distroless) — use ephemeral debug container.
17. **`kubectl debug` ephemeral container?** Adds a debug container sharing the pod's namespaces.
18. **`kubectl debug node/<node>`?** Launches a privileged pod for node-level troubleshooting.
19. **`kubectl port-forward` use?** Access a pod/Service locally without exposing it.
20. **`kubectl cp` use?** Copy files in/out of a container.
21. **How to test a Service internally?** Exec a pod and curl `svc.ns.svc.cluster.local`.
22. **Service has no endpoints — cause?** Selector mismatch or no Ready pods.
23. **How to check endpoints?** `kubectl get endpoints/endpointslices <svc>`.
24. **DNS resolution failing — steps?** nslookup from a pod; check CoreDNS pods/logs; check egress NetworkPolicy.
25. **NetworkPolicy blocking traffic — how to tell?** Connectivity works only after allowing the flow.
26. **RBAC denied error form?** "forbidden: User X cannot <verb> <resource>".
27. **How to verify RBAC?** `kubectl auth can-i --as=...`.
28. **Node NotReady steps?** Check kubelet, container runtime, CNI, disk, and node conditions.
29. **Where are kubelet logs?** `journalctl -u kubelet` on the node.
30. **Static pod not starting — where to look?** `/etc/kubernetes/manifests` + kubelet logs.
31. **etcd unhealthy symptom?** API server errors/timeouts; cluster state operations fail.
32. **How to check control-plane pods?** `kubectl get pods -n kube-system`.
33. **How to see restart counts?** `kubectl get pods` RESTARTS column.
34. **How to find which node a pod is on?** `kubectl get pod -o wide`.
35. **CPU throttling detection?** `container_cpu_cfs_throttled_periods_total` rising with latency.
36. **How to right-size requests?** Observe p50–p95 usage via `kubectl top`/metrics and set requests accordingly.
37. **PVC stuck Pending cause?** No matching PV / provisioner / WaitForFirstConsumer without a scheduled pod.
38. **How to inspect a volume mount issue?** `describe pod` events + exec to check mount path.
39. **How to debug a failing initContainer?** `kubectl logs <pod> -c <init>`.
40. **How to see previous crash reason across restarts?** `describe` Last State + `logs --previous`.
41. **How to safely drain a node?** `kubectl drain <node> --ignore-daemonsets`.
42. **What blocks a drain?** PDBs and pods without controllers/emptyDir data.
43. **How to test connectivity between pods?** Deploy a netshoot/busybox pod and curl/ping.
44. **What is netshoot?** A container image packed with network debugging tools.
45. **How to check a container's env at runtime?** `kubectl exec -- env`.
46. **How to verify a mounted ConfigMap/Secret?** exec and read the mount path.
47. **How to reproduce load?** Use a load generator (hey/k6/fortio) against the Service.
48. **How to confirm a fix?** Re-run the failing scenario and watch SLIs return to normal.
49. **What to always do after fixing?** Document root cause + prevention (postmortem/runbook).
50. **One-liner K8s debugging order?** get → describe (events) → logs (--previous) → exec/debug → node/network → metrics → fix → verify.

### Day 15 Schedule
- **Study 1:** kubectl debug toolkit + failure signatures. **Lab 1:** Fix CrashLoop/ImagePull/OOM/Pending scenarios (timed).
- **Study 2:** Node + network + perf debugging. **Lab 2:** Full incident sim: inject fault, localize with observability, fix, verify.
- **Recall:** 50 questions; **Feynman:** explain your universal debugging order and why each step comes next.

### Fast-Learning Tips (Day 15)
- Build a one-page "failure signature → first command" cheat card; drill it until each failure triggers an instant first move.
- After each fixed scenario, redo it from a clean slate faster — speed under pressure is the CKA/on-call skill.

---

# DAY 16 — SRE Principles: SLI, SLO, SLA & Error Budgets

### Topics & Skills
- What SRE is (Google's model): reliability as a feature, engineering approach to operations
- **SLI** (indicator), **SLO** (objective), **SLA** (agreement) — precise definitions and relationships
- Choosing good SLIs (availability, latency, error rate, freshness, correctness) and the request/windowed model
- **Error budgets**: definition, math, and how they gate release velocity
- **Burn-rate alerting** (multi-window multi-burn) — the modern SLO alerting pattern
- Reliability vs cost vs velocity tradeoffs; the "nines" and their real cost

### KodeKloud Labs
- KodeKloud **SRE / DevOps** course modules on SLO/SLI
- Personal lab: define SLIs on your stack (from Days 7–13 metrics); compute an SLO and error budget in Grafana
- Build a multi-window burn-rate alert in vmalert/Alertmanager

### 50 Questions & Answers
1. **What is SRE?** Applying software engineering to operations to build reliable, scalable systems.
2. **SRE origin?** Google's practice, popularized by the SRE book.
3. **SLI definition?** A quantitative measure of a service's behavior (e.g., % successful requests).
4. **SLO definition?** A target value/range for an SLI over a window (e.g., 99.9% over 30 days).
5. **SLA definition?** A contract with consequences (credits/penalties) if SLOs are missed.
6. **SLI vs SLO vs SLA relationship?** SLI is measured; SLO is the internal target; SLA is the external promise (usually looser than SLO).
7. **Why SLO stricter than SLA?** Buffer so you fix issues before breaching the contract.
8. **Common SLI types?** Availability, latency, error rate, throughput, freshness, correctness, durability.
9. **Request-based SLI?** good events / valid events (e.g., non-5xx / total).
10. **Windowed SLI?** Good time windows / total windows.
11. **What is an error budget?** The allowed unreliability: 100% − SLO.
12. **99.9% monthly downtime budget?** ~43.2 minutes/month.
13. **99.99% monthly budget?** ~4.3 minutes/month.
14. **99% monthly budget?** ~7.2 hours/month.
15. **Purpose of error budgets?** Balance reliability vs feature velocity objectively.
16. **What happens when budget is exhausted?** Freeze risky releases; prioritize reliability work.
17. **What if budget is plentiful?** Ship faster / take more risk.
18. **What is burn rate?** How fast you're consuming the error budget relative to the SLO window.
19. **Burn rate of 1 means?** You'll exactly exhaust the budget by window's end.
20. **Burn rate of 14.4 over 1h (30d SLO)?** Consumes ~2% of budget in an hour — page-worthy.
21. **Why multi-window burn-rate alerts?** Fast window catches acute; long window confirms sustained; reduces false pages.
22. **Typical multi-burn setup?** e.g., 1h+5m at burn 14.4, and 6h+30m at burn 6.
23. **Why alert on burn rate not raw errors?** Ties paging to user-impacting budget consumption.
24. **What makes a good SLI?** User-centric, measurable, and correlates with user happiness.
25. **What is the "valid events" nuance?** Exclude events you shouldn't be judged on (e.g., 4xx client errors, sometimes).
26. **Availability SLI formula?** successful requests / valid requests.
27. **Latency SLI formulation?** % of requests faster than threshold (e.g., 95% < 300ms).
28. **Why threshold-based latency, not average?** Percentile thresholds reflect user experience; averages hide tails.
29. **What is the "happiness test" for SLOs?** If the SLO is met, are users generally happy?
30. **How many nines is "too many"?** When cost/effort exceeds user-perceived benefit.
31. **Cost of each added nine?** Roughly exponential effort/cost.
32. **What is a target percentile SLO example?** p99 latency < 500ms over 28 days.
33. **Rolling vs calendar window?** Rolling (last 30d) is smoother; calendar (this month) aligns with reporting.
34. **Who owns the SLO?** Service owners with SRE partnership; product must agree.
35. **What is an SLO document?** Records SLIs, targets, windows, rationale, and consequences.
36. **What is toil's link to SLOs?** Freed reliability capacity funds toil reduction when budget is healthy.
37. **What is a stretch SLO vs achievable SLO?** Achievable reflects reality; stretch is aspirational — don't page on stretch.
38. **Can you have too-strict SLO?** Yes — causes over-investment and constant paging.
39. **What is the "error budget policy"?** Agreed actions triggered by budget status.
40. **How to measure SLIs?** From metrics (Prometheus/VM), logs, or synthetic probes.
41. **What is a synthetic/black-box SLI?** Measured by an external prober hitting the service.
42. **White-box vs black-box SLI?** White-box from internal metrics; black-box from outside the user's view.
43. **Why include both?** Internal detail + true user perspective.
44. **What is availability's relationship to dependencies?** Your ceiling is bounded by critical dependencies' availability.
45. **How to combine dependency availabilities (serial)?** Multiply (0.999 × 0.999 ≈ 0.998).
46. **What is graceful degradation?** Reduced functionality instead of total failure to protect SLOs.
47. **Error budget and postmortems link?** Budget burns often trigger postmortems.
48. **How do SLOs guide alerting?** Alert on SLO burn (symptoms), silence cause-only noise.
49. **What is the first SLO to define?** Usually availability of the most critical user journey.
50. **One-liner SLO stack?** Measure user-centric SLIs, set SLOs with error budgets, and let burn-rate alerts + budget policy govern paging and release speed.

### Day 16 Schedule
- **Study 1:** SLI/SLO/SLA + error budget math. **Lab 1:** Define availability + latency SLIs on your stack in Grafana.
- **Study 2:** Burn-rate alerting. **Lab 2:** Build a multi-window multi-burn alert; simulate a burn.
- **Recall:** 50 questions; **Feynman:** explain error budgets to a "product manager."

### Fast-Learning Tips (Day 16)
- Memorize the nines→downtime table cold; it's the most-used SRE fact in interviews and reviews.
- Compute a real error budget from your own metrics — abstract math becomes concrete instantly.

---

# DAY 17 — Toil, Automation & Release Engineering

### Topics & Skills
- **Toil**: definition, why it's harmful, the ≤50% toil budget, identifying and eliminating it
- Automation mindset: from manual → documented → scripted → self-service → autonomous
- Release engineering: CI/CD, progressive delivery (**canary, blue-green, rolling**), feature flags
- Deployment safety: automated rollback, health-gated promotion, freezes tied to error budget
- Configuration as code, GitOps (Argo CD/Flux) concept, immutable infrastructure
- Reducing risk: small batches, gradual rollout, observability-gated deploys

### KodeKloud Labs
- KodeKloud **CI/CD**, **GitOps/Argo CD**, and **Helm** labs
- Personal lab: set up a canary rollout (e.g., Argo Rollouts) with metric-based analysis using your Prometheus/VM SLIs
- Automate a repetitive task from earlier days into a script/pipeline

### 50 Questions & Answers
1. **What is toil?** Manual, repetitive, automatable, tactical work that scales with service size and lacks lasting value.
2. **Why limit toil?** It crowds out engineering, causes burnout, and doesn't scale.
3. **SRE toil budget?** Keep toil ≤50% of time; the rest for engineering.
4. **Characteristics of toil?** Manual, repetitive, automatable, reactive, no enduring value, O(n) with growth.
5. **Is all operational work toil?** No — design, automation, and improvement aren't toil.
6. **First step to eliminate toil?** Measure/identify it, then automate the highest-cost items.
7. **Automation maturity ladder?** Manual → documented → scripted → self-service → fully automated.
8. **What is CI?** Continuous Integration — frequently merging and automatically building/testing code.
9. **What is CD?** Continuous Delivery/Deployment — automated release to staging/prod.
10. **Canary deployment?** Release to a small % first, watch metrics, then expand.
11. **Blue-green deployment?** Two environments; switch traffic from old (blue) to new (green) instantly.
12. **Rolling deployment?** Gradually replace old pods with new (K8s default).
13. **Canary vs blue-green tradeoff?** Canary limits blast radius progressively; blue-green enables instant switch/rollback but doubles resources.
14. **What is a feature flag?** Toggle enabling/disabling features without redeploying.
15. **Why decouple deploy from release?** Deploy code dark, then enable via flag to control exposure.
16. **What is progressive delivery?** Gradual, metric-gated rollout (canary + flags + automated analysis).
17. **What is automated rollback?** Reverting automatically when health/SLIs degrade.
18. **What gates a canary promotion?** Success metrics (error rate, latency) within thresholds.
19. **What is GitOps?** Git as the single source of truth; a controller reconciles cluster to Git.
20. **GitOps tools?** Argo CD, Flux.
21. **Benefit of GitOps?** Auditability, easy rollback (git revert), drift detection.
22. **What is immutable infrastructure?** Replace rather than modify servers/images in place.
23. **Why immutable?** Reproducibility, no config drift, easy rollback.
24. **What is configuration as code?** Storing config in version control, applied via automation.
25. **What is Argo Rollouts?** A controller for advanced canary/blue-green with analysis.
26. **What is analysis-based promotion?** Querying metrics (Prometheus/VM) to decide promotion.
27. **Why small batch releases?** Smaller blast radius, easier debugging, faster feedback.
28. **What is a deployment freeze?** Pausing risky releases (e.g., during exhausted error budget or peak events).
29. **What is a rollout budget/window?** Allowed pace/timing of changes.
30. **What is a change failure rate (DORA)?** % of deployments causing a failure/incident.
31. **DORA four metrics?** Deployment frequency, lead time for changes, change failure rate, MTTR.
32. **What is lead time for changes?** Time from commit to running in production.
33. **What is MTTR?** Mean time to restore/recovery.
34. **What is a release train?** Regular scheduled release cadence.
35. **What is idempotent automation?** Re-running yields the same result safely.
36. **Why prefer declarative automation?** Convergence to desired state; safer re-runs.
37. **What is self-service tooling?** Letting teams operate safely without SRE hand-holding.
38. **What is a runbook automation?** Turning runbook steps into scripts/bots.
39. **What is chatops?** Operating systems via chat commands with audit trail.
40. **Risk of over-automation?** Automating a broken process faster; automate good processes.
41. **What is a pre-deploy check?** Automated validation (tests, policy, canary analysis) before promotion.
42. **What is policy-as-code?** Enforcing rules (OPA/Kyverno) automatically in the pipeline/cluster.
43. **How does observability gate deploys?** Promotion depends on SLI health post-deploy.
44. **What reduces MTTR most in releases?** Fast, automated rollback + good signals.
45. **How to sequence a safe rollout?** Deploy → canary small % → analyze SLIs → progressively expand → full → monitor.
46. **What is a bake time?** Wait period to observe a canary before promotion.
47. **What is drift detection?** Noticing cluster state diverging from Git (GitOps).
48. **Why version everything?** Reproducibility, rollback, and audit.
49. **Toil vs overhead?** Toil scales with load; overhead (meetings, admin) is different.
50. **One-liner release engineering?** Ship small, automated, declarative changes with metric-gated progressive delivery and instant rollback — governed by the error budget.

### Day 17 Schedule
- **Study 1:** Toil + automation ladder + DORA. **Lab 1:** Automate a repetitive earlier task into a script/pipeline.
- **Study 2:** Progressive delivery + GitOps. **Lab 2:** Argo Rollouts canary gated by your Prometheus/VM SLIs.
- **Recall:** 50 questions; **Feynman:** explain how error budgets control release velocity.

### Fast-Learning Tips (Day 17)
- Identify the single most repetitive thing you did in Days 1–16 and automate it today — meta-learning through application.
- Do one canary that *fails* analysis and auto-rolls-back; watching the safety net catch is the lesson.

---

# DAY 18 — Incident Management, On-Call & Postmortems

### Topics & Skills
- Incident lifecycle: detect → triage → mitigate → resolve → learn
- Incident command: roles (Incident Commander, Ops/Communications/Scribe), clear ownership
- Severity levels, escalation, and communication (status updates, stakeholders)
- **On-call**: healthy rotations, alert quality, paging philosophy, handoffs, sustainable load
- **Blameless postmortems**: structure, root-cause analysis, action items, learning culture
- Key metrics: MTTD/MTTA/MTTR, incident review cadence

### KodeKloud Labs
- KodeKloud **SRE/DevOps** incident & on-call modules (concept-heavy; pair with practice)
- Personal lab: run a solo incident simulation on your stack using an incident timeline template
- Write a full blameless postmortem for a fault you injected on Day 14/15

### 50 Questions & Answers
1. **What is an incident?** An unplanned disruption or degradation requiring urgent response.
2. **Incident lifecycle stages?** Detect → triage → mitigate → resolve → learn.
3. **What is triage?** Assessing scope, severity, and impact to prioritize response.
4. **Mitigate vs resolve?** Mitigate stops user pain; resolve removes the root cause.
5. **What is an Incident Commander (IC)?** The single person coordinating the response and decisions.
6. **Does the IC fix the problem?** No — they coordinate; responders fix.
7. **Communications lead role?** Handles stakeholder/status updates so responders can focus.
8. **Scribe role?** Records timeline, actions, and decisions in real time.
9. **Why define roles?** Prevents chaos, duplicated effort, and missed comms.
10. **What is severity (SEV)?** A ranking of incident impact (e.g., SEV1 critical → SEV4 minor).
11. **What triggers escalation?** Severity thresholds, time limits, or lack of progress.
12. **What is MTTD?** Mean time to detect.
13. **What is MTTA?** Mean time to acknowledge.
14. **What is MTTR?** Mean time to resolve/restore.
15. **How to reduce MTTD?** Good SLO-based alerting + observability.
16. **How to reduce MTTA?** Reliable paging + healthy on-call.
17. **How to reduce MTTR?** Runbooks, good signals, practiced responders, easy rollback.
18. **What is a status page?** External/internal communication of incident status.
19. **What is a war room / incident channel?** A dedicated space to coordinate response.
20. **On-call primary vs secondary?** Primary responds first; secondary backs up/escalation.
21. **Healthy on-call load?** Limited pages per shift so responders can sleep/recover.
22. **What is alert fatigue?** Desensitization from too many/noisy alerts → missed real ones.
23. **How to fix alert fatigue?** Delete/tune noisy alerts; alert on symptoms/SLO burn.
24. **What is an actionable alert?** One that requires human action and has a clear next step.
25. **Should every alert page?** No — page only for urgent, actionable, user-impacting issues; others are tickets.
26. **What is a runbook's role on-call?** Fast, tested steps to diagnose/mitigate a known alert.
27. **What is a handoff?** Transferring on-call context between shifts.
28. **What is a blameless postmortem?** A retrospective focused on systemic causes, not individual blame.
29. **Why blameless?** Encourages honesty and learning; people don't hide mistakes.
30. **Core postmortem sections?** Summary, impact, timeline, root cause, resolution, action items, lessons.
31. **What is a contributing factor?** A condition that helped cause/worsen the incident.
32. **Root cause vs trigger?** Trigger sets it off; root cause is the underlying weakness.
33. **What is an action item?** A concrete, owned, tracked task to prevent recurrence.
34. **Good action item traits?** Specific, assigned, prioritized, with a due date.
35. **What is the 5 Whys in postmortems?** Iterative questioning to reach systemic causes.
36. **When to write a postmortem?** For significant/SLO-impacting or novel incidents (per policy).
37. **What is a near-miss?** An event that almost caused an incident; worth reviewing too.
38. **What is toil from incidents?** Repeated manual firefighting — signal to automate/fix root cause.
39. **What is an error budget's role in incidents?** Budget burn may trigger reviews and reliability focus.
40. **What is a follow-the-sun rotation?** Global on-call handoffs to avoid night pages.
41. **What is paging vs ticketing?** Paging = urgent now; ticketing = handle during business hours.
42. **What is an incident timeline?** Chronological record of detection, actions, and recovery.
43. **What is a comms cadence?** Regular update interval to stakeholders during an incident.
44. **How to prevent recurrence?** Track action items to completion and verify effectiveness.
45. **What is a game day?** Practiced incident/fault drill to build response muscle.
46. **What is chaos engineering's tie to incidents?** Proactively surfaces failure modes to prepare responders.
47. **What kills postmortem culture?** Blame, unfinished action items, and ritual-without-learning.
48. **What is a SEV1 typically?** Major outage / broad user impact / data risk.
49. **What is on-call sustainability?** Load, comp, and rotation design that avoids burnout.
50. **One-liner incident management?** Detect fast, assign clear roles, mitigate first, communicate steadily, then run a blameless postmortem with tracked action items.

### Day 18 Schedule
- **Study 1:** Incident lifecycle + roles + severity. **Lab 1:** Run a solo incident sim with an IC/scribe template.
- **Study 2:** On-call + alert quality + postmortems. **Lab 2:** Write a full blameless postmortem for a Day-15 fault.
- **Recall:** 50 questions; **Feynman:** run a mock incident aloud, narrating IC decisions.

### Fast-Learning Tips (Day 18)
- Practice being the IC out loud during your fault sim — the coordination skill only forms by doing it.
- Write one real postmortem; the structure becomes automatic and interview-ready.

---

# DAY 19 — Reliability Engineering: Capacity, Resilience & Chaos

### Topics & Skills
- Capacity planning: demand forecasting, headroom, load testing, and resource modeling
- Scalability patterns: horizontal vs vertical, statelessness, sharding, caching, queues
- Resilience patterns: redundancy, retries+backoff+jitter, circuit breakers, bulkheads, rate limiting, load shedding, graceful degradation
- Failure domains, redundancy (N+1/N+2), multi-AZ/region, blast-radius reduction
- **Chaos engineering**: hypothesis-driven fault injection, steady-state, blast-radius control
- Reliability review of your full stack (K8s + observability)

### KodeKloud Labs
- KodeKloud **SRE reliability**, **load testing**, and **chaos** modules if available
- Personal lab: load-test your stack (k6/fortio), find the breaking point, add HPA/limits/PDBs
- Run a chaos experiment (e.g., kill pods / inject latency) and validate steady-state holds

### 50 Questions & Answers
1. **What is capacity planning?** Ensuring enough resources to meet forecasted demand with headroom.
2. **What is headroom?** Spare capacity buffer for spikes/failures.
3. **Why load test?** To find limits and validate capacity before users do.
4. **What is a breaking point?** The load at which SLOs start failing.
5. **Vertical scaling?** Bigger instances (more CPU/RAM per node/pod).
6. **Horizontal scaling?** More instances/replicas.
7. **Why prefer horizontal?** Better fault tolerance and near-linear scale (if stateless).
8. **Why is statelessness key to scaling?** Any replica can serve any request; easy to add/remove.
9. **What is sharding?** Partitioning data/load across nodes by a key.
10. **What is caching's reliability role?** Reduces load and latency but adds staleness/invalidations concerns.
11. **What is a queue's role?** Buffers bursts and decouples producers/consumers.
12. **What is redundancy?** Extra components so one failure doesn't cause outage.
13. **N+1 redundancy?** One spare beyond required capacity.
14. **What is a failure domain?** A scope that fails together (node, rack, AZ, region).
15. **Why spread across AZs?** Survive a zone failure.
16. **What is a bulkhead?** Isolating resources so one overload doesn't sink everything.
17. **What is a circuit breaker?** Stops calling a failing dependency to allow recovery.
18. **Retry best practice?** Exponential backoff + jitter + a retry budget/cap.
19. **Why jitter?** Prevents synchronized retry storms.
20. **What is rate limiting?** Capping request rate to protect a service.
21. **What is load shedding?** Dropping low-priority requests under overload to preserve core function.
22. **What is graceful degradation?** Reduced functionality instead of full failure.
23. **What is backpressure?** Signaling upstream to slow down when overloaded.
24. **What is a timeout budget?** Bounding total time across dependency calls.
25. **What is a dependency's availability impact?** Serial deps multiply; your ceiling is limited by them.
26. **How to reduce blast radius?** Cells/shards, canaries, isolation, gradual rollout.
27. **What is cell-based architecture?** Independent isolated units serving subsets of users.
28. **What is chaos engineering?** Deliberately injecting failures to build confidence in resilience.
29. **What is steady-state in chaos?** The normal measurable behavior you expect to hold.
30. **Chaos experiment structure?** Define steady-state → hypothesize → inject fault → observe → learn.
31. **Why control blast radius in chaos?** To limit real user impact during experiments.
32. **Start chaos where?** In staging/small scope, then production with guardrails.
33. **Example chaos faults?** Kill pods, add latency, drop packets, fail a dependency/AZ.
34. **What tool for K8s chaos?** e.g., Chaos Mesh, LitmusChaos.
35. **What does killing a pod test?** Self-healing, redundancy, and readiness handling.
36. **What is a stress test vs load test?** Stress pushes beyond limits; load tests expected demand.
37. **What is a soak test?** Sustained load over long time to catch leaks/degradation.
38. **What is a spike test?** Sudden large load increase to test elasticity.
39. **How does HPA aid reliability?** Adds capacity automatically under load.
40. **How do PDBs aid reliability?** Preserve minimum availability during voluntary disruptions.
41. **Why set resource requests correctly?** Prevents noisy neighbors and scheduling starvation.
42. **What is over-provisioning's tradeoff?** More reliability/headroom vs higher cost.
43. **What is autoscaling lag risk?** Scale-up may trail sudden spikes — pre-scale for known events.
44. **What is pre-scaling?** Scaling ahead of predictable demand (sales, launches).
45. **What is a single point of failure (SPOF)?** A component whose failure takes down the system.
46. **How to find SPOFs?** Dependency mapping + failure analysis + chaos.
47. **What is disaster recovery (DR)?** Plans/capability to recover from major failures (RTO/RPO).
48. **RTO vs RPO?** RTO = max acceptable downtime; RPO = max acceptable data loss.
49. **What validates resilience claims?** Actually testing them (game days/chaos), not assuming.
50. **One-liner reliability engineering?** Plan capacity with headroom, design for redundancy and graceful failure, limit blast radius, and prove resilience with load and chaos testing.

### Day 19 Schedule
- **Study 1:** Capacity + scalability + resilience patterns. **Lab 1:** Load-test to breaking point; add HPA/PDB/limits.
- **Study 2:** Chaos engineering + DR. **Lab 2:** Run a chaos experiment; verify steady-state and self-healing.
- **Recall:** 50 questions; **Feynman:** explain 3 resilience patterns and the failure each prevents.

### Fast-Learning Tips (Day 19)
- Break your own system on purpose and measure whether it degrades gracefully — resilience is only real once tested.
- Tie each resilience pattern to a Day-14 failure mode so patterns become answers, not vocabulary.

---

# DAY 20 — Capstone: Full-Stack SRE Integration

### Topics & Skills
Build and operate one complete system that exercises everything. **Deliverable:** a Kubernetes-hosted microservice app, fully observable, with SLOs, alerts, an automated release pipeline, and a written incident + postmortem.

**Capstone checklist:**
1. **Deploy** a 2–3 service app on K8s (Deployments, Services, Ingress, ConfigMaps/Secrets, PVC, HPA, probes, RBAC).
2. **Instrument** with OpenTelemetry (traces + metrics) → OTel Collector.
3. **Metrics** → VictoriaMetrics (via remote_write / Collector); dashboards in Grafana.
4. **Logs** → Loki via Fluent Bit/Collector; structured + trace-correlated.
5. **Traces** → Jaeger/Tempo; correlate logs↔traces↔metrics in Grafana.
6. **Define SLIs/SLOs** (availability + latency) and a **multi-window burn-rate alert** via vmalert/Alertmanager.
7. **Release pipeline**: canary via Argo Rollouts gated by SLIs; automated rollback.
8. **Inject a failure**, run an incident (IC + timeline), mitigate, then write a **blameless postmortem**.
9. **Chaos/load test** to validate resilience and capacity.

### KodeKloud Labs
- KodeKloud **Ultimate Mock/Playground** for any weak spot surfaced during the capstone
- Redo (timed) the labs for whichever domain felt shakiest during recall this week

### 50 Questions & Answers (Integrative — the whole stack)
1. **Trace a user request end-to-end through your stack.** Ingress → Service → pod (span created) → downstream service (context propagated) → response; metrics/logs/traces emitted throughout.
2. **A latency alert fires — your first three moves?** Check golden-signals dashboard, isolate scope, then trace slow requests to the offending span.
3. **Which pillar do you start with for a latency spike?** Metrics (confirm/scope), then traces (localize), then logs (why).
4. **How do metrics get from app to Grafana?** App → OTel Collector → VictoriaMetrics (remote_write) → Grafana (Prometheus datasource).
5. **How do you correlate a log line to a trace?** Shared trace_id + Grafana derived field linking to the trace.
6. **What defines "up" for your SLO?** Successful (non-5xx) requests over valid requests meeting latency threshold.
7. **Your error budget is exhausted — what happens?** Freeze risky releases; prioritize reliability per the error-budget policy.
8. **A canary's error rate rises — what should happen?** Analysis fails, promotion halts, automated rollback triggers.
9. **How does the Collector protect backends?** Batching, memory_limiter, sampling, queue/retry, fan-out.
10. **How do you keep metric cardinality sane end-to-end?** Avoid unbounded labels at instrumentation and relabel/drop at the Collector/vmagent.
11. **How do you scale ingestion if VM is overloaded?** Add vminsert (and vmstorage for capacity); reduce cardinality.
12. **Pod is CrashLoopBackOff during rollout — response?** `logs --previous`, check config/secret, fix or rollback.
13. **Service returns intermittent 5xx — approach?** Trace error requests by trace_id, inspect dependency saturation, check retries/timeouts.
14. **How do probes interact with zero-downtime deploys?** Readiness gates traffic; rolling update waits for Ready pods.
15. **How do you page only on user impact?** Multi-window burn-rate alerts on SLOs, not raw resource metrics.
16. **Where do you enforce PII redaction?** At the app and/or Collector before storage.
17. **How do you prove your system is resilient?** Chaos experiments + load tests validating steady-state and SLOs.
18. **How does GitOps help incident recovery?** `git revert` restores known-good declared state; controller reconciles.
19. **What's your MTTR-reduction toolkit?** Good SLO alerts, correlated observability, runbooks, and fast rollback.
20. **How do you right-size a service?** Observe real usage (metrics), set requests near p50–p90, cap limits, add HPA.
21. **How do traces reduce debugging time?** They localize the slow/failing span instantly vs guessing.
22. **What's the difference between a Service and Ingress in your app?** Service = internal L4 endpoint; Ingress = external L7 router.
23. **How do you avoid alert fatigue in the capstone?** Symptom/SLO-based alerts, grouping, inhibition, sensible `for`.
24. **What happens if CoreDNS is down?** Service discovery fails cluster-wide — many components break.
25. **How does the OTel Collector add K8s context to telemetry?** k8sattributes processor enriches with pod/namespace/node.
26. **How would you find your highest-cost metric?** VM cardinality explorer / tsdb status.
27. **What's the release flow with safety?** Deploy → canary → SLI analysis → progressive expand → full → monitor; rollback on failure.
28. **How do you validate an SLO is realistic?** Backtest against historical SLIs; ensure achievable and user-aligned.
29. **How do you handle a retry storm in your app?** Backoff+jitter, circuit breaker, retry budget, load shedding.
30. **How do you reduce blast radius of a bad deploy?** Canary + feature flags + gradual rollout + fast rollback.
31. **What signals go on your primary dashboard?** RED/golden signals: traffic, errors, latency percentiles, saturation.
32. **How do logs, metrics, traces share identity?** service.name (resource) + trace/span IDs correlate them.
33. **Where does sampling belong for traces?** Head at SDK for volume; tail at Collector to keep errors/slow traces.
34. **How do you detect a memory leak in the capstone?** Rising memory metric trend → eventual OOMKilled.
35. **What's your first action in a SEV1?** Assign IC, mitigate to restore service, communicate.
36. **How do you make the app horizontally scalable?** Statelessness + HPA + externalized state/storage.
37. **How does a PDB protect the app during node drain?** Ensures a minimum number stay available.
38. **What's the value of structured logs here?** Reliable querying/correlation in Loki via LogQL.
39. **How do you gate a deploy on observability?** Argo Rollouts analysis querying VM/Prometheus SLIs.
40. **How do you connect a spike in Grafana to a code change?** Deploy annotations overlaid on graphs.
41. **What's your end-to-end verification after a fix?** Re-run the failing scenario; watch SLIs return to target.
42. **Why VictoriaMetrics over vanilla Prometheus storage here?** Long-term retention, compression, and horizontal scale.
43. **What does vmalert do in your stack?** Evaluates SLO/recording/alerting rules against VM → Alertmanager.
44. **How do you keep the on-call sustainable?** Actionable alerts only, runbooks, and reduced toil via automation.
45. **How do you demonstrate graceful degradation?** Load-shed non-critical features while core stays within SLO.
46. **What's the difference between mitigation and postmortem action items?** Mitigation restores now; action items prevent recurrence.
47. **What proves you understand the reconciliation loop?** Delete a pod and watch the controller recreate it to match desired state.
48. **How do you export both metrics and traces from one app?** OTel SDK → OTLP → Collector → VM (metrics) + Jaeger (traces).
49. **What's the single most important SRE habit?** Measure user-centric reliability (SLOs) and let data drive decisions.
50. **Summarize the whole stack in one sentence.** Declarative Kubernetes workloads, instrumented with OpenTelemetry, observed via VictoriaMetrics/Loki/Jaeger in Grafana, governed by SLOs and error budgets, released through metric-gated automation, and operated with blameless incident practice.

### Day 20 Schedule
- **Study 1 (review):** Fill weakest-domain gaps surfaced during the week. **Lab 1:** Build the capstone stack (steps 1–5).
- **Study 2:** SLO + release + incident design. **Lab 2:** Steps 6–9: SLOs/alerts, canary, incident+postmortem, chaos/load.
- **Recall:** 50 integrative questions; **Feynman:** present your capstone architecture end-to-end as if to an interview panel.

### Fast-Learning Tips (Day 20)
- Teaching = the ultimate test: record a 10-minute walkthrough of your capstone; any stumble marks a real gap.
- Turn every gap found today into an Anki card and a mini-lab for spaced review over the following weeks.

---

## Post-Plan: Making It Stick (Spaced Repetition Beyond Day 20)

- **Anki daily (15 min):** Review the missed-question cards you carded each day. This is where 150 hours converts into long-term proficiency.
- **Weekly rebuild (Weeks 3–6):** Once a week, rebuild one slice of the capstone from scratch (e.g., "deploy + instrument + SLO alert in 90 min").
- **Certification anchors:** The plan maps closely to **CKA** (Days 1–6, 15), **PCA – Prometheus Certified Associate** (Days 7, 9), and SRE interview prep (Days 16–20). Consider scheduling CKA/PCA ~4–6 weeks out to force retention.
- **Teach one thing publicly:** Write a short blog/README on your capstone. Public explanation is the strongest retention forcing-function.

## Meta-Learning Cheat Sheet (apply every single day)
1. **Prime → Learn → Do → Recall → Teach.** Never skip Do or Recall.
2. **Retrieval > review.** Closed-book questions beat re-reading, always.
3. **Space it.** Re-test yesterday every morning; card every miss.
4. **Interleave.** Mix topics/labs; desirable difficulty = durable memory.
5. **Deliberately break things.** Failure you cause and fix teaches faster than success you follow.
6. **Protect sleep.** 7 hours is a learning tool, not a luxury — memory consolidates during sleep.
7. **One-variable debugging.** In labs and life, change one thing at a time.
8. **Feynman nightly.** If you can't explain it simply, that's tomorrow's first review.









