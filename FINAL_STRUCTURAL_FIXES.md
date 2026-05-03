# FINAL_STRUCTURAL_FIXES
## AVAL AI Reconciliation Engine — Structural Audit & Agent Fix Protocol

> **Project:** AVAL-AI-Reconciliation-Engine  
> **Date:** May 2026  
> **Issues Covered:** 9 High / Critical Severity  
> **Status:** ALL ISSUES REQUIRE FIX

---

## Will These 9 Fixes Make the App Fully Functional?

**For the current dataset (scenario_01): YES — the end-to-end flow will work.**

The full pipeline — Login → Select Client → Upload Ledger → Upload PDF → Reconcile → Manual Review → Report Generation → Email — is implemented and will function correctly after these 9 fixes. `ReportController` and `EmailService` are fully built. `AnomalyDetectionEngine` is wired into the pipeline.

**For production use beyond scenario_01: NOT YET.**

The following medium-severity issues remain after these 9 fixes and will cause problems with other datasets or in production:

| Remaining Issue | Impact |
|---|---|
| `consolidateMultiSource()` is incomplete | Multi-source reconciliation (3+ datasets) produces wrong results — B↔C pairs are never matched |
| XLSX parser breaks on non-standard files | Wrong sheet index, narrow column keyword matching, limited date formats |
| `MatchingConfig` thresholds are hardcoded | All clients share the same 95% auto-confirm and 70% review floor — not configurable per workspace |
| `AnomalyDetectionEngine.findDuplicates()` is O(n²) | Will be very slow on datasets with 10,000+ transactions |

These are tracked separately. Resolve the 9 issues in this document first.

---

## Agent Protocol

For every issue, follow these steps **in order**. Do not skip. Do not combine fixes across issues.

1. **LOCATE** — Open the file and confirm the exact lines cited exist as described.
2. **VERIFY** — Confirm the bug is present by checking the specific condition described.
3. **FIX** — Apply the fix precisely as described. Do not refactor beyond what is specified.
4. **TEST** — Create the specified test file under `src/test/java/aval/`. Write exactly the described tests.
5. **VALIDATE** — Run the tests. Confirm they pass.
6. **CONFIRM** — State: *"Issue N is resolved. Test passed."* before moving to the next issue.

> **DO NOT** skip VERIFY. **DO NOT** combine fixes. **DO NOT** move to the next issue without confirming the current one.

---

## Resolution Checklist

| # | Severity | Issue | File(s) | Test Class | Status |
|---|---|---|---|---|---|
| 1 | CRITICAL | Missing Workspace Retrieval | `RegistryController`, `DataStore` | `WorkspaceRetrievalTest` | [ ] OPEN |
| 2 | CRITICAL | MainUIContext Thread Safety + State Leak | `MainUIContext` | `MainUIContextTest` | [ ] OPEN |
| 3 | HIGH | Silent Failure in openWorkspace() | `RegistryController` | `RegistryControllerTest` | [ ] OPEN |
| 4 | HIGH | No JDBC Transaction Wrapping | `IngestionService`, `ReconciliationService`, `DataStore` | `TransactionConsistencyTest` | [ ] OPEN |
| 5 | HIGH | Fake System User UUID | `ReconciliationService` | `ReconciliationServiceSystemUserTest` | [ ] OPEN |
| 6 | HIGH | No Ollama Health Check | `LangChain4jVectorizationEngine`, `Main` | `VectorizationHealthCheckTest` | [ ] OPEN |
| 7 | HIGH | PDF Parser `^` Anchor Regex | `PDFBankStatementParser` | `PDFBankStatementParserTest` | [ ] OPEN |
| 8 | HIGH | Reconcile Button Enabled on Empty Lists | `ReconController` | `ReconButtonStateTest` | [ ] OPEN |
| 9 | HIGH | ManualCheck Silent Approval Loss | `ManualCheckController` | `ManualCheckControllerTest` | [ ] OPEN |

---

## Issue 1 — Missing Workspace Retrieval: Session Never Resumes

**Severity:** CRITICAL  
**Files:** `RegistryController.java`, `DataStore.java`  
**Key Lines:** `RegistryController.java:185–189` | `DataStore.java` (entire read surface)  
**Symptom:** Every app launch prompts the user to re-upload their ledger even though data already exists in PostgreSQL.

