# Day 8 — Log Storage & Indexing

You've been shipping logs since Day 7 — now let's talk about what happens when they land. The backend you choose determines whether a 2am query returns in 200ms or times out, and whether your storage bill is manageable or terrifying. Today you'll understand the fundamental trade-off between full-text indexing (Elasticsearch/OpenSearch) and label-based indexing (Loki), and you'll run a real Loki + Grafana stack yourself.

## 🎯 Learning objectives

- [ ] Explain the difference between an inverted index (Elasticsearch/OpenSearch) and a label index (Loki)
- [ ] Describe how Loki stores log content in compressed "chunks" in object storage
- [ ] Articulate why Loki is cheaper but slower for content search, while Elasticsearch is faster for content search but more expensive to run
- [ ] Explain hot/warm/cold tiering and how it reduces storage cost
- [ ] State a rule of thumb for choosing Loki vs Elasticsearch/OpenSearch for a given use case
- [ ] Run Loki + Grafana locally in Docker and query logs with LogQL
- [ ] Identify at least two ways high-cardinality labels can break a Loki deployment

## ⏱️ Your 60-minute plan

| Segment | Time | Activity |
|---|---|---|
| Warm-up recall | 5m | From memory, write down: what is structured logging, what does a log shipper do, and name one shipper (Day 6/7 retrieval). Then jot your guess: "why might indexing *every word* of every log be expensive?" |
| Learn | 20m | Read Loki's "How Loki works" docs + skim the Elasticsearch inverted-index concept. Focus on: chunks vs inverted index, index vs no-index trade-off, hot/warm/cold tiers |
| Do | 25m | Hands-on project: spin up Loki + Grafana in Docker, push logs, query with LogQL, inspect storage layout |
| Reinforce | 10m | Run through as many of the 50 active-recall questions as you can without peeking; flag the ones you missed for tomorrow's warm-up |

## 📚 Free resources

- **Grafana Loki official docs — "Loki architecture"** — the canonical explanation of chunks, the index, ingesters, and object storage; read this first. (type: docs)
- **Grafana Loki docs — "Loki design documents / Loki: like Prometheus, but for logs"** — the original design philosophy blog post explaining why Loki indexes only labels, not content. (type: blog)
- **Grafana Labs blog** — regularly publishes deep dives on Loki internals, cost optimization, and cardinality pitfalls; search "Loki cardinality" and "Loki storage". (type: blog)
- **Elastic official docs — "Inverted index"** — Elasticsearch's own explanation of how the inverted index works under the hood. (type: docs)
- **OpenSearch documentation — "Index" concepts** — the open-source fork's docs, nearly identical concepts to Elasticsearch, useful if your org uses OpenSearch. (type: docs)
- **Google SRE Book (sre.google/books)** — chapter on monitoring/logging philosophy gives useful framing on retention vs cost trade-offs at scale. (type: book)
- **Grafana Labs YouTube channel** — has walkthroughs of Loki setup, LogQL querying, and Grafana dashboards with logs. (type: video)
- **"Elasticsearch: The Definitive Guide" (free online, elastic.co)** — older but the fundamentals of inverted indices and sharding still hold. (type: book)
- **Docker Hub — grafana/loki and grafana/grafana official images** — the images you'll actually run today, with usage docs on the image pages. (type: docs)

## 🛠️ Hands-on project (free, ~25 min)

**Goal:** Run Loki + Grafana locally, push some structured logs, and query them with LogQL so you *feel* the label-based indexing model instead of just reading about it.

