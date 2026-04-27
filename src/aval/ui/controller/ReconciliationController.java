package aval.ui.controller;

import aval.domain.ai.StandardizedTransaction;
import aval.ui.util.MockUIProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

import java.util.Random;

public class ReconciliationController {

    @FXML
    private TableView<StandardizedTransaction> ledgerTable;
    @FXML
    private TableColumn<StandardizedTransaction, String> lDateCol, lRefCol, lNarrativeCol, lAmountCol;

    @FXML
    private TableView<StandardizedTransaction> bankTable;
    @FXML
    private TableColumn<StandardizedTransaction, String> bDateCol, bRefCol, bNarrativeCol, bAmountCol;

    @FXML
    private HBox floatingActionBar;
    @FXML
    private Label confidenceLabel;

    private final Random random = new Random();

    public void initialize() {
        setupColumns();
        loadData();
        setupSelectionListener();
    }

    private void setupColumns() {
        // Ledger Columns
        lDateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValueDate().toString()));
        lRefCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTransactionId().toString().substring(0, 8)));
        lNarrativeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNarrative()));
        lAmountCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAmount().toString()));

        // Bank Columns
        bDateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValueDate().toString()));
        bRefCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTransactionId().toString().substring(0, 8)));
        bNarrativeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNarrative()));
        bAmountCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAmount().toString()));
    }

    private void loadData() {
        ledgerTable.setItems(FXCollections.observableArrayList(MockUIProvider.getMockTransactions(20)));
        bankTable.setItems(FXCollections.observableArrayList(MockUIProvider.getMockTransactions(20)));
    }

    private void setupSelectionListener() {
        ledgerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Simulate AI finding a match
                int randomIndex = random.nextInt(bankTable.getItems().size());
                bankTable.getSelectionModel().select(randomIndex);
                bankTable.scrollTo(randomIndex);

                showActionBar();
            } else {
                hideActionBar();
            }
        });
    }

    private void showActionBar() {
        floatingActionBar.setVisible(true);
        floatingActionBar.setManaged(true);
        // Randomize confidence for effect
        int confidence = 85 + random.nextInt(15);
        confidenceLabel.setText("Confidence Score: " + confidence + "%");
    }

    private void hideActionBar() {
        floatingActionBar.setVisible(false);
        floatingActionBar.setManaged(false);
    }
}
