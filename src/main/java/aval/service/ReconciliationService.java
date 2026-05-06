//Dev: Rayyan
//Use Cases: UC6-UC9
package aval.service;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.MatchType;
import aval.common.enums.TransactionSide;
import aval.common.enums.UnresolvableReason;
import aval.domain.SystemUser;
import aval.domain.ai.Anomaly;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.ai.UnresolvableRecord;
import aval.domain.core.MatchingConfig;
import aval.domain.core.ReconciliationWorkspace;
import aval.engine.MatchingEngine;
import aval.engine.VectorizationEngine;
import aval.persistence.DataStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//@desc:   Central orchestrator for the AI reconciliation pipeline, coordinating engines and
//         data objects to execute the matching workflow (UC6-UC9).
//         Also handles the new unresolvable-item disposition path — every transaction that
//         exits this service has an explicit, auditable status in the database.
//@grasp:  Controller, Pure Fabrication
//@gof:    Facade (wraps matching engine + persistence into a single interface)
public class ReconciliationService {

    private static final java.util.UUID SYSTEM_USER_ID =
        java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

    //-------------- Attributes ----------------------//
    private final VectorizationEngine vectorizationEngine;
    private final MatchingEngine matchingEngine;
    private final DataStore dataStore;

    //-------------- Constructor ----------------------//
    public ReconciliationService(
        VectorizationEngine vectorizationEngine,
        MatchingEngine matchingEngine,
        DataStore dataStore
    ) {
        this.vectorizationEngine = vectorizationEngine;
        this.matchingEngine = matchingEngine;
        this.dataStore = dataStore;
    }

    // =========================================================
    // UC6 — Run Probabilistic Matching Engine
    // =========================================================

    /**
     * Orchestrates the generation of match candidates using the injected engine strategy.
     * Candidates above the auto-confirm threshold are sealed immediately; those below
     * the review floor are auto-rejected; the rest go to the pending queue.
     */
    public ReconciliationResult runMatching(
        ReconciliationWorkspace workspace,
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    ) {
        List<MatchHypothesis> candidates = matchingEngine.generateHypotheses(
            ledgerTransactions, bankTransactions
        );

        MatchingConfig config = workspace.getMatchingConfig();
        double threshold   = config != null ? config.getAutoConfirmThreshold() : new MatchingConfig().getAutoConfirmThreshold();
        double reviewFloor = config != null ? config.getReviewFloor()          : new MatchingConfig().getReviewFloor();

        List<ReconciliationRecord> autoRecords = new ArrayList<>();
        SystemUser systemUser = new SystemUser(
            SYSTEM_USER_ID, "System (Auto-Reconcile)", "00000-0000000-0",
            "system_auto", aval.common.enums.UserRole.ADMIN, "System"
        );

        for (MatchHypothesis hypothesis : candidates) {
            if (hypothesis.getConfidenceScore() >= threshold) {
                hypothesis.setStatus(HypothesisStatus.AUTO_RECONCILED);
                hypothesis.setJustification("Auto-reconciled: Score exceeds " + threshold);
                autoRecords.add(new ReconciliationRecord(hypothesis, systemUser));
            } else if (hypothesis.getConfidenceScore() < reviewFloor) {
                hypothesis.setStatus(HypothesisStatus.REJECTED);
                hypothesis.setJustification("Auto-rejected: Score below review floor " + reviewFloor);
            } else {
                hypothesis.setStatus(HypothesisStatus.PENDING_REVIEW);
            }
        }

        dataStore.saveMatchingResults(candidates, autoRecords);
        return new ReconciliationResult(candidates, autoRecords);
    }

    // =========================================================
    // UC7 — Review AI-Suggested Matches
    // =========================================================

    /**
     * Approve: transitions a pending hypothesis into a final immutable audit record.
     */
    public ReconciliationRecord confirmHypothesis(
        MatchHypothesis hypothesis,
        SystemUser confirmingUser
    ) {
        hypothesis.setStatus(HypothesisStatus.APPROVED);
        hypothesis.setJustification("Confirmed by " + confirmingUser.getUsername());

        ReconciliationRecord record = new ReconciliationRecord(hypothesis, confirmingUser);
        List<ReconciliationRecord> records = new ArrayList<>();
        records.add(record);
        dataStore.saveReconciliationRecords(records);
        return record;
    }

