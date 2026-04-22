//Dev: Safwan
//Use Cases: UC2
package aval.domain.ingestion;
import aval.common.enums.DataSourceType;
import aval.common.enums.DatasetStatus;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//@desc:   Represents an unparsed, raw internal client ledger file uploaded in CSV format.
//@grasp:  Information Expert
//@gof:    N/A
public class RawInternalLedger extends FinancialDataset {
    private String accountingSystem;
    private String fiscalPeriod;
    private Map<String, Integer> columnMappings;

    public RawInternalLedger(UUID datasetId, LocalDate importDate, String filePath, DatasetStatus status, String accountingSystem, String fiscalPeriod) {
        super(datasetId, importDate, filePath, status);
        this.accountingSystem = accountingSystem;
        this.fiscalPeriod = fiscalPeriod;
        this.columnMappings = new HashMap<>();
    }

    @Override
    public DataSourceType getSourceType() {
        return DataSourceType.INTERNAL_CSV;
    }

    @Override
    public boolean validate() {
        // A basic check to ensure the dataset has the required metadata
        return this.accountingSystem != null && !this.accountingSystem.trim().isEmpty()
                && this.fiscalPeriod != null && !this.fiscalPeriod.trim().isEmpty();
    }

    public char detectDelimiter() {
        String path = this.getFilePath(); // Inherited from FinancialDataset
        if (path == null || path.trim().isEmpty()) {
            return ','; // Fallback default
        }

        char bestDelimiter = ',';
        int maxCount = 0;
        char[] possibleDelimiters = {',', ';', '\t', '|'};

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String firstLine = reader.readLine();

            if (firstLine != null && !firstLine.trim().isEmpty()) {
                for (char delimiter : possibleDelimiters) {
                    int count = 0;
                    for (int i = 0; i < firstLine.length(); i++) {
                        if (firstLine.charAt(i) == delimiter) {
                            count++;
                        }
                    }
                    // The character that appears most frequently in the header is our winner
                    if (count > maxCount) {
                        maxCount = count;
                        bestDelimiter = delimiter;
                    }
                }
            }
        } catch (IOException e) {
            // If file reading fails, safely default back to standard CSV comma
            return ',';
        }

        return bestDelimiter;
    }
}