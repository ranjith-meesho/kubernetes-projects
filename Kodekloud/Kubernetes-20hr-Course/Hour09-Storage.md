# Hour 9: Volumes, Persistent Volumes, Persistent Volume Claims, Storage Classes

## 1. Explanation (Simple → Technical)

**Simple version:** By default, a container's filesystem is like a whiteboard in a shared meeting room — anything you write on it disappears the moment the room is reset (the container restarts or is rescheduled). If your app writes important data (a database file, an uploaded image) directly into the container's filesystem, that data is gone the next time the Pod restarts.

A **Volume** is like bringing your own notebook into the meeting room — as long as the Pod that owns it is alive, the notebook survives container restarts inside that Pod. But if the whole Pod is deleted, some volume types (like `emptyDir`) still disappear with it.

A **PersistentVolume (PV)** is like a storage locker in a big warehouse — it exists independently of any one Pod, provisioned by a cluster admin (or dynamically) and available for use.

A **PersistentVolumeClaim (PVC)** is like a rental reservation slip — a user (your Pod, via its spec) says "I need 10Gi of storage that can be mounted by one node at a time," and Kubernetes finds/binds a locker (PV) that matches.

A **StorageClass** is like an on-demand locker-building service — instead of the warehouse admin pre-building lockers and hoping the sizes match, the StorageClass builds a brand-new locker exactly when someone submits a reservation request (dynamic provisioning).

**Technical version:**
- **Container filesystem is ephemeral by default.** Anything written to a container's writable layer is lost when the container crashes/restarts, because a new container gets a fresh filesystem layer.
- **Volume** — a Kubernetes abstraction that attaches storage to a **Pod's lifecycle** (not the container's). Types include `emptyDir` (scratch space, deleted when the Pod is deleted), `hostPath` (mounts a path from the node — dangerous/unportable), `configMap`/`secret` (inject config as files), and persistent-backed volumes (`persistentVolumeClaim`, cloud disks, NFS, etc.).
- **PersistentVolume (PV)** — a **cluster-scoped resource** representing a piece of real storage (an AWS EBS volume, GCE PD, Azure Disk, NFS share, local disk, etc.). PVs are provisioned either **statically** (an admin manually creates the PV object ahead of time) or **dynamically** (a StorageClass creates one automatically when a PVC requests it). PVs have a lifecycle independent of any Pod.
- **PersistentVolumeClaim (PVC)** — a **namespaced resource** representing a user's *request* for storage: how much size, which access modes, optionally which StorageClass. Kubernetes' control loop **binds** a PVC to a PV that satisfies the request (capacity ≥ requested, matching access modes, matching StorageClass if specified). Once bound, the binding is exclusive (1:1).
- **StorageClass (SC)** — defines a "class" of storage (e.g. `fast-ssd`, `standard`) with a **provisioner** (e.g. `kubernetes.io/aws-ebs`, `rancher.io/local-path` used by Minikube) and parameters (disk type, filesystem, reclaim policy). When a PVC references a StorageClass (or a default StorageClass exists), the provisioner dynamically creates a matching PV — no admin needs to pre-create storage.
- **The chain:** `Pod` (mounts a volume of type `persistentVolumeClaim`) → `PVC` (the request, bound to) → `PV` (the actual cluster storage resource, backed by) → **storage backend** (cloud disk / NFS / local disk / etc.).
- **Access Modes** (what a PV supports, requested by a PVC):
  - **ReadWriteOnce (RWO)** — mounted as read-write by a single node at a time (most cloud block storage: EBS, GCE PD, Azure Disk).
  - **ReadOnlyMany (ROX)** — mounted read-only by many nodes simultaneously.
  - **ReadWriteMany (RWX)** — mounted read-write by many nodes simultaneously (needs a shared filesystem backend like NFS, EFS, Azure Files, CephFS — not plain block storage).
  - (Newer: **ReadWriteOncePod (RWOP)** — read-write by a single Pod, stricter than RWO.)
- **Reclaim Policy** on a PV determines what happens after its PVC is deleted: `Retain` (PV and data kept, needs manual cleanup), `Delete` (PV and underlying storage deleted automatically — common default for dynamically provisioned volumes), `Recycle` (deprecated).

## 2. Diagram

