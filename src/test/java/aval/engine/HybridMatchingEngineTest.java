package aval.engine;

import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.MatchingConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for HybridMatchingEngine.
 * Verifies the delegate/fallback orchestration between rule-based and semantic engines.
 */
public class HybridMatchingEngineTest {

    private StandardizedTransaction tx(BigDecimal amount, LocalDate date, TransactionType type) {
        return new StandardizedTransaction(UUID.randomUUID(), date, amount, "narrative", type, UUID.randomUUID());
    }

    // ── rule engine matches everything → semantic never called ────────────────

    @Test
    void whenAllLedgerMatchedByRules_semanticIsNotCalled() {
        MatchingEngine ruleEngine    = mock(MatchingEngine.class);
        MatchingEngine semanticEngine = mock(MatchingEngine.class);

        LocalDate today = LocalDate.now();
        StandardizedTransaction ledger = tx(new BigDecimal("100.00"), today, TransactionType.DEBIT);
        StandardizedTransaction bank   = tx(new BigDecimal("100.00"), today, TransactionType.CREDIT);
        MatchHypothesis ruleHyp = new MatchHypothesis(ledger, bank, 1.0, MatchType.EXACT_RULE);

        when(ruleEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(ruleHyp));

        HybridMatchingEngine hybrid = new HybridMatchingEngine(ruleEngine, semanticEngine);
        List<MatchHypothesis> results = hybrid.generateHypotheses(List.of(ledger), List.of(bank));

        assertEquals(1, results.size());
        verify(semanticEngine, never()).generateHypotheses(anyList(), anyList());
    }

    // ── rule engine misses a tx → semantic is called with that tx ─────────────

    @Test
    void whenLedgerTxUnmatched_semanticCalledWithUnmatchedOnly() {
        MatchingEngine ruleEngine     = mock(MatchingEngine.class);
        MatchingEngine semanticEngine = mock(MatchingEngine.class);

        LocalDate today = LocalDate.now();
        StandardizedTransaction matched   = tx(new BigDecimal("100.00"), today, TransactionType.DEBIT);
        StandardizedTransaction unmatched = tx(new BigDecimal("999.00"), today, TransactionType.DEBIT);
        StandardizedTransaction bank1     = tx(new BigDecimal("100.00"), today, TransactionType.CREDIT);
        StandardizedTransaction bank2     = tx(new BigDecimal("500.00"), today, TransactionType.CREDIT);

        // Rule engine matches "matched" with "bank1" but not "unmatched"
        MatchHypothesis ruleHyp = new MatchHypothesis(matched, bank1, 1.0, MatchType.EXACT_RULE);
        when(ruleEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(ruleHyp));
        when(semanticEngine.generateHypotheses(anyList(), anyList())).thenReturn(Collections.emptyList());

        HybridMatchingEngine hybrid = new HybridMatchingEngine(ruleEngine, semanticEngine);
        hybrid.generateHypotheses(List.of(matched, unmatched), List.of(bank1, bank2));

        // Semantic should be called with only the unmatched ledger tx and unmatched bank tx (bank2)
        verify(semanticEngine).generateHypotheses(
            argThat(list -> list.size() == 1 && list.get(0).getTransactionId().equals(unmatched.getTransactionId())),
            argThat(list -> list.size() == 1 && list.get(0).getTransactionId().equals(bank2.getTransactionId()))
        );
    }

    // ── combined results ──────────────────────────────────────────────────────

    @Test
    void hybridResult_containsBothRuleAndSemanticHypotheses() {
        MatchingEngine ruleEngine     = mock(MatchingEngine.class);
        MatchingEngine semanticEngine = mock(MatchingEngine.class);

        LocalDate today = LocalDate.now();
        StandardizedTransaction l1 = tx(new BigDecimal("100.00"), today, TransactionType.DEBIT);
        StandardizedTransaction l2 = tx(new BigDecimal("777.00"), today, TransactionType.DEBIT);
        StandardizedTransaction b1 = tx(new BigDecimal("100.00"), today, TransactionType.CREDIT);
        StandardizedTransaction b2 = tx(new BigDecimal("780.00"), today, TransactionType.CREDIT);

        MatchHypothesis ruleHyp     = new MatchHypothesis(l1, b1, 1.0, MatchType.EXACT_RULE);
        MatchHypothesis semanticHyp = new MatchHypothesis(l2, b2, 0.82, MatchType.AI_PROBABILISTIC);

        when(ruleEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(ruleHyp));
        when(semanticEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(semanticHyp));

        HybridMatchingEngine hybrid = new HybridMatchingEngine(ruleEngine, semanticEngine);
        List<MatchHypothesis> results = hybrid.generateHypotheses(List.of(l1, l2), List.of(b1, b2));

        assertEquals(2, results.size());
        assertTrue(results.contains(ruleHyp));
        assertTrue(results.contains(semanticHyp));
    }

