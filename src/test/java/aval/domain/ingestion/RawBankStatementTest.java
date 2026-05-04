package aval.domain.ingestion;

import aval.common.enums.DataSourceType;
import aval.common.enums.DatasetStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RawBankStatement — validation and metadata.
 */
public class RawBankStatementTest {

    private RawBankStatement statement(String bankName, String accountNumber,
                                       LocalDate start, LocalDate end) {
        return new RawBankStatement(
            UUID.randomUUID(), LocalDate.now(), "/test.pdf",
            DatasetStatus.PARSED, bankName, accountNumber, start, end
        );
    }

    // ── validate ───────────────────────────────────────────────────────────────

    @Test
    void validate_allFieldsPresent_returnsTrue() {
        RawBankStatement s = statement(
            "HBL", "1234567890",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        assertTrue(s.validate());
    }

    @Test
    void validate_nullBankName_returnsFalse() {
        RawBankStatement s = statement(
            null, "123456",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        assertFalse(s.validate());
    }

    @Test
    void validate_emptyBankName_returnsFalse() {
        RawBankStatement s = statement(
            "   ", "123456",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        assertFalse(s.validate());
    }

    @Test
    void validate_nullAccountNumber_returnsFalse() {
        RawBankStatement s = statement(
            "HBL", null,
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        assertFalse(s.validate());
    }

    @Test
    void validate_emptyAccountNumber_returnsFalse() {
        RawBankStatement s = statement(
            "HBL", "",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        assertFalse(s.validate());
    }

    @Test
    void validate_nullStartDate_returnsFalse() {
        RawBankStatement s = statement(
            "HBL", "123456", null, LocalDate.of(2024, 1, 31)
        );
        assertFalse(s.validate());
    }

    @Test
    void validate_nullEndDate_returnsFalse() {
        RawBankStatement s = statement(
            "HBL", "123456", LocalDate.of(2024, 1, 1), null
        );
        assertFalse(s.validate());
    }

    @Test
    void validate_startAfterEnd_returnsFalse() {
        RawBankStatement s = statement(
            "HBL", "123456",
            LocalDate.of(2024, 1, 31), LocalDate.of(2024, 1, 1)  // reversed
        );
        assertFalse(s.validate());
    }

    @Test
    void validate_startEqualsEnd_returnsTrue() {
        LocalDate same = LocalDate.of(2024, 6, 15);
        RawBankStatement s = statement("HBL", "123456", same, same);
        assertTrue(s.validate(), "Same start and end date should be valid (single-day statement)");
    }

    // ── source type ───────────────────────────────────────────────────────────

    @Test
    void getSourceType_returnsExternalPdf() {
        RawBankStatement s = statement(
            "MCB", "987654321",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        assertEquals(DataSourceType.EXTERNAL_PDF, s.getSourceType());
    }

    // ── extractTableRegion ────────────────────────────────────────────────────

    @Test
    void extractTableRegion_returnsNonEmptyList() {
        RawBankStatement s = statement(
            "UBL", "555555",
            LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)
        );
        List<String[]> regions = s.extractTableRegion();
        assertNotNull(regions);
        assertFalse(regions.isEmpty(), "extractTableRegion should return at least one region");
    }

    @Test
    void extractTableRegion_eachEntryHasFiveElements() {
        RawBankStatement s = statement(
            "UBL", "555555",
            LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)
        );
        for (String[] region : s.extractTableRegion()) {
            assertEquals(5, region.length,
                "Each region entry should have [name, x, y, width, height]");
        }
    }

    // ── inherited getters ─────────────────────────────────────────────────────

    @Test
    void getFilePath_returnsCorrectPath() {
        RawBankStatement s = new RawBankStatement(
            UUID.randomUUID(), LocalDate.now(), "/statements/jan2024.pdf",
            DatasetStatus.PARSED, "HBL", "11111",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        assertEquals("/statements/jan2024.pdf", s.getFilePath());
    }

    @Test
    void getDatasetId_isNotNull() {
        RawBankStatement s = statement("HBL", "123",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
        assertNotNull(s.getDatasetId());
    }

    @Test
    void rawTransactions_initiallyEmpty() {
        RawBankStatement s = statement("HBL", "123",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
        assertNotNull(s.getRawTransactions());
        assertTrue(s.getRawTransactions().isEmpty());
    }

    @Test
    void standardizedTransactions_initiallyEmpty() {
        RawBankStatement s = statement("HBL", "123",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
        assertNotNull(s.getStandardizedTransactions());
        assertTrue(s.getStandardizedTransactions().isEmpty());
    }

    @Test
    void markAsStandardized_changesStatus() {
        RawBankStatement s = statement("HBL", "123",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
        assertEquals(DatasetStatus.PARSED, s.getStatus());
        s.markAsStandardized();
        assertEquals(aval.common.enums.DatasetStatus.STANDARDIZED, s.getStatus());
    }
}
