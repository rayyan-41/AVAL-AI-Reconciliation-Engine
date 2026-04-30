package aval.domain.ai;

import aval.domain.SystemUser;
import java.time.LocalDateTime;
import java.util.UUID;

//@desc:   The final, immutable audit record of a successful reconciliation between transactions.
//@grasp:  Information Expert
//@gof:    N/A
public class ReconciliationRecord {

    //---------------- Attributes ------------------//
    private final UUID recordId;
    private final MatchHypothesis hypothesis;
    private final SystemUser confirmingUser;
    private final LocalDateTime reconciledAt;

    //Constructor
    public ReconciliationRecord(
        MatchHypothesis hypothesis,
        SystemUser confirmingUser
    ) {
        this.recordId = UUID.randomUUID();
        this.hypothesis = hypothesis;
        this.confirmingUser = confirmingUser;
        this.reconciledAt = LocalDateTime.now();
    }

    //---------- Methods -----------//

    //Getters
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

    public boolean isManualOverride() {
        return (
            hypothesis != null &&
            hypothesis.getMatchType() ==
            aval.common.enums.MatchType.FORCE_OVERRIDE
        );
    }

    @Override
    public String toString() {
        return String.format(
            "Record[%s]: %s | ConfirmedBy: %s",
            recordId.toString().substring(0, 8),
            hypothesis != null ? hypothesis.toString() : "NULL_HYPOTHESIS",
            confirmingUser != null ? confirmingUser.getUsername() : "SYSTEM"
        );
    }
}
