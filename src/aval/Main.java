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
 */
public class Main extends Application {

    private PauseTransition transitionDelay;

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println(
                "[BOOT] Starting AVAL AI Reconciliation Engine..."
            );

            // 1. Keep the JavaFX runtime alive even when windows are closed
            Platform.setImplicitExit(false);

            // 2. Setup and show Splash Screen
            System.out.println("[BOOT] Displaying Splash Screen...");
            FXMLLoader splashLoader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/Splash.fxml")
            );
            Parent splashRoot = splashLoader.load();
            Scene splashScene = new Scene(splashRoot);
            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setScene(splashScene);
            primaryStage.show();

            // 3. Initiate background loading of the Dashboard
            transitionDelay = new PauseTransition(Duration.seconds(3.5));
            transitionDelay.setOnFinished(event -> {
                System.out.println("[TRANSITION] Switching to Dashboard...");

                Platform.runLater(() -> {
                    try {
                        // Load Dashboard
                        System.out.println(
                            "[TRANSITION] Loading Dashboard.fxml..."
                        );
                        FXMLLoader mainLoader = new FXMLLoader(
                            getClass().getResource(
                                "/aval/ui/views/Dashboard.fxml"
                            )
                        );
                        Parent dashboardRoot = mainLoader.load();

                        Stage mainStage = new Stage();
                        Scene mainScene = new Scene(dashboardRoot, 1200, 800);

                        // Apply Theme
                        System.out.println(
                            "[TRANSITION] Applying global theme..."
                        );
                        MainUIContext.getInstance().applyTheme(mainScene);

                        mainStage.setTitle("AVAL AI Reconciliation Engine");
                        mainStage.setScene(mainScene);

                        // 4. Critical Handover: Show new first, then hide old
                        System.out.println(
                            "[TRANSITION] Launching Main Stage..."
                        );
                        mainStage.show();

                        // Hide splash
                        primaryStage.hide();

                        // 5. Allow app to exit naturally now that main window is up
                        Platform.setImplicitExit(true);
                        System.out.println("[SUCCESS] Boot sequence finished.");
                    } catch (Exception e) {
                        System.err.println(
                            "[FATAL ERROR] Failed to switch to Dashboard:"
                        );
                        e.printStackTrace();
                        Platform.exit(); // Exit if we can't load the main UI
                    }
                });
            });

            transitionDelay.play();
            System.out.println("[BOOT] Transition timer running.");
        } catch (Exception e) {
            System.err.println("[FATAL ERROR] Boot failed:");
            e.printStackTrace();
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
