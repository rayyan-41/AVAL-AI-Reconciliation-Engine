package aval.ui.controller;

import aval.domain.ai.StandardizedTransaction;
import aval.ui.MainUIContext;
import aval.ui.util.MockUIProvider;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ReconController {

    @FXML
    private HBox actionBar;

    @FXML
    private Label periodLabel;

    @FXML
    private Button btnRecon;

    @FXML
    private Label reconHint;

    @FXML
    private HBox pipelineStrip;

    @FXML
    private HBox resultBar;

    @FXML
    private Label rbAuto;

    @FXML
    private Label rbManual;

    @FXML
    private Label bankStatus;

    @FXML
    private StackPane bankPanel;

    @FXML
    private VBox dropZonePane;

    @FXML
    private VBox ingestingPane;

    @FXML
    private VBox ingestedPane;

    @FXML
    private Label ingFilename;

    @FXML
    private ProgressBar ingProgress;

    @FXML
    private Label ingLabel;

    @FXML
    private TableView<StandardizedTransaction> ledgerTable;

    @FXML
    private TableColumn<StandardizedTransaction, String> lDateCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lRefCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lNarrCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lTypeCol;

    @FXML
    private TableColumn<StandardizedTransaction, String> lAmtCol;

    private static final String[] ING_STEPS = {
        "Parsing document structure...",
        "Extracting transaction table...",
        "Normalizing date and amount fields...",
        "Running schema standardization...",
        "Building semantic index...",
        "Ingestion complete.",
    };

    private static final List<String> pipeSteps = Arrays.asList(
        "Load",
        "Clean",
        "Index",
        "Rule Match",
        "AI Vector",
        "Score",
        "Done"
    );

    public void initialize() {
        periodLabel.setText("Reconciliation Period: Sep 2024");

        setupDropZone();
        setupLedgerTable();
        showBankState("dropzone");
    }

    private void setupLedgerTable() {
        lDateCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getValueDate().toString())
        );
        lRefCol.setCellValueFactory(cd ->
            new SimpleStringProperty(
                cd
                    .getValue()
                    .getTransactionId()
                    .toString()
                    .substring(0, 8)
                    .toUpperCase()
            )
        );
        lNarrCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getNarrative())
        );
        lTypeCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getType().name())
        );

        lAmtCol.setCellValueFactory(cd -> {
            boolean isDebit =
                cd.getValue().getType() ==
                aval.common.enums.TransactionType.DEBIT;
            String prefix = isDebit ? "-" : "+";
            return new SimpleStringProperty(
                prefix + "$" + cd.getValue().getAmount().toString()
            );
        });

        lAmtCol.setCellFactory(col ->
            new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label l = new Label(item);
                    l.setStyle(
                        "-fx-font-family: 'Consolas'; " +
                            (item.startsWith("-")
                                ? "-fx-text-fill: -fx-red-text;"
                                : "-fx-text-fill: -fx-text-primary;")
                    );
                    setGraphic(l);
                    setText(null);
                }
            }
        );

        ObservableList<StandardizedTransaction> txns =
            FXCollections.observableArrayList(
                MockUIProvider.getMockTransactions(10)
            );
        ledgerTable.setItems(txns);
    }

    private void setupDropZone() {
        dropZonePane.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });

        dropZonePane.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    startIngestion(files.get(0));
                }
            }
            e.setDropCompleted(true);
            e.consume();
        });
    }

    @FXML
    void handleBrowse() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Bank Statement");
        fc
            .getExtensionFilters()
            .add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        Stage stage = (Stage) dropZonePane.getScene().getWindow();
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            startIngestion(f);
        }
    }

    private void startIngestion(File f) {
        showBankState("ingesting");
        ingFilename.setText(f.getName());
        bankStatus.setText("Processing");
        bankStatus.getStyleClass().setAll("badge", "badge-pending");

        SequentialTransition seq = new SequentialTransition();
        for (int i = 0; i < ING_STEPS.length; i++) {
            final int idx = i;
            PauseTransition pt = new PauseTransition(Duration.millis(460));
            pt.setOnFinished(e -> {
                ingProgress.setProgress((double) (idx + 1) / ING_STEPS.length);
                ingLabel.setText(ING_STEPS[idx]);
            });
            seq.getChildren().add(pt);
        }
        seq.setOnFinished(e -> finishIngestion(f));
        seq.play();
    }

    private void finishIngestion(File f) {
        showBankState("ingested");
        bankStatus.setText("Ingested");
        bankStatus.getStyleClass().setAll("badge", "badge-active");
        btnRecon.setDisable(false);
        reconHint.setText("Both datasets loaded. Ready to reconcile.");
    }

    @FXML
    void resetDropZone() {
        showBankState("dropzone");
        bankStatus.setText("Awaiting File");
        bankStatus.getStyleClass().setAll("badge", "badge-pending");
        btnRecon.setDisable(true);
        reconHint.setText("Upload Bank Statement to proceed.");
    }

    private void showBankState(String state) {
        dropZonePane.setVisible("dropzone".equals(state));
        dropZonePane.setManaged("dropzone".equals(state));

        ingestingPane.setVisible("ingesting".equals(state));
        ingestingPane.setManaged("ingesting".equals(state));

        ingestedPane.setVisible("ingested".equals(state));
        ingestedPane.setManaged("ingested".equals(state));
    }

    @FXML
    void handlePerformRecon() {
        actionBar.setVisible(false);
        actionBar.setManaged(false);
        pipelineStrip.setVisible(true);
        pipelineStrip.setManaged(true);
        pipelineStrip.getChildren().clear();

        // Build pipeline strip visually
        for (int i = 0; i < pipeSteps.size(); i++) {
            StackPane node = new StackPane();
            Circle c = new Circle(14, Color.web("#e2e5e9"));
            c.setId("circle_" + i);
            Label l = new Label(pipeSteps.get(i));
            l.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -fx-text-muted; -fx-translate-y: 24;"
            );
            node.getChildren().addAll(c, l);
            pipelineStrip.getChildren().add(node);

            if (i < pipeSteps.size() - 1) {
                Label arrow = new Label("→");
                arrow.setStyle(
                    "-fx-text-fill: -fx-text-ghost; -fx-font-weight: bold;"
                );
                pipelineStrip.getChildren().add(arrow);
            }
        }

        long[] delays = { 600, 700, 900, 1100, 1200, 800, 400 };
        SequentialTransition seq = new SequentialTransition();

        for (int i = 0; i < pipeSteps.size(); i++) {
            final int idx = i;
            PauseTransition pt = new PauseTransition(
                Duration.millis(delays[idx])
            );
            pt.setOnFinished(e -> markStepDone(idx));
            seq.getChildren().add(pt);
        }

        seq.setOnFinished(e -> finishReconciliation());
        seq.play();
    }

    private void markStepDone(int stepIdx) {
        // Step nodes are at 0, 2, 4, 6... in pipelineStrip.getChildren()
        StackPane node = (StackPane) pipelineStrip
            .getChildren()
            .get(stepIdx * 2);
        Circle c = (Circle) node.getChildren().get(0);
        c.setFill(Color.web("#800020"));
    }

    private void finishReconciliation() {
        resultBar.setVisible(true);
        resultBar.setManaged(true);

        // Mock result numbers
        rbAuto.setText("243");
        rbManual.setText("5");

        // Save mock hypotheses to context for Manual Check phase
        MainUIContext.getInstance().setPendingHypotheses(
            MockUIProvider.getMockHypotheses(5)
        );

        // Unlock the manual check tab via WorkspaceController
        Object ctrl = MainUIContext.getInstance().getWorkspaceController();
        if (ctrl instanceof WorkspaceController) {
            ((WorkspaceController) ctrl).unlockManualCheck();
        }
    }
}
