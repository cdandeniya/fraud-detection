package com.cdandeniya.fraud.rules;

import com.cdandeniya.fraud.model.Transaction;
import com.cdandeniya.fraud.repository.TransactionRepository;
import org.springframework.stereotype.Component;

/**
 * Fires when a card shows up in a country we've never seen it in before.
 * Brand-new cards (no history at all) get a pass, otherwise every card's
 * very first transaction would look suspicious.
 */
@Component
public class NewCountryRule implements Rule {

    private static final int SCORE = 40;

    private final TransactionRepository transactionRepository;

    public NewCountryRule(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        long history = transactionRepository.countByCardId(transaction.getCardId());
        if (history == 0) {
            return RuleResult.notTriggered();
        }

        boolean seenBefore = transactionRepository.existsByCardIdAndCountry(
                transaction.getCardId(), transaction.getCountry());
        if (!seenBefore) {
            String reason = String.format("first time card %s has been used in %s",
                    transaction.getCardId(), transaction.getCountry());
            return RuleResult.triggered(SCORE, reason);
        }
        return RuleResult.notTriggered();
    }
}
