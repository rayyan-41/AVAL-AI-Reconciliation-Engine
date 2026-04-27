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
    private VBox ingestionPortal; // We need to wrap existing content in an ID

    @FXML
    private void showIngestion() {
        contentArea.getChildren().setAll(ingestionPortal);
        System.out.println("Navigating to Ingestion...");
    }

    @FXML
    private void showAILab() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/AILab.fxml")
            );
            Parent aiLabView = loader.load();
            contentArea.getChildren().setAll(aiLabView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showReconciliation() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/ReconciliationHub.fxml")
            );
            Parent reconView = loader.load();
            contentArea.getChildren().setAll(reconView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showAudit() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/AuditVault.fxml")
            );
            Parent auditView = loader.load();
            contentArea.getChildren().setAll(auditView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initialize() {
        loadClientCards();

        // Ensure toggle matches current state
        themeToggle.setSelected(MainUIContext.getInstance().isDarkModeActive());
    }

    private void loadClientCards() {
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
