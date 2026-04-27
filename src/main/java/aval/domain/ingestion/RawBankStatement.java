//Dev: Safwan
//Use Cases: UC3
package aval.domain.ingestion;
import aval.common.enums.DataSourceType;
import aval.common.enums.DatasetStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@desc:   Represents an unparsed, raw external bank statement imported as a PDF document.
//@grasp:  Information Expert
//@gof:    N/A
public class RawBankStatement extends FinancialDataset {
    private String bankName;
    private String accountNumber;
    private LocalDate statementPeriodStart;
    private LocalDate statementPeriodEnd;

    public RawBankStatement(UUID datasetId, LocalDate importDate, String filePath, DatasetStatus status, String bankName, String accountNumber, LocalDate statementPeriodStart, LocalDate statementPeriodEnd) {
        super(datasetId, importDate, filePath, status);
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.statementPeriodStart = statementPeriodStart;
        this.statementPeriodEnd = statementPeriodEnd;
    }

    @Override
    public DataSourceType getSourceType() {
        return DataSourceType.EXTERNAL_PDF;
    }

    @Override
    public boolean validate() {
        // Ensure core banking metadata is present and dates make chronological sense
        return this.bankName != null && !this.bankName.trim().isEmpty()
                && this.accountNumber != null && !this.accountNumber.trim().isEmpty()
                && this.statementPeriodStart != null
                && this.statementPeriodEnd != null
                && !this.statementPeriodStart.isAfter(this.statementPeriodEnd);
    }

    // Provides the bounding box coordinates [RegionName, X, Y, Width, Height]
    // for the PDF parser to know where to look for transaction data.
    public List<String[]> extractTableRegion() {
        List<String[]> regions = new ArrayList<>();
        // Standard heuristic coordinates for a typical Pakistani bank statement
        regions.add(new String[]{"HEADER_BOUNDS", "50", "700", "500", "25"});
        regions.add(new String[]{"DATA_BOUNDS", "50", "100", "500", "600"});
        return regions;
    }
}