# Day 6 — Structured Logging in Practice

You've spent the last three days building a mental model of what observability is and where logs, metrics, and traces each fit. Today you get your hands dirty with the pillar you'll touch most often: logs. By the end of this hour you'll know how to make an application emit logs that are actually useful — machine-parseable, context-rich, and cheap to query later.

## 🎯 Learning objectives

- [ ] Explain what "structured logging" means and why JSON-per-line beats free-text log lines
- [ ] Pick an appropriate logging library for a given language (structlog/stdlib `logging`, zap/zerolog/slog, pino/winston) and state a key tradeoff for each
- [ ] Design a consistent field schema (timestamp, level, message, service, request_id, trace_id) you'd reuse across services
- [ ] Add contextual fields (request_id, trace_id, user/tenant id) to every log line without repeating yourself
- [ ] Use log levels correctly in code and justify a level choice for a given event
- [ ] Explain why string interpolation/concatenation in log calls is a performance and security anti-pattern
- [ ] Emit logs to stdout following 12-factor app principles, and write a basic test asserting on log output

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5 min | No notes: write down the Four Golden Signals and the "logs vs metrics vs traces" distinction from Days 2–4. Then define "structured logging" in one sentence from memory. |
| Learn | 20 min | Skim one library's docs (pick your language) for structured/JSON output; read the 12-factor "Logs" section; note the standard field schema pattern (timestamp, level, msg, service, context fields). |
| Do | 25 min | Hands-on project below: instrument a tiny script to emit JSON logs with request context, at multiple levels, to stdout. |
| Reinforce | 10 min | Run through as many active-recall questions as you can from the 50 below, checking answers as you go. |

## 📚 Free resources

- **The Twelve-Factor App — Logs** (12factor.net/logs) — the canonical short read on why apps should treat logs as event streams to stdout, not manage log files themselves. (type: docs)
- **structlog documentation** (structlog.readthedocs.io) — Python library docs with excellent examples of bound context and JSON rendering. (type: docs)
- **Python `logging` cookbook** (docs.python.org/3/howto/logging-cookbook.html) — official recipes including a JSON formatter pattern if you don't want a third-party lib. (type: docs)
- **Uber's zap README** (github.com/uber-go/zap) — explains the performance rationale behind structured, allocation-free logging in Go; great "why fast logging matters" read. (type: docs)
- **zerolog README** (github.com/rs/zerolog) — a Go alternative with a clean "zero-allocation JSON logger" pitch; good compare/contrast with zap. (type: docs)
- **pino documentation** (getpino.io) — Node.js JSON logger docs; explains why pino avoids synchronous string formatting for speed. (type: docs)
- **Google SRE Book — free online** (sre.google/books) — see the chapters touching monitoring/practical alerting for how logs feed the broader observability picture. (type: book)
- **Grafana Labs blog — "What is structured logging"** (grafanalabs.com/blog) — practical, vendor-neutral explanation aimed at people about to ship logs into Loki/Elasticsearch. (type: blog)
- **Dave Cheney — "Let's talk about logging"** (dave.cheney.net) — a widely cited blog post on log levels and why most apps only need two (info and debug/error). (type: blog)

## 🛠️ Hands-on project (free, ~25 min)

Goal: turn an unstructured `print`/`console.log`-style script into one emitting clean, structured JSON logs with request context — no cloud services or paid tools required.

1. Pick any small script idea you already understand, e.g. a fake "order service" function `process_order(order_id, user_id)` that does a couple of steps and can randomly fail.
2. Add a structured logging library for your language of choice (`pip install structlog`, or use stdlib `logging` with a JSON formatter; `go get go.uber.org/zap`; `npm install pino`).
3. Configure the logger to write JSON to **stdout** (not a file) — this is the 12-factor pattern; let the platform handle log routing.
4. Generate a `request_id` (e.g. `uuid4()`) at the start of each simulated "request" and bind it to the logger so every subsequent log line in that request automatically includes it, without you passing it manually to each call.
5. Add at least 4 log lines across different levels: one `debug` (e.g. "computing discount"), one `info` (e.g. "order processed"), one `warning` (e.g. "retrying payment"), and one `error` (e.g. "order failed") — trigger the error path deliberately (simulate a failure for one order_id).
6. Include structured fields, not interpolated strings: e.g. `log.info("order_processed", order_id=order_id, amount=42.50)` rather than `log.info(f"Order {order_id} processed for ${amount}")`.
7. Run the script and pipe output through `jq` (or just eyeball it) to confirm every line is valid, parseable JSON with consistent field names.

