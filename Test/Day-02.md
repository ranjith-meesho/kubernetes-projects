# Day 2 — The Observability Landscape & OpenTelemetry's Role

Yesterday you drew the conceptual line between monitoring and observability and mapped out how you'll learn this material. Today you fill in the map with real names: the tools you'll keep hearing about for the next 23 days, what each one actually does, and why OpenTelemetry increasingly sits at the center of all of it. Get this mental model right now and every later day — logging, metrics, tracing, correlation — will click into a slot instead of floating as a loose fact.

## 🎯 Learning objectives

- [ ] Name the three observability signal types (logs, metrics, traces) and one purpose-built tool for each
- [ ] Place Prometheus, Grafana, Loki, Elasticsearch/OpenSearch, Jaeger, Tempo, Fluent Bit, and Vector correctly on a signal/role map
- [ ] Explain the difference between an open standard (OpenTelemetry) and a vendor/backend (Datadog, New Relic, Grafana Cloud)
- [ ] Describe what OTLP is and why a common wire protocol matters
- [ ] Explain in your own words why OpenTelemetry exists and what problem it solves (instrumentation lock-in)
- [ ] Sketch a high-level pipeline: source → collection/agent → storage/backend → query/visualization
- [ ] Identify which tool you'd reach for first in three different troubleshooting scenarios

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5 min | Freehand-write yesterday's monitoring vs. observability definition and the 3 signal types, no notes |
| Learn | 20 min | Skim the OpenTelemetry docs "What is OpenTelemetry" page + CNCF landscape overview; build your tool map |
| Do | 25 min | Hands-on project below — inspect a real OTLP payload and pipeline config |
| Reinforce | 10 min | Active recall questions (aim for the first 25; do the rest as spaced review tomorrow) |

## 📚 Free resources

- **OpenTelemetry official docs (opentelemetry.io)** — the "What is OpenTelemetry" and "Concepts" pages are the single best explanation of why it exists. (type: docs)
- **CNCF Cloud Native Landscape (landscape.cncf.io)** — the canonical visual map of every tool in this space, filterable by "Observability and Analysis" category. (type: docs)
- **Prometheus docs — Overview (prometheus.io/docs)** — short, precise explanation of what Prometheus is and isn't. (type: docs)
- **Grafana Labs blog (grafana.com/blog)** — frequent, practical posts comparing Loki vs. Elasticsearch and explaining the Grafana/Loki/Tempo/Mimir ("LGTM") stack. (type: blog)
- **Jaeger docs (jaegertracing.io)** — clear architecture diagrams for a dedicated tracing backend. (type: docs)
- **Grafana Tempo docs (grafana.com/docs/tempo)** — explains a trace backend built to be cheap at scale via object storage. (type: docs)
- **Fluent Bit docs (fluentbit.io)** and **Vector docs (vector.dev)** — both have short "why this exists" pages contrasting log/metric shippers with the backends they feed. (type: docs)
- **Google SRE Book — free online (sre.google/books)**, chapter on monitoring distributed systems — grounds the "why" behind all these tools in production reality. (type: book)
- **"OpenTelemetry Explained" style talks on the CNCF YouTube channel** — search CNCF's channel for OpenTelemetry intro talks; most are 20-30 min conference talks free to watch. (type: video)
- **OpenTelemetry Protocol (OTLP) specification (opentelemetry.io/docs/specs/otlp)** — short, worth skimming even if you don't understand every field yet. (type: docs)

## 🛠️ Hands-on project (free, ~25 min)

**Goal:** see a real OTLP payload and a real shipping pipeline config, so "the landscape" stops being abstract names and becomes files you've actually opened.

1. Install/confirm Docker is available (`docker --version`). If you did Day 1's setup, you may already have this.
2. Pull and run the OpenTelemetry Collector in demo/debug mode:
   ```
   docker run --rm -p 4317:4317 -p 4318:4318 \
     -v $(pwd)/otel-collector-config.yaml:/etc/otel/config.yaml \
     otel/opentelemetry-collector:latest --config /etc/otel/config.yaml
   ```
   If you don't have a config file yet, first create a minimal one (search "OpenTelemetry Collector getting started config" for the receivers/processors/exporters skeleton with a `logging`/`debug` exporter) — the point is just to get it running, not to perfect it.
