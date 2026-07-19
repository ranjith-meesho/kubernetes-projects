# Kubernetes One-Page Cheat Sheet

## Object Hierarchy

```
Namespace
 └─ Deployment ─────────► ReplicaSet ─────────► Pod(s) ─────► Container(s)
      (rollout mgmt)        (replica count)      (net/storage    (app process)
                                                    unit)
 └─ StatefulSet ─────────► Pod(s) (stable identity, ordered)
 └─ DaemonSet ───────────► Pod (one per Node)
 └─ Job/CronJob ─────────► Pod (run-to-completion / scheduled)

Service ──(selector: labels)──► Pod(s)
Ingress ──(routes host/path)──► Service ──► Pod(s)
ConfigMap / Secret ──(mounted as env or volume)──► Pod
PVC ──(binds to)──► PV ──(backed by)──► StorageClass/disk
HPA ──(watches metrics)──► Deployment/StatefulSet (scales replicas)
ServiceAccount + Role/ClusterRole + RoleBinding/ClusterRoleBinding ──► RBAC on API objects
```

## Core Objects — One-Line Purpose

| Object | Purpose |
|---|---|
| **Pod** | Smallest deployable unit; one or more containers sharing network/storage. |
| **ReplicaSet** | Ensures N identical Pod replicas are running at all times. |
| **Deployment** | Manages ReplicaSets to provide declarative updates, rollouts, rollbacks. |
| **Service** | Stable virtual IP/DNS name that load-balances traffic to a set of Pods. |
| **Ingress** | HTTP(S) routing rules (host/path) into Services from outside the cluster. |
| **ConfigMap** | Non-secret key-value config injected into Pods as env vars or files. |
| **Secret** | Base64-encoded sensitive data (passwords, tokens, certs) injected into Pods. |
| **PV** | Cluster-wide storage resource provisioned by admin/CSI driver. |
| **PVC** | A Pod's request/claim for storage, bound to a matching PV. |
| **Job** | Runs Pod(s) to completion for a one-off/batch task. |
| **CronJob** | Schedules Jobs to run on a time-based (cron) schedule. |
| **HPA** | Horizontal Pod Autoscaler — scales replica count based on CPU/memory/custom metrics. |
| **Namespace** | Virtual cluster / logical partition for isolating resources and names. |
| **Role / RoleBinding** | Namespace-scoped RBAC permission set / grant of that permission to a subject. |

## Most-Used kubectl Verbs

| Verb | Use |
|---|---|
| `get` | List resources |
| `describe` | Detailed state + events for one resource |
| `apply -f` | Create/update from declarative YAML (idempotent) |
| `create` | Create imperatively |
| `delete` | Remove a resource |
| `edit` | Live-edit a resource in default editor |
| `logs` | Container stdout/stderr |
| `exec -it` | Shell into a running container |
| `port-forward` | Tunnel local port to Pod/Service port |
| `rollout status/undo/history` | Manage Deployment rollouts |
| `scale` | Change replica count |
| `apply --dry-run=client -o yaml` | Preview/generate YAML |
| `top` | Live CPU/memory usage (needs metrics-server) |
| `explain` | Field-level docs for a resource kind |

## Mental Model

```
   kubectl  ──REST──►  API Server  ──validates/persists──►  etcd
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
        Scheduler     Controller Mgr    kubelet (on each Node)
        (assigns      (Deploy/RS/Job    (starts containers via
         Pod→Node)     reconcile loops)  CRI, reports status)
                                              │
                                        kube-proxy / CNI
                                        (Service routing,
                                         Pod networking)
```

**Rule of thumb:** You declare *desired state* → controllers loop to reconcile
*actual state* → kubelet/kube-proxy make it real on each Node.
