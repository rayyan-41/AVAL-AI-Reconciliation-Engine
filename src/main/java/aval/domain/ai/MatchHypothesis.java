//Dev: Rayyan
//Use Cases: UC6-UC9
package aval.domain.ai;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.MatchType;
import java.util.UUID;

//@desc:   A candidate reconciliation link proposed by an engine, pending human review or auto-approval.
//@grasp:  Information Expert
//@gof:    N/A
public class MatchHypothesis {

    private final UUID hypothesisId;
    private final StandardizedTransaction ledgerTransaction;
    private final StandardizedTransaction bankTransaction;
    private final double confidenceScore;
    private final MatchType matchType;
    private HypothesisStatus status;
    private String justification;

    public MatchHypothesis(
        StandardizedTransaction ledgerTransaction,
        StandardizedTransaction bankTransaction,
        double confidenceScore,
        MatchType matchType
    ) {
        this.hypothesisId = UUID.randomUUID();
        this.ledgerTransaction = ledgerTransaction;
        this.bankTransaction = bankTransaction;
        this.confidenceScore = confidenceScore;
        this.matchType = matchType;
        this.status = HypothesisStatus.PENDING_REVIEW;
    }

    public UUID getHypothesisId() {
        return hypothesisId;
    }

    public StandardizedTransaction getLedgerTransaction() {
        return ledgerTransaction;
    }

    public StandardizedTransaction getBankTransaction() {
        return bankTransaction;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public HypothesisStatus getStatus() {
        return status;
    }

    public String getJustification() {
        return justification;
    }

    public void setStatus(HypothesisStatus status) {
        this.status = status;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    @Override
    public String toString() {
        return String.format(
            "Hypothesis[%s]: Ledger(%s) <-> Bank(%s) | Score: %.2f | Type: %s",
            hypothesisId.toString().substring(0, 8),
            ledgerTransaction != null ? ledgerTransaction.getAmount() : "N/A",
            bankTransaction != null ? bankTransaction.getAmount() : "N/A",
            confidenceScore,
            matchType
        );
    }
}
