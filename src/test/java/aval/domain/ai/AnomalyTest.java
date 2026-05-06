package aval.domain.ai;

import org.junit.jupiter.api.Test;

import aval.common.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Anomaly domain object (Phase 1 — UC10 typed anomaly).
 */
public class AnomalyTest {

    private StandardizedTransaction makeTx(BigDecimal amount) {
        return new StandardizedTransaction(
            UUID.randomUUID(), LocalDate.of(2024, 6, 15), amount,
            "Test narrative", TransactionType.DEBIT, UUID.randomUUID()
        );
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void constructor_assignsNonNullId() {
        Anomaly a = new Anomaly(Anomaly.Category.DUPLICATE, "desc", null, null);
        assertNotNull(a.getAnomalyId());
    }

    @Test
    void constructor_assignsCategory() {
        Anomaly a = new Anomaly(Anomaly.Category.OUTLIER, "big number", null, null);
        assertEquals(Anomaly.Category.OUTLIER, a.getCategory());
    }

    @Test
    void constructor_assignsDescription() {
        Anomaly a = new Anomaly(Anomaly.Category.WEEKEND_POSTING, "sunday tx", null, null);
        assertEquals("sunday tx", a.getDescription());
    }

    @Test
    void constructor_assignsPrimaryTransaction() {
        StandardizedTransaction tx = makeTx(new BigDecimal("500.00"));
        Anomaly a = new Anomaly(Anomaly.Category.OUTLIER, "desc", tx, null);
        assertSame(tx, a.getPrimaryTransaction());
    }

    @Test
    void constructor_assignsSecondaryTransaction() {
        StandardizedTransaction t1 = makeTx(new BigDecimal("100.00"));
        StandardizedTransaction t2 = makeTx(new BigDecimal("100.00"));
        Anomaly a = new Anomaly(Anomaly.Category.DUPLICATE, "dup", t1, t2);
        assertSame(t1, a.getPrimaryTransaction());
        assertSame(t2, a.getSecondaryTransaction());
    }

    @Test
    void constructor_secondaryCanBeNull() {
        Anomaly a = new Anomaly(Anomaly.Category.OUTLIER, "desc", makeTx(new BigDecimal("1")), null);
        assertNull(a.getSecondaryTransaction());
    }

    // ── Unique IDs ────────────────────────────────────────────────────────────

    @Test
    void twoAnomalies_haveDifferentIds() {
        Anomaly a1 = new Anomaly(Anomaly.Category.DUPLICATE, "dup1", null, null);
        Anomaly a2 = new Anomaly(Anomaly.Category.DUPLICATE, "dup2", null, null);
        assertNotEquals(a1.getAnomalyId(), a2.getAnomalyId());
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_containsCategoryName() {
        Anomaly a = new Anomaly(Anomaly.Category.CONSOLIDATION_VARIANCE, "variance", null, null);
        assertTrue(a.toString().contains("CONSOLIDATION_VARIANCE"));
    }

    @Test
    void toString_containsDescription() {
        Anomaly a = new Anomaly(Anomaly.Category.OUTLIER, "Amount 9999 exceeds", null, null);
        assertTrue(a.toString().contains("Amount 9999 exceeds"));
    }

    @Test
    void toString_format_bracketedCategory() {
        Anomaly a = new Anomaly(Anomaly.Category.DUPLICATE, "test", null, null);
        assertTrue(a.toString().startsWith("[DUPLICATE]"));
    }

    // ── Category enum ─────────────────────────────────────────────────────────

    @Test
    void categoryEnum_hasFourValues() {
        assertEquals(4, Anomaly.Category.values().length);
    }

    @Test
    void categoryEnum_containsExpectedValues() {
        assertNotNull(Anomaly.Category.valueOf("DUPLICATE"));
        assertNotNull(Anomaly.Category.valueOf("OUTLIER"));
        assertNotNull(Anomaly.Category.valueOf("WEEKEND_POSTING"));
        assertNotNull(Anomaly.Category.valueOf("CONSOLIDATION_VARIANCE"));
    }
}
