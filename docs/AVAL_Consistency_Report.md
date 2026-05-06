# AVAL.sys — Design ↔ Code Consistency Report
**Prepared for:** SDA (CX01) Evaluation — 8 May 2026  
**Scope:** V1 Class Diagram (`AVAL_Class_Diagram.puml`) · 12 Sequence Diagrams · SSDs (Domain and SSD.pdf) · All 12 UCs  
**Status of code:** Java 17 / JavaFX 17 / Maven, inspected in full

---

## Executive Summary

The project satisfies **9 of 12 use cases** in its backend service layer. All three gated UCs (UC8, UC9, UC10) have partial backend implementations but **no corresponding UI**, meaning the system cannot demonstrate them end-to-end without the additions described in `UC8_UC9_UC10_Implementation_Plan.md`.

The design artifacts (class diagram, SDs, SSDs) were written before the final implementation sprint. This created ~20 named discrepancies — mostly renamed identifiers and field names — that the instructor will check for in the "design-code consistency" criterion. **None of these discrepancies represent architectural drift; they are all surface-level renames.** The plan below prioritises which to fix before 8 May.

---

## Part 1 — Class Diagram vs Code (`AVAL_Class_Diagram.puml`)

### 1.1 Class / Controller Names

| Diagram Name | Actual Code Name | Severity |
|---|---|---|
| `CSVLedgerParser` | `ExcelLedgerParser` | 🔴 High — parser name contradicts accepted file type (.xlsx) |
| `AppController` | Does not exist; replaced by `MainUIContext` singleton | 🔴 High — central orchestrator is missing from diagram |
| `IngestionController` | `ReconController` | 🟡 Medium — name mismatch |
| `ReconciliationDashboardController` | Split into `ReconController` + `ManualCheckController` | 🟡 Medium — 1-class → 2-class split not reflected |

### 1.2 `StandardizedTransaction` Fields

| Diagram Field | Code Field | Severity |
|---|---|---|
| `isoDate: Date` | `valueDate: LocalDate` | 🟡 Medium — rename + type upgrade |
| `cleanContext: String` | `narrative: String` | 🟡 Medium — rename |
| `sourceRaw: RawTransaction` | Removed entirely | 🟡 Medium — back-reference dropped |

### 1.3 `MatchHypothesis` Fields

| Diagram Field | Code Field | Severity |
|---|---|---|
| `bankSideTx: StandardizedTransaction` | `bankTransaction` | 🟢 Low — minor rename |
| `ledgerSideTx: StandardizedTransaction` | `ledgerTransaction` | 🟢 Low — minor rename |
| `semanticProofLog: String` | `justification: String` | 🟢 Low — minor rename |

### 1.4 Enumerations

| Diagram Enum Value | Code Enum Value | Severity |
|---|---|---|
| `MatchType.EXACT` | `MatchType.EXACT_RULE` | 🟡 Medium |
| `MatchType.SEMANTIC` | `MatchType.AI_PROBABILISTIC` | 🟡 Medium |
| `HypothesisStatus.FORCE_RECONCILED` | Not in code | 🔴 High — UC8 requires this value; must be added |
| `DataSourceType.INTERNAL_LEDGER` | `DataSourceType.INTERNAL_EXCEL` | 🟢 Low |
| `DataSourceType.BANK_STATEMENT` | `DataSourceType.EXTERNAL_PDF` | 🟢 Low |
| `UserRole.AVAL_ENGINEER` | `UserRole.ADMIN` | 🟢 Low |
| `UserRole.FINANCE_OFFICER` | `UserRole.ACCOUNTANT` | 🟢 Low |
| `WorkspaceStatus.*` | Values differ from diagram | 🟡 Medium — check exact values at presentation |

### 1.5 Method Signatures

