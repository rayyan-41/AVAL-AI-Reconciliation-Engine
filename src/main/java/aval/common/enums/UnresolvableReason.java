package aval.common.enums;

public enum UnresolvableReason {
    BANK_CHARGE,          // bank fee / wire fee / service charge — never in ledger
    TIMING_DIFFERENCE,    // will clear next period, defer
    LEDGER_ERROR,         // journal entry mistake, being corrected separately
    PENDING_INVESTIGATION,// flagged, someone needs to call the bank
    APPROVED_WRITE_OFF    // finance manager sign-off, permanent write-off
}
