# Study Guide — understanding this project inside and out

This is my own notes for being able to explain this project end to end without hand-waving.
It goes from the elevator pitch down to why each line exists, then the concepts behind it,
then the questions I should expect and how I'd answer them.

**How to use it:** read Part 1 until you can trace a transaction from memory. Read Part 2 until
you can explain each concept to someone who's never used it. Then drill Part 5 and 6 out loud —
saying it out loud is the whole point, since that's the format of the interview.

---

## Part 0 — The pitches

### 30-second version
> I built a real-time fraud detection pipeline in Java and Spring Boot. Transactions come in
> through Kafka, a pool of scoring services reads them, runs each one through a rules engine
> plus a machine-learning model — using recent card activity cached in Redis — and writes an
> approve/review/decline decision to Postgres, publishing anything suspicious to an alerts
> topic. It scales horizontally with Kafka consumer groups and it's instrumented with
> Prometheus metrics.

### 2-minute version
Add the *why* to each piece:
- **Why Kafka?** Scoring inside an HTTP request means a traffic spike backs up onto the caller
  and you can only scale by making one machine bigger. A queue turns a spike into a longer
  queue and lets producers and consumers scale independently.
- **Why Redis?** One of my rules needs "how many times has this card been used in the last five
  minutes." As a `COUNT(*)` against Postgres that's a query on the hot path of every single
  transaction. In Redis it's a sorted-set range count, and the TTL cleans up old data for free.
- **Why a separate model service?** Python owns training and serving, Java owns the pipeline.
  Clean boundary, at the cost of a network hop — so the client fails open and degrades to
  rules-only if the model is down.
- **Why partition by card?** Kafka only guarantees ordering within a partition, so keying by
  card keeps each card's history ordered and pinned to one consumer.

### The whiteboard version
Be able to draw this from memory in under a minute:

```
producer -> [Kafka: transactions, 3 partitions] -> consumer group {scorer1, scorer2, scorer3}
                                                          |
                     +------------------+-----------------+------------------+
                     |                  |                 |                  |
                  Redis            model service       Postgres        Kafka: fraud-alerts
              (velocity)          (probability)      (decisions)          (downstream)
```

---

## Part 1 — The journey of one transaction

This is the single most important thing to know cold. Trace it in order.

### 1. The producer creates it
`messaging/TransactionSimulator.java` — on a timer, builds a `TransactionMessage`
(eventId, cardId, amount, merchant, country) and hands it to the producer.

`messaging/TransactionProducer.java`:
```java
kafkaTemplate.send(topic, message.getCardId(), message);
//                        ^^^^^^^^^^^^^^^^^^^ the KEY
```
**The key matters.** Kafka hashes the key to pick a partition, so every transaction for
`card-7` always goes to the same partition. Same card → same partition → same consumer →
ordered. If I passed `null` as the key, Kafka would round-robin and a card's transactions
could be processed out of order by different instances.

### 2. It sits in the topic
The `transactions` topic has 3 partitions. A partition is an **append-only ordered log**.
Each message gets an increasing **offset**. Kafka doesn't push to consumers; consumers **pull**
and track how far they've read.

### 3. A consumer picks it up
`messaging/TransactionConsumer.java`:
```java
@KafkaListener(topics = "...", groupId = "...", concurrency = "...")
public void onMessage(TransactionMessage message,
                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                      @Header(KafkaHeaders.OFFSET) long offset) { ... }
```
All the scorer instances share one `groupId`, which makes them a **consumer group**. Kafka
assigns each partition to exactly one member. Three partitions and three instances → one each.
A fourth instance would sit idle (this is why partition count caps your parallelism).

### 4. Scoring
`service/ScoringService.score()` — the orchestrator. Order is deliberate:

```java
Transaction transaction = new Transaction(...);        // not saved yet
EngineResult result = rulesEngine.evaluate(transaction); // rules see PAST only
Transaction saved = transactionRepository.save(transaction);
featureStore.record(saved.getCardId(), saved.getTimestamp());
decisionRepository.save(decision);
metrics.recordDecision(result.getVerdict());
```

