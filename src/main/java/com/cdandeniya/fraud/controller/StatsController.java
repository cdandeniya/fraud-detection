package com.cdandeniya.fraud.controller;

import com.cdandeniya.fraud.dto.StatsResponse;
import com.cdandeniya.fraud.metrics.DecisionMetrics;
import com.cdandeniya.fraud.model.Verdict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small read-only endpoint the dashboard polls. It just reshapes the metrics into
 * something easy to render - the numbers themselves live in Micrometer.
 */
@RestController
public class StatsController {

    private final DecisionMetrics metrics;

    public StatsController(DecisionMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        long approve = metrics.count(Verdict.APPROVE);
        long review = metrics.count(Verdict.REVIEW);
        long decline = metrics.count(Verdict.DECLINE);
        return new StatsResponse(approve, review, decline,
                approve + review + decline, metrics.meanLatencyMillis());
    }
}
