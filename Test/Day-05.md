# Day 5 — Tracing Fundamentals

You've spent four days building the foundation: telling observability apart from monitoring, mapping the landscape, and getting fluent in logs and metrics. Today you add the third pillar — distributed tracing — which is the only tool that shows you the *shape* of a request as it hops across services, and it's the piece that makes microservice debugging tractable instead of miserable.

## 🎯 Learning objectives

- [ ] Define a span and a trace, and explain how spans nest into parent/child relationships
- [ ] Explain what a trace ID and span ID are and how they uniquely identify a request's journey
- [ ] Describe context propagation and why it's required for traces to work across service boundaries
- [ ] Explain the W3C Trace Context standard (`traceparent`/`tracestate` headers) and why it matters for interoperability
- [ ] Distinguish span attributes, span events, and span status, and know when to use each
- [ ] Explain sampling at a conceptual level and why you can't trace 100% of requests at scale
- [ ] Decide, given a debugging scenario, whether a trace, a log, or a metric is the right tool

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5 min | Blind-recall from memory: the Four Golden Signals (Day 4) and one thing structured logging gave you that plain text didn't (Day 3). Write both from memory before checking notes. |
| Learn | 20 min | Read/watch the trace concept explainer + W3C Trace Context spec skim (resources below). Focus on: span anatomy, parent/child, trace ID propagation, sampling basics. |
| Do | 25 min | Hands-on project: run Jaeger locally, generate a multi-service trace, inspect spans and the propagated trace ID in the UI. |
| Reinforce | 10 min | Active recall — answer 15-20 of today's 50 questions cold, then check answers. Flag any you missed for tomorrow's warm-up. |

## 📚 Free resources

- **OpenTelemetry docs — "Traces" concepts page** — the canonical, vendor-neutral explanation of spans, trace context, and the trace data model; this is the vocabulary you'll use for the rest of the roadmap. (type: docs)
- **W3C Trace Context specification (w3.org)** — short, precise spec defining `traceparent` and `tracestate` headers; skim the "Traceparent Header Field Values" section to see the actual ID format. (type: docs)
- **Jaeger documentation (jaegertracing.io)** — official docs for the tracing backend you'll run today; the "Getting Started" and "Architecture" pages are the most useful. (type: docs)
- **Google's Dapper paper (research.google, free PDF)** — the original paper that inspired Jaeger, Zipkin, and OpenTelemetry tracing; short and foundational, worth skimming the intro and diagrams. (type: paper)
- **Honeycomb blog — posts on distributed tracing and "wide events"** — practitioner-written, opinionated, and excellent at explaining *why* tracing matters, not just how it works. (type: blog)
- **Grafana Labs blog — Tempo and tracing tutorials** — practical walkthroughs connecting tracing concepts to a real backend (Tempo), useful for seeing traces alongside metrics/logs. (type: blog)
- **CNCF YouTube channel — OpenTelemetry and Jaeger talks (KubeCon recordings)** — free conference talks that show real production tracing setups and trade-offs. (type: video)
- **Google SRE Book (sre.google/books)** — free online; while not tracing-specific, the "Monitoring Distributed Systems" chapter frames why request-level visibility matters at scale. (type: book)
- **Zipkin documentation (zipkin.io)** — the other major open tracing backend; useful for comparing its data model to Jaeger's and seeing how similar the concepts are across tools. (type: docs)

## 🛠️ Hands-on project (free, ~25 min)

**Goal:** Stand up Jaeger locally, generate a real multi-span trace, and read the trace ID propagation with your own eyes.

1. Run Jaeger's all-in-one Docker image (no signup, fully local):
   ```
   docker run -d --name jaeger \
     -p 16686:16686 -p 4318:4318 \
     jaegertracing/all-in-one:latest
   ```
