# AVAL — Non-UI Audit (vs `Project Rubric.pdf`)

**Audit date:** 27 April 2026 · **Scope:** everything outside `aval.ui.controller` · **Rubric:** the eight scored dimensions in `Project Rubric.pdf` at the repo root.

This audit supersedes `AUDIT_REPORT.md` (v3, 26 April). Most of the items v3 listed have since been fixed — UC11 is wired, UC12 has a history view, the workspace is now passed into `runMatching`, raw transactions are persisted, the consolidation null guard is in. Two of v3's seven items are still open. The numbers below reflect today's state.

---

## Score sheet (UI excluded per request)

| Rubric dimension | Max | Level | Score |
|---|---|---|---|
| Business Logic Functionality | 15 | **Good** | 11 |
| OOP Principles in Business Logic | 15 | **Excellent** | 15 |
| Database | 10 | **Good** | 7 |
| Architecture & Integration (UI / BL / DB) | 10 | **Good** | 7 |
| Use Cases | 10 | **Good** | 7 |
| Consistency between Design and Code | 10 | **Good** | 7 |
| Design Patterns (GRASP & GoF) | 10 | **Excellent** | 10 |
| Documentation | 10 | **Excellent** | 10 |
| **Non-UI total** | **90** | | **74** |

UI dimension (10 marks) excluded. Result: **74 / 90 ≈ 82 %**, two Excellents and six Goods.

---

## Dimension by dimension

### Business Logic Functionality — 11 / 15 (Good)

The pipeline runs end-to-end. Excel parsing, PDF parsing, schema standardisation, embedding generation via Ollama, pgvector cosine search, hypothesis generation, approve / reject / force-reconcile, CSV report export, anomaly detection, transaction history view — all execute. No syntax errors, no runtime crashes on the happy path.

Four logic gaps keep this out of the Excellent band:

1. **`ReconciliationService.runMatching()` never applies the 0.95 auto-confirm threshold.** The engine returns hypotheses; the service persists them and returns them. UC6's "matches exceeding 95% are automatically reconciled" branch never executes — every hypothesis goes to the review queue regardless of score.
2. **`MatchingConfig` is dead.** The class holds `autoConfirmThreshold = 0.95` and `reviewFloor = 0.70`, but neither engine reads it. `RuleBasedMatchingEngine` hardcodes `EXACT_MATCH_CONFIDENCE = 1.0` and `DATE_TOLERANCE_DAYS = 7`; `SemanticMatchingEngine` hardcodes `MAX_CANDIDATES = 3`. The whole point of MatchingConfig was per-workspace policy without touching engine code.
3. **`IngestionService.ingestFile()` calls `saveFinancialDataset(dataset)` without the workspace ID.** The overload that accepts a workspace UUID exists in `DataStore`, but the service uses the single-arg version. Result: every row in `financial_dataset.workspace_id` is `NULL`, breaking the FK relationship that `db-init/01-init.sql` declares.
4. **`consolidateMultiSource()` is the wrong shape for UC9.** UC9 is N-to-1: many small ledger entries summed against one large bank deposit, with variance and audit-note prompts. The current implementation pairwise-matches "primary" against every other dataset, which is closer to a multi-pass UC6.

The headline output is correct, the secondary logic is off — that's exactly the "slight logical errors that do not significantly affect the results" wording in the Good band.

### OOP Principles in Business Logic — 15 / 15 (Excellent)

This is the cleanest dimension. The four OOP pillars are all in active use, not just labelled:

- **Encapsulation** — every field private across all 33 non-UI classes; no public mutable state; controlled setters only where the lifecycle demands them (`MatchHypothesis.setStatus`, `FinancialDataset.markAsStandardized`).
- **Inheritance** — `FinancialDataset` is a real abstract base with two concrete subclasses (`RawInternalLedger`, `RawBankStatement`) and two abstract methods that the subclasses are forced to implement (`getSourceType()`, `validate()`).
- **Abstraction** — three live interfaces (`DocumentParser<T>`, `MatchingEngine`, `VectorizationEngine`) plus generics on the parser. Concrete LangChain4j / POI / PDFBox SDK references never leak above their respective implementation classes.
- **Polymorphism** — Strategy dispatch on `MatchingEngine` (Rule-Based vs Semantic interchangeable through `ReconciliationService`), Template Method on `DocumentParser`, Factory Method on `IngestionService.createParser(DataSourceType)`.

