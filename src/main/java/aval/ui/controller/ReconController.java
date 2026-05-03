package aval.ui.controller;

import aval.common.enums.DataSourceType;
import aval.common.enums.HypothesisStatus;
import aval.common.enums.UserRole;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ReconciliationWorkspace;
import aval.domain.ingestion.FinancialDataset;
import aval.engine.AnomalyDetectionEngine;
import aval.service.IngestionService;
import aval.service.ReconciliationResult;
import aval.service.ReconciliationService;
import aval.ui.MainUIContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ReconController {

    @FXML
    private HBox actionBar;

    @FXML
    private Label periodLabel;

    @FXML
    private Button btnRecon;

    @FXML
    private Label reconHint;

    @FXML
    private HBox pipelineStrip;

    @FXML
    private HBox resultBar;

    @FXML
    private Label rbAuto;

    @FXML
    private Label rbManual;

    @FXML
    private Label bankStatus;

    @FXML
    private Label ledgerStatus;

    @FXML
    private StackPane bankPanel;

    @FXML
    private VBox dropZonePane;

    @FXML
    private VBox ingestingPane;

    @FXML
    private VBox ingestedPane;

    @FXML
    private Label ingFilename;

    @FXML
    private ProgressBar ingProgress;

    @FXML
    private Label ingLabel;

    @FXML
    private TableView<StandardizedTransaction> ledgerTable;

    @FXML
    private TableColumn<StandardizedTransaction, String> lDateCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lRefCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lNarrCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lTypeCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lAmtCol;

    private static final String[] ING_STEPS = {
        "Parsing document structure...",
        "Extracting transaction table...",
        "Normalizing date and amount fields...",
        "Running schema standardization...",
        "Building semantic index...",
        "Ingestion complete.",
    };

    private static final List<String> pipeSteps = Arrays.asList(
        "Load",
        "Clean",
        "Index",
        "Rule Match",
        "AI Vector",
        "Score",
        "Done"
    );

    public void initialize() {
        // Set dynamic period based on current date
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
        periodLabel.setText("Reconciliation Period: " + now.format(formatter));

        setupDropZone();
        setupLedgerTable();
        showBankState("dropzone");
        updateReconButtonState();
    }

    private void setupLedgerTable() {
        lDateCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getValueDate().toString())
        );
        lRefCol.setCellValueFactory(cd ->
            new SimpleStringProperty(
                cd
                    .getValue()
                    .getTransactionId()
                    .toString()
                    .substring(0, 8)
                    .toUpperCase()
            )
        );
        lNarrCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getNarrative())
        );
        lTypeCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getType().name())
        );

        lAmtCol.setCellValueFactory(cd -> {
            boolean isDebit =
                cd.getValue().getType() ==
                aval.common.enums.TransactionType.DEBIT;
            String prefix = isDebit ? "-" : "+";
            return new SimpleStringProperty(
                prefix + "$" + cd.getValue().getAmount().toString()
            );
        });

        lAmtCol.setCellFactory(col ->
            new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label l = new Label(item);
                    l.setStyle(
                        "-fx-font-family: 'Consolas'; " +
                            (item.startsWith("-")
                                ? "-fx-text-fill: -fx-red-text;"
                                : "-fx-text-fill: -fx-text-primary;")
                    );
                    setGraphic(l);
                    setText(null);
                }
            }
        );

        populateLedgerTable(List.of());
    }

    public void populateLedgerTable(
        List<StandardizedTransaction> transactions
    ) {
        List<StandardizedTransaction> source =
            transactions != null ? transactions : List.of();
        ObservableList<StandardizedTransaction> txns =
            FXCollections.observableArrayList(source);
        ledgerTable.setItems(txns);
    }

    private void setupDropZone() {
        dropZonePane.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });

        dropZonePane.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    startIngestion(files.get(0));
                }
            }
            e.setDropCompleted(true);
            e.consume();
        });
    }

    @FXML
    void handleBrowse() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Bank Statement");
        fc
            .getExtensionFilters()
            .add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        Stage stage = (Stage) dropZonePane.getScene().getWindow();
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            startIngestion(f);
        }
    }

    @FXML
    void handleLoadLedger() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Internal Ledger");
        fc
            .getExtensionFilters()
            .add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        Stage stage = (Stage) ledgerStatus.getScene().getWindow();
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            startLedgerIngestion(f);
        }
    }

    private void startLedgerIngestion(File f) {
        MainUIContext.getInstance().setStandardizedLedgerTransactions(null);
        updateReconButtonState();
        ledgerStatus.setText("Loading...");
        ledgerStatus.getStyleClass().setAll("badge", "badge-pending");

        Task<List<StandardizedTransaction>> task = new Task<>() {
            @Override
            protected List<StandardizedTransaction> call() throws Exception {
                MainUIContext ctx = MainUIContext.getInstance();
                IngestionService svc = ctx.getIngestionService();
                ReconciliationWorkspace workspace = ctx.getActiveWorkspace();
                if (svc == null || workspace == null) {
                    throw new IllegalStateException(
                        "Ingestion service or active workspace is not available."
                    );
                }

                FinancialDataset dataset = svc.ingestFile(
                    workspace.getWorkspaceId(),
                    f.getPath(),
                    DataSourceType.INTERNAL_EXCEL
                );
                if (dataset == null) {
                    throw new IllegalStateException(
                        "Ledger file could not be ingested."
                    );
                }
                return svc.standardize(dataset);
            }
        };

        task.setOnSucceeded(e ->
            Platform.runLater(() -> {
                List<StandardizedTransaction> stdLedger = task.getValue();
                MainUIContext.getInstance().setStandardizedLedgerTransactions(
                    stdLedger
                );
                ledgerStatus.setText(
                    "Loaded — " + stdLedger.size() + " records"
                );
                ledgerStatus.getStyleClass().setAll("badge", "badge-active");
                populateLedgerTable(stdLedger);
                updateReconButtonState();
            })
        );

        task.setOnFailed(e ->
            Platform.runLater(() -> {
                String errMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                System.err.println("[LEDGER ERROR] " + errMsg);
                task.getException().printStackTrace();
                MainUIContext.getInstance().setStandardizedLedgerTransactions(
                    null
                );
                ledgerStatus.setText("Error: " + errMsg);
                ledgerStatus.getStyleClass().setAll("badge", "badge-review");
                updateReconButtonState();
            })
        );

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void startIngestion(File f) {
        MainUIContext.getInstance().setStandardizedBankTransactions(null);
        updateReconButtonState();
        showBankState("ingesting");
        ingFilename.setText(f.getName());
        bankStatus.setText("Processing");
        bankStatus.getStyleClass().setAll("badge", "badge-pending");

        SequentialTransition seq = new SequentialTransition();
        for (int i = 0; i < ING_STEPS.length; i++) {
            final int idx = i;
            PauseTransition pt = new PauseTransition(Duration.millis(460));
            pt.setOnFinished(e -> {
                ingProgress.setProgress((double) (idx + 1) / ING_STEPS.length);
                ingLabel.setText(ING_STEPS[idx]);
            });
            seq.getChildren().add(pt);
        }
        seq.setOnFinished(e -> finishIngestion(f));
        seq.play();
    }

    private void finishIngestion(File f) {
        Task<List<StandardizedTransaction>> task = new Task<>() {
            @Override
            protected List<StandardizedTransaction> call() throws Exception {
                MainUIContext ctx = MainUIContext.getInstance();
                IngestionService svc = ctx.getIngestionService();
                ReconciliationWorkspace workspace = ctx.getActiveWorkspace();
                if (svc == null || workspace == null) {
                    throw new IllegalStateException(
                        "Ingestion service or active workspace is not available."
                    );
                }

                FinancialDataset dataset = svc.ingestFile(
                    workspace.getWorkspaceId(),
                    f.getPath(),
                    DataSourceType.EXTERNAL_PDF
                );
                if (dataset == null) {
                    throw new IllegalStateException(
                        "Bank statement could not be ingested."
                    );
                }
                return svc.standardize(dataset);
            }
        };

        task.setOnSucceeded(e ->
            Platform.runLater(() -> {
                List<StandardizedTransaction> stdBank = task.getValue();
                MainUIContext.getInstance().setStandardizedBankTransactions(
                    stdBank
                );
                showBankState("ingested");
                bankStatus.setText("Ingested — " + stdBank.size() + " records");
                bankStatus.getStyleClass().setAll("badge", "badge-active");
                updateReconButtonState();
            })
        );

        task.setOnFailed(e ->
            Platform.runLater(() -> {
                String errMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                System.err.println("[BANK STATEMENT ERROR] " + errMsg);
                task.getException().printStackTrace();
                MainUIContext.getInstance().setStandardizedBankTransactions(
                    null
                );
                showBankState("dropzone");
                bankStatus.setText("Parse Error: " + errMsg);
                bankStatus.getStyleClass().setAll("badge", "badge-review");
                updateReconButtonState();
            })
        );

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    void resetDropZone() {
        MainUIContext.getInstance().setStandardizedBankTransactions(null);
        showBankState("dropzone");
        bankStatus.setText("Awaiting File");
        bankStatus.getStyleClass().setAll("badge", "badge-pending");
        updateReconButtonState();
    }

    private void showBankState(String state) {
        dropZonePane.setVisible("dropzone".equals(state));
        dropZonePane.setManaged("dropzone".equals(state));

        ingestingPane.setVisible("ingesting".equals(state));
        ingestingPane.setManaged("ingesting".equals(state));

        ingestedPane.setVisible("ingested".equals(state));
        ingestedPane.setManaged("ingested".equals(state));
    }

    @FXML
    void handlePerformRecon() {
        MainUIContext ctx = MainUIContext.getInstance();
        boolean ready =
            ctx.getStandardizedLedgerTransactions() != null &&
            ctx.getStandardizedBankTransactions() != null;
        if (!ready) {
            updateReconButtonState();
            return;
        }

        actionBar.setVisible(false);
        actionBar.setManaged(false);
        pipelineStrip.setVisible(true);
        pipelineStrip.setManaged(true);
        pipelineStrip.getChildren().clear();

        // Build pipeline strip visually
        for (int i = 0; i < pipeSteps.size(); i++) {
            StackPane node = new StackPane();
            Circle c = new Circle(14, Color.web("#e2e5e9"));
            c.setId("circle_" + i);
            Label l = new Label(pipeSteps.get(i));
            l.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -fx-text-muted; -fx-translate-y: 24;"
            );
            node.getChildren().addAll(c, l);
            pipelineStrip.getChildren().add(node);

            if (i < pipeSteps.size() - 1) {
                Label arrow = new Label("→");
                arrow.setStyle(
                    "-fx-text-fill: -fx-text-ghost; -fx-font-weight: bold;"
                );
                pipelineStrip.getChildren().add(arrow);
            }
        }

        long[] delays = { 600, 700, 900, 1100, 1200, 800, 400 };
        SequentialTransition seq = new SequentialTransition();

        for (int i = 0; i < pipeSteps.size(); i++) {
            final int idx = i;
            PauseTransition pt = new PauseTransition(
                Duration.millis(delays[idx])
            );
            pt.setOnFinished(e -> markStepDone(idx));
            seq.getChildren().add(pt);
        }

        seq.setOnFinished(e -> finishReconciliation());
        seq.play();
    }

    private void markStepDone(int stepIdx) {
        // Step nodes are at 0, 2, 4, 6... in pipelineStrip.getChildren()
        StackPane node = (StackPane) pipelineStrip
            .getChildren()
            .get(stepIdx * 2);
        Circle c = (Circle) node.getChildren().get(0);
        c.setFill(Color.web("#800020"));
    }

    private void finishReconciliation() {
        Task<ReconciliationResult> task = new Task<>() {
            @Override
            protected ReconciliationResult call() {
                MainUIContext ctx = MainUIContext.getInstance();
                ReconciliationService svc = ctx.getReconciliationService();
                ReconciliationWorkspace ws = ctx.getActiveWorkspace();
                List<StandardizedTransaction> stdLedger =
                    ctx.getStandardizedLedgerTransactions();
                List<StandardizedTransaction> stdBank =
                    ctx.getStandardizedBankTransactions();

                if (
                    svc == null ||
                    ws == null ||
                    stdLedger == null ||
                    stdBank == null
                ) {
                    throw new IllegalStateException(
                        "Reconciliation dependencies are not ready."
                    );
                }
                return svc.runMatching(ws, stdLedger, stdBank);
            }
        };

        task.setOnSucceeded(e ->
            Platform.runLater(() -> {
                ReconciliationResult result = task.getValue();
                List<MatchHypothesis> all = result.getHypotheses();
                List<ReconciliationRecord> autoRecords = result.getAutoReconciledRecords();
                MainUIContext ctx = MainUIContext.getInstance();
                ctx.setAllHypotheses(all);

                long autoCount = all
                    .stream()
                    .filter(
                        h -> h.getStatus() == HypothesisStatus.AUTO_RECONCILED
                    )
                    .count();
                List<MatchHypothesis> pending = all
                    .stream()
                    .filter(
                        h -> h.getStatus() == HypothesisStatus.PENDING_REVIEW
                    )
                    .collect(Collectors.toList());
                ctx.setPendingHypotheses(pending);

                // Use the auto-reconciled records from the service (already persisted to DB)
                ctx.setReconciledRecords(new ArrayList<>(autoRecords));

                Set<UUID> matchedLedgerIds = all
                    .stream()
                    .map(MatchHypothesis::getLedgerTransaction)
                    .filter(Objects::nonNull)
                    .map(StandardizedTransaction::getTransactionId)
                    .collect(Collectors.toSet());
                Set<UUID> matchedBankIds = all
                    .stream()
                    .map(MatchHypothesis::getBankTransaction)
                    .filter(Objects::nonNull)
                    .map(StandardizedTransaction::getTransactionId)
                    .collect(Collectors.toSet());

                List<StandardizedTransaction> unmatchedLedger = ctx
                    .getStandardizedLedgerTransactions()
                    .stream()
                    .filter(t ->
                        !matchedLedgerIds.contains(t.getTransactionId())
                    )
                    .collect(Collectors.toList());
                List<StandardizedTransaction> unmatchedBank = ctx
                    .getStandardizedBankTransactions()
                    .stream()
                    .filter(t -> !matchedBankIds.contains(t.getTransactionId()))
                    .collect(Collectors.toList());

                ctx.setUnmatchedLedger(unmatchedLedger);
                ctx.setUnmatchedBank(unmatchedBank);

                AnomalyDetectionEngine anomalyEngine =
                    ctx.getAnomalyDetectionEngine();
                List<String> anomalies =
                    anomalyEngine != null
                        ? anomalyEngine.identifyAnomalies(
                              unmatchedLedger,
                              unmatchedBank
                          )
                        : List.of();
                ctx.setAnomalies(anomalies);

                rbAuto.setText(String.valueOf(autoCount));
                rbManual.setText(String.valueOf(pending.size()));
                resultBar.setVisible(true);
                resultBar.setManaged(true);
                reconHint.setText("Reconciliation complete.");

                Object wsc = ctx.getWorkspaceController();
                if (wsc instanceof WorkspaceController wc) {
                    wc.notifyReconciliationCompleted();
                    wc.unlockManualCheck();
                }
            })
        );

        task.setOnFailed(e ->
            Platform.runLater(() -> {
                reconHint.setText(
                    "Reconciliation failed: " + task.getException().getMessage()
                );
                actionBar.setVisible(true);
                actionBar.setManaged(true);
                pipelineStrip.setVisible(false);
                pipelineStrip.setManaged(false);
            })
        );

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void updateReconButtonState() {
        MainUIContext ctx = MainUIContext.getInstance();
        boolean ready =
            ctx.getStandardizedLedgerTransactions() != null &&
            ctx.getStandardizedBankTransactions() != null;
        btnRecon.setDisable(!ready);
        reconHint.setText(
            ready
                ? "Both datasets loaded. Ready to reconcile."
                : "Load both datasets to proceed."
        );
    }
}
