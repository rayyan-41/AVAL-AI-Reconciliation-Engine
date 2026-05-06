//Dev: Rayyan
//Use Cases: UC10
package aval.domain.ai;

import java.util.UUID;

//@desc:   Represents a single detected financial anomaly with a typed category and affected transaction references.
//@grasp:  Information Expert
//@gof:    N/A
public class Anomaly {

    public enum Category { DUPLICATE, OUTLIER, WEEKEND_POSTING, CONSOLIDATION_VARIANCE }

    //------------ Attributes ------------//
    private final UUID anomalyId;
    private final Category category;
    private final String description;           // human-readable message (replaces the raw String)
    private final StandardizedTransaction primaryTransaction;
    private final StandardizedTransaction secondaryTransaction; // null unless DUPLICATE

    //Constructor
    public Anomaly(Category category, String description,
                   StandardizedTransaction primary, StandardizedTransaction secondary) {
        this.anomalyId           = UUID.randomUUID();
        this.category            = category;
        this.description         = description;
        this.primaryTransaction  = primary;
        this.secondaryTransaction = secondary;
    }

    //--------- Methods -----------//

    // Getters only — immutable after construction
    public UUID getAnomalyId()                           { return anomalyId; }
    public Category getCategory()                        { return category; }
    public String getDescription()                       { return description; }
    public StandardizedTransaction getPrimaryTransaction()   { return primaryTransaction; }
    public StandardizedTransaction getSecondaryTransaction() { return secondaryTransaction; }

    @Override
    public String toString() { return "[" + category + "] " + description; }
}
