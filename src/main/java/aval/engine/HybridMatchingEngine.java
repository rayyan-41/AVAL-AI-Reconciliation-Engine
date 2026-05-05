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

    //-------------- Attributes ----------------------//
    private final MatchingEngine ruleBasedEngine;
    private final MatchingEngine semanticEngine;

    //-------------- Methods ----------------------//
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
        Set<UUID> matchedBankIds = new HashSet<>();
        for (MatchHypothesis h : ruleResults) {
            if (h.getLedgerTransaction() != null) {
                matchedLedgerIds.add(h.getLedgerTransaction().getTransactionId());
            }
            if (h.getBankTransaction() != null) {
                matchedBankIds.add(h.getBankTransaction().getTransactionId());
            }
        }

        List<StandardizedTransaction> unmatchedLedger = new ArrayList<>();
        for (StandardizedTransaction tx : ledgerTransactions) {
            if (!matchedLedgerIds.contains(tx.getTransactionId())) {
                unmatchedLedger.add(tx);
            }
        }

        List<StandardizedTransaction> unmatchedBank = new ArrayList<>();
        for (StandardizedTransaction tx : bankTransactions) {
            if (!matchedBankIds.contains(tx.getTransactionId())) {
                unmatchedBank.add(tx);
            }
        }

        List<MatchHypothesis> allResults = new ArrayList<>(ruleResults);
        if (!unmatchedLedger.isEmpty() && !unmatchedBank.isEmpty()) {
            List<MatchHypothesis> semanticResults =
                semanticEngine.generateHypotheses(unmatchedLedger, unmatchedBank);

            Set<UUID> usedBankIds = new HashSet<>(matchedBankIds);
            for (MatchHypothesis h : semanticResults) {
                UUID bankId = h.getBankTransaction() != null
                    ? h.getBankTransaction().getTransactionId()
                    : null;
                if (bankId != null && usedBankIds.contains(bankId)) {
                    h.setStatus(aval.common.enums.HypothesisStatus.REJECTED);
                    h.setJustification("Auto-rejected: Bank transaction already matched by rule-based or higher-confidence hypothesis");
                } else {
                    usedBankIds.add(bankId);
                    allResults.add(h);
                }
            }
        }

        return allResults;
    }
}