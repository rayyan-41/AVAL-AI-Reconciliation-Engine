# Scenario 02 — Multi-Source Consolidation & Anomaly Detection

## Overview

This scenario simulates a full Q1 2025 (January–March) of financial activity for **Meridian Logistics Group**, a mid-sized transportation and logistics company. It is purpose-built to demonstrate **UC9 (Multi-Source Consolidation)** and **UC10 (Identify Financial Anomalies)** in isolation, with a clean baseline of standard matched transactions to contrast against.

| File | Source | Format | Rows |
|---|---|---|---|
| `company_ledger_meridian.xlsx` | Internal accounting system export | Excel (.xlsx) | 59 |
| `bank_statement_northern_capital.pdf` | Northern Capital Bank — official statement | PDF | 43 |

Neither file labels or flags the consolidation groups or anomalies. The engine must discover them on its own.

---

## The Company

**Meridian Logistics Group**
- Industry: Transportation & Logistics
- Bank: Northern Capital Bank — Account `****-****-****-4519`
- Period: Q1 2025 (January 1 – March 31, 2025)
- Currency: USD
- Opening Ledger Balance: $500,000.00
- Opening Bank Balance: $285,000.00

Transaction categories include: client freight revenue, subcontractor costs, fuel expenses, vehicle maintenance, office rent, insurance, payroll, toll & road taxes, SaaS subscriptions, driver allowances, vehicle licensing, port handling fees, equipment hire, legal & compliance, utilities, and equipment purchases.

---

## What to Expect After Running the Engine

### Step 1 — Load Ledger & Bank Statement
Ingest `company_ledger_meridian.xlsx` as the Internal Ledger and `bank_statement_northern_capital.pdf` as the External Bank Statement. The ingestion pipeline will parse, standardize, and vectorize all transactions.

### Step 2 — Run Reconciliation
The hybrid matching engine will automatically match **35 transaction pairs** (same amount, bank date within 1–2 days of ledger date). These will either auto-confirm at ≥95% confidence or appear in the pending review queue.

After reconciliation the following will be **left unmatched**:

| Pool | Count | Reason |
|---|---|---|
| Unmatched Ledger | 24 | 12 consolidation entries + 7 anomaly entries + 5 timing entries |
| Unmatched Bank | 8 | 4 consolidation bank entries + 1 weekend bank entry + 3 bank charges |

### Step 3 — UC10 Anomaly Detection (Manual Check screen)
The anomaly engine runs on the unmatched pool and will flag:

| Anomaly | Type | Description |
|---|---|---|
| `REF-DUPA01` + `REF-DUPA02` | **DUPLICATE** | Two ledger entries on 02/03/2025 both for $975.00 — "Emergency Repair - Fleet Truck #12" |
| `REF-DUPB01` + `REF-DUPB02` | **DUPLICATE** | Two ledger entries on 03/17/2025 both for $1,400.00 — "Liability Insurance - Q2 Premium" |
| `REF-OTL001` | **OUTLIER** | Ledger entry on 03/28/2025 for $85,000.00 — "Heavy Freight Truck - 30T Capacity Unit #7" (far exceeds 3 standard deviations of unmatched pool) |
| `REF-WKD001` | **WEEKEND** | Ledger entry on 01/19/2025 (Sunday) — "Emergency Toll - Northern Mountain Pass" |
| `REF-WKD002` | **WEEKEND** | Ledger entry on 02/23/2025 (Sunday) — "Emergency Driver Callout - Karachi Port Night" |
| `REF-WKD003` | **WEEKEND** | Bank entry on 03/02/2025 (Sunday) — "Emergency Customs Duty WKND Processing" |

Dismiss these anomalies in the Manual Check screen to proceed.

### Step 4 — UC9 Multi-Source Consolidation (Manual Check screen)
After dismissing anomalies, use the **Multi-Source Consolidation** panel to resolve the four consolidation groups. For each group: select the bank target, multi-select the ledger entries, and click Consolidate.

