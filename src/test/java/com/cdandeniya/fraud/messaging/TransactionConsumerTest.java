package com.cdandeniya.fraud.messaging;

import com.cdandeniya.fraud.dto.DecisionResponse;
import com.cdandeniya.fraud.dto.TransactionRequest;
import com.cdandeniya.fraud.model.Verdict;
import com.cdandeniya.fraud.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the consumer's routing logic with mocks - no Kafka broker needed.
 * A clean APPROVE should not raise an alert; anything else should.
 */
class TransactionConsumerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private ScoringService scoringService;
    private TransactionConsumer consumer;

    @BeforeEach
    void setUp() {
        scoringService = mock(ScoringService.class);
        consumer = new TransactionConsumer(scoringService, kafkaTemplate, "fraud-alerts");
    }

    private TransactionMessage message() {
        return new TransactionMessage("evt-1", "card-1", new BigDecimal("5000"), "Amazon", "RU");
    }

    @Test
    void doesNotAlertOnApprove() {
        when(scoringService.score(any(TransactionRequest.class)))
                .thenReturn(new DecisionResponse(1L, Verdict.APPROVE, 0, List.of("no rules triggered")));

        consumer.handle(message());

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void alertsOnDecline() {
        when(scoringService.score(any(TransactionRequest.class)))
                .thenReturn(new DecisionResponse(1L, Verdict.DECLINE, 85, List.of("high amount", "new country")));

        consumer.handle(message());

        verify(kafkaTemplate).send(eq("fraud-alerts"), eq("card-1"), any(AlertMessage.class));
    }
}