    // ── empty inputs ──────────────────────────────────────────────────────────

    @Test
    void emptyLedger_returnsEmpty_semanticNotCalled() {
        MatchingEngine ruleEngine     = mock(MatchingEngine.class);
        MatchingEngine semanticEngine = mock(MatchingEngine.class);

        when(ruleEngine.generateHypotheses(anyList(), anyList())).thenReturn(Collections.emptyList());

        HybridMatchingEngine hybrid = new HybridMatchingEngine(ruleEngine, semanticEngine);
        List<MatchHypothesis> results = hybrid.generateHypotheses(
            Collections.emptyList(),
            List.of(tx(new BigDecimal("100.00"), LocalDate.now(), TransactionType.CREDIT))
        );

        assertTrue(results.isEmpty());
        verify(semanticEngine, never()).generateHypotheses(anyList(), anyList());
    }

    @Test
    void bothEmpty_returnsEmpty() {
        MatchingEngine ruleEngine     = mock(MatchingEngine.class);
        MatchingEngine semanticEngine = mock(MatchingEngine.class);

        when(ruleEngine.generateHypotheses(anyList(), anyList())).thenReturn(Collections.emptyList());

        HybridMatchingEngine hybrid = new HybridMatchingEngine(ruleEngine, semanticEngine);
        List<MatchHypothesis> results = hybrid.generateHypotheses(
            Collections.emptyList(), Collections.emptyList()
        );

        assertTrue(results.isEmpty());
    }

    // ── real engines integration ──────────────────────────────────────────────

    @Test
    void realRuleEngine_allMatched_semanticMockNeverCalled() {
        MatchingConfig cfg = new MatchingConfig();
        MatchingEngine real = new RuleBasedMatchingEngine(cfg);
        MatchingEngine semanticMock = mock(MatchingEngine.class);

        LocalDate today = LocalDate.now();
        StandardizedTransaction ledger = tx(new BigDecimal("250.00"), today, TransactionType.DEBIT);
        StandardizedTransaction bank   = tx(new BigDecimal("250.00"), today, TransactionType.CREDIT);

        HybridMatchingEngine hybrid = new HybridMatchingEngine(real, semanticMock);
        List<MatchHypothesis> results = hybrid.generateHypotheses(List.of(ledger), List.of(bank));

        assertEquals(1, results.size());
        assertEquals(MatchType.EXACT_RULE, results.get(0).getMatchType());
        verify(semanticMock, never()).generateHypotheses(anyList(), anyList());
    }

    // ── ledger transaction ID integrity ──────────────────────────────────────

    @Test
    void unmatchedLedger_forwardedById_notByReference() {
        MatchingEngine ruleEngine     = mock(MatchingEngine.class);
        MatchingEngine semanticEngine = mock(MatchingEngine.class);

        UUID ledgerId = UUID.randomUUID();
        StandardizedTransaction ledger = new StandardizedTransaction(
            ledgerId, LocalDate.now(), new BigDecimal("500.00"), "pay", TransactionType.DEBIT, UUID.randomUUID()
        );
        StandardizedTransaction bank = tx(new BigDecimal("100.00"), LocalDate.now(), TransactionType.CREDIT);

        // Rule engine returns no hypotheses → all unmatched
        when(ruleEngine.generateHypotheses(anyList(), anyList())).thenReturn(Collections.emptyList());
        when(semanticEngine.generateHypotheses(anyList(), anyList())).thenReturn(Collections.emptyList());

        HybridMatchingEngine hybrid = new HybridMatchingEngine(ruleEngine, semanticEngine);
        hybrid.generateHypotheses(List.of(ledger), List.of(bank));

        verify(semanticEngine).generateHypotheses(
            argThat(list -> list.size() == 1 && list.get(0).getTransactionId().equals(ledgerId)),
            argThat(list -> list.size() == 1 && list.get(0).getTransactionId().equals(bank.getTransactionId()))
        );
    }
}
