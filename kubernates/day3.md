# 🚢 DAY 3 — Kubernetes Storage: Volumes, PVs, PVCs & StorageClasses

---

## 1. LEARNING OBJECTIVES

**What you will learn today**

- The difference between *ephemeral* and *persistent* storage in Kubernetes.
- How `Volumes` attach storage to Pods, focusing on `emptyDir` and `hostPath`.
- What a **PersistentVolume (PV)** and a **PersistentVolumeClaim (PVC)** are, and how they bind.
- How **StorageClasses** enable **dynamic provisioning** so you never hand-craft disks again.
- **Access modes** (RWO / ROX / RWX) and **reclaim policies** (Retain / Delete / Recycle).
- How to resize, inspect, and troubleshoot storage in production.

**Why it matters**

- Pods are ephemeral. When a Pod dies, anything written to its container filesystem is gone. Days 1–2 taught you to *run* workloads; today you learn to make their *data survive*.
- Every stateful system you will ever run on Kubernetes — databases, message queues, caches, object stores — depends on this chapter.

**Where it fits in the real world**

- A `postgres` Pod that loses its data on every restart is useless. PV/PVC is the contract that guarantees the disk follows the database.
- Cloud platforms (EKS, GKE, AKS) auto-provision EBS/PD/Disk volumes through StorageClasses. Understanding this is the line between "I can deploy nginx" and "I can run production infrastructure."

**Relation to prior days**

- **Day 1**: Pods, the smallest deployable unit — today we give those Pods durable storage.
- **Day 2**: Deployments/ReplicaSets and the controller pattern — today's PV/PVC binding is another controller-driven reconciliation loop, and tomorrow's StatefulSets (Day 4) stitch these together.

---

## 2. 80/20 BREAKDOWN

### The critical 20% (learn these cold)

| Concept | Why it's in the critical 20% | Interview / Prod frequency |
|---|---|---|
| `emptyDir` vs `hostPath` vs PVC | The #1 "which volume type?" decision | Very High |
| PVC → PV binding model | The core abstraction; explains 90% of storage bugs | Very High |
| StorageClass + dynamic provisioning | How real clusters give disks without manual PVs | Very High |
| Access modes (RWO/ROX/RWX) | Cause of "multi-Pod can't mount" failures | High |
| Reclaim policy (Retain vs Delete) | The "I deleted my PVC and lost prod data" interview trap | High |
| `kubectl get pvc,pv` + describe | First diagnostic move for any storage issue | Very High |

### Defer list (don't rabbit-hole today)

