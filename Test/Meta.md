# 🔭 The 25-Hour Observability Ultralearning Roadmap

> **Master observability, logging, and metrics in 25 focused days — one hour a day.**
> Built on Scott H. Young's *Ultralearning* principles and the 80/20 rule.

You don't need months to become genuinely competent in observability. You need **directness** (learning by doing the real thing), **drill** (attacking your weakest sub-skills), **retrieval** (recalling instead of re-reading), and **focus**. This roadmap gives you all four, one hour at a time. Be patient with yourself, show up daily, and trust the compounding. You've got this. 💪

---

## How to use this roadmap

Each day is designed for **~60 minutes** and follows the same rhythm:

| Segment | Time | Ultralearning principle |
|---|---|---|
| **Warm-up recall** | 5 min | *Retrieval* — quiz yourself on yesterday before starting |
| **Learn** | 20 min | *Directness* — read/watch the day's core resource with a goal |
| **Do** | 25 min | *Directness + Drill* — the hands-on project/exercise |
| **Reinforce** | 10 min | *Retrieval + Feedback* — work through the 50 Q&A actively |

**Rules of the game (from Ultralearning):**
1. **Metalearning first.** Day 1 builds your map of the whole field so every later hour has a place to land.
2. **Directness over passivity.** Always run the tool, write the query, break the dashboard. Reading *about* Prometheus is not learning Prometheus.
3. **Retrieve, don't review.** Cover the answer, say it out loud, *then* check. The struggle is the learning.
4. **Spaced repetition.** Re-answer a random 10 questions from 3 days ago during each warm-up.
5. **Feedback loops.** When an exercise fails, that error *is* the lesson — sit with it.

### The 80/20 focus
Observability is a huge field. This roadmap deliberately concentrates on the ~20% of concepts that deliver ~80% of real-world value: **the three pillars (logs, metrics, traces)**, **Prometheus + PromQL**, **structured logging + aggregation**, **OpenTelemetry**, **Grafana**, and **SLOs/alerting**. Vendor-specific trivia is skipped on purpose.

### How to work the 50 questions each day
- Treat them as **active recall**, not reading. Answer *before* revealing the detailed answer.
- Mark any you miss. These become your **drill** targets and your spaced-repetition deck.
- Aim for ~10 minutes; if you run long, that's fine — the recall is the point.

---

## 🗺️ The full 25-day map

### Phase 1 — Foundations (Days 1–5)
| Day | Topic | Core outcome |
|---|---|---|
| 1 | Observability vs. monitoring + your metalearning map | Explain the *why* and the three pillars |
| 2 | The observability landscape & OpenTelemetry's role | Name the ecosystem and how pieces fit |
| 3 | Logging fundamentals | Structured vs. unstructured, log levels, anatomy |
| 4 | Metrics fundamentals | Counters, gauges, histograms; the Four Golden Signals |
| 5 | Tracing fundamentals | Spans, traces, context propagation |

### Phase 2 — Logging deep dive (Days 6–9)
| Day | Topic | Core outcome |
|---|---|---|
| 6 | Structured logging in practice | Emit clean JSON logs from a real app |
| 7 | Log pipelines & shipping | Fluent Bit / Vector collect → route → transform |
| 8 | Log storage & indexing | Loki vs. Elasticsearch/OpenSearch trade-offs |
| 9 | Querying & alerting on logs | LogQL, dashboards, log-based alerts |

### Phase 3 — Metrics deep dive (Days 10–14)
| Day | Topic | Core outcome |
|---|---|---|
| 10 | Prometheus architecture & data model | Scraping, exposition format, labels |
| 11 | PromQL I — selectors & rates | Query real time-series confidently |
| 12 | PromQL II — aggregation & rules | Recording/alerting rules, histograms |
| 13 | Instrumentation & the RED/USE methods | Add custom metrics that matter |
| 14 | Grafana & visualization | Build dashboards that tell a story |

### Phase 4 — Tracing & OpenTelemetry (Days 15–18)
| Day | Topic | Core outcome |
|---|---|---|
| 15 | Distributed tracing in depth | Sampling, spans, baggage, latency analysis |
| 16 | OpenTelemetry architecture | SDK, API, Collector, OTLP |
| 17 | Instrumenting apps with OTel | Auto + manual instrumentation |
| 18 | Trace backends & analysis | Jaeger / Tempo, service graphs |

### Phase 5 — Integration & production (Days 19–25)
| Day | Topic | Core outcome |
|---|---|---|
| 19 | Alerting done right | Alertmanager, routing, alert fatigue |
| 20 | SLIs, SLOs & error budgets | Define and defend reliability targets |
| 21 | Correlating the three pillars | Exemplars, trace↔log↔metric linking |
| 22 | Cardinality, cost & sampling | Keep observability affordable at scale |
| 23 | Observability for microservices | Patterns, health, golden signals per service |
| 24 | Production practice & o11y-as-code | On-call, incident response, dashboards in Git |
| 25 | Capstone + spaced-repetition plan | Build a full stack; lock in retention |

---

## 📁 Daily files

Each day lives in its own file with objectives, free resources, a hands-on project, and 50 Q&A.

- Day 1 → `Day-01.md`
- Day 2 → `Day-02.md`
- … through `Day-25.md`

> **Status:** Days 1–5 generated first. Later phases are produced in batches — ask to continue when you're ready for the next set.

---

## 🧰 One-time setup (do before Day 3)
You'll want a tiny lab. All free, all local:
- **Docker + Docker Compose** — to run Prometheus, Grafana, Loki, Jaeger locally.
- A scratch app in any language you know (Python/Go/Node) — your instrumentation target.
- A GitHub repo to commit your daily exercises (also doubles as a portfolio).

## 🔁 Spaced-repetition tracker
Keep a simple table. Each warm-up, re-answer 10 old questions and log honesty scores.

| Reviewed on (day) | Source day | # correct / 10 | Weak spots to drill |
|---|---|---|---|
|  |  |  |  |

---

*Learning is a skill, and you're about to get better at it. Onward. 🚀*