### LOCATE

Open `RegistryController.java` and find `openWorkspace()` (approx. line 182). Then open `DataStore.java`.

### VERIFY

Confirm ALL of the following are true:

- `openWorkspace()` calls `new ReconciliationWorkspace(UUID.randomUUID(), ...)` unconditionally — no prior check for existing workspaces.
- `DataStore.java` has NO method named `findWorkspacesByClientId`, `findActiveWorkspaceForClient`, or any equivalent.
- `DataStore.java` has NO method to retrieve `standardized_ledger`, `standardized_bank`, or `financial_dataset` rows by workspace ID.
- `ReconController.initialize()` calls `populateLedgerTable(List.of())` — hardcoded empty list, never queries the DB.

### FIX

**Step 1 — Add workspace retrieval to `DataStore.java`:**

```java
public Optional<ReconciliationWorkspace> findLatestWorkspaceForClient(UUID clientId) {
    String sql = "SELECT workspace_id, status FROM reconciliation_workspace"
               + " WHERE org_id = ? ORDER BY created_at DESC LIMIT 1";
    try (Connection c = dataSource.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setObject(1, clientId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            ReconciliationWorkspace ws = new ReconciliationWorkspace(
                (UUID) rs.getObject("workspace_id"), client, new MatchingConfig());
            return Optional.of(ws);
        }
    } catch (SQLException e) { throw new RuntimeException(e); }
    return Optional.empty();
}
```

**Step 2 — Add transaction retrieval to `DataStore.java`:**

```java
public List<StandardizedTransaction> getLedgerTransactionsForWorkspace(UUID workspaceId) {
    String sql = "SELECT sl.* FROM standardized_ledger sl"
               + " JOIN financial_dataset fd ON sl.source_dataset_id = fd.dataset_id"
               + " WHERE fd.workspace_id = ?";
    // Map ResultSet rows to StandardizedTransaction objects and return list
}

public List<StandardizedTransaction> getBankTransactionsForWorkspace(UUID workspaceId) {
    String sql = "SELECT sb.* FROM standardized_bank sb"
               + " JOIN financial_dataset fd ON sb.source_dataset_id = fd.dataset_id"
               + " WHERE fd.workspace_id = ?";
    // Map ResultSet rows to StandardizedTransaction objects and return list
}
```

**Step 3 — Update `RegistryController.openWorkspace()` to resume before creating:**

```java
private void openWorkspace(ClientOrganization client) {
    DataStore ds = MainUIContext.getInstance().getDataStore();
    ReconciliationWorkspace workspace;
    Optional<ReconciliationWorkspace> existing =
        ds.findLatestWorkspaceForClient(client.getOrgId());
    if (existing.isPresent()) {
        workspace = existing.get();  // Resume existing session
    } else {
        workspace = new ReconciliationWorkspace(
            UUID.randomUUID(), client, new MatchingConfig());
        ds.saveReconciliationWorkspace(workspace);  // Only save when truly new
    }
    MainUIContext ctx = MainUIContext.getInstance();
    ctx.setActiveClient(client);
    ctx.setActiveWorkspace(workspace);
    navigateTo("Workspace.fxml");
}
```

**Step 4 — Update `ReconController.initialize()` to load existing data:**

```java
public void initialize() {
    MainUIContext ctx = MainUIContext.getInstance();
    DataStore ds = ctx.getDataStore();
    UUID wsId = ctx.getActiveWorkspace().getWorkspaceId();
    List<StandardizedTransaction> ledger = ds.getLedgerTransactionsForWorkspace(wsId);
    List<StandardizedTransaction> bank   = ds.getBankTransactionsForWorkspace(wsId);
    if (!ledger.isEmpty()) {
        ctx.setStandardizedLedgerTransactions(ledger);
        populateLedgerTable(ledger);
    }
    if (!bank.isEmpty()) {
        ctx.setStandardizedBankTransactions(bank);
        showBankState("ingested");
    } else {
        showBankState("dropzone");
    }
    updateReconButtonState();
}
```

### TEST

Create `src/test/java/aval/persistence/WorkspaceRetrievalTest.java`:

