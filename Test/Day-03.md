# Day 3 — Logging Fundamentals

You've mapped the observability landscape and seen where OpenTelemetry fits — now it's time to get hands-on with the pillar you'll touch most often day-to-day: logs. Today you'll build the vocabulary and instincts (levels, structure, context) that make every log line you write or read for the rest of this roadmap actually useful, and you'll stand up the local lab you'll keep using through Phase 2.

## 🎯 Learning objectives

- [ ] Describe the anatomy of a good log line (timestamp, level, message, context/fields)
- [ ] Explain each log level (TRACE/DEBUG/INFO/WARN/ERROR/FATAL) and pick the right one for a given scenario
- [ ] Explain why structured (JSON/key-value) logging beats unstructured free-text logging at scale
- [ ] Add a correlation/request ID to log context and explain why it matters for tracing a request across a system
- [ ] Identify at least 3 common logging pitfalls (PII leakage, log spam, unbounded cost) and how to avoid each
- [ ] Have a working local Docker lab that emits and displays logs

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5 min | No notes: write down yesterday's 4 golden signals... wait, that's Day 4 — instead, from memory, write the 3 pillars of observability and one sentence on how OTel relates to them (Day 2 recall) |
| Learn | 20 min | Read/skim log levels, structured vs unstructured logging, and correlation IDs (resources below) |
| Do | 25 min | Build the Docker lab: a tiny app container + a log viewer, emit structured JSON logs with a correlation ID |
| Reinforce | 10 min | Run through 15-20 of today's active recall questions without looking at answers first |

## 📚 Free resources

- **The Twelve-Factor App — Logs** — the classic, short manifesto on treating logs as event streams, not files to manage; shapes how modern log pipelines are designed. (type: docs)
- **Google SRE Book (sre.google/books)** — chapters touching monitoring/logging philosophy give you the "why" behind structured, low-noise logging at scale. (type: book)
- **Grafana Labs blog** — has multiple practical posts on structured logging and "what makes a good log line," written by people who operate Loki at scale. (type: blog)
- **OpenTelemetry docs — Logs** — the emerging standard's take on log data model, severity levels, and how logs attach to trace context. (type: docs)
- **Elastic (elastic.co) — "What is structured logging?" blog/docs** — clear, practical explanation of structured vs unstructured with before/after examples. (type: blog)
- **Python `logging` module docs (docs.python.org)** — even if you don't use Python daily, its level definitions (DEBUG/INFO/WARNING/ERROR/CRITICAL) are the de facto reference other languages mirror. (type: docs)
- **RFC 5424 (The Syslog Protocol)** — the origin of severity levels many systems still map to; skim just the severity table. (type: docs)
- **"12 Factor Logging" talks/videos on YouTube (search: structured logging best practices)** — several conference talks (e.g., from Honeycomb, Datadog engineers) cover PII redaction and log spam pitfalls concretely. (type: video)
- **Fluent Bit / Vector docs (fluentbit.io, vector.dev)** — you'll use one of these in Day 7, but their "getting started" pages already explain why structured JSON input matters for pipelines. (type: docs)

## 🛠️ Hands-on project (free, ~25 min)

**Goal:** stand up a local Docker lab that emits structured logs with correlation IDs, and observe the difference between good and bad logging firsthand.

1. Create a scratch folder and inside it a file `app.py` (or `app.js` if you prefer Node) that is a tiny HTTP server with one endpoint `/order`. On each request, generate a `request_id` (uuid), and log 3 lines: an INFO on entry, a WARN if a random condition trips (simulate "low stock"), and an INFO on completion — each as a single JSON object with fields like `timestamp`, `level`, `request_id`, `message`, `user_id`, `duration_ms`.
2. Write a minimal `Dockerfile` that runs this app, and a `docker-compose.yml` that starts the container and mounts a volume or pipes stdout to a file `logs/app.log`.
3. Run `docker compose up`, then hit the endpoint a few times with `curl localhost:8000/order?user_id=42` (loop it 10-20 times, varying `user_id`).
4. Tail the logs with `docker compose logs -f` or `tail -f logs/app.log`, then pipe one request's 3 lines through `jq` (`docker compose logs | grep <request_id> | jq .`) to confirm you can reconstruct the full lifecycle of a single request using only the `request_id`.
5. Now deliberately break it: change one log line to a plain string like `print("user did something")` with no timestamp/level/id, restart, and compare how much harder that line is to correlate or filter versus your JSON lines.

