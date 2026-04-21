package aval.domain.ai;

import aval.domain.SystemUser;
import java.time.LocalDateTime;
import java.util.UUID;

//@desc:   The final, immutable audit record of a successful reconciliation between transactions.
//@grasp:  Information Expert
//@gof:    N/A
public class ReconciliationRecord {

    private final UUID recordId;
    private final MatchHypothesis hypothesis;
    private final SystemUser confirmingUser;
    private final LocalDateTime reconciledAt;

    public ReconciliationRecord(
        MatchHypothesis hypothesis,
        SystemUser confirmingUser
    ) {
        this.recordId = UUID.randomUUID();
        this.hypothesis = hypothesis;
        this.confirmingUser = confirmingUser;
        this.reconciledAt = LocalDateTime.now();
    }

    public UUID getRecordId() {
        return recordId;
    }

    public MatchHypothesis getHypothesis() {
        return hypothesis;
    }

    public SystemUser getConfirmingUser() {
        return confirmingUser;
    }

    public LocalDateTime getReconciledAt() {
        return reconciledAt;
    }
}