```java
public class WorkspaceRetrievalTest {

    // Use an in-memory H2 datasource mirroring the schema for this test

    @Test
    public void testFindLatestWorkspaceForClient_returnsExisting() {
        // Insert a client org and workspace row into test DB
        // Call ds.findLatestWorkspaceForClient(clientId)
        // Assert Optional is present and workspace ID matches inserted row
    }

    @Test
    public void testFindLatestWorkspaceForClient_returnsEmpty_whenNone() {
        // Call with a random clientId that has no workspace
        // Assert Optional is empty
    }

    @Test
    public void testGetLedgerTransactionsForWorkspace_returnsRows() {
        // Insert financial_dataset and standardized_ledger rows linked to a workspace
        // Call ds.getLedgerTransactionsForWorkspace(workspaceId)
        // Assert returned list size matches inserted row count
    }
}
```

### VALIDATE

- Run `WorkspaceRetrievalTest` — all 3 tests must pass green.
- Manually start the app and open a client that already has uploaded data.
- Confirm the ledger table populates automatically without a new upload prompt.
- Confirm the Reconcile button is enabled if both ledger and bank data exist.

---

## Issue 2 — MainUIContext Thread-Unsafe Singleton + State Leak Between Sessions

**Severity:** CRITICAL  
**File:** `MainUIContext.java`  
**Key Lines:** Lines 61–66 (singleton getter) | Lines 33–51 (fields) | Line 34 (Object type)  
**Symptom:** Concurrent access can produce two distinct context instances. Stale data from a previous workspace session bleeds into the next one.

### LOCATE

Open `MainUIContext.java`. Find `getInstance()` and the field declarations at the top of the class.

### VERIFY

- `getInstance()` uses a plain `if (instance == null)` check with no `synchronized` block.
- The static field `instance` is NOT declared `volatile`.
- Field `workspaceController` is declared as `Object` with a comment acknowledging the anti-pattern.
- `WorkspaceController.handleExit()` clears context fields individually with no `try/finally` — if one throws, subsequent fields are never cleared.
- List getters (`getPendingHypotheses()`, etc.) return the raw reference with no null guard.

### FIX

**Step 1 — Make singleton thread-safe with eager initialisation:**

```java
// Replace lazy init entirely — thread-safe by class-loading guarantee
private static final MainUIContext instance = new MainUIContext();

public static MainUIContext getInstance() {
    return instance;
}
```

**Step 2 — Add `clearSession()` and call it on workspace exit:**

```java
public synchronized void clearSession() {
    this.activeWorkspace                   = null;
    this.standardizedLedgerTransactions    = null;
    this.standardizedBankTransactions      = null;
    this.pendingHypotheses                 = null;
    this.allHypotheses                     = null;
    this.reconciledRecords                 = null;
    this.workspaceController               = null;
}

// In WorkspaceController.handleExit(), replace manual field clearing with:
MainUIContext.getInstance().clearSession();
```

**Step 3 — Add null-safe getters for all list fields:**

```java
public List<MatchHypothesis> getPendingHypotheses() {
    return pendingHypotheses != null ? pendingHypotheses : Collections.emptyList();
}
// Apply the same pattern to:
// getStandardizedLedgerTransactions(), getStandardizedBankTransactions(),
// getAllHypotheses(), getReconciledRecords()
```

**Step 4 — Replace the `Object workspaceController` field with a typed interface:**

```java
// Create: src/main/java/aval/ui/controller/IWorkspaceController.java
public interface IWorkspaceController {
    void unlockManualCheckTab();
}

// In MainUIContext, change the field type:
private IWorkspaceController workspaceController;

// WorkspaceController implements IWorkspaceController
// Remove all instanceof casts in ReconController
```

### TEST

Create `src/test/java/aval/ui/MainUIContextTest.java`:

```java
public class MainUIContextTest {

    @Test
    public void testSingletonIdentity_sameInstanceAcrossThreads() throws Exception {
        // Spawn 10 threads each calling getInstance()
        // Assert all returned references are identical (==)
    }

    @Test
    public void testClearSession_nullsAllFields() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.setStandardizedLedgerTransactions(List.of(/* mock tx */));
        ctx.setPendingHypotheses(List.of(/* mock hypothesis */));
        ctx.clearSession();
        assertNotNull(ctx.getPendingHypotheses());
        assertEquals(0, ctx.getPendingHypotheses().size());
        assertNotNull(ctx.getStandardizedLedgerTransactions());
        assertEquals(0, ctx.getStandardizedLedgerTransactions().size());
    }

    @Test
    public void testGetters_returnEmptyList_whenNotSet() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();
        assertNotNull(ctx.getPendingHypotheses());
        assertNotNull(ctx.getStandardizedLedgerTransactions());
        assertNotNull(ctx.getStandardizedBankTransactions());
    }
}
```

