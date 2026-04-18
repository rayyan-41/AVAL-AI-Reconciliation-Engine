package aval.domain.ingestion;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 2 - Ingestion, covers UC2, UC3

//@desc:   An individual row extracted from a raw dataset before schema standardization.
//@grasp:  Information Expert
//@gof:    N/A
public class RawTransaction {
    private UUID transactionId;
    private String rawDate;
    private String rawAmount;
    private String narrative;
    private TransactionType transactionType;
    private FinancialDataset sourceDataset;

    public RawTransaction(UUID transactionId, String rawDate, String rawAmount, String narrative, TransactionType transactionType, FinancialDataset sourceDataset) {
        this.transactionId = transactionId;
        this.rawDate = rawDate;
        this.rawAmount = rawAmount;
        this.narrative = narrative;
        this.transactionType = transactionType;
        this.sourceDataset = sourceDataset;
    }

    public String toEmbeddingInputText() {
        return "";
    }

    public boolean hasValidAmount() {
        return false;
    }

    public BigDecimal getParsedAmount() {
        return BigDecimal.ZERO;
    }
}