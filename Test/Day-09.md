# Day 9 — Querying & Alerting on Logs

You've got logs flowing through a pipeline and landing in Loki or Elasticsearch/OpenSearch — now it's time to actually *use* them. Today you'll learn to query logs like a power user with LogQL, turn raw log lines into dashboards and metrics, and understand when a log-based alert is the right tool versus a trap. This is the day your logging investment starts paying rent.

## 🎯 Learning objectives

- [ ] Write LogQL queries combining stream selectors, line filters, and label filters
- [ ] Use parsers (`json`, `logfmt`, `regexp`) to extract structured fields from unstructured log lines
- [ ] Convert log streams into time-series metrics using `rate()` and `count_over_time()`
- [ ] Build a simple log-based panel/dashboard in Grafana
- [ ] Explain the trade-offs of alerting on logs vs. alerting on metrics
- [ ] Extract a business/operational metric directly from log volume
- [ ] Use a correlation/trace ID to jump from a log line to a distributed trace

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5 min | Blind-recall: write down Loki's label vs. content model and how Fluent Bit/Vector ship logs (from Day 7-8), no notes |
| Learn | 20 min | Read LogQL docs on stream selectors, filters, parsers, and metric queries; skim Grafana log panel docs |
| Do | 25 min | Hands-on project: run Loki + Grafana locally, write 8-10 progressively complex LogQL queries, build one dashboard panel and one alert rule |
| Reinforce | 10 min | Run through the first 20 recall questions below, out loud or written, no peeking |

## 📚 Free resources

- **Grafana Loki docs — LogQL** — the canonical, example-rich reference for stream selectors, filters, parsers, and metric queries; go here first. (type: docs)
- **Grafana Labs blog** — regularly publishes practical LogQL and Loki alerting tutorials with real query examples. (type: blog)
- **Grafana docs — Alerting** — how log-derived metric queries plug into Grafana's unified alerting engine. (type: docs)
- **Elastic (Elasticsearch/OpenSearch) — Query DSL docs** — if you're on the ELK/OpenSearch side, the equivalent reference for structured log querying. (type: docs)
- **OpenSearch documentation — Alerting plugin** — free, open-source reference for building log-based alerts outside the Loki ecosystem. (type: docs)
- **Google SRE Book (sre.google/books)** — "Practical Alerting" chapter, essential for the alerting trade-offs question (symptom vs. cause, alert fatigue). (type: book)
- **Grafana Play (play.grafana.org)** — a live, free public Grafana instance with real Loki data sources you can query without installing anything. (type: docs)
- **Fluent Bit / Grafana Labs YouTube talks (KubeCon/GrafanaCon recordings)** — search for "LogQL" or "Loki alerting" talks, free conference recordings walking through real production setups. (type: video)
- **OpenTelemetry docs — Trace Context / Correlation** — explains how trace IDs propagate so you can connect a log line to a trace, directly relevant to today's correlation-ID objective. (type: docs)

## 🛠️ Hands-on project (free, ~25 min)

Goal: run a real Loki + Grafana stack locally, query logs with increasing sophistication, and wire up one log-based alert.

1. **Spin up the stack.** Use Grafana's official `docker-compose` for Loki + Promtail + Grafana (search "Loki docker-compose quick start" in the docs you just read, or reuse your Day 7-8 pipeline if it's still running). `docker compose up -d`.
2. **Confirm ingestion.** In Grafana, add/verify the Loki data source, open Explore, and run a bare stream selector: `{job="varlogs"}` (or whatever label your pipeline sets). You should see raw lines flowing in.
3. **Layer on filters.** Progress through:
   - Line filter: `{job="varlogs"} |= "error"`
   - Negated filter: `{job="varlogs"} != "healthcheck"`
   - Parser + label filter: `{job="varlogs"} | json | status_code >= 500`
   - Regex extraction if your logs aren't structured: `{job="varlogs"} | regexp "status=(?P<status>\\d+)"`
4. **Turn logs into metrics.** Run a metric query: `sum(rate({job="varlogs"} |= "error" [5m]))` and `count_over_time({job="varlogs"} | json | status_code=`500`[5m])`. Add this as a Grafana panel (Explore → "Add to dashboard").
5. **Build one alert.** In Grafana Alerting, create a rule on the error-rate metric query from step 4 (e.g., fire if rate > 0 for 5 minutes). Save it, then trigger a test error line via your app or a manual log injection to watch it fire.
6. **Expected outcome:** a working dashboard panel showing error rate over time, and an alert rule that transitions to "Firing" when you inject an error log line.