3. Send it a test signal. Use `curl` against the OTLP HTTP endpoint (`localhost:4318/v1/traces`) with a sample JSON trace payload (search "OTLP HTTP example curl payload" for a copy-pasteable one), or use the OpenTelemetry demo app's `telemetrygen` tool if you have it.
4. Watch the collector's own console output (`debug` exporter) — you'll see the raw OTLP-formatted trace/log/metric data it received, printed in a structured form.
5. Separately, open the Fluent Bit or Vector docs "quick start" page and just read one example config file end to end — note the `input` / `filter` / `output` (or `source`/`transform`/`sink`) shape. That shape is the pipeline pattern you'll see everywhere.

**Expected outcome:** you've watched one real signal travel through a collector and seen, concretely, that "OTLP" just means a defined JSON/protobuf shape for telemetry data — not magic.

**Stretch goal:** point the same collector config's exporter section at a second destination (e.g., add a second `debug` exporter or a Prometheus exporter block) to see how one pipeline can fan out to multiple backends — the exact reason OpenTelemetry decouples instrumentation from backend choice.

## 🧠 50 questions for active recall

**Q1. What are the three canonical observability signal types?**
> Logs, metrics, and traces. Logs are discrete timestamped event records, metrics are numeric time-series aggregates, and traces are structured records of a request's path through a distributed system. Some add "profiles" as a fourth emerging signal, but the core three are what you'll work with in this roadmap.

**Q2. What is Prometheus, in one sentence?**
> Prometheus is an open-source metrics collection, storage, and querying system that pulls (scrapes) numeric time-series data from instrumented targets on a schedule. It's the de facto standard for metrics in cloud-native environments and includes its own query language, PromQL.

**Q3. What is Grafana, and what is it not?**
> Grafana is a visualization and dashboarding layer that queries multiple backends (Prometheus, Loki, Tempo, Elasticsearch, and more) to render graphs, tables, and alerts. It is not a storage or collection system itself — it has no data of its own; it's a window onto other systems' data.

**Q4. What is Loki, and how does its design philosophy differ from Elasticsearch?**
> Loki is a log aggregation system built by Grafana Labs that indexes only metadata (labels) rather than full log text, keeping the log content itself compressed and cheap in object storage. Elasticsearch (and OpenSearch) fully indexes log content for rich full-text search, which is more powerful but far more expensive in storage and compute at scale.

**Q5. What problem does Jaeger solve?**
> Jaeger is a distributed tracing backend: it stores and lets you query and visualize traces, showing how a single request propagated across multiple services with timing for each hop (span). It was originally built at Uber and donated to CNCF.

**Q6. What is Grafana Tempo and how does it relate to Jaeger?**
> Tempo is Grafana Labs' trace backend, solving the same core problem as Jaeger (storing/querying traces) but designed to be extremely cost-efficient by storing trace data in object storage (like S3) and indexing minimally, similar to how Loki treats logs. You'd choose between them based on ecosystem fit and cost/scale needs, not because they do fundamentally different jobs.

**Q7. What do Fluent Bit and Vector have in common?**
> Both are lightweight agents/pipelines for collecting, transforming, and shipping telemetry data (primarily logs, though Vector also handles metrics) from sources to one or more backends. They sit in the "collection and routing" layer of the pipeline, not the storage or visualization layer.

**Q8. Name one meaningful difference between Fluent Bit and Vector.**
> Fluent Bit is written in C, extremely small-footprint, and part of the CNCF Fluentd ecosystem, commonly used as a Kubernetes DaemonSet log forwarder. Vector is written in Rust, has a more modern unified config model for logs/metrics/traces, and markets itself as a single tool that can replace multiple specialized shippers.

**Q9. What is OpenTelemetry, in one sentence?**
> OpenTelemetry (OTel) is a CNCF open standard and set of SDKs/APIs/tools for generating, collecting, and exporting telemetry data (logs, metrics, traces) in a vendor-neutral way. It defines how you instrument code and how that data is shaped and transported, independent of which backend eventually stores it.

