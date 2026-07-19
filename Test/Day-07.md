# Day 7 — Log Pipelines & Shipping

You've written structured logs (Day 6) — now you need to get them from a hundred containers into a place you can search. Today you learn the collect → parse → route → ship pipeline that powers every real observability stack, and you'll actually run a log shipper locally so the concept stops being abstract. This is the plumbing that makes everything downstream (Day 8 storage, Day 9 querying) possible.

## 🎯 Learning objectives

- [ ] Explain the four stages of a log pipeline (collect, parse/transform, route, ship) and what happens at each
- [ ] Compare Fluent Bit, Fluentd, Vector, and the OpenTelemetry Collector by resource footprint and use case
- [ ] Describe how a parser turns an unstructured log line into structured key-value fields
- [ ] Explain buffering, backpressure, and retry behavior when a downstream backend is slow or down
- [ ] Describe how a collector enriches logs with Kubernetes metadata (pod, namespace, labels)
- [ ] Configure a pipeline to fan-out the same logs to two different destinations
- [ ] Run Fluent Bit or Vector in Docker, feed it logs, and see them arrive transformed at an output

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5m | Freehand-write yesterday's structured logging schema from memory (fields you chose, why); then write the 4 pipeline stage names before reading anything new |
| Learn | 20m | Read Fluent Bit docs (Key Concepts, Pipeline) + Vector docs (Concepts) to map inputs/parsers/filters/outputs to collect/parse/route/ship |
| Do | 25m | Run Fluent Bit (or Vector) in Docker: tail a log file, parse it, fan it out to stdout and a file |
| Reinforce | 10m | Answer 15-20 of today's recall questions out loud or in writing, no peeking |

## 📚 Free resources

- **Fluent Bit official docs** — the "Concepts" and "Pipeline" pages give you the exact mental model (input → parser → filter → buffer → router → output) this day is built on. (type: docs)
- **Vector docs (vector.dev)** — read "Concepts" and "Reference/Configuration"; Vector's own diagrams of sources → transforms → sinks are the clearest visual of fan-out and buffering. (type: docs)
- **OpenTelemetry Collector docs (opentelemetry.io)** — read "Collector" and "Data Collection" to see the vendor-neutral alternative (receivers/processors/exporters) you'll meet again on Day 16. (type: docs)
- **Fluentd "Life of a Fluentd event" (docs.fluentd.org)** — a short, classic diagram of how one log line moves through tag matching, buffering, and output plugins. (type: docs)
- **Grafana Labs blog** — search their blog for Fluent Bit / Vector / Promtail comparisons; Grafana writes frequent practical pieces on shipping logs into Loki. (type: blog)
- **Google SRE Book (sre.google/books)** — Chapter 4 (Monitoring Distributed Systems) reinforces why pipelines need buffering/backpressure discipline, not just "ship everything." (type: book)
- **Vector "Guides" section (vector.dev/guides)** — walks through real pipeline configs including Kubernetes log enrichment, directly matching today's hands-on. (type: docs)
- **CNCF YouTube channel** — search for Fluent Bit or Vector KubeCon talks; conference talks on log agent internals (buffering, memory limits) are free and give production war stories. (type: video)

## 🛠️ Hands-on project (free, ~25 min)

Build a tiny local log pipeline with Fluent Bit in Docker: read a log file, parse it into fields, and fan it out to two outputs.

1. Create a scratch directory with a file `app.log`. Seed it with a few lines that look like real app logs, e.g.:
   `2026-07-16T10:00:01Z INFO order_service order_id=A100 status=created`
   `2026-07-16T10:00:02Z ERROR order_service order_id=A100 status=failed reason=timeout`
2. Write a minimal `fluent-bit.conf` with:
   - an `[INPUT]` of type `tail` pointing at `app.log`
   - a `[FILTER]` of type `parser` using a regex parser to extract `timestamp`, `level`, `service`, and the rest as key=value fields
   - two `[OUTPUT]`s: one `stdout` (format json), one `file` writing to `/output/parsed.log`
