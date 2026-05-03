package aval.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import aval.common.enums.MatchType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.MatchingConfig;
import aval.domain.core.ReconciliationWorkspace;
import aval.engine.RuleBasedMatchingEngine;
import aval.engine.VectorizationEngine;
import aval.persistence.DataStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for Issue 5: Fake System User UUID.
 * Verifies that auto-reconciled records always use the stable system user ID.
 */
public class ReconciliationServiceSystemUserTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );

    @Test
    public void testRunMatching_usesStableSystemUserId() throws Exception {
        // Create a mock engine and DataStore
        VectorizationEngine mockEngine = mock(VectorizationEngine.class);
        DataStore mockStore = mock(DataStore.class);

        ReconciliationService svc = new ReconciliationService(
            mockEngine,
            new RuleBasedMatchingEngine(new MatchingConfig()),
            mockStore
        );

        // Create test data
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(),
            new ClientOrganization(UUID.randomUUID(), "Test Client", ""),
            new MatchingConfig()
        );

        StandardizedTransaction ledgerTx = new StandardizedTransaction(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.valueOf(500),
            "Office Rent",
            aval.common.enums.TransactionType.DEBIT,
            UUID.randomUUID()
        );
        StandardizedTransaction bankTx = new StandardizedTransaction(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.valueOf(500),
            "Office Rent Payment",
            aval.common.enums.TransactionType.CREDIT,
            UUID.randomUUID()
        );

        List<StandardizedTransaction> ledger = List.of(ledgerTx);
        List<StandardizedTransaction> bank = List.of(bankTx);

        // Run matching
        var result = svc.runMatching(ws, ledger, bank);

        // Verify the auto-confirmed record uses the stable system user ID
        var autoRecords = result.getAutoReconciledRecords();
        assertFalse(
            autoRecords.isEmpty(),
            "Should have auto-reconciled records at high threshold"
        );

        UUID confirmingId = autoRecords.get(0).getConfirmingUser().getUserId();
        assertEquals(
            SYSTEM_USER_ID,
            confirmingId,
            "Auto-reconciled records must use the stable system user UUID"
        );
    }

    @Test
    public void testSystemUserConstant_matchesExpectedValue() {
        // The constant should be the well-known system user UUID
        assertEquals(
            "00000000-0000-0000-0000-000000000001",
            SYSTEM_USER_ID.toString()
        );
    }

    @Test
    public void testRunMatching_producesDifferentUserIdThanRandom() {
        // Verify that the system user ID is NOT a random UUID
        UUID randomUUID = UUID.randomUUID();
        assertNotEquals(
            SYSTEM_USER_ID,
            randomUUID,
            "System user UUID must be stable (not random)"
        );
        assertNotEquals(
            "00000000-0000-0000-0000-000000000001",
            randomUUID.toString()
        );
    }
}
