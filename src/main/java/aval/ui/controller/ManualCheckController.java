package aval.ui.controller;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import aval.domain.ai.Anomaly;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.service.ReconciliationService;
import aval.ui.MainUIContext;
import aval.ui.util.UIAnimationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class ManualCheckController {

    @FXML private Label mcTitle;
    @FXML private Label mcSub;
    @FXML private Label mcProgText;
    @FXML private ProgressBar mcProgressBar;
    @FXML private TableView<MatchHypothesis> mcTable;
    @FXML private TableColumn<MatchHypothesis, Double> mcConfCol;
    @FXML private TableColumn<MatchHypothesis, String> mcLedgerCol;
    @FXML private TableColumn<MatchHypothesis, String> mcBankCol;
    @FXML private TableColumn<MatchHypothesis, String> mcJustCol;
    @FXML private TableColumn<MatchHypothesis, Void> mcActionCol;
    @FXML private VBox anomalyPane;
    @FXML private ListView<Anomaly> anomalyList;
    @FXML private HBox mcCompleteBar;
    @FXML private Button btnApproveAllPending;
    @FXML private Button btnRejectAllPending;

    // UC8: Force match panel
    @FXML private VBox forceMatchPane;
    @FXML private TableView<StandardizedTransaction> unmatchedLedgerTable;
    @FXML private TableColumn<StandardizedTransaction, String> fmLDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> fmLAmtCol;
    @FXML private TableColumn<StandardizedTransaction, String> fmLNarrCol;
    @FXML private TableView<StandardizedTransaction> unmatchedBankTable;
    @FXML private TableColumn<StandardizedTransaction, String> fmBDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> fmBAmtCol;
    @FXML private TableColumn<StandardizedTransaction, String> fmBNarrCol;
    @FXML private TextField forceJustField;
    @FXML private Button btnForceLink;
    @FXML private Label forceFeedback;

    // UC9: Multi-Source Consolidation panel
    @FXML private VBox consolidationPane;
    @FXML private TableView<StandardizedTransaction> consolidationBankTable;
    @FXML private TableColumn<StandardizedTransaction, String> csBDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> csBAmtCol;
    @FXML private TableColumn<StandardizedTransaction, String> csBNarrCol;
    @FXML private TableView<StandardizedTransaction> consolidationLedgerTable;
    @FXML private TableColumn<StandardizedTransaction, String> csLDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> csLAmtCol;
    @FXML private TableColumn<StandardizedTransaction, String> csLNarrCol;
    @FXML private TextField toleranceField;
    @FXML private Label consolidationSumLabel;
    @FXML private Button btnConsolidate;
    @FXML private Label consolidationFeedback;

    //-------------- Attributes ----------------------//
    private static final String DECISION_CONFIRMED = "CONFIRMED";
    private static final String DECISION_REJECTED = "REJECTED";
    private IWorkspaceController workspaceController;
    private ObservableList<MatchHypothesis> hypothesesList;
    private int resolved = 0;
    private boolean anomaliesDismissed = true;

    //-------------- Methods ----------------------//
    public void setWorkspaceController(IWorkspaceController wc) { this.workspaceController = wc; }

    @FXML
    public void initialize() {
        mcSub.setText(
            "Review AI-suggested hypotheses below 95% confidence and approve or reject each item."
        );
        UIAnimationUtil.applyButtonPressFeedback(btnApproveAllPending);
        UIAnimationUtil.applyButtonPressFeedback(btnRejectAllPending);
        setupForceMatchTables();
        setupConsolidationTables();
        hypothesesList = FXCollections.observableArrayList();
        mcTable.setItems(hypothesesList);
        mcTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        mcConfCol
            .prefWidthProperty()
            .bind(mcTable.widthProperty().subtract(5).multiply(0.10));
        mcLedgerCol
            .prefWidthProperty()
            .bind(mcTable.widthProperty().subtract(5).multiply(0.25));
        mcBankCol
            .prefWidthProperty()
            .bind(mcTable.widthProperty().subtract(5).multiply(0.25));
        mcJustCol
            .prefWidthProperty()
            .bind(mcTable.widthProperty().subtract(5).multiply(0.25));
        mcActionCol
            .prefWidthProperty()
            .bind(mcTable.widthProperty().subtract(5).multiply(0.15));

        // Value factories
        mcConfCol.setCellValueFactory(data ->
            new SimpleObjectProperty<>(data.getValue().getConfidenceScore())
        );
        mcLedgerCol.setCellValueFactory(data -> {
            return new SimpleStringProperty(
                formatTransaction(data.getValue().getLedgerTransaction())
            );
        });
        mcBankCol.setCellValueFactory(data -> {
            return new SimpleStringProperty(
                formatTransaction(data.getValue().getBankTransaction())
            );
        });
        mcJustCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getJustification())
        );

        // Confidence pill cell
        mcConfCol.setCellFactory(col ->
            new TableCell<MatchHypothesis, Double>() {
                @Override
                protected void updateItem(Double conf, boolean empty) {
                    super.updateItem(conf, empty);
                    if (empty || conf == null) {
                        setGraphic(null);
                        return;
                    }
                    int pct = (int) (conf * 100);
                    Label pill = new Label(pct + "%");
                    pill
                        .getStyleClass()
                        .addAll("conf-pill", pct >= 85 ? "conf-hi" : "conf-lo");
                    setGraphic(pill);
                    setText(null);
                }
            }
        );

        // Action buttons cell
        mcActionCol.setCellFactory(col ->
            new TableCell<MatchHypothesis, Void>() {
                private final Button approve = createIconActionButton(
                    "Approve",
                    "M3 10 L8 15 L17 5",
                    "#b6d9be",
                    "#1a5c2a",
                    "#1a5c2a",
                    "#f0faf2"
                );
                private final Button reject = createIconActionButton(
                    "Reject",
                    "M4 4 L16 16 M16 4 L4 16",
                    "#f2c8cc",
                    "#991b1b",
                    "#991b1b",
                    "#fdf2f5"
                );

                {
                    approve.setOnAction(e ->
                        resolveItem(getIndex(), DECISION_CONFIRMED, getTableRow())
                    );
                    reject.setOnAction(e ->
                        resolveItem(getIndex(), DECISION_REJECTED, getTableRow())
                    );
                }

                @Override
                protected void updateItem(Void v, boolean empty) {
                    super.updateItem(v, empty);
                    MatchHypothesis item = empty
                        ? null
                        : getTableView().getItems().get(getIndex());
                    if (item == null) {
                        setGraphic(null);
                        return;
                    }
                    if (item.getStatus() == HypothesisStatus.PENDING_REVIEW) {
                        setGraphic(new HBox(8, approve, reject));
                    } else {
                        boolean confirmed = item.getStatus() == HypothesisStatus.APPROVED;
                        Label done = new Label(confirmed ? "CONFIRMED" : "REJECTED");
                        done.setStyle(
                            confirmed
                                ? "-fx-text-fill: #1a5c2a;"
                                : "-fx-text-fill: #800020;"
                        );
                        setGraphic(done);
                    }
                    setText(null);
                }
            }
        );
    }

    public void setItems(List<MatchHypothesis> pendingHypotheses) {
        List<MatchHypothesis> source =
            pendingHypotheses != null ? pendingHypotheses : List.of();
        hypothesesList.setAll(
            source
                .stream()
                .filter(h -> h.getConfidenceScore() < 0.95)
                .collect(Collectors.toList())
        );
        resolved = (int) hypothesesList
            .stream()
            .filter(h -> h.getStatus() != HypothesisStatus.PENDING_REVIEW)
            .count();
        mcCompleteBar.setVisible(false);
        mcCompleteBar.setManaged(false);
        updateProgress();
    }

    public void loadAnomalies() {
        List<Anomaly> anomalies = MainUIContext.getInstance().getAnomalies();
        if (anomalies != null && !anomalies.isEmpty()) {
            anomaliesDismissed = false;
            anomalyList.setItems(FXCollections.observableArrayList(anomalies));
            anomalyList.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Anomaly item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); return; }
                    String badge = switch (item.getCategory()) {
                        case DUPLICATE              -> "⚠ DUPLICATE";
                        case OUTLIER                -> "📈 OUTLIER";
                        case WEEKEND_POSTING        -> "📅 WEEKEND";
                        case CONSOLIDATION_VARIANCE -> "∑ VARIANCE";
                    };
                    setText(badge + "  —  " + item.getDescription());
                }
            });
            anomalyPane.setVisible(true);
            anomalyPane.setManaged(true);
        } else {
            anomaliesDismissed = true;
            anomalyList.getItems().clear();
            anomalyPane.setVisible(false);
            anomalyPane.setManaged(false);
        }
        checkIfComplete();
    }

    @FXML
    void handleDismissAnomalies() {
        anomaliesDismissed = true;
        anomalyPane.setVisible(false);
        anomalyPane.setManaged(false);
        anomalyList.getItems().clear();
        MainUIContext.getInstance().setAnomalies(new ArrayList<>());
        checkIfComplete();
    }

    @FXML
    void handleApproveAllPending() {
        resolveAllPending(DECISION_CONFIRMED);
    }

    @FXML
    void handleRejectAllPending() {
        resolveAllPending(DECISION_REJECTED);
    }

    private void resolveAllPending(String decision) {
        List<MatchHypothesis> pending = hypothesesList
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.PENDING_REVIEW)
            .collect(Collectors.toList());
        if (pending.isEmpty()) {
            return;
        }

        SystemUser user = getOrCreateCurrentUser();
        ReconciliationService svc = getReconciliationServiceOrShowError();
        if (svc == null) {
            return;
        }

        pending.forEach(h -> applyDecision(h, decision));
        mcTable.refresh();
        updateProgress();
        persistDecisionsAsync(pending, decision, user, svc);
    }

    private void resolveItem(
        int index,
        String decision,
        TableRow<MatchHypothesis> row
    ) {
        if (index < 0 || index >= mcTable.getItems().size()) {
            return;
        }

        MatchHypothesis h = mcTable.getItems().get(index);
        if (h.getStatus() != HypothesisStatus.PENDING_REVIEW) {
            return;
        }

        SystemUser user = getOrCreateCurrentUser();
        ReconciliationService svc = getReconciliationServiceOrShowError();
        if (svc == null) {
            return;
        }

        flashResolvedRow(row, decision, () -> {
            applyDecision(h, decision);
            mcTable.refresh();
            updateProgress();
            persistDecisionsAsync(List.of(h), decision, user, svc);
        });
    }

    private void updateProgress() {
        int total = mcTable.getItems().size();
        if (total == 0) {
            mcProgressBar.setProgress(0);
            mcProgText.setText("0 of 0 resolved");
            checkIfComplete();
            return;
        }
        double prog = (double) resolved / total;
        mcProgressBar.setProgress(prog);
        mcProgText.setText(resolved + " of " + total + " resolved");
        checkIfComplete();
    }

    private void checkIfComplete() {
        int total = mcTable.getItems().size();
        boolean hypothesesDone = resolved >= total;
        boolean unmatchedDone  = unmatchedLedgerTable.getItems().isEmpty()
                              && unmatchedBankTable.getItems().isEmpty();
        if (hypothesesDone && anomaliesDismissed && unmatchedDone) {
            mcCompleteBar.setVisible(true);
            mcCompleteBar.setManaged(true);
        } else {
            mcCompleteBar.setVisible(false);
            mcCompleteBar.setManaged(false);
        }
    }

    @FXML
    void handleProceedToReport() {
        if (workspaceController != null) {
            workspaceController.unlockReport();
            return;
        }
        Object ctrl = MainUIContext.getInstance().getWorkspaceController();
        if (ctrl instanceof WorkspaceController) {
            ((WorkspaceController) ctrl).unlockReport();
        }
    }

    private Button createIconActionButton(
        String revealText,
        String iconPath,
        String borderColor,
        String iconColor,
        String textColor,
        String hoverBackground
    ) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.setStyle(
            "-fx-fill: transparent; " +
            "-fx-stroke: " + iconColor + ";" +
            "-fx-stroke-width: 2;"
        );

        Button button = new Button();
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(6);
        button.setText("");
        button.setMinWidth(34);
        button.setPrefWidth(34);
        button.setMaxWidth(34);
        button.setMinHeight(30);
        button.setPrefHeight(30);
        button.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-radius: 999;" +
            "-fx-background-radius: 999;" +
            "-fx-padding: 0 10 0 10;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + textColor + ";"
        );

        button.setOnMouseEntered(e -> {
            animateActionButtonWidth(button, 120);
            button.setText(revealText);
            button.setStyle(
                "-fx-background-color: " + hoverBackground + ";" +
                "-fx-border-color: " + borderColor + ";" +
                "-fx-border-radius: 999;" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 0 10 0 10;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + textColor + ";"
            );
        });
        button.setOnMouseExited(e -> {
            animateActionButtonWidth(button, 34);
            button.setText("");
            button.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-border-color: " + borderColor + ";" +
                "-fx-border-radius: 999;" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 0 10 0 10;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + textColor + ";"
            );
        });
        return button;
    }

    private void animateActionButtonWidth(Button button, double targetWidth) {
        Timeline widthAnim = new Timeline(
            new KeyFrame(
                Duration.millis(180),
                new KeyValue(
                    button.prefWidthProperty(),
                    targetWidth,
                    Interpolator.EASE_BOTH
                ),
                new KeyValue(
                    button.maxWidthProperty(),
                    targetWidth,
                    Interpolator.EASE_BOTH
                )
            )
        );
        widthAnim.play();
    }

    private void flashResolvedRow(
        TableRow<MatchHypothesis> row,
        String decision,
        Runnable onFinished
    ) {
        if (row == null) {
            onFinished.run();
            return;
        }
        String originalStyle = row.getStyle() == null ? "" : row.getStyle();
        String flashColor = DECISION_CONFIRMED.equals(decision)
            ? "rgba(26, 92, 42, 0.18)"
            : "rgba(153, 27, 27, 0.18)";
        row.setStyle("-fx-background-color: " + flashColor + ";");
        PauseTransition flash = new PauseTransition(Duration.millis(150));
        flash.setOnFinished(e -> {
            row.setStyle(originalStyle);
            onFinished.run();
        });
        flash.play();
    }

    private void applyDecision(MatchHypothesis hypothesis, String decision) {
        if (hypothesis.getStatus() != HypothesisStatus.PENDING_REVIEW) {
            return;
        }
        if (DECISION_CONFIRMED.equals(decision)) {
            hypothesis.setStatus(HypothesisStatus.APPROVED);
        } else {
            hypothesis.setStatus(HypothesisStatus.REJECTED);
        }
        resolved++;
    }

    private SystemUser getOrCreateCurrentUser() {
        MainUIContext ctx = MainUIContext.getInstance();
        SystemUser user = ctx.getCurrentUser();
        if (user != null) {
            return user;
        }
        user = new SystemUser(
            UUID.randomUUID(),
            "Local User",
            "00000-0000000-0",
            "local-user",
            UserRole.ACCOUNTANT,
            "Local"
        );
        ctx.setCurrentUser(user);
        return user;
    }

    private ReconciliationService getReconciliationServiceOrShowError() {
        ReconciliationService svc = MainUIContext.getInstance()
            .getReconciliationService();
        if (svc != null) {
            return svc;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Cannot Save Review");
        alert.setHeaderText("Reconciliation service is not available");
        alert.setContentText(
            "Your decision could not be saved because the reconciliation service " +
            "is not initialized. Please restart the application and try again."
        );
        alert.showAndWait();
        return null;
    }

    private void persistDecisionsAsync(
        List<MatchHypothesis> hypotheses,
        String decision,
        SystemUser user,
        ReconciliationService svc
    ) {
        Task<Void> persistTask = new Task<>() {
            @Override
            protected Void call() {
                for (MatchHypothesis h : hypotheses) {
                    if (DECISION_CONFIRMED.equals(decision)) {
                        ReconciliationRecord record = svc.confirmHypothesis(
                            h,
                            user
                        );
                        MainUIContext.getInstance().addReconciledRecord(record);
                    } else {
                        svc.rejectHypothesis(h, user);
                    }
                }
                return null;
            }
        };
        persistTask.setOnFailed(e ->
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Save Failed");
                alert.setHeaderText("Could not save review decision(s)");
                alert.setContentText(
                    "Error: " + persistTask.getException().getMessage() +
                    "\n\nSome decisions may not have been persisted. Please retry."
                );
                alert.showAndWait();
            })
        );
        Thread thread = new Thread(persistTask);
        thread.setDaemon(true);
        thread.start();
    }

    private String formatTransaction(StandardizedTransaction tx) {
        if (tx == null) {
            return "";
        }
        String ref = tx
            .getTransactionId()
            .toString()
            .substring(0, 8)
            .toUpperCase();
        return tx.getNarrative() + "\n" + ref + "\n" + tx.getAmount();
    }

    // ── UC8: Force Match Setup ─────────────────────────────────────────────

    private void setupForceMatchTables() {
        // Value factories
        fmLDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
        fmLAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
        fmLNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));
        fmBDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
        fmBAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
        fmBNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));

        // Proportional column widths: 20% date, 25% amount, 55% narrative
        unmatchedLedgerTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        fmLDateCol.prefWidthProperty().bind(unmatchedLedgerTable.widthProperty().subtract(5).multiply(0.20));
        fmLAmtCol .prefWidthProperty().bind(unmatchedLedgerTable.widthProperty().subtract(5).multiply(0.25));
        fmLNarrCol.prefWidthProperty().bind(unmatchedLedgerTable.widthProperty().subtract(5).multiply(0.55));

        unmatchedBankTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        fmBDateCol.prefWidthProperty().bind(unmatchedBankTable.widthProperty().subtract(5).multiply(0.20));
        fmBAmtCol .prefWidthProperty().bind(unmatchedBankTable.widthProperty().subtract(5).multiply(0.25));
        fmBNarrCol.prefWidthProperty().bind(unmatchedBankTable.widthProperty().subtract(5).multiply(0.55));

        // Enable Force Link only when both tables have a selection AND justification is non-blank
        javafx.beans.binding.BooleanBinding canForce = unmatchedLedgerTable.getSelectionModel()
            .selectedItemProperty().isNotNull()
            .and(unmatchedBankTable.getSelectionModel().selectedItemProperty().isNotNull())
            .and(forceJustField.textProperty().isNotEmpty());
        btnForceLink.disableProperty().bind(canForce.not());
    }

    public void loadUnmatchedTransactions() {
        MainUIContext ctx = MainUIContext.getInstance();
        List<StandardizedTransaction> ledger = ctx.getUnmatchedLedger();
        List<StandardizedTransaction> bank   = ctx.getUnmatchedBank();

        ObservableList<StandardizedTransaction> ledgerItems =
            FXCollections.observableArrayList(ledger != null ? ledger : List.of());
        ObservableList<StandardizedTransaction> bankItems =
            FXCollections.observableArrayList(bank   != null ? bank   : List.of());

        // UC8 tables
        unmatchedLedgerTable.setItems(ledgerItems);
        unmatchedBankTable  .setItems(bankItems);

        // UC9 tables (same source data, separate table references)
        consolidationLedgerTable.setItems(FXCollections.observableArrayList(ledgerItems));
        consolidationBankTable  .setItems(FXCollections.observableArrayList(bankItems));

        boolean anyUnmatched = !ledgerItems.isEmpty() || !bankItems.isEmpty();
        forceMatchPane    .setVisible(anyUnmatched);
        forceMatchPane    .setManaged(anyUnmatched);
        consolidationPane .setVisible(anyUnmatched);
        consolidationPane .setManaged(anyUnmatched);
        checkIfComplete();
    }

    @FXML
    void handleForceLink() {
        StandardizedTransaction selectedLedger = unmatchedLedgerTable.getSelectionModel().getSelectedItem();
        StandardizedTransaction selectedBank   = unmatchedBankTable.getSelectionModel().getSelectedItem();
        String justification = forceJustField.getText().trim();

        if (selectedLedger == null || selectedBank == null || justification.isBlank()) return;

        SystemUser user = getOrCreateCurrentUser();
        ReconciliationService svc = getReconciliationServiceOrShowError();
        if (svc == null) return;

        showForceFeedback("Saving forced reconciliation...");

        Task<ReconciliationRecord> task = new Task<>() {
            @Override
            protected ReconciliationRecord call() {
                return svc.forceReconcile(selectedLedger, selectedBank, user, justification);
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            ReconciliationRecord record = task.getValue();
            MainUIContext ctx = MainUIContext.getInstance();
            ctx.addReconciledRecord(record);

            // Remove from UI tables
            unmatchedLedgerTable.getItems().remove(selectedLedger);
            unmatchedBankTable  .getItems().remove(selectedBank);

            // Push updated lists back to context
            ctx.setUnmatchedLedger(new ArrayList<>(unmatchedLedgerTable.getItems()));
            ctx.setUnmatchedBank(new ArrayList<>(unmatchedBankTable.getItems()));

            forceJustField.clear();
            showForceFeedback("Force-linked: " + selectedLedger.getNarrative()
                + "  ↔  " + selectedBank.getNarrative());

            // Hide panel if no more unmatched
            if (unmatchedLedgerTable.getItems().isEmpty() && unmatchedBankTable.getItems().isEmpty()) {
                forceMatchPane.setVisible(false);
                forceMatchPane.setManaged(false);
            }
            checkIfComplete();
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            showForceFeedback("Error: " + task.getException().getMessage());
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void showForceFeedback(String msg) {
        forceFeedback.setText(msg);
        forceFeedback.setVisible(true);
        forceFeedback.setManaged(true);
    }

    // ── UC9: Multi-Source Consolidation Setup ───────────────────────────────

    private void setupConsolidationTables() {
        csBDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
        csBAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
        csBNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));

        csLDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
        csLAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
        csLNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));

        // Multi-select on ledger table
        consolidationLedgerTable.getSelectionModel()
            .setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        // Proportional column widths
        consolidationBankTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        csBDateCol.prefWidthProperty().bind(consolidationBankTable.widthProperty().subtract(5).multiply(0.20));
        csBAmtCol .prefWidthProperty().bind(consolidationBankTable.widthProperty().subtract(5).multiply(0.25));
        csBNarrCol.prefWidthProperty().bind(consolidationBankTable.widthProperty().subtract(5).multiply(0.55));

        consolidationLedgerTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        csLDateCol.prefWidthProperty().bind(consolidationLedgerTable.widthProperty().subtract(5).multiply(0.20));
        csLAmtCol .prefWidthProperty().bind(consolidationLedgerTable.widthProperty().subtract(5).multiply(0.25));
        csLNarrCol.prefWidthProperty().bind(consolidationLedgerTable.widthProperty().subtract(5).multiply(0.55));

        // Live sum label as ledger rows are selected
        consolidationLedgerTable.getSelectionModel().getSelectedItems()
            .addListener((javafx.collections.ListChangeListener<StandardizedTransaction>) change -> {
                java.math.BigDecimal sum = consolidationLedgerTable.getSelectionModel()
                    .getSelectedItems().stream()
                    .map(tx -> tx.getAmount().abs())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                consolidationSumLabel.setText("Selected sum: " + sum.toPlainString());
                updateConsolidateButton();
            });

        consolidationBankTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, o, n) -> updateConsolidateButton());
    }

    private void updateConsolidateButton() {
        boolean bankSelected   = consolidationBankTable.getSelectionModel().getSelectedItem() != null;
        boolean ledgerSelected = !consolidationLedgerTable.getSelectionModel().getSelectedItems().isEmpty();
        btnConsolidate.setDisable(!(bankSelected && ledgerSelected));
    }

    @FXML
    void handleConsolidate() {
        StandardizedTransaction bankTx = consolidationBankTable.getSelectionModel().getSelectedItem();
        List<StandardizedTransaction> selectedLedger = new ArrayList<>(
            consolidationLedgerTable.getSelectionModel().getSelectedItems());

        if (bankTx == null || selectedLedger.isEmpty()) return;

        double tolerancePct;
        try {
            tolerancePct = Double.parseDouble(toleranceField.getText().trim()) / 100.0;
        } catch (NumberFormatException ex) {
            consolidationFeedback.setText("Invalid tolerance value — enter a number like 2.0");
            consolidationFeedback.setVisible(true);
            consolidationFeedback.setManaged(true);
            return;
        }

        SystemUser user = getOrCreateCurrentUser();
        ReconciliationService svc = getReconciliationServiceOrShowError();
        if (svc == null) return;

        btnConsolidate.setDisable(true);

        Task<List<aval.domain.ai.ReconciliationRecord>> task = new Task<>() {
            @Override
            protected List<aval.domain.ai.ReconciliationRecord> call() {
                List<aval.domain.ai.Anomaly> newAnomalies = new ArrayList<>();
                List<aval.domain.ai.ReconciliationRecord> records = svc.consolidateMultiSource(
                    MainUIContext.getInstance().getActiveWorkspace(),
                    selectedLedger, bankTx, tolerancePct, newAnomalies, user);

                // Merge any consolidation-variance anomalies back into context
                if (!newAnomalies.isEmpty()) {
                    List<aval.domain.ai.Anomaly> existing =
                        MainUIContext.getInstance().getAnomalies();
                    if (existing == null) existing = new ArrayList<>();
                    else existing = new ArrayList<>(existing);
                    existing.addAll(newAnomalies);
                    MainUIContext.getInstance().setAnomalies(existing);
                }
                return records;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<aval.domain.ai.ReconciliationRecord> records = task.getValue();

            if (records.isEmpty()) {
                // Variance anomaly was added — reload anomaly panel
                loadAnomalies();
                consolidationFeedback.setText("Variance detected — anomaly logged. Adjust selection or tolerance.");
            } else {
                records.forEach(r -> MainUIContext.getInstance().addReconciledRecord(r));
                
                // Remove consolidated entries from unmatched lists
                selectedLedger.forEach(l -> {
                    unmatchedLedgerTable.getItems().remove(l);
                    consolidationLedgerTable.getItems().remove(l);
                });
                unmatchedBankTable.getItems().remove(bankTx);
                consolidationBankTable.getItems().remove(bankTx);

                // Sync the main context unmatched lists back
                MainUIContext.getInstance().setUnmatchedLedger(new ArrayList<>(unmatchedLedgerTable.getItems()));
                MainUIContext.getInstance().setUnmatchedBank(new ArrayList<>(unmatchedBankTable.getItems()));

                consolidationFeedback.setText(
                    records.size() + " ledger entries consolidated against bank transaction "
                    + bankTx.getAmount().toPlainString());
                checkIfComplete();

                // If no more unmatched, hide both panes
                if (unmatchedLedgerTable.getItems().isEmpty() && unmatchedBankTable.getItems().isEmpty()) {
                    forceMatchPane.setVisible(false);
                    forceMatchPane.setManaged(false);
                    consolidationPane.setVisible(false);
                    consolidationPane.setManaged(false);
                }
            }

            consolidationFeedback.setVisible(true);
            consolidationFeedback.setManaged(true);
            btnConsolidate.setDisable(false);
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            consolidationFeedback.setText("Error: " + task.getException().getMessage());
            consolidationFeedback.setVisible(true);
            consolidationFeedback.setManaged(true);
            btnConsolidate.setDisable(false);
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}
