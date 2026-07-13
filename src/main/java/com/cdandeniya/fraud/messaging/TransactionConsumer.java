package com.cdandeniya.fraud.messaging;

import com.cdandeniya.fraud.dto.DecisionResponse;
import com.cdandeniya.fraud.dto.TransactionRequest;
import com.cdandeniya.fraud.model.Verdict;
import com.cdandeniya.fraud.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * The heart of the streaming pipeline: read a transaction off the topic, score it with
 * the same ScoringService the REST endpoint uses, and if it's not a clean APPROVE,
 * publish an alert to the "fraud-alerts" topic for whoever is downstream.
 *
 * Scoring stays free of Kafka - the consumer is the only thing that knows about topics,
 * so the rules and the service don't get coupled to the transport.
 */
@Component
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final ScoringService scoringService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String alertsTopic;

    public TransactionConsumer(ScoringService scoringService,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${fraud.kafka.topics.alerts}") String alertsTopic) {
        this.scoringService = scoringService;
        this.kafkaTemplate = kafkaTemplate;
        this.alertsTopic = alertsTopic;
    }

    @KafkaListener(topics = "${fraud.kafka.topics.transactions}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(TransactionMessage message) {
        TransactionRequest request = new TransactionRequest();
        request.setCardId(message.getCardId());
        request.setAmount(message.getAmount());
        request.setMerchant(message.getMerchant());
        request.setCountry(message.getCountry());

        DecisionResponse decision = scoringService.score(request);

        if (decision.getVerdict() != Verdict.APPROVE) {
            AlertMessage alert = new AlertMessage(
                    decision.getTransactionId(),
                    message.getCardId(),
                    decision.getVerdict().name(),
                    decision.getScore(),
                    decision.getReasons());
            kafkaTemplate.send(alertsTopic, message.getCardId(), alert);
            log.info("flagged txn {} for card {}: {} (score {})",
                    decision.getTransactionId(), message.getCardId(),
                    decision.getVerdict(), decision.getScore());
        }
    }
}
