package aval.ui.controller;

import aval.common.enums.HypothesisStatus;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.MatchingConfig;
import aval.domain.core.ReconciliationWorkspace;
import aval.persistence.DataStore;
import aval.ui.MainUIContext;
import aval.ui.util.UIAnimationUtil;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class FinanceController {

    @FXML private Label fdTitle;
    @FXML private Label fdSub;
    @FXML private HBox statRow;
    @FXML private GridPane coGrid;
    @FXML private GridPane srcGrid;
    @FXML private GridPane policyGrid;
    @FXML private TableView<HistoryRow> historyTable;
    @FXML private TableColumn<HistoryRow, String> hDateCol;
    @FXML private TableColumn<HistoryRow, String> hPeriodCol;
    @FXML private TableColumn<HistoryRow, String> hTxnsCol;
    @FXML private TableColumn<HistoryRow, String> hMatchedCol;
    @FXML private TableColumn<HistoryRow, String> hRateCol;
    @FXML private TableColumn<HistoryRow, String> hAnomCol;
    @FXML private TableColumn<HistoryRow, String> hStatusCol;

    private static final String INK = "0d0d0d";
    private static final String GREEN = "1a5c2a";
    private static final String AMBER = "92400e";
    private static final String BURGUNDY = "800020";

    private static final DateTimeFormatter LOADED_FMT =
        DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);

    public void initialize() {
        ClientOrganization client = MainUIContext.getInstance().getActiveClient();
        if (client != null) {
            fdTitle.setText(client.getName());
            fdSub.setText(
                "Reconciliation overview — " +
                    java.time.LocalDate.now().format(
                        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
                    )
            );
        } else {
            fdTitle.setText("Unknown Company");
            fdSub.setText("No company selected.");
        }

        populateCompanyGrid(client);
        populatePolicyGrid();
        refreshStats();

        setupHistoryTable();
        loadHistoryData(client);
    }

    /**
     * Rebuilds the stat cards and data-source card from live session state.
     * Called when the tab is shown and after each reconciliation run.
     */
    public void refreshStats() {
        MainUIContext ctx = MainUIContext.getInstance();
        List<StandardizedTransaction> ledger = ctx.getStandardizedLedgerTransactions();
        List<StandardizedTransaction> bank = ctx.getStandardizedBankTransactions();
        List<MatchHypothesis> all = ctx.getAllHypotheses();

        int ledgerN = ledger.size();
        int bankN = bank.size();
        boolean hasRun = all != null && !all.isEmpty();

        long autoN = hasRun
            ? all.stream().filter(h -> h.getStatus() == HypothesisStatus.AUTO_RECONCILED).count()
            : 0;
        long pendingN = hasRun
            ? all.stream().filter(h -> h.getStatus() == HypothesisStatus.PENDING_REVIEW).count()
            : 0;
        long anomN = ctx.getAnomalies().size();
        MatchingConfig config = ctx.getActiveMatchingConfig();

        String rateValue = hasRun && ledgerN > 0
            ? String.format(Locale.ENGLISH, "%.0f%%", (double) autoN / ledgerN * 100)
            : "—";
        String rateSub = hasRun
            ? String.format(
                Locale.ENGLISH,
                "%d auto-matched · ≥ %.0f%% confidence",
                autoN,
                config.getAutoConfirmThreshold() * 100
            )
            : "Run reconciliation to compute";

        statRow.getChildren().setAll(
            makeStatCard(
                "LEDGER RECORDS",
                ledgerN > 0 ? String.valueOf(ledgerN) : "—",
                ledgerN > 0 ? "Standardized transactions" : "Awaiting ledger upload",
                INK
            ),
            makeStatCard(
                "BANK RECORDS",
                bankN > 0 ? String.valueOf(bankN) : "—",
                bankN > 0 ? "Standardized transactions" : "Awaiting bank statement",
                INK
            ),
            makeStatCard("AUTO-MATCH RATE", rateValue, rateSub, GREEN),
            makeStatCard(
                "PENDING REVIEW",
                hasRun ? String.valueOf(pendingN) : "—",
                hasRun ? "Awaiting your approval" : "Awaiting reconciliation",
                AMBER
            ),
            makeStatCard(
                "ANOMALIES",
                hasRun ? String.valueOf(anomN) : "—",
                hasRun
                    ? (anomN > 0 ? "Require investigation" : "None detected")
                    : "Awaiting reconciliation",
                BURGUNDY
            )
        );

        populateSourcesGrid();
    }

    private VBox makeStatCard(String label, String value, String sub, String valColour) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        UIAnimationUtil.applyHoverLift(card);
        HBox.setHgrow(card, Priority.ALWAYS);
        Label lbl = new Label(label); lbl.getStyleClass().add("sc-label");
        Label val = new Label(value); val.getStyleClass().add("sc-value");
        val.setStyle("-fx-text-fill: #" + valColour + ";");
        Label s   = new Label(sub);   s.getStyleClass().add("sc-sub");
        card.getChildren().addAll(lbl, val, s);
        return card;
    }

    public void playEntranceAnimations() {
        List<Node> cards = new ArrayList<>(statRow.getChildren());
        UIAnimationUtil.playStaggeredEntrance(
            cards,
            Duration.millis(400),
            Duration.millis(150)
        );

        for (Node cardNode : cards) {
            if (!(cardNode instanceof VBox card) || card.getChildren().size() < 2) {
                continue;
            }
            Node valueNode = card.getChildren().get(1);
            if (!(valueNode instanceof Label valueLabel)) {
                continue;
            }
            String raw = valueLabel.getText();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            boolean isPercent = raw.contains("%");
            String digits = raw.replaceAll("[^0-9.\\-]", "");
            if (digits.isBlank()) {
                continue;
            }
            try {
                double target = Double.parseDouble(digits);
                UIAnimationUtil.rollNumber(valueLabel, target, 0, isPercent ? "%" : "");
            } catch (NumberFormatException ignored) {
                // Skip labels that are not numeric.
            }
        }
    }

    // =========================================================
    // Info cards — real session data only
    // =========================================================

    private void populateCompanyGrid(ClientOrganization client) {
        coGrid.setHgap(30);
        coGrid.setVgap(12);
        coGrid.getChildren().clear();

        MainUIContext ctx = MainUIContext.getInstance();
        ReconciliationWorkspace ws = ctx.getActiveWorkspace();

        String name = client != null ? client.getName() : "N/A";
        String clientId = client != null ? shortId(client.getOrgId()) : "N/A";
        String contact = client != null && client.getContactMetadata() != null
            && !client.getContactMetadata().isBlank()
            ? client.getContactMetadata()
            : "Not on file";
        String wsId = ws != null ? shortId(ws.getWorkspaceId()) : "N/A";
        String wsStatus = ws != null ? ws.getStatus().name() : "N/A";

        addGridRow(coGrid, 0, "Name", name);
        addGridRow(coGrid, 1, "Client ID", clientId);
        addGridRow(coGrid, 2, "Contact", contact);
        addGridRow(coGrid, 3, "Workspace", wsId);
        addGridRow(coGrid, 4, "Status", wsStatus);
    }

    private void populateSourcesGrid() {
        srcGrid.setHgap(30);
        srcGrid.setVgap(12);
        srcGrid.getChildren().clear();

        MainUIContext ctx = MainUIContext.getInstance();
        int ledgerN = ctx.getStandardizedLedgerTransactions().size();
        int bankN = ctx.getStandardizedBankTransactions().size();

        String ledgerFile = ctx.getLedgerSourceFileName();
        String bankFile = ctx.getBankSourceFileName();

        addGridRow(srcGrid, 0, "Ledger file",
            ledgerFile != null ? ledgerFile : (ledgerN > 0 ? "Restored from workspace" : "Not loaded"));
        addGridRow(srcGrid, 1, "Ledger records", ledgerN > 0 ? String.valueOf(ledgerN) : "—");
        addGridRow(srcGrid, 2, "Ledger loaded",
            ctx.getLedgerLoadedAt() != null ? ctx.getLedgerLoadedAt().format(LOADED_FMT) : "—");
        addGridRow(srcGrid, 3, "Bank file",
            bankFile != null ? bankFile : (bankN > 0 ? "Restored from workspace" : "Not loaded"));
        addGridRow(srcGrid, 4, "Bank records", bankN > 0 ? String.valueOf(bankN) : "—");
        addGridRow(srcGrid, 5, "Bank loaded",
            ctx.getBankLoadedAt() != null ? ctx.getBankLoadedAt().format(LOADED_FMT) : "—");
    }

    private void populatePolicyGrid() {
        policyGrid.setHgap(30);
        policyGrid.setVgap(12);
        policyGrid.getChildren().clear();

        MatchingConfig config = MainUIContext.getInstance().getActiveMatchingConfig();
        double auto = config.getAutoConfirmThreshold() * 100;
        double floor = config.getReviewFloor() * 100;

        addGridRow(policyGrid, 0, "Auto-confirm",
            String.format(Locale.ENGLISH, "≥ %.0f%% confidence", auto));
        addGridRow(policyGrid, 1, "Manual review",
            String.format(Locale.ENGLISH, "%.0f%% – %.0f%% confidence", floor, auto));
        addGridRow(policyGrid, 2, "Auto-reject",
            String.format(Locale.ENGLISH, "< %.0f%% confidence", floor));
        addGridRow(policyGrid, 3, "Date tolerance",
            "± " + config.getRuleBasedDateToleranceDays() + " days");
        addGridRow(policyGrid, 4, "AI candidates",
            "Top " + config.getSemanticMaxCandidates() + " per transaction");
    }

    private void addGridRow(GridPane grid, int row, String key, String value) {
        grid.add(makeGridLabel(key, true), 0, row);
        grid.add(makeGridLabel(value, false), 1, row);
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }

    private Label makeGridLabel(String text, boolean isKey) {
        Label l = new Label(text);
        if (isKey) {
            l.setStyle("-fx-text-fill: -fx-text-muted; -fx-font-size: 13px;");
        } else {
            l.setStyle("-fx-text-fill: -fx-text-primary; -fx-font-size: 13px; -fx-font-weight: bold;");
            l.setWrapText(true);
        }
        return l;
    }

    // =========================================================
    // History
    // =========================================================

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

    private void setupHistoryTable() {
        historyTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        hDateCol
            .prefWidthProperty()
            .bind(historyTable.widthProperty().subtract(5).multiply(0.12));
        hPeriodCol
            .prefWidthProperty()
            .bind(historyTable.widthProperty().subtract(5).multiply(0.20));
        hTxnsCol
            .prefWidthProperty()
            .bind(historyTable.widthProperty().subtract(5).multiply(0.12));
        hMatchedCol
            .prefWidthProperty()
            .bind(historyTable.widthProperty().subtract(5).multiply(0.14));
        hRateCol
            .prefWidthProperty()
            .bind(historyTable.widthProperty().subtract(5).multiply(0.12));
        hAnomCol
            .prefWidthProperty()
            .bind(historyTable.widthProperty().subtract(5).multiply(0.12));
        hStatusCol
            .prefWidthProperty()
            .bind(historyTable.widthProperty().subtract(5).multiply(0.18));

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
