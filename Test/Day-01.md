# Day 1 — Observability vs. Monitoring + Your Metalearning Map

Welcome to day one — there's no "prior day" to build on yet, so today you're laying the foundation the other 24 days will stand on. You'll leave this hour with a sharp mental model of what observability actually is, and a personal map of how you're going to learn it fast.

## 🎯 Learning objectives

- [ ] Explain the difference between monitoring and observability in your own words, with an example
- [ ] Name the three pillars of observability (logs, metrics, traces) and what question each answers best
- [ ] Explain "unknown-unknowns" and why dashboards built for known failure modes don't catch them
- [ ] Define cardinality and high-dimensionality and explain why they matter for debugging
- [ ] Distinguish "control" (can I change the system?) from "observability" (can I understand it from outputs?)
- [ ] Produce a written metalearning map for the 25-day roadmap (concepts, tools, benchmarks, resources)
- [ ] Identify what "directness" and "retrieval" mean for how you'll study for the rest of this course

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5 min | Free-write: "What do I already believe monitoring and observability mean?" No lookups — just dump prior knowledge to anchor new learning against it |
| Learn | 20 min | Read the Honeycomb "monitoring vs observability" explainer + skim the Google SRE Book's monitoring chapter; note the 3 pillars and the unknown-unknowns idea |
| Do | 25 min | Hands-on project below: stand up a tiny app, break it in an *unplanned* way, and see what a metrics-only dashboard fails to tell you |
| Reinforce | 10 min | Answer 15–20 of the recall questions below from memory (no peeking), then check answers and flag misses for tomorrow's warm-up |

## 📚 Free resources

- **Honeycomb "Observability vs. Monitoring" blog** — the canonical, widely-cited explainer that draws the distinction this whole roadmap is built on. (type: blog)
- **Google SRE Book, Chapter 6: Monitoring Distributed Systems** (sre.google/books) — free, authoritative, grounds you in why teams monitor and what symptoms vs. causes means. (type: book)
- **OpenTelemetry official docs — "Observability primer"** (opentelemetry.io/docs) — vendor-neutral definitions of logs/metrics/traces you'll rely on for the rest of the roadmap. (type: docs)
- **Charity Majors' blog (charity.wtf)** — the most prominent voice on "observability 2.0" and unknown-unknowns; several free posts on why dashboards aren't enough. (type: blog)
- **Grafana Labs blog — "What is observability?"** — practical, tool-grounded framing that complements the more philosophical Honeycomb/Charity posts. (type: blog)
- **CNCF YouTube channel** — search "observability primer" or "three pillars of observability" for free conference talks (KubeCon/CloudNativeCon recordings). (type: video)
- **Cindy Sridharan's "Distributed Systems Observability" (free ebook/blog series, O'Reilly-adjacent, widely mirrored on her blog)** — dense but excellent on cardinality and dimensionality. (type: book/blog)
- **Prometheus docs — "Overview"** (prometheus.io/docs) — read just enough today to see how a metrics-first tool frames the problem; you'll go deep on this in Phase 3. (type: docs)

## 🛠️ Hands-on project (free, ~25 min)

**Goal:** feel the gap between monitoring and observability with your own hands, not just read about it.

1. On your machine, run a trivial web app with Docker — simplest option: `docker run -d -p 8080:80 nginx` (or if you have Python: a 10-line Flask app with 2-3 routes, one of which sleeps randomly or errors on a random condition).
2. Set up the crudest possible "monitoring": a shell loop that curls the app every 2 seconds and prints UP/DOWN based on HTTP status (`while true; do curl -s -o /dev/null -w "%{http_code}\n" localhost:8080; sleep 2; done`). This is your monitoring dashboard — it only knows "known-known" health.
3. Now introduce an *unplanned* failure mode you didn't design a check for — e.g., if using Flask, add a route that returns 200 but takes 8 seconds for exactly one specific query parameter value; if using nginx, request a path that doesn't exist but check response body content, not just status code.
4. Watch your monitoring loop: does it flag the problem? Almost certainly not — it only checks the one signal (status code) you predefined.
5. Now manually inspect: add a raw access log line per request (nginx already does this in `/var/log/nginx/access.log` inside the container, or add a `print()`/log line in Flask with method, path, status, and latency). Grep/inspect that log for the slow or malformed requests you triggered in step 3.

**Expected outcome:** your monitoring check stays green while the underlying problem is real — direct proof that monitoring (checking pre-defined thresholds) misses unknown-unknowns, while having rich, arbitrary-context logs let you ask a question you didn't anticipate needing to ask.

