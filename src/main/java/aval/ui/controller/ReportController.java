package aval.ui.controller;

import aval.common.enums.HypothesisStatus;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.service.ReportService;
import aval.ui.MainUIContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class ReportController {

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

    private final List<MatchHypothesis> currentHypotheses = new ArrayList<>();
    private String lastGeneratedReportPath = null;

    @FXML
    public void initialize() {
        updateHeader();
        setupLineageTable();

        List<MatchHypothesis> existing =
            MainUIContext.getInstance().getAllHypotheses();
        if (existing == null) {
            existing = MainUIContext.getInstance().getPendingHypotheses();
        }
        if (existing != null) {
            populateReport(existing);
        }
    }

    public void populateReport(List<MatchHypothesis> all) {
        currentHypotheses.clear();
        currentHypotheses.addAll(all);

        long autoMatched = all
            .stream()
            .filter(h -> h.getConfidenceScore() >= 0.95)
            .count();
        long approved = all
            .stream()
            .filter(
                h ->
                    h.getStatus() == HypothesisStatus.APPROVED &&
                    h.getConfidenceScore() < 0.95
            )
            .count();
        long rejected = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.REJECTED)
            .count();
        long total = all.size();
        long matched = autoMatched + approved;
        double rate = total == 0 ? 0 : ((double) matched / total) * 100;

        rptStatRow
            .getChildren()
            .setAll(
                makeStatCard(
                    "Total Hypotheses",
                    String.valueOf(total),
                    "All candidates",
                    "0d0d0d"
                ),
                makeStatCard(
                    "Auto-Matched",
                    String.valueOf(autoMatched),
                    "Confidence ≥ 95%",
                    "1a5c2a"
                ),
                makeStatCard(
                    "Manual Approved",
                    String.valueOf(approved),
                    "Approved in review",
                    "0d0d0d"
                ),
                makeStatCard(
                    "Rejected",
                    String.valueOf(rejected),
                    "Dismissed by reviewer",
                    "800020"
                ),
                makeStatCard(
                    "Final Match Rate",
                    String.format("%.1f%%", rate),
                    "Auto + approved",
                    "1a5c2a"
                )
            );

        lineageTable.setItems(FXCollections.observableArrayList(all));
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
        String outputPath =
            System.getProperty("user.home") +
            "\\reconciliation_report_" +
            clientName +
            "_Sep2024.csv";
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
                List<String> anomalies = ctx.getAnomalies();

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

    private VBox makeStatCard(
        String label,
        String value,
        String sub,
        String valueColor
    ) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
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
