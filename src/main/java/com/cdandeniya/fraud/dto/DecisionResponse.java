package com.cdandeniya.fraud.dto;

import com.cdandeniya.fraud.model.Verdict;

import java.util.List;

/**
 * What POST /score sends back: the stored transaction id, the verdict,
 * the risk score, and the list of reasons behind it.
 */
public class DecisionResponse {

    private final Long transactionId;
    private final Verdict verdict;
    private final int score;
    private final List<String> reasons;

    public DecisionResponse(Long transactionId, Verdict verdict, int score, List<String> reasons) {
        this.transactionId = transactionId;
        this.verdict = verdict;
        this.score = score;
        this.reasons = reasons;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public int getScore() {
        return score;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