2. Open `http://localhost:16686` — this is the Jaeger UI, currently empty.
3. Generate a trace without writing an app: use the OpenTelemetry Demo's trace generator, OR simpler — use `curl` with a manually-crafted `traceparent` header against any two local endpoints you control (even two `python -m http.server` style scripts wrapped with a tiny OTel SDK snippet), OR fastest path: install the `opentelemetry-instrument` CLI wrapper for Python (`pip install opentelemetry-distro opentelemetry-exporter-otlp`, then `opentelemetry-bootstrap -a install`) and run a trivial Flask/requests script that calls itself twice, exporting to `http://localhost:4318`.
4. In the Jaeger UI, find your trace. Click into it and identify: the root span, at least one child span, the trace ID (same across all spans), each span's own span ID, the parent-child indentation, and the duration bars.
5. Add a manual span **attribute** (e.g., `user.id=42`) and a span **event** (e.g., `cache.miss`) in your script if you're comfortable editing the code, re-run, and find them in the UI's span detail panel.

**Expected outcome:** You see one trace containing 2+ spans, sharing one trace ID, with a visible parent/child waterfall and durations.

**Stretch goal:** Force an error in one span (raise an exception, catch it, set span status to ERROR) and confirm Jaeger visually flags that span in red — this is exactly how you'd spot a failing downstream call in production.

## 🧠 50 questions for active recall

**Q1. What is a span, in one sentence?**
> A span is a single unit of work in a trace — it represents one operation (e.g., an HTTP call, a DB query, a function execution) with a start time, duration, and metadata describing what happened.

**Q2. What is a trace?**
> A trace is the complete record of a single request's journey through a system, represented as a collection of spans linked together by a shared trace ID, typically forming a tree or DAG of parent/child relationships.

**Q3. How does a trace relate to a span, structurally?**
> A trace is made up of one or more spans; the first span created (with no parent) is the root span, and every other span in that trace is a descendant of it, directly or transitively.

**Q4. What is a trace ID?**
> A globally unique identifier (typically a 128-bit value) generated once for a request, then propagated to every span created while handling that request, so all spans across all services can be grouped back into one trace.

**Q5. What is a span ID?**
> A unique identifier (typically 64-bit) for one specific span within a trace, distinguishing it from every sibling and ancestor span that shares the same trace ID.

**Q6. Why do span IDs need to be unique only within a trace, but trace IDs need to be globally unique?**
> Because spans are grouped and queried by trace ID — collisions there would merge unrelated requests into one trace, corrupting the whole picture; span IDs only need to disambiguate within that one trace's tree, so a smaller ID space (64-bit vs 128-bit) is fine.

**Q7. What is a parent-child relationship between spans?**
> It's the causal link showing that one operation (the child) was invoked from within another (the parent) — e.g., an API handler span is the parent of the database-query span it triggers, and this nesting is what lets a trace visually reconstruct call structure.

**Q8. Can a span have more than one child?**
> Yes — if an operation fans out into multiple parallel or sequential downstream calls (e.g., one API request calling three separate microservices), all three resulting spans are children of that same parent span.

