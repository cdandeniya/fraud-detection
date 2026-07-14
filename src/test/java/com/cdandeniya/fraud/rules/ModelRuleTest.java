package com.cdandeniya.fraud.rules;

import com.cdandeniya.fraud.features.FeatureStore;
import com.cdandeniya.fraud.ml.ModelClient;
import com.cdandeniya.fraud.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the model rule. Both the model client and the feature store are mocked,
 * so no model service or Redis is needed - just the "probability over threshold" logic.
 */
class ModelRuleTest {

    private ModelClient modelClient;
    private FeatureStore featureStore;
    private ModelRule rule;

    @BeforeEach
    void setUp() {
        modelClient = mock(ModelClient.class);
        featureStore = mock(FeatureStore.class);
        rule = new ModelRule(modelClient, featureStore, 0.7); // threshold 0.7
        when(featureStore.countInWindow(eq("card-1"), any(Duration.class))).thenReturn(2L);
    }

    private Transaction txn() {
        return new Transaction("card-1", new BigDecimal("100"), "test-merchant", "US", Instant.now());
    }

    @Test
    void doesNotFireBelowThreshold() {
        when(modelClient.fraudProbability(anyDouble(), anyDouble())).thenReturn(0.30);

        RuleResult result = rule.evaluate(txn());

        assertThat(result.isTriggered()).isFalse();
    }

    @Test
    void firesAboveThreshold() {
        when(modelClient.fraudProbability(anyDouble(), anyDouble())).thenReturn(0.90);

        RuleResult result = rule.evaluate(txn());

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getScore()).isEqualTo(45); // round(0.90 * 50)
        assertThat(result.getReason()).contains("model flagged");
    }
}
