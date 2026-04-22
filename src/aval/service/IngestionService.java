package aval.service;

import aval.common.enums.DataSourceType;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.ingestion.FinancialDataset;
import aval.parser.DocumentParser;
import aval.parser.ExcelLedgerParser;
import aval.parser.PDFBankStatementParser;
import aval.persistence.DataStore;
import java.util.ArrayList;
import java.util.List;

//@desc:   Orchestrates the data ingestion pipeline, covering file intake, parsing, schema standardisation, and initial persistence.
//@grasp:  Creator, Controller
//@gof:    Factory Method
public class IngestionService {

    private DataStore dataStore;

    public IngestionService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public FinancialDataset ingestFile(
        String filePath,
        DataSourceType sourceType
    ) {
        DocumentParser<? extends FinancialDataset> parser = createParser(
            sourceType
        );

        if (parser.validate(filePath)) {
            FinancialDataset dataset = parser.parse(filePath);
            this.dataStore.saveFinancialDataset(dataset);
            return dataset;
        }
        return null;
    }

    public List<StandardizedTransaction> standardize(FinancialDataset dataset) {
        List<StandardizedTransaction> standardizedList = new ArrayList<>();
        // standardisation logic here
        this.dataStore.saveStandardizedTransactions(standardizedList);
        return standardizedList;
    }

    private DocumentParser<? extends FinancialDataset> createParser(
        DataSourceType sourceType
    ) {
        if (sourceType == DataSourceType.INTERNAL_CSV) {
            return new ExcelLedgerParser(',', new ArrayList<>());
        } else if (sourceType == DataSourceType.EXTERNAL_PDF) {
            return new PDFBankStatementParser(
                "DEFAULT_STRATEGY",
                new ArrayList<>()
            );
        }
        throw new IllegalArgumentException(
            "Unsupported data source type: " + sourceType
        );
    }
}
