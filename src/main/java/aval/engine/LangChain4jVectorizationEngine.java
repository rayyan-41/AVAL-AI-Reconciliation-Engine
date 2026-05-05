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
        // Extract reference number from narrative if present (e.g., "Payment to Vendor | REF-12345678 | Invoice 999")
        String narrative = transaction.getNarrative() != null ? transaction.getNarrative() : "";
        String refNumber = extractReferenceNumber(narrative);

        // Combining relevant fields to give the LLM maximum context.
        // Reference number is included first as it's the strongest matching signal per dataset README.
        // e.g. "REF-12345678 | DEBIT | 150.00 | Office Supplies | Staples"
        StringBuilder sb = new StringBuilder();
        if (!refNumber.isEmpty()) {
            sb.append(refNumber).append(" | ");
        }
        sb.append(transaction.getType()).append(" | ")
          .append(transaction.getAmount()).append(" | ")
          .append(narrative);
        return sb.toString();
    }

    private String extractReferenceNumber(String text) {
        if (text == null || text.isEmpty()) return "";
        // Match common reference number patterns: REF-XXXXXXXX, TXN-XXXX, INV-XXXX, CHECK-XXXX
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(REF-[A-Z0-9]{8,}|TXN-[A-Z0-9]{6,}|INV-[A-Z0-9]{4,}|CHECK-[A-Z0-9]{4,}|\\b[A-Z]{2,3}-[0-9]{4,}\\b)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().toUpperCase();
        }
        return "";
    }

    @Override
    public boolean healthCheck() {
        try {
            // Attempt a minimal embedding to verify Ollama is reachable
            embeddingModel.embed("health check");
            return true;
        } catch (Exception e) {
            System.err.println("[healthCheck] Ollama unreachable: " + e.getMessage());
            return false;
        }
    }
}
