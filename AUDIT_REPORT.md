# AVAL AI Reconciliation Engine — Audit Report
**Date:** 2026-04-25  
**Evaluator:** Nia (via Claude AI audit)  
**Purpose:** Pre-submission code audit against project rubric. All issues listed below should be fixed before submission.

---

## RUBRIC SUMMARY & ESTIMATED SCORES

| Category | Max | Estimated | Gap |
|---|---|---|---|
| User Interface | 10 | 4–7 | Missing 3 UI source files |
| Business Logic | 15 | 11 | UC9/UC10 incomplete |
| OOP Principles | 15 | 11–15 | Missing getters on 2 domain classes |
| Database | 10 | 7 | Stub methods, missing tables |
| Architecture & Integration | 10 | 7–10 | Solid, minor gaps |
| Use Cases | 10 | 7 | 2 UCs unimplemented |
| Design–Code Consistency | 10 | 7 | ~70% mapping |
| Design Patterns (GRASP & GoF) | 10 | 7–10 | Well applied |
| Documentation | 10 | 7–10 | Comprehensive |
| **TOTAL** | **100** | **~68–83** | |

---

## CRITICAL ISSUES (Fix These First)

---

### ISSUE-01 — Missing Source Files for 3 UI Controllers
**Severity:** CRITICAL  
**Rubric Impact:** User Interface (up to -6 pts)  
**File(s):** `src/aval/ui/controller/` — missing sources  

**Problem:**  
The compiled `target/classes/` directory contains `.class` files for three controllers that have **no corresponding `.java` source files** in `src/`:
- `IngestionController.class`
- `ReconciliationDashboardController.class`
- `ReportController.class`

The marker will only see `AppController.java` (one screen) and will score the UI at 4/10 or lower. The compiled classes prove these screens were built at some point.

**Fix:**  
Recover the source files from git history, a teammate's machine, or any IDE local history. Run:
```bash
git log --all --full-history -- "src/aval/ui/controller/IngestionController.java"
git log --all --full-history -- "src/aval/ui/controller/ReconciliationDashboardController.java"
git log --all --full-history -- "src/aval/ui/controller/ReportController.java"
```
If they exist in git history, restore them with:
```bash
git checkout <commit-hash> -- src/aval/ui/controller/IngestionController.java
```
If not recoverable, **recreate minimal JavaFX versions** of these screens that at least display the correct UI structure mapped to the SSDs.

---

### ISSUE-02 — UC10 (Identify Financial Anomalies) Has Zero Implementation
**Severity:** CRITICAL  
**Rubric Impact:** Use Cases, Business Logic, Completeness  
**File(s):** None — this use case does not exist in code  

**Problem:**  
UC10 (`IdentifyFinancialAnomalies`) has a full sequence diagram in `/complete_documentation/Sequence Diagrams/UC10_IdentifyFinancialAnomalies.svg` but no corresponding service method, domain logic, or UI trigger anywhere in the codebase.

**Fix:**  
Add a method to `ReconciliationService` (or a new `AnomalyDetectionService`):

```java
// In ReconciliationService.java (or new AnomalyDetectionService.java)

/**
 * UC10 — Identify Financial Anomalies
 * Scans reconciled and unmatched transactions to flag statistical outliers.
 */
public List<StandardizedTransaction> identifyAnomalies(
    List<StandardizedTransaction> transactions,
    double thresholdMultiplier
) {
    if (transactions == null || transactions.isEmpty()) return new ArrayList<>();

    // Compute mean amount
    BigDecimal sum = transactions.stream()
        .map(StandardizedTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal mean = sum.divide(BigDecimal.valueOf(transactions.size()), 4, java.math.RoundingMode.HALF_UP);

    // Compute standard deviation
    BigDecimal variance = transactions.stream()
        .map(t -> t.getAmount().subtract(mean).pow(2))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(transactions.size()), 4, java.math.RoundingMode.HALF_UP);
    double stdDev = Math.sqrt(variance.doubleValue());

    double upperBound = mean.doubleValue() + (thresholdMultiplier * stdDev);
    double lowerBound = mean.doubleValue() - (thresholdMultiplier * stdDev);

    return transactions.stream()
        .filter(t -> {
            double amt = t.getAmount().doubleValue();
            return amt > upperBound || amt < lowerBound;
        })
        .collect(java.util.stream.Collectors.toList());
}
```
Wire this call into the pipeline in `AppController.executePipeline()` after matching, and log the results.