**Q10. What specific problem was OpenTelemetry created to solve?**
> Before OTel, teams instrumented code with vendor-specific SDKs (e.g., a specific APM vendor's library), which meant switching backends required re-instrumenting your entire codebase. OTel decouples "how you instrument" from "where data goes," so you instrument once and can point the data at any compatible backend.

**Q11. Is OpenTelemetry a backend/storage system?**
> No. OpenTelemetry does not store or visualize data long-term; it standardizes instrumentation, collection, and transport. You still need a backend (Prometheus, Tempo, Jaeger, Elasticsearch, or a commercial vendor) to store and query the data OTel produces.

**Q12. What is OTLP?**
> OTLP (OpenTelemetry Protocol) is the wire protocol OpenTelemetry defines for transmitting logs, metrics, and traces — specifying the data shape and encoding (protobuf over gRPC, or JSON/protobuf over HTTP) so any OTel-compatible producer and consumer can exchange telemetry without custom integration code.

**Q13. Why does having a common protocol like OTLP matter practically?**
> It means an instrumentation library, a collector, and a backend built by three completely different vendors/projects can interoperate without bespoke glue code, because they all agree on the same data shape. This is the same value proposition HTTP or SQL provide in their domains — a lingua franca.

**Q14. What is the OpenTelemetry Collector?**
> It's a standalone, vendor-agnostic service that receives telemetry (often via OTLP), can process/filter/transform it, and exports it to one or more backends. It's the "pipeline hub" piece of the OTel architecture — think of it as a programmable router for telemetry data.

**Q15. Contrast "open standard" and "vendor" using OpenTelemetry and Datadog as examples.**
> OpenTelemetry is a specification and open-source implementation governed by CNCF, with no single company controlling it and no cost to adopt. Datadog is a commercial vendor offering a proprietary SaaS backend (and historically its own instrumentation agents); it can consume OTLP data, but the storage/query/UI product itself is closed and paid.

**Q16. Where does Elasticsearch/OpenSearch fit in the observability landscape?**
> They're general-purpose search and analytics engines that are commonly used as a log storage/indexing backend (the "E" in the classic ELK/EFK stack), offering powerful full-text search over log content. OpenSearch is the open-source fork created after Elasticsearch's license change.

**Q17. Why did OpenSearch come to exist as a separate project from Elasticsearch?**
> Elastic changed Elasticsearch's license away from a fully open-source license, so AWS and other contributors forked the last open-source version to create OpenSearch, keeping an Apache-2.0-licensed alternative available.

**Q18. Sketch the generic pipeline shape common to almost every observability tool.**
> Source/instrumentation (app emits data) → collection/agent (Fluent Bit, Vector, OTel Collector, Prometheus scrape) → storage/backend (Loki, Elasticsearch, Prometheus TSDB, Tempo, Jaeger) → query/visualization (Grafana, Kibana, Jaeger UI). Almost every tool you'll learn in this roadmap slots into one of these four stages.

**Q19. Where does Prometheus deviate from the generic "agent pushes data" pattern?**
> Prometheus primarily uses a pull model — it scrapes metrics endpoints on a schedule — rather than agents pushing data to it, which is the opposite of how most log shippers (Fluent Bit, Vector) and OTLP exporters work by default (push).

**Q20. What's the CNCF, and why does it matter that so many of these tools are CNCF projects?**
> The Cloud Native Computing Foundation is a vendor-neutral home for open-source cloud infrastructure projects (Kubernetes, Prometheus, OpenTelemetry, Jaeger, Fluentd, etc.). CNCF governance signals these projects aren't controlled by a single vendor's commercial interest, which is a major reason they've become de facto standards across the industry.

**Q21. Give one reason a team might choose Elasticsearch over Loki for logs despite the higher cost.**
> If the team needs rich, ad-hoc full-text search across unstructured log content — searching for arbitrary substrings or complex text patterns quickly — Elasticsearch's full indexing supports that well, whereas Loki's label-only indexing makes unindexed full-text search slower at scale.

**Q22. Give one reason a team might choose Loki over Elasticsearch.**
> Cost and operational simplicity: Loki's minimal indexing keeps storage and compute costs far lower, especially at high log volume, and it integrates natively with Grafana alongside metrics and traces for unified dashboards.

**Q23. What does "vendor lock-in" mean in the observability context, and how does OTel reduce it?**
> Vendor lock-in means your application code is tightly coupled to one specific vendor's proprietary instrumentation SDK, making it costly to switch backends later. OTel reduces this by giving you a single, vendor-neutral instrumentation API/SDK; switching backends becomes a matter of reconfiguring an exporter, not rewriting instrumentation code.

**Q24. Can OpenTelemetry send data directly to a backend without a Collector?**
> Yes — SDKs can export directly to a backend (e.g., an app can export OTLP straight to Tempo or a vendor endpoint), but routing through a Collector is common because it centralizes processing (batching, filtering, sampling, fan-out to multiple destinations) instead of baking that logic into every service.

**Q25. What's the practical difference between "instrumentation" and "collection" in this landscape?**
> Instrumentation is code inside (or attached to) your application that generates telemetry (OTel SDKs, Prometheus client libraries, log statements). Collection is the separate infrastructure layer that gathers, batches, and forwards that already-generated telemetry onward (OTel Collector, Fluent Bit, Vector, Prometheus's scraper).

**Q26. Scenario: your team wants dashboards showing logs, metrics, and traces side by side in one UI, correlated by time and labels. Which single tool from today's list makes this easiest, and why?**
> Grafana, because it's a visualization layer designed to query multiple backends (Prometheus for metrics, Loki for logs, Tempo/Jaeger for traces) simultaneously and correlate them by shared labels/time — that's precisely its role in the landscape, unlike any single storage backend which only knows its own signal.

**Q27. Scenario: you need to search for a rare error string across terabytes of unstructured log text with sub-second response. Which backend fits better, Loki or Elasticsearch, and why?**
> Elasticsearch/OpenSearch, because its full-text inverted index is built exactly for fast arbitrary substring/phrase search across large text corpora, whereas Loki intentionally avoids full content indexing to stay cheap, making broad unindexed text search slower.

**Q28. Scenario: a request is slow, and you need to see exactly which downstream service call caused the delay across five microservices. What signal and what kind of tool do you reach for?**
> Tracing — a distributed trace showing spans for each service hop with timing, viewed in a tracing backend like Jaeger or Tempo. Metrics would tell you something is slow in aggregate; only a trace shows you the specific request's path and where time was spent.

**Q29. Why is it inaccurate to say "OpenTelemetry replaces Prometheus" or "OpenTelemetry replaces Jaeger"?**
> Because OTel operates at a different layer — instrumentation and transport — while Prometheus and Jaeger are storage/query backends. OTel can produce metrics that Prometheus stores, or traces that Jaeger stores; it doesn't compete with them, it feeds them (though OTel does include its own lightweight local collector capabilities, it isn't a replacement for a full backend).

**Q30. What does "signal" mean in observability terminology, and name the OTel term for a single unit of tracing data.**
> A "signal" refers to a category of telemetry (logs, metrics, or traces). In tracing specifically, a single unit of work within a trace — one operation with a start time, duration, and metadata — is called a "span."

**Q31. Predict the output: you run the OTel Collector with only a `receivers: [otlp]` and `exporters: [debug]` config (no processors) and send it one trace via OTLP HTTP. What do you see, and what's missing from a production setup?**
> You'd see the raw trace data printed to the collector's console/logs in a structured, readable form — proof the pipeline works end to end. What's missing for production: no processors (batching, sampling, filtering), and no real backend exporter, so nothing is actually stored or queryable — you'd lose the data once the console output scrolls away.

**Q32. Predict the output: a Prometheus server is configured to scrape a target every 15s, but that target's `/metrics` endpoint is down for 2 minutes. What happens to the data for that gap when queried later?**
> There will be a genuine gap (no data points) in Prometheus's time series for that target during the outage window — Prometheus does not backfill or interpolate missing scrapes by default, so PromQL functions like `rate()` may behave oddly or return no data across that gap depending on the query window.

**Q33. Predict the output: you point Grafana at both a Prometheus data source and a Loki data source and build one dashboard panel using PromQL and another using LogQL. What must be true for this to work, and what won't happen automatically?**
> Grafana must have both data sources correctly configured/reachable; each panel independently queries its own backend using that backend's native query language — this works fine. What won't happen automatically is deep semantic correlation (e.g., Grafana won't automatically know a spike in a metric panel and a burst of error logs in another panel are "the same incident") unless you deliberately align them by time range and shared labels, which is a Day 21 topic.

**Q34. Why is "open standard" important for long-term instrumentation strategy specifically (not just cost)?**
> Because instrumentation code gets embedded deeply and pervasively throughout an application; if it's tied to a proprietary API, migrating backends later means touching every instrumented call site again. An open standard means the instrumentation investment survives a future backend change.

**Q35. What does "vendor-neutral" mean when describing OpenTelemetry's governance?**
> It means no single company owns or controls the specification or reference implementation — CNCF governs it, with contributions from many competing vendors (AWS, Google, Microsoft, Datadog, Splunk, etc.) who all agree to support the same standard rather than pushing their own proprietary format.

**Q36. In the CNCF landscape, what category would you look under to find tools like Prometheus, Grafana, and Jaeger grouped together?**
> "Observability and Analysis" — CNCF's landscape site groups monitoring, logging, and tracing tools under this umbrella category, with further sub-groupings for monitoring, logging, and tracing specifically.

**Q37. Compare "push" and "pull" collection models with one example tool each.**
> Push: the source actively sends data to a collector/backend without being asked (e.g., an OTel SDK exporting spans via OTLP, or Fluent Bit shipping logs). Pull: the backend actively requests data from the source on its own schedule (e.g., Prometheus scraping a `/metrics` HTTP endpoint).

**Q38. Why might a team run both an OpenTelemetry Collector and Fluent Bit/Vector in the same infrastructure rather than picking just one?**
> The OTel Collector is signal-agnostic and central to the OTel ecosystem for traces/metrics (and increasingly logs), while Fluent Bit/Vector may already be deployed as battle-tested Kubernetes DaemonSets specifically optimized for high-volume log tailing from container stdout/files. Teams often keep the specialized log shipper for raw log collection and use the OTel Collector for processing/routing and for traces and metrics.

**Q39. What is a "backend" in the observability sense, and give three examples from today.**
> A backend is the system that durably stores telemetry data and answers queries against it. Examples: Prometheus (metrics storage + query), Elasticsearch/OpenSearch (log storage + search), Jaeger or Tempo (trace storage + query).

**Q40. Why is it misleading to think of Grafana, Loki, Tempo, and Prometheus (the "LGTM stack") as one monolithic product?**
> Each is an independently deployable, independently developed open-source project with its own architecture and can be swapped out (e.g., you could use Grafana with Elasticsearch and Jaeger instead). They're commonly bundled and marketed together by Grafana Labs, but nothing forces you to adopt all four together — the landscape is modular by design.

**Q41. What role does "sampling" play conceptually in a tracing pipeline, and where would you configure it in an OTel setup?**
> Sampling decides which traces are kept versus discarded, since capturing every single trace at high request volume is often prohibitively expensive to store. In an OTel setup, sampling can be configured at the SDK (head-based, at trace start) or in the Collector (tail-based, after seeing the full trace) — a topic you'll go deeper on in Day 22.

**Q42. What is the practical difference between a "metric" and a "log line" describing the same event, e.g., an HTTP request?**
> A metric aggregates that event into a number over time (e.g., a counter incrementing, or a value in a histogram bucket) with low storage cost regardless of request volume. A log line records the full discrete event with arbitrary detail (headers, user ID, error text) but at storage cost proportional to request volume and verbosity.

**Q43. Why do dedicated tracing backends like Jaeger/Tempo exist instead of just storing traces as structured logs in Elasticsearch?**
> Trace data has a specific shape (parent-child span relationships forming a tree per trace ID) and query patterns (e.g., "show me the full waterfall for this one request," "find traces where a specific span was slow") that generic log storage doesn't optimize for or visualize well; dedicated trace backends build indexing and UI specifically around trace/span structure.

**Q44. What does "collector" mean generically across this landscape, separate from the specific "OpenTelemetry Collector" product?**
> Generically, a collector is any component that gathers telemetry from sources before it reaches long-term storage — this includes Fluent Bit, Vector, and Prometheus's own scraper, not just the OpenTelemetry Collector by name. Don't conflate the generic role with the specific named OTel product; the OTel Collector is one implementation of the "collector" role, built specifically around OTLP.

**Q45. Scenario: your org currently ships all logs to Elasticsearch via Fluent Bit, and now wants to add distributed tracing without re-architecting logging. What's a sensible next addition to the stack?**
> Add OpenTelemetry instrumentation to services (for traces specifically) and a trace backend like Jaeger or Tempo, run alongside (not replacing) the existing Fluent Bit → Elasticsearch logging pipeline. The signals are independent enough that you can adopt tracing incrementally without disturbing the working log pipeline.

**Q46. Why is "the observability landscape has too many tools" a common but slightly wrong complaint — what's the actual reason for the multiplicity?**
> Each tool specializes in one signal type and one job in the pipeline (collection, storage, or visualization) rather than all tools doing everything; the apparent sprawl reflects specialization and choice at each pipeline stage (e.g., choosing Loki vs. Elasticsearch for storage), not redundant tools doing the same job badly.

**Q47. What would you tell a teammate who says "we should just use OpenTelemetry for everything and skip Prometheus/Grafana/Loki entirely"?**
> That's a category error — OpenTelemetry doesn't provide long-term storage, dashboards, or alerting UI; you still need backends like Prometheus (metrics storage/query) and Grafana (visualization) or equivalents. OTel changes how you instrument and transport data, not whether you need storage/visualization tools at all.

**Q48. Predict the output: a service is instrumented with the OpenTelemetry SDK configured to export metrics via OTLP to a Collector, but the Collector's config has no metrics exporter defined (only a traces exporter). What happens to the metrics?**
> The metrics data arrives at the Collector via the OTLP receiver but is silently dropped for the metrics pipeline, since there's no exporter defined to send it anywhere — traces would continue flowing to their configured exporter unaffected, since each signal type has its own pipeline definition in the Collector config.

**Q49. Why does today's "map" matter before you dive into hands-on Prometheus/Loki/tracing work in later phases?**
> Because metalearning (Day 1's principle) says orienting yourself in the terrain before drilling details prevents you from mistaking a tool's role — e.g., thinking Grafana stores data, or that OpenTelemetry is a backend — mistakes that would cause confusion and wasted debugging time once you're deep in Phase 2-4 hands-on work.

**Q50. In one or two sentences, explain why OpenTelemetry is described as "the unifying standard" of this landscape rather than just "one more tool in the landscape."**
> Because unlike Prometheus, Loki, or Jaeger — which are backends solving one signal's storage/query problem — OpenTelemetry defines the common instrumentation API and wire protocol (OTLP) that lets data flow into any of those backends interchangeably. It sits above and across the landscape as connective tissue rather than occupying a single slot within it.

## ✅ Day 2 wrap-up

- You can now correctly place Prometheus, Grafana, Loki, Elasticsearch/OpenSearch, Jaeger, Tempo, Fluent Bit, and Vector on a signal/pipeline map without confusing their roles.
- You can explain OTLP and articulate, in your own words, why OpenTelemetry's vendor-neutral instrumentation matters for avoiding lock-in.
- You've run a real OTLP payload through a Collector and seen the collection → export shape with your own eyes, not just read about it.
- Tomorrow (Day 3) you zoom into the first signal in depth: logging fundamentals — structure, levels, and what makes a log line actually useful during an incident.

You've now got the map others spend weeks piecing together from scattered blog posts — solid work for one hour.
