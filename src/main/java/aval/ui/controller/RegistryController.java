package aval.ui.controller;

import aval.domain.core.ClientOrganization;
import aval.ui.MainUIContext;
import aval.ui.util.MockUIProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class RegistryController {

    @FXML private TextField searchField;
    @FXML private TableView<ClientOrganization> clientTable;
    @FXML private TableColumn<ClientOrganization, String> nameCol;
    @FXML private TableColumn<ClientOrganization, String> industryCol;
    @FXML private TableColumn<ClientOrganization, String> statusCol;
    @FXML private TableColumn<ClientOrganization, Void> arrowCol;

    private ObservableList<ClientOrganization> allClients;
    private FilteredList<ClientOrganization> filtered;

    public void initialize() {
        allClients = FXCollections.observableArrayList(
            MockUIProvider.getMockClients(7)
        );
        filtered = new FilteredList<>(allClients, p -> true);

        searchField.textProperty().addListener((obs, old, nw) -> {
            filtered.setPredicate(c ->
                nw == null || nw.isBlank() ||
                c.getName().toLowerCase().contains(nw.toLowerCase()) ||
                c.getOrgId().toString().contains(nw.toLowerCase())
            );
        });

        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                ClientOrganization c = getTableRow().getItem();
                VBox box = new VBox(2);
                Label n = new Label(c.getName());
                n.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-text-primary; -fx-font-size: 14px;");
                Label id = new Label(c.getOrgId().toString());
                id.setStyle("-fx-font-family: 'Consolas'; -fx-text-fill: -fx-text-muted; -fx-font-size: 11px;");
                box.getChildren().addAll(n, id);
                setGraphic(box);
                setText(null);
            }
        });

        industryCol.setCellValueFactory(cd -> {
            String name = cd.getValue().getName();
            String ind = name.contains("Retail") ? "Retail & eCommerce" :
                         name.contains("Logistics") ? "Transportation & Logistics" :
                         name.contains("Trust") ? "Financial Services" :
                         name.contains("Estates") ? "Real Estate" :
                         "Enterprise Holding";
            return new SimpleStringProperty(ind);
        });

        statusCol.setCellValueFactory(cd -> {
            String name = cd.getValue().getName();
            String st = name.contains("Trust") ? "Active" :
                        name.contains("Logistics") ? "Pending" :
                        name.contains("Retail") ? "Active" :
                        "Review";
            return new SimpleStringProperty(st);
        });

        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(status);
                badge.getStyleClass().add("badge");
                switch (status.toLowerCase()) {
                    case "active":
                        badge.getStyleClass().add("badge-active");
                        break;
                    case "pending":
                        badge.getStyleClass().add("badge-pending");
                        break;
                    default:
                        badge.getStyleClass().add("badge-review");
                        break;
                }
                setGraphic(badge);
                setText(null);
            }
        });

        arrowCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                Label arrow = new Label("→");
                arrow.setStyle("-fx-text-fill: -fx-text-ghost; -fx-font-weight: bold; -fx-font-size: 16px;");
                setGraphic(arrow);
            }
        });

        clientTable.setItems(filtered);
    }

    @FXML
    private void handleRowClick(MouseEvent e) {
        if (e.getClickCount() >= 1) {
            ClientOrganization selected = clientTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openWorkspace(selected);
            }
        }
    }

    private void openWorkspace(ClientOrganization client) {
        MainUIContext.getInstance().setActiveClient(client);
        navigateTo("Workspace.fxml");
    }

    @FXML
    private void handleSignOut() {
        navigateTo("Auth.fxml");
    }

    @FXML
    private void handleAddClient() {
        System.out.println("Add Client requested (Not implemented in this phase).");
    }

    private void navigateTo(String fxml) {
        try {
            Stage stage = (Stage) searchField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/aval/ui/views/" + fxml));
            Parent root = loader.load();
            Scene newScene = new Scene(root);
            newScene.getStylesheets().addAll(searchField.getScene().getStylesheets());
            stage.setScene(newScene);
        } catch (Exception e) {
            System.err.println("Could not navigate to " + fxml + " (it might not be created yet).");
            e.printStackTrace();
        }
    }
}
