# Day 4 — Metrics Fundamentals + The Four Golden Signals

You've spent three days building your foundation — telling observability apart from monitoring, mapping the landscape, and getting fluent with logs. Today you flip to metrics: the cheapest, most aggregatable signal you have, and the one that answers "is the system healthy right now?" faster than anything else. Nail the Four Golden Signals today and you'll have the mental model that every dashboard, alert, and SLO in this roadmap builds on.

## 🎯 Learning objectives

- [ ] Define a metric/time-series and explain what makes it structurally different from a log line
- [ ] Distinguish counter, gauge, histogram, and summary — and pick the right one for a given measurement
- [ ] Explain labels/dimensions and how they turn one metric name into many time-series
- [ ] State the Four Golden Signals (latency, traffic, errors, saturation) and why each matters
- [ ] Describe the RED method and the USE method, and know when to reach for each
- [ ] Explain why metrics are cheap to store and query at scale compared to logs
- [ ] Identify, for a real system, at least one concrete metric per Golden Signal

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5 min | Closed-book: write down yesterday's tracing basics (span, trace ID, parent/child) from memory, then check Day 3 notes for gaps |
| Learn | 20 min | Read/watch on metric types (counter/gauge/histogram/summary), labels, and the Four Golden Signals / RED / USE |
| Do | 25 min | Hands-on project below — run Prometheus's own `/metrics` endpoint or `node_exporter` locally and classify real metrics by type and Golden Signal |
| Reinforce | 10 min | Active recall — attempt as many of the 50 questions as you can from memory before checking answers |

## 📚 Free resources

- **Prometheus docs — "Metric Types"** — The canonical, precise definitions of counter, gauge, histogram, and summary; short and authoritative. (type: docs)
- **Prometheus docs — "Data model"** — Explains time-series, labels, and how a metric name + label set forms a unique series. (type: docs)
- **Google SRE Book, Chapter 6 "Monitoring Distributed Systems" (sre.google/books)** — Origin of the Four Golden Signals, written by the people who coined the term. (type: book)
- **Brendan Gregg's site — "The USE Method"** — The original writeup of Utilization/Saturation/Errors for resource-focused analysis, by the engineer who created it. (type: blog)
- **Weaveworks blog — "RED Method: Key Metrics for Microservices Architecture"** — The origin post for Rate/Errors/Duration, aimed squarely at service-level metrics. (type: blog)
- **Grafana Labs blog — "Histograms and summaries" / Prometheus histogram explainer** — Clarifies the trickiest part of today's material: how histograms differ from summaries and why bucket choice matters. (type: blog)
- **Prometheus docs — "Instrumenting a Go application" / client library docs** — Shows metric types used in real code, useful even if you don't write Go. (type: docs)
- **PromLabs / Julius Volz YouTube — "Prometheus metric types explained"** — Short, visual walkthroughs of counters vs gauges vs histograms if reading isn't clicking. (type: video)
- **node_exporter GitHub README** — Real-world catalog of gauges and counters exposing actual machine metrics you'll use in today's hands-on project. (type: docs)

## 🛠️ Hands-on project (free, ~25 min)

**Goal:** See real metric types, labels, and Golden Signals in raw Prometheus exposition format — no cluster needed.

