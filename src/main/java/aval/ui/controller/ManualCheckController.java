package aval.ui.controller;

import aval.common.enums.HypothesisStatus;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.StandardizedTransaction;
import aval.ui.MainUIContext;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.stream.Collectors;

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
    @FXML private HBox mcCompleteBar;

    private WorkspaceController workspaceController;
    private ObservableList<MatchHypothesis> hypothesesList;
    private int resolved = 0;

    public void setWorkspaceController(WorkspaceController wc) {
        this.workspaceController = wc;
    }

    @FXML
    public void initialize() {
        mcSub.setText(
            "Review AI-suggested hypotheses below 95% confidence and approve or reject each item."
        );
        hypothesesList = FXCollections.observableArrayList();
        mcTable.setItems(hypothesesList);

        // Value factories
        mcConfCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getConfidenceScore()));
        mcLedgerCol.setCellValueFactory(data -> {
            return new SimpleStringProperty(formatTransaction(data.getValue().getLedgerTransaction()));
        });
        mcBankCol.setCellValueFactory(data -> {
            return new SimpleStringProperty(formatTransaction(data.getValue().getBankTransaction()));
        });
        mcJustCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getJustification())
        );

        // Confidence pill cell
        mcConfCol.setCellFactory(col -> new TableCell<MatchHypothesis, Double>() {
            @Override protected void updateItem(Double conf, boolean empty) {
                super.updateItem(conf, empty);
                if (empty || conf == null) { setGraphic(null); return; }
                int pct = (int)(conf * 100);
                Label pill = new Label(pct + "%");
                pill.getStyleClass().addAll("conf-pill", pct >= 85 ? "conf-hi" : "conf-lo");
                setGraphic(pill); setText(null);
            }
        });

        // Action buttons cell
        mcActionCol.setCellFactory(col -> new TableCell<MatchHypothesis, Void>() {
            private final Button approve = new Button("Approve");
            private final Button reject  = new Button("Reject");
            {
                approve.getStyleClass().add("btn-approve");
                reject.getStyleClass().add("btn-reject");
                approve.setOnAction(e -> resolveItem(getIndex(), "APPROVED"));
                reject.setOnAction(e  -> resolveItem(getIndex(), "REJECTED"));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                MatchHypothesis item = empty ? null : getTableView().getItems().get(getIndex());
                if (item == null) { setGraphic(null); return; }
                if (item.getStatus() == HypothesisStatus.PENDING_REVIEW) {
                    setGraphic(new HBox(8, approve, reject));
                } else {
                    Label done = new Label(item.getStatus().name());
                    done.setStyle(item.getStatus() == HypothesisStatus.APPROVED
                        ? "-fx-text-fill: #1a5c2a;" : "-fx-text-fill: #800020;");
                    setGraphic(done);
                }
                setText(null);
            }
        });
    }

    public void setItems(List<MatchHypothesis> pendingHypotheses) {
        hypothesesList.setAll(
            pendingHypotheses
                .stream()
                .filter(h -> h.getConfidenceScore() < 0.95)
                .collect(Collectors.toList())
        );
        resolved = 0;
        updateProgress();
    }

    private void resolveItem(int index, String decision) {
        MatchHypothesis h = mcTable.getItems().get(index);
        if (decision.equals("APPROVED"))
            h.setStatus(HypothesisStatus.APPROVED);
        else
            h.setStatus(HypothesisStatus.REJECTED);
        resolved++;
        mcTable.refresh();
        updateProgress();
    }

    private void updateProgress() {
        int total = mcTable.getItems().size();
        if (total == 0) return;
        double prog = (double) resolved / total;
        mcProgressBar.setProgress(prog);
        mcProgText.setText(resolved + " of " + total + " resolved");
        if (resolved >= total) {
            mcCompleteBar.setVisible(true);
            mcCompleteBar.setManaged(true);
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
        String ref = tx.getTransactionId().toString().substring(0, 8).toUpperCase();
        return tx.getNarrative() + "\n" + ref + "\n" + tx.getAmount();
    }
}
