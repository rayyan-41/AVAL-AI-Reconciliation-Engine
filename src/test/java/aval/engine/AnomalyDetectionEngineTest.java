package aval.engine;

import aval.common.enums.TransactionType;
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
        List<String> anomalies = engine.identifyAnomalies(
            Collections.emptyList(), Collections.emptyList()
        );
        assertTrue(anomalies.isEmpty());
    }

    @Test
    void singleTransaction_noAnomalies_returnsEmpty() {
        List<String> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), nextMonday())),
            Collections.emptyList()
        );
        assertTrue(anomalies.isEmpty(), "Single normal transaction should have no anomalies");
    }

    // ── duplicate detection ────────────────────────────────────────────────────

    @Test
    void duplicateLedgerEntries_sameAmountSameDate_flagged() {
        LocalDate date = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("500.00"), date),
                tx(new BigDecimal("500.00"), date)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.startsWith("DUPLICATE:")),
            "Duplicate ledger entries should be flagged");
    }

    @Test
    void duplicateBankEntries_sameAmountSameDate_flagged() {
        LocalDate date = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            Collections.emptyList(),
            List.of(
                tx(new BigDecimal("250.00"), date),
                tx(new BigDecimal("250.00"), date)
            )
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.startsWith("DUPLICATE:")));
    }

    @Test
    void duplicates_differentAmounts_notFlagged() {
        LocalDate date = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("200.00"), date)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().noneMatch(a -> a.startsWith("DUPLICATE:")));
    }

    @Test
    void duplicates_sameAmount_differentDates_notFlagged() {
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("300.00"), nextMonday()),
                tx(new BigDecimal("300.00"), nextMonday().plusDays(1))
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().noneMatch(a -> a.startsWith("DUPLICATE:")));
    }

    @Test
    void duplicateMentionsSourceName_Ledger() {
        LocalDate date = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.contains("Ledger")));
    }

    @Test
    void duplicateMentionsSourceName_Bank() {
        LocalDate date = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            Collections.emptyList(),
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            )
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.contains("Bank")));
    }

    @Test
    void threeDuplicates_twoAnomaliesReported() {
        // 3 identical entries → pairs (0,1), (0,2), (1,2) = 3 duplicate pairs
        LocalDate date = nextMonday();
        StandardizedTransaction t = tx(new BigDecimal("100.00"), date);
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date)
            ),
            Collections.emptyList()
        );
        long dupCount = anomalies.stream().filter(a -> a.startsWith("DUPLICATE:")).count();
        assertEquals(3, dupCount, "3 identical entries → 3 duplicate pair alerts (0,1), (0,2), (1,2)");
    }

    // ── outlier detection ─────────────────────────────────────────────────────

    @Test
    void extremeOutlier_beyondThreeSigma_flagged() {
        LocalDate date = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), date),
                tx(new BigDecimal("100.00"), date.plusDays(1)),
                tx(new BigDecimal("100.00"), date.plusDays(2)),
                tx(new BigDecimal("100.00"), date.plusDays(3)),
                tx(new BigDecimal("1000000.00"), date.plusDays(4))   // massive outlier
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.startsWith("OUTLIER:")),
            "1,000,000 surrounded by 100s should be flagged as OUTLIER");
    }

    @Test
    void uniformAmounts_noOutliers() {
        LocalDate base = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), base),
                tx(new BigDecimal("100.00"), base.plusDays(1)),
                tx(new BigDecimal("100.00"), base.plusDays(2))
            ),
            Collections.emptyList()
        );
        // stdDev = 0 → threshold condition never triggers
        assertTrue(anomalies.stream().noneMatch(a -> a.startsWith("OUTLIER:")));
    }

    @Test
    void outlier_includedInBothSources() {
        LocalDate date = nextMonday();
        // Use both ledger and bank to build the combined set
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("50.00"), date),
                tx(new BigDecimal("50.00"), date.plusDays(1))
            ),
            List.of(
                tx(new BigDecimal("50.00"), date.plusDays(2)),
                tx(new BigDecimal("999999.00"), date.plusDays(3))   // outlier in bank
            )
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.startsWith("OUTLIER:")));
    }

    // ── weekend / Sunday detection ────────────────────────────────────────────

    @Test
    void transactionOnSunday_flaggedAsWeekend() {
        LocalDate sunday = nextSunday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), sunday)),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.startsWith("WEEKEND:")),
            "Sunday transaction should be flagged as WEEKEND");
    }

    @Test
    void transactionOnWeekday_notFlaggedAsWeekend() {
        LocalDate monday = nextMonday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("100.00"), monday)),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().noneMatch(a -> a.startsWith("WEEKEND:")));
    }

    @Test
    void sundayTransaction_flagMessageContainsDate() {
        LocalDate sunday = nextSunday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(tx(new BigDecimal("500.00"), sunday)),
            Collections.emptyList()
        );
        String weekendFlag = anomalies.stream()
            .filter(a -> a.startsWith("WEEKEND:"))
            .findFirst()
            .orElse("");
        assertTrue(weekendFlag.contains(sunday.toString()),
            "Weekend flag should contain the date");
    }

    @Test
    void multipleSundayTransactions_multipleFlagsGenerated() {
        LocalDate sunday1 = nextSunday();
        LocalDate sunday2 = sunday1.plusWeeks(1);
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), sunday1),
                tx(new BigDecimal("200.00"), sunday2)
            ),
            Collections.emptyList()
        );
        long weekendCount = anomalies.stream().filter(a -> a.startsWith("WEEKEND:")).count();
        assertEquals(2, weekendCount);
    }

    // ── combined anomalies ────────────────────────────────────────────────────

    @Test
    void duplicateAndSunday_bothFlagged() {
        LocalDate sunday = nextSunday();
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("100.00"), sunday),
                tx(new BigDecimal("100.00"), sunday)
            ),
            Collections.emptyList()
        );
        assertTrue(anomalies.stream().anyMatch(a -> a.startsWith("DUPLICATE:")));
        assertTrue(anomalies.stream().anyMatch(a -> a.startsWith("WEEKEND:")));
    }

    @Test
    void anomalyMessages_containAmountInfo() {
        LocalDate date = nextMonday();
        // Duplicate test - message should contain the amount
        List<String> anomalies = engine.identifyAnomalies(
            List.of(
                tx(new BigDecimal("12345.67"), date),
                tx(new BigDecimal("12345.67"), date)
            ),
            Collections.emptyList()
        );
        String dup = anomalies.stream()
            .filter(a -> a.startsWith("DUPLICATE:"))
            .findFirst().orElse("");
        assertTrue(dup.contains("12345.67"), "Duplicate message should mention the amount");
    }
}