1. Install Docker if you don't have it, then run: `docker run -d --name node_exporter -p 9100:9100 prom/node-exporter`
2. Open `http://localhost:9100/metrics` in your browser (or `curl http://localhost:9100/metrics`). This is the raw scrape target Prometheus would pull from.
3. Find and label at least 5 metrics by type. Look for `# TYPE` comments — spot one `counter` (e.g. `node_cpu_seconds_total`), one `gauge` (e.g. `node_memory_MemAvailable_bytes`), and one `histogram` if present (e.g. some `_bucket`/`_sum`/`_count` trio).
4. Pick one metric with labels (e.g. `node_cpu_seconds_total{cpu="0",mode="idle"}`) and write down: what is the metric name, what are the label keys, what does each unique label combination represent as a separate time-series?
5. Map at least one metric from this output to each of the Four Golden Signals: Traffic (e.g. network bytes received), Saturation (e.g. CPU or memory usage), Errors (e.g. any `_errors_total` counter you can find), Latency (node_exporter mostly won't have this — note that gap and think about why: it's a host exporter, not a service, so latency isn't its job).
6. Bonus check: run `curl -s http://localhost:9100/metrics | grep "^# TYPE" | wc -l` to count how many distinct metrics this one exporter exposes, and skim a few names you don't recognize.

**Expected outcome:** A short written list — 5+ metrics, each tagged with its type, its labels, and (where applicable) which Golden Signal it belongs to.

**Stretch goal:** Also run `docker run -d --name cadvisor -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock:ro gcr.io/cadvisor/cadvisor` and compare — cAdvisor exposes container-level metrics including latency-adjacent histograms (`container_...`), giving you a fuller Golden Signals picture than node_exporter alone.

## 🧠 50 questions for active recall

**Q1. What is a metric, in the observability sense?**
> A metric is a numeric measurement of a system's behavior or state captured at a point in time, usually with a name, a value, and a timestamp. Unlike a log line, it discards the specific event context (no "what request," no free-text message) and keeps only the number — that's what makes it compact and cheap to aggregate.

**Q2. What is a time-series?**
> A time-series is a sequence of (timestamp, value) pairs for one uniquely identified metric — identified by its name plus its exact set of label key/value pairs. Every distinct label combination under a metric name is its own separate time-series, stored and queried independently.

**Q3. Why are metrics described as "cheap" compared to logs?**
> Metrics are pre-aggregated numbers with a small, bounded schema (name + labels + value + timestamp), so storing and querying a million data points costs far less in disk and CPU than storing a million full-text log lines. Logs carry unbounded free-form text and must often be indexed or grepped; metrics are stored in compact, purpose-built time-series formats (like Prometheus's TSDB) that compress extremely well because consecutive values in a series are often similar.

**Q4. What does "aggregatable" mean for metrics, and why does it matter operationally?**
> Aggregatable means many individual data points can be mathematically combined — summed, averaged, percentiled — cheaply and meaningfully, e.g. summing request counts across 500 pods into one number. It matters because at scale you rarely want per-instance detail in a dashboard; you want "total error rate across the fleet," and metrics are built to answer that fast, whereas aggregating logs requires parsing and computation over raw text.

**Q5. Name the four core Prometheus-style metric types.**
> Counter, gauge, histogram, and summary. Each models a different shape of measurement: ever-increasing counts, arbitrary up/down values, and two different ways of capturing distributions (histogram and summary).

**Q6. Define a counter.**
> A counter is a cumulative metric whose value only ever increases (or resets to zero on restart) — it never decreases while the process is running. It's used for counts of things that happened, like total HTTP requests served or total errors raised.

**Q7. Give two real-world examples of counters.**
> `http_requests_total` (count of requests handled since process start) and `node_cpu_seconds_total` (cumulative CPU time consumed). Both only go up; you derive rate of change (e.g. requests per second) by taking the difference over a time window, not by reading the raw value directly.

**Q8. Why don't you graph a raw counter value directly?**
> A raw counter is a monotonically increasing number since process start, so its absolute value is meaningless on its own (e.g. "47,382,910 requests" tells you nothing actionable) and it resets to zero on every restart, creating a misleading sawtooth. Instead you apply a rate function (like PromQL's `rate()`) over a time window to get a meaningful per-second rate that's comparable across time and instances.

**Q9. Define a gauge.**
> A gauge is a metric whose value can go up or down arbitrarily, representing a current state or snapshot rather than an accumulation. It's used for things like current memory usage, number of active connections, or queue depth.

**Q10. Give two real-world examples of gauges.**
> `node_memory_MemAvailable_bytes` (current free memory, rises and falls) and `queue_depth` (current number of items waiting, rises and falls with load). Both represent "what's true right now," not a running total.

**Q11. Compare counter vs gauge in one sentence each on what question they answer.**
> A counter answers "how many of these have happened in total (or since this window)?" A gauge answers "what is the value of this thing right now?" Mixing them up — e.g. treating a gauge like it only increases — leads to broken rate calculations and misleading graphs.

**Q12. Define a histogram as a metric type.**
> A histogram samples observations (like request durations) and counts them into a configurable set of buckets (e.g. ≤0.1s, ≤0.5s, ≤1s), while also exposing a running `_sum` of all observed values and a `_count` of total observations. From bucket counts you can calculate approximate quantiles (like p95 latency) server-side at query time, and buckets aggregate cleanly across instances.

**Q13. Define a summary as a metric type.**
> A summary also tracks a `_sum` and `_count` of observations, but instead of buckets it calculates configurable quantiles (e.g. p50, p90, p99) directly on the client side at scrape time. Unlike histograms, these pre-calculated quantiles cannot be meaningfully aggregated across multiple instances (you can't average two p99s and get a correct p99).

**Q14. Why can't you aggregate summary quantiles across instances, but you can aggregate histogram buckets?**
> A summary's quantile is already a computed statistic local to one process's observed data; combining quantiles from different instances mathematically doesn't produce the true global quantile. A histogram instead exposes raw counts per bucket, and counts are simple integers that sum correctly across instances — so you can add up bucket counts from every replica and then compute the true global quantile from the combined buckets.

**Q15. When would you choose a histogram over a summary?**
> Choose a histogram whenever you need to aggregate percentiles across multiple instances or dimensions (e.g. p99 latency across a whole fleet of pods) — which is the overwhelmingly common case in distributed systems. Choose a summary only when you need an exact client-side quantile for a single process and cross-instance aggregation isn't required, since summaries are more accurate per-instance but far less flexible.

**Q16. What is the tradeoff of choosing histogram bucket boundaries poorly?**
> If buckets are too coarse (e.g. only 0.5s and 5s) you lose resolution and can't distinguish a p95 of 0.6s from 4.9s. If buckets are too fine or too numerous, you increase cardinality and storage cost per instrumented call site. Good bucket boundaries are chosen around your actual SLO thresholds (e.g. if your latency target is 300ms, have a bucket boundary right around there).

**Q17. What are labels (dimensions) on a metric?**
> Labels are key-value pairs attached to a metric that let you slice one metric name into many distinct time-series — e.g. `http_requests_total{method="GET", status="500", handler="/checkout"}`. They turn a single counter into a queryable, filterable, groupable dataset without needing separate metric names for every variation.

**Q18. Why use labels instead of encoding dimensions into the metric name (e.g. `http_requests_get_500_total`)?**
> Encoding dimensions into names creates an explosion of unrelated metric names that can't be queried together with wildcards or aggregated with functions like `sum by (...)`. Labels let you keep one metric name and use query-time operators to slice, filter, or aggregate across any label combination flexibly.

**Q19. What is "cardinality" in the context of metric labels?**
> Cardinality is the number of unique time-series produced by a metric — the product of the number of distinct values across all its labels. A metric with labels `user_id` and `request_id` can explode into millions of series, because those values are effectively unbounded, unlike a `status_code` label with maybe 10 possible values.

**Q20. Why is high-cardinality labeling dangerous for a metrics system, even though it's fine for logs?**
> Each unique label combination creates a new independent time-series that the metrics backend must store and index in memory/disk indefinitely (or until it ages out), so unbounded label values (user IDs, request IDs, raw URLs) can blow up memory and query performance. Logs don't have this problem the same way because each log line is just an independent event record, not a persistent series that must be tracked over time.

**Q21. What are the Four Golden Signals?**
> Latency, traffic, errors, and saturation — a small set of signals proposed by Google's SRE book as the minimum you need to understand a user-facing system's health. The idea is that if you can only instrument four things, these four give you the most diagnostic value per unit of effort (the 80/20 of monitoring).

**Q22. Define "latency" as a Golden Signal, and note one subtlety.**
> Latency is the time it takes to service a request. The key subtlety is you must distinguish the latency of successful requests from the latency of failed requests — a fast error (e.g. immediate 500) can mask a real slowdown if you only look at overall average latency.

**Q23. Define "traffic" as a Golden Signal.**
> Traffic is a measure of how much demand is being placed on your system, expressed in a domain-appropriate unit — e.g. HTTP requests per second for a web service, or transactions per second for a database. It tells you the load context needed to interpret every other signal (an error rate of 1% means something very different at 10 req/s vs 10,000 req/s).

**Q24. Define "errors" as a Golden Signal.**
> Errors is the rate of requests that fail, whether through explicit failures (HTTP 500s), implicit failures (a 200 response with the wrong content), or policy failures (e.g. exceeding a response-time SLA counted as an error even if the response eventually succeeded). The definition of "error" should be tied to what actually breaks the user's experience, not just what the process logs as an exception.

**Q25. Define "saturation" as a Golden Signal.**
> Saturation measures how "full" your system is — how close a constrained resource (CPU, memory, disk I/O, connection pool, queue) is to its capacity limit. High saturation is a leading indicator of future latency and errors, so it lets you catch problems before users feel them.

**Q26. Why does the SRE book emphasize these four specifically, rather than "instrument everything"?**
> Because in the real world, engineering time and system complexity are limited, and these four signals give the highest diagnostic return for a user-facing service: they let you quickly answer "is it slow, is it overloaded, is it broken, and is it running out of headroom?" It's a direct application of the 80/20 rule — most incidents show up clearly in at least one of these four before you need deeper instrumentation.

**Q27. What is the RED method?**
> RED stands for Rate, Errors, Duration — a metrics framework proposed for monitoring request-driven services (proposed by Tom Wilkie, popularized via Weaveworks). Rate is requests per second, Errors is the rate of failed requests, and Duration is the distribution of time each request takes — essentially a practical, service-focused subset mapping closely onto three of the Four Golden Signals (traffic, errors, latency).

**Q28. What is the USE method?**
> USE stands for Utilization, Saturation, Errors — a framework by Brendan Gregg aimed at analyzing physical and virtual resources (CPU, memory, disk, network) rather than services. Utilization is percent of time the resource is busy, Saturation is the extra work queued waiting for the resource, and Errors is the count of error events for that resource.

**Q29. Compare RED and USE: what's the difference in what each is "about"?**
> RED is service-centric — it looks outward from the perspective of requests flowing through a service (how many, how fast, how many failed). USE is resource-centric — it looks inward at the infrastructure components (CPU, disks, network interfaces) a service depends on. In practice you use RED for your application's own services and USE for the hosts/containers/resources underneath them.

**Q30. How do RED, USE, and the Four Golden Signals overlap?**
> RED's Rate maps to Golden Signals' Traffic, RED's Errors maps directly to Golden Signals' Errors, and RED's Duration maps to Latency — RED essentially omits Saturation because it's not naturally a per-request concept. USE's Saturation and Errors overlap directly with the Golden Signals' terms, while USE's Utilization is a resource-level precursor to Saturation. Golden Signals is the umbrella concept; RED and USE are two practical checklists for applying it to services vs. resources respectively.

**Q31. Give a scenario where you'd reach for USE instead of RED.**
> You're debugging why a database node is slow, and you want to check whether the disk, CPU, or network is the bottleneck underneath the service. That's a resource-level question about utilization/saturation/errors of physical components, not about request rate through an API, so USE (disk I/O utilization, CPU saturation, disk error counts) is the right lens.

**Q32. Give a scenario where you'd reach for RED instead of USE.**
> You want a single dashboard row per microservice showing whether each one is healthy from a caller's perspective — request rate, error rate, and p95 latency. That's inherently service-facing, so RED is the natural fit, and you'd drill into USE metrics on the underlying hosts only if RED flags a problem.

**Q33. Why is "traffic" necessary context for interpreting "errors"?**
> An error rate alone (e.g. "50 errors in the last minute") is meaningless without knowing the denominator — 50 errors out of 100 requests is a crisis, 50 errors out of 500,000 requests may be within normal noise. You almost always want errors expressed as a percentage or ratio of traffic, not an absolute count.

**Q34. Why is saturation described as a "leading indicator" while errors and latency are often "lagging indicators"?**
> Saturation (e.g. queue depth climbing, CPU nearing 100%) tends to rise before the system actually starts failing or slowing down for users, giving you a chance to act proactively. Errors and elevated latency usually only appear once the problem has already started affecting real requests, so by the time you see them, users are already impacted.

**Q35. What is the difference between a histogram's `_bucket`, `_sum`, and `_count` series?**
> `_bucket{le="X"}` gives the cumulative count of observations less than or equal to X for each configured boundary. `_sum` is the running total of all observed values (e.g. total seconds across all requests), and `_count` is the total number of observations — together `_sum / _count` gives you an average, while the buckets let you compute approximate percentiles.

**Q36. If `http_request_duration_seconds_count` is a counter-like series that resets on deploy, what happens to a naive average-latency dashboard across a rolling deploy?**
> Right after a restart the counter (and `_sum`) resets to zero, so a query computing average latency as `_sum/_count` over a window that spans the restart will show an artificial dip or spike because it's dividing partial post-restart data. This is why PromQL functions like `rate()` are designed to handle counter resets automatically by detecting decreases and treating them as a reset rather than a negative rate.

**Q37. Predict the output: you scrape a counter `errors_total` and it goes 10, 15, 22, then the process restarts and it goes 3, 8. What does a naive "current value minus previous value" calculation show at the restart point, and why is that wrong?**
> The naive calculation would compute 3 - 22 = -19, showing a nonsensical negative error rate, because the counter reset to near-zero on restart rather than continuing to climb. This is exactly why counter-aware rate functions detect a decrease between samples and treat it as a reset (counting the new value as the increase, e.g. treating it as +3 rather than -19) instead of naively subtracting.

**Q38. What happens if you accidentally instrument a value that goes up and down (like current memory usage) as a counter type?**
> Since a counter type assumes monotonic increase, any legitimate decrease in the underlying value (memory freed, connections closed) will be misread by rate-calculating tools as a "reset," causing bogus spikes in derived rate graphs even though nothing actually restarted. The fix is to use a gauge for any value that can legitimately decrease.

**Q39. What happens if a metric label has unbounded cardinality, like adding `user_id` to a request counter, at scale (e.g. millions of users)?**
> The single metric now produces one time-series per unique user, potentially millions of series, causing memory and disk usage in the metrics backend to grow unboundedly and query performance (especially aggregations) to degrade severely — this is the classic "cardinality explosion" that can crash or badly slow a Prometheus-style TSDB. The fix is to keep such high-cardinality identifiers in logs or traces, and only use bounded-value labels (status code, method, region) on metrics.

**Q40. Why do metrics pair especially well with alerting, more so than logs?**
> Metrics are numeric and pre-aggregated, so a rule engine can cheaply evaluate a threshold ("error rate > 5% for 5 minutes") on every scrape without scanning text, making near-real-time alerting computationally trivial at scale. Deriving the same signal from logs would require continuous parsing/counting of log lines, which is much more expensive to do continuously across a large fleet.

**Q41. Why might a system have high traffic and low errors and low latency, yet still be at risk?**
> This describes rising saturation with no visible symptoms yet — e.g. CPU or connection pool utilization climbing toward its ceiling while requests are still being served fine. Because saturation is a leading indicator, everything can look healthy on the other three signals right up until the resource limit is hit, at which point latency and errors can spike suddenly and sharply (a "cliff," not a gradual slope).

**Q42. In the USE method, what's the difference between "utilization" and "saturation" for a resource like a CPU?**
> Utilization is the percentage of time the resource was busy doing work (e.g. CPU at 70% busy). Saturation is the extra amount of work the resource can't get to right now — for a CPU, this shows up as the length of the run queue (processes waiting for CPU time) even if utilization reads under 100%, because scheduling overhead and queueing can create real slowdowns before raw utilization hits its ceiling.

**Q43. Why can a resource show 100% utilization but low saturation, or vice versa, and why does that matter?**
> A resource can be 100% utilized but have no queue if work arrives steadily and each unit is serviced immediately with none left waiting — that's "busy but not backed up." Conversely, a resource can have moderate utilization but growing saturation if the pattern of arrivals is bursty and queues form during peaks, meaning average utilization alone would hide a real problem visible only in the queue/saturation metric — this is why USE tracks both separately rather than treating them as interchangeable.

**Q44. Give one example each of a metric that would be classified as latency, traffic, errors, and saturation for a typical HTTP API.**
> Latency: `http_request_duration_seconds` (histogram of response times). Traffic: `http_requests_total` (rate of requests per second). Errors: `http_requests_total{status=~"5.."}` rate as a fraction of total. Saturation: something like `process_open_fds` relative to its limit, or connection pool `in_use / max` ratio.

**Q45. Why should "errors" be defined by user impact rather than by what the code logs as an exception?**
> A request can return HTTP 200 but contain the wrong data or a broken partial response — that's a real error from the user's perspective even though no exception was thrown, and it would be invisible if you only counted logged exceptions. Conversely, a caught-and-retried exception that ultimately succeeds might not represent a user-facing error at all, so the "errors" signal should be defined at the boundary of what the user actually experienced.

**Q46. Why does aggregation (sum, avg, percentile) work naturally on metrics but require special care with concurrent counters across many instances?**
> Aggregation works naturally because metric values are already numbers with known semantics (a count, a duration), so `sum()` across 100 pod instances of a counter's rate gives a mathematically correct fleet-wide rate. The care required is knowing which operations are valid to combine — summing counters/rates is fine, but averaging percentiles (like averaging 100 different p99 values) is mathematically invalid, which is exactly why histograms (summable buckets) are preferred over summaries for fleet-wide latency percentiles.

**Q47. Scenario: your dashboard shows overall p50 latency is fine, but users are complaining about slowness. What might you be missing, and how does this connect to labels?**
> A p50 (median) can look fine while a meaningful subset of users experience high latency — e.g. one specific endpoint, region, or customer tier is slow while the bulk of traffic is fast, and the aggregate median just averages this out. Adding labels (endpoint, region, tier) and looking at label-sliced percentiles, or checking a higher percentile like p99, would surface the affected subset that a single blended p50 hides.

**Q48. Predict the output: if you configure a histogram with buckets only at 1s, 5s, and 10s, and your actual request latencies cluster between 50ms and 300ms, what problem will you see when you try to compute p95 latency?**
> Since all real observations fall below the smallest bucket boundary (1s), effectively every request lands in the same "≤1s" bucket, giving you no resolution to distinguish a p95 of 60ms from 900ms. The computed quantile will be a useless approximation (likely just reported near the 1s boundary or via linear interpolation within it), so you'd need to redefine bucket boundaries around your actual expected latency range (e.g. 0.05, 0.1, 0.2, 0.3, 0.5, 1).

**Q49. Why is it more useful to alert on a rate-of-change or ratio (e.g. error rate %) computed from counters, rather than on raw counter values?**
> A raw counter's absolute value depends on how long the process has been running and total traffic volume, so a static threshold like "alert if errors_total > 1000" would trigger inconsistently — quickly on a high-traffic day, maybe never on a quiet day, and always eventually just from long uptime. A rate or ratio (errors per second, or error rate as % of traffic) normalizes for both time and volume, giving a threshold that means the same thing regardless of how long the process has run or how busy it currently is.

**Q50. Tie it together: why does today's material (metric types, labels, Golden Signals, RED/USE) set you up for Days 10-14 (Prometheus and PromQL)?**
> Prometheus's entire data model is built directly on today's concepts — every PromQL query you'll write manipulates counters/gauges/histograms via labels, and every dashboard you'll build is organized around Golden-Signal-style panels (rate, errors, duration, saturation). Without today's conceptual grounding, PromQL functions like `rate()`, `histogram_quantile()`, and `sum by (...)` would look like arbitrary syntax rather than direct, sensible tools for the exact problems (counter resets, label aggregation, percentile computation) you now understand.

## ✅ Day 4 wrap-up

- You can now correctly classify any metric as a counter, gauge, histogram, or summary, and explain why that choice matters for correct aggregation.
- You can name and apply the Four Golden Signals to any user-facing system, and know when to reach for RED (services) versus USE (resources).
- You understand why metrics are cheap and aggregatable, and why unbounded label cardinality breaks that promise.
- Tomorrow (Day 5) you'll add the third pillar — distributed tracing fundamentals — to see how a single request's journey connects the dots that metrics and logs only show in aggregate or in isolation.

Solid work today — you've just internalized the vocabulary that every metrics dashboard you'll ever look at is built from. That's a genuine unlock, not just trivia.
