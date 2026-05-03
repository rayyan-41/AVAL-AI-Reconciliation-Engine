//Dev: Safwan
//Use Cases: All
package aval.persistence;

import aval.common.enums.UserRole;
import aval.common.enums.TransactionType;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.SemanticEmbedding;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import aval.domain.ingestion.FinancialDataset;
import aval.domain.ingestion.RawTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.mindrot.jbcrypt.BCrypt;

//@desc:   The sole repository holding a JDBC connection to execute SQL against the PostgreSQL database.
//@grasp:  Controller
//@gof:    Repository / DAO
public class DataStore {

    private final DataSource dataSource;

    public DataStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Saves a standardized ledger (internal) transaction along with its semantic embedding to pgvector.
     */
    public void saveStandardizedLedgerTransaction(
        StandardizedTransaction tx,
        SemanticEmbedding embedding
    ) throws SQLException {
        String sql =
            "INSERT INTO standardized_ledger (transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id, embedding) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::vector)";

        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, tx.getTransactionId());
            pstmt.setDate(2, java.sql.Date.valueOf(tx.getValueDate()));
            pstmt.setBigDecimal(3, tx.getAmount());
            pstmt.setString(4, tx.getNarrative());
            pstmt.setString(5, tx.getType().name());
            pstmt.setObject(6, tx.getSourceDatasetId());
            // Passing the vector as a string array format which pgvector parses correctly
            pstmt.setString(7, formatVector(embedding.getVector()));
            pstmt.executeUpdate();
        }
    }

    /**
     * Saves a standardized bank (external) transaction along with its semantic embedding to pgvector.
     */
    public void saveStandardizedBankTransaction(
        StandardizedTransaction tx,
        SemanticEmbedding embedding
    ) throws SQLException {
        String sql =
            "INSERT INTO standardized_bank (transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id, embedding) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::vector)";

        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, tx.getTransactionId());
            pstmt.setDate(2, java.sql.Date.valueOf(tx.getValueDate()));
            pstmt.setBigDecimal(3, tx.getAmount());
            pstmt.setString(4, tx.getNarrative());
            pstmt.setString(5, tx.getType().name());
            pstmt.setObject(6, tx.getSourceDatasetId());
            pstmt.setString(7, formatVector(embedding.getVector()));
            pstmt.executeUpdate();
        }
    }

    /**
     * Performs a pgvector Cosine Distance (<=>) nearest-neighbor search.
     * Returns a list of transactions paired with their distance score.
     */
    public List<Object[]> findSimilarBankTransactions(
        SemanticEmbedding ledgerEmbedding,
        int limit
    ) throws SQLException {
        String sql =
            "SELECT transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id, " +
            "(embedding <=> ?::vector) as distance " +
            "FROM standardized_bank " +
            "ORDER BY distance " +
            "LIMIT ?";

        List<Object[]> results = new ArrayList<>();
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, formatVector(ledgerEmbedding.getVector()));
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    StandardizedTransaction tx = new StandardizedTransaction(
                        (UUID) rs.getObject("transaction_id"),
                        rs.getDate("value_date").toLocalDate(),
                        rs.getBigDecimal("amount"),
                        rs.getString("narrative"),
                        TransactionType.valueOf(
                            rs.getString("transaction_type")
                        ),
                        (UUID) rs.getObject("source_dataset_id")
                    );
                    double distance = rs.getDouble("distance");
                    results.add(new Object[] { tx, distance });
                }
            }
        }
        return results;
    }

    /**
     * Helper to format float[] into PostgreSQL vector literal representation: '[0.1, 0.2, ...]'
     */
    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // --- Original Stubs Below ---

    public void saveClientOrganization(ClientOrganization org) {
        String sql =
            "INSERT INTO client_organization (org_id, name) VALUES (?, ?) ON CONFLICT (org_id) DO UPDATE SET name = EXCLUDED.name";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, org.getOrgId());
            pstmt.setString(2, org.getName());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save client organization '" + org.getName() + "': " + e.getMessage(), e);
        }
    }

    public ClientOrganization findClientOrganizationById(UUID id) {
        String sql =
            "SELECT org_id, name FROM client_organization WHERE org_id = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new ClientOrganization(
                        (UUID) rs.getObject("org_id"),
                        rs.getString("name"),
                        null
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("findClientOrganizationById failed: " + e.getMessage());
        }
        return null;
    }

    public void saveReconciliationWorkspace(ReconciliationWorkspace workspace) {
        String sql =
            "INSERT INTO reconciliation_workspace (workspace_id, org_id, status) VALUES (?, ?, ?) ON CONFLICT (workspace_id) DO UPDATE SET status = EXCLUDED.status";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, workspace.getWorkspaceId());
            pstmt.setObject(
                2,
                workspace.getClientOrganization() != null
                    ? workspace.getClientOrganization().getOrgId()
                    : null
            );
            pstmt.setString(3, workspace.getStatus().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save reconciliation workspace: " + e.getMessage(), e);
        }
    }

    public ReconciliationWorkspace findReconciliationWorkspaceById(UUID id) {
        String sql =
            "SELECT workspace_id, org_id, status FROM reconciliation_workspace WHERE workspace_id = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UUID orgId = (UUID) rs.getObject("org_id");
                    ClientOrganization org = findClientOrganizationById(orgId);
                    return new ReconciliationWorkspace(
                        (UUID) rs.getObject("workspace_id"),
                        org,
                        null
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("findReconciliationWorkspaceById failed: " + e.getMessage());
        }
        return null;
    }

    public void saveFinancialDataset(FinancialDataset dataset) {
        saveFinancialDataset(dataset, null);
    }

    public void saveFinancialDataset(
        FinancialDataset dataset,
        UUID workspaceId
    ) {
        String sql =
            "INSERT INTO financial_dataset (dataset_id, workspace_id, file_path, source_type, status, import_date) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (dataset_id) DO UPDATE SET workspace_id = EXCLUDED.workspace_id";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, dataset.getDatasetId());
            pstmt.setObject(2, workspaceId);
            pstmt.setString(3, dataset.getFilePath());
            pstmt.setString(4, dataset.getSourceType().name());
            pstmt.setString(5, dataset.getStatus().name());
            pstmt.setDate(6, java.sql.Date.valueOf(dataset.getImportDate()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save financial dataset '" + dataset.getFilePath() + "': " + e.getMessage(), e);
        }
    }

    public void saveRawTransactions(List<RawTransaction> rawTransactions) {
        String sql =
            "INSERT INTO raw_transactions (transaction_id, raw_date, raw_amount, narrative, transaction_type, source_dataset_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            for (RawTransaction tx : rawTransactions) {
                pstmt.setObject(1, tx.getTransactionId());
                pstmt.setString(2, tx.getRawDate());
                pstmt.setString(3, tx.getRawAmount());
                pstmt.setString(4, tx.getNarrative());
                pstmt.setString(5, tx.getTransactionType().name());
                pstmt.setObject(6, tx.getSourceDataset().getDatasetId());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save raw transactions: " + e.getMessage(), e);
        }
    }

    public void saveStandardizedTransactions(
        List<StandardizedTransaction> standardizedTransactions
    ) {
        // Note: Individual transactions are saved via saveStandardizedLedgerTransaction
        // and saveStandardizedBankTransaction to handle specific pgvector requirements.
    }

    public void saveMatchHypotheses(List<MatchHypothesis> hypotheses) {
        String sql =
            "INSERT INTO match_hypotheses (hypothesis_id, ledger_id, bank_id, confidence_score, match_type, status, justification) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (hypothesis_id) DO UPDATE SET status = EXCLUDED.status, justification = EXCLUDED.justification";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            for (MatchHypothesis h : hypotheses) {
                pstmt.setObject(1, h.getHypothesisId());
                pstmt.setObject(
                    2,
                    h.getLedgerTransaction() != null
                        ? h.getLedgerTransaction().getTransactionId()
                        : null
                );
                pstmt.setObject(
                    3,
                    h.getBankTransaction() != null
                        ? h.getBankTransaction().getTransactionId()
                        : null
                );
                pstmt.setDouble(4, h.getConfidenceScore());
                pstmt.setString(
                    5,
                    h.getMatchType() != null ? h.getMatchType().name() : null
                );
                pstmt.setString(
                    6,
                    h.getStatus() != null ? h.getStatus().name() : null
                );
                pstmt.setString(7, h.getJustification());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save match hypotheses: " + e.getMessage(), e);
        }
    }

    public void saveReconciliationRecords(List<ReconciliationRecord> records) {
        String sql =
            "INSERT INTO reconciliation_records (record_id, hypothesis_id, confirming_user_id, reconciled_at) VALUES (?, ?, ?, ?)";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            for (ReconciliationRecord r : records) {
                pstmt.setObject(1, r.getRecordId());
                pstmt.setObject(
                    2,
                    r.getHypothesis() != null
                        ? r.getHypothesis().getHypothesisId()
                        : null
                );
                pstmt.setObject(
                    3,
                    r.getConfirmingUser() != null
                        ? r.getConfirmingUser().getUserId()
                        : null
                );
                pstmt.setTimestamp(
                    4,
                    r.getReconciledAt() != null
                        ? java.sql.Timestamp.valueOf(r.getReconciledAt())
                        : null
                );
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save reconciliation records: " + e.getMessage(), e);
        }
    }

    // â”€â”€ UPDATE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * UC7 / UC8 â€” Updates the status and justification of an existing match hypothesis.
     * Called after a user approves, rejects, or force-reconciles a hypothesis.
     */
    public void updateHypothesisStatus(UUID hypothesisId,
                                       aval.common.enums.HypothesisStatus status,
                                       String justification) throws SQLException {
        String sql = "UPDATE match_hypotheses SET status = ?, justification = ? WHERE hypothesis_id = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, justification);
            pstmt.setObject(3, hypothesisId);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("updateHypothesisStatus: no row found for hypothesis_id = " + hypothesisId);
            }
        }
    }

    /**
     * UC1 â€” Updates the status of a reconciliation workspace (e.g. OPEN â†’ COMPLETED).
     */
    public void updateWorkspaceStatus(UUID workspaceId,
                                      aval.common.enums.WorkspaceStatus status) throws SQLException {
        String sql = "UPDATE reconciliation_workspace SET status = ? WHERE workspace_id = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, status.name());
            pstmt.setObject(2, workspaceId);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("updateWorkspaceStatus: no row found for workspace_id = " + workspaceId);
            }
        }
    }

    // â”€â”€ DELETE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Deletes all financial datasets belonging to a workspace, then deletes the workspace itself.
     * Child datasets are removed first to respect foreign-key constraints.
     */
    public void deleteReconciliationWorkspace(UUID workspaceId) throws SQLException {
        String deleteDatasets  = "DELETE FROM financial_dataset WHERE workspace_id = ?";
        String deleteWorkspace = "DELETE FROM reconciliation_workspace WHERE workspace_id = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement ds = conn.prepareStatement(deleteDatasets);
            PreparedStatement ws = conn.prepareStatement(deleteWorkspace)
        ) {
            ds.setObject(1, workspaceId);
            ds.executeUpdate();
            ws.setObject(1, workspaceId);
            int rows = ws.executeUpdate();
            if (rows == 0) {
                throw new SQLException("deleteReconciliationWorkspace: no workspace found for id = " + workspaceId);
            }
        }
    }

    /**
     * Deletes a single financial dataset record by its ID.
     */
    public void deleteFinancialDataset(UUID datasetId) throws SQLException {
        String sql = "DELETE FROM financial_dataset WHERE dataset_id = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, datasetId);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("deleteFinancialDataset: no dataset found for id = " + datasetId);
            }
        }
    }

    // â”€â”€ HISTORY â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static class ReconciliationHistoryRow {
        public String date;
        public String period;
        public String txns;
        public String matched;
        public String rate;
        public String anomalies;
        public String status;

        public ReconciliationHistoryRow(String date, String period, String txns,
                                       String matched, String rate, String anomalies, String status) {
            this.date = date;
            this.period = period;
            this.txns = txns;
            this.matched = matched;
            this.rate = rate;
            this.anomalies = anomalies;
            this.status = status;
        }
    }

    public List<ReconciliationHistoryRow> getReconciliationHistoryForOrg(UUID orgId) {
        List<ReconciliationHistoryRow> rows = new ArrayList<>();
        String sql =
            "SELECT " +
            "  DATE(rr.reconciled_at) AS rec_date, " +
            "  rw.workspace_id, " +
            "  COUNT(mh.hypothesis_id) AS total_hyp, " +
            "  COUNT(CASE WHEN mh.status IN ('AUTO_RECONCILED','APPROVED') THEN 1 END) AS matched_hyp, " +
            "  ROUND(100.0 * COUNT(CASE WHEN mh.status IN ('AUTO_RECONCILED','APPROVED') THEN 1 END) / NULLIF(COUNT(mh.hypothesis_id),0), 1) AS rate, " +
            "  rw.status " +
            "FROM reconciliation_workspace rw " +
            "JOIN match_hypotheses mh ON mh.ledger_id IN ( " +
            "  SELECT transaction_id FROM standardized_ledger " +
            "  WHERE source_dataset_id IN ( " +
            "    SELECT dataset_id FROM financial_dataset " +
            "    WHERE workspace_id = rw.workspace_id)) " +
            "JOIN reconciliation_records rr ON rr.hypothesis_id = mh.hypothesis_id " +
            "WHERE rw.org_id = ? " +
            "GROUP BY rec_date, rw.workspace_id, rw.status " +
            "ORDER BY rec_date DESC " +
            "LIMIT 10";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setObject(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long total = rs.getLong("total_hyp");
                    long matched = rs.getLong("matched_hyp");
                    double rateVal = rs.getDouble("rate");
                    long anom = total - matched;
                    rows.add(new ReconciliationHistoryRow(
                        rs.getString("rec_date"),
                        rs.getString("rec_date").substring(0, 7),
                        String.valueOf(total),
                        String.valueOf(matched),
                        String.format("%.1f%%", rateVal),
                        String.valueOf(anom),
                        rs.getString("status")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("getReconciliationHistoryForOrg failed: " + e.getMessage());
        }
        return rows;
    }

    public List<String> getReconciliationHistory() {
        List<String> results = new ArrayList<>();
        String sql =
            "SELECT rr.record_id, rr.reconciled_at, mh.confidence_score, mh.match_type " +
            "FROM reconciliation_records rr JOIN match_hypotheses mh ON rr.hypothesis_id = mh.hypothesis_id " +
            "ORDER BY rr.reconciled_at DESC LIMIT 50";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()
        ) {
            while (rs.next()) {
                results.add(
                    String.format(
                        "[%s] Type: %s | Confidence: %.2f",
                        rs.getTimestamp("reconciled_at"),
                        rs.getString("match_type"),
                        rs.getDouble("confidence_score")
                    )
                );
            }
        } catch (SQLException e) {
            System.err.println("getReconciliationHistory failed: " + e.getMessage());
        }
        return results;
    }

    public SystemUser findSystemUserById(UUID id) {
        String sql =
            "SELECT user_id, full_name, cnic, username, role, location FROM app_user WHERE user_id = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new SystemUser((UUID) rs.getObject("user_id"), rs.getString("full_name"), rs.getString("cnic"), rs.getString("username"), aval.common.enums.UserRole.valueOf(rs.getString("role")), rs.getString("location"));
                }
            }
        } catch (SQLException e) {
            System.err.println("findSystemUserById failed: " + e.getMessage());
        }
        return null;
    }

    // â”€â”€ READ LOOKUPS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Fetches all client organizations ordered by display name.
     */
    public List<ClientOrganization> findAllClients() {
        List<ClientOrganization> clients = new ArrayList<>();
        String sql = "SELECT org_id, name FROM client_organization ORDER BY name";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()
        ) {
            while (rs.next()) {
                clients.add(
                    new ClientOrganization(
                        (UUID) rs.getObject("org_id"),
                        rs.getString("name"),
                        ""
                    )
                );
            }
        } catch (SQLException e) {
            System.err.println("findAllClients failed: " + e.getMessage());
        }
        return clients;
    }

    /**
     * Resolves a username to a SystemUser record used during authentication.
     */
    public SystemUser findSystemUserByUsername(String username) {
        String sql = "SELECT user_id, full_name, cnic, username, role, location FROM app_user WHERE username = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new SystemUser((UUID) rs.getObject("user_id"), rs.getString("full_name"), rs.getString("cnic"), rs.getString("username"), UserRole.valueOf(rs.getString("role")), rs.getString("location"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "findSystemUserByUsername failed: " + e.getMessage(),
                e
            );
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                "findSystemUserByUsername failed due to invalid role mapping: " +
                e.getMessage(),
                e
            );
        }
        return null;
    }

    public SystemUser findSystemUserByCredentials(
        String username,
        String passkey
    ) {
        String sql =
            "SELECT user_id, full_name, cnic, username, role, location, password_hash FROM app_user WHERE username = ?";
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (storedHash != null && BCrypt.checkpw(passkey, storedHash)) {
                        return new SystemUser((UUID) rs.getObject("user_id"), rs.getString("full_name"), rs.getString("cnic"), rs.getString("username"), UserRole.valueOf(rs.getString("role")), rs.getString("location"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "findSystemUserByCredentials failed: " + e.getMessage(),
                e
            );
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                "findSystemUserByCredentials failed due to invalid role mapping: " +
                e.getMessage(),
                e
            );
        }
        return null;
    }
    public boolean isAnyUserRegistered() {
        String sql = "SELECT COUNT(*) FROM app_user";
        try (
            java.sql.Connection conn = dataSource.getConnection();
            java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
            java.sql.ResultSet rs = pstmt.executeQuery()
        ) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Failed to check user: " + e.getMessage());
        }
        return false;
    }
    public void registerUser(String fullName, String cnic, String username, aval.common.enums.UserRole role, String location, String passwordHash) {
        String sql = "INSERT INTO app_user (user_id, full_name, cnic, username, role, location, password_hash) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (
            java.sql.Connection conn = dataSource.getConnection();
            java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setObject(1, java.util.UUID.randomUUID());
            pstmt.setString(2, fullName);
            pstmt.setString(3, cnic);
            pstmt.setString(4, username);
            pstmt.setString(5, role.name());
            pstmt.setString(6, location);
            pstmt.setString(7, passwordHash);
            pstmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to register user: " + e.getMessage(), e);
        }
    }

    /**
     * Issue 1 Fix: Retrieves the latest workspace for a client org.
     */
    public java.util.Optional<ReconciliationWorkspace> findLatestWorkspaceForClient(java.util.UUID clientId) {
        String sql = "SELECT workspace_id, status FROM reconciliation_workspace"
                   + " WHERE org_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, clientId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ClientOrganization org = findClientOrganizationById(clientId);
                ReconciliationWorkspace ws = new ReconciliationWorkspace(
                    (java.util.UUID) rs.getObject("workspace_id"),
                    org,
                    null
                );
                return java.util.Optional.of(ws);
            }
        } catch (SQLException e) {
            throw new RuntimeException("findLatestWorkspaceForClient failed: " + e.getMessage(), e);
        }
        return java.util.Optional.empty();
    }

    /**
     * Issue 1 Fix: Retrieves ledger transactions for a workspace.
     */
    public List<StandardizedTransaction> getLedgerTransactionsForWorkspace(java.util.UUID workspaceId) {
        String sql = "SELECT sl.transaction_id, sl.value_date, sl.amount, sl.narrative, sl.transaction_type, sl.source_dataset_id"
                   + " FROM standardized_ledger sl"
                   + " JOIN financial_dataset fd ON sl.source_dataset_id = fd.dataset_id"
                   + " WHERE fd.workspace_id = ?";
        List<StandardizedTransaction> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new StandardizedTransaction(
                    (java.util.UUID) rs.getObject("transaction_id"),
                    rs.getDate("value_date").toLocalDate(),
                    rs.getBigDecimal("amount"),
                    rs.getString("narrative"),
                    TransactionType.valueOf(rs.getString("transaction_type")),
                    (java.util.UUID) rs.getObject("source_dataset_id")
                ));
            }
        } catch (SQLException e) {
            System.err.println("getLedgerTransactionsForWorkspace failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Issue 1 Fix: Retrieves bank transactions for a workspace.
     */
    public List<StandardizedTransaction> getBankTransactionsForWorkspace(java.util.UUID workspaceId) {
        String sql = "SELECT sb.transaction_id, sb.value_date, sb.amount, sb.narrative, sb.transaction_type, sb.source_dataset_id"
                   + " FROM standardized_bank sb"
                   + " JOIN financial_dataset fd ON sb.source_dataset_id = fd.dataset_id"
                   + " WHERE fd.workspace_id = ?";
        List<StandardizedTransaction> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new StandardizedTransaction(
                    (java.util.UUID) rs.getObject("transaction_id"),
                    rs.getDate("value_date").toLocalDate(),
                    rs.getBigDecimal("amount"),
                    rs.getString("narrative"),
                    TransactionType.valueOf(rs.getString("transaction_type")),
                    (java.util.UUID) rs.getObject("source_dataset_id")
                ));
            }
        } catch (SQLException e) {
            System.err.println("getBankTransactionsForWorkspace failed: " + e.getMessage());
        }
        return result;
    }
}

