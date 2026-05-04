package aval.engine;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.MatchingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for RuleBasedMatchingEngine.
 */
public class RuleBasedMatchingEngineTest {

    private MatchingConfig config;
    private RuleBasedMatchingEngine engine;

    @BeforeEach
    void setUp() {
        config = new MatchingConfig();       // tolerance = 7 days, threshold = 0.95
        engine = new RuleBasedMatchingEngine(config);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private StandardizedTransaction tx(BigDecimal amount, LocalDate date, TransactionType type) {
        return new StandardizedTransaction(UUID.randomUUID(), date, amount, "narrative", type, UUID.randomUUID());
    }

    private StandardizedTransaction tx(BigDecimal amount, LocalDate date) {
        return tx(amount, date, TransactionType.DEBIT);
    }

    // ── exact match ──────────────────────────────────────────────────────────

    @Test
    void exactAmountSameDay_producesOneHypothesis() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("500.00"), today)),
            List.of(tx(new BigDecimal("500.00"), today))
        );
        assertEquals(1, results.size());
    }

    @Test
    void exactAmountSameDay_hasConfidenceOne() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("1234.56"), today)),
            List.of(tx(new BigDecimal("1234.56"), today))
        );
        assertEquals(1.0, results.get(0).getConfidenceScore(), 0.0001);
    }

    @Test
    void exactAmountSameDay_matchTypeIsExactRule() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("100.00"), today)),
            List.of(tx(new BigDecimal("100.00"), today))
        );
        assertEquals(MatchType.EXACT_RULE, results.get(0).getMatchType());
    }

    @Test
    void exactAmountSameDay_statusIsPendingReview() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("100.00"), today)),
            List.of(tx(new BigDecimal("100.00"), today))
        );
        assertEquals(HypothesisStatus.PENDING_REVIEW, results.get(0).getStatus());
    }

    @Test
    void exactAmountSameDay_justificationMentionsDateTolerance() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("100.00"), today)),
            List.of(tx(new BigDecimal("100.00"), today))
        );
        assertNotNull(results.get(0).getJustification());
        assertTrue(results.get(0).getJustification().contains("7-day"));
    }

    // ── within tolerance window ───────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 7})
    void exactAmount_withinToleranceDays_matches(int dayOffset) {
        LocalDate base = LocalDate.of(2024, 6, 1);
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("750.00"), base)),
            List.of(tx(new BigDecimal("750.00"), base.plusDays(dayOffset)))
        );
        assertEquals(1, results.size(), "Should match at offset " + dayOffset + " days");
    }

    @Test
    void exactAmount_atExactBoundary7Days_matches() {
        LocalDate base = LocalDate.of(2024, 1, 1);
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("200.00"), base)),
            List.of(tx(new BigDecimal("200.00"), base.plusDays(7)))
        );
        assertEquals(1, results.size(), "Boundary of 7 days should still match");
    }

    @Test
    void exactAmount_oneMoreThanTolerance_doesNotMatch() {
        LocalDate base = LocalDate.of(2024, 1, 1);
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("200.00"), base)),
            List.of(tx(new BigDecimal("200.00"), base.plusDays(8)))
        );
        assertTrue(results.isEmpty(), "8 days apart should NOT match with 7-day tolerance");
    }

    @Test
    void exactAmount_bankIsBeforeLedger_stillMatchesWithinTolerance() {
        LocalDate base = LocalDate.of(2024, 3, 10);
        // bank date 5 days before ledger
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("300.00"), base)),
            List.of(tx(new BigDecimal("300.00"), base.minusDays(5)))
        );
        assertEquals(1, results.size(), "Negative offset within tolerance should still match");
    }

    // ── amount mismatch ───────────────────────────────────────────────────────

    @Test
    void differentAmounts_sameDate_doesNotMatch() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("100.00"), today)),
            List.of(tx(new BigDecimal("100.01"), today))
        );
        assertTrue(results.isEmpty(), "Amounts differing by 1 cent should not match");
    }

    @Test
    void largeAmountDifference_doesNotMatch() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("5000.00"), today)),
            List.of(tx(new BigDecimal("4999.99"), today))
        );
        assertTrue(results.isEmpty());
    }

    // ── multiple transactions ─────────────────────────────────────────────────

    @Test
    void multipleMatches_correctCountReturned() {
        LocalDate d1 = LocalDate.of(2024, 1, 1);
        LocalDate d2 = LocalDate.of(2024, 2, 1);
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(
                tx(new BigDecimal("100.00"), d1),
                tx(new BigDecimal("200.00"), d2)
            ),
            List.of(
                tx(new BigDecimal("100.00"), d1),
                tx(new BigDecimal("200.00"), d2)
            )
        );
        assertEquals(2, results.size());
    }

    @Test
    void oneLedgerMatchesMultipleBank_producesMultipleHypotheses() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("500.00"), today)),
            List.of(
                tx(new BigDecimal("500.00"), today),
                tx(new BigDecimal("500.00"), today.plusDays(1))
            )
        );
        assertEquals(2, results.size(), "One ledger matching two bank entries should produce 2 hypotheses");
    }

    @Test
    void noMatches_emptyResultReturned() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("999.00"), today)),
            List.of(tx(new BigDecimal("888.00"), today))
        );
        assertTrue(results.isEmpty());
    }

    // ── empty / null inputs ───────────────────────────────────────────────────

    @Test
    void emptyLedger_returnsEmptyList() {
        List<MatchHypothesis> results = engine.generateHypotheses(
            Collections.emptyList(),
            List.of(tx(new BigDecimal("100.00"), LocalDate.now()))
        );
        assertTrue(results.isEmpty());
    }

    @Test
    void emptyBank_returnsEmptyList() {
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("100.00"), LocalDate.now())),
            Collections.emptyList()
        );
        assertTrue(results.isEmpty());
    }

    @Test
    void bothEmpty_returnsEmptyList() {
        List<MatchHypothesis> results = engine.generateHypotheses(
            Collections.emptyList(),
            Collections.emptyList()
        );
        assertTrue(results.isEmpty());
    }

    // ── hypothesis fields are correctly set ───────────────────────────────────

    @Test
    void hypothesis_ledgerAndBankTransactions_areCorrectlyLinked() {
        LocalDate today = LocalDate.now();
        StandardizedTransaction ledger = tx(new BigDecimal("300.00"), today, TransactionType.DEBIT);
        StandardizedTransaction bank   = tx(new BigDecimal("300.00"), today, TransactionType.CREDIT);
        List<MatchHypothesis> results = engine.generateHypotheses(List.of(ledger), List.of(bank));
        assertEquals(1, results.size());
        assertSame(ledger, results.get(0).getLedgerTransaction());
        assertSame(bank,   results.get(0).getBankTransaction());
    }

    @Test
    void hypothesis_hasNonNullId() {
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("100.00"), today)),
            List.of(tx(new BigDecimal("100.00"), today))
        );
        assertNotNull(results.get(0).getHypothesisId());
    }

    // ── BigDecimal scale sensitivity ──────────────────────────────────────────

    @Test
    void amounts_withDifferentScale_matchCorrectly() {
        // BigDecimal("100") vs BigDecimal("100.00") — compareTo should still be 0
        LocalDate today = LocalDate.now();
        List<MatchHypothesis> results = engine.generateHypotheses(
            List.of(tx(new BigDecimal("100"), today)),
            List.of(tx(new BigDecimal("100.00"), today))
        );
        assertEquals(1, results.size(), "BigDecimal compareTo should treat 100 == 100.00");
    }

    // ── large dataset performance sanity ─────────────────────────────────────

    @Test
    void largeDatasets_complete_inReasonableTime() {
        LocalDate base = LocalDate.of(2023, 1, 1);
        List<StandardizedTransaction> ledger = new java.util.ArrayList<>();
        List<StandardizedTransaction> bank   = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ledger.add(tx(BigDecimal.valueOf(i + 1), base.plusDays(i % 30)));
        }
        for (int i = 0; i < 100; i++) {
            bank.add(tx(BigDecimal.valueOf(i + 1), base.plusDays(i % 30)));
        }
        long start = System.currentTimeMillis();
        List<MatchHypothesis> results = engine.generateHypotheses(ledger, bank);
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(results.isEmpty());
        assertTrue(elapsed < 3000, "100x100 matching should complete in < 3s, took " + elapsed + "ms");
    }
}
