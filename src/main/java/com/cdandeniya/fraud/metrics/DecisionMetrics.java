package com.cdandeniya.fraud.metrics;

import com.cdandeniya.fraud.model.Verdict;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Wraps the metrics for the scoring path so the rest of the code doesn't have to think
 * about Micrometer. Two things get tracked:
 *   - a counter of decisions, tagged by verdict  (fraud_decisions_total{verdict="..."})
 *   - a timer for how long scoring takes          (fraud_scoring_latency_seconds)
 *
 * Both show up on /actuator/prometheus for scraping, and feed the /stats dashboard.
 */
@Component
public class DecisionMetrics {

    private final MeterRegistry registry;
    private final Timer scoringTimer;

    public DecisionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.scoringTimer = Timer.builder("fraud.scoring.latency")
                .description("time to score one transaction")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordDecision(Verdict verdict) {
        registry.counter("fraud.decisions", "verdict", verdict.name()).increment();
    }

    public void recordLatency(long nanos) {
        scoringTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    public long count(Verdict verdict) {
        Counter counter = registry.find("fraud.decisions").tag("verdict", verdict.name()).counter();
        return counter == null ? 0L : (long) counter.count();
    }

    public long totalScored() {
        return scoringTimer.count();
    }

    public double meanLatencyMillis() {
        return scoringTimer.mean(TimeUnit.MILLISECONDS);
    }
}