```
Static/Dynamic binding chain:

┌─────────┐      mounts       ┌─────────┐     binds to      ┌─────────┐     backed by      ┌──────────────────┐
│   Pod   │ ────────────────► │   PVC   │ ─────────────────►│   PV    │ ──────────────────►│  Storage Backend  │
│         │  volumes:         │ (request│   (cluster admin   │(cluster │   (real disk/share) │ EBS / GCE PD /    │
│         │   - pvc: my-claim │  size,  │    or dynamic       │ resource│                     │ NFS / Local Disk  │
└─────────┘  access mode)     │ access) │    provisioning)    │)        │                     └──────────────────┘
                               └─────────┘                     └─────────┘


Dynamic Provisioning Lifecycle (via StorageClass):

 1. User creates PVC             2. No matching PV exists       3. StorageClass's provisioner
    (references StorageClass) ─────► so control loop checks ──────► creates a NEW PV on the fly
                                       the StorageClass                (e.g. calls cloud API to
                                                                         create a disk)
                                                                              │
                                                                              ▼
 5. Pod mounts the PVC    ◄──────  4. New PV auto-binds to the PVC  ◄────────┘
    and can read/write               (status: Bound)
    persistent data


Analogy:
  PV          = a storage unit already sitting in the warehouse
  PVC         = your rental reservation slip requesting a unit of a certain size
  StorageClass= an on-demand unit-builder: builds a brand-new unit the moment
                a reservation slip (PVC) comes in, instead of relying on
                pre-built inventory
```

## 3. Real-World Example

Think of a **stateless web frontend** vs a **stateful Postgres database**, both running in the same cluster:

- The **frontend Pod** holds no important local state — if Kubernetes kills it and reschedules it on another node, a brand-new container starts fresh, pulls its static assets from the image, and nothing is lost. `emptyDir` or no volume at all is fine.
- The **Postgres Pod**, however, stores actual customer data on disk (`/var/lib/postgresql/data`). If that Pod crashes and Kubernetes reschedules it — possibly onto a *different node* — the new Postgres container **must** see the same data files, or the database is effectively wiped.
- This is solved by giving the Postgres Pod a **PVC** backed by a **PV** (e.g. an AWS EBS volume via the `gp2`/`gp3` StorageClass). The PVC/PV binding is independent of which node the Pod lands on (cloud block storage can be attached to whichever node the Pod is rescheduled to). When the Pod is deleted and recreated (e.g. via a StatefulSet), it re-mounts the *same* PVC, and Postgres sees its data exactly as it left it.
- This is why StatefulSets (covered later) are almost always paired with `volumeClaimTemplates` — each replica gets its own stable, persistent PVC that follows it across restarts.

## 4. Hands-On Lab

**Goal:** Create a PVC using Minikube's default StorageClass, mount it in a Pod, write a file, delete/recreate the Pod, and prove the file survives.

```bash
# 1. Confirm Minikube has a default StorageClass
kubectl get sc
```

**Expected output:**
```
NAME                 PROVISIONER                RECLAIMPOLICY   VOLUMEBINDINGMODE   ALLOWVOLUMEEXPANSION   AGE
standard (default)   k8s.io/minikube-hostpath   Delete          Immediate           false                  10m
```

```bash
# 2. Create a PVC requesting 1Gi with default StorageClass
cat <<EOF > pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: data-claim
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
EOF

kubectl apply -f pvc.yaml

# 3. Watch it bind (dynamic provisioning creates a matching PV automatically)
kubectl get pvc data-claim
```

**Expected output:**
```
NAME         STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
data-claim   Bound    pvc-3f1a9b2e-1234-4c56-9abc-1234567890ab   1Gi        RWO            standard       5s
```

```bash
# 4. Confirm the PV was auto-created
kubectl get pv
```

**Expected output:**
```
NAME                                       CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM                STORAGECLASS   AGE
pvc-3f1a9b2e-1234-4c56-9abc-1234567890ab   1Gi        RWO            Delete           Bound    default/data-claim   standard       5s
```

```bash
# 5. Create a Pod that mounts the PVC
cat <<EOF > pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: storage-test
spec:
  containers:
    - name: app
      image: busybox
      command: ["sleep", "3600"]
      volumeMounts:
        - name: data
          mountPath: /data
  volumes:
    - name: data
      persistentVolumeClaim:
        claimName: data-claim
EOF

kubectl apply -f pod.yaml
kubectl wait --for=condition=Ready pod/storage-test --timeout=60s

# 6. Write a file inside the mounted volume
kubectl exec storage-test -- sh -c "echo 'hello persistent world' > /data/proof.txt"
kubectl exec storage-test -- cat /data/proof.txt
```

