package com.cdandeniya.fraud.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes transactions to the "transactions" topic. The message key is the cardId
 * on purpose: Kafka sends all records with the same key to the same partition, so in
 * Stage 4 every transaction for a card lands on the same consumer and its Redis
 * velocity counts stay consistent.
 */
@Component
public class TransactionProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public TransactionProducer(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${fraud.kafka.topics.transactions}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(TransactionMessage message) {
        kafkaTemplate.send(topic, message.getCardId(), message);
    }
}
