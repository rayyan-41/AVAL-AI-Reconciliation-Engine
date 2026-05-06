# AVAL — UC8 / UC9 / UC10 Implementation Plan

**Target:** Bring UC8 (Force Manual Reconciliation), UC9 (Multi-Source Consolidation), and UC10 (Identify Financial Anomalies) from partial to fully satisfied.  
**Stack:** Java 17, JavaFX 17, existing `aval.*` package structure, PostgreSQL + pgvector, FXML-based UI.  
**Style contract:** match every pattern already in the codebase — constructor injection, `BigDecimal`/`java.time`, `//@desc //@grasp //@gof` headers on every new non-UI class, `Task<T>` for all background work in controllers, no Lombok, no static singletons.

---

## Phase 1 — UC10: Typed Anomaly Domain Object

> **Why first:** UC10's `List<String>` output feeds the anomaly panel in `ManualCheckController`. Typing it now means UC8's forced-match justification and UC9's consolidation discrepancies can reuse the same `Anomaly` object. Doing this last would require retrofitting.

### 1.1 Create `aval/domain/ai/Anomaly.java`

New file: `src/main/java/aval/domain/ai/Anomaly.java`

```java
//@desc:   Represents a single detected financial anomaly with a typed category and affected transaction references.
//@grasp:  Information Expert
//@gof:    N/A
public class Anomaly {

    public enum Category { DUPLICATE, OUTLIER, WEEKEND_POSTING, CONSOLIDATION_VARIANCE }

    private final UUID anomalyId;
    private final Category category;
    private final String description;           // human-readable message (replaces the raw String)
    private final StandardizedTransaction primaryTransaction;
    private final StandardizedTransaction secondaryTransaction; // null unless DUPLICATE

    public Anomaly(Category category, String description,
                   StandardizedTransaction primary, StandardizedTransaction secondary) {
        this.anomalyId           = UUID.randomUUID();
        this.category            = category;
        this.description         = description;
        this.primaryTransaction  = primary;
        this.secondaryTransaction = secondary;
    }

    // Getters only — immutable after construction
    public UUID getAnomalyId()                          { return anomalyId; }
    public Category getCategory()                        { return category; }
    public String getDescription()                       { return description; }
    public StandardizedTransaction getPrimaryTransaction()  { return primaryTransaction; }
    public StandardizedTransaction getSecondaryTransaction(){ return secondaryTransaction; }

    @Override
    public String toString() { return "[" + category + "] " + description; }
}
```

### 1.2 Update `AnomalyDetectionEngine`

**File:** `src/main/java/aval/engine/AnomalyDetectionEngine.java`

Change the return type of `identifyAnomalies()` from `List<String>` to `List<Anomaly>`. Update each detection block:

```java
//@desc:   Engine that flags suspicious transactions based on duplication, outliers, and timing (UC10).
//@grasp:  Pure Fabrication, Information Expert
//@gof:    N/A
public class AnomalyDetectionEngine {

    public List<Anomaly> identifyAnomalies(
            List<StandardizedTransaction> unmatchedLedger,
            List<StandardizedTransaction> unmatchedBank) {

        List<Anomaly> anomalies = new ArrayList<>();
        List<StandardizedTransaction> all = new ArrayList<>();
        all.addAll(unmatchedLedger);
        all.addAll(unmatchedBank);
        if (all.isEmpty()) return anomalies;

        // 1. Exact Duplicates
        findDuplicates(unmatchedLedger, "Ledger", anomalies);
        findDuplicates(unmatchedBank,   "Bank",   anomalies);

        // 2. Outliers (>3 std-dev)
        double mean   = all.stream().mapToDouble(t -> t.getAmount().abs().doubleValue()).average().orElse(0.0);
        double sumSq  = all.stream().mapToDouble(t -> Math.pow(t.getAmount().abs().doubleValue() - mean, 2)).sum();
        double stdDev = Math.sqrt(sumSq / all.size());
        double ceiling = mean + (3 * stdDev);

        for (StandardizedTransaction t : all) {
            if (stdDev > 0 && t.getAmount().abs().doubleValue() > ceiling) {
                anomalies.add(new Anomaly(
                    Anomaly.Category.OUTLIER,
                    String.format("Amount %s on %s exceeds 3 std-deviations (threshold: %.2f)",
                                  t.getAmount(), t.getValueDate(), ceiling),
                    t, null));
            }
        }

        // 3. Weekend/Sunday postings
        for (StandardizedTransaction t : all) {
            if (t.getValueDate().getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                anomalies.add(new Anomaly(
                    Anomaly.Category.WEEKEND_POSTING,
                    String.format("Transaction %s for %s posted on a Sunday (%s)",
                                  t.getTransactionId().toString().substring(0, 8).toUpperCase(),
                                  t.getAmount(), t.getValueDate()),
                    t, null));
            }
        }
        return anomalies;
    }

    private void findDuplicates(List<StandardizedTransaction> txns, String source, List<Anomaly> out) {
        for (int i = 0; i < txns.size(); i++) {
            for (int j = i + 1; j < txns.size(); j++) {
                StandardizedTransaction a = txns.get(i), b = txns.get(j);
                if (a.getValueDate().equals(b.getValueDate()) && a.getAmount().equals(b.getAmount())) {
                    out.add(new Anomaly(
                        Anomaly.Category.DUPLICATE,
                        String.format("Duplicate %s entries on %s for %s", source, a.getValueDate(), a.getAmount()),
                        a, b));
                }
            }
        }
    }
}
```