---

### ISSUE-03 — UC9 (Multi-Source Consolidation) Is an Explicit Stub
**Severity:** HIGH  
**Rubric Impact:** Use Cases, Completeness  
**File:** `src/aval/service/ReconciliationService.java` — `consolidateMultiSource()` method  

**Problem:**  
The method body contains only a comment and returns an empty list. The sequence diagram for UC9 exists in the documentation.

```java
// Current code (stub):
public List<MatchHypothesis> consolidateMultiSource(...) {
    List<MatchHypothesis> consolidated = new ArrayList<>();
    // In a real implementation, we would iterate through pairs of datasets...
    return consolidated;
}
```

**Fix:**  
Replace the stub with a working loop that cross-matches pairs of datasets:

```java
public List<MatchHypothesis> consolidateMultiSource(
    ReconciliationWorkspace workspace,
    List<List<StandardizedTransaction>> multipleDatasets
) {
    List<MatchHypothesis> consolidated = new ArrayList<>();
    if (multipleDatasets == null || multipleDatasets.size() < 2) return consolidated;

    // Cross-match each dataset pair
    for (int i = 0; i < multipleDatasets.size() - 1; i++) {
        for (int j = i + 1; j < multipleDatasets.size(); j++) {
            List<MatchHypothesis> pairHypotheses = matchingEngine.generateHypotheses(
                multipleDatasets.get(i),
                multipleDatasets.get(j)
            );
            consolidated.addAll(pairHypotheses);
        }
    }
    dataStore.saveMatchHypotheses(consolidated);
    return consolidated;
}
```

---

## HIGH PRIORITY ISSUES

---

### ISSUE-04 — `ClientOrganization` and `ReconciliationWorkspace` Have No Getters
**Severity:** HIGH  
**Rubric Impact:** OOP Principles (encapsulation), Use Cases (UC1)  
**Files:**  
- `src/aval/domain/core/ClientOrganization.java`  
- `src/aval/domain/core/ReconciliationWorkspace.java`  

**Problem:**  
Both classes declare all fields as `private` but provide **no getter methods**. This means no other class can read `orgId`, `name`, `workspaces`, `workspaceId`, `status`, `datasets`, `hypotheses`, or `records`. This breaks encapsulation (fields are effectively dead), prevents UC1 from completing, and makes the domain objects unusable.

**Fix — `ClientOrganization.java`:** Add the following getters:
```java
public UUID getOrgId() { return orgId; }
public String getName() { return name; }
public String getContactMetadata() { return contactMetadata; }
public List<ReconciliationWorkspace> getWorkspaces() { return workspaces; }
public void addWorkspace(ReconciliationWorkspace workspace) { this.workspaces.add(workspace); }
```

**Fix — `ReconciliationWorkspace.java`:** Add the following getters:
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

---

### ISSUE-05 — DataStore Has Many Null-Returning Stub Methods
**Severity:** HIGH  
**Rubric Impact:** Database (up to -3 pts), Use Cases  
**File:** `src/aval/persistence/DataStore.java`  

**Problem:**  
The following methods silently do nothing or return `null`, making UC1, workspace management, and user lookups non-functional:
- `saveClientOrganization(ClientOrganization org)` — empty body
- `findClientOrganizationById(UUID id)` — returns `null`
- `saveReconciliationWorkspace(ReconciliationWorkspace workspace)` — empty body
- `findReconciliationWorkspaceById(UUID id)` — returns `null`
- `saveFinancialDataset(FinancialDataset dataset)` — empty body
- `saveRawTransactions(List<RawTransaction> rawTransactions)` — empty body
- `saveStandardizedTransactions(List<StandardizedTransaction>)` — empty body
- `findSystemUserById(UUID id)` — returns `null`

**Fix — Add real SQL to each stub.** First, add the missing tables to `db-init/01-init.sql` (see ISSUE-06), then implement the methods:

```java
public void saveClientOrganization(ClientOrganization org) {
    String sql = "INSERT INTO client_organizations (org_id, name, contact_metadata) VALUES (?, ?, ?) ON CONFLICT (org_id) DO NOTHING";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        pstmt.setObject(1, org.getOrgId());
        pstmt.setString(2, org.getName());
        pstmt.setString(3, org.getContactMetadata());
        pstmt.executeUpdate();
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

public void saveFinancialDataset(FinancialDataset dataset) {
    String sql = "INSERT INTO financial_datasets (dataset_id, import_date, file_path, source_type, status) VALUES (?, ?, ?, ?, ?) ON CONFLICT (dataset_id) DO NOTHING";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        pstmt.setObject(1, dataset.getDatasetId());
        pstmt.setDate(2, java.sql.Date.valueOf(java.time.LocalDate.now()));
        pstmt.setString(3, dataset.getFilePath());
        pstmt.setString(4, dataset.getSourceType().name());
        pstmt.setString(5, "PARSED");
        pstmt.executeUpdate();
    } catch (SQLException e) {
        e.printStackTrace();
    }
}
```
Apply the same pattern for all remaining stubs.

---

### ISSUE-06 — Missing Database Tables for Core Domain Objects
**Severity:** HIGH  
**Rubric Impact:** Database (naming, CRUD completeness)  
**File:** `db-init/01-init.sql`  

**Problem:**  
The SQL schema only defines 4 tables. There are no tables for `client_organizations`, `system_users`, `reconciliation_workspaces`, or `financial_datasets`, despite these being core domain entities with DataStore methods referencing them.

**Fix — Append to `db-init/01-init.sql`:**
```sql
-- 5. Client Organizations
CREATE TABLE IF NOT EXISTS client_organizations (
    org_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    contact_metadata TEXT
);

-- 6. System Users
CREATE TABLE IF NOT EXISTS system_users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL
);

-- 7. Financial Datasets (tracks uploaded files)
CREATE TABLE IF NOT EXISTS financial_datasets (
    dataset_id UUID PRIMARY KEY,
    import_date DATE,
    file_path TEXT,
    source_type VARCHAR(30),
    status VARCHAR(30)
);

-- 8. Reconciliation Workspaces
CREATE TABLE IF NOT EXISTS reconciliation_workspaces (
    workspace_id UUID PRIMARY KEY,
    org_id UUID REFERENCES client_organizations(org_id),
    status VARCHAR(30) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### ISSUE-07 — Wrong `DataSourceType` in `RawInternalLedger`
**Severity:** MEDIUM  
**Rubric Impact:** Business Logic correctness, Design-Code Consistency  
**File:** `src/aval/domain/ingestion/RawInternalLedger.java` — `getSourceType()` method  

**Problem:**  
```java
@Override
public DataSourceType getSourceType() {
    return DataSourceType.INTERNAL_CSV;  // WRONG — the actual file handled is .xlsx
}
```
The `DataSourceType` enum has `INTERNAL_EXCEL` and `EXTERNAL_PDF`. The ledger parser is `ExcelLedgerParser` and handles `.xlsx` files. Returning `INTERNAL_CSV` is factually incorrect and breaks the conditional logic in `IngestionService.standardize()` which checks `dataset.getSourceType() == DataSourceType.INTERNAL_EXCEL`.

**Fix:**
```java
@Override
public DataSourceType getSourceType() {
    return DataSourceType.INTERNAL_EXCEL;  // Corrected
}
```

---

### ISSUE-08 — UC1 (Create Client Organization Profile) Not Wired Into Pipeline
**Severity:** MEDIUM  
**Rubric Impact:** Use Cases, Completeness  
**File:** `src/aval/ui/controller/AppController.java` — `executePipeline()`  

**Problem:**  
The sequence diagram for UC1 shows that a `ClientOrganization` and `ReconciliationWorkspace` should be created before ingestion begins. The pipeline in `AppController` skips this entirely — it goes straight to ingestion with no client context. The `DataStore.saveClientOrganization()` stub is also never called.

**Fix:**  
At the start of `executePipeline()`, before the ingestion step, add:
```java
// UC1 — Create Client Organization Profile
log("0. Initializing Reconciliation Workspace...");
ClientOrganization client = new ClientOrganization(
    UUID.randomUUID(),
    "Brightline Retail",
    "scenario_01@brightline.com"
);
dataStore.saveClientOrganization(client);

