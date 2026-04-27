//Dev: Safwan
//Use Cases: UC2, UC3, UC4
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
    private aval.engine.VectorizationEngine vectorizationEngine;

    public IngestionService(
        DataStore dataStore,
        aval.engine.VectorizationEngine vectorizationEngine
    ) {
        this.dataStore = dataStore;
        this.vectorizationEngine = vectorizationEngine;
    }

    public FinancialDataset ingestFile(
        java.util.UUID workspaceId,
        String filePath,
        DataSourceType sourceType
    ) {
        DocumentParser<? extends FinancialDataset> parser = createParser(
            sourceType
        );

        if (parser.validate(filePath)) {
            FinancialDataset dataset = parser.parse(filePath);
            this.dataStore.saveFinancialDataset(dataset, workspaceId);
            this.dataStore.saveRawTransactions(dataset.getRawTransactions());
            return dataset;
        }
        return null;
    }

    public List<StandardizedTransaction> standardize(FinancialDataset dataset) {
        List<StandardizedTransaction> standardizedList = new ArrayList<>();

        for (aval.domain.ingestion.RawTransaction raw : dataset.getRawTransactions()) {
            if (!raw.hasValidAmount()) {
                continue; // Skip invalid or empty amounts
            }

            java.time.LocalDate parsedDate = parseDate(raw.getRawDate());
            if (parsedDate == null) {
                // Fallback if date is completely unparseable
                parsedDate = java.time.LocalDate.now();
            }

            StandardizedTransaction std = new StandardizedTransaction(
                raw.getTransactionId(),
                parsedDate,
                raw.getParsedAmount(),
                raw.getNarrative() != null ? raw.getNarrative().trim() : "",
                raw.getTransactionType(),
                dataset.getDatasetId()
            );
            standardizedList.add(std);

            try {
                aval.domain.ai.SemanticEmbedding embedding =
                    vectorizationEngine.vectorize(std);
                if (dataset.getSourceType() == DataSourceType.INTERNAL_EXCEL) {
                    this.dataStore.saveStandardizedLedgerTransaction(
                        std,
                        embedding
                    );
                } else if (
                    dataset.getSourceType() == DataSourceType.EXTERNAL_PDF
                ) {
                    this.dataStore.saveStandardizedBankTransaction(
                        std,
                        embedding
                    );
                }
            } catch (Exception e) {
                System.err.println(
                    "Failed to vectorize/save transaction " +
                        std.getTransactionId() +
                        ": " +
                        e.getMessage()
                );
            }
        }

        dataset.getStandardizedTransactions().addAll(standardizedList);
        dataset.markAsStandardized();
        this.dataStore.saveStandardizedTransactions(standardizedList);
        return standardizedList;
    }

    private java.time.LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) return null;
        String cleanDate = rawDate.trim();

        String[] patterns = {
            "MM/dd/yyyy",
            "M/d/yyyy",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "M/d/yy",
            "MM/dd/yy",
        };

        for (String pattern : patterns) {
            try {
                return java.time.LocalDate.parse(
                    cleanDate,
                    java.time.format.DateTimeFormatter.ofPattern(pattern)
                );
            } catch (Exception e) {
                // ignore and try next
            }
        }
        return null;
    }

    private DocumentParser<? extends FinancialDataset> createParser(
        DataSourceType sourceType
    ) {
        if (sourceType == DataSourceType.INTERNAL_EXCEL) {
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