**Stretch goal:** add a request ID and a "user_id" or "customer_tier" field to each log line, then ask yourself: "could I answer 'which customers are hit by this?'" — a preview of why high-cardinality fields matter (Day 22 territory).

## 🧠 50 questions for active recall

**Q1. What is monitoring, in one sentence?**
> Monitoring is the practice of collecting and watching predefined signals (metrics, thresholds, health checks) to detect known failure modes and alert when they occur. It answers "is the system in one of the states I already anticipated?"

**Q2. What is observability, in one sentence?**
> Observability is a property of a system: how well you can infer its internal state purely from its external outputs (logs, metrics, traces) — especially for questions or failure modes you didn't anticipate in advance. It's about being able to ask arbitrary new questions, not just check predefined ones.

**Q3. Where does the word "observability" come from originally?**
> It comes from control theory (Rudolf Kálmán, 1960s), where a system is "observable" if its internal state can be fully determined from its external outputs over time. Software borrowed the term to describe systems whose internal behavior can be inferred from telemetry.

**Q4. What's the key practical difference between "monitoring" and "observability" for a working engineer?**
> Monitoring tells you *that* something is wrong (a dashboard turns red); observability lets you figure out *why*, even for a failure mode nobody wrote a dashboard for. Monitoring is built around known-unknowns; observability is built to survive unknown-unknowns.

**Q5. Give an example of a "known-unknown."**
> "I don't know if disk usage will exceed 90% today, but I know to check it" — you've anticipated the failure mode and instrumented a check for it (a disk-usage alert). It's unknown *whether* it happens, but known *that* it could.

**Q6. Give an example of an "unknown-unknown."**
> A specific combination of a rare browser version, a particular feature flag, and a specific database shard causes checkout to silently fail for 0.3% of users — nobody anticipated this interaction, so no dashboard or alert was built for it. You only find it by being able to slice and query arbitrary dimensions after the fact.

**Q7. Why can't you simply build a dashboard for every possible unknown-unknown in advance?**
> Because the combinatorial space of possible failure interactions (code paths × inputs × infra states × user segments) is effectively infinite; you cannot enumerate and pre-build alerts for all of them. Observability instead invests in rich, queryable telemetry so you can investigate novel combinations after they occur.

**Q8. What are the "three pillars" of observability?**
> Logs, metrics, and traces. Logs are discrete timestamped event records; metrics are numeric aggregates measured over time; traces show the path and timing of a single request as it moves through a distributed system.

**Q9. Are the three pillars sufficient on their own, or does the "pillars" framing have a known criticism?**
> The framing has been criticized (notably by Charity Majors) as vendor-driven and misleading, because collecting all three in isolation doesn't automatically make a system observable — what matters is being able to correlate them and ask novel questions, not just collect three data types in separate silos.

**Q10. What question do metrics answer best?**
> "What is the aggregate behavior of the system over time?" — e.g., request rate, error rate, latency percentiles. Metrics are cheap, efficient, and great for trends, thresholds, and dashboards, but they lose per-event detail.

**Q11. What question do logs answer best?**
> "What exactly happened, in detail, for this event or request?" Logs preserve rich, arbitrary context (user ID, error stack trace, specific parameter values) that metrics compress away.

**Q12. What question do traces answer best?**
> "Where did time go, and what was the path of this specific request across services?" Traces reveal causality and latency breakdown across a distributed call chain, which neither logs nor metrics show in isolation.

**Q13. Why can't metrics alone tell you *why* latency spiked, only *that* it did?**
> Metrics are pre-aggregated numeric summaries — a p99 latency metric tells you the aggregate shifted, but the aggregation discards which specific requests, users, or code paths were responsible. To find "why" you need to correlate with logs or traces that retain per-request detail.

**Q14. Why can't logs alone easily answer "how did this request's latency break down across five microservices"?**
> Individual service logs are siloed per-service and lack a shared causal thread; without a trace/span ID linking them, you can't reconstruct the ordering and timing relationships across services for one specific request. Traces exist specifically to stitch that causal chain together.

**Q15. What is cardinality, in the observability context?**
> Cardinality is the number of unique values a given field/dimension can take — e.g., "user_id" has extremely high cardinality (millions of unique values), while "http_status_code" has low cardinality (a few dozen values).

**Q16. What is "high-dimensionality" and how does it differ from high cardinality?**
> Dimensionality is the *number of different fields/attributes* attached to an event (e.g., user_id, region, device_type, feature_flag, shard — 5 dimensions); cardinality is how many unique values *each* field has. A system can have few dimensions but one is high-cardinality, or many dimensions each with modest cardinality — both push up the total number of unique combinations you might need to query.