MatchingConfig config = new MatchingConfig();
ReconciliationWorkspace workspace = new ReconciliationWorkspace(
    UUID.randomUUID(), client, config
);
dataStore.saveReconciliationWorkspace(workspace);
log("   -> Workspace created for: " + client.getName());
```
Then pass `workspace` into `reconciliationService.runMatching()` (it already accepts a `ReconciliationWorkspace` parameter but currently receives `null`).

---

## MEDIUM PRIORITY ISSUES

---

### ISSUE-09 — `rejectHypothesis()` Does Not Persist Rejection to DB
**Severity:** MEDIUM  
**Rubric Impact:** Business Logic, Database  
**File:** `src/aval/service/ReconciliationService.java` — `rejectHypothesis()`  

**Problem:**  
The method updates the in-memory `hypothesis.status` to `REJECTED` but includes a comment stating persistence is not implemented:
```java
// Typically, we would update the hypothesis in DataStore here
// For this prototype, we assume hypothesis state is tracked in the session/DB.
```
This means rejected hypotheses are lost on restart and the audit trail is incomplete.

**Fix:**  
Add an update SQL method to `DataStore`:
```java
// In DataStore.java
public void updateHypothesisStatus(MatchHypothesis hypothesis) {
    String sql = "UPDATE match_hypotheses SET status = ?, justification = ? WHERE hypothesis_id = ?";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        pstmt.setString(1, hypothesis.getStatus().name());
        pstmt.setString(2, hypothesis.getJustification());
        pstmt.setObject(3, hypothesis.getHypothesisId());
        pstmt.executeUpdate();
    } catch (SQLException e) {
        e.printStackTrace();
    }
}
```
Then call it in `rejectHypothesis()`:
```java
public void rejectHypothesis(MatchHypothesis hypothesis, SystemUser rejectingUser) {
    hypothesis.setStatus(HypothesisStatus.REJECTED);
    hypothesis.setJustification("Rejected by " + rejectingUser.getUsername());
    dataStore.updateHypothesisStatus(hypothesis);  // Add this line
}
```

---

### ISSUE-10 — `initializeBackend()` Silently Swallows DB Connection Failure
**Severity:** MEDIUM  
**Rubric Impact:** Business Logic robustness  
**File:** `src/aval/ui/controller/AppController.java` — `initializeBackend()`  

**Problem:**  
If the PostgreSQL database is unavailable (Docker not running), the catch block prints a warning but all services (`dataStore`, `ingestionService`, `reconciliationService`, `reportService`) remain `null`. The pipeline will then throw a `NullPointerException` when the user clicks "Run", with no informative error shown to the user.

**Fix:**  
Show an alert dialog and disable the run button if initialization fails:
```java
} catch (Exception e) {
    System.err.println("Failed to initialize database connection.");
    e.printStackTrace();
    // Show user-facing alert
    Platform.runLater(() -> {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Connection Error");
        alert.setHeaderText("Database Unavailable");
        alert.setContentText("Could not connect to PostgreSQL. Ensure Docker is running:\n\ndocker compose up -d\n\nThen restart the application.");
        alert.showAndWait();
        if (runReconciliationBtn != null) runReconciliationBtn.setDisable(true);
    });
}
```

---

### ISSUE-11 — No Input Validation in the UI Layer
**Severity:** MEDIUM  
**Rubric Impact:** Database criterion (input validation for all values taken from user)  
**File:** `src/aval/ui/controller/AppController.java`  

**Problem:**  
The rubric explicitly requires input validation for values taken from the user. The file chooser accepts any `.xlsx` or `.pdf` file without further validation before running the pipeline. There is no check that the selected file is non-empty, readable, or has the expected format before kicking off the pipeline.

**Fix:**  
Add validation before calling the pipeline:
```java
private boolean validateInputs() {
    File ledger = new File(ledgerPath);
    File bank = new File(bankPath);
    
    if (!ledger.exists() || ledger.length() == 0) {
        showValidationError("Invalid Ledger File", "The selected Excel ledger file does not exist or is empty.");
        return false;
    }
    if (!bank.exists() || bank.length() == 0) {
        showValidationError("Invalid Bank Statement", "The selected PDF bank statement does not exist or is empty.");
        return false;
    }
    if (!ledgerPath.toLowerCase().endsWith(".xlsx")) {
        showValidationError("Wrong File Type", "Ledger must be an Excel (.xlsx) file.");
        return false;
    }
    if (!bankPath.toLowerCase().endsWith(".pdf")) {
        showValidationError("Wrong File Type", "Bank statement must be a PDF (.pdf) file.");
        return false;
    }
    return true;
}