**Why evaluate before saving?** The new-country rule asks "has this card ever been used in this
country?" If I saved first, the answer would always be yes — the transaction I'm scoring would
be its own evidence. Same for velocity. **Rules must only see history, never themselves.**
This is the kind of subtle ordering bug an interviewer loves, so know it.

### 5. The rules engine
`engine/RulesEngine.java`:
```java
public RulesEngine(List<Rule> rules) { this.rules = rules; }  // Spring injects ALL Rule beans
```
Loops the rules, sums scores of the ones that fired, collects reasons, maps total → verdict:
`0` = APPROVE, `1–69` = REVIEW, `70+` = DECLINE.

The four rules and their points:

| Rule | Fires when | Points | Reads from |
|---|---|---|---|
| `HighAmountRule` | amount > threshold (default 1000) | 45 | the transaction itself |
| `NewCountryRule` | card never seen in this country (new cards exempt) | 40 | Postgres |
| `VelocityRule` | > N transactions in 5 min | 35 | Redis |
| `ModelRule` | model probability ≥ threshold (0.7) | up to 50 | model service + Redis |

Note the scoring design: **any two rules firing ≥ 70 → DECLINE**. 45+40=85, 45+35=80, 40+35=75.
One rule alone is only ever a REVIEW. That's intentional — one signal is suspicion, two is a
pattern.

### 6. Persist + alert
Transaction and decision rows go to Postgres. Back in the consumer, if the verdict isn't
APPROVE, an `AlertMessage` is published to `fraud-alerts`. Note **scoring never touches Kafka** —
only the consumer does. That keeps the domain logic transport-agnostic and testable.

### 7. Metrics
`metrics/DecisionMetrics.java` bumps a counter tagged by verdict and records the latency in a
timer. Exposed at `/actuator/prometheus`, summarized at `/stats` for the dashboard.

---

## Part 2 — Concepts, from first principles

### 2.1 Kafka

**What it actually is:** a distributed, append-only commit log. Not a traditional queue — messages
aren't deleted when read. They stay for a retention period, and each consumer group tracks its
own position.

**Vocabulary you must be fluent in:**
- **Topic** — a named stream (`transactions`).
- **Partition** — an ordered, append-only log. A topic is split into partitions; this is the
  unit of parallelism. **Ordering is guaranteed within a partition, never across partitions.**
- **Offset** — a message's position in its partition. Consumers commit offsets to record progress.
- **Producer key** — hashed to choose a partition. Same key → same partition.
- **Consumer group** — a set of consumers sharing a `groupId`. Kafka gives each partition to
  exactly one member of the group. Add members → each gets fewer partitions. More members than
  partitions → the extras idle.
- **Rebalancing** — when a member joins or dies, Kafka reassigns partitions across the survivors.
  This is where fault tolerance comes from, and it's free.
- **Consumer lag** — how far behind the latest offset a group is. The number to watch: rising lag
  means consumers can't keep up.

**Delivery semantics** (know all three):
- *At-most-once* — commit the offset before processing. Crash = message lost.
- *At-least-once* — process, then commit. Crash between the two = reprocessed. **This is what I
  have**, and it's the common default.
- *Exactly-once* — possible in Kafka via transactions, but expensive and it doesn't extend to
  external side effects like my Postgres write.

Because I'm at-least-once, **duplicates are possible**. Every message carries an `eventId` for
that reason. I don't dedupe on it yet — that's the top item in DESIGN.md's gaps, and saying
"here's the flaw and here's how I'd fix it" is stronger than pretending it isn't there.

**Why a queue at all?** Three answers, all worth having ready:
1. *Decoupling* — producer doesn't know or care who consumes, or how many.
2. *Buffering* — a spike becomes queue depth instead of timeouts or dropped requests.
3. *Independent scaling* — add consumers without touching producers.

### 2.2 Redis and the feature store