Expected outcome: a terminal full of JSON lines, each containing `timestamp`, `level`, `event`/`message`, `request_id`, and event-specific fields — and you can `grep`/`jq`-filter by `request_id` to reconstruct one request's full story.

Stretch goal: add a `trace_id` field too (just a second random ID for now — you'll wire up real distributed tracing on Day 15–17), and write one small test (e.g. with `pytest`/`capsys`, Go's `httptest` + buffer, or Jest capturing stdout) that asserts the log output contains valid JSON with the expected `level` field for the error path.

## 🧠 50 questions for active recall

**Q1. What is structured logging, in one sentence?**
> Structured logging means emitting log entries as data (typically JSON key-value pairs) with a consistent schema, rather than free-form human-readable sentences, so machines can parse, filter, and aggregate them reliably.

**Q2. Why is `logger.info(f"User {user_id} logged in")` worse than `logger.info("user_login", user_id=user_id)`?**
> The f-string version embeds the value in unstructured text, so extracting `user_id` later requires fragile regex parsing; the structured version emits `user_id` as its own field, letting log backends index and filter on it directly (e.g. `user_id="123"` in a query) without parsing.

**Q3. Name three fields that should appear on almost every log line in a production system.**
> Timestamp, log level, and a message/event name are the baseline; most teams also always include a service name and some request/trace correlation ID.

**Q4. Why should applications log to stdout rather than writing directly to log files?**
> This is the 12-factor app principle: the application shouldn't know or care about log routing/storage/rotation — it just emits a stream of events, and the execution environment (container runtime, systemd, log shipper) is responsible for capturing stdout and routing it wherever it needs to go (file, syslog, log aggregator).

**Q5. What problem does binding context (e.g. request_id) to a logger solve?**
> Without binding, you'd have to manually pass request_id as an argument to every single log call in a request's lifecycle, which is repetitive and error-prone; binding attaches it once to a logger instance (or context) so every subsequent log call automatically includes it.

**Q6. What is a "field schema" and why does consistency matter across services?**
> A field schema is the agreed-upon set of field names and types used in log entries (e.g. always `user_id` not sometimes `uid` or `userId`); consistency across services means a single dashboard or query can work across your whole fleet instead of needing per-service translation.

**Q7. Compare Python's stdlib `logging` module with `structlog`.**
> Stdlib `logging` is built-in, mature, and handler/formatter-based but produces unstructured text by default and requires custom formatters to get JSON; `structlog` wraps around logging (or works standalone) to make structured key-value logging and context-binding first-class, with JSON rendering built in.

**Q8. Why did Go's ecosystem produce zap and zerolog specifically emphasizing "zero allocation"?**
> Logging happens extremely frequently in high-throughput services, so any per-log-call heap allocation (e.g. from string formatting or reflection) adds up to real CPU and GC pressure; zap and zerolog are designed to avoid allocations on the hot path, using techniques like pre-sized buffers and avoiding `interface{}`/reflection where possible.

**Q9. What is Go's `log/slog` and why does its existence matter?**
> `slog` is the structured logging package added to Go's standard library (Go 1.21+), providing built-in structured/JSON logging so teams no longer need a third-party dependency just to get key-value structured logs.

**Q10. In Node.js, why is pino generally faster than winston for high-volume logging?**
> Pino minimizes synchronous work in the logging call itself — it serializes to JSON with a fast, purpose-built serializer and defers formatting/transport to a separate step (or process), whereas winston historically does more synchronous formatting and has a more general-purpose (heavier) architecture.

**Q11. What are log levels, and list them from most to least severe as commonly used.**
> Log levels indicate the severity/importance of an event so consumers can filter noise; a common ordering is FATAL/CRITICAL > ERROR > WARN > INFO > DEBUG > TRACE.

**Q12. When should you use ERROR vs WARNING?**
> Use ERROR when something failed and requires attention or indicates a real problem (e.g. a request failed, a downstream call errored out with no fallback); use WARNING for something unexpected but recovered-from or non-fatal (e.g. a retry succeeded, a deprecated field was used, a fallback path was taken).

**Q13. Why shouldn't you log every function call at INFO level?**
> INFO-level logging that fires on every function call floods your log volume with low-value noise, drives up storage/ingestion cost, and makes it harder to find the genuinely important events; such fine-grained tracing of execution belongs at DEBUG (or isn't logged at all — that's what distributed tracing is for).

**Q14. Give an example of something that belongs at DEBUG level but not INFO.**
> Something like "computed discount=0.15 for tier=gold" during order processing — useful when troubleshooting a specific issue locally or with elevated verbosity, but not something you want streaming into production logs at normal volume.