### 1.3 Update `MainUIContext`

**File:** `src/main/java/aval/ui/MainUIContext.java`

Change the anomaly field and its getter/setter from `List<String>` to `List<Anomaly>`:

```java
// BEFORE
private List<String> anomalies;
public List<String> getAnomalies() { return anomalies; }
public void setAnomalies(List<String> anomalies) { this.anomalies = anomalies; }

// AFTER
private List<Anomaly> anomalies;
public List<Anomaly> getAnomalies()              { return anomalies; }
public void setAnomalies(List<Anomaly> anomalies) { this.anomalies = anomalies; }
```

### 1.4 Update `ReconController`

**File:** `src/main/java/aval/ui/controller/ReconController.java`

In `finishReconciliation()`, the anomaly engine call already returns `List<Anomaly>` after the engine change — just update the type reference:

```java
// BEFORE
List<String> anomalies = anomalyEngine != null
    ? anomalyEngine.identifyAnomalies(unmatchedLedger, unmatchedBank)
    : List.of();
ctx.setAnomalies(anomalies);

// AFTER
List<aval.domain.ai.Anomaly> anomalies = anomalyEngine != null
    ? anomalyEngine.identifyAnomalies(unmatchedLedger, unmatchedBank)
    : List.of();
ctx.setAnomalies(anomalies);
```

### 1.5 Update `ManualCheckController`

**File:** `src/main/java/aval/ui/controller/ManualCheckController.java`

Change `anomalyList` from `ListView<String>` to `ListView<Anomaly>` and add a cell factory that renders category + description:

```java
// Field type change
@FXML private ListView<Anomaly> anomalyList;

// In loadAnomalies()
public void loadAnomalies() {
    List<Anomaly> anomalies = MainUIContext.getInstance().getAnomalies();
    if (anomalies != null && !anomalies.isEmpty()) {
        anomaliesDismissed = false;
        anomalyList.setItems(FXCollections.observableArrayList(anomalies));
        anomalyList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Anomaly item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String badge = switch (item.getCategory()) {
                    case DUPLICATE              -> "⚠ DUPLICATE";
                    case OUTLIER                -> "📈 OUTLIER";
                    case WEEKEND_POSTING        -> "📅 WEEKEND";
                    case CONSOLIDATION_VARIANCE -> "∑ VARIANCE";
                };
                setText(badge + "  —  " + item.getDescription());
            }
        });
        anomalyPane.setVisible(true);
        anomalyPane.setManaged(true);
    } else {
        anomaliesDismissed = true;
        anomalyList.getItems().clear();
        anomalyPane.setVisible(false);
        anomalyPane.setManaged(false);
    }
    checkIfComplete();
}

// In handleDismissAnomalies()
MainUIContext.getInstance().setAnomalies(new ArrayList<>()); // List<Anomaly> — no change needed
```

### 1.6 Update `ReportService` (pass anomaly descriptions for the CSV)

**File:** `src/main/java/aval/service/ReportService.java`