**What Redis is:** an in-memory key-value store. Fast because it's RAM and single-threaded for
command execution (no lock contention; commands are atomic).

**The data structure I chose — a sorted set (ZSET):** a set of members, each with a numeric
*score*, kept ordered by that score. I use **timestamp as the score**:

```java
redis.opsForZSet().add(key, member, ts);                          // ZADD
redis.opsForZSet().removeRangeByScore(key, 0, ts - RETENTION);    // ZREMRANGEBYSCORE - trim old
redis.expire(key, RETENTION);                                     // TTL - self-cleanup
redis.opsForZSet().count(key, from, now);                         // ZCOUNT - sliding window
```

That gives a **sliding window count** in O(log N): "how many entries have a score between
`now - 5min` and `now`." Trimming keeps memory bounded, and the TTL means a card that goes
quiet disappears on its own — no cleanup job.

**Why the member is `ts + ":" + UUID`:** sorted-set members are unique. If two transactions
landed in the same millisecond and I used the timestamp alone as the member, the second `ZADD`
would overwrite the first and I'd undercount. The UUID guarantees uniqueness.

**The real lesson — cache the computed feature, not the raw query.** A naive cache would store
"the result of this SQL query." That's brittle: it goes stale, and it invalidates awkwardly.
Instead I store the *shape of data that answers the question* and let TTL handle expiry. There's
no invalidation logic anywhere in this project, by design.

**Know the trade-off:** Redis is not the source of truth. If it dies, velocity detection degrades
but nothing is lost, because Postgres has the durable record. Cache = optimization, DB = truth.

### 2.3 Spring Boot

**Dependency injection / inversion of control.** I never write `new ScoringService(...)`. I declare
what a class needs in its constructor and Spring supplies it from the application context. Why it
matters: in tests I pass mocks into the same constructor, so the code under test doesn't know or
care that Redis isn't real.

**The stereotype annotations** are all `@Component` underneath; the others just express intent:
`@Service` (business logic), `@Repository` (data access), `@RestController` (HTTP), `@Configuration`
(bean definitions).

**The trick worth pointing out in an interview** — this line:
```java
public RulesEngine(List<Rule> rules) { ... }
```
Spring sees a `List<Rule>` and injects **every bean implementing `Rule`**. So adding a fifth rule
means creating one class annotated `@Component` — the engine never changes. That's the
**open–closed principle** (open to extension, closed to modification), and it's the **strategy
pattern**: a family of interchangeable algorithms behind one interface.

**`@Transactional`** wraps the method in a database transaction — everything commits together or
rolls back together. The gotcha: it works via a **proxy**, so if a bean calls its own
`@Transactional` method internally (self-invocation), the proxy is bypassed and the annotation
does nothing. That's exactly why my timing code sits *inside* `score()` rather than in a wrapper
method that calls it.

**`@ConditionalOnProperty`** creates a bean only when a property is set — how the simulator and
the model rule are switched off in tests. **Profiles** (`application-docker.properties`) swap
config per environment, which is how the same jar talks to `localhost` on my laptop and to
`kafka:29092` inside Compose.

### 2.4 JPA / Hibernate

Maps Java objects to database rows. `@Entity` = a table, `@Id @GeneratedValue` = auto primary key.
`JpaRepository<Transaction, Long>` gives CRUD for free, and **derived queries** are generated from
the method *name*:
```java
boolean existsByCardIdAndCountry(String cardId, String country);
```
Spring parses that name and writes the SQL. No implementation needed.

`ddl-auto=update` lets Hibernate create/alter tables at startup. **Be honest that this is a
dev-only choice** — in production you'd use versioned migrations (Flyway/Liquibase) because
auto-DDL can silently do destructive or unrepeatable things.

### 2.5 The machine learning piece

**Logistic regression in one sentence:** it fits a weighted sum of the features, squashes it
through a sigmoid into a 0–1 range, and that output is the probability of the positive class.

**Why start here and not something fancier:** it's fast, it's interpretable (you can read the
coefficients and see which feature drove the score), and it's a strong baseline. Reaching for a
deep model on two features would be showing off, not engineering.