### VALIDATE

- Run `MainUIContextTest` — all 3 tests must pass.
- Open two clients back-to-back. Confirm the second workspace shows no data from the first.
- Confirm no `NullPointerException` when navigating to a fresh workspace.

---

## Issue 3 — Silent Failure in openWorkspace(): App Navigates Despite DB Error

**Severity:** HIGH  
**File:** `RegistryController.java`  
**Key Lines:** Lines 190–199  
**Symptom:** Workspace save fails silently. The user works in a session that will be permanently lost on restart with no warning.

### LOCATE

Open `RegistryController.java`. Find the `try/catch` block inside `openWorkspace()` where `saveReconciliationWorkspace()` is called.

### VERIFY

- The `catch` block catches `RuntimeException`, logs to stderr, and falls through to `navigateTo("Workspace.fxml")`.
- There is no user-facing alert or dialog.
- There is no `return` or `throw` inside the catch — execution always continues to navigation.

### FIX

```java
try {
    ds.saveReconciliationWorkspace(workspace);
} catch (RuntimeException e) {
    System.err.println("[REGISTRY] Failed to persist workspace: " + e.getMessage());
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Database Error");
    alert.setHeaderText("Could not create workspace");
    alert.setContentText(
        "Failed to save workspace to the database. Please check your database " +
        "connection and try again.\n\nDetail: " + e.getMessage());
    alert.showAndWait();
    return;  // CRITICAL: stop navigation — do not proceed without a persisted workspace
}
navigateTo("Workspace.fxml");  // Only reached if save succeeded
```

### TEST

Create `src/test/java/aval/ui/controller/RegistryControllerTest.java`:

```java
public class RegistryControllerTest {

    @Test
    public void testOpenWorkspace_doesNotNavigate_whenSaveFails() {
        // Mock DataStore.saveReconciliationWorkspace() to throw RuntimeException
        // Call openWorkspace() with a test ClientOrganization
        // Assert navigateTo() was NOT called (use a spy or navigation flag)
        // Assert no exception propagates to the caller
    }
}
```

### VALIDATE

- Run `RegistryControllerTest` — test must pass.
- Stop the Docker database container and attempt to open a client.
- Confirm an error dialog appears and the app does NOT navigate to the workspace view.

---

## Issue 4 — No JDBC Transaction Wrapping: Partial Writes Leave DB Inconsistent

**Severity:** HIGH  
**Files:** `IngestionService.java`, `ReconciliationService.java`, `DataStore.java`  
**Key Lines:** `IngestionService.java:54–55` | `ReconciliationService.java:80–84`  
**Symptom:** If any step in a multi-write sequence fails, the database is left in a partially-written inconsistent state. Example: `financial_dataset` row exists but `raw_transactions` are missing.

### LOCATE

Open `IngestionService.java` lines 54–55. Then open `ReconciliationService.java` lines 80–84.

### VERIFY

- `IngestionService.ingestFile()`: `saveFinancialDataset()` and `saveRawTransactions()` are two separate calls with no shared JDBC connection or transaction boundary.
- `ReconciliationService.runMatching()`: `saveMatchHypotheses()` (line 80) and `saveReconciliationRecords()` (line 83) are two separate calls — if the second fails, hypotheses exist in the DB with no corresponding records.
- Each `DataStore` method opens its own connection and auto-commits immediately — there is no `conn.setAutoCommit(false)` anywhere.

### FIX

**Step 1 — Add a transactional ingestion method to `DataStore.java`:**

```java
public void saveDatasetWithTransactions(FinancialDataset dataset, UUID workspaceId) {
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try {
            saveFinancialDatasetWithConn(conn, dataset, workspaceId);
            saveRawTransactionsWithConn(conn, dataset.getRawTransactions());
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new RuntimeException("Ingestion transaction failed — rolled back.", e);
        }
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}
```

**Step 2 — Add a transactional matching results method to `DataStore.java`:**

