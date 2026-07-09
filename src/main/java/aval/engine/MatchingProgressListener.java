package aval.engine;

/**
 * Listener interface for tracking pipeline stage transitions
 * of reconciliation matching engines.
 */
public interface MatchingProgressListener {

    enum Stage {
        // Core engine steps
        RULE_MATCH,
        SEMANTIC_MATCH,

        // Pipeline/Service steps
        PREPARE,
        CLASSIFY,
        PERSIST,
        COMPLETE
    }

    /**
     * Called when the matching engine transitions to a new pipeline stage.
     *
     * @param stage The current stage of execution.
     */
    void onStage(Stage stage);
}
