package aval.domain.ingestion;

import aval.common.enums.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

//@desc:   Value object holding a single parsed line from a financial document before schema standardization.
//@grasp:  Information Expert
//@gof:    N/A
public class RawTransaction {

    private UUID transactionId;
    private String rawDate;
    private String rawAmount;
    private String narrative;
    private TransactionType transactionType;
    private FinancialDataset sourceDataset;

    public RawTransaction(
        UUID transactionId,
        String rawDate,
        String rawAmount,
        String narrative,
        TransactionType transactionType,
        FinancialDataset sourceDataset
    ) {
        this.transactionId = transactionId;
        this.rawDate = rawDate;
        this.rawAmount = rawAmount;
        this.narrative = narrative;
        this.transactionType = transactionType;
        this.sourceDataset = sourceDataset;
    }

    // 1. Combine fields for the AI to read
    public String toEmbeddingInputText() {
        String safeDate = (this.rawDate != null) ? this.rawDate.trim() : "";
        String safeNarrative = (this.narrative != null)
            ? this.narrative.trim()
            : "";
        String safeAmount = (this.rawAmount != null)
            ? this.rawAmount.trim()
            : "";

        return String.format(
            "%s | %s | %s",
            safeDate,
            safeNarrative,
            safeAmount
        );
    }

    // 2. Check if the string can safely become a number
    public boolean hasValidAmount() {
        if (this.rawAmount == null || this.rawAmount.trim().isEmpty()) {
            return false;
        }
        try {
            // Test parsing after cleaning commas and common symbols
            String cleaned = this.rawAmount.replaceAll("[^\\d.-]", "");
            new BigDecimal(cleaned);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 3. Convert the messy string into precise financial math
    public BigDecimal getParsedAmount() {
        if (!hasValidAmount()) {
            return BigDecimal.ZERO;
        }
        // Remove everything except numbers, decimals, and negative signs
        String cleaned = this.rawAmount.replaceAll("[^\\d.-]", "");
        return new BigDecimal(cleaned);
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getRawDate() {
        return rawDate;
    }

    public String getRawAmount() {
        return rawAmount;
    }

    public String getNarrative() {
        return narrative;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public FinancialDataset getSourceDataset() {
        return sourceDataset;
    }
}