```java
public void saveMatchingResults(List<MatchHypothesis> hypotheses,
                                List<ReconciliationRecord> records) {
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try {
            saveMatchHypothesesWithConn(conn, hypotheses);
            if (!records.isEmpty()) {
                saveReconciliationRecordsWithConn(conn, records);
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new RuntimeException("Matching results transaction failed — rolled back.", e);
        }
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}
```

**Step 3 — Update callers:**

- In `IngestionService.ingestFile()`, replace the two separate save calls with `dataStore.saveDatasetWithTransactions(dataset, workspaceId)`.
- In `ReconciliationService.runMatching()`, replace the two separate save calls with `dataStore.saveMatchingResults(candidates, autoRecords)`.

### TEST

Create `src/test/java/aval/persistence/TransactionConsistencyTest.java`:

```java
public class TransactionConsistencyTest {

    @Test
    public void testSaveDataset_rollsBack_whenRawTransactionsSaveFails() {
        // Mock saveRawTransactionsWithConn() to throw SQLException
        // Call saveDatasetWithTransactions()
        // Query financial_dataset table — assert NO row was inserted (rollback worked)
    }

    @Test
    public void testSaveMatchingResults_rollsBack_whenRecordsSaveFails() {
        // Mock saveReconciliationRecordsWithConn() to throw SQLException
        // Call saveMatchingResults()
        // Query match_hypotheses table — assert NO rows were inserted (rollback worked)
    }
}
```

### VALIDATE

- Run `TransactionConsistencyTest` — both tests must pass.
- After a forced failure, query `financial_dataset` and confirm zero orphaned rows exist.

---

## Issue 5 — Fake System User UUID: Audit Trail Broken Across Reconciliation Runs

**Severity:** HIGH  
**File:** `ReconciliationService.java`  
**Key Lines:** Lines 56–63  
**Symptom:** Every call to `runMatching()` creates a new system user with a random UUID. Auto-reconciled records across different sessions are attributed to different phantom users — audit queries cannot link them.

### LOCATE

Open `ReconciliationService.java`. Find the `SystemUser` construction block inside `runMatching()`.

### VERIFY

- The `SystemUser` is constructed with `UUID.randomUUID()` on every invocation.
- This UUID is written as `confirming_user_id` in `reconciliation_records`.
- There is no stable constant or seeded system user UUID anywhere in the codebase.

### FIX

**Step 1 — Define a stable constant at the top of `ReconciliationService.java`:**

```java
private static final UUID SYSTEM_USER_ID =
    UUID.fromString("00000000-0000-0000-0000-000000000001");
```

**Step 2 — Replace the random UUID in `runMatching()`:**

```java
SystemUser systemUser = new SystemUser(
    SYSTEM_USER_ID,
    "System (Auto-Reconcile)",
    "00000-0000000-0",
    "system_auto",
    aval.common.enums.UserRole.ADMIN,
    "System"
);
```

**Step 3 — Seed the system user in `db-init/01-init.sql`:**

```sql
INSERT INTO app_user (user_id, full_name, cnic, username, role, location, password_hash)
VALUES ('00000000-0000-0000-0000-000000000001',
        'System (Auto-Reconcile)', '00000-0000000-0',
        'system_auto', 'ADMIN', 'System', 'N/A')
ON CONFLICT DO NOTHING;
```

### TEST

Create `src/test/java/aval/service/ReconciliationServiceSystemUserTest.java`:

```java
public class ReconciliationServiceSystemUserTest {

    @Test
    public void testRunMatching_usesStableSystemUserId() {
        // Call runMatching() twice on the same workspace with the same data
        // Capture the confirming_user_id from each saved ReconciliationRecord
        // Assert both calls produce identical confirming_user_id values
        // Assert that ID equals 00000000-0000-0000-0000-000000000001
    }
}
```

### VALIDATE

- Run `ReconciliationServiceSystemUserTest` — test must pass.
- Run reconciliation twice on scenario_01 data.
- Query `reconciliation_records` in PostgreSQL — confirm all auto-reconciled rows share the same `confirming_user_id` across both runs.

---

## Issue 6 — No Ollama Health Check: Failure Surfaces Mid-Pipeline

**Severity:** HIGH  
**Files:** `LangChain4jVectorizationEngine.java`, `Main.java`  
**Key Lines:** `LangChain4jVectorizationEngine.java:21–27` (constructor)  
**Symptom:** If Ollama is not running, the user uploads data and waits — then receives a cryptic failure after the connection timeout fires (up to 60 seconds per transaction).

