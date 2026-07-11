package com.cdandeniya.fraud.engine;

import com.cdandeniya.fraud.model.Transaction;
import com.cdandeniya.fraud.model.Verdict;
import com.cdandeniya.fraud.rules.Rule;
import com.cdandeniya.fraud.rules.RuleResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every rule against a transaction, adds up the scores, and turns the
 * total into a verdict. Spring injects the full list of Rule beans, so a new
 * rule is just a new @Component - this class never has to change.
 */
@Component
public class RulesEngine {

    static final int REVIEW_THRESHOLD = 1;
    static final int DECLINE_THRESHOLD = 70;

    private final List<Rule> rules;

    public RulesEngine(List<Rule> rules) {
        this.rules = rules;
    }

    public EngineResult evaluate(Transaction transaction) {
        int totalScore = 0;
        List<String> reasons = new ArrayList<>();

        for (Rule rule : rules) {
            RuleResult result = rule.evaluate(transaction);
            if (result.isTriggered()) {
                totalScore += result.getScore();
                reasons.add(result.getReason());
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("no rules triggered");
        }
        return new EngineResult(toVerdict(totalScore), totalScore, reasons);
    }

    private Verdict toVerdict(int score) {
        if (score >= DECLINE_THRESHOLD) {
            return Verdict.DECLINE;
        }
        if (score >= REVIEW_THRESHOLD) {
            return Verdict.REVIEW;
        }
        return Verdict.APPROVE;
    }
}
