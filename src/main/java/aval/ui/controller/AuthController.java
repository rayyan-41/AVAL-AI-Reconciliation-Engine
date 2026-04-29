package aval.ui.controller;

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

        // Phase 2: navigate to Registry.fxml
        navigateTo("Registry.fxml");
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
