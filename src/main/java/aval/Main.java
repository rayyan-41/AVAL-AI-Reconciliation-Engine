package aval;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Main entry point for the AVAL AI Reconciliation Engine.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println(
                "[BOOT] Starting AVAL AI Reconciliation Engine..."
            );

            Platform.setImplicitExit(true);

            // Load fonts before any Scene is created
            Font.loadFont(
                getClass().getResourceAsStream("/aval/ui/fonts/Jura-Bold.ttf"),
                72
            );
            Font.loadFont(
                getClass().getResourceAsStream(
                    "/aval/ui/fonts/Jura-SemiBold.ttf"
                ),
                18
            );

            System.out.println("[BOOT] Displaying Splash Screen...");
            FXMLLoader splashLoader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/Splash.fxml")
            );
            Parent splashRoot = splashLoader.load();
            Scene splashScene = new Scene(splashRoot, 800, 500);

            // Add the light theme css
            splashScene
                .getStylesheets()
                .add(
                    getClass()
                        .getResource("/aval/ui/styles/fintech-light.css")
                        .toExternalForm()
                );

            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setScene(splashScene);
            primaryStage.show();
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
