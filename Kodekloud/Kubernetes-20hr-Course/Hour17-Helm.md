# Hour 17: Helm Introduction, Why Helm Matters

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine you're cooking the same dish for a small family dinner (4 people) and for a huge party (40 people). You don't rewrite the recipe from scratch each time — you use the **same recipe** and just scale the **ingredient quantities**. **Helm** is that recipe book for Kubernetes. A **chart** is the recipe (a packaged, reusable template of Kubernetes YAML). **values.yaml** is the ingredient quantities you tweak per occasion (dev vs prod). Installing the chart with a set of values gives you a **release** — an actual cooked dish sitting on the table (a running deployment in your cluster).

**Technical version:**

Real applications are never just one YAML file. A single microservice typically needs:
- A `Deployment` (the pods)
- A `Service` (internal networking)
- A `ConfigMap` / `Secret` (configuration)
- An `Ingress` (external routing)
- An `HPA` (autoscaling)
- Maybe a `ServiceAccount`, `NetworkPolicy`, `PodDisruptionBudget`...

That's 5-10+ YAML files, and you need **near-identical copies** of them for dev, staging, and prod — differing only in things like image tag, replica count, domain name, and resource limits. Hand-maintaining these via copy-paste is exactly how outages happen: someone forgets to bump the replica count in prod, or an Ingress host gets copy-pasted wrong from staging.

**Helm** solves this by separating **structure** from **configuration**:
- A **Chart** is a packaged, templated bundle of Kubernetes manifests (using Go templating syntax) plus metadata (`Chart.yaml`) describing name/version.
- **values.yaml** holds the parameters that get injected into those templates — image tag, replica count, domain, resource limits, feature flags, etc. Each environment gets its own values file (`values-dev.yaml`, `values-prod.yaml`) while the chart's *templates* stay identical.
- A **Release** is a specific, named, deployed instance of a chart with a particular set of values — e.g., installing `mychart` with `values-prod.yaml` as the release `orders-prod`. Every `helm upgrade` creates a new **revision** of that release, and Helm keeps history so you can `helm rollback` to any previous revision — this is conceptually identical to the Deployment rollout history you learned in Hour 4, just one abstraction layer higher (a release revision can bundle changes across *multiple* Kubernetes objects at once, not just one Deployment).
- A **Helm repository** is a place to publish and discover charts — like npm for JavaScript packages or a Docker registry for images. **Artifact Hub** is the most popular public index of Helm repositories (Bitnami, official app vendors, etc.), letting you `helm install` production-grade Postgres, Redis, NGINX Ingress Controller, etc. without writing the YAML yourself.

Helm is often called "the package manager for Kubernetes" for exactly this reason — `apt install nginx` on Linux versus `helm install nginx bitnami/nginx` on Kubernetes.

## 2. Diagram

```
        Chart (templates/*.yaml)          values.yaml (or values-prod.yaml)
        ┌───────────────────────┐         ┌───────────────────────────┐
        │ deployment.yaml        │         │ replicaCount: 3           │
        │  image: {{ .Values.   │  <----  │ image:                    │
        │    image.repository }}│  merge  │   repository: myapp       │
        │ service.yaml           │         │   tag: "1.4.2"            │
        │ ingress.yaml            │         │ ingress:                 │
        │ hpa.yaml                 │         │   host: prod.example.com │
        └───────────────────────┘         └───────────────────────────┘
                      \                         /
                       \                       /
                        v                     v
                  ┌─────────────────────────────────┐
                  │     Helm Template Engine          │
                  │  (Go templating + functions)      │
                  └─────────────────────────────────┘
                                  │
                                  v
                  ┌─────────────────────────────────┐
                  │   Rendered Kubernetes Manifests   │
                  │   (real Deployment/Service/...)   │
                  └─────────────────────────────────┘
                                  │
                            helm install
                                  v
                  ┌─────────────────────────────────┐
                  │   Applied to Cluster as a         │
                  │        "Release" (rev 1)          │
                  └─────────────────────────────────┘
                                  │
                    helm upgrade (new values/chart)
                                  v
                  ┌─────────────────────────────────┐
                  │           Release rev 2           │
                  └─────────────────────────────────┘
                                  │
                    something breaks → helm rollback
                                  v
                  ┌─────────────────────────────────┐
                  │   Release rev 3 == content of     │
                  │        rev 1 (reverted)           │
                  └─────────────────────────────────┘

  helm template  --> renders manifests WITHOUT installing (for review/diff)
  helm list      --> shows all releases and their current revision
```

