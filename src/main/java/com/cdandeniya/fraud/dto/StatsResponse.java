package com.cdandeniya.fraud.dto;

/** Live totals for the dashboard, read straight off the metrics. */
public class StatsResponse {

    private final long approve;
    private final long review;
    private final long decline;
    private final long total;
    private final double meanLatencyMs;

    public StatsResponse(long approve, long review, long decline, long total, double meanLatencyMs) {
        this.approve = approve;
        this.review = review;
        this.decline = decline;
        this.total = total;
        this.meanLatencyMs = meanLatencyMs;
    }

    public long getApprove() { return approve; }
    public long getReview() { return review; }
    public long getDecline() { return decline; }
    public long getTotal() { return total; }
    public double getMeanLatencyMs() { return meanLatencyMs; }
}
