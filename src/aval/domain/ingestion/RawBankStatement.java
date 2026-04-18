package aval.domain.ingestion;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 2 - Ingestion, covers UC3

import enums.DataSourceType;
import enums.DatasetStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@desc:   Represents an unparsed, raw external bank statement file.
//@grasp:  Information Expert.
//@gof:    N/A.
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
        return DataSourceType.BANK_STATEMENT;
    }

    @Override
    public boolean validate() {
        return false;
    }

    public List<String[]> extractTableRegion() {
        return new ArrayList<>();
    }
}