**Expected output:**
```
hello persistent world
```

```bash
# 7. Delete and recreate the Pod (simulates crash/reschedule)
kubectl delete pod storage-test
kubectl apply -f pod.yaml
kubectl wait --for=condition=Ready pod/storage-test --timeout=60s

# 8. Verify the file is still there
kubectl exec storage-test -- cat /data/proof.txt
```

**Expected output (proves persistence):**
```
hello persistent world
```

```bash
# 9. Inspect everything together
kubectl get pv,pvc,sc
```

**Expected output:**
```
NAME                                                        CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM                STORAGECLASS   AGE
persistentvolume/pvc-3f1a9b2e-1234-4c56-9abc-1234567890ab   1Gi        RWO            Delete           Bound    default/data-claim   standard       3m

NAME                                 STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
persistentvolumeclaim/data-claim     Bound    pvc-3f1a9b2e-1234-4c56-9abc-1234567890ab   1Gi        RWO            standard       3m

NAME                            PROVISIONER                RECLAIMPOLICY   VOLUMEBINDINGMODE   ALLOWVOLUMEEXPANSION   AGE
storageclass.storage.k8s.io/standard (default)   k8s.io/minikube-hostpath   Delete   Immediate   false   10m
```

```bash
# 10. Cleanup (this deletes the PVC and, with reclaim policy "Delete", the PV/data too)
kubectl delete pod storage-test
kubectl delete pvc data-claim
kubectl get pv    # PV should now be gone
```

**Troubleshooting:**
- PVC stuck `Pending` → run `kubectl describe pvc data-claim` and check events; usually no StorageClass matched or the provisioner can't create storage.
- Pod stuck `ContainerCreating` → run `kubectl describe pod storage-test`; often a volume mount/attach failure.

## 5. Common Mistakes

1. **Using `emptyDir` and expecting data to survive Pod restarts.** `emptyDir` survives *container* restarts within the same Pod, but is **deleted the moment the Pod itself is deleted** — it is not for durable data.
2. **PVC stuck in `Pending` forever** because no StorageClass matches (typo in `storageClassName`, no default StorageClass configured in the cluster) or no static PV exists with sufficient capacity/matching access mode. Always check `kubectl describe pvc <name>` for binding events.
3. **Requesting the wrong access mode for a multi-Pod scenario.** Asking for `ReadWriteMany` on a backend that only supports block storage (e.g. AWS EBS, GCE PD) will fail — RWX needs a shared filesystem backend (NFS, EFS, Azure Files, CephFS). Conversely, using `ReadWriteOnce` and expecting two Pods on different nodes to both mount it read-write will cause scheduling/attach failures.
4. **Assuming deleting a Pod deletes its data.** PVs/PVCs are decoupled from Pod lifecycle by design — deleting a Pod does NOT delete the PVC or PV. However, deleting the *PVC* itself, combined with a `Delete` reclaim policy, WILL delete the underlying PV and its real storage — a common source of "why is my data gone" incidents when someone cleans up namespaces carelessly.
5. **Forgetting StorageClass differences across environments.** A YAML that hardcodes `storageClassName: gp2` will fail on Minikube (`standard`) or a different cloud provider — prefer omitting `storageClassName` to use the cluster's default, or parameterize it (Helm/Kustomize) per environment.
6. **Not setting resource requests correctly** — requesting more storage in the PVC than any available/dynamically-provisionable size allows, leaving the PVC permanently `Pending`.

## 6. Interview Questions (with brief answers)

