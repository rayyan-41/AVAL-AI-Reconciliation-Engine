package aval.service;

import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ReportService — CSV generation (UC11) and summary (UC12).
 */
public class ReportServiceTest {

    private ReportService reportService;
    private SystemUser auditor;

    @BeforeEach
    void setUp() {
        reportService = new ReportService();
        auditor = new SystemUser(
            UUID.randomUUID(), "Auditor", "12345-6789012-3", "auditor",
            UserRole.AUDITOR, "Lahore"
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private StandardizedTransaction ledgerTx(BigDecimal amount) {
        return new StandardizedTransaction(UUID.randomUUID(), LocalDate.of(2024, 1, 15), amount,
            "Ledger entry", TransactionType.DEBIT, UUID.randomUUID());
    }

    private StandardizedTransaction bankTx(BigDecimal amount) {
        return new StandardizedTransaction(UUID.randomUUID(), LocalDate.of(2024, 1, 16), amount,
            "Bank credit", TransactionType.CREDIT, UUID.randomUUID());
    }

    private ReconciliationRecord reconciledRecord(BigDecimal amount) {
        MatchHypothesis hyp = new MatchHypothesis(ledgerTx(amount), bankTx(amount), 1.0, MatchType.EXACT_RULE);
        hyp.setJustification("Auto-reconciled");
        return new ReconciliationRecord(hyp, auditor);
    }

    // ── generateReconciliationReport — happy path ──────────────────────────────

    @Test
    void generateReport_createsFile(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("report/output.csv");
        reportService.generateReconciliationReport(
            List.of(reconciledRecord(new BigDecimal("500.00"))),
            Collections.emptyList(),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        assertTrue(Files.exists(out), "Report file should be created");
    }

    @Test
    void generateReport_fileIsNonEmpty(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("reports/jan2024.csv");
        reportService.generateReconciliationReport(
            List.of(reconciledRecord(new BigDecimal("100.00"))),
            Collections.emptyList(),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        assertTrue(Files.size(out) > 0, "Report file should not be empty");
    }

    @Test
    void generateReport_containsReconciledStatus(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("report/r.csv");
        reportService.generateReconciliationReport(
            List.of(reconciledRecord(new BigDecimal("250.00"))),
            Collections.emptyList(),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        String content = Files.readString(out);
        assertTrue(content.contains("RECONCILED"));
    }

    @Test
    void generateReport_containsLedgerAmount(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("report/r.csv");
        reportService.generateReconciliationReport(
            List.of(reconciledRecord(new BigDecimal("750.00"))),
            Collections.emptyList(),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        String content = Files.readString(out);
        assertTrue(content.contains("750.00"));
    }

    @Test
    void generateReport_withUnmatchedLedger_containsUnmatchedLedgerStatus(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("report/unmatched.csv");
        reportService.generateReconciliationReport(
            Collections.emptyList(),
            List.of(ledgerTx(new BigDecimal("300.00"))),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        String content = Files.readString(out);
        assertTrue(content.contains("UNMATCHED_LEDGER"));
    }

    @Test
    void generateReport_withUnmatchedBank_containsUnmatchedBankStatus(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("report/unmatched.csv");
        reportService.generateReconciliationReport(
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(bankTx(new BigDecimal("400.00"))),
            null, null,
            out.toString()
        );
        String content = Files.readString(out);
        assertTrue(content.contains("UNMATCHED_BANK"));
    }

    @Test
    void generateReport_hasHeaderRow(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("report/header.csv");
        reportService.generateReconciliationReport(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        String content = Files.readString(out);
        assertTrue(content.contains("Status"), "CSV should have Status header");
        assertTrue(content.contains("Ledger Date"), "CSV should have Ledger Date header");
        assertTrue(content.contains("Match Type"), "CSV should have Match Type header");
    }

    @Test
    void generateReport_createsParentDirectories(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("deep/nested/path/report.csv");
        reportService.generateReconciliationReport(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        assertTrue(Files.exists(out.getParent()), "Parent directories should be created");
    }

    @Test
    void generateReport_multipleReconciledRecords_allInFile(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("report/multi.csv");
        reportService.generateReconciliationReport(
            List.of(
                reconciledRecord(new BigDecimal("100.00")),
                reconciledRecord(new BigDecimal("200.00")),
                reconciledRecord(new BigDecimal("300.00"))
            ),
            Collections.emptyList(),
            Collections.emptyList(),
            null, null,
            out.toString()
        );
        String content = Files.readString(out);
        long reconciledLines = content.lines()
            .filter(l -> l.startsWith("RECONCILED"))
            .count();
        assertEquals(3, reconciledLines);
    }

    // ── UnresolvedItemsException ───────────────────────────────────────────────

    @Test
    void generateReport_withPendingHypotheses_throwsUnresolvedItemsException(@TempDir Path tempDir) {
        MatchHypothesis pending = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00")), bankTx(new BigDecimal("100.00")),
            0.8, MatchType.AI_PROBABILISTIC
        );
        Path out = tempDir.resolve("report/blocked.csv");
        assertThrows(ReportService.UnresolvedItemsException.class, () ->
            reportService.generateReconciliationReport(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(pending), null,
                out.toString()
            )
        );
    }

    @Test
    void generateReport_withUnresolvedAnomalies_throwsUnresolvedItemsException(@TempDir Path tempDir) {
        Path out = tempDir.resolve("report/blocked2.csv");
        assertThrows(ReportService.UnresolvedItemsException.class, () ->
            reportService.generateReconciliationReport(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null, List.of("OUTLIER: suspicious transaction"),
                out.toString()
            )
        );
    }

    @Test
    void generateReport_nullPendingAndAnomalies_succeeds(@TempDir Path tempDir) {
        Path out = tempDir.resolve("report/ok.csv");
        assertDoesNotThrow(() ->
            reportService.generateReconciliationReport(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null, null,
                out.toString()
            )
        );
    }

    @Test
    void generateReport_emptyPendingAndAnomalies_succeeds(@TempDir Path tempDir) {
        Path out = tempDir.resolve("report/ok2.csv");
        assertDoesNotThrow(() ->
            reportService.generateReconciliationReport(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                out.toString()
            )
        );
    }

    @Test
    void unresolvedItemsException_messageIsDescriptive() {
        try {
            reportService.generateReconciliationReport(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of(new MatchHypothesis(ledgerTx(new BigDecimal("1")), bankTx(new BigDecimal("1")), 0.7, MatchType.AI_PROBABILISTIC)),
                null, "/tmp/report.csv"
            );
            fail("Should have thrown");
        } catch (ReportService.UnresolvedItemsException e) {
            assertNotNull(e.getMessage());
            assertFalse(e.getMessage().isBlank());
        } catch (IOException e) {
            fail("Wrong exception type: " + e);
        }
    }

    // ── generateSummary ────────────────────────────────────────────────────────

    @Test
    void generateSummary_containsReconciledCount() {
        String summary = reportService.generateSummary(
            List.of(reconciledRecord(new BigDecimal("100.00"))),
            Collections.emptyList(),
            Collections.emptyList()
        );
        assertTrue(summary.contains("1"), "Summary should mention reconciled count");
        assertTrue(summary.contains("Reconciled") || summary.contains("reconciled"));
    }

    @Test
    void generateSummary_containsUnmatchedLedgerCount() {
        String summary = reportService.generateSummary(
            Collections.emptyList(),
            List.of(ledgerTx(new BigDecimal("200.00")), ledgerTx(new BigDecimal("300.00"))),
            Collections.emptyList()
        );
        assertTrue(summary.contains("2"), "Summary should mention unmatched ledger count");
    }

    @Test
    void generateSummary_containsUnmatchedBankCount() {
        String summary = reportService.generateSummary(
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(bankTx(new BigDecimal("150.00")))
        );
        assertTrue(summary.contains("1"), "Summary should mention unmatched bank count");
    }

    @Test
    void generateSummary_allEmpty_returnsNonEmptyString() {
        String summary = reportService.generateSummary(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
        assertNotNull(summary);
        assertFalse(summary.isBlank());
    }

    @Test
    void generateSummary_containsReportHeader() {
        String summary = reportService.generateSummary(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
        assertTrue(summary.contains("RECONCILIATION") || summary.contains("Summary") || summary.contains("SUMMARY"));
    }
}
