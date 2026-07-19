# Hour 12: Jobs, CronJobs

## 1. Explanation (Simple → Technical)

**Simple version:** A **Deployment** is like a **shift worker** who is always supposed to be on duty. If they go home sick, Kubernetes immediately hires a replacement so the shift is never empty — the job (in the everyday sense) never "finishes," it just keeps running forever.

A **Job** is like a **contractor** you hire to do one specific task — say, paint a room. Once the room is painted, the contractor is done. You don't want Kubernetes to keep "restarting" them to repaint an already-painted room. Success means "stop," not "restart."

A **CronJob** is that same contractor, but **auto-hired every night at 2am** to redo the same task — like a cleaning crew scheduled to show up on a recurring timer, do the work, and leave, without you having to manually call them each time.

**Technical version:**
- A **Job** creates one or more Pods and ensures a specified number of them **terminate successfully** (exit code 0). Unlike a Deployment's ReplicaSet, which treats a terminated Pod as a failure to be replaced indefinitely, a Job treats successful termination as the *goal state*.
- Key Job fields:
  - `spec.completions` — how many successful Pod completions are needed (default 1).
  - `spec.parallelism` — how many Pods can run at once working toward those completions (batch/parallel processing).
  - `spec.backoffLimit` — how many times to retry a failed Pod before marking the Job as failed (default 6). Without bounding this, a permanently broken Job can retry forever, burning cluster resources.
  - `spec.activeDeadlineSeconds` — a wall-clock timeout for the whole Job.
  - `spec.ttlSecondsAfterFinished` — auto-delete the Job (and its Pods) N seconds after it finishes, so completed Jobs don't pile up.
- Job **patterns**:
  - Single Pod, run to completion (e.g., a one-off DB migration script).
  - Fixed completion count (`completions: 5`) — run the same task 5 times total.
  - Parallel work queue (`parallelism: 3`, `completions: 10`) — up to 3 Pods running concurrently until 10 total succeed (classic batch-processing fan-out).
- A **CronJob** is a higher-level controller that creates a new **Job** object on each tick of a **cron schedule** (`spec.schedule`, standard 5-field cron syntax: `minute hour day-of-month month day-of-week`).
- Key CronJob fields:
  - `spec.schedule` — e.g., `"0 2 * * *"` = every day at 2:00 AM.
  - `spec.concurrencyPolicy` — controls what happens if a previous run is still active when the next tick fires:
    - `Allow` (default) — let them run concurrently.
    - `Forbid` — skip the new run if the old one is still running.
    - `Replace` — kill the old run and start the new one.
  - `spec.successfulJobsHistoryLimit` / `spec.failedJobsHistoryLimit` — how many completed/failed Job objects to keep around for inspection (defaults 3 and 1). Without these, old Jobs accumulate and clutter `kubectl get jobs`.
  - `spec.startingDeadlineSeconds` — how late a missed schedule can still be started (e.g., if the control plane was down).
  - `spec.suspend` — pause the CronJob without deleting it.
- **Deployment vs Job — the critical distinction:** Deployments are for **long-running services** (web servers, APIs) that should *never* naturally exit — if the container exits, that's treated as a crash and it's restarted. Jobs are for **run-to-completion work** (batch jobs, migrations, reports, cleanup scripts) where exiting with code 0 is *success*, and the controller should stop, not restart.

## 2. Diagram

