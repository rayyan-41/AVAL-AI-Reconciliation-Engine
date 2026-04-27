//Dev: Aryan
//Use Cases: All navigation
package aval.ui.controller;

import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ReconciliationWorkspace;
import aval.engine.AnomalyDetectionEngine;
import aval.engine.LangChain4jVectorizationEngine;
import aval.engine.SemanticMatchingEngine;
import aval.persistence.DataStore;
import aval.service.IngestionService;
import aval.service.ReconciliationService;
import aval.service.ReportService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

//@desc:   Facade controller — single entry point that initialises all backend services,
//         owns shared observable state, and wires five dedicated UC controllers into a
//         tab-based window. Contains no business logic itself.
//@grasp:  Controller (Facade), Low Coupling
//@gof:    Facade
public class AppController {

    // ── Backend services ────────────────────────────────────────────────────
    private DataStore dataStore;
    private IngestionService ingestionService;
    private ReconciliationService reconciliationService;
    private ReportService reportService;
    private AnomalyDetectionEngine anomalyEngine;
    private SystemUser currentUser;

    // ── Shared observable state passed across sub-controllers ───────────────
    private final ObservableList<StandardizedTransaction> stdLedger =
        FXCollections.observableArrayList();
    private final ObservableList<StandardizedTransaction> stdBank =
        FXCollections.observableArrayList();
    private final ObservableList<StandardizedTransaction> unmatchedLedger =
        FXCollections.observableArrayList();
    private final ObservableList<StandardizedTransaction> unmatchedBank =
        FXCollections.observableArrayList();
    private final ObservableList<MatchHypothesis> hypotheses =
        FXCollections.observableArrayList();
    private final ObservableList<ReconciliationRecord> confirmedRecords =
        FXCollections.observableArrayList();
    private ReconciliationWorkspace currentWorkspace;

    // ── Root UI ─────────────────────────────────────────────────────────────
    private BorderPane root;
    private TextArea globalLog;

    public AppController() {
        initializeBackend();
        buildView();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Backend initialisation
    // ────────────────────────────────────────────────────────────────────────
    private void initializeBackend() {
        currentUser = new SystemUser(
            UUID.randomUUID(),
            "Aryan-Dev",
            aval.common.enums.UserRole.ADMIN
        );

        try {
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
            // Show an alert on the JavaFX thread once the toolkit is ready
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Database Connection Error");
                alert.setHeaderText("Could not connect to PostgreSQL");
                alert.setContentText(
                    "Ensure the Docker containers (aval-postgres, aval-ollama) are running.\n\n" +
                        e.getMessage()
                );
                alert.showAndWait();
            });
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  View construction — wires five dedicated controllers into a TabPane
    // ────────────────────────────────────────────────────────────────────────
    private void buildView() {
        root = new BorderPane();
        root.setPadding(new Insets(10));

        // ── Header ──
        Label appTitle = new Label("AVAL  AI Reconciliation Engine");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 22));
        Label userBadge = new Label(
            "User: " +
                currentUser.getUsername() +
                "   |   Role: " +
                currentUser.getRole()
        );
        userBadge.setStyle("-fx-text-fill: #777777; -fx-font-size: 12px;");
        HBox header = new HBox(20, appTitle, userBadge);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 8, 0));
        root.setTop(header);

        // ── Sub-controllers ──
        WorkspaceSetupController wsCtrl = new WorkspaceSetupController(
            dataStore,
            currentUser,
            ws -> {
                this.currentWorkspace = ws;
                log("UC1: Workspace created — ID: " + ws.getWorkspaceId());
            }
        );

        IngestionController ingCtrl = new IngestionController(
            ingestionService,
            stdLedger,
            stdBank,
            this::log,
            () ->
                currentWorkspace != null
                    ? currentWorkspace
                    : new aval.domain.core.ReconciliationWorkspace(
                          java.util.UUID.randomUUID(),
                          null,
                          null
                      )
        );

        ReconciliationDashboardController reconCtrl =
            new ReconciliationDashboardController(
                reconciliationService,
                currentUser,
                stdLedger,
                stdBank,
                unmatchedLedger,
                unmatchedBank,
                hypotheses,
                confirmedRecords,
                this::log,
                () ->
                    currentWorkspace != null
                        ? currentWorkspace
                        : new ReconciliationWorkspace(
                              UUID.randomUUID(),
                              null,
                              null
                          )
            );

        AnomalyReviewController anomalyCtrl = new AnomalyReviewController(
            anomalyEngine,
            unmatchedLedger,
            unmatchedBank,
            this::log
        );

        ReportController reportCtrl = new ReportController(
            reportService,
            confirmedRecords,
            unmatchedLedger,
            unmatchedBank,
            dataStore,
            this::log
        );

        // ── TabPane ──
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs
            .getTabs()
            .addAll(
                tab("1.  Workspace Setup  (UC1)", wsCtrl.getView()),
                tab("2.  Data Ingestion   (UC2 / 3 / 4)", ingCtrl.getView()),
                tab("3.  Reconciliation   (UC6 – 9)", reconCtrl.getView()),
                tab("4.  Anomaly Review   (UC10)", anomalyCtrl.getView()),
                tab("5.  Reports          (UC11 / 12)", reportCtrl.getView())
            );
        root.setCenter(tabs);

        // ── Global log at the bottom ──
        globalLog = new TextArea();
        globalLog.setEditable(false);
        globalLog.setPrefHeight(75);
        globalLog.setWrapText(true);
        Label logLabel = new Label("System Log:");
        logLabel.setStyle("-fx-font-weight: bold;");
        VBox bottom = new VBox(3, logLabel, globalLog);
        bottom.setPadding(new Insets(6, 0, 0, 0));
        root.setBottom(bottom);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title);
        t.setContent(content);
        return t;
    }

    private void log(String message) {
        Platform.runLater(() -> globalLog.appendText(message + "\n"));
    }

    public Region getView() {
        return root;
    }
}
