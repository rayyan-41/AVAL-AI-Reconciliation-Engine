package aval.ui.controller;

import aval.domain.core.ClientOrganization;
import aval.ui.MainUIContext;
import aval.ui.util.MockUIProvider;
import java.io.IOException;
import java.util.List;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class DashboardController {

    @FXML
    private ToggleButton themeToggle;

    @FXML
    private StackPane contentArea;

    @FXML
    private FlowPane clientCardsArea;

    @FXML
    private VBox ingestionPortal;

    @FXML
    private void showIngestion() {
        contentArea.getChildren().setAll(ingestionPortal);
        System.out.println("Navigating to Ingestion...");
    }

    @FXML
    private void showAILab() {
        loadView("/aval/ui/views/AILab.fxml");
    }

    @FXML
    private void showReconciliation() {
        loadView("/aval/ui/views/ReconciliationHub.fxml");
    }

    @FXML
    private void showAudit() {
        loadView("/aval/ui/views/AuditVault.fxml");
    }

    private void loadView(String fxmlPath) {
        try {
            System.out.println("[DASHBOARD] Loading view: " + fxmlPath);
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(fxmlPath)
            );
            Parent view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            System.err.println("[DASHBOARD] Failed to load view: " + fxmlPath);
            e.printStackTrace();
        }
    }

    public void initialize() {
        System.out.println("[DASHBOARD] Initializing controller...");
        try {
            loadClientCards();

            if (themeToggle != null) {
                themeToggle.setSelected(
                    MainUIContext.getInstance().isDarkModeActive()
                );
            }
            System.out.println("[DASHBOARD] Initialization complete.");
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Error during initialization:");
            e.printStackTrace();
        }
    }

    private void loadClientCards() {
        System.out.println("[DASHBOARD] Loading mock client cards...");
        List<ClientOrganization> clients = MockUIProvider.getMockClients(3);
        clientCardsArea.getChildren().clear();

        for (ClientOrganization client : clients) {
            VBox card = new VBox(5);
            card.getStyleClass().add("client-card");
            card.setPrefSize(180, 100);
            card.setPadding(new Insets(15));

            Label nameLabel = new Label(client.getName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            Label statusLabel = new Label(client.getContactMetadata());
            statusLabel.getStyleClass().add("text-muted");
            statusLabel.setStyle("-fx-font-size: 11px;");

            card.getChildren().addAll(nameLabel, statusLabel);
            clientCardsArea.getChildren().add(card);
        }
        System.out.println("[DASHBOARD] Loaded " + clients.size() + " cards.");
    }

    @FXML
    private void handleThemeToggle() {
        MainUIContext context = MainUIContext.getInstance();
        context.setDarkModeActive(themeToggle.isSelected());
        context.applyTheme(themeToggle.getScene());
        themeToggle.setText(
            context.isDarkModeActive() ? "Light Mode" : "Dark Mode"
        );
    }
}