3. Run it: `docker run -v $(pwd):/fluent-bit/etc -v $(pwd)/output:/output fluent/fluent-bit:latest -c /fluent-bit/etc/fluent-bit.conf`
4. Append a new line to `app.log` while it's running and watch both outputs update in near-real-time — this is the tail + buffer + fan-out loop in action.
5. Expected outcome: your unstructured text line now appears as structured JSON in stdout and in the output file, simultaneously, from one input.

**Stretch goal:** add a second `[FILTER]` of type `modify` or `record_modifier` that adds a static field like `env=local` and `cluster=laptop` — this is exactly the kind of enrichment collectors do with Kubernetes metadata (pod name, namespace) in production, just faked by hand here.

## 🧠 50 questions for active recall

**Q1. What are the four stages of a log pipeline?**
> Collect (an agent reads logs from a source — file, socket, stdout), parse/transform (turn raw text into structured fields, add/remove/rename fields), route (decide which logs go to which destination based on rules), and ship (send the batched, buffered data to one or more backends). Every log shipper, regardless of vendor, is organized around these four stages even if the naming differs.

**Q2. What is a "collector" or "agent" in the logging context?**
> It's a lightweight process that runs alongside your application (as a sidecar, a host daemon, or a Kubernetes DaemonSet) whose job is to collect logs from a source, process them, and forward them onward. Examples are Fluent Bit, Fluentd, Vector, and the OpenTelemetry Collector.

**Q3. Name four common log collectors/agents and one defining trait of each.**
> Fluent Bit — written in C, extremely small memory/CPU footprint, designed for edge/embedded and Kubernetes DaemonSets. Fluentd — written in Ruby/C, more plugins and flexibility but heavier footprint, often used as a central aggregator behind Fluent Bit. Vector — written in Rust, very fast, unified config for logs/metrics/traces, strong transform language (VRL). OpenTelemetry Collector — vendor-neutral, unifies logs/metrics/traces under one pipeline model (receivers/processors/exporters), designed to avoid lock-in to any single ecosystem.

**Q4. Why would a team run Fluent Bit as a DaemonSet AND Fluentd as a central aggregator, instead of just one or the other?**
> Fluent Bit's small footprint makes it cheap to run on every node to just tail files and forward, while Fluentd's richer plugin ecosystem and buffering capabilities make it better suited to run centrally where it can do heavier parsing, enrichment, and routing to many backends. This "light edge agent, heavier central aggregator" pattern reduces per-node resource cost while still getting flexible processing.

**Q5. What is an "input" or "source" in a log pipeline?**
> It's the pipeline stage that defines where raw log data comes from — a tailed file, a Unix/TCP socket, stdin, a Kubernetes container log directory, syslog, or a message queue like Kafka. It's the entry point of the collect stage.

**Q6. What is a "parser" and why is it necessary?**
> A parser is a rule (regex, JSON parser, or grok-style pattern) that takes an unstructured or semi-structured log line and extracts named fields from it — for example pulling `timestamp`, `level`, and `message` out of a plain text line. It's necessary because most legacy or third-party applications emit unstructured text, and you need structured fields to filter, aggregate, and query logs efficiently downstream (as you learned on Day 3/6).

**Q7. Give an example of parsing an unstructured line into structured fields.**
> The line `2026-07-16T10:00:01Z ERROR order_service timeout` could be parsed with a regex like `^(?<time>\S+) (?<level>\S+) (?<service>\S+) (?<msg>.*)$` into the fields `time=2026-07-16T10:00:01Z`, `level=ERROR`, `service=order_service`, `msg=timeout`. Now each field is independently queryable instead of being buried in a blob of text.

**Q8. What is a "filter" or "transform" stage, and what kinds of things happen there besides parsing?**
> It's the stage where records are modified after being read but before being shipped: parsing raw text, adding/removing fields, renaming fields, dropping unwanted records (sampling or filtering out noisy debug logs), redacting sensitive data (PII masking), and enriching with metadata. It sits conceptually between "collect" and "route/ship."

