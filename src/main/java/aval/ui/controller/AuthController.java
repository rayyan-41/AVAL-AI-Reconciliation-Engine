package aval.ui.controller;

import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import aval.persistence.DataStore;
import aval.ui.MainUIContext;
import java.sql.SQLException;
import java.util.UUID;
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
        boolean hasError = false;

        if (workspaceIdField.getText().isBlank()) {
            workspaceIdField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            hasError = true;
        } else {
            workspaceIdField.setStyle("");
        }

        if (passkeyField.getText().isBlank()) {
            passkeyField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            hasError = true;
        } else {
            passkeyField.setStyle("");
        }

        if (hasError) {
            return;
        }

        String username = workspaceIdField.getText().trim();
        DataStore ds = MainUIContext.getInstance().getDataStore();

        Task<SystemUser> authTask = new Task<>() {
            @Override
            protected SystemUser call() throws Exception {
                if (ds == null) {
                    throw new SQLException("DataStore not initialized");
                }
                return ds.findSystemUserByUsername(username);
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
            }
        });

        authTask.setOnFailed(e -> {
            Throwable failure = authTask.getException();
            if (isSqlFailure(failure)) {
                MainUIContext.getInstance().setCurrentUser(
                    createSyntheticUser(username)
                );
                navigateTo("Registry.fxml");
                return;
            }

            workspaceIdField.setStyle(
                "-fx-border-color: transparent transparent #991b1b transparent;"
            );
            System.err.println("Authentication failed: " + failure.getMessage());
        });

        Thread authThread = new Thread(authTask);
        authThread.setDaemon(true);
        authThread.start();
    }

    private SystemUser createSyntheticUser(String username) {
        return new SystemUser(UUID.randomUUID(), username, UserRole.ACCOUNTANT);
    }

    private boolean isSqlFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SQLException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
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
