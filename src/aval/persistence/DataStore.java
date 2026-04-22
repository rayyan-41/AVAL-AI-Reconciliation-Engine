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
     * Finds the closest bank transactions to a given ledger transaction's semantic embedding.
     */
    public List<StandardizedTransaction> findSimilarBankTransactions(
        SemanticEmbedding ledgerEmbedding,
        int limit
    ) throws SQLException {
        String sql =
            "SELECT transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id " +
            "FROM standardized_bank " +
            "ORDER BY embedding <=> ?::vector " +
            "LIMIT ?";

        List<StandardizedTransaction> results = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, formatVector(ledgerEmbedding.getVector()));
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(
                        new StandardizedTransaction(
                            (UUID) rs.getObject("transaction_id"),
                            rs.getDate("value_date").toLocalDate(),
                            rs.getBigDecimal("amount"),
                            rs.getString("narrative"),
                            TransactionType.valueOf(
                                rs.getString("transaction_type")
                            ),
                            (UUID) rs.getObject("source_dataset_id")
                        )
                    );
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

    public void saveClientOrganization(ClientOrganization org) {}

    public ClientOrganization findClientOrganizationById(UUID id) {
        return null;
    }

    public void saveReconciliationWorkspace(
        ReconciliationWorkspace workspace
    ) {}

    public ReconciliationWorkspace findReconciliationWorkspaceById(UUID id) {
        return null;
    }

    public void saveFinancialDataset(FinancialDataset dataset) {}

    public void saveRawTransactions(List<RawTransaction> rawTransactions) {}

    public void saveStandardizedTransactions(
        List<StandardizedTransaction> standardizedTransactions
    ) {}

    public void saveMatchHypotheses(List<MatchHypothesis> hypotheses) {}

    public void saveReconciliationRecords(List<ReconciliationRecord> records) {}

    public SystemUser findSystemUserById(UUID id) {
        return null;
    }
}