| Diagram Signature | Code Signature | Severity |
|---|---|---|
| `MatchingEngine.match(bank, ledger, cfg)` | `generateHypotheses(ledger, bank)` | 🔴 High — name + param order both differ |
| `VectorizationEngine.batchVectorize()` | `vectorizeBatch()` | 🟡 Medium — rename |
| `VectorizationEngine.preprocess()` | Not in interface | 🟡 Medium — method missing from interface |
| `ReconciliationService.runMatching(ws)` | `runMatching(workspace, ledgerTxs, bankTxs)` | 🟡 Medium — extra params not in diagram |

### 1.6 Missing Classes (exist in code, absent from diagram)

| Missing from Diagram | Severity |
|---|---|
| `MainUIContext` (central UI singleton) | 🔴 High |
| `AnomalyDetectionEngine` (implements UC10) | 🔴 High — a whole engine class not diagrammed |
| `Anomaly` domain object | 🔴 High — returned by anomaly engine; diagram shows `reason: String` on a detached element |
| `MatchingConfig` / `VectorizationConfig` | 🟡 Medium |
| `HybridMatchingEngine` (concrete composite) | 🟡 Medium |

---

## Part 2 — Sequence Diagrams vs Code (all 12 SDs)

### UC1 — Create Client Organisation Profile
- Diagram shows `AppController` orchestrating workspace creation → **`AppController` does not exist.** Actual flow goes through `LoginController` → `DashboardController` → `WorkspaceController`.
- Fix: Update SD participant names to `WorkspaceController` / `ReconciliationService`.

### UC2 — Ingest Client Internal Ledger
- Diagram shows `CSVLedgerParser` → **code uses `ExcelLedgerParser`.**
- Diagram shows `RawLedgerTransaction` as the return type → code returns `StandardizedTransaction` directly from ingestion pipeline.
- Fix: Rename participant in SD to `ExcelLedgerParser`.

### UC3 — Import External Financial Data
- Diagram is consistent with `PDFBankStatementParser` usage. ✅ No discrepancies.

### UC4 — Standardize Financial Schema
- Diagram shows `StandardizationService` as a separate class → **absorbed into `IngestionService.standardize()`.** No separate class.
- Fix: Merge into `IngestionService` participant in SD.

### UC5 — Vectorize Transactions
- Diagram shows `VectorizationEngine.batchVectorize(batch)` → **code method is `vectorizeBatch(transactions)`.** Param name and method name differ.
- Fix: Rename in SD.

### UC6 — Run Reconciliation Matching
- Diagram shows `MatchingEngine.match(bankList, ledgerList, config)` → **code interface is `generateHypotheses(ledgerList, bankList)`.** Method name, param order, and config param all differ.
- Fix: Update SD call signature.

### UC7 — Review Pending Matches
- SD is consistent with `ManualCheckController.handleApprove()` / `handleReject()` and `ReconciliationService.confirmHypothesis()` / `rejectHypothesis()`. ✅ Minor: diagram says `ReconciliationDashboardController`; should say `ManualCheckController`.

### UC8 — Force Manual Reconciliation
- SD references `ReconciliationDashboardController` → **this class does not exist.** Actual controller is `ManualCheckController`.
- SD references `MatchType.FORCE_OVERRIDE` → **not in `MatchType` enum.** Only `EXACT_RULE` and `AI_PROBABILISTIC` exist.
- SD references `HypothesisStatus.FORCE_RECONCILED` → **not in `HypothesisStatus` enum.**
- SD shows `MatchHypothesis.seal(user)` method → **method does not exist on `MatchHypothesis`.**
- SD shows `MatchHypothesis.isManualOverride()` → **method does not exist.**
- **Critically: no `handleForceReconcile()` in any FXML controller.** The backend `forceReconcile()` is implemented in `ReconciliationService` but is unreachable from the UI.
- Fix: Add `FORCE_RECONCILED` to `HypothesisStatus`, add `FORCE_OVERRIDE` to `MatchType`, wire UI per `UC8_UC9_UC10_Implementation_Plan.md`.

