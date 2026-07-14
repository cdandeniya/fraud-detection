package com.cdandeniya.fraud.rules;

import com.cdandeniya.fraud.features.FeatureStore;
import com.cdandeniya.fraud.ml.ModelClient;
import com.cdandeniya.fraud.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Brings the ML model into the same rules pipeline as the hand-written rules. It builds
 * the model's features (amount + recent velocity from Redis), asks the model service for
 * a fraud probability, and if that's over a threshold it contributes to the risk score.
 *
 * So the rules catch the obvious stuff and the model catches subtler patterns, and both
 * feed one score. Disabled with fraud.model.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "fraud.model.enabled", havingValue = "true", matchIfMissing = true)
public class ModelRule implements Rule {

    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_SCORE = 50;

    private final ModelClient modelClient;
    private final FeatureStore featureStore;
    private final double threshold;

    public ModelRule(ModelClient modelClient,
                     FeatureStore featureStore,
                     @Value("${fraud.model.threshold:0.7}") double threshold) {
        this.modelClient = modelClient;
        this.featureStore = featureStore;
        this.threshold = threshold;
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        double velocity = featureStore.countInWindow(transaction.getCardId(), WINDOW);
        double probability = modelClient.fraudProbability(transaction.getAmount().doubleValue(), velocity);

        if (probability >= threshold) {
            int score = (int) Math.round(probability * MAX_SCORE);
            String reason = String.format("model flagged this as likely fraud (p=%.2f)", probability);
            return RuleResult.triggered(score, reason);
        }
        return RuleResult.notTriggered();
    }
}