private void showValidationError(String header, String content) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Validation Error");
    alert.setHeaderText(header);
    alert.setContentText(content);
    alert.showAndWait();
}
```
Then call `if (!validateInputs()) return;` at the top of `executePipeline()`.

---

### ISSUE-12 — `SemanticMatchingEngine` Uses Rank-Based Confidence, Not Actual Cosine Distance
**Severity:** MEDIUM  
**Rubric Impact:** Business Logic accuracy  
**File:** `src/aval/engine/SemanticMatchingEngine.java`  

**Problem:**  
The confidence score assigned to hypotheses is artificially calculated as `BASE_CONFIDENCE - (i * 0.05)` (e.g., 0.85, 0.80, 0.75) based purely on the rank of the result from the database query. The actual cosine distance from pgvector is never retrieved or used. This means the confidence values are meaningless — they only reflect order, not actual semantic similarity.

**Fix:**  
Modify `DataStore.findSimilarBankTransactions()` to also return the cosine distance by adding it to the SELECT:
```java
// In DataStore.java — update the SELECT to include the distance
String sql =
    "SELECT transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id, " +
    "1 - (embedding <=> ?::vector) AS similarity_score " +  // cosine similarity (1 - distance)
    "FROM standardized_bank " +
    "ORDER BY embedding <=> ?::vector " +
    "LIMIT ?";