**Q9. What does "enrichment" mean in a log pipeline, and give a concrete Kubernetes example.**
> Enrichment means adding extra contextual fields to a log record that weren't in the original line, usually pulled from the environment rather than the app itself. In Kubernetes, the Fluent Bit `kubernetes` filter queries the Kubernetes API (or local metadata) to attach `pod_name`, `namespace`, `container_name`, `labels`, and `node_name` to every log line coming from a container, so you can filter logs by deployment or label later without the app itself knowing about Kubernetes.

**Q10. What is a "router" in this context, and why is it a distinct concept from "output"?**
> A router decides, based on tags/labels/rules, which processed records get sent to which output(s) — it's the decision layer, while the output is the actual destination and protocol used to ship. In Fluent Bit, for instance, routing is done via tag matching between inputs/filters and outputs, so one input can conditionally reach multiple or zero outputs depending on tag rules.

**Q11. What is "fan-out" and why is it useful?**
> Fan-out means sending the same collected/processed log stream to multiple destinations simultaneously — for example sending logs to both Loki (for cheap long-term storage) and Elasticsearch (for full-text search), or to both a SIEM and your regular log store. It's useful because different consumers of logs (security, on-call debugging, compliance archival) often want different backends optimized for different query patterns, and you don't want to run separate collection agents for each.

**Q12. What is buffering, and why does every log pipeline need it?**
> Buffering means temporarily holding collected/processed log records in memory or on disk before shipping them, rather than sending each line the instant it's produced. It's needed because shipping one record at a time is inefficient (network overhead per line) and because the destination may be temporarily slow, unreachable, or rate-limited — the buffer absorbs that mismatch so you don't lose data or block the application.

**Q13. What is "backpressure" in a logging pipeline?**
> Backpressure is what happens when the output (destination) can't keep up with the rate of incoming data, and that slowness propagates backward through the pipeline — the buffer fills up, and the pipeline must either slow down reading from the input, drop data, or apply backoff/retry. Handling backpressure well (rather than crashing or OOMing) is one of the main design challenges for any collector.

**Q14. What are the three typical strategies for handling backpressure when a buffer is full?**
> (1) Block/slow the input — stop reading new logs until buffer space frees up, which risks slowing the application if buffers are shared. (2) Drop records — discard oldest or newest data once buffers are full, trading data completeness for stability. (3) Spill to disk — use a filesystem-backed buffer instead of memory-only, trading some latency/throughput for much larger effective buffer capacity and surviving process restarts.

**Q15. What is "batching" and how does it relate to buffering?**
> Batching means grouping multiple buffered records together and sending them to the output in a single request/payload instead of one request per record. It relies on buffering (you need somewhere to accumulate records first) and it dramatically reduces network overhead and backend load, at the cost of slightly higher latency before data appears at the destination.

**Q16. Why do log shippers implement retries, and what's the risk if retries are unbounded?**
> Retries exist because destinations are sometimes transiently unavailable (network blip, backend restart, rate limiting) and you don't want to lose data over a temporary failure. The risk of unbounded retries is that if the destination stays down, retries pile up, buffers grow unbounded, memory/disk fills, and the collector itself can crash or fall further and further behind — hence real configs cap retry counts/duration and pair retries with backoff and eventually dropping or disk-spilling.

**Q17. What is exponential backoff, and why pair it with retries?**
> Exponential backoff means increasing the wait time between successive retry attempts (e.g., 1s, 2s, 4s, 8s) instead of retrying immediately and repeatedly. It's paired with retries because hammering an already-struggling destination with immediate retries can make the outage worse (retry storms); backing off gives the destination room to recover.

**Q18. Compare memory buffering vs. filesystem buffering in Fluent Bit/Vector.**
> Memory buffering is fast and simple but bounded by RAM and lost entirely if the collector process crashes or restarts. Filesystem buffering persists data to disk, surviving crashes/restarts and allowing much larger effective buffer sizes, at the cost of disk I/O overhead and slightly higher latency. Production setups handling important logs typically prefer filesystem buffering for durability.

