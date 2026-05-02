//Dev: Safwan
//Use Cases: UC6
package aval.domain.core;

//@desc:   Configuration value-object that holds the threshold constants governing the AI matching pipeline.
//@grasp:  Information Expert
//@gof:    N/A
public class MatchingConfig {

    //----------- Attributes ----------------//
    private final Double autoConfirmThreshold;
    private final Double reviewFloor;
    private final Integer ruleBasedDateToleranceDays;
    private final Integer semanticMaxCandidates;

    //Constructor
    public MatchingConfig() {
        this.autoConfirmThreshold = 0.95;
        this.reviewFloor = 0.70;
        this.ruleBasedDateToleranceDays = 7;
        this.semanticMaxCandidates = 3;
    }

    //----------- Methods ------------//

    //Getters
    public Double getAutoConfirmThreshold() {
        return this.autoConfirmThreshold;
    }

    public Double getReviewFloor() {
        return this.reviewFloor;
    }

    public Integer getRuleBasedDateToleranceDays() {
        return this.ruleBasedDateToleranceDays;
    }

    public Integer getSemanticMaxCandidates() {
        return this.semanticMaxCandidates;
    }
}
