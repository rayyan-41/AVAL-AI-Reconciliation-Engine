package aval.ui.controller;

import aval.common.enums.UserRole;
import aval.persistence.DataStore;
import aval.ui.MainUIContext;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;
import javafx.scene.input.MouseEvent;
import org.mindrot.jbcrypt.BCrypt;

public class RegisterController {
    @FXML private HBox rootNode;
    private double xOffset = 0;
    private double yOffset = 0;
    @FXML private void handleClose() { System.exit(0); }

    @FXML private TextField nameField;
    @FXML private TextField cnicField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private ComboBox<String> locationComboBox;
    @FXML private PasswordField keyField;

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
        roleComboBox.getItems().addAll("Finance Officer", "System Officer");
        locationComboBox.getItems().addAll("DHA V", "BAHRIA PHASE 4");
    }

    @FXML
    private void handleCreateUser() {
        String key = keyField.getText();
        if (!"0767@AVAL".equals(key) && !"0581@AVAL".equals(key)) {
            keyField.setStyle("-fx-border-color: transparent transparent #991b1b transparent;");
            return;
        }
        keyField.setStyle("");

        String name = nameField.getText().trim();
        String cnic = cnicField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String roleSelection = roleComboBox.getValue();
        String location = locationComboBox.getValue();

        if (name.isBlank() || cnic.isBlank() || username.isBlank() || password.isBlank() || roleSelection == null || location == null) {
            return; // Add proper styling/feedback if needed
        }

        UserRole role = "Finance Officer".equals(roleSelection) ? UserRole.ACCOUNTANT : UserRole.ADMIN;
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));

        DataStore ds = MainUIContext.getInstance().getDataStore();
        if (ds != null) {
            ds.registerUser(name, cnic, username, role, location, hashedPassword);
            navigateTo("Auth.fxml");
        }
    }

    @FXML
    private void handleBack() {
        navigateTo("Auth.fxml");
    }

    private void navigateTo(String fxml) {
        try {
            Stage stage = (Stage) nameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/aval/ui/views/" + fxml));
            Parent root = loader.load();
            Scene newScene = new Scene(root);
            newScene.getStylesheets().addAll(nameField.getScene().getStylesheets());
            stage.setScene(newScene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
