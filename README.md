# Fraud Detection Pipeline

A real-time system that scores payment transactions for fraud as they stream in. Built in
Java/Spring Boot, growing toward Kafka + Redis + Postgres one stage at a time.

![status](https://img.shields.io/badge/status-stage%202%20done-brightgreen)
![java](https://img.shields.io/badge/Java-17-orange)
![spring](https://img.shields.io/badge/Spring%20Boot-3-green)
![license](https://img.shields.io/badge/license-MIT-blue)

> Heads up: this is an active learning project I'm building in stages, not a finished product.
> I'm building it partly to have a real project to point at, and partly to actually learn
> distributed systems by building one instead of just reading about it. The roadmap below shows
> what's done and what's next.

## What it does

A transaction comes in (right now from a `POST /score` call), gets run through a rules engine,
and comes back out with a decision: **APPROVE**, **REVIEW**, or **DECLINE**, plus the reasons
why. Every transaction and its decision get written to Postgres, and recent per-card activity
is tracked in Redis so the next transaction is scored against up-to-date behavior.

The interesting part isn't the fraud rules themselves — it's making this *fast, scalable, and
reliable at the same time* as it grows, which is where all the system-design stuff comes in.

## Where it's at (Stage 2)

A single Spring Boot service backed by Postgres and Redis:

```
POST /score  ->  RulesEngine  ->  Postgres (store txn + decision)
                    |    ^
                    v    |  reads/writes recent velocity
                  Redis feature store
```

Three rules so far:
- **High amount** — flags anything over a configurable threshold (default $1000).
- **New country** — flags a card used in a country it's never been seen in before
  (brand-new cards get a pass so their first transaction isn't auto-flagged).
- **Velocity** — flags a card used too many times in a short window (default > 5 in 5 min),
  the classic "card testing" pattern. This count comes from Redis, not the database.

Each rule adds points to a risk score, and the total maps to a verdict (0 = approve,
1–69 = review, 70+ = decline). Rules are just `@Component`s implementing a `Rule` interface,
so adding a new one doesn't touch the engine.

### Why Redis for velocity?

Answering "how many times has this card been used in the last 5 minutes?" with a
`COUNT(*)` against Postgres on every single transaction would be slow and would pound the
database on the hot path. Instead each card gets a Redis sorted set of recent transactions
scored by timestamp, so the count is a cheap `ZCOUNT` over a time range. Old entries are
trimmed and the key expires after an hour, so it cleans itself up. That's the caching lesson:
**cache the computed feature, not the raw query.**

## Getting started

You need JDK 17 and Docker.

```bash
# 1. start Postgres + Redis
docker compose up -d

# 2. run the app (or just hit Run in your IDE)
./mvnw spring-boot:run      # or: mvn spring-boot:run
```

Score a transaction:

```bash
curl -X POST http://localhost:8080/score \
  -H "Content-Type: application/json" \
  -d '{"cardId":"card-1","amount":5000,"merchant":"Amazon","country":"RU"}'
```

Example response:

```json
{
  "transactionId": 1,
  "verdict": "REVIEW",
  "score": 45,
  "reasons": ["amount 5000 is over the 1000 threshold"]
}
```

Fire that same request six times fast and the velocity rule kicks in — the score climbs and
the verdict flips to DECLINE.

Run the tests with `mvn test` — they mock Redis and use an in-memory H2 database, so you
don't need any infra running.

## Tech stack

| Piece | What I used | Why |
|-------|-------------|-----|
| Language | Java 17 | The language I want to be strong in for backend interviews |
| Service | Spring Boot 3 | Fast to build REST + (later) Kafka consumers, industry standard |
| Database | PostgreSQL | Stores every transaction and its decision |
| Feature store / cache | Redis | Fast rolling per-card velocity counts, TTL cleans up old data |
| Testing | JUnit 5 + Mockito + H2 | Unit-test the rules, smoke-test the wiring, no infra needed |
| Infra | Docker Compose | One command to bring up dependencies |
| Streaming *(next)* | Apache Kafka | Decouples ingest from scoring, handles spikes |

## Roadmap

Each stage adds a single system-design idea so I actually understand *why* it's there. Full
detail in [`BUILD_PLAN.md`](BUILD_PLAN.md).

- [x] **Stage 0 — Foundations:** repo, README, architecture, domain model
- [x] **Stage 1 — MVP:** single service, rules engine, Postgres storage
- [x] **Stage 2 — Caching:** Redis feature store for real-time velocity features
- [ ] **Stage 3 — Async:** Kafka producer + consumer, decouple ingest from scoring
- [ ] **Stage 4 — Scale:** multiple consumers in a group, partitioned by card
- [ ] **Stage 5 — ML:** train + serve a fraud model alongside the rules
- [ ] **Stage 6 — Observability:** metrics, live dashboard, load testing
- [ ] **Stage 7 — Polish:** CI, docker-compose everything, write-up

## What I'm learning

I'm using this as the hands-on companion to the system design I'm studying:

- **Caching** → the Redis feature store (Stage 2) ✅ — cache computed features, TTL windows
- **Async / queues** → Kafka between producer and scorer (Stage 3)
- **Horizontal scaling & load balancing** → Kafka consumer groups + partitioning (Stage 4)
- **Consistency vs. availability** → what I do when a piece goes down (Stage 7)

If you're a recruiter or hiring manager reading this: the short version is that it's a
real-time transaction scoring service I'm building up to horizontal scale, and I can walk
through every design decision in it. Thanks for stopping by.

## Notes

- Fraud data is synthetic or a public Kaggle dataset — no real card data.
- Personal learning project, not meant for production.

---

*Built by Chanul Dandeniya · Computer Science @ Stony Brook University*
