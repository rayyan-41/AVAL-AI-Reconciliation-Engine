package aval.ui.controller;

import aval.common.enums.WorkspaceStatus;
import aval.domain.ai.MatchHypothesis;
import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import aval.persistence.DataStore;
import aval.ui.MainUIContext;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

public class WorkspaceController implements IWorkspaceController {

    //-------------- Attributes ----------------------//
    @FXML
    private Label wsCompanyName;

    @FXML
    private Label wsCompanyId;

    @FXML
    private ToggleButton tabFinance;

    @FXML
    private ToggleButton tabRecon;

    @FXML
    private ToggleButton tabManual;

    @FXML
    private ToggleButton tabReport;

    @FXML
    private StackPane contentPane;

    private Node financeView;
    private Node reconView;
    private Node manualView;
    private Node reportView;
    private FinanceController financeController;
    private ManualCheckController manualCheckController;
    private ReportController reportController;
    private Node activeView;

    public void initialize() {
        MainUIContext.getInstance().setWorkspaceController(this);
        ClientOrganization client =
            MainUIContext.getInstance().getActiveClient();
        if (client != null) {
            wsCompanyName.setText(client.getName());
            wsCompanyId.setText(
                client.getOrgId() +
                    " · " +
                    getIndustryFromName(client.getName())
            );
        } else {
            wsCompanyName.setText("Unknown Company");
            wsCompanyId.setText("Unknown ID");
        }

        // Load all sub-views into StackPane
        loadFinanceView();
        reconView = loadFxml("Recon.fxml");
        loadManualView();
        loadReportView();

        if (financeView != null) contentPane.getChildren().add(financeView);
        if (reconView != null) contentPane.getChildren().add(reconView);
        if (manualView != null) contentPane.getChildren().add(manualView);
        if (reportView != null) contentPane.getChildren().add(reportView);

        contentPane.getChildren().forEach(n -> {
            n.setVisible(false);
            n.setManaged(false);
            n.setOpacity(1.0);
        });

        // ToggleGroup ensures only one tab is active
        ToggleGroup tg = new ToggleGroup();
        tabFinance.setToggleGroup(tg);
        tabRecon.setToggleGroup(tg);
        tabManual.setToggleGroup(tg);
        tabReport.setToggleGroup(tg);
        tabFinance.setSelected(true);
        showFinance();
    }

    private String getIndustryFromName(String name) {
        if (name.contains("Retail")) return "Retail & eCommerce";
        if (name.contains("Logistics")) return "Transportation & Logistics";
        if (name.contains("Trust")) return "Financial Services";
        if (name.contains("Estates")) return "Real Estate";
        return "Enterprise Holding";
    }

    private Node loadFxml(String fxml) {
        try {
            return FXMLLoader.load(
                getClass().getResource("/aval/ui/views/" + fxml)
            );
        } catch (Exception e) {
            System.err.println("Could not load sub-view: " + fxml);
            e.printStackTrace();
            return null;
        }
    }

