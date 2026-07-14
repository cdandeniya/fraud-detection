package com.cdandeniya.fraud.metrics;

import com.cdandeniya.fraud.model.Verdict;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionMetricsTest {

    @Test
    void countsDecisionsByVerdict() {
        DecisionMetrics metrics = new DecisionMetrics(new SimpleMeterRegistry());

        metrics.recordDecision(Verdict.APPROVE);
        metrics.recordDecision(Verdict.APPROVE);
        metrics.recordDecision(Verdict.DECLINE);

        assertThat(metrics.count(Verdict.APPROVE)).isEqualTo(2);
        assertThat(metrics.count(Verdict.DECLINE)).isEqualTo(1);
        assertThat(metrics.count(Verdict.REVIEW)).isZero();
    }

    @Test
    void tracksScoringLatency() {
        DecisionMetrics metrics = new DecisionMetrics(new SimpleMeterRegistry());

        metrics.recordLatency(5_000_000L); // 5 ms

        assertThat(metrics.totalScored()).isEqualTo(1);
        assertThat(metrics.meanLatencyMillis()).isGreaterThan(0.0);
    }
}
