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
import javafx.stage.Stage;

public class AuthController {

    @FXML
    private TextField workspaceIdField;

    @FXML
    private PasswordField passkeyField;

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
            System.err.println("Authentication failed: " + failure.getMessage());
        });

        Thread authThread = new Thread(authTask);
        authThread.setDaemon(true);
        authThread.start();
    }

    private void navigateTo(String fxml) {
        try {
            Stage stage = (Stage) workspaceIdField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/" + fxml)
            );
            Parent root = loader.load();
            Scene newScene = new Scene(root);

            // Reapply existing stylesheets from the old scene if any
            newScene
                .getStylesheets()
                .addAll(workspaceIdField.getScene().getStylesheets());

            stage.setScene(newScene);
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