- CSI driver internals and writing your own provisioner.
- `Recycle` reclaim policy (deprecated — mention it, don't use it).
- Volume snapshots / `VolumeSnapshotClass` (advanced, post-fundamentals).
- `local` PVs and node affinity tuning.
- Projected volumes, `downwardAPI`, `csi` ephemeral inline volumes.
- Storage capacity scheduling and topology-aware provisioning details.

> 💰 **Interview gold**
> If you remember nothing else: **A PVC is a request for storage; a PV is the actual storage; a StorageClass is the factory that makes PVs on demand.** `emptyDir` dies with the Pod; `hostPath` ties you to one node; a PVC is the only portable, durable option. Deleting a PVC with reclaim policy `Delete` **destroys the underlying disk** — this is the most common "we lost prod data" story in interviews.

---

## 3. CONCEPT EXPLANATIONS

### 3.1 Ephemeral vs Persistent storage

**Beginner explanation**
A container's writable layer lives and dies with the container. Restart the Pod → fresh filesystem. *Ephemeral* storage (`emptyDir`) survives container restarts within the same Pod but vanishes when the Pod is deleted or rescheduled. *Persistent* storage (PVs via PVCs) lives independently of any Pod.

**Real-world analogy**
Ephemeral storage is the whiteboard in a meeting room — wiped when the meeting ends. Persistent storage is the filing cabinet down the hall — it stays even after everyone leaves.

**Production use case**
- Ephemeral: scratch space for image processing, caches, sidecar log buffers.
- Persistent: database files, uploaded user content, Prometheus TSDB.

**Common mistakes**
- Storing a database on `emptyDir` "because it worked in the demo."
- Assuming the container filesystem persists across restarts.

**Best practice**
Default to *ephemeral* for anything regenerable; use PVCs the moment data must outlive a Pod.

```
 Pod lifecycle vs storage
 ┌────────────────────────────────────────────┐
 │ Pod                                          │
 │  ┌───────────────┐   container restart       │
 │  │ container FS  │ ──✗ wiped ───────────────► │
 │  └───────────────┘                            │
 │  ┌───────────────┐   container restart        │
 │  │ emptyDir      │ ──✔ survives ────────────► │   Pod delete → ✗ gone
 │  └───────────────┘                            │
 └──────────────┬───────────────────────────────┘
                │ mounts
        ┌───────▼────────┐
        │ PVC → PV (disk) │  ✔ survives Pod delete, reschedule, node loss
        └─────────────────┘
```

---

### 3.2 Volumes: `emptyDir`

**Beginner explanation**
`emptyDir` creates an empty directory when the Pod is assigned to a node. All containers in the Pod can read/write it (great for sharing data between containers). It is deleted forever when the Pod is removed from the node.

**Real-world analogy**
A shared scratchpad passed between two coworkers (containers) sitting at the same desk (Pod). When the desk is cleared (Pod deleted), the scratchpad is thrown away.

**Production use case**
A web container writes files that a sidecar uploads to S3; both share an `emptyDir`. Also used for `medium: Memory` (tmpfs) RAM-backed scratch space for speed.

**Common mistakes**
- Expecting `emptyDir` to survive Pod rescheduling.
- Forgetting `medium: Memory` counts against the Pod's memory limit.

**Best practice**
Use it only for transient, regenerable data shared within a single Pod.

```
 ┌──────────── Pod ────────────┐
 │ ┌─────────┐   ┌──────────┐  │
 │ │ writer  │   │ sidecar  │  │
 │ │ /data   │   │ /data    │  │
 │ └────┬────┘   └────┬─────┘  │
 │      └─── emptyDir ─┘        │
 └─────────────────────────────┘
```

---

### 3.3 Volumes: `hostPath`

**Beginner explanation**
`hostPath` mounts a file or directory from the *node's* filesystem into the Pod. Data persists on that node, but if the Pod reschedules to a different node it sees a different (or empty) directory.

**Real-world analogy**
Storing files on one specific office's local hard drive. If you move offices (nodes), your files don't come with you.

**Production use case**
Node-level agents that legitimately need host access — e.g., a logging agent reading `/var/log`, or a node monitoring DaemonSet reading `/proc`.

**Common mistakes**
- Using `hostPath` for app data — breaks the moment the Pod moves nodes.
- Security risk: a Pod with `hostPath: /` can read/modify the entire node.

**Best practice**
Avoid for application data. Acceptable only for node-scoped system tooling (often via DaemonSets), and lock down paths.

```
 Node A                       Node B
 ┌──────────────┐             ┌──────────────┐
 │ /data/app  ◄─┤ Pod (here)  │ /data/app    │  ← empty/different!
 └──────────────┘             └──────────────┘
   reschedule ──────────────────────►  data NOT here
```

---

### 3.4 PersistentVolume (PV)

**Beginner explanation**
A PV is a cluster-level resource representing a *piece of real storage* (an EBS volume, an NFS export, a cloud disk). It is provisioned either by an admin (static) or automatically (dynamic). PVs exist independently of Pods.

**Real-world analogy**
A PV is an apartment unit that physically exists in a building. It's there whether or not anyone is renting it.

**Production use case**
A pre-created 100Gi NFS share that several teams will claim chunks of.

**Common mistakes**
- Confusing PV (the actual storage) with PVC (the request).
- Hand-creating PVs when a StorageClass would do it dynamically.

**Best practice**
In cloud, rely on dynamic provisioning. Reserve static PVs for pre-existing storage (legacy NFS, imported disks).

---

### 3.5 PersistentVolumeClaim (PVC)

**Beginner explanation**
A PVC is a *request* for storage by a user/Pod: "I need 10Gi, RWO, from this StorageClass." Kubernetes binds the PVC to a matching PV (or dynamically creates one). Pods mount PVCs, never PVs directly.

**Real-world analogy**
A PVC is the lease application: "I want a 2-bedroom apartment in this neighborhood." The system matches you to an actual apartment (PV).

**Production use case**
Every database Deployment/StatefulSet references a PVC for its data directory.

**Common mistakes**
- Requesting an access mode the StorageClass can't satisfy (RWX on EBS).
- Deleting a PVC and assuming the data is safe.

**Best practice**
Always set `storageClassName` explicitly and choose access modes deliberately.

---

### 3.6 The PV / PVC binding model

**Beginner explanation**
The control plane continuously tries to bind unbound PVCs to suitable PVs. A bind matches on: capacity (PV ≥ request), access modes, StorageClass, and selectors. Binding is **1:1 and exclusive** — one PVC ↔ one PV. With dynamic provisioning, if no PV matches, the StorageClass *creates* one.

**Real-world analogy**
A matchmaking service pairing lease applications (PVCs) to apartments (PVs). Once matched, that apartment is yours exclusively.

**Production use case**
You apply a PVC, the cloud provisioner creates an EBS volume, the PVC moves to `Bound`, and your Pod schedules onto a node in that volume's zone.

**Common mistakes**
- Expecting two PVCs to share one PV.
- Forgetting `WaitForFirstConsumer` delays binding until a Pod is scheduled (correct behavior for zonal disks).

**Best practice**
Use `volumeBindingMode: WaitForFirstConsumer` for zonal block storage to avoid scheduling a Pod to a zone where its disk can't attach.

```
 PVC (request)              binding loop              PV (storage)
 ┌──────────────┐         ┌──────────────┐          ┌──────────────┐
 │ 10Gi, RWO,   │ ───────►│ match: cap≥,  │◄──────── │ 20Gi, RWO,   │
 │ sc=standard  │         │ AM, sc, sel   │          │ sc=standard  │
 │ status:      │         └──────┬───────┘          │ status:      │
 │   Pending    │                │ bound            │  Available   │
 └──────────────┘                ▼                  └──────────────┘
        ▲              ┌──────────────────┐                ▲
        └────── Bound ─┤ exclusive 1:1    ├──── Bound ─────┘
                       └──────────────────┘
   (if no PV & dynamic SC → provisioner creates PV automatically)
```

---

### 3.7 StorageClass & dynamic provisioning

**Beginner explanation**
A StorageClass describes a *type* of storage and the provisioner that creates it (e.g., `ebs.csi.aws.com`). When a PVC names a StorageClass and no matching PV exists, the provisioner **dynamically creates** a PV. This removes manual PV creation entirely.

**Real-world analogy**
A vending machine for apartments: insert a request (PVC), it builds and hands you a brand-new unit (PV) on demand, in whatever model (SSD, HDD, gp3, io2) you selected.

**Production use case**
`gp3` StorageClass on EKS so every PVC automatically gets a right-sized EBS gp3 volume with tuned IOPS.

**Common mistakes**
- No default StorageClass set → PVCs hang `Pending` forever.
- Setting `allowVolumeExpansion: false` then trying to grow a disk.

**Best practice**
Mark one StorageClass as default, set `allowVolumeExpansion: true`, and use `WaitForFirstConsumer`.

```
 PVC(sc=fast) ──► StorageClass(fast) ──► Provisioner (CSI) ──► creates PV ──► Bound
                       │
                       ├─ reclaimPolicy: Delete
                       ├─ volumeBindingMode: WaitForFirstConsumer
                       └─ allowVolumeExpansion: true
```

---

### 3.8 Access modes (RWO / ROX / RWX)

**Beginner explanation**
Access modes declare how many nodes can mount the volume and how:
- **RWO** (ReadWriteOnce): mounted read-write by a single *node*. (Multiple Pods on that same node can share it.)
- **ROX** (ReadOnlyMany): mounted read-only by many nodes.
- **RWX** (ReadWriteMany): mounted read-write by many nodes (needs NFS/CephFS/EFS — *not* plain block storage like EBS).
- **RWOP** (ReadWriteOncePod): exactly one Pod, cluster-wide (newer).

**Real-world analogy**
RWO = one car, one driver at a time. ROX = a printed book many can read. RWX = a shared Google Doc everyone edits live.

**Production use case**
A database → RWO. A shared media library read by many web Pods → ROX/RWX (EFS).

**Common mistakes**
- Requesting RWX on EBS/PD block storage (unsupported) → Pods stuck.
- Assuming RWO means "one Pod" (it's one *node*).

**Best practice**
Match access mode to the storage backend's real capability. Use RWOP when you truly need single-Pod exclusivity.

---

### 3.9 Reclaim policies (Retain / Delete / Recycle)

**Beginner explanation**
The reclaim policy decides what happens to a PV (and its backing disk) when its PVC is deleted:
- **Delete** (default for dynamic): PV *and* the real disk are destroyed. Data gone.
- **Retain**: PV kept (becomes `Released`), disk preserved for manual recovery.
- **Recycle**: deprecated — don't use.

**Real-world analogy**
Delete = move out and the apartment is demolished. Retain = move out but the apartment (and your stuff) is sealed for you to retrieve later.

**Production use case**
Production databases use `Retain` so an accidental PVC delete doesn't nuke the data.

**Common mistakes**
- Leaving dynamic PVs on `Delete` for critical data.
- Forgetting that `Retain`ed PVs must be manually cleaned up (they don't auto-rebind).

**Best practice**
`Retain` for stateful production data; `Delete` for ephemeral/regenerable dynamic volumes.

```
 PVC deleted
     │
     ├── policy=Delete  ─► PV removed ─► backing disk DESTROYED  ✗
     └── policy=Retain  ─► PV status=Released ─► disk kept (manual recovery) ✔
```

---

## 4. HANDS-ON LABS

> All labs assume a working cluster (`kind`, `minikube`, or cloud). Verify with `kubectl get nodes`.

### Lab 1 — `emptyDir` shared between two containers

**Goal:** A `writer` container writes a timestamp; a `reader` sidecar reads it from the same `emptyDir`.

```yaml
# lab1-emptydir.yaml
apiVersion: v1
kind: Pod
metadata:
  name: shared-emptydir
spec:
  containers:
    - name: writer
      image: busybox:1.36
      command: ["sh", "-c", "while true; do date >> /data/out.txt; sleep 5; done"]
      volumeMounts:
        - name: scratch
          mountPath: /data
    - name: reader
      image: busybox:1.36
      command: ["sh", "-c", "tail -f /data/out.txt"]
      volumeMounts:
        - name: scratch
          mountPath: /data
  volumes:
    - name: scratch
      emptyDir: {}
```

```bash
kubectl apply -f lab1-emptydir.yaml
kubectl wait --for=condition=Ready pod/shared-emptydir --timeout=60s
kubectl logs shared-emptydir -c reader --tail=3
```

**Expected output:**
```
pod/shared-emptydir created
pod/shared-emptydir condition met
Sat Jun 14 10:00:00 UTC 2026
Sat Jun 14 10:00:05 UTC 2026
Sat Jun 14 10:00:10 UTC 2026
```

**Prove it's ephemeral:**
```bash
kubectl delete pod shared-emptydir
# recreate, then check:
kubectl apply -f lab1-emptydir.yaml
kubectl exec shared-emptydir -c reader -- wc -l /data/out.txt
```
**Expected:** the file restarts near `1` lines — old data is gone (Pod deletion wiped the emptyDir).

---

### Lab 2 — Static PV + PVC + Pod mounting it

**Goal:** Manually create a PV (using `hostPath` for a single-node lab), claim it, and mount it.

```yaml
# lab2-static-pv.yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: static-pv
spec:
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  storageClassName: manual
  hostPath:
    path: /mnt/data
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: static-pvc
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: manual
  resources:
    requests:
      storage: 500Mi
---
apiVersion: v1
kind: Pod
metadata:
  name: pv-consumer
spec:
  containers:
    - name: app
      image: busybox:1.36
      command: ["sh", "-c", "echo 'persisted!' > /store/data.txt && sleep 3600"]
      volumeMounts:
        - name: vol
          mountPath: /store
  volumes:
    - name: vol
      persistentVolumeClaim:
        claimName: static-pvc
```

```bash
kubectl apply -f lab2-static-pv.yaml
kubectl get pv,pvc
```

**Expected output:**
```
NAME                         CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM                STORAGECLASS
persistentvolume/static-pv   1Gi        RWO            Retain           Bound    default/static-pvc   manual

NAME                               STATUS   VOLUME      CAPACITY   ACCESS MODES   STORAGECLASS
persistentvolumeclaim/static-pvc   Bound    static-pv   1Gi        RWO            manual
```

Note: the 500Mi request bound to the 1Gi PV (PV capacity ≥ request). The Pod sees the *whole* PV.

```bash
kubectl exec pv-consumer -- cat /store/data.txt
```
**Expected:** `persisted!`

---

### Lab 3 — Dynamic provisioning via StorageClass

**Goal:** Let a StorageClass create the PV automatically. (`kind`/`minikube` ship a default provisioner; on cloud, use the cloud StorageClass.)

```bash
kubectl get storageclass
```
**Expected (kind example):**
```
NAME                 PROVISIONER             RECLAIMPOLICY   VOLUMEBINDINGMODE      ALLOWVOLUMEEXPANSION
standard (default)   rancher.io/local-path   Delete          WaitForFirstConsumer   false
```

```yaml
# lab3-dynamic.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: dynamic-pvc
spec:
  accessModes:
    - ReadWriteOnce
  # storageClassName omitted → uses the default StorageClass
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: Pod
metadata:
  name: dynamic-consumer
spec:
  containers:
    - name: app
      image: busybox:1.36
      command: ["sh", "-c", "echo dynamic > /data/file && sleep 3600"]
      volumeMounts:
        - name: vol
          mountPath: /data
  volumes:
    - name: vol
      persistentVolumeClaim:
        claimName: dynamic-pvc
```

```bash
kubectl apply -f lab3-dynamic.yaml
kubectl get pvc dynamic-pvc
```

**Expected (WaitForFirstConsumer → binds once the Pod schedules):**
```
NAME          STATUS    VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS
dynamic-pvc   Bound     pvc-7f3c1a9e-2b6d-4f0a-9c2e-1a2b3c4d5e6f   1Gi        RWO            standard
```

Notice: **you never created a PV** — the provisioner generated `pvc-7f3c...` automatically.

---

### Lab 4 — Inspecting and resizing a PVC

**Goal:** Grow a PVC (requires `allowVolumeExpansion: true` on the StorageClass).

```bash
# Create an expandable StorageClass-backed PVC, or patch an existing expandable SC.
# Inspect current size:
kubectl get pvc dynamic-pvc -o jsonpath='{.status.capacity.storage}{"\n"}'
```
**Expected:** `1Gi`

```bash
# Request expansion to 2Gi (only works if allowVolumeExpansion=true):
kubectl patch pvc dynamic-pvc -p '{"spec":{"resources":{"requests":{"storage":"2Gi"}}}}'
kubectl describe pvc dynamic-pvc | grep -A2 Conditions
```
**Expected (online expansion):**
```
Conditions:
  Type                      Status
  FileSystemResizePending   True
```
After the Pod restarts (or for filesystems supporting online resize):
```bash
kubectl get pvc dynamic-pvc -o jsonpath='{.status.capacity.storage}{"\n"}'
```
**Expected:** `2Gi`

> ⚠️ Shrinking a PVC is **not supported**. You can only grow.

```bash
# Inspect deeply:
kubectl describe pv $(kubectl get pvc dynamic-pvc -o jsonpath='{.spec.volumeName}')
```

---

## 5. EXERCISES

1. Create a Pod with a `medium: Memory` (tmpfs) `emptyDir`, write a 50MB file into it, and observe it counting against the Pod's memory. (`emptyDir: { medium: Memory, sizeLimit: 64Mi }`.)
2. Create a static PV with reclaim policy `Retain`, bind a PVC, write data, then **delete the PVC** and verify the PV moves to `Released` and the data is still on disk.
3. Build a custom StorageClass named `fast` with `allowVolumeExpansion: true` and `volumeBindingMode: WaitForFirstConsumer`, then create a PVC that uses it.
4. Deliberately request `ReadWriteMany` on a block-storage StorageClass and capture the resulting events to see *why* it fails (or hangs).
5. Make one StorageClass the cluster default, create a PVC with **no** `storageClassName`, and confirm it binds via the default class.

---

## 6. TROUBLESHOOTING SECTION

### 6.1 PVC stuck in `Pending`

- **Symptoms:** `kubectl get pvc` shows `Pending` indefinitely; Pod can't start.
- **Root cause:** No matching PV (static) OR no default/named StorageClass OR provisioner unavailable OR requested access mode/size unsatisfiable.
- **Diagnosis:**
  ```bash
  kubectl describe pvc <name>
  kubectl get sc
  kubectl get events --field-selector involvedObject.name=<pvc>
  ```
  Look for `no persistent volumes available` or `storageclass not found`.
- **Resolution:** Set a default StorageClass, correct `storageClassName`, lower the requested size, or create a matching PV. For `WaitForFirstConsumer`, this is *normal* until a Pod is scheduled.

### 6.2 Pod stuck in `ContainerCreating` due to volume mount

- **Symptoms:** Pod `ContainerCreating`; events show `FailedMount` / `FailedAttachVolume`.
- **Root cause:** Disk in a different AZ than the node, CSI driver issue, multi-attach conflict (RWO volume still attached to another node), or stale `volumeattachment`.
- **Diagnosis:**
  ```bash
  kubectl describe pod <pod>      # look at Events
  kubectl get volumeattachment
  ```
  Common message: `Multi-Attach error for volume ... already used by pod`.
- **Resolution:** Ensure the Pod schedules in the volume's zone (use `WaitForFirstConsumer`); delete the old Pod holding the RWO volume; verify CSI driver Pods are healthy.

### 6.3 Access mode mismatch (RWX on block storage)

- **Symptoms:** PVC `Pending`, or multiple Pods on different nodes fail to mount.
- **Root cause:** Requested `ReadWriteMany` from a backend (EBS/PD) that only supports `ReadWriteOnce`.
- **Diagnosis:**
  ```bash
  kubectl describe pvc <name>   # provisioner rejects unsupported access mode
  kubectl get sc <sc> -o yaml   # confirm provisioner
  ```
- **Resolution:** Use an RWX-capable backend (EFS, NFS, CephFS, Azure Files) or redesign for RWO (single writer). For "many readers," use ROX.

### 6.4 Data lost on restart due to `emptyDir`

- **Symptoms:** App reports empty database / lost files after a Pod reschedule or delete.
- **Root cause:** Storage was an `emptyDir` (or container filesystem), which is tied to the Pod lifecycle.
- **Diagnosis:**
  ```bash
  kubectl get pod <pod> -o jsonpath='{.spec.volumes}' | jq
  ```
  If you see `emptyDir`, that's the culprit.
- **Resolution:** Replace `emptyDir` with a PVC backed by a PV. For stateful workloads, use a StatefulSet (Day 4) with `volumeClaimTemplates`.

---

## 7. QUIZ SECTION

**MCQ**

**Q1.** What does `ReadWriteOnce` actually restrict?
- A) One Pod cluster-wide
- B) One node mounts it read-write
- C) One container
- D) One namespace

