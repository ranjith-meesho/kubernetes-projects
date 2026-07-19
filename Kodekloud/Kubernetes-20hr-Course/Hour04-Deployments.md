# Hour 4: ReplicaSets, Deployments, Rolling Updates, Rollbacks

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine a store manager whose job is to make sure exactly **5 cashiers** are always staffed at the checkout counters. If one cashier goes home sick, the manager immediately calls in a replacement. If a cashier is fired, another is hired within minutes. This manager doesn't care *which* specific person is at register 3 — only that the *count* stays at 5. This manager is a **ReplicaSet**.

Now imagine the store owner (bigger boss) who doesn't talk to cashiers directly at all. Instead, the owner talks to the manager: "I want 5 cashiers, and starting Monday, retrain them all to use the new register software — but do it 1 at a time so checkout never fully stops." The owner also keeps a logbook of every past instruction, so if the new register software turns out buggy, they can say "go back to what we had yesterday" instantly. This owner is a **Deployment**.

**Technical version:**
- A **ReplicaSet (RS)** is a Kubernetes object whose sole job is: "ensure exactly N replicas of a given Pod template are running at all times." If a Pod dies, the RS controller notices (via the control loop) and creates a new one. If you manually delete a Pod that's managed by an RS, it gets recreated immediately.
- A **Deployment** is a higher-level controller that **owns and manages ReplicaSets** for you. You almost never create a ReplicaSet directly — you create a Deployment, and Kubernetes creates the ReplicaSet underneath it automatically.
- Why do we need the Deployment layer at all, if ReplicaSet already keeps Pods alive? Because ReplicaSets have **no concept of versioning or update strategy**. If you change the Pod template inside a raw ReplicaSet, nothing happens to existing Pods — you'd have to manually delete old Pods yourself. Deployments solve this by:
  1. **Declarative updates** — you just change the image/spec, and the Deployment figures out how to get there.
  2. **Rolling updates** — gradually replacing old Pods with new ones with zero downtime.
  3. **Rollback** — reverting to a previous known-good version instantly, using stored revision history.
  4. **Pause/resume rollouts**, and scaling.
- **Mechanics of a rolling update:** When you update a Deployment's Pod spec (e.g., a new container image), Kubernetes:
  1. Creates a **new ReplicaSet** with the updated Pod template (revision N+1).
  2. Scales the **new** ReplicaSet up gradually while scaling the **old** ReplicaSet down, controlled by two parameters:
     - `maxSurge` (default `25%`): how many *extra* Pods above the desired count can be created during the rollout (allows faster rollout by briefly running more Pods than requested).
     - `maxUnavailable` (default `25%`): how many Pods can be *unavailable* (not ready) at once during the rollout (controls how much capacity can dip during rollout).
  3. Kubernetes keeps checking Pod readiness (via readiness probes) before proceeding to replace more Pods — this is what makes it "zero-downtime": traffic through the Service always has enough Ready Pods (old or new) to serve requests.
  4. Once all new Pods are Ready and old Pods are terminated, the rollout is complete. The **old ReplicaSet is kept around (scaled to 0)** for rollback purposes, up to `revisionHistoryLimit` (default 10).
- **Rollback:** `kubectl rollout undo deployment/<name>` tells the Deployment to scale the previous ReplicaSet back up and the current one down — essentially a rolling update in reverse, using the exact previous Pod template. This only works if the old ReplicaSet's revision is still retained in history (governed by `revisionHistoryLimit`).

## 2. Diagram