**Expected outcome:** you can filter your container's log stream down to exactly one request's 3 log lines using `request_id`, and you can see structurally why the unstructured `print` line is useless for that same task.

**Stretch goal:** add a second container (a "downstream" service the `/order` endpoint calls via HTTP) that receives the same `request_id` via a header and logs its own JSON lines — now correlate a request across *two* services just from log text, no tracing tool involved. This is the manual version of what distributed tracing (Day 5, Day 15) automates.

## 🧠 50 questions for active recall

**Q1. What are the four "must-have" fields in a well-formed log line?**
> Timestamp, severity/level, message, and context (structured key-value fields like request_id, user_id, service name). Without a timestamp you can't order events; without a level you can't triage; without context you can't correlate or filter at scale.

**Q2. Why is a timestamp alone not enough — what precision/format issues should you watch for?**
> You need consistent timezone (always UTC) and sufficient precision (usually milliseconds) so that events from different services can be interleaved correctly. Mixed timezones or second-level precision cause misleading event ordering when merging logs from multiple sources.

**Q3. Order the standard log levels from least to most severe.**
> TRACE < DEBUG < INFO < WARN < ERROR < FATAL. Each level up implies "this needs progressively more attention," and most systems let you set a minimum threshold to filter out noise below it.

**Q4. When would you use TRACE versus DEBUG?**
> TRACE is for extremely fine-grained, step-by-step execution detail (e.g., every function entry/exit, every loop iteration) — usually only enabled when hunting a specific bug. DEBUG is coarser diagnostic detail useful during development or targeted troubleshooting (e.g., "cache miss, fetching from DB") without drowning in every single step.

**Q5. What's the defining characteristic of an INFO log?**
> It records normal, expected business/application events (e.g., "order created", "user logged in") that are useful for understanding what the system is doing under normal operation, without indicating a problem.

**Q6. Give an example of something that should be WARN, not ERROR.**
> A retried operation that eventually succeeds (e.g., "DB connection retry 2/3, succeeded") or a deprecated API being called. It signals "this is not normal, keep an eye on it" but didn't cause a failure the user or system needs to react to right now.

