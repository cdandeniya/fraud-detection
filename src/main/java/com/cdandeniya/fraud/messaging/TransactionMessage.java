package com.cdandeniya.fraud.messaging;

import java.math.BigDecimal;

/**
 * The JSON shape we put on the "transactions" topic. eventId gives every message a
 * unique id, which matters because Kafka is at-least-once: the same message can be
 * redelivered, so downstream we can dedupe on it if we need to.
 *
 * Needs a no-arg constructor + setters so the JSON deserializer can build it.
 */
public class TransactionMessage {

    private String eventId;
    private String cardId;
    private BigDecimal amount;
    private String merchant;
    private String country;

    public TransactionMessage() {
    }

    public TransactionMessage(String eventId, String cardId, BigDecimal amount, String merchant, String country) {
        this.eventId = eventId;
        this.cardId = cardId;
        this.amount = amount;
        this.merchant = merchant;
        this.country = country;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
