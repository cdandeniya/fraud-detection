# Fraud Detection Pipeline

A real-time system that scores payment transactions for fraud as they stream in. Built in
Java/Spring Boot with Kafka, Redis, and Postgres.

## What it does

When a card gets swiped (or in my case, a simulator pretends one did), the transaction flows
through the pipeline and comes out the other side with a decision: **APPROVE**, **REVIEW**, or
**DECLINE**, plus the reasons why. It does this in real time, one transaction at a time, fast
enough that it wouldn't hold up a real checkout.

The interesting part isn't the fraud rules themselves — it's making it *fast, scalable, and
reliable at the same time*, which is where all the system-design stuff comes in.

## Architecture

**The flow:** a producer publishes transactions to Kafka → the scoring service reads them,
looks up recent behavior features from Redis (like how many times this card was used in the
last 5 minutes), runs them through a rules engine and an ML model → writes the decision to
Postgres and pushes anything suspicious to an alerts topic. Multiple copies of the scoring
service run at once and split the work through a Kafka consumer group.