**Q2.** A dynamically provisioned PV has reclaim policy `Delete`. You delete the PVC. What happens?
- A) PV becomes `Released`, disk kept
- B) PV and the backing disk are destroyed
- C) Nothing until you delete the PV
- D) PVC is recreated automatically

**Q3.** A PVC is `Pending` and `kubectl get sc` returns nothing. Most likely cause?
- A) Wrong access mode
- B) No StorageClass exists / none is default
- C) Node out of disk
- D) Pod not scheduled

**Short answer**

**Q4.** In one sentence, distinguish a PV from a PVC.

**Q5.** Why is `WaitForFirstConsumer` preferable to `Immediate` for zonal block storage?

**Scenario**

**Q6.** Your team runs Postgres on Kubernetes. After a node failure, the Pod rescheduled and Postgres came up *empty*. The volume was an `emptyDir`. Walk through what went wrong and how you'd fix it permanently.

---

### Answers

**A1 — B.** RWO is per-*node*, not per-Pod. Multiple Pods on the *same* node can share an RWO volume. (For one-Pod-cluster-wide, use `ReadWriteOncePod`.)

**A2 — B.** `Delete` destroys both the PV object and the real underlying disk. This is the classic data-loss trap — use `Retain` for critical data.

**A3 — B.** No StorageClass means dynamic provisioning can't happen; with no matching static PV either, the PVC hangs `Pending`.

