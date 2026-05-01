package aval;

import aval.engine.AnomalyDetectionEngine;
import aval.engine.LangChain4jVectorizationEngine;
import aval.engine.MatchingEngine;
import aval.engine.RuleBasedMatchingEngine;
import aval.engine.VectorizationEngine;
import aval.persistence.DataStore;
import aval.service.IngestionService;
import aval.service.ReconciliationService;
import aval.service.ReportService;
import aval.ui.MainUIContext;
import java.sql.Connection;
import java.sql.DriverManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Main entry point for the AVAL AI Reconciliation Engine.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println(
                "[BOOT] Starting AVAL AI Reconciliation Engine..."
            );

            Platform.setImplicitExit(true);

            // Load fonts before any Scene is created
            Font.loadFont(
                getClass().getResourceAsStream("/aval/ui/fonts/Jura-Bold.ttf"),
                72
            );
            Font.loadFont(
                getClass().getResourceAsStream(
                    "/aval/ui/fonts/Jura-SemiBold.ttf"
                ),
                18
            );

            DataStore dataStore = null;
            IngestionService ingestionService = null;
            ReconciliationService reconciliationService = null;

            try {
                Connection connection = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/aval_db",
                    "aval_user",
                    "aval_password"
                );
                dataStore = new DataStore(connection);
                VectorizationEngine vectorizationEngine =
                    new LangChain4jVectorizationEngine(
                        "http://localhost:11434",
                        "nomic-embed-text"
                    );
                MatchingEngine matchingEngine = new RuleBasedMatchingEngine();

                ingestionService = new IngestionService(
                    dataStore,
                    vectorizationEngine
                );
                reconciliationService =
                    new ReconciliationService(
                        vectorizationEngine,
                        matchingEngine,
                        dataStore
                    );
            } catch (Exception e) {
                System.err.println(
                    "[BOOT] Backend unavailable. Running UI in fallback mode."
                );
                System.err.println("[BOOT] " + e.getMessage());
            }

            ReportService reportService = new ReportService();
            AnomalyDetectionEngine anomalyDetectionEngine =
                new AnomalyDetectionEngine();

            MainUIContext context = MainUIContext.getInstance();
            context.setDataStore(dataStore);
            context.setIngestionService(ingestionService);
            context.setReconciliationService(reconciliationService);
            context.setReportService(reportService);
            context.setAnomalyDetectionEngine(anomalyDetectionEngine);

            System.out.println("[BOOT] Displaying Splash Screen...");
            FXMLLoader splashLoader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/Splash.fxml")
            );
            Parent splashRoot = splashLoader.load();
            Scene splashScene = new Scene(splashRoot, 800, 500);

            // Add the light theme css
            splashScene
                .getStylesheets()
                .add(
                    getClass()
                        .getResource("/aval/ui/styles/fintech-light.css")
                        .toExternalForm()
                );

            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setScene(splashScene);
            primaryStage.show();
        } catch (Exception e) {
            System.err.println("[FATAL ERROR] Boot failed:");
            e.printStackTrace();
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
