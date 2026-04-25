-- AVAL AI Reconciliation Engine - Database Initialization
-- Extension for AI Vector Similarity Search
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Standardized Ledger Transactions (Internal Data)
CREATE TABLE standardized_ledger (
    transaction_id UUID PRIMARY KEY,
    value_date DATE NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    narrative TEXT,
    transaction_type VARCHAR(20),
    source_dataset_id UUID,
    embedding vector(768) -- Matches nomic-embed-text dimensions
);

-- 2. Standardized Bank Transactions (External Data)
CREATE TABLE standardized_bank (
    transaction_id UUID PRIMARY KEY,
    value_date DATE NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    narrative TEXT,
    transaction_type VARCHAR(20),
    source_dataset_id UUID,
    embedding vector(768)
);

-- 3. Match Hypotheses (AI-Suggested Links)
CREATE TABLE match_hypotheses (
    hypothesis_id UUID PRIMARY KEY,
    ledger_id UUID REFERENCES standardized_ledger(transaction_id),
    bank_id UUID REFERENCES standardized_bank(transaction_id),
    confidence_score DOUBLE PRECISION,
    match_type VARCHAR(30),
    status VARCHAR(20),
    justification TEXT
);

-- 4. Finalized Reconciliation Records (Audit Trail)
CREATE TABLE reconciliation_records (
    record_id UUID PRIMARY KEY,
    hypothesis_id UUID REFERENCES match_hypotheses(hypothesis_id),
    confirming_user_id UUID,
    reconciled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. System Users
CREATE TABLE system_user (
    user_id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL
);

-- 6. Client Organizations
CREATE TABLE client_organization (
    org_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

-- 7. Reconciliation Workspaces
CREATE TABLE reconciliation_workspace (
    workspace_id UUID PRIMARY KEY,
    org_id UUID REFERENCES client_organization(org_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL
);

-- 8. Financial Datasets
CREATE TABLE financial_dataset (
    dataset_id UUID PRIMARY KEY,
    workspace_id UUID REFERENCES reconciliation_workspace(workspace_id),
    file_path TEXT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    import_date DATE DEFAULT CURRENT_DATE
);
