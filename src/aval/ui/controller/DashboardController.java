package aval.ui.controller;

import aval.domain.core.ClientOrganization;
import aval.ui.MainUIContext;
import aval.ui.util.MockUIProvider;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;

public class DashboardController {

    @FXML
    private ToggleButton themeToggle;

    @FXML
    private FlowPane clientCardsArea;

    @FXML
    private VBox dropZone;

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

            Label statusLabel = new Label(client.getContactInfo());
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

        themeToggle.setText(context.isDarkModeActive() ? "Light Mode" : "Dark Mode");
    }
}