**Q19. Why is it a bad idea to have your application write logs synchronously and wait for the log shipper to acknowledge before continuing?**
> Because that couples your application's latency and availability to the logging pipeline's health — if the collector or backend is slow, your application requests slow down too. Logging should be effectively fire-and-forget from the application's perspective (write to stdout/file, let the collector handle delivery asynchronously with its own buffering/retry), so log pipeline problems don't become application outages.

**Q20. What does the OpenTelemetry Collector call its equivalent of inputs, filters, and outputs?**
> Receivers (equivalent to inputs — how data enters), processors (equivalent to filters/transforms — batching, filtering, adding attributes), and exporters (equivalent to outputs — where processed data is sent). The key difference from Fluent Bit/Vector is that OTel Collector pipelines are explicitly unified across logs, metrics, and traces using the same three-stage model.

**Q21. Why might a team choose the OpenTelemetry Collector over Fluent Bit specifically?**
> If they want a single vendor-neutral pipeline for logs, metrics, and traces together (rather than separate agents per signal), and want to avoid being locked into any one backend's proprietary agent, the OTel Collector's unified model and broad exporter ecosystem is attractive. It also directly benefits from the OTel ecosystem you'll study in Phase 4 (Days 15-18), since traces and logs can share the same collector infrastructure.

**Q22. What's a tradeoff of choosing the OpenTelemetry Collector today (in 2026) versus Fluent Bit for a pure logging use case?**
> Fluent Bit is more mature and battle-tested specifically for logs, with a smaller footprint and a huge library of log-specific parsers/filters built up over many years, whereas OTel Collector's logs support, while solid, has historically lagged behind its metrics/traces maturity and can require more configuration for log-specific parsing tasks. Teams with pure logging needs and tight resource constraints often still lean Fluent Bit; teams standardizing on OTel across all three pillars lean OTel Collector.

**Q23. What is Vector's "VRL" and what problem does it solve?**
> VRL (Vector Remap Language) is Vector's purpose-built expression language for transforming events inline in configuration — parsing, reshaping, filtering, and enriching records without writing external plugins in a general-purpose language. It solves the problem of needing custom transform logic (e.g., "if field X matches this pattern, extract Y and drop Z") without leaving the config file or writing/compiling a plugin.

**Q24. In Fluent Bit's architecture, what is a "tag" and why does it matter for routing?**
> A tag is a string attached to records as they enter through an input, used to match against filters and outputs via wildcard or exact rules. It matters because Fluent Bit's routing is fundamentally tag-based — an output only receives records whose tag matches its configured match pattern, which is how one Fluent Bit process can selectively route different log sources to different destinations.

**Q25. Why do most log pipelines apply parsing/filtering as close to the source as possible, rather than only at the central aggregator?**
> Parsing early reduces the volume and complexity of what needs to travel over the network and lets you drop or sample unwanted logs (e.g., noisy debug lines) before they consume bandwidth and storage, rather than after. It also distributes the CPU cost of parsing across many lightweight edge agents instead of concentrating it on a single central bottleneck.

**Q26. What happens if you configure a regex parser with a mistake (e.g., a capture group name typo) — what's the typical failure mode?**
> The parser fails to match, and depending on configuration the record either passes through unparsed (kept as a raw `message` field, no structured fields extracted) or gets dropped/tagged as a parse error. This is why testing parsers against sample log lines before deploying them widely is essential — silent parse failures mean you lose the structured fields you were counting on for querying (Day 9).

**Q27. Predict the output: you tail a file, apply a JSON parser filter, but the log lines are actually plain text, not JSON. What happens?**
> The JSON parser fails to parse each line since it's not valid JSON; most collectors will either leave the record as an unparsed raw string in a default field (e.g., `log` or `message`) or emit a parser error/warning, but the pipeline typically doesn't crash — it just fails to extract structured fields, and you'll see raw text arrive at your output. You'd need to switch to a regex or a different parser type to actually extract fields.

