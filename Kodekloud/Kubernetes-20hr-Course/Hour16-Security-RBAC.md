# Hour 16: Security Basics, RBAC, Service Accounts

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine your company issues **badges**. Your badge doesn't open every door — it only opens the doors your job requires. A junior analyst's badge won't open the server room; the sysadmin's badge will. In Kubernetes, every request to the API server ("open this door") must show a badge (identity), and Kubernetes checks a rulebook to see which doors that badge is allowed to open. That rulebook is **RBAC (Role-Based Access Control)**.

- A **Role** is like a **job description**: "can open the supply closet, can view the meeting room schedule." It lists *permitted actions* on *specific things*.
- A **RoleBinding** is the act of actually **issuing someone that badge** tied to that job description: "Priya now has the Intern badge, which grants Intern permissions."
- A **ServiceAccount** is a badge issued to a **robot/process** (a Pod) rather than a human — because Pods talk to the API server too (to read ConfigMaps, list other Pods, etc.), and they need an identity just like people do.

**Technical version:**
- **Authentication** answers "who are you?" — humans authenticate via certificates, OIDC tokens (e.g., cloud IAM/SSO), or static tokens; Pods authenticate via a **ServiceAccount token** automatically mounted into the container.
- **Authorization** answers "are you allowed to do that?" — Kubernetes' primary authorization mechanism is **RBAC**, made of four objects:
  1. **Role** — namespace-scoped set of permission rules: `verbs` (get, list, watch, create, update, patch, delete) on `resources` (pods, deployments, secrets, etc.) within one namespace.
  2. **ClusterRole** — the same idea, but cluster-wide (or reusable across namespaces): can grant access to cluster-scoped resources (nodes, PersistentVolumes) or to namespaced resources across *all* namespaces.
  3. **RoleBinding** — attaches a Role (or a ClusterRole) to a subject (User, Group, or ServiceAccount) **within one namespace**.
  4. **ClusterRoleBinding** — attaches a ClusterRole to a subject **cluster-wide**.
- **ServiceAccounts vs Users:**
  - A **ServiceAccount** is a first-class Kubernetes object (`kubectl get sa`), created and managed *inside* the cluster. Every Pod runs as some ServiceAccount — if you don't specify one, it uses the `default` ServiceAccount of its namespace. Modern Kubernetes (1.24+) no longer auto-mounts long-lived tokens by default and issues short-lived, audience-bound projected tokens instead.
  - A **User** (human) is *not* a Kubernetes object at all — Kubernetes has no built-in user database. Users are authenticated externally (client certificates, cloud IAM like AWS IAM/EKS, Google/GCP IAM, Azure AD, or an OIDC provider like Okta/Dex) and Kubernetes just trusts the identity asserted by that external system, then applies RBAC to it.
- **Principle of least privilege** is the guiding theme of all Kubernetes security: grant the *minimum* verbs on the *minimum* resources in the *minimum* scope needed — nothing more "just in case."
- Other security layers that work alongside RBAC:
  - **NetworkPolicies** (Hour 15) — firewall rules for pod-to-pod traffic ("who can talk to whom over the network").
  - **Pod Security Standards / admission control** — restrict *how* a Pod runs: disallow privileged containers, enforce running as non-root, drop Linux capabilities, restrict hostPath mounts. Enforced via the built-in Pod Security Admission controller (labels on namespaces: `privileged`, `baseline`, `restricted`).
  - Together: RBAC controls **API access**, NetworkPolicy controls **network access**, Pod Security Standards control **container runtime behavior** — three independent layers of defense in depth.

## 2. Diagram