```
DEPLOYMENT (long-running service)              JOB (run-to-completion task)
┌─────────────────────────────┐                ┌─────────────────────────────┐
│  ReplicaSet keeps N Pods     │                │  Job creates Pod(s), waits   │
│  ALIVE forever                │                │  for them to SUCCEED, then   │
│                                │                │  STOPS creating new ones     │
│   Pod ──crash──> restart      │                │   Pod ──exit 0──> DONE ✔     │
│   Pod ──crash──> restart      │                │   Pod ──exit 1──> retry      │
│   (never intentionally exits) │                │        (up to backoffLimit)  │
└─────────────────────────────┘                └─────────────────────────────┘


CRONJOB (scheduled Job factory)

   schedule: "*/1 * * * *"   (every 1 minute)

   tick 12:00 ───► creates Job "report-28471523" ───► Pod runs ───► exits 0 ───► Job: Complete
   tick 12:01 ───► creates Job "report-28471524" ───► Pod runs ───► exits 0 ───► Job: Complete
   tick 12:02 ───► creates Job "report-28471525" ───► Pod runs ───► exits 0 ───► Job: Complete

   concurrencyPolicy: Forbid
        ┌───────────────┐   still running when   ┌───────────────┐
        │ Job (12:00)   │──── next tick fires ──►│ tick SKIPPED  │
        └───────────────┘                        └───────────────┘

   concurrencyPolicy: Allow (default)              concurrencyPolicy: Replace
        Job(12:00) ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                Job(12:00) ▓▓▓▓▓▓✗ (killed)
        Job(12:01)       ▓▓▓▓▓▓▓▓▓▓▓▓▓▓             Job(12:01)       ▓▓▓▓▓▓▓▓▓▓▓▓ (new, runs)
        (both run side by side)                     (old replaced by new)
```

## 3. Real-World Example

**Nightly database backup (CronJob):** An e-commerce platform needs its orders database backed up every night. A CronJob with `schedule: "0 2 * * *"` spins up a Pod at 2am that runs `pg_dump`, uploads the file to S3, and exits. `concurrencyPolicy: Forbid` ensures that if last night's backup somehow ran long, tonight's doesn't start on top of it and corrupt output. `successfulJobsHistoryLimit: 7` keeps a week's worth of Job records visible for auditing without unbounded growth.

**One-time data migration (Job):** When Meesho-style platforms launch a new feature — say, adding a `loyalty_tier` column that needs to be backfilled for 50 million existing users — engineers don't want a Deployment (which would "crash-loop" once the migration script finishes and exits). Instead, they run a **Job** with `parallelism: 5, completions: 20` that splits the user table into 20 shards, processes 5 at a time, and the Job is marked `Complete` once all 20 shards succeed. The migration runs exactly once and then is done — no restart, no crash-loop.

## 4. Hands-On Lab

**Goal:** Run a one-off Job to completion, then schedule a recurring CronJob and watch it spawn Jobs automatically.

### Step 1 — A simple Job

```yaml
# job-pi.yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: compute-pi
spec:
  backoffLimit: 3
  ttlSecondsAfterFinished: 120
  template:
    spec:
      containers:
      - name: pi
        image: perl:5.34
        command: ["perl", "-Mbignum=bpi", "-wle", "print bpi(2000)"]
      restartPolicy: Never
```

```bash
kubectl apply -f job-pi.yaml
kubectl get jobs
```

**Expected output (while running, then once complete):**
```
NAME         COMPLETIONS   DURATION   AGE
compute-pi   0/1           3s         3s
...
NAME         COMPLETIONS   DURATION   AGE
compute-pi   1/1           7s         12s
```

```bash
kubectl get pods --selector=job-name=compute-pi
kubectl logs -l job-name=compute-pi
```

**Expected output:** the Pod shows `STATUS: Completed`, and `kubectl logs` prints the first 2000 digits of pi.

### Step 2 — A CronJob (schedule every 1 minute, for fast feedback while testing)

```yaml
# cronjob-report.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: minute-report
spec:
  schedule: "*/1 * * * *"       # every 1 minute — cron syntax: min hour day month weekday
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 1
  jobTemplate:
    spec:
      backoffLimit: 2
      template:
        spec:
          containers:
          - name: report
            image: busybox
            command: ["sh", "-c", "echo Report generated at $(date)"]
          restartPolicy: OnFailure
```

**Cron schedule syntax reminder:**
```
┌───────────── minute (0 - 59)
│ ┌─────────── hour (0 - 23)
│ │ ┌───────── day of month (1 - 31)
│ │ │ ┌─────── month (1 - 12)
│ │ │ │ ┌───── day of week (0 - 6) (Sunday=0)
│ │ │ │ │
* * * * *

"*/1 * * * *"  → every 1 minute
"0 2 * * *"    → every day at 2:00 AM
"0 0 * * 0"    → every Sunday at midnight
```

