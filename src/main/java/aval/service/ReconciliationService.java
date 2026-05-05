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
     * Draft logic for future expansion to multiple financial datasets.
     */
    public List<MatchHypothesis> consolidateMultiSource(
        ReconciliationWorkspace workspace,
        List<List<StandardizedTransaction>> multipleDatasets
    ) {
        List<MatchHypothesis> consolidated = new ArrayList<>();
        if (
            multipleDatasets == null || multipleDatasets.size() < 2
        ) return consolidated;

        List<StandardizedTransaction> primary = multipleDatasets.get(0);
        for (int i = 1; i < multipleDatasets.size(); i++) {
            List<StandardizedTransaction> secondary = multipleDatasets.get(i);
            ReconciliationResult result = runMatching(workspace, primary, secondary);
            consolidated.addAll(result.getHypotheses());
        }
        return consolidated;
    }
}
