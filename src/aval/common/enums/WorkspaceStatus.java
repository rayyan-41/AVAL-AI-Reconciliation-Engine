package aval.common.enums;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Enums, covers UC1

//@desc:   Represents the current lifecycle phase of a reconciliation workspace[cite: 136].
//@grasp:  Information Expert [cite: 104]
//@gof:    N/A
public enum WorkspaceStatus {
    SETUP,
    INGESTING,
    STANDARDIZING,
    VECTORIZING,
    MATCHING,
    REVIEW_REQUIRED,
    SEALED
}
