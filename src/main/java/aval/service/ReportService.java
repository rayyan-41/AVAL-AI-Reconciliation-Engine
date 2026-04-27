//Dev: Aryan
//Use Cases: UC11, UC12
package aval.service;

import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

//@desc:   Service responsible for generating final verified reconciliation reports (UC11).
//@grasp:  Pure Fabrication, Controller
//@gof:    N/A
public class ReportService {

    public static class UnresolvedItemsException extends Exception {

        public UnresolvedItemsException(String message) {
            super(message);
        }
    }

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * UC11 - Generate Verified Reconciliation Report
     * Generates a comprehensive CSV report detailing reconciled transactions,
     * along with unmatched transactions from both the internal ledger and the bank statement.
     */
    public void generateReconciliationReport(
        List<ReconciliationRecord> reconciledRecords,
        List<StandardizedTransaction> unmatchedLedger,
        List<StandardizedTransaction> unmatchedBank,
        List<aval.domain.ai.MatchHypothesis> pendingHypotheses,
        List<String> unresolvedAnomalies,
        String outputFilePath
    ) throws IOException, UnresolvedItemsException {
        if (
            (pendingHypotheses != null && !pendingHypotheses.isEmpty()) ||
            (unresolvedAnomalies != null && !unresolvedAnomalies.isEmpty())
        ) {
            throw new UnresolvedItemsException(
                "Cannot Generate: Unresolved Items exist. Please resolve all pending hypotheses and anomalies before generating the report."
            );
        }

        // Ensure the parent directories exist
        Files.createDirectories(Paths.get(outputFilePath).getParent());

        try (
            FileWriter writer = new FileWriter(outputFilePath);
            CSVPrinter csvPrinter = new CSVPrinter(
                writer,
                CSVFormat.DEFAULT.builder()
                    .setHeader(
                        "Status",
                        "Ledger Date",
                        "Ledger Amount",
                        "Ledger Type",
                        "Ledger Narrative",
                        "Bank Date",
                        "Bank Amount",
                        "Bank Type",
                        "Bank Narrative",
                        "Match Type",
                        "Confidence",
                        "Reconciled By",
                        "Reconciled At",
                        "Justification"
                    )
                    .build()
            )
        ) {
            // 1. Write Reconciled Records
            for (ReconciliationRecord record : reconciledRecords) {
                StandardizedTransaction ledgerTx = record
                    .getHypothesis()
                    .getLedgerTransaction();
                StandardizedTransaction bankTx = record
                    .getHypothesis()
                    .getBankTransaction();

                csvPrinter.printRecord(
                    "RECONCILED",
                    ledgerTx.getValueDate().format(DATE_FORMAT),
                    ledgerTx.getAmount().toPlainString(),
                    ledgerTx.getType().name(),
                    ledgerTx.getNarrative(),
                    bankTx.getValueDate().format(DATE_FORMAT),
                    bankTx.getAmount().toPlainString(),
                    bankTx.getType().name(),
                    bankTx.getNarrative(),
                    record.getHypothesis().getMatchType().name(),
                    String.format(
                        "%.2f",
                        record.getHypothesis().getConfidenceScore()
                    ),
                    record.getConfirmingUser() != null
                        ? record.getConfirmingUser().getUsername()
                        : "SYSTEM",
                    record.getReconciledAt().format(DATETIME_FORMAT),
                    record.getHypothesis().getJustification()
                );
            }

            // 2. Write Unmatched Ledger Transactions (Missing from Bank)
            for (StandardizedTransaction ledgerTx : unmatchedLedger) {
                csvPrinter.printRecord(
                    "UNMATCHED_LEDGER",
                    ledgerTx.getValueDate().format(DATE_FORMAT),
                    ledgerTx.getAmount().toPlainString(),
                    ledgerTx.getType().name(),
                    ledgerTx.getNarrative(),
                    "",
                    "",
                    "",
                    "", // No bank data
                    "",
                    "",
                    "",
                    "",
                    "Missing from bank statement"
                );
            }

            // 3. Write Unmatched Bank Transactions (Missing from Ledger)
            for (StandardizedTransaction bankTx : unmatchedBank) {
                csvPrinter.printRecord(
                    "UNMATCHED_BANK",
                    "",
                    "",
                    "",
                    "", // No ledger data
                    bankTx.getValueDate().format(DATE_FORMAT),
                    bankTx.getAmount().toPlainString(),
                    bankTx.getType().name(),
                    bankTx.getNarrative(),
                    "",
                    "",
                    "",
                    "",
                    "Unexpected bank posting / Missing from ledger"
                );
            }

            csvPrinter.flush();
        }
    }

    /**
     * Generates a textual summary of the reconciliation results.
     */
    public String generateSummary(
        List<ReconciliationRecord> reconciledRecords,
        List<StandardizedTransaction> unmatchedLedger,
        List<StandardizedTransaction> unmatchedBank
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================\n");
        sb.append("     RECONCILIATION SUMMARY REPORT       \n");
        sb.append("=========================================\n\n");

        sb.append(
            String.format(
                "Successfully Reconciled: %d transactions\n",
                reconciledRecords.size()
            )
        );
        sb.append(
            String.format(
                "Unmatched Ledger (Missing from Bank): %d transactions\n",
                unmatchedLedger.size()
            )
        );
        sb.append(
            String.format(
                "Unmatched Bank (Missing from Ledger): %d transactions\n\n",
                unmatchedBank.size()
            )
        );

        sb.append(
            "Details of discrepancies and matched pairs can be found in the generated CSV report.\n"
        );
        return sb.toString();
    }
}
