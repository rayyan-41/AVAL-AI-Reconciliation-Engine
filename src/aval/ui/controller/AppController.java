//Dev: Aryan
//Use Cases: All navigation
package aval.ui.controller;

import aval.common.enums.DataSourceType;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import aval.domain.ingestion.FinancialDataset;
import aval.engine.AnomalyDetectionEngine;
import aval.engine.LangChain4jVectorizationEngine;
import aval.engine.RuleBasedMatchingEngine;
import aval.engine.SemanticMatchingEngine;
import aval.persistence.DataStore;
import aval.service.IngestionService;
import aval.service.ReconciliationService;
import aval.service.ReportService;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class AppController {

    private BorderPane root;
    private TextArea logArea;
    private Button runReconciliationBtn;
    private TableView<MatchHypothesis> hypothesisTable;
    private ObservableList<MatchHypothesis> hypothesisData =
        FXCollections.observableArrayList();

    private ListView<StandardizedTransaction> unmatchedLedgerList =
        new ListView<>();
    private ListView<StandardizedTransaction> unmatchedBankList =
        new ListView<>();

    // Backend Services
    private DataStore dataStore;
    private IngestionService ingestionService;
    private ReconciliationService reconciliationService;
    private ReportService reportService;
    private AnomalyDetectionEngine anomalyEngine;

    // State
    private String ledgerPath;
    private String bankPath;
    private SystemUser currentUser;

    public AppController() {
        initializeBackend();
        createView();
    }

    private void initializeBackend() {
        try {
            currentUser = new SystemUser(
                UUID.randomUUID(),
                "Aryan-Dev",
                aval.common.enums.UserRole.ADMIN
            );
            Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/aval_db",
                "aval_user",
                "aval_password"
            );
            dataStore = new DataStore(conn);
            anomalyEngine = new AnomalyDetectionEngine();

            LangChain4jVectorizationEngine vectorEngine =
                new LangChain4jVectorizationEngine(
                    "http://localhost:11434",
                    "nomic-embed-text"
                );
            ingestionService = new IngestionService(dataStore, vectorEngine);

            SemanticMatchingEngine matchingEngine = new SemanticMatchingEngine(
                vectorEngine,
                dataStore
            );

            reconciliationService = new ReconciliationService(
                vectorEngine,
                matchingEngine,
                dataStore
            );
            reportService = new ReportService();
        } catch (Exception e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Database Connection Error");
                alert.setHeaderText("Could not connect to PostgreSQL");
                alert.setContentText(
                    "Ensure the Docker containers are running and the database is accessible.\n\n" +
                        e.getMessage()
                );
                alert.showAndWait();
            });
            e.printStackTrace();
        }
    }

    private void createView() {
        root = new BorderPane();
        root.setPadding(new Insets(20));

        Label headerLabel = new Label("AVAL AI Reconciliation Engine");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        HBox headerBox = new HBox(headerLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(0, 0, 20, 0));
        root.setTop(headerBox);

        VBox centerBox = new VBox(15);

        // Ledger Selection
        HBox ledgerBox = new HBox(10);
        ledgerBox.setAlignment(Pos.CENTER_LEFT);
        Label ledgerLabel = new Label("Internal Ledger (Excel):");
        ledgerLabel.setPrefWidth(150);
        TextField ledgerPathField = new TextField();
        ledgerPathField.setPrefWidth(400);
        ledgerPathField.setEditable(false);
        Button browseLedgerBtn = new Button("Browse...");
        browseLedgerBtn.setOnAction(e -> {
            File file = openFileChooser("Select Internal Ledger", "*.xlsx");
            if (file != null) {
                ledgerPath = file.getAbsolutePath();
                ledgerPathField.setText(ledgerPath);
                checkReadyToRun();
            }
        });
        ledgerBox
            .getChildren()
            .addAll(ledgerLabel, ledgerPathField, browseLedgerBtn);

        // Bank Statement Selection
        HBox bankBox = new HBox(10);
        bankBox.setAlignment(Pos.CENTER_LEFT);
        Label bankLabel = new Label("Bank Statement (PDF):");
        bankLabel.setPrefWidth(150);
        TextField bankPathField = new TextField();
        bankPathField.setPrefWidth(400);
        bankPathField.setEditable(false);
        Button browseBankBtn = new Button("Browse...");
        browseBankBtn.setOnAction(e -> {
            File file = openFileChooser("Select Bank Statement", "*.pdf");
            if (file != null) {
                bankPath = file.getAbsolutePath();
                bankPathField.setText(bankPath);
                checkReadyToRun();
            }
        });
        bankBox.getChildren().addAll(bankLabel, bankPathField, browseBankBtn);

        // Action Buttons
        runReconciliationBtn = new Button("Run AI Reconciliation");
        runReconciliationBtn.setDisable(true);
        runReconciliationBtn.setStyle(
            "-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;"
        );
        runReconciliationBtn.setOnAction(e -> executePipeline());

        // Hypothesis Table
        setupHypothesisTable();

        // Manual Override Section
        VBox manualSection = setupManualOverrideSection();

        // Logs
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(100);

        centerBox
            .getChildren()
            .addAll(
                ledgerBox,
                bankBox,
                runReconciliationBtn,
                new Label("AI Suggested Matches:"),
                hypothesisTable,
                setupApprovalButtons(),
                new Label("Manual Reconciliation:"),
                manualSection,
                new Label("Execution Logs:"),
                logArea
            );
        root.setCenter(new ScrollPane(centerBox));
    }

    private void setupHypothesisTable() {
        hypothesisTable = new TableView<>(hypothesisData);
        hypothesisTable.setPrefHeight(200);

        TableColumn<MatchHypothesis, String> lDateCol = new TableColumn<>(
            "Ledger Date"
        );
        lDateCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData
                    .getValue()
                    .getLedgerTransaction()
                    .getValueDate()
                    .toString()
            )
        );

        TableColumn<MatchHypothesis, String> lAmtCol = new TableColumn<>(
            "Ledger Amount"
        );
        lAmtCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData
                    .getValue()
                    .getLedgerTransaction()
                    .getAmount()
                    .toString()
            )
        );

        TableColumn<MatchHypothesis, String> bDateCol = new TableColumn<>(
            "Bank Date"
        );
        bDateCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData
                    .getValue()
                    .getBankTransaction()
                    .getValueDate()
                    .toString()
            )
        );

        TableColumn<MatchHypothesis, String> bAmtCol = new TableColumn<>(
            "Bank Amount"
        );
        bAmtCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData.getValue().getBankTransaction().getAmount().toString()
            )
        );

        TableColumn<MatchHypothesis, String> typeCol = new TableColumn<>(
            "Match Type"
        );
        typeCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getMatchType().name())
        );

        TableColumn<MatchHypothesis, String> confCol = new TableColumn<>(
            "Confidence"
        );
        confCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                String.format("%.2f", cellData.getValue().getConfidenceScore())
            )
        );

        hypothesisTable
            .getColumns()
            .addAll(lDateCol, lAmtCol, bDateCol, bAmtCol, typeCol, confCol);

        // ISSUE-15: Highlight rows exceeding the auto-confirm threshold (0.95)
        hypothesisTable.setRowFactory(tv ->
            new TableRow<MatchHypothesis>() {
                @Override
                protected void updateItem(MatchHypothesis item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setStyle("");
                    } else if (item.getConfidenceScore() >= 0.95) {
                        setStyle("-fx-background-color: #d1e7dd;"); // Light green
                    } else {
                        setStyle("");
                    }
                }
            }
        );
    }

    private HBox setupApprovalButtons() {
        Button approveBtn = new Button("Approve Selected");
        approveBtn.setOnAction(e -> {
            MatchHypothesis selected = hypothesisTable
                .getSelectionModel()
                .getSelectedItem();
            if (selected != null) {
                reconciliationService.confirmHypothesis(selected, currentUser);
                hypothesisData.remove(selected);
                log(
                    "Approved match: " +
                        selected.getLedgerTransaction().getNarrative()
                );
            }
        });

        Button rejectBtn = new Button("Reject Selected");
        rejectBtn.setOnAction(e -> {
            MatchHypothesis selected = hypothesisTable
                .getSelectionModel()
                .getSelectedItem();
            if (selected != null) {
                reconciliationService.rejectHypothesis(selected, currentUser);
                hypothesisData.remove(selected);
                log(
                    "Rejected match: " +
                        selected.getLedgerTransaction().getNarrative()
                );
            }
        });

        HBox box = new HBox(10, approveBtn, rejectBtn);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private VBox setupManualOverrideSection() {
        HBox listsBox = new HBox(10);
        VBox ledgerBox = new VBox(
            5,
            new Label("Unmatched Ledger"),
            unmatchedLedgerList
        );
        VBox bankBox = new VBox(
            5,
            new Label("Unmatched Bank"),
            unmatchedBankList
        );
        listsBox.getChildren().addAll(ledgerBox, bankBox);
        HBox.setHgrow(ledgerBox, Priority.ALWAYS);
        HBox.setHgrow(bankBox, Priority.ALWAYS);

        Button forceMatchBtn = new Button("Force Manual Match");
        forceMatchBtn.setOnAction(e -> {
            StandardizedTransaction lTx = unmatchedLedgerList
                .getSelectionModel()
                .getSelectedItem();
            StandardizedTransaction bTx = unmatchedBankList
                .getSelectionModel()
                .getSelectedItem();
            if (lTx != null && bTx != null) {
                reconciliationService.forceReconcile(
                    lTx,
                    bTx,
                    currentUser,
                    "Manual override by user"
                );
                unmatchedLedgerList.getItems().remove(lTx);
                unmatchedBankList.getItems().remove(bTx);
                log("Manually matched transactions.");
            }
        });

        VBox box = new VBox(10, listsBox, forceMatchBtn);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void executePipeline() {
        if (!validateInputs()) return;

        runReconciliationBtn.setDisable(true);
        logArea.clear();
        hypothesisData.clear();
        unmatchedLedgerList.getItems().clear();
        unmatchedBankList.getItems().clear();
        log("Starting AVAL Reconciliation Pipeline...");

        Thread pipelineThread = new Thread(() -> {
            try {
                // 0. UC1 - Create Client Profile and Workspace
                log("0. Initializing Workspace (UC1)...");
                ClientOrganization client = new ClientOrganization(
                    UUID.randomUUID(),
                    "BrightLine Retail",
                    "Contact: info@brightline.com"
                );
                dataStore.saveClientOrganization(client);

                ReconciliationWorkspace workspace = new ReconciliationWorkspace(
                    UUID.randomUUID(),
                    client,
                    null
                );
                dataStore.saveReconciliationWorkspace(workspace);
                log("   -> Created profile for: " + client.getName());

                log("1. Ingesting Data...");
                FinancialDataset ledgerDataset = ingestionService.ingestFile(
                    ledgerPath,
                    DataSourceType.INTERNAL_EXCEL
                );
                FinancialDataset bankDataset = ingestionService.ingestFile(
                    bankPath,
                    DataSourceType.EXTERNAL_PDF
                );

                // Associate datasets with workspace
                dataStore.saveFinancialDataset(ledgerDataset);
                dataStore.saveFinancialDataset(bankDataset);

                log("2. Standardizing Schema...");
                List<StandardizedTransaction> stdLedger =
                    ingestionService.standardize(ledgerDataset);
                List<StandardizedTransaction> stdBank =
                    ingestionService.standardize(bankDataset);

                log("3. Running AI Matching...");
                List<MatchHypothesis> hypotheses =
                    reconciliationService.runMatching(null, stdLedger, stdBank);

                Platform.runLater(() -> {
                    hypothesisData.addAll(hypotheses);

                    // Identify unmatched for manual override
                    List<StandardizedTransaction> matchedLedger = hypotheses
                        .stream()
                        .map(MatchHypothesis::getLedgerTransaction)
                        .collect(Collectors.toList());
                    List<StandardizedTransaction> matchedBank = hypotheses
                        .stream()
                        .map(MatchHypothesis::getBankTransaction)
                        .collect(Collectors.toList());

                    unmatchedLedgerList
                        .getItems()
                        .addAll(
                            stdLedger
                                .stream()
                                .filter(t -> !matchedLedger.contains(t))
                                .collect(Collectors.toList())
                        );
                    unmatchedBankList
                        .getItems()
                        .addAll(
                            stdBank
                                .stream()
                                .filter(t -> !matchedBank.contains(t))
                                .collect(Collectors.toList())
                        );

                    // UC10 Anomaly Detection
                    log("4. Identifying Anomalies...");
                    List<String> anomalies = anomalyEngine.identifyAnomalies(
                        unmatchedLedgerList.getItems(),
                        unmatchedBankList.getItems()
                    );
                    anomalies.forEach(this::log);
                });

                log("PIPELINE PROCESSING COMPLETE. Pending Human Review.");
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> runReconciliationBtn.setDisable(false));
            }
        });
        pipelineThread.setDaemon(true);
        pipelineThread.start();
    }

    public Region getView() {
        return root;
    }

    private void checkReadyToRun() {
        if (
            ledgerPath != null &&
            bankPath != null &&
            !ledgerPath.isEmpty() &&
            !bankPath.isEmpty()
        ) {
            runReconciliationBtn.setDisable(false);
        }
    }

    private File openFileChooser(String title, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser
            .getExtensionFilters()
            .add(new FileChooser.ExtensionFilter("Supported Files", extension));
        File initialDir = new File("data/scenario_01_retail_ecommerce");
        if (initialDir.exists()) fileChooser.setInitialDirectory(initialDir);
        return fileChooser.showOpenDialog(root.getScene().getWindow());
    }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private boolean validateInputs() {
        if (ledgerPath == null || ledgerPath.isEmpty()) {
            showValidationError(
                "Input Error",
                "Internal Ledger file must be selected."
            );
            return false;
        }
        if (bankPath == null || bankPath.isEmpty()) {
            showValidationError(
                "Input Error",
                "Bank Statement file must be selected."
            );
            return false;
        }
        File lFile = new File(ledgerPath);
        File bFile = new File(bankPath);
        if (!lFile.exists() || !bFile.exists()) {
            showValidationError(
                "File Error",
                "One or more selected files do not exist on disk."
            );
            return false;
        }
        return true;
    }

    private void showValidationError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
