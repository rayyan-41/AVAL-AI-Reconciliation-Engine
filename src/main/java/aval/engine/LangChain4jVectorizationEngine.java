//Dev: Rayyan
//Use Cases: UC5
package aval.engine;

import aval.domain.ai.SemanticEmbedding;
import aval.domain.ai.StandardizedTransaction;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

//@desc:   Concrete implementation using LangChain4j and a local Ollama instance for vectorization.
//@grasp:  Polymorphism, Indirection
//@gof:    Adapter / Strategy
public class LangChain4jVectorizationEngine implements VectorizationEngine {

    private final EmbeddingModel embeddingModel;

    public LangChain4jVectorizationEngine(String baseUrl, String modelName) {
        this.embeddingModel = OllamaEmbeddingModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();
    }

    @Override
    public SemanticEmbedding vectorize(StandardizedTransaction transaction) {
        // Create a rich text representation of the transaction to embed
        String textToEmbed = buildTextToEmbed(transaction);

        // Call the local Ollama model to get the vector
        Embedding embedding = embeddingModel.embed(textToEmbed).content();

        return new SemanticEmbedding(
            transaction.getTransactionId(),
            embedding.vector()
        );
    }

    @Override
    public List<SemanticEmbedding> vectorizeBatch(
        List<StandardizedTransaction> transactions
    ) {
        List<SemanticEmbedding> embeddings = new ArrayList<>();
    
        for (StandardizedTransaction tx : transactions) {
            embeddings.add(vectorize(tx));
        }
        return embeddings;
    }

    private String buildTextToEmbed(StandardizedTransaction transaction) {
        // Combining relevant fields to give the LLM maximum context.
        // e.g. "DEBIT | 150.00 | Office Supplies | Staples"
        return String.format(
            "%s | %s | %s",
            transaction.getType(),
            transaction.getAmount(),
            transaction.getNarrative()
        );
    }
}