1. **What is the difference between a Volume, a PersistentVolume, and a PersistentVolumeClaim?** — A **Volume** is a Pod-level abstraction for storage tied to the Pod's lifecycle (can be ephemeral like `emptyDir` or backed by persistent storage). A **PersistentVolume (PV)** is a cluster-scoped resource representing actual provisioned storage, independent of any Pod. A **PersistentVolumeClaim (PVC)** is a namespaced *request* for storage made by a user/Pod, which Kubernetes binds to a matching PV.
2. **What is a StorageClass and why do we need it?** — A StorageClass defines a class of storage (provisioner + parameters, e.g. disk type, reclaim policy) that enables **dynamic provisioning**: when a PVC references it (or it's the cluster default), Kubernetes automatically creates a new PV on demand instead of requiring an admin to pre-create PVs manually.
3. **What happens to a PV when its PVC is deleted?** — It depends on the PV's **reclaim policy**: `Delete` removes the PV and underlying storage automatically; `Retain` keeps the PV (and data) around for manual admin cleanup/reuse; `Recycle` (deprecated) wiped and made the PV available again.
4. **What are the Kubernetes storage access modes, and what's a real backend limitation?** — `ReadWriteOnce` (one node, read-write), `ReadOnlyMany` (many nodes, read-only), `ReadWriteMany` (many nodes, read-write), and `ReadWriteOncePod` (one Pod, read-write). A key real-world limitation: cloud block storage like AWS EBS or GCE PD only supports RWO — RWX requires a shared filesystem backend such as NFS or EFS.
5. **Why would you use a PVC-backed volume instead of `hostPath` for a database Pod?** — `hostPath` ties data to a specific node's local disk — if the Pod is rescheduled to another node, the data is inaccessible (and `hostPath` is inherently unportable/insecure). A PVC-backed PV (e.g. an EBS volume) can be attached to whichever node the Pod lands on, and is managed declaratively/portably by Kubernetes rather than depending on node-specific paths.

## 7. Quiz (50 Questions)

**True/False:**
1. A container's filesystem is persistent by default across restarts. (F)
2. A Volume's lifecycle is tied to the Pod, not the individual container. (T)
3. `emptyDir` volumes survive Pod deletion. (F)
4. A PersistentVolume is a cluster-scoped resource. (T)
5. A PersistentVolumeClaim is namespaced. (T)
6. PVCs and PVs bind in a 1:1 relationship. (T)
7. A StorageClass is required to statically provision a PV. (F)
8. Dynamic provisioning removes the need for an admin to manually pre-create PVs. (T)
9. `ReadWriteMany` is supported by all cloud block storage types like AWS EBS. (F)
10. Deleting a Pod automatically deletes its bound PVC. (F)
11. Deleting a PVC can trigger deletion of its PV if the reclaim policy is `Delete`. (T)
12. `hostPath` volumes are portable across any node in the cluster. (F)
13. Minikube ships with a default StorageClass called `standard`. (T)
14. A PV can be bound to multiple PVCs simultaneously. (F)
15. `ReadWriteOncePod` restricts read-write access to a single Pod (stricter than RWO). (T)

**Multiple Choice:**
16. Which resource represents a user's *request* for storage? a) Volume b) PV c) PVC d) StorageClass → (c)
17. Which resource represents actual cluster-wide provisioned storage? a) PVC b) PV c) ConfigMap d) Secret → (b)
18. What enables dynamic provisioning of PVs? a) hostPath b) StorageClass c) emptyDir d) Node affinity → (b)
19. Which access mode allows read-write by many nodes concurrently? a) RWO b) ROX c) RWX d) None → (c)
20. Which reclaim policy deletes the underlying storage when the PVC is removed? a) Retain b) Delete c) Recycle d) Ignore → (b)
21. Which volume type is deleted along with its Pod? a) PVC-backed volume b) emptyDir c) NFS PV d) Cloud disk PV → (b)
22. What field in a PVC spec requests a StorageClass explicitly? a) volumeName b) storageClassName c) provisioner d) className → (b)
23. Which command shows PVs, PVCs, and StorageClasses together? a) `kubectl get all` b) `kubectl get pv,pvc,sc` c) `kubectl describe storage` d) `kubectl get volumes` → (b)
24. Which backend is best suited for ReadWriteMany? a) AWS EBS b) GCE PD c) NFS d) Local SSD → (c)
25. What's the Minikube default StorageClass provisioner? a) kubernetes.io/aws-ebs b) k8s.io/minikube-hostpath c) csi.vsphere.vmware.com d) rancher.io/nfs → (b)

**Short Answer:**
26. What happens to data written to a container's writable layer when the container restarts?
27. What problem does a Volume solve that the container filesystem alone does not?
28. What is the difference between static and dynamic PV provisioning?
29. Name the three standard access modes for a PV.
30. What command would you use to check why a PVC is stuck in `Pending`?
31. What is the relationship chain from Pod to actual storage backend?
32. Why might a StatefulSet use `volumeClaimTemplates` instead of a single shared PVC?
33. What does the `Retain` reclaim policy protect against?
34. Why can't you typically mount an EBS-backed PVC as ReadWriteMany?
35. What is one risk of using `hostPath` in production?

