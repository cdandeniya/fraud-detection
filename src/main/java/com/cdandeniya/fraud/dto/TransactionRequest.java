package com.cdandeniya.fraud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * The JSON body that comes in on POST /score. Bean-validation annotations
 * reject junk (missing card, negative amount) before it hits the service.
 */
public class TransactionRequest {

    @NotBlank
    private String cardId;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String merchant;

    @NotBlank
    private String country;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