```bash
kubectl apply -f cronjob-report.yaml
kubectl get cronjob minute-report
kubectl get jobs --watch
```

**Expected output over a few minutes:**
```
NAME             SCHEDULE      SUSPEND   ACTIVE   LAST SCHEDULE   AGE
minute-report    */1 * * * *   False     0        14s             1m

NAME                        COMPLETIONS   DURATION   AGE
minute-report-28471523      1/1           2s         58s
minute-report-28471524      1/1           2s         2s   <- new one just spawned
```

```bash
kubectl logs job/minute-report-28471524
# Report generated at Thu Jul 2 02:03:00 UTC 2026
```

**Cleanup:**
```bash
kubectl delete cronjob minute-report
kubectl delete job compute-pi
```

## 5. Common Mistakes

1. **Using a Deployment for a batch task.** A Deployment expects its container to run forever; when the script finishes and exits 0, Kubernetes sees an "unexpectedly terminated" Pod and restarts it — the task re-runs in an infinite crash-loop-like cycle, even though nothing is actually broken. Batch/one-off work belongs in a Job, not a Deployment.
2. **Not setting `backoffLimit`.** Leaving it at the default (or forgetting about it) on a Job whose script has a bug means Kubernetes will retry it repeatedly, chewing through cluster resources and delaying visibility that something is broken. Set a sane `backoffLimit` and `activeDeadlineSeconds`.
3. **Misconfigured `concurrencyPolicy`.** Leaving the default `Allow` on a CronJob that isn't safe to run concurrently (e.g., two backup Jobs writing to the same file, or two migration Jobs racing on the same rows) causes overlapping runs and data corruption. Use `Forbid` (skip if still running) or `Replace` (kill and restart) depending on the task's semantics.
4. **Forgetting `successfulJobsHistoryLimit` / `failedJobsHistoryLimit`.** Without bounding history, every scheduled tick leaves behind a completed Job (and its Pod) forever, cluttering `kubectl get jobs/pods` and slowly consuming etcd storage. Also consider `ttlSecondsAfterFinished` on the Job template for automatic cleanup.
5. **Setting `restartPolicy: Always` on a Job's Pod template.** Jobs only support `Never` or `OnFailure` — `Always` is invalid for Job Pods, because a Job needs to know when a Pod has "finished" versus should be restarted in place.
6. **Assuming a missed CronJob run will "catch up" automatically.** If the CronJob controller was down (cluster maintenance, etc.) past `startingDeadlineSeconds`, that scheduled run is simply skipped, not queued — don't rely on CronJobs for guaranteed-exactly-once execution without additional idempotency/monitoring.

## 6. Interview Questions (with brief answers)

1. **Job vs Deployment — what's the core difference?** — A Deployment manages Pods that are expected to run indefinitely and restarts them on *any* termination (success or failure) to maintain a steady-state count. A Job manages Pods that are expected to *terminate successfully*; success stops the Job, and only failures are retried (up to `backoffLimit`).
2. **How would you schedule a nightly cleanup task in Kubernetes?** — Use a CronJob with `spec.schedule` set to the desired cron expression (e.g., `"0 3 * * *"` for 3am daily), wrap the cleanup logic in the `jobTemplate`, set `concurrencyPolicy: Forbid` if overlapping runs would be unsafe, and set history limits so completed/failed Job objects don't accumulate.
3. **What happens if a Job's Pod fails repeatedly?** — Kubernetes retries creating a new Pod for the Job, counting failures, until `backoffLimit` is reached — after that the Job is marked `Failed` and stops retrying (it does not retry forever).
4. **What's the difference between `completions` and `parallelism` on a Job?** — `completions` is the total number of successful Pod completions required for the Job to be considered done; `parallelism` is the max number of Pods allowed to run concurrently while working toward that total. E.g., `completions: 10, parallelism: 3` processes 10 units of work, 3 at a time.
5. **How do you prevent a CronJob from running two overlapping instances?** — Set `spec.concurrencyPolicy: Forbid`, which causes the CronJob controller to skip starting a new Job if the previous one is still active; alternatively `Replace` cancels the old run and starts the new one immediately.