    /**
     * Reject: marks the hypothesis as rejected and persists the decision.
     */
    public void rejectHypothesis(
        MatchHypothesis hypothesis,
        SystemUser rejectingUser
    ) {
        hypothesis.setStatus(HypothesisStatus.REJECTED);
        hypothesis.setJustification("Rejected by " + rejectingUser.getUsername());

        List<MatchHypothesis> hypotheses = new ArrayList<>();
        hypotheses.add(hypothesis);
        dataStore.saveMatchHypotheses(hypotheses);
    }

    // =========================================================
    // UC8 — Force Manual Reconciliation (1-to-1)
    // =========================================================

    /**
     * Bypasses engines to manually link exactly one ledger and one bank transaction.
     * Maintains the full audit trail via FORCE_OVERRIDE match type and mandatory justification.
     * Use when amounts differ but the Finance Officer asserts the transactions are the same.
     *
     * @param ledgerTx        the unmatched internal ledger entry
     * @param bankTx          the unmatched bank statement entry
     * @param overridingUser  the authenticated Finance Officer performing the override
     * @param justification   mandatory non-blank explanation (appears in audit report)
     * @return the sealed ReconciliationRecord
     * @throws IllegalArgumentException if justification is blank
     */
    public ReconciliationRecord forceReconcile(
        StandardizedTransaction ledgerTx,
        StandardizedTransaction bankTx,
        SystemUser overridingUser,
        String justification
    ) {
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("Justification is mandatory for a force-reconcile override.");
        }

        BigDecimal ledgerAmt = ledgerTx.getAmount().abs();
        BigDecimal bankAmt   = bankTx.getAmount().abs();
        BigDecimal discrepancy = ledgerAmt.subtract(bankAmt).abs();

        String fullJustification = String.format(
            "FORCE OVERRIDE by %s — Discrepancy: %s — Reason: %s",
            overridingUser.getUsername(), discrepancy.toPlainString(), justification
        );

        MatchHypothesis manualHypothesis = new MatchHypothesis(
            ledgerTx, bankTx, 1.0, MatchType.FORCE_OVERRIDE
        );
        manualHypothesis.setStatus(HypothesisStatus.APPROVED);
        manualHypothesis.setJustification(fullJustification);

        ReconciliationRecord record = new ReconciliationRecord(manualHypothesis, overridingUser);
        List<ReconciliationRecord> records = new ArrayList<>();
        records.add(record);
        
        List<MatchHypothesis> hypotheses = new ArrayList<>();
        hypotheses.add(manualHypothesis);
        
