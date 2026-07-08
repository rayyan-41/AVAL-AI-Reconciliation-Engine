//Dev: Claude (Agent)
package aval.ui.controller;

/**
 * Interface for workspace controller operations that need to be accessed
 * from other controllers without circular dependency.
 */
public interface IWorkspaceController {
    /**
     * Unlocks the manual check tab after reconciliation completes.
     * Does not navigate — the user stays on the results summary.
     */
    void unlockManualCheck();

    /**
     * Unlocks the manual check tab and navigates to it (explicit user action).
     */
    void openManualCheck();

    /**
     * Unlocks the report tab and navigates to it after manual check completes.
     */
    void unlockReport();
}