```
Return a `Map.Entry<StandardizedTransaction, Double>` or a simple wrapper object containing the score, and use that real score in `SemanticMatchingEngine` instead of the rank formula.

---

### ISSUE-13 — `PDFBankStatementParser.extractRawRows()` Returns Empty List
**Severity:** LOW-MEDIUM  
**Rubric Impact:** OOP/Interface contract  
**File:** `src/aval/parser/PDFBankStatementParser.java`  

**Problem:**  
The `DocumentParser<T>` interface requires `extractRawRows(String filePath)` to be implemented. The PDF parser returns an empty `ArrayList<>()` unconditionally, violating the interface contract.

**Fix:**  
Implement the method to return the parsed PDF lines as raw string arrays (reuse logic from `parse()`):
```java
@Override
public List<String[]> extractRawRows(String filePath) {
    List<String[]> rows = new ArrayList<>();
    try (PDDocument document = Loader.loadPDF(new File(filePath))) {
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                rows.add(new String[]{line.trim()});
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return rows;
}
```

---

## LOW PRIORITY ISSUES

---

### ISSUE-14 — `DataSourceType` Enum Missing `INTERNAL_CSV` Value
**Severity:** LOW  
**File:** `src/aval/common/enums/DataSourceType.java`  

**Problem:**  
`RawInternalLedger.getSourceType()` (before fix) returns `INTERNAL_CSV`, but that value does not exist in the `DataSourceType` enum — only `INTERNAL_EXCEL` and `EXTERNAL_PDF` exist. This causes a compile error if the enum value referenced is wrong. After applying ISSUE-07's fix, this is resolved; however, consider whether `INTERNAL_CSV` might be needed for future datasets.

**Fix:** Either add `INTERNAL_CSV` to the enum for completeness, or confirm ISSUE-07's fix is applied so this is never referenced.

---

### ISSUE-15 — `MatchingConfig` Auto-Confirm Threshold Not Used in Pipeline
**Severity:** LOW  
**File:** `src/aval/domain/core/MatchingConfig.java`, `src/aval/ui/controller/AppController.java`  

**Problem:**  
`MatchingConfig` defines `autoConfirmThreshold = 0.95` and `reviewFloor = 0.70`, but the pipeline in `AppController.executePipeline()` hard-codes its own threshold of `0.8`:
```java
if (h.getConfidenceScore() >= 0.8) { ... }  // Should use MatchingConfig
```
The configuration object is never consulted, making it dead code.

**Fix:**  
After creating `MatchingConfig config = new MatchingConfig()`, use it:
```java
if (h.getConfidenceScore() >= config.getAutoConfirmThreshold()) { ... }
```

---

### ISSUE-16 — No `toString()` or Logging on Key Domain Objects Except `StandardizedTransaction`
**Severity:** LOW  
**Files:** `src/aval/domain/ai/MatchHypothesis.java`, `src/aval/domain/ai/ReconciliationRecord.java`  

**Problem:**  
Only `StandardizedTransaction` implements `toString()`. `MatchHypothesis` and `ReconciliationRecord` print as memory addresses in logs, making debugging difficult.

**Fix:**
```java
// In MatchHypothesis.java
@Override
public String toString() {
    return String.format("MatchHypothesis[%s <-> %s, confidence=%.2f, status=%s]",
        ledgerTransaction, bankTransaction, confidenceScore, status);
}

// In ReconciliationRecord.java
@Override
public String toString() {
    return String.format("ReconciliationRecord[id=%s, reconciledAt=%s, by=%s]",
        recordId, reconciledAt, confirmingUser != null ? confirmingUser.getUsername() : "N/A");
}
```

---

### ISSUE-17 — `INTERNAL_CSV` Referenced in `RawInternalLedger` But Enum Only Has `INTERNAL_EXCEL`
**Severity:** LOW (compile-time risk if not caught)  
**File:** `src/aval/domain/ingestion/RawInternalLedger.java`  

Already covered by ISSUE-07 and ISSUE-14. Listed here separately for completeness so the CLI fixer knows to check both files together.

---

### ISSUE-18 — `compose.yml` Has No Volume for Persistent DB Data
**Severity:** LOW (deployment concern)  
**File:** `compose.yml`  

**Problem:**  
The PostgreSQL service has no named volume for its data directory. The `db-init` volume only mounts init scripts. If the container is removed, all DB data is lost.

**Fix:**  
Add a named volume to `compose.yml`:
```yaml
services:
  db:
    ...
    volumes:
      - ./db-init:/docker-entrypoint-initdb.d
      - aval_postgres_data:/var/lib/postgresql/data  # Add this

volumes:
  aval_postgres_data:
```

---

## ORDERED FIX CHECKLIST FOR CLI CODER

Work top to bottom. Each fix is tagged with the issue number.

```
[ ] ISSUE-01  Recover/recreate source files for IngestionController, ReconciliationDashboardController, ReportController
[ ] ISSUE-07  Fix RawInternalLedger.getSourceType() to return INTERNAL_EXCEL (1 line)
[ ] ISSUE-04  Add getters to ClientOrganization and ReconciliationWorkspace
[ ] ISSUE-06  Add 4 missing tables to db-init/01-init.sql
[ ] ISSUE-05  Implement DataStore stub methods with real SQL
[ ] ISSUE-02  Implement identifyAnomalies() for UC10
[ ] ISSUE-03  Implement consolidateMultiSource() for UC9
[ ] ISSUE-08  Wire UC1 (ClientOrganization + Workspace creation) into executePipeline()
[ ] ISSUE-09  Persist hypothesis rejection to DB in rejectHypothesis()
[ ] ISSUE-10  Show error alert on DB connection failure in initializeBackend()
[ ] ISSUE-11  Add input validation before running pipeline
[ ] ISSUE-13  Implement extractRawRows() in PDFBankStatementParser
[ ] ISSUE-15  Use MatchingConfig.getAutoConfirmThreshold() instead of hard-coded 0.8
[ ] ISSUE-12  Return actual cosine similarity from DataStore and use it in SemanticMatchingEngine
[ ] ISSUE-16  Add toString() to MatchHypothesis and ReconciliationRecord
[ ] ISSUE-18  Add named volume for PostgreSQL persistence in compose.yml
```

---

## FILES REQUIRING CHANGES (Summary)

| File | Issues |
|---|---|
| `src/aval/ui/controller/IngestionController.java` | ISSUE-01 (create) |
| `src/aval/ui/controller/ReconciliationDashboardController.java` | ISSUE-01 (create) |
| `src/aval/ui/controller/ReportController.java` | ISSUE-01 (create) |
| `src/aval/ui/controller/AppController.java` | ISSUE-08, ISSUE-10, ISSUE-11, ISSUE-15 |
| `src/aval/domain/ingestion/RawInternalLedger.java` | ISSUE-07 |
| `src/aval/domain/core/ClientOrganization.java` | ISSUE-04 |
| `src/aval/domain/core/ReconciliationWorkspace.java` | ISSUE-04 |
| `src/aval/persistence/DataStore.java` | ISSUE-05, ISSUE-09, ISSUE-12 |
| `src/aval/service/ReconciliationService.java` | ISSUE-02, ISSUE-03, ISSUE-09 |
| `src/aval/engine/SemanticMatchingEngine.java` | ISSUE-12 |
| `src/aval/parser/PDFBankStatementParser.java` | ISSUE-13 |
| `src/aval/domain/ai/MatchHypothesis.java` | ISSUE-16 |
| `src/aval/domain/ai/ReconciliationRecord.java` | ISSUE-16 |
| `db-init/01-init.sql` | ISSUE-06 |
| `compose.yml` | ISSUE-18 |

---

*End of Audit Report. Total issues identified: 18 (3 Critical, 5 High, 5 Medium, 5 Low).*
