# AVAL AI Reconciliation Engine — Audit Report (v3)
**Date:** 2026-04-26 (updated after 9-commit pull)  
**Evaluator:** Nia (via Claude AI audit)  
**Purpose:** Pre-submission code audit. Reflects state after latest batch of commits.

---

## ✅ EVERYTHING FIXED IN THIS PULL

Every critical and high issue from v2 has been resolved. The project now compiles.

| Issue | Commit | Status |
|---|---|---|
| NEW-01 DataStore.java truncated | `refactor: resolve compilation errors` | ✅ Fixed — complete, all methods present |
| NEW-02 AppController.java truncated | `refactor: resolve compilation errors` | ✅ Fixed — complete, all methods present |
| NEW-03 Missing getters (compile error) | `fix: add missing domain entity getters` | ✅ Fixed — ClientOrganization, ReconciliationWorkspace, FinancialDataset all have getters |
| NEW-04 AnomalyDetectionEngine not wired | `refactor: resolve compilation errors` | ✅ Fixed — called in executePipeline() as UC10 |
| NEW-05 Missing DB tables | `feat: implement raw transaction persistence` | ✅ Fixed — 9 tables now in schema (added system_user, client_organization, reconciliation_workspace, financial_dataset, raw_transactions) |
| ISSUE-03 UC9 stub | `refactor: resolve compilation errors` | ✅ Fixed — real cross-pair matching loop implemented |
| ISSUE-07 Wrong DataSourceType | `refactor: resolve compilation errors` | ✅ Fixed — INTERNAL_EXCEL returned correctly |
| ISSUE-08 UC1 not wired | `refactor: resolve compilation errors` | ✅ Fixed — ClientOrganization + Workspace created at pipeline start |
| ISSUE-09 Rejection not persisted | `refactor: resolve compilation errors` | ✅ Fixed — calls `dataStore.saveMatchHypotheses()` with REJECTED status |
| ISSUE-10 Silent DB failure | `feat: add UI input validation and error alerts` | ✅ Fixed — Alert dialog shown on connection error |
| ISSUE-11 No input validation | `feat: add UI input validation and error alerts` | ✅ Fixed — validateInputs() runs before pipeline |
| ISSUE-12 Fake confidence scores | `feat: implement distance-based AI confidence scores` | ✅ Fixed — real cosine distance from pgvector used |
| ISSUE-13 extractRawRows() empty | `refactor: resolve compilation errors` | ✅ Fixed — parses PDF lines into rows |
| ISSUE-15 MatchingConfig unused | `feat: highlight high-confidence matches in UI` | ✅ Fixed — 0.95 threshold used for green row highlighting |
| ISSUE-16 Missing toString() | `fix: implement toString methods` | ✅ Fixed — MatchHypothesis and ReconciliationRecord both have toString() |
| ISSUE-18 No DB volume | `fix: add persistent storage volume for PostgreSQL` | ✅ Fixed — `aval_db_data` named volume in compose.yml |

---

## REMAINING ISSUES

The project is in good shape. What's left is medium and low severity.

---

### ISSUE-A — UC11 (Generate Report) Is Never Triggered
**Severity:** MEDIUM  
**Rubric Impact:** Use Cases, Completeness  
**File:** `src/aval/ui/controller/AppController.java`

**Problem:**  
`ReportService` is instantiated in `initializeBackend()` but `generateReconciliationReport()` is never called anywhere in the pipeline or the UI. The pipeline ends with `"PIPELINE PROCESSING COMPLETE. Pending Human Review."` — there is no "Generate Report" button and no auto-generation step. UC11's sequence diagram exists and the `ReportService` logic is fully written, but it is unreachable from the running application.

**Fix:**  
Add a "Generate Report" button to the UI, or call the report at the end of `executePipeline()` after the anomaly detection block. Minimum fix — add this inside the `Platform.runLater()` block after the anomaly detection:

```java
// UC11 — Generate Report
log("5. Generating Reconciliation Report (UC11)...");
try {
    List<ReconciliationRecord> approvedRecords = new ArrayList<>(); // Records approved in this session
    reportService.generateReconciliationReport(
        approvedRecords,
        new ArrayList<>(unmatchedLedgerList.getItems()),
        new ArrayList<>(unmatchedBankList.getItems()),
        "data/scenario_01_retail_ecommerce/reconciliation_report_output.csv"
    );
    log("   -> Report saved to: data/scenario_01_retail_ecommerce/reconciliation_report_output.csv");
} catch (Exception ex) {
    log("   -> Report generation failed: " + ex.getMessage());
}
```