**Q28. Predict the output: you configure fan-out to two outputs, but one destination is unreachable and has no retry limit or buffer cap configured. What happens over time?**
> The buffer feeding that failing output keeps growing since data can't be shipped and there's no cap forcing drop/backoff, so memory (or disk, depending on buffer type) usage climbs continuously; eventually the collector can hit resource limits and crash or get OOM-killed by Kubernetes, potentially taking down log collection for the healthy output too if buffers are shared. This is exactly why bounding buffers and retries per-output is a critical production safeguard, not an edge case.

**Q29. Predict the output: a Kubernetes filter is enabled but the collector's service account lacks permission to query the Kubernetes API. What field enrichment behavior results?**
> The collector will typically log an error/warning about failing to reach the Kubernetes API, and the enrichment step silently no-ops — logs still flow through the pipeline but without the expected `pod_name`, `namespace`, or `labels` fields attached. This is a common real-world gotcha: the pipeline "works" (data still ships) but silently loses the metadata that makes it useful for debugging.

**Q30. What's the difference between "tailing a file" and "reading from stdout via the container runtime" as a log input?**
> Tailing a file means the agent watches a file on disk (e.g., `/var/log/containers/*.log`) for new appended lines, which is how most Kubernetes node-level agents actually read container logs since the container runtime writes stdout/stderr to files on the node. "Reading from stdout directly" typically applies to sidecar patterns where an agent is attached to the same pod and can consume the stream more directly, avoiding the extra disk write/read round-trip but adding a container per pod.

**Q31. Why is tailing files (rather than push-based logging from the app) the dominant pattern in Kubernetes logging?**
> Because it decouples the application from any specific logging backend or agent — the app just writes to stdout/stderr (12-factor app principle), the container runtime redirects that to a file, and a node-level agent tails those files. This means you can swap Fluent Bit for Vector or add a new backend without ever touching application code.

**Q32. What is "multiline log handling" and why is it a pipeline challenge?**
> Multiline handling is the logic needed when a single logical log event (like a Java stack trace) spans multiple physical lines in the log file, but a naive line-based tailer would treat each line as a separate record. It's a challenge because you need special parsers/rules (e.g., detecting a line that doesn't start with a timestamp and appending it to the previous record) to correctly reassemble the full event before shipping, otherwise stack traces get fragmented and unreadable in your backend.

**Q33. What's a practical reason to drop or sample certain logs in the filter stage rather than shipping everything?**
> Cost and noise — shipping and storing 100% of DEBUG-level or health-check logs from every request can dominate your storage/ingest cost and bury the signal you actually need for debugging, so filters commonly drop known-noisy patterns (successful health checks, verbose debug lines) or sample them (keep 1 in N) before they ever leave the node. This is the same cardinality/cost tradeoff you'll dig into more on Day 22.

**Q34. What is redaction/masking in a log pipeline, and give an example rule?**
> Redaction means detecting and replacing sensitive data (PII, credit card numbers, tokens, passwords) in log fields with a placeholder before shipping, so sensitive data never lands in your log storage. An example rule: a filter that regex-matches an `email` or `ssn`-shaped pattern in any field value and replaces it with `[REDACTED]` before the record proceeds to the output stage.

**Q35. Why should redaction happen in the pipeline (at collection time) rather than relying on the storage backend to restrict access later?**
> Because once sensitive data is stored, it's much harder to guarantee it was never seen by every process/backup/replica/index it might have touched, and access controls on storage don't prevent the data from existing in logs, backups, or being exposed by a misconfiguration. Redacting at collection time means sensitive data never enters the pipeline at all, which is a much stronger and simpler guarantee.

**Q36. What is the difference between "push" and "pull" collection models for logs?**
> Push means the source actively sends its logs to the collector (e.g., an app pushing logs to a syslog endpoint or an HTTP log ingestion API), while pull means the collector actively reaches out and reads from the source (e.g., tailing a file, or a collector scraping a log API on a schedule). Most container/Kubernetes logging is pull-based (tailing files), while some appliance/network device logging is push-based (syslog).

