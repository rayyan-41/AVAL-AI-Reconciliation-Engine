//Dev: Safwan
//Use Cases: UC2, UC3
package aval.domain.ingestion;

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

    //-------------- Attributes --------------------------------//
    private UUID datasetId;
    private LocalDate importDate;
    private String filePath;
    private DatasetStatus status;
    private List<RawTransaction> rawTransactions;
    private List<StandardizedTransaction> standardizedTransactions;

    //Constructor
    public FinancialDataset(
        UUID datasetId,
        LocalDate importDate,
        String filePath,
        DatasetStatus status
    ) {
        this.datasetId = datasetId;
        this.importDate = importDate;
        this.filePath = filePath;
        this.status = status;
        this.rawTransactions = new ArrayList<>();
        this.standardizedTransactions = new ArrayList<>();
    }

    //-------------------- Methods --------------------//

    //Abstractions
    public abstract DataSourceType getSourceType();

    public abstract boolean validate();

    //Getters
    public List<RawTransaction> getRawTransactions() { return this.rawTransactions; }
    public List<StandardizedTransaction> getStandardizedTransactions() { return this.standardizedTransactions; }
    public String getFilePath() { return this.filePath; }
    public UUID getDatasetId() { return this.datasetId; }
    public LocalDate getImportDate() { return this.importDate; }
    public DatasetStatus getStatus() { return this.status; }

    public void markAsStandardized() {
        this.status = DatasetStatus.STANDARDIZED;
    }
}
