package aval.parser;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Parser Impl, covers UC3

//@desc:   Implementation for parsing and extracting table data from PDF bank statements.
//@grasp:  Information Expert
//@gof:    Strategy
public class PDFBankStatementParser implements DocumentParser<RawBankStatement> {
    private String pageParsingStrategy;
    private List<String> tableDetectionHeuristics;

    public PDFBankStatementParser(String pageParsingStrategy, List<String> tableDetectionHeuristics) {
        this.pageParsingStrategy = pageParsingStrategy;
        this.tableDetectionHeuristics = new ArrayList<>(tableDetectionHeuristics);
    }

    @Override
    public RawBankStatement parse(String filePath) {
        return null;
    }

    @Override
    public boolean validate(String filePath) {
        return false;
    }

    @Override
    public String getSupportedFormat() {
        return "PDF";
    }

    @Override
    public List<String[]> extractRawRows(String filePath) {
        return new ArrayList<>();
    }

    public List<String[]> extractTableRows(String filePath) {
        return new ArrayList<>();
    }

    public String normalizeNarrative(String raw) {
        return "";
    }
}