### UC9 — Multi-Source Consolidation
- SD is explicitly marked **"DEFERRED TO V2"** in the diagram. However, `ReconciliationService.consolidateMultiSource()` IS implemented in the backend code.
- This is a documentation inconsistency: the SD says V2, but V1 code ships with the service method. The SD must be updated to remove the V2 deferral notice and reflect the actual implemented method.
- **No UI exists** for this feature despite the backend being ready.
- Fix: Remove V2 deferral label from SD, update participant name to `ManualCheckController`, wire UI per implementation plan.

### UC10 — Identify Financial Anomalies
- SD is explicitly marked **"DEFERRED TO V2"** — same problem as UC9. `AnomalyDetectionEngine` IS implemented.
- However, `AnomalyDetectionEngine.detectAnomalies()` returns `List<String>`, not `List<Anomaly>`. The domain class `Anomaly` (with typed `Category` enum) does not exist yet.
- SD participant `AnomalyDetectionEngine` does not appear in the class diagram at all.
- Fix: Create `Anomaly.java` domain class, update engine return type, remove V2 label from SD.

### UC11 — Generate Verified Reconciliation Report
- SD is marked **"PARTIAL"** — but the code is actually fully implemented including the `UnresolvedItemsException` gate.
- Fix: Remove "PARTIAL" label from SD header.

### UC12 — Create and Maintain Verified Transaction History
- SD is marked **"DEFERRED TO V2"** — but `FinanceController` implements history viewing.
- Fix: Remove V2 deferral label, confirm participant names match actual controllers.

---

## Part 3 — SSDs (Domain and SSD.pdf) vs Code

The SSDs in `Domain and SSD.pdf` describe system-level message flows. Key findings:

### Domain Model (Page 1 of PDF)
The domain class sketch on Page 1 shows:
- `StandardizedTransaction.isoDate` → code has `valueDate` 🟡
- `StandardizedTransaction.cleanContext` → code has `narrative` 🟡
- `Anomaly.reason: String` → code returns `List<String>` from engine, no typed class 🔴
- `MatchHypothesis.semanticProofLog` → code has `justification` 🟢
- `MatchHypothesis.status: String` → code has typed `HypothesisStatus` enum (improvement) ✅
- `FinancialDataset` abstract with `RawInternalLedger` / `RawBankStatement` children → code has `RawTransaction` as the base entity only, no inheritance hierarchy for datasets 🟡

### UC8 SSD (Page 9)
SSD shows: `forceReconcile(bankEntryID, ledgerEntryIDs)` + `promptForAuditNote(netDifference)` + `submitAuditNote(justification)`  
Code: `ReconciliationService.forceReconcile(ledger, bank, user, justification)` ✅ backend matches; **UI is missing.**

### UC9 SSD (Page 10)
SSD shows: `selectUnmatchedDeposit()` → `suggestRelatedLedgerEntries()` → `calculateConsolidation()` → `performConsolidationAndDisplay()` → `sealMatch()`  
Code: `consolidateMultiSource()` in service covers the core logic. **No UI, no `suggestRelatedLedgerEntries()` method.**

### UC10 SSD (Page 11)
SSD shows: `displaySuspiciousRecords()` → `inspectAnomaly()` → `selectResolutionAction()` → `finalizeResolutionAndLog()`  
Code: `ManualCheckController.loadAnomalies()` loads strings from `AnomalyDetectionEngine`; there is no `inspectAnomaly()` detail view and no typed resolution action.

### UC11 SSD (Page 12)
SSD matches implemented `ReportService.generateReport()` with exception gate. ✅

### UC12 SSD (Page 13)
SSD shows `requestSemanticProofLog()` + `requestCertifiedAuditExport()`.  
Code: `FinanceController` renders history data but "certified audit export" is not a separate dedicated method. 🟡

---

## Part 4 — Manifesto vs Code

The Manifesto is an **early vision document**; divergences are expected and not graded directly. Noted for completeness:

| Manifesto Statement | Actual Implementation |
|---|---|
| Java 21 + Spring Boot 3 | Java 17 + JavaFX 17 (no Spring Boot) — intentional shift to desktop tool |
| Alif-1.0-8B + Gemini 3 Pro | `nomic-embed-text` via Ollama + LangChain4j — equivalent semantic layer |
| WhatsApp screenshot OCR | Not implemented (out of scope for SDA submission) |
| FBR Annexure-C automation | Not implemented (out of scope) |
| Raast P2M API | Not implemented (out of scope) |

These differences represent scope reduction for the semester prototype. They do not affect the SDA design-consistency grade.

---

## Part 5 — Prioritised Fix List (Before 8 May 2026)

### 🔴 Critical — Must Fix (affects UC completeness + consistency score)

| # | Fix | Where | Effort |
|---|---|---|---|
| C1 | Add `FORCE_RECONCILED` to `HypothesisStatus` enum | `HypothesisStatus.java` | 5 min |
| C2 | Add `FORCE_OVERRIDE` to `MatchType` enum | `MatchType.java` | 5 min |
| C3 | Create `Anomaly.java` domain class with typed `Category` enum | New file | 30 min |
| C4 | Update `AnomalyDetectionEngine` to return `List<Anomaly>` | `AnomalyDetectionEngine.java` | 45 min |
| C5 | Wire UC8 force-link UI in `ManualCheck.fxml` + `ManualCheckController` | Per implementation plan | 2 hr |
| C6 | Wire UC9 consolidation UI panel in `ManualCheck.fxml` + controller | Per implementation plan | 3 hr |
| C7 | Update class diagram: rename `CSVLedgerParser` → `ExcelLedgerParser` | `AVAL_Class_Diagram.puml` | 5 min |
| C8 | Add `AnomalyDetectionEngine` + `Anomaly` to class diagram | `AVAL_Class_Diagram.puml` | 20 min |
| C9 | Add `MainUIContext` to class diagram | `AVAL_Class_Diagram.puml` | 10 min |
| C10 | Remove "DEFERRED TO V2" labels from UC9, UC10, UC12 SDs | SVG files | 10 min each |
| C11 | Remove "PARTIAL" label from UC11 SD | SVG file | 5 min |

### 🟡 Medium — Fix if Time Allows

| # | Fix | Where | Effort |
|---|---|---|---|
| M1 | Update SD UC1 participant `AppController` → `WorkspaceController` | UC1 SD | 10 min |
| M2 | Update SD UC2 participant `CSVLedgerParser` → `ExcelLedgerParser` | UC2 SD | 10 min |
| M3 | Update SD UC5 call `batchVectorize()` → `vectorizeBatch()` | UC5 SD | 10 min |
| M4 | Update SD UC6 call `match(bank,ledger,cfg)` → `generateHypotheses(ledger,bank)` | UC6 SD | 10 min |
| M5 | Update SD UC7/UC8 participant `ReconciliationDashboardController` → `ManualCheckController` | UC7/UC8 SDs | 10 min |
| M6 | Update class diagram field names (`isoDate`→`valueDate`, `cleanContext`→`narrative`, etc.) | `AVAL_Class_Diagram.puml` | 20 min |
| M7 | Update class diagram method `match(bank,ledger,cfg)` → `generateHypotheses(ledger,bank)` | `AVAL_Class_Diagram.puml` | 10 min |
| M8 | Update class diagram method `batchVectorize()` → `vectorizeBatch()` | `AVAL_Class_Diagram.puml` | 5 min |

### 🟢 Low — Nice to Have

| # | Fix |
|---|---|
| L1 | Align `WorkspaceStatus` enum values between diagram and code |
| L2 | Align `DataSourceType` values (`INTERNAL_LEDGER` → `INTERNAL_EXCEL` etc.) in diagram |
| L3 | Align `UserRole` values (`AVAL_ENGINEER` → `ADMIN` etc.) in diagram |
| L4 | Add `MatchingConfig` / `VectorizationConfig` to class diagram |