**Q37. What role does a message queue (like Kafka) sometimes play between collection and storage?**
> Kafka can sit as a durable buffer between the edge collectors and the final backend, decoupling ingestion rate from indexing rate — collectors ship to Kafka quickly and reliably, and a separate consumer process reads from Kafka to index into Elasticsearch/Loki/etc. at whatever pace the backend can handle. This adds resilience (Kafka retains data even if the indexing backend is down for a while) at the cost of additional infrastructure to operate.

**Q38. Why might you route ERROR-level logs to a fast/expensive backend and INFO/DEBUG logs to a cheap/slower one?**
> Because different log severities have different query urgency and retention needs — errors need to be searched quickly during incidents and often need alerting, justifying a pricier low-latency store, while high-volume routine INFO/DEBUG logs are rarely queried and mainly needed for occasional deep-dive debugging, so a cheaper, slower-to-query store (or shorter retention) is a better cost/value tradeoff. This routing decision happens at the "route" stage using tag/label rules.

**Q39. What is the practical difference between a "sink" and an "output" — are they the same thing?**
> They're the same concept named differently by different tools: Vector calls its destinations "sinks," Fluent Bit and Fluentd call them "outputs," and OpenTelemetry Collector calls them "exporters." All three represent the final stage where processed records leave the pipeline toward a storage/analysis backend.

**Q40. Why does resource footprint (CPU/memory) matter so much when choosing a log agent for a DaemonSet?**
> A DaemonSet runs one instance of the agent on every node in the cluster, so even a small per-instance resource increase gets multiplied across potentially hundreds or thousands of nodes, directly increasing your infrastructure bill and competing with your actual application workloads for node resources. This is why Fluent Bit (C, low footprint) is often preferred at the DaemonSet layer even when Fluentd or Vector might offer more features.

**Q41. Scenario: your team ships to both Loki and a SIEM tool, and the SIEM ingestion endpoint starts silently rejecting malformed records instead of erroring. How would misconfigured routing/parsing surface here, and what would you check first?**
> Because the SIEM silently drops bad records rather than failing loudly, you might not notice missing security logs until an audit or incident review reveals gaps; you'd check the SIEM's own ingestion/rejection metrics or dead-letter logs first, then verify the parser/filter feeding that output is producing records in exactly the schema the SIEM expects (field names, required fields, data types). This illustrates why validating each output's schema requirements, not just "did the pipeline run," is part of routing to multiple heterogeneous backends.

**Q42. Scenario: after adding a new enrichment filter, your log throughput visibly drops and node CPU usage climbs. What's the most likely cause and how would you confirm it?**
> The most likely cause is that the new filter (e.g., a Kubernetes metadata lookup, a complex regex, or an API call per record) is computationally expensive or making blocking network calls per log line, becoming a bottleneck in the pipeline. You'd confirm by checking the collector's own internal metrics (most agents expose Prometheus metrics on records processed/dropped/latency per plugin) to see which stage's throughput dropped, and by testing the filter in isolation against a sample dataset.

**Q43. Scenario: developers complain that logs from a new service never show up in your central backend, but `kubectl logs` shows them fine on the pod. Where would you look first in the pipeline?**
> First check whether the collector's input (tail path/pattern) actually covers this pod's log file location and whether any tag/routing rule excludes it (e.g., a namespace-based filter); then check whether a parser is failing and silently dropping records rather than passing them through; finally check the collector's own health/error metrics for signs of backpressure or crash-looping on that node. `kubectl logs` reading fine tells you the container runtime has the data — the gap is somewhere between the node-level agent's input and its output.

**Q44. Scenario: you need to add a third destination (a long-term archive in object storage) without disrupting your existing two outputs. What's the safe way to do this in a pipeline config?**
> Add a new output block with the same input tag/match pattern as the existing outputs so it receives a copy of the same stream (fan-out), test it in a non-production or canary agent first, and roll it out gradually (e.g., one node/DaemonSet pod at a time) while watching the collector's resource usage and the new output's ingestion success — since each additional output adds buffering and network overhead on every node. You would not need to touch the input or existing outputs at all, which is the benefit of the modular pipeline design.

