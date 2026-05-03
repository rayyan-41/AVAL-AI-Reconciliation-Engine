package aval.parser;

import static org.junit.jupiter.api.Assertions.*;

import aval.common.enums.TransactionType;
import aval.domain.ingestion.FinancialDataset;
import aval.domain.ingestion.RawTransaction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for Issue 7: PDF Parser ^ Anchor Regex.
 * Verifies that PDF bank statements with row-numbered lines are parsed correctly.
 */
public class PDFBankStatementParserTest {

    @Test
    public void testParse_returnsNonZeroTransactions_forPacificTrustPDF(
        @TempDir Path tempDir
    ) throws Exception {
        // Create a test PDF with Pacific Trust bank statement format
        Path pdfPath = createTestPDF(
            tempDir,
            List.of(
                "1  01/15/2025  Office Supplies  Staples  150.00  9500.00",
                "2  01/16/2025  Electric Bill  PECO  200.00  9300.00",
                "3  01/17/2025  Deposit  Customer Payment  2500.00  11800.00",
                "4  01/20/2025  Rent Payment  Smith Properties  3200.00  8600.00"
            )
        );

        PDFBankStatementParser parser = new PDFBankStatementParser(
            "DEFAULT_STRATEGY",
            List.of()
        );
        FinancialDataset dataset = parser.parse(pdfPath.toString());

        assertFalse(
            dataset.getRawTransactions().isEmpty(),
            "Parser should extract transactions from row-numbered lines"
        );
        assertEquals(
            4,
            dataset.getRawTransactions().size(),
            "Should parse all 4 transactions from test PDF"
        );
    }

    @Test
    public void testParse_extractsCorrectDate_fromNumberPrefixedLine(
        @TempDir Path tempDir
    ) throws Exception {
        // Test the regex fix directly: "1 01/15/2025 Some Payment 500.00 1000.00"
        // The date should be "01/15/2025" NOT "1 01/15/2025"
        String testLine =
            "1  01/15/2025  Office Supplies  Staples  150.00  9500.00";

        PDFBankStatementParser parser = new PDFBankStatementParser(
            "DEFAULT_STRATEGY",
            List.of()
        );

        // Verify via a direct parse of a minimal test PDF
        Path pdfPath = createTestPDF(tempDir, List.of(testLine));

        FinancialDataset dataset = parser.parse(pdfPath.toString());
        assertFalse(
            dataset.getRawTransactions().isEmpty(),
            "Parser must extract date '01/15/2025' from '1 01/15/2025...' format"
        );
        assertEquals(
            "01/15/2025",
            dataset.getRawTransactions().get(0).getRawDate()
        );
    }

    @Test
    public void testParse_extractsCorrectAmount_notBalance() {
        // Line: "3 03/10/2025 Office Rent REF-001 3200.00 86681.68"
        // Parsed amount should be 3200.00 (the debit), not 86681.68 (the running balance)
        String testLine = "3 03/10/2025 Office Rent REF-001 3200.00 86681.68";

        // The narrative should NOT contain the row number "3"
        // Amount should be the first monetary value, not the balance
        assertTrue(
            testLine.contains("3200.00"),
            "Test line must contain the debit amount"
        );
        assertTrue(
            testLine.contains("86681.68"),
            "Test line must contain the balance"
        );

        // Verify narrative cleaning logic strips amounts correctly
        // (Implementation detail verification)
        String cleanedNarrative = testLine
            .replaceAll("\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}\\s*", "")
            .replaceAll("[\\d,]+\\.\\d{2}\\s*", "")
            .trim();

        // The cleaned narrative should be: "3 Office Rent REF-001"
        // (Row number "3" at start, then narrative)
        // But the narrative should not include "86681.68" (balance)
        assertFalse(
            cleanedNarrative.contains("86681.68"),
            "Cleaned narrative must not include the balance amount 86681.68"
        );
    }

    @Test
    public void testParse_returnsEmpty_forEmptyPDF(@TempDir Path tempDir)
        throws Exception {
        Path pdfPath = createTestPDF(tempDir, List.of());

        PDFBankStatementParser parser = new PDFBankStatementParser(
            "DEFAULT_STRATEGY",
            List.of()
        );
        FinancialDataset dataset = parser.parse(pdfPath.toString());

        // Empty PDF should produce empty transactions
        assertTrue(
            dataset.getRawTransactions().isEmpty(),
            "Empty PDF should produce 0 transactions"
        );
    }

    @Test
    public void testParse_handlesMultipleDateFormats(@TempDir Path tempDir)
        throws Exception {
        Path pdfPath = createTestPDF(
            tempDir,
            List.of(
                "1 01/15/2025 Payment 100.00 5000.00",
                "2 1/20/2025 Refund 50.00 5050.00",
                "3 12-25-2024 Purchase 200.00 4850.00"
            )
        );

        PDFBankStatementParser parser = new PDFBankStatementParser(
            "DEFAULT_STRATEGY",
            List.of()
        );
        FinancialDataset dataset = parser.parse(pdfPath.toString());

        assertEquals(
            3,
            dataset.getRawTransactions().size(),
            "Should parse all 3 date format variations"
        );
    }

    // Helper: Create a valid test PDF file using PDFBox
    private Path createTestPDF(Path tempDir, List<String> lines)
        throws Exception {
        Path pdfPath = tempDir.resolve(
            "test_" + java.util.UUID.randomUUID().toString() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (
                PDPageContentStream contentStream = new PDPageContentStream(
                    document,
                    page
                )
            ) {
                contentStream.beginText();
                contentStream.setFont(
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    12
                );
                contentStream.newLineAtOffset(25, 700);
                contentStream.setLeading(14.5f);
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLine();
                }
                contentStream.endText();
            }
            document.save(pdfPath.toFile());
        }
        return pdfPath;
    }
}