**A4.** A PV is the actual storage resource in the cluster; a PVC is a user/Pod's *request* that gets bound 1:1 to a suitable PV.

**A5.** With `Immediate`, the PV (and its zonal disk) is created before the Pod is scheduled, so the scheduler may place the Pod in a different zone where the disk can't attach. `WaitForFirstConsumer` delays provisioning until the Pod is scheduled, ensuring the disk is created in the right zone.

**A6.** `emptyDir` is tied to the Pod's lifetime on a node; when the node failed and the Pod rescheduled elsewhere, the directory was recreated empty. Fix: use a PVC backed by a durable PV (cloud block storage) via a StorageClass, ideally through a StatefulSet `volumeClaimTemplate`, with reclaim policy `Retain` so accidental PVC deletion doesn't destroy data.

---

## 8. CHALLENGE PROJECT — Stateful PostgreSQL with persistent storage

**Goal:** Deploy PostgreSQL whose data survives Pod deletion and node reschedule, backed by a dynamically provisioned PVC.

**Requirements**
1. A `Secret` for the DB password.
2. A PVC (1Gi) using the default StorageClass.
3. A Deployment mounting the PVC at `/var/lib/postgresql/data`.
4. A `ClusterIP` Service.
5. Prove durability: write a row, delete the Pod, confirm the row survives.

