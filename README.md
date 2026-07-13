# Fraud Detection Pipeline

A real-time system that scores payment transactions for fraud as they stream in. Built in
Java/Spring Boot with Kafka, Redis, and Postgres, and scaled out horizontally.

![status](https://img.shields.io/badge/status-stage%204%20done-brightgreen)
![java](https://img.shields.io/badge/Java-17-orange)
![spring](https://img.shields.io/badge/Spring%20Boot-3-green)
![kafka](https://img.shields.io/badge/Apache%20Kafka-streaming-black)
![license](https://img.shields.io/badge/license-MIT-blue)

> Heads up: this is an active learning project I'm building in stages, not a finished product.
> I'm building it partly to have a real project to point at, and partly to actually learn
> distributed systems by building one instead of just reading about it. The roadmap below shows
> what's done and what's next.

## What it does

Transactions stream in through Kafka. Multiple scoring instances share the load, each running
transactions through a rules engine (using recent per-card behavior cached in Redis) and coming
back with a decision: **APPROVE**, **REVIEW**, or **DECLINE**, plus the reasons why. Every
transaction and its decision are written to Postgres, and anything flagged is published to a
separate alerts topic.

The interesting part isn't the fraud rules themselves — it's making this *fast, scalable, and
reliable at the same time*, which is where all the system-design stuff comes in.

## Where it's at (Stage 4)

The pipeline is event-driven and now scales horizontally:

```
                         ┌──▶  scorer #1  ─┐
 producer ─▶ Kafka ──────┼──▶  scorer #2  ─┼──▶  Postgres (txn + decision)
            transactions │    (consumer     │         │
            (3 partitions)     group)       │         ▼
                         └──▶  scorer #3  ─┘   Redis + Kafka: fraud-alerts ─▶ downstream
```

- The `transactions` topic has **3 partitions**. All the scorer instances join one Kafka
  **consumer group**, and Kafka hands each instance a share of the partitions. Run three and
  each gets one; if one dies, the group **rebalances** and the survivors pick up its partitions.
- Messages are keyed by **card id**, so every transaction for a given card goes to the same
  partition — which means the same consumer handles it, keeping that card's stream ordered.
- One instance can also run several consumer threads (`fraud.kafka.concurrency`), so you can
  scale up (threads) *and* out (instances).

### Why partition by card?

Kafka only guarantees ordering *within* a partition. Keying by card id keeps each card's
transactions in order and pinned to one consumer, so time-ordered logic like velocity behaves
predictably instead of racing across instances. The partition count (3) is the ceiling on how
many consumers can actually share the work — extra instances beyond that just sit idle, which
is exactly the trade-off you tune in a real system.

Three rules run on every transaction: **high amount**, **new country**, and **velocity** (too
many in a short window, counted from Redis).

## Getting started

You need JDK 17 and Docker.

### Quick way — everything in Docker, scaled out

```bash
docker compose up --build --scale scorer=3
```

That starts Postgres, Redis, Kafka, one producer, and three scorer instances in a consumer
group. Watch the logs and you'll see the three scorers each pick up a partition.

### Dev way — infra in Docker, app from your IDE

```bash
docker compose up -d db redis kafka      # just the infrastructure
./mvnw spring-boot:run                    # one instance, runs 3 consumer threads locally
```

Watch the alerts topic:

```bash
docker exec -it fraud-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic fraud-alerts --from-beginning
```

Score one by hand (with the simulator off) via the REST endpoint:

```bash
curl -X POST http://localhost:8080/score \
  -H "Content-Type: application/json" \
  -d '{"cardId":"card-1","amount":5000,"merchant":"Amazon","country":"RU"}'
```

Run the tests with `mvn test` — they mock Kafka and Redis and use in-memory H2, so no infra
is needed.

## Tech stack

| Piece | What I used | Why |
|-------|-------------|-----|
| Language | Java 17 | The language I want to be strong in for backend interviews |
| Service | Spring Boot 3 | Fast to build REST + Kafka consumers, industry standard |
| Streaming | Apache Kafka | Decouples ingest, handles spikes, and splits load across a consumer group |
| Feature store / cache | Redis | Fast rolling per-card velocity counts, TTL cleans up old data |
| Database | PostgreSQL | Stores every transaction and its decision |
| Packaging | Docker + Compose | One command to run and scale the whole thing |
| Testing | JUnit 5 + Mockito + H2 | Unit-test the logic with no infra running |

## Roadmap

Each stage adds a single system-design idea so I actually understand *why* it's there. Full
detail in [`BUILD_PLAN.md`](BUILD_PLAN.md).

- [x] **Stage 0 — Foundations:** repo, README, architecture, domain model
- [x] **Stage 1 — MVP:** single service, rules engine, Postgres storage
- [x] **Stage 2 — Caching:** Redis feature store for real-time velocity features
- [x] **Stage 3 — Async:** Kafka producer + consumer, decouple ingest from scoring
- [x] **Stage 4 — Scale:** multiple consumers in a group, partitioned by card
- [ ] **Stage 5 — ML:** train + serve a fraud model alongside the rules
- [ ] **Stage 6 — Observability:** metrics, live dashboard, load testing
- [ ] **Stage 7 — Polish:** CI, docker-compose everything, write-up

## What I'm learning

I'm using this as the hands-on companion to the system design I'm studying:

- **Caching** → the Redis feature store (Stage 2) ✅
- **Async / queues** → Kafka between producer and scorer (Stage 3) ✅
- **Horizontal scaling & load balancing** → Kafka consumer groups + partitioning (Stage 4) ✅ — group rebalancing, partition keys, scale up vs out
- **Consistency vs. availability** → what I do when a piece goes down (Stage 7)

If you're a recruiter or hiring manager reading this: the short version is that it's a
real-time, event-driven transaction scoring service that scales horizontally, and I can walk
through every design decision in it. Thanks for stopping by.

## Notes

- Fraud data is synthetic (a simulator) or a public Kaggle dataset — no real card data.
- Personal learning project, not meant for production.

---

*Built by Chanul Dandeniya · Computer Science @ Stony Brook University*
