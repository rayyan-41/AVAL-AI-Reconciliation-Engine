package aval.domain.ai;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for MatchHypothesis lifecycle and state transitions.
 */
public class MatchHypothesisTest {

    private StandardizedTransaction ledgerTx() {
        return new StandardizedTransaction(
            UUID.randomUUID(), LocalDate.now(),
            new BigDecimal("500.00"), "Ledger payment",
            TransactionType.DEBIT, UUID.randomUUID()
        );
    }

    private StandardizedTransaction bankTx() {
        return new StandardizedTransaction(
            UUID.randomUUID(), LocalDate.now(),
            new BigDecimal("500.00"), "Bank credit",
            TransactionType.CREDIT, UUID.randomUUID()
        );
    }

    // ── construction ──────────────────────────────────────────────────────────

    @Test
    void newHypothesis_hasNonNullId() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.95, MatchType.EXACT_RULE);
        assertNotNull(h.getHypothesisId());
    }

    @Test
    void newHypothesis_defaultStatus_isPendingReview() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.95, MatchType.EXACT_RULE);
        assertEquals(HypothesisStatus.PENDING_REVIEW, h.getStatus());
    }

    @Test
    void newHypothesis_confidenceScore_storedCorrectly() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.87, MatchType.AI_PROBABILISTIC);
        assertEquals(0.87, h.getConfidenceScore(), 0.0001);
    }

    @Test
    void newHypothesis_matchType_storedCorrectly() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 1.0, MatchType.FORCE_OVERRIDE);
        assertEquals(MatchType.FORCE_OVERRIDE, h.getMatchType());
    }

    @Test
    void newHypothesis_transactions_storedCorrectly() {
        StandardizedTransaction l = ledgerTx();
        StandardizedTransaction b = bankTx();
        MatchHypothesis h = new MatchHypothesis(l, b, 1.0, MatchType.EXACT_RULE);
        assertSame(l, h.getLedgerTransaction());
        assertSame(b, h.getBankTransaction());
    }

    @Test
    void newHypothesis_twoInstances_haveDifferentIds() {
        MatchHypothesis h1 = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        MatchHypothesis h2 = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        assertNotEquals(h1.getHypothesisId(), h2.getHypothesisId());
    }

    @Test
    void newHypothesis_justification_isNullByDefault() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        assertNull(h.getJustification());
    }

    // ── state transitions ─────────────────────────────────────────────────────

    @Test
    void setStatus_toApproved_updatesStatus() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        h.setStatus(HypothesisStatus.APPROVED);
        assertEquals(HypothesisStatus.APPROVED, h.getStatus());
    }

    @Test
    void setStatus_toRejected_updatesStatus() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        h.setStatus(HypothesisStatus.REJECTED);
        assertEquals(HypothesisStatus.REJECTED, h.getStatus());
    }

    @Test
    void setStatus_toAutoReconciled_updatesStatus() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.97, MatchType.EXACT_RULE);
        h.setStatus(HypothesisStatus.AUTO_RECONCILED);
        assertEquals(HypothesisStatus.AUTO_RECONCILED, h.getStatus());
    }

    @Test
    void setJustification_storesText() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        h.setJustification("Confirmed by auditor");
        assertEquals("Confirmed by auditor", h.getJustification());
    }

    @Test
    void setJustification_canBeOverwritten() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        h.setJustification("First");
        h.setJustification("Second");
        assertEquals("Second", h.getJustification());
    }

    // ── null transactions ─────────────────────────────────────────────────────

    @Test
    void nullLedgerTransaction_doesNotThrow() {
        assertDoesNotThrow(() -> new MatchHypothesis(null, bankTx(), 0.5, MatchType.AI_PROBABILISTIC));
    }

    @Test
    void nullBankTransaction_doesNotThrow() {
        assertDoesNotThrow(() -> new MatchHypothesis(ledgerTx(), null, 0.5, MatchType.AI_PROBABILISTIC));
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_containsConfidenceScore() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.87, MatchType.EXACT_RULE);
        assertTrue(h.toString().contains("0.87"));
    }

    @Test
    void toString_containsMatchType() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.87, MatchType.AI_PROBABILISTIC);
        assertTrue(h.toString().contains("AI_PROBABILISTIC"));
    }

    @Test
    void toString_withNullLedger_doesNotThrow() {
        MatchHypothesis h = new MatchHypothesis(null, bankTx(), 0.5, MatchType.AI_PROBABILISTIC);
        assertDoesNotThrow(h::toString);
        assertTrue(h.toString().contains("N/A"));
    }

    // ── all MatchType values ──────────────────────────────────────────────────

    @Test
    void allMatchTypes_canBeSet() {
        for (MatchType type : MatchType.values()) {
            MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, type);
            assertEquals(type, h.getMatchType());
        }
    }

    // ── all HypothesisStatus values ────────────────────────────────────────────

    @Test
    void allHypothesisStatuses_canBeSet() {
        MatchHypothesis h = new MatchHypothesis(ledgerTx(), bankTx(), 0.9, MatchType.EXACT_RULE);
        for (HypothesisStatus status : HypothesisStatus.values()) {
            h.setStatus(status);
            assertEquals(status, h.getStatus());
        }
    }
}