**Q17. Why do traditional metrics systems struggle with high-cardinality data like user_id or request_id?**
> Time-series metrics systems typically store data indexed by the combination of metric name and label values; adding a high-cardinality label like user_id multiplies the number of distinct time series (a "cardinality explosion"), which can blow up storage and query cost. This is why metrics systems favor a small number of low-cardinality labels.

**Q18. Why does high cardinality matter FOR debugging, rather than just being a cost problem?**
> High-cardinality fields (user_id, request_id, trace_id) are exactly the fields you need to pinpoint a specific failing request or a narrow affected segment; without them, you can only see aggregate trends, not the individual instance that reveals the root cause. This is the core tension: what's expensive for metrics systems is exactly what's valuable for debugging unknown-unknowns.

**Q19. What tool/approach is generally better suited to very high-cardinality event data — traditional metrics, or event-based/log-based systems?**
> Event-based systems (structured logs, wide events, or tracing backends built for arbitrary tags) handle high cardinality better than classic Prometheus-style metrics, because they store raw or semi-structured events rather than pre-aggregating into fixed time series per label combination.

**Q20. Define "control" as distinct from "observability" in a systems sense.**
> Control is your ability to *change* a system's behavior (deploy a fix, flip a flag, scale a service); observability is your ability to *understand* the system's current state from its outputs. You need observability first to know what to change — control without observability is often guessing.

**Q21. Why is it dangerous to have strong control mechanisms (auto-scaling, feature flags, kill switches) without observability?**
> Without visibility into current state, automated or manual control actions can be applied blindly — e.g., auto-scaling based on a misleading metric, or a kill switch flipped without knowing which segment is actually affected — which can worsen an incident instead of resolving it.

**Q22. What does "monitoring is necessary but not sufficient for observability" mean?**
> You still need basic monitoring (health checks, key metrics, alerts) to catch known failure modes efficiently — observability doesn't replace that. But monitoring alone won't help when you hit a novel failure mode; you additionally need the deep, ad-hoc queryability that observability tooling provides.

**Q23. Give a one-line definition of "metalearning" as used in ultralearning.**
> Metalearning is drawing the map before the journey — researching how a subject is structured, what its sub-skills are, and how experts learn it, before diving into study, so your effort targets what actually matters.

**Q24. Why does this roadmap ask you to build a "metalearning map" on Day 1 specifically?**
> Because 25 days is a real commitment, and without a map you risk spending disproportionate time on comfortable topics (e.g., re-reading logging basics) while under-investing in harder, higher-leverage ones (e.g., PromQL, trace correlation) — the map lets you allocate the 80/20 effort deliberately from day one.

**Q25. What is the "directness" principle in ultralearning?**
> Directness means practicing the target skill in the actual context you'll use it, rather than only in abstract or proxy forms — e.g., actually querying real logs and breaking a real app today, instead of only reading definitions, because reading alone transfers weakly to real troubleshooting.

**Q26. Why does today's hands-on project (breaking an app in an unplanned way) embody "directness"?**
> Because it puts you in the actual position an observability practitioner is in — discovering an unanticipated failure using real telemetry — rather than passively reading a description of unknown-unknowns; you directly experience monitoring's blind spot instead of being told about it.

**Q27. What is "retrieval practice" and why does this roadmap use 50 recall questions instead of a summary to reread?**
> Retrieval practice is testing yourself on material to actively recall it from memory, which strengthens retention far more than passive rereading (the "testing effect"). The 50 questions force you to reconstruct concepts, which is what will make them stick for the following 24 days.

**Q28. What is the 80/20 rule as applied to this roadmap's structure?**
> Roughly 20% of the concepts and skills (e.g., the three pillars, PromQL basics, trace context propagation, SLOs) deliver about 80% of the practical value in real observability work, so the roadmap deliberately over-indexes time on those instead of spreading evenly across every possible topic.

**Q29. What does "feedback" mean as an ultralearning principle, and where will you get it in this roadmap?**
> Feedback means getting fast, honest signal on whether you actually understand or can do something — here, that's the recall-question answer-checking, the hands-on project's observable outcomes (did the dashboard catch the bug or not?), and later days' exercises where a query either returns the right data or it doesn't.

