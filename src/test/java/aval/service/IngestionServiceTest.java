package aval.service;

import aval.common.enums.DataSourceType;
import aval.common.enums.DatasetStatus;
import aval.common.enums.TransactionType;
import aval.domain.ai.SemanticEmbedding;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.ingestion.FinancialDataset;
import aval.domain.ingestion.RawBankStatement;
import aval.domain.ingestion.RawInternalLedger;
import aval.domain.ingestion.RawTransaction;
import aval.engine.VectorizationEngine;
import aval.persistence.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for IngestionService — standardization pipeline (UC4).
 */
@ExtendWith(MockitoExtension.class)
public class IngestionServiceTest {

    @Mock private DataStore mockDataStore;
    @Mock private VectorizationEngine mockVectorEngine;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(mockDataStore, mockVectorEngine);
    }

    // ── helper: create a dataset with raw transactions ────────────────────────

    private RawInternalLedger createLedgerWithTransactions(RawTransaction... txns) throws Exception {
        RawInternalLedger ledger = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), "/test.xlsx",
            DatasetStatus.PARSED, "SAP", "FY2024"
        );
        java.lang.reflect.Field field = aval.domain.ingestion.FinancialDataset.class.getDeclaredField("rawTransactions");
        field.setAccessible(true);
        java.util.List<RawTransaction> list = new java.util.ArrayList<>(java.util.Arrays.asList(txns));
        field.set(ledger, list);
        return ledger;
    }

    private RawBankStatement createBankWithTransactions(RawTransaction... txns) throws Exception {
        RawBankStatement bank = new RawBankStatement(
            UUID.randomUUID(), LocalDate.now(), "/test.pdf",
            DatasetStatus.PARSED, "HBL", "1234567890",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        java.lang.reflect.Field field = aval.domain.ingestion.FinancialDataset.class.getDeclaredField("rawTransactions");
        field.setAccessible(true);
        java.util.List<RawTransaction> list = new java.util.ArrayList<>(java.util.Arrays.asList(txns));
        field.set(bank, list);
        return bank;
    }

    private RawTransaction rawTx(String date, String amount, FinancialDataset dataset) {
        return new RawTransaction(UUID.randomUUID(), date, amount, "Test narrative", TransactionType.DEBIT, dataset);
    }

    private SemanticEmbedding dummyEmbedding(UUID txId) {
        return new SemanticEmbedding(txId, new float[]{0.1f, 0.2f, 0.3f});
    }

    // ── standardize — happy path ──────────────────────────────────────────────

    @Test
    void standardize_validTransactions_returnsCorrectCount() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-01-15", "500.00", null),
            rawTx("2024-02-01", "300.00", null)
        );
        // Fix source dataset back reference
        for (RawTransaction t : ledger.getRawTransactions()) {
            setSource(t, ledger);
        }

        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);

        assertEquals(2, result.size());
    }

    @Test
    void standardize_invalidAmount_isSkipped() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-01-15", "500.00", null),
            rawTx("2024-01-16", "N/A", null),      // invalid
            rawTx("2024-01-17", "", null)            // invalid
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);

        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);

        assertEquals(1, result.size(), "Only 1 valid transaction should be standardized");
    }

    @Test
    void standardize_nullAmount_isSkipped() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-01-15", "750.00", null),
            rawTx("2024-01-16", null, null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);

        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);

        assertEquals(1, result.size());
    }

    @Test
    void standardize_marksDatasetAsStandardized() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-01-15", "100.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);

        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        service.standardize(ledger);

        assertEquals(DatasetStatus.STANDARDIZED, ledger.getStatus());
    }

    @Test
    void standardize_addsResultsToDatasetStandardizedList() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-01-15", "500.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);

        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        service.standardize(ledger);

        assertFalse(ledger.getStandardizedTransactions().isEmpty());
    }

    // ── date parsing ─────────────────────────────────────────────────────────

    @Test
    void standardize_dateFormat_MM_dd_yyyy_parsedCorrectly() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("01/15/2024", "100.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);
        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);
        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2024, 1, 15), result.get(0).getValueDate());
    }

    @Test
    void standardize_dateFormat_yyyy_MM_dd_parsedCorrectly() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-06-30", "200.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);
        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);
        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2024, 6, 30), result.get(0).getValueDate());
    }

    @Test
    void standardize_dateFormat_dd_MM_yyyy_parsedCorrectly() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("31/12/2024", "300.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);
        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);
        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2024, 12, 31), result.get(0).getValueDate());
    }

    @Test
    void standardize_unparsableDate_fallsBackToToday() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("INVALID_DATE", "100.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);
        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);

        assertEquals(1, result.size());
        assertEquals(LocalDate.now(), result.get(0).getValueDate(), "Unparsable date should fall back to today");
    }

    @Test
    void standardize_nullDate_fallsBackToToday() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx(null, "100.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);
        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        List<StandardizedTransaction> result = service.standardize(ledger);

        assertEquals(1, result.size());
        assertEquals(LocalDate.now(), result.get(0).getValueDate());
    }

    // ── bank dataset routing ──────────────────────────────────────────────────

    @Test
    void standardize_bankDataset_callsSaveBankTransaction() throws Exception {
        RawBankStatement bank = createBankWithTransactions(
            rawTx("2024-01-15", "500.00", null)
        );
        for (RawTransaction t : bank.getRawTransactions()) setSource(t, bank);

        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedBankTransaction(any(), any());

        service.standardize(bank);

        verify(mockDataStore).saveStandardizedBankTransaction(any(), any());
        verify(mockDataStore, never()).saveStandardizedLedgerTransaction(any(), any());
    }

    @Test
    void standardize_ledgerDataset_callsSaveLedgerTransaction() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-01-15", "500.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);

        SemanticEmbedding emb = dummyEmbedding(UUID.randomUUID());
        when(mockVectorEngine.vectorize(any())).thenReturn(emb);
        doNothing().when(mockDataStore).saveStandardizedLedgerTransaction(any(), any());

        service.standardize(ledger);

        verify(mockDataStore).saveStandardizedLedgerTransaction(any(), any());
        verify(mockDataStore, never()).saveStandardizedBankTransaction(any(), any());
    }

    // ── vectorization failure tolerance ──────────────────────────────────────

    @Test
    void standardize_vectorizationFails_doesNotThrow() throws Exception {
        RawInternalLedger ledger = createLedgerWithTransactions(
            rawTx("2024-01-15", "100.00", null)
        );
        for (RawTransaction t : ledger.getRawTransactions()) setSource(t, ledger);

        when(mockVectorEngine.vectorize(any())).thenThrow(new RuntimeException("Ollama down"));

        // Should not throw; vectorization failure is logged and skipped
        assertDoesNotThrow(() -> {
            List<StandardizedTransaction> result = service.standardize(ledger);
            assertEquals(1, result.size(), "Transaction should still be standardized even if vectorization fails");
        });
    }

    // ── ingestFile — invalid source type ─────────────────────────────────────

    @Test
    void ingestFile_unsupportedSourceType_throwsIllegalArgument() {
        // INTERNAL_CSV is not handled by createParser → should throw
        assertThrows(IllegalArgumentException.class, () ->
            service.ingestFile(UUID.randomUUID(), "/test.json", DataSourceType.INTERNAL_CSV)
        );
    }

    // ── helper reflection ─────────────────────────────────────────────────────

    private void setSource(RawTransaction tx, FinancialDataset dataset) throws Exception {
        java.lang.reflect.Field field = RawTransaction.class.getDeclaredField("sourceDataset");
        field.setAccessible(true);
        field.set(tx, dataset);
    }
}