The existing `generateReconciliationReport()` signature takes `List<String> unresolvedAnomalies`. Update the parameter to `List<Anomaly>` and call `.toString()` when checking emptiness and writing:

```java
// Signature change
public void generateReconciliationReport(
        List<ReconciliationRecord> reconciledRecords,
        List<StandardizedTransaction> unmatchedLedger,
        List<StandardizedTransaction> unmatchedBank,
        List<MatchHypothesis> pendingHypotheses,
        List<aval.domain.ai.Anomaly> unresolvedAnomalies,   // ← typed
        String outputFilePath) throws IOException, UnresolvedItemsException { ... }
```

The empty-check and `UnresolvedItemsException` throw logic stays identical — `List.isEmpty()` works on both types.

---

## Phase 2 — UC8: Force Manual Reconciliation UI

> **Pre-condition:** Phase 1 complete (typed anomalies in context). UC8's backend (`ReconciliationService.forceReconcile()`, `MatchType.FORCE_OVERRIDE`, `ReconciliationRecord.isManualOverride()`) already exists and is correct. This phase adds only the UI surface.

### 2.1 Add an "Unmatched Transactions" section to `ManualCheck.fxml`

**File:** `src/main/resources/aval/ui/views/ManualCheck.fxml`

Insert the following VBox **after** the `mcTable` TableView block and **before** the `anomalyPane` VBox:

```xml
<!-- UC8: Force Manual Reconciliation Panel -->
<VBox fx:id="forceMatchPane" spacing="12" styleClass="fd-card"
      visible="true" managed="true">

  <Label text="Unmatched Transactions" styleClass="section-heading" />
  <Label text="Select one entry from each list and provide a justification to force-link them."
         styleClass="mc-sub" wrapText="true" />

  <HBox spacing="16" alignment="TOP_LEFT">

    <!-- Unmatched Ledger -->
    <VBox spacing="6" HBox.hgrow="ALWAYS">
      <Label text="Unmatched Ledger" styleClass="mc-prog-label" />
      <TableView fx:id="unmatchedLedgerTable" styleClass="data-table" prefHeight="180">
        <columns>
          <TableColumn text="Date"      fx:id="fmLDateCol"  prefWidth="90" />
          <TableColumn text="Amount"    fx:id="fmLAmtCol"   prefWidth="100" />
          <TableColumn text="Narrative" fx:id="fmLNarrCol"  prefWidth="200" />
        </columns>
      </TableView>
    </VBox>

    <!-- Unmatched Bank -->
    <VBox spacing="6" HBox.hgrow="ALWAYS">
      <Label text="Unmatched Bank" styleClass="mc-prog-label" />
      <TableView fx:id="unmatchedBankTable" styleClass="data-table" prefHeight="180">
        <columns>
          <TableColumn text="Date"      fx:id="fmBDateCol"  prefWidth="90" />
          <TableColumn text="Amount"    fx:id="fmBAmtCol"   prefWidth="100" />
          <TableColumn text="Narrative" fx:id="fmBNarrCol"  prefWidth="200" />
        </columns>
      </TableView>
    </VBox>

  </HBox>

  <HBox spacing="12" alignment="CENTER_LEFT">
    <TextField fx:id="forceJustField"
               promptText="Enter justification for manual link..."
               HBox.hgrow="ALWAYS" />
    <Button fx:id="btnForceLink"
            text="Force Link"
            styleClass="btn-approve"
            onAction="#handleForceLink"
            disable="true" />
  </HBox>

  <Label fx:id="forceFeedback" styleClass="mc-sub" visible="false" managed="false"
         wrapText="true" />

</VBox>
```

### 2.2 Update `ManualCheckController`

**File:** `src/main/java/aval/ui/controller/ManualCheckController.java`

**New FXML-injected fields** (add alongside existing `@FXML` fields):

```java
@FXML private VBox forceMatchPane;
@FXML private TableView<StandardizedTransaction> unmatchedLedgerTable;
@FXML private TableColumn<StandardizedTransaction, String> fmLDateCol;
@FXML private TableColumn<StandardizedTransaction, String> fmLAmtCol;
@FXML private TableColumn<StandardizedTransaction, String> fmLNarrCol;
@FXML private TableView<StandardizedTransaction> unmatchedBankTable;
@FXML private TableColumn<StandardizedTransaction, String> fmBDateCol;
@FXML private TableColumn<StandardizedTransaction, String> fmBAmtCol;
@FXML private TableColumn<StandardizedTransaction, String> fmBNarrCol;
@FXML private TextField forceJustField;
@FXML private Button btnForceLink;
@FXML private Label forceFeedback;
```

