package aval.parser;

import aval.common.enums.TransactionType;
import aval.domain.ingestion.RawInternalLedger;
import aval.domain.ingestion.RawTransaction;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.*;

//@desc:   Concrete implementation handling Excel (.xlsx) files produced by accounting software.
//@grasp:  Polymorphism
//@gof:    Template Method
public class ExcelLedgerParser implements DocumentParser<RawInternalLedger> {

    private char delimiter;
    private List<String> expectedHeaders;

    public ExcelLedgerParser(char delimiter, List<String> expectedHeaders) {
        this.delimiter = delimiter;
        this.expectedHeaders = new ArrayList<>(expectedHeaders);
    }

    @Override
    public RawInternalLedger parse(String filePath) {
        RawInternalLedger ledger = new RawInternalLedger(
            UUID.randomUUID(),
            LocalDate.now(),
            filePath,
            aval.common.enums.DatasetStatus.PARSED,
            "Internal System",
            "2025"
        );

        try (
            FileInputStream fis = new FileInputStream(filePath);
            Workbook workbook = WorkbookFactory.create(fis)
        ) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isHeader = true;

            for (Row row : sheet) {
                if (isHeader) {
                    isHeader = false;
                    continue; // Skip header row
                }

                if (row.getCell(1) == null) continue; // Skip empty rows

                String rawDate = getCellValue(row.getCell(1));
                String category = getCellValue(row.getCell(2));
                String vendor = getCellValue(row.getCell(4));
                String referenceNo = getCellValue(row.getCell(5));
                String typeStr = getCellValue(row.getCell(6));

                String rawAmount = "";
                TransactionType type = TransactionType.DEBIT;

                if ("CREDIT".equalsIgnoreCase(typeStr)) {
                    rawAmount = getCellValue(row.getCell(8));
                    type = TransactionType.CREDIT;
                } else {
                    rawAmount = getCellValue(row.getCell(7));
                    type = TransactionType.DEBIT;
                }

                String narrative =
                    category + " | " + vendor + " | " + referenceNo;

                RawTransaction tx = new RawTransaction(
                    UUID.randomUUID(),
                    rawDate,
                    rawAmount,
                    narrative,
                    type,
                    ledger
                );

                ledger.getRawTransactions().add(tx);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ledger;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    @Override
    public boolean validate(String filePath) {
        return (
            new java.io.File(filePath).exists() && filePath.endsWith(".xlsx")
        );
    }

    @Override
    public String getSupportedFormat() {
        return "XLSX";
    }

    @Override
    public List<String[]> extractRawRows(String filePath) {
        List<String[]> rawRows = new ArrayList<>();
        try (
            FileInputStream fis = new FileInputStream(filePath);
            Workbook workbook = WorkbookFactory.create(fis)
        ) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                String[] r = new String[row.getLastCellNum()];
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    r[i] = getCellValue(row.getCell(i));
                }
                rawRows.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rawRows;
    }
}
