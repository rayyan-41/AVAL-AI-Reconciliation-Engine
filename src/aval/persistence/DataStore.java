//Dev: Safwan
//Use Cases: All
package aval.persistence;

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

//@desc:   The sole repository holding a JDBC connection to execute SQL against the PostgreSQL database.
//@grasp:  Controller
//@gof:    Repository / DAO
public class DataStore {

    private Connection connection;

    public DataStore(Connection connection) {
        this.connection = connection;
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

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setObject(1, org.getOrgId());
            pstmt.setString(2, org.getName());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ClientOrganization findClientOrganizationById(UUID id) {
        String sql =
            "SELECT org_id, name FROM client_organization WHERE org_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
        return null;
    }

    public void saveReconciliationWorkspace(ReconciliationWorkspace workspace) {
        String sql =
            "INSERT INTO reconciliation_workspace (workspace_id, org_id, status) VALUES (?, ?, ?) ON CONFLICT (workspace_id) DO UPDATE SET status = EXCLUDED.status";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
    }

    public ReconciliationWorkspace findReconciliationWorkspaceById(UUID id) {
        String sql =
            "SELECT workspace_id, org_id, status FROM reconciliation_workspace WHERE workspace_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
        return null;
    }

    public void saveFinancialDataset(FinancialDataset dataset) {
        String sql =
            "INSERT INTO financial_dataset (dataset_id, workspace_id, file_path, source_type, status, import_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setObject(1, dataset.getDatasetId());
            pstmt.setObject(2, null);
            pstmt.setString(3, dataset.getFilePath());
            pstmt.setString(4, dataset.getSourceType().name());
            pstmt.setString(5, dataset.getStatus().name());
            pstmt.setDate(6, java.sql.Date.valueOf(dataset.getImportDate()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveRawTransactions(List<RawTransaction> rawTransactions) {
        String sql =
            "INSERT INTO raw_transactions (transaction_id, raw_date, raw_amount, narrative, transaction_type, source_dataset_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
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
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
    }

    public void saveReconciliationRecords(List<ReconciliationRecord> records) {
        String sql =
            "INSERT INTO reconciliation_records (record_id, hypothesis_id, confirming_user_id, reconciled_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
    }

    public SystemUser findSystemUserById(UUID id) {
        String sql =
            "SELECT user_id, username, role FROM system_user WHERE user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setObject(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new SystemUser(
                        (UUID) rs.getObject("user_id"),
                        rs.getString("username"),
                        aval.common.enums.UserRole.valueOf(rs.getString("role"))
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