**In `initialize()`** — add after existing setup:

```java
setupForceMatchTables();
```

**New method `setupForceMatchTables()`:**

```java
private void setupForceMatchTables() {
    // Wire columns
    fmLDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
    fmLAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
    fmLNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));
    fmBDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
    fmBAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
    fmBNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));

    // Enable the Force Link button only when both tables have a selection AND justification is non-blank
    javafx.beans.binding.BooleanBinding canForce = unmatchedLedgerTable.getSelectionModel()
        .selectedItemProperty().isNotNull()
        .and(unmatchedBankTable.getSelectionModel().selectedItemProperty().isNotNull())
        .and(forceJustField.textProperty().isNotEmpty());
    btnForceLink.disableProperty().bind(canForce.not());
}
```

**New public method `loadUnmatchedTransactions()`** — called by `WorkspaceController.showManual()`:

```java
public void loadUnmatchedTransactions() {
    MainUIContext ctx = MainUIContext.getInstance();
    List<StandardizedTransaction> ledger = ctx.getUnmatchedLedger();
    List<StandardizedTransaction> bank   = ctx.getUnmatchedBank();

    ObservableList<StandardizedTransaction> ledgerItems =
        FXCollections.observableArrayList(ledger != null ? ledger : List.of());
    ObservableList<StandardizedTransaction> bankItems =
        FXCollections.observableArrayList(bank   != null ? bank   : List.of());

    unmatchedLedgerTable.setItems(ledgerItems);
    unmatchedBankTable  .setItems(bankItems);

    // Hide the panel entirely if there is nothing unmatched
    boolean anyUnmatched = !ledgerItems.isEmpty() || !bankItems.isEmpty();
    forceMatchPane.setVisible(anyUnmatched);
    forceMatchPane.setManaged(anyUnmatched);
}
```

**New `@FXML` handler `handleForceLink()`:**

```java
@FXML
void handleForceLink() {
    StandardizedTransaction selectedLedger = unmatchedLedgerTable.getSelectionModel().getSelectedItem();
    StandardizedTransaction selectedBank   = unmatchedBankTable  .getSelectionModel().getSelectedItem();
    String justification = forceJustField.getText().trim();

    if (selectedLedger == null || selectedBank == null || justification.isBlank()) return;

    SystemUser user = getOrCreateCurrentUser();
    ReconciliationService svc = getReconciliationServiceOrShowError();
    if (svc == null) return;

    btnForceLink.setDisable(true);
    showForceFeedback("Saving forced reconciliation...");

    Task<ReconciliationRecord> task = new Task<>() {
        @Override
        protected ReconciliationRecord call() {
            return svc.forceReconcile(selectedLedger, selectedBank, user, justification);
        }
    };

    task.setOnSucceeded(e -> Platform.runLater(() -> {
        ReconciliationRecord record = task.getValue();
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.addReconciledRecord(record);

        // Remove the force-linked entries from the unmatched lists
        ctx.getUnmatchedLedger().remove(selectedLedger);
        ctx.getUnmatchedBank()  .remove(selectedBank);
        unmatchedLedgerTable.getItems().remove(selectedLedger);
        unmatchedBankTable  .getItems().remove(selectedBank);

        forceJustField.clear();
        showForceFeedback("Force-linked: " + selectedLedger.getNarrative()
            + "  ↔  " + selectedBank.getNarrative());
        checkIfComplete();
    }));

    task.setOnFailed(e -> Platform.runLater(() -> {
        btnForceLink.setDisable(false);
        showForceFeedback("Error: " + task.getException().getMessage());
    }));

    Thread t = new Thread(task);
    t.setDaemon(true);
    t.start();
}

private void showForceFeedback(String msg) {
    forceFeedback.setText(msg);
    forceFeedback.setVisible(true);
    forceFeedback.setManaged(true);
}
```

### 2.3 Update `WorkspaceController.showManual()`