## 3. Real-World Example

A payments company maintains **one Helm chart** — `payment-service` — containing the Deployment, Service, Ingress, ConfigMap, and HPA templates. That single chart is reused across three environments purely by swapping the values file:

- `helm install payment-service ./chart -f values-dev.yaml` → 1 replica, debug logging on, `dev.payments.internal`
- `helm install payment-service ./chart -f values-staging.yaml` → 2 replicas, `staging.payments.internal`
- `helm install payment-service ./chart -f values-prod.yaml` → 20 replicas, HPA enabled, `payments.example.com`, strict resource limits

When a Friday deploy to prod (`helm upgrade payment-service ./chart -f values-prod.yaml --version 2.3.0`) causes error rates to spike, the on-call engineer doesn't scramble to remember what the last-good YAML looked like — they run:

```bash
helm rollback payment-service 4
```

...and within seconds the release is back to revision 4's exact chart version and values, restoring service. This is the same "instant undo" superpower `kubectl rollout undo` gives a single Deployment (Hour 4), but scoped to an entire bundle of interrelated Kubernetes objects at once.

## 4. Hands-On Lab

**Goal:** Install Helm, scaffold a chart, customize it, and go through the full release lifecycle including rollback.

```bash
# 1. Install Helm CLI
brew install helm                  # macOS
helm version
```

**Expected output:**
```
version.BuildInfo{Version:"v3.14.0", GitCommit:"...", GitTreeState:"clean", GoVersion:"go1.21.5"}
```

```bash
# 2. Scaffold a new chart
helm create mychart
```

**Expected output:**
```
Creating mychart
```

This generates:
```
mychart/
├── Chart.yaml          # chart metadata: name, version, appVersion
├── values.yaml         # default configuration values
├── templates/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── hpa.yaml
│   ├── serviceaccount.yaml
│   ├── _helpers.tpl    # reusable template snippets
│   └── tests/
└── charts/             # subcharts/dependencies go here
```

```bash
# 3. Inspect the defaults
cat mychart/values.yaml
```

Note key defaults: `replicaCount: 1`, `image.repository: nginx`, `image.tag: ""` (defaults to appVersion), `service.type: ClusterIP`.

```bash
# 4. Customize replicaCount and image tag
```

Edit `mychart/values.yaml`:
```yaml
replicaCount: 2
image:
  repository: nginx
  tag: "1.25"
```

```bash
# 5. Preview the rendered manifests BEFORE installing (critical habit!)
helm template myapp ./mychart
```

This prints the fully rendered Deployment/Service/etc. YAML to your terminal — nothing is applied to the cluster. Read it to confirm replicas, image, labels look right.

```bash
# 6. Install (creates release "myapp", revision 1)
helm install myapp ./mychart
```

**Expected output:**
```
NAME: myapp
LAST DEPLOYED: Wed Jul  2 10:00:00 2026
NAMESPACE: default
STATUS: deployed
REVISION: 1
```

```bash
kubectl get pods
helm list
```

**Expected `helm list` output:**
```
NAME    NAMESPACE  REVISION  STATUS    CHART           APP VERSION
myapp   default    1         deployed  mychart-0.1.0   1.16.0
```

```bash
# 7. Upgrade with a changed value (bump replicas, change tag)
helm upgrade myapp ./mychart --set replicaCount=4 --set image.tag=1.26
```