### Reference solution

```yaml
# challenge-postgres.yaml
apiVersion: v1
kind: Secret
metadata:
  name: pg-secret
type: Opaque
stringData:
  POSTGRES_PASSWORD: "S3cretPass!"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pg-data
spec:
  accessModes: ["ReadWriteOnce"]
  resources:
    requests:
      storage: 1Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
spec:
  replicas: 1
  selector:
    matchLabels: { app: postgres }
  strategy:
    type: Recreate          # never two Pods on one RWO volume
  template:
    metadata:
      labels: { app: postgres }
    spec:
      containers:
        - name: postgres
          image: postgres:16
          env:
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef: { name: pg-secret, key: POSTGRES_PASSWORD }
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          ports:
            - containerPort: 5432
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: pg-data
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
spec:
  selector: { app: postgres }
  ports:
    - port: 5432
      targetPort: 5432
```

**Verify durability:**
```bash
kubectl apply -f challenge-postgres.yaml
kubectl rollout status deploy/postgres

POD=$(kubectl get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POD -- psql -U postgres -c "CREATE TABLE t(id int); INSERT INTO t VALUES (42);"

# Kill the Pod — the PVC stays bound:
kubectl delete pod $POD
kubectl rollout status deploy/postgres

POD=$(kubectl get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POD -- psql -U postgres -c "SELECT * FROM t;"
```
**Expected:**
```
 id
----
 42
(1 row)
```
The row survived because the data lives on the PVC, not the Pod. (In production this would be a **StatefulSet** with `volumeClaimTemplates` — Day 4.)