### LOCATE

Open `LangChain4jVectorizationEngine.java` and find the constructor. Then open `Main.java` and find where the engine is instantiated.

### VERIFY

- The constructor calls `OllamaEmbeddingModel.builder()...build()` but never sends a test request.
- There is no `ping()`, `healthCheck()`, or `isAvailable()` call before the engine is passed to services.
- In `IngestionService.standardize()`, the catch on vectorization failure prints to stderr and continues — transactions are silently skipped.

### FIX

**Step 1 — Add `healthCheck()` to `LangChain4jVectorizationEngine`:**

```java
public boolean healthCheck() {
    try {
        embeddingModel.embed("health-check").content();
        return true;
    } catch (Exception e) {
        System.err.println("[VECTORIZATION] Ollama health check failed: " + e.getMessage());
        return false;
    }
}
```

**Step 2 — Call `healthCheck()` during startup in `Main.java`:**

```java
VectorizationEngine vectorizationEngine =
    new LangChain4jVectorizationEngine(ollamaUrl, modelName);

if (!vectorizationEngine.healthCheck()) {
    System.err.println("[STARTUP] Ollama is not reachable at " + ollamaUrl +
        ". Ingestion will be unavailable.");
    MainUIContext.getInstance().setVectorizationAvailable(false);
    // Show a non-blocking startup warning dialog here
} else {
    MainUIContext.getInstance().setVectorizationAvailable(true);
}
```

**Step 3 — Guard upload drop zones in `ReconController.initialize()`:**

```java
if (!MainUIContext.getInstance().isVectorizationAvailable()) {
    ledgerDropZone.setDisable(true);
    bankDropZone.setDisable(true);
    statusLabel.setText(
        "Ollama is unavailable. Start the Ollama service to enable ingestion.");
}
```

### TEST

Create `src/test/java/aval/engine/VectorizationHealthCheckTest.java`:

```java
public class VectorizationHealthCheckTest {

    @Test
    public void testHealthCheck_returnsFalse_whenOllamaUnreachable() {
        // Instantiate engine with a bad URL (e.g. http://localhost:9999)
        // Call healthCheck()
        // Assert returns false within a reasonable timeout (< 5 seconds)
        // Assert no exception propagates to the caller
    }

    @Test
    public void testHealthCheck_doesNotThrow_onNetworkError() {
        // Same setup — verify the method handles the failure gracefully with no thrown exception
    }
}
```

### VALIDATE

- Run `VectorizationHealthCheckTest` — both tests must pass and complete within 5 seconds.
- Stop Ollama, start the application — confirm a warning message appears at startup.
- Confirm the upload drop zones are disabled when Ollama is unreachable.

---

## Issue 7 — PDF Parser `^` Anchor Regex: 0 Transactions Returned

**Severity:** HIGH  
**File:** `PDFBankStatementParser.java`  
**Key Lines:** Lines 55–57  
**Symptom:** `[INGESTION] Parsed dataset has 0 raw transactions` — all bank statement data is discarded.

### LOCATE

Open `PDFBankStatementParser.java`. Find the `Pattern.compile()` call near line 55.

### VERIFY

- The compiled pattern is: `^(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})`
- The `^` anchor is present, requiring the date to be at the very start of the line.
- Extract 5–10 lines from `bank_statement_pacific_trust.pdf` using `PDFTextStripper` and confirm they start with a transaction number (e.g. `1 01/01/2025 ...`), NOT with a date.
- Confirm `Matcher.find()` on these lines returns `false` because of the `^` anchor.

### FIX

**Step 1 — Remove the `^` anchor:**

```java
// BEFORE (broken):
Pattern datePattern = Pattern.compile(
    "^(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"
);

// AFTER (fixed):
Pattern datePattern = Pattern.compile(
    "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"
);
```

**Step 2 — Derive the remainder from `m.end()`, not position 0:**

```java
Matcher m = datePattern.matcher(line);
if (m.find()) {
    String rawDate = m.group(1);
    // Start remainder from the end of the date match — not from position 0
    // This prevents the row number from leaking into the narrative field
    String remainder = line.substring(m.end()).trim();
    // Extract amounts and narrative from remainder
}
```

### TEST

