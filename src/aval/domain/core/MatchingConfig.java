package aval.domain.core;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 1 - Core, covers UC6

//@desc:   Holds the two critical constants for auto-reconciliation and human review thresholds.
//@grasp:  Information Expert
//@gof:    N/A
public class MatchingConfig {
    private final Double AUTO_CONFIRM_THRESHOLD = 0.95;
    private final Double REVIEW_FLOOR = 0.70;

    public Double getAutoConfirmThreshold() {
        return this.AUTO_CONFIRM_THRESHOLD;
    }

    public Double getReviewFloor() {
        return this.REVIEW_FLOOR;
    }
}