**Expected output:**
```
REVISION: 2
STATUS: deployed
```

```bash
kubectl get pods           # should now show 4 pods running nginx:1.26
helm history myapp
```

**Expected `helm history` output:**
```
REVISION  UPDATED       STATUS      CHART           APP VERSION  DESCRIPTION
1         ... 10:00:00  superseded  mychart-0.1.0   1.16.0       Install complete
2         ... 10:05:00  deployed    mychart-0.1.0   1.16.0       Upgrade complete
```

```bash
# 8. Simulate "oops" and roll back to revision 1
helm rollback myapp 1
```

**Expected output:**
```
Rollback was a success! Happy Helming!
```

```bash
helm history myapp   # now shows revision 3, whose content matches revision 1
kubectl get pods     # back to 2 replicas of nginx:1.25
```

```bash
# 9. Clean up
helm uninstall myapp
```

**Expected output:**
```
release "myapp" uninstalled
```

**Troubleshooting:**
- `helm: command not found` → `brew install helm` (macOS) or download from GitHub releases.
- `Error: INSTALLATION FAILED: cannot re-use a name that is still in use` → a release with that name already exists; `helm uninstall` it first or pick a new name.
- Rendered YAML looks wrong → always run `helm template` or `helm install --dry-run --debug` before a real install/upgrade.

## 5. Common Mistakes

