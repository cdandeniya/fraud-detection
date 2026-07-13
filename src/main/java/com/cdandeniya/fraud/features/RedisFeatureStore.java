package com.cdandeniya.fraud.features;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Redis-backed feature store using one sorted set per card.
 *
 * Each transaction is a member scored by its timestamp (epoch millis). That makes
 * a sliding-window count cheap: ZCOUNT over [now - window, now]. On every write I
 * also trim anything older than an hour and set a 1-hour TTL, so a card that goes
 * quiet cleans itself up and Redis never grows without bound.
 *
 * This is the "cache the computed feature, not the raw query" idea - the count
 * lives in Redis instead of being a COUNT(*) against Postgres on the hot path.
 */
@Component
public class RedisFeatureStore implements FeatureStore {

    private static final Duration RETENTION = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public RedisFeatureStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String cardId) {
        return "txn:velocity:" + cardId;
    }

    @Override
    public void record(String cardId, Instant when) {
        String key = key(cardId);
        long ts = when.toEpochMilli();
        // member is unique so two transactions in the same millisecond both count
        String member = ts + ":" + UUID.randomUUID();

        redis.opsForZSet().add(key, member, ts);
        // drop anything older than the retention window
        redis.opsForZSet().removeRangeByScore(key, 0, ts - RETENTION.toMillis());
        redis.expire(key, RETENTION);
    }

    @Override
    public long countInWindow(String cardId, Duration window) {
        long now = System.currentTimeMillis();
        long from = now - window.toMillis();
        Long count = redis.opsForZSet().count(key(cardId), from, now);
        return count == null ? 0 : count;
    }
}
