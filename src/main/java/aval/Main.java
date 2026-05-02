package aval;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Main entry point for the AVAL AI Reconciliation Engine.
 */
public class Main extends Application {

    private static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (
            InputStream classpathStream = Main.class.getResourceAsStream(
                "/aval.properties"
            )
        ) {
            if (classpathStream != null) {
                props.load(classpathStream);
                return props;
            }
        }
        File externalFile = new File("aval.properties");
        if (externalFile.exists()) {
            try (InputStream fileStream = new FileInputStream(externalFile)) {
                props.load(fileStream);
                return props;
            }
        }
        throw new IOException(
            "aval.properties not found. Copy aval.properties.template to aval.properties and fill in your values."
        );
    }

    private static String requireProperty(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required configuration key: " + key
            );
        }
        return value;
    }

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
            HikariDataSource hikariDataSource = null;

            try {
                Properties appConfig = loadConfig();
                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setJdbcUrl(requireProperty(appConfig, "db.url"));
                hikariConfig.setUsername(
                    requireProperty(appConfig, "db.username")
                );
                hikariConfig.setPassword(
                    requireProperty(appConfig, "db.password")
                );
                hikariConfig.setMaximumPoolSize(
                    Integer.parseInt(appConfig.getProperty("db.pool.maxSize", "10"))
                );
                hikariConfig.setMinimumIdle(2);
                hikariConfig.setConnectionTimeout(30000);
                hikariConfig.setIdleTimeout(600000);

                hikariDataSource = new HikariDataSource(hikariConfig);
                dataStore = new DataStore(hikariDataSource);
                VectorizationEngine vectorizationEngine =
                    new LangChain4jVectorizationEngine(
                        requireProperty(appConfig, "ollama.baseUrl"),
                        requireProperty(appConfig, "ollama.model")
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
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Database Unavailable");
                alert.setHeaderText("Cannot connect to AVAL database.");
                alert.setContentText(
                    "Check aval.properties configuration and ensure dependencies are running.\n\nError: " +
                    e.getMessage()
                );
                alert.showAndWait();
                Platform.exit();
                return;
            }

            if (hikariDataSource != null) {
                HikariDataSource finalDataSource = hikariDataSource;
                Runtime.getRuntime()
                    .addShutdownHook(new Thread(finalDataSource::close));
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