1. **Hardcoding values inside templates instead of parameterizing via `values.yaml`.** If `templates/deployment.yaml` has `image: myapp:1.4.2` typed literally instead of `image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"`, you've recreated the exact copy-paste problem Helm exists to solve — every environment needs its own hand-edited copy of the template.
2. **Not reviewing rendered output before installing.** Skipping `helm template` or `helm install --dry-run --debug` means the first time you see the actual generated YAML is after it's already applied to the cluster. Always render and read the diff first, especially for `helm upgrade` on production releases.
3. **Confusing chart version with app version.** `Chart.yaml` has two separate fields: `version` (the chart's own version, bump it when you change templates/values structure) and `appVersion` (the version of the application/image the chart deploys). Bumping one without the other, or assuming they're always in sync, causes confusion about "which chart do I need for app v2.0."
4. **Not pinning chart versions from a shared/remote repository.** Running `helm install myapp somerepo/myapp` without `--version x.y.z` pulls the *latest* chart version at install time — if the maintainer pushes a breaking change, your next `helm upgrade` (or a teammate's fresh install) silently pulls in different templates/defaults than you tested. Always pin: `--version 2.3.1`.
5. **Treating `helm upgrade --set` as a substitute for maintaining values files.** Piling on many `--set` flags on the command line is fine for quick experiments, but production changes should live in a versioned `values-prod.yaml` file — otherwise nobody can see what configuration is actually running, and rollbacks/audits become guesswork.
6. **Forgetting that `helm rollback` reverts the release (chart + values), not just one field.** Engineers sometimes expect rollback to undo a single bad value while keeping other recent changes — it reverts the *entire* release to that revision's full state, same as `kubectl rollout undo` reverts a full Deployment spec.

## 6. Interview Questions (with brief answers)

1. **What is a Helm chart?** — A packaged, templated bundle of Kubernetes manifests plus metadata (`Chart.yaml`) and default configuration (`values.yaml`), designed to be installed repeatedly with different parameter values across environments.
2. **How does `helm rollback` work?** — Helm stores the rendered manifests and values for every release revision (as Secrets/ConfigMaps in-cluster, by default as Secrets since Helm 3). `helm rollback <release> <revision>` re-applies the stored manifests from that revision to the cluster and records the rollback itself as a new revision in history — nothing is deleted, so you can roll forward again if needed.
3. **What's the difference between `helm install` and `helm upgrade`?** — `install` creates a brand-new release (fails if the name already exists); `upgrade` updates an existing release in place with new values/chart version, creating a new revision. `helm upgrade --install` does either depending on whether the release exists.
4. **Why would you use `helm template` instead of `helm install`?** — `helm template` renders the final Kubernetes YAML locally without contacting the cluster or creating a release — useful for code review, CI diffing, or debugging templating logic before anything is actually deployed.
5. **What is the difference between a chart's `version` and `appVersion`?** — `version` is the chart's own semantic version (bump when chart templates/structure/defaults change); `appVersion` reflects the version of the application/container image the chart deploys. They evolve independently.

## 7. Quiz (50 Questions)

**True/False:**
1. A Helm chart is a packaged, templated set of Kubernetes manifests. (T)
2. `values.yaml` contains the actual rendered Kubernetes objects. (F)
3. A Helm release is a specific deployed instance of a chart with a given set of values. (T)
4. Helm can only install charts from a local directory, never from a remote repository. (F)
5. `helm upgrade` creates a new revision of a release. (T)
6. `helm rollback` deletes all history after the target revision. (F)
7. Artifact Hub is a public index for discovering Helm charts. (T)
8. `helm template` applies the rendered manifests to the cluster. (F)
9. Chart version and appVersion always have to be identical. (F)
10. `helm uninstall` removes a release and its associated Kubernetes resources. (T)
11. `helm create` scaffolds a new chart with example templates and values. (T)
12. You must hand-write every Kubernetes manifest from scratch even when using Helm. (F)
13. Pinning a chart version prevents unexpected changes when installing from a shared repo. (T)
14. Helm is often described as "the package manager for Kubernetes." (T)
15. `helm list` shows all currently installed releases and their revision numbers. (T)

**Multiple Choice:**
16. What file holds the default configurable parameters of a chart? a) Chart.yaml b) values.yaml c) helmfile.yaml d) release.yaml → (b)
17. Which command renders manifests without installing them? a) helm install --dry-run only b) helm template c) helm get d) helm lint only → (b)
18. What do you call a specific deployed instance of a chart with a set of values? a) Chart b) Release c) Repository d) Revision only → (b)
19. Which command instantly reverts a release to a previous state? a) helm undo b) helm revert c) helm rollback d) kubectl rollback → (c)
20. Where can you publicly discover shared Helm charts? a) Docker Hub only b) Artifact Hub c) npm registry d) PyPI → (b)
21. What metadata file describes a chart's name and version? a) values.yaml b) Chart.yaml c) manifest.yaml d) charts.lock → (b)
22. What happens if you `helm install` with a release name that already exists? a) It upgrades automatically b) It fails with an error c) It silently overwrites d) It creates revision 0 → (b)
23. Which command scaffolds a brand-new chart directory structure? a) helm init b) helm new c) helm create d) helm scaffold → (c)
24. What is the risk of not pinning a chart version from a remote repo? a) Slower installs b) Unexpected upgrades from newer chart defaults c) No risk d) Charts become read-only → (b)
25. Which field distinguishes the app's own version from the chart's packaging version? a) version vs appVersion b) tag vs digest c) release vs revision d) chart vs template → (a)

**Short Answer:**
26. In one sentence, what real-world problem does Helm solve for teams managing many YAML files?
27. What is the relationship between a Chart, values.yaml, and a Release?
28. Why is `helm template` useful before running `helm install` or `helm upgrade`?
29. How does a Helm release revision relate to Deployment rollout history from Hour 4?
30. What command would you run to see the revision history of a release named `orders`?

**Scenario-Based:**
31. Your team has separate copy-pasted YAML files for dev, staging, and prod that keep drifting apart. How would Helm fix this?
32. A junior engineer hardcodes the image tag directly in `templates/deployment.yaml`. What's wrong with this, and what should they do instead?
33. A production `helm upgrade` just caused a spike in 500 errors. What's the fastest safe recovery action?
34. Your teammate installs a chart from a public repo without specifying `--version`. Six months later, a fresh install behaves differently. Why?
35. You want a code reviewer to see the exact Kubernetes YAML your PR will produce, without deploying anything. What command do you use?