## 7. Quiz (50 Questions)

**True/False:**
1. A Job is designed to run a Pod until it terminates successfully, then stop. (T)
2. A Deployment will restart a Pod that exits with code 0. (T)
3. `backoffLimit` controls how many times a failed Job Pod is retried before the Job is marked Failed. (T)
4. `restartPolicy: Always` is a valid setting for a Job's Pod template. (F)
5. A CronJob directly creates Pods without going through a Job. (F)
6. `concurrencyPolicy: Forbid` allows overlapping runs of the same CronJob. (F)
7. `concurrencyPolicy: Replace` kills the currently running Job when a new scheduled tick fires. (T)
8. `successfulJobsHistoryLimit` controls how many completed Job objects are retained. (T)
9. Cron schedules in Kubernetes use the standard 5-field cron syntax. (T)
10. `parallelism` controls the total number of successful completions required. (F)
11. `ttlSecondsAfterFinished` can be used to auto-delete a Job after it finishes. (T)
12. A CronJob guarantees a missed run (due to controller downtime) will always execute later. (F)
13. Jobs are appropriate for long-running web servers. (F)
14. `activeDeadlineSeconds` sets a wall-clock timeout for the entire Job. (T)
15. `startingDeadlineSeconds` controls how late a missed CronJob schedule can still be started. (T)

**Multiple Choice:**
16. Which controller is best suited for a one-time database migration script? a) Deployment b) DaemonSet c) Job d) StatefulSet → (c)
17. Which field limits retries of a failing Job Pod? a) `parallelism` b) `backoffLimit` c) `completions` d) `replicas` → (b)
18. Which CronJob field prevents overlapping runs? a) `schedule` b) `concurrencyPolicy: Forbid` c) `suspend` d) `parallelism` → (b)
19. What cron expression means "every day at 2 AM"? a) `2 0 * * *` b) `0 2 * * *` c) `* 2 0 * *` d) `0 0 2 * *` → (b)
20. What valid `restartPolicy` values exist for a Job's Pod? a) Always, Never b) Never, OnFailure c) Always, OnFailure d) Always only → (b)
21. What happens to a Deployment's Pod when it exits 0 unexpectedly? a) Nothing b) Deployment is deleted c) ReplicaSet restarts it d) Job is created → (c)
22. Which field controls how many Pods run concurrently while working toward Job completions? a) `completions` b) `parallelism` c) `backoffLimit` d) `replicas` → (b)
23. Which resource "owns" the Jobs created by a CronJob? a) The Deployment b) The CronJob controller c) The kubelet d) The ReplicaSet → (b)
24. `concurrencyPolicy: Allow` means: a) skip new run if old is active b) kill old run c) let both run simultaneously d) pause the schedule → (c)
25. Which field auto-deletes a finished Job after N seconds? a) `activeDeadlineSeconds` b) `ttlSecondsAfterFinished` c) `startingDeadlineSeconds` d) `terminationGracePeriodSeconds` → (b)

**Short Answer:**
26. In one sentence, explain the difference between a Job and a Deployment.
27. What does the `completions` field on a Job spec represent?
28. What does the `parallelism` field on a Job spec represent?
29. Why must a Job's Pod template avoid `restartPolicy: Always`?
30. What is the purpose of `backoffLimit`?
31. What cron field position represents "day of week," and what values does it accept?
32. What command lists all CronJobs in the current namespace?
33. What command shows the Jobs spawned by a specific CronJob over time?
34. What happens when `concurrencyPolicy` is set to `Replace`?
35. Why would you set `successfulJobsHistoryLimit` to a small number like 3?

