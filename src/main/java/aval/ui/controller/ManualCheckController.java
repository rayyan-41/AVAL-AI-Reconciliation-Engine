package aval.ui.controller;

import aval.common.enums.HypothesisStatus;
import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.service.ReconciliationService;
import aval.ui.MainUIContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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
    @FXML private ListView<String> anomalyList;
    @FXML private HBox mcCompleteBar;

    //-------------- Attributes ----------------------//
    private WorkspaceController workspaceController;
    private ObservableList<MatchHypothesis> hypothesesList;
    private int resolved = 0;
    private boolean anomaliesDismissed = true;

    //-------------- Methods ----------------------//
    public void setWorkspaceController(WorkspaceController wc) { this.workspaceController = wc; }

    @FXML
    public void initialize() {
        mcSub.setText(
            "Review AI-suggested hypotheses below 95% confidence and approve or reject each item."
        );
        hypothesesList = FXCollections.observableArrayList();
        mcTable.setItems(hypothesesList);

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
                private final Button approve = new Button("Approve");
                private final Button reject = new Button("Reject");

                {
                    approve.getStyleClass().add("btn-approve");
                    reject.getStyleClass().add("btn-reject");
                    approve.setOnAction(e ->
                        resolveItem(getIndex(), "APPROVED")
                    );
                    reject.setOnAction(e ->
                        resolveItem(getIndex(), "REJECTED")
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
                        Label done = new Label(item.getStatus().name());
                        done.setStyle(
                            item.getStatus() == HypothesisStatus.APPROVED
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
        resolved = 0;
        mcCompleteBar.setVisible(false);
        mcCompleteBar.setManaged(false);
        updateProgress();
    }

    public void loadAnomalies() {
        List<String> anomalies = MainUIContext.getInstance().getAnomalies();
        if (anomalies != null && !anomalies.isEmpty()) {
            anomaliesDismissed = false;
            anomalyList.setItems(FXCollections.observableArrayList(anomalies));
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

    private void resolveItem(int index, String decision) {
        if (index < 0 || index >= mcTable.getItems().size()) {
            return;
        }

        MatchHypothesis h = mcTable.getItems().get(index);
        if (h.getStatus() != HypothesisStatus.PENDING_REVIEW) {
            return;
        }

        MainUIContext ctx = MainUIContext.getInstance();
        SystemUser user = ctx.getCurrentUser();
        if (user == null) {
            user = new SystemUser(
                UUID.randomUUID(),
                "Local User",
                "00000-0000000-0",
                "local-user",
                UserRole.ACCOUNTANT,
                "Local"
            );
            ctx.setCurrentUser(user);
        }
        ReconciliationService svc = ctx.getReconciliationService();
        if (svc == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Cannot Save Review");
            alert.setHeaderText("Reconciliation service is not available");
            alert.setContentText(
                "Your decision could not be saved because the reconciliation service " +
                "is not initialized. Please restart the application and try again.");
            alert.showAndWait();
            return;
        }

        if ("APPROVED".equals(decision)) {
            h.setStatus(HypothesisStatus.APPROVED);
        } else {
            h.setStatus(HypothesisStatus.REJECTED);
        }
        resolved++;
        mcTable.refresh();
        updateProgress();

        SystemUser finalUser = user;
        Task<Void> persistTask = new Task<>() {
            @Override
            protected Void call() {
                if ("APPROVED".equals(decision)) {
                    ReconciliationRecord record = svc.confirmHypothesis(
                        h,
                        finalUser
                    );
                    MainUIContext.getInstance().addReconciledRecord(record);
                } else {
                    svc.rejectHypothesis(h, finalUser);
                }
                return null;
            }
        };
        persistTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Save Failed");
                alert.setHeaderText("Could not save your decision");
                alert.setContentText(
                    "Error: " + persistTask.getException().getMessage() +
                    "\n\nYour decision was NOT persisted. Please try again.");
                alert.showAndWait();
            });
        });
        Thread thread = new Thread(persistTask);
        thread.setDaemon(true);
        thread.start();
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
