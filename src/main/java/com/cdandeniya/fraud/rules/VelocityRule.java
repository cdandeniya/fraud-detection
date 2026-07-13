package com.cdandeniya.fraud.rules;

import com.cdandeniya.fraud.features.FeatureStore;
import com.cdandeniya.fraud.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fires when a card is used too many times in a short window - the classic
 * "card testing" pattern where a stolen card gets hammered with rapid charges.
 * Reads its count from the Redis feature store instead of the database.
 */
@Component
public class VelocityRule implements Rule {

    private static final int SCORE = 35;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final FeatureStore featureStore;
    private final int maxPer5Min;

    public VelocityRule(FeatureStore featureStore,
                        @Value("${fraud.rules.velocity.max-per-5-min:5}") int maxPer5Min) {
        this.featureStore = featureStore;
        this.maxPer5Min = maxPer5Min;
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        long recent = featureStore.countInWindow(transaction.getCardId(), WINDOW);
        if (recent > maxPer5Min) {
            String reason = String.format(
                    "card %s made %d transactions in the last 5 minutes (limit %d)",
                    transaction.getCardId(), recent, maxPer5Min);
            return RuleResult.triggered(SCORE, reason);
        }
        return RuleResult.notTriggered();
    }
}
