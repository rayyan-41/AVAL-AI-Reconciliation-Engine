package aval.common.enums;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Enums, covers UC1

//@desc:   Represents the lifecycle state of a ReconciliationWorkspace.
//@grasp:  Information Expert
//@gof:    N/A
public enum WorkspaceStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    LOCKED
}