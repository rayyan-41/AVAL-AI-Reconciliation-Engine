//Dev: Safwan
//Use Cases: UC2
package aval.parser;

import aval.common.enums.TransactionType;
import aval.domain.ingestion.RawInternalLedger;
import aval.domain.ingestion.RawTransaction;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.*;

//@desc:   Concrete implementation handling Excel (.xlsx) files produced by accounting software.
//@grasp:  Polymorphism
//@gof:    Template Method
public class ExcelLedgerParser implements DocumentParser<RawInternalLedger> {

    //-------------- Attributes ----------------------//
    private static final Set<String> DATE_HEADERS = Set.of("date", "transaction date", "posting date", "value date", "trans date");
    private static final Set<String> AMOUNT_HEADERS = Set.of("amount", "debit", "credit", "value", "sum", "total");
    private static final Set<String> DESC_HEADERS = Set.of("description", "narrative", "details", "memo", "particulars", "vendor", "merchant", "category", "reference", "desc");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");

    public ExcelLedgerParser(char delimiter, List<String> expectedHeaders) {
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

        System.out.println("[LEDGER PARSER] Starting to parse: " + filePath);

        try (
            FileInputStream fis = new FileInputStream(filePath);
            Workbook workbook = WorkbookFactory.create(fis)
        ) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                System.err.println("[LEDGER PARSER] Sheet is empty");
                return ledger;
            }

            int numCols = inferColumnCount(sheet);
            if (numCols <= 0) {
                System.err.println("[LEDGER PARSER] No columns found");
                return ledger;
            }

            // Find the actual header row (skip title rows, metadata, etc.)
            int headerRowIdx = findHeaderRow(sheet, numCols);
            System.out.println("[LEDGER PARSER] Found header at row: " + headerRowIdx);

            if (headerRowIdx < 0) {
                System.err.println("[LEDGER PARSER] Could not find header row");
                return ledger;
            }

            Row headerRow = sheet.getRow(headerRowIdx);
            String[] headers = new String[numCols];
            for (int c = 0; c < numCols; c++) {
                headers[c] = getCellValue(headerRow.getCell(c)).toLowerCase().trim();
            }
            System.out.println("[LEDGER PARSER] Headers: " + String.join(", ", headers));

            // Auto-detect columns by analyzing header row AND content
            int[] colTypes = detectColumnTypesFromHeaders(headers, sheet, headerRowIdx + 1, numCols);

            int dateIdx = colTypes[0];
            int amountIdx = colTypes[1];
            int descIdx = colTypes[2];
            int debitIdx = colTypes[3];
            int creditIdx = colTypes[4];

            System.out.println("[LEDGER PARSER] Auto-detected columns - date:" + dateIdx + " amount:" + amountIdx + " desc:" + descIdx + " debit:" + debitIdx + " credit:" + creditIdx);

            // Parse data rows (start after header)
            int startRowIdx = headerRowIdx + 1;
            for (int rowIdx = startRowIdx; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                // Get date
                String rawDate = dateIdx >= 0 ? getCellValue(row.getCell(dateIdx)) : "";
                if (rawDate.isEmpty()) continue;

                // Get amount - try multiple strategies
                String rawAmount = "";
                TransactionType type = TransactionType.DEBIT;

                // Strategy 1: Separate debit/credit columns
                if (debitIdx >= 0 && creditIdx >= 0) {
                    String debitVal = getCellValue(row.getCell(debitIdx));
                    String creditVal = getCellValue(row.getCell(creditIdx));
                    if (!creditVal.isEmpty() && isNumeric(creditVal)) {
                        rawAmount = creditVal;
                        type = TransactionType.CREDIT;
                    } else if (!debitVal.isEmpty() && isNumeric(debitVal)) {
                        rawAmount = debitVal;
                        type = TransactionType.DEBIT;
                    }
                }
                // Strategy 2: Single amount column with sign
                else if (amountIdx >= 0) {
                    rawAmount = getCellValue(row.getCell(amountIdx));
                    if (rawAmount.startsWith("-")) {
                        rawAmount = rawAmount.substring(1);
                        type = TransactionType.CREDIT;
                    }
                }

                if (rawAmount.isEmpty() || !isNumeric(rawAmount)) continue;

                // Get description from remaining columns
                StringBuilder narrative = new StringBuilder();
                for (int c = 0; c < numCols; c++) {
                    if (c == dateIdx || c == amountIdx || c == debitIdx || c == creditIdx) continue;
                    String val = getCellValue(row.getCell(c));
                    if (!val.isEmpty()) {
                        if (narrative.length() > 0) narrative.append(" | ");
                        narrative.append(val);
                    }
                }

                RawTransaction tx = new RawTransaction(
                    UUID.randomUUID(),
                    rawDate,
                    rawAmount,
                    narrative.toString(),
                    type,
                    ledger
                );

                ledger.getRawTransactions().add(tx);
            }