**Scenario-Based:**
36. Your team deployed a data-backfill script as a Deployment, and it keeps restarting every few seconds even though logs show it completing successfully each time. What's wrong, and how do you fix it?
37. You need to back up a database every night at 2am, and a backup taking longer than 24 hours (unlikely but possible) should never overlap with the next one. Which CronJob fields do you configure and how?
38. A CronJob is meant to run every 5 minutes, but you notice in `kubectl get jobs` that dozens of old completed Jobs from last week are still hanging around. What field did the team forget to set?
39. You want to process 100 image-resizing tasks, with at most 10 running at once, and the whole thing considered done only once all 100 succeed. How do you configure `completions` and `parallelism`?
40. A CronJob's Job keeps failing and retrying seemingly forever, consuming cluster CPU. What field was likely misconfigured or left at a bad value, and how do you cap the damage?

**Fill in the Blank:**
41. A ______ ensures a Pod runs to successful completion, while a ______ ensures Pods run continuously forever.
42. The cron schedule field order is: minute, ______, day of month, ______, day of week.
43. ______ controls whether overlapping CronJob runs are allowed, forbidden, or replaced.
44. The field ______ bounds how many times a failing Job Pod is retried.
45. ______ is the field that automatically cleans up a finished Job after a set number of seconds.

**Conceptual Deep-Dive:**
46. Why does Kubernetes treat "successful exit" differently for Jobs than for Deployments at a fundamental controller-logic level?
47. Explain how a CronJob, Job, and Pod relate to each other in a three-level ownership hierarchy.
48. Why is idempotency important for the task inside a Job or CronJob, given retries and possible `Replace`/overlap behavior?
49. What risk does `concurrencyPolicy: Allow` introduce for a task that writes to a shared file or database row, and how would you mitigate it without changing the policy?
50. How does `startingDeadlineSeconds` interact with a CronJob that was suspended (`spec.suspend: true`) and then resumed hours later?

## 8. Hour 12 Summary (One-Page Takeaway)

| Concept | Key Point |
|---|---|
| **Job** | Runs Pod(s) to successful completion, then stops; retries failures up to `backoffLimit` |
| **completions / parallelism** | Total successes needed vs. how many Pods run concurrently toward that total |
| **CronJob** | A Job scheduled on a cron-style timer (`spec.schedule`); creates a new Job on each tick |
| **concurrencyPolicy** | `Allow` (run side by side), `Forbid` (skip if still running), `Replace` (kill old, start new) |
| **History limits** | `successfulJobsHistoryLimit` / `failedJobsHistoryLimit` prevent unbounded Job accumulation |
| **Deployment vs Job** | Deployment = always-on service (exit = crash, gets restarted); Job = run-to-completion task (exit 0 = done) |
| **Mental model** | Deployment = shift worker always on duty; Job = one-off contractor; CronJob = contractor auto-hired every night at 2am |

**Mnemonic:** *"CRAB"* — **C**ompletions define the goal, **R**etries are bounded by backoffLimit, **A**llow/Forbid/Replace govern overlap, **B**ackoff and history limits keep things clean.

## 9. Free Resources & Mini-Project

**Free Resources:**
- [Kubernetes Official Docs — Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/job/)
- [Kubernetes Official Docs — CronJob](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)
- [crontab.guru](https://crontab.guru/) — interactive cron syntax playground, great for practicing schedule expressions before pasting them into a CronJob spec
- [KodeKloud Kubernetes Jobs & CronJobs labs](https://kodekloud.com/) — hands-on practice environment
- YouTube: "TechWorld with Nana — Kubernetes Jobs & CronJobs Explained" (free, visual walkthrough)

**Mini-Project for Hour 12 (30 min):**
- Create a `PersistentVolumeClaim` (or use an `emptyDir` for a quicker version) mounted at `/data`.
- Build a CronJob named `heartbeat` with `schedule: "*/5 * * * *"` (every 5 minutes) whose Pod runs a command like `sh -c "date >> /data/heartbeat.log"`, mounting the same volume.
- Let it run for 15–20 minutes, then inspect the shared volume (e.g., via a temporary debug Pod mounting the same PVC, or `kubectl exec` into the CronJob's most recent Pod before it's cleaned up) and confirm multiple timestamp lines have been appended, each ~5 minutes apart.
- Bonus: set `concurrencyPolicy: Forbid` and `successfulJobsHistoryLimit: 2`, then verify with `kubectl get jobs` that old Job objects get pruned automatically.
