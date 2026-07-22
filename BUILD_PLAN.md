# Fraud Detection Pipeline — Build Plan

My working plan for building this in stages. Start with the dumbest version that works, then
add one system-design idea at a time. Every stage lines up with something I'm studying for
interviews, so building the project *is* the studying. I don't move on until the current stage
runs and I can explain why it works.

Stack: **Java 17 + Spring Boot, Apache Kafka, Redis, PostgreSQL, Docker Compose.**

## End goal

```
transaction producer  ->  Kafka  ->  scoring service  ->  Postgres (decisions)
   (simulator)                          |   ^                     |
                                        v   |                     v
                                  Redis feature store        alerts topic -> dashboard
```

A transaction comes in, the service pulls recent behavior features from Redis, runs rules + an
ML model, writes an APPROVE / REVIEW / DECLINE decision to Postgres, and fires an alert if it
looks fraudulent. Multiple scoring instances run at once and split load via a Kafka consumer group.

## Why this project (resume angle)

Fraud detection forces the interesting trade-offs on purpose: it has to be fast (a payment
can't wait), scalable (traffic spikes), and can't lose data. That's a tour of every
system-design topic, so I can talk about caching, queues, scaling, and consistency using code
I actually wrote instead of a whiteboard.

## Stages

### Stage 0 — Foundations  ✅
Repo, README, architecture diagram, this plan, and the domain model (`Transaction`, `Decision`).
Concept: single-server baseline.

### Stage 1 — Single-service MVP  ✅
One Spring Boot app. `POST /score` takes a transaction, a rules engine scores it, the
transaction + decision get stored in Postgres. Rules are pluggable `@Component`s.
Concept: the vertical baseline — one machine doing everything, and where it falls over
(it blocks on every DB call).

### Stage 2 — Feature store + caching (Redis)  ✅
Move "recent behavior" features into Redis: rolling per-card counters (txns in the last
1/5/60 min). Read those instead of a `COUNT(*)` every time.
Concept: caching — why cache computed features not raw queries, and TTLs as a natural fit for
time-windowed features.

### Stage 3 — Decouple with Kafka (async)  ✅
A producer/simulator publishes transactions to a Kafka topic; the scoring service becomes a
consumer, writes decisions, and publishes flagged ones to a `fraud-alerts` topic.
Concept: asynchronism and queues — buffering spikes, back-pressure, at-least-once delivery.

### Stage 4 — Scale it out  ✅
Run multiple scoring instances in one consumer group, partition the topic by `cardId` so a
card's traffic lands on the same consumer (keeps Redis counters consistent).
Concept: horizontal scaling and load balancing; partition key choice.

### Stage 5 — Add the ML model  ✅
Train a model offline in Python (logistic regression, then XGBoost) on a public dataset,
export it, and serve it alongside the rules. Compare catch rates.
Concept: service boundaries and the trade-off of a network hop vs. embedding the model.

### Stage 6 — Observability + dashboard + load test  ✅
Metrics (Micrometer -> Prometheus): throughput, p95 latency, decisions/sec. A small live
dashboard. Load test it and find where it slows down.
Concept: latency vs. throughput, measure before optimizing.

### Stage 7 — Polish for recruiters  ✅
`docker compose up` for the whole thing, tests, CI badge, clean README, resume bullets with
real numbers from the load test.
Concept: consistency vs. availability (CAP) — write up the trade-offs I chose.

## Resume bullets (measured)

- Built a real-time fraud detection pipeline in **Java/Spring Boot** processing **~600
  transactions/sec** on a single instance through **Kafka**, at **~15 ms mean scoring latency**
  (107 ms p95 end-to-end), with a **Redis** feature store keeping velocity lookups off the DB hot path.
- Scaled horizontally with **Kafka consumer groups** partitioned by card, with automatic
  rebalancing on instance failure.
- Combined a rules engine with a **scikit-learn** model behind a fail-open service boundary, and
  instrumented the pipeline with **Micrometer/Prometheus** metrics and a live dashboard;
  validated throughput and latency with a custom load-testing harness.

## Where the trade-offs are written up

The full design write-up - failure modes, consistency vs. availability, delivery semantics,
and what I'd fix next - lives in [`DESIGN.md`](DESIGN.md).

## Guardrails so I actually understand it
- After each stage I write a few sentences on *why*, not just what.
- I don't commit code I couldn't explain in an interview.
- Every stage has to run before I start the next one.
