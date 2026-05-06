package aval.ui;

import aval.domain.SystemUser;
import aval.domain.ai.Anomaly;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import aval.engine.AnomalyDetectionEngine;
import aval.persistence.DataStore;
import aval.service.EmailService;
import aval.service.IngestionService;
import aval.service.ReconciliationService;
import aval.service.ReportService;
import aval.ui.controller.IWorkspaceController;
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
    private static final MainUIContext instance = new MainUIContext();
    private final BooleanProperty darkModeActive = new SimpleBooleanProperty(
        false
    );
    private ClientOrganization activeClient;
    private volatile List<MatchHypothesis> pendingHypotheses;
    private volatile IWorkspaceController workspaceController;
    private volatile SystemUser currentUser;
    private volatile ReconciliationWorkspace activeWorkspace;
    private IngestionService ingestionService;
    private ReconciliationService reconciliationService;
    private ReportService reportService;
    private EmailService emailService;
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
    private volatile List<Anomaly> anomalies;

    //Constructor
    private MainUIContext() {
        // Private constructor for Singleton
    }

    //---------- Methods ------------//

    //Singleton Accessor
    public static MainUIContext getInstance() {
        return instance;
    }

    public synchronized void clearSession() {
        this.activeWorkspace = null;
        this.standardizedLedgerTransactions = null;
        this.standardizedBankTransactions = null;
        this.pendingHypotheses = null;
        this.allHypotheses = null;
        this.reconciledRecords.clear();
        this.workspaceController = null;
        this.unmatchedLedger = null;
        this.unmatchedBank = null;
        this.anomalies = null;
    }

    private volatile boolean vectorizationAvailable = true;

    //Getters
    public BooleanProperty darkModeActiveProperty() { return darkModeActive; }
    public boolean isDarkModeActive() { return darkModeActive.get(); }
    public ClientOrganization getActiveClient() { return activeClient; }
    public List<MatchHypothesis> getPendingHypotheses() { return pendingHypotheses != null ? pendingHypotheses : Collections.emptyList(); }
    public IWorkspaceController getWorkspaceController() { return workspaceController; }
    public SystemUser getCurrentUser() { return currentUser; }
    public ReconciliationWorkspace getActiveWorkspace() { return activeWorkspace; }
    public IngestionService getIngestionService() { return ingestionService; }
    public ReconciliationService getReconciliationService() { return reconciliationService; }
    public ReportService getReportService() { return reportService; }
    public EmailService getEmailService() { return emailService; }
    public AnomalyDetectionEngine getAnomalyDetectionEngine() { return anomalyDetectionEngine; }
    public DataStore getDataStore() { return dataStore; }
    public List<StandardizedTransaction> getStandardizedLedgerTransactions() { return standardizedLedgerTransactions != null ? standardizedLedgerTransactions : Collections.emptyList(); }
    public List<StandardizedTransaction> getStandardizedBankTransactions() { return standardizedBankTransactions != null ? standardizedBankTransactions : Collections.emptyList(); }
    public List<MatchHypothesis> getAllHypotheses() { return allHypotheses != null ? allHypotheses : Collections.emptyList(); }
    public List<ReconciliationRecord> getReconciledRecords() {
        synchronized (reconciledRecords) {
            return new ArrayList<>(reconciledRecords);
        }
    }
    public List<StandardizedTransaction> getUnmatchedLedger() { return unmatchedLedger != null ? unmatchedLedger : Collections.emptyList(); }
    public List<StandardizedTransaction> getUnmatchedBank() { return unmatchedBank != null ? unmatchedBank : Collections.emptyList(); }
    public List<Anomaly> getAnomalies() { return anomalies != null ? anomalies : Collections.emptyList(); }
    public boolean isVectorizationAvailable() { return vectorizationAvailable; }

    //Setters
    public void setDarkModeActive(boolean active) { darkModeActive.set(active); }
    public void setActiveClient(ClientOrganization activeClient) { this.activeClient = activeClient; }
    public void setPendingHypotheses(List<MatchHypothesis> pendingHypotheses) { this.pendingHypotheses = pendingHypotheses; }
public void setWorkspaceController(IWorkspaceController workspaceController) { this.workspaceController = workspaceController; }
    public void setCurrentUser(SystemUser currentUser) { this.currentUser = currentUser; }
    public void setActiveWorkspace(ReconciliationWorkspace activeWorkspace) { this.activeWorkspace = activeWorkspace; }
    public void setIngestionService(IngestionService ingestionService) { this.ingestionService = ingestionService; }
    public void setReconciliationService(ReconciliationService reconciliationService) { this.reconciliationService = reconciliationService; }
    public void setReportService(ReportService reportService) { this.reportService = reportService; }
    public void setAnomalyDetectionEngine(AnomalyDetectionEngine anomalyDetectionEngine) { this.anomalyDetectionEngine = anomalyDetectionEngine; }
    public void setEmailService(EmailService emailService) { this.emailService = emailService; }
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
    public void setAnomalies(List<Anomaly> anomalies) { this.anomalies = anomalies; }
    public void setVectorizationAvailable(boolean available) { this.vectorizationAvailable = available; }

    //Helper Methods
    public void addReconciledRecord(ReconciliationRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record cannot be null");
        }
        reconciledRecords.add(record);
    }

    /**
     * Applies the light theme to the provided scene.
     * @param scene The JavaFX scene to style.
     */
    public void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        String path = "/aval/ui/styles/fintech-light.css";
        var resource = getClass().getResource(path);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        } else {
            System.err.println("Theme resource not found: " + path);
        }
    }
}
