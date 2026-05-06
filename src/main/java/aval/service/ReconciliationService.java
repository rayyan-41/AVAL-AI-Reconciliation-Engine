//Dev: Rayyan
//Use Cases: UC6-UC9
package aval.service;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.MatchType;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.MatchingConfig;
import aval.domain.core.ReconciliationWorkspace;
import aval.engine.MatchingEngine;
import aval.engine.VectorizationEngine;
import aval.persistence.DataStore;
import java.util.ArrayList;
import java.util.List;

//@desc:   Central orchestrator for the AI reconciliation pipeline, coordinating engines and data objects to execute the matching workflow.
//@grasp:  Controller, Pure Fabrication
//@gof:    N/A
public class ReconciliationService {

    private static final java.util.UUID SYSTEM_USER_ID =
        java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

    //-------------- Attributes ----------------------//
    private final VectorizationEngine vectorizationEngine;
    private final MatchingEngine matchingEngine;
    private final DataStore dataStore;

    //-------------- Methods ----------------------//
    public ReconciliationService(
        VectorizationEngine vectorizationEngine,
        MatchingEngine matchingEngine,
        DataStore dataStore
    ) {
        this.vectorizationEngine = vectorizationEngine;
        this.matchingEngine = matchingEngine;
        this.dataStore = dataStore;
    }

    /**
     * UC6 — Run Probabilistic Matching Engine
     * Orchestrates the generation of match candidates using the injected engine strategy.
     */
    public ReconciliationResult runMatching(
        ReconciliationWorkspace workspace,
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    ) {
        // 1. Generate candidates using the matching strategy (e.g., Rule-Based or AI)
        List<MatchHypothesis> candidates = matchingEngine.generateHypotheses(
            ledgerTransactions,
            bankTransactions
        );

        MatchingConfig config = workspace.getMatchingConfig();
        double threshold = config != null
            ? config.getAutoConfirmThreshold()
            : new MatchingConfig().getAutoConfirmThreshold();
        double reviewFloor = config != null
            ? config.getReviewFloor()
            : new MatchingConfig().getReviewFloor();
        List<ReconciliationRecord> autoRecords = new ArrayList<>();
        SystemUser systemUser = new SystemUser(
            SYSTEM_USER_ID,
            "System (Auto-Reconcile)",
            "00000-0000000-0",
            "system_auto",
            aval.common.enums.UserRole.ADMIN,
            "System"
        );

        for (MatchHypothesis hypothesis : candidates) {
            if (hypothesis.getConfidenceScore() >= threshold) {
                hypothesis.setStatus(HypothesisStatus.AUTO_RECONCILED);
                hypothesis.setJustification(
                    "Auto-reconciled: Score exceeds " + threshold
                );
                autoRecords.add(
                    new ReconciliationRecord(hypothesis, systemUser)
                );
            } else if (hypothesis.getConfidenceScore() < reviewFloor) {
                hypothesis.setStatus(HypothesisStatus.REJECTED);
                hypothesis.setJustification(
                    "Auto-rejected: Score below review floor " + reviewFloor
                );
            } else {
                hypothesis.setStatus(HypothesisStatus.PENDING_REVIEW);
            }
        }

        // 2. Persist suggested matches for human review
        dataStore.saveMatchingResults(candidates, autoRecords);

        return new ReconciliationResult(candidates, autoRecords);
    }

    /**
     * UC7 — Review AI-Suggested Matches (Approve)
     * Transitions a proposal into a final, immutable audit record.
     */
    public ReconciliationRecord confirmHypothesis(
        MatchHypothesis hypothesis,
        SystemUser confirmingUser
    ) {
        // 1. Update the suggestion status
        hypothesis.setStatus(HypothesisStatus.APPROVED);
        hypothesis.setJustification(
            "Confirmed by " + confirmingUser.getUsername()
        );

        // 2. Create the immutable audit record
        ReconciliationRecord record = new ReconciliationRecord(
            hypothesis,
            confirmingUser
        );

        // 3. Persist the final record
        List<ReconciliationRecord> records = new ArrayList<>();
        records.add(record);
        dataStore.saveReconciliationRecords(records);

        return record;
    }

    /**
     * UC7 — Review AI-Suggested Matches (Reject)
     */
    public void rejectHypothesis(
        MatchHypothesis hypothesis,
        SystemUser rejectingUser
    ) {
        hypothesis.setStatus(HypothesisStatus.REJECTED);
        hypothesis.setJustification(
            "Rejected by " + rejectingUser.getUsername()
        );

        List<MatchHypothesis> hypotheses = new ArrayList<>();
        hypotheses.add(hypothesis);
        dataStore.saveMatchHypotheses(hypotheses);
    }

