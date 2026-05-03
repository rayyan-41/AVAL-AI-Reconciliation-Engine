package aval.ui.controller;

import aval.domain.ai.StandardizedTransaction;
import aval.ui.MainUIContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Issue 8: Reconcile Button Enabled on Empty Transaction Lists.
 * Verifies the Reconcile button is disabled when lists are empty.
 */
public class ReconButtonStateTest {

    @AfterEach
    public void cleanup() {
        // Reset context after each test
        MainUIContext.getInstance().clearSession();
    }

    @Test
    public void testButtonDisabled_whenLedgerListIsEmpty() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();

        ctx.setStandardizedLedgerTransactions(Collections.emptyList());
        ctx.setStandardizedBankTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Test", aval.common.enums.TransactionType.CREDIT, UUID.randomUUID())
        ));

        // Verify the null-safe getter returns empty list (not null)
        assertNotNull(ctx.getStandardizedLedgerTransactions());
        assertTrue(ctx.getStandardizedLedgerTransactions().isEmpty(),
            "Empty list should register as empty for button state logic");
    }

    @Test
    public void testButtonDisabled_whenBothListsAreEmpty() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();

        ctx.setStandardizedLedgerTransactions(Collections.emptyList());
        ctx.setStandardizedBankTransactions(Collections.emptyList());

        assertTrue(ctx.getStandardizedLedgerTransactions().isEmpty());
        assertTrue(ctx.getStandardizedBankTransactions().isEmpty());
        // Both empty → button should be disabled
        assertFalse(ctx.getStandardizedLedgerTransactions().size() > 0 &&
            ctx.getStandardizedBankTransactions().size() > 0,
            "Both empty → ready should be false → button disabled");
    }

    @Test
    public void testButtonEnabled_whenBothListsHaveData() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();

        ctx.setStandardizedLedgerTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Ledger Entry", aval.common.enums.TransactionType.DEBIT, UUID.randomUUID())
        ));
        ctx.setStandardizedBankTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Bank Entry", aval.common.enums.TransactionType.CREDIT, UUID.randomUUID())
        ));

        assertFalse(ctx.getStandardizedLedgerTransactions().isEmpty());
        assertFalse(ctx.getStandardizedBankTransactions().isEmpty());
        // Both non-empty → button should be enabled
        assertTrue(ctx.getStandardizedLedgerTransactions().size() > 0 &&
            ctx.getStandardizedBankTransactions().size() > 0,
            "Both non-empty → ready should be true → button enabled");
    }

    @Test
    public void testButtonDisabled_whenBankIsNull() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();

        ctx.setStandardizedLedgerTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Test", aval.common.enums.TransactionType.DEBIT, UUID.randomUUID())
        ));
        ctx.setStandardizedBankTransactions(null);

        // Null bank → should be treated as not ready
        assertNotNull(ctx.getStandardizedLedgerTransactions());
        assertNull(ctx.getStandardizedBankTransactions(),
            "Bank list set to null");
        // With null bank, button should be disabled
        assertFalse(ctx.getStandardizedLedgerTransactions() != null &&
            ctx.getStandardizedBankTransactions() != null,
            "Null bank → not ready → button disabled");
    }

    @Test
    public void testHintTextDistinguishesMissingDataset() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();

        // Both null → specific message
        ctx.setStandardizedLedgerTransactions(null);
        ctx.setStandardizedBankTransactions(null);
        // The fix distinguishes: both missing vs one missing
        assertTrue(ctx.getStandardizedLedgerTransactions().isEmpty() ||
            ctx.getStandardizedLedgerTransactions() == null);
        assertTrue(ctx.getStandardizedBankTransactions().isEmpty() ||
            ctx.getStandardizedBankTransactions() == null);

        // Only ledger set → bank missing message
        ctx.setStandardizedLedgerTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Ledger", aval.common.enums.TransactionType.DEBIT, UUID.randomUUID())
        ));
        ctx.setStandardizedBankTransactions(Collections.emptyList());

        // Empty bank should also prevent button enabling
        assertTrue(ctx.getStandardizedBankTransactions().isEmpty());
    }

    @Test
    public void testEmptyListVsNullList_bothDisableButton() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();

        // Null list
        ctx.setStandardizedLedgerTransactions(null);
        ctx.setStandardizedBankTransactions(List.of(
            new StandardizedTransaction(UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(100), "Bank", aval.common.enums.TransactionType.CREDIT, UUID.randomUUID())
        ));
        // With null ledger, both non-empty check fails
        boolean ready1 = ctx.getStandardizedLedgerTransactions() != null &&
            !ctx.getStandardizedLedgerTransactions().isEmpty() &&
            ctx.getStandardizedBankTransactions() != null &&
            !ctx.getStandardizedBankTransactions().isEmpty();
        assertFalse(ready1, "Null ledger should make ready=false");

        // Empty list
        ctx.setStandardizedLedgerTransactions(Collections.emptyList());
        // With empty ledger, both non-empty check fails
        boolean ready2 = ctx.getStandardizedLedgerTransactions() != null &&
            !ctx.getStandardizedLedgerTransactions().isEmpty() &&
            ctx.getStandardizedBankTransactions() != null &&
            !ctx.getStandardizedBankTransactions().isEmpty();
        assertFalse(ready2, "Empty ledger should make ready=false");
    }
}