**Stretch goal:** add a `trace_id` field to one of your log lines (even a fake one), then write a LogQL query that filters on it (`| json | trace_id="abc123"`) to simulate the "jump from log to trace" workflow you'll formalize in Phase 4.

## 🧠 50 questions for active recall

**Q1. What is LogQL, in one sentence?**
> LogQL is Loki's query language, modeled on PromQL, that lets you select log streams by labels and then filter, parse, and aggregate the log lines within them.

**Q2. What is a "stream selector" in LogQL?**
> It's the `{label="value"}` portion of a query that narrows down which log streams (label combinations) to search, e.g. `{app="checkout", env="prod"}` — it's evaluated first and cheaply, using Loki's label index.

**Q3. Why does Loki require a stream selector before any filtering?**
> Because Loki doesn't index log content, only labels — the selector narrows the search to a manageable set of chunks, after which line filters scan the actual text. Without it, every query would require a full scan of all logs.

**Q4. What's the difference between a line filter and a label filter in LogQL?**
> A line filter (`|=`, `!=`, `|~`, `!~`) matches on the raw text content of the log line; a label filter (applied after a parser, e.g. `| status_code >= 500`) matches on structured fields extracted from that line. Line filters run first and are cheaper.

**Q5. Name the four line filter operators and what each does.**
> `|=` line contains string; `!=` line does not contain string; `|~` line matches regex; `!~` line does not match regex. They chain left to right and each narrows the result further.

**Q6. Why should you prefer `|=` over `|~` when you just need a substring match?**
> `|=` (plain string match) is significantly cheaper to evaluate than regex (`|~`), since it avoids regex engine overhead — use regex only when you actually need pattern matching, not for simple substring checks.

**Q7. What does the `json` parser do in a LogQL pipeline?**
> It parses each log line as JSON and extracts top-level (and optionally nested) keys as labels you can then filter or format on, e.g. `| json` turns `{"status":500,"msg":"fail"}` into filterable fields `status` and `msg`.

**Q8. What does the `logfmt` parser do, and when would you use it instead of `json`?**
> `logfmt` parses `key=value key2="value 2"` style lines (common in Go apps like Prometheus/Loki itself) into labels. Use it when your logs are logfmt-formatted rather than JSON — trying to run `json` on logfmt lines will fail to parse.

**Q9. When would you reach for the `regexp` parser over `json`/`logfmt`?**
> When your log lines are unstructured or in a custom/legacy format that isn't valid JSON or logfmt — you define named capture groups, e.g. `| regexp "(?P<level>\\w+): (?P<msg>.*)"`, to extract fields manually.

**Q10. What's the performance cost ordering, roughly, of label selector → line filter → parser → label filter?**
> Label selector is cheapest (index lookup), line filter is next (fast string/regex scan on raw text), and parsing (json/logfmt/regexp) plus post-parse label filtering is the most expensive since it runs on every remaining line. Always filter with `|=` before parsing to reduce the parse workload.

**Q11. Write a LogQL query to find all lines containing "timeout" in the `payments` app.**
> `{app="payments"} |= "timeout"` — the stream selector narrows to the payments app, and the line filter matches lines containing "timeout".

**Q12. Write a LogQL query for 5xx errors from JSON-structured logs in the `api` job.**
> `{job="api"} | json | status_code >= 500` — select the stream, parse JSON, then filter on the extracted numeric field.

**Q13. What does `count_over_time({job="api"} |= "error" [5m])` return?**
> A metric query: the count of matching log lines within each 5-minute window, returned as a range vector/time series — essentially "how many error lines occurred per interval."

**Q14. What does `rate({job="api"} |= "error" [5m])` compute, and how does it differ from `count_over_time`?**
> `rate()` computes the per-second rate of matching log lines over the range window (count divided by seconds in the window), giving a normalized rate comparable across different window sizes — `count_over_time` gives raw counts per window, not normalized.

**Q15. Why would you wrap a LogQL metric query in `sum()` or `sum by (label)()`?**
> Because a metric query over multiple streams returns one series per stream; `sum()` aggregates them into a single overall value, while `sum by (label)()` groups them into fewer series bucketed by a label you care about (e.g., by `pod` or `status_code`), which is usually what you want for dashboards/alerts.

**Q16. What's the LogQL equivalent of PromQL's `[5m]` range vector syntax, and why is the syntax shared?**
> LogQL reuses the exact same `[5m]`-style range duration syntax as PromQL for its metric queries, since Loki's metric query layer was deliberately designed to feel like PromQL — this means anything you know about PromQL range vectors transfers directly.