---

## Part 6 — UC Completeness Matrix

| UC | Description | Backend | UI | SD Label | Overall |
|---|---|---|---|---|---|
| UC1 | Create Client Org Profile | ✅ | ✅ | ✅ | ✅ Done |
| UC2 | Ingest Internal Ledger | ✅ | ✅ | ⚠️ CSVLedgerParser | ✅ Done |
| UC3 | Import External Statement | ✅ | ✅ | ✅ | ✅ Done |
| UC4 | Standardize Schema | ✅ | ✅ | ⚠️ Separate class | ✅ Done |
| UC5 | Vectorize Transactions | ✅ | ✅ | ⚠️ Method name | ✅ Done |
| UC6 | Run Matching | ✅ | ✅ | ⚠️ Method sig | ✅ Done |
| UC7 | Review Pending Matches | ✅ | ✅ | ✅ | ✅ Done |
| UC8 | Force Manual Reconciliation | ✅ Service | ❌ No UI | ❌ Wrong enum values | ⚠️ Partial |
| UC9 | Multi-Source Consolidation | ✅ Service | ❌ No UI | ❌ V2 label | ⚠️ Partial |
| UC10 | Identify Anomalies | ⚠️ Returns String | ⚠️ Strings only | ❌ V2 label | ⚠️ Partial |
| UC11 | Generate Report | ✅ | ✅ | ⚠️ Partial label | ✅ Done |
| UC12 | Transaction History | ✅ | ✅ | ❌ V2 label | ✅ Done |

**Current score: 9/12 full, 3/12 partial.**  
With C1–C6 applied from the fix list above, score becomes **12/12**.

---

## Part 7 — GRASP / GoF Pattern Inventory (for report/slides)

| Pattern | Class(es) | Evidence |
|---|---|---|
| **GoF Strategy** | `MatchingEngine` (interface) → `RuleBasedMatchingEngine`, `SemanticMatchingEngine`, `HybridMatchingEngine` | Strategy swap at runtime |
| **GoF Strategy** | `VectorizationEngine` (interface) → `LangChain4jVectorizationEngine` | Same pattern |
| **GoF Template Method** | `DocumentParser<T>` abstract class → `ExcelLedgerParser`, `PDFBankStatementParser` | Hook methods in subclasses |
| **GoF Factory Method** | `DocumentParserFactory` | Creates correct parser by file type |
| **GoF Repository/DAO** | `DataStore` interface → `PostgresDataStore` | Persistence abstraction |
| **GoF Facade** | `ReconciliationService` | Single façade over matching engine + persistence |
| **GRASP Controller** | `WorkspaceController`, `ReconController`, `ManualCheckController`, `FinanceController` | UI system-event delegation |
| **GRASP Information Expert** | `MatchHypothesis` knows its own confidence + status | Encapsulates matching knowledge |
| **GRASP Pure Fabrication** | `MatchingConfig`, `VectorizationConfig` | No domain equivalent; exist purely for cohesion |
| **GRASP Low Coupling** | Constructor injection throughout; no static singletons except `MainUIContext` | Verified in all service classes |
| **GRASP High Cohesion** | `IngestionService` (UC2–4 only), `ReportService` (UC11–12 only) | Responsibility-aligned services |
| **GRASP Protected Variations** | `MatchingEngine` interface isolates rule vs AI engine swap | Adding new engine = no client change |
| **GRASP Creator** | `IngestionService` creates `StandardizedTransaction`; `ReconciliationService` creates `MatchHypothesis` | Creator owns the objects it initialises |
| **GRASP Polymorphism** | `DocumentParser<T>` dispatch; `MatchingEngine` dispatch | Runtime type resolution |

---

*Report generated: 2026-05-06. Next action: apply Critical fixes C1–C11, then regenerate class diagram PNG for slides.*