```
                          AUTHENTICATION            AUTHORIZATION (RBAC)
                         ┌───────────────┐         ┌─────────────────────┐
  Human User ──cert/OIDC─┤  "Who are     │         │   Role / ClusterRole │
  ServiceAccount ─token──┤   you?"       ├────────▶│   (job description)  │
                         └───────────────┘         │  verbs: get,list,... │
                                                    │  resources: pods,... │
                                                    └──────────┬──────────┘
                                                               │ attached via
                                                    ┌──────────▼──────────┐
                                                    │ RoleBinding /       │
                                                    │ ClusterRoleBinding  │
                                                    │  (issuing the badge)│
                                                    └──────────┬──────────┘
                                                               │ grants
                                                    ┌──────────▼──────────┐
                                                    │  Permitted API      │
                                                    │  actions on         │
                                                    │  specific resources │
                                                    └─────────────────────┘

Namespace-scoped (most common)              Cluster-scoped (use sparingly)
┌─────────────────────────────┐             ┌──────────────────────────────┐
│ namespace: staging          │             │ whole cluster                │
│  Role: pod-reader           │             │  ClusterRole: node-viewer    │
│  RoleBinding → ci-sa        │             │  ClusterRoleBinding → sa     │
│  effect: only in "staging"  │             │  effect: every namespace,    │
│                              │             │  or cluster-scoped resources │
└─────────────────────────────┘             └──────────────────────────────┘

Badge analogy:
  Role            = job description ("can open supply closet, can view schedule")
  RoleBinding      = issuing the actual badge to a specific person/robot
  ServiceAccount   = a robot's badge (used by Pods)
  User             = a human's badge (identity lives outside Kubernetes, e.g. IAM/OIDC)
```

## 3. Real-World Example

A company runs a **CI/CD pipeline** (e.g., Jenkins, GitLab CI, Argo CD) that needs to deploy new versions of an app to Kubernetes. The naive approach: give the pipeline a ServiceAccount bound to `cluster-admin`. This is dangerous — if the CI system's credentials leak (a common real-world breach vector), an attacker gets full control of the entire cluster, including the `prod` namespace with real customer data.

The secure approach:
- Create a ServiceAccount `ci-deployer` in the `staging` namespace.
- Create a `Role` in `staging` granting only `create`, `update`, `patch`, `get`, `list` on `deployments`, `pods`, `services` — nothing on `secrets`, and nothing at all in `prod`.
- Bind it with a `RoleBinding` scoped to `staging` only.
- The CI pipeline's kubeconfig uses this ServiceAccount's token. Even if compromised, an attacker can only mess with `staging` — `prod` is untouched because no RoleBinding exists there for this ServiceAccount.
- Promotion to `prod` requires either a *separate*, more tightly scoped ServiceAccount/RoleBinding with manual approval gates, or a GitOps controller (Argo CD) with its own RBAC, keeping human-approved promotion as a deliberate, auditable step.

This is exactly how companies like Meesho segregate CI/CD blast radius — one leaked staging credential should never be able to touch production.

## 4. Hands-On Lab

**Goal:** Create a ServiceAccount with least-privilege access (get/list pods only, in one namespace), verify permitted and denied actions.

```bash
# 1. Create a namespace to work in
kubectl create namespace demo-rbac

# 2. Create the ServiceAccount
kubectl create serviceaccount ci-deployer -n demo-rbac

# 3. Create a Role granting only get/list on pods in this namespace
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-reader
  namespace: demo-rbac
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]
EOF

# 4. Bind the Role to the ServiceAccount via a RoleBinding
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ci-deployer-pod-reader
  namespace: demo-rbac
subjects:
- kind: ServiceAccount
  name: ci-deployer
  namespace: demo-rbac
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
EOF

# 5. Test PERMITTED action: can it list pods in demo-rbac?
kubectl auth can-i list pods \
  --as=system:serviceaccount:demo-rbac:ci-deployer \
  -n demo-rbac
```

**Expected output:**
```
yes
```

```bash
# 6. Test DENIED action: can it delete pods?
kubectl auth can-i delete pods \
  --as=system:serviceaccount:demo-rbac:ci-deployer \
  -n demo-rbac
```

**Expected output:**
```
no
```

```bash
# 7. Test DENIED action: can it list secrets (never granted)?
kubectl auth can-i list secrets \
  --as=system:serviceaccount:demo-rbac:ci-deployer \
  -n demo-rbac
```

**Expected output:**
```
no
```

```bash
# 8. Test DENIED action: can it list pods in a DIFFERENT namespace (e.g. prod)?
kubectl create namespace prod --dry-run=client -o yaml | kubectl apply -f -
kubectl auth can-i list pods \
  --as=system:serviceaccount:demo-rbac:ci-deployer \
  -n prod
```

**Expected output:**
```
no
```

```bash
# 9. Run a real pod using this ServiceAccount and confirm from inside
kubectl run test-pod -n demo-rbac --image=bitnami/kubectl:latest \
  --overrides='{"spec":{"serviceAccountName":"ci-deployer"}}' \
  --command -- sleep 3600

kubectl exec -n demo-rbac test-pod -- kubectl get pods -n demo-rbac
# Expected: succeeds, lists pods

kubectl exec -n demo-rbac test-pod -- kubectl delete pod test-pod -n demo-rbac
# Expected: Error from server (Forbidden): pods "test-pod" is forbidden:
# User "system:serviceaccount:demo-rbac:ci-deployer" cannot delete resource "pods"
```