**Class imbalance — the thing to get right.** Fraud is ~3% of my synthetic data. A model that
predicts "never fraud" scores **97% accuracy** and is completely useless. So:
- `class_weight="balanced"` makes the rare class count more during training.
- **Accuracy is the wrong metric.** The right ones:
  - **Precision** = of the transactions I flagged, how many were actually fraud (TP / (TP+FP)).
    Low precision = angry customers whose real purchases got declined.
  - **Recall** = of all actual fraud, how much I caught (TP / (TP+FN)). Low recall = fraud got through.
  - They trade off against each other via the threshold. Lower the threshold → catch more fraud
    (recall up), flag more legit transactions (precision down).
- This is a live weakness in my project: `train.py` prints accuracy. Saying "I know that's the
  wrong metric and here's what I'd replace it with" is a strong answer.

**`predict_proba` returns `[P(legit), P(fraud)]`** — I take index `[1]`, the fraud probability.

**Why the model is a separate service, and why it fails open:**
```java
catch (Exception e) { log.warn("model service unavailable, failing open"); return 0.0; }
```
A fraud *check* must not be able to take down the fraud *system*. Degraded scoring beats no
scoring. The cost is silent degradation — which is exactly why the warning is logged and why
you'd alert on it in production.

### 2.6 Metrics and observability

- **Counter** — only goes up (number of decisions).
- **Gauge** — goes up and down (queue depth, memory).
- **Timer / histogram** — records durations and their distribution.

**Why percentiles instead of averages:** an average hides the tail. If 99 requests take 10ms and
one takes 5 seconds, the mean is ~60ms and looks fine, but one user in a hundred had a terrible
experience. **p95 = 95% of requests were at least this fast.** Tail latency is what users feel.

**A real limitation in my code, worth knowing:** I use `publishPercentiles`, which computes the
quantiles *inside each instance*. You **cannot average percentiles across instances** — p95 of
three scorers is not the mean of their three p95s. To aggregate properly you emit histogram
buckets (`publishPercentileHistogram`) and let Prometheus compute `histogram_quantile` across
them. Knowing this distinction is a genuinely senior-sounding detail.

**Latency vs. throughput:** latency = how long one request takes; throughput = how many per
second. They are not the same, and they trade off — batching raises throughput while raising
latency. My load test measures both.

### 2.7 Docker and Compose

**Image vs container:** image = the recipe, container = a running instance of it.

**Multi-stage build** (my `Dockerfile`): stage one uses a full Maven+JDK image to compile; stage
two copies just the resulting jar into a slim JRE image. The build tools don't ship to
production — smaller image, smaller attack surface.

**Why Kafka needs two listeners in Compose:** Kafka tells clients where to reach it via
`advertised.listeners`. A client *inside* the Compose network must be told `kafka:29092`; a client
on my laptop must be told `localhost:9092`. One address can't satisfy both, so there are two
listeners — `INTERNAL` and `EXTERNAL`. This trips up a lot of people and is worth being able to
explain.

### 2.8 Testing

All 14 tests run with **no infrastructure**: Kafka, Redis, and the model service are mocked with
Mockito, and the database is in-memory H2.

**Why mock?** Speed and determinism. A test that needs Docker is slow and flaky, so it gets run
less, so it stops catching things.

**The design point:** I could only mock these because of dependency injection and because
`FeatureStore` is an *interface*. Testability isn't something bolted on at the end — it falls out
of decoupled design. That's why `TransactionConsumer.handle()` is split out from the
`@KafkaListener` method: the annotated method needs Kafka headers, the extracted one doesn't, so
the logic is testable without a broker.

---

## Part 3 — Trade-offs, stated as decisions

For each: what I chose, what I gave up, when I'd choose differently.

