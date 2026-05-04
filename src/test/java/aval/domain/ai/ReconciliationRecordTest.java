package aval.domain.ai;

import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ReconciliationRecord — immutability and audit trail.
 */
public class ReconciliationRecordTest {

    private StandardizedTransaction tx(BigDecimal amount) {
        return new StandardizedTransaction(
            UUID.randomUUID(), LocalDate.now(), amount, "Test", TransactionType.DEBIT, UUID.randomUUID()
        );
    }

    private SystemUser auditor() {
        return new SystemUser(
            UUID.randomUUID(), "Auditor Name", "12345-6789012-3", "auditor",
            UserRole.AUDITOR, "Karachi"
        );
    }

    private MatchHypothesis hypothesis(MatchType type) {
        MatchHypothesis h = new MatchHypothesis(
            tx(new BigDecimal("500.00")),
            tx(new BigDecimal("500.00")),
            0.95, type
        );
        return h;
    }

    // ── construction ──────────────────────────────────────────────────────────

    @Test
    void newRecord_hasNonNullId() {
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), auditor());
        assertNotNull(rec.getRecordId());
    }

    @Test
    void newRecord_twoInstances_haveDifferentIds() {
        ReconciliationRecord r1 = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), auditor());
        ReconciliationRecord r2 = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), auditor());
        assertNotEquals(r1.getRecordId(), r2.getRecordId());
    }

    @Test
    void newRecord_hypothesisIsLinked() {
        MatchHypothesis h = hypothesis(MatchType.EXACT_RULE);
        ReconciliationRecord rec = new ReconciliationRecord(h, auditor());
        assertSame(h, rec.getHypothesis());
    }

    @Test
    void newRecord_confirmingUserIsLinked() {
        SystemUser user = auditor();
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), user);
        assertSame(user, rec.getConfirmingUser());
    }

    @Test
    void newRecord_reconciledAt_isNotNull() {
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), auditor());
        assertNotNull(rec.getReconciledAt());
    }

    @Test
    void newRecord_reconciledAt_isReasonablyRecent() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(2);
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), auditor());
        LocalDateTime after = LocalDateTime.now().plusSeconds(2);
        assertTrue(rec.getReconciledAt().isAfter(before));
        assertTrue(rec.getReconciledAt().isBefore(after));
    }

    // ── isManualOverride ──────────────────────────────────────────────────────

    @Test
    void isManualOverride_forceOverrideHypothesis_returnsTrue() {
        MatchHypothesis h = hypothesis(MatchType.FORCE_OVERRIDE);
        ReconciliationRecord rec = new ReconciliationRecord(h, auditor());
        assertTrue(rec.isManualOverride());
    }

    @Test
    void isManualOverride_exactRuleHypothesis_returnsFalse() {
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), auditor());
        assertFalse(rec.isManualOverride());
    }

    @Test
    void isManualOverride_aiProbabilisticHypothesis_returnsFalse() {
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.AI_PROBABILISTIC), auditor());
        assertFalse(rec.isManualOverride());
    }

    @Test
    void isManualOverride_nullHypothesis_returnsFalse() {
        ReconciliationRecord rec = new ReconciliationRecord(null, auditor());
        assertFalse(rec.isManualOverride());
    }

    // ── null inputs ───────────────────────────────────────────────────────────

    @Test
    void nullUser_doesNotThrow() {
        assertDoesNotThrow(() -> new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), null));
    }

    @Test
    void nullHypothesis_doesNotThrow() {
        assertDoesNotThrow(() -> new ReconciliationRecord(null, auditor()));
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_containsShortId() {
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), auditor());
        // toString should contain at least the first 8 chars of the UUID
        assertTrue(rec.toString().contains(rec.getRecordId().toString().substring(0, 8)));
    }

    @Test
    void toString_withNullHypothesis_showsNullHypothesisText() {
        ReconciliationRecord rec = new ReconciliationRecord(null, auditor());
        assertTrue(rec.toString().contains("NULL_HYPOTHESIS"));
    }

    @Test
    void toString_withNullUser_showsSystem() {
        ReconciliationRecord rec = new ReconciliationRecord(hypothesis(MatchType.EXACT_RULE), null);
        assertTrue(rec.toString().contains("SYSTEM"));
    }
}