    /**
     * UC8 — Force Manual Reconciliation
     * Bypasses engines to manually link two transactions, maintaining the audit trail.
     */
    public ReconciliationRecord forceReconcile(
        StandardizedTransaction ledgerTx,
        StandardizedTransaction bankTx,
        SystemUser overridingUser,
        String justification
    ) {
        // 1. Create a manual hypothesis (score 1.0 because it's forced)
        MatchHypothesis manualHypothesis = new MatchHypothesis(
            ledgerTx,
            bankTx,
            1.0,
            MatchType.FORCE_OVERRIDE
        );
        manualHypothesis.setStatus(HypothesisStatus.APPROVED);
        manualHypothesis.setJustification("Manual override: " + justification);

        // 2. Wrap in a record
        ReconciliationRecord record = new ReconciliationRecord(
            manualHypothesis,
            overridingUser
        );

        // 3. Persist
        List<ReconciliationRecord> records = new ArrayList<>();
        records.add(record);
        dataStore.saveReconciliationRecords(records);

        return record;
    }

    /**
     * UC9 — Perform Multi-Source Consolidation
     *
     * Groups ledger transactions whose amounts sum (within tolerancePct %) to the amount
     * of each bank transaction, creating a single FORCE_OVERRIDE hypothesis per group.
     * Variance anomalies (sum outside tolerance) are returned via the provided list.
     *
     * @param workspace         the active workspace (used for MatchingConfig)
     * @param ledgerTransactions all unmatched ledger transactions to consider
     * @param bankTransaction    the single bank transaction to match against
     * @param tolerancePct       acceptable variance as a fraction, e.g. 0.02 = 2 %
     * @param anomaliesOut       mutable list — CONSOLIDATION_VARIANCE entries appended here
     * @param confirmingUser     user performing the consolidation
     * @return list of ReconciliationRecords created (one per group)
     */
    public List<ReconciliationRecord> consolidateMultiSource(
            ReconciliationWorkspace workspace,
            List<StandardizedTransaction> ledgerTransactions,
            StandardizedTransaction bankTransaction,
            double tolerancePct,
            List<aval.domain.ai.Anomaly> anomaliesOut,
            SystemUser confirmingUser) {

        if (ledgerTransactions == null || ledgerTransactions.isEmpty() || bankTransaction == null) {
            return new ArrayList<>();
        }

        java.math.BigDecimal bankAmt   = bankTransaction.getAmount().abs();
        java.math.BigDecimal tolerance = bankAmt.multiply(java.math.BigDecimal.valueOf(tolerancePct));
        java.math.BigDecimal lower     = bankAmt.subtract(tolerance);
        java.math.BigDecimal upper     = bankAmt.add(tolerance);

        // Build a subset whose running sum falls within [lower, upper]
        List<StandardizedTransaction> group = new ArrayList<>();
        java.math.BigDecimal runningSum = java.math.BigDecimal.ZERO;

        for (StandardizedTransaction ledger : ledgerTransactions) {
            java.math.BigDecimal candidate = runningSum.add(ledger.getAmount().abs());
            if (candidate.compareTo(upper) <= 0) {
                group.add(ledger);
                runningSum = candidate;
            }
            if (runningSum.compareTo(lower) >= 0) break;
        }

        List<ReconciliationRecord> records = new ArrayList<>();

        if (runningSum.compareTo(lower) >= 0 && runningSum.compareTo(upper) <= 0) {
            // Within tolerance — create one hypothesis per grouped ledger entry
            for (StandardizedTransaction ledger : group) {
                MatchHypothesis h = new MatchHypothesis(ledger, bankTransaction,
                    1.0, aval.common.enums.MatchType.FORCE_OVERRIDE);
                h.setStatus(aval.common.enums.HypothesisStatus.APPROVED);
                h.setJustification(String.format(
                    "UC9 Consolidation: group sum %s matches bank %s (tolerance %.1f%%)",
                    runningSum.toPlainString(), bankAmt.toPlainString(), tolerancePct * 100));
                records.add(new ReconciliationRecord(h, confirmingUser));
            }
            dataStore.saveReconciliationRecords(records);
        } else {
            // Outside tolerance — flag as variance anomaly
            if (anomaliesOut != null) {
                anomaliesOut.add(new aval.domain.ai.Anomaly(
                    aval.domain.ai.Anomaly.Category.CONSOLIDATION_VARIANCE,
                    String.format("Consolidation variance: ledger group sums to %s, bank posted %s (tolerance %.1f%%)",
                        runningSum.toPlainString(), bankAmt.toPlainString(), tolerancePct * 100),
                    bankTransaction, null));
            }
        }
        return records;
    }
}
