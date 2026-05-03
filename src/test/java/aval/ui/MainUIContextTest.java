package aval.ui;

import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import aval.ui.controller.IWorkspaceController;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for Issue 2: MainUIContext Thread-Unsafe Singleton + State Leak.
 */
public class MainUIContextTest {

    @Test
    public void testSingletonIdentity_sameInstanceAcrossThreads() throws Exception {
        MainUIContext[] instances = new MainUIContext[10];
        Thread[] threads = new Thread[10];

        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                instances[index] = MainUIContext.getInstance();
            });
            threads[i].start();
        }

        for (Thread t : threads) t.join();

        for (int i = 1; i < 10; i++) {
            assertSame(instances[0], instances[i], "All threads should get the same singleton instance");
        }
        assertSame(MainUIContext.getInstance(), MainUIContext.getInstance());
    }

    @Test
    public void testClearSession_nullsAllFields() {
        MainUIContext ctx = MainUIContext.getInstance();

        ctx.setStandardizedLedgerTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Test", aval.common.enums.TransactionType.DEBIT, UUID.randomUUID())
        ));
        ctx.setStandardizedBankTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Test", aval.common.enums.TransactionType.CREDIT, UUID.randomUUID())
        ));
        ctx.setPendingHypotheses(List.of(
            new MatchHypothesis(null, null, 0.85, aval.common.enums.MatchType.AI_PROBABILISTIC)
        ));
        ctx.setAllHypotheses(List.of());
        ctx.setUnmatchedLedger(List.of());
        ctx.setUnmatchedBank(List.of());
        ctx.setAnomalies(List.of("anomaly1"));

        ctx.clearSession();

        assertNotNull(ctx.getPendingHypotheses());
        assertEquals(0, ctx.getPendingHypotheses().size());
        assertNotNull(ctx.getStandardizedLedgerTransactions());
        assertEquals(0, ctx.getStandardizedLedgerTransactions().size());
        assertNotNull(ctx.getStandardizedBankTransactions());
        assertEquals(0, ctx.getStandardizedBankTransactions().size());
        assertNotNull(ctx.getAllHypotheses());
        assertEquals(0, ctx.getAllHypotheses().size());
        assertNotNull(ctx.getUnmatchedLedger());
        assertNotNull(ctx.getUnmatchedBank());
        assertNotNull(ctx.getAnomalies());
        assertEquals(0, ctx.getAnomalies().size());
    }

    @Test
    public void testGetters_returnEmptyList_whenNotSet() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();

        assertNotNull(ctx.getPendingHypotheses());
        assertNotNull(ctx.getStandardizedLedgerTransactions());
        assertNotNull(ctx.getStandardizedBankTransactions());
        assertNotNull(ctx.getAllHypotheses());
        assertNotNull(ctx.getUnmatchedLedger());
        assertNotNull(ctx.getUnmatchedBank());
        assertNotNull(ctx.getAnomalies());
    }

    @Test
    public void testVectorizationAvailableFlag() {
        MainUIContext ctx = MainUIContext.getInstance();

        assertTrue(ctx.isVectorizationAvailable(), "Should default to true");

        ctx.setVectorizationAvailable(false);
        assertFalse(ctx.isVectorizationAvailable());

        ctx.setVectorizationAvailable(true);
        assertTrue(ctx.isVectorizationAvailable());
    }

    @Test
    public void testWorkspaceControllerTypedSetter() {
        MainUIContext ctx = MainUIContext.getInstance();
        IWorkspaceController mock = mock(IWorkspaceController.class);
        ctx.setWorkspaceController(mock);
        assertSame(mock, ctx.getWorkspaceController());
    }
}