Or, for a better UX, add a standalone button that generates the report after the user finishes reviewing matches:
```java
Button generateReportBtn = new Button("Generate Report (UC11)");
generateReportBtn.setOnAction(e -> {
    try {
        reportService.generateReconciliationReport(
            new ArrayList<>(),
            unmatchedLedgerList.getItems(),
            unmatchedBankList.getItems(),
            "data/scenario_01_retail_ecommerce/reconciliation_report_output.csv"
        );
        log("Report generated.");
    } catch (Exception ex) { log("Report error: " + ex.getMessage()); }
});
```

---

### ISSUE-B — Workspace Passed as `null` to `runMatching()`
**Severity:** MEDIUM  
**Rubric Impact:** Business Logic, Design–Code Consistency  
**File:** `src/aval/ui/controller/AppController.java` — `executePipeline()`

**Problem:**  
UC1 now correctly creates and persists a `ReconciliationWorkspace` object, but it is a local variable and never passed downstream. `runMatching()` is called with `null`:
```java
List<MatchHypothesis> hypotheses = reconciliationService.runMatching(null, stdLedger, stdBank);
//                                                                    ^^^^ should be `workspace`
```
The `ReconciliationService.runMatching()` signature accepts a workspace parameter specifically for this purpose, but it currently receives nothing useful.

**Fix:**  
Declare `workspace` before the thread so it's in scope, then pass it in:
```java
// Before the thread starts (or make it effectively final):
final ReconciliationWorkspace workspace = new ReconciliationWorkspace(UUID.randomUUID(), client, new aval.domain.core.MatchingConfig());
dataStore.saveReconciliationWorkspace(workspace);

// Then inside the thread:
List<MatchHypothesis> hypotheses = reconciliationService.runMatching(workspace, stdLedger, stdBank);
```

---

### ISSUE-C — `saveRawTransactions()` Has Real SQL But Is Never Called
**Severity:** MEDIUM  
**Rubric Impact:** Database (CRUD completeness — raw transactions are never persisted)  
**Files:** `src/aval/persistence/DataStore.java`, `src/aval/service/IngestionService.java`

**Problem:**  
`DataStore.saveRawTransactions()` was implemented this pull with a real SQL batch INSERT into the `raw_transactions` table. However, `IngestionService.ingestFile()` never calls it. The raw transactions are parsed into memory and the `FinancialDataset` is saved, but the individual raw transaction rows never reach the database.

**Fix:**  
In `IngestionService.ingestFile()`, after parsing, add:
```java
public FinancialDataset ingestFile(String filePath, DataSourceType sourceType) {
    DocumentParser<? extends FinancialDataset> parser = createParser(sourceType);
    if (parser.validate(filePath)) {
        FinancialDataset dataset = parser.parse(filePath);
        this.dataStore.saveFinancialDataset(dataset);
        this.dataStore.saveRawTransactions(dataset.getRawTransactions()); // Add this line
        return dataset;
    }
    return null;
}
```

---

### ISSUE-D — `saveFinancialDataset()` Always Sets `workspace_id` to `null`
**Severity:** LOW-MEDIUM  
**Rubric Impact:** Database integrity  
**File:** `src/aval/persistence/DataStore.java` — `saveFinancialDataset()`

**Problem:**  
```java
pstmt.setObject(2, null);  // workspace_id is always null
```
The `financial_dataset` table has `workspace_id UUID REFERENCES reconciliation_workspace(workspace_id)`, but it is always inserted as `null`. Datasets are never linked to their workspace in the database, breaking the relational integrity the schema was designed for.

**Fix:**  
Pass the workspace ID into the save call. Either add it as a parameter, or overload the method:
```java
public void saveFinancialDataset(FinancialDataset dataset, UUID workspaceId) {
    String sql = "INSERT INTO financial_dataset (dataset_id, workspace_id, file_path, source_type, status, import_date) VALUES (?, ?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        pstmt.setObject(1, dataset.getDatasetId());
        pstmt.setObject(2, workspaceId);  // Pass actual workspace ID
        // ... rest unchanged
    }
}
```
Then in `AppController.executePipeline()`:
```java
dataStore.saveFinancialDataset(ledgerDataset, workspace.getWorkspaceId());
dataStore.saveFinancialDataset(bankDataset, workspace.getWorkspaceId());
```

---

### ISSUE-E — UC12 (Transaction History) Has No UI View
**Severity:** LOW  
**Rubric Impact:** Use Cases, Completeness  
**Files:** `src/aval/ui/controller/AppController.java`

**Problem:**  
UC12 (Create and Maintain Verified Transaction History) has a sequence diagram in the documentation. Records are saved to `reconciliation_records` in the DB when approved, which is the "maintain" side. However, there is no way for the user to *view* the history — no table, no screen, no query. The "Create" side is done; the "View/Maintain" side is absent.

