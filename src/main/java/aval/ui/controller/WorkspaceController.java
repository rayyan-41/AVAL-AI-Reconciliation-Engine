package aval.ui.controller;

import aval.domain.core.ClientOrganization;
import aval.ui.MainUIContext;
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

public class WorkspaceController {

    @FXML private Label wsCompanyName;
    @FXML private Label wsCompanyId;
    @FXML private ToggleButton tabFinance;
    @FXML private ToggleButton tabRecon;
    @FXML private ToggleButton tabManual;
    @FXML private ToggleButton tabReport;
    @FXML private StackPane contentPane;

    private Node financeView;
    private Node reconView;
    private Node manualView;
    private Node reportView;

    public void initialize() {
        ClientOrganization client = MainUIContext.getInstance().getActiveClient();
        if (client != null) {
            wsCompanyName.setText(client.getName());
            wsCompanyId.setText(client.getOrgId() + " · " + getIndustryFromName(client.getName()));
        } else {
            wsCompanyName.setText("Unknown Company");
            wsCompanyId.setText("Unknown ID");
        }

        // Load all sub-views into StackPane
        financeView  = loadFxml("Finance.fxml");
        reconView    = loadFxml("Recon.fxml");
        manualView   = loadFxml("ManualCheck.fxml");
        reportView   = loadFxml("Report.fxml");

        if (financeView != null) contentPane.getChildren().add(financeView);
        if (reconView != null) contentPane.getChildren().add(reconView);
        if (manualView != null) contentPane.getChildren().add(manualView);
        if (reportView != null) contentPane.getChildren().add(reportView);

        if (financeView != null) showView(financeView);

        // ToggleGroup ensures only one tab is active
        ToggleGroup tg = new ToggleGroup();
        tabFinance.setToggleGroup(tg);
        tabRecon.setToggleGroup(tg);
        tabManual.setToggleGroup(tg);
        tabReport.setToggleGroup(tg);
        tabFinance.setSelected(true);
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
            return FXMLLoader.load(getClass().getResource("/aval/ui/views/" + fxml));
        } catch (Exception e) {
            System.err.println("Could not load sub-view: " + fxml);
            e.printStackTrace();
            return null;
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

    @FXML public void showFinance() { if (financeView != null) showView(financeView); tabFinance.setSelected(true); }
    @FXML public void showRecon()   { if (reconView != null) showView(reconView); tabRecon.setSelected(true); }
    @FXML public void showManual()  { if (manualView != null) showView(manualView); tabManual.setSelected(true); }
    @FXML public void showReport()  { if (reportView != null) showView(reportView); tabReport.setSelected(true); }

    private void showView(Node view) {
        contentPane.getChildren().forEach(n -> n.setVisible(false));
        view.setVisible(true);
    }

    @FXML void handleExit() {
        MainUIContext.getInstance().setActiveClient(null);
        try {
            Stage stage = (Stage) wsCompanyName.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/aval/ui/views/Registry.fxml"));
            Parent root = loader.load();
            Scene newScene = new Scene(root);
            newScene.getStylesheets().addAll(wsCompanyName.getScene().getStylesheets());
            stage.setScene(newScene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
