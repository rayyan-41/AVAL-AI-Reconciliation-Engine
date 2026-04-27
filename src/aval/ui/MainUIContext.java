package aval.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;

/**
 * Global context for the UI, managing application-wide state such as theme selection.
 */
public class MainUIContext {

    private static MainUIContext instance;
    private final BooleanProperty darkModeActive = new SimpleBooleanProperty(false);

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

    /**
     * Applies the current theme to the provided scene.
     * @param scene The JavaFX scene to style.
     */
    public void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        if (isDarkModeActive()) {
            scene.getStylesheets().add(getClass().getResource("styles/fintech-dark.css").toExternalForm());
        } else {
            scene.getStylesheets().add(getClass().getResource("styles/fintech-light.css").toExternalForm());
        }
    }
}
