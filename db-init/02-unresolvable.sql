-- AVAL AI Reconciliation Engine — Migration 02
-- Adds the unresolvable_records table for UC8/UC9 disposition flow.
-- Run after 01-init.sql.

CREATE TABLE IF NOT EXISTS unresolvable_records (
    record_id        UUID PRIMARY KEY,
    transaction_id   UUID NOT NULL,
    transaction_side VARCHAR(10)  NOT NULL,   -- 'BANK' or 'LEDGER'
    reason_code      VARCHAR(30)  NOT NULL,   -- UnresolvableReason enum value
    audit_note       TEXT         NOT NULL,
    sealed_by        UUID REFERENCES app_user(user_id),
    sealed_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookup by workspace (via transaction_id join)
CREATE INDEX IF NOT EXISTS idx_unresolvable_tx ON unresolvable_records(transaction_id);