**Q15. What is the risk of logging at DEBUG level in production by default?**
> It can massively increase log volume and ingestion/storage cost, potentially log sensitive data more often, and bury the signal (real problems) in noise — DEBUG should typically be toggled on temporarily/selectively when actively investigating an issue.

**Q16. Why is string concatenation/interpolation in log messages a performance anti-pattern, beyond structure concerns?**
> Building the formatted string happens eagerly at the call site even if that log level is disabled and the line would be discarded, wasting CPU on string building/allocation that's thrown away; well-designed structured loggers defer serialization until they know the log will actually be emitted.

**Q17. What does "lazy evaluation" mean in the context of logging, and which libraries support it?**
> Lazy evaluation means expensive argument construction (e.g. serializing a large object) only happens if the log level is actually enabled; libraries like Python's `logging` support this via `%`-style lazy formatting (`logger.debug("%s", expensive_call())` still evaluates eagerly in Python, but structured loggers and some patterns like passing callables/thunks avoid the work when disabled — the key idea is checking `isEnabledFor` before building expensive payloads).

**Q18. Why is logging secrets or PII directly into log fields a serious problem, and what's a common mitigation?**
> Logs often flow to less access-controlled systems, get retained for long periods, and get widely queried/exported, so secrets (passwords, tokens) or PII (emails, SSNs) leak far beyond their original access boundary; mitigations include redaction/masking at the logging layer, allowlisting which fields are safe to log, and scrubbing middleware.

**Q19. What is a request_id and why is it critical for structured logging in a web service?**
> A request_id is a unique identifier generated (or received) at the start of handling a request that gets attached to every log line produced while handling that request, letting you filter logs down to the exact sequence of events for one specific request — essential for debugging a single user's issue among millions of log lines.

**Q20. How does a trace_id differ from a request_id, and how do they typically relate?**
> A request_id is usually local to one service's handling of one request; a trace_id (from distributed tracing, covered Day 15–17) identifies a request's journey across multiple services. Often a service's request_id for an inbound request is set to (or correlated with) the trace_id so logs and traces can be cross-referenced.

**Q21. Why would you want to include a tenant_id or user_id field on log lines in a multi-tenant SaaS system?**
> It lets you filter/aggregate logs per customer, quickly determine if an incident affects one tenant or all tenants, and answer support questions like "what happened for tenant X in the last hour" without grepping through everyone's data.

**Q22. What does "12-factor logging" mean for how log level and destination configuration should work?**
> The 12-factor model says the app shouldn't decide where logs end up (no built-in file rotation, no app-managed log storage); the app just streams structured events to stdout/stderr, and the runtime environment routes/stores/rotates them — this keeps the app portable across dev, staging, and different production platforms.

**Q23. If your app writes logs to a local file inside a Docker container instead of stdout, what problem arises?**
> The logs get trapped inside the container's writable layer; when the container is destroyed (redeployed, crashed, rescheduled) those logs are lost unless you've mounted volumes and configured extra log-shipping machinery — going against the platform's built-in stdout capture that most container orchestrators (and Day 7's Fluent Bit/Vector) expect.

**Q24. What's the difference between a "logger" and a "handler/formatter" in most logging frameworks?**
> The logger is the object your code calls (`logger.info(...)`) that decides whether an event passes its level filter; handlers/formatters are attached to the logger (or its root) and decide where the log goes (stdout, file, network) and how it's rendered (plain text, JSON) — one logger can fan out to multiple handlers.

**Q25. Why might you configure different log levels for different loggers/modules in one application?**
> Some modules (e.g. a noisy third-party HTTP client) may need to be quieted to WARNING while your own business logic logger stays at INFO or DEBUG, letting you control noise per-component without a single global level.

**Q26. What is the "event" or "msg" field convention in structured logging, and why prefer a short stable string over a full sentence?**
> Many structured loggers use a short, stable, machine-friendly string (e.g. `"order_processed"`) as the primary message/event name, with variable data in separate fields; this makes the event name groupable/searchable as an exact match across all occurrences, unlike a full sentence that varies per call (`"Order 123 processed"` vs `"Order 456 processed"`).

**Q27. Scenario: your team's logs mix `userId`, `user_id`, and `uid` for the same concept across different services. What's the downside and the fix?**
> Downside: queries and dashboards built against one naming convention silently miss data logged under another name, and cross-service correlation breaks; fix: agree on and enforce (e.g. via a shared logging library/wrapper or lint rule) one canonical field name schema across all services.

**Q28. Why is JSON a popular structured log format, and what's a real alternative?**
> JSON is ubiquitous, human-readable enough for debugging, and every language and log backend (Loki, Elasticsearch/OpenSearch, Datadog, etc.) can parse it natively; a real alternative is logfmt (simple `key=value key2=value2` pairs), which is more compact and still line-based/greppable, popular in some Go tooling.

