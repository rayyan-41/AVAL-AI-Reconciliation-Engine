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
        if (resolved >= total && anomaliesDismissed) {
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
}
