package aval.parser;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Parser Impl, covers UC2

import ingestion.RawInternalLedger;
import ingestion.RawTransaction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

//@desc:   Implementation for parsing client internal ledgers from CSV files.
//@grasp:  Information Expert
//@gof:    Strategy
public class CSVLedgerParser implements DocumentParser<RawInternalLedger> {
    private char delimiter;
    private List<String> expectedHeaders;

    public CSVLedgerParser(char delimiter, List<String> expectedHeaders) {
        this.delimiter = delimiter;
        this.expectedHeaders = new ArrayList<>(expectedHeaders);
    }

    @Override
    public RawInternalLedger parse(String filePath) {
        return null;
    }

    @Override
    public boolean validate(String filePath) {
        return false;
    }

    @Override
    public String getSupportedFormat() {
        return "CSV";
    }

    @Override
    public List<String[]> extractRawRows(String filePath) {
        return new ArrayList<>();
    }

    public RawTransaction mapRowToRawTransaction(String[] row) {
        return null;
    }

    public String detectEncoding(File file) {
        return "UTF-8";
    }
}

