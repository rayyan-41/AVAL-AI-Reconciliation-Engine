package aval.engine;

import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Composite engine that runs rule-based matching first, then falls back
 * to semantic AI matching for any ledger transactions that didn't get
 * an exact match.
 */
public class HybridMatchingEngine implements MatchingEngine {

    private final MatchingEngine ruleBasedEngine;
    private final MatchingEngine semanticEngine;

    public HybridMatchingEngine(
        MatchingEngine ruleBasedEngine,
        MatchingEngine semanticEngine
    ) {
        this.ruleBasedEngine = ruleBasedEngine;
        this.semanticEngine = semanticEngine;
    }

    @Override
    public List<MatchHypothesis> generateHypotheses(
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    ) {
        List<MatchHypothesis> ruleResults =
            ruleBasedEngine.generateHypotheses(ledgerTransactions, bankTransactions);

        Set<UUID> matchedLedgerIds = new HashSet<>();
        for (MatchHypothesis h : ruleResults) {
            if (h.getLedgerTransaction() != null) {
                matchedLedgerIds.add(h.getLedgerTransaction().getTransactionId());
            }
        }

        List<StandardizedTransaction> unmatchedLedger = new ArrayList<>();
        for (StandardizedTransaction tx : ledgerTransactions) {
            if (!matchedLedgerIds.contains(tx.getTransactionId())) {
                unmatchedLedger.add(tx);
            }
        }

        List<MatchHypothesis> allResults = new ArrayList<>(ruleResults);
        if (!unmatchedLedger.isEmpty()) {
            allResults.addAll(
                semanticEngine.generateHypotheses(unmatchedLedger, bankTransactions)
            );
        }

        return allResults;
    }
}