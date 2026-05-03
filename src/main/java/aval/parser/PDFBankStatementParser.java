//Dev: Safwan
//Use Cases: UC3
package aval.parser;

import aval.common.enums.TransactionType;
import aval.domain.ingestion.RawBankStatement;
import aval.domain.ingestion.RawTransaction;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

//@desc:   Concrete implementation reading scanned or digital bank statement PDFs.
//@grasp:  Polymorphism
//@gof:    Template Method
public class PDFBankStatementParser
    implements DocumentParser<RawBankStatement>
{

    //-------------- Methods ----------------------//
    public PDFBankStatementParser(
        String pageParsingStrategy,
        List<String> tableDetectionHeuristics
    ) {
        // Parameters kept for constructor compatibility but unused in dynamic PDFBox logic
    }

    @Override
    public RawBankStatement parse(String filePath) {
        String fileName = new File(filePath).getName();
        String bankName = fileName.replace(".pdf", "").replace("_", " ");

        RawBankStatement statement = new RawBankStatement(
            UUID.randomUUID(),
            LocalDate.now(),
            filePath,
            aval.common.enums.DatasetStatus.PARSED,
            bankName,
            "Unknown Account",
            LocalDate.now().minusMonths(1),
            LocalDate.now()
        );

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            String[] lines = text.split("\\r?\\n");

            // Look for a date like MM/DD/YYYY or similar anywhere in the line
            Pattern datePattern = Pattern.compile(
                "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"
            );
            Pattern amountPattern = Pattern.compile("([\\d,]+\\.\\d{2})");

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Matcher m = datePattern.matcher(line);
                if (m.find()) {
                    String rawDate = m.group(1);
                    String remainder = line.substring(m.end()).trim();

                    Matcher amtMatcher = amountPattern.matcher(remainder);
                    List<String> amounts = new ArrayList<>();
                    while (amtMatcher.find()) {
                        amounts.add(amtMatcher.group(1));
                    }

                    if (!amounts.isEmpty()) {
                        String narrative = remainder;
                        for (String amt : amounts) {
                            narrative = narrative.replace(amt, "").trim();
                        }

                        String rawAmount = amounts.get(0);
                        TransactionType type = TransactionType.DEBIT;

                        if (
                            narrative.toLowerCase().contains("deposit") ||
                            narrative.toLowerCase().contains("refund") ||
                            narrative.toLowerCase().contains("transfer in")
                        ) {
                            type = TransactionType.CREDIT;
                        }

                        RawTransaction tx = new RawTransaction(
                            UUID.randomUUID(),
                            rawDate,
                            rawAmount,
                            narrative,
                            type,
                            statement
                        );
                        statement.getRawTransactions().add(tx);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return statement;
    }

    @Override
    public boolean validate(String filePath) {
        return new File(filePath).exists() && filePath.endsWith(".pdf");
    }

    @Override
    public String getSupportedFormat() { return "PDF"; }

    @Override
    public List<String[]> extractRawRows(String filePath) {
        List<String[]> rawRows = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    rawRows.add(new String[] { line.trim() });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rawRows;
    }

    public List<String[]> extractTableRows(String filePath) {
        return new ArrayList<>();
    }

    public String normalizeNarrative(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }
}
