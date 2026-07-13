package com.cdandeniya.fraud.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics so they exist on startup. Three partitions each - I don't need
 * them yet with one consumer, but Stage 4 scales out to multiple consumers and the
 * partition count is the ceiling on how many can share the load, so I set it now.
 *
 * Guarded by a property so tests (and anyone running without a broker) can turn it off.
 */
@Configuration
@ConditionalOnProperty(name = "fraud.kafka.create-topics", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Bean
    public NewTopic transactionsTopic(@Value("${fraud.kafka.topics.transactions}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic alertsTopic(@Value("${fraud.kafka.topics.alerts}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
