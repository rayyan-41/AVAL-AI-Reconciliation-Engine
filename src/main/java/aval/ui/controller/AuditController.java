package aval.ui.controller;

import aval.domain.ai.MatchHypothesis;
import aval.ui.util.MockUIProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

public class AuditController {

    @FXML
    private TableView<MatchHypothesis> anomaliesTable;
    @FXML
    private TableColumn<MatchHypothesis, String> dateCol, typeCol, justificationCol, confidenceCol;

    public void initialize() {
        setupColumns();
        loadData();
        setupRowFactory();
    }

    private void setupColumns() {
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getLedgerTransaction().getValueDate().toString()
        ));
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getMatchType().toString()
        ));
        justificationCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getJustification()
        ));
        confidenceCol.setCellValueFactory(data -> new SimpleStringProperty(
            String.format("%.2f", data.getValue().getConfidenceScore())
        ));
    }

    private void loadData() {
        anomaliesTable.setItems(FXCollections.observableArrayList(MockUIProvider.getMockAnomalies(10)));
    }

    private void setupRowFactory() {
        anomaliesTable.setRowFactory(tv -> new TableRow<MatchHypothesis>() {
            @Override
            protected void updateItem(MatchHypothesis item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    getStyleClass().remove("anomaly-row");
                } else if (item.getJustification().contains("CRITICAL")) {
                    if (!getStyleClass().contains("anomaly-row")) {
                        getStyleClass().add("anomaly-row");
                    }
                } else {
                    getStyleClass().remove("anomaly-row");
                }
            }
        });
    }
}