---

## 9. KNOWLEDGE CHECK

Tick each before moving on:

- [ ] I can explain ephemeral vs persistent storage with an example.
- [ ] I know when to use `emptyDir`, `hostPath`, and a PVC.
- [ ] I can describe the PV ↔ PVC binding rules (capacity, access mode, SC).
- [ ] I can create a PVC that triggers dynamic provisioning.
- [ ] I understand RWO/ROX/RWX and which backends support RWX.
- [ ] I understand `Retain` vs `Delete` and the data-loss risk.
- [ ] I can diagnose a `Pending` PVC and a `ContainerCreating` mount failure.
- [ ] I successfully completed the Postgres durability challenge.

---

## 10. CHEAT SHEET

```bash
# Inspect storage
kubectl get pv                       # cluster-wide volumes
kubectl get pvc -A                   # claims (namespaced)
kubectl get sc                       # storage classes
kubectl describe pvc <name>          # binding events, conditions
kubectl describe pv <name>           # backing disk, reclaim policy
kubectl get pvc <n> -o jsonpath='{.spec.volumeName}'    # find bound PV

# Default StorageClass
kubectl patch sc <sc> -p \
 '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

# Resize a PVC (needs allowVolumeExpansion: true)
kubectl patch pvc <n> -p '{"spec":{"resources":{"requests":{"storage":"5Gi"}}}}'

# Reclaim policy change on a live PV
kubectl patch pv <pv> -p '{"spec":{"persistentVolumeReclaimPolicy":"Retain"}}'

# Debug mounts
kubectl describe pod <pod>           # FailedMount / FailedAttach events
kubectl get volumeattachment
```

