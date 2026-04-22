package aval.engine;

import aval.domain.ai.SemanticEmbedding;
import aval.domain.ai.StandardizedTransaction;
import java.util.List;

//@desc:   Standard interface for generating vector embeddings from transactions.
//@grasp:  Polymorphism, Protected Variations
//@gof:    Strategy
public interface VectorizationEngine {
    /**
     * Generates a semantic vector embedding for a single transaction.
     * @param transaction The standardized transaction to vectorize.
     * @return A SemanticEmbedding containing the vector representation.
     */
    SemanticEmbedding vectorize(StandardizedTransaction transaction);

    /**
     * Generates semantic vector embeddings for a batch of transactions.
     * @param transactions The list of standardized transactions.
     * @return A list of SemanticEmbeddings.
     */
    List<SemanticEmbedding> vectorizeBatch(
        List<StandardizedTransaction> transactions
    );
}
