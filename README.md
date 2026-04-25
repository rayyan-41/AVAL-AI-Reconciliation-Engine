# AVAL AI Reconciliation Engine 

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=java" alt="Java 21" />
  <img src="https://img.shields.io/badge/JavaFX-Desktop-blue.svg?style=for-the-badge" alt="JavaFX" />
  <img src="https://img.shields.io/badge/PostgreSQL-pgvector-336791.svg?style=for-the-badge&logo=postgresql" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/AI-LangChain4j-000000.svg?style=for-the-badge" alt="LangChain4j" />
  <img src="https://img.shields.io/badge/Ollama-Local_Models-white.svg?style=for-the-badge&logo=ollama" alt="Ollama" />
  <img src="https://img.shields.io/badge/Maven-Build_Tool-C71A36.svg?style=for-the-badge&logo=apachemaven" alt="Maven" />
</p>

##  Executive Summary

**AVAL's AIRE** is a highly intelligent financial reconciliation engine designed to automate and streamline the process of matching internal company ledgers with external bank statements. 

Traditional reconciliation relies heavily on rigid, exact-match rules (e.g., date and exact amount), which often fail when dealing with aggregated payments, partial matches, or varying merchant narrative descriptions (e.g., "AMZN Mktp US" vs. "Amazon"). 

AVAL transcends these limitations by employing a **Hybrid Matching Engine**. It combines traditional **Rule-Based Deterministic Matching** with **Semantic AI Vectorization**. Using **LangChain4j** and local **Ollama** models, AVAL converts transaction narratives into 768-dimensional mathematical vectors, storing them in **PostgreSQL via `pgvector`** to perform lightning-fast Cosine Similarity searches.

---

## Core Features & Use Cases

The engine is built around 12 core Use Cases (UC) mapped across the data pipeline:

- **UC1: Create Client Organization Profile** - Setup multi-tenant environments.
- **UC2: Ingest Client Internal Ledger** - Parses internal financial data using **Apache POI** for Excel (`.xlsx`) files.
- **UC3: Import External Financial Data** - Parses complex external bank statements using **Apache PDFBox**.
- **UC4: Standardize Financial Schema** - Normalizes disparate data formats into a unified transaction model.
- **UC5: Execute Semantic Vectorization** - Uses the `nomic-embed-text` model to embed transactions (e.g., `"DEBIT | 150.00 | Office Supplies"`).
- **UC6: Run Probabilistic Matching Engine** - Executes the Hybrid Engine (Rule-based exact matches + Semantic Cosine Distance searches).
- **UC7: Review AI-Suggested Matches** - Human-in-the-loop (HITL) approval or rejection of generated Match Hypotheses.
- **UC8: Force Manual Reconciliation** - Administrator override capabilities.
- **UC9: Perform Multi-Source Consolidation** - Future-proof design for N-way reconciliation.
- **UC10-UC12:** Anomaly Detection, Report Generation, and Audit Trail persistence.

---

## Architecture & Design Patterns

The codebase adheres strictly to **GRASP** (General Responsibility Assignment Software Patterns) and **GoF** (Gang of Four) design patterns to ensure high cohesion and loose coupling:

1. **Strategy Pattern (`MatchingEngine`)**: Polymorphic execution of either `SemanticMatchingEngine` or `RuleBasedMatchingEngine`.
2. **Template Method (`DocumentParser`)**: Defines the skeleton for PDF and Excel parsing.
3. **Repository / DAO (`DataStore`)**: Centralizes JDBC connections and abstracts raw SQL and `pgvector` operations away from the business logic.
4. **Adapter (`LangChain4jVectorizationEngine`)**: Adapts the LangChain4j and Ollama APIs to the internal `VectorizationEngine` interface.
5. **Controller / Pure Fabrication (`ReconciliationService`)**: Orchestrates the complex workflows between vectors, parsers, and the database.

---

## The Hybrid Matching Engine Deep Dive

### 1. Rule-Based Engine (`RuleBasedMatchingEngine.java`)
A deterministic fallback and first-pass engine.
- Matches transactions if the `amount` matches exactly.
- Enforces a configurable date proximity window (default: `7 days tolerance`).
- Operates at **1.0 Confidence Score (100%)**.

### 2. Semantic AI Engine (`SemanticMatchingEngine.java`)
The core innovation of AVAL. 
1. **Text Serialization**: Combines Transaction Type, Amount, and Narrative.
2. **Vectorization**: Calls a local Ollama instance running `nomic-embed-text`.
3. **Similarity Search**: Queries PostgreSQL using `ORDER BY embedding <=> ?::vector` (Cosine Distance) to find the `MAX_CANDIDATES` (top 3) nearest neighbors.
4. **Hypothesis Generation**: Outputs a `MatchHypothesis` object with a calculated Confidence Score, awaiting human approval.

---

## Database Schema (`pgvector` Integration)

The application utilizes **PostgreSQL 16** initialized via `db-init/01-init.sql`.