            System.out.println("[LEDGER PARSER] Parsed " + ledger.getRawTransactions().size() + " transactions");

        } catch (Exception e) {
            System.err.println("[LEDGER PARSER] Error parsing file: " + e.getMessage());
            e.printStackTrace();
        }

        return ledger;
    }

    private int inferColumnCount(Sheet sheet) {
        int maxCols = 0;
        int maxScanRows = Math.min(10, sheet.getLastRowNum() + 1);

        for (int rowIdx = 0; rowIdx < maxScanRows; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            short lastCellNum = row.getLastCellNum();
            if (lastCellNum > maxCols) {
                maxCols = lastCellNum;
            }
        }

        return maxCols;
    }

    /**
     * Find the header row by looking for a row with common header keywords.
     * Skips title rows, metadata rows, etc.
     */
    private int findHeaderRow(Sheet sheet, int numCols) {
        // Common header keywords to look for
        Set<String> headerKeywords = Set.of("date", "amount", "debit", "credit", "description", "narrative", "memo", "particulars", "vendor", "category", "reference", "type", "value", "entry", "no.", "number");

        // Scan first 10 rows to find header
        int maxScanRows = Math.min(10, sheet.getLastRowNum() + 1);
        for (int rowIdx = 0; rowIdx < maxScanRows; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            int matchCount = 0;
            int nonEmptyCount = 0;
            List<String> rowValues = new ArrayList<>();

            for (int c = 0; c < numCols && c < 20; c++) {
                String val = getCellValue(row.getCell(c)).toLowerCase().trim();
                rowValues.add(val.isEmpty() ? "_" : val);
                if (!val.isEmpty()) {
                    nonEmptyCount++;
                    if (headerKeywords.stream().anyMatch(k -> val.contains(k))) {
                        matchCount++;
                    }
                }
            }
            System.out.println("[LEDGER PARSER] Row " + rowIdx + " scan: matches=" + matchCount + ", nonEmpty=" + nonEmptyCount + ", content=" + rowValues);

            // If we found header-like content, this is likely the header row
            // Lower threshold to 1 match to catch single-column headers like "entry no."
            // But also verify row is not all dates (could be sample data)
            boolean hasNumericData = false;
            // Quick check: if first column below has numbers, it's likely a data row
            Row firstDataRow = sheet.getRow(rowIdx + 1);
            if (firstDataRow != null) {
                String firstCell = getCellValue(firstDataRow.getCell(0)).trim();
                hasNumericData = firstCell.matches("^\\d+$") || firstCell.matches("^\\d{1,2}[/\\-.]\\d{1,2}");
            }
            if (matchCount >= 1 && nonEmptyCount >= 2) {
                // If row has matches but below looks like sample data, skip it
                if (matchCount >= 2 || hasNumericData) {
                    return rowIdx;
                }
            }
        }

        // Fallback: return row 2 (index 2 = 3rd row, after title + metadata)
        return 2;
    }

    /**
     * Detect column types from header names
     */
    private int[] detectColumnTypesFromHeaders(String[] headers, Sheet sheet, int dataStartRow, int numCols) {
        int[] result = {-1, -1, -1, -1, -1}; // date, amount, desc, debit, credit

        // Match by header name
        for (int c = 0; c < headers.length; c++) {
            String header = headers[c];
            if (header.isEmpty()) continue;

            if (result[0] == -1 && DATE_HEADERS.stream().anyMatch(h -> header.contains(h))) {
                result[0] = c; // date
            } else if (result[3] == -1 && header.contains("debit")) {
                result[3] = c; // debit
            } else if (result[4] == -1 && header.contains("credit")) {
                result[4] = c; // credit
            } else if (result[1] == -1 && (header.contains("amount") || header.contains("value"))) {
                result[1] = c; // amount
            } else if (result[2] == -1 && DESC_HEADERS.stream().anyMatch(h -> header.contains(h))) {
                result[2] = c; // description
            }
        }

        // Second pass: if not all found, detect by content type in data rows
        if (result[0] == -1 || result[1] == -1) {
            for (int r = dataStartRow; r <= Math.min(dataStartRow + 5, sheet.getLastRowNum()); r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) continue;

                for (int c = 0; c < numCols; c++) {
                    String val = getCellValue(dataRow.getCell(c));
                    if (val.isEmpty()) continue;

                    // Detect date column
                    if (result[0] == -1 && isDate(val)) {
                        result[0] = c;
                    }
                    // Detect amount column (numeric)
                    else if (result[1] == -1 && isNumeric(val) && !isDate(val)) {
                        result[1] = c;
                    }
                }
                // Stop if we found something
                if (result[0] != -1 || result[1] != -1) break;
            }
        }

        // Fallback: first column = date, first numeric = amount
        if (result[0] == -1) result[0] = 0;
        if (result[1] == -1 && result[3] == -1 && result[4] == -1) {
            for (int c = 0; c < numCols; c++) {
                if (c == result[0]) continue;
                Row dataRow = sheet.getRow(dataStartRow);
                if (dataRow != null) {
                    String val = getCellValue(dataRow.getCell(c));
                    if (isNumeric(val)) {
                        result[1] = c;
                        break;
                    }
                }
            }
        }

        return result;
    }

    private int[] detectColumnTypes(Sheet sheet, int numCols) {
        int[] result = {-1, -1, -1, -1, -1}; // date, amount, desc, debit, credit
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return result;

        // First pass: match by header name
        for (int c = 0; c < numCols; c++) {
            String header = getCellValue(headerRow.getCell(c)).toLowerCase().trim();
            if (header.isEmpty()) continue;

            if (result[0] == -1 && DATE_HEADERS.stream().anyMatch(h -> header.contains(h))) {
                result[0] = c; // date
            } else if (result[3] == -1 && header.contains("debit")) {
                result[3] = c; // debit
            } else if (result[4] == -1 && header.contains("credit")) {
                result[4] = c; // credit
            } else if (result[1] == -1 && (header.contains("amount") || header.contains("value"))) {
                result[1] = c; // amount
            } else if (result[2] == -1 && DESC_HEADERS.stream().anyMatch(h -> header.contains(h))) {
                result[2] = c; // description
            }
        }

        // Second pass: if not all found, detect by content type
        if (result[0] == -1 || result[1] == -1) {
            Row dataRow = sheet.getRow(1);
            if (dataRow != null) {
                for (int c = 0; c < numCols; c++) {
                    String val = getCellValue(dataRow.getCell(c));
                    if (val.isEmpty()) continue;

                    // Detect date column
                    if (result[0] == -1 && isDate(val)) {
                        result[0] = c;
                    }
                    // Detect amount column (numeric with optional decimal)
                    else if (result[1] == -1 && isNumeric(val) && !isDate(val)) {
                        result[1] = c;
                    }
                }
            }
        }

        // Fallback: assume first column is date, first numeric is amount
        if (result[0] == -1) result[0] = 0;
        if (result[1] == -1 && result[3] == -1 && result[4] == -1) {
            // Look for any numeric column
            Row dataRow = sheet.getRow(1);
            if (dataRow != null) {
                for (int c = 0; c < numCols; c++) {
                    String val = getCellValue(dataRow.getCell(c));
                    if (c != result[0] && isNumeric(val)) {
                        result[1] = c;
                        break;
                    }
                }
            }
        }

        return result;
    }

    private boolean isNumeric(String val) {
        if (val == null || val.isEmpty()) return false;
        String cleaned = val.replaceAll("[^\\d.,-]", "").trim();
        if (cleaned.isEmpty()) return false;

        // Remove thousands separators before validating the numeric form.
        cleaned = cleaned.replace(",", "");
        if (cleaned.equals("-") || cleaned.equals(".")) return false;

        return NUMBER_PATTERN.matcher(cleaned).matches();
    }

    private boolean isDate(String val) {
        if (val == null || val.isEmpty()) return false;
        String[] dateFormats = {
            "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy",
            "MMM dd, yyyy", "dd MMM yyyy", "yyyy/MM/dd", "M/d/yyyy"
        };
        for (String fmt : dateFormats) {
            try {
                DateTimeFormatter.ofPattern(fmt).parse(val);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    @Override
    public boolean validate(String filePath) {
        return new java.io.File(filePath).exists() && filePath.endsWith(".xlsx");
    }

    //-------------- Methods ----------------------//
    @Override
    public String getSupportedFormat() { return "XLSX"; }

    @Override
    public List<String[]> extractRawRows(String filePath) {
        List<String[]> rawRows = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {
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
