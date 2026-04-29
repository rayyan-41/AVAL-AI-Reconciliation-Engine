package aval.ui;

import aval.domain.ai.MatchHypothesis;
import aval.domain.core.ClientOrganization;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;

/**
 * Global context for the UI, managing application-wide state such as theme selection.
 */
public class MainUIContext {

    private static MainUIContext instance;
    private final BooleanProperty darkModeActive = new SimpleBooleanProperty(
        false
    );
    private ClientOrganization activeClient;
    private List<MatchHypothesis> pendingHypotheses;
    private Object workspaceController; // Using Object to avoid circular deps, cast later if needed.

    private MainUIContext() {
        // Private constructor for Singleton
    }

    public static MainUIContext getInstance() {
        if (instance == null) {
            instance = new MainUIContext();
        }
        return instance;
    }

    public BooleanProperty darkModeActiveProperty() {
        return darkModeActive;
    }

    public boolean isDarkModeActive() {
        return darkModeActive.get();
    }

    public void setDarkModeActive(boolean active) {
        darkModeActive.set(active);
    }

    public ClientOrganization getActiveClient() {
        return activeClient;
    }

    public void setActiveClient(ClientOrganization activeClient) {
        this.activeClient = activeClient;
    }

    public List<MatchHypothesis> getPendingHypotheses() {
        return pendingHypotheses;
    }

    public void setPendingHypotheses(List<MatchHypothesis> pendingHypotheses) {
        this.pendingHypotheses = pendingHypotheses;
    }

    public void setWorkspaceController(Object workspaceController) {
        this.workspaceController = workspaceController;
    }

    public Object getWorkspaceController() {
        return workspaceController;
    }

    /**
     * Applies the current theme to the provided scene.
     * @param scene The JavaFX scene to style.
     */
    public void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        String path = isDarkModeActive()
            ? "/aval/ui/styles/fintech-dark.css"
            : "/aval/ui/styles/fintech-light.css";
        var resource = getClass().getResource(path);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        } else {
            System.err.println("Theme resource not found: " + path);
        }
    }
}