**Q7. What distinguishes ERROR from FATAL?**
> ERROR means a specific operation failed but the application/process can continue running (e.g., one request failed). FATAL means the failure is unrecoverable and the process must terminate (e.g., can't bind to a required port, out of memory) — FATAL is typically followed by process exit.

**Q8. Why shouldn't you run production services at DEBUG or TRACE level all the time?**
> Those levels generate enormous volumes of low-value data, driving up storage/ingestion cost and query latency, and making it harder to spot the signal (real problems) in the noise. Best practice is INFO or WARN as the default production threshold, with the ability to dynamically raise verbosity when investigating an issue.

**Q9. What is "structured logging"?**
> Logging events as machine-parseable data (typically JSON or key-value pairs) with named fields, rather than free-form human-readable sentences. Each field (level, request_id, user_id, etc.) is queryable independently instead of requiring text parsing/regex.

**Q10. Give an example of an unstructured log line and its structured equivalent.**
> Unstructured: `"User 42 placed order 991 in 120ms"`. Structured: `{"level":"info","message":"order placed","user_id":42,"order_id":991,"duration_ms":120}`. The structured version lets you filter/aggregate by `user_id` or `duration_ms` directly without regex.

**Q11. Why does structured logging matter more as system scale grows?**
> At small scale a human can eyeball a log file; at scale (many services, high request volume) you need machines — log aggregators and query engines — to search, filter, and aggregate millions of lines per minute, which requires consistent, parseable fields rather than free text.

**Q12. What's a downside of structured (JSON) logging?**
> It's less human-readable at a glance in a raw terminal (verbose, harder to skim) and has slightly higher per-line size/overhead than a terse text line — usually mitigated with pretty-printers or dashboards for humans, while machines consume the raw JSON.

**Q13. What is a "correlation ID" (a.k.a. request ID / trace ID)?**
> A unique identifier generated when a request enters a system (or at the client) and propagated through every downstream call and log line related to that request, so you can reconstruct the full path and timeline of that single request across services just by filtering on the ID.

**Q14. How does a correlation ID typically get propagated across services?**
> It's generated (or extracted if already present) at the entry point, attached to the logging context for that request, and passed to downstream services via an HTTP header (e.g., `X-Request-ID`) or message metadata, so each service logs it and passes it further along.

**Q15. Why is a correlation ID considered a bridge between logging and tracing?**
> Distributed tracing (Day 5, Day 15) formalizes this same idea with trace IDs and span IDs generated and propagated automatically by instrumentation; manually adding a correlation ID to logs is effectively a lightweight, DIY version of that same correlation mechanism.

**Q16. What is "log context" beyond the correlation ID?**
> Any additional structured fields attached consistently to log lines for a given scope — e.g., service name, environment, host, user_id, session_id — that let you slice and filter logs along multiple useful dimensions, not just by request.

**Q17. What's the risk of logging PII (personally identifiable information)?**
> It can violate privacy regulations (GDPR, CCPA, etc.), expose sensitive user data to anyone with log access (which is often broader than production DB access), and create compliance/security liability if logs are breached or improperly retained.

**Q18. Name three examples of data that commonly leak into logs as PII by accident.**
> Full names, email addresses, raw payment/card numbers, phone numbers, IP addresses (in some jurisdictions), and full request/response bodies that happen to contain user-submitted personal data.

**Q19. What are two mitigation techniques for avoiding PII in logs?**
> (1) Redact or hash sensitive fields before logging (e.g., log `user_id` instead of email, or a hashed/truncated identifier). (2) Use allow-listing of fields to log rather than dumping entire objects/payloads, and add automated scanning/linting for common PII patterns in CI.

**Q20. What is "log spam" and why is it a problem?**
> Log spam is excessive, repetitive, or low-value log output (e.g., logging inside a tight loop, or the same warning on every request) that drowns out meaningful signals, increases cost, and makes it hard to find the log line that actually matters during an incident.

**Q21. Give two techniques to control log spam.**
> Rate limiting/sampling (e.g., log only 1 in every N occurrences of a repeated event), and log level discipline (moving noisy diagnostic detail to DEBUG/TRACE so it's off by default in production, only enabled when needed).

**Q22. Why is logging cost more than "storage" — what else drives it up?**
> Cost includes ingestion/processing compute in the log pipeline (parsing, indexing), network egress if shipped to an external SaaS, index/storage volume, and query-time compute (search performance often degrades with high-cardinality unindexed text). All rise faster than raw log volume alone once you add pipelines and indexing.

**Q23. What is "cardinality" in a logging context, and why does high cardinality drive cost?**
> Cardinality is the number of distinct values a field can take (e.g., `request_id` has extremely high cardinality — nearly one value per request). Indexing high-cardinality fields is expensive because the index must track a huge number of unique keys; systems like Loki intentionally avoid full-text indexing of high-cardinality fields for this reason (previewed in Day 8/Day 22).

**Q24. Why might a team choose to log less rather than more, contrary to the instinct to "log everything just in case"?**
> Because every log line has a marginal storage/processing/query cost, and excessive volume makes it harder (not easier) to find relevant information during an incident; the 80/20 approach is to log the ~20% of events (state changes, errors, boundary crossings) that carry ~80% of the diagnostic value.

**Q25. What does it mean for logging to be "structured but still readable"? How do teams achieve both?**
> It means emitting JSON (or key-value) as the source of truth for machines, while using a local log viewer, pretty-printer (e.g., `jq`), or a UI (like Grafana/Kibana) to render it in a human-friendly way, rather than choosing one format to satisfy both audiences directly in the raw stream.

**Q26. Compare: what's the primary difference between a "log" and a "metric"?**
> A log is a discrete, timestamped event with rich, often unique context (e.g., "this exact request failed because X"); a metric is a numeric measurement aggregated over time (e.g., request count, error rate) optimized for trends and alerting rather than individual event detail.

**Q27. Compare: what's the primary difference between a "log" and a "trace"?**
> A log records what happened at a point in time from one component's perspective; a trace records the causal, timed relationship of an entire request's journey across multiple components (spans), showing where time was spent and how services relate to each other for that one request.

**Q28. Scenario: your service logs `"Error occurred"` with no other fields, at ERROR level, thousands of times per minute. What's wrong and how do you fix it?**
> The message lacks context (no error type, no identifying fields) making it impossible to diagnose or count distinct issues, and volume suggests it may be a symptom of using ERROR for something recoverable/expected — fix by adding structured fields (error type/code, request_id, relevant params) and reassessing whether this specific case truly warrants ERROR versus WARN.

**Q29. Scenario: two microservices both log `"Payment failed"` at ERROR level but neither includes a request/correlation ID. What operational problem does this cause during an incident?**
> You cannot determine whether the two ERROR lines are from the same failed request (a causal chain) or two unrelated failures, forcing manual timestamp-matching guesswork instead of a reliable, precise join — exactly the gap correlation IDs are meant to close.

**Q30. Scenario: a developer adds `log.debug(f"user object: {user}")` where `user` is a full user object including email and address. What are the two distinct problems here?**
> First, a PII leak — the email/address ends up in log storage, possibly with broader access than the source DB. Second, even at DEBUG, dumping whole objects is verbose and brittle (schema changes silently change log shape) — better to log specific, intentional fields like `user_id`.

**Q31. Predict the output: if your production log level threshold is set to WARN, and your code calls `log.info("cache warm complete")`, what happens?**
> Nothing is emitted — INFO is below the WARN threshold, so that log call is filtered out (typically cheaply, without even formatting the string, in well-designed loggers) and never reaches your log pipeline or storage.

**Q32. Predict the output: a request enters service A (generates request_id=abc123), calls service B, but the header carrying request_id is dropped by a proxy in between. What do B's logs look like relative to A's?**
> B either generates its own new request_id (breaking the ability to correlate A and B's logs for that request) or logs with no request_id at all — either way, filtering by `abc123` will only surface A's log lines, silently losing the downstream picture unless you notice the missing propagation and fix the proxy config.

**Q33. Predict the output: you log `duration_ms` as a string field (`"120ms"`) instead of a number (`120`) in your structured logs. What breaks later?**
> Any downstream aggregation, sorting, or numeric filtering (e.g., "show me all requests over 500ms," or computing an average) on that field will fail or require ugly parsing/casting, because the log query engine sees it as text, not a number — a good reminder that structured logging requires consistent, typed fields, not just JSON syntax.

**Q34. Why do most logging frameworks let you set the level threshold per-logger or per-module rather than globally only?**
> Because different parts of a system have different diagnostic value/noise tradeoffs at any given time — e.g., you might want DEBUG only for the payment module while investigating an issue there, without paying the noise/cost of DEBUG everywhere else.

**Q35. What is "log sampling" and when is it appropriate?**
> Log sampling means intentionally recording only a subset of events (e.g., 1 in 100 successful requests) rather than every occurrence, appropriate for very high-volume, low-diagnostic-value repetitive events (like successful health checks) where full fidelity isn't worth the cost — while still logging 100% of errors/warnings.

**Q36. Why should ERROR and FATAL logs generally NOT be sampled?**
> Because each error may represent a distinct root cause or affected user, and sampling could hide a rare-but-critical failure; the cost of missing an error is typically far higher than the storage cost of keeping all of them, so full fidelity is kept for high-severity events even while sampling low-severity ones.

**Q37. What's a "log schema" and why is agreeing on one across a team/organization valuable?**
> A log schema is a shared convention for field names and types (e.g., always `request_id` not sometimes `req_id`/`correlation_id`) across all services. It's valuable because it lets you write one dashboard/query/alert that works across every service, instead of maintaining per-service translations.

**Q41 ordering note skipped — continuing sequentially.**

**Q38. What does "log as an event stream" (from the Twelve-Factor App) mean in practice?**
> Applications should write logs as an unbuffered stream of events to stdout/stderr, not manage log files, rotation, or routing themselves — that responsibility is delegated to the execution environment (container runtime, log shipper) which can then route the stream wherever it's needed (file, aggregator, multiple destinations).

**Q39. Why does the "log as event stream" philosophy matter especially in containerized environments?**
> Containers are ephemeral — if an app writes to a local log file, that file disappears when the container is destroyed/rescheduled. Writing to stdout lets the container runtime (and a log collector like Fluent Bit, covered Day 7) capture and ship logs before the container's filesystem vanishes.

**Q40. What's the difference between "log level" and "log verbosity" as commonly confused terms?**
> Log level refers to the severity classification of an individual event (INFO, ERROR, etc.); verbosity usually refers to the configured threshold controlling which levels are actually emitted/displayed at runtime. You "set the verbosity" to control which "levels" you see.

**Q41. Scenario: after adopting structured JSON logs, your team's dashboards show a spike in log volume with no code changes. What are 2 likely causes to investigate?**
> (1) A bug causing an event to log inside a loop or retry path far more often than intended (log spam). (2) A traffic spike or a new client hammering an endpoint, which is legitimately increasing INFO-level "request received" logs proportionally — you'd distinguish these by checking if the spike correlates with request volume/metrics or is isolated to one log message/field.

**Q42. Why is `service_name` (or equivalent) an important field to always include in structured logs, even for a single-service app?**
> Because logs are almost always eventually aggregated centrally alongside logs from other services (Day 8), and without a `service_name` field you can't filter to "just this service" or attribute an error to its source once everything is in one index.

**Q43. What's the tradeoff between logging a full stack trace on every ERROR versus just the error message?**
> A full stack trace gives you the exact code path for debugging but significantly increases log line size (and thus cost) and can be noisy if the same error recurs often; a common practice is to always include the stack trace for ERROR/FATAL (since debugging value there is high) while omitting it for WARN.

**Q44. Why do many teams standardize on ISO 8601 / RFC 3339 timestamps in UTC for logs?**
> It's an unambiguous, sortable, human- and machine-readable format (e.g., `2026-07-16T14:32:01.123Z`) that avoids locale/timezone ambiguity, letting logs from different servers/timezones be correctly interleaved and compared without conversion errors.

**Q45. What is the relationship between log levels and alerting — should you alert on every ERROR log?**
> Not necessarily — alerting (Day 19) should be based on symptoms that matter (rate of errors crossing a threshold, or specific critical failures), because alerting on every single ERROR line can cause alert fatigue if errors are frequent but individually low-impact; the log level is a triage signal for humans reading logs, not automatically an alerting trigger.

**Q46. In your Docker lab, if you `grep` for a `request_id` across two containers' combined log output and get zero matches for the second container, what are two possible explanations?**
> (1) The correlation ID isn't being propagated to the downstream service (e.g., missing header forwarding) — a code/config bug. (2) The two containers' logs aren't actually being combined in your view (e.g., you only tailed one container's stream) — a lab/tooling issue rather than an application bug. Diagnosing requires checking both the header-forwarding code and your log-viewing command.

**Q47. Why is FATAL sometimes omitted entirely from a language's default logging framework (e.g., Python's `logging` has no built-in FATAL distinct from CRITICAL)?**
> Because the boundary between "ERROR that's really severe" and "process must exit" is often handled by the application explicitly calling exit/panic rather than needing a separate log level — many frameworks alias FATAL to CRITICAL and let the calling code decide whether to terminate the process after logging it.

**Q48. What's a practical rule of thumb for deciding whether an event belongs at INFO or WARN?**
> Ask: "does this represent normal, expected system behavior, or something unexpected that a human might want to investigate even though nothing failed?" If it's expected (order placed, cache refreshed), it's INFO; if it's unexpected-but-handled (retry succeeded, fallback used, deprecated field present), it's WARN.

**Q49. Why does adding structure (JSON/fields) to logs also make them more useful as an input to the log pipelines and storage systems you'll study in Days 7-9?**
> Because pipelines like Fluent Bit/Vector and storage/query engines like Loki or Elasticsearch can parse and index structured fields directly, enabling fast filtering/aggregation by field (e.g., `service_name="checkout" AND level="error"`) — capabilities that are slow or impossible against unstructured free text at scale.

**Q50. Tie it together: explain in one or two sentences why "anatomy of a log line" (timestamp, level, message, context) is the foundational skill for everything else in Phase 2 (log pipelines, storage, querying).**
> Every downstream capability — shipping logs reliably, indexing them efficiently, and querying/alerting on them precisely — depends entirely on the log line being well-formed at the source: a consistent timestamp for ordering, a level for filtering severity, and structured context fields for correlation and search. Get this anatomy right today, and Days 7-9 become about tooling; get it wrong, and no pipeline or query language can fully compensate.

## ✅ Day 3 wrap-up

- You can now write (and critique) a log line with proper anatomy: timestamp, level, message, and structured context fields
- You can choose the correct log level for a given event and explain why over-logging at DEBUG/TRACE in production is costly
- You've propagated a correlation ID across at least one request in your own Docker lab and used it to reconstruct a request's lifecycle
- You know the top pitfalls (PII, spam, cardinality/cost) to watch for as you instrument real systems going forward
- Tomorrow (Day 4) you shift pillars entirely: metrics fundamentals and the Four Golden Signals — the numeric, aggregate view that complements the event-level detail you mastered today.

Nice work today — structured logging with correlation IDs is a skill that pays off in literally every incident you'll ever debug. Keep that lab running; you'll extend it in Days 7-9.
