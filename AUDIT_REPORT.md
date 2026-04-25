# AVAL AI Reconciliation Engine — Audit Report (v2)
**Date:** 2026-04-25 (updated after latest pull)  
**Evaluator:** Nia (via Claude AI audit)  
**Purpose:** Pre-submission code audit against project rubric. Reflects state after latest git pull.

---

## WHAT CHANGED IN THIS PULL ✅

| Old Issue | Status | Notes |
|---|---|---|
| ISSUE-01 Missing UI source files | ✅ PARTIALLY FIXED | AppController now has a hypothesis TableView, approve/reject buttons, manual override section with ListViews. Much richer UI. The 3 orphaned `.class` files remain without source, but the single AppController now covers most of their scope. |
| ISSUE-02 UC10 zero implementation | ✅ PARTIALLY FIXED | `AnomalyDetectionEngine.java` created with real logic (duplicate detection, outlier detection, weekend flags). **However, it is never called in the pipeline — see NEW-02.** |
| ISSUE-05 DataStore null stubs | ✅ PARTIALLY FIXED | `saveClientOrganization`, `findClientOrganizationById`, `saveReconciliationWorkspace`, `findReconciliationWorkspaceById` now have real SQL. **However, DataStore.java is truncated mid-file — see NEW-01.** |

---

## 🚨 NEW CRITICAL ISSUES (introduced in this pull)

---

### NEW-01 — `DataStore.java` is TRUNCATED — Will Not Compile
**Severity:** CRITICAL  
**File:** `src/aval/persistence/DataStore.java` (200 lines, cut off mid-statement)

**Problem:**  
The file ends abruptly inside `saveFinancialDataset()` with no closing brace for the method, the try-catch block, or the class. The last line is:
```java
            pstmt.setDate(6, java.sql.Date.valueOf(dataset.getImportDate()));
         
```
There is no `pstmt.executeUpdate()`, no `} catch`, no closing `}` for the method, and no closing `}` for the class. Additionally, `saveRawTransactions`, `saveStandardizedTransactions`, `saveMatchHypotheses`, `saveReconciliationRecords`, and `findSystemUserById` are entirely missing from the file — they existed in the previous version and have been lost.

**Fix:**  
Complete the `saveFinancialDataset` method, close the class, and restore the missing methods. Minimum required to close the file:
```java
            pstmt.setDate(6, java.sql.Date.valueOf(dataset.getImportDate()));
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void saveRawTransactions(List<RawTransaction> rawTransactions) { /* stub OK for now */ }

    public void saveStandardizedTransactions(List<StandardizedTransaction> standardizedTransactions) { /* stub OK */ }

    public void saveMatchHypotheses(List<MatchHypothesis> hypotheses) {
        String sql = "INSERT INTO match_hypotheses (hypothesis_id, ledger_id, bank_id, confidence_score, match_type, status, justification) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (hypothesis_id) DO UPDATE SET status = EXCLUDED.status, justification = EXCLUDED.justification";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (MatchHypothesis h : hypotheses) {
                pstmt.setObject(1, h.getHypothesisId());
                pstmt.setObject(2, h.getLedgerTransaction() != null ? h.getLedgerTransaction().getTransactionId() : null);
                pstmt.setObject(3, h.getBankTransaction() != null ? h.getBankTransaction().getTransactionId() : null);
                pstmt.setDouble(4, h.getConfidenceScore());
                pstmt.setString(5, h.getMatchType() != null ? h.getMatchType().name() : null);
                pstmt.setString(6, h.getStatus() != null ? h.getStatus().name() : null);
                pstmt.setString(7, h.getJustification());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void saveReconciliationRecords(List<ReconciliationRecord> records) {
        String sql = "INSERT INTO reconciliation_records (record_id, hypothesis_id, confirming_user_id, reconciled_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (ReconciliationRecord r : records) {
                pstmt.setObject(1, r.getRecordId());
                pstmt.setObject(2, r.getHypothesis() != null ? r.getHypothesis().getHypothesisId() : null);
                pstmt.setObject(3, r.getConfirmingUser() != null ? r.getConfirmingUser().getUserId() : null);
                pstmt.setTimestamp(4, r.getReconciledAt() != null ? java.sql.Timestamp.valueOf(r.getReconciledAt()) : null);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public SystemUser findSystemUserById(UUID id) { return null; }
}
```

---

### NEW-02 — `AppController.java` is TRUNCATED — Will Not Compile
**Severity:** CRITICAL  
**File:** `src/aval/ui/controller/AppController.java` (287 lines, cut off mid-lambda)

