package com.cdandeniya.fraud.rules;

import com.cdandeniya.fraud.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fires when the amount is bigger than a configured threshold. The threshold
 * lives in application.properties so I can change it without recompiling.
 */
@Component
public class HighAmountRule implements Rule {

    private static final int SCORE = 45;

    private final BigDecimal threshold;

    public HighAmountRule(@Value("${fraud.rules.high-amount-threshold:1000}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        if (transaction.getAmount().compareTo(threshold) > 0) {
            String reason = String.format("amount %s is over the %s threshold",
                    transaction.getAmount(), threshold);
            return RuleResult.triggered(SCORE, reason);
        }
        return RuleResult.notTriggered();
    }
}
