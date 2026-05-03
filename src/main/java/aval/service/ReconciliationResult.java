package aval.service;

import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import java.util.List;

/**
 * Result DTO for reconciliation matching operations.
 * Contains both the hypotheses and any auto-reconciled records created during matching.
 */
public class ReconciliationResult {

    //-------------- Attributes ----------------------//
    private final List<MatchHypothesis> hypotheses;
    private final List<ReconciliationRecord> autoReconciledRecords;

    //-------------- Methods ----------------------//
    public ReconciliationResult(
        List<MatchHypothesis> hypotheses,
        List<ReconciliationRecord> autoReconciledRecords
    ) {
        this.hypotheses = hypotheses;
        this.autoReconciledRecords = autoReconciledRecords;
    }

    public List<MatchHypothesis> getHypotheses() { return hypotheses; }

    public List<ReconciliationRecord> getAutoReconciledRecords() { return autoReconciledRecords; }
}