**Q17. Give one concrete example of extracting a business metric from logs (not infra metrics).**
> Counting `order_placed` events from application logs via `sum(count_over_time({app="checkout"} |= "order_placed" [1h]))` gives you an orders-per-hour metric without instrumenting a dedicated Prometheus counter — useful when the code isn't instrumented yet.

**Q18. What's a major risk of deriving business metrics from logs instead of proper instrumentation?**
> Log-derived metrics depend on log message text/format staying stable — a developer rewording a log message silently breaks your metric with no compile-time or type-level warning, whereas a Prometheus counter is a durable, versioned contract.

**Q19. What is `unwrap` used for in LogQL metric queries?**
> `unwrap` extracts a numeric value from a label (usually produced by a parser) and lets you compute metrics like `avg_over_time`, `sum_over_time`, or `quantile_over_time` on that value, e.g. unwrapping a `duration` field to compute average latency from logs.

**Q20. Write a query that computes average request duration from JSON logs with a `duration_ms` field.**
> `avg_over_time({app="api"} | json | unwrap duration_ms [5m])` — parse JSON, unwrap the numeric field, then average it over the window.

**Q21. Why is computing latency percentiles from log-derived `unwrap` metrics generally worse than from a proper Prometheus histogram?**
> Log-based unwrap metrics compute quantiles over sampled log lines (subject to log sampling/dropped lines and higher cardinality cost), while a Prometheus histogram is purpose-built, pre-aggregated at scrape time, and far cheaper to query at high resolution — logs work in a pinch but aren't the right primary source for SLO-grade latency percentiles.

**Q22. In Grafana, what's the simplest way to visualize raw log lines (not a metric) on a dashboard?**
> Add a "Logs" panel type with a Loki (or Elasticsearch) data source and a LogQL/query that returns log streams rather than a metric — it renders as a scrollable, timestamped log viewer, optionally with highlighted search terms.

**Q23. What's the difference between a "Logs" panel and a "Time series" panel fed by a LogQL query in Grafana?**
> A Logs panel displays the raw matching log lines themselves; a Time series panel requires a metric query (like `rate(...)` or `count_over_time(...)`) and plots the resulting numeric series over time — you can't feed raw log lines into a time series panel.

**Q24. Why is it useful to put a log volume panel next to a metric panel on the same dashboard row?**
> It lets you visually correlate a metric anomaly (e.g., latency spike) with a simultaneous spike in error log volume, giving an immediate first clue for root cause without switching tools or context.

**Q25. What is "Derived Fields" in Grafana's Loki data source configuration used for?**
> Derived Fields let you define a regex to extract a value (like a trace ID) from a log line and turn it into a clickable link that jumps to another data source (e.g., Tempo/Jaeger) — this is the mechanism behind log-to-trace correlation in Grafana.

**Q26. Describe the log-to-trace correlation workflow end to end.**
> An application emits a trace ID as a structured field in both its logs and its trace spans (via OpenTelemetry context propagation); Grafana's Derived Fields config recognizes that field in a log line and renders it as a link; clicking it queries the trace backend (Tempo/Jaeger) for that trace ID, jumping you from "what happened" (log) to "where time was spent across services" (trace).

**Q27. Why must you propagate the same trace/correlation ID through all services for this to work?**
> If each service generates its own independent ID, there's no shared key to join logs and traces on — the ID must be created at the entry point (or by the tracing SDK) and passed along via context propagation headers so every log line and every span in the request path shares one identifier.

**Q28. What is the primary trade-off of alerting directly on log content vs. alerting on a Prometheus metric?**
> Log-based alerts can catch things metrics never captured (specific error strings, stack traces, rare conditions) but are more expensive to evaluate continuously, more brittle to log format changes, and typically have higher latency; metric alerts are cheap, fast, and stable but only alert on what was pre-instrumented.

**Q29. Why is alert latency often worse for log-based alerts than metric-based alerts?**
> Log ingestion pipelines (shipping, batching, indexing) add latency before a line is queryable, and log-metric queries themselves can be heavier to evaluate repeatedly, whereas Prometheus metrics are scraped and evaluated on a tight, predictable interval optimized for fast alerting.

**Q30. Give a concrete scenario where a log-based alert is clearly the better (or only) choice.**
> A specific, rare exception message (e.g., "certificate expired for vendor X") that was never instrumented as a metric — you can't alert on it via Prometheus without code changes, but you can alert on it immediately via a LogQL count query, no redeploy needed.

