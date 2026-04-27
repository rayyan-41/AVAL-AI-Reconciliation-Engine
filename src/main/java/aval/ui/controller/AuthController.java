package aval.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class AuthController {

    @FXML
    private TextField workspaceIdField;

    @FXML
    private PasswordField passkeyField;

    @FXML
    private Button authButton;

    @FXML
    private void handleAuthenticate() {
        String workspaceId = workspaceIdField.getText();
        String passkey = passkeyField.getText();

        if (workspaceId != null && !workspaceId.trim().isEmpty() &&
            passkey != null && !passkey.trim().isEmpty()) {

            System.out.println("User Authenticated");

            // Clear fields as requested
            workspaceIdField.clear();
            passkeyField.clear();

            // In a real flow, this would trigger navigation to the main dashboard
        } else {
            System.out.println("Authentication Failed: Empty Fields");
        }
    }
}