**Fix:**  
Add a "View History" button that queries the DB and displays past reconciliation records. Minimum viable version — add a button to the UI that calls a new DataStore method:
```java
// In DataStore.java
public List<String> getReconciliationHistory() {
    List<String> results = new ArrayList<>();
    String sql = "SELECT rr.record_id, rr.reconciled_at, mh.confidence_score, mh.match_type " +
                 "FROM reconciliation_records rr JOIN match_hypotheses mh ON rr.hypothesis_id = mh.hypothesis_id " +
                 "ORDER BY rr.reconciled_at DESC LIMIT 50";
    try (PreparedStatement pstmt = connection.prepareStatement(sql);
         ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
            results.add(String.format("[%s] Type: %s | Confidence: %.2f",
                rs.getTimestamp("reconciled_at"), rs.getString("match_type"), rs.getDouble("confidence_score")));
        }
    } catch (SQLException e) { e.printStackTrace(); }
    return results;
}
```

---

### ISSUE-F — Three Unused Imports in AppController
**Severity:** LOW  
**Rubric Impact:** Code quality  
**File:** `src/aval/ui/controller/AppController.java`

**Problem:**  
Three imports are declared but never used, which generates compiler warnings:
```java
import aval.engine.RuleBasedMatchingEngine;              // Never instantiated
import javafx.scene.control.cell.PropertyValueFactory;   // Not used (lambda factories used instead)
import javafx.stage.Stage;                               // Not used
```

**Fix:**  
Remove those three import lines.

---

### ISSUE-G — `consolidateMultiSource()` Missing Null Check
**Severity:** LOW  
**Rubric Impact:** Business Logic robustness  
**File:** `src/aval/service/ReconciliationService.java`

**Problem:**  
The UC9 implementation checks `multipleDatasets.size() < 2` but will throw a `NullPointerException` if `null` is passed in:
```java
if (multipleDatasets.size() < 2) return consolidated;  // NPE if multipleDatasets is null
```

**Fix (1 line):**
```java
if (multipleDatasets == null || multipleDatasets.size() < 2) return consolidated;
```

---

## FINAL CHECKLIST

```
[ ] ISSUE-A  Add "Generate Report" button or auto-call reportService.generateReconciliationReport() in pipeline (UC11)
[ ] ISSUE-B  Pass `workspace` object instead of `null` into reconciliationService.runMatching()
[ ] ISSUE-C  Call dataStore.saveRawTransactions() in IngestionService.ingestFile() after parsing
[ ] ISSUE-D  Pass workspace_id into saveFinancialDataset() instead of null
[ ] ISSUE-E  Add a "View History" button / query to show reconciliation_records (UC12)
[ ] ISSUE-F  Remove 3 unused imports in AppController (RuleBasedMatchingEngine, PropertyValueFactory, Stage)
[ ] ISSUE-G  Add null check to consolidateMultiSource() before .size() call
```

---

## UPDATED SCORE ESTIMATE

| Rubric Category | v1 | v2 | v3 | Notes |
|---|---|---|---|---|
| User Interface | 4–7 | 7 | **7–10** | Full multi-section UI, hypothesis table, approve/reject, manual override, validation, error alerts |
| Business Logic | 11 | 4–11* | **11–15** | Compiles, pipeline runs end-to-end; UC11 not auto-triggered drops it from 15 |
| OOP Principles | 11–15 | 3–15* | **15** | All interfaces, inheritance, polymorphism, encapsulation now correct |
| Database | 7 | 4–7* | **7–10** | 9 tables, real CRUD; workspace_id null and raw_tx not saved prevent full 10 |
| Architecture & Integration | 7–10 | 7–10 | **10** | Clean 3-layer separation, all integrated and functioning |
| Use Cases | 7 | 7 | **7–10** | UC1–UC10 implemented; UC11 not triggered, UC12 no history view |
| Design–Code Consistency | 7 | 7 | **7–10** | Near-complete mapping; UC11/UC12 gaps remain |
| Design Patterns | 7–10 | 7–10 | **10** | All GRASP + GoF patterns correctly applied |
| Documentation | 7–10 | 7–10 | **7–10** | Unchanged, still comprehensive |
| **TOTAL** | ~68–83 | ~40–83* | **~81–90** | |

*v2 range was wide due to compile-blocking issues. Those are now resolved.*

---

## BOTTOM LINE

The project is in **submittable condition**. It compiles, the pipeline runs, all major use cases have business logic, the database schema is complete, and OOP patterns are well applied throughout. The 7 remaining issues are all medium-to-low severity polish items. Fixing ISSUE-A (UC11 report button) and ISSUE-B (passing workspace) should be the priority — those have the most direct rubric impact. The rest are correctness details that won't swing the grade significantly but are worth a quick fix.

---

*End of Audit Report v3. Open issues: 7 (0 Critical, 0 High, 3 Medium, 4 Low).*