`BigDecimal` is used everywhere money flows. `java.time.LocalDate`/`LocalDateTime` everywhere temporal. Constructor injection on every service and engine. No setters for dependencies, no Lombok, no static singletons, no global state.

### Database — 7 / 10 (Good)

Nine tables in `db-init/01-init.sql`, all named in the conventional `snake_case`: `standardized_ledger`, `standardized_bank`, `match_hypotheses`, `reconciliation_records`, `system_user`, `client_organization`, `reconciliation_workspace`, `financial_dataset`, `raw_transactions`. `pgvector` extension enabled, `vector(768)` columns sized to `nomic-embed-text`. Foreign keys declared. ON CONFLICT upserts on the entities that need them. Parameterised `PreparedStatement` everywhere — no SQL injection surface.

Three issues hold this back:

1. **`financial_dataset.workspace_id` is always `NULL` in practice** because `IngestionService` calls the no-workspace overload. The schema enforces a FK, the data violates the intent. (Same as BL issue 3 — counted once on each side because it's both a logic bug and a DB integrity bug.)
2. **Inconsistent exception handling in `DataStore`.** Three styles in one class: some methods throw `SQLException` (UC1 updates), some wrap into `RuntimeException` (organisation save), some swallow into `e.printStackTrace()` (history queries). The rubric's Excellent band says "Exception Handling" without qualifying it, but mixing three styles undermines the claim.
3. **`saveStandardizedTransactions(List)` is a no-op stub.** The body is empty with a comment explaining that the per-side methods (`saveStandardizedLedgerTransaction`, `saveStandardizedBankTransaction`) carry the embeddings. Defensible, but a future caller will assume this method persists things and lose data silently.

### Architecture & Integration (UI / BL / DB) — 7 / 10 (Good)

Three packages, three responsibilities, cleanly named:

- UI: `aval.ui.controller`
- BL: `aval.service` + `aval.engine` + `aval.parser` + `aval.domain.*`
- DB: `aval.persistence`

Layer integration works — the pipeline crosses all three layers in both directions.

The reason this isn't Excellent: **two controllers reach into `DataStore` directly, and one reaches into `AnomalyDetectionEngine` directly.** `WorkspaceSetupController` calls `dataStore.saveClientOrganization(...)` and `dataStore.saveReconciliationWorkspace(...)` for UC1 because no service exposes those operations. `ReportController` calls `dataStore.getReconciliationHistory()` for UC12 for the same reason. `AnomalyReviewController` instantiates and calls `AnomalyDetectionEngine` directly because no `AnomalyService` exists. The three layers are present and labelled correctly, but in those four call sites the BL layer is being short-circuited. That is exactly what the rubric's "All layers fully integrated" wording is testing.

### Use Cases — 7 / 10 (Good)

Holistic completion across all twelve UCs:

| UC | Coverage | Notes |
|---|---|---|
| UC1 Create Org Profile | ✅ done | UI + DataStore (bypasses service — see Architecture) |
| UC2 Ingest Ledger | ✅ done | `IngestionService.ingestFile` + `ExcelLedgerParser` |
| UC3 Import Bank Statement | ✅ done | same path with `PDFBankStatementParser` |
| UC4 Standardize Schema | ✅ done | `IngestionService.standardize` |
| UC5 Vectorization | ✅ done | `LangChain4jVectorizationEngine` + pgvector |
| UC6 Probabilistic Matching | ⚠️ partial | works, but no auto-confirm threshold branch |
| UC7 Review Suggestions | ✅ done | `confirmHypothesis` / `rejectHypothesis` |
| UC8 Force Reconciliation | ⚠️ partial | functional, but `MatchType.FORCE_OVERRIDE` and `ReconciliationRecord.isManualOverride()` are absent (V1 spec named both as evaluator checkpoints) |
| UC9 Multi-Source Consolidation | ⚠️ wrong shape | pairwise matching, not N-to-1 sum |
| UC10 Identify Anomalies | ⚠️ partial | `AnomalyDetectionEngine` works, but returns `List<String>` instead of an `Anomaly` aggregate |
| UC11 Generate Report | ⚠️ partial | wired and runs, but no "Cannot Generate: Unresolved Items" gate (the UC11 extension the work-division doc named explicitly) |
| UC12 Transaction History | ✅ done | `historyBtn` → `viewHistory()` → `dataStore.getReconciliationHistory()` |

Six fully done, six partial. By per-member roll-up: Safwan 4/4, Rayyan 3/5 fully + 2 partial, Aryan 1/3 fully + 2 partial. As a group, that lands in the rubric's "Most (2-3 of 4-5)" band.

### Consistency between Design and Code — 7 / 10 (Good)

Mapping of the V1 class diagram and `AVAL_Class_Reference.docx` to actual code is roughly 80 %. The structure is right, the names mostly match. The deviations:

- `CSVLedgerParser` (V1) → `ExcelLedgerParser` (code). Format support changed from CSV to XLSX, but the diagram, dev prompt, and work-division master table all still say CSV.
- `MatchType.FORCE_OVERRIDE` (V1) → `MatchType.MANUAL_OVERRIDE` (code).
- `ReconciliationRecord.isManualOverride()` (V1) → not present.
- `HypothesisStatus` should have four values per UC6/UC7 (`AUTO_RECONCILED`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`); code has three (`PENDING`, `APPROVED`, `REJECTED`). The auto-reconciliation path has nowhere to live in the state machine.
- Three classes added that aren't in V1: `AnomalyDetectionEngine`, `WorkspaceSetupController`, `AnomalyReviewController`. The first two cover UC10 and UC1 respectively, so they're justified additions rather than scope creep — but they aren't in the diagram.
- Three classes still missing the mandatory `//@desc / //@grasp / //@gof` header block per Dev Prompt rule 2: `Main.java`, `engine/AnomalyDetectionEngine.java`, `service/ReportService.java`. (The other 35 non-UI files have the block.)
- `DataSourceType` enum has `INTERNAL_EXCEL`, `INTERNAL_CSV`, `EXTERNAL_PDF`. `INTERNAL_CSV` is dead code now that the parser is XLSX.

Headline structure faithful, named-checkpoint details drift. That's the gap between Excellent (100 %) and Good (70 %).

### Design Patterns (GRASP & GoF) — 10 / 10 (Excellent)

All six GRASP patterns the rubric names are applied correctly and visibly:

- **Controller** — `ReconciliationService` for UC6–UC9, `IngestionService` for UC2–UC4, `AppController` as UI Facade.
- **Information Expert** — `ReconciliationWorkspace` knows its datasets / hypotheses / records; `MatchHypothesis` knows its own confidence and produces its own justification text; `ClientOrganization` owns its workspaces.
- **Creator** — `IngestionService.createParser(DataSourceType)` (real factory), `ReconciliationService` creates `ReconciliationRecord` from `MatchHypothesis`.
- **Low Coupling** — `ReconciliationService` depends on `MatchingEngine` (interface), never on `SemanticMatchingEngine` or `RuleBasedMatchingEngine` directly. Same for `VectorizationEngine`.
- **High Cohesion** — each service owns one pipeline phase. `IngestionService` doesn't reconcile; `ReconciliationService` doesn't ingest; `ReportService` doesn't match.
- **Protected Variations** — Strategy interfaces and Template Method shield callers from LangChain4j / POI / PDFBox SDK churn.

GoF: Strategy (matching + vectorization), Template Method (parsers), Repository/DAO (`DataStore`), Factory Method (`IngestionService`), Facade (`AppController`). All real.

One minor note worth flagging before evaluation, even though it doesn't move the score: the dev prompt labels `RuleBasedMatchingEngine` as a Null Object pattern, but the class actually does real rule-based matching (date proximity + exact amount + 7-day window), so the label is wrong. The class header in the source is honest about being a Concrete Strategy. If an evaluator presses on it, the dev-prompt label is what to defend or correct.

### Documentation — 10 / 10 (Excellent)

Inventory of `complete_documentation/`:

- `Manifesto.pdf` — project vision, motivation, scope, expected outcomes
- `Domain and SSD.pdf` — domain model + system sequence diagrams
- `Use_Case_Diagram.pdf` — actor / use case map for all 12 UCs
- `Fully_Dressed_Usecases.docx` — full Cockburn-style write-ups for all 12 UCs (main scenario, extensions, pre/post-conditions, stakeholders)
- 12 sequence diagrams (`UC1` through `UC12`), one SVG per UC
- `AVAL_Class_Diagram.png` — full class diagram, readable
- `AVAL_Class_Reference.docx` — per-class explanation of every class in V1
- `AVAL_Dev_Prompt.docx` — coding rules, package structure, GoF/GRASP map
- `AVAL_Work_Division.docx` — class ownership table, integration handshake notes
- `V2/AVAL_V2.docx` — refined design spec (the V2 expansion)
- `README.md` and `Project Rubric.pdf` at repo root

All diagrams are readable, all sections complete. This is the easiest 10 / 10 in the audit.

---

## Open carry-overs from `AUDIT_REPORT.md` v3

Of the seven items v3 left open, two are still open today and the rest are fixed:

| v3 ID | Status today | Notes |
|---|---|---|
| ISSUE-A — UC11 not triggered | ✅ fixed | `ReportController.java:105` calls `reportService.generateReconciliationReport(records, unmatL, unmatB, OUTPUT_PATH)` |
| ISSUE-B — workspace passed as null | ✅ fixed | `ReconciliationDashboardController` now uses `workspaceSupplier.get()` |
| ISSUE-C — `saveRawTransactions` not called | ✅ fixed | `IngestionService.ingestFile:42` calls it |
| **ISSUE-D — `workspace_id` always null** | ❌ **still open** | `IngestionService.ingestFile:41` calls `saveFinancialDataset(dataset)` without the workspace UUID. The two-arg overload exists in `DataStore` but isn't used. |
| ISSUE-E — UC12 history view absent | ✅ fixed | `ReportController` has `historyBtn` → `viewHistory()` |
| ISSUE-F — three unused imports in AppController | ✅ fixed | none of the named imports remain |
| ISSUE-G — null check on `consolidateMultiSource` | ✅ fixed | guard now reads `multipleDatasets == null \|\| multipleDatasets.size() < 2` |

---

## The five highest-leverage fixes before evaluation

Cheapest gains to move from 74 / 90 toward the high 80s:

1. **Pass `workspaceId` into `saveFinancialDataset`.** Two-line fix in `IngestionService.ingestFile`. Recovers one mark in Database (FK integrity) and one in BL Functionality. The two-arg overload is already there.
2. **Wire the auto-confirm threshold into `ReconciliationService.runMatching`.** Read `workspace.getMatchingConfig().getAutoConfirmThreshold()` and split the returned hypotheses into auto-confirmed vs review-queue based on score. Fifteen lines. Recovers two marks in BL Functionality and one in Consistency, and finally makes `MatchingConfig` non-dead.
3. **Add `MatchType.FORCE_OVERRIDE` and `ReconciliationRecord.isManualOverride()`.** Two evaluator-named checkpoints, both surface-level. Ten lines. Recovers one or two marks in Consistency and one in Use Cases.
4. **Add the three missing `//@desc / //@grasp / //@gof` header blocks** on `Main`, `ReportService`, `AnomalyDetectionEngine`. Cosmetic but the dev prompt lists rule 2 as non-negotiable. Recovers ~half a mark in Consistency.
5. **Add the UC11 unresolved-items gate.** Throw a typed exception from `ReportService` when any hypothesis is still `PENDING` or any anomaly is unresolved; have `ReportController.showBlockingError()` catch it. Twenty lines. Recovers one mark in Use Cases.

If all five land, the realistic ceiling on the non-UI portion is ~83–85 / 90.

---

## What's already strong and shouldn't be touched

- pgvector cosine search is genuinely wired via `DataStore.findSimilarBankTransactions`.
- Strategy / Template Method / Repository / Factory Method / Facade are all real — no labels-without-implementation.
- BigDecimal / `java.time` discipline is consistent across every monetary and temporal field in non-UI code.
- Constructor injection is universal; no setters for dependencies anywhere in services or engines.
- 35 of 38 non-UI files carry the mandatory `//@desc / //@grasp / //@gof` header block.
- Documentation is genuinely comprehensive across diagrams, use cases, sequence flows, class reference, and rules.
