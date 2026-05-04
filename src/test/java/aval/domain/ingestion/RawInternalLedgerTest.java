package aval.domain.ingestion;

import aval.common.enums.DataSourceType;
import aval.common.enums.DatasetStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RawInternalLedger — validation and delimiter detection.
 */
public class RawInternalLedgerTest {

    private RawInternalLedger ledger(String accountingSystem, String fiscalPeriod) {
        return new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), "/dummy.csv",
            DatasetStatus.PARSED, accountingSystem, fiscalPeriod
        );
    }

    // ── validate ───────────────────────────────────────────────────────────────

    @Test
    void validate_allFieldsPresent_returnsTrue() {
        assertTrue(ledger("SAP", "Q1-2024").validate());
    }

    @Test
    void validate_nullAccountingSystem_returnsFalse() {
        assertFalse(ledger(null, "Q1-2024").validate());
    }

    @Test
    void validate_emptyAccountingSystem_returnsFalse() {
        assertFalse(ledger("", "Q1-2024").validate());
    }

    @Test
    void validate_whitespaceAccountingSystem_returnsFalse() {
        assertFalse(ledger("   ", "Q1-2024").validate());
    }

    @Test
    void validate_nullFiscalPeriod_returnsFalse() {
        assertFalse(ledger("SAP", null).validate());
    }

    @Test
    void validate_emptyFiscalPeriod_returnsFalse() {
        assertFalse(ledger("SAP", "").validate());
    }

    @Test
    void validate_whitespaceFiscalPeriod_returnsFalse() {
        assertFalse(ledger("SAP", "   ").validate());
    }

    // ── source type ───────────────────────────────────────────────────────────

    @Test
    void getSourceType_returnsInternalExcel() {
        assertEquals(DataSourceType.INTERNAL_EXCEL, ledger("SAP", "Q1").getSourceType());
    }

    // ── detectDelimiter ───────────────────────────────────────────────────────

    @Test
    void detectDelimiter_commaFile_returnsComma(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, "Date,Amount,Description,Type\n2024-01-01,500.00,Rent,DEBIT\n");
        RawInternalLedger l = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), file.toString(),
            DatasetStatus.PARSED, "SAP", "Q1"
        );
        assertEquals(',', l.detectDelimiter());
    }

    @Test
    void detectDelimiter_semicolonFile_returnsSemicolon(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, "Date;Amount;Description;Type\n2024-01-01;500.00;Rent;DEBIT\n");
        RawInternalLedger l = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), file.toString(),
            DatasetStatus.PARSED, "SAP", "Q1"
        );
        assertEquals(';', l.detectDelimiter());
    }

    @Test
    void detectDelimiter_pipeFile_returnsPipe(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, "Date|Amount|Description|Type\n2024-01-01|500.00|Rent|DEBIT\n");
        RawInternalLedger l = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), file.toString(),
            DatasetStatus.PARSED, "SAP", "Q1"
        );
        assertEquals('|', l.detectDelimiter());
    }

    @Test
    void detectDelimiter_tabFile_returnsTab(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.tsv");
        Files.writeString(file, "Date\tAmount\tDescription\tType\n2024-01-01\t500.00\tRent\tDEBIT\n");
        RawInternalLedger l = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), file.toString(),
            DatasetStatus.PARSED, "SAP", "Q1"
        );
        assertEquals('\t', l.detectDelimiter());
    }

    @Test
    void detectDelimiter_nonExistentFile_returnsCommaFallback() {
        RawInternalLedger l = ledger("SAP", "Q1");
        // The path is /dummy.csv which doesn't exist
        assertEquals(',', l.detectDelimiter(), "Non-existent file should fall back to comma");
    }

    @Test
    void detectDelimiter_nullPath_returnsCommaFallback() {
        RawInternalLedger l = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), null,
            DatasetStatus.PARSED, "SAP", "Q1"
        );
        assertEquals(',', l.detectDelimiter());
    }

    @Test
    void detectDelimiter_emptyPath_returnsCommaFallback() {
        RawInternalLedger l = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), "   ",
            DatasetStatus.PARSED, "SAP", "Q1"
        );
        assertEquals(',', l.detectDelimiter());
    }

    @Test
    void detectDelimiter_emptyFile_returnsCommaFallback(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty.csv");
        Files.writeString(file, "");
        RawInternalLedger l = new RawInternalLedger(
            UUID.randomUUID(), LocalDate.now(), file.toString(),
            DatasetStatus.PARSED, "SAP", "Q1"
        );
        assertEquals(',', l.detectDelimiter());
    }

    // ── inheritance ───────────────────────────────────────────────────────────

    @Test
    void markAsStandardized_updatesStatus() {
        RawInternalLedger l = ledger("SAP", "Q1");
        assertEquals(DatasetStatus.PARSED, l.getStatus());
        l.markAsStandardized();
        assertEquals(DatasetStatus.STANDARDIZED, l.getStatus());
    }

    @Test
    void rawTransactions_initiallyEmpty() {
        assertTrue(ledger("SAP", "Q1").getRawTransactions().isEmpty());
    }
}