**File:** `src/main/java/aval/ui/controller/WorkspaceController.java`

Add the `loadUnmatchedTransactions()` call alongside the existing `setItems` / `loadAnomalies` calls:

```java
@FXML
public void showManual() {
    if (manualCheckController != null) {
        List<MatchHypothesis> hypotheses = MainUIContext.getInstance().getPendingHypotheses();
        manualCheckController.setItems(hypotheses);
        manualCheckController.loadAnomalies();
        manualCheckController.loadUnmatchedTransactions();   // ← ADD THIS LINE
    }
    if (manualView != null) showView(manualView, null);
    tabManual.setSelected(true);
}
```

### 2.4 Update `checkIfComplete()` in `ManualCheckController`

The "Proceed to Report" bar should only appear when hypotheses, anomalies, **and** unmatched transactions are all resolved. Add an unmatched-items check:

```java
private void checkIfComplete() {
    int total = mcTable.getItems().size();
    boolean hypothesesDone = resolved >= total;
    boolean unmatchedDone  = unmatchedLedgerTable.getItems().isEmpty()
                          && unmatchedBankTable  .getItems().isEmpty();
    if (hypothesesDone && anomaliesDismissed && unmatchedDone) {
        mcCompleteBar.setVisible(true);
        mcCompleteBar.setManaged(true);
    } else {
        mcCompleteBar.setVisible(false);
        mcCompleteBar.setManaged(false);
    }
}
```

> **Note:** If the team decides unmatched transactions should not block report generation (i.e., they are legitimate discrepancies), remove the `unmatchedDone` condition and leave the check as-is. The `ReportService` already handles `unmatchedLedger` and `unmatchedBank` lists in the CSV output.

---

## Phase 3 — UC9: Multi-Source Consolidation

> UC9 describes summing multiple small ledger entries against one large bank deposit — e.g., three individual invoices (£500 + £300 + £200) consolidated into one bank credit (£1,000). The current `consolidateMultiSource()` does pairwise UC6 matching instead. This phase fixes the shape and adds a UI entry point.

### 3.1 Fix `ReconciliationService.consolidateMultiSource()`

**File:** `src/main/java/aval/service/ReconciliationService.java`

Replace the existing `consolidateMultiSource()` body entirely:

```java
/**
 * UC9 — Perform Multi-Source Consolidation
 *
 * Groups ledger transactions whose amounts sum (within tolerancePct %) to the amount
 * of each bank transaction, creating a single FORCE_OVERRIDE hypothesis per group.
 * Variance anomalies (sum outside tolerance) are returned via the provided list.
 *
 * @param workspace         the active workspace (used for MatchingConfig)
 * @param ledgerTransactions all unmatched ledger transactions to consider
 * @param bankTransaction    the single bank transaction to match against
 * @param tolerancePct       acceptable variance as a fraction, e.g. 0.02 = 2 %
 * @param anomaliesOut       mutable list — CONSOLIDATION_VARIANCE entries appended here
 * @param confirmingUser     user performing the consolidation
 * @return list of ReconciliationRecords created (one per group)
 */
public List<ReconciliationRecord> consolidateMultiSource(
        ReconciliationWorkspace workspace,
        List<StandardizedTransaction> ledgerTransactions,
        StandardizedTransaction bankTransaction,
        double tolerancePct,
        List<aval.domain.ai.Anomaly> anomaliesOut,
        SystemUser confirmingUser) {

    if (ledgerTransactions == null || ledgerTransactions.isEmpty() || bankTransaction == null) {
        return new ArrayList<>();
    }

    java.math.BigDecimal bankAmt   = bankTransaction.getAmount().abs();
    java.math.BigDecimal tolerance = bankAmt.multiply(java.math.BigDecimal.valueOf(tolerancePct));
    java.math.BigDecimal lower     = bankAmt.subtract(tolerance);
    java.math.BigDecimal upper     = bankAmt.add(tolerance);

    // Build a subset whose running sum falls within [lower, upper]
    List<StandardizedTransaction> group = new ArrayList<>();
    java.math.BigDecimal runningSum = java.math.BigDecimal.ZERO;

    for (StandardizedTransaction ledger : ledgerTransactions) {
        java.math.BigDecimal candidate = runningSum.add(ledger.getAmount().abs());
        if (candidate.compareTo(upper) <= 0) {
            group.add(ledger);
            runningSum = candidate;
        }
        if (runningSum.compareTo(lower) >= 0) break;
    }

    List<ReconciliationRecord> records = new ArrayList<>();

    if (runningSum.compareTo(lower) >= 0 && runningSum.compareTo(upper) <= 0) {
        // Within tolerance — create one hypothesis per grouped ledger entry
        for (StandardizedTransaction ledger : group) {
            MatchHypothesis h = new MatchHypothesis(ledger, bankTransaction,
                1.0, aval.common.enums.MatchType.FORCE_OVERRIDE);
            h.setStatus(aval.common.enums.HypothesisStatus.APPROVED);
            h.setJustification(String.format(
                "UC9 Consolidation: group sum %s matches bank %s (tolerance %.1f%%)",
                runningSum.toPlainString(), bankAmt.toPlainString(), tolerancePct * 100));
            records.add(new ReconciliationRecord(h, confirmingUser));
        }
        dataStore.saveReconciliationRecords(records);
    } else {
        // Outside tolerance — flag as variance anomaly
        if (anomaliesOut != null) {
            anomaliesOut.add(new aval.domain.ai.Anomaly(
                aval.domain.ai.Anomaly.Category.CONSOLIDATION_VARIANCE,
                String.format("Consolidation variance: ledger group sums to %s, bank posted %s (tolerance %.1f%%)",
                    runningSum.toPlainString(), bankAmt.toPlainString(), tolerancePct * 100),
                bankTransaction, null));
        }
    }
    return records;
}
```