**Q9. Can a span have more than one parent?**
> Not in the classic tree model most tracing systems use; each span has exactly one parent span ID (or none, if it's the root). Some richer models (OpenTelemetry "links") allow a span to reference other spans for fan-in scenarios without literally becoming a second parent.

**Q10. What does context propagation mean in tracing?**
> The mechanism by which the trace ID, parent span ID, and sampling decision are carried forward from one process/service to the next — typically via HTTP headers, message metadata, or gRPC metadata — so that a new span created in the downstream service knows which trace and parent it belongs to.

**Q11. Why is context propagation the hardest part of getting distributed tracing to actually work?**
> Because it requires every hop in the request path — every service, every library, every queue, every proxy — to correctly read incoming context and forward it outward; a single service that drops or fails to propagate the headers breaks the trace into disconnected fragments.

**Q12. What happens to a trace if one service in the call chain doesn't propagate context?**
> The trace "breaks" — spans created after that point start a brand new trace ID (or become orphaned) instead of linking back to the original, so you end up with two or more disconnected partial traces instead of one complete picture.

**Q13. What is the W3C Trace Context standard?**
> A W3C specification that standardizes how trace context (trace ID, parent span ID, trace flags) is represented and passed between services via two HTTP headers: `traceparent` and `tracestate`.

**Q14. What does a `traceparent` header contain?**
> Four fields separated by hyphens: a version, the trace ID (32 hex chars), the parent span ID (16 hex chars), and trace flags (e.g., whether this trace is sampled), e.g. `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`.

**Q15. What is the `tracestate` header for?**
> It carries vendor-specific or system-specific extra trace state that different tracing tools/vendors need to pass along, without polluting the standardized `traceparent` fields — it's the extensibility escape hatch.

**Q16. Why did the industry need a W3C standard for trace context instead of everyone doing their own thing?**
> Before it existed, every vendor (Zipkin, Jaeger, various APM tools) used incompatible propagation header formats, so a request passing through services instrumented with different tools would break the trace at each boundary; a shared standard lets heterogeneous systems interoperate.

**Q17. What are span attributes?**
> Key-value metadata attached to a span describing static facts about the operation — e.g., `http.method=GET`, `http.status_code=200`, `db.statement=SELECT * FROM orders` — used for filtering, grouping, and understanding what the span represents.

**Q18. What are span events?**
> Timestamped, named occurrences that happened *during* a span's lifetime — e.g., "cache miss", "retry attempt 2", "validation failed" — think of them as structured log lines scoped to a specific span, each with its own timestamp and optional attributes.

**Q19. How do span events differ from span attributes?**
> Attributes describe the span as a whole and don't have their own timestamp (they apply span-wide); events are point-in-time occurrences within the span's duration and each carries its own timestamp, letting you see *when* within the operation something happened.

**Q20. What is span status?**
> A field indicating whether the operation the span represents succeeded, failed, or is unset/unknown (commonly OK, ERROR, UNSET) — it's what tracing UIs use to visually flag failing spans, typically in red.

**Q21. If an HTTP call returns a 500, should you set span status to ERROR even if no exception was thrown in your code?**
> Yes — span status should reflect whether the operation semantically failed, not just whether an exception propagated; a 500 response is a failure regardless of whether your client code chose to treat it as an exception.

**Q22. Give an example of a good span attribute vs a bad one.**
> Good: `http.route=/api/orders/{id}` (bounded set of values, useful for grouping). Bad: `http.full_url_with_query_string` containing raw user IDs or session tokens as a single high-cardinality unbounded string — that bloats storage and can leak sensitive data.

**Q23. Why shouldn't you sample 100% of traces in a high-traffic production system?**
> Because every span consumes storage, network bandwidth, and processing cost proportional to traffic volume; at high request rates, tracing everything can become prohibitively expensive and can itself add latency overhead, so systems selectively record only a subset of traces.

**Q24. What is a sampling decision?**
> The determination — made once per trace, ideally at or near the start — of whether that trace's spans will be recorded and exported, versus dropped; this decision is propagated via trace flags so every service downstream honors the same choice consistently.

**Q25. What is head-based sampling?**
> A sampling strategy where the decision to keep or drop a trace is made at the very start (the "head") of the request, before any spans are even generated, often using a simple probability (e.g., sample 1% of requests) — cheap but can miss rare error traces.

**Q26. What is tail-based sampling?**
> A strategy where the decision to keep a trace is made *after* the full trace has been assembled (the "tail"), allowing you to selectively keep interesting traces — e.g., all traces with errors or high latency — at the cost of needing to buffer and evaluate complete traces before deciding.

**Q27. Why is tail-based sampling more operationally complex than head-based sampling?**
> It requires buffering all spans of a trace somewhere (often a collector) until the full trace is complete or a timeout passes, so a decision can be made using complete information — this needs more memory/infrastructure and adds latency to the decision versus head-based sampling's instant per-request coin-flip.

**Q28. Why must the sampling decision be propagated consistently across all services in a trace?**
> If one service decides to sample and a downstream service independently decides not to (or vice versa), you get a trace with gaps — some services' spans missing — which produces a misleading, incomplete picture; the "sampled" flag in the trace context ensures every hop makes the same choice.

**Q29. When does a trace beat a log for debugging a problem?**
> When the problem is about *where time went* or *which downstream call failed* across multiple services for a single request — e.g., "why did this specific checkout take 3 seconds" — a trace shows the causal, timed breakdown across service boundaries that scattered logs can't easily reconstruct.

**Q30. When does a log beat a trace?**
> When you need the exact content of what happened — a stack trace, a specific error message, a payload value, or free-form debugging detail — traces are structured around timing and causality, not rich arbitrary content, so logs remain better for "what exactly went wrong here."

**Q31. When does a metric beat both a trace and a log?**
> When you need to know aggregate system health cheaply over time — e.g., "is error rate trending up across the whole fleet" — metrics are pre-aggregated and cheap to store/query at scale, whereas traces and logs are per-request and expensive to scan in bulk for trend detection.

**Q32. Scenario: a customer reports "checkout is slow sometimes." Which pillar do you reach for first, and why?**
> Metrics first — check the latency percentiles/Golden Signals for the checkout service to confirm it's really slow and see how often; once confirmed, pull a trace for a slow request (ideally sampled or tail-sampled for high latency) to see exactly which downstream span is the bottleneck.

**Q33. Scenario: a trace shows a single span taking 4 of a request's 4.2 seconds, with no child spans and no error. What's your next step?**
> Since tracing shows *that* it's slow but not necessarily *why* (no further span breakdown), go to the logs for that specific service/request — using the trace ID as a correlation key — to find what that code path was doing during those 4 seconds (e.g., waiting on a lock, doing CPU-heavy work, blocked on an external call not yet instrumented).

**Q34. What does "correlating logs and traces via trace ID" mean in practice?**
> Emitting the current trace ID (and span ID) as a field in every log line, so you can jump from a trace in your tracing UI straight to the exact log lines produced during that specific request — turning trace ID into the shared key across pillars.

**Q35. Why is a trace ID a good correlation key across pillars but a metric label is usually a bad one?**
> Trace IDs are unique per request by design, so they're perfect for pinpointing one specific occurrence (logs, traces) but terrible as metric labels because metrics need low-cardinality, reusable label values to stay aggregable and cheap — a per-request ID as a label would explode cardinality (a concept you'll cover on Day 22).

