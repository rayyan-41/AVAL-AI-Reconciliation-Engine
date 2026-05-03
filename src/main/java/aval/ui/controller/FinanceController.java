package aval.ui.controller;

import aval.common.enums.HypothesisStatus;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.persistence.DataStore;
import aval.ui.MainUIContext;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;

public class FinanceController {

    @FXML private Label fdTitle;
    @FXML private Label fdSub;
    @FXML private HBox statRow;
    @FXML private GridPane coGrid;
    @FXML private GridPane ldgGrid;
    @FXML private TableView<HistoryRow> historyTable;
    @FXML private TableColumn<HistoryRow, String> hDateCol;
    @FXML private TableColumn<HistoryRow, String> hPeriodCol;
    @FXML private TableColumn<HistoryRow, String> hTxnsCol;
    @FXML private TableColumn<HistoryRow, String> hMatchedCol;
    @FXML private TableColumn<HistoryRow, String> hRateCol;
    @FXML private TableColumn<HistoryRow, String> hAnomCol;
    @FXML private TableColumn<HistoryRow, String> hStatusCol;

    public void initialize() {
        ClientOrganization client = MainUIContext.getInstance().getActiveClient();
        if (client != null) {
            fdTitle.setText(client.getName());
            fdSub.setText("Financial dashboard and reconciliation metadata.");
        } else {
            fdTitle.setText("Unknown Company");
            fdSub.setText("No company selected.");
        }

        // Stat cards
        statRow.getChildren().addAll(
            makeStatCard("Ledger Transactions", "—",     "Load data to begin",    "0d0d0d"),
            makeStatCard("Last Match Rate",     "—",     "Run reconciliation",    "1a5c2a"),
            makeStatCard("Pending Review",      "—",     "Awaiting reconciliation","92400e"),
            makeStatCard("Flagged Anomalies",   "—",     "No data yet",           "800020")
        );

        // Metadata grids
        populateCompanyGrid(client);
        populateLedgerGrid();

        // History table
        setupHistoryTable();
        loadHistoryData(client);
    }

    private void loadHistoryData(ClientOrganization client) {
        DataStore ds = MainUIContext.getInstance().getDataStore();
        Task<List<DataStore.ReconciliationHistoryRow>> histTask = new Task<>() {
            @Override
            protected List<DataStore.ReconciliationHistoryRow> call() {
                if (client == null || ds == null) return new ArrayList<>();
                return ds.getReconciliationHistoryForOrg(client.getOrgId());
            }
        };

        histTask.setOnSucceeded(e -> Platform.runLater(() -> {
            List<DataStore.ReconciliationHistoryRow> dbRows = histTask.getValue();
            ObservableList<HistoryRow> uiRows = FXCollections.observableArrayList();
            for (DataStore.ReconciliationHistoryRow r : dbRows) {
                uiRows.add(new HistoryRow(r.date, r.period, r.txns, r.matched, r.rate, r.anomalies, r.status));
            }
            historyTable.setItems(uiRows);
        }));

        Thread histThread = new Thread(histTask);
        histThread.setDaemon(true);
        histThread.start();
    }

