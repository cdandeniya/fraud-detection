package com.cdandeniya.fraud.service;

import com.cdandeniya.fraud.dto.DecisionResponse;
import com.cdandeniya.fraud.dto.TransactionRequest;
import com.cdandeniya.fraud.engine.EngineResult;
import com.cdandeniya.fraud.engine.RulesEngine;
import com.cdandeniya.fraud.model.Decision;
import com.cdandeniya.fraud.model.Transaction;
import com.cdandeniya.fraud.repository.DecisionRepository;
import com.cdandeniya.fraud.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Ties everything together: score the transaction, save it, save the decision.
 * One DB trip in, the rules run, two rows written out.
 */
@Service
public class ScoringService {

    private final RulesEngine rulesEngine;
    private final TransactionRepository transactionRepository;
    private final DecisionRepository decisionRepository;

    public ScoringService(RulesEngine rulesEngine,
                          TransactionRepository transactionRepository,
                          DecisionRepository decisionRepository) {
        this.rulesEngine = rulesEngine;
        this.transactionRepository = transactionRepository;
        this.decisionRepository = decisionRepository;
    }

    @Transactional
    public DecisionResponse score(TransactionRequest request) {
        Transaction transaction = new Transaction(
                request.getCardId(),
                request.getAmount(),
                request.getMerchant(),
                request.getCountry(),
                Instant.now());

        // Run the rules BEFORE saving so the new-country rule only sees past history,
        // not the transaction we're scoring right now.
        EngineResult result = rulesEngine.evaluate(transaction);

        Transaction saved = transactionRepository.save(transaction);

        Decision decision = new Decision(
                saved.getId(),
                result.getVerdict(),
                result.getScore(),
                String.join("; ", result.getReasons()),
                Instant.now());
        decisionRepository.save(decision);

        return new DecisionResponse(
                saved.getId(),
                result.getVerdict(),
                result.getScore(),
                result.getReasons());
    }
}