Key Tables:
- `standardized_ledger` & `standardized_bank`: Stores normalized transaction data alongside a `vector(768)` column for the AI embeddings.
- `match_hypotheses`: Stores proposed links between internal and external transactions alongside a confidence score and AI justification.
- `reconciliation_records`: The immutable audit trail of finalized matches.

---

## Technology Stack

| Domain | Technology | Purpose |
| :--- | :--- | :--- |
| **Language** | Java 21 | Core backend and application logic. |
| **GUI** | JavaFX 21 | Desktop application interface (`AppController.java`). |
| **AI / LLM** | LangChain4j (`0.30.0`) | Framework for orchestrating embeddings and LLM calls. |
| **Local Models** | Ollama | Runs local, privacy-preserving embedding models (`nomic-embed-text`). |
| **Database** | PostgreSQL + `pgvector` | Relational storage and Vector Similarity Search. |
| **Document Parsing** | Apache PDFBox, POI, Commons CSV | Extracts raw data from PDFs, Excel sheets, and CSVs. |
| **Build Tool** | Maven Wrapper (`mvnw`) | Dependency management and build lifecycle. |
| **Containerization**| Docker Compose | Seamless infrastructure setup. |

---

## Setup & Installation Guide

### Prerequisites
- **Java JDK 21+** installed and added to your system `PATH`.
- **Docker** & **Docker Compose** installed and running.

### Step 1: Clone the Repository
```bash
git clone https://github.com/your-org/AVAL-AI-Reconciliation-Engine.git
cd AVAL-AI-Reconciliation-Engine
```

### Step 2: Spin Up the Infrastructure
AVAL requires the PostgreSQL database and Ollama inference server. We have provided a `compose.yml` that handles everything, including a sidecar container that automatically downloads the required AI model.

```bash
docker compose up -d
```
*Wait approximately 30-60 seconds for the `aval-ollama-pull` container to finish downloading the `nomic-embed-text` model.*

### Step 3: Build the Project
Use the bundled Maven Wrapper to download dependencies and compile the code.

**Linux / macOS:**
```bash
./mvnw clean install
```
**Windows:**
```cmd
mvnw.cmd clean install
```

### Step 4: Run the JavaFX Application
Launch the graphical interface:

**Linux / macOS:**
```bash
./mvnw javafx:run
```
**Windows:**
```cmd
mvnw.cmd javafx:run
```

---

## Project Structure Breakdown

```text
AVAL-AI-Reconciliation-Engine/
├── compose.yml               # Docker configuration for DB & Ollama
├── db-init/                  # SQL scripts run automatically on DB creation
│   └── 01-init.sql           # Table and Vector extension definitions
├── data/                     # Sample scenario data for testing
│   └── scenario_01_retail_ecommerce/
│       ├── bank_statement_pacific_trust.pdf
│       └── company_ledger_brightline.xlsx
├── complete_documentation/   # Comprehensive UML, Domain, and Sequence diagrams
└── src/aval/
    ├── Main.java             # JavaFX Application Entry Point
    ├── common/enums/         # Shared Enums (DatasetStatus, MatchType, etc.)
    ├── domain/               # POJOs and Entity Models
    │   ├── ai/               # AI Models (SemanticEmbedding, MatchHypothesis)
    │   ├── core/             # Core Entities (Workspace, ClientOrganization)
    │   └── ingestion/        # Raw Input Models
    ├── engine/               # Core Algorithms
    │   ├── MatchingEngine.java
    │   ├── RuleBasedMatchingEngine.java
    │   ├── SemanticMatchingEngine.java
    │   └── LangChain4jVectorizationEngine.java
    ├── parser/               # File extractors
    │   ├── ExcelLedgerParser.java
    │   └── PDFBankStatementParser.java
    ├── persistence/          # Database Layer
    │   └── DataStore.java    # JDBC and pgvector queries
    ├── service/              # Orchestration Controllers
    │   └── ReconciliationService.java
    └── ui/controller/        # JavaFX UI Controllers
```

---

## Testing with Sample Data

Navigate to `data/scenario_01_retail_ecommerce/` to find sample datasets:
1. **Internal Ledger**: `company_ledger_brightline.xlsx`
2. **External Bank Statement**: `bank_statement_pacific_trust.pdf`

You can use these files within the JavaFX UI to test the end-to-end ingestion, parsing, vectorization, and matching pipeline.

---

## Documentation & Design Artifacts

The `complete_documentation/` folder contains extensive architectural artifacts:
- **AVAL Class Diagram** (`AVAL_Class_Diagram.png`)
- **Sequence Diagrams** (`UC1` through `UC12` `.svg` files)
- **Manifesto & Use Case Specifications** (`Manifesto.pdf`, `Fully_Dressed_Usecases.docx`)
- **Domain & System Sequence Diagrams** (`Domain and SSD.pdf`)
---
*Built for modern financial engineering.*
