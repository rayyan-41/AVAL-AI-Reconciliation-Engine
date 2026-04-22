//Dev: Rayyan
//Use Cases: UC6
package aval.engine;

import aval.common.enums.MatchType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.SemanticEmbedding;
import aval.domain.ai.StandardizedTransaction;
import aval.persistence.DataStore;
import java.util.ArrayList;
import java.util.List;

//@desc:   Concrete strategy utilizing AI semantic embeddings to find non-obvious transaction matches.
//@grasp:  Concrete Strategy, High Cohesion
//@gof:    Strategy
public class SemanticMatchingEngine implements MatchingEngine {

    private final VectorizationEngine vectorizationEngine;
    private final DataStore dataStore;

    // Configurable thresholds
    private static final int MAX_CANDIDATES = 3;
    private static final double BASE_CONFIDENCE = 0.85;

    public SemanticMatchingEngine(
        VectorizationEngine vectorizationEngine,
        DataStore dataStore
    ) {
        this.vectorizationEngine = vectorizationEngine;
        this.dataStore = dataStore;
    }

    @Override
    public List<MatchHypothesis> generateHypotheses(
        List<StandardizedTransaction> ledgerTransactions,
        List<StandardizedTransaction> bankTransactions
    ) {
        List<MatchHypothesis> hypotheses = new ArrayList<>();

        for (StandardizedTransaction ledgerTx : ledgerTransactions) {
            try {
                // 1. Vectorize the ledger transaction narrative and metadata
                SemanticEmbedding ledgerEmbedding =
                    vectorizationEngine.vectorize(ledgerTx);

                // 2. Query pgvector for closest semantic matches
                // Note: We rely on the DataStore because pgvector handles the heavy lifting
                // of cosine similarity search across the entire bank transaction dataset.
                List<StandardizedTransaction> similarBankTxs =
                    dataStore.findSimilarBankTransactions(
                        ledgerEmbedding,
                        MAX_CANDIDATES
                    );

                // 3. Generate hypotheses for the top candidates
                for (int i = 0; i < similarBankTxs.size(); i++) {
                    StandardizedTransaction bankTx = similarBankTxs.get(i);

                    // We generate a descending confidence score based on rank for the prototype
                    // (e.g. 0.85, 0.80, 0.75) since DataStore doesn't return the exact cosine distance yet.
                    double confidence = BASE_CONFIDENCE - (i * 0.05);

                    MatchHypothesis hypothesis = new MatchHypothesis(
                        ledgerTx,
                        bankTx,
                        confidence,
                        MatchType.AI_PROBABILISTIC
                    );

                    hypothesis.setJustification(
                        String.format(
                            "AI detected high semantic similarity in narrative and metadata. Rank: %d",
                            i + 1
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
