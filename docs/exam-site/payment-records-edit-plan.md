# Plan: a page to correct recorded payments (15 Nov 2026)

Status: **built 15 Nov (Flyway V104); browser check and suites passed; not deployed.**

## 1. Decisions (Shahriar, 15 Nov)

| # | Decision |
|---|---|
| P1 | The page covers **student course fee payments, instructor payments and direct enrolment payments**. |
| P2 | **Editable:** amount, paid-on date, method, reference and note. Who the payment belongs to (student, class, instructor, plan) stays. |
| P3 | **Staff** (System Admins) may propose an edit; it **waits for the Master Admin**, who finalizes or rejects it. The **Master Admin's own edit is final at once**. |
| P4 | A corrected fee receipt **keeps its number** and says "Corrected on <date>". |
| P5 | **Finance reflects the changes:** it reads the payment rows, so a finalized edit changes every Finance figure. A waiting edit changes nothing. |

## 2. Who sees what

- **Course fee payments and instructor payments:** the Master Admin and System Admins (they record them today).
- **Direct enrolment payments:** the Master Admin only (only the Master Admin records them).
- **Finalize and reject:** the Master Admin only.

## 3. The page: Money → Payment records

**Filters:**
- Period: today, this month, last month, custom dates.
- Kind: all, course fees, instructor payments, direct enrolment.
- Batch.
- Instructor.
- Search: name, reference or receipt.

**Each row:**
- Date, kind, who (student or instructor), batch, amount, method, reference, receipt and status.
- "Corrected" when an edit was finalized.
- "Edit waiting" when an edit is waiting.

**Editing:**
- **Edit** opens the payment's fields with a required reason.
- A staff edit waits; the Master Admin's applies at once.

**Waiting edits** (a tab for the Master Admin):
- Each waiting edit shows the old and new values, who asked, when and why.
- **Finalize** or **Reject** (with a reason).
- The person who asked is told the decision.

**Correction history** (a third tab): the finalized and rejected corrections (`GET /api/v1/payment-records/edits?status=DECIDED`).

## 4. Rules when an edit applies

- **Any payment:** a voided or rejected payment cannot be edited. One waiting edit per payment at a time. The date cannot be in the future.
- **Course fee payment:**
  - **Approved, after onboarding:** its matching to the instalments is redone with the new amount. The new amount cannot be more than
    what is owed once this payment's old amount is taken back.
  - **Approved, before onboarding:** the amount cannot be more than the course fee less the other approved payments.
  - **Pending:** the fields change; the approval checks it as usual.
  - **Receipt:** same number, "Corrected on <date>".
- **Instructor payment:** the class's amount due stays. A paid amount different from it needs a note, as when recording.
- **Direct enrolment payment:**
  - Date, method, reference and note can change on any payment that is not voided.
  - **The amount only on the latest one**, as only the latest can be voided: each later receipt carries a running total. Its
    running total, what is due after it and the next due are worked out again.
  - The reference must stay unique on the plan.
- **Audit:** every edit keeps its before and after values (`payment_edits`), and each step is in the audit log.

## 5. Data (V104)

- `payment_edits`: kind, payment, status (PENDING, FINALIZED, REJECTED), the before and after values (JSON), the reason, who asked
  and when, who decided, when and why.
- `corrected_at` on `offline_fee_payments`, `offline_class_payments` and `live_exam_direct_payments`.
- All additive, so a rollback is safe.

## 6. Checks

- **API tests:**
  - each kind;
  - staff waits and the Master Admin's edit is final;
  - finalize and reject;
  - the fee rematching and the limits;
  - the direct "latest only" rule;
  - Finance totals before and after;
  - the filters.
- **Web tests:** the page, the filters, the edit dialog and the waiting tab.
- **Browser check,** then the release.