**Q30. Why is "spaced repetition" scheduled for Day 25 rather than left out?**
> Because retention decays over time (the forgetting curve); revisiting Day 1-24 concepts after a gap, rather than only once, is what converts short-term recall into durable, retrievable knowledge — Day 25 is explicitly designed to fight forgetting, not just to be a summary.

**Q31. Scenario: your team's dashboard shows p50 latency is fine, but a subset of premium customers is complaining about slowness. What observability gap does this reveal?**
> The dashboard is aggregating across all traffic, masking a segment-specific problem — this is a cardinality/dimensionality gap: you need to be able to slice latency by customer tier (a high-cardinality-adjacent dimension) rather than only viewing an overall aggregate.

**Q32. Scenario: a service returns HTTP 200 for a request but the response body is empty due to a downstream timeout that was silently swallowed. Would monitoring based on HTTP status codes catch this? Why or why not?**
> No — monitoring based on status codes only checks a predefined signal (the status code), and 200 looks healthy even though the actual content is wrong. This is a classic unknown-unknown: the failure mode (empty body on success status) wasn't anticipated, so no check exists for it; only inspecting logs, request/response bodies, or traces would reveal it.

**Q33. Predict the output: if you only add unstructured, freeform text logs (no fields, no trace IDs, no timestps in structured format) to a service, will you have "observability" per this roadmap's definition?**
> No, or only weak observability — you'll have some raw information, but without structure (parseable fields, correlation IDs) you can't efficiently query, filter, or correlate that data with metrics/traces, which severely limits how many novel questions you can actually answer. Day 6 covers why structured logging fixes this.

**Q34. Predict the output: you add a `user_id` label to every Prometheus metric in a system with 10 million users. What happens to your metrics backend?**
> You'd likely see a cardinality explosion — the number of unique time series could grow by orders of magnitude (up to one per user per metric), causing massive memory/storage growth and potentially crashing or badly degrading the metrics backend. This is exactly why user_id belongs in logs/traces, not metric labels.

**Q35. Predict the output: your monitoring system alerts "CPU > 90%" and pages you at 3am, but the actual user-facing symptom (slow checkout) started 20 minutes before CPU crossed that threshold. What does this reveal about the relationship between monitoring and root cause?**
> It shows monitoring thresholds are lagging or proxy indicators, not causes — CPU crossing a threshold is a downstream symptom, and by the time it alerts, the underlying issue (a query regression, a leaking connection pool, etc.) has already been happening for a while. Observability tooling would let you trace back from the symptom to the originating request/service/deploy.

**Q36. Why is "we have Prometheus and Grafana" not the same as "we have observability"?**
> Prometheus/Grafana give you monitoring and visualization of predefined metrics — valuable, but on their own they don't provide the ability to drill into arbitrary high-cardinality dimensions or reconstruct per-request causality; true observability requires that queryable depth, often via logs and traces alongside metrics.

**Q37. What's the relationship between "three pillars" and "correlation" that Day 21 will dig into?**
> Having logs, metrics, and traces separately is necessary but the real power comes from correlating them — e.g., seeing a metric spike, jumping to the traces in that time window, then to the specific logs for a slow trace — which requires shared identifiers (trace IDs, timestamps) across all three, previewed today and built out on Day 21.

**Q38. Why do SREs commonly say "monitor for symptoms, not causes"?**
> Because there are too many possible causes to enumerate and alert on individually, but user-facing symptoms (error rate, latency, availability) are a small, stable set — monitoring symptoms catches "something is wrong" reliably, while root-cause investigation (aided by observability) happens afterward.

**Q39. What is a "wide event" (or "canonical log line"), and how does it relate to cardinality/dimensionality?**
> A wide event is a single, richly-structured log record per unit of work (e.g., one line per HTTP request with dozens of fields — user_id, duration, db_query_count, feature_flags, etc.) rather than many small scattered log lines. It embraces high dimensionality and cardinality deliberately, because that richness is what enables answering novel questions later.

**Q40. Why might an engineer new to observability be tempted to just add more metrics rather than better logs/traces, and why is that not always the fix?**
> Metrics feel familiar and cheap to add, and dashboards feel reassuring. But metrics are inherently pre-aggregated and low-cardinality by design, so adding more of them still can't answer "which specific request/user/segment" questions — that requires the richer, high-cardinality data that logs and traces are suited to carry.

**Q41. Scenario: a deploy goes out and five minutes later error rate climbs from 0.1% to 2%. Your metrics dashboard shows this clearly. Is metrics-based monitoring enough here, or do you still need observability tooling?**
> The metrics dashboard did its job — it caught a known-unknown (error rate rising) via monitoring. But to find out *which* code path in the deploy is causing errors, for which requests, you'll want to drill into logs/traces filtered to that time window — that drill-down step is the observability capability layered on top of the alert.

