package aval.common.enums;

//@desc:   Tracks the processing lifecycle of a FinancialDataset from upload to standardisation.
//@grasp:  Information Expert
//@gof:    N/A
public enum DatasetStatus {
    UPLOADED,
    VALIDATED,
    PARSED,
    STANDARDIZED,
    FAILED,
}