---

## UC9 Consolidation Groups (Ground Truth)

### Group A — Subcontractor Batch Payment
**Bank target:** `BATCH-SUB-JAN1` — 01/17/2025 — **$2,700.00 DEBIT**

Select these 3 ledger entries (sum = $2,700.00, tolerance 0%):

| Reference | Date | Vendor | Amount |
|---|---|---|---|
| REF-CSUB01A | 01/15/2025 | Ahmad Transport Services | $1,200.00 |
| REF-CSUB01B | 01/15/2025 | Raza Freight Co. | $850.00 |
| REF-CSUB01C | 01/16/2025 | Khan & Sons Carriers | $650.00 |

**Business context:** Three individual driver subcontractor invoices were paid in a single batch transfer by the accounts team. The bank only records one outgoing payment.

---

### Group B — Fuel Card Monthly Settlement
**Bank target:** `FUELCO-JAN-01` — 01/31/2025 — **$1,300.00 DEBIT**

Select these 4 ledger entries (sum = $1,300.00, tolerance 0%):

| Reference | Date | Vendor | Amount |
|---|---|---|---|
| REF-CFUEL01A | 01/08/2025 | Fuel Station - Lahore Hub | $340.00 |
| REF-CFUEL01B | 01/14/2025 | Fuel Station - Karachi Port | $285.00 |
| REF-CFUEL01C | 01/22/2025 | Fuel Station - Islamabad Depot | $410.00 |
| REF-CFUEL01D | 01/27/2025 | Fuel Station - Peshawar Route | $265.00 |

**Business context:** The company uses a corporate fuel card. Individual fill-ups are recorded separately in the ledger as they occur, but the fuel card company consolidates all charges into one monthly debit at month-end.

---

### Group C — Vehicle Maintenance Batch
**Bank target:** `MECH-BATCH-FEB` — 02/14/2025 — **$900.00 DEBIT**

Select these 2 ledger entries (sum = $900.00, tolerance 0%):

| Reference | Date | Vendor | Amount |
|---|---|---|---|
| REF-CMNT01A | 02/10/2025 | Fleet Garage A - Full Service | $520.00 |
| REF-CMNT01B | 02/12/2025 | Fleet Garage B - Brake Repair | $380.00 |

**Business context:** Two separate workshops serviced different vehicles. The company's maintenance coordinator issued a consolidated payment cheque for both workshops on 02/14.

---

### Group D — Client NovaCorp Consolidated Payment
**Bank target:** `NOVA-PAY-MAR` — 03/10/2025 — **$5,000.00 CREDIT**

Select these 3 ledger entries (sum = $5,000.00, tolerance 0%):

| Reference | Date | Description | Amount |
|---|---|---|---|
| REF-CREV01A | 03/05/2025 | NovaCorp - Freight Invoice #1 | $2,500.00 |
| REF-CREV01B | 03/05/2025 | NovaCorp - Storage Invoice #2 | $1,800.00 |
| REF-CREV01C | 03/06/2025 | NovaCorp - Customs Handling #3 | $700.00 |

**Business context:** NovaCorp received three separate service invoices from Meridian across two days. They paid all three in a single wire transfer on 03/10.

---

## Ground Truth Summary

| Category | Count |
|---|---|
| Clean matched pairs (engine auto-resolves) | 35 |
| UC9 consolidation groups | 4 |
| UC9 ledger entries requiring consolidation | 12 |
| UC9 bank entries (consolidated payments) | 4 |
| UC10 duplicate pairs | 2 pairs (4 entries) |
| UC10 statistical outliers | 1 |
| UC10 weekend postings | 3 (2 ledger, 1 bank) |
| Extra unmatched (timing / bank charges) | 8 (5 ledger, 3 bank) |

After resolving all four consolidation groups and dismissing the 6 anomalies, the report gate should unlock cleanly with all transactions accounted for.