**Q29. What does it mean that JSON logs are "self-describing," and why does that matter for log pipelines?**
> Each JSON log line carries its own field names, so a downstream parser (Fluent Bit, Vector, a log backend) doesn't need a separate schema definition or regex to extract fields — it just parses the JSON and every field is immediately available, unlike free-text logs that need custom parsing rules per log format.

**Q30. Predict the output: you log `logger.info("payment", amount=19.99, currency="USD")` using a JSON-structured logger. What would the resulting log line roughly look like?**
> Something like `{"timestamp": "2026-07-16T10:00:00Z", "level": "info", "event": "payment", "amount": 19.99, "currency": "USD"}` — one JSON object per line, with the standard fields plus your custom key-value pairs merged in.

**Q31. Predict the output: if you accidentally log an object containing a circular reference (e.g. a Python object referencing itself) with a JSON-based structured logger, what typically happens?**
> Most JSON serializers will raise a `TypeError`/serialization error (e.g. Python's `json.dumps` raises "Circular reference detected") unless the logging library has a custom encoder that handles cycles gracefully (e.g. by truncating or falling back to `repr()`), so you should sanitize or explicitly select fields rather than logging arbitrary objects.

**Q32. Predict the output: you set your root logger level to WARNING, then call `logger.info("checkpoint")`. What appears in the output?**
> Nothing — the INFO-level call is below the WARNING threshold, so the log line is filtered out and never reaches a handler/formatter; only WARNING level and above would be emitted.

**Q33. Why do many teams standardize on ISO 8601 / RFC 3339 timestamps in UTC for log entries?**
> It's unambiguous, sortable as a string, includes timezone information (avoiding "which timezone is this?" confusion), and is universally parseable by log backends — using UTC specifically avoids daylight-saving and multi-region timezone mismatches when correlating logs across services.

**Q34. What's the danger of relying on wall-clock log arrival time (when the log backend received it) instead of the timestamp embedded in the log event itself?**
> Network delays, buffering, and retries in the shipping pipeline (Day 7) mean arrival time can lag or reorder relative to when the event actually happened, so ordering/correlating events by arrival time can misrepresent the true sequence — the embedded event timestamp, generated at the source, is authoritative.

**Q35. Scenario: your service occasionally logs a stack trace as a giant multi-line string inside a single log field. What issue does this cause downstream, and how is it typically handled?**
> Multi-line content can break naive line-based log parsers/shippers that assume one log entry per line, causing a stack trace to be split into multiple garbled "log entries"; the fix is to ensure your logger fully JSON-encodes the exception (escaping newlines within the JSON string value) so the entire entry, trace included, is still one JSON object per physical line.

**Q36. Why is exception/error logging typically done with a dedicated method (e.g. `logger.exception(...)` or `logger.error(err, ...)`) rather than manually formatting the traceback into the message string?**
> Dedicated exception-logging methods automatically capture the full stack trace and exception metadata (type, message) as structured fields, ensuring consistency and completeness, whereas manually formatting a traceback into a plain string risks losing structure and making the exception type/message impossible to filter on separately.

**Q37. What does "log every request exactly once at completion, with outcome and duration" achieve that logging at both start and multiple intermediate points does not?**
> A single completion log line per request (with status, duration, key identifiers) is cheap, fully summarizes the request, and is easy to aggregate for rate/error/duration analysis (feeding RED metrics, Day 13); scattering many intermediate INFO logs is noisier and harder to reduce to per-request metrics without extra correlation work.

**Q38. Why might you still want a log line at the *start* of a request even if you always log one at completion?**
> If the process crashes or hangs mid-request, a start-of-request log line is the only trace that the request was ever received — the completion log would never be written, so you'd be blind to in-flight/stuck requests without it.

**Q39. What is the "sampling" concern for logs (previewed before Day 22), and why might you not want to log 100% of high-volume events even at INFO level?**
> At very high request volumes, logging every single request/event can become prohibitively expensive to store and index; sampling means intentionally logging only a fraction (or all errors but a sample of successes) to control cost while retaining enough signal for troubleshooting.

**Q40. Compare "logs as an event stream to stdout" versus "logs written to rotating local files" from a portability standpoint.**
> Stdout streaming decouples the app from any assumption about the filesystem, disk space, or rotation policy — it works identically in a container, a VM, or a serverless function; local file rotation embeds infrastructure concerns (disk management, rotation, retention) into the app itself, which breaks or requires reconfiguration in different deployment environments.

**Q41. Why is unit-testing your logging output a good practice, and what would such a test typically assert?**
> Logging output is part of your application's observable contract (dashboards, alerts, and debugging depend on it), so a regression that silently drops a field or breaks JSON formatting can cripple production visibility without breaking any functional test; a logging test typically captures stdout/log records and asserts the presence of expected keys, correct level, and valid JSON structure.

**Q42. Scenario: how would you test that an error path correctly logs at ERROR level with an `order_id` field, without hitting a real logging backend?**
> Redirect/capture the logger's output (e.g. Python's `caplog`/`capsys` fixtures, a custom in-memory handler, or Go's/Node's equivalent of writing to a buffer instead of stdout) during the test, trigger the failure path, then parse the captured line as JSON and assert `level == "error"` and `order_id` is present with the expected value.

