package aval.parser;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Parser Impl, covers UC2

import aval.domain.ingestion.RawInternalLedger;
import aval.domain.ingestion.RawTransaction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

//@desc:   Concrete implementation handling comma-separated value files produced by accounting software.
//@grasp:  Polymorphism
//@gof:    Template Method
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