package aval.ui.controller;

import aval.common.enums.HypothesisStatus;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.service.ReportService;
import aval.ui.MainUIContext;
import aval.ui.util.UIAnimationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

public class ReportController {

    //-------------- Attributes ----------------------//
    @FXML
    private Label rptTitle;

    @FXML
    private Label rptSub;

    @FXML
    private HBox rptStatRow;

    @FXML
    private Button btnGenerate;

    @FXML
    private Button btnEmail;

    @FXML
    private Label feedbackLabel;

    @FXML
    private TableView<MatchHypothesis> lineageTable;

    @FXML
    private TableColumn<MatchHypothesis, String> rlRefCol;

    @FXML
    private TableColumn<MatchHypothesis, String> rlNarrCol;

    @FXML
    private TableColumn<MatchHypothesis, String> rbRefCol;

    @FXML
    private TableColumn<MatchHypothesis, String> rbNarrCol;

    @FXML
    private TableColumn<MatchHypothesis, String> rTypeCol;

    @FXML
    private TableColumn<MatchHypothesis, String> rConfCol;

    @FXML
    private TableColumn<MatchHypothesis, String> rResCol;

    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterAuto;
    @FXML private ToggleButton filterApproved;
    @FXML private ToggleButton filterRejected;

    private ToggleGroup filterGroup;
    private final List<MatchHypothesis> currentHypotheses = new ArrayList<>();
    private String lastGeneratedReportPath = null;

    @FXML
    public void initialize() {
        updateHeader();
        setupLineageTable();
        setupFilterGroup();
        UIAnimationUtil.applyButtonPressFeedback(btnGenerate);
        UIAnimationUtil.applyButtonPressFeedback(btnEmail);

        List<MatchHypothesis> existing =
            MainUIContext.getInstance().getAllHypotheses();
        if (existing == null) {
            existing = MainUIContext.getInstance().getPendingHypotheses();
        }
        if (existing != null) {
            populateReport(existing);
        }
    }

