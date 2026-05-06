package aval.engine;

import aval.domain.ai.Anomaly;
import aval.domain.ai.StandardizedTransaction;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * UC10 - Identify Financial Anomalies
 * Flags suspicious transactions based on duplication, outliers, and timing.
 */
//@desc:   Engine that flags suspicious transactions based on duplication, outliers, and timing (UC10).
//@grasp:  Pure Fabrication, Information Expert
//@gof:    N/A
public class AnomalyDetectionEngine {

    //-------------- Methods ----------------------//
    public List<Anomaly> identifyAnomalies(
        List<StandardizedTransaction> unmatchedLedger,
        List<StandardizedTransaction> unmatchedBank
    ) {
        List<Anomaly> anomalies = new ArrayList<>();
        List<StandardizedTransaction> allTransactions = new ArrayList<>();
        allTransactions.addAll(unmatchedLedger);
        allTransactions.addAll(unmatchedBank);

        System.out.printf("[ANOMALY ENGINE] Inputs: %d unmatched ledger, %d unmatched bank, %d total%n",
            unmatchedLedger.size(), unmatchedBank.size(), allTransactions.size());

        if (allTransactions.isEmpty()) return anomalies;

        // 1. Exact Duplicates (within the same dataset)
        findDuplicates(unmatchedLedger, "Ledger", anomalies);
        findDuplicates(unmatchedBank, "Bank", anomalies);

        // 2. Extreme Outliers (> 3 Standard Deviations)
        double mean = allTransactions
            .stream()
            .mapToDouble(t -> t.getAmount().abs().doubleValue())
            .average()
            .orElse(0.0);
        double sumSq = allTransactions
            .stream()
            .mapToDouble(t ->
                Math.pow(t.getAmount().abs().doubleValue() - mean, 2)
            )
            .sum();
        double stdDev = Math.sqrt(sumSq / allTransactions.size());
        double threshold = mean + (3 * stdDev);

        for (StandardizedTransaction t : allTransactions) {
            if (t.getAmount().abs().doubleValue() > threshold && stdDev > 0) {
                anomalies.add(new Anomaly(
                    Anomaly.Category.OUTLIER,
                    String.format(
                        "Amount %s on %s exceeds 3 std-deviations (threshold: %.2f)",
                        t.getAmount(), t.getValueDate(), threshold
                    ),
                    t, null));
            }
        }

        // 3. Weekend/Holiday Flags (Sunday transactions)
        for (StandardizedTransaction t : allTransactions) {
            if (t.getValueDate().getDayOfWeek() == DayOfWeek.SUNDAY) {
                anomalies.add(new Anomaly(
                    Anomaly.Category.WEEKEND_POSTING,
                    String.format(
                        "Transaction %s for %s posted on a Sunday (%s)",
                        t.getTransactionId().toString().substring(0, 8).toUpperCase(),
                        t.getAmount(), t.getValueDate()
                    ),
                    t, null));
            }
        }

        System.out.printf("[ANOMALY ENGINE] Detected %d anomalies (dup=%d, outlier=%d, weekend=%d)%n",
            anomalies.size(),
            anomalies.stream().filter(a -> a.getCategory() == Anomaly.Category.DUPLICATE).count(),
            anomalies.stream().filter(a -> a.getCategory() == Anomaly.Category.OUTLIER).count(),
            anomalies.stream().filter(a -> a.getCategory() == Anomaly.Category.WEEKEND_POSTING).count());

        return anomalies;
    }

    private void findDuplicates(
        List<StandardizedTransaction> transactions,
        String source,
        List<Anomaly> anomalies
    ) {
        for (int i = 0; i < transactions.size(); i++) {
            for (int j = i + 1; j < transactions.size(); j++) {
                StandardizedTransaction t1 = transactions.get(i);
                StandardizedTransaction t2 = transactions.get(j);
                if (
                    t1.getValueDate().equals(t2.getValueDate()) &&
                    t1.getAmount().equals(t2.getAmount())
                ) {
                    anomalies.add(new Anomaly(
                        Anomaly.Category.DUPLICATE,
                        String.format(
                            "Duplicate %s entries on %s for %s",
                            source, t1.getValueDate(), t1.getAmount()
                        ),
                        t1, t2));
                }
            }
        }
    }
}