    public void refreshStats() {
        List<MatchHypothesis> all = MainUIContext.getInstance().getAllHypotheses();
        if (all == null || all.isEmpty()) {
            return;
        }

        List<StandardizedTransaction> ledger = MainUIContext.getInstance()
            .getStandardizedLedgerTransactions();
        long total = ledger != null ? ledger.size() : 0;
        long autoN = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.AUTO_RECONCILED)
            .count();
        long pending = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.PENDING_REVIEW)
            .count();
        List<String> anomalies = MainUIContext.getInstance().getAnomalies();
        long anomN = anomalies != null ? anomalies.size() : 0;

        statRow.getChildren().setAll(
            makeStatCard("Ledger Transactions", String.valueOf(total), "Current period", "0d0d0d"),
            makeStatCard("Auto-Matched", String.valueOf(autoN), "Confidence >= 95%", "1a5c2a"),
            makeStatCard("Pending Review", String.valueOf(pending), "Awaiting confirmation", "92400e"),
            makeStatCard("Anomalies Detected", String.valueOf(anomN), "Requires investigation", "800020")
        );
    }

    private VBox makeStatCard(String label, String value, String sub, String valColour) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        Label lbl = new Label(label); lbl.getStyleClass().add("sc-label");
        Label val = new Label(value); val.getStyleClass().add("sc-value");
        val.setStyle("-fx-text-fill: #" + valColour + ";");
        Label s   = new Label(sub);   s.getStyleClass().add("sc-sub");
        card.getChildren().addAll(lbl, val, s);
        return card;
    }

    private void populateCompanyGrid(ClientOrganization client) {
        coGrid.setHgap(30);
        coGrid.setVgap(12);

        String name = client != null ? client.getName() : "N/A";
        String id = client != null ? client.getOrgId().toString() : "N/A";
        String industry = "N/A";
        if (client != null) {
            if (name.contains("Retail")) industry = "Retail & eCommerce";
            else if (name.contains("Logistics")) industry = "Transportation & Logistics";
            else if (name.contains("Trust")) industry = "Financial Services";
            else if (name.contains("Estates")) industry = "Real Estate";
            else industry = "Enterprise Holding";
        }

        coGrid.add(makeGridLabel("Name", true), 0, 0); coGrid.add(makeGridLabel(name, false), 1, 0);
        coGrid.add(makeGridLabel("Workspace ID", true), 0, 1); coGrid.add(makeGridLabel(id, false), 1, 1);
        coGrid.add(makeGridLabel("Industry", true), 0, 2); coGrid.add(makeGridLabel(industry, false), 1, 2);
        coGrid.add(makeGridLabel("Registration", true), 0, 3); coGrid.add(makeGridLabel("Incorporated 2018", false), 1, 3);
        coGrid.add(makeGridLabel("Status", true), 0, 4); coGrid.add(makeGridLabel("Active Profile", false), 1, 4);
        coGrid.add(makeGridLabel("Onboarded", true), 0, 5); coGrid.add(makeGridLabel("Jan 12, 2025", false), 1, 5);
    }

    private void populateLedgerGrid() {
        ldgGrid.setHgap(30);
        ldgGrid.setVgap(12);

        // Dynamic date for current period
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter periodFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d");
        String periodStart = now.withDayOfMonth(1).format(periodFormatter);
        String periodEnd = now.format(periodFormatter);
        String currentYear = String.valueOf(now.getYear());

        ldgGrid.add(makeGridLabel("Source file", true), 0, 0); ldgGrid.add(makeGridLabel("—", false), 1, 0);
        ldgGrid.add(makeGridLabel("Period", true), 0, 1); ldgGrid.add(makeGridLabel(periodStart + " - " + periodEnd + ", " + currentYear, false), 1, 1);
        ldgGrid.add(makeGridLabel("Total Records", true), 0, 2); ldgGrid.add(makeGridLabel("—", false), 1, 2);
        ldgGrid.add(makeGridLabel("Debits", true), 0, 3); ldgGrid.add(makeGridLabel("—", false), 1, 3);
        ldgGrid.add(makeGridLabel("Credits", true), 0, 4); ldgGrid.add(makeGridLabel("—", false), 1, 4);
        ldgGrid.add(makeGridLabel("Net", true), 0, 5); ldgGrid.add(makeGridLabel("—", false), 1, 5);
        ldgGrid.add(makeGridLabel("Updated", true), 0, 6); ldgGrid.add(makeGridLabel("—", false), 1, 6);
    }

    private Label makeGridLabel(String text, boolean isKey) {
        Label l = new Label(text);
        if (isKey) {
            l.setStyle("-fx-text-fill: -fx-text-muted; -fx-font-size: 13px;");
        } else {
            l.setStyle("-fx-text-fill: -fx-text-primary; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
        return l;
    }

    private void setupHistoryTable() {
        hDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().date));
        hPeriodCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().period));
        hTxnsCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().txns));
        hMatchedCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().matched));
        hRateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().rate));
        hAnomCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().anomalies));
        hStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status));

        hRateCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label l = new Label(item);
                l.setStyle("-fx-text-fill: -fx-green-text; -fx-font-weight: bold;");
                setGraphic(l); setText(null);
            }
        });

        hAnomCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label l = new Label(item);
                if (!"0".equals(item)) {
                    l.setStyle("-fx-text-fill: -fx-red-text; -fx-font-weight: bold;");
                } else {
                    l.setStyle("-fx-text-fill: -fx-text-muted;");
                }
                setGraphic(l); setText(null);
            }
        });

        hStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(status);
                badge.getStyleClass().addAll("badge");
                switch (status.toLowerCase()) {
                    case "completed" -> badge.getStyleClass().add("badge-active");
                    case "in_progress" -> badge.getStyleClass().add("badge-pending");
                    default -> badge.getStyleClass().add("badge-review");
                }
                setGraphic(badge); setText(null);
            }
        });

        historyTable.setItems(FXCollections.observableArrayList());
    }

    public static class HistoryRow {
        public String date, period, txns, matched, rate, anomalies, status;
        public HistoryRow(String d, String p, String t, String m, String r, String a, String s) {
            this.date=d; this.period=p; this.txns=t; this.matched=m; this.rate=r; this.anomalies=a; this.status=s;
        }
    }
}
