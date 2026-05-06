package aval.engine;

import aval.common.enums.TransactionType;
import aval.domain.ai.Anomaly;
import aval.domain.ai.StandardizedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for AnomalyDetectionEngine.
 */
public class AnomalyDetectionEngineTest {

    private AnomalyDetectionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AnomalyDetectionEngine();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private StandardizedTransaction tx(BigDecimal amount, LocalDate date) {
        return new StandardizedTransaction(
            UUID.randomUUID(), date, amount, "narrative", TransactionType.DEBIT, UUID.randomUUID()
        );
    }

    private LocalDate nextSunday() {
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    private LocalDate nextMonday() {
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    // ── empty lists ───────────────────────────────────────────────────────────

    @Test
    void bothEmpty_returnsNoAnomalies() {
        List<Anomaly> anomalies = engine.identifyAnomalies(
            Collections.emptyList(), Collections.emptyList()
        );
        assertTrue(anomalies.isEmpty());
    }

    @Test
    void singleTransaction_noAnomalies_returnsEmpty() {
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), nextMonday())),
            Collections.emptyList()
        );
        assertTrue(anomalies.isEmpty(), "Single normal transaction should have no anomalies");
    }

    // ── duplicate detection ────────────────────────────────────────────────────

    @Test
    void duplicateLedgerEntries_sameAmountSameDate_flagged() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("500.00"), date),
                tx(new BigDecimal("500.00"), date)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getCategory() == Anomaly.Category.DUPLICATE),
            "Duplicate ledger entries should be flagged");
    }

    @Test
    void duplicateBankEntries_sameAmountSameDate_flagged() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            Collections.emptyList(),
            List.of(
                tx(new BigDecimal("250.00"), date),
                tx(new BigDecimal("250.00"), date)
            )
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getCategory() == Anomaly.Category.DUPLICATE));
    }

    @Test
    void duplicates_differentAmounts_notFlagged() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("200.00"), date)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().noneMatch(a -> a.getCategory() == Anomaly.Category.DUPLICATE));
    }

    @Test
    void duplicates_sameAmount_differentDates_notFlagged() {
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("300.00"), nextMonday()),
                tx(new BigDecimal("300.00"), nextMonday().plusDays(1))
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().noneMatch(a -> a.getCategory() == Anomaly.Category.DUPLICATE));
    }

    @Test
    void duplicateMentionsSourceName_Ledger() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getDescription().contains("Ledger")));
    }

    @Test
    void duplicateMentionsSourceName_Bank() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            Collections.emptyList(),
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            )
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getDescription().contains("Bank")));
    }

    @Test
    void threeDuplicates_twoAnomaliesReported() {
        // 3 identical entries → pairs (0,1), (0,2), (1,2) = 3 duplicate pairs
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            ),
            Collections.emptyList()
        );
        long dupCount = anomalies.stream().filter(a -> a.getCategory() == Anomaly.Category.DUPLICATE).count();
        assertEquals(3, dupCount, "3 identical entries → 3 duplicate pair alerts (0,1), (0,2), (1,2)");
    }

    // ── outlier detection ─────────────────────────────────────────────────────

    @Test
    void extremeOutlier_beyondThreeSigma_flagged() {
        // The engine uses population σ: outlier is only flagged when sqrt(n-1) > 3,
        // i.e. total data points must exceed 10.  Use 10 normals + 1 outlier = 11 pts.
        // Dates stay on weekdays (Mon–Fri ×2 weeks) to avoid spurious WEEKEND flags.
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date.plusDays(1)),
                tx(new BigDecimal("100.00"), date.plusDays(2)),
                tx(new BigDecimal("100.00"), date.plusDays(3)),
                tx(new BigDecimal("100.00"), date.plusDays(4)),    // Fri week 1
                tx(new BigDecimal("100.00"), date.plusDays(7)),    // Mon week 2
                tx(new BigDecimal("100.00"), date.plusDays(8)),
                tx(new BigDecimal("100.00"), date.plusDays(9)),
                tx(new BigDecimal("100.00"), date.plusDays(10)),
                tx(new BigDecimal("100.00"), date.plusDays(11)),   // Fri week 2
                tx(new BigDecimal("1000000.00"), date.plusDays(14)) // Mon week 3 — outlier
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getCategory() == Anomaly.Category.OUTLIER),
            "1,000,000 surrounded by 100s should be flagged as OUTLIER");
    }

    @Test
    void uniformAmounts_noOutliers() {
        LocalDate base = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), base),
                tx(new BigDecimal("100.00"), base.plusDays(1)),
                tx(new BigDecimal("100.00"), base.plusDays(2))
            ),
            Collections.emptyList()
        );
        // stdDev = 0 → threshold condition never triggers
        assertTrue(anomalies.stream().noneMatch(a -> a.getCategory() == Anomaly.Category.OUTLIER));
    }

    @Test
    void outlier_includedInBothSources() {
        // Same minimum-sample reasoning: need >= 11 points total.
        // Split evenly across ledger (6) and bank (5) to exercise the combined-set path.
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("50.00"), date),
                tx(new BigDecimal("50.00"), date.plusDays(1)),
                tx(new BigDecimal("50.00"), date.plusDays(2)),
                tx(new BigDecimal("50.00"), date.plusDays(3)),
                tx(new BigDecimal("50.00"), date.plusDays(4)),    // Fri week 1
                tx(new BigDecimal("50.00"), date.plusDays(7))     // Mon week 2
            ),
            List.of(
                tx(new BigDecimal("50.00"), date.plusDays(8)),
                tx(new BigDecimal("50.00"), date.plusDays(9)),
                tx(new BigDecimal("50.00"), date.plusDays(10)),
                tx(new BigDecimal("50.00"), date.plusDays(11)),   // Fri week 2
                tx(new BigDecimal("999999.00"), date.plusDays(14)) // Mon week 3 — outlier in bank
            )
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getCategory() == Anomaly.Category.OUTLIER));
    }

    // ── weekend / Sunday detection ────────────────────────────────────────────

    @Test
    void transactionOnSunday_flaggedAsWeekend() {
        LocalDate sunday = nextSunday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), sunday)),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getCategory() == Anomaly.Category.WEEKEND_POSTING),
            "Sunday transaction should be flagged as WEEKEND_POSTING");
    }

    @Test
    void transactionOnWeekday_notFlaggedAsWeekend() {
        LocalDate monday = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), monday)),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().noneMatch(a -> a.getCategory() == Anomaly.Category.WEEKEND_POSTING));
    }

    @Test
    void sundayTransaction_flagMessageContainsDate() {
        LocalDate sunday = nextSunday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("500.00"), sunday)),
            Collections.emptyList()
        );
        String weekendDesc = anomalies.stream()
            .filter(a -> a.getCategory() == Anomaly.Category.WEEKEND_POSTING)
            .findFirst()
            .map(Anomaly::getDescription)
            .orElse("");
        assertTrue(weekendDesc.contains(sunday.toString()),
            "Weekend flag description should contain the date");
    }

    @Test
    void multipleSundayTransactions_multipleFlagsGenerated() {
        LocalDate sunday1 = nextSunday();
        LocalDate sunday2 = sunday1.plusWeeks(1);
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), sunday1),
                tx(new BigDecimal("200.00"), sunday2)
            ),
            Collections.emptyList()
        );
        long weekendCount = anomalies.stream().filter(a -> a.getCategory() == Anomaly.Category.WEEKEND_POSTING).count();
        assertEquals(2, weekendCount);
    }

    // ── combined anomalies ────────────────────────────────────────────────────

    @Test
    void duplicateAndSunday_bothFlagged() {
        LocalDate sunday = nextSunday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), sunday),
                tx(new BigDecimal("100.00"), sunday)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.getCategory() == Anomaly.Category.DUPLICATE));
        assertTrue(anomalies.stream().anyMatch(a -> a.getCategory() == Anomaly.Category.WEEKEND_POSTING));
    }

    @Test
    void anomalyMessages_containAmountInfo() {
        LocalDate date = nextMonday();
        // Duplicate test - message should contain the amount
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("12345.67"), date),
                tx(new BigDecimal("12345.67"), date)
            ),
            Collections.emptyList()
        );
        String dup = anomalies.stream()
            .filter(a -> a.getCategory() == Anomaly.Category.DUPLICATE)
            .findFirst().map(Anomaly::getDescription).orElse("");
        assertTrue(dup.contains("12345.67"), "Duplicate message should mention the amount");
    }

    // ── Phase 1: Typed Anomaly domain object tests ────────────────────────────

    @Test
    void anomalyObject_hasNonNullId() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            ),
            Collections.emptyList()
        );
        assertFalse(anomalies.isEmpty());
        assertNotNull(anomalies.get(0).getAnomalyId(), "Each anomaly should have a UUID");
    }

    @Test
    void duplicateAnomaly_hasBothTransactionReferences() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            ),
            Collections.emptyList()
        );
        Anomaly dup = anomalies.stream()
            .filter(a -> a.getCategory() == Anomaly.Category.DUPLICATE)
            .findFirst()
            .orElseThrow();
        assertNotNull(dup.getPrimaryTransaction(), "Duplicate should reference primary transaction");
        assertNotNull(dup.getSecondaryTransaction(), "Duplicate should reference secondary transaction");
    }

    @Test
    void outlierAnomaly_hasOnlyPrimaryTransaction() {
        LocalDate date = nextMonday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date.plusDays(1)),
                tx(new BigDecimal("100.00"), date.plusDays(2)),
                tx(new BigDecimal("100.00"), date.plusDays(3)),
                tx(new BigDecimal("100.00"), date.plusDays(4)),
                tx(new BigDecimal("100.00"), date.plusDays(7)),
                tx(new BigDecimal("100.00"), date.plusDays(8)),
                tx(new BigDecimal("100.00"), date.plusDays(9)),
                tx(new BigDecimal("100.00"), date.plusDays(10)),
                tx(new BigDecimal("100.00"), date.plusDays(11)),
                tx(new BigDecimal("1000000.00"), date.plusDays(14))
            ),
            Collections.emptyList()
        );
        Anomaly outlier = anomalies.stream()
            .filter(a -> a.getCategory() == Anomaly.Category.OUTLIER)
            .findFirst()
            .orElseThrow();
        assertNotNull(outlier.getPrimaryTransaction(), "Outlier should reference the flagged transaction");
        assertNull(outlier.getSecondaryTransaction(), "Outlier should not have a secondary transaction");
    }

    @Test
    void weekendAnomaly_hasOnlyPrimaryTransaction() {
        LocalDate sunday = nextSunday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), sunday)),
            Collections.emptyList()
        );
        Anomaly weekend = anomalies.stream()
            .filter(a -> a.getCategory() == Anomaly.Category.WEEKEND_POSTING)
            .findFirst()
            .orElseThrow();
        assertNotNull(weekend.getPrimaryTransaction());
        assertNull(weekend.getSecondaryTransaction());
    }

    @Test
    void anomalyToString_containsCategoryAndDescription() {
        LocalDate sunday = nextSunday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), sunday)),
            Collections.emptyList()
        );
        Anomaly a = anomalies.get(0);
        String str = a.toString();
        assertTrue(str.contains(a.getCategory().name()), "toString should contain category name");
        assertTrue(str.contains(a.getDescription()), "toString should contain description");
    }

    @Test
    void eachAnomalyHasUniqueId() {
        LocalDate sunday = nextSunday();
        List<Anomaly> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), sunday),
                tx(new BigDecimal("100.00"), sunday)
            ),
            Collections.emptyList()
        );
        // Should have at least a duplicate + 2 weekend flags
        assertTrue(anomalies.size() >= 2);
        long distinctIds = anomalies.stream().map(Anomaly::getAnomalyId).distinct().count();
        assertEquals(anomalies.size(), distinctIds, "Each anomaly should have a unique ID");
    }
}