Create `src/test/java/aval/parser/PDFBankStatementParserTest.java`:

```java
public class PDFBankStatementParserTest {

    @Test
    public void testParse_returnsNonZeroTransactions_forPacificTrustPDF() {
        PDFBankStatementParser parser =
            new PDFBankStatementParser("DEFAULT_STRATEGY", List.of());
        String path = "data/scenario_01_retail_ecommerce/bank_statement_pacific_trust.pdf";
        FinancialDataset dataset = parser.parse(path);
        assertTrue("Expected > 0 transactions",
            dataset.getRawTransactions().size() > 0);
    }

    @Test
    public void testParse_extractsCorrectDate_fromNumberPrefixedLine() {
        // Directly test regex on: "1 01/15/2025 Some Payment 500.00 1000.00"
        // Assert parsed date is "01/15/2025"
        // Assert the row number "1" does NOT appear in the narrative field
    }

    @Test
    public void testParse_extractsCorrectAmount_notBalance() {
        // Line: "3 03/10/2025 Office Rent REF-001 3200.00 86681.68"
        // Assert parsed amount is 3200.00 (the debit), not 86681.68 (the balance)
    }
}
```

### VALIDATE

- Run `PDFBankStatementParserTest` — all 3 tests must pass.
- Run the full ingestion pipeline against `bank_statement_pacific_trust.pdf`.
- Confirm log shows: `[INGESTION] Parsed dataset has N raw transactions` where N > 0.
- Spot-check 3 parsed transactions: verify date, narrative, and amount are correct, and no row numbers bleed into narrative.

---

## Issue 8 — Reconcile Button Enabled on Empty Transaction Lists

**Severity:** HIGH  
**File:** `ReconController.java`  
**Key Lines:** Lines 621–632  
**Symptom:** If a file parses to 0 valid transactions, the Reconcile button still enables. The user runs reconciliation on empty data and receives no explanation.

### LOCATE

Open `ReconController.java`. Find `updateReconButtonState()`.

### VERIFY

- The `boolean ready` is set based only on `!= null` checks for both lists.
- There is no `.isEmpty()` check — a list with 0 items still evaluates `ready` as `true`.
- The hint text does not distinguish between "no file uploaded" and "file uploaded but 0 transactions parsed."

### FIX

```java
private void updateReconButtonState() {
    MainUIContext ctx = MainUIContext.getInstance();
    List<StandardizedTransaction> ledger = ctx.getStandardizedLedgerTransactions();
    List<StandardizedTransaction> bank   = ctx.getStandardizedBankTransactions();

    boolean ledgerReady = ledger != null && !ledger.isEmpty();
    boolean bankReady   = bank   != null && !bank.isEmpty();
    boolean ready       = ledgerReady && bankReady;

    btnRecon.setDisable(!ready);

    if (!ledgerReady && !bankReady) {
        reconHint.setText("Load both the ledger and bank statement to proceed.");
    } else if (!ledgerReady) {
        reconHint.setText("Ledger not loaded or contains no valid transactions.");
    } else if (!bankReady) {
        reconHint.setText("Bank statement not loaded or contains no valid transactions.");
    } else {
        reconHint.setText("Ready to reconcile " + ledger.size() +
            " ledger and " + bank.size() + " bank transactions.");
    }
}
```

### TEST

Create `src/test/java/aval/ui/controller/ReconButtonStateTest.java`:

```java
public class ReconButtonStateTest {

    @Test
    public void testButtonDisabled_whenLedgerListIsEmpty() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();
        ctx.setStandardizedLedgerTransactions(Collections.emptyList());
        ctx.setStandardizedBankTransactions(List.of(/* one mock tx */));
        // Invoke updateReconButtonState() (make package-private or use reflection)
        // Assert btnRecon.isDisable() == true
    }

    @Test
    public void testButtonEnabled_whenBothListsHaveData() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.setStandardizedLedgerTransactions(List.of(/* mock tx */));
        ctx.setStandardizedBankTransactions(List.of(/* mock tx */));
        // Assert btnRecon.isDisable() == false
    }

    @Test
    public void testHintText_reflectsSpecificMissingDataset() {
        MainUIContext ctx = MainUIContext.getInstance();
        ctx.clearSession();
        ctx.setStandardizedLedgerTransactions(List.of(/* mock tx */));
        // Leave bank null
        // Assert hint text contains "Bank statement not loaded"
    }
}
```

