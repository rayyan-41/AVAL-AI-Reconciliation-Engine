//Dev: Safwan
//Use Cases: UC6
package aval.domain.core;

//@desc:   Configuration value-object that holds the threshold constants governing the AI matching pipeline.
//@grasp:  Information Expert
//@gof:    N/A
public class MatchingConfig {

    private final Double autoConfirmThreshold;
    private final Double reviewFloor;

    public MatchingConfig() {
        this.autoConfirmThreshold = 0.95;
        this.reviewFloor = 0.70;
    }

    public Double getAutoConfirmThreshold() {
        return this.autoConfirmThreshold;
    }

    public Double getReviewFloor() {
        return this.reviewFloor;
    }
}
