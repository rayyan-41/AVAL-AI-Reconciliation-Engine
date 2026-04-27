//Dev: Rayyan
//Use Cases: App Bootstrap & Integration Wiring
package aval;

import aval.ui.MainUIContext;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Boilerplate Main Class for AVAL AI Reconciliation Engine
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("Starting AVAL AI Reconciliation Engine UI...");

        // Load Splash Screen
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("ui/views/Splash.fxml")
        );
        Parent splashRoot = loader.load();
        Scene splashScene = new Scene(splashRoot);

        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setScene(splashScene);
        primaryStage.show();

        // Transition to Main View after 3 seconds
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(event -> {
            try {
                primaryStage.close();

                Stage mainStage = new Stage();
                aval.ui.controller.AppController appController =
                    new aval.ui.controller.AppController();
                Scene mainScene = new Scene(appController.getView(), 1200, 800);

                MainUIContext.getInstance().applyTheme(mainScene);

                mainStage.setTitle("AVAL AI Reconciliation Engine");
                mainStage.setScene(mainScene);
                mainStage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        delay.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
