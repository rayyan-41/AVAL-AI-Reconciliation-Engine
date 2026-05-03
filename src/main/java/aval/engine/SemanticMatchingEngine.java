//Dev: Rayyan
//Use Cases: UC6
package aval.engine;

import aval.common.enums.MatchType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.SemanticEmbedding;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.MatchingConfig;
import aval.persistence.DataStore;
import java.util.ArrayList;
import java.util.List;

//@desc:   Concrete strategy utilizing AI semantic embeddings to find non-obvious transaction matches.
//@grasp:  Concrete Strategy, High Cohesion
//@gof:    Strategy
public class SemanticMatchingEngine implements MatchingEngine {

    //-------------- Attributes ----------------------//
    private final VectorizationEngine vectorizationEngine;
    private final DataStore dataStore;
    private final MatchingConfig matchingConfig;

    //-------------- Methods ----------------------//
    public SemanticMatchingEngine(
        VectorizationEngine vectorizationEngine,
        DataStore dataStore,
        MatchingConfig matchingConfig
    ) {
        this.vectorizationEngine = vectorizationEngine;
        this.dataStore = dataStore;
        this.matchingConfig = matchingConfig;
    }

    @Override
    public List<MatchHypothesis> generateHypotheses(
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    ) {
        List<MatchHypothesis> hypotheses = new ArrayList<>();
        int maxCandidates = matchingConfig.getSemanticMaxCandidates();

        for (StandardizedTransaction ledgerTx : ledgerTransactions) {
            try {
                // 1. Vectorize the ledger transaction narrative and metadata
                SemanticEmbedding ledgerEmbedding =
                    vectorizationEngine.vectorize(ledgerTx);

                // 2. Query pgvector for closest semantic matches with distances
                List<Object[]> candidates =
                    dataStore.findSimilarBankTransactions(
                        ledgerEmbedding,
                        maxCandidates
                    );

                // 3. Generate hypotheses using real distance-based confidence
                for (Object[] candidate : candidates) {
                    StandardizedTransaction bankTx =
                        (StandardizedTransaction) candidate[0];
                    double distance = (double) candidate[1];

                    // Convert cosine distance (0 to 2) to a confidence score (0 to 1)
                    // Cosine Distance = 1 - Cosine Similarity.
                    // A distance of 0.0 is a perfect match (100% confidence).
                    double confidence = Math.max(0, 1.0 - distance);

                    MatchHypothesis hypothesis = new MatchHypothesis(
                        ledgerTx,
                        bankTx,
                        confidence,
                        MatchType.AI_PROBABILISTIC
                    );

                    hypothesis.setJustification(
                        String.format(
                            "AI detected semantic similarity (Cosine Distance: %.4f)",
                            distance
                        )
                    );

                    hypotheses.add(hypothesis);
                }
            } catch (Exception e) {
                // In production, we would log this properly and potentially proceed with the next transaction
                System.err.println(
                    "Failed to perform semantic match for transaction " +
                        ledgerTx.getTransactionId() +
                        ": " +
                        e.getMessage()
                );
            }
        }

        return hypotheses;
    }
}
