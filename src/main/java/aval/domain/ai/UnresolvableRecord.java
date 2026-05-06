package aval.domain.ai;

import aval.common.enums.TransactionSide;
import aval.common.enums.UnresolvableReason;
import aval.domain.SystemUser;

import java.time.LocalDateTime;
import java.util.UUID;

public class UnresolvableRecord {
    private final UUID recordId;
    private final StandardizedTransaction transaction;
    private final TransactionSide side;
    private final UnresolvableReason reason;
    private final String auditNote;
    private final SystemUser sealedBy;
    private final LocalDateTime sealedAt;

    public UnresolvableRecord(
        StandardizedTransaction transaction,
        TransactionSide side,
        UnresolvableReason reason,
        String auditNote,
        SystemUser sealedBy
    ) {
        if (auditNote == null || auditNote.isBlank()) {
            throw new IllegalArgumentException("Audit note is mandatory for unresolvable records.");
        }
        if (transaction == null || side == null || reason == null || sealedBy == null) {
            throw new IllegalArgumentException("Transaction, side, reason, and sealedBy are mandatory.");
        }
        this.recordId = UUID.randomUUID();
        this.transaction = transaction;
        this.side = side;
        this.reason = reason;
        this.auditNote = auditNote;
        this.sealedBy = sealedBy;
        this.sealedAt = LocalDateTime.now();
    }

    public UUID getRecordId() { return recordId; }
    public StandardizedTransaction getTransaction() { return transaction; }
    public TransactionSide getSide() { return side; }
    public UnresolvableReason getReason() { return reason; }
    public String getAuditNote() { return auditNote; }
    public SystemUser getSealedBy() { return sealedBy; }
    public LocalDateTime getSealedAt() { return sealedAt; }
}
