package aval.domain.ingestion;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 2 - Ingestion, covers UC2

import aval.common.enums.DataSourceType;
import aval.common.enums.DatasetStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//@desc:   Represents an unparsed, raw internal client ledger file uploaded in CSV format.
//@grasp:  Information Expert
//@gof:    N/A
public class RawInternalLedger extends FinancialDataset {
    private String accountingSystem;
    private String fiscalPeriod;
    private Map<String, Integer> columnMappings;

    public RawInternalLedger(UUID datasetId, LocalDate importDate, String filePath, DatasetStatus status, String accountingSystem, String fiscalPeriod) {
        super(datasetId, importDate, filePath, status);
        this.accountingSystem = accountingSystem;
        this.fiscalPeriod = fiscalPeriod;
        this.columnMappings = new HashMap<>();
    }

    @Override
    public DataSourceType getSourceType() {
        return DataSourceType.INTERNAL_CSV;
    }

    @Override
    public boolean validate() {
        return false;
    }

    public char detectDelimiter() {
        return ',';
    }
}