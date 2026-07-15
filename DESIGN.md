# Design notes & trade-offs

This is the write-up I'd walk an interviewer through. It covers why the pieces are arranged
the way they are, what happens when each one fails, and what I'd fix next.

## The flow, end to end

1. A producer publishes a transaction to the Kafka `transactions` topic, **keyed by card id**.
2. A scorer instance (one of several in a consumer group) picks it up.
3. The rules engine runs four checks: high amount, new country (Postgres history), velocity
   (Redis), and the ML model (HTTP call to the Python service).
4. Scores are summed into a verdict: `0` = APPROVE, `1–69` = REVIEW, `70+` = DECLINE.
5. The transaction and its decision are written to Postgres, the card's activity is recorded
   in Redis, and anything not APPROVE is published to `fraud-alerts`.
6. Metrics are updated on every decision and exposed to Prometheus.

## Key trade-offs

### Queue in the middle instead of synchronous scoring
Scoring inside the HTTP request means a traffic spike backs up onto the caller and the only way
to scale is a bigger machine. With Kafka between the two sides, a spike becomes a longer queue
instead of timeouts, and producers and consumers scale independently. The cost is that scoring
is no longer instant from the caller's point of view — fine here, since the decision is consumed
asynchronously anyway.

### Partition by card id
Kafka only guarantees ordering *within* a partition. Keying by card keeps each card's
transactions ordered and pinned to one consumer, so time-ordered logic (velocity) behaves
predictably. The downside: a single very hot card can create a hot partition, and the partition
count (3) caps how many consumers can share work. In a real system I'd raise partitions well
above expected consumer count.

### Cache computed features, not raw queries
"How many times was this card used in the last 5 minutes" as a `COUNT(*)` on Postgres would put
a query on the hot path of every transaction. Instead each card has a Redis sorted set scored by
timestamp, so the answer is a `ZCOUNT` over a range, with old entries trimmed and a TTL so it
self-cleans. Redis is the cache; Postgres remains the durable record.

### The model as a separate service
Python owns training and serving; Java owns the pipeline. Clean boundary, but it costs a network
hop per transaction. That's why the client **fails open** — see below.

## Failure modes

| What breaks | What happens | Why I chose that |
|---|---|---|
| **Model service** down/slow | `ModelClient` catches, logs, returns 0. Scoring continues on rules alone. | Availability over completeness — a fraud *check* shouldn't take down the fraud *system*. Degraded scoring beats no scoring. |
| **Redis** down | Velocity lookups fail; that rule effectively goes quiet. Other rules still run. | Same reasoning: the cache is an optimization, not the source of truth. (Currently this surfaces as an exception — see gaps.) |
| **Postgres** down | Scoring fails for that transaction; the Kafka offset isn't committed, so it's redelivered later. | Decisions must be durable. This is the one place I choose consistency over availability. |
| **A scorer instance** dies | Kafka rebalances its partitions onto the surviving group members. | Free fault tolerance from the consumer-group protocol. |
| **Kafka** down | Nothing is ingested; the REST endpoint still works. | Ingest is unavailable but nothing is silently lost. |

## Consistency vs. availability

Different parts of this system sit in different places on that spectrum, deliberately:

- **Decision storage (Postgres)** — favors consistency. A decision that was returned but not
  stored is worse than a retry, so a DB failure fails the message and lets Kafka redeliver it.
- **Feature store (Redis)** — favors availability. Velocity counts are best-effort; a slightly
  stale or missing count degrades detection quality but shouldn't stop payments from being scored.
- **Model service** — favors availability, via fail-open.

The general rule I applied: anything that is a *record* gets consistency, anything that is a
*signal* gets availability.

## Delivery semantics

Kafka gives at-least-once delivery, so the same transaction can be processed twice (e.g. a
rebalance before the offset commits). Every message carries an `eventId` for exactly this reason.
Right now I don't dedupe on it — that's the top item below.

## Known gaps / what I'd do next

1. **Idempotent consumer** — dedupe on `eventId` (unique constraint or a Redis `SETNX`) so a
   redelivery doesn't double-count a transaction or write a duplicate decision.
2. **Dead-letter topic** — messages that fail repeatedly should go to a DLQ instead of blocking
   the partition, with retry/backoff in front of it.
3. **Graceful Redis degradation** — catch failures in the feature store the same way the model
   client does, so a Redis outage degrades instead of erroring.
4. **Outbox pattern** — right now the DB write and the alert publish aren't atomic; a crash
   between them can store a decision without emitting its alert.
5. **Schema management** — JSON with no schema registry means a producer change can break
   consumers silently. Avro/Protobuf + a registry would fix that.
6. **Real data + better model** — swap synthetic data for the Kaggle credit-card fraud dataset,
   move from logistic regression to gradient boosting, and track precision/recall rather than
   accuracy (which is misleading on a heavily imbalanced problem).
7. **Backpressure & tuning** — measure consumer lag, tune batch sizes and partition count under
   the load test.
