//Dev: Rayyan
//Use Cases: App Bootstrap & Integration Wiring
package aval;

import aval.ui.MainUIContext;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Main entry point for the AVAL AI Reconciliation Engine.
 * Handles the boot sequence, splash screen, and transition to the Dashboard.
 */
public class Main extends Application {

    // Store transition as a field to prevent Garbage Collection
    private PauseTransition transitionDelay;

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println(
                "[BOOT] Starting AVAL AI Reconciliation Engine..."
            );

            // Ensure the application doesn't exit when the splash screen is hidden
            Platform.setImplicitExit(false);

            // Load Splash Screen
            System.out.println("[BOOT] Loading Splash.fxml...");
            FXMLLoader splashLoader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/Splash.fxml")
            );
            Parent splashRoot = splashLoader.load();
            Scene splashScene = new Scene(splashRoot);

            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setScene(splashScene);
            primaryStage.show();
            System.out.println("[BOOT] Splash screen displayed.");

            // Transition logic - using a field to ensure it stays alive
            transitionDelay = new PauseTransition(Duration.seconds(3.5));
            transitionDelay.setOnFinished(event -> {
                System.out.println("[TRANSITION] Initiating Dashboard load...");

                try {
                    // Locate Dashboard resource
                    var dashboardURL = getClass().getResource(
                        "/aval/ui/views/Dashboard.fxml"
                    );
                    if (dashboardURL == null) {
                        System.err.println("[ERROR] Dashboard.fxml NOT FOUND!");
                        Platform.exit();
                        return;
                    }

                    // Load Dashboard
                    FXMLLoader mainLoader = new FXMLLoader(dashboardURL);
                    Parent dashboardRoot = mainLoader.load();

                    // Prepare Stage
                    Stage mainStage = new Stage();
                    Scene mainScene = new Scene(dashboardRoot, 1200, 800);

                    // Apply Global Theme
                    MainUIContext.getInstance().applyTheme(mainScene);

                    mainStage.setTitle("AVAL AI Reconciliation Engine");
                    mainStage.setScene(mainScene);

                    // Show Dashboard FIRST
                    mainStage.show();

                    // Then cleanup Splash
                    primaryStage.hide();
                    Platform.setImplicitExit(true);
                    System.out.println(
                        "[SUCCESS] Application bootup complete."
                    );
                } catch (Exception e) {
                    System.err.println("[FATAL] Dashboard transition failed:");
                    e.printStackTrace();
                    Platform.exit();
                }
            });

            transitionDelay.play();
            System.out.println("[BOOT] Transition timer started.");
        } catch (Exception e) {
            System.err.println("[FATAL] Boot sequence failed:");
            e.printStackTrace();
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