**Troubleshooting:**
- `Error from server (Forbidden)` on an action you *expected* to work → double check `apiGroups` (empty string `""` for core resources like pods, but `"apps"` for deployments) and that the RoleBinding's `roleRef.name` matches the Role's `metadata.name` exactly.
- `kubectl auth can-i` always says `no` even after creating the RoleBinding → verify you're passing the correct `-n <namespace>`; RoleBindings only apply within the namespace they're created in.
- ServiceAccount token not mounting in older clusters → check `automountServiceAccountToken` isn't set to `false` on the ServiceAccount or Pod spec.

## 5. Common Mistakes

1. **Binding `cluster-admin` to a ServiceAccount "just to be safe."** This is the RBAC equivalent of giving every employee a master key to every room in the building. A single leaked token then compromises the entire cluster. Always scope to the narrowest Role that does the job.
2. **Confusing Role/RoleBinding (namespaced) with ClusterRole/ClusterRoleBinding (cluster-wide).** A common bug: creating a `ClusterRole` but binding it with a `RoleBinding` (this actually works and scopes it to one namespace — often intentional and a good pattern for reusable ClusterRoles!) versus accidentally using a `ClusterRoleBinding` when you meant to scope access to one namespace only (this leaks the permission cluster-wide by mistake).
3. **Never auditing or rotating RoleBindings over time.** Permissions granted for a one-off migration script six months ago often stay forever ("permission creep"). Without periodic audits (`kubectl get rolebindings,clusterrolebindings -A -o wide`), stale, over-broad bindings accumulate silently and become the easiest attack path.
4. **Running containers as root with no `securityContext`, when the app never needs root.** This violates least privilege at the container-runtime layer — if an attacker achieves code execution inside the container, running as root makes container-breakout and host-level damage far easier. Pod Security Standards (`restricted` profile) and `runAsNonRoot: true` should be the default, not an afterthought.
5. **Forgetting that `default` ServiceAccount is used implicitly.** If you don't set `serviceAccountName` on a Pod spec, it silently uses the namespace's `default` ServiceAccount. If that SA has been over-permissioned (mistake #1), *every* Pod in the namespace inherits that risk — even ones that never needed API access at all.
6. **Testing permissions only with `kubectl` as yourself, not `--as=<serviceaccount>`.** Engineers often verify "it works" while authenticated as their own (highly privileged) admin user, never actually testing what the workload's ServiceAccount can and cannot do — masking under-permissioning or, worse, over-permissioning bugs until production.

## 6. Interview Questions (with brief answers)

1. **What is the difference between a Role and a ClusterRole?** — A `Role` is namespace-scoped: its rules only apply within the namespace it's defined in. A `ClusterRole` is cluster-wide: it can grant access to cluster-scoped resources (nodes, PersistentVolumes, namespaces themselves) and can also be bound in a specific namespace via a RoleBinding (for reusability) or cluster-wide via a ClusterRoleBinding.
2. **How would you grant a CI/CD pipeline limited access to deploy to only one namespace?** — Create a ServiceAccount in that namespace, define a Role scoped to that namespace with only the verbs/resources needed (e.g., `create`/`update`/`patch`/`get`/`list` on `deployments`, `pods`, `services` — never `secrets` or cluster-scoped resources), and attach it with a RoleBinding in that same namespace. No RoleBinding or ClusterRoleBinding should exist granting this SA any access to other namespaces like `prod`.
3. **What is a ServiceAccount and how is it different from a User?** — A ServiceAccount is a Kubernetes-native identity object used by Pods/processes to authenticate to the API server; it's created, managed, and scoped entirely within the cluster. A User represents a human and is *not* a Kubernetes object at all — authentication is delegated to external systems (client certs, cloud IAM, OIDC), and Kubernetes RBAC simply authorizes whatever identity that external system asserts.
4. **What's the principle of least privilege, and how does RBAC enforce it?** — It means granting only the minimum permissions necessary to perform a task, nothing more. RBAC enforces it by requiring explicit, additive grants (verbs on resources) via Roles/ClusterRoles — there's no implicit access; anything not explicitly granted is denied by default.
5. **How would you audit or debug what a given ServiceAccount can actually do?** — Use `kubectl auth can-i <verb> <resource> --as=system:serviceaccount:<ns>:<name> -n <namespace>` to test specific actions, or `kubectl auth can-i --list --as=...` to see all permissions. For a full picture, inspect `kubectl get rolebindings,clusterrolebindings -A -o json` and cross-reference `roleRef` and `subjects` to trace exactly which Roles/ClusterRoles apply to that identity.

## 7. Quiz (50 Questions)

**True/False:**
1. A Role is namespace-scoped, while a ClusterRole can be cluster-wide. (T)
2. Every Pod automatically runs as some ServiceAccount, even if you don't set one explicitly. (T)
3. Kubernetes maintains its own internal database of human Users. (F)
4. A RoleBinding can bind a ClusterRole within a single namespace. (T)
5. Binding `cluster-admin` to a ServiceAccount is a good default for CI/CD pipelines. (F)
6. `kubectl auth can-i` can be used to simulate permissions for a specific ServiceAccount using `--as`. (T)
7. NetworkPolicies control which API verbs a user can call. (F)
8. Pod Security Standards restrict how containers run (e.g., privileged mode, root user). (T)
9. RBAC governs authorization (what you can do), not authentication (who you are). (T)
10. The `default` ServiceAccount in a namespace is used implicitly if none is specified. (T)
11. A ClusterRoleBinding always grants access across the entire cluster. (T)
12. Least privilege means granting broad access up front to save time later. (F)
13. ServiceAccounts and Users are the same type of Kubernetes object. (F)
14. Modern Kubernetes versions no longer auto-mount long-lived, non-expiring ServiceAccount tokens by default. (T)
15. A Role's rules can include cluster-scoped resources like Nodes. (F)

**Multiple Choice:**
16. Which object defines *what actions are allowed* (verbs on resources)? a) RoleBinding b) Role c) ServiceAccount d) Namespace → (b)
17. Which object *attaches* a Role to a subject? a) ClusterRole b) RoleBinding c) Secret d) NetworkPolicy → (b)
18. Which identity type is used by a Pod to talk to the API server? a) User b) Group c) ServiceAccount d) ConfigMap → (c)
19. Which of these is cluster-scoped (not namespace-scoped)? a) Role b) RoleBinding c) ClusterRole d) Pod → (c)
20. Where are human Users typically authenticated? a) Inside etcd b) Via a Kubernetes CRD c) Externally (certs, IAM, OIDC) d) In the kubelet config → (c)
21. What does `kubectl auth can-i --as=system:serviceaccount:ns:name delete pods` test? a) If a real Pod exists b) If that identity is authorized to delete pods c) Network connectivity d) Node health → (b)
22. Which layer restricts a container from running as root? a) RBAC b) NetworkPolicy c) Pod Security Standards d) Ingress → (c)
23. What is the security risk of binding `cluster-admin` to a CI ServiceAccount? a) Slower deploys b) Excess memory use c) Full cluster compromise if leaked d) No risk → (c)
24. Which verb would you grant for read-only access to pods? a) create b) delete c) get/list/watch d) patch → (c)
25. What does "least privilege" primarily aim to reduce? a) Cost b) Blast radius of a compromised identity c) Cluster size d) Node count → (b)

