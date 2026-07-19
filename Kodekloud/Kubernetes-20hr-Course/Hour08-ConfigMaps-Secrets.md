# Hour 8: ConfigMaps, Secrets

## 1. Explanation (Simple → Technical)

**Simple version:** Imagine you buy a phone (your app's container image). The phone itself — its hardware, its OS — is the same whether it's sold in India or the US. What changes is the **SIM card** you put in it: it configures which network, which number, which region the phone connects to. A **ConfigMap** is like that SIM card for non-sensitive settings (server region, feature flags, connection URLs). A **Secret** is like the SIM's **PIN code** — same idea, but something you'd never want lying around in plain sight.

Another way to think about it: a ConfigMap/Secret is a **sealed envelope of settings** handed to your app the moment it starts, instead of **baking those settings into the app's DNA** (the container image). If you bake config into the image, you need a new image for dev, a new image for staging, a new image for prod — wasteful and risky. If you hand the *same* image a different envelope per environment, you get one image, many environments.

**Technical version:**
- A **ConfigMap** is a Kubernetes object that stores non-confidential key-value data (strings) — things like `DB_HOST`, `LOG_LEVEL`, `FEATURE_FLAG_X`. It decouples configuration from the container image so the same image can run identically across dev/staging/prod, differing only in the config supplied.
- A **Secret** uses the exact same mechanism (same API shape, same consumption patterns) but is intended for sensitive data — passwords, API tokens, TLS certificates, SSH keys. Secret values are stored **base64-encoded**, which is **encoding, not encryption** — anyone with API access (or etcd access) can trivially decode them.
- **Why this matters for real security:**
  - By default, Secrets are only base64-encoded in etcd — not encrypted. Kubernetes supports **encryption at rest** for etcd (via `EncryptionConfiguration`) to actually encrypt Secret data on disk.
  - For real production-grade secret management, teams use **HashiCorp Vault**, **AWS Secrets Manager**, **GCP Secret Manager**, or Kubernetes-native tools like **Sealed Secrets** or the **External Secrets Operator**, which pull secrets from an external vault at runtime instead of storing raw secrets in Kubernetes/Git at all.
- **Consumption patterns** — both ConfigMaps and Secrets can be injected into a Pod as:
  1. **Environment variables** (`env` / `envFrom`) — simple, but the whole container process (and often `kubectl describe`/logs) can see them; also, env vars are read once at container start.
  2. **Mounted volumes** (files inside the container's filesystem) — better for larger blobs (certs, config files) and can be updated live via kubelet's periodic sync (the file content on disk updates when the ConfigMap/Secret changes, though your app must re-read the file — most apps don't watch for changes automatically).
- **Decoupling config from code** is a core tenet of the [12-factor app](https://12factor.net/config) methodology: the same build artifact should be deployable anywhere; only the config changes.

## 2. Diagram

```
                     ┌─────────────────────────────┐
                     │        ConfigMap            │
                     │  DB_HOST=db.prod.svc         │
                     │  DB_PORT=5432                │
                     └──────────────┬──────────────┘
                                    │ injected as
                                    │ env vars
                                    ▼
┌───────────────────────────────────────────────────────────┐
│                          Pod                               │
│  ┌───────────────────────────────────────────────────┐    │
│  │                Container                            │   │
│  │                                                      │   │
│  │  env:                                               │   │
│  │    DB_HOST=db.prod.svc     ◄── from ConfigMap        │   │
│  │    DB_PORT=5432            ◄── from ConfigMap        │   │
│  │                                                      │   │
│  │  filesystem:                                        │   │
│  │    /etc/secret/db-password  ◄── mounted volume       │   │
│  └───────────────────────────────────────────────────┘    │
└───────────────────────────────────────┬─────────────────────┘
                                        │ mounted as
                                        │ volume (file)
                     ┌──────────────────┴──────────────┐
                     │           Secret                 │
                     │  db-password: cGFzc3dvcmQxMjM=   │ (base64, NOT encrypted)
                     └───────────────────────────────────┘

Same container image, different ConfigMap/Secret per environment:

  dev namespace:   ConfigMap{DB_HOST=db.dev}     Secret{password=dev123}
  prod namespace:  ConfigMap{DB_HOST=db.prod}    Secret{password=***vault-managed***}
```

## 3. Real-World Example

Consider an order-service app that needs to connect to a database. The **connection details** (`DB_HOST=db.dev.internal`, `DB_PORT=5432`) are not sensitive — they're stored in a ConfigMap. The **database password**, however, is sensitive — it's stored in a Secret.

- In **dev**, the ConfigMap points to `db.dev.internal` and the Secret holds a throwaway dev password.
- In **prod**, the *same container image* is deployed, but the ConfigMap points to `db.prod.internal` and the Secret holds the real production password — ideally synced from **AWS Secrets Manager** via the **External Secrets Operator** rather than typed directly into a Kubernetes manifest.

This is exactly the pattern used at companies like Meesho: the same Docker image is promoted from dev → staging → prod, and only the ConfigMap/Secret values differ per environment (often per Helm values file or Kustomize overlay), so nobody has to rebuild the image just to change a hostname.

## 4. Hands-On Lab

**Goal:** Create a ConfigMap and a Secret, inject them into a Pod as env vars and as a mounted volume, and verify both.

```bash
# 1. Create a ConfigMap from literal values
kubectl create configmap db-config \
  --from-literal=DB_HOST=db.dev.internal \
  --from-literal=DB_PORT=5432

# 2. Create a ConfigMap from a file
echo "log.level=debug" > app.properties
kubectl create configmap app-file-config --from-file=app.properties

# 3. Create a Secret (generic, from literal)
kubectl create secret generic db-secret \
  --from-literal=DB_PASSWORD=SuperSecret123
```

**Expected output (each create command):**
```
configmap/db-config created
configmap/app-file-config created
secret/db-secret created
```

**4. Reference both in a Pod spec** — save as `config-secret-pod.yaml`:
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: config-demo
spec:
  containers:
  - name: app
    image: busybox
    command: ["sleep", "3600"]
    env:
    - name: DB_HOST
      valueFrom:
        configMapKeyRef:
          name: db-config
          key: DB_HOST
    - name: DB_PORT
      valueFrom:
        configMapKeyRef:
          name: db-config
          key: DB_PORT
    volumeMounts:
    - name: secret-vol
      mountPath: "/etc/secret"
      readOnly: true
    - name: config-vol
      mountPath: "/etc/config"
  volumes:
  - name: secret-vol
    secret:
      secretName: db-secret
  - name: config-vol
    configMap:
      name: app-file-config
```

```bash
kubectl apply -f config-secret-pod.yaml
kubectl get pods
```

**Expected output:**
```
NAME           READY   STATUS    RESTARTS   AGE
config-demo    1/1     Running   0          10s
```

**5. Verify env vars inside the container:**
```bash
kubectl exec -it config-demo -- env | grep DB_
```
**Expected output:**
```
DB_HOST=db.dev.internal
DB_PORT=5432
```

**6. Verify the mounted Secret and ConfigMap files:**
```bash
kubectl exec -it config-demo -- cat /etc/secret/DB_PASSWORD
kubectl exec -it config-demo -- cat /etc/config/app.properties
```
**Expected output:**
```
SuperSecret123
log.level=debug
```

**7. Confirm Secrets are only base64-encoded, not encrypted:**
```bash
kubectl get secret db-secret -o yaml
```
**Expected output (excerpt):**
```yaml
apiVersion: v1
data:
  DB_PASSWORD: U3VwZXJTZWNyZXQxMjM=
kind: Secret
```
Decode it yourself to prove the point:
```bash
echo "U3VwZXJTZWNyZXQxMjM=" | base64 --decode
# SuperSecret123
```
This is the crux of the security lesson — **anyone with `get secret` RBAC permission (or etcd access) can trivially read this**. It is not "encrypted," it's just encoded for safe transport in JSON/YAML text.

**Troubleshooting:**
- `configmap "db-config" already exists` → `kubectl delete configmap db-config` first, or use `--dry-run=client -o yaml | kubectl apply -f -` for idempotent creation.
- Pod stuck in `CreateContainerConfigError` → usually means a referenced ConfigMap/Secret key doesn't exist; check `kubectl describe pod config-demo`.
- Changed a ConfigMap but the app still shows old env values → **expected** — see Common Mistakes #4 below.

## 5. Common Mistakes

1. **Committing Secret YAML with real credentials to Git.** Because a Secret manifest just base64-encodes the value, it *looks* obfuscated but is fully reversible — pushing it to GitHub is effectively pushing plaintext credentials. Use `.gitignore`, Sealed Secrets, or External Secrets Operator instead.
2. **Assuming base64 = encryption.** Base64 is an *encoding* scheme for safely representing binary/text data, not a cryptographic transformation. It has no key, no secret — decoding is a one-line command. Real protection requires etcd encryption at rest and/or an external secret manager.
3. **Not restricting RBAC access to Secrets.** By default, if a ServiceAccount or user has broad `get`/`list` permissions on `secrets`, they can read every Secret in that namespace. Scope Roles narrowly (per-Secret via `resourceNames`, or per-namespace) and audit who can read Secrets.
4. **Forgetting that Pods must be restarted (or the app must re-read files) to pick up ConfigMap/Secret changes.** Env vars injected via `valueFrom` are read once at container start — updating the ConfigMap does **not** update the running container's env vars. Volume-mounted ConfigMaps/Secrets *do* eventually sync to disk (kubelet updates the file within ~1 minute), but your application code must actively re-read the file — most apps don't do this automatically unless built with a config-reload mechanism (e.g., Reloader, or a sidecar that sends SIGHUP).
5. **Storing large binary blobs or entire config files as many small ConfigMap keys** instead of a single mounted file — makes the Pod spec unwieldy. Prefer `--from-file` for file-shaped config.
6. **Not setting a size limit awareness** — ConfigMaps/Secrets are limited to 1MiB total; trying to store large files (e.g., ML models) in them will fail or degrade etcd performance.

## 6. Interview Questions (with brief answers)

1. **How are Secrets different from ConfigMaps?** — Structurally almost identical (same API shape, same consumption via env/volume), but Secrets are intended for sensitive data, are base64-encoded (not plaintext in the API response), can be encrypted at rest in etcd, and Kubernetes takes extra precautions with them (e.g., not printing values in `kubectl describe`, tmpfs-backed volume mounts).
2. **Is base64 encoding secure? Can it be considered encryption?** — No. Base64 has no key and is fully reversible by anyone (`base64 --decode`). It only protects against binary data breaking text-based transport (YAML/JSON), not against unauthorized access. True security requires etcd encryption at rest, RBAC restrictions, and ideally an external secrets manager like Vault.
3. **How would you avoid committing real secrets to Git while still using GitOps?** — Use Sealed Secrets (encrypts the Secret so only the cluster's controller can decrypt it, safe to commit) or the External Secrets Operator (Kubernetes Secret is just a pointer synced live from Vault/AWS Secrets Manager/GCP Secret Manager — the real value never lives in Git).
4. **If you update a ConfigMap that's mounted as a volume, will a running Pod see the change without a restart?** — The file on disk will eventually update (kubelet syncs periodically, typically within ~1 minute), but only if the app re-reads the file. Env-var-injected values from `valueFrom.configMapKeyRef` will **not** update at all without a Pod restart.
5. **Why decouple config from the container image at all — why not just bake environment-specific values into separate images per environment?** — It multiplies build/test/security-scanning effort per environment, breaks the "build once, promote everywhere" principle, and risks environment-specific bugs being introduced during rebuilds. Decoupling means you build and verify one image and trust it identically across dev/staging/prod, changing only the externalized config.

## 7. Quiz (50 Questions)

**True/False:**
1. ConfigMaps are intended for non-sensitive configuration data. (T)
2. Secrets are encrypted by default in Kubernetes. (F)
3. Base64 encoding and encryption are the same thing. (F)
4. The same container image can be reused across dev, staging, and prod by changing only the ConfigMap/Secret. (T)
5. Kubernetes supports encrypting Secret data at rest in etcd. (T)
6. Updating a ConfigMap automatically updates env vars in already-running Pods. (F)
7. Volume-mounted ConfigMaps can eventually reflect updates without a Pod restart. (T)
8. Secrets have a maximum size limit (around 1MiB). (T)
9. `kubectl create secret generic` is a valid command. (T)
10. Vault and AWS Secrets Manager are examples of external secret management tools. (T)

**Multiple Choice:**
11. What encoding is used for Secret values by default? a) AES-256 b) base64 c) SHA-256 hash d) plaintext JSON → (b)
12. Which command creates a ConfigMap from a file? a) `kubectl create configmap X --from-file=Y` b) `kubectl apply configmap` c) `kubectl create file X` d) `kubectl set configmap` → (a)
13. Which of these is NOT a way to consume a ConfigMap in a Pod? a) Environment variables b) Mounted volume c) Direct kernel syscall d) `envFrom` → (c)
14. Which tool lets you safely commit encrypted secrets to Git? a) kubectl b) Sealed Secrets c) Helm d) Minikube → (b)
15. What happens to env vars set via `valueFrom.configMapKeyRef` when the ConfigMap changes? a) They update instantly b) They never update without a Pod restart c) They update after 1 minute d) They are deleted → (b)

**Short Answer:**
16. Why is base64 not considered a security mechanism?
17. Name two ways to inject a ConfigMap's data into a container.
18. What is the purpose of `kubectl create secret generic`?
19. What command decodes a base64 string on the command line?
20. Why should Secret manifests generally not be committed to Git in plaintext?
21. What is etcd's role in Secret storage?
22. Name one external secret management tool used in production.
23. What is the maximum practical size for a ConfigMap/Secret?
24. How does mounting differ from injecting as env vars in terms of update behavior?
25. What principle from the 12-factor app methodology relates to ConfigMaps/Secrets?

**Scenario-Based:**
26. Your teammate hardcodes the database password directly into the app's source code instead of using a Secret. What's your feedback?
27. A Secret was accidentally committed to a public GitHub repo. What are your immediate next steps?
28. Your app doesn't pick up a new DB_HOST value after you updated the ConfigMap. What's the likely cause, and how do you fix it?
29. Security wants to know why "Secrets" aren't actually secret. How do you explain the base64 reality and recommend a real fix?
30. You need to run the same image in 3 environments with 3 different DB endpoints. How do you structure your ConfigMaps to support this cleanly?

**Fill in the Blank:**
31. ______ is the encoding scheme Kubernetes uses to store Secret data (not encryption).
32. `kubectl create configmap NAME --from-______=key=value` creates a ConfigMap from literal values.
33. To mount a Secret as files inside a container, you define a ______ in the Pod spec's `volumes` section.
34. ______ at rest is a Kubernetes feature that encrypts Secret data in etcd.
35. ______ and ______ are two production-grade tools for managing secrets outside of raw Kubernetes manifests.

**Conceptual Deep-Dive:**
36. Why does decoupling configuration from the container image support the "build once, deploy everywhere" principle?
37. Why might env-var injection be less safe than volume mounting for very sensitive values (hint: think about `kubectl describe`, process listings, and child process inheritance)?
38. What risk does giving broad `get`/`list` RBAC permissions on Secrets introduce, and how would you mitigate it?
39. Why does the kubelet's periodic sync for volume-mounted ConfigMaps not guarantee your application picks up new values immediately?
40. Explain the difference between "the Kubernetes Secret object is protected" and "the value inside the Secret object is protected."

**Command Practice:**
41. Write the command to create a Secret named `api-token` with key `TOKEN` and value `xyz123`.
42. Write the command to view a Secret's raw YAML (still base64-encoded).
43. Write the command to decode a specific field from a Secret using `kubectl` and `base64` together (conceptually — describe the pipe).
44. Write the command to exec into a Pod named `config-demo` and print its environment variables.
45. Write the command to create a ConfigMap named `app-file-config` from a file called `app.properties`.

**Reflection:**
46. In your own words, explain the "sealed envelope vs baked-in DNA" analogy for ConfigMaps/Secrets.
47. What part of today's lesson was most surprising — the base64 behavior, or the "no auto-restart" behavior?
48. Can you think of a real config value in an app you've built that should have been externalized but wasn't?
49. Why do you think Kubernetes didn't just encrypt Secrets by default from day one?
50. What questions do you still have about Secrets management before we move to Hour 9?

---

## 8. Hour 8 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **ConfigMap** | Stores non-sensitive key-value config, decoupled from the container image |
| **Secret** | Same mechanism as ConfigMap, intended for sensitive data (passwords, tokens, certs) |
| **Base64 ≠ encryption** | Secrets are base64-encoded by default — trivially reversible, not cryptographically protected |
| **Real security** | Enable etcd encryption at rest; use Vault / AWS Secrets Manager / Sealed Secrets / External Secrets Operator for production |
| **Consumption** | Both can be injected as env vars (`env`/`envFrom`) or mounted as volumes (files) |
| **Update behavior** | Env vars need a Pod restart to update; mounted volumes sync eventually, but the app must re-read the file |
| **Why decouple** | Same image across dev/staging/prod; only config differs — no rebuilds per environment |
| **Mental model** | ConfigMap/Secret = sealed envelope of settings handed to the app at start, not baked into its image DNA |

**Mnemonic:** *"CODE"* — **C**onfig decoupled from image, **O**ne image many environments, **D**ecode ≠ decrypt (base64 isn't security), **E**xternal vaults for real secrets in production.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Kubernetes Official Docs — Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Kubernetes Official Docs — Encrypting Secret Data at Rest](https://kubernetes.io/docs/tasks/administer-cluster/encrypt-data/)
- [Bitnami Sealed Secrets (GitHub)](https://github.com/bitnami-labs/sealed-secrets) — encrypt Secrets so they're safe to commit to Git
- [External Secrets Operator](https://external-secrets.io/) — syncs Secrets live from Vault, AWS/GCP/Azure secret managers
- YouTube: "TechWorld with Nana — Kubernetes ConfigMap & Secrets Tutorial" (free, excellent visuals)

**Mini-Project for Hour 8 (30–45 min):**
Simulate two environments (`dev` and `prod`) for a sample app that connects to a database:
1. Create two namespaces: `kubectl create namespace dev` and `kubectl create namespace prod`.
2. In each namespace, create a ConfigMap `db-config` with different `DB_HOST`/`DB_PORT` values (e.g., `db.dev.internal` vs `db.prod.internal`).
3. In each namespace, create a Secret `db-secret` with a different `DB_PASSWORD` value.
4. Deploy the **same** Pod spec (referencing `db-config` and `db-secret` by name, not by hardcoded values) into both namespaces.
5. Exec into each Pod and confirm `env | grep DB_` shows different values per namespace — proving one image, fully parameterized, running correctly across two "environments."
6. Bonus: try updating the `dev` ConfigMap, and observe (and explain) why the running Pod's env vars don't change until you delete and recreate the Pod.