**Q43. Why is it useful to include a `service` (and often `version`/`environment`) field on every log line?**
> When logs from many services are aggregated centrally, the `service` field lets you filter to just the component you care about, and `version`/`environment` lets you distinguish canary vs stable rollouts or staging vs production when diagnosing whether a bug is version- or environment-specific.

**Q44. What's the relationship between structured logging today (Day 6) and log querying with LogQL (Day 9)?**
> LogQL and similar structured-log query languages rely on being able to filter/extract by field name (e.g. `{service="orders"} | json | level="error"`); if your logs aren't structured with consistent field names now, those queries either don't work or require fragile regex parsing later — today's discipline directly enables tomorrow's (Day 9's) querying power.

**Q45. Why do library authors (zap, zerolog, structlog) emphasize avoiding reflection-based serialization for structured fields?**
> Reflection (inspecting a value's type at runtime to decide how to serialize it) is significantly slower than direct, type-specific encoding; high-performance loggers instead provide typed field constructors (e.g. `zap.String("key", val)`, `zap.Int("key", val)`) so the encoder knows exactly how to write each value without runtime type inspection.

**Q46. What's a reasonable default log level for a production service, and why not lower?**
> INFO is a common default — it captures meaningful business/operational events (requests handled, jobs completed, errors) without the fine-grained noise of DEBUG; running production permanently at DEBUG typically produces excessive volume/cost with limited added value for most events.

**Q47. Scenario: after adopting structured logging, your team notices log ingestion costs tripled. What are two likely causes worth investigating first?**
> First, check whether DEBUG-level or overly verbose per-iteration logs got left enabled in production; second, check for "cardinality bloat" in fields — e.g. accidentally logging a high-cardinality value (like a full request body or a unique ID) as an indexed/labeled field rather than as unindexed log content, which multiplies index size (this connects directly to Day 22's cardinality/cost topic).

**Q48. Why should log field values generally avoid being unbounded/high-cardinality when the log backend indexes fields (as opposed to full-text log content)?**
> Backends like Loki create separate streams per unique label/field combination; if a field like `request_id` were used as an indexed label (rather than plain log content), it would create a virtually unlimited number of streams, exploding storage and query cost — this nuance is covered in depth on Day 8/Day 22, but the seed is: know the difference between "indexed label" and "queryable JSON field."

**Q49. What is the core difference in *purpose* between structured application logs (today's topic) and metrics (Day 4) even though both can describe similar events?**
> Metrics are pre-aggregated numeric time series optimized for trends, rates, and alerting thresholds over time at low storage cost; logs are discrete, detailed, high-cardinality event records optimized for deep contextual investigation of a specific incident — you'd use a metric to know "error rate spiked at 10:03" and a log to know "which specific requests failed and why."

**Q50. Predict the output: your logging library is configured with level=INFO, and code does `logger.warning("cache_miss", key=cache_key)` followed by `logger.debug("cache_lookup_detail", ...)`. Which line(s) appear in your JSON log stream?**
> Only the `cache_miss` WARNING line appears; the DEBUG line is filtered out entirely since DEBUG is below the configured INFO threshold — the output stream would contain exactly one JSON object for the warning, with no trace of the debug call.

## ✅ Day 6 wrap-up

- You can now design a consistent log field schema and configure a real logging library to emit clean, structured JSON to stdout.
- You understand why log levels, avoiding string interpolation, and lazy evaluation matter for both correctness and performance.
- You've practiced attaching request-scoped context (request_id, trace_id) so a single request's story can be reconstructed from log output.
- Tomorrow (Day 7) you'll follow these logs downstream — learning how log pipelines like Fluent Bit and Vector collect, transform, and ship the structured lines you just learned to produce.

Nice work — solid, well-structured logs are the foundation everything else in this roadmap builds on, and you just built that foundation with your own hands.
