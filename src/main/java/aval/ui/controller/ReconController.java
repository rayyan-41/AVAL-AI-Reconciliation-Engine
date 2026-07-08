package aval.ui.controller;

import aval.common.enums.DataSourceType;
import aval.common.enums.HypothesisStatus;
import aval.common.enums.TransactionType;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.MatchingConfig;
import aval.domain.core.ReconciliationWorkspace;
import aval.domain.ingestion.FinancialDataset;
import aval.engine.AnomalyDetectionEngine;
import aval.engine.MatchingProgressListener;
import aval.persistence.DataStore;
import aval.service.IngestionService;
import aval.service.ReconciliationResult;
import aval.service.ReconciliationService;
import aval.ui.MainUIContext;
import aval.ui.util.UIAnimationUtil;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ReconController {

    //-------------- Header ----------------------//
    @FXML private VBox actionBar;
    @FXML private Label periodLabel;
    @FXML private Button btnRecon;
    @FXML private Label reconHint;
    @FXML private Label stepLedger;
    @FXML private Label stepBank;

    //-------------- Pipeline & result ----------------------//
    @FXML private HBox pipelineStrip;
    @FXML private HBox resultStrip;
    @FXML private HBox resultChips;
    @FXML private Button btnReview;

    //-------------- Ledger panel ----------------------//
    @FXML private VBox ledgerPanel;
    @FXML private Label ledgerStatus;
    @FXML private Button btnLedgerReplace;
    @FXML private VBox ledgerDropPane;
    @FXML private VBox ledgerLoadingPane;
    @FXML private VBox ledgerLoadedPane;
    @FXML private Label ledgerLoadingFile;
    @FXML private ProgressBar ledgerLoadingBar;
    @FXML private Label ledgerLoadingLabel;
    @FXML private HBox ledgerMetaBar;
    @FXML private TableView<StandardizedTransaction> ledgerTable;
    @FXML private TableColumn<StandardizedTransaction, String> lDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> lRefCol;
    @FXML private TableColumn<StandardizedTransaction, String> lNarrCol;
    @FXML private TableColumn<StandardizedTransaction, String> lTypeCol;
    @FXML private TableColumn<StandardizedTransaction, String> lAmtCol;

    //-------------- Bank panel ----------------------//
    @FXML private VBox bankPanel;
    @FXML private Label bankStatus;
    @FXML private Button btnBankReplace;
    @FXML private VBox bankDropPane;
    @FXML private VBox bankLoadingPane;
    @FXML private VBox bankLoadedPane;
    @FXML private Label bankLoadingFile;
    @FXML private ProgressBar bankLoadingBar;
    @FXML private Label bankLoadingLabel;
    @FXML private HBox bankMetaBar;
    @FXML private TableView<StandardizedTransaction> bankTable;
    @FXML private TableColumn<StandardizedTransaction, String> bDateCol;
    @FXML private TableColumn<StandardizedTransaction, String> bRefCol;
    @FXML private TableColumn<StandardizedTransaction, String> bNarrCol;
    @FXML private TableColumn<StandardizedTransaction, String> bTypeCol;
    @FXML private TableColumn<StandardizedTransaction, String> bAmtCol;

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /** Visible pipeline stages, in execution order (COMPLETE is terminal, not a step). */
    private static final MatchingProgressListener.Stage[] PIPELINE_STAGES = {
        MatchingProgressListener.Stage.PREPARE,
        MatchingProgressListener.Stage.RULE_MATCH,
        MatchingProgressListener.Stage.SEMANTIC_MATCH,
        MatchingProgressListener.Stage.CLASSIFY,
        MatchingProgressListener.Stage.PERSIST,
    };

    private static final String[] PIPELINE_LABELS = {
        "Prepare", "Rule Match", "Semantic AI", "Score", "Persist",
    };

    public void initialize() {
        java.time.LocalDate now = java.time.LocalDate.now();
        periodLabel.setText(
            "Period: " +
                now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        );

        UIAnimationUtil.applyButtonPressFeedback(btnRecon);
        UIAnimationUtil.applyButtonPressFeedback(btnReview);
        setupTransactionTable(ledgerTable, lDateCol, lRefCol, lNarrCol, lTypeCol, lAmtCol);
        setupTransactionTable(bankTable, bDateCol, bRefCol, bNarrCol, bTypeCol, bAmtCol);
        setupDropZone(ledgerDropPane, ".xlsx", this::startLedgerIngestion);
        setupDropZone(bankDropPane, ".pdf", this::startBankIngestion);

        restoreFromContext();

        MainUIContext ctx = MainUIContext.getInstance();
        if (!ctx.isVectorizationAvailable()) {
            ledgerDropPane.setDisable(true);
            bankDropPane.setDisable(true);
            setBadge(ledgerStatus, "Ollama offline — ingestion disabled", "badge-review");
            setBadge(bankStatus, "Ollama offline — ingestion disabled", "badge-review");
        }
        updateReconButtonState();
    }

    /** Re-hydrates both panels from any previously persisted workspace data. */
    private void restoreFromContext() {
        MainUIContext ctx = MainUIContext.getInstance();
        DataStore ds = ctx.getDataStore();
        ReconciliationWorkspace ws = ctx.getActiveWorkspace();
        if (ds == null || ws == null) {
            return;
        }
        UUID wsId = ws.getWorkspaceId();
        List<StandardizedTransaction> ledger = ds.getLedgerTransactionsForWorkspace(wsId);
        List<StandardizedTransaction> bank = ds.getBankTransactionsForWorkspace(wsId);
        if (!ledger.isEmpty()) {
            ctx.setStandardizedLedgerTransactions(ledger);
            showLedgerLoaded(ledger);
        }
        if (!bank.isEmpty()) {
            ctx.setStandardizedBankTransactions(bank);
            showBankLoaded(bank);
        }
    }

    // =========================================================
    // Shared table configuration
    // =========================================================

    private void setupTransactionTable(
        TableView<StandardizedTransaction> table,
        TableColumn<StandardizedTransaction, String> dateCol,
        TableColumn<StandardizedTransaction, String> refCol,
        TableColumn<StandardizedTransaction, String> narrCol,
        TableColumn<StandardizedTransaction, String> typeCol,
        TableColumn<StandardizedTransaction, String> amtCol
    ) {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        dateCol.prefWidthProperty().bind(table.widthProperty().subtract(5).multiply(0.16));
        refCol.prefWidthProperty().bind(table.widthProperty().subtract(5).multiply(0.13));
        narrCol.prefWidthProperty().bind(table.widthProperty().subtract(5).multiply(0.38));
        typeCol.prefWidthProperty().bind(table.widthProperty().subtract(5).multiply(0.14));
        amtCol.prefWidthProperty().bind(table.widthProperty().subtract(5).multiply(0.19));

        dateCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getValueDate().format(DATE_FMT))
        );
        refCol.setCellValueFactory(cd ->
            new SimpleStringProperty(shortRef(cd.getValue()))
        );
        refCol.setCellFactory(col -> styledCell("cell-mono"));
        narrCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getNarrative())
        );
        typeCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getType().name())
        );
        typeCol.setCellFactory(col ->
            new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label chip = new Label(item);
                    chip.getStyleClass().addAll(
                        "tx-type",
                        TransactionType.DEBIT.name().equals(item)
                            ? "tx-debit"
                            : "tx-credit"
                    );
                    setGraphic(chip);
                    setText(null);
                }
            }
        );
        amtCol.setCellValueFactory(cd ->
            new SimpleStringProperty(formatAmount(cd.getValue().getAmount()))
        );
        amtCol.setCellFactory(col -> styledCell("cell-amount"));
        amtCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        table.setItems(FXCollections.observableArrayList());
    }

    private TableCell<StandardizedTransaction, String> styledCell(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove(styleClass);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                getStyleClass().add(styleClass);
                setText(item);
            }
        };
    }

    private static String shortRef(StandardizedTransaction tx) {
        return tx.getTransactionId().toString().substring(0, 8).toUpperCase();
    }

    private static String formatAmount(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "$%,.2f", amount);
    }

    // =========================================================
    // Drop zones & file selection
    // =========================================================

    private interface FileHandler {
        void accept(File file);
    }

    private void setupDropZone(VBox dropPane, String extension, FileHandler onFile) {
        dropPane.setOnDragEntered(e -> {
            if (hasAcceptableFile(e, extension)) {
                UIAnimationUtil.activateDropZone(dropPane);
            }
            e.consume();
        });
        dropPane.setOnDragExited(e -> {
            UIAnimationUtil.resetDropZone(dropPane);
            e.consume();
        });
        dropPane.setOnDragOver(e -> {
            if (hasAcceptableFile(e, extension)) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        dropPane.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(extension)) {
                    onFile.accept(file);
                }
            }
            e.setDropCompleted(true);
            UIAnimationUtil.resetDropZone(dropPane);
            e.consume();
        });
    }

    private boolean hasAcceptableFile(DragEvent e, String extension) {
        Dragboard db = e.getDragboard();
        return db.hasFiles() &&
            !db.getFiles().isEmpty() &&
            db.getFiles().get(0).getName().toLowerCase(Locale.ENGLISH).endsWith(extension);
    }

    @FXML
    void handleLoadLedger() {
        File f = chooseFile("Select Internal Ledger", "Excel Files", "*.xlsx", ledgerDropPane);
        if (f != null) {
            startLedgerIngestion(f);
        }
    }

    @FXML
    void handleBrowse() {
        File f = chooseFile("Select Bank Statement", "PDF Files", "*.pdf", bankDropPane);
        if (f != null) {
            startBankIngestion(f);
        }
    }

    private File chooseFile(String title, String filterName, String pattern, VBox owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, pattern));
        Stage stage = (Stage) owner.getScene().getWindow();
        return fc.showOpenDialog(stage);
    }

    @FXML
    void handleLedgerReplace() {
        MainUIContext.getInstance().setStandardizedLedgerTransactions(null);
        MainUIContext.getInstance().setLedgerSourceFileName(null);
        showLedgerState(SourceState.EMPTY);
        setBadge(ledgerStatus, "Not Loaded", "badge-pending");
        clearStaleResults();
        updateReconButtonState();
    }

    @FXML
    void handleBankReplace() {
        MainUIContext.getInstance().setStandardizedBankTransactions(null);
        MainUIContext.getInstance().setBankSourceFileName(null);
        showBankState(SourceState.EMPTY);
        setBadge(bankStatus, "Not Loaded", "badge-pending");
        clearStaleResults();
        updateReconButtonState();
    }

    // =========================================================
    // Ingestion (real two-phase progress: parse, then standardize)
    // =========================================================

    private void startLedgerIngestion(File f) {
        startSourceIngestion(f, true);
    }

    private void startBankIngestion(File f) {
        startSourceIngestion(f, false);
    }

    private void startSourceIngestion(File file, boolean isLedger) {
        MainUIContext ctx = MainUIContext.getInstance();
        if (isLedger) {
            ctx.setStandardizedLedgerTransactions(null);
            showLedgerState(SourceState.LOADING);
            ledgerLoadingFile.setText(file.getName());
            ledgerLoadingLabel.textProperty().unbind();
            setBadge(ledgerStatus, "Processing", "badge-pending");
        } else {
            ctx.setStandardizedBankTransactions(null);
            showBankState(SourceState.LOADING);
            bankLoadingFile.setText(file.getName());
            bankLoadingLabel.textProperty().unbind();
            setBadge(bankStatus, "Processing", "badge-pending");
        }
        clearStaleResults();
        updateReconButtonState();

        DataSourceType sourceType = isLedger
            ? DataSourceType.INTERNAL_EXCEL
            : DataSourceType.EXTERNAL_PDF;

        Task<List<StandardizedTransaction>> task = new Task<>() {
            @Override
            protected List<StandardizedTransaction> call() throws Exception {
                IngestionService svc = ctx.getIngestionService();
                ReconciliationWorkspace workspace = ctx.getActiveWorkspace();
                if (svc == null || workspace == null) {
                    throw new IllegalStateException(
                        "Ingestion service or active workspace is not available."
                    );
                }
                updateMessage("Parsing document and extracting transactions…");
                FinancialDataset dataset = svc.ingestFile(
                    workspace.getWorkspaceId(),
                    file.getPath(),
                    sourceType
                );
                if (dataset == null) {
                    throw new IllegalStateException(
                        "The file could not be read. Check the format and try again."
                    );
                }
                updateMessage(
                    "Standardizing " +
                        dataset.getRawTransactions().size() +
                        " records and building the semantic index…"
                );
                return svc.standardize(dataset);
            }
        };

        Label loadingLabel = isLedger ? ledgerLoadingLabel : bankLoadingLabel;
        loadingLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e ->
            Platform.runLater(() -> {
                loadingLabel.textProperty().unbind();
                List<StandardizedTransaction> txns = task.getValue();
                if (isLedger) {
                    ctx.setStandardizedLedgerTransactions(txns);
                    ctx.setLedgerSourceFileName(file.getName());
                    ctx.setLedgerLoadedAt(LocalDateTime.now());
                    showLedgerLoaded(txns);
                } else {
                    ctx.setStandardizedBankTransactions(txns);
                    ctx.setBankSourceFileName(file.getName());
                    ctx.setBankLoadedAt(LocalDateTime.now());
                    showBankLoaded(txns);
                }
                updateReconButtonState();
            })
        );

        task.setOnFailed(e ->
            Platform.runLater(() -> {
                loadingLabel.textProperty().unbind();
                String errMsg = task.getException() != null
                    ? task.getException().getMessage()
                    : "Unknown error";
                System.err.println(
                    "[" + (isLedger ? "LEDGER" : "BANK STATEMENT") + " ERROR] " + errMsg
                );
                if (task.getException() != null) {
                    task.getException().printStackTrace();
                }
                if (isLedger) {
                    ctx.setStandardizedLedgerTransactions(null);
                    showLedgerState(SourceState.EMPTY);
                    setBadge(ledgerStatus, "Error: " + errMsg, "badge-review");
                } else {
                    ctx.setStandardizedBankTransactions(null);
                    showBankState(SourceState.EMPTY);
                    setBadge(bankStatus, "Error: " + errMsg, "badge-review");
                }
                updateReconButtonState();
            })
        );

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // =========================================================
    // Panel state handling
    // =========================================================

    private enum SourceState { EMPTY, LOADING, LOADED }

    private void showLedgerState(SourceState state) {
        applyState(state, ledgerDropPane, ledgerLoadingPane, ledgerLoadedPane, btnLedgerReplace);
    }

    private void showBankState(SourceState state) {
        applyState(state, bankDropPane, bankLoadingPane, bankLoadedPane, btnBankReplace);
    }

    private void applyState(
        SourceState state,
        VBox dropPane,
        VBox loadingPane,
        VBox loadedPane,
        Button replaceButton
    ) {
        setShown(dropPane, state == SourceState.EMPTY);
        setShown(loadingPane, state == SourceState.LOADING);
        setShown(loadedPane, state == SourceState.LOADED);
        replaceButton.setVisible(state == SourceState.LOADED);
        replaceButton.setManaged(state == SourceState.LOADED);
    }

    private static void setShown(javafx.scene.Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void showLedgerLoaded(List<StandardizedTransaction> txns) {
        ledgerTable.setItems(FXCollections.observableArrayList(txns));
        populateMetaBar(ledgerMetaBar, txns);
        setBadge(ledgerStatus, "Loaded — " + txns.size() + " records", "badge-active");
        showLedgerState(SourceState.LOADED);
    }

    private void showBankLoaded(List<StandardizedTransaction> txns) {
        bankTable.setItems(FXCollections.observableArrayList(txns));
        populateMetaBar(bankMetaBar, txns);
        setBadge(bankStatus, "Loaded — " + txns.size() + " records", "badge-active");
        showBankState(SourceState.LOADED);
    }

    private void populateMetaBar(HBox metaBar, List<StandardizedTransaction> txns) {
        BigDecimal debits = sumByType(txns, TransactionType.DEBIT);
        BigDecimal credits = sumByType(txns, TransactionType.CREDIT);
        metaBar.getChildren().setAll(
            makeMetaItem("RECORDS", String.valueOf(txns.size())),
            makeMetaItem("DEBITS", formatAmount(debits)),
            makeMetaItem("CREDITS", formatAmount(credits)),
            makeMetaItem("PERIOD", dateRange(txns))
        );
    }

    private static BigDecimal sumByType(
        List<StandardizedTransaction> txns,
        TransactionType type
    ) {
        return txns
            .stream()
            .filter(t -> t.getType() == type)
            .map(t -> t.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String dateRange(List<StandardizedTransaction> txns) {
        var min = txns.stream().map(StandardizedTransaction::getValueDate).min(Comparable::compareTo);
        var max = txns.stream().map(StandardizedTransaction::getValueDate).max(Comparable::compareTo);
        if (min.isEmpty() || max.isEmpty()) {
            return "—";
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        return min.get().format(fmt) + " – " + max.get().format(fmt);
    }

    private VBox makeMetaItem(String caption, String value) {
        Label cap = new Label(caption);
        cap.getStyleClass().add("pm-cap");
        Label val = new Label(value);
        val.getStyleClass().add("pm-val");
        VBox box = new VBox(1, cap, val);
        return box;
    }

    private void setBadge(Label badge, String text, String variant) {
        badge.setText(text);
        badge.getStyleClass().setAll("badge", variant);
    }

    // =========================================================
    // Run reconciliation — pipeline driven by real engine stages
    // =========================================================

    @FXML
    void handlePerformRecon() {
        MainUIContext ctx = MainUIContext.getInstance();
        boolean ready =
            !ctx.getStandardizedLedgerTransactions().isEmpty() &&
            !ctx.getStandardizedBankTransactions().isEmpty();
        if (!ready) {
            updateReconButtonState();
            return;
        }

        setShown(actionBar, false);
        setShown(resultStrip, false);
        setShown(pipelineStrip, true);
        ledgerPanel.setDisable(true);
        bankPanel.setDisable(true);
        buildPipelineSteps();

        Task<ReconciliationResult> task = new Task<>() {
            @Override
            protected ReconciliationResult call() {
                ReconciliationService svc = ctx.getReconciliationService();
                ReconciliationWorkspace ws = ctx.getActiveWorkspace();
                List<StandardizedTransaction> stdLedger =
                    ctx.getStandardizedLedgerTransactions();
                List<StandardizedTransaction> stdBank =
                    ctx.getStandardizedBankTransactions();
                if (svc == null || ws == null) {
                    throw new IllegalStateException(
                        "Reconciliation dependencies are not ready."
                    );
                }
                return svc.runMatching(
                    ws,
                    stdLedger,
                    stdBank,
                    stage -> Platform.runLater(() -> markPipelineStage(stage))
                );
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> onReconciliationDone(task.getValue())));
        task.setOnFailed(e ->
            Platform.runLater(() -> {
                String errMsg = task.getException() != null
                    ? task.getException().getMessage()
                    : "Unknown error";
                reconHint.setText("Reconciliation failed: " + errMsg);
                setShown(pipelineStrip, false);
                setShown(actionBar, true);
                ledgerPanel.setDisable(false);
                bankPanel.setDisable(false);
            })
        );

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void buildPipelineSteps() {
        pipelineStrip.getChildren().clear();
        for (int i = 0; i < PIPELINE_STAGES.length; i++) {
            if (i > 0) {
                Region connector = new Region();
                connector.getStyleClass().add("pl-connector");
                pipelineStrip.getChildren().add(connector);
            }
            Region dot = new Region();
            dot.getStyleClass().add("pl-dot");
            Label label = new Label(PIPELINE_LABELS[i]);
            label.getStyleClass().add("pl-label");
            VBox step = new VBox(6, dot, label);
            step.setAlignment(javafx.geometry.Pos.CENTER);
            step.getStyleClass().add("pl-step");
            pipelineStrip.getChildren().add(step);
        }
    }

    private void markPipelineStage(MatchingProgressListener.Stage stage) {
        int activeIdx = -1;
        if (stage == MatchingProgressListener.Stage.COMPLETE) {
            activeIdx = PIPELINE_STAGES.length; // everything done
        } else {
            for (int i = 0; i < PIPELINE_STAGES.length; i++) {
                if (PIPELINE_STAGES[i] == stage) {
                    activeIdx = i;
                    break;
                }
            }
        }
        if (activeIdx < 0) {
            return;
        }
        int stepIdx = 0;
        for (javafx.scene.Node node : pipelineStrip.getChildren()) {
            if (!(node instanceof VBox step)) {
                continue;
            }
            Region dot = (Region) step.getChildren().get(0);
            Label label = (Label) step.getChildren().get(1);
            if (stepIdx < activeIdx) {
                dot.getStyleClass().setAll("pl-dot", "pl-dot-done");
                label.getStyleClass().setAll("pl-label", "pl-label-done");
            } else if (stepIdx == activeIdx) {
                dot.getStyleClass().setAll("pl-dot", "pl-dot-active");
                label.getStyleClass().setAll("pl-label", "pl-label-active");
            } else {
                dot.getStyleClass().setAll("pl-dot");
                label.getStyleClass().setAll("pl-label");
            }
            stepIdx++;
        }
    }

    private void onReconciliationDone(ReconciliationResult result) {
        MainUIContext ctx = MainUIContext.getInstance();
        List<MatchHypothesis> all = result.getHypotheses();
        List<ReconciliationRecord> autoRecords = result.getAutoReconciledRecords();
        ctx.setAllHypotheses(all);

        long autoCount = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.AUTO_RECONCILED)
            .count();
        List<MatchHypothesis> pending = all
            .stream()
            .filter(h -> h.getStatus() == HypothesisStatus.PENDING_REVIEW)
            .collect(Collectors.toList());
        ctx.setPendingHypotheses(pending);
        ctx.setReconciledRecords(new ArrayList<>(autoRecords));

        // Only auto-reconciled and pending hypotheses claim their transactions;
        // rejected candidates leave both sides unmatched.
        Set<UUID> matchedLedgerIds = all
            .stream()
            .filter(h -> h.getStatus() != HypothesisStatus.REJECTED)
            .map(MatchHypothesis::getLedgerTransaction)
            .filter(Objects::nonNull)
            .map(StandardizedTransaction::getTransactionId)
            .collect(Collectors.toSet());
        Set<UUID> matchedBankIds = all
            .stream()
            .filter(h -> h.getStatus() != HypothesisStatus.REJECTED)
            .map(MatchHypothesis::getBankTransaction)
            .filter(Objects::nonNull)
            .map(StandardizedTransaction::getTransactionId)
            .collect(Collectors.toSet());

        List<StandardizedTransaction> unmatchedLedger = ctx
            .getStandardizedLedgerTransactions()
            .stream()
            .filter(t -> !matchedLedgerIds.contains(t.getTransactionId()))
            .collect(Collectors.toList());
        List<StandardizedTransaction> unmatchedBank = ctx
            .getStandardizedBankTransactions()
            .stream()
            .filter(t -> !matchedBankIds.contains(t.getTransactionId()))
            .collect(Collectors.toList());
        ctx.setUnmatchedLedger(unmatchedLedger);
        ctx.setUnmatchedBank(unmatchedBank);

        AnomalyDetectionEngine anomalyEngine = ctx.getAnomalyDetectionEngine();
        List<aval.domain.ai.Anomaly> anomalies = anomalyEngine != null
            ? anomalyEngine.identifyAnomalies(unmatchedLedger, unmatchedBank)
            : List.of();
        ctx.setAnomalies(anomalies);

        int ledgerTotal = ctx.getStandardizedLedgerTransactions().size();
        double autoRate = ledgerTotal == 0 ? 0 : (double) autoCount / ledgerTotal * 100;
        MatchingConfig config = ctx.getActiveMatchingConfig();
        String thresholdText = String.format(
            Locale.ENGLISH,
            "auto-matched · ≥ %.0f%% confidence",
            config.getAutoConfirmThreshold() * 100
        );

        resultChips.getChildren().setAll(
            makeResultChip(String.valueOf(autoCount), thresholdText, "sum-good"),
            makeResultChip(String.valueOf(pending.size()), "need your review", "sum-warn"),
            makeResultChip(String.valueOf(unmatchedLedger.size()), "unmatched ledger", "sum-open"),
            makeResultChip(String.valueOf(unmatchedBank.size()), "unmatched bank", "sum-open"),
            makeResultChip(
                String.format(Locale.ENGLISH, "%.0f%%", autoRate),
                "auto-match rate",
                "sum-rate"
            )
        );
        btnReview.setText(
            pending.isEmpty()
                ? "Continue to Review"
                : "Review " + pending.size() + " Matches"
        );

        markPipelineStage(MatchingProgressListener.Stage.COMPLETE);
        PauseTransition settle = new PauseTransition(Duration.millis(500));
        settle.setOnFinished(ev -> {
            setShown(pipelineStrip, false);
            setShown(resultStrip, true);
        });
        settle.play();

        ledgerPanel.setDisable(false);
        bankPanel.setDisable(false);

        IWorkspaceController wsc = ctx.getWorkspaceController();
        if (wsc instanceof WorkspaceController wc) {
            wc.notifyReconciliationCompleted();
        }
        if (wsc != null) {
            wsc.unlockManualCheck();
        }
    }

    private HBox makeResultChip(String value, String caption, String variant) {
        Label num = new Label(value);
        num.getStyleClass().add("sum-num");
        Label cap = new Label(caption);
        cap.getStyleClass().add("sum-cap");
        HBox chip = new HBox(7, num, cap);
        chip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        chip.getStyleClass().addAll("sum-chip", variant);
        return chip;
    }

    @FXML
    void handleGoToReview() {
        IWorkspaceController wsc = MainUIContext.getInstance().getWorkspaceController();
        if (wsc != null) {
            wsc.openManualCheck();
        }
    }

    @FXML
    void handleRunAgain() {
        clearStaleResults();
        updateReconButtonState();
    }

    /** Hides stale results/pipeline when a source file changes after a run. */
    private void clearStaleResults() {
        setShown(resultStrip, false);
        setShown(pipelineStrip, false);
        setShown(actionBar, true);
    }

    // =========================================================
    // Readiness
    // =========================================================

    private void updateReconButtonState() {
        MainUIContext ctx = MainUIContext.getInstance();
        List<StandardizedTransaction> ledger = ctx.getStandardizedLedgerTransactions();
        List<StandardizedTransaction> bank = ctx.getStandardizedBankTransactions();
        boolean ledgerReady = ledger != null && !ledger.isEmpty();
        boolean bankReady = bank != null && !bank.isEmpty();
        btnRecon.setDisable(!(ledgerReady && bankReady));

        applyStepChip(stepLedger, "1 · INTERNAL LEDGER", "✓ INTERNAL LEDGER", ledgerReady);
        applyStepChip(stepBank, "2 · BANK STATEMENT", "✓ BANK STATEMENT", bankReady);

        if (!ledgerReady && !bankReady) {
            reconHint.setText(
                "Load the internal ledger and the bank statement, then run the matching engine."
            );
        } else if (!ledgerReady) {
            reconHint.setText("Bank statement ready. Load the internal ledger to proceed.");
        } else if (!bankReady) {
            reconHint.setText("Ledger ready. Load the bank statement to proceed.");
        } else {
            reconHint.setText(
                "Ready to reconcile " + ledger.size() + " ledger records against " +
                    bank.size() + " bank records."
            );
        }
    }

    private void applyStepChip(Label chip, String pendingText, String doneText, boolean done) {
        chip.setText(done ? doneText : pendingText);
        chip.getStyleClass().setAll("step-chip", done ? "step-chip-done" : "step-chip-pending");
    }
}