### 3.2 Add a "Multi-Source Consolidation" section to `ManualCheck.fxml`

**File:** `src/main/resources/aval/ui/views/ManualCheck.fxml`

Insert **after** the `forceMatchPane` VBox (end of Phase 2 block):

```xml
<!-- UC9: Multi-Source Consolidation Panel -->
<VBox fx:id="consolidationPane" spacing="12" styleClass="fd-card"
      visible="false" managed="false">

  <Label text="Multi-Source Consolidation" styleClass="section-heading" />
  <Label text="Select a single bank transaction and the ledger entries whose amounts sum to it."
         styleClass="mc-sub" wrapText="true" />

  <HBox spacing="16" alignment="TOP_LEFT">

    <!-- Bank target picker -->
    <VBox spacing="6" minWidth="280">
      <Label text="Target Bank Transaction" styleClass="mc-prog-label" />
      <TableView fx:id="consolidationBankTable" styleClass="data-table" prefHeight="160">
        <columns>
          <TableColumn text="Date"      fx:id="csBDateCol" prefWidth="90" />
          <TableColumn text="Amount"    fx:id="csBAmtCol"  prefWidth="110" />
          <TableColumn text="Narrative" fx:id="csBNarrCol" prefWidth="160" />
        </columns>
      </TableView>
    </VBox>

    <!-- Ledger multi-select -->
    <VBox spacing="6" HBox.hgrow="ALWAYS">
      <Label text="Ledger Entries to Consolidate (multi-select)" styleClass="mc-prog-label" />
      <TableView fx:id="consolidationLedgerTable" styleClass="data-table" prefHeight="160"
                 selectionMode="MULTIPLE">
        <columns>
          <TableColumn text="Date"      fx:id="csLDateCol" prefWidth="90" />
          <TableColumn text="Amount"    fx:id="csLAmtCol"  prefWidth="110" />
          <TableColumn text="Narrative" fx:id="csLNarrCol" prefWidth="200" />
        </columns>
      </TableView>
    </VBox>

  </HBox>

  <HBox spacing="12" alignment="CENTER_LEFT">
    <Label text="Tolerance (%)" styleClass="mc-prog-label" />
    <TextField fx:id="toleranceField" text="2.0" prefWidth="70" />
    <Region HBox.hgrow="ALWAYS" />
    <Label fx:id="consolidationSumLabel" styleClass="mc-sub" />
    <Button fx:id="btnConsolidate"
            text="Consolidate"
            styleClass="btn-approve"
            onAction="#handleConsolidate"
            disable="true" />
  </HBox>

  <Label fx:id="consolidationFeedback" styleClass="mc-sub" visible="false" managed="false"
         wrapText="true" />

</VBox>
```

### 3.3 Update `ManualCheckController` for UC9

**New FXML-injected fields:**

```java
@FXML private VBox consolidationPane;
@FXML private TableView<StandardizedTransaction> consolidationBankTable;
@FXML private TableColumn<StandardizedTransaction, String> csBDateCol;
@FXML private TableColumn<StandardizedTransaction, String> csBAmtCol;
@FXML private TableColumn<StandardizedTransaction, String> csBNarrCol;
@FXML private TableView<StandardizedTransaction> consolidationLedgerTable;
@FXML private TableColumn<StandardizedTransaction, String> csLDateCol;
@FXML private TableColumn<StandardizedTransaction, String> csLAmtCol;
@FXML private TableColumn<StandardizedTransaction, String> csLNarrCol;
@FXML private TextField toleranceField;
@FXML private Label consolidationSumLabel;
@FXML private Button btnConsolidate;
@FXML private Label consolidationFeedback;
```

**Add to `initialize()`:**

```java
setupConsolidationTables();
```

**New method `setupConsolidationTables()`:**

```java
private void setupConsolidationTables() {
    csBDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
    csBAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
    csBNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));

    csLDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getValueDate().toString()));
    csLAmtCol .setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmount().toPlainString()));
    csLNarrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNarrative()));

    // Multi-select on ledger table
    consolidationLedgerTable.getSelectionModel()
        .setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

    // Live sum label as ledger rows are selected
    consolidationLedgerTable.getSelectionModel().getSelectedItems()
        .addListener((javafx.collections.ListChangeListener<StandardizedTransaction>) change -> {
            java.math.BigDecimal sum = consolidationLedgerTable.getSelectionModel()
                .getSelectedItems().stream()
                .map(t -> t.getAmount().abs())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            consolidationSumLabel.setText("Selected sum: " + sum.toPlainString());
            updateConsolidateButton();
        });

    consolidationBankTable.getSelectionModel().selectedItemProperty()
        .addListener((obs, o, n) -> updateConsolidateButton());
}

private void updateConsolidateButton() {
    boolean bankSelected   = consolidationBankTable.getSelectionModel().getSelectedItem() != null;
    boolean ledgerSelected = !consolidationLedgerTable.getSelectionModel().getSelectedItems().isEmpty();
    btnConsolidate.setDisable(!(bankSelected && ledgerSelected));
}
```

**Update `loadUnmatchedTransactions()`** to also populate the consolidation tables and toggle the panel:

```java
public void loadUnmatchedTransactions() {
    MainUIContext ctx = MainUIContext.getInstance();
    List<StandardizedTransaction> ledger = ctx.getUnmatchedLedger();
    List<StandardizedTransaction> bank   = ctx.getUnmatchedBank();

    ObservableList<StandardizedTransaction> ledgerItems =
        FXCollections.observableArrayList(ledger != null ? ledger : List.of());
    ObservableList<StandardizedTransaction> bankItems =
        FXCollections.observableArrayList(bank   != null ? bank   : List.of());

    // UC8 tables
    unmatchedLedgerTable.setItems(ledgerItems);
    unmatchedBankTable  .setItems(bankItems);

    // UC9 tables (same source data, separate table references)
    consolidationLedgerTable.setItems(FXCollections.observableArrayList(ledgerItems));
    consolidationBankTable  .setItems(FXCollections.observableArrayList(bankItems));

    boolean anyUnmatched = !ledgerItems.isEmpty() || !bankItems.isEmpty();
    forceMatchPane    .setVisible(anyUnmatched);
    forceMatchPane    .setManaged(anyUnmatched);
    consolidationPane .setVisible(anyUnmatched);
    consolidationPane .setManaged(anyUnmatched);
}
```

**New `@FXML` handler `handleConsolidate()`:**

```java
@FXML
void handleConsolidate() {
    StandardizedTransaction bankTx = consolidationBankTable.getSelectionModel().getSelectedItem();
    List<StandardizedTransaction> selectedLedger = new ArrayList<>(
        consolidationLedgerTable.getSelectionModel().getSelectedItems());

    if (bankTx == null || selectedLedger.isEmpty()) return;

    double tolerancePct;
    try {
        tolerancePct = Double.parseDouble(toleranceField.getText().trim()) / 100.0;
    } catch (NumberFormatException ex) {
        consolidationFeedback.setText("Invalid tolerance value — enter a number like 2.0");
        consolidationFeedback.setVisible(true);
        consolidationFeedback.setManaged(true);
        return;
    }

    SystemUser user = getOrCreateCurrentUser();
    ReconciliationService svc = getReconciliationServiceOrShowError();
    if (svc == null) return;

    btnConsolidate.setDisable(true);

    Task<List<aval.domain.ai.ReconciliationRecord>> task = new Task<>() {
        @Override
        protected List<aval.domain.ai.ReconciliationRecord> call() {
            List<aval.domain.ai.Anomaly> newAnomalies = new ArrayList<>();
            List<aval.domain.ai.ReconciliationRecord> records = svc.consolidateMultiSource(
                MainUIContext.getInstance().getActiveWorkspace(),
                selectedLedger, bankTx, tolerancePct, newAnomalies, user);

            // Merge any consolidation-variance anomalies back into context
            if (!newAnomalies.isEmpty()) {
                List<aval.domain.ai.Anomaly> existing =
                    MainUIContext.getInstance().getAnomalies();
                if (existing == null) existing = new ArrayList<>();
                existing.addAll(newAnomalies);
                MainUIContext.getInstance().setAnomalies(existing);
            }
            return records;
        }
    };

    task.setOnSucceeded(e -> Platform.runLater(() -> {
        List<aval.domain.ai.ReconciliationRecord> records = task.getValue();

        if (records.isEmpty()) {
            // Variance anomaly was added — reload anomaly panel
            loadAnomalies();
            consolidationFeedback.setText("Variance detected — anomaly logged. Adjust selection or tolerance.");
        } else {
            records.forEach(r -> MainUIContext.getInstance().addReconciledRecord(r));
            // Remove consolidated entries from unmatched lists
            selectedLedger.forEach(l -> {
                MainUIContext.getInstance().getUnmatchedLedger().remove(l);
                unmatchedLedgerTable.getItems().remove(l);
                consolidationLedgerTable.getItems().remove(l);
            });
            MainUIContext.getInstance().getUnmatchedBank().remove(bankTx);
            unmatchedBankTable.getItems().remove(bankTx);
            consolidationBankTable.getItems().remove(bankTx);

            consolidationFeedback.setText(
                records.size() + " ledger entries consolidated against bank transaction "
                + bankTx.getAmount().toPlainString());
            checkIfComplete();
        }

        consolidationFeedback.setVisible(true);
        consolidationFeedback.setManaged(true);
        btnConsolidate.setDisable(false);
    }));

    task.setOnFailed(e -> Platform.runLater(() -> {
        consolidationFeedback.setText("Error: " + task.getException().getMessage());
        consolidationFeedback.setVisible(true);
        consolidationFeedback.setManaged(true);
        btnConsolidate.setDisable(false);
    }));

    Thread t = new Thread(task);
    t.setDaemon(true);
    t.start();
}
```

---

## Execution Order

Implement phases in sequence — each phase builds on the one before:

```
Phase 1  →  Phase 2  →  Phase 3
UC10 domain typing     UC8 force UI     UC9 consolidation UI + fixed service
```

Within each phase, implement in the listed section order (domain → engine → context → controllers → FXML) to avoid compilation errors from missing types.

---

## File Change Summary

| File | Change Type |
|---|---|
| `aval/domain/ai/Anomaly.java` | **NEW** |
| `aval/engine/AnomalyDetectionEngine.java` | Return type `List<String>` → `List<Anomaly>` |
| `aval/ui/MainUIContext.java` | Anomaly field type |
| `aval/ui/controller/ReconController.java` | Anomaly type reference |
| `aval/ui/controller/ManualCheckController.java` | New fields, new handlers × 3, `loadUnmatchedTransactions()`, typed anomaly cell factory |
| `aval/ui/controller/WorkspaceController.java` | Add `loadUnmatchedTransactions()` call in `showManual()` |
| `aval/service/ReportService.java` | `unresolvedAnomalies` param type |
| `aval/service/ReconciliationService.java` | `consolidateMultiSource()` full replacement |
| `aval/ui/views/ManualCheck.fxml` | Two new VBox panels (UC8 + UC9) |
