package com.cdandeniya.fraud.features;

import java.time.Duration;
import java.time.Instant;

/**
 * A fast store of recent per-card behavior. The point is to answer questions like
 * "how many times has this card been used in the last 5 minutes?" without hitting
 * Postgres every time. Backed by Redis (see RedisFeatureStore), but kept as an
 * interface so the rules don't care where the numbers come from.
 */
public interface FeatureStore {

    /** Record that a card was just used at a given time. */
    void record(String cardId, Instant when);

    /** How many transactions this card has had within the given window up to now. */
    long countInWindow(String cardId, Duration window);
}
