# Fraud Detection Pipeline

A real-time system that scores payment transactions for fraud as they stream in. Built in
Java/Spring Boot with Kafka, Redis, and Postgres.

![status](https://img.shields.io/badge/status-stage%203%20done-brightgreen)
![java](https://img.shields.io/badge/Java-17-orange)
![spring](https://img.shields.io/badge/Spring%20Boot-3-green)
![kafka](https://img.shields.io/badge/Apache%20Kafka-streaming-black)
![license](https://img.shields.io/badge/license-MIT-blue)

> Heads up: this is an active learning project I'm building in stages, not a finished product.
> I'm building it partly to have a real project to point at, and partly to actually learn
> distributed systems by building one instead of just reading about it. The roadmap below shows
> what's done and what's next.

## What it does

Transactions stream in through Kafka. A scoring service reads them, runs each one through a
rules engine (using recent per-card behavior cached in Redis), and comes back with a decision:
**APPROVE**, **REVIEW**, or **DECLINE**, plus the reasons why. Every transaction and its decision
are written to Postgres, and anything flagged is published to a separate alerts topic.

The interesting part isn't the fraud rules themselves — it's making this *fast, scalable, and
reliable at the same time* as it grows, which is where all the system-design stuff comes in.

## Where it's at (Stage 3)

The pipeline is now event-driven:

```
 simulator ──▶  Kafka: transactions ──▶  scoring service ──▶  Postgres (txn + decision)
 (producer)                                   │    ▲                │
                                              ▼    │ Redis          ▼
                                        feature store        Kafka: fraud-alerts ──▶ (downstream)
```

- A **producer/simulator** makes up transactions (mostly normal, some deliberately sketchy)
  and publishes them to the `transactions` topic, keyed by card id.
- A **consumer** (`@KafkaListener`) reads each one, scores it with the same `ScoringService`
  the REST endpoint uses, saves it, and — if it's not a clean APPROVE — publishes an
  `AlertMessage` to the `fraud-alerts` topic.
- The old `POST /score` endpoint still works as a manual way to score one transaction.

### Why put Kafka in the middle?

Scoring straight inside the HTTP request means a traffic spike backs up onto whoever is
calling you, and you can only scale by making the one service bigger. A queue decouples the
two sides: the producer just drops messages and moves on, and the scorer pulls them at its own
pace, so a spike turns into a temporarily longer queue instead of dropped or timed-out
requests. It also sets up horizontal scaling — Stage 4 runs several scorer instances in one
consumer group, splitting the partitions between them. Kafka is at-least-once, so each message
carries an `eventId` for deduping if a redelivery happens.

Three rules run on every transaction:
- **High amount** — over a configurable threshold (default $1000).
- **New country** — a card used somewhere it's never been seen before.
- **Velocity** — too many transactions in a short window (default > 5 in 5 min), counted from
  Redis rather than the database.

## Getting started

You need JDK 17 and Docker.

```bash
# 1. start Postgres + Redis + Kafka
docker compose up -d

# 2. run the app (or hit Run in your IDE)
./mvnw spring-boot:run      # or: mvn spring-boot:run
```

The simulator starts feeding transactions immediately, so you'll see scoring and alert logs
right away. To watch the alerts topic:

```bash
docker exec -it fraud-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic fraud-alerts --from-beginning
```

Prefer to drive it by hand? Set `fraud.simulator.enabled=false` and use the REST endpoint:

```bash
curl -X POST http://localhost:8080/score \
  -H "Content-Type: application/json" \
  -d '{"cardId":"card-1","amount":5000,"merchant":"Amazon","country":"RU"}'
```

Run the tests with `mvn test` — they mock Kafka and Redis and use in-memory H2, so no infra
is needed to run them.

## Tech stack

| Piece | What I used | Why |
|-------|-------------|-----|
| Language | Java 17 | The language I want to be strong in for backend interviews |
| Service | Spring Boot 3 | Fast to build REST + Kafka consumers, industry standard |
| Streaming | Apache Kafka | Decouples ingest from scoring, handles spikes, enables scale-out |
| Feature store / cache | Redis | Fast rolling per-card velocity counts, TTL cleans up old data |
| Database | PostgreSQL | Stores every transaction and its decision |
| Testing | JUnit 5 + Mockito + H2 | Unit-test the logic with no infra running |
| Infra | Docker Compose | One command to bring up Postgres + Redis + Kafka |

## Roadmap

Each stage adds a single system-design idea so I actually understand *why* it's there. Full
detail in [`BUILD_PLAN.md`](BUILD_PLAN.md).

- [x] **Stage 0 — Foundations:** repo, README, architecture, domain model
- [x] **Stage 1 — MVP:** single service, rules engine, Postgres storage
- [x] **Stage 2 — Caching:** Redis feature store for real-time velocity features
- [x] **Stage 3 — Async:** Kafka producer + consumer, decouple ingest from scoring
- [ ] **Stage 4 — Scale:** multiple consumers in a group, partitioned by card
- [ ] **Stage 5 — ML:** train + serve a fraud model alongside the rules
- [ ] **Stage 6 — Observability:** metrics, live dashboard, load testing
- [ ] **Stage 7 — Polish:** CI, docker-compose everything, write-up

## What I'm learning

I'm using this as the hands-on companion to the system design I'm studying:

- **Caching** → the Redis feature store (Stage 2) ✅
- **Async / queues** → Kafka between producer and scorer (Stage 3) ✅ — decoupling, back-pressure, at-least-once
- **Horizontal scaling & load balancing** → Kafka consumer groups + partitioning (Stage 4)
- **Consistency vs. availability** → what I do when a piece goes down (Stage 7)

If you're a recruiter or hiring manager reading this: the short version is that it's a
real-time, event-driven transaction scoring service I'm building up to horizontal scale, and I
can walk through every design decision in it. Thanks for stopping by.

## Notes

- Fraud data is synthetic (a simulator) or a public Kaggle dataset — no real card data.
- Personal learning project, not meant for production.

---

*Built by Chanul Dandeniya · Computer Science @ Stony Brook University*