| Decision | Chose | Gave up | Would revisit if |
|---|---|---|---|
| Queue between ingest and scoring | Kafka | Immediate synchronous response | The caller genuinely needs a decision in-band (a real card authorization does) |
| Partition key | cardId | Even load distribution | One card is extremely hot and creates a hot partition |
| Velocity storage | Redis | Durability of the counts | The counts became something I had to audit |
| Model deployment | Separate Python service | A network hop per transaction | Latency budget got tight — I'd embed via ONNX in the JVM |
| Model failure behavior | Fail open | Detection quality during an outage | The check were regulatory/mandatory, then fail *closed* |
| Decision storage | Postgres, consistency-first | Availability during a DB outage | Never — decisions must be durable |
| Schema | Plain JSON | Compile-time safety across services | Multiple teams produced to this topic; I'd add Avro + a schema registry |
| DDL | `ddl-auto=update` | Repeatable, reviewable migrations | Anything production — switch to Flyway |

**On CAP, be precise.** CAP says that *when a network partition occurs*, you must choose between
consistency and availability — it is not a general "pick two of three." The everyday version is
PACELC: during a Partition choose A or C; Else (normal operation) choose Latency or Consistency.
My rule of thumb in this system: **anything that is a *record* gets consistency; anything that is
a *signal* gets availability.** Decisions are records. Velocity counts and model opinions are
signals.

---

## Part 4 — Failure drill

Be able to answer instantly for each component: what breaks, what still works, why I chose that.

| Component dies | What happens | Why |
|---|---|---|
| **Model service** | Client catches, returns 0, scoring continues on rules alone | Availability > completeness for a signal |
| **Redis** | Velocity rule can't get counts; other rules still run | Cache is an optimization, not truth. (Currently surfaces as an exception — a known gap) |
| **Postgres** | Message processing fails, offset not committed, Kafka redelivers | Decisions must be durable — the one place I pick consistency |
| **One scorer** | Group rebalances its partitions onto survivors | Free fault tolerance from consumer groups |
| **Kafka** | No ingest; REST endpoint still works | Unavailable, but nothing silently lost |

---

## Part 5 — Interview question bank

### About the project
**"Walk me through the project."** → Use the 2-minute pitch, then offer to go deeper on any piece.
Don't recite the file list; tell the story of one transaction.

**"Why did you build it this way?"** → Each stage added exactly one concept so I understood it
before moving on: baseline → caching → async → scaling → ML → observability → resilience.

**"What was the hardest part?"** → Getting the ordering right so rules only see history and not
the transaction being scored. It's a one-line fix and an easy bug to never notice, because the
system still runs — it just quietly never flags anything.

**"What would you do differently?"** → Idempotent consumption keyed on `eventId`; I designed the
field in but didn't implement dedupe. And I'd track precision/recall instead of accuracy.

### Kafka
- *Why Kafka and not RabbitMQ?* Kafka retains a replayable log and scales reads via partitions;
  RabbitMQ is a traditional broker that deletes on ack and is better for complex routing. I wanted
  replay and horizontal consumer scaling.
- *What happens if you add a 4th consumer to a 3-partition topic?* It idles. Partitions cap parallelism.
- *How do you guarantee ordering?* Only within a partition — by keying on cardId.
- *What if a consumer crashes mid-message?* The offset wasn't committed, so after rebalance the
  message is redelivered — at-least-once, hence the need for idempotency.
- *How would you handle a poison message?* Retry with backoff, then route to a dead-letter topic
  so it can't block the partition.

### Redis
- *Why not just query Postgres?* It's a query on the hot path of every transaction; Redis makes it
  an O(log N) in-memory range count.
- *Why a sorted set and not a counter?* A plain counter can't express a *sliding* window — I'd have
  to bucket by fixed intervals and lose precision at the boundaries.
- *What if Redis loses data?* Detection quality degrades; nothing is lost, because Postgres is truth.
- *How do you stop it growing forever?* Trim on write plus a TTL on the key.

### Spring / Java
- *How does the engine get all the rules?* Constructor injection of `List<Rule>` — Spring supplies
  every bean of that type. Adding a rule requires no change to the engine.
