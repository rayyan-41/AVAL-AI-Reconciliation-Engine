package aval.ui.controller;

import aval.domain.SystemUser;
import aval.persistence.DataStore;
import aval.ui.MainUIContext;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;

public class AuthController {

    //-------------- Attributes ----------------------//
    @FXML
    private HBox rootNode;

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    private void handleClose() {
        System.exit(0);
    }

    @FXML
    private TextField workspaceIdField;

    @FXML
    private PasswordField passkeyField;

    @FXML
    private VBox loginForm;

    @FXML
    private VBox createUserForm;

    @FXML
    public void initialize() {
        if (rootNode != null) {
            rootNode.setOnMousePressed(e -> {
                xOffset = e.getSceneX();
                yOffset = e.getSceneY();
            });
            rootNode.setOnMouseDragged(e -> {
                Stage stage = (Stage) rootNode.getScene().getWindow();
                stage.setX(e.getScreenX() - xOffset);
                stage.setY(e.getScreenY() - yOffset);
            });
        }
        DataStore ds = MainUIContext.getInstance().getDataStore();
        if (ds != null && !ds.isAnyUserRegistered()) {
            if (loginForm != null) {
                loginForm.setVisible(false);
                loginForm.setManaged(false);
            }
            if (createUserForm != null) {
                createUserForm.setVisible(true);
                createUserForm.setManaged(true);
            }
        }
    }

    @FXML
    private void handleRegister() {
        navigateTo("Register.fxml");
    }

    @FXML
    private void handleAuthenticate() {
        String username = workspaceIdField.getText().trim();
        String passkey = passkeyField.getText();

        if (username.isBlank()) {
            workspaceIdField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            return;
        }
        workspaceIdField.setStyle("");
        if (passkey.isBlank()) {
            passkeyField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            return;
        }
        passkeyField.setStyle("");
        DataStore ds = MainUIContext.getInstance().getDataStore();
        if (ds == null) {
            workspaceIdField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            passkeyField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            return;
        }

        Task<SystemUser> authTask = new Task<>() {
            @Override
            protected SystemUser call() {
                return ds.findSystemUserByCredentials(username, passkey);
            }
        };

        authTask.setOnSucceeded(e -> {
            SystemUser user = authTask.getValue();
            if (user != null) {
                MainUIContext.getInstance().setCurrentUser(user);
                navigateTo("Registry.fxml");
            } else {
                workspaceIdField.setStyle(
                    "-fx-border-color: transparent transparent #991b1b transparent;"
                );
                passkeyField.setStyle(
                    "-fx-border-color: transparent transparent #991b1b transparent;"
                );
            }
        });

        authTask.setOnFailed(e -> {
            workspaceIdField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            passkeyField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            Throwable failure = authTask.getException();
            System.err.println(
                "Authentication failed: " + failure.getMessage()
            );
        });

        Thread authThread = new Thread(authTask);
        authThread.setDaemon(true);
        authThread.start();
    }

    private void navigateTo(String fxml) {
        try {
            Stage currentStage = (Stage) workspaceIdField
                .getScene()
                .getWindow();

            // Create fresh loader each time to avoid node reuse issues
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/" + fxml)
            );
            Parent root = loader.load();

            List<String> oldStyles = new ArrayList<>(workspaceIdField.getScene().getStylesheets());

            if (fxml.equals("Registry.fxml")) {
                Stage newStage = new Stage();
                newStage.setTitle("AVAL AIRE");
                newStage.initStyle(javafx.stage.StageStyle.DECORATED);
                Scene newScene = new Scene(root, 1200, 800);
                newScene.getStylesheets().addAll(oldStyles);
                newStage.setScene(newScene);
                newStage.centerOnScreen();
                newStage.show();
                currentStage.close();
            } else {
                Scene newScene = new Scene(root);
                newScene.getStylesheets().addAll(oldStyles);
                currentStage.setScene(newScene);
                currentStage.centerOnScreen();
            }
        } catch (Exception e) {
            System.err.println(
                "Could not navigate to " +
                    fxml +
                    " (it might not be created yet in this Phase)."
            );
            e.printStackTrace();
        }
    }
}
