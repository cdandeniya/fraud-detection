# Fraud Detection Pipeline

A real-time system that scores payment transactions for fraud as they stream in. Built in
Java/Spring Boot with Kafka, Redis, Postgres, and an ML model, scaled out horizontally.

![status](https://img.shields.io/badge/status-stage%205%20done-brightgreen)
![java](https://img.shields.io/badge/Java-17-orange)
![spring](https://img.shields.io/badge/Spring%20Boot-3-green)
![kafka](https://img.shields.io/badge/Apache%20Kafka-streaming-black)
![ml](https://img.shields.io/badge/ML-logistic%20regression-blue)
![license](https://img.shields.io/badge/license-MIT-blue)

> Heads up: this is an active learning project I'm building in stages, not a finished product.
> I'm building it partly to have a real project to point at, and partly to actually learn
> distributed systems by building one instead of just reading about it. The roadmap below shows
> what's done and what's next.

## What it does

Transactions stream in through Kafka. Multiple scoring instances share the load, running each
one through a rules engine *and* a machine-learning model, using recent per-card behavior
cached in Redis. Out comes a decision: **APPROVE**, **REVIEW**, or **DECLINE**, plus the reasons
why. Every transaction and its decision are written to Postgres, and anything flagged is
published to a separate alerts topic.

The interesting part isn't the fraud rules themselves — it's making this *fast, scalable, and
reliable at the same time*, which is where all the system-design stuff comes in.

## Architecture (Stage 5)

```
                         ┌──▶  scorer #1 ─┐   rules + ML model
 producer ─▶ Kafka ──────┼──▶  scorer #2 ─┼──────────┬────────▶  Postgres (txn + decision)
            transactions │    (consumer   │          │
            (3 partitions)     group)     │          ├──▶ Redis (velocity features)
                         └──▶  scorer #3 ─┘          ├──▶ model service (Python, /predict)
                                                     └──▶ Kafka: fraud-alerts ─▶ downstream
```

Four checks run on every transaction and add up to one risk score:
- **High amount** — over a configurable threshold (default $1000).
- **New country** — a card used somewhere it's never been seen before.
- **Velocity** — too many transactions in a short window, counted from Redis.
- **Model** — a logistic-regression model scores the transaction's features and contributes
  if its fraud probability crosses a threshold.

### Why a separate model service?

The model is a small **Python** service (`/model`) that the Java scorer calls over HTTP. That
keeps a clean language boundary — Python owns training and serving, Java owns the pipeline —
at the cost of a network hop per transaction. So the client **fails open**: if the model
service is slow or down, scoring falls back to the rules alone instead of taking the whole
pipeline down with it. Rules catch the obvious cases; the model catches subtler patterns; both
feed the same score.

The model trains on synthetic labeled data (`model/train.py`) so the repo is self-contained;
the plan is to swap in the Kaggle credit-card fraud dataset later.

## Getting started

You need JDK 17 and Docker.

### Everything in Docker, scaled out

```bash
docker compose up --build --scale scorer=3
```

Starts Postgres, Redis, Kafka, the Python model service, one producer, and three scorer
instances in a consumer group.

### Dev — infra in Docker, app from your IDE

```bash
docker compose up -d db redis kafka model    # infra + model service
./mvnw spring-boot:run                         # the app
```

Watch the alerts topic:

```bash
docker exec -it fraud-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic fraud-alerts --from-beginning
```

Run the tests with `mvn test` — they mock Kafka, Redis, and the model, and use in-memory H2,
so no infra is needed.

## Tech stack

| Piece | What I used | Why |
|-------|-------------|-----|
| Language | Java 17 | The language I want to be strong in for backend interviews |
| Service | Spring Boot 3 | Fast to build REST + Kafka consumers, industry standard |
| Streaming | Apache Kafka | Decouples ingest, handles spikes, splits load across a consumer group |
| Feature store / cache | Redis | Fast rolling per-card velocity counts, TTL cleans up old data |
| Database | PostgreSQL | Stores every transaction and its decision |
| ML | Python, scikit-learn, FastAPI | Trains + serves a fraud model behind a small HTTP service |
| Packaging | Docker + Compose | One command to run and scale the whole thing |
| Testing | JUnit 5 + Mockito + H2 | Unit-test the logic with no infra running |

## Roadmap

Each stage adds a single idea so I actually understand *why* it's there. Full detail in
[`BUILD_PLAN.md`](BUILD_PLAN.md).

- [x] **Stage 0 — Foundations:** repo, README, architecture, domain model
- [x] **Stage 1 — MVP:** single service, rules engine, Postgres storage
- [x] **Stage 2 — Caching:** Redis feature store for real-time velocity features
- [x] **Stage 3 — Async:** Kafka producer + consumer, decouple ingest from scoring
- [x] **Stage 4 — Scale:** multiple consumers in a group, partitioned by card
- [x] **Stage 5 — ML:** train + serve a fraud model alongside the rules
- [ ] **Stage 6 — Observability:** metrics, live dashboard, load testing
- [ ] **Stage 7 — Polish:** CI, docker-compose everything, write-up

## What I'm learning

I'm using this as the hands-on companion to the system design I'm studying:

- **Caching** → the Redis feature store (Stage 2) ✅
- **Async / queues** → Kafka between producer and scorer (Stage 3) ✅
- **Horizontal scaling & load balancing** → Kafka consumer groups + partitioning (Stage 4) ✅
- **Service boundaries & resilience** → the model service + fail-open client (Stage 5) ✅
- **Consistency vs. availability** → what I do when a piece goes down (Stage 7)

If you're a recruiter or hiring manager reading this: the short version is that it's a
real-time, event-driven transaction scoring service with an ML model, that scales
horizontally, and I can walk through every design decision in it. Thanks for stopping by.

## Notes

- Fraud data is synthetic (a simulator) or a public Kaggle dataset — no real card data.
- Personal learning project, not meant for production.

---

*Built by Chanul Dandeniya · Computer Science @ Stony Brook University*
