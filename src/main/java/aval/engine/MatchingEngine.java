//Dev: Rayyan
//Use Cases: UC6
package aval.engine;

import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import java.util.List;

//@desc:   Standard interface for reconciliation strategies. Allows the Service to swap between Rule-Based and Semantic engines.
//@grasp:  Polymorphism, Strategy Pattern
//@gof:    Strategy
public interface MatchingEngine {
    /**
     * Analyzes two sets of transactions and generates potential match candidates.
     * @param ledgerTransactions Transactions from the internal accounting system.
     * @param bankTransactions Transactions from the external financial institution.
     * @return A list of proposed MatchHypothesis objects.
     */
    List<MatchHypothesis> generateHypotheses(
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    );

    /**
     * Same as {@link #generateHypotheses(List, List)} but reports pipeline stage
     * transitions to the given listener so the UI can track real progress.
     * Engines without internal stages simply ignore the listener.
     */
    default List<MatchHypothesis> generateHypotheses(
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions,
        MatchingProgressListener progressListener
    ) {
        return generateHypotheses(ledgerTransactions, bankTransactions);
    }
}