### VALIDATE

- Run `ReconButtonStateTest` — all 3 tests must pass.
- Upload a deliberately malformed XLSX (all amounts invalid). Confirm the button stays disabled.
- Confirm the hint text tells the user exactly which dataset is missing or empty.

---

## Issue 9 — ManualCheckController Silent Approval Loss

**Severity:** HIGH  
**File:** `ManualCheckController.java`  
**Key Lines:** Lines 205–214  
**Symptom:** User clicks Approve on a hypothesis. Nothing is saved. On restart, the approval is gone. The user has no idea.

### LOCATE

Open `ManualCheckController.java`. Find `resolveItem()` and the null check for `reconciliationService`.

### VERIFY

- When `reconciliationService` is null, the method sets a subtitle text and returns — no save is performed.
- The UI shows no error state — no alert, no color change, no indication of failure.
- The method returns `void` — the caller cannot detect the no-op.
- There is no retry mechanism.

### FIX

```java
public void resolveItem(MatchHypothesis hypothesis, boolean approved) {
    if (reconciliationService == null) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Cannot Save Review");
        alert.setHeaderText("Reconciliation service is not available");
        alert.setContentText(
            "Your decision could not be saved because the reconciliation service " +
            "is not initialised. Please restart the application and try again.");
        alert.showAndWait();
        return;
    }
    try {
        reconciliationService.recordManualDecision(
            hypothesis, approved, MainUIContext.getInstance().getCurrentUser());
        markRowResolved(hypothesis, approved);  // Visual confirmation
    } catch (Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Save Failed");
        alert.setHeaderText("Could not save your decision");
        alert.setContentText(
            "Error: " + e.getMessage() +
            "\n\nYour decision was NOT persisted. Please try again.");
        alert.showAndWait();
    }
}
```

### TEST

Create `src/test/java/aval/ui/controller/ManualCheckControllerTest.java`:

```java
public class ManualCheckControllerTest {

    @Test
    public void testResolveItem_showsAlert_whenServiceIsNull() {
        ManualCheckController ctrl = new ManualCheckController();
        // Leave reconciliationService null (default state)
        // Mock Alert to capture dialog invocations
        // Call resolveItem(mockHypothesis, true)
        // Assert Alert was shown with ERROR type
        // Assert no save attempt was made on DataStore
    }

    @Test
    public void testResolveItem_showsAlert_whenSaveThrows() {
        // Inject a mock reconciliationService that throws on recordManualDecision()
        // Call resolveItem(mockHypothesis, true)
        // Assert Alert was shown with "Save Failed" title
    }

    @Test
    public void testResolveItem_marksRowResolved_onSuccess() {
        // Inject a working mock reconciliationService
        // Call resolveItem(mockHypothesis, true)
        // Assert markRowResolved() was called with correct parameters
    }
}
```

### VALIDATE

- Run `ManualCheckControllerTest` — all 3 tests must pass.
- Set `reconciliationService` to null in a test build and click Approve — confirm error dialog appears.
- In a normal run, approve a hypothesis, restart the app, open the same workspace — confirm the approved hypothesis still shows as resolved.

---

## What Remains After These 9 Fixes (Medium Priority)

These issues do not block scenario_01 but should be addressed before production use:

| Issue | File | Impact |
|---|---|---|
| `consolidateMultiSource()` only matches A↔B, A↔C — never B↔C | `ReconciliationService.java` | Multi-source reconciliation with 3+ datasets produces incomplete results |
| XLSX parser always reads sheet index 0; no sheet-name detection | `ExcelLedgerParser.java` | Fails silently on any file where the ledger is not the first sheet |
| Column keyword matching too narrow (`date`, `debit`, `credit` only) | `ExcelLedgerParser.java` | Fails on headers like `Entry Date`, `Amt Debit`, `Post Date` |
| `MatchingConfig` thresholds hardcoded at 0.95 / 0.70 / 7 days | `MatchingConfig.java` | Cannot tune matching sensitivity per client or workspace |
| `AnomalyDetectionEngine.findDuplicates()` is O(n²) | `AnomalyDetectionEngine.java` | Performance degrades severely beyond ~5,000 transactions |

---

*All 9 issues above must be resolved and confirmed before this document is considered closed.*