```
Deployment / ReplicaSet / Pod hierarchy:

┌─────────────────────────────────────────────┐
│               Deployment: web                │
│   (desired state, strategy, revision history) │
└───────────────────┬───────────────────────────┘
                     │ owns
                     ▼
┌─────────────────────────────────────────────┐
│           ReplicaSet: web-7d9f8c             │
│         (ensures exactly 3 Pods exist)        │
└───────┬───────────────┬───────────────┬──────┘
        ▼               ▼               ▼
   ┌─────────┐     ┌─────────┐     ┌─────────┐
   │  Pod 1  │     │  Pod 2  │     │  Pod 3  │
   │ nginx:1.25│   │ nginx:1.25│   │ nginx:1.25│
   └─────────┘     └─────────┘     └─────────┘


Rolling Update sequence (nginx:1.25 -> nginx:1.26), maxSurge=1, maxUnavailable=1:

Step 0 (before):     [v1] [v1] [v1]                       (RS-old: 3, RS-new: 0)
Step 1 (surge new):  [v1] [v1] [v1] [v2]                  (RS-old: 3, RS-new: 1)
Step 2 (kill 1 old):  [v1] [v1] ---- [v2]                 (RS-old: 2, RS-new: 1)
Step 3 (surge new):  [v1] [v1] [v2] [v2]                  (RS-old: 2, RS-new: 2)
Step 4 (kill 1 old):  [v1] ---- [v2] [v2]                 (RS-old: 1, RS-new: 2)
Step 5 (surge new):  [v1] [v2] [v2] [v2]                  (RS-old: 1, RS-new: 3)
Step 6 (kill 1 old):  ---- [v2] [v2] [v2]                 (RS-old: 0, RS-new: 3)
Step 7 (done):             [v2] [v2] [v2]                 (RS-old kept @0 for rollback)

At every step, at least 2 Pods are Ready and serving traffic → zero downtime.

Rollback (kubectl rollout undo) simply runs this same sequence in reverse,
scaling RS-old back up to 3 and RS-new down to 0.
```

## 3. Real-World Example

Meesho's `payment-service` is running `v1` in production, handling live checkout traffic. The team ships `v2` with a new fraud-check feature.

- They update the Deployment's image to `payment-service:v2` and run `kubectl apply`.
- Kubernetes performs a **rolling update**: it spins up `v2` Pods one (or a few) at a time, waits for each to pass its readiness probe (e.g., `/healthz` responds 200), then retires an old `v1` Pod. Throughout this, the Service load-balances across whichever Pods (v1 or v2) are Ready — checkout never goes down.
- Ten minutes later, alerts fire: `v2`'s fraud-check has a bug rejecting valid payments. Instead of scrambling to manually redeploy old code, the on-call engineer runs:
  ```
  kubectl rollout undo deployment/payment-service
  ```
- Because Kubernetes kept the old ReplicaSet (revision history), it immediately scales `v1` Pods back up and `v2` Pods down — production is back to known-good `v1` within seconds, without rebuilding or re-pushing any image.

This "instant undo" is precisely why Deployments (not raw Pods or ReplicaSets) are the default way anything is shipped to production in Kubernetes.

## 4. Hands-On Lab

**Goal:** Create a Deployment, scale it, update its image via rolling update, inspect rollout history, and roll back.

**Step 1 — Create the Deployment YAML** (`nginx-deployment.yaml`):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  replicas: 3
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 1
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
        resources:
          requests:
            cpu: "100m"
            memory: "64Mi"
          limits:
            cpu: "200m"
            memory: "128Mi"
```

**Step 2 — Apply it:**
```bash
kubectl apply -f nginx-deployment.yaml
```
Expected output:
```
deployment.apps/nginx-deployment created
```

**Step 3 — Verify Pods and the auto-created ReplicaSet:**
```bash
kubectl get deployments
kubectl get replicasets
kubectl get pods -l app=nginx
```
Expected output (abbreviated):
```
NAME                READY   UP-TO-DATE   AVAILABLE   AGE
nginx-deployment    3/3     3            3           30s

NAME                           DESIRED   CURRENT   READY   AGE
nginx-deployment-7d9f8c9c6b    3         3         3       30s