    private void loadFinanceView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/Finance.fxml")
            );
            financeView = loader.load();
            financeController = loader.getController();
        } catch (Exception e) {
            System.err.println("Could not load sub-view: Finance.fxml");
            e.printStackTrace();
            financeView = null;
            financeController = null;
        }
    }

    private void loadManualView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/ManualCheck.fxml")
            );
            manualView = loader.load();
            manualCheckController = loader.getController();
            if (manualCheckController != null) {
                manualCheckController.setWorkspaceController(this);
            }
        } catch (Exception e) {
            System.err.println("Could not load sub-view: ManualCheck.fxml");
            e.printStackTrace();
            manualView = null;
            manualCheckController = null;
        }
    }

    private void loadReportView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/Report.fxml")
            );
            reportView = loader.load();
            reportController = loader.getController();
        } catch (Exception e) {
            System.err.println("Could not load sub-view: Report.fxml");
            e.printStackTrace();
            reportView = null;
            reportController = null;
        }
    }

    public void unlockManualCheck() {
        tabManual.setDisable(false);
        showManual();
    }

    public void unlockReport() {
        tabReport.setDisable(false);
        showReport();
    }

    public void notifyReconciliationCompleted() {
        if (financeController != null) {
            financeController.refreshStats();
        }
    }

    @FXML
    public void showFinance() {
        if (financeController != null) {
            financeController.refreshStats();
        }
        if (financeView != null) {
            showView(financeView, () -> {
                if (financeController != null) {
                    financeController.playEntranceAnimations();
                }
            });
        }
        tabFinance.setSelected(true);
    }

    @FXML
    public void showRecon() {
        if (reconView != null) showView(reconView, null);
        tabRecon.setSelected(true);
    }

    @FXML
    public void showManual() {
        if (manualCheckController != null) {
            List<MatchHypothesis> hypotheses = MainUIContext.getInstance()
                .getPendingHypotheses();
            manualCheckController.setItems(hypotheses);
            manualCheckController.loadAnomalies();
            manualCheckController.loadUnmatchedTransactions();
        }
        if (manualView != null) showView(manualView, null);
        tabManual.setSelected(true);
    }

    @FXML
    public void showReport() {
        if (reportController != null) {
            List<MatchHypothesis> hypotheses = MainUIContext.getInstance()
                .getAllHypotheses();
            if (hypotheses == null) {
                hypotheses = MainUIContext.getInstance().getPendingHypotheses();
            }
            if (hypotheses != null) {
                reportController.populateReport(hypotheses);
            }
        }
        if (reportView != null) {
            showView(reportView, () -> {
                if (reportController != null) {
                    reportController.playEntranceAnimations();
                }
            });
        }
        tabReport.setSelected(true);
    }

    private void showView(Node view) {
        showView(view, null);
    }

    private void showView(Node view, Runnable onShown) {
        if (view == null) {
            return;
        }
        if (activeView == view) {
            if (onShown != null) {
                onShown.run();
            }
            return;
        }

        if (activeView == null) {
            contentPane
                .getChildren()
                .forEach(n -> {
                    boolean isTarget = n == view;
                    n.setVisible(isTarget);
                    n.setManaged(isTarget);
                    n.setOpacity(1.0);
                });
            activeView = view;
            if (onShown != null) {
                onShown.run();
            }
            return;
        }

        Node previous = activeView;
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), previous);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            previous.setVisible(false);
            previous.setManaged(false);
            previous.setOpacity(1.0);

            view.setVisible(true);
            view.setManaged(true);
            view.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), view);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setOnFinished(e2 -> {
                activeView = view;
                if (onShown != null) {
                    onShown.run();
                }
            });
            fadeIn.play();
        });
        fadeOut.play();
    }

    @FXML
    void handleExit() {
        MainUIContext ctx = MainUIContext.getInstance();
        ReconciliationWorkspace workspace = ctx.getActiveWorkspace();
        DataStore dataStore = ctx.getDataStore();
        if (workspace != null && dataStore != null) {
            Task<Void> closeTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    dataStore.updateWorkspaceStatus(
                        workspace.getWorkspaceId(),
                        WorkspaceStatus.COMPLETED
                    );
                    return null;
                }
            };
            Thread closeThread = new Thread(closeTask);
            closeThread.setDaemon(true);
            closeThread.start();
        }

        ctx.setActiveWorkspace(null);
        ctx.setAllHypotheses(null);
        ctx.setStandardizedLedgerTransactions(null);
        ctx.setStandardizedBankTransactions(null);
        ctx.setUnmatchedLedger(null);
        ctx.setUnmatchedBank(null);
        ctx.setAnomalies(null);
        ctx.setPendingHypotheses(null);
        ctx.setReconciledRecords(new ArrayList<>());
        try {
            Stage stage = (Stage) wsCompanyName.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/Registry.fxml")
            );
            Parent root = loader.load();
            Scene newScene = new Scene(root);
            newScene
                .getStylesheets()
                .addAll(wsCompanyName.getScene().getStylesheets());
            stage.setScene(newScene);
            ctx.setActiveClient(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
