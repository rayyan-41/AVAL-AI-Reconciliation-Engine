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
}