| Concept | Key fact |
|---|---|
| `emptyDir` | Dies with the Pod; shared across containers in the Pod |
| `hostPath` | Node-local; breaks on reschedule; security-sensitive |
| PV | The real storage resource (cluster-scoped) |
| PVC | The request for storage (namespaced); binds 1:1 to a PV |
| StorageClass | Factory for dynamic PVs; defines provisioner & policies |
| RWO | One node read-write (multi-Pod same node OK) |
| ROX | Many nodes read-only |
| RWX | Many nodes read-write (needs NFS/EFS/CephFS) |
| RWOP | Exactly one Pod cluster-wide |
| Retain | Disk preserved after PVC delete (manual cleanup) |
| Delete | Disk destroyed after PVC delete (default for dynamic) |
| WaitForFirstConsumer | Provision after Pod scheduled (zonal safety) |

---

## 11. INTERVIEW PREPARATION

**Beginner**
- *What's the difference between a Volume and a PersistentVolume?* A Volume is defined in the Pod spec and (for `emptyDir`) tied to its lifecycle; a PV is an independent cluster resource that outlives Pods.
- *Why not store database data in the container filesystem?* It's wiped on every container restart and lost on reschedule.

**Intermediate**
- *Explain the PVC binding process.* Control plane matches an unbound PVC to a PV by capacity, access modes, StorageClass, and selectors; with dynamic provisioning it creates a PV if none matches; binding is exclusive 1:1.
- *RWO vs RWOP?* RWO is one *node*; RWOP is one *Pod* cluster-wide.

**Scenario**
- *A PVC is stuck `Pending` in prod — walk me through it.* `describe pvc` → check StorageClass exists/default → check provisioner Pods → check access mode/size feasibility → check `WaitForFirstConsumer` (needs a scheduled Pod).
- *You need shared read-write storage for 5 web Pods across nodes.* RWX backend (EFS/NFS/CephFS), not EBS.

**Production**
- *How do you protect prod data from accidental PVC deletion?* Reclaim policy `Retain`, backups/snapshots, RBAC restricting PVC delete, and finalizers/PV protection.
- *How do you grow a full database volume with zero downtime?* Ensure `allowVolumeExpansion: true`, patch the PVC size, rely on online filesystem resize (or restart the Pod if offline resize is required).

---

## 12. 🎓 TOP 50 QUESTIONS

### Fundamentals (15)
1. What is ephemeral vs persistent storage in Kubernetes?
2. What is an `emptyDir` volume and when is it deleted?
3. How do containers in a Pod share an `emptyDir`?
4. What is `emptyDir` `medium: Memory` and its cost?
5. What is a `hostPath` volume and its main risk?
6. Define a PersistentVolume.
7. Define a PersistentVolumeClaim.
8. What is the relationship between a PV and a PVC (cardinality)?
9. What is a StorageClass?
10. What is dynamic provisioning?
11. List the four access modes and their meanings.
12. What's the difference between RWO and RWOP?
13. What reclaim policies exist and what does each do?
14. What is the default reclaim policy for dynamically provisioned PVs?
15. What does `volumeBindingMode: WaitForFirstConsumer` do?