    private void setupFilterGroup() {
        filterGroup = new ToggleGroup();
        filterAll.setToggleGroup(filterGroup);
        filterAuto.setToggleGroup(filterGroup);
        filterApproved.setToggleGroup(filterGroup);
        filterRejected.setToggleGroup(filterGroup);
        filterAll.setSelected(true);

        // Prevent deselecting all — at least one must stay selected
        filterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    public void populateReport(List<MatchHypothesis> all) {
        currentHypotheses.clear();
        currentHypotheses.addAll(all);

        // Count by resolution status so the cards, the filters, and the lineage
        // table all agree on the same numbers.
        long autoMatched = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.AUTO_RECONCILED)
            .count();
        long approved = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.APPROVED)
            .count();
        long rejected = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.REJECTED)
            .count();
        long total = all.size();

        // Final match rate: reconciled ledger transactions over all ledger
        // transactions — the number a client actually cares about.
        MainUIContext ctx = MainUIContext.getInstance();
        java.util.Set<java.util.UUID> matchedLedgerIds = all
            .stream()
            .filter(
                h ->
                    h.getStatus() == HypothesisStatus.AUTO_RECONCILED ||
                    h.getStatus() == HypothesisStatus.APPROVED
            )
            .map(MatchHypothesis::getLedgerTransaction)
            .filter(java.util.Objects::nonNull)
            .map(StandardizedTransaction::getTransactionId)
            .collect(Collectors.toSet());
        int ledgerTotal = ctx.getStandardizedLedgerTransactions().size();
        double rate = ledgerTotal > 0
            ? ((double) matchedLedgerIds.size() / ledgerTotal) * 100
            : (total == 0 ? 0 : ((double) (autoMatched + approved) / total) * 100);

        String autoSub = String.format(
            java.util.Locale.ENGLISH,
            "Confidence ≥ %.0f%%",
            ctx.getActiveMatchingConfig().getAutoConfirmThreshold() * 100
        );

        rptStatRow
            .getChildren()
            .setAll(
                makeStatCard(
                    "Total Candidates",
                    String.valueOf(total),
                    "All match hypotheses",
                    "0d0d0d"
                ),
                makeStatCard(
                    "Auto-Reconciled",
                    String.valueOf(autoMatched),
                    autoSub,
                    "1a5c2a"
                ),
                makeStatCard(
                    "Approved in Review",
                    String.valueOf(approved),
                    "Confirmed by reviewer",
                    "0d0d0d"
                ),
                makeStatCard(
                    "Rejected",
                    String.valueOf(rejected),
                    "Dismissed candidates",
                    "800020"
                ),
                makeStatCard(
                    "Ledger Match Rate",
                    String.format(java.util.Locale.ENGLISH, "%.1f%%", rate),
                    "Reconciled ledger records",
                    "1a5c2a"
                )
            );

        lineageTable.setItems(FXCollections.observableArrayList(all));
        filterAll.setSelected(true);
    }

    // ── Filter handlers ────────────────────────────────────────────────────

    @FXML
    void handleFilterAll() {
        applyFilter(null);
    }

    @FXML
    void handleFilterAuto() {
        applyFilter("AUTO");
    }

    @FXML
    void handleFilterApproved() {
        applyFilter("APPROVED");
    }

    @FXML
    void handleFilterRejected() {
        applyFilter("REJECTED");
    }

    private void applyFilter(String filterType) {
        List<MatchHypothesis> filtered;
        if (filterType == null) {
            // Show all
            filtered = currentHypotheses;
        } else {
            filtered = currentHypotheses.stream().filter(h -> {
                return switch (filterType) {
                    case "AUTO" -> h.getStatus() == HypothesisStatus.AUTO_RECONCILED;
                    case "APPROVED" -> h.getStatus() == HypothesisStatus.APPROVED;
                    case "REJECTED" -> h.getStatus() == HypothesisStatus.REJECTED;
                    default -> true;
                };
            }).collect(java.util.stream.Collectors.toList());
        }
        lineageTable.setItems(FXCollections.observableArrayList(filtered));
    }

    @FXML
    void handleGenerate() {
        MainUIContext ctx = MainUIContext.getInstance();
        List<MatchHypothesis> allHypotheses = ctx.getAllHypotheses();
        List<MatchHypothesis> stillPending =
            allHypotheses == null
                ? new ArrayList<>()
                : allHypotheses
                      .stream()
                      .filter(
                          h -> h.getStatus() == HypothesisStatus.PENDING_REVIEW
                      )
                      .collect(Collectors.toList());
        if (!stillPending.isEmpty()) {
            showFeedback(
                "Cannot generate report: " +
                    stillPending.size() +
                    " hypotheses are still pending review. Return to Manual Check to resolve them."
            );
            return;
        }

        ClientOrganization client = ctx.getActiveClient();
        String clientName =
            client != null
                ? client.getName().replace(" ", "_")
                : "Unknown_Client";
        String period = java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern(
                "MMMyyyy",
                java.util.Locale.ENGLISH
            )
        );
        String outputPath =
            System.getProperty("user.home") +
            java.io.File.separator +
            "reconciliation_report_" +
            clientName +
            "_" +
            period +
            ".csv";
        this.lastGeneratedReportPath = outputPath;
        showFeedback("Generating report...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ReportService rptSvc = ctx.getReportService();
                if (rptSvc == null) {
                    throw new IllegalStateException(
                        "Report service is not initialized."
                    );
                }

                List<ReconciliationRecord> records = ctx.getReconciledRecords();
                List<StandardizedTransaction> unmatchedL =
                    ctx.getUnmatchedLedger();
                List<StandardizedTransaction> unmatchedB =
                    ctx.getUnmatchedBank();
                List<aval.domain.ai.Anomaly> anomalies = ctx.getAnomalies();

                if (records == null) records = new ArrayList<>();
                if (unmatchedL == null) unmatchedL = new ArrayList<>();
                if (unmatchedB == null) unmatchedB = new ArrayList<>();
                if (anomalies == null) anomalies = new ArrayList<>();

                rptSvc.generateReconciliationReport(
                    records,
                    unmatchedL,
                    unmatchedB,
                    stillPending,
                    anomalies,
                    outputPath
                );
                return null;
            }
        };
        task.setOnSucceeded(e ->
            Platform.runLater(() ->
                showFeedback("Report saved to: " + outputPath)
            )
        );
        task.setOnFailed(e ->
            Platform.runLater(() ->
                showFeedback("Error: " + task.getException().getMessage())
            )
        );

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    void handleEmail() {
        if (lastGeneratedReportPath == null) {
            showFeedback("Generate the report first before emailing.");
            return;
        }

        ClientOrganization client =
            MainUIContext.getInstance().getActiveClient();
        if (client == null) {
            showFeedback("No active client — cannot determine the recipient.");
            return;
        }
        String toEmail = client.getContactMetadata();

        if (toEmail == null || toEmail.isBlank()) {
            showFeedback("No email address on file for " + client.getName());
            return;
        }

        showFeedback("Sending...");
        java.io.File reportFile = new java.io.File(lastGeneratedReportPath);
        aval.service.EmailService emailSvc =
            MainUIContext.getInstance().getEmailService();

        if (emailSvc == null) {
            showFeedback("Email service is not configured.");
            return;
        }

        Task<Void> emailTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                emailSvc.sendReport(toEmail, client.getName(), reportFile);
                return null;
            }
        };

        emailTask.setOnSucceeded(e ->
            Platform.runLater(() ->
                showFeedback("Report emailed to " + toEmail + " - Delivered.")
            )
        );
        emailTask.setOnFailed(e ->
            Platform.runLater(() ->
                showFeedback(
                    "Email failed: " + emailTask.getException().getMessage()
                )
            )
        );

        Thread emailThread = new Thread(emailTask);
        emailThread.setDaemon(true);
        emailThread.start();
    }

    private void updateHeader() {
        ClientOrganization client =
            MainUIContext.getInstance().getActiveClient();
        if (client != null) {
            rptTitle.setText(client.getName() + " - Reconciliation Report");
            rptSub.setText(
                "Summary of reconciliation outcomes, approvals, and final lineage."
            );
        } else {
            rptTitle.setText("Reconciliation Report");
            rptSub.setText("Summary of reconciliation outcomes and lineage.");
        }
    }

    private void setupLineageTable() {
        lineageTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        rlRefCol
            .prefWidthProperty()
            .bind(lineageTable.widthProperty().subtract(5).multiply(0.12));
        rlNarrCol
            .prefWidthProperty()
            .bind(lineageTable.widthProperty().subtract(5).multiply(0.22));
        rbRefCol
            .prefWidthProperty()
            .bind(lineageTable.widthProperty().subtract(5).multiply(0.12));
        rbNarrCol
            .prefWidthProperty()
            .bind(lineageTable.widthProperty().subtract(5).multiply(0.22));
        rTypeCol
            .prefWidthProperty()
            .bind(lineageTable.widthProperty().subtract(5).multiply(0.10));
        rConfCol
            .prefWidthProperty()
            .bind(lineageTable.widthProperty().subtract(5).multiply(0.10));
        rResCol
            .prefWidthProperty()
            .bind(lineageTable.widthProperty().subtract(5).multiply(0.12));

        rlRefCol.setCellValueFactory(data ->
            new SimpleStringProperty(
                getRef(data.getValue().getLedgerTransaction())
            )
        );
        rlNarrCol.setCellValueFactory(data ->
            new SimpleStringProperty(
                getNarrative(data.getValue().getLedgerTransaction())
            )
        );
        rbRefCol.setCellValueFactory(data ->
            new SimpleStringProperty(
                getRef(data.getValue().getBankTransaction())
            )
        );
        rbNarrCol.setCellValueFactory(data ->
            new SimpleStringProperty(
                getNarrative(data.getValue().getBankTransaction())
            )
        );
        rTypeCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getMatchType().name())
        );
        rConfCol.setCellValueFactory(data ->
            new SimpleStringProperty(
                String.format(
                    "%.0f%%",
                    data.getValue().getConfidenceScore() * 100
                )
            )
        );
        rResCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getStatus().name())
        );
        rResCol.setCellFactory(col ->
            new TableCell<>() {
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
                    switch (status) {
                        case "APPROVED":
                        case "AUTO_RECONCILED":
                            badge.getStyleClass().add("badge-active");
                            break;
                        case "PENDING_REVIEW":
                            badge.getStyleClass().add("badge-pending");
                            break;
                        default:
                            badge.getStyleClass().add("badge-review");
                            break;
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        );
    }

    //-------------- Methods ----------------------//
    private VBox makeStatCard(
        String label,
        String value,
        String sub,
        String valueColor
    ) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        UIAnimationUtil.applyHoverLift(card);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("sc-label");

        Label val = new Label(value);
        val.getStyleClass().add("sc-value");
        val.setStyle("-fx-text-fill: #" + valueColor + ";");

        Label s = new Label(sub);
        s.getStyleClass().add("sc-sub");

        card.getChildren().addAll(lbl, val, s);
        return card;
    }

    public void playEntranceAnimations() {
        List<Node> cards = new ArrayList<>(rptStatRow.getChildren());
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
                int decimals = digits.contains(".") ? 1 : 0;
                UIAnimationUtil.rollNumber(
                    valueLabel,
                    target,
                    decimals,
                    isPercent ? "%" : ""
                );
            } catch (NumberFormatException ignored) {
                // Skip labels that are not numeric.
            }
        }
    }

    private void showFeedback(String text) {
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
        feedbackLabel.setText(text);
    }

    private String getRef(aval.domain.ai.StandardizedTransaction tx) {
        if (tx == null) {
            return "";
        }
        return tx.getTransactionId().toString().substring(0, 8).toUpperCase();
    }

    private String getNarrative(aval.domain.ai.StandardizedTransaction tx) {
        return tx == null ? "" : tx.getNarrative();
    }
}