**Short Answer:**
26. In one sentence, what problem does RBAC solve?
27. What's the difference in scope between a Role and a ClusterRole?
28. Why does a Pod need a ServiceAccount at all?
29. What command tests whether a ServiceAccount can perform an action, without actually performing it?
30. Why shouldn't a CI/CD pipeline's ServiceAccount have access to the `prod` namespace by default?
31. What happens if you don't explicitly set `serviceAccountName` on a Pod spec?
32. How does authentication differ from authorization in Kubernetes?
33. Give an example of a verb and resource combination that represents "read-only" access.
34. What Kubernetes feature restricts privileged/root containers at admission time?
35. Why is auditing RoleBindings periodically important?

**Scenario-Based:**
36. Your CI pipeline needs to deploy to `staging` but should never touch `prod`. Describe the RBAC objects you'd create and how you'd scope them.
37. A teammate proposes binding `cluster-admin` to a new monitoring ServiceAccount "to avoid permission issues." How do you respond, and what would you do instead?
38. You find a RoleBinding granting `delete` on `secrets` to a ServiceAccount that hasn't been used in 8 months. What should you do?
39. A Pod fails with `Forbidden: cannot list resource "deployments"`. Walk through how you'd diagnose and fix this.
40. Your security team asks you to enforce that no container in the `payments` namespace can run as root. Which mechanism do you reach for, and why (not RBAC)?