### Practical (10)
16. Write a PVC that requests 5Gi RWO from the default StorageClass.
17. How do you find which PV a PVC is bound to?
18. How do you make a StorageClass the cluster default?
19. How do you resize a PVC, and what must the StorageClass allow?
20. Can you shrink a PVC? What's the alternative?
21. How do you mount a PVC into a Pod?
22. How do you change a live PV's reclaim policy to `Retain`?
23. How do you inspect why a PVC won't bind?
24. How do you list all PVCs across all namespaces?
25. How do you create a `hostPath` PV for a single-node lab?

### Scenario (10)
26. A Pod loses data on reschedule — what volume type was likely used and how do you fix it?
27. You need shared RW storage across nodes — which access mode and backend?
28. A zonal disk Pod won't schedule to the disk's zone — what binding mode fixes it?
29. You must guarantee only one Pod ever writes to a volume — which access mode?
30. Prod DB needs protection from accidental PVC deletion — what reclaim policy + controls?
31. Two PVCs need to share one PV — is that possible? Why/why not?
32. Migrating from `emptyDir` to durable storage for a cache that must survive restarts — plan it.
33. You imported a legacy NFS export — static or dynamic PV? Show the approach.
34. A StorageClass has `allowVolumeExpansion: false` but you need more space — options?
35. You deleted a PVC with `Retain` — how do you recover and reuse the data?

### Troubleshooting (10)
36. PVC stuck `Pending` — diagnostic steps?
37. Pod stuck `ContainerCreating` with `FailedMount` — causes and fixes?
38. `Multi-Attach error` on an RWO volume — what's happening?
39. PVC `Pending` because RWX requested on EBS — how do you confirm and fix?
40. No default StorageClass — symptom and fix?
41. PV shows `Released` and won't rebind — why and what to do?
42. Disk full inside the Pod despite a large PVC — what to check?
43. Volume mounts but appears empty — possible causes (wrong subPath, fresh disk)?
44. CSI driver Pods crashing — how does it affect provisioning and how to detect?
45. Data appears on one node but not another for the same `hostPath` — explain.

### Interview (5)
46. Explain the full lifecycle of a dynamically provisioned volume from PVC creation to disk deletion.
47. Why is RWO "per node" and not "per Pod," and how does RWOP change that?
48. Compare `Retain`, `Delete`, and (deprecated) `Recycle`.
49. How would you architect storage for a multi-AZ stateful service?
50. What changes when you move from a Deployment+PVC to a StatefulSet+volumeClaimTemplates?

---

## 13. FREE RESOURCES

| Resource | Type | Focus |
|---|---|---|
| kubernetes.io/docs/concepts/storage/volumes | Docs | All volume types |
| kubernetes.io/docs/concepts/storage/persistent-volumes | Docs | PV/PVC lifecycle |
| kubernetes.io/docs/concepts/storage/storage-classes | Docs | Dynamic provisioning |
| kubernetes.io/docs/concepts/storage/dynamic-provisioning | Docs | Provisioner flow |
| KodeKloud CKA Storage labs | Practice | Hands-on PV/PVC |
| "Kubernetes Storage Explained" — TechWorld with Nana (YouTube) | Video | Visual intro |
| killercoda.com Kubernetes scenarios | Practice | Free interactive labs |

**Docs reading plan (in order)**
1. Volumes → Persistent Volumes → Storage Classes → Dynamic Provisioning.
2. Then the CSI overview (skim) and Volume Expansion.

**Must-Read:** PV/PVC lifecycle section (binding, phases, reclaim).
**Must-Watch:** Nana's storage video for the mental model.
**Must-Practice:** Labs 2 and 3 above until you can write them from memory.
**Must-Memorize:** Access modes table + reclaim policies + the binding match criteria.

**Highest-ROI:** The PV/PVC/StorageClass binding model — it explains the overwhelming majority of real storage incidents and interview questions.

---

## 14. NEXT STEPS

**Active recall (do this now, no notes):**
1. From memory, write a PVC YAML and the Pod that mounts it.
2. Explain out loud: what happens to the disk when you delete a PVC under `Delete` vs `Retain`.
3. State which backend you'd choose for RWX and why EBS can't do it.
4. Diagnose, from memory, the three top reasons a PVC stays `Pending`.

If you can do all four without peeking, you've internalized Day 3.

➡️ **Continue to Day 4** — StatefulSets, Headless Services, and `volumeClaimTemplates`: where today's storage knowledge meets ordered, identity-stable stateful workloads.
