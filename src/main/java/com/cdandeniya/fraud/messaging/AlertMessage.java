package com.cdandeniya.fraud.messaging;

import java.util.List;

/**
 * What we publish to the "fraud-alerts" topic when a transaction is flagged.
 * Downstream consumers (a dashboard, an ops queue, etc.) can subscribe to this
 * without caring how scoring works.
 */
public class AlertMessage {

    private Long transactionId;
    private String cardId;
    private String verdict;
    private int score;
    private List<String> reasons;

    public AlertMessage() {
    }

    public AlertMessage(Long transactionId, String cardId, String verdict, int score, List<String> reasons) {
        this.transactionId = transactionId;
        this.cardId = cardId;
        this.verdict = verdict;
        this.score = score;
        this.reasons = reasons;
    }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
}
