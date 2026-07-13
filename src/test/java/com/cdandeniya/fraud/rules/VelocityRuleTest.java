package com.cdandeniya.fraud.rules;

import com.cdandeniya.fraud.features.FeatureStore;
import com.cdandeniya.fraud.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the velocity rule. The feature store is mocked so these run
 * without Redis - I'm only testing the "too many too fast" logic here.
 */
class VelocityRuleTest {

    private FeatureStore featureStore;
    private VelocityRule rule;

    @BeforeEach
    void setUp() {
        featureStore = mock(FeatureStore.class);
        rule = new VelocityRule(featureStore, 5); // limit of 5 per 5 minutes
    }

    private Transaction txn() {
        return new Transaction("card-1", new BigDecimal("20"), "test-merchant", "US", Instant.now());
    }

    @Test
    void doesNotFireUnderTheLimit() {
        when(featureStore.countInWindow(eq("card-1"), any(Duration.class))).thenReturn(3L);

        RuleResult result = rule.evaluate(txn());

        assertThat(result.isTriggered()).isFalse();
    }

    @Test
    void firesOverTheLimit() {
        when(featureStore.countInWindow(eq("card-1"), any(Duration.class))).thenReturn(9L);

        RuleResult result = rule.evaluate(txn());

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getScore()).isEqualTo(35);
        assertThat(result.getReason()).contains("9 transactions");
    }
}