**Q36. What is a root span?**
> The first span created for a trace, with no parent span ID; it typically represents the entry point of the request (e.g., the incoming HTTP request at the edge/gateway) and its duration usually represents the total end-to-end time of the whole trace.

**Q37. If you see two root spans with the same trace ID, what does that suggest?**
> That's unusual/invalid in the standard model — normally only one span per trace has no parent. Seeing two suggests either a context propagation bug (two independent request entry points accidentally sharing a trace ID) or a misconfigured/buggy instrumentation library.

**Q38. What is a span's duration, and how is it computed?**
> The elapsed wall-clock time the operation took, computed as the difference between the span's end timestamp and its start timestamp — visualized in tracing UIs as a horizontal bar whose length is proportional to this duration.

**Q39. In a trace waterfall view, what does it mean if a child span's bar extends past its parent's bar?**
> That would indicate a timing/instrumentation bug or clock skew between services — logically, a child operation happens within its parent's lifetime, so a child ending after its parent ends is inconsistent and usually signals broken span start/end capture or unsynchronized clocks across hosts.

**Q40. Predict the output: Service A calls Service B via HTTP, and Service B forwards the `traceparent` header unchanged to Service C without updating the parent span ID field. What trace structure results?**
> Service C's spans will incorrectly appear as direct children of Service A's span (since the parent ID field never got updated to reference B's span), effectively hiding Service B from the trace hierarchy — the trace ID stays correct, but the causal tree is wrong.

**Q41. Predict the output: a service crashes mid-request after starting a span but before it's able to export/end that span. What do you see in the tracing backend?**
> Typically nothing for that span — most tracers only export completed (ended) spans, so an in-flight span that never calls "end" is simply never sent to the backend, leaving a gap in the trace with no visual indication that work was even attempted there (unless the tracer uses streaming/partial export).

**Q42. Predict the output: you sample only 1% of traces head-based, and a rare bug only occurs in 0.01% of requests and always causes an error. How likely are you to see a trace for it?**
> Very unlikely — since sampling is independent of whether the request errors, roughly only 1% of that already-rare 0.01% of failing requests will be captured, meaning you'll likely need many thousands of occurrences before catching one on trace, which is exactly the case tail-based/error-biased sampling is designed to fix.

**Q43. What's the difference between a span and a transaction in APM tooling vocabulary?**
> Different vendors use different terms, but "transaction" (New Relic, Elastic APM) commonly refers to what OpenTelemetry calls the root span or a top-level entry-point span for a service, while "span" refers to any unit of work including nested children — the underlying concept (a timed operation) is the same.

**Q44. Why do most tracing systems use 128-bit trace IDs instead of something shorter?**
> To make collisions statistically negligible even at very high request volumes across many independent services generating IDs without central coordination — a smaller ID space would risk two unrelated requests accidentally sharing a trace ID and getting merged.

**Q45. What is a span kind (e.g., SERVER, CLIENT, INTERNAL, PRODUCER, CONSUMER)?**
> A classification of what role a span plays in a distributed operation — e.g., SERVER for the receiving side of an RPC, CLIENT for the initiating side — used by tracing backends to correctly render request/response pairs and messaging flows in visualizations.

**Q46. Why does OpenTelemetry distinguish CLIENT and SERVER spans for the same RPC call instead of using one span?**
> Because the client-side and server-side of a call have different timing (network latency is included in the client's view but the server only sees from when it received the request), different attributes (e.g., client sees the target address, server sees the caller), and separating them lets you isolate network overhead from server processing time.

**Q47. What's the minimum information a downstream service needs to correctly continue a trace?**
> The trace ID, the parent span ID (which becomes this service's new span's parent reference), and the sampling flag — all of which the W3C `traceparent` header conveniently bundles into one string.

**Q48. Why can't you retroactively add tracing to a system just by turning on a tracing backend?**
> A tracing backend only stores and visualizes spans it receives — it can't manufacture data your services never emitted; you need instrumentation (auto or manual) in every service to generate spans and propagate context before any trace data exists to show.

**Q49. Scenario: your trace shows spans for Service A and Service B but nothing for Service C, which you know was called in between. What are two likely causes?**
> Either (1) Service C isn't instrumented at all, so it never creates or exports spans, or (2) Service C is instrumented but context propagation is broken on the hop into or out of it (e.g., an async queue or a proxy that strips the `traceparent` header), so its spans exist but under a different/disconnected trace ID.

**Q50. Why is tracing described as showing the "shape" of a request while metrics show "trends" and logs show "detail"?**
> Because a trace's core value is structural — it reveals the causal graph of which services were called, in what order, nested how, and for how long, which is exactly the topology information neither a scalar metric (aggregated over many requests) nor a standalone log line (isolated to one moment in one service) can convey on its own.

## ✅ Day 5 wrap-up

- You can now explain spans, traces, parent/child relationships, and trace/span IDs precisely enough to read any tracing UI with understanding.
- You understand why context propagation and the W3C Trace Context standard are the load-bearing plumbing that makes multi-service tracing possible at all.
- You can reason about span attributes vs. events vs. status, and you have a working mental model for when to sample and why 100% tracing isn't the default at scale.
- You can now decide, given a real debugging scenario, whether a trace, log, or metric is your fastest path to the answer.
- Tomorrow (Day 6) you'll go deeper into structured logging in practice — building on today's insight that trace IDs are the glue that ties logs back to the trace they belong to.

You've now got all three observability pillars conceptually in place — that's real progress. Keep the momentum going.