**Fill in the Blank:**
41. A ______ is namespace-scoped, while a ______ can apply cluster-wide.
42. A ______ attaches a Role to a subject within a single namespace.
43. A ______ attaches a ClusterRole to a subject across the entire cluster.
44. Every Pod authenticates to the API server using a ______.
45. The Kubernetes object representing a robot/process identity is called a ______, while a human identity is called a ______ (managed outside the cluster).

**Conceptual Deep-Dive:**
46. Why is it valid (and sometimes a good pattern) to bind a ClusterRole using a RoleBinding instead of a ClusterRoleBinding?
47. Explain why Kubernetes has no built-in concept of a "User" object, unlike ServiceAccounts.
48. How do RBAC, NetworkPolicy, and Pod Security Standards form "defense in depth" as three separate layers?
49. Why might short-lived, audience-bound ServiceAccount tokens (introduced in newer Kubernetes versions) be more secure than older long-lived tokens?
50. In the badge analogy, map each of the following to its badge-system equivalent: Role, RoleBinding, ServiceAccount, User.

---

## 8. Hour 16 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **RBAC** | Kubernetes' authorization system: controls *what actions* an identity can perform on *which resources* |
| **Role vs ClusterRole** | Role = namespace-scoped permissions; ClusterRole = cluster-wide (or reusable across namespaces) permissions |
| **RoleBinding vs ClusterRoleBinding** | RoleBinding = attaches permissions within one namespace; ClusterRoleBinding = attaches permissions cluster-wide |
| **ServiceAccount** | The identity a Pod uses to talk to the API server; every Pod has one (defaults to `default` SA if unset) |
| **User** | A human identity, *not* a Kubernetes object — authenticated externally via certs/IAM/OIDC |
| **Least privilege** | Grant only the minimum verbs/resources/scope needed — the guiding theme of all K8s security |
| **Other security layers** | NetworkPolicy = pod-to-pod network firewalling; Pod Security Standards = restrict privileged/root containers |
| **Key command** | `kubectl auth can-i <verb> <resource> --as=system:serviceaccount:<ns>:<name> -n <namespace>` |
| **Lab outcome** | You created a ServiceAccount with get/list-only pod access and proved denied vs permitted actions |

**Mnemonic:** *"BADGE"* — **B**adge (identity: User/ServiceAccount) needs a **A**ssigned **D**escription of duties (Role/ClusterRole) via a **G**ranting document (RoleBinding/ClusterRoleBinding) to **E**nter (perform the permitted API action).

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Using RBAC Authorization](https://kubernetes.io/docs/reference/access-authn-authz/rbac/) (the definitive reference for Role/ClusterRole/RoleBinding/ClusterRoleBinding)
- [Kubernetes Official Docs — Managing Service Accounts](https://kubernetes.io/docs/reference/access-authn-authz/service-accounts-admin/)
- [Kubernetes Official Docs — Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/)
- [Kubernetes Official Docs — Authenticating (Users, OIDC, certs)](https://kubernetes.io/docs/reference/access-authn-authz/authentication/)
- YouTube: "TechWorld with Nana — Kubernetes RBAC Explained" (free, excellent visuals)

**Mini-Project for Hour 16 (30–45 min):**
Design and implement least-privilege RBAC for a hypothetical CI/CD ServiceAccount:
1. Create two namespaces: `staging` and `prod`.
2. Create a ServiceAccount `pipeline-bot` in `staging` only.
3. Write a `Role` in `staging` granting exactly: `get`, `list`, `create`, `update`, `patch` on `deployments`, `pods`, `services` — explicitly excluding `secrets` and `delete`.
4. Bind it with a `RoleBinding` scoped to `staging`.
5. Prove via `kubectl auth can-i --as=system:serviceaccount:staging:pipeline-bot`:
   - It **can** create/update deployments in `staging`.
   - It **cannot** read secrets in `staging`.
   - It **cannot** do anything at all in `prod` (no binding exists there).
6. Bonus: add a `securityContext` to a test Pod using this ServiceAccount enforcing `runAsNonRoot: true`, and confirm a root-requiring image fails admission under the `restricted` Pod Security Standard.