**Q42. Why is "just add logging everywhere" not automatically the same as achieving observability?**
> Volume of logs isn't the goal — unstructured, uncorrelated, or overly verbose logging without consistent fields, IDs, and sampling strategy can create noise that's expensive to store and hard to query, actually hurting your ability to find signal. Observability is about queryability and correlation, not raw volume.

**Q43. What's a practical first check to see if a system currently has "monitoring" but not "observability"?**
> Ask: "If a user reports a specific, unusual problem not covered by an existing alert, can an engineer answer 'why' within minutes using existing telemetry, or do they need to add new logging/instrumentation and wait for it to reproduce?" If it's the latter, you likely have monitoring without much observability.

**Q44. Why does the roadmap introduce OpenTelemetry's role as early as Day 2, right after this conceptual day?**
> Because OpenTelemetry provides the vendor-neutral instrumentation layer that generates logs, metrics, and traces in a consistent, correlatable way — understanding it early gives you the practical "how do these pillars actually get produced and linked" grounding before you go deep on each pillar individually in Phases 2-4.

**Q45. In your own metalearning map, why should "PromQL" and "trace context propagation" likely receive more of your hour-budget than, say, memorizing every Prometheus function name?**
> Per the 80/20 principle, being able to write practical, correct queries and understand how trace IDs propagate across services will be used constantly in real troubleshooting, while exhaustive recall of every function name is easily looked up in docs and has low practice value relative to time spent.

**Q46. What should go into a personal "metalearning map" for this roadmap? Name at least 4 components.**
> A good map includes: (1) the core concepts to master (three pillars, PromQL, OTel, SLOs, correlation), (2) the tools you'll actually touch hands-on (Prometheus, Grafana, Loki/Elasticsearch, Jaeger/Tempo, OpenTelemetry SDKs), (3) benchmarks/checkpoints (e.g., "by Day 14 I can build a RED-method dashboard from scratch"), and (4) resources per topic (docs, blogs, one hands-on project each) — essentially a personalized syllabus you'll refine as you go.

**Q47. Why is it useful to revisit and adjust your metalearning map partway through the 25 days, rather than treating Day 1's version as final?**
> As you learn, you'll discover which topics are harder or more relevant to your actual work than expected (metalearning itself is a skill you improve with feedback) — adjusting the map mid-course lets you reallocate remaining hours toward genuine gaps rather than rigidly following a first-day guess.

**Q48. Compare/contrast: "alerting" vs. "monitoring" vs. "observability" — are these three the same thing?**
> No. Monitoring is the practice of watching predefined signals; alerting is the specific mechanism of notifying humans when a monitored signal crosses a threshold (a subset/output of monitoring); observability is the broader system property of being able to understand internal state — including novel states — from telemetry. Alerting depends on monitoring; monitoring is one input to, but not the whole of, observability.

**Q49. Scenario: you inherit a system with beautiful Grafana dashboards for every service, but engineers still spend hours grepping through raw server logs during incidents to find root cause. What does this suggest about the system's observability maturity?**
> It suggests decent monitoring maturity (metrics/dashboards exist) but low observability maturity — the telemetry isn't structured or correlated well enough to let engineers quickly pivot from "something is wrong" (shown by dashboards) to "here's exactly why" (requiring manual, slow log archaeology). This gap is precisely what structured logging, tracing, and correlation (later phases) are meant to close.

**Q50. Predict the output: if today you only memorize the definitions of "logs, metrics, traces" but skip the hands-on project and the recall questions, how well will this knowledge likely transfer to Day 15's distributed tracing work?**
> Likely poorly — without directness (hands-on practice) and retrieval (active recall), the definitions will sit as shallow, easily-forgotten facts rather than durable, applied understanding; you'd probably need to relearn the basics on Day 15 instead of building directly on them, which is exactly what the ultralearning principles here are designed to prevent.

## ✅ Day 1 wrap-up

- You can now explain, with a concrete example, why monitoring catches known-unknowns while observability is built to survive unknown-unknowns
- You've felt firsthand (via the hands-on project) how a metrics/status-code check can stay green while a real problem occurs
- You have a first-draft personal metalearning map for the next 24 days, ready to refine as you go
- Tomorrow (Day 2) you'll see how OpenTelemetry ties the three pillars together as the connective tissue of modern observability

You've built the foundation the rest of this roadmap stands on — nice work, and see you on Day 2.
