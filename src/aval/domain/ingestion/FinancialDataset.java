package aval.domain.ingestion;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 2 - Ingestion, covers UC2, UC3

import aval.common.enums.DataSourceType;
import aval.common.enums.DatasetStatus;
import aval.domain.ai.StandardizedTransaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@desc:   Abstract base class representing any uploaded financial file with shared state and processing status.
//@grasp:  Information Expert, Polymorphism
//@gof:    N/A
public abstract class FinancialDataset {
    private UUID datasetId;
    private LocalDate importDate;
    private String filePath;
    private DatasetStatus status;
    private List<RawTransaction> rawTransactions;
    private List<StandardizedTransaction> standardizedTransactions;

    public FinancialDataset(UUID datasetId, LocalDate importDate, String filePath, DatasetStatus status) {
        this.datasetId = datasetId;
        this.importDate = importDate;
        this.filePath = filePath;
        this.status = status;
        this.rawTransactions = new ArrayList<>();
        this.standardizedTransactions = new ArrayList<>();
    }

    public abstract DataSourceType getSourceType();

    public abstract boolean validate();

    public List<RawTransaction> getRawTransactions() {
        return this.rawTransactions;
    }

    public List<StandardizedTransaction> getStandardizedTransactions() {
        return this.standardizedTransactions;
    }

    public void markAsStandardized() {
        this.status = DatasetStatus.STANDARDIZED;
    }
}