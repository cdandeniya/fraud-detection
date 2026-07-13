package com.cdandeniya.fraud.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

/**
 * Stands in for a real payment stream. On a timer it makes up transactions and drops
 * them on the topic - mostly normal, and roughly one in ten deliberately sketchy
 * (big amount from an unusual country) so there's something for the rules to catch.
 *
 * Off by default in tests; toggle with fraud.simulator.enabled.
 */
@Component
@ConditionalOnProperty(name = "fraud.simulator.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionSimulator {

    private static final String[] MERCHANTS = {"Amazon", "Walmart", "Steam", "Uber", "Apple"};
    private static final String[] ODD_COUNTRIES = {"RU", "NG", "BR"};

    private final TransactionProducer producer;
    private final Random random = new Random();

    public TransactionSimulator(TransactionProducer producer) {
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "${fraud.simulator.interval-ms:1000}")
    public void tick() {
        String cardId = "card-" + (random.nextInt(20) + 1);
        String merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];

        BigDecimal amount;
        String country;
        if (random.nextInt(10) == 0) {
            // suspicious: large amount from an unusual country
            amount = BigDecimal.valueOf(1000 + random.nextInt(9000));
            country = ODD_COUNTRIES[random.nextInt(ODD_COUNTRIES.length)];
        } else {
            // normal everyday spend
            amount = BigDecimal.valueOf(5 + random.nextInt(200));
            country = "US";
        }

        producer.publish(new TransactionMessage(
                UUID.randomUUID().toString(), cardId, amount, merchant, country));
    }
}