        dataStore.saveMatchingResults(hypotheses, records);
        return record;
    }

    // =========================================================
    // UC9 — Multi-Source Consolidation (N-to-1)
    // =========================================================

    /**
     * Consolidates N ledger entries against a single bank transaction whose amount equals
     * (or is within tolerancePct of) the sum of the selected ledger entries.
     *
     * Each ledger entry gets its own FORCE_OVERRIDE MatchHypothesis pointing at the same
     * bank transaction — this is the correct model for the existing 1:1 DB schema.
     * If the sum falls outside tolerance a CONSOLIDATION_VARIANCE anomaly is appended to
     * anomaliesOut and an empty list is returned (caller should re-display for correction).
     *
     * @param workspace          active workspace (reserved for future config use)
     * @param ledgerTransactions N user-selected ledger entries to consolidate
     * @param bankTransaction    the single bank anchor entry
     * @param tolerancePct       acceptable fractional variance, e.g. 0.02 = ±2%
     * @param anomaliesOut       mutable list — CONSOLIDATION_VARIANCE appended on failure
     * @param confirmingUser     the Finance Officer authorising the consolidation
     * @return list of ReconciliationRecords (one per ledger entry), or empty on variance failure
     */
    public List<ReconciliationRecord> consolidateMultiSource(
        ReconciliationWorkspace workspace,
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions,
        double tolerancePct,
        List<Anomaly> anomaliesOut,
        SystemUser confirmingUser
    ) {
        if (ledgerTransactions == null || ledgerTransactions.isEmpty()) {
            throw new IllegalArgumentException("ledgerTransactions must not be empty for consolidation.");
        }
        if (bankTransactions == null || bankTransactions.isEmpty()) {
            throw new IllegalArgumentException("bankTransactions must not be empty for consolidation.");
        }

        // 1. Sum the selected amounts
        BigDecimal ledgerSum = ledgerTransactions.stream()
            .map(t -> t.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bankSum = bankTransactions.stream()
            .map(t -> t.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Check variance against tolerance
        BigDecimal variance = ledgerSum.subtract(bankSum).abs();
        BigDecimal variancePct = bankSum.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : variance.divide(bankSum, 10, RoundingMode.HALF_UP);

        boolean withinTolerance = variancePct.compareTo(BigDecimal.valueOf(tolerancePct)) <= 0;

        if (!withinTolerance) {
            if (anomaliesOut != null) {
                anomaliesOut.add(new Anomaly(
                    Anomaly.Category.CONSOLIDATION_VARIANCE,
                    String.format(
                        "Consolidation variance %.2f%% exceeds tolerance %.1f%% — "
                        + "Ledger sum: %s, Bank sum: %s. Re-select entries or adjust tolerance.",
                        variancePct.multiply(BigDecimal.valueOf(100)).doubleValue(),
                        tolerancePct * 100,
                        ledgerSum.toPlainString(),
                        bankSum.toPlainString()),
                    bankTransactions.get(0), null
                ));
            }
            return Collections.emptyList();
        }

        // 3. Within tolerance — create hypotheses (M:N mapping using anchors)
        List<MatchHypothesis> hypotheses = new ArrayList<>();
        List<ReconciliationRecord> records = new ArrayList<>();
        String groupNote = String.format(
            "UC9 Consolidation by %s — %d Ledger summing %s against %d Bank summing %s (variance %.4f%%)",
            confirmingUser.getUsername(),
            ledgerTransactions.size(),
            ledgerSum.toPlainString(),
            bankTransactions.size(),
            bankSum.toPlainString(),
            variancePct.multiply(BigDecimal.valueOf(100)).doubleValue()
        );

        StandardizedTransaction anchorBank = bankTransactions.get(0);
        for (StandardizedTransaction ledger : ledgerTransactions) {
            MatchHypothesis h = new MatchHypothesis(
                ledger, anchorBank, 1.0, MatchType.FORCE_OVERRIDE
            );
            h.setStatus(HypothesisStatus.APPROVED);
            h.setJustification(groupNote);
            hypotheses.add(h);
            records.add(new ReconciliationRecord(h, confirmingUser));
        }
        
        StandardizedTransaction anchorLedger = ledgerTransactions.get(0);
        for (int i = 1; i < bankTransactions.size(); i++) {
            MatchHypothesis h = new MatchHypothesis(
                anchorLedger, bankTransactions.get(i), 1.0, MatchType.FORCE_OVERRIDE
            );
            h.setStatus(HypothesisStatus.APPROVED);
            h.setJustification(groupNote);
            hypotheses.add(h);
            records.add(new ReconciliationRecord(h, confirmingUser));
        }

        // 4. Persist atomically
        dataStore.saveMatchingResults(hypotheses, records);
        return records;
    }

    // =========================================================
    // UC8 / UC9 — Mark Transaction as Unresolvable
    // =========================================================

    /**
     * Disposes of a transaction that has no counterpart on either side by recording
     * an explicit, auditable UnresolvableRecord.  This gives every unmatched item an
     * exit path so the reconciliation can be marked complete without blocking the
     * report gate.
     *
     * Mandatory use cases:
     *  - Bank charges / wire fees that are never journalled in the ledger
     *  - Timing differences expected to clear next period
     *  - Ledger errors being corrected separately
     *  - Any item under investigation that cannot be matched now
     *
     * @param transaction  the transaction with no counterpart
     * @param side         BANK or LEDGER — which pool this transaction came from
     * @param reason       typed reason code (drives audit report categorisation)
     * @param auditNote    mandatory non-blank free-text explanation
     * @param sealedBy     the Finance Officer authorising the disposition
     * @return the sealed UnresolvableRecord
     * @throws IllegalArgumentException if auditNote is blank
     */
    public UnresolvableRecord markAsUnresolvable(
        StandardizedTransaction transaction,
        TransactionSide side,
        UnresolvableReason reason,
        String auditNote,
        SystemUser sealedBy
    ) {
        // UnresolvableRecord constructor validates all fields
        UnresolvableRecord record = new UnresolvableRecord(
            transaction, side, reason, auditNote, sealedBy
        );
        dataStore.saveUnresolvableRecord(record);
        return record;
    }
}
