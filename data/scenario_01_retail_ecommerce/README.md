# Scenario 01 — Retail / E-Commerce Reconciliation

## Overview

This scenario simulates a full fiscal year (January–December 2025) of financial activity for **Brightline Commerce Inc.**, a mid-sized retail and e-commerce company. It is designed to test an AI reconciliation engine's ability to ingest two independent financial sources and surface discrepancies without being told where they are.

The two files represent what each party recorded independently:

| File | Source | Format | Rows |
|------|--------|--------|------|
| `company_ledger_brightline.xlsx` | Internal accounting system (e.g. QuickBooks / NetSuite export) | Excel (.xlsx) | 500 |
| `bank_statement_pacific_trust.pdf` | Pacific Trust Bank — official account statement | PDF | ~497 |

Neither file contains any flags, labels, or annotations pointing to discrepancies. The reconciliation engine must discover all anomalies on its own by comparing the two sources.

---

## The Company

**Brightline Commerce Inc.**
- Industry: Retail / E-Commerce
- Bank: Pacific Trust Bank — Account `****-****-****-7842`
- Fiscal Year: January 1, 2025 – December 31, 2025
- Currency: USD

Transaction types include: sales revenue, customer refunds, inventory purchases, supplier payments, shipping and logistics, digital marketing, platform fees (Shopify), payroll, office rent, utilities, SaaS subscriptions, loan repayments, tax payments, equipment purchases, consulting/legal fees, interest income, and miscellaneous income.

---

## Files

### `company_ledger_brightline.xlsx`

The internal general ledger as it would be exported from an accounting system. Contains two sheets:

- **General Ledger** — 500 journal entries with the following columns:
  - `Entry No.` — sequential journal entry ID (JE-0001 through JE-0500)
  - `Date` — transaction date (MM/DD/YYYY)
  - `Category` — transaction type (e.g. "Payroll", "Inventory Purchase")
  - `Account Code` — chart of accounts code (4000–8100)
  - `Vendor / Counterparty` — payee or payer name
  - `Reference No.` — shared reference number used to match against bank entries (REF-XXXXXXXX)
  - `Type` — DEBIT or CREDIT
  - `Debit (USD)` / `Credit (USD)` — amount columns
  - `Running Balance (USD)` — cumulative balance after each entry

- **Category Summary** — pivot-style summary of totals and entry counts per category

### `bank_statement_pacific_trust.pdf`

An official-style bank statement issued by Pacific Trust Bank. Contains:
- Account summary (opening balance, total credits, total debits, closing balance)
- Monthly activity breakdown (12 months)
- Full chronological transaction listing with date, description, reference number, debit/credit columns, and running balance

The bank statement does **not** include journal entry numbers or account codes — it only has what the bank recorded on its end.

---

## Embedded Anomalies

The following problems are hidden in the data and should be discovered by the reconciliation engine. **The total count of each is known; the specific transactions are not labeled.**

### 1. Amount Mismatches (18 transactions)

The same transaction (identifiable by its Reference No.) appears in both files, but the amounts differ. This simulates real-world causes such as:
- Bank processing fees deducted at source
- Rounding differences from currency conversion
- Manual data entry errors in the internal ledger
- Partial payments recorded differently by each party

**How to detect:** Match on `Reference No.`. Where a match exists, compare the ledger amount to the bank amount. A non-zero difference is a mismatch.

### 2. Missing Transactions (15 transactions)

These entries exist in the company ledger but have no corresponding transaction in the bank statement. This simulates:
- Payments recorded in the books but not yet cleared by the bank (outstanding checks)
- Ledger entries made in error (ghost transactions)
- Timing differences where the bank will post the transaction in a future period

**How to detect:** After matching all Reference Nos. between the two files, any ledger entry with no bank counterpart is flagged as missing/uncleared.

### 3. Duplicate Bank Postings (12 transactions)

The bank statement contains 12 transactions that were posted twice — same reference number, same amount, same description, but on dates 1–3 days apart. This simulates:
- Bank system errors where a payment was processed twice
- Double-charging by a payment processor
- ACH/wire retries that weren't reversed

**How to detect:** Within the bank statement alone, any Reference No. that appears more than once with the same amount is a candidate duplicate.

---

## Reconciliation Logic (Expected Engine Behavior)

A correct reconciliation of this scenario should:

1. **Parse** both files and normalize into a common transaction schema
2. **Match** records using `Reference No.` as the primary key
3. **Compare** matched pairs for amount equality
4. **Flag unmatched** ledger entries as missing from the bank
5. **Flag unmatched** bank entries as unexpected (not in ledger) — these will include the duplicates
6. **Identify duplicates** within the bank statement by grouping on Reference No. + Amount
7. **Produce a reconciliation report** summarizing:
   - Total matched (clean)
   - Mismatches (count + delta)
   - Missing from bank
   - Duplicate bank postings

---

## Ground Truth (for evaluation)

| Anomaly Type | Count |
|---|---|
| Amount mismatches | 18 |
| Missing from bank | 15 |
| Duplicate bank postings | 12 |
| **Total anomalies** | **45** |
| Clean matched transactions | ~470 |

Use this table to evaluate precision and recall of the reconciliation engine's output.
