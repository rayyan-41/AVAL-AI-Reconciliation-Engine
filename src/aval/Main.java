package aval;

import aval.ui.controller.AppController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

//Responsibility: Rayyan
//Status: Complete
//Explanation: This class is responsible for app bootstrap and final integration wiring

/**
 * Boilerplate Main Class for AVAL AI Reconciliation Engine
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        System.out.println("Starting AVAL AI Reconciliation Engine UI...");

        AppController appController = new AppController();
        Scene scene = new Scene(appController.getView(), 1000, 700);

        primaryStage.setTitle("AVAL AI Reconciliation Engine");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
