package aval.ui.controller;

import aval.common.enums.TransactionSide;
import aval.common.enums.UnresolvableReason;
import aval.common.enums.HypothesisStatus;
import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import aval.domain.ai.Anomaly;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.ai.UnresolvableRecord;
import aval.domain.core.MatchingConfig;
import aval.service.ReconciliationService;
import aval.ui.MainUIContext;
import aval.ui.util.UIAnimationUtil;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    // Unified Manual Disposition Panel
    @FXML private VBox manualActionPane;
    @FXML private TableView<StandardizedTransaction> unmatchedLedgerTable;
    @FXML private TableColumn<StandardizedTransaction, String> ulDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> ulAmtCol;
    @FXML private TableColumn<StandardizedTransaction, String> ulNarrCol;
    
    @FXML private TableView<StandardizedTransaction> unmatchedBankTable;
    @FXML private TableColumn<StandardizedTransaction, String> ubDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> ubAmtCol;
    @FXML private TableColumn<StandardizedTransaction, String> ubNarrCol;
    
    @FXML private javafx.scene.control.ComboBox<aval.common.enums.UnresolvableReason> unresolvableReasonCombo;
    @FXML private TextField actionNoteField;
    @FXML private Label toleranceLabel;
    @FXML private TextField toleranceField;
    @FXML private Label selectionSumLabel;
    
    @FXML private Button btnForceLink;
    @FXML private Button btnConsolidate;
    @FXML private Button btnUnresolvable;
    @FXML private Label actionFeedback;

    //-------------- Attributes ----------------------//
    private static final String DECISION_CONFIRMED = "CONFIRMED";
    private static final String DECISION_REJECTED = "REJECTED";
    private IWorkspaceController workspaceController;
    private ObservableList<MatchHypothesis> hypothesesList;
    private int resolved = 0;
    private boolean anomaliesDismissed = true;
    private boolean pendedForLater = false;

    //-------------- Methods ----------------------//
    public void setWorkspaceController(IWorkspaceController wc) { this.workspaceController = wc; }

    private static final DateTimeFormatter PAIR_DATE_FMT =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    @FXML
    public void initialize() {
        MatchingConfig config = MainUIContext.getInstance().getActiveMatchingConfig();
        mcSub.setText(
            String.format(
                Locale.ENGLISH,
                "Approve or reject AI-suggested matches below the %.0f%% auto-confirm threshold.",
                config.getAutoConfirmThreshold() * 100
            )
        );
        UIAnimationUtil.applyButtonPressFeedback(btnApproveAllPending);
        UIAnimationUtil.applyButtonPressFeedback(btnRejectAllPending);
        setupUnifiedTables();
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
        mcLedgerCol.setCellValueFactory(data ->
            new SimpleStringProperty(
                data.getValue().getLedgerTransaction() != null
                    ? data.getValue().getLedgerTransaction().getNarrative()
                    : ""
            )
        );
        mcLedgerCol.setCellFactory(col -> matchSideCell(true));
        mcBankCol.setCellValueFactory(data ->
            new SimpleStringProperty(
                data.getValue().getBankTransaction() != null
                    ? data.getValue().getBankTransaction().getNarrative()
                    : ""
            )
        );
        mcBankCol.setCellFactory(col -> matchSideCell(false));
        mcJustCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getJustification())
        );
        mcJustCol.setCellFactory(col ->
            new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label text = new Label(item);
                    text.setWrapText(true);
                    text.getStyleClass().add("pair-meta");
                    text.maxWidthProperty().bind(widthProperty().subtract(24));
                    setGraphic(text);
                    setText(null);
                }
            }
        );

        // Confidence pill cell — band cutoff derived from the workspace policy
        // (midpoint of the manual-review range) instead of a hardcoded value.
        mcConfCol.setCellFactory(col ->
            new TableCell<MatchHypothesis, Double>() {
                @Override
                protected void updateItem(Double conf, boolean empty) {
                    super.updateItem(conf, empty);
                    if (empty || conf == null) {
                        setGraphic(null);
                        return;
                    }
                    MatchingConfig cfg =
                        MainUIContext.getInstance().getActiveMatchingConfig();
                    double bandMid =
                        (cfg.getReviewFloor() + cfg.getAutoConfirmThreshold()) / 2.0;
                    int pct = (int) Math.round(conf * 100);
                    Label pill = new Label(pct + "%");
                    pill
                        .getStyleClass()
                        .addAll("conf-pill", conf >= bandMid ? "conf-hi" : "conf-lo");
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
        double autoThreshold = MainUIContext.getInstance()
            .getActiveMatchingConfig()
            .getAutoConfirmThreshold();
        hypothesesList.setAll(
            source
                .stream()
                .filter(h -> h.getConfidenceScore() < autoThreshold)
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
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label chip = new Label(item.getCategory().name().replace('_', ' '));
                    chip.getStyleClass().add("anomaly-chip");
                    Label desc = new Label(item.getDescription());
                    desc.getStyleClass().add("anomaly-desc");
                    desc.setWrapText(true);
                    HBox row = new HBox(10, chip, desc);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(row);
                    setText(null);
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
        boolean unmatchedDone  = pendedForLater || (unmatchedLedgerTable.getItems().isEmpty()
                              && unmatchedBankTable.getItems().isEmpty());
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

    /**
     * Structured cell for one side of a match pair: narrative on top,
     * reference + value date underneath, amount emphasised at the bottom.
     */
    private TableCell<MatchHypothesis, String> matchSideCell(boolean ledgerSide) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                MatchHypothesis h = null;
                if (!empty && getTableRow() != null) {
                    h = getTableRow().getItem();
                }
                StandardizedTransaction tx = h == null
                    ? null
                    : (ledgerSide ? h.getLedgerTransaction() : h.getBankTransaction());
                if (tx == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label narr = new Label(tx.getNarrative());
                narr.getStyleClass().add("pair-narr");
                Label meta = new Label(
                    tx.getTransactionId().toString().substring(0, 8).toUpperCase() +
                        " · " +
                        tx.getValueDate().format(PAIR_DATE_FMT)
                );
                meta.getStyleClass().add("pair-meta");
                Label amt = new Label(
                    String.format(Locale.ENGLISH, "$%,.2f", tx.getAmount())
                );
                amt.getStyleClass().add("pair-amt");
                VBox box = new VBox(2, narr, meta, amt);
                setGraphic(box);
                setText(null);
            }
        };
    }

    // ── Unified Manual Disposition Setup ─────────────────────────────────────────────

    private void setupUnifiedTables() {
        // Value factories
        ulDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
        ulAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
        ulNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));
        
        ubDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
        ubAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
        ubNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));

        // Proportional column widths
        unmatchedLedgerTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        ulDateCol.prefWidthProperty().bind(unmatchedLedgerTable.widthProperty().subtract(5).multiply(0.20));
        ulAmtCol .prefWidthProperty().bind(unmatchedLedgerTable.widthProperty().subtract(5).multiply(0.25));
        ulNarrCol.prefWidthProperty().bind(unmatchedLedgerTable.widthProperty().subtract(5).multiply(0.55));

        unmatchedBankTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        ubDateCol.prefWidthProperty().bind(unmatchedBankTable.widthProperty().subtract(5).multiply(0.20));
        ubAmtCol .prefWidthProperty().bind(unmatchedBankTable.widthProperty().subtract(5).multiply(0.25));
        ubNarrCol.prefWidthProperty().bind(unmatchedBankTable.widthProperty().subtract(5).multiply(0.55));

        // Ledger and Bank are multi-select
        unmatchedLedgerTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        unmatchedBankTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Populate Unresolvable reasons
        unresolvableReasonCombo.setItems(FXCollections.observableArrayList(UnresolvableReason.values()));

        // Add selection listeners
        unmatchedLedgerTable.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<StandardizedTransaction>) c -> updateActionState());
        unmatchedBankTable.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<StandardizedTransaction>) c -> updateActionState());
        actionNoteField.textProperty().addListener((obs, oldV, newV) -> updateActionState());
        unresolvableReasonCombo.valueProperty().addListener((obs, oldV, newV) -> updateActionState());
        
        updateActionState();
    }

    private void updateActionState() {
        List<StandardizedTransaction> ledgers = unmatchedLedgerTable.getSelectionModel().getSelectedItems();
        List<StandardizedTransaction> bank = unmatchedBankTable.getSelectionModel().getSelectedItems();
        boolean hasNote = !actionNoteField.getText().trim().isEmpty();
        boolean hasReason = unresolvableReasonCombo.getValue() != null;
        
        int lCount = ledgers.size();
        int bCount = bank.size();
        
        // Hide all buttons and tolerance by default
        btnForceLink.setVisible(false); btnForceLink.setManaged(false); btnForceLink.setDisable(true);
        btnConsolidate.setVisible(false); btnConsolidate.setManaged(false); btnConsolidate.setDisable(true);
        btnUnresolvable.setVisible(false); btnUnresolvable.setManaged(false); btnUnresolvable.setDisable(true);
        toleranceLabel.setVisible(false); toleranceLabel.setManaged(false);
        toleranceField.setVisible(false); toleranceField.setManaged(false);
        selectionSumLabel.setVisible(false); selectionSumLabel.setManaged(false);
        
        if (lCount > 0 || bCount > 0) {
            selectionSumLabel.setVisible(true); selectionSumLabel.setManaged(true);
            
            if ((lCount >= 2 && bCount >= 1) || (lCount >= 1 && bCount >= 2)) {
                java.math.BigDecimal sum = ledgers.stream()
                    .map(tx -> tx.getAmount().abs())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal bSum = bank.stream()
                    .map(tx -> tx.getAmount().abs())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                selectionSumLabel.setText(String.format("Ledger sum: %s | Bank sum: %s", 
                    sum.toPlainString(), bSum.toPlainString()));
            } else {
                selectionSumLabel.setText(String.format("Selected: %d Ledger | %d Bank", lCount, bCount));
            }
        }
        
        if (lCount == 1 && bCount == 1) {
            btnForceLink.setVisible(true); btnForceLink.setManaged(true);
            btnForceLink.setDisable(!hasNote);
        } else if ((lCount >= 2 && bCount >= 1) || (lCount >= 1 && bCount >= 2)) {
            btnConsolidate.setVisible(true); btnConsolidate.setManaged(true);
            toleranceLabel.setVisible(true); toleranceLabel.setManaged(true);
            toleranceField.setVisible(true); toleranceField.setManaged(true);
            
            btnConsolidate.setDisable(false); 
        } else if ((lCount >= 1 && bCount == 0) || (lCount == 0 && bCount == 1)) {
            btnUnresolvable.setVisible(true); btnUnresolvable.setManaged(true);
            btnUnresolvable.setDisable(!(hasNote && hasReason));
        }
    }

    public void loadUnmatchedTransactions() {
        MainUIContext ctx = MainUIContext.getInstance();
        List<StandardizedTransaction> ledger = ctx.getUnmatchedLedger();
        List<StandardizedTransaction> bank   = ctx.getUnmatchedBank();

        ObservableList<StandardizedTransaction> ledgerItems =
            FXCollections.observableArrayList(ledger != null ? ledger : List.of());
        ObservableList<StandardizedTransaction> bankItems =
            FXCollections.observableArrayList(bank   != null ? bank   : List.of());

        // We must call setupUnifiedTables once if it hasn't been called. We can do it safely here or in initialize.
        // Actually, we'll do it in initialize. Let's just set items here.
        unmatchedLedgerTable.setItems(ledgerItems);
        unmatchedBankTable  .setItems(bankItems);

        boolean anyUnmatched = !ledgerItems.isEmpty() || !bankItems.isEmpty();
        manualActionPane.setVisible(anyUnmatched);
        manualActionPane.setManaged(anyUnmatched);
        checkIfComplete();
    }

    @FXML
    void handleForceLink() {
        StandardizedTransaction selectedLedger = unmatchedLedgerTable.getSelectionModel().getSelectedItem();
        StandardizedTransaction selectedBank   = unmatchedBankTable.getSelectionModel().getSelectedItem();
        String justification = actionNoteField.getText().trim();

        if (selectedLedger == null || selectedBank == null || justification.isBlank()) return;

        SystemUser user = getOrCreateCurrentUser();
        ReconciliationService svc = getReconciliationServiceOrShowError();
        if (svc == null) return;

        showActionFeedback("Saving forced reconciliation...");

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

            unmatchedLedgerTable.getItems().remove(selectedLedger);
            unmatchedBankTable  .getItems().remove(selectedBank);
            syncUnmatchedToContext();

            actionNoteField.clear();
            showActionFeedback("Force-linked: " + selectedLedger.getNarrative()
                + "  ↔  " + selectedBank.getNarrative());
            checkCompletionAndVisibility();
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            showActionFeedback("Error: " + task.getException().getMessage());
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    void handleConsolidate() {
        List<StandardizedTransaction> selectedBank = new ArrayList<>(
            unmatchedBankTable.getSelectionModel().getSelectedItems());
        List<StandardizedTransaction> selectedLedger = new ArrayList<>(
            unmatchedLedgerTable.getSelectionModel().getSelectedItems());

        if (selectedBank.isEmpty() || selectedLedger.isEmpty()) return;

        double tolerancePct;
        try {
            tolerancePct = Double.parseDouble(toleranceField.getText().trim()) / 100.0;
        } catch (NumberFormatException ex) {
            showActionFeedback("Invalid tolerance value — enter a number like 2.0");
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
                    selectedLedger, selectedBank, tolerancePct, newAnomalies, user);

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
                loadAnomalies();
                showActionFeedback("Variance detected — anomaly logged. Adjust selection or tolerance.");
            } else {
                records.forEach(r -> MainUIContext.getInstance().addReconciledRecord(r));
                
                selectedLedger.forEach(l -> unmatchedLedgerTable.getItems().remove(l));
                selectedBank.forEach(b -> unmatchedBankTable.getItems().remove(b));
                syncUnmatchedToContext();

                showActionFeedback(records.size() + " ledger/bank entries consolidated successfully");
                checkCompletionAndVisibility();
            }
            btnConsolidate.setDisable(false);
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            showActionFeedback("Error: " + task.getException().getMessage());
            btnConsolidate.setDisable(false);
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    void handleUnresolvable() {
        List<StandardizedTransaction> ledgers = new ArrayList<>(unmatchedLedgerTable.getSelectionModel().getSelectedItems());
        List<StandardizedTransaction> banks = new ArrayList<>(unmatchedBankTable.getSelectionModel().getSelectedItems());
        UnresolvableReason reason = unresolvableReasonCombo.getValue();
        String note = actionNoteField.getText().trim();
        
        if (reason == null || note.isEmpty()) return;
        
        SystemUser user = getOrCreateCurrentUser();
        ReconciliationService svc = getReconciliationServiceOrShowError();
        if (svc == null) return;
        
        btnUnresolvable.setDisable(true);
        
        Task<List<UnresolvableRecord>> task = new Task<>() {
            @Override
            protected List<UnresolvableRecord> call() {
                List<UnresolvableRecord> records = new ArrayList<>();
                for (StandardizedTransaction bank : banks) {
                    records.add(svc.markAsUnresolvable(bank, TransactionSide.BANK, reason, note, user));
                }
                for (StandardizedTransaction ledger : ledgers) {
                    records.add(svc.markAsUnresolvable(ledger, TransactionSide.LEDGER, reason, note, user));
                }
                return records;
            }
        };
        
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<UnresolvableRecord> records = task.getValue();
            records.forEach(r -> MainUIContext.getInstance().addUnresolvableRecord(r));
            
            banks.forEach(b -> unmatchedBankTable.getItems().remove(b));
            ledgers.forEach(l -> unmatchedLedgerTable.getItems().remove(l));
            syncUnmatchedToContext();
            
            unresolvableReasonCombo.getSelectionModel().clearSelection();
            actionNoteField.clear();
            showActionFeedback("Marked " + records.size() + " item(s) as unresolvable: " + reason.name());
            
            checkCompletionAndVisibility();
            btnUnresolvable.setDisable(false);
        }));
        
        task.setOnFailed(e -> Platform.runLater(() -> {
            showActionFeedback("Error: " + task.getException().getMessage());
            btnUnresolvable.setDisable(false);
        }));
        
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    void handlePendLater() {
        pendedForLater = true;
        manualActionPane.setVisible(false);
        manualActionPane.setManaged(false);
        checkIfComplete();
        showActionFeedback("Remaining items pended. You can now proceed to the report.");
    }

    private void syncUnmatchedToContext() {
        MainUIContext.getInstance().setUnmatchedLedger(new ArrayList<>(unmatchedLedgerTable.getItems()));
        MainUIContext.getInstance().setUnmatchedBank(new ArrayList<>(unmatchedBankTable.getItems()));
    }

    private void checkCompletionAndVisibility() {
        if (unmatchedLedgerTable.getItems().isEmpty() && unmatchedBankTable.getItems().isEmpty()) {
            manualActionPane.setVisible(false);
            manualActionPane.setManaged(false);
        }
        checkIfComplete();
    }

    private void showActionFeedback(String msg) {
        actionFeedback.setText(msg);
        actionFeedback.setVisible(true);
        actionFeedback.setManaged(true);
    }
}
