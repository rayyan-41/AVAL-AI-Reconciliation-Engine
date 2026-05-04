package aval.domain.ingestion;

import aval.common.enums.DatasetStatus;
import aval.common.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for RawTransaction — amount parsing and embedding text.
 */
public class RawTransactionTest {

    private RawInternalLedger dummyDataset;

    @BeforeEach
    void setUp() {
        dummyDataset = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), "/dummy.csv",
            DatasetStatus.PARSED, "SAP", "FY2024"
        );
    }

    private RawTransaction raw(String rawAmount) {
        return new RawTransaction(UUID.randomUUID(), "2024-01-15", rawAmount,
            "Test payment", TransactionType.DEBIT, dummyDataset);
    }

    // ── hasValidAmount ────────────────────────────────────────────────────────

    @Test
    void hasValidAmount_simpleDecimal_returnsTrue() {
        assertTrue(raw("1500.00").hasValidAmount());
    }

    @Test
    void hasValidAmount_wholeNumber_returnsTrue() {
        assertTrue(raw("200").hasValidAmount());
    }

    @Test
    void hasValidAmount_withCommas_returnsTrue() {
        assertTrue(raw("1,500.00").hasValidAmount());
    }

    @Test
    void hasValidAmount_withCurrencySymbol_returnsTrue() {
        assertTrue(raw("$500.00").hasValidAmount());
    }

    @Test
    void hasValidAmount_null_returnsFalse() {
        assertFalse(raw(null).hasValidAmount());
    }

    @Test
    void hasValidAmount_empty_returnsFalse() {
        assertFalse(raw("").hasValidAmount());
    }

    @Test
    void hasValidAmount_whitespaceOnly_returnsFalse() {
        assertFalse(raw("   ").hasValidAmount());
    }

    @Test
    void hasValidAmount_textOnly_returnsFalse() {
        assertFalse(raw("N/A").hasValidAmount());
    }

    @Test
    void hasValidAmount_dash_returnsFalse() {
        assertFalse(raw("-").hasValidAmount());
    }

    @Test
    void hasValidAmount_justDot_returnsFalse() {
        assertFalse(raw(".").hasValidAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "0", "0.01", "99999999.99"})
    void hasValidAmount_variousValidFormats_returnTrue(String amount) {
        assertTrue(raw(amount).hasValidAmount(), "Expected valid: " + amount);
    }

    // ── getParsedAmount ────────────────────────────────────────────────────────

    @Test
    void getParsedAmount_simpleDecimal_correct() {
        assertEquals(new BigDecimal("1500.00"), raw("1500.00").getParsedAmount());
    }

    @Test
    void getParsedAmount_withCommas_stripsCommas() {
        assertEquals(new BigDecimal("150000"), raw("150,000").getParsedAmount());
    }

    @Test
    void getParsedAmount_withCurrencySymbol_stripsSymbol() {
        BigDecimal result = raw("$250.75").getParsedAmount();
        assertEquals(new BigDecimal("250.75"), result);
    }

    @Test
    void getParsedAmount_invalidAmount_returnsZero() {
        assertEquals(BigDecimal.ZERO, raw("N/A").getParsedAmount());
    }

    @Test
    void getParsedAmount_null_returnsZero() {
        assertEquals(BigDecimal.ZERO, raw(null).getParsedAmount());
    }

    @Test
    void getParsedAmount_emptyString_returnsZero() {
        assertEquals(BigDecimal.ZERO, raw("").getParsedAmount());
    }

    @Test
    void getParsedAmount_largeNumber_parsedCorrectly() {
        BigDecimal result = raw("9999999.99").getParsedAmount();
        assertEquals(new BigDecimal("9999999.99"), result);
    }

    @Test
    void getParsedAmount_zero_parsedCorrectly() {
        assertEquals(new BigDecimal("0.00"), raw("0.00").getParsedAmount());
    }

    // ── toEmbeddingInputText ──────────────────────────────────────────────────

    @Test
    void toEmbeddingInputText_includesDate() {
        RawTransaction tx = new RawTransaction(UUID.randomUUID(), "2024-01-15",
            "500.00", "Office Rent", TransactionType.DEBIT, dummyDataset);
        assertTrue(tx.toEmbeddingInputText().contains("2024-01-15"));
    }

    @Test
    void toEmbeddingInputText_includesNarrative() {
        RawTransaction tx = new RawTransaction(UUID.randomUUID(), "2024-01-15",
            "500.00", "Office Rent", TransactionType.DEBIT, dummyDataset);
        assertTrue(tx.toEmbeddingInputText().contains("Office Rent"));
    }

    @Test
    void toEmbeddingInputText_includesAmount() {
        RawTransaction tx = new RawTransaction(UUID.randomUUID(), "2024-01-15",
            "500.00", "Office Rent", TransactionType.DEBIT, dummyDataset);
        assertTrue(tx.toEmbeddingInputText().contains("500.00"));
    }

    @Test
    void toEmbeddingInputText_withNullDate_doesNotThrow() {
        RawTransaction tx = new RawTransaction(UUID.randomUUID(), null,
            "500.00", "Test", TransactionType.DEBIT, dummyDataset);
        assertDoesNotThrow(tx::toEmbeddingInputText);
    }

    @Test
    void toEmbeddingInputText_withNullNarrative_doesNotThrow() {
        RawTransaction tx = new RawTransaction(UUID.randomUUID(), "2024-01-01",
            "100.00", null, TransactionType.DEBIT, dummyDataset);
        assertDoesNotThrow(tx::toEmbeddingInputText);
        assertNotNull(tx.toEmbeddingInputText());
    }

    @Test
    void toEmbeddingInputText_withNullAmount_doesNotThrow() {
        RawTransaction tx = new RawTransaction(UUID.randomUUID(), "2024-01-01",
            null, "Test", TransactionType.DEBIT, dummyDataset);
        assertDoesNotThrow(tx::toEmbeddingInputText);
    }

    @Test
    void toEmbeddingInputText_usesBarDelimiter() {
        RawTransaction tx = new RawTransaction(UUID.randomUUID(), "2024-01-15",
            "500.00", "Rent", TransactionType.DEBIT, dummyDataset);
        String text = tx.toEmbeddingInputText();
        assertTrue(text.contains("|"), "Embedding text should use '|' as delimiter");
    }

    // ── getters ───────────────────────────────────────────────────────────────

    @Test
    void getters_returnCorrectValues() {
        UUID id = UUID.randomUUID();
        RawTransaction tx = new RawTransaction(
            id, "2024-06-01", "999.99", "Some narrative", TransactionType.CREDIT, dummyDataset
        );
        assertEquals(id, tx.getTransactionId());
        assertEquals("2024-06-01", tx.getRawDate());
        assertEquals("999.99", tx.getRawAmount());
        assertEquals("Some narrative", tx.getNarrative());
        assertEquals(TransactionType.CREDIT, tx.getTransactionType());
        assertSame(dummyDataset, tx.getSourceDataset());
    }
}