1. Create a scratch directory and a `docker-compose.yml` with two services: `loki` (image `grafana/loki:latest`, port `3100`) and `grafana` (image `grafana/grafana:latest`, port `3000`). Add a Loki datasource pointing Grafana at `http://loki:3100`.
2. `docker compose up -d` and confirm both containers are healthy (`docker ps`, check `curl http://localhost:3100/ready`).
3. Push sample logs into Loki. Easiest path: install `logcli` (Loki's CLI) OR use `curl` against Loki's push API (`POST /loki/api/v1/push`) with a small JSON payload containing labels like `{job="myapp", env="dev"}` and a few log lines, some with the word "error" and some without.
4. Open Grafana at `localhost:3000`, go to Explore, select the Loki datasource, and run a LogQL query: `{job="myapp"}` to see everything, then `{job="myapp"} |= "error"` to filter by content.
5. Inspect what Loki actually indexed: notice the query by label (`job`, `env`) is instant, while `|= "error"` is a linear scan through chunk content — this is the "index vs no-index" trade-off you read about, made visible.

**Expected outcome:** You can see log lines in Grafana, filter by label instantly, and filter by content via a grep-style scan — and you understand *why* those two operations feel different in speed as your data grows.

**Stretch goal:** Add a second label dimension (e.g., `pod="pod-1"`, `pod="pod-2"`) with many distinct values, then read about why doing this with something like `request_id` as a label (instead of content) would blow up Loki's index — try to reason through why before checking the docs.

## 🧠 50 questions for active recall

**Q1. What is the core function of a log storage backend beyond just "holding" the data?**
> It must let you retrieve relevant logs fast, at scale, without scanning everything — that's what indexing solves. Storage alone (like a flat file) is cheap but useless for anything beyond sequential reads; a backend adds structure so you can jump directly to relevant data.

**Q2. What is an inverted index, in plain terms?**
> It's a mapping from a token (a word) to the list of documents (log lines) that contain it — the reverse of a normal document-to-words mapping, hence "inverted." Elasticsearch/OpenSearch build one of these for every field you index, which is why full-text search across arbitrary words is fast.

**Q3. Why is an inverted index expensive to build and store?**
> Every unique word (or token) across every log line needs an index entry, and that index typically ends up comparable in size to the original data — sometimes larger. At high log volume, this means large storage overhead and heavy CPU/memory cost during ingestion (tokenizing, analyzing, updating index structures).

**Q4. What does Loki index instead of full text?**
> Loki indexes only the labels attached to a log stream (e.g., `job`, `namespace`, `pod`, `env`) — small key-value metadata — not the log line content itself. The actual log content is stored compressed in chunks that Loki scans through at query time.

**Q5. What is a "stream" in Loki?**
> A stream is the unique set of log lines sharing an identical set of label values (e.g., `{job="api", env="prod", pod="api-7f9"}`). Loki's index maps each unique label combination to the chunks holding that stream's log lines.

**Q6. What is a "chunk" in Loki?**
> A chunk is a compressed block of log lines belonging to one stream, batched together and flushed to object storage (S3, GCS, or filesystem) once it fills up or times out. Chunks are the actual log content storage — cheap, compressed, and not individually indexed.

**Q7. Why is Loki's index so much smaller than Elasticsearch's?**
> Because it only stores label metadata (a small, low-cardinality set of key-value pairs) rather than every word in every log line. The index size scales with the number of unique label combinations (streams), not with total log volume or vocabulary.

**Q8. If Loki doesn't index log content, how does `|= "error"` work in a LogQL query?**
> Loki first uses the label selector (e.g., `{job="api"}`) to find the relevant chunks via the index, then decompresses and linearly scans (grep-style) through those chunks' content for the string "error." This is why label selection is instant but content filtering is proportional to the volume of matching chunks.

**Q9. What is the famous one-line description of Loki's design philosophy?**
> "Like Prometheus, but for logs" — Loki borrows Prometheus's model of indexing only metadata/labels cheaply, while leaving the bulk data (log content, like Prometheus leaves sample values) in cheap storage, scanned on demand.

**Q10. Why does this design make Loki cheaper to operate at scale than Elasticsearch?**
> Because the expensive, memory/CPU-hungry part (full-text indexing) is skipped entirely, and chunk storage in object storage (S3-class) is extremely cheap per GB compared to running large stateful Elasticsearch clusters with SSDs for index shards. You trade query-time compute for storage-time savings.

**Q11. What's the corresponding downside of Loki's approach?**
> Content-based search (full-text queries, complex filtering not aligned to labels) is slower because it requires scanning chunk data rather than an instant index lookup. If your primary use case is "search for this exact error string across all services," Elasticsearch will typically answer faster.

**Q12. What happens if you put a high-cardinality field (like `request_id` or `user_id`) as a Loki label?**
> You create one stream per unique value, exploding the number of streams and therefore the index size and the number of small chunks — this is the classic "cardinality explosion" that degrades Loki's performance and can even crash ingesters from memory pressure. High-cardinality fields belong in the log line content, not labels.

**Q13. What is the recommended fix if you've accidentally used a high-cardinality field as a label?**
> Remove it from labels and instead put it in the structured log body (e.g., as a JSON field), then use LogQL's parser stages (`| json`, `| logfmt`) to filter or extract it at query time instead of indexing it.

**Q14. Compare index cardinality concerns in Loki vs Elasticsearch.**
> In Loki, cardinality concerns apply to labels (the index) — few unique label combos is good. In Elasticsearch, cardinality concerns apply more to fields you choose to index/aggregate on (like using a raw high-cardinality field for aggregations), but Elasticsearch's inverted index handles free-text and high-cardinality string values far more gracefully because that's its actual purpose.

**Q15. What is a document, in Elasticsearch/OpenSearch terms?**
> A document is a single JSON object representing one unit of data (e.g., one log line), stored with a set of fields; each field can be individually indexed and typed (text, keyword, date, number, etc.).

**Q16. What is a "shard" in Elasticsearch/OpenSearch?**
> A shard is a horizontal partition of an index — Elasticsearch splits an index into multiple shards distributed across nodes, each an independent Lucene index, enabling parallel search and horizontal scaling.

**Q17. Why do Elasticsearch clusters typically need more resources than a Loki deployment for the same log volume?**
> Because building and maintaining inverted indices (tokenizing text, updating index segments, merging segments, keeping hot indices in memory/SSD for speed) is CPU, memory, and disk-I/O intensive, whereas Loki just compresses and appends log content to object storage with a lightweight metadata index.

**Q18. What does "index vs no-index" trade-off mean in one sentence?**
> You either pay cost upfront (at ingest/indexing time) for fast arbitrary queries later (Elasticsearch), or you pay cost at query time (scanning chunks) in exchange for very cheap, simple ingestion and storage (Loki).

**Q19. When would you choose Elasticsearch/OpenSearch over Loki?**
> When you need powerful full-text search, complex aggregations, fuzzy/relevance-based search, or you're doing security/audit log analysis where analysts search arbitrary free text frequently and query latency on content matters more than infrastructure cost.

**Q20. When would you choose Loki over Elasticsearch/OpenSearch?**
> When your logs are already well-labeled (e.g., via Kubernetes metadata) and most queries start by narrowing to a service/pod/namespace, cost efficiency at high volume matters, and you want your logs stack to feel consistent with a Prometheus-style label model — especially if you're already using Grafana + Prometheus.

**Q21. What's a practical rule of thumb summarizing the choice?**
> "If you know what you're looking for structurally (which service, which pod), use Loki; if you need to search for content you don't know the location of, use Elasticsearch/OpenSearch." Many orgs actually run both for different purposes.

**Q22. Does using Loki mean you get no full-text search capability at all?**
> No — Loki still lets you filter by content with LogQL line filters (`|=`, `!=`, `|~`, `!~`) and parse structured fields, it's just implemented as scanning rather than an indexed lookup, so it's less optimized than Elasticsearch for large-scale arbitrary text search across huge unfiltered datasets.

**Q23. What is retention, in the context of log storage?**
> Retention is the policy defining how long log data is kept before being deleted or archived — e.g., "keep logs 30 days" — balancing the need to investigate past incidents against storage cost.

**Q24. Why can't you (usually) just "keep all logs forever"?**
> Storage cost grows linearly (or faster, with index overhead) with retention length, and most log lines are never looked at again after a short window — so unlimited retention is usually not worth the cost, especially for high-volume debug-level logs.

**Q25. What is hot/warm/cold tiering?**
> A strategy of storing recent, frequently-queried data on fast/expensive storage ("hot"), older/less-queried data on slower/cheaper storage ("warm"), and rarely-accessed archival data on the cheapest storage or object storage ("cold") — moving data down the tiers automatically as it ages.

**Q26. Why does tiering reduce cost without losing all old data?**
> Because you keep the expensive, fast resources (RAM, SSD, more replicas) only for the small slice of data actually queried often (recent logs), while older data still exists but on cheaper media (HDD, S3/GCS) with acceptable slower retrieval — you're not throwing data away, just demoting it.

**Q27. Give an example of hot/warm/cold tiering in Elasticsearch specifically.**
> Elasticsearch's Index Lifecycle Management (ILM) can automatically roll indices from hot nodes (fast SSDs, more shards/replicas) to warm nodes (cheaper storage, fewer replicas) to cold/frozen tiers (searchable snapshots on object storage) based on index age or size.

**Q28. How does Loki's architecture relate to tiering?**
> Loki is inherently tiered by design — recent chunks may be cached (e.g., in memory or a fast cache layer) for quick queries, while the bulk of chunks live in cheap object storage (S3/GCS/Azure Blob) from day one, rather than needing a separate migration step like Elasticsearch's ILM.

**Q29. What is a "searchable snapshot" (Elasticsearch/OpenSearch concept)?**
> A snapshot stored in cheap object storage (like S3) that can still be queried directly without fully restoring it to a live index — it's slower than a hot index but far cheaper, letting you retain searchability over very old data at lower cost.

**Q30. Why is object storage (S3/GCS-class) central to Loki's cost advantage?**
> Because object storage costs a fraction per GB compared to block storage/SSDs backing traditional index-heavy databases, and Loki's design deliberately minimizes what needs to live on expensive, low-latency storage (just the small label index), pushing nearly all data into object storage.

**Q31. What are the three main components in Loki's architecture (conceptually)?**
> Distributor (receives and routes incoming log streams), ingester (buffers, compresses into chunks, flushes to storage), and querier (handles LogQL queries by consulting the index and reading chunks) — plus the index store and chunk store (often object storage) they depend on.

**Q32. What is LogQL?**
> Loki's query language, syntactically similar to PromQL, used to select streams by label matchers (e.g., `{job="api"}`) and then apply line filters, parsers, and metric aggregations (e.g., counting error rates over time) on the matched log content.

**Q33. Give the LogQL syntax for: select logs from job "api", then filter for lines containing "timeout".**
> `{job="api"} |= "timeout"` — the `{}` selects the stream(s) by label, and `|=` is a line filter requiring the substring to be present.

**Q34. What's the difference between `|=` and `|~` in LogQL?**
> `|=` does a simple substring match (fast, no regex overhead), while `|~` applies a regex match, which is more flexible but more computationally expensive — prefer `|=` when you just need a literal substring.

**Q35. What does the LogQL `| json` parser stage do?**
> It parses each log line as JSON and extracts its fields into labels usable for further filtering or aggregation within that query (not persisted to the index), e.g., `{job="api"} | json | status_code="500"`.

**Q36. Why is it better to parse JSON fields at query time (Loki) rather than index them at ingest time?**
> Because it keeps the ingest-time index small and cheap (avoiding cardinality blowup) while still giving you the flexibility to filter on any field when you actually query — you pay the parsing cost only for the queries you run, not for every log line ingested.

**Q37. What happens if you run a LogQL query with only a line filter and no label selector, e.g. `|= "error"` alone?**
> This isn't valid LogQL — a query must start with a label matcher (stream selector) like `{job="api"}` before any filters; Loki requires you to first identify which streams to scan, since it has no global full-text index to search independent of labels.

**Q38. Predict the output: you push logs with labels `{env="prod"}` and `{env="staging"}`, both containing the line "error connecting to db". You query `{env="prod"} |= "error"`. What do you get?**
> Only the matching log lines from the `env="prod"` stream — the staging stream's matching content is not returned because the label selector already scoped the query to prod only, before the content filter runs.

**Q39. Predict what happens if a Loki label has extremely high cardinality (e.g., you accidentally label by `trace_id`, generating millions of unique values) under sustained high log volume.**
> Loki will create a huge number of tiny streams/chunks, index size balloons, ingesters can run out of memory buffering many small chunks before flush, and both ingestion and query performance degrade sharply — this is the textbook Loki cardinality failure mode.

**Q40. What is index lifecycle management (ILM) used for in Elasticsearch/OpenSearch?**
> Automating the rollover, shard reduction, tier migration (hot→warm→cold→delete), and eventual deletion of indices based on age, size, or document count — so retention and tiering policies don't require manual index management.

**Q41. Why might full-text search cost more in compute even if storage were free?**
> Because analysis (tokenizing, stemming, normalizing text), building/merging index segments, and keeping enough of the index in memory for fast lookups all consume CPU and RAM regardless of disk cost — the compute overhead of building rich indices is separate from storage overhead.

**Q42. What's a practical downside of running Elasticsearch/OpenSearch clusters operationally, beyond raw compute cost?**
> They require careful cluster management: shard sizing, node roles, index rollover strategy, avoiding split-brain, managing JVM heap — meaningfully more operational complexity than Loki's simpler, largely stateless-plus-object-storage model.

**Q43. Why do many teams pair Loki with Grafana specifically?**
> Because Grafana natively supports LogQL and can correlate logs with metrics (Prometheus) and traces (Tempo) in one UI, and Loki's label model matches Prometheus's, making cross-pillar correlation (Day 21 topic) far more seamless.

**Q44. Can Elasticsearch/OpenSearch also integrate with Grafana?**
> Yes — Grafana has a native Elasticsearch/OpenSearch data source too, so the choice of backend doesn't lock you out of using Grafana as your visualization layer; the difference is in the query language and underlying cost/performance model, not the UI.

**Q45. What's a reasonable retention strategy that balances cost and utility for most teams?**
> Keep high-volume debug/verbose logs for a short window (days), keep info/warn/error logs longer (weeks to a couple months), and archive only what's needed for compliance or long-term trend analysis to cold/cheap storage — tiering by log level and age together, not a single blanket retention period.

**Q46. Why does "how you'll query it" matter more than "how much you'll log" when picking a backend?**
> Because the backend's whole value proposition is about query patterns: if 95% of your queries start with "which service" (label-first), Loki is cheap and fast; if your queries are "find this error message anywhere," you need full-text search regardless of volume. Picking based on volume alone misses the actual bottleneck.

**Q47. What does it mean that Loki chunks are "compressed"? Why does that matter for cost?**
> Log content is highly repetitive (similar structure across lines), so compression (e.g., gzip/snappy) dramatically shrinks the bytes actually stored in object storage, directly reducing storage cost — much more so than an inverted index, which by nature can't compress as aggressively since it needs fast random access into token positions.

**Q48. Scenario: your team has Kubernetes with well-structured pod/namespace/service labels, cares mostly about cost, and mostly debugs by first identifying which service is failing. Which backend fits better, and why?**
> Loki — the label model aligns naturally with Kubernetes metadata you already have, your query pattern is label-first (which service, then look inside), and cost efficiency is a stated priority; Elasticsearch's extra full-text power would be paid for but underused.

**Q49. Scenario: a security team needs to search across all logs org-wide for a specific IP address or credential string with no prior knowledge of which service it came from. Which backend fits, and why?**
> Elasticsearch/OpenSearch — this is a classic full-text, "needle in haystack across everything" search with no label to narrow by first, which is exactly what an inverted index is optimized for; a Loki-only setup would require scanning enormous amounts of chunk content and would be much slower.

**Q50. Predict the output: you configure a hot/warm/cold ILM policy in Elasticsearch that moves indices to cold storage after 7 days, and someone queries data that's 30 days old. What should you expect, compared to querying data from today?**
> The query will still succeed but will likely be noticeably slower (and possibly need to "thaw" or access a searchable snapshot from object storage) because the data now lives on cheaper, less performant storage tiers optimized for cost rather than latency — this is the intended trade-off of tiering, not a malfunction.

## ✅ Day 8 wrap-up

- You can now explain, in concrete terms, why Loki is cheap-but-content-search-slow and Elasticsearch/OpenSearch is powerful-but-heavier, and choose between them based on query pattern rather than gut feeling.
- You understand chunks, object storage, and label-based indexing well enough to reason about cardinality risk before it bites you in production.
- You've run a real Loki + Grafana stack and felt the difference between an instant label lookup and a content scan firsthand.
- Tomorrow (Day 9) you'll build on this by learning to query and alert on logs with LogQL in depth — turning the storage model you learned today into actionable insight.

Nice work getting through the storage layer — this is the part most engineers gloss over, and you didn't. That foundation will make every LogQL query you write from here on feel intuitive instead of magical.
