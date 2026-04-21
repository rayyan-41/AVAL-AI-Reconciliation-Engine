package aval.engine;

import aval.common.enums.MatchType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

//@desc:   Deterministic engine that uses hard-coded business rules (Exact Amount, Date Proximity) to find matches.
//@grasp:  Concrete Strategy, High Cohesion
//@gof:    Strategy
public class RuleBasedMatchingEngine implements MatchingEngine {

    private static final int DATE_TOLERANCE_DAYS = 7;
    private static final double EXACT_MATCH_CONFIDENCE = 1.0;

    @Override
    public List<MatchHypothesis> generateHypotheses(
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    ) {
        List<MatchHypothesis> hypotheses = new ArrayList<>();

        // Logic: Iterate through ledger transactions and find potential matches in bank data
        // For a prototype, O(N*M) is acceptable. For production, we would use a hash-map by amount.
        for (StandardizedTransaction ledger : ledgerTransactions) {
            for (StandardizedTransaction bank : bankTransactions) {
                if (isMatch(ledger, bank)) {
                    MatchHypothesis hypothesis = new MatchHypothesis(
                        ledger,
                        bank,
                        EXACT_MATCH_CONFIDENCE,
                        MatchType.EXACT_RULE
                    );
                    hypothesis.setJustification(
                        "Exact amount match within date tolerance window."
                    );
                    hypotheses.add(hypothesis);

                    // Note: In a real scenario, we'd mark these as 'considered'
                    // to avoid one-to-many duplicates, but we leave that to the Service orchestrator.
                }
            }
        }

        return hypotheses;
    }

    /**
     * Business Rule: Transactions match if they have the exact same amount
     * and occur within 7 days of each other.
     */
    private boolean isMatch(
        StandardizedTransaction ledger,
        StandardizedTransaction bank
    ) {
        boolean sameAmount =
            ledger.getAmount().compareTo(bank.getAmount()) == 0;

        long daysBetween = Math.abs(
            ChronoUnit.DAYS.between(ledger.getValueDate(), bank.getValueDate())
        );
        boolean withinDateRange = daysBetween <= DATE_TOLERANCE_DAYS;

        // Transaction types should usually be opposite (e.g. Ledger Credit vs Bank Debit)
        // or handled by the standardization layer to be comparable.
        return sameAmount && withinDateRange;
    }
}
