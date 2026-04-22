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

    private String pageParsingStrategy;
    private List<String> tableDetectionHeuristics;

    public PDFBankStatementParser(
        String pageParsingStrategy,
        List<String> tableDetectionHeuristics
    ) {
        this.pageParsingStrategy = pageParsingStrategy;
        this.tableDetectionHeuristics = new ArrayList<>(
            tableDetectionHeuristics
        );
    }

    @Override
    public RawBankStatement parse(String filePath) {
        RawBankStatement statement = new RawBankStatement(
            UUID.randomUUID(),
            LocalDate.now(),
            filePath,
            aval.common.enums.DatasetStatus.PARSED,
            "Pacific Trust Bank",
            "1234-5678",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        );

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            String[] lines = text.split("\\r?\\n");

            // Look for a line starting with a date like MM/DD/YYYY or similar
            Pattern datePattern = Pattern.compile(
                "^(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"
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
    public String getSupportedFormat() {
        return "PDF";
    }

    @Override
    public List<String[]> extractRawRows(String filePath) {
        return new ArrayList<>();
    }

    public List<String[]> extractTableRows(String filePath) {
        return new ArrayList<>();
    }

    public String normalizeNarrative(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }
}
