package com.cdandeniya.fraud.engine;

import com.cdandeniya.fraud.model.Verdict;

import java.util.List;

/**
 * The engine's overall answer for one transaction: the final verdict,
 * the summed risk score, and every reason that contributed.
 */
public class EngineResult {

    private final Verdict verdict;
    private final int score;
    private final List<String> reasons;

    public EngineResult(Verdict verdict, int score, List<String> reasons) {
        this.verdict = verdict;
        this.score = score;
        this.reasons = reasons;
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
