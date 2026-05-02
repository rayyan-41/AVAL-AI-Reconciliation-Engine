package aval.ui;

import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import aval.engine.AnomalyDetectionEngine;
import aval.persistence.DataStore;
import aval.service.IngestionService;
import aval.service.ReconciliationService;
import aval.service.ReportService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;

/**
 * Global context for the UI, managing application-wide state such as theme selection.
 */
public class MainUIContext {

    //-------------- Attributes ----------------------//
    private static MainUIContext instance;
    private final BooleanProperty darkModeActive = new SimpleBooleanProperty(
        false
    );
    private ClientOrganization activeClient;
    private volatile List<MatchHypothesis> pendingHypotheses;
    private Object workspaceController; // Using Object to avoid circular deps, cast later if needed.
    private volatile SystemUser currentUser;
    private volatile ReconciliationWorkspace activeWorkspace;
    private IngestionService ingestionService;
    private ReconciliationService reconciliationService;
    private ReportService reportService;
    private AnomalyDetectionEngine anomalyDetectionEngine;
    private DataStore dataStore;
    private volatile List<StandardizedTransaction> standardizedLedgerTransactions;
    private volatile List<StandardizedTransaction> standardizedBankTransactions;
    private volatile List<MatchHypothesis> allHypotheses;
    private final List<ReconciliationRecord> reconciledRecords = Collections.synchronizedList(
        new ArrayList<>()
    );
    private volatile List<StandardizedTransaction> unmatchedLedger;
    private volatile List<StandardizedTransaction> unmatchedBank;
    private volatile List<String> anomalies;

    //Constructor
    private MainUIContext() {
        // Private constructor for Singleton
    }

    //---------- Methods ------------//

    //Singleton Accessor
    public static MainUIContext getInstance() {
        if (instance == null) {
            instance = new MainUIContext();
        }
        return instance;
    }

    //Getters
    public BooleanProperty darkModeActiveProperty() { return darkModeActive; }
    public boolean isDarkModeActive() { return darkModeActive.get(); }
    public ClientOrganization getActiveClient() { return activeClient; }
    public List<MatchHypothesis> getPendingHypotheses() { return pendingHypotheses; }
    public Object getWorkspaceController() { return workspaceController; }
    public SystemUser getCurrentUser() { return currentUser; }
    public ReconciliationWorkspace getActiveWorkspace() { return activeWorkspace; }
    public IngestionService getIngestionService() { return ingestionService; }
    public ReconciliationService getReconciliationService() { return reconciliationService; }
    public ReportService getReportService() { return reportService; }
    public AnomalyDetectionEngine getAnomalyDetectionEngine() { return anomalyDetectionEngine; }
    public DataStore getDataStore() { return dataStore; }
    public List<StandardizedTransaction> getStandardizedLedgerTransactions() { return standardizedLedgerTransactions; }
    public List<StandardizedTransaction> getStandardizedBankTransactions() { return standardizedBankTransactions; }
    public List<MatchHypothesis> getAllHypotheses() { return allHypotheses; }
    public List<ReconciliationRecord> getReconciledRecords() {
        synchronized (reconciledRecords) {
            return new ArrayList<>(reconciledRecords);
        }
    }
    public List<StandardizedTransaction> getUnmatchedLedger() { return unmatchedLedger; }
    public List<StandardizedTransaction> getUnmatchedBank() { return unmatchedBank; }
    public List<String> getAnomalies() { return anomalies; }

    //Setters
    public void setDarkModeActive(boolean active) { darkModeActive.set(active); }
    public void setActiveClient(ClientOrganization activeClient) { this.activeClient = activeClient; }
    public void setPendingHypotheses(List<MatchHypothesis> pendingHypotheses) { this.pendingHypotheses = pendingHypotheses; }
    public void setWorkspaceController(Object workspaceController) { this.workspaceController = workspaceController; }
    public void setCurrentUser(SystemUser currentUser) { this.currentUser = currentUser; }
    public void setActiveWorkspace(ReconciliationWorkspace activeWorkspace) { this.activeWorkspace = activeWorkspace; }
    public void setIngestionService(IngestionService ingestionService) { this.ingestionService = ingestionService; }
    public void setReconciliationService(ReconciliationService reconciliationService) { this.reconciliationService = reconciliationService; }
    public void setReportService(ReportService reportService) { this.reportService = reportService; }
    public void setAnomalyDetectionEngine(AnomalyDetectionEngine anomalyDetectionEngine) { this.anomalyDetectionEngine = anomalyDetectionEngine; }
    public void setDataStore(DataStore dataStore) { this.dataStore = dataStore; }
    public void setStandardizedLedgerTransactions(List<StandardizedTransaction> standardizedLedgerTransactions) { this.standardizedLedgerTransactions = standardizedLedgerTransactions; }
    public void setStandardizedBankTransactions(List<StandardizedTransaction> standardizedBankTransactions) { this.standardizedBankTransactions = standardizedBankTransactions; }
    public void setAllHypotheses(List<MatchHypothesis> allHypotheses) { this.allHypotheses = allHypotheses; }
    public void setReconciledRecords(List<ReconciliationRecord> reconciledRecords) {
        synchronized (this.reconciledRecords) {
            this.reconciledRecords.clear();
            if (reconciledRecords != null) {
                this.reconciledRecords.addAll(reconciledRecords);
            }
        }
    }
    public void setUnmatchedLedger(List<StandardizedTransaction> unmatchedLedger) { this.unmatchedLedger = unmatchedLedger; }
    public void setUnmatchedBank(List<StandardizedTransaction> unmatchedBank) { this.unmatchedBank = unmatchedBank; }
    public void setAnomalies(List<String> anomalies) { this.anomalies = anomalies; }

    //Helper Methods
    public void addReconciledRecord(ReconciliationRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record cannot be null");
        }
        reconciledRecords.add(record);
    }

    /**
     * Applies the current theme to the provided scene.
     * @param scene The JavaFX scene to style.
     */
    public void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        String path = isDarkModeActive()
            ? "/aval/ui/styles/fintech-dark.css"
            : "/aval/ui/styles/fintech-light.css";
        var resource = getClass().getResource(path);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        } else {
            System.err.println("Theme resource not found: " + path);
        }
    }
}