NAME                                 READY   STATUS    RESTARTS   AGE
nginx-deployment-7d9f8c9c6b-4kxqz    1/1     Running   0          30s
nginx-deployment-7d9f8c9c6b-8zplt    1/1     Running   0          30s
nginx-deployment-7d9f8c9c6b-vb2wq    1/1     Running   0          30s
```

**Step 4 — Scale it:**
```bash
kubectl scale deployment nginx-deployment --replicas=5
kubectl get pods -l app=nginx
```
Expected: 5 Pods now show `Running`.

**Step 5 — Update the image (triggers a rolling update):**
```bash
kubectl set image deployment/nginx-deployment nginx=nginx:1.26
```
Expected output:
```
deployment.apps/nginx-deployment image updated
```

**Step 6 — Watch the rollout status live:**
```bash
kubectl rollout status deployment/nginx-deployment
```
Expected output (streams until done):
```
Waiting for deployment "nginx-deployment" rollout to finish: 2 out of 5 new replicas have been updated...
Waiting for deployment "nginx-deployment" rollout to finish: 3 out of 5 new replicas have been updated...
Waiting for deployment "nginx-deployment" rollout to finish: 1 old replicas are pending termination...
deployment "nginx-deployment" successfully rolled out
```

**Step 7 — Check rollout history:**
```bash
kubectl rollout history deployment/nginx-deployment
```
Expected output:
```
deployment.apps/nginx-deployment
REVISION  CHANGE-CAUSE
1         <none>
2         <none>
```
(Tip: use `kubectl apply -f nginx-deployment.yaml --record` or annotate `kubernetes.io/change-cause` to populate meaningful CHANGE-CAUSE entries.)

**Step 8 — Simulate a bad rollout (bug scenario):**
```bash
kubectl set image deployment/nginx-deployment nginx=nginx:1.99-does-not-exist
kubectl rollout status deployment/nginx-deployment
```
Expected: it hangs / reports `ImagePullBackOff` — new Pods never become Ready, so the rollout stalls (old Pods are untouched, so you still have partial capacity — this is the safety net rolling updates give you).

**Step 9 — Roll back:**
```bash
kubectl rollout undo deployment/nginx-deployment
kubectl rollout status deployment/nginx-deployment
```
Expected output:
```
deployment.apps/nginx-deployment rolled back
deployment "nginx-deployment" successfully rolled out
```

**Step 10 — Roll back to a specific revision:**
```bash
kubectl rollout history deployment/nginx-deployment
kubectl rollout undo deployment/nginx-deployment --to-revision=1
```

**Troubleshooting:**
- Rollout stuck? Check `kubectl describe deployment nginx-deployment` for events, or `kubectl get pods` for `ImagePullBackOff`/`CrashLoopBackOff`.
- `rollout undo` does nothing? Check `revisionHistoryLimit` hasn't purged the revision you want.

## 5. Common Mistakes

1. **Editing Pods directly instead of the Deployment.** If you `kubectl edit pod ...` on a Pod owned by a ReplicaSet, your change is thrown away the moment that Pod is replaced — the ReplicaSet only knows about the template stored in the Deployment/RS, not your manual tweak. Always edit the Deployment (or its YAML).
2. **Scaling up without setting resource requests/limits first.** Suddenly going from 3 to 50 replicas with no `resources.requests/limits` can starve a node or get Pods OOMKilled/evicted unpredictably, and makes the scheduler's bin-packing decisions unreliable.
3. **Misunderstanding `maxUnavailable` and `maxSurge` defaults.** Both default to `25%`, rounded per Kubernetes' rules — for small replica counts (e.g., `replicas: 2`) this can mean `maxUnavailable` rounds to 0 and `maxSurge` rounds to 1, or vice versa, producing surprising rollout speeds or capacity dips. Always explicitly check behavior for low replica counts.
4. **Assuming rollback always works.** `kubectl rollout undo` depends on old ReplicaSets still existing, which depends on `revisionHistoryLimit` (default 10, but can be set lower or to 0). If old ReplicaSets have been garbage collected, there's nothing to roll back to — treat revision history as a *limited* safety net, not a permanent one.
5. **Confusing "rollout paused/stuck" with "rollout failed."** A stalled rollout (e.g., bad image) doesn't automatically roll back on its own by default — you must run `kubectl rollout undo` yourself, or configure a `progressDeadlineSeconds` and act on failure signals.
6. **Forgetting `--record` / change-cause annotations**, ending up with a rollout history full of `<none>` entries that give no clue what each revision actually changed, making rollbacks a guessing game months later.

## 6. Interview Questions (with brief answers)

1. **Deployment vs ReplicaSet vs Pod — what's the difference?** — A **Pod** is the smallest deployable unit (one or more containers sharing network/storage). A **ReplicaSet** ensures a fixed number of identical Pods are always running (self-healing count). A **Deployment** manages ReplicaSets on your behalf, adding declarative updates, rolling update strategy, and rollback/revision history. In practice: you write Deployments; Kubernetes creates/manages ReplicaSets and Pods for you.
2. **How does Kubernetes achieve zero-downtime deployment?** — Via the **rolling update** strategy: it incrementally creates new-version Pods and terminates old-version Pods, governed by `maxSurge`/`maxUnavailable`, only proceeding once new Pods pass readiness probes. Because the Service always routes to whatever Pods are currently Ready (old or new), there's never a moment with zero available backends.
3. **What happens to the old ReplicaSet after a rolling update completes?** — It's scaled down to 0 replicas but not deleted immediately; it's retained (up to `revisionHistoryLimit`, default 10) so `kubectl rollout undo` can scale it back up quickly instead of rebuilding Pods from scratch.
4. **What's the difference between `maxSurge` and `maxUnavailable`?** — `maxSurge` caps how many *extra* Pods (above desired count) may exist temporarily during rollout (controls rollout speed/resource usage). `maxUnavailable` caps how many Pods may be *missing* from the desired count at once (controls the minimum guaranteed capacity during rollout). Both default to 25%.
5. **If you delete a Pod that's managed by a Deployment, what happens?** — The owning ReplicaSet's control loop detects the actual replica count (2) is below desired (3) and immediately schedules a new Pod to restore the count — this is the self-healing behavior, independent of any rollout logic.

## 7. Quiz (50 Questions)

**True/False:**
1. A ReplicaSet's only job is to maintain a specified number of Pod replicas. (T)
2. You should typically create ReplicaSets directly instead of Deployments. (F)
3. A Deployment automatically creates a ReplicaSet under the hood. (T)
4. Rolling updates always cause some downtime by design. (F)
5. `maxSurge` controls how many extra Pods can exist temporarily during a rollout. (T)
6. `maxUnavailable` and `maxSurge` both default to 25%. (T)
7. Old ReplicaSets are deleted immediately after a successful rollout. (F)
8. `kubectl rollout undo` can fail if revision history has been purged. (T)
9. Editing a Pod directly is a reliable way to permanently change its configuration. (F)
10. Readiness probes influence how a rolling update proceeds. (T)

**Multiple Choice:**
11. What ensures a ReplicaSet's replacement Pod count stays correct? a) A cron job b) The ReplicaSet controller's control loop c) Manual admin intervention d) The kubelet alone → (b)
12. Which object stores rollout revision history? a) Pod b) ReplicaSet directly (unlinked) c) Deployment d) Node → (c)
13. Default value of `maxUnavailable` in a Deployment's rolling update strategy: a) 0% b) 10% c) 25% d) 50% → (c)
14. Which command triggers an instant revert to the previous Deployment revision? a) kubectl scale b) kubectl rollout undo c) kubectl delete deployment d) kubectl edit rs → (b)
15. What happens if you manually delete a Pod owned by a ReplicaSet with desired replicas = 3? a) Nothing b) The RS creates a replacement Pod c) The Deployment is deleted d) The cluster crashes → (b)
16. Which flag lets you view historical revisions of a Deployment? a) kubectl get history b) kubectl rollout history c) kubectl describe revisions d) kubectl logs --history → (b)
17. What is created first when you update a Deployment's image? a) A new Node b) A new ReplicaSet c) A new Namespace d) A new Service → (b)
18. Rolling back to a specific past revision uses which flag? a) --revision b) --to-revision c) --rev d) --history → (b)
19. Which setting limits how many old ReplicaSets are retained for rollback? a) maxSurge b) maxUnavailable c) revisionHistoryLimit d) replicas → (c)
20. What does `kubectl set image deployment/x container=image:tag` do? a) Deletes the Deployment b) Updates the Pod template's image, triggering a rollout c) Only updates a running Pod's binary in place d) Restarts the whole cluster → (b)

**Short Answer:**
21. In one sentence, what does a ReplicaSet guarantee?
22. In one sentence, what extra capability does a Deployment add on top of a ReplicaSet?
23. What are the two parameters that control rolling update pacing?
24. What command shows the live progress of an ongoing rollout?
25. What command shows past revisions of a Deployment?
26. What command reverts a Deployment to its previous revision?
27. Why does Kubernetes keep old, scaled-down ReplicaSets around after a rollout?
28. What is a readiness probe's role during a rolling update?
29. What happens to traffic routing during a rolling update, from the Service's perspective?
30. Why shouldn't you edit a Pod directly to change its image?

**Scenario-Based:**
31. You update a Deployment's image and the rollout hangs at "2/5 new replicas updated" indefinitely. What's your first diagnostic command, and what might be wrong?
32. Your team set `revisionHistoryLimit: 0`. After a bad deploy, `kubectl rollout undo` does nothing useful. Why?
33. You need a faster rollout that tolerates briefly running extra Pods but never wants capacity to dip below desired count. How would you set `maxSurge`/`maxUnavailable`?
34. A teammate manually edited a running Pod's environment variable to hotfix a bug. Next day, the fix "disappeared." Explain why.
35. You have `replicas: 2` and default rollout settings. Explain why the rollout might briefly run only 1 Pod or briefly run 3 Pods.
36. Payment-service v2 has a subtle bug only visible under load 10 minutes after deploy. What's the fastest safe recovery action?
37. Your Deployment YAML has no `resources` block, and someone scales replicas from 3 to 100. What could go wrong on the cluster?
38. How would you verify, before a rollout even starts, what the exact rollout plan (surge/unavailable Pod counts) will look like for 10 replicas with default settings?
39. You want a rollout history that shows meaningful reasons for each revision instead of `<none>`. What should you do differently when applying changes?
40. A new engineer asks, "why not just delete all Pods and recreate them instantly with the new version — wouldn't that be simpler?" How do you explain the tradeoff?

**Fill in the Blank:**
41. A ______ is a higher-level Kubernetes object that manages ReplicaSets and enables declarative updates.
42. The parameter that controls how many extra Pods can exist above the desired count during a rollout is called ______.
43. The parameter that controls how many Pods can be unavailable during a rollout is called ______.
44. The command to revert a Deployment to a prior version is `kubectl rollout ______`.
45. The command to check ongoing rollout progress is `kubectl rollout ______`.

**Conceptual Deep-Dive:**
46. Explain, step by step, what happens internally in Kubernetes from the moment you run `kubectl set image` to the rollout completing.
47. Why is a Deployment considered "declarative" rather than "imperative"?
48. What's the relationship between a Deployment's `selector.matchLabels` and the Pod template's `labels`, and what happens if they don't match?
49. Why does Kubernetes prefer creating a brand-new ReplicaSet for each distinct Pod template version, rather than mutating the existing ReplicaSet in place?
50. How does the rollback mechanism avoid needing to rebuild container images or re-pull anything from a registry (in the common case)?

---

## 8. Hour 4 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **ReplicaSet** | Ensures exactly N replicas of a Pod template are running; replaces dead Pods automatically |
| **Deployment** | Higher-level object managing ReplicaSets; adds declarative updates, rolling updates, rollback |
| **In practice** | You create/manage Deployments, not raw ReplicaSets, almost always |
| **Rolling update** | Gradually replaces old Pods with new ones; controlled by `maxSurge` and `maxUnavailable` |
| **maxSurge** | Extra Pods allowed above desired count during rollout (default 25%) |
| **maxUnavailable** | Pods allowed to be unavailable during rollout (default 25%) |
| **Rollback** | `kubectl rollout undo`; relies on retained old ReplicaSets (`revisionHistoryLimit`) |
| **Zero downtime** | Achieved because Service always routes to currently-Ready Pods, old or new, throughout the rollout |
| **Lab outcome** | You created, scaled, updated, watched, inspected history, and rolled back an nginx Deployment |

**Mnemonic:** *"RDSR"* — **R**eplicaSet keeps count → **D**eployment adds strategy → **S**urge/unavailable pace the rollout → **R**ollback undoes it via revision history.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/) (the canonical reference for this hour's topic)
- [Kubernetes Official Docs — ReplicaSet](https://kubernetes.io/docs/concepts/workloads/controllers/replicaset/)
- [KodeKloud Kubernetes for Beginners](https://kodekloud.com/courses/kubernetes-for-the-absolute-beginners-hands-on)
- YouTube: "TechWorld with Nana — Kubernetes Deployments Explained" (free, excellent visuals)
- [Play with Kubernetes](https://labs.play-with-k8s.com/) — free browser-based cluster, no install needed

**Mini-Project for Hour 4 (30 min):**
1. Deploy the `nginx-deployment.yaml` from the lab with 3 replicas.
2. Intentionally break it: `kubectl set image deployment/nginx-deployment nginx=nginx:this-tag-does-not-exist`.
3. Observe the stuck rollout with `kubectl rollout status` and `kubectl describe pods` (look for `ImagePullBackOff`), and confirm old Pods are still serving traffic the whole time.
4. Practice recovery: `kubectl rollout undo deployment/nginx-deployment`, then confirm with `kubectl rollout status` and `kubectl rollout history` that you're back on the last good revision.
5. Bonus: repeat the break/fix cycle but this time roll back to a specific revision number using `--to-revision`, and try setting `maxSurge: 0` / `maxUnavailable: 1` to see a slower, more conservative rollout pattern.
