package aval.persistence;

import aval.common.enums.TransactionType;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Issue 1: Missing Workspace Retrieval.
 * Verifies that the app can resume an existing workspace session from the DB.
 */
public class WorkspaceRetrievalTest {

    private Connection conn;

    @BeforeEach
    public void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        createSchema();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    private void createSchema() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS vector");
            s.execute("CREATE TABLE client_organization (org_id UUID PRIMARY KEY, name VARCHAR(100))");
            s.execute("CREATE TABLE reconciliation_workspace (workspace_id UUID PRIMARY KEY, org_id UUID, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, status VARCHAR(20))");
            s.execute("CREATE TABLE financial_dataset (dataset_id UUID PRIMARY KEY, workspace_id UUID, file_path TEXT, source_type VARCHAR(30), status VARCHAR(20), import_date DATE)");
            s.execute("CREATE TABLE standardized_ledger (transaction_id UUID PRIMARY KEY, value_date DATE, amount DECIMAL(19,4), narrative TEXT, transaction_type VARCHAR(20), source_dataset_id UUID)");
            s.execute("CREATE TABLE standardized_bank (transaction_id UUID PRIMARY KEY, value_date DATE, amount DECIMAL(19,4), narrative TEXT, transaction_type VARCHAR(20), source_dataset_id UUID)");
        }
    }

    @Test
    public void testFindLatestWorkspaceForClient_returnsExisting() throws Exception {
        DataStore ds = new DataStore(new MockDataSource(conn));
        UUID orgId = UUID.randomUUID();

        // Insert client and workspace
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO client_organization (org_id, name) VALUES ('" + orgId + "', 'Test Client')");
            UUID wsId = UUID.randomUUID();
            s.execute("INSERT INTO reconciliation_workspace (workspace_id, org_id, status) VALUES ('" + wsId + "', '" + orgId + "', 'OPEN')");
        }

        var result = ds.findLatestWorkspaceForClient(orgId);

        assertTrue(result.isPresent(), "Should find existing workspace");
        assertEquals(orgId, result.get().getClientOrganization().getOrgId());
    }

    @Test
    public void testFindLatestWorkspaceForClient_returnsEmpty_whenNone() throws Exception {
        DataStore ds = new DataStore(new MockDataSource(conn));

        var result = ds.findLatestWorkspaceForClient(UUID.randomUUID());

        assertTrue(result.isEmpty(), "Should return empty for unknown client");
    }

    @Test
    public void testGetLedgerTransactionsForWorkspace_returnsRows() throws Exception {
        DataStore ds = new DataStore(new MockDataSource(conn));
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO reconciliation_workspace (workspace_id, org_id, status) VALUES ('" + wsId + "', '" + UUID.randomUUID() + "', 'OPEN')");
            s.execute("INSERT INTO financial_dataset (dataset_id, workspace_id, file_path, source_type, status, import_date) VALUES ('" + dsId + "', '" + wsId + "', '/test.csv', 'INTERNAL_EXCEL', 'PARSED', CURRENT_DATE)");
            s.execute("INSERT INTO standardized_ledger (transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id) VALUES ('" + txId + "', CURRENT_DATE, 1500.00, 'Test Payment', 'DEBIT', '" + dsId + "')");
        }

        List<StandardizedTransaction> ledger = ds.getLedgerTransactionsForWorkspace(wsId);

        assertFalse(ledger.isEmpty(), "Should return ledger transactions for workspace");
        assertEquals(1, ledger.size());
        assertEquals(txId, ledger.get(0).getTransactionId());
        assertEquals("Test Payment", ledger.get(0).getNarrative());
    }

    @Test
    public void testGetBankTransactionsForWorkspace_returnsRows() throws Exception {
        DataStore ds = new DataStore(new MockDataSource(conn));
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO reconciliation_workspace (workspace_id, org_id, status) VALUES ('" + wsId + "', '" + UUID.randomUUID() + "', 'OPEN')");
            s.execute("INSERT INTO financial_dataset (dataset_id, workspace_id, file_path, source_type, status, import_date) VALUES ('" + dsId + "', '" + wsId + "', '/test.pdf', 'EXTERNAL_PDF', 'PARSED', CURRENT_DATE)");
            s.execute("INSERT INTO standardized_bank (transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id) VALUES ('" + txId + "', CURRENT_DATE, 1500.00, 'Bank Test', 'CREDIT', '" + dsId + "')");
        }

        List<StandardizedTransaction> bank = ds.getBankTransactionsForWorkspace(wsId);

        assertFalse(bank.isEmpty(), "Should return bank transactions for workspace");
        assertEquals(1, bank.size());
        assertEquals(txId, bank.get(0).getTransactionId());
    }

    // Simple mock DataSource for H2
    private static class MockDataSource implements javax.sql.DataSource {
        private final Connection conn;
        MockDataSource(Connection conn) { this.conn = conn; }
        public java.sql.Connection getConnection() { return conn; }
        public java.sql.Connection getConnection(String u, String p) { return conn; }
        public java.sql.PrintWriter getLogWriter() { return null; }
        public void setLogWriter(java.sql.PrintWriter w) {}
        public void setLoginTimeout(int s) {}
        public int getLoginTimeout() { return 0; }
        public java.util.logging.Logger getParentLogger() { return null; }
        public <T> T unwrap(Class<T> c) { return null; }
        public boolean isWrapperFor(Class<?> c) { return false; }
    }
}