**Q45. Why is "one log line = one event" often a false assumption in a pipeline, and what breaks if you assume it blindly?**
> As covered with multiline logs (Q32), a single logical event (e.g., an exception with a stack trace, or a pretty-printed JSON blob) can span many physical lines; if you assume one line = one event, your collector will chop these into many meaningless fragments, breaking correlation and making the log unreadable in your backend. You must explicitly configure multiline detection/parsing rules whenever your application can emit multi-line output.

**Q46. What is the relationship between the pipeline concepts here and the OTel Collector concepts you'll study on Day 16?**
> They're the same architectural pattern (collect/receive → transform/process → route → ship/export) applied specifically within the OpenTelemetry project's unified model for logs, metrics, and traces together — so everything you learn today about buffering, backpressure, batching, and fan-out transfers directly, you'll just see it expressed as receivers/processors/exporters instead of Fluent Bit's inputs/filters/outputs.

**Q47. Why might a company standardize on Vector across logs, metrics, and traces instead of running separate specialized agents for each?**
> A single unified agent reduces operational complexity (one config language, one deployment, one set of resource limits to tune) and lets you apply the same enrichment (like adding a `cluster` or `region` tag) consistently across all three signal types from one place, rather than maintaining that logic three times in three different tools. The tradeoff is depending on one tool's maturity across all three domains rather than picking a specialist for each.

**Q48. What is a practical first health check to run on any log pipeline agent in production?**
> Check the collector's own internal/self-monitoring metrics (most expose a Prometheus endpoint) for records-in vs records-out per plugin, buffer usage/fullness, and dropped-record counts — this tells you immediately whether data is flowing end-to-end or getting stuck/dropped at some stage, before you go hunting through application logs or backend query results.

**Q49. Why is it important to test parser configs against real sample log lines before deploying to production, rather than just writing the regex/grok pattern from memory?**
> Real log lines often have edge cases (extra whitespace, occasional missing fields, encoding quirks, unexpected multiline content) that a regex written from memory won't anticipate, and a silent parse failure (Q26) means you lose structured fields without any obvious error — you'd only discover it later when trying to query a field that was never actually extracted. Testing against a representative sample of actual production log output catches these mismatches before they cause silent data-quality gaps.

**Q50. Tie it together: describe, stage by stage, what happens to a single ERROR log line from a Kubernetes pod that needs to end up structured, enriched with pod metadata, and searchable in both Loki and a SIEM.**
> Collect: a node-level Fluent Bit/Vector agent tails the container's log file where the runtime wrote the pod's stdout line. Parse/transform: a parser filter extracts fields like `timestamp`, `level`, `message` from the raw text (or passes through cleanly if already JSON from Day 6's structured logging). Enrich: a Kubernetes metadata filter attaches `pod_name`, `namespace`, and `labels` by querying the K8s API/local cache. Route: because the record's tag matches both output rules (and its level is ERROR, matching a SIEM-specific routing rule), it's selected for fan-out to two destinations. Ship: the record is placed in each output's buffer, batched with other records, and sent — with retry/backoff if either destination is briefly unavailable — until it lands, structured and enriched, in both Loki and the SIEM.

## ✅ Day 7 wrap-up

- You can now describe and diagram the collect → parse → route → ship pipeline and map it onto any real tool (Fluent Bit, Fluentd, Vector, OTel Collector)
- You understand buffering, backpressure, batching, and retries well enough to reason about what breaks when a downstream backend goes down
- You've run a real pipeline locally in Docker, parsing raw text into structured fields and fanning it out to two outputs
- Tomorrow (Day 8) you'll take these shipped, structured logs and dig into where they land — Loki vs. Elasticsearch/OpenSearch — and how storage/indexing choices shape what queries you can run later

You've just built the plumbing that every logging stack depends on — that's real, transferable infrastructure knowledge. Nice work today.
