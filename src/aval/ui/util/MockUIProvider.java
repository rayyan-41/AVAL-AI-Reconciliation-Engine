package aval.ui.util;

import aval.common.enums.MatchType;
import aval.common.enums.TransactionType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Utility class to provide mock data for the UI, ensuring complete decoupling
 * from the actual backend logic during frontend development.
 */
public class MockUIProvider {

    private static final Random random = new Random();

    public static List<ClientOrganization> getMockClients(int count) {
        List<ClientOrganization> clients = new ArrayList<>();
        String[] names = {
            "Pacific Trust",
            "BrightLine Retail",
            "Azure Logistics",
            "Bordeaux Estates",
            "Onyx Holdings",
        };
        for (int i = 0; i < count; i++) {
            clients.add(
                new ClientOrganization(
                    UUID.randomUUID(),
                    names[i % names.length],
                    "Active - Last sync: " + (i + 1) + "h ago"
                )
            );
        }
        return clients;
    }

    public static List<StandardizedTransaction> getMockTransactions(int count) {
        List<StandardizedTransaction> transactions = new ArrayList<>();
        UUID datasetId = UUID.randomUUID();

        String[] narratives = {
            "Z-Bank Transfer 8821",
            "Onyx Logistics INV-99",
            "Azure Cloud Services",
            "Bordeaux Vineyards LTD",
            "Payroll Disbursement Q1",
            "Internal Adjustment - MISC",
            "Global Equities Dividend",
            "Strata Properties Rent",
        };

        for (int i = 0; i < count; i++) {
            transactions.add(
                new StandardizedTransaction(
                    UUID.randomUUID(),
                    LocalDate.now().minusDays(random.nextInt(30)),
                    new BigDecimal(random.nextInt(1000000) / 100.0),
                    narratives[random.nextInt(narratives.length)],
                    TransactionType.values()[random.nextInt(
                        TransactionType.values().length
                    )],
                    datasetId
                )
            );
        }
        return transactions;
    }

    public static List<MatchHypothesis> getMockHypotheses(int count) {
        List<MatchHypothesis> hypotheses = new ArrayList<>();
        List<StandardizedTransaction> ledgerBatch = getMockTransactions(count);
        List<StandardizedTransaction> bankBatch = getMockTransactions(count);

        for (int i = 0; i < count; i++) {
            double confidence = 0.7 + (0.3 * random.nextDouble());
            MatchType type =
                random.nextDouble() > 0.3
                    ? MatchType.AI_PROBABILISTIC
                    : MatchType.EXACT_RULE;

            MatchHypothesis hypothesis = new MatchHypothesis(
                ledgerBatch.get(i),
                bankBatch.get(i),
                confidence,
                type
            );

            if (confidence < 0.85) {
                hypothesis.setJustification(
                    "Fuzzy match detected on narrative and amount variance < 0.05%."
                );
            } else {
                hypothesis.setJustification(
                    "Deterministic rule match on Reference ID."
                );
            }

            hypotheses.add(hypothesis);
        }
        return hypotheses;
    }

    public static List<MatchHypothesis> getMockAnomalies(int count) {
        List<MatchHypothesis> anomalies = new ArrayList<>();
        List<StandardizedTransaction> ledgerBatch = getMockTransactions(count);
        List<StandardizedTransaction> bankBatch = getMockTransactions(count);

        for (int i = 0; i < count; i++) {
            double confidence = 0.3 + (0.4 * random.nextDouble());
            MatchHypothesis hypothesis = new MatchHypothesis(
                ledgerBatch.get(i),
                bankBatch.get(i),
                confidence,
                MatchType.AI_PROBABILISTIC
            );

            if (i % 3 == 0) {
                hypothesis.setJustification(
                    "CRITICAL: Amount Mismatch detected (> $500 variance)"
                );
            } else {
                hypothesis.setJustification(
                    "Fuzzy match: Timing variance > 48 hours."
                );
            }

            anomalies.add(hypothesis);
        }
        return anomalies;
    }
}
