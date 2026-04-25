package aval.engine;

import aval.domain.ai.StandardizedTransaction;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * UC10 - Identify Financial Anomalies
 * Flags suspicious transactions based on duplication, outliers, and timing.
 */
public class AnomalyDetectionEngine {

    public List<String> identifyAnomalies(List<StandardizedTransaction> unmatchedLedger, List<StandardizedTransaction> unmatchedBank) {
        List<String> anomalies = new ArrayList<>();
        List<StandardizedTransaction> allTransactions = new ArrayList<>();
        allTransactions.addAll(unmatchedLedger);
        allTransactions.addAll(unmatchedBank);

        if (allTransactions.isEmpty()) return anomalies;

        // 1. Exact Duplicates (within the same dataset)
        findDuplicates(unmatchedLedger, "Ledger", anomalies);
        findDuplicates(unmatchedBank, "Bank", anomalies);

        // 2. Extreme Outliers (> 3 Standard Deviations)
        double mean = allTransactions.stream()
                .mapToDouble(t -> t.getAmount().abs().doubleValue())
                .average().orElse(0.0);
        double sumSq = allTransactions.stream()
                .mapToDouble(t -> Math.pow(t.getAmount().abs().doubleValue() - mean, 2))
                .sum();
        double stdDev = Math.sqrt(sumSq / allTransactions.size());
        double threshold = mean + (3 * stdDev);

        for (StandardizedTransaction t : allTransactions) {
            if (t.getAmount().abs().doubleValue() > threshold && stdDev > 0) {
                anomalies.add(String.format("OUTLIER: [%s] Amount %s exceeds 3 standard deviations (%s)",
                        t.getValueDate(), t.getAmount(), String.format("%.2f", threshold)));
            }
        }

        // 3. Weekend/Holiday Flags (Sunday transactions)
        for (StandardizedTransaction t : allTransactions) {
            if (t.getValueDate().getDayOfWeek() == DayOfWeek.SUNDAY) {
                anomalies.add(String.format("WEEKEND: [%s] Large transaction %s occurred on a Sunday.",
                        t.getValueDate(), t.getAmount()));
            }
        }

        return anomalies;
    }

    private void findDuplicates(List<StandardizedTransaction> transactions, String source, List<String> anomalies) {
        for (int i = 0; i < transactions.size(); i++) {
            for (int j = i + 1; j < transactions.size(); j++) {
                StandardizedTransaction t1 = transactions.get(i);
                StandardizedTransaction t2 = transactions.get(j);
                if (t1.getValueDate().equals(t2.getValueDate()) && t1.getAmount().equals(t2.getAmount())) {
                    anomalies.add(String.format("DUPLICATE: Two %s entries on %s for %s found.",
                            source, t1.getValueDate(), t1.getAmount()));
                }
            }
        }
    }
}
