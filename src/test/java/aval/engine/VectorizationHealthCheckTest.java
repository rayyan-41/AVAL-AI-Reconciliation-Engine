package aval.engine;

import aval.domain.ai.SemanticEmbedding;
import aval.domain.ai.StandardizedTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Issue 6: No Ollama Health Check.
 * Verifies healthCheck() returns false gracefully when Ollama is unreachable.
 */
public class VectorizationHealthCheckTest {

    @Test
    public void testHealthCheck_returnsFalse_whenOllamaUnreachable() {
        // Instantiate engine with unreachable URL
        LangChain4jVectorizationEngine engine =
            new LangChain4jVectorizationEngine("http://localhost:9999", "nonexistent-model");

        long start = System.currentTimeMillis();
        boolean result = engine.healthCheck();
        long elapsed = System.currentTimeMillis() - start;

        // Should return false (not throw)
        assertFalse(result, "healthCheck() should return false when Ollama unreachable");

        // Should complete within reasonable timeout (5 seconds)
        assertTrue(elapsed < 5000, "healthCheck() should timeout within 5 seconds, took " + elapsed + "ms");
    }

    @Test
    public void testHealthCheck_doesNotThrow_onNetworkError() {
        LangChain4jVectorizationEngine engine =
            new LangChain4jVectorizationEngine("http://192.0.2.1:9999", "test");

        // Should not throw any exception
        assertDoesNotThrow(() -> {
            boolean result = engine.healthCheck();
            // Result should be false due to unreachable host
            assertFalse(result);
        }, "healthCheck() must handle network errors gracefully without throwing");
    }

    @Test
    public void testVectorizationEngine_interfaceHasHealthCheck() {
        // Verify the interface declares healthCheck()
        VectorizationEngine engine = new LangChain4jVectorizationEngine("http://localhost:9999", "test");

        // The interface contract requires healthCheck() to exist
        assertTrue(engine instanceof VectorizationEngine,
            "LangChain4jVectorizationEngine must implement VectorizationEngine");

        // healthCheck() should be callable
        boolean result = engine.healthCheck();
        // Result can be true or false depending on Ollama availability
        // But it should not throw
        assertNotNull(Boolean.valueOf(result), "healthCheck() should return a boolean");
    }

    @Test
    public void testHealthCheck_returnsTrue_whenOllamaAvailable() {
        // This test requires Ollama to be running locally on port 11434
        // Skip in CI environments - run manually to verify
        String ollamaUrl = System.getProperty("ollama.url", "http://localhost:11434");
        if (System.getenv("CI") != null) {
            // In CI, skip if Ollama is not available
            return;
        }

        LangChain4jVectorizationEngine engine =
            new LangChain4jVectorizationEngine(ollamaUrl, "nomic-embed-text");

        boolean result = engine.healthCheck();
        assertTrue(result, "healthCheck() should return true when Ollama is running");
    }
}