**Problem:**  
The file ends inside a `Platform.runLater()` lambda inside `executePipeline()`, mid-expression:
```java
                    unmatchedBankList.getItems().addAll(stdBank.stream().filter(t -> !matchedBank.contains
```
This is syntactically invalid Java. There are no closing parentheses for the stream, no closing `});` for `Platform.runLater`, no `} catch`, no `} finally`, no closing `}` for `executePipeline()`, and no closing `}` for the class. The `log()` helper method and `getView()` method are also gone.

**Fix:**  
Complete the truncated lambda and close all open blocks. The minimum to make it compile:
```java
                    unmatchedBankList.getItems().addAll(stdBank.stream()
                        .filter(t -> !matchedBank.contains(t))
                        .collect(Collectors.toList()));

                    // UC10 — Run Anomaly Detection
                    List<String> anomalies = anomalyEngine.identifyAnomalies(
                        new ArrayList<>(unmatchedLedgerList.getItems()),
                        new ArrayList<>(unmatchedBankList.getItems())
                    );
                    if (!anomalies.isEmpty()) {
                        log("\n--- ANOMALIES DETECTED ---");
                        anomalies.forEach(a -> log("  " + a));
                    }
                });

                // Generate Report
                log("4. Generating Report...");
                List<ReconciliationRecord> approvedRecords = new ArrayList<>();
                reportService.generateReconciliationReport(
                    approvedRecords,
                    new ArrayList<>(unmatchedLedgerList.getItems()),
                    new ArrayList<>(unmatchedBankList.getItems()),
                    "data/scenario_01_retail_ecommerce/reconciliation_report_output.csv"
                );
                log("Pipeline complete.");

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

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private File openFileChooser(String title, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Supported Files", extension));
        File initialDir = new File("data/scenario_01_retail_ecommerce");
        if (initialDir.exists()) fileChooser.setInitialDirectory(initialDir);
        return fileChooser.showOpenDialog(root.getScene().getWindow());
    }

    private void checkReadyToRun() {
        if (ledgerPath != null && bankPath != null &&
            !ledgerPath.isEmpty() && !bankPath.isEmpty()) {
            runReconciliationBtn.setDisable(false);
        }
    }

    public Region getView() {
        return root;
    }
}
```

---

### NEW-03 — COMPILE ERROR: `DataStore` Calls Getters That Don't Exist
**Severity:** CRITICAL  
**Files:** `src/aval/persistence/DataStore.java` + `src/aval/domain/core/ClientOrganization.java` + `src/aval/domain/core/ReconciliationWorkspace.java`

**Problem:**  
The new DataStore SQL methods call getters on `ClientOrganization` and `ReconciliationWorkspace` that have never been added to those classes:

```java
// DataStore calls these — none of them exist:
org.getOrgId()                         // ClientOrganization has no getOrgId()
org.getName()                          // ClientOrganization has no getName()
workspace.getWorkspaceId()             // ReconciliationWorkspace has no getWorkspaceId()
workspace.getStatus()                  // ReconciliationWorkspace has no getStatus()
workspace.getClientOrganization()      // ReconciliationWorkspace has no getClientOrganization()
dataset.getStatus()                    // FinancialDataset has no getStatus()
dataset.getImportDate()                // FinancialDataset has no getImportDate()
```

The project will not compile until these are added.

**Fix — `ClientOrganization.java`:** Add at the end of the class body (before closing `}`):
```java
public UUID getOrgId() { return orgId; }
public String getName() { return name; }
public String getContactMetadata() { return contactMetadata; }
public List<ReconciliationWorkspace> getWorkspaces() { return workspaces; }
public void addWorkspace(ReconciliationWorkspace workspace) { this.workspaces.add(workspace); }
```

**Fix — `ReconciliationWorkspace.java`:** Add at the end of the class body (before closing `}`):
```java
public UUID getWorkspaceId() { return workspaceId; }
public WorkspaceStatus getStatus() { return status; }
public void setStatus(WorkspaceStatus status) { this.status = status; }
public ClientOrganization getClientOrganization() { return clientOrganization; }
public MatchingConfig getMatchingConfig() { return matchingConfig; }
public List<FinancialDataset> getDatasets() { return datasets; }
public List<MatchHypothesis> getHypotheses() { return hypotheses; }
public List<ReconciliationRecord> getRecords() { return records; }
```

**Fix — `FinancialDataset.java`:** Add these getters to the abstract base class:
```java
public DatasetStatus getStatus() { return status; }
public LocalDate getImportDate() { return importDate; }
```

---

### NEW-04 — `AnomalyDetectionEngine` Instantiated But Never Called
**Severity:** HIGH  
**File:** `src/aval/ui/controller/AppController.java`

**Problem:**  
`anomalyEngine` is declared as a field and instantiated in `initializeBackend()`, but is never called anywhere in `executePipeline()` or anywhere else. UC10 is therefore still non-functional at runtime despite the engine existing.

**Fix:**  
Wire it into the completion of the truncated `executePipeline()` (see NEW-02 fix above — the fix includes the `anomalyEngine.identifyAnomalies()` call).

---

### NEW-05 — DataStore References Tables That Don't Exist in the Schema
**Severity:** CRITICAL  
**Files:** `src/aval/persistence/DataStore.java` + `db-init/01-init.sql`

**Problem:**  
The new DataStore methods issue SQL against three tables that are not defined in `db-init/01-init.sql`:
- `client_organization` (DataStore uses this name; schema has no such table)
- `reconciliation_workspace` (same — doesn't exist in SQL)
- `financial_dataset` (same — doesn't exist in SQL)

Every call to `saveClientOrganization()`, `saveReconciliationWorkspace()`, and `saveFinancialDataset()` will throw a `SQLException: relation does not exist` at runtime.

**Fix — Append to `db-init/01-init.sql`:**
```sql
-- 5. Client Organizations
CREATE TABLE IF NOT EXISTS client_organization (
    org_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    contact_metadata TEXT
);

-- 6. Reconciliation Workspaces
CREATE TABLE IF NOT EXISTS reconciliation_workspace (
    workspace_id UUID PRIMARY KEY,
    org_id UUID REFERENCES client_organization(org_id),
    status VARCHAR(30) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. Financial Datasets (tracks uploaded files)
CREATE TABLE IF NOT EXISTS financial_dataset (
    dataset_id UUID PRIMARY KEY,
    workspace_id UUID REFERENCES reconciliation_workspace(workspace_id),
    file_path TEXT,
    source_type VARCHAR(30),
    status VARCHAR(30),
    import_date DATE
);
```
**Note:** After adding these tables, you must recreate the Docker container for the init script to re-run:
```bash
docker compose down -v
docker compose up -d
```

---

## PERSISTING ISSUES (unchanged from v1)

---

### ISSUE-03 — UC9 `consolidateMultiSource()` Still a Stub
**Severity:** HIGH  
**File:** `src/aval/service/ReconciliationService.java`

The method body is identical to v1 — returns an empty list with a comment. No change was made.

**Fix:** (same as v1 — iterate dataset pairs through `matchingEngine.generateHypotheses()`)
```java
public List<MatchHypothesis> consolidateMultiSource(
    ReconciliationWorkspace workspace,
    List<List<StandardizedTransaction>> multipleDatasets
) {
    List<MatchHypothesis> consolidated = new ArrayList<>();
    if (multipleDatasets == null || multipleDatasets.size() < 2) return consolidated;
    for (int i = 0; i < multipleDatasets.size() - 1; i++) {
        for (int j = i + 1; j < multipleDatasets.size(); j++) {
            consolidated.addAll(matchingEngine.generateHypotheses(
                multipleDatasets.get(i), multipleDatasets.get(j)));
        }
    }
    dataStore.saveMatchHypotheses(consolidated);
    return consolidated;
}
```

---

### ISSUE-07 — `RawInternalLedger.getSourceType()` Returns Wrong Enum Value
**Severity:** HIGH (silent pipeline bug)  
**File:** `src/aval/domain/ingestion/RawInternalLedger.java` line 32

Still returns `DataSourceType.INTERNAL_CSV`. `INTERNAL_CSV` does not exist in the `DataSourceType` enum (`INTERNAL_EXCEL` and `EXTERNAL_PDF` are the only values). This is a compile error waiting to happen, and it already silently breaks the standardization branch in `IngestionService` which checks `== DataSourceType.INTERNAL_EXCEL`.

**Fix (1 line):**
```java
// Change:
return DataSourceType.INTERNAL_CSV;
// To:
return DataSourceType.INTERNAL_EXCEL;
```

---

### ISSUE-08 — UC1 Not Wired Into Pipeline
**Severity:** MEDIUM  
**File:** `src/aval/ui/controller/AppController.java`

`executePipeline()` still goes straight to ingestion with no `ClientOrganization` or `ReconciliationWorkspace` being created or persisted. The `reconciliationService.runMatching()` call still passes `null` as the workspace parameter.

**Fix:** Add at the top of `executePipeline()` before ingestion:
```java
log("0. Initializing Workspace (UC1)...");
aval.domain.core.ClientOrganization client = new aval.domain.core.ClientOrganization(
    UUID.randomUUID(), "Brightline Retail", "scenario_01@brightline.com");
dataStore.saveClientOrganization(client);
aval.domain.core.MatchingConfig config = new aval.domain.core.MatchingConfig();
aval.domain.core.ReconciliationWorkspace workspace = new aval.domain.core.ReconciliationWorkspace(
    UUID.randomUUID(), client, config);
dataStore.saveReconciliationWorkspace(workspace);
log("   -> Workspace ready.");
```
Then pass `workspace` instead of `null` into `reconciliationService.runMatching(workspace, stdLedger, stdBank)`.

---

### ISSUE-09 — `rejectHypothesis()` Does Not Persist Rejection to DB
**Severity:** MEDIUM  
**File:** `src/aval/service/ReconciliationService.java`

Identical to v1. Status is updated in memory only, with the comment still present:  
`// For this prototype, we assume hypothesis state is tracked in the session/DB.`

**Fix:** Add `updateHypothesisStatus()` to DataStore and call it:
```java
// In DataStore.java
public void updateHypothesisStatus(MatchHypothesis hypothesis) {
    String sql = "UPDATE match_hypotheses SET status = ?, justification = ? WHERE hypothesis_id = ?";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        pstmt.setString(1, hypothesis.getStatus().name());
        pstmt.setString(2, hypothesis.getJustification());
        pstmt.setObject(3, hypothesis.getHypothesisId());
        pstmt.executeUpdate();
    } catch (SQLException e) { e.printStackTrace(); }
}

// In ReconciliationService.java — rejectHypothesis():
dataStore.updateHypothesisStatus(hypothesis);  // Add this line
```

---

### ISSUE-10 — Silent Failure When Docker/DB is Unavailable
**Severity:** MEDIUM  
**File:** `src/aval/ui/controller/AppController.java` — `initializeBackend()`

Unchanged. Services remain `null` on DB failure and the user gets no feedback. Clicking "Run" will throw a `NullPointerException`.

**Fix:** Show an error `Alert` in the catch block and disable the run button (see v1 fix code).

---

### ISSUE-11 — No Input Validation in the UI
**Severity:** MEDIUM  
**File:** `src/aval/ui/controller/AppController.java`

Unchanged. No file validation before running the pipeline.

**Fix:** Add `validateInputs()` method and call it at the top of `executePipeline()` (see v1 fix code).

---

### ISSUE-12 — `SemanticMatchingEngine` Uses Fake Rank-Based Confidence Scores
**Severity:** MEDIUM  
**File:** `src/aval/engine/SemanticMatchingEngine.java`

Unchanged. Confidence is `0.85 - (rank * 0.05)`, not actual cosine similarity from pgvector.

**Fix:** Modify `DataStore.findSimilarBankTransactions()` to return the cosine similarity score alongside each result and use it as the confidence value (see v1 for full SQL change).

---

### ISSUE-13 — `PDFBankStatementParser.extractRawRows()` Returns Empty List
**Severity:** LOW  
**File:** `src/aval/parser/PDFBankStatementParser.java`

Unchanged. Violates the `DocumentParser<T>` interface contract.

**Fix:** (see v1 fix code — reuse the PDF text parsing logic)

---

### ISSUE-15 — `MatchingConfig` Thresholds Are Dead Code
**Severity:** LOW  
**File:** `src/aval/ui/controller/AppController.java`

Unchanged. The hard-coded `0.8` threshold is still used instead of `config.getAutoConfirmThreshold()`. Note: with the AppController truncation, the auto-approve loop from v1 is missing entirely. The new UI instead uses manual approve/reject buttons, which actually makes this less critical — but `MatchingConfig` is still never referenced.

---

### ISSUE-16 — Missing `toString()` on `MatchHypothesis` and `ReconciliationRecord`
**Severity:** LOW  
**Files:** `src/aval/domain/ai/MatchHypothesis.java`, `src/aval/domain/ai/ReconciliationRecord.java`

Unchanged. This matters more now that hypotheses are displayed in a `ListView` and `TableView` — any list cell not using a custom factory will fall back to the object's `toString()`.

---

### ISSUE-18 — No Named Volume for PostgreSQL Data in `compose.yml`
**Severity:** LOW  
**File:** `compose.yml`

Unchanged.

---

## ORDERED FIX CHECKLIST (updated)

Work strictly top to bottom — the compile errors (NEW-01, NEW-02, NEW-03) must be fixed before anything else will run.

```
[ ] NEW-01   Complete truncated DataStore.java — close saveFinancialDataset(), restore missing methods (saveRawTransactions, saveStandardizedTransactions, saveMatchHypotheses, saveReconciliationRecords, findSystemUserById)
[ ] NEW-02   Complete truncated AppController.java — close the lambda, add anomaly call, add report call, add catch/finally, restore log() and getView() methods
[ ] NEW-03   Add getters to ClientOrganization, ReconciliationWorkspace, and FinancialDataset (getStatus, getImportDate)
[ ] NEW-05   Add 3 missing tables to db-init/01-init.sql, then docker compose down -v && up -d
[ ] ISSUE-07 Fix RawInternalLedger.getSourceType() → INTERNAL_EXCEL (1 line)
[ ] NEW-04   Confirm anomalyEngine.identifyAnomalies() is wired into executePipeline() (handled by NEW-02 fix)
[ ] ISSUE-03 Implement consolidateMultiSource() in ReconciliationService
[ ] ISSUE-08 Wire UC1 (ClientOrganization + Workspace creation) into executePipeline()
[ ] ISSUE-09 Persist hypothesis rejection: add updateHypothesisStatus() to DataStore, call it in rejectHypothesis()
[ ] ISSUE-10 Show error Alert on DB connection failure in initializeBackend()
[ ] ISSUE-11 Add input validation before running pipeline
[ ] ISSUE-13 Implement extractRawRows() in PDFBankStatementParser
[ ] ISSUE-12 Return actual cosine similarity from DataStore and use in SemanticMatchingEngine
[ ] ISSUE-16 Add toString() to MatchHypothesis and ReconciliationRecord
[ ] ISSUE-18 Add named volume to compose.yml
```

---

## FILES REQUIRING CHANGES (updated)

| File | Issues | Priority |
|---|---|---|
| `src/aval/persistence/DataStore.java` | NEW-01, NEW-03, ISSUE-09, ISSUE-12 | 🔴 CRITICAL |
| `src/aval/ui/controller/AppController.java` | NEW-02, NEW-04, ISSUE-08, ISSUE-10, ISSUE-11 | 🔴 CRITICAL |
| `src/aval/domain/core/ClientOrganization.java` | NEW-03 | 🔴 CRITICAL |
| `src/aval/domain/core/ReconciliationWorkspace.java` | NEW-03 | 🔴 CRITICAL |
| `src/aval/domain/ingestion/FinancialDataset.java` | NEW-03 | 🔴 CRITICAL |
| `db-init/01-init.sql` | NEW-05 | 🔴 CRITICAL |
| `src/aval/domain/ingestion/RawInternalLedger.java` | ISSUE-07 | 🟠 HIGH |
| `src/aval/service/ReconciliationService.java` | ISSUE-03, ISSUE-09 | 🟠 HIGH |
| `src/aval/engine/SemanticMatchingEngine.java` | ISSUE-12 | 🟡 MEDIUM |
| `src/aval/parser/PDFBankStatementParser.java` | ISSUE-13 | 🟡 MEDIUM |
| `src/aval/domain/ai/MatchHypothesis.java` | ISSUE-16 | 🟢 LOW |
| `src/aval/domain/ai/ReconciliationRecord.java` | ISSUE-16 | 🟢 LOW |
| `compose.yml` | ISSUE-18 | 🟢 LOW |

---

## SCORE IMPACT SUMMARY

| Rubric Category | v1 Estimate | v2 Estimate | Change |
|---|---|---|---|
| User Interface | 4–7 | 7 | ↑ AppController now has multi-section UI with hypothesis table and manual override |
| Business Logic | 11 | 4–11* | ↓ Will not compile in current state; if fixed, back to ~11 |
| OOP Principles | 11–15 | 3–15* | ↓ Missing getters are a compile-breaking encapsulation failure |
| Database | 7 | 4–7* | ↓ New tables referenced but not defined; truncated DataStore |
| Architecture & Integration | 7–10 | 7–10 | → Unchanged |
| Use Cases | 7 | 7 | → UC10 engine exists but not wired; UC9 still stub |
| Design–Code Consistency | 7 | 7 | → Unchanged |
| Design Patterns | 7–10 | 7–10 | → Unchanged |
| Documentation | 7–10 | 7–10 | → Unchanged |

*\* These scores collapse to the low end if the truncation and compile errors are not fixed before submission. A program that does not compile scores 3/15 on Business Logic and 3/15 on OOP by the rubric's "Poor" tier.*

---

*End of Audit Report v2. Total open issues: 15 (5 Critical, 3 High, 4 Medium, 3 Low). The truncated files are the most urgent — fix those first or nothing runs.*
