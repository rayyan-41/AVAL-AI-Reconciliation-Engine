//Dev: Rayyan
//Use Cases: UC6 fallback
package aval.engine;

import aval.common.enums.MatchType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.MatchingConfig;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

//@desc:   Deterministic engine that uses hard-coded business rules (Exact Amount, Date Proximity) to find matches.
//@grasp:  Concrete Strategy, High Cohesion
//@gof:    Strategy
public class RuleBasedMatchingEngine implements MatchingEngine {

    //-------------- Attributes ----------------------//
    private final MatchingConfig matchingConfig;
    private static final double EXACT_MATCH_CONFIDENCE = 1.0;

    public RuleBasedMatchingEngine(MatchingConfig matchingConfig) {
        this.matchingConfig = matchingConfig;
    }

    //-------------- Methods ----------------------//
    @Override
    public List<MatchHypothesis> generateHypotheses(
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    ) {
        List<MatchHypothesis> hypotheses = new ArrayList<>();
        int dateToleranceDays = matchingConfig.getRuleBasedDateToleranceDays();

        // Logic: Iterate through ledger transactions and find potential matches in bank data
        // For a prototype, O(N*M) is acceptable. For production, we would use a hash-map by amount.
        for (StandardizedTransaction ledger : ledgerTransactions) {
            for (StandardizedTransaction bank : bankTransactions) {
                if (isMatch(ledger, bank, dateToleranceDays)) {
                    MatchHypothesis hypothesis = new MatchHypothesis(
                        ledger,
                        bank,
                        EXACT_MATCH_CONFIDENCE,
                        MatchType.EXACT_RULE
                    );
                    hypothesis.setJustification(
                        "Exact amount match within " + dateToleranceDays + "-day tolerance window."
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
     * and occur within the configured number of days of each other.
     */
    private boolean isMatch(
        StandardizedTransaction ledger,
        StandardizedTransaction bank,
        int dateToleranceDays
    ) {
        boolean sameAmount =
            ledger.getAmount().compareTo(bank.getAmount()) == 0;

        long daysBetween = Math.abs(
            ChronoUnit.DAYS.between(ledger.getValueDate(), bank.getValueDate())
        );
        boolean withinDateRange = daysBetween <= dateToleranceDays;

        // Transaction types should usually be opposite (e.g. Ledger Credit vs Bank Debit)
        // or handled by the standardization layer to be comparable.
        return sameAmount && withinDateRange;
    }
}