- *What does `@Transactional` actually do?* Proxies the method in a transaction; self-invocation
  bypasses the proxy.
- *Why constructor injection over field injection?* Dependencies are explicit and final, and the
  class can be constructed in a test without a Spring context.

### ML
- *Why logistic regression?* Fast, interpretable baseline. I'd move to gradient boosting with more features.
- *Your data is 3% fraud — what's wrong with accuracy?* Predicting "never fraud" gets 97%. Need
  precision and recall.
- *How would you pick the threshold?* It's a business decision: the cost of a missed fraud vs. the
  cost of declining a real customer. Plot the precision-recall curve and choose deliberately.

### Scaling & hypotheticals
- *Traffic goes 10x.* First look at consumer lag. Scale consumers up to the partition count, then
  raise partitions. Check whether Postgres writes are the real bottleneck — batch them if so.
- *One card gets a million transactions.* Hot partition; keying by card is now a liability. I'd
  use a composite key or split that card's traffic and accept weaker ordering for it.
- *Add a new rule.* One class implementing `Rule`, annotated `@Component`. Nothing else changes.
- *How do you know it's working?* Consumer lag, decision counts by verdict, p95 scoring latency,
  and the model-unavailable warning rate.

---

## Part 6 — Rapid-recall flashcards

Cover the right column and answer out loud.

| Prompt | Answer |
|---|---|
| Unit of parallelism in Kafka | The partition |
| Ordering guarantee | Within a partition only |
| Why key by cardId | Same card → same partition → ordered, one consumer |
| 4 consumers, 3 partitions | One idles |
| Delivery semantics here | At-least-once |
| Why `eventId` exists | To dedupe redeliveries (not yet implemented) |
| Redis structure for velocity | Sorted set, score = timestamp |
| Command for the window count | ZCOUNT over [now-5min, now] |
| Why the UUID in the member | Members are unique; same-ms collisions would undercount |
| Cache rule of thumb | Cache the computed feature, not the raw query |
| How the engine finds rules | Spring injects `List<Rule>` |
| Design principle that enables it | Open–closed / strategy pattern |
| Verdict thresholds | 0 approve, 1–69 review, 70+ decline |
| Why rules run before saving | So rules see history, not the transaction itself |
| Model failure behavior | Fail open, rules-only |
| Consistency vs availability rule | Records get consistency, signals get availability |
| Why p95 not mean | The mean hides the tail |
| Percentile gotcha | Can't average percentiles across instances |
| Why multi-stage Docker build | Build tools don't ship to production |
| Why two Kafka listeners | Internal vs external advertised addresses |

---

## Part 7 — Weaknesses to own

Never pretend the project is finished. Naming the gaps precisely reads as senior; hiding them
reads as junior. The honest list:

1. **No idempotent consumption** — at-least-once means a redelivery double-counts. Fix: unique
   constraint on `eventId` or a Redis `SETNX` guard.
2. **DB write and alert publish aren't atomic** — a crash between them stores a decision with no
   alert. Fix: transactional outbox.
3. **No dead-letter queue** — a permanently failing message blocks its partition.
4. **Redis failures aren't caught** the way model failures are — inconsistent degradation.
5. **Accuracy instead of precision/recall** on a heavily imbalanced problem.
6. **`ddl-auto=update`** instead of real migrations.
7. **Percentiles computed per instance**, so they don't aggregate correctly across scorers.
8. **Synthetic data** — the model hasn't met real fraud.

---

## Glossary

**Offset** — a message's position in a partition. **Consumer lag** — distance between the latest
offset and where the group has read. **Rebalance** — reassignment of partitions when membership
changes. **Idempotent** — doing it twice has the same effect as doing it once. **Fail open** —
on failure, allow the flow to continue. **Fail closed** — on failure, block. **TTL** — time to
live, after which a key expires. **p95** — the value 95% of samples are below. **Precision** — of
what I flagged, how much was right. **Recall** — of what was there, how much I caught.
**Backpressure** — a slow consumer signaling a fast producer to slow down.
