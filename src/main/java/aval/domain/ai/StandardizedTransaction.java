//Dev: Rayyan
//Use Cases: UC4, UC5
package aval.domain.ai;

import aval.common.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

//@desc:   Standardized representation of a financial transaction post-mapping, used by AI and rule engines.
//@grasp:  Information Expert, Pure Fabrication
//@gof:    N/A
public class StandardizedTransaction {

    private final UUID transactionId;
    private final LocalDate valueDate;
    private final BigDecimal amount;
    private final String narrative;
    private final TransactionType type;
    private final UUID sourceDatasetId;

    public StandardizedTransaction(
        UUID transactionId,
        LocalDate valueDate,
        BigDecimal amount,
        String narrative,
        TransactionType type,
        UUID sourceDatasetId
    ) {
        this.transactionId = transactionId;
        this.valueDate = valueDate;
        this.amount = amount;
        this.narrative = narrative;
        this.type = type;
        this.sourceDatasetId = sourceDatasetId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNarrative() {
        return narrative;
    }

    public TransactionType getType() {
        return type;
    }

    public UUID getSourceDatasetId() {
        return sourceDatasetId;
    }

    @Override
    public String toString() {
        return String.format(
            "[%s] %s | %s | %s",
            valueDate,
            type,
            amount,
            narrative
        );
    }
}
