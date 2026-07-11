package com.cdandeniya.fraud.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cardId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String merchant;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private Instant timestamp;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(String cardId, BigDecimal amount, String merchant, String country, Instant timestamp) {
        this.cardId = cardId;
        this.amount = amount;
        this.merchant = merchant;
        this.country = country;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public String getCardId() {
        return cardId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getCountry() {
        return country;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