**Fill in the Blank:**
36. A ______ is a packaged, templated set of Kubernetes manifests.
37. ______ holds the parameters used to customize a chart per environment.
38. A specific deployed instance of a chart with a given set of values is called a ______.
39. ______ is a public index/registry for discovering shareable Helm charts.
40. The command `helm ______` renders manifests locally without installing them.

**Conceptual Deep-Dive:**
41. Why does separating "structure" (templates) from "configuration" (values.yaml) reduce copy-paste errors?
42. Explain how Helm's release revision history parallels a Kubernetes Deployment's rollout history, and how it differs in scope.
43. Why might a company maintain one chart with multiple values files (`values-dev.yaml`, `values-prod.yaml`) rather than one values file with conditionals?
44. What's the danger of installing charts from an unpinned, shared remote repository in a CI/CD pipeline?
45. Why is `helm rollback` considered safer/faster than manually reverting each Kubernetes YAML file by hand?

**Command Practice:**
46. Write the command to scaffold a new chart named `orders-chart`.
47. Write the command to install a chart at `./orders-chart` as a release named `orders` using a custom values file `values-prod.yaml`.
48. Write the command to upgrade the `orders` release setting `replicaCount=5` via `--set`.
49. Write the command to roll the `orders` release back to revision 3.
50. Write the command to completely remove the `orders` release from the cluster.

---

## 8. Hour 17 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Problem Helm solves** | Real apps need many interrelated, near-duplicate YAML manifests per environment; hand-copying them is error-prone |
| **Chart** | Packaged, templated set of Kubernetes manifests (the "recipe") |
| **values.yaml** | Parameters that customize a chart per environment (the "ingredient quantities") |
| **Release** | A specific deployed instance of a chart + values (the "cooked dish on the table") |
| **Helm repository** | Place to share/discover charts, e.g. Artifact Hub (like a package registry) |
| **Core commands** | `helm create`, `helm install`, `helm upgrade`, `helm rollback`, `helm uninstall`, `helm template`, `helm list`, `helm history` |
| **Rollback model** | Every install/upgrade creates a new revision; `helm rollback` re-applies a past revision, parallels Deployment rollout history (Hour 4) but scoped to the whole release |
| **Lab outcome** | You scaffolded, customized, installed, upgraded, rolled back, and uninstalled a chart |

**Mnemonic:** *"CVRR"* — **C**hart (recipe) + **V**alues (quantities) → **R**elease (cooked dish) → **R**ollback (undo button).

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Helm Official Docs](https://helm.sh/docs/) — the canonical reference for charts, templates, and CLI commands
- [Artifact Hub](https://artifacthub.io/) — browse thousands of public Helm charts (Bitnami, official vendors, community)
- [Helm Chart Template Guide](https://helm.sh/docs/chart_template_guide/) — deep dive into Go templating syntax used inside charts
- [Helm Best Practices](https://helm.sh/docs/chart_best_practices/) — official guidance on structuring charts and values
- YouTube: "TechWorld with Nana — Helm Crash Course" (free, excellent visuals on chart structure)

**Mini-Project for Hour 17 (30-45 min):**
- Take an app you deployed in an earlier hour (e.g., your Deployment + Service + Ingress from Hours 4-10) and package it as your own Helm chart:
  1. `helm create myapp-chart`, then replace the generated templates with versions matching your actual app's Deployment/Service/Ingress.
  2. Expose `replicaCount` and `image.tag` as configurable values in `values.yaml` (don't hardcode them in templates).
  3. Create `values-dev.yaml` and `values-prod.yaml` with different replica counts and tags.
  4. `helm install myapp ./myapp-chart -f values-dev.yaml`, verify it works, then `helm upgrade` with `values-prod.yaml`, then practice `helm rollback` back to the dev configuration.