**Q31. What is "alert fatigue" and how do noisy log-based alerts contribute to it?**
> Alert fatigue is when responders become desensitized to alerts due to excessive false positives or low-value pages, leading them to ignore or delay response to real incidents; log-based alerts on noisy or frequently-changing log text are especially prone to firing on benign log volume fluctuations, accelerating fatigue.

**Q32. Why should you generally alert on symptoms (rate of errors affecting users) rather than raw log line counts?**
> Because raw log counts fluctuate with traffic and log verbosity for reasons unrelated to actual user impact; a ratio like error-rate-as-percentage-of-total-requests (a symptom) is far more stable and meaningful than an absolute count (a cause-adjacent proxy), matching the Google SRE "alert on symptoms" principle.

**Q33. What LogQL pattern would you use to alert on error rate as a percentage rather than absolute count?**
> Something like `sum(rate({app="api"} |= "error" [5m])) / sum(rate({app="api"}[5m])) * 100` — errors over total request lines, expressed as a ratio, then alert if that ratio crosses a threshold.

**Q34. What happens if you set a Grafana log-based alert's evaluation interval shorter than your log shipping pipeline's typical delay?**
> The alert may evaluate against incomplete data each cycle (recent lines haven't arrived yet), causing flapping, false "all clear" readings, or delayed detection — you should set evaluation intervals with margin above your pipeline's end-to-end ingestion latency.

**Q35. Predict: you run `{app="api"} | json` on a stream where 10% of lines aren't valid JSON. What happens to those lines?**
> Loki's `json` parser doesn't error out the whole query — non-parseable lines typically get skipped or an error label is attached (`__error__="JSONParserErr"`) rather than crashing, and depending on the query they may be dropped from label-filtered results or shown with the error label if you inspect it directly.

**Q36. Predict: you write `{app="api"} | json | status_code>=500` but the field is actually named `statusCode` in the JSON. What happens?**
> The label filter references a field name that doesn't exist as extracted, so the filter effectively matches nothing (or errors depending on backend version) — the query returns zero results, not an error about a "missing field," which is a common silent-failure trap. Always check actual field names first with a bare `| json` query.

**Q37. Predict: you alert on `sum(rate({app="api"} |= "error"))` with no time range in brackets. What happens?**
> LogQL metric queries require an explicit range duration (e.g. `[5m]`) on the log selector for `rate`/`count_over_time` — omitting it is a syntax error, the query will fail to parse rather than default to some implicit window.

**Q38. What's a "troubleshooting workflow" that starts from a metric alert and ends in a specific log line? Sketch it.**
> 1) A Prometheus/Grafana alert fires on elevated latency or error rate for service X. 2) You open the relevant Grafana dashboard, note the time window and affected labels (pod, endpoint). 3) You pivot to Explore/Loki, apply the same labels and time window as a stream selector. 4) You filter with `|= "error"` or parse and filter on status code to narrow to the specific failing requests. 5) You extract a trace ID from a matching line and jump to the trace backend to see the full request path.

**Q39. Why is it valuable to include the same labels (service, pod, region) on both your metrics and your logs?**
> Shared labels let you pivot directly between a metric dashboard and a log query using identical selectors, without translation — this "same labels everywhere" discipline is what makes the three-pillars correlation workflow (Day 21) fast instead of a manual hunt.

**Q40. What does `topk()` do when wrapped around a LogQL metric query, and give a use case.**
> `topk(5, sum by (path) (rate({app="api"} |= "error" [5m])))` returns the 5 series (grouped by `path` here) with the highest error rates — useful for quickly spotting which endpoints are contributing most to an error spike during triage.

**Q41. What's the difference between filtering on a label from the stream selector vs. a label extracted via a parser, in terms of what Loki had to do to get that label?**
> Stream selector labels are static metadata attached at ingestion time (indexed, cheap to filter on); parser-extracted labels are computed on the fly at query time from the log line content (not indexed, more expensive, computed fresh every query) — this is why you can't put a JSON-extracted field into the initial `{}` selector.

**Q42. Why might you choose to "promote" a frequently-filtered log field into an actual indexed label at ingestion time (via Promtail/Fluent Bit/Vector) instead of always parsing it at query time?**
> If a field like `status_code` or `environment` is queried constantly, indexing it as a real label at ingestion avoids repeated parse overhead on every query — but this must be done carefully since indexed labels directly drive Loki's stream/series cardinality, and over-indexing causes the cardinality explosion problem you'll cover on Day 22.

