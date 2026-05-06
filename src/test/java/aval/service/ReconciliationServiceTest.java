package aval.service;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.MatchingConfig;
import aval.domain.core.ReconciliationWorkspace;
import aval.engine.MatchingEngine;
import aval.engine.RuleBasedMatchingEngine;
import aval.engine.VectorizationEngine;
import aval.persistence.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ReconciliationService — all use cases (UC6–UC9).
 */
@ExtendWith(MockitoExtension.class)
public class ReconciliationServiceTest {

    @Mock private VectorizationEngine mockVectorEngine;
    @Mock private MatchingEngine mockMatchingEngine;
    @Mock private DataStore mockDataStore;

    private ReconciliationService service;
    private ReconciliationWorkspace workspace;
    private SystemUser user;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(mockVectorEngine, mockMatchingEngine, mockDataStore);
        workspace = new ReconciliationWorkspace(
            UUID.randomUUID(),
            new ClientOrganization(UUID.randomUUID(), "Test Corp", ""),
            new MatchingConfig()
        );
        user = new SystemUser(UUID.randomUUID(), "Test User", "12345-6789012-3",
            "testuser", UserRole.ACCOUNTANT, "Karachi");
    }

    private StandardizedTransaction ledgerTx(BigDecimal amount, LocalDate date) {
        return new StandardizedTransaction(UUID.randomUUID(), date, amount, "Ledger", TransactionType.DEBIT, UUID.randomUUID());
    }

    private StandardizedTransaction bankTx(BigDecimal amount, LocalDate date) {
        return new StandardizedTransaction(UUID.randomUUID(), date, amount, "Bank", TransactionType.CREDIT, UUID.randomUUID());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UC6 — runMatching
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void runMatching_returnsHypothesesFromEngine() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.8, MatchType.EXACT_RULE
        );
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(hyp));
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        ReconciliationResult result = service.runMatching(workspace,
            List.of(ledgerTx(new BigDecimal("100.00"), LocalDate.now())),
            List.of(bankTx(new BigDecimal("100.00"), LocalDate.now()))
        );

        assertNotNull(result);
        assertEquals(1, result.getHypotheses().size());
    }

    @Test
    void runMatching_highConfidenceHypothesis_isAutoReconciled() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("500.00"), LocalDate.now()),
            bankTx(new BigDecimal("500.00"), LocalDate.now()),
            1.0, MatchType.EXACT_RULE
        );
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(hyp));
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        ReconciliationResult result = service.runMatching(workspace,
            List.of(ledgerTx(new BigDecimal("500.00"), LocalDate.now())),
            List.of(bankTx(new BigDecimal("500.00"), LocalDate.now()))
        );

        assertFalse(result.getAutoReconciledRecords().isEmpty(), "Score 1.0 should be auto-reconciled");
        assertEquals(HypothesisStatus.AUTO_RECONCILED, hyp.getStatus());
    }

    @Test
    void runMatching_belowReviewFloor_isRejected() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("500.00"), LocalDate.now()),
            bankTx(new BigDecimal("500.00"), LocalDate.now()),
            0.5, MatchType.AI_PROBABILISTIC    // below 0.70 reviewFloor
        );
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(hyp));
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        ReconciliationResult result = service.runMatching(workspace,
            List.of(ledgerTx(new BigDecimal("500.00"), LocalDate.now())),
            List.of(bankTx(new BigDecimal("500.00"), LocalDate.now()))
        );

        assertTrue(result.getAutoReconciledRecords().isEmpty());
        assertEquals(HypothesisStatus.REJECTED, hyp.getStatus());
    }

    @Test
    void runMatching_betweenReviewFloorAndThreshold_remainsPendingReview() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("500.00"), LocalDate.now()),
            bankTx(new BigDecimal("500.00"), LocalDate.now()),
            0.80, MatchType.AI_PROBABILISTIC    // between 0.70 reviewFloor and 0.95 threshold
        );
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(hyp));
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        ReconciliationResult result = service.runMatching(workspace,
            List.of(ledgerTx(new BigDecimal("500.00"), LocalDate.now())),
            List.of(bankTx(new BigDecimal("500.00"), LocalDate.now()))
        );

        assertTrue(result.getAutoReconciledRecords().isEmpty());
        assertEquals(HypothesisStatus.PENDING_REVIEW, hyp.getStatus());
    }

    @Test
    void runMatching_atExactThreshold_isAutoReconciled() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("300.00"), LocalDate.now()),
            bankTx(new BigDecimal("300.00"), LocalDate.now()),
            0.95, MatchType.EXACT_RULE   // exactly at threshold
        );
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(hyp));
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        ReconciliationResult result = service.runMatching(workspace, List.of(), List.of());

        assertEquals(1, result.getAutoReconciledRecords().size());
    }

    @Test
    void runMatching_callsDataStoreSave() {
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(Collections.emptyList());
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        service.runMatching(workspace, List.of(), List.of());

        verify(mockDataStore).saveMatchingResults(anyList(), anyList());
    }

    @Test
    void runMatching_autoReconciledRecords_useSystemUserId() {
        UUID systemId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            1.0, MatchType.EXACT_RULE
        );
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(hyp));
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        ReconciliationResult result = service.runMatching(workspace, List.of(), List.of());

        assertEquals(systemId, result.getAutoReconciledRecords().get(0).getConfirmingUser().getUserId());
    }

    @Test
    void runMatching_emptyInputs_returnsEmptyResult() {
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(Collections.emptyList());
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        ReconciliationResult result = service.runMatching(workspace, List.of(), List.of());

        assertTrue(result.getHypotheses().isEmpty());
        assertTrue(result.getAutoReconciledRecords().isEmpty());
    }

    @Test
    void runMatching_autoReconciled_justificationMentionsThreshold() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            1.0, MatchType.EXACT_RULE
        );
        when(mockMatchingEngine.generateHypotheses(anyList(), anyList())).thenReturn(List.of(hyp));
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        service.runMatching(workspace, List.of(), List.of());

        assertNotNull(hyp.getJustification());
        assertTrue(hyp.getJustification().contains("Auto-reconciled"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UC7 — confirmHypothesis
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void confirmHypothesis_setsStatusToApproved() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.8, MatchType.EXACT_RULE
        );
        doNothing().when(mockDataStore).saveReconciliationRecords(anyList());

        service.confirmHypothesis(hyp, user);

        assertEquals(HypothesisStatus.APPROVED, hyp.getStatus());
    }

    @Test
    void confirmHypothesis_returnsNonNullRecord() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.8, MatchType.EXACT_RULE
        );
        doNothing().when(mockDataStore).saveReconciliationRecords(anyList());

        ReconciliationRecord record = service.confirmHypothesis(hyp, user);

        assertNotNull(record);
    }

    @Test
    void confirmHypothesis_recordLinksHypothesisAndUser() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("200.00"), LocalDate.now()),
            bankTx(new BigDecimal("200.00"), LocalDate.now()),
            0.9, MatchType.EXACT_RULE
        );
        doNothing().when(mockDataStore).saveReconciliationRecords(anyList());

        ReconciliationRecord record = service.confirmHypothesis(hyp, user);

        assertSame(hyp, record.getHypothesis());
        assertSame(user, record.getConfirmingUser());
    }

    @Test
    void confirmHypothesis_justificationMentionsUser() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.8, MatchType.EXACT_RULE
        );
        doNothing().when(mockDataStore).saveReconciliationRecords(anyList());

        service.confirmHypothesis(hyp, user);

        assertTrue(hyp.getJustification().contains(user.getUsername()));
    }

    @Test
    void confirmHypothesis_persistsRecord() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.8, MatchType.EXACT_RULE
        );
        doNothing().when(mockDataStore).saveReconciliationRecords(anyList());

        service.confirmHypothesis(hyp, user);

        verify(mockDataStore).saveReconciliationRecords(anyList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UC7 — rejectHypothesis
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void rejectHypothesis_setsStatusToRejected() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.6, MatchType.AI_PROBABILISTIC
        );
        doNothing().when(mockDataStore).saveMatchHypotheses(anyList());

        service.rejectHypothesis(hyp, user);

        assertEquals(HypothesisStatus.REJECTED, hyp.getStatus());
    }

    @Test
    void rejectHypothesis_justificationMentionsUser() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.6, MatchType.AI_PROBABILISTIC
        );
        doNothing().when(mockDataStore).saveMatchHypotheses(anyList());

        service.rejectHypothesis(hyp, user);

        assertTrue(hyp.getJustification().contains(user.getUsername()));
    }

    @Test
    void rejectHypothesis_persistsHypothesis() {
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx(new BigDecimal("100.00"), LocalDate.now()),
            bankTx(new BigDecimal("100.00"), LocalDate.now()),
            0.6, MatchType.AI_PROBABILISTIC
        );
        doNothing().when(mockDataStore).saveMatchHypotheses(anyList());

        service.rejectHypothesis(hyp, user);

        verify(mockDataStore).saveMatchHypotheses(anyList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UC8 — forceReconcile
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void forceReconcile_returnsNonNullRecord() {
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        StandardizedTransaction l = ledgerTx(new BigDecimal("750.00"), LocalDate.now());
        StandardizedTransaction b = bankTx(new BigDecimal("750.00"), LocalDate.now());

        ReconciliationRecord record = service.forceReconcile(l, b, user, "Auditor override");

        assertNotNull(record);
    }

    @Test
    void forceReconcile_matchTypeIsForceOverride() {
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        StandardizedTransaction l = ledgerTx(new BigDecimal("750.00"), LocalDate.now());
        StandardizedTransaction b = bankTx(new BigDecimal("750.00"), LocalDate.now());

        ReconciliationRecord record = service.forceReconcile(l, b, user, "Manual match");

        assertEquals(MatchType.FORCE_OVERRIDE, record.getHypothesis().getMatchType());
    }

    @Test
    void forceReconcile_statusIsApproved() {
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        StandardizedTransaction l = ledgerTx(new BigDecimal("750.00"), LocalDate.now());
        StandardizedTransaction b = bankTx(new BigDecimal("750.00"), LocalDate.now());

        ReconciliationRecord record = service.forceReconcile(l, b, user, "Manual");

        assertEquals(HypothesisStatus.APPROVED, record.getHypothesis().getStatus());
    }

    @Test
    void forceReconcile_confidenceIsOne() {
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        StandardizedTransaction l = ledgerTx(new BigDecimal("750.00"), LocalDate.now());
        StandardizedTransaction b = bankTx(new BigDecimal("750.00"), LocalDate.now());

        ReconciliationRecord record = service.forceReconcile(l, b, user, "Manual");

        assertEquals(1.0, record.getHypothesis().getConfidenceScore(), 0.0001);
    }

    @Test
    void forceReconcile_isManualOverride_returnsTrue() {
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        StandardizedTransaction l = ledgerTx(new BigDecimal("750.00"), LocalDate.now());
        StandardizedTransaction b = bankTx(new BigDecimal("750.00"), LocalDate.now());

        ReconciliationRecord record = service.forceReconcile(l, b, user, "Manual");

        assertTrue(record.isManualOverride());
    }

    @Test
    void forceReconcile_justificationContainsProvidedText() {
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        StandardizedTransaction l = ledgerTx(new BigDecimal("750.00"), LocalDate.now());
        StandardizedTransaction b = bankTx(new BigDecimal("750.00"), LocalDate.now());

        ReconciliationRecord record = service.forceReconcile(l, b, user, "Client confirmed via phone");

        assertTrue(record.getHypothesis().getJustification().contains("Client confirmed via phone"));
    }

    @Test
    void forceReconcile_persists() {
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        StandardizedTransaction l = ledgerTx(new BigDecimal("100.00"), LocalDate.now());
        StandardizedTransaction b = bankTx(new BigDecimal("100.00"), LocalDate.now());

        service.forceReconcile(l, b, user, "test");

        verify(mockDataStore).saveMatchingResults(anyList(), anyList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UC9 — consolidateMultiSource
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void consolidateMultiSource_nullInput_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.consolidateMultiSource(workspace, null, null, 0.0, null, user);
        });
    }

    @Test
    void consolidateMultiSource_withinTolerance_createsRecords() {
        StandardizedTransaction l1 = ledgerTx(new BigDecimal("100.00"), LocalDate.now());
        StandardizedTransaction l2 = ledgerTx(new BigDecimal("200.00"), LocalDate.now());
        StandardizedTransaction b  = bankTx(new BigDecimal("300.00"), LocalDate.now());

        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());

        List<ReconciliationRecord> result = service.consolidateMultiSource(
            workspace, List.of(l1, l2), List.of(b), 0.05, new java.util.ArrayList<>(), user);

        assertEquals(2, result.size());
        assertEquals(MatchType.FORCE_OVERRIDE, result.get(0).getHypothesis().getMatchType());
        verify(mockDataStore).saveMatchingResults(anyList(), anyList());
    }

    @Test
    void consolidateMultiSource_outsideTolerance_createsAnomaly() {
        StandardizedTransaction l1 = ledgerTx(new BigDecimal("100.00"), LocalDate.now());
        StandardizedTransaction l2 = ledgerTx(new BigDecimal("50.00"), LocalDate.now());
        StandardizedTransaction b  = bankTx(new BigDecimal("300.00"), LocalDate.now());

        List<aval.domain.ai.Anomaly> anomalies = new java.util.ArrayList<>();
        List<ReconciliationRecord> result = service.consolidateMultiSource(
            workspace, List.of(l1, l2), List.of(b), 0.05, anomalies, user);

        assertTrue(result.isEmpty());
        assertEquals(1, anomalies.size());
        assertEquals(aval.domain.ai.Anomaly.Category.CONSOLIDATION_VARIANCE, anomalies.get(0).getCategory());
        verify(mockDataStore, never()).saveMatchingResults(anyList(), anyList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UC8/UC9 — markAsUnresolvable
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void markAsUnresolvable_createsAndSavesRecord() {
        StandardizedTransaction tx = bankTx(new BigDecimal("100.00"), LocalDate.now());
        aval.common.enums.UnresolvableReason reason = aval.common.enums.UnresolvableReason.BANK_CHARGE;
        String note = "Verified bank charge";

        doNothing().when(mockDataStore).saveUnresolvableRecord(any(aval.domain.ai.UnresolvableRecord.class));

        aval.domain.ai.UnresolvableRecord record = service.markAsUnresolvable(
            tx, aval.common.enums.TransactionSide.BANK, reason, note, user);

        assertNotNull(record);
        assertEquals(tx.getTransactionId(), record.getTransaction().getTransactionId());
        assertEquals(aval.common.enums.TransactionSide.BANK, record.getSide());
        assertEquals(reason, record.getReason());
        assertEquals(note, record.getAuditNote());
        assertEquals(user, record.getSealedBy());

        verify(mockDataStore).saveUnresolvableRecord(record);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  End-to-end using real RuleBasedMatchingEngine
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void endToEnd_ruleBasedEngine_matchAndConfirm() {
        ReconciliationService realService = new ReconciliationService(
            mockVectorEngine,
            new RuleBasedMatchingEngine(new MatchingConfig()),
            mockDataStore
        );
        doNothing().when(mockDataStore).saveMatchingResults(anyList(), anyList());
        // runMatching only calls saveMatchingResults — no saveReconciliationRecords stub needed

        LocalDate today = LocalDate.now();
        StandardizedTransaction l = ledgerTx(new BigDecimal("999.00"), today);
        StandardizedTransaction b = bankTx(new BigDecimal("999.00"), today);

        ReconciliationResult runResult = realService.runMatching(workspace, List.of(l), List.of(b));

        // Should have 1 hypothesis auto-reconciled (score = 1.0 >= 0.95)
        assertEquals(1, runResult.getHypotheses().size());
        assertEquals(1, runResult.getAutoReconciledRecords().size());
    }
}
