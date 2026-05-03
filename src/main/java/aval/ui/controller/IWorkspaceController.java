//Dev: Claude (Agent)
package aval.ui.controller;

/**
 * Interface for workspace controller operations that need to be accessed
 * from other controllers without circular dependency.
 */
public interface IWorkspaceController {
    /**
     * Unlocks the manual check tab after reconciliation completes.
     */
    void unlockManualCheck();
}