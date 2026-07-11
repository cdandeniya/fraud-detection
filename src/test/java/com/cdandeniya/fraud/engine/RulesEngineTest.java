package com.cdandeniya.fraud.engine;

import com.cdandeniya.fraud.model.Transaction;
import com.cdandeniya.fraud.model.Verdict;
import com.cdandeniya.fraud.repository.TransactionRepository;
import com.cdandeniya.fraud.rules.HighAmountRule;
import com.cdandeniya.fraud.rules.NewCountryRule;
import com.cdandeniya.fraud.rules.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the engine. The repository is mocked so these run without a
 * database - I'm only testing the scoring/verdict logic here.
 */
class RulesEngineTest {

    private TransactionRepository transactionRepository;
    private RulesEngine engine;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        Rule highAmount = new HighAmountRule(new BigDecimal("1000"));
        Rule newCountry = new NewCountryRule(transactionRepository);
        engine = new RulesEngine(List.of(highAmount, newCountry));
    }

    private Transaction txn(String cardId, String amount, String country) {
        return new Transaction(cardId, new BigDecimal(amount), "test-merchant", country, Instant.now());
    }

    @Test
    void approvesANormalTransaction() {
        when(transactionRepository.countByCardId("card-1")).thenReturn(5L);
        when(transactionRepository.existsByCardIdAndCountry("card-1", "US")).thenReturn(true);

        EngineResult result = engine.evaluate(txn("card-1", "20.00", "US"));

        assertThat(result.getVerdict()).isEqualTo(Verdict.APPROVE);
        assertThat(result.getScore()).isZero();
    }

    @Test
    void flagsAHighAmountForReview() {
        when(transactionRepository.countByCardId("card-1")).thenReturn(5L);
        when(transactionRepository.existsByCardIdAndCountry("card-1", "US")).thenReturn(true);

        EngineResult result = engine.evaluate(txn("card-1", "5000.00", "US"));

        assertThat(result.getVerdict()).isEqualTo(Verdict.REVIEW);
        assertThat(result.getScore()).isEqualTo(45);
    }

    @Test
    void flagsANewCountryForReview() {
        when(transactionRepository.countByCardId("card-1")).thenReturn(5L);
        when(transactionRepository.existsByCardIdAndCountry("card-1", "RU")).thenReturn(false);

        EngineResult result = engine.evaluate(txn("card-1", "20.00", "RU"));

        assertThat(result.getVerdict()).isEqualTo(Verdict.REVIEW);
        assertThat(result.getScore()).isEqualTo(40);
    }

    @Test
    void declinesAHighAmountInANewCountry() {
        when(transactionRepository.countByCardId("card-1")).thenReturn(5L);
        when(transactionRepository.existsByCardIdAndCountry("card-1", "RU")).thenReturn(false);

        EngineResult result = engine.evaluate(txn("card-1", "5000.00", "RU"));

        assertThat(result.getVerdict()).isEqualTo(Verdict.DECLINE);
        assertThat(result.getScore()).isEqualTo(85);
        assertThat(result.getReasons()).hasSize(2);
    }

    @Test
    void leavesBrandNewCardsAlone() {
        // no history at all -> the new-country rule should not fire
        when(transactionRepository.countByCardId("card-9")).thenReturn(0L);

        EngineResult result = engine.evaluate(txn("card-9", "20.00", "US"));

        assertThat(result.getVerdict()).isEqualTo(Verdict.APPROVE);
    }
}
