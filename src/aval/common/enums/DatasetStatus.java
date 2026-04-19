package aval.common.enums;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Enums, covers UC2-UC4

//@desc:   Tracks the processing lifecycle of a FinancialDataset from upload to standardisation.
//@grasp:  Information Expert
//@gof:    N/A
public enum DatasetStatus {
    UPLOADED,
    VALIDATED,
    PARSED,
    STANDARDIZED,
    FAILED
}