//Dev: Safwan
//Use Cases: UC2
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

            int dateIdx = -1,
                categoryIdx = -1,
                vendorIdx = -1,
                refIdx = -1,
                typeIdx = -1,
                debitIdx = -1,
                creditIdx = -1,
                amountIdx = -1;

            for (Row row : sheet) {
                if (isHeader) {
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        String header = getCellValue(
                            row.getCell(i)
                        ).toLowerCase();
                        if (header.contains("date")) dateIdx = i;
                        else if (header.contains("category")) categoryIdx = i;
                        else if (
                            header.contains("vendor") ||
                            header.contains("merchant") ||
                            header.contains("description")
                        ) vendorIdx = i;
                        else if (header.contains("ref")) refIdx = i;
                        else if (header.contains("type")) typeIdx = i;
                        else if (header.contains("debit")) debitIdx = i;
                        else if (header.contains("credit")) creditIdx = i;
                        else if (header.contains("amount")) amountIdx = i;
                    }
                    isHeader = false;
                    continue; // Skip header row
                }

                if (dateIdx == -1 || row.getCell(dateIdx) == null) continue; // Skip empty rows

                String rawDate = getCellValue(row.getCell(dateIdx));
                if (rawDate.isEmpty()) continue;

                String category =
                    categoryIdx != -1
                        ? getCellValue(row.getCell(categoryIdx))
                        : "";
                String vendor =
                    vendorIdx != -1 ? getCellValue(row.getCell(vendorIdx)) : "";
                String referenceNo =
                    refIdx != -1 ? getCellValue(row.getCell(refIdx)) : "";
                String typeStr =
                    typeIdx != -1 ? getCellValue(row.getCell(typeIdx)) : "";

                String rawAmount = "";
                TransactionType type = TransactionType.DEBIT;

                if (debitIdx != -1 && creditIdx != -1) {
                    String creditVal = getCellValue(row.getCell(creditIdx));
                    String debitVal = getCellValue(row.getCell(debitIdx));
                    if (
                        !creditVal.isEmpty() &&
                        (typeStr.isEmpty() ||
                            "CREDIT".equalsIgnoreCase(typeStr))
                    ) {
                        rawAmount = creditVal;
                        type = TransactionType.CREDIT;
                    } else if (!debitVal.isEmpty()) {
                        rawAmount = debitVal;
                        type = TransactionType.DEBIT;
                    }
                } else if (amountIdx != -1) {
                    rawAmount = getCellValue(row.getCell(amountIdx));
                    if ("CREDIT".equalsIgnoreCase(typeStr)) {
                        type = TransactionType.CREDIT;
                    }
                }

                if (rawAmount.isEmpty()) continue;

                String narrative =
                    category +
                    (category.isEmpty() || vendor.isEmpty() ? "" : " | ") +
                    vendor +
                    (vendor.isEmpty() || referenceNo.isEmpty() ? "" : " | ") +
                    referenceNo;

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
