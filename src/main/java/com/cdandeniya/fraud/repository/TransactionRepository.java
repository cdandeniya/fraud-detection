package com.cdandeniya.fraud.repository;

import com.cdandeniya.fraud.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // used by the new-country rule to check a card's history
    boolean existsByCardIdAndCountry(String cardId, String country);

    long countByCardId(String cardId);
}
