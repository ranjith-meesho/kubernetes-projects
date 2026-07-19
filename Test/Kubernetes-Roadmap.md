# Kubernetes in 20 Hours — The 80/20 Beginner Roadmap

This roadmap covers the ~20% of Kubernetes concepts that deliver ~80% of real-world value: Pods, Deployments, Services, ConfigMaps/Secrets, StatefulSets, Ingress, Namespaces/RBAC, Persistent Volumes, and Troubleshooting. Advanced topics (Operators, CRDs, service meshes, cluster internals) are intentionally excluded.

**Total time:** 20 hours across 6 days (3–4 hrs/day). Do the days in order — each builds on the last.

**Setup (do this once, ~20 min, not counted in the 20 hours):**
1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) (free)
2. Install [Minikube](https://minikube.sigs.k8s.io/docs/start/) (free, official) — `brew install minikube` (Mac) or see docs for Windows/Linux
3. Install `kubectl` — [official install docs](https://kubernetes.io/docs/tasks/tools/)
4. Start your cluster: `minikube start`
5. Verify: `kubectl get nodes` — you should see one node in `Ready` status

No paid tools are used anywhere in this roadmap.

---

## Day 1 (4 hrs) — Pods & Deployments

### Core Concepts
1. **Pod** — the smallest deployable unit in Kubernetes; a wrapper around one or more tightly-coupled containers that share network/storage.
2. **Deployment** — a controller that manages Pod replicas, handles rolling updates, and self-heals crashed Pods.
3. **ReplicaSet** — the mechanism (managed by Deployments, rarely used directly) that ensures N copies of a Pod are running.
4. **Labels & Selectors** — key-value tags used to identify and group objects; the "glue" connecting Deployments to Pods and (later) Services to Pods.
5. **kubectl basics** — the CLI you'll use for everything: `get`, `describe`, `apply`, `delete`, `logs`, `exec`.

### Hands-On Examples

**Example 1 — Run a Pod imperatively:**
```bash
kubectl run nginx-pod --image=nginx:latest --port=80
kubectl get pods
kubectl describe pod nginx-pod
```

**Example 2 — Declarative Pod YAML** (`pod.yaml`):
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: nginx-pod
  labels:
    app: nginx
spec:
  containers:
  - name: nginx
    image: nginx:latest
    ports:
    - containerPort: 80
```
```bash
kubectl apply -f pod.yaml
kubectl delete -f pod.yaml
```

**Example 3 — Deployment YAML** (`deployment.yaml`):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.25
        ports:
        - containerPort: 80
```
```bash
kubectl apply -f deployment.yaml
kubectl get deployments
kubectl get pods -o wide
kubectl scale deployment nginx-deployment --replicas=5
kubectl set image deployment/nginx-deployment nginx=nginx:1.26
kubectl rollout status deployment/nginx-deployment
kubectl rollout undo deployment/nginx-deployment
```

### Project: Deploy Your First Application
Using Minikube, create a Deployment running `nginx:1.25` with 3 replicas. Scale it to 5, then perform a rolling update to `nginx:1.26`, watch the rollout with `kubectl rollout status`, then roll it back. Confirm at every step with `kubectl get pods -o wide`.

### Free Resources
- [Kubernetes Docs: Pods](https://kubernetes.io/docs/concepts/workloads/pods/)
- [Kubernetes Docs: Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
- [Kubernetes Basics Interactive Tutorial](https://kubernetes.io/docs/tutorials/kubernetes-basics/) (official, browser-based, free)
- [Minikube Get Started](https://minikube.sigs.k8s.io/docs/start/)
- [Play with Kubernetes](https://labs.play-with-k8s.com/) (free browser sandbox, no install)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)

### Top 50 Questions & Answers

1. **Q: What is a Pod?**
   A: The smallest deployable unit in Kubernetes. It wraps one or more containers that share the same network namespace (IP address) and storage volumes. You almost never create bare Pods in production — they're managed by controllers like Deployments.

2. **Q: Why does Kubernetes use Pods instead of scheduling containers directly?**
   A: Some containers need to be co-located and share resources (e.g., a main app + a logging sidecar). Pods give Kubernetes an atomic unit to schedule, scale, and network — simplifying the scheduler's job.

3. **Q: Can a Pod contain multiple containers?**
   A: Yes. Multi-container Pods share localhost networking and can share volumes — common patterns are sidecar (logging/proxy) and init containers (setup tasks before the main container starts).

4. **Q: What is a Deployment?**
   A: A higher-level controller that manages ReplicaSets and Pods for you — declaring "I want N replicas of this Pod spec running" and Kubernetes continuously works to make that true.

5. **Q: What's the difference between a Pod, a ReplicaSet, and a Deployment?**
   A: Pod = single running instance. ReplicaSet = ensures a fixed number of identical Pods exist. Deployment = manages ReplicaSets over time, enabling rolling updates and rollbacks. In practice, you write Deployments; Kubernetes creates the ReplicaSets and Pods for you.

6. **Q: Why shouldn't you create Pods directly in production?**
   A: A bare Pod has no self-healing — if the node dies or the Pod crashes, nothing recreates it. A Deployment (or other controller) watches and recreates Pods automatically.

7. **Q: What does `kubectl get pods` show you?**
   A: Pod name, READY count (ready containers / total containers), STATUS (Running, Pending, CrashLoopBackOff, etc.), RESTARTS, and AGE.

8. **Q: How do you view detailed information and recent events for a Pod?**
   A: `kubectl describe pod <pod-name>` — shows spec, container statuses, and an Events log at the bottom, which is usually the first place to look when debugging.

9. **Q: How do you view a container's logs?**
   A: `kubectl logs <pod-name>`. For a specific container in a multi-container Pod: `kubectl logs <pod-name> -c <container-name>`. Add `-f` to stream/follow.

10. **Q: How do you get a shell inside a running container?**
    A: `kubectl exec -it <pod-name> -- /bin/sh` (or `/bin/bash` if available). Useful for inspecting files, testing connectivity, or debugging from inside.

11. **Q: What are Labels used for?**
    A: Arbitrary key-value pairs attached to objects (e.g., `app: nginx`) used to organize and select subsets of resources — Deployments find their Pods via label selectors, and Services find their target Pods the same way.

12. **Q: What is a Selector?**
    A: A query that matches objects by their labels. A Deployment's `spec.selector.matchLabels` must match the labels in its Pod template, or Kubernetes will reject the Deployment as invalid.

13. **Q: What happens if you delete a Pod that's managed by a Deployment?**
    A: The Deployment's ReplicaSet controller notices the replica count dropped below desired and immediately creates a replacement Pod. This is the self-healing behavior a bare Pod does not have.

14. **Q: Scenario — you run `kubectl delete pod nginx-abc123` and a new Pod appears seconds later with a different name. Why?**
    A: It's managed by a Deployment/ReplicaSet. The controller detected the missing replica and created a new Pod (new name/ID, same spec) to restore the desired replica count.

15. **Q: What is a rolling update?**
    A: A Deployment strategy that gradually replaces old Pods with new ones (a few at a time), keeping the app available throughout — as opposed to killing everything and starting fresh (recreate strategy).

16. **Q: How do you trigger a rolling update to a new image version?**
    A: `kubectl set image deployment/<name> <container-name>=<new-image>` or edit the YAML's image field and `kubectl apply -f`.

17. **Q: How do you check the status of a rollout?**
    A: `kubectl rollout status deployment/<name>` — blocks and reports progress until the rollout completes or fails.

18. **Q: How do you roll back a bad deployment?**
    A: `kubectl rollout undo deployment/<name>` reverts to the previous ReplicaSet/revision. Use `kubectl rollout history deployment/<name>` to see revisions and `--to-revision=N` to target a specific one.

19. **Q: Scenario — you deployed a new image and now all Pods are crash-looping. What's the fastest recovery action?**
    A: `kubectl rollout undo deployment/<name>` to immediately revert to the last known-good revision, then investigate the bad image separately (check logs, describe pod) before retrying.

20. **Q: What is `replicas` in a Deployment spec?**
    A: The desired number of identical Pod copies to keep running at all times. The Deployment controller continuously reconciles actual state toward this desired number.

21. **Q: Write the command to scale a Deployment to 5 replicas.**
    A: `kubectl scale deployment <name> --replicas=5`

22. **Q: Scenario — a Pod shows STATUS `Pending` and stays that way. What are three possible causes?**
    A: (1) Insufficient cluster resources (no node has enough CPU/memory) — check `kubectl describe pod` for "FailedScheduling" events. (2) A `nodeSelector`/affinity rule that no node satisfies. (3) A PersistentVolumeClaim the Pod depends on hasn't been bound yet.

23. **Q: Scenario — a Pod shows STATUS `CrashLoopBackOff`. What does that mean and how do you investigate?**
    A: The container starts, exits with an error, and Kubernetes keeps retrying with increasing backoff delay. Investigate with `kubectl logs <pod> --previous` (logs from the crashed instance) and `kubectl describe pod` for exit codes/events.

24. **Q: Scenario — a Pod shows STATUS `ImagePullBackOff`. What are the likely causes?**
    A: The image name/tag is wrong, the image doesn't exist in the registry, or the cluster lacks credentials to pull from a private registry. Check `kubectl describe pod` events for the exact pull error.

25. **Q: What is the difference between `kubectl apply` and `kubectl create`?**
    A: `create` makes a new resource and fails if it already exists (imperative, one-shot). `apply` creates it if missing or updates it to match the YAML if it exists — the standard declarative approach for ongoing management.

26. **Q: Write the command to view all Pods with extra details like Node and IP.**
    A: `kubectl get pods -o wide`

27. **Q: What does the READY column like `2/3` mean in `kubectl get pods`?**
    A: 2 of 3 containers in that Pod are passing their readiness checks / running. If it never reaches 3/3, one container is failing to start or stay healthy.

28. **Q: What is a container restart count, and when should you worry about it?**
    A: The RESTARTS column shows how many times a container in the Pod has been restarted by the kubelet. A rising count indicates repeated crashes — investigate with logs even if the Pod currently shows Running.

29. **Q: How does Kubernetes decide which Node to schedule a Pod on?**
    A: The scheduler filters Nodes that meet resource requests/constraints, then scores remaining candidates (e.g., by resource balance) and picks the best fit — unless you use nodeSelector/affinity to constrain it.

30. **Q: What is the `imagePullPolicy` field and its common values?**
    A: Controls when the kubelet pulls the image: `Always` (every start), `IfNotPresent` (only if not cached locally), `Never`. Using `:latest` tag defaults to `Always`; a specific tag defaults to `IfNotPresent`.

31. **Q: Scenario — you want zero downtime during an update but your app takes 30 seconds to become ready. What should you configure?**
    A: A `readinessProbe` with an appropriate `initialDelaySeconds`/timing so the rolling update waits for new Pods to report ready before terminating old ones.

32. **Q: What is a livenessProbe and how does it differ from a readinessProbe?**
    A: livenessProbe checks if a container is still healthy — if it fails, Kubernetes restarts the container. readinessProbe checks if it's ready to serve traffic — if it fails, the Pod is removed from Service load-balancing but not restarted.

33. **Q: Write a command to delete a Deployment and all its Pods.**
    A: `kubectl delete deployment <name>` — this deletes the Deployment, its ReplicaSets, and all managed Pods.

34. **Q: What happens to a Pod's data when the container restarts?**
    A: Anything not on a mounted volume is lost — container filesystems are ephemeral. Data on a volume (covered Day 4) survives container restarts.

35. **Q: Scenario — you need to run a one-off diagnostic container without a controller. What command creates it and cleans up automatically?**
    A: `kubectl run debug --image=busybox --rm -it -- sh` — the `--rm` flag deletes the Pod once you exit.

36. **Q: What is the purpose of `metadata.name` vs `metadata.labels` on a Pod?**
    A: `name` is the unique identifier for that specific object. `labels` are non-unique, arbitrary key-value tags used for grouping/selecting — many Pods can share the same label.

37. **Q: How do you find out which node a specific Pod is running on?**
    A: `kubectl get pod <name> -o wide` (NODE column) or `kubectl describe pod <name>` (Node field near the top).

38. **Q: What's the difference between `restartPolicy: Always`, `OnFailure`, and `Never`?**
    A: Always (default, used by Deployments) restarts the container regardless of exit code. OnFailure restarts only on non-zero exit. Never doesn't restart — used for one-off Jobs where you want to inspect a failure.

39. **Q: Scenario — `kubectl apply -f deployment.yaml` fails with "selector does not match template labels." What's wrong?**
    A: The `spec.selector.matchLabels` doesn't match `spec.template.metadata.labels`. Both must align — the selector is how the Deployment finds "its" Pods.

40. **Q: What is the command to edit a live Deployment directly without a YAML file on disk?**
    A: `kubectl edit deployment <name>` — opens the live object in your default editor; saving triggers an update.

41. **Q: How can you see the YAML of a currently running resource?**
    A: `kubectl get deployment <name> -o yaml` (or `-o json`).

42. **Q: What is `kubectl diff -f file.yaml` used for?**
    A: Shows what would change if you applied that file, without actually applying it — useful for reviewing changes before committing them.

43. **Q: Scenario — you have 3 replicas but only see 2 Pods running, one Pending. What single command tells you exactly why?**
    A: `kubectl describe pod <pending-pod-name>` — read the Events section at the bottom (e.g., "Insufficient cpu" or "0/1 nodes are available").

44. **Q: What does `minReadySeconds` do on a Deployment?**
    A: The minimum time a new Pod must stay Ready before it's considered available, slowing down a rollout slightly to avoid counting a flaky Pod as healthy too early.

45. **Q: What's the difference between `RollingUpdate` and `Recreate` deployment strategies?**
    A: RollingUpdate replaces Pods incrementally (default, zero-downtime capable). Recreate kills all old Pods first, then creates new ones (brief downtime, useful when old/new versions can't run simultaneously, e.g., conflicting DB migrations).

46. **Q: Write the command to view the revision history of a Deployment.**
    A: `kubectl rollout history deployment/<name>`

47. **Q: Scenario — you scaled a Deployment to 0 replicas. What happens to the Deployment object itself?**
    A: It still exists (config, name, history) but has zero running Pods — a common way to "pause" a workload without deleting its definition.

48. **Q: What is the purpose of `resources.requests` and `resources.limits` on a container?**
    A: `requests` is what the scheduler guarantees/reserves on a Node (used for placement decisions); `limits` is the hard ceiling the container cannot exceed (CPU throttled, memory OOM-killed if exceeded).

49. **Q: Scenario — a Pod was OOMKilled. What does that mean and where do you see it?**
    A: The container exceeded its memory `limit` and the kernel killed it. Visible via `kubectl describe pod` — look for "Reason: OOMKilled" under the container's Last State.

50. **Q: What is the single most useful first command to run when something is wrong with a Pod?**
    A: `kubectl describe pod <name>` — it surfaces status, container states, exit codes/reasons, and the Events timeline, which almost always points to the root cause.

---

## Day 2 (4 hrs) — Services & Networking

### Core Concepts
1. **Service** — a stable network endpoint (virtual IP + DNS name) that load-balances traffic across a set of Pods matched by label selector, decoupling clients from ever-changing Pod IPs.
2. **ClusterIP** (default) — internal-only virtual IP reachable only inside the cluster.
3. **NodePort** — exposes a Service on a static port on every Node's IP, reachable from outside the cluster.
4. **LoadBalancer** — requests a cloud provider's external load balancer (on Minikube, simulated via `minikube tunnel`).
5. **Cluster DNS** — every Service gets a DNS name (`<service>.<namespace>.svc.cluster.local`) automatically resolvable from any Pod.

### Hands-On Examples

**Example 1 — Expose the Day 1 Deployment via ClusterIP:**
```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-service
spec:
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
```
```bash
kubectl apply -f service.yaml
kubectl get svc
kubectl run tmp-shell --rm -it --image=busybox -- wget -qO- nginx-service
```

**Example 2 — Expose externally with NodePort:**
```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-nodeport
spec:
  type: NodePort
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
    nodePort: 30080
```
```bash
kubectl apply -f nodeport.yaml
minikube service nginx-nodeport --url
```

**Example 3 — Imperative shortcut:**
```bash
kubectl expose deployment nginx-deployment --port=80 --target-port=80 --type=NodePort
```

### Project: Expose Your App to the World
Take Day 1's Deployment, create a ClusterIP Service and verify internal DNS resolution from a temporary debug Pod, then create a second NodePort Service and access the app from your host machine's browser via `minikube service <name> --url`.

### Free Resources
- [Kubernetes Docs: Service](https://kubernetes.io/docs/concepts/services-networking/service/)
- [Kubernetes Docs: DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
- [Kubernetes Basics: Expose Your App](https://kubernetes.io/docs/tutorials/kubernetes-basics/expose/expose-intro/)
- [Minikube Networking Docs](https://minikube.sigs.k8s.io/docs/handbook/accessing/)
- [Play with Kubernetes](https://labs.play-with-k8s.com/)

### Top 50 Questions & Answers

1. **Q: What problem does a Service solve?**
   A: Pod IPs change every time a Pod is recreated (crash, rollout, rescheduling). A Service gives clients one stable virtual IP/DNS name that always routes to whichever Pods currently match its selector.

2. **Q: How does a Service know which Pods to send traffic to?**
   A: Via a label selector — any Pod whose labels match the Service's `spec.selector` becomes an eligible backend, tracked automatically by an Endpoints/EndpointSlice object.

3. **Q: What is the default Service type?**
   A: `ClusterIP` — an internal-only virtual IP reachable from within the cluster.

4. **Q: What is `ClusterIP` used for?**
   A: Internal service-to-service communication — e.g., a frontend Pod calling a backend API Service. Not reachable from outside the cluster.

5. **Q: What is `NodePort` used for?**
   A: Exposes the Service on the same static port (30000-32767 range) on every Node's IP address, making it reachable from outside the cluster — mainly useful for dev/test, not production-grade external access.

6. **Q: What is `LoadBalancer` type used for?**
   A: Requests an external load balancer from the cloud provider (AWS ELB, GCP LB, etc.) that routes external traffic to the Service. On Minikube it's simulated with `minikube tunnel`.

7. **Q: What is `ExternalName` Service type?**
   A: Maps a Service to an external DNS name (e.g., an external database) via a CNAME, with no selector or proxying — just DNS-level redirection.

8. **Q: Scenario — your Pod's IP changed after a restart but your app still works fine talking to "backend-service." Why?**
   A: You're connecting via the Service's stable DNS name/ClusterIP, not the Pod IP directly — the Service transparently updates its backend list as Pods come and go.

9. **Q: What is the DNS name format for a Service inside the cluster?**
   A: `<service-name>.<namespace>.svc.cluster.local` — Pods in the same namespace can just use `<service-name>`.

10. **Q: Write the command to expose an existing Deployment as a ClusterIP Service on port 80.**
    A: `kubectl expose deployment <name> --port=80 --target-port=80`

11. **Q: What is `targetPort` vs `port` in a Service spec?**
    A: `port` is what clients connect to on the Service's virtual IP. `targetPort` is the actual port the container listens on inside the Pod — they can differ (e.g., Service port 80 → container port 8080).

12. **Q: Scenario — you `curl` your ClusterIP Service and get "connection refused." What are two likely causes?**
    A: (1) No Pods match the Service's selector (label mismatch) — check `kubectl get endpoints <svc>`, an empty list means no backends. (2) `targetPort` doesn't match the container's actual listening port.

13. **Q: How do you check which Pods a Service is currently routing to?**
    A: `kubectl get endpoints <service-name>` — lists the Pod IPs currently registered as backends.

14. **Q: Scenario — `kubectl get endpoints my-svc` shows `<none>`. What's the root cause and fix?**
    A: The Service's selector doesn't match any Pod's labels. Fix by correcting either the Service's `spec.selector` or the Pod template's `labels` so they align.

15. **Q: What component actually implements Service load-balancing on each Node?**
    A: `kube-proxy`, which programs iptables (or IPVS) rules on every Node to route Service IP traffic to backend Pod IPs.

16. **Q: How does a Service load-balance across multiple backend Pods?**
    A: By default, roughly round-robin/random distribution across all healthy (Ready) endpoint Pods via the kube-proxy rules.

17. **Q: What happens to Service routing if a backend Pod fails its readinessProbe?**
    A: It's removed from the Service's Endpoints list immediately — no traffic is routed to it until it becomes Ready again, without needing a restart.

18. **Q: Write the command to list all Services in the current namespace.**
    A: `kubectl get svc` (or `kubectl get services`)

19. **Q: Scenario — you need external users to reach your app from Minikube on your laptop. What's the simplest approach?**
    A: Create a NodePort Service, then run `minikube service <name> --url` to get a directly accessible URL (Minikube handles the tunneling for you locally).

20. **Q: What port range is valid for NodePort?**
    A: 30000–32767 by default; if you don't specify one, Kubernetes auto-assigns from that range.

21. **Q: Scenario — two different Deployments both have Pods labeled `app: web`, and you only want your Service to hit one of them. What should you check?**
    A: Add a more specific/unique label (e.g., `tier: frontend-v2`) to the intended Deployment's Pod template and reference that in the Service's selector — ambiguous overlapping labels will route to both.

22. **Q: What is a headless Service (`clusterIP: None`)?**
    A: A Service with no virtual IP — DNS lookups return the individual Pod IPs directly instead of one load-balanced IP. Used when clients need to address each backend Pod individually (common with StatefulSets, Day 4).

23. **Q: How can a Pod discover a Service's ClusterIP via environment variables?**
    A: Kubernetes injects `<SVCNAME>_SERVICE_HOST` and `<SVCNAME>_SERVICE_PORT` env vars into Pods started after the Service exists — though DNS is the more reliable, order-independent method.

24. **Q: Scenario — your app works when you `curl` from inside a Pod using the Service's ClusterIP, but not by DNS name. What's likely misconfigured?**
    A: Cluster DNS (CoreDNS) may not be resolving correctly, or you're testing from a Pod in a different namespace without the full `<svc>.<namespace>.svc.cluster.local` suffix.

25. **Q: What is CoreDNS's role in the cluster?**
    A: It's the cluster's internal DNS server, running as Pods, resolving Service and Pod names to their virtual/actual IPs for any Pod that queries it (configured automatically via each Pod's `/etc/resolv.conf`).

26. **Q: Write the command to test DNS resolution of a Service from inside a temporary Pod.**
    A: `kubectl run dns-test --rm -it --image=busybox -- nslookup <service-name>`

27. **Q: What's the difference between a Service and an Ingress (preview for Day 5)?**
    A: A Service load-balances at L4 (TCP/UDP) to a set of Pods, typically one Service per app. An Ingress operates at L7 (HTTP), routing by hostname/path across multiple Services, usually with one external entry point for many apps.

28. **Q: Scenario — you delete and recreate a Service with the same name. Does the ClusterIP stay the same?**
    A: Not guaranteed — deleting a Service releases its ClusterIP back to the pool; recreating it (without explicitly setting `spec.clusterIP`) may assign a different IP. Clients should always use the DNS name, not the IP.

29. **Q: What does `sessionAffinity: ClientIP` do on a Service?**
    A: Routes all requests from the same client IP to the same backend Pod for the session duration, instead of default round-robin — useful for stateful in-memory sessions (though shared storage is usually a better fix).

30. **Q: Scenario — after scaling a Deployment from 3 to 6 replicas, do you need to update the Service?**
    A: No — the Service selector automatically picks up any Pod matching its labels, so new replicas are added to the Endpoints list with zero Service configuration changes.

31. **Q: What is the `port` field required for, even if `targetPort` is omitted?**
    A: It's mandatory — the port the Service itself listens on. If `targetPort` is omitted, it defaults to the same value as `port`.

32. **Q: How would you expose multiple ports (e.g., 80 and 443) on one Service?**
    A: List multiple entries under `spec.ports`, each with a unique `name` field (required when there's more than one port) — e.g., `- name: http, port: 80` and `- name: https, port: 443`.

33. **Q: Scenario — your NodePort Service works on `minikube ip`:30080 but a teammate on a different cloud cluster expects a real LoadBalancer URL. Why the difference?**
    A: NodePort just opens a port on every Node's own IP — there's no managed external IP. LoadBalancer type additionally provisions a cloud load balancer with its own external IP/DNS, which Minikube doesn't natively provide (hence `minikube tunnel` as a substitute).

34. **Q: What does `minikube tunnel` do?**
    A: Creates a network route on your host so `LoadBalancer`-type Services get a routable external IP, simulating what a real cloud provider would provision.

35. **Q: Write the command to delete a Service.**
    A: `kubectl delete svc <service-name>`

36. **Q: Scenario — you have a Service targeting port 8080 but your container's Dockerfile exposes port 3000. What breaks and what's the fix?**
    A: Nothing routes correctly since traffic gets sent to a port nothing is listening on. Fix by setting `targetPort: 3000` (matching what the app actually listens on) while `port` can remain whatever clients should use.

37. **Q: How does Kubernetes networking guarantee every Pod gets its own unique, routable IP?**
    A: Via the cluster's CNI (Container Network Interface) plugin, which implements the Kubernetes networking model: every Pod can reach every other Pod's IP directly, without NAT, cluster-wide.

38. **Q: Scenario — Pod A can `curl` Pod B's IP directly but you're told not to rely on that in your app code. Why?**
    A: Pod IPs are ephemeral — they change on every restart/reschedule. Only Service names/IPs are meant to be stable references for application code.

39. **Q: What is an EndpointSlice?**
    A: The modern, scalable version of the older Endpoints object — tracks the actual backend Pod IPs/ports for a Service, split into slices for performance at scale.

40. **Q: How do you check if a Service has any active traffic-routing issues quickly?**
    A: `kubectl describe svc <name>` — shows the selector, Endpoints (or lack thereof), and Port mappings all in one place.

41. **Q: Scenario — you want internal-only access between microservices but also need one public entry point later. What Service type(s) should you use now?**
    A: ClusterIP for all internal microservice-to-microservice communication now; you'll add an Ingress (Day 5) as the single external entry point later rather than exposing every Service externally.

42. **Q: What is the security implication of using NodePort in a real (non-learning) environment?**
    A: It opens a port directly on every Node's network interface, potentially bypassing intended network boundaries/firewalls — generally discouraged for production external exposure in favor of Ingress or a cloud LoadBalancer with proper security groups.

43. **Q: Scenario — a Service selector is `app: nginx, tier: frontend` but your Pods only have the label `app: nginx`. What happens?**
    A: The Service matches nothing (a Service selector requires ALL listed labels to match) — Endpoints will be empty and traffic will fail to route anywhere.

44. **Q: Write the command to see a Service's full YAML definition as currently applied in the cluster.**
    A: `kubectl get svc <name> -o yaml`

45. **Q: What's the difference between "Service" and "Endpoints" as API objects?**
    A: Service defines the stable virtual IP/selector/ports (the "front door" spec). Endpoints (or EndpointSlices) is the automatically-maintained list of actual backend Pod IPs currently matching that selector.

46. **Q: Scenario — you want zero-downtime deploys and notice new Pods briefly receive traffic before they're fully ready during a rollout. What's missing?**
    A: A properly configured `readinessProbe` on the container — without it, Kubernetes assumes the Pod is ready as soon as it starts, even if the app inside hasn't finished initializing.

47. **Q: Can a single Pod be a backend for multiple different Services simultaneously?**
    A: Yes — as long as its labels match each Service's selector, it can receive traffic through multiple independent Services (e.g., one for internal metrics, one for the main app).

48. **Q: What is `kube-proxy` and does it run as a Pod?**
    A: The Node-level component implementing Service virtual IPs by writing iptables/IPVS rules; it typically runs as a DaemonSet Pod on every Node.

49. **Q: Scenario — after deploying, `curl` to the Service times out (not "connection refused"). What does that suggest vs. a refused connection?**
    A: A refused connection usually means something is listening but rejecting (or nothing is behind that specific port); a timeout usually points to a network/firewall/CNI issue or the request never reaching any Pod at all (e.g., no Endpoints, or NetworkPolicy blocking it).

50. **Q: What's the single command to quickly diagnose "my Service isn't working"?**
    A: `kubectl describe svc <name>` combined with `kubectl get endpoints <name>` — together they show selector correctness, registered backend Pods, and port mappings in seconds.

---

## Day 3 (3 hrs) — ConfigMaps & Secrets

### Core Concepts
1. **ConfigMap** — stores non-sensitive configuration data (key-value pairs, files) separately from container images, injected as env vars or mounted files.
2. **Secret** — like a ConfigMap but for sensitive data (passwords, tokens, certs); base64-encoded at rest (not encrypted by default — a key nuance for beginners).
3. **Env var injection** (`envFrom`/`valueFrom`) — pulling ConfigMap/Secret values into a container's environment.
4. **Volume mounting** — projecting a ConfigMap/Secret as files inside a container's filesystem.
5. **Decoupling config from image** — the core principle: the same container image should run in dev/staging/prod, with only the ConfigMap/Secret changing.

### Hands-On Examples

**Example 1 — ConfigMap from literals, consumed as env vars:**
```bash
kubectl create configmap app-config --from-literal=APP_COLOR=blue --from-literal=APP_MODE=prod
```
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: config-demo
spec:
  containers:
  - name: demo
    image: busybox
    command: ["sh", "-c", "env | grep APP_ && sleep 3600"]
    envFrom:
    - configMapRef:
        name: app-config
```

**Example 2 — Secret consumed as a mounted file:**
```bash
kubectl create secret generic db-secret --from-literal=DB_PASSWORD=SuperSecret123
```
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: secret-demo
spec:
  containers:
  - name: demo
    image: busybox
    command: ["sh", "-c", "cat /etc/secret/DB_PASSWORD && sleep 3600"]
    volumeMounts:
    - name: secret-vol
      mountPath: /etc/secret
  volumes:
  - name: secret-vol
    secret:
      secretName: db-secret
```

**Example 3 — ConfigMap from a file:**
```bash
kubectl create configmap nginx-config --from-file=nginx.conf
```

### Project: Multi-Container Pod with ConfigMap and Secret
Build a Pod with two containers: one running your app image reading `APP_COLOR`/`APP_MODE` from a ConfigMap as env vars, and a `DB_PASSWORD` value mounted from a Secret as a file. Verify both are accessible with `kubectl exec` inside the running Pod.

### Free Resources
- [Kubernetes Docs: ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Kubernetes Docs: Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Kubernetes Basics: Configuration](https://kubernetes.io/docs/tutorials/configuration/)
- [Kubernetes Docs: Configure a Pod to Use a ConfigMap](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/)

### Top 50 Questions & Answers

1. **Q: What is a ConfigMap?**
   A: A Kubernetes object that stores non-sensitive configuration data as key-value pairs, decoupled from your container image so the same image can be reused across environments with different config.

2. **Q: What is a Secret and how is it different from a ConfigMap?**
   A: Structurally almost identical to a ConfigMap, but intended for sensitive data. Values are base64-encoded (not encrypted by default at the API level) and Kubernetes treats Secrets with slightly tighter defaults (e.g., not shown in plain in some `describe` output).

3. **Q: Is base64 encoding the same as encryption?**
   A: No — base64 is just an encoding, trivially reversible by anyone with access to the Secret object. Real protection requires enabling encryption-at-rest on the etcd datastore and restricting RBAC access to Secrets (covered Day 5).

4. **Q: Why decouple configuration from the container image?**
   A: So the exact same tested image can be promoted from dev → staging → prod without rebuilding — only the ConfigMap/Secret values change per environment.

5. **Q: Write the command to create a ConfigMap from literal key-value pairs.**
   A: `kubectl create configmap <name> --from-literal=KEY=value --from-literal=KEY2=value2`

6. **Q: Write the command to create a ConfigMap from a file.**
   A: `kubectl create configmap <name> --from-file=<path-to-file>`

7. **Q: Write the command to create a Secret from literal values.**
   A: `kubectl create secret generic <name> --from-literal=PASSWORD=mysecret`

8. **Q: How do you inject an entire ConfigMap's keys as environment variables?**
   A: Use `envFrom: - configMapRef: name: <configmap-name>` in the container spec — every key becomes an env var of the same name.

9. **Q: How do you inject a single specific key from a ConfigMap as one named env var?**
   A: `env: - name: MY_VAR valueFrom: configMapKeyRef: name: <configmap> key: <key>`

10. **Q: How do you mount a ConfigMap as files inside a container?**
    A: Define a volume of type `configMap` referencing the ConfigMap, then `volumeMounts` it into the container at a chosen `mountPath` — each key becomes a filename, its value the file's content.

11. **Q: Scenario — you update a ConfigMap's value with `kubectl apply`, but the env var inside an already-running Pod doesn't change. Why?**
    A: Env vars are injected only at container start time — they're a one-time snapshot. You must restart/recreate the Pod (e.g., `kubectl rollout restart deployment/<name>`) to pick up new values.

12. **Q: Scenario — you update a ConfigMap that's mounted as a volume (not env vars). Does the running Pod see the change?**
    A: Yes, eventually — mounted ConfigMap files are updated automatically (usually within about a minute) without restarting the Pod, unlike env vars. The app itself must be watching the file for changes to act on it, though.

13. **Q: What is `subPath` used for when mounting a ConfigMap?**
    A: Mounts a single key from the ConfigMap as one specific file at the mountPath, instead of replacing the whole target directory with all keys — useful when you only want to inject one config file into an existing directory without hiding other files there.

14. **Q: Scenario — you mount a ConfigMap at `/etc/config` and the directory already contained other files. What happens to those other files?**
    A: The Kubernetes-managed mount fully replaces the target directory's contents — pre-existing files at that exact path get hidden/lost (this is why `subPath` exists, to mount just one file without wiping the directory).

15. **Q: What does `kubectl get secret <name> -o yaml` show for values, and how do you decode them?**
    A: Values shown are base64-encoded strings. Decode manually with `echo '<value>' | base64 --decode`, or use `kubectl get secret <name> -o jsonpath='{.data.KEY}' | base64 --decode`.

16. **Q: Write the command to view the decoded value of a specific Secret key.**
    A: `kubectl get secret <name> -o jsonpath='{.data.PASSWORD}' | base64 --decode`

17. **Q: Scenario — a Pod is Pending and `kubectl describe pod` shows "configmap not found." What's the fix?**
    A: Create the missing ConfigMap (the Pod references one by name that doesn't exist yet in that namespace) — `kubectl create configmap <name> ...` — then the Pod will schedule.

18. **Q: What is the difference between `configMapRef` and `configMapKeyRef`?**
    A: `configMapRef` (used inside `envFrom`) imports ALL keys as env vars. `configMapKeyRef` (used inside `env[].valueFrom`) imports exactly ONE named key as one named env var — giving finer control.

19. **Q: Can a ConfigMap and Secret be used together in the same Pod?**
    A: Yes, freely — e.g., non-sensitive app settings from a ConfigMap and a database password from a Secret, both injected into the same container via env vars or volumes.

20. **Q: What are the size limits for a ConfigMap/Secret?**
    A: Each is limited to 1MiB total, because they're stored in etcd, which isn't designed for large blobs — use them for config/credentials, not large files or binaries.

21. **Q: Scenario — you accidentally committed a Secret's YAML (with base64 values) to a public Git repo. Is your data safe?**
    A: No — base64 is trivially decodable by anyone. Treat this exactly like committing a plaintext password: rotate/invalidate the credential immediately and remove it from git history.

22. **Q: What's a common way to avoid committing Secret manifests to git while still using `kubectl apply` workflows?**
    A: Generate Secrets from local files/env at apply time (`kubectl create secret generic ... --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -`) or use a secrets manager/sealed-secrets tool that keeps ciphertext in git instead of raw base64.

23. **Q: What is `kubectl create secret docker-registry` used for?**
    A: Creates a Secret holding credentials for pulling images from a private container registry, referenced via `imagePullSecrets` in a Pod spec.

24. **Q: Scenario — your Pod needs to pull from a private Docker registry and shows ImagePullBackOff with an "unauthorized" error. What's missing?**
    A: An `imagePullSecrets` entry on the Pod (or its ServiceAccount) referencing a `docker-registry`-type Secret with valid registry credentials.

25. **Q: What is the purpose of the `type` field on a Secret (e.g., `Opaque`, `kubernetes.io/tls`)?**
    A: It tells Kubernetes (and tooling) what kind of data the Secret holds and enforces expected key structure — e.g., `kubernetes.io/tls` requires exactly `tls.crt` and `tls.key` keys, used later by Ingress for HTTPS.

26. **Q: Write the command to list all ConfigMaps in the current namespace.**
    A: `kubectl get configmaps` (or `kubectl get cm`)

27. **Q: Write the command to edit a ConfigMap in place.**
    A: `kubectl edit configmap <name>`

28. **Q: Scenario — two containers in the same Pod both mount the same ConfigMap volume. Do they see the same content?**
    A: Yes — both mounts read from the same underlying ConfigMap object, so any update is reflected consistently to all containers mounting it (subject to the same eventual-consistency propagation delay).

29. **Q: What happens if you reference a ConfigMap key in `valueFrom.configMapKeyRef` that doesn't exist?**
    A: The Pod fails to start, staying Pending, with an event describing the missing key — unless you set `optional: true` on that reference, in which case it's simply skipped.

30. **Q: What is the `optional: true` field used for on ConfigMap/Secret references?**
    A: Allows the Pod to start successfully even if the referenced ConfigMap/Secret (or specific key) doesn't exist yet, instead of blocking scheduling — useful for gradually-rolled-out config.

31. **Q: Scenario — you want the same app image to run with different settings in "dev" and "prod" namespaces. What's the cleanest approach?**
    A: Deploy identical Deployment YAML in both namespaces, each referencing a ConfigMap of the same name but with environment-specific values — the image never changes, only the ConfigMap content per namespace.

32. **Q: How do immutable ConfigMaps/Secrets (`immutable: true`) help at scale?**
    A: Prevents accidental updates to a ConfigMap/Secret in use, and lets the API server skip watching it for changes, improving performance in clusters with many ConfigMaps — you create a new one and update the reference instead of mutating in place.

33. **Q: Write the command to delete a Secret.**
    A: `kubectl delete secret <name>`

34. **Q: Scenario — a teammate says "just put the password directly in the Deployment YAML env value" instead of a Secret. What's the risk?**
    A: Plaintext credentials end up in version control, `kubectl get deployment -o yaml` output, and CI logs — anyone with read access to the manifest sees the password. A Secret at least separates the sensitive value and allows RBAC to restrict who can read it.

35. **Q: What is the difference between `Opaque` and `kubernetes.io/basic-auth` Secret types?**
   A: `Opaque` (the default) is a generic free-form Secret. `kubernetes.io/basic-auth` enforces specific keys (`username`, `password`) for tooling that expects that standard shape.

36. **Q: Scenario — after mounting a Secret as a volume, the file `/etc/secret/DB_PASSWORD` contains a trailing newline your app doesn't expect. Why?**
    A: `--from-literal` and file-based Secret creation preserve exact byte content; if the source file had a trailing newline it's included verbatim — trim it in your app or ensure the source value has no newline.

37. **Q: Can Secrets be shared across namespaces?**
    A: No — Secrets (like ConfigMaps) are namespace-scoped; a Pod in namespace `dev` cannot reference a Secret living in namespace `prod` directly. You'd need to duplicate it or use a cross-namespace secret-sync tool.

38. **Q: What is the risk of using `kubectl get secret -o yaml` on a shared terminal/screen-share?**
    A: It reveals the base64-encoded (easily decodable) sensitive value on screen — always be mindful of who can see your terminal when inspecting Secrets.

39. **Q: Scenario — your app reads `DB_PASSWORD` from an env var, but you rotate the Secret value. What's needed for the running Pod to pick up the new password?**
    A: A Pod restart (env vars are snapshotted at start) — e.g., `kubectl rollout restart deployment/<name>` — mounted-volume Secrets would update live, but env-var-injected ones require a restart.

40. **Q: What does `kubectl describe configmap <name>` show that `get -o yaml` also shows?**
    A: Both show the Data keys and values (ConfigMaps aren't obscured since they're non-sensitive) — `describe` gives a cleaner human-readable summary while `-o yaml` gives the full raw manifest.

41. **Q: Scenario — `kubectl describe secret <name>` shows key names but `<redacted>` or nothing for values. Why the difference from ConfigMap?**
    A: `kubectl describe` intentionally hides Secret values by default to reduce accidental shoulder-surfing/leakage — you must explicitly `get -o yaml` and decode base64 to see them.

42. **Q: What's a Volume Projection (`projected` volume) and why is it useful with ConfigMaps/Secrets?**
    A: Lets you combine multiple sources (ConfigMap + Secret + downward API) into a single mounted directory — useful when an app expects all its config files together in one folder regardless of source object type.

43. **Q: Scenario — you need a certificate and private key available to your app as files. What Secret type and structure fits best?**
    A: `kubernetes.io/tls` Secret type with `tls.crt` and `tls.key` keys, mounted as a volume — this is also the exact format Ingress TLS termination expects (Day 5).

44. **Q: Write the command to create a ConfigMap from an entire directory of files.**
    A: `kubectl create configmap <name> --from-file=<directory-path>` — each file in the directory becomes a separate key.

45. **Q: What happens if a ConfigMap and an explicit `env` entry define the same variable name in a container spec?**
    A: The explicit `env` entry (later in evaluation, more specific) generally wins/overrides values coming from `envFrom` — always verify the actual precedence for your Kubernetes version if it matters for correctness.

46. **Q: Scenario — you want to test a new config value without affecting the live ConfigMap other Pods use. What's a safe approach?**
    A: Create a new ConfigMap (e.g., `app-config-v2`), point only your test Deployment/Pod at it, and leave the original untouched until you're ready to cut everything over.

47. **Q: What's the practical difference in reasoning between "ConfigMap for config" and "Secret for credentials," given both are technically similar?**
    A: The distinction is about intent and tooling behavior (RBAC defaults, hidden describe output, encryption-at-rest support) — using the right type signals sensitivity correctly to teammates and to Kubernetes' own safeguards.

48. **Q: Scenario — you delete a ConfigMap that's still referenced (non-optional) by a running Deployment. What happens to already-running Pods?**
    A: Already-running Pods with env vars keep running fine (values were snapshotted at start) — but any NEW Pod created afterward (e.g., during a restart or scale-up) will fail to start until the ConfigMap exists again.

49. **Q: What is the benefit of mounting a Secret as `readOnly` (the default for Secret volumes)?**
    A: Prevents the container from accidentally (or maliciously) modifying credential files on disk — Secret volumes are read-only by default precisely because sensitive data shouldn't be writable from inside the container.

50. **Q: What's the single biggest beginner mistake with Secrets covered today?**
    A: Assuming base64 encoding means "encrypted and safe to share/commit" — it is neither; treat any base64 Secret value exactly as you would a plaintext password.

---

## Day 4 (3 hrs) — StatefulSets & Persistent Volumes

### Core Concepts
1. **PersistentVolume (PV)** — a piece of storage provisioned in the cluster (by an admin or dynamically), existing independently of any Pod's lifecycle.
2. **PersistentVolumeClaim (PVC)** — a Pod's request for storage; Kubernetes binds it to a matching PV.
3. **StorageClass** — enables dynamic provisioning: a PVC referencing a StorageClass automatically gets a PV created on demand (Minikube ships a default one).
4. **StatefulSet** — like a Deployment but for stateful apps: gives Pods stable, unique network identities and stable, per-Pod persistent storage across restarts/rescheduling.
5. **Stable identity & ordering** — StatefulSet Pods get predictable names (`app-0`, `app-1`, ...), are created/deleted in order, and each keeps its own PVC even after being rescheduled.

### Hands-On Examples

**Example 1 — PVC using the default dynamic StorageClass:**
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: my-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```
```bash
kubectl apply -f pvc.yaml
kubectl get pvc
kubectl get pv
```

**Example 2 — Pod using the PVC:**
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: pvc-pod
spec:
  containers:
  - name: app
    image: busybox
    command: ["sh", "-c", "echo hello > /data/test.txt && sleep 3600"]
    volumeMounts:
    - name: storage
      mountPath: /data
  volumes:
  - name: storage
    persistentVolumeClaim:
      claimName: my-pvc
```

**Example 3 — Minimal StatefulSet with volumeClaimTemplates:**
```yaml
apiVersion: v1
kind: Service
metadata:
  name: web-headless
spec:
  clusterIP: None
  selector:
    app: web
  ports:
  - port: 80
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: web
spec:
  serviceName: web-headless
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
      - name: nginx
        image: nginx
        volumeMounts:
        - name: www
          mountPath: /usr/share/nginx/html
  volumeClaimTemplates:
  - metadata:
      name: www
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
```
```bash
kubectl apply -f statefulset.yaml
kubectl get pods -w   # observe ordered web-0, web-1, web-2 creation
kubectl get pvc       # one PVC per Pod
```

### Project: Stateful App with Persistent Storage
Deploy the 3-replica StatefulSet above. Write a unique file into each Pod's volume (`kubectl exec web-0 -- sh -c 'echo pod0 > /usr/share/nginx/html/index.html'`), delete `web-0`, and confirm after it's recreated that its data (and identity `web-0`) persists — unlike a Deployment Pod, which would come back with a fresh empty volume unless using the same PVC pattern.

### Free Resources
- [Kubernetes Docs: Persistent Volumes](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)
- [Kubernetes Docs: StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
- [Kubernetes Docs: Storage Classes](https://kubernetes.io/docs/concepts/storage/storage-classes/)
- [Run a Stateful Application (official tutorial)](https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/)
- [Minikube Storage Docs](https://minikube.sigs.k8s.io/docs/handbook/persistent_volumes/)

### Top 50 Questions & Answers

1. **Q: What is a PersistentVolume (PV)?**
   A: A cluster-level storage resource, provisioned either statically by an admin or dynamically via a StorageClass, that exists independently of any particular Pod's lifecycle.

2. **Q: What is a PersistentVolumeClaim (PVC)?**
   A: A user's/Pod's request for storage ("I need 1Gi, ReadWriteOnce") — Kubernetes matches and binds it to a suitable PV, existing PVs get claimed or new ones get dynamically provisioned.

3. **Q: Why do we need both PV and PVC instead of just mounting storage directly?**
   A: It separates "what storage exists" (infrastructure concern, PV) from "what storage an app needs" (application concern, PVC) — developers write PVCs without knowing the underlying storage details.

4. **Q: What is a StorageClass?**
   A: A template that tells Kubernetes how to dynamically provision a PV when a PVC references it (e.g., which cloud disk type, replication settings) — removes the need to manually pre-create PVs.

5. **Q: What does `accessModes: ReadWriteOnce` mean?**
   A: The volume can be mounted as read-write by a single Node at a time (multiple Pods on that same Node can still share it, but not across different Nodes simultaneously).

6. **Q: What are the other common access modes besides ReadWriteOnce?**
   A: `ReadOnlyMany` (many Nodes, read-only), `ReadWriteMany` (many Nodes, read-write — requires storage backends that support it, e.g., NFS), and `ReadWriteOncePod` (only a single Pod cluster-wide, stricter than RWO).

7. **Q: Scenario — you create a PVC and it stays in `Pending` status. What are two likely causes?**
   A: (1) No PV exists that satisfies the requested size/access mode, and no StorageClass is available/default to dynamically provision one. (2) The StorageClass referenced doesn't exist or its provisioner is misconfigured.

8. **Q: Write the command to check the status of a PVC.**
   A: `kubectl get pvc` — look at the STATUS column (`Bound` = success, `Pending` = not yet satisfied).

9. **Q: What happens to data on a Pod's volume when the Pod is deleted and recreated, if it uses a PVC?**
   A: Data persists — the PVC (and its underlying PV/data) is a separate object from the Pod's lifecycle; a new Pod referencing the same PVC picks up right where the old one left off.

10. **Q: What happens to data in a container that does NOT use a volume, when the Pod restarts?**
    A: It's lost — the container filesystem is ephemeral and recreated fresh on every container start unless backed by a mounted volume.

11. **Q: What is a StatefulSet used for?**
    A: Managing stateful applications (databases, queues) that need stable network identity and stable per-replica storage across restarts — e.g., each replica of a database cluster needs to always come back as "the same member."

12. **Q: How do StatefulSet Pod names differ from Deployment Pod names?**
    A: StatefulSet Pods get predictable, ordinal names like `web-0`, `web-1`, `web-2` (stable across restarts), while Deployment Pods get random suffixes like `web-7d9f8c9-x2kdp` (a new name every time they're recreated).

13. **Q: What is `volumeClaimTemplates` in a StatefulSet?**
    A: A template Kubernetes uses to automatically create one unique PVC per replica (e.g., `www-web-0`, `www-web-1`), so each Pod gets its own dedicated, persistent storage that follows it across rescheduling.

14. **Q: Scenario — you delete Pod `web-1` from a StatefulSet. Does its PVC get deleted too?**
    A: No — PVCs created via `volumeClaimTemplates` are NOT deleted automatically when a Pod is deleted; the replacement Pod `web-1` reattaches to the same existing PVC and its data.

15. **Q: What order does a StatefulSet create and delete its Pods in?**
    A: In strict ordinal sequence — `web-0` first, then `web-1`, then `web-2`, etc. Deletion (scale-down) happens in reverse order, highest ordinal first — important for apps with leader/follower startup dependencies.

16. **Q: Why does a StatefulSet require a headless Service (`clusterIP: None`)?**
    A: To give each Pod its own stable, individually resolvable DNS name (`web-0.web-headless`, `web-1.web-headless`, ...) instead of one load-balanced virtual IP — necessary when clients need to talk to a specific replica (e.g., the primary database node).

17. **Q: Write the DNS name format for an individual StatefulSet Pod behind a headless Service.**
    A: `<pod-name>.<headless-service-name>.<namespace>.svc.cluster.local` — e.g., `web-0.web-headless.default.svc.cluster.local`.

18. **Q: Scenario — you scale a StatefulSet from 3 to 5 replicas. What new PVCs get created?**
    A: Two new PVCs (`www-web-3`, `www-web-4`), matching the volumeClaimTemplate — each new replica gets fresh, empty storage of its own since it's a new ordinal, not a reused one.

19. **Q: Scenario — you scale a StatefulSet down from 5 to 3, then back up to 5. Does `web-3` come back with its old data?**
    A: Yes — scaling down doesn't delete the PVC, so scaling back up reattaches `web-3` to its still-existing PVC and its previous data.

20. **Q: How do you manually delete the leftover PVCs after permanently removing a StatefulSet?**
    A: `kubectl delete pvc -l app=<label>` or explicitly by name — Kubernetes intentionally never auto-deletes StatefulSet PVCs, treating data loss as something that must be explicit.

21. **Q: What's the `reclaimPolicy` on a PersistentVolume and its two common values?**
    A: Controls what happens to the underlying storage when its PVC is deleted: `Delete` (the PV and underlying storage are deleted too — common default for dynamically provisioned volumes) or `Retain` (the PV and data survive for manual admin recovery/cleanup).

22. **Q: Scenario — you need a database's data to survive even if someone accidentally deletes its PVC. What reclaimPolicy should the PV use?**
    A: `Retain` — ensures the underlying storage isn't wiped automatically; an admin must manually clean it up, adding a safety buffer against accidental data loss.

23. **Q: What is dynamic provisioning?**
    A: The process where creating a PVC that references a StorageClass automatically triggers creation of a new matching PV (and underlying storage) on demand, instead of requiring an admin to pre-create PVs manually.

24. **Q: Write the command to see what StorageClasses are available in your cluster.**
    A: `kubectl get storageclass` (or `kubectl get sc`)

25. **Q: Scenario — `kubectl get pvc` shows `Pending` and `kubectl describe pvc` mentions "no persistent volumes available for this claim." What's the fix?**
    A: Either apply a StorageClass that supports dynamic provisioning (Minikube has one enabled by default named `standard`) or manually create a PV that matches the requested size/access mode.

26. **Q: What does "storage" mean in `resources.requests.storage: 1Gi` on a PVC?**
    A: The minimum capacity being requested — Kubernetes binds to a PV with at least that much capacity (it may bind to a larger one if no exact match exists, depending on the provisioner).

27. **Q: Can two different Pods share the same PVC simultaneously?**
    A: Only if the access mode allows it — `ReadWriteOnce` restricts writable mounting to one Node at a time (multiple Pods on that Node can share it); true multi-Pod-multi-Node sharing needs `ReadWriteMany`-capable storage.

28. **Q: Scenario — your StatefulSet-based database Pod `web-0` is stuck in `Pending` after a Node failure. What might be wrong?**
    A: Its PVC may be bound to a PV backed by storage physically tied to the failed Node (common with local/hostPath volumes) — the Pod can't reschedule elsewhere until the Node returns or the storage is portable (a cloud/network-attached volume, not local disk).

29. **Q: What is the practical downside of using `hostPath` volumes for real workloads?**
   A: Data is tied to a specific Node's local disk — if the Pod gets rescheduled to a different Node, it won't see the same data; unsuitable for anything requiring durability across rescheduling.

30. **Q: Write the command to inspect which PV a given PVC is bound to.**
    A: `kubectl get pvc <name> -o jsonpath='{.spec.volumeName}'` or simply `kubectl describe pvc <name>` (shows the Volume field).

31. **Q: Scenario — you need to resize a PVC from 1Gi to 5Gi. Is this possible, and how?**
    A: Yes, if the StorageClass has `allowVolumeExpansion: true` — edit the PVC's `spec.resources.requests.storage` to the new size and apply; the underlying volume is expanded (a Pod restart may be required for the filesystem to see the new size, depending on the storage driver).

32. **Q: What is the relationship between a StatefulSet and a Deployment in terms of when to choose each?**
    A: Use a Deployment for stateless apps where any replica is interchangeable (e.g., web frontends). Use a StatefulSet when replicas are NOT interchangeable — each needs its own persistent identity/data (e.g., databases, message brokers with per-node state).

33. **Q: Scenario — you accidentally used a Deployment (not a StatefulSet) for a 3-node database cluster. What breaks?**
    A: Pod names/IPs change unpredictably on every restart, and by default all replicas would share or fight over storage (or each get ephemeral storage) — database clustering logic that depends on stable peer addresses and per-node data will likely fail or corrupt state.

34. **Q: What does `kubectl get pv` show that helps you understand cluster storage state?**
    A: Each PV's capacity, access mode, reclaim policy, STATUS (`Available`, `Bound`, `Released`, `Failed`), and which PVC (if any) currently claims it.

35. **Q: What does a PV status of `Released` mean?**
    A: Its bound PVC was deleted, but (with `Retain` policy) the PV and its data still exist and are not yet available for a new claim until an admin manually reclaims/cleans it.

36. **Q: Scenario — a developer asks "why can't I just use emptyDir for my database's data?" What's the answer?**
    A: `emptyDir` is tied to the Pod's lifetime — deleted permanently when the Pod is removed (not just restarted-in-place). A database needs storage that survives Pod deletion/rescheduling, which only a PV/PVC provides.

37. **Q: What is `emptyDir` actually useful for, then?**
    A: Temporary scratch space shared between containers in the same Pod (e.g., a cache, or a shared directory between a main app and a sidecar) where losing the data on Pod deletion is acceptable.

38. **Q: Write the command to delete a PVC.**
    A: `kubectl delete pvc <name>` — note this may also delete the underlying PV/data if its reclaimPolicy is `Delete`.

39. **Q: Scenario — you run `kubectl delete pvc my-pvc` while a Pod is still using it. What happens?**
    A: The PVC is marked for deletion (Terminating) but is protected from actually being removed until no Pod still references it, thanks to PVC protection finalizers — you must delete/detach the consuming Pod first.

40. **Q: How does a StatefulSet handle a rolling update differently from a Deployment by default?**
    A: It updates Pods in reverse ordinal order, one at a time (highest number first), waiting for each to become Ready before moving to the next — preserving ordering guarantees that stateful apps often depend on.

41. **Q: What is `podManagementPolicy: Parallel` on a StatefulSet?**
    A: Overrides the default strict ordered creation/deletion, letting all Pods launch/terminate simultaneously — useful when your stateful app doesn't actually require strict startup ordering and you want faster scaling.

42. **Q: Scenario — after deleting a StatefulSet with `kubectl delete statefulset web`, do the Pods and PVCs disappear immediately?**
    A: The StatefulSet's Pods are deleted, but the PVCs created via volumeClaimTemplates remain untouched — this is intentional, protecting data from accidental deletion of the controller object.

43. **Q: What field guarantees a StatefulSet Pod always gets matched to the same PVC across restarts?**
    A: The naming convention `<volumeClaimTemplate-name>-<statefulset-name>-<ordinal>` (e.g., `www-web-0`) — Kubernetes deterministically re-associates that exact PVC to the Pod with the matching ordinal every time.

44. **Q: How would you verify that a StatefulSet's persistent storage actually survives a Pod deletion (practical test)?**
    A: Write a unique file into the volume via `kubectl exec`, delete the Pod, wait for it to be recreated, then `kubectl exec` again to confirm the file is still there.

45. **Q: What is the main reason StatefulSets are more complex to operate than Deployments?**
    A: They involve managing real persistent data and ordering guarantees — scaling down, deleting, or migrating a StatefulSet risks real data loss/corruption if PVC cleanup and ordering aren't handled carefully, unlike stateless Deployment Pods which are fully disposable.

46. **Q: Scenario — you need a shared configuration file readable (not writable) by many Pods across different Nodes. Is a PVC the right tool?**
    A: Only if the StorageClass supports `ReadOnlyMany`/`ReadWriteMany` (e.g., NFS-backed); otherwise a ConfigMap (Day 3) is usually the simpler, more appropriate tool for shared read-only config rather than a PV/PVC.

47. **Q: What command lets you watch StatefulSet Pods come up in real time to observe ordering?**
    A: `kubectl get pods -w` (the `-w`/watch flag streams live updates as Pods progress through creation).

48. **Q: Scenario — `web-0` is stuck `Pending` because its PVC can't bind, and you notice `web-1`/`web-2` haven't even been created yet. Why?**
    A: StatefulSets create Pods strictly in order and wait for each to become Ready before starting the next — since `web-0` never becomes Ready, `web-1` and `web-2` are intentionally blocked until it does.

49. **Q: Why is understanding PV/PVC essential even though beginners often skip storage topics?**
    A: Almost every real-world app eventually needs a database or persistent state, and misunderstanding volume lifecycle is one of the most common causes of "I lost my data after a redeploy" incidents in production.

50. **Q: What's the single most important mental model to take from today?**
    A: Storage (PV/PVC) and compute (Pod) have independent lifecycles — a Pod is disposable and gets recreated constantly, but its PVC-backed data is not, as long as you never explicitly delete the PVC.

---

## Day 5 (3 hrs) — Ingress, Namespaces & RBAC

### Core Concepts
1. **Ingress** — an L7 (HTTP/HTTPS) routing layer that exposes multiple Services under one external entry point, routing by hostname and/or URL path.
2. **Ingress Controller** — the actual component (e.g., NGINX Ingress Controller) that implements Ingress rules; Ingress objects do nothing without one running.
3. **Namespace** — a virtual cluster/partition within a physical cluster, isolating resource names and enabling per-team/per-environment scoping.
4. **Role & RoleBinding** — RBAC primitives defining "who can do what" within a namespace (Role = permissions, RoleBinding = grants those permissions to a user/group/ServiceAccount).
5. **ClusterRole & ClusterRoleBinding** — the cluster-wide equivalents, for permissions spanning all namespaces or on cluster-scoped resources.

### Hands-On Examples

**Example 1 — Enable Ingress on Minikube and deploy an Ingress rule:**
```bash
minikube addons enable ingress
```
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: nginx-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: myapp.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nginx-service
            port:
              number: 80
```
```bash
kubectl apply -f ingress.yaml
echo "$(minikube ip) myapp.local" | sudo tee -a /etc/hosts
curl myapp.local
```

**Example 2 — Namespaces:**
```bash
kubectl create namespace dev
kubectl create namespace prod
kubectl apply -f deployment.yaml -n dev
kubectl get pods -n dev
kubectl config set-context --current --namespace=dev
```

**Example 3 — RBAC: Role + RoleBinding restricting a user to read-only Pods in `dev`:**
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: dev
  name: pod-reader
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: read-pods-binding
  namespace: dev
subjects:
- kind: ServiceAccount
  name: dev-viewer
  namespace: dev
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```
```bash
kubectl create serviceaccount dev-viewer -n dev
kubectl apply -f role.yaml
kubectl auth can-i list pods --as=system:serviceaccount:dev:dev-viewer -n dev
kubectl auth can-i delete pods --as=system:serviceaccount:dev:dev-viewer -n dev
```

### Project: Namespaced Multi-Environment App with Ingress
Create `dev` and `prod` namespaces, deploy the same app Deployment+Service into both, expose the `prod` one via Ingress at `myapp.local`, and create a restricted ServiceAccount that can only read (not modify/delete) Pods in `dev` — verify restrictions with `kubectl auth can-i`.

### Free Resources
- [Kubernetes Docs: Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)
- [Kubernetes Docs: Ingress Controllers](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/)
- [Kubernetes Docs: Namespaces](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)
- [Kubernetes Docs: RBAC](https://kubernetes.io/docs/reference/access-control/rbac/)
- [Minikube Ingress Addon Tutorial](https://kubernetes.io/docs/tasks/access-application-cluster/ingress-minikube/)

### Top 50 Questions & Answers

1. **Q: What problem does Ingress solve that Services alone don't?**
   A: Without Ingress, exposing many HTTP apps externally means many NodePort/LoadBalancer Services (many IPs/ports). Ingress lets one entry point route by hostname/path to many backend Services, using standard HTTP semantics like host-based and path-based routing.

2. **Q: Does creating an Ingress object alone expose anything?**
   A: No — an Ingress resource is just a routing rule. It requires an Ingress Controller (like NGINX Ingress Controller) running in the cluster to actually watch and implement those rules.

3. **Q: What is an Ingress Controller?**
   A: The actual software (usually a Pod/Deployment, e.g., NGINX-based) that watches Ingress objects and configures a reverse proxy/load balancer accordingly to route real traffic.

4. **Q: Write the command to enable the Ingress Controller addon on Minikube.**
   A: `minikube addons enable ingress`

5. **Q: What is `pathType: Prefix` vs `pathType: Exact` in an Ingress rule?**
   A: `Prefix` matches the given path and everything under it (e.g., `/api` matches `/api/users`). `Exact` matches only that literal path with no sub-paths.

6. **Q: Scenario — you apply an Ingress but `curl myapp.local` gives "could not resolve host." What's missing?**
   A: DNS resolution for `myapp.local` isn't configured — on Minikube you must add an entry mapping `myapp.local` to `$(minikube ip)` in your local `/etc/hosts` file (there's no real DNS server involved in this local setup).

7. **Q: How does host-based routing work in Ingress?**
   A: Multiple `rules` entries each specify a different `host` (e.g., `api.myapp.local`, `web.myapp.local`) — the Ingress Controller inspects the incoming HTTP `Host` header and routes to the matching rule's backend Service.

8. **Q: How does path-based routing work in Ingress?**
   A: Within a single host's rules, multiple `paths` entries route different URL prefixes (e.g., `/api` → api-service, `/` → web-service) to different backend Services under the same domain.

9. **Q: Scenario — your Ingress rule targets a Service on port 80, but the Service itself has no matching Pods. What happens when you `curl`?**
   A: The Ingress Controller returns an error (often a 502 Bad Gateway) since it successfully routes to the Service but finds no healthy backend Endpoints to actually forward the request to.

10. **Q: How does TLS/HTTPS termination work with Ingress?**
    A: You reference a `kubernetes.io/tls`-type Secret (containing `tls.crt`/`tls.key`) in the Ingress's `spec.tls` section — the Ingress Controller terminates HTTPS at that layer and forwards plain HTTP internally to your Service.

11. **Q: Write the command to list all Ingress objects in the current namespace.**
    A: `kubectl get ingress`

12. **Q: What is a Namespace?**
    A: A way to logically partition a single physical cluster into multiple virtual clusters — most object names only need to be unique within a namespace, not across the whole cluster.

13. **Q: Write the command to create a namespace.**
    A: `kubectl create namespace <name>`

14. **Q: Write the command to list all Pods in a specific namespace.**
    A: `kubectl get pods -n <namespace>`

15. **Q: Write the command to list Pods across ALL namespaces.**
    A: `kubectl get pods --all-namespaces` (or the shorthand `-A`)

16. **Q: Scenario — you `kubectl apply -f deployment.yaml` with no `-n` flag or namespace in the YAML. Where does it end up?**
    A: In whatever namespace your current kubectl context is set to (`default` unless you've changed it) — check with `kubectl config view --minify | grep namespace`.

17. **Q: Write the command to permanently switch your default working namespace for future kubectl commands.**
    A: `kubectl config set-context --current --namespace=<name>`

18. **Q: What are the four default namespaces every cluster starts with?**
    A: `default` (unlabeled user resources go here), `kube-system` (Kubernetes system components), `kube-public` (readable by all, even unauthenticated users), and `kube-node-lease` (node heartbeat/lease objects).

19. **Q: Scenario — you have identically-named Deployments `api` in namespaces `dev` and `prod`. Is that a conflict?**
    A: No — namespaces provide name isolation; `api` in `dev` and `api` in `prod` are completely independent objects that happen to share a name.

20. **Q: Are all Kubernetes objects namespaced?**
    A: No — some are cluster-scoped (Nodes, PersistentVolumes, ClusterRoles, Namespaces themselves), meaning they exist once cluster-wide and aren't tied to any single namespace.

21. **Q: Write the command to check if an object type is namespaced or cluster-scoped.**
    A: `kubectl api-resources` — includes a NAMESPACED column (true/false) for every resource type.

22. **Q: What is RBAC and what does it stand for?**
    A: Role-Based Access Control — the mechanism controlling which users/service accounts can perform which actions (verbs) on which resources, based on assigned roles.

23. **Q: What is a Role in RBAC?**
    A: A namespaced object defining a set of permissions (verbs like get/list/create/delete on resources like pods/deployments), scoped to a single namespace.

24. **Q: What is a ClusterRole and how does it differ from a Role?**
    A: Same structure as a Role (permissions/verbs/resources) but cluster-scoped — usable either for cluster-wide resources (like Nodes) or, via a RoleBinding, granted within just one specific namespace too.

25. **Q: What is a RoleBinding?**
    A: Grants the permissions defined in a Role (or ClusterRole) to specific subjects (users, groups, or ServiceAccounts) — within the RoleBinding's own namespace.

26. **Q: What is a ClusterRoleBinding?**
    A: Grants a ClusterRole's permissions to subjects across the ENTIRE cluster, all namespaces at once — used sparingly, for genuinely cluster-wide access needs (e.g., cluster admins).

27. **Q: Scenario — you bind a ClusterRole via a RoleBinding (not a ClusterRoleBinding) in namespace `dev`. What's the effect?**
    A: The subject gets the ClusterRole's permissions, but only within the `dev` namespace — a common, useful pattern for reusing a predefined ClusterRole's permission set without granting cluster-wide access.

28. **Q: What is a ServiceAccount and how does it differ from a regular user?**
    A: An identity for processes/Pods running inside the cluster (not humans) — Pods authenticate to the API server as a ServiceAccount, which can be bound to Roles just like a human user.

29. **Q: Write the command to test whether a specific ServiceAccount can perform an action.**
    A: `kubectl auth can-i <verb> <resource> --as=system:serviceaccount:<namespace>:<sa-name> -n <namespace>` — e.g., `kubectl auth can-i delete pods --as=system:serviceaccount:dev:dev-viewer -n dev`.

30. **Q: Scenario — `kubectl auth can-i delete pods --as=system:serviceaccount:dev:dev-viewer -n dev` returns "no." What does that confirm?**
    A: The RBAC Role bound to that ServiceAccount doesn't include `delete` in its verbs list for pods — exactly the least-privilege restriction you intended when only granting `get`/`list`/`watch`.

31. **Q: What are the common RBAC verbs?**
    A: `get`, `list`, `watch` (read operations), `create`, `update`, `patch`, `delete` (write operations) — you compose exactly the verbs a Role needs, following least-privilege.

32. **Q: What does the empty string `""` mean in a Role's `apiGroups` field?**
    A: It refers to the "core" API group — resources like Pods, Services, ConfigMaps, Secrets, Namespaces that don't belong to a named group (unlike, e.g., `apps` for Deployments/StatefulSets).

33. **Q: Scenario — your Role's `rules` lists `resources: ["pods"]` but you also need permission on `pods/log` (to view logs). What's needed?**
    A: Add a separate/additional entry for `resources: ["pods/log"]` — subresources like logs, exec, and status are permissioned independently from the parent resource.

34. **Q: What's the principle of "least privilege" and why does it matter in RBAC design?**
    A: Grant only the exact permissions a user/ServiceAccount needs to do its job, nothing more — minimizing blast radius if credentials are compromised or a bug causes unintended API calls.

35. **Q: Write the command to view all Roles in a namespace.**
    A: `kubectl get roles -n <namespace>`

36. **Q: Write the command to view all RoleBindings in a namespace.**
    A: `kubectl get rolebindings -n <namespace>`

37. **Q: Scenario — a Pod's application code needs to call the Kubernetes API to list other Pods (e.g., a monitoring tool). What's the standard setup?**
    A: Create a dedicated ServiceAccount, a Role granting the needed verbs (e.g., `get`/`list` on `pods`), and a RoleBinding connecting them — then reference that ServiceAccount in the Pod's `spec.serviceAccountName`.

38. **Q: What ServiceAccount does a Pod use if you don't specify one?**
    A: The `default` ServiceAccount for that namespace, which by default has minimal/no meaningful permissions beyond basic API discovery in most clusters.

39. **Q: Scenario — you want to delete an entire environment cleanly, including all Deployments, Services, ConfigMaps, etc. inside it. What's the single command?**
    A: `kubectl delete namespace <name>` — deletes the namespace and cascades deletion to every object within it.

40. **Q: Why is deleting a namespace considered risky and worth double-checking before running?**
    A: It's irreversible and recursively destroys everything inside — every Deployment, Service, Secret, PVC, etc. in that namespace, with no built-in "are you sure" beyond kubectl's own confirmation-less default.

41. **Q: What is a NetworkPolicy (brief context, related to namespaces) and does Minikube's default CNI support it?**
    A: An object that restricts which Pods can talk to which other Pods/namespaces at the network level (a firewall for Pod traffic) — not all CNI plugins enforce it, and Minikube's default driver may not without enabling a compatible network add-on.

42. **Q: Scenario — you're granted a ClusterRoleBinding making you `cluster-admin`. What can you do?**
    A: Essentially anything, in any namespace and on cluster-scoped resources — the most powerful built-in ClusterRole, so it should be granted extremely sparingly (equivalent to root/superuser).

43. **Q: What does `kubectl get clusterrolebinding` show, and why should a beginner review it periodically?**
    A: All cluster-wide permission grants — reviewing it helps catch overly broad access (e.g., accidentally binding `cluster-admin` to a service account) that violates least-privilege.

44. **Q: Scenario — two teams share one cluster and you want to prevent Team A from even seeing Team B's resources by default. What combination achieves this?**
    A: Separate namespaces per team (`team-a`, `team-b`) plus RBAC Roles/RoleBindings scoping each team's users to only their own namespace — without RBAC, namespaces alone don't enforce access control, only naming/resource isolation.

45. **Q: Is namespace isolation alone (without RBAC) a security boundary?**
    A: No — namespaces separate object names and allow resource quotas, but without RBAC restricting API access, any authenticated user could still `kubectl get pods -n <other-namespace>` and see/act on other teams' resources.

46. **Q: Write the command to create an Ingress imperatively without a YAML file (for a quick test).**
    A: `kubectl create ingress test-ingress --rule="myapp.local/=nginx-service:80"`

47. **Q: Scenario — your Ingress works for `/` but returns 404 for `/api/users` even though you defined a `/api` path rule. What's a likely cause?**
    A: The backend Service/app expects the full path `/api/users` but the Ingress rewrite-target annotation is stripping the prefix incorrectly (or vice versa) — check the `nginx.ingress.kubernetes.io/rewrite-target` annotation and ensure it matches what your app actually expects to receive.

48. **Q: What is the relationship between Ingress and Services — does Ingress replace Services?**
    A: No — Ingress routes traffic TO Services (never directly to Pods); you still need a ClusterIP Service per app behind the Ingress, which then handles the final Pod-level load balancing.

49. **Q: Scenario — you want `api.myapp.local` and `web.myapp.local` to both work over HTTPS with different certificates. How does Ingress support this?**
    A: Define multiple entries in `spec.tls`, each specifying its own `hosts` list and a distinct `secretName` referencing the appropriate TLS Secret for that specific host (SNI-based multi-cert termination).

50. **Q: What's the single biggest RBAC mistake beginners make, and how do you avoid it?**
    A: Granting `cluster-admin` (or overly broad ClusterRoles) to ServiceAccounts/users out of convenience instead of writing a narrowly-scoped Role — always start from the minimum verbs/resources actually needed and expand only when a real, observed need arises.

---

## Day 6 (3 hrs) — Troubleshooting, Logging & Capstone

### Core Concepts
1. **The debugging funnel** — `get` (spot the symptom) → `describe` (read Events) → `logs` (see app output) → `exec` (inspect from inside) — the standard order of operations for any Kubernetes issue.
2. **Common failure states** — Pending, ImagePullBackOff, CrashLoopBackOff, OOMKilled, Error, each with a distinct root-cause category.
3. **Resource requests/limits and quotas** — how misconfigured or exhausted CPU/memory causes scheduling failures and throttling/kills.
4. **Log aggregation basics** — why per-Pod `kubectl logs` doesn't scale, and the role of centralized logging (conceptually, without deploying a full stack).
5. **Health probes as the root of most self-healing** — tying readiness/liveness probes (seen Day 1) back into why Kubernetes takes the actions it does during failures.

### Hands-On Examples

**Example 1 — The debugging funnel in practice:**
```bash
kubectl get pods                       # 1. spot the symptom (status, restarts)
kubectl describe pod <name>            # 2. read Events at the bottom
kubectl logs <name> --previous         # 3. see what the crashed process printed
kubectl exec -it <name> -- sh          # 4. poke around live (env vars, files, connectivity)
```

**Example 2 — Deliberately break things to practice diagnosis:**
```yaml
# broken-pod.yaml — bad image tag on purpose
apiVersion: v1
kind: Pod
metadata:
  name: broken-pod
spec:
  containers:
  - name: app
    image: nginx:this-tag-does-not-exist
```
```bash
kubectl apply -f broken-pod.yaml
kubectl get pods            # ImagePullBackOff
kubectl describe pod broken-pod   # read the exact pull error in Events
```

**Example 3 — Resource limits causing OOMKill:**
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: oom-demo
spec:
  containers:
  - name: hog
    image: polinux/stress
    resources:
      limits:
        memory: "50Mi"
    command: ["stress"]
    args: ["--vm", "1", "--vm-bytes", "150M", "--vm-hang", "1"]
```
```bash
kubectl apply -f oom-demo.yaml
kubectl get pods -w         # watch it get OOMKilled and restart
kubectl describe pod oom-demo   # confirm "Reason: OOMKilled"
```

### Project (Capstone): Break It, Then Fix It
Deploy three intentionally broken manifests (bad image tag, wrong `targetPort` on a Service, memory limit too low for the app) into a `troubleshoot` namespace. Using only `kubectl get`, `describe`, `logs`, and `exec`, diagnose and fix all three without looking at the "answer" (the deliberate misconfiguration) until you've formed a hypothesis from the tool output alone. This exercises everything from Days 1–5 end-to-end.

### Free Resources
- [Kubernetes Docs: Troubleshoot Applications](https://kubernetes.io/docs/tasks/debug/debug-application/)
- [Kubernetes Docs: Debug Running Pods](https://kubernetes.io/docs/tasks/debug/debug-application/debug-running-pod/)
- [Kubernetes Docs: Determine the Reason for Pod Failure](https://kubernetes.io/docs/tasks/debug/debug-application/determine-reason-pod-failure/)
- [Kubernetes Docs: Resource Management for Pods and Containers](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)
- [Kubernetes Basics Interactive Tutorial](https://kubernetes.io/docs/tutorials/kubernetes-basics/) (revisit for hands-on practice)

### Top 50 Questions & Answers

1. **Q: What is the recommended order of commands when debugging any Kubernetes issue?**
   A: `kubectl get` (spot which object/status is wrong) → `kubectl describe` (read the Events log for the reason) → `kubectl logs` (see what the application itself reported) → `kubectl exec` (interactively inspect from inside if still unclear).

2. **Q: Why check `kubectl describe` before `kubectl logs`?**
   A: `describe`'s Events section often reveals infrastructure-level problems (scheduling failures, image pull errors, OOMKills) that happen BEFORE your application code even runs — `logs` only shows output from a container that actually started.

3. **Q: Scenario — `kubectl get pods` shows STATUS `Pending` for over 5 minutes. What's your very next command?**
   A: `kubectl describe pod <name>` — check the Events section for "FailedScheduling" and its stated reason (insufficient resources, unmatched node selector, unbound PVC, etc.).

4. **Q: What are the three most common causes of a Pod stuck in `Pending`?**
   A: (1) Insufficient CPU/memory on any Node to satisfy the Pod's resource requests. (2) A nodeSelector/affinity rule no Node satisfies. (3) A PersistentVolumeClaim that can't bind (no matching PV, no StorageClass).

5. **Q: Scenario — `kubectl get pods` shows `CrashLoopBackOff` with restart count climbing. What command reveals why the previous attempt failed?**
   A: `kubectl logs <name> --previous` — shows the log output from the last crashed instance of the container, since the current instance's logs may not have printed the error yet (or is in a fresh backoff wait).

6. **Q: What does `ImagePullBackOff` mean, distinct from `ErrImagePull`?**
   A: `ErrImagePull` is the immediate first failed pull attempt; `ImagePullBackOff` is Kubernetes waiting with exponential backoff before retrying after repeated failures — both point to the same underlying image-pull problem.

7. **Q: Write the three most common root causes of `ImagePullBackOff`.**
   A: (1) Typo in image name or tag. (2) Image genuinely doesn't exist in that registry. (3) Missing/incorrect registry credentials (`imagePullSecrets`) for a private repository.

8. **Q: Scenario — `kubectl describe pod` shows `Reason: OOMKilled` under a container's Last State. What's the direct cause and two possible fixes?**
   A: The container exceeded its `resources.limits.memory`. Fixes: (1) raise the memory limit if the workload legitimately needs more, or (2) investigate/fix a memory leak in the app if the limit is already reasonable.

9. **Q: What does the Pod-level status `Error` (as opposed to CrashLoopBackOff) typically indicate?**
   A: The container ran and exited with a non-zero code but hasn't yet been restarted enough times to be classified as crash-looping, or `restartPolicy: Never` is set — check `kubectl logs` for the actual application error.

10. **Q: Write the command to view logs from a Pod's previous (crashed) instance.**
    A: `kubectl logs <pod-name> --previous`

11. **Q: Write the command to stream logs live as they're produced.**
    A: `kubectl logs -f <pod-name>`

12. **Q: Scenario — you need logs from all 3 replicas of a Deployment at once, not just one Pod. What's a practical approach with just kubectl?**
    A: `kubectl logs -l app=<label> --all-containers --prefix` (label selector across matching Pods, prefixing each line with its source Pod) — for anything beyond ad-hoc debugging, a centralized log aggregator (e.g., shipping logs to Loki/ELK) is the real production answer.

13. **Q: Why doesn't `kubectl logs` scale well as a primary debugging tool in production?**
    A: Pods are ephemeral — once deleted, their logs are gone unless captured elsewhere; and manually checking logs pod-by-pod across many replicas/nodes doesn't scale, which is why centralized log aggregation (shipping stdout/stderr off-node) is standard practice.

14. **Q: What is the conceptual role of a log aggregation stack (e.g., ELK, Loki) that raw `kubectl logs` doesn't provide?**
    A: Persistent storage of logs beyond a Pod's lifetime, searching/filtering across all Pods/Nodes at once, and alerting on log patterns — kubectl only gives you a live, per-Pod, no-history view.

15. **Q: Scenario — your app writes logs to a file inside the container instead of stdout/stderr. Why is this a problem in Kubernetes?**
    A: `kubectl logs` and any node-level log-shipping agent only capture a container's stdout/stderr streams by convention — logs written only to an internal file are invisible to standard Kubernetes tooling unless you add extra volume-mounting/sidecar complexity.

16. **Q: Write the command to check a container's exit code after it crashed.**
    A: `kubectl describe pod <name>` — look under the container's "Last State: Terminated" section for the `Exit Code` and `Reason` fields.

17. **Q: What does exit code 137 typically indicate?**
    A: The container was killed via SIGKILL — very often from being OOMKilled (128 + signal 9 = 137), though it can also result from other external kill signals.

18. **Q: What does exit code 1 (generic) tell you, and what should you check next?**
    A: Just that the application exited with a general error — you must check `kubectl logs` (or `--previous`) for the actual application-level error message, since the exit code alone rarely explains the specific cause.

19. **Q: Scenario — a Pod is `Running` and `1/1 Ready`, but users report the app is unreachable. Where do you look first?**
    A: The Service layer — `kubectl get endpoints <svc>` to confirm the Pod is actually registered as a backend, and `kubectl describe svc` to confirm selector/port correctness (the Pod itself may be healthy while its Service routing is misconfigured).

20. **Q: Write the command to check resource requests/limits currently defined on a running Pod.**
    A: `kubectl describe pod <name>` — shows each container's Requests/Limits under the "Containers" section.

21. **Q: Scenario — many Pods across the namespace are `Pending` and `kubectl describe pod` says "Insufficient cpu." What's the systemic fix, beyond that one Pod?**
    A: Either add more Node capacity to the cluster, reduce the CPU requests on Deployments if they're over-provisioned relative to actual usage, or review/adjust any ResourceQuota capping the namespace.

22. **Q: What is a ResourceQuota (brief, ties resource requests to namespaces)?**
    A: A namespace-level object capping total resource consumption (CPU, memory, object counts) across all Pods in that namespace — prevents one team/namespace from exhausting shared cluster capacity.

23. **Q: Scenario — a Pod that used to schedule fine now stays `Pending` and `describe` shows "exceeded quota." What changed?**
    A: The namespace likely has (or recently had lowered) a ResourceQuota, and either this Pod's request or the sum of existing Pods' requests now exceeds the allowed limit — check `kubectl describe resourcequota -n <namespace>`.

24. **Q: Write the command to get a one-line summary of resource usage per Pod (requires metrics-server).**
    A: `kubectl top pods` (and `kubectl top nodes` for Node-level) — requires the metrics-server addon; on Minikube: `minikube addons enable metrics-server`.

25. **Q: Scenario — `kubectl top pods` returns an error about metrics not being available. What's the fix on Minikube?**
    A: `minikube addons enable metrics-server` — metrics-server isn't enabled by default and is required for `kubectl top` to function.

26. **Q: What does a rising restart count combined with `Running` status (not CrashLoopBackOff) suggest?**
    A: The container is crashing occasionally but not frequently/consistently enough to trigger the CrashLoopBackOff label yet — still worth investigating via `kubectl logs --previous` before it worsens.

27. **Q: Scenario — you fix a bug and update the Deployment's image, but `kubectl get pods` still shows old Pods running. What might be wrong?**
    A: You may have used the same image tag (e.g., `:latest`) without Kubernetes detecting a "change" to trigger a rollout, or `imagePullPolicy` is set to `IfNotPresent` and the Node already has a cached (stale) copy of that tag — best practice is unique, immutable tags per build.

28. **Q: Write the command to force a rollout restart even without changing the image tag.**
    A: `kubectl rollout restart deployment/<name>` — creates new Pods (picking up any updated ConfigMap/Secret refs or forcing a fresh image pull) without requiring a manifest change.

29. **Q: Scenario — `kubectl describe pod` shows readiness probe failures repeatedly, but the container itself never restarts. Why not?**
    A: Readiness failures only remove the Pod from Service Endpoints (traffic routing) — they do NOT restart the container; only liveness probe failures (or crashes) trigger a restart.

30. **Q: What field lets you tune how quickly a probe considers a container failed vs. still starting up?**
    A: `initialDelaySeconds` (grace period before the first check), `periodSeconds` (check frequency), and `failureThreshold` (consecutive failures before acting) — misconfigured values are a very common source of false-positive restarts/removals.

31. **Q: Scenario — a slow-starting app (30s boot time) gets killed by its liveness probe before it ever finishes starting. What's the fix?**
    A: Increase `initialDelaySeconds` past the app's typical startup time, or (better, in modern Kubernetes) add a `startupProbe` that suppresses liveness checks until startup is confirmed complete.

32. **Q: What is a `startupProbe` and why is it useful for legacy/slow apps?**
    A: A probe that runs only during container startup — while it hasn't succeeded, liveness/readiness probes are disabled, preventing premature kills of slow-starting applications without permanently loosening liveness timing for steady-state operation.

33. **Q: Scenario — you `kubectl exec` into a container to run `curl localhost:8080/health` and it fails, but the app "works" per external users. What does this tell you?**
    A: The failure is likely upstream (DNS/Service/Ingress layer) rather than the app itself — the fact that a direct in-container check succeeds/fails is the cleanest way to isolate "is it the app or is it the network path to it."

34. **Q: Write the command to check events across an entire namespace, not just one Pod.**
    A: `kubectl get events -n <namespace> --sort-by='.lastTimestamp'` — useful for seeing a chronological feed of everything (scheduling, pulls, probe failures) happening namespace-wide.

35. **Q: Scenario — `kubectl get events` shows a burst of "BackOff" and "Unhealthy" events shortly after a deploy. What's the likely story those events are telling?**
    A: New Pods are failing their health checks repeatedly right after rollout — check `kubectl logs` on the new Pods for a startup error and confirm the probe configuration/timing actually fits the new version's behavior.

36. **Q: What is the difference in troubleshooting approach between a Pod-level problem and a Service-level problem?**
    A: Pod-level: focus on `describe`/`logs`/`exec` on the specific Pod. Service-level: focus on `describe svc`, `get endpoints`, and verifying label/selector/port alignment — a perfectly healthy Pod can still be unreachable due to a Service misconfiguration.

37. **Q: Scenario — you suspect a NetworkPolicy (or firewall-like rule) is silently blocking traffic between two Pods that both look healthy. How would you test this in isolation?**
    A: `kubectl exec` into the source Pod and try a direct raw connection test (`curl`, `wget`, or `nc`) to the destination Pod's IP/port — bypassing the Service layer to isolate whether it's a network-policy/connectivity issue versus a Service-configuration issue.

38. **Q: What's the value of intentionally breaking things (like today's capstone) as a learning method?**
    A: Real production incidents rarely announce their cause — practicing pattern recognition on deliberately-injected common failures (bad image, bad port, tight memory limit) builds the instinct to recognize the same symptoms quickly under real pressure.

39. **Q: Scenario — after fixing a Service's wrong `targetPort`, do you need to delete/recreate the Pods for connectivity to be restored?**
    A: No — Services route based on live label-matched Endpoints; fixing the Service's `targetPort` and re-applying takes effect immediately without touching the underlying Pods at all.

40. **Q: What is the fastest way to confirm "is my problem the Pod, the Service, or the Ingress" when a user reports "the site is down"?**
    A: Test from the inside out: `kubectl exec` into a Pod and confirm the app responds locally → `curl` the ClusterIP Service from another Pod → `curl` through the Ingress's external URL — the layer where it first stops working is where the problem lives.

41. **Q: Scenario — a ConfigMap change was applied, but the app still behaves with old settings after a Pod restart. What's a step often forgotten?**
    A: Confirming the Pod is actually reading from that ConfigMap (correct name/key referenced) and that you restarted the right Deployment/namespace — also double-check you didn't edit a same-named ConfigMap in the wrong namespace.

42. **Q: What information does `kubectl get pod <name> -o yaml` give you that `describe` sometimes doesn't make obvious?**
    A: The full, exact live spec/status as stored by the API server — useful for spotting subtle misconfigurations (wrong image string, missing field, unexpected default) that `describe`'s human-formatted summary might gloss over.

43. **Q: Scenario — `kubectl apply -f file.yaml` succeeds with no errors, but nothing changes in the cluster. What should you check?**
    A: Confirm you're applying to the intended namespace/context (`kubectl config current-context`, and any `-n` flag or `metadata.namespace` in the file) — a very common "silent" mistake is applying successfully to the wrong namespace or cluster.

44. **Q: Write the command to check which cluster/context your kubectl commands are currently targeting.**
    A: `kubectl config current-context` (and `kubectl config get-contexts` to see all available ones).

45. **Q: Scenario — everything you `kubectl apply` seems to vanish or not show up in `kubectl get`. What's a likely root cause worth checking first?**
    A: You're switching between multiple contexts/clusters (e.g., Minikube vs. a cloud cluster) without realizing it — always verify `kubectl config current-context` matches where you expect to be working.

46. **Q: What is the value of labeling all objects in a project consistently (e.g., `app`, `env`, `team`) for troubleshooting?**
    A: Enables fast, precise filtering during an incident — `kubectl get pods -l app=checkout,env=prod` narrows a large cluster down to exactly the relevant objects instantly, instead of scrolling through everything.

47. **Q: Scenario — you've fixed the bad image tag, wrong targetPort, and memory limit in today's capstone. What single command across all three fixes confirms success end-to-end?**
    A: `kubectl get pods -n troubleshoot -o wide` showing all Pods `Running`/`Ready`, combined with a successful `curl` through the Service/Ingress confirming actual traffic reaches the app — status alone isn't proof; a real request completing is.

48. **Q: What's the difference between treating symptoms and finding root cause in Kubernetes troubleshooting?**
    A: E.g., repeatedly deleting a CrashLoopBackOff Pod "fixes" the symptom for a few seconds but the same crash recurs — the root cause (bad config, insufficient resources, app bug) must be found via logs/describe and actually corrected, not worked around by restarting.

49. **Q: Having completed all 6 days, what are the 8 areas you can now troubleshoot and operate independently?**
    A: Pods/Deployments (workload basics), Services/networking (connectivity), ConfigMaps/Secrets (configuration), StatefulSets/PVs (persistent state), Ingress (external HTTP routing), Namespaces/RBAC (isolation & access control), and the troubleshooting workflow tying all of them together.

50. **Q: What's the recommended next step after this 20-hour roadmap, staying within the 80/20 principle?**
    A: Rebuild a small real app (e.g., a to-do API + database) end-to-end using everything from Days 1–6 in one cohesive manifest set — repetition on a real project cements these fundamentals far more than consuming additional new topics before this base is solid.

---

## Summary Schedule

| Day | Hours | Topic | Project |
|-----|-------|-------|---------|
| 1 | 4h | Pods & Deployments | Deploy, scale, and roll back an nginx Deployment |
| 2 | 4h | Services & Networking | Expose the app internally (ClusterIP) and externally (NodePort) |
| 3 | 3h | ConfigMaps & Secrets | Multi-container Pod reading config/secret data |
| 4 | 3h | StatefulSets & Persistent Volumes | 3-replica StatefulSet with per-Pod persistent storage |
| 5 | 3h | Ingress, Namespaces & RBAC | Multi-namespace app with Ingress and a restricted ServiceAccount |
| 6 | 3h | Troubleshooting & Logging | Capstone: diagnose and fix 3 intentionally broken manifests |
| **Total** | **20h** | | |

After Day 6, you'll be able to independently deploy, expose, configure, persist, route, secure, and debug a real application on Kubernetes — the core 20% of the platform that covers the vast majority of day-to-day work.
