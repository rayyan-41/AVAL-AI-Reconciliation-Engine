package aval.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.ingestion.FinancialDataset;
import aval.domain.ingestion.RawInternalLedger;
import aval.domain.ingestion.RawTransaction;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for Issue 4: No JDBC Transaction Wrapping.
 * Verifies that failed saves result in proper rollback with no orphaned data.
 */
public class TransactionConsistencyTest {

    @Test
    public void testSaveDataset_createsBothRecords_onSuccess()
        throws Exception {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

        DataStore ds = new DataStore(mockDs);
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        RawInternalLedger dataset = new RawInternalLedger(
            dsId,
            LocalDate.now(),
            "/test.csv",
            aval.common.enums.DatasetStatus.PARSED,
            "SAP",
            "Q1"
        );

        java.lang.reflect.Field field = FinancialDataset.class.getDeclaredField(
            "rawTransactions"
        );
        field.setAccessible(true);
        java.util.List<RawTransaction> rawList = new java.util.ArrayList<>();
        RawTransaction rawTx = new RawTransaction(
            txId,
            "2024-01-15",
            "1500.00",
            "Test payment",
            TransactionType.DEBIT,
            dataset
        );
        rawList.add(rawTx);
        field.set(dataset, rawList);

        assertDoesNotThrow(() -> ds.saveDatasetWithTransactions(dataset, wsId));

        verify(mockConn).setAutoCommit(false);
        verify(mockConn).commit();
        verify(mockConn).close();
    }

    @Test
    public void testSaveMatchingResults_createsBothRecords_onSuccess()
        throws Exception {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

        DataStore ds = new DataStore(mockDs);

        StandardizedTransaction ledgerTx = new StandardizedTransaction(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.valueOf(100),
            "Test",
            TransactionType.DEBIT,
            UUID.randomUUID()
        );
        StandardizedTransaction bankTx = new StandardizedTransaction(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.valueOf(100),
            "Test",
            TransactionType.CREDIT,
            UUID.randomUUID()
        );
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx,
            bankTx,
            0.95,
            MatchType.EXACT_RULE
        );
        ReconciliationRecord record = new ReconciliationRecord(hyp, null);

        assertDoesNotThrow(() ->
            ds.saveMatchingResults(List.of(hyp), List.of(record))
        );

        verify(mockConn).setAutoCommit(false);
        verify(mockConn).commit();
        verify(mockConn).close();
    }

    @Test
    public void testSaveMatchingResults_usesAtomicTransaction()
        throws Exception {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt1 = mock(PreparedStatement.class);
        PreparedStatement mockStmt2 = mock(PreparedStatement.class);

        when(mockDs.getConnection()).thenReturn(mockConn);

        // Return stmt1 for hypotheses, stmt2 for records
        when(mockConn.prepareStatement(anyString()))
            .thenReturn(mockStmt1)
            .thenReturn(mockStmt2);

        // Make the second executeUpdate throw an exception
        when(mockStmt2.executeBatch()).thenThrow(
            new java.sql.SQLException("Simulated DB Error")
        );

        DataStore ds = new DataStore(mockDs);

        StandardizedTransaction ledgerTx = new StandardizedTransaction(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.valueOf(100),
            "Test",
            TransactionType.DEBIT,
            UUID.randomUUID()
        );
        StandardizedTransaction bankTx = new StandardizedTransaction(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.valueOf(100),
            "Test",
            TransactionType.CREDIT,
            UUID.randomUUID()
        );
        MatchHypothesis hyp = new MatchHypothesis(
            ledgerTx,
            bankTx,
            0.95,
            MatchType.EXACT_RULE
        );
        ReconciliationRecord record = new ReconciliationRecord(hyp, null);

        Exception e = assertThrows(RuntimeException.class, () ->
            ds.saveMatchingResults(List.of(hyp), List.of(record))
        );
        assertTrue(
            e.getMessage().contains("Matching results transaction failed"),
            "Should wrap in RuntimeException"
        );

        verify(mockConn).setAutoCommit(false);
        verify(mockConn).rollback();
        // Should not commit if rollback happened
        verify(mockConn, never()).commit();
    }
}
