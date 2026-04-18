package aval.common.enums;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Enums, covers UC2, UC3

//@desc:   Categorizes transactions into specific financial types for matching and analysis.
//@grasp:  Information Expert
//@gof:    N/A
public enum TransactionType {
    DEBIT,
    CREDIT,
    IBFT,
    CHEQUE,
    CASH,
    BANK_FEE,
    UNKNOWN
}