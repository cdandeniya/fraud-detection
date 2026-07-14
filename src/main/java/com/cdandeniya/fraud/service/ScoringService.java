package com.cdandeniya.fraud.service;

import com.cdandeniya.fraud.dto.DecisionResponse;
import com.cdandeniya.fraud.dto.TransactionRequest;
import com.cdandeniya.fraud.engine.EngineResult;
import com.cdandeniya.fraud.engine.RulesEngine;
import com.cdandeniya.fraud.features.FeatureStore;
import com.cdandeniya.fraud.metrics.DecisionMetrics;
import com.cdandeniya.fraud.model.Decision;
import com.cdandeniya.fraud.model.Transaction;
import com.cdandeniya.fraud.repository.DecisionRepository;
import com.cdandeniya.fraud.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Ties everything together: score the transaction, save it, save the decision, record it
 * in the feature store, and track metrics (count by verdict + how long scoring took).
 */
@Service
public class ScoringService {

    private final RulesEngine rulesEngine;
    private final FeatureStore featureStore;
    private final DecisionMetrics metrics;
    private final TransactionRepository transactionRepository;
    private final DecisionRepository decisionRepository;

    public ScoringService(RulesEngine rulesEngine,
                          FeatureStore featureStore,
                          DecisionMetrics metrics,
                          TransactionRepository transactionRepository,
                          DecisionRepository decisionRepository) {
        this.rulesEngine = rulesEngine;
        this.featureStore = featureStore;
        this.metrics = metrics;
        this.transactionRepository = transactionRepository;
        this.decisionRepository = decisionRepository;
    }

    @Transactional
    public DecisionResponse score(TransactionRequest request) {
        long start = System.nanoTime();
        try {
            Transaction transaction = new Transaction(
                    request.getCardId(),
                    request.getAmount(),
                    request.getMerchant(),
                    request.getCountry(),
                    Instant.now());

            // Run the rules BEFORE recording this transaction so the velocity and
            // new-country rules only see past history, not the txn we're scoring now.
            EngineResult result = rulesEngine.evaluate(transaction);

            Transaction saved = transactionRepository.save(transaction);
            featureStore.record(saved.getCardId(), saved.getTimestamp());

            Decision decision = new Decision(
                    saved.getId(),
                    result.getVerdict(),
                    result.getScore(),
                    String.join("; ", result.getReasons()),
                    Instant.now());
            decisionRepository.save(decision);

            metrics.recordDecision(result.getVerdict());

            return new DecisionResponse(
                    saved.getId(),
                    result.getVerdict(),
                    result.getScore(),
                    result.getReasons());
        } finally {
            metrics.recordLatency(System.nanoTime() - start);
        }
    }
}
