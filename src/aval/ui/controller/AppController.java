//Dev: Aryan
//Use Cases: All navigation
package aval.ui.controller;

import aval.common.enums.DataSourceType;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.ingestion.FinancialDataset;
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
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class AppController {

    private BorderPane root;
    private TextArea logArea;
    private Button runReconciliationBtn;

    // Backend Services
    private DataStore dataStore;
    private IngestionService ingestionService;
    private ReconciliationService reconciliationService;
    private ReportService reportService;

    // State
    private String ledgerPath;
    private String bankPath;

    public AppController() {
        initializeBackend();
        createView();
    }

    private void initializeBackend() {
        try {
            // Note: For actual production, connection management should be handled better
            Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/aval_db",
                "aval_user",
                "aval_password"
            );
            dataStore = new DataStore(conn);

            LangChain4jVectorizationEngine vectorEngine =
                new LangChain4jVectorizationEngine(
                    "http://localhost:11434",
                    "nomic-embed-text"
                );
            ingestionService = new IngestionService(dataStore, vectorEngine);

            // For MVP UI we'll orchestrate the engines. We could use RuleBased or Semantic. We'll use Semantic for demonstration.
            SemanticMatchingEngine matchingEngine = new SemanticMatchingEngine(
                vectorEngine,
                dataStore
            );
            // Alternatively: RuleBasedMatchingEngine matchingEngine = new RuleBasedMatchingEngine();

            reconciliationService = new ReconciliationService(
                vectorEngine,
                matchingEngine,
                dataStore
            );
            reportService = new ReportService();
        } catch (Exception e) {
            System.err.println(
                "Failed to initialize database connection. Ensure Docker is running."
            );
            e.printStackTrace();
        }
    }

    private void createView() {
        root = new BorderPane();
        root.setPadding(new Insets(20));

        // Header
        Label headerLabel = new Label("AVAL AI Reconciliation Engine");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        HBox headerBox = new HBox(headerLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(0, 0, 20, 0));
        root.setTop(headerBox);

        // Center - File Selection and Logs
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

        // Optional: Pre-fill with data directory for convenience
        File defaultLedger = new File(
            "data/scenario_01_retail_ecommerce/company_ledger_brightline.xlsx"
        );
        if (defaultLedger.exists()) {
            ledgerPath = defaultLedger.getAbsolutePath();
            ledgerPathField.setText(ledgerPath);
        }
        File defaultBank = new File(
            "data/scenario_01_retail_ecommerce/bank_statement_pacific_trust.pdf"
        );
        if (defaultBank.exists()) {
            bankPath = defaultBank.getAbsolutePath();
            bankPathField.setText(bankPath);
        }

        // Action Buttons
        runReconciliationBtn = new Button("Run AI Reconciliation");
        runReconciliationBtn.setDisable(true);
        runReconciliationBtn.setStyle(
            "-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;"
        );
        runReconciliationBtn.setOnAction(e -> executePipeline());

        checkReadyToRun();

        // Logs
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        centerBox
            .getChildren()
            .addAll(
                ledgerBox,
                bankBox,
                runReconciliationBtn,
                new Label("Execution Logs:"),
                logArea
            );
        root.setCenter(centerBox);
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

    private void executePipeline() {
        runReconciliationBtn.setDisable(true);
        logArea.clear();
        log("Starting AVAL Reconciliation Pipeline...");

        // Run in background thread to keep UI responsive
        Thread pipelineThread = new Thread(() -> {
            try {
                // 1. Ingestion
                log("1. Ingesting Internal Ledger (Excel)...");
                FinancialDataset ledgerDataset = ingestionService.ingestFile(
                    ledgerPath,
                    DataSourceType.INTERNAL_EXCEL
                );
                log(
                    "   -> Extracted " +
                        ledgerDataset.getRawTransactions().size() +
                        " raw transactions."
                );

                log("2. Ingesting Bank Statement (PDF)...");
                FinancialDataset bankDataset = ingestionService.ingestFile(
                    bankPath,
                    DataSourceType.EXTERNAL_PDF
                );
                log(
                    "   -> Extracted " +
                        bankDataset.getRawTransactions().size() +
                        " raw transactions."
                );

                // 2. Standardization
                log("3. Standardizing Schema...");
                List<StandardizedTransaction> stdLedger =
                    ingestionService.standardize(ledgerDataset);
                List<StandardizedTransaction> stdBank =
                    ingestionService.standardize(bankDataset);
                log(
                    "   -> Standardized " +
                        stdLedger.size() +
                        " ledger entries and " +
                        stdBank.size() +
                        " bank entries."
                );

                // 3. Matching
                log(
                    "4. Running Semantic AI Matching Engine... (This may take a moment to query Ollama)"
                );
                List<MatchHypothesis> hypotheses =
                    reconciliationService.runMatching(null, stdLedger, stdBank);
                log(
                    "   -> Generated " +
                        hypotheses.size() +
                        " AI match hypotheses."
                );

                // 4. Auto-Approve matches for demonstration (UC7)
                log("5. Auto-approving high confidence matches...");
                SystemUser systemUser = new SystemUser(
                    UUID.randomUUID(),
                    "AVAL-AUTO",
                    aval.common.enums.UserRole.ADMIN
                );
                List<ReconciliationRecord> records = new ArrayList<>();
                for (MatchHypothesis h : hypotheses) {
                    if (h.getConfidenceScore() >= 0.8) {
                        records.add(
                            reconciliationService.confirmHypothesis(
                                h,
                                systemUser
                            )
                        );
                    }
                }
                log("   -> Approved " + records.size() + " records.");

                // 5. Reporting
                log("6. Generating CSV Report...");
                File outputReport = new File(
                    "data/scenario_01_retail_ecommerce/reconciliation_report_output.csv"
                );

                // Identify unmatched items
                List<StandardizedTransaction> unmatchedLedger = new ArrayList<>(
                    stdLedger
                );
                List<StandardizedTransaction> unmatchedBank = new ArrayList<>(
                    stdBank
                );

                for (ReconciliationRecord r : records) {
                    unmatchedLedger.remove(
                        r.getHypothesis().getLedgerTransaction()
                    );
                    unmatchedBank.remove(
                        r.getHypothesis().getBankTransaction()
                    );
                }

                reportService.generateReconciliationReport(
                    records,
                    unmatchedLedger,
                    unmatchedBank,
                    outputReport.getAbsolutePath()
                );
                log(
                    "   -> Report generated at: " +
                        outputReport.getAbsolutePath()
                );

                log(
                    "\n" +
                        reportService.generateSummary(
                            records,
                            unmatchedLedger,
                            unmatchedBank
                        )
                );
                log("PIPELINE COMPLETE.");
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
}