**Q43. What does `{app="api"} | logfmt | line_format "{{.level}} {{.msg}}"` do?**
> It parses logfmt fields, then reformats the displayed log line using a Go template to show only the `level` and `msg` fields — useful for cleaning up noisy log output in Explore/dashboards without losing the underlying filterable labels.

**Q44. Scenario: your dashboard's log panel is showing far fewer lines than you expect for a busy service. What are two likely causes to check first?**
> (1) The stream selector labels might not match what the shipper actually attached (typo, or the app is emitting under a different label set) — verify with a bare selector query. (2) Loki query limits (`limit`/`maxLines`) or the dashboard's default row limit may be truncating results before you see the full set — check the query's line limit setting.

**Q45. Why is `!~` (does not match regex) typically more expensive than `|~` (matches regex), even though both use regex?**
> They're roughly the same cost to evaluate per line (both run the regex engine), but `!~` can't short-circuit as early in some engines since it must confirm the pattern doesn't appear anywhere, and more importantly it can't benefit as well from filter reordering optimizations — in practice the bigger cost driver is usually how much of the corpus survives to that filter stage, so ordering filters from most-selective to least-selective matters more than which operator you use.

**Q46. Compare/contrast: metric query `rate()` in LogQL vs. `rate()` in PromQL — what's actually being rated?**
> In PromQL, `rate()` operates on a counter metric's numeric values to compute per-second increase; in LogQL, `rate()` operates on the count of log lines matching a selector/filter within the window — conceptually similar (both give a per-second rate) but LogQL's input is "how many lines matched," not a pre-existing numeric counter.

**Q47. Why would a team choose to alert on both a metric-based SLO burn rate AND a log-based alert for a known rare error string, rather than picking just one?**
> They serve different purposes: the SLO burn-rate alert (Day 20) catches broad user-impacting degradation reliably and cheaply, while the log-based alert catches a specific known failure mode fast, before it accumulates enough impact to move the SLO metric — using both covers "slow broad drift" and "fast specific failure" scenarios respectively.

**Q48. Predict: you set a Grafana alert rule on a LogQL metric query with a "Reduce" step using "last()" over a 5-minute evaluation window where the query itself already aggregates over `[5m]`. Is this redundant, and does it cause a problem?**
> It's not necessarily a bug, but it's worth understanding: the LogQL range vector already gives you one aggregated value per evaluation, so "last()" just takes that single point — it's effectively a no-op here, but if you'd used a wider window in the query than the eval interval, the reduce function's choice (last vs. avg vs. max) would matter for how spiky data gets flattened into the alert condition. Mismatched windows are a common source of surprising alert behavior.

**Q49. Scenario: a log-based alert on `sum(rate({app="checkout"} |= "OutOfStock" [5m])) > 10` starts flapping on and off every evaluation cycle. Name two plausible causes and how you'd investigate.**
> (1) The threshold is too close to normal baseline noise — check the historical rate of this line via Explore over a longer range to see if 10/sec is genuinely anomalous or just typical variance. (2) The log shipping pipeline has bursty/batched delivery, causing artificial spikes and troughs in what should be a smooth rate — check the shipper's batching/flush interval (Day 7) and consider a longer smoothing window or `for` duration on the alert to require sustained breach before firing.

**Q50. Why is "alert on logs sparingly, prefer metrics for anything ongoing and well-understood" a defensible 80/20 rule for a growing observability practice?**
> Metric alerts are cheaper to run continuously, more stable against text changes, and faster to evaluate — so they should be your default for known, recurring failure modes. Log-based alerts are best reserved for the long tail of specific, rare, or newly-discovered failure signatures where writing a code-level metric isn't yet justified; treating log alerts as the exception rather than the default keeps your alerting system fast, stable, and cheap to run at scale.

## ✅ Day 9 wrap-up

- You can now write LogQL queries that go from a coarse stream selector down to precise, parsed, filtered results — and turn those into rate/count metrics.
- You can build a log panel and a log-derived metric alert in Grafana, and you understand exactly when that's the right tool vs. reaching for a proper Prometheus metric.
- You have a working mental model of the metric-alert-to-log-line-to-trace troubleshooting workflow, anchored by shared labels and correlation IDs.
- Tomorrow (Day 10) you pivot fully into metrics: Prometheus's architecture and data model — the foundation everything you did today with `rate()` and `count_over_time()` was secretly borrowing from.

You've now closed the loop on an entire pillar of observability in just four days — that's real, compounding progress. Keep going.