**Scenario-Based:**
36. Your Postgres Pod is rescheduled to a different node after a crash. What Kubernetes storage setup ensures the database data isn't lost?
37. A developer created a PVC with `storageClassName: fast-ssd`, but the cluster has no such StorageClass. What will happen, and how do you diagnose it?
38. Two Pods on different nodes both need read-write access to the same files simultaneously. What access mode and backend type do you need?
39. A team accidentally deletes a PVC in production and later discovers all the data is gone. What reclaim policy setting explains this, and what should have been used instead?
40. You're moving an app from Minikube to AWS EKS. What storage-related YAML field might you need to change or remove?

**Fill in the Blank:**
41. A ______ attaches storage to a Pod's lifecycle.
42. A ______ represents cluster-wide storage that can be statically or dynamically provisioned.
43. A ______ is a user's request for storage that gets matched to a PV.
44. A ______ enables Kubernetes to automatically create a new PV when a matching one doesn't exist.
45. The access mode that allows only a single node to mount read-write is called ______.

**Conceptual Deep-Dive:**
46. Why is it architecturally important that PV/PVC lifecycles are decoupled from Pod lifecycles?
47. How does the "on-demand locker-builder" analogy map to a StorageClass's provisioner field?
48. What tradeoffs exist between using `Retain` vs `Delete` reclaim policy in a production environment?
49. Why do stateless apps (like frontends) typically not need PersistentVolumes, while stateful apps (like databases) do?
50. How does Kubernetes decide which PV to bind to a given PVC when multiple PVs could technically satisfy the request?

---

## 8. Hour 9 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Container filesystem** | Ephemeral by default — lost on container restart |
| **Volume** | Storage attached to a Pod's lifecycle (e.g. `emptyDir`, `hostPath`, PVC-backed) |
| **PersistentVolume (PV)** | Cluster-scoped storage resource, provisioned statically by an admin or dynamically |
| **PersistentVolumeClaim (PVC)** | Namespaced request for storage; binds 1:1 to a matching PV |
| **StorageClass** | Enables dynamic provisioning — auto-creates a PV on demand, no manual pre-creation needed |
| **Chain** | Pod → PVC → PV → actual storage backend (cloud disk, NFS, local disk) |
| **Access Modes** | RWO (one node r/w), ROX (many nodes read-only), RWX (many nodes r/w), RWOP (one Pod r/w) |
| **Reclaim Policy** | `Delete` removes PV+storage with PVC; `Retain` keeps it for manual cleanup |
| **Mental model** | PV = warehouse storage unit; PVC = rental reservation; StorageClass = on-demand unit-builder |
| **Lab outcome** | You proved a file written to a PVC-backed volume survives Pod deletion and recreation |

**Mnemonic:** *"VPPS"* — **V**olume ties storage to a Pod, **P**ersistentVolume is the real cluster storage, **P**ersistentVolumeClaim is the request that binds to it, **S**torageClass builds PVs on demand.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Volumes](https://kubernetes.io/docs/concepts/storage/volumes/)
- [Kubernetes Official Docs — Persistent Volumes](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)
- [Kubernetes Official Docs — Storage Classes](https://kubernetes.io/docs/concepts/storage/storage-classes/)
- [Kubernetes Official Docs — Dynamic Volume Provisioning](https://kubernetes.io/docs/concepts/storage/dynamic-provisioning/)
- YouTube: "TechWorld with Nana — Kubernetes Volumes, Persistent Volumes & Persistent Volume Claims" (free, excellent visuals)

**Mini-Project for Hour 9 (30–45 min):**
- Deploy a small stateful app (e.g. a single-replica Postgres or MySQL Deployment) backed by a PVC using Minikube's default StorageClass.
- Connect to it and create a table/insert a row (or, if using a simpler image, write a file to the mounted path as in the lab).
- Delete the Pod (`kubectl delete pod <name>`) and let the Deployment recreate it.
- Reconnect and prove the data/table/file is still there — this is your hands-on proof that PVC-backed storage survives Pod rescheduling, unlike a stateless app relying on ephemeral storage.
- Bonus: change the PV's reclaim policy behavior by deleting the PVC afterward and observing whether the PV and underlying storage are also removed (`kubectl get pv` before/after).
