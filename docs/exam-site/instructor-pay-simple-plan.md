# Instructor pay, made simple (plan, 24 Nov 2026)

Status: **built 24 Nov, not committed, tests written but not run.** Stage 0 is done: the 18 Nov and join-code work is committed
(25048c1, b6b790e, 85e7a1c) and the V107 "class replaced by hand" work is in `git stash` (stash@{0}), not released. Neither dev database
had applied it (sample at V95, dev at V60). What differs from the plan is in section 12.

## 1. The change

Today an instructor's pay is worked out from the batch's classes. Each class has an instructor and a rate; a class whose time has
passed is "owed"; staff settle it class by class (pay, or "not taken"); and a System Admin's entry waits for the Master Admin. A routine
upload also adds every unknown instructor name as a new instructor.

The client wants this gone **for good**:
- **Classes and routines stay.** Students need them, and results hang on exam classes. They no longer produce anything to pay.
- **The Master Admin alone records each payment by hand**, on the instructor's page, under **Record payment**.
- **Payment history** lists every payment to that instructor, old and new, and the Master Admin can edit or void each one.

## 2. Decisions (24 Nov, Shahriar)

| # | Decision |
|---|---|
| D1 | Class-based pay is removed for good (no switch): rates, owed, settle, not taken, approvals, reminders. |
| D2 | Two pages stay: **Instructor payments** and **Offline instructors**. |
| D3 | The date fills in as today and can be changed, but not to a future day. |
| D4 | Type: Lecture, Exam or Other. A description is required only for Other. |
| D5 | Batch is optional. With a batch, Finance counts the payment in that batch's cost. |
| D6 | Number of classes is asked, for the record only (no calculation). |
| D7 | The instructor page has two tabs: **Record payment** and **Payment history**. |
| D8 | Every payment already recorded is kept and shown in Payment history. Finance totals do not change. |
| D9 | The Master Admin can edit a payment. |
| D10 | Master Admin only. System Admins lose the instructor pay pages and every pay action. |
| D11 | A routine upload **no longer adds unknown names as instructors**. Instructors are added only on Offline instructors. |
| D12 | Entries waiting for approval are cleared by the Master Admin on the current pages before the release; the release waits until there are none. |
| D13 | **Instructor payments** page: summary cards (this month, last month, this year) and one list of every instructor payment, filtered by date, batch, instructor and type. Each row links to its instructor. |
| D14 | Summary on the instructor page: total paid, paid this month, last payment date, number of payments, batches they teach. |
| D15 | The instructor page's **Add class** goes. Classes are added from the batch's **Classes** only. The uncommitted V107 work (an exchange added from the instructor page) is dropped. |
| D16 | The Master Admin can void a payment made by mistake, with a reason. |

## 3. Behaviour in detail

### 3.1 Record payment (instructor page, first tab)

| Field | Rule |
|---|---|
| Date | Required. Starts as today in Dhaka. Any past day; a future day is refused ("The date can't be after today."). |
| Type | Required: Lecture, Exam or Other. No default, so the admin chooses. |
| Number of classes | Optional, a whole number from 1 to 200. Shown as "4 lectures", "1 exam" or "3 classes" in history. |
| Amount | Required. More than 0, at most 2 decimals, at most ৳10,00,000. |
| Method | Required: Cash, bKash, Nagad, Rocket or Bank. Starts as Cash. |
| Reference | Optional, up to 100 characters. |
| Batch | Optional. Any batch that is not deleted, drafts included. Shown as "No batch" when empty. |
| Description | Required for Other ("Say what the payment is for."); optional for Lecture and Exam. Up to 500 characters. |
| Note | Optional, up to 500 characters. |

- On save the payment counts at once (status Approved). A toast says "Payment recorded". The form clears except the date and the
  method, and the history tab's count goes up.
- An archived instructor can still be paid; the page shows the "Archived" pill as now.

### 3.2 Payment history (instructor page, second tab)

- **One list, newest first:** the instructor's new payments and their old class payments (`offline_class_payments`).
- **Columns:** date, type, classes, batch, description, amount, method and reference, status, actions.
  - **Old class rows** show the class's kind (Lecture, Exam, Other) as the type, "1" class, and "Class: <subject> on <date>" as the
    description.
  - **Status:** Paid; Voided, with the reason on hover or tap; Rejected and old Pending rows show their status but no actions. A
    corrected row also says "Corrected on <date>".
- **Edit** opens the same form, filled in, and asks for a reason for the correction (required, up to 500 characters). It applies at
  once.
  - An old class row can change only the date, amount, method, reference and note. Its type, classes, batch and description come
    from the class.
- **Void** asks for a reason. A voided payment leaves Finance and the summary. It cannot be edited or voided again.
- **Filters:** a date range and a type. A total of the rows shown sits at the foot of the list.

### 3.3 Summary (instructor page, above the tabs)

- **Total paid:** all approved payments, old and new.
- **Paid this month:** approved, with the payment date in this Dhaka month.
- **Last payment:** its date and amount, or "None yet".
- **Payments:** how many are approved.
- **Batches:** the batches with at least one class of theirs that is not cancelled. This comes from the routine and has nothing to do
  with pay.

### 3.4 Instructor payments page (D13)

- **Cards:** paid this month, paid last month, paid this year. Each shows the amount and the number of payments, approved only.
- **List:** every approved and voided payment, old and new, with the same columns as history plus the instructor's name, which links
  to their page.
- **Filters:** from and to (starting at this month), batch, instructor and type. The total of the rows shown is at the foot.
- **No actions on this page.** Editing and voiding happen on the instructor's page.

### 3.5 Offline instructors page

- Stays the list: name, phone, batches, **paid this month**, **last payment** (these two replace "classes this month" and "owed"),
  search, Add instructor, Edit, Archive.
- Add, edit and archive are for the Master Admin only (D10, D11).

### 3.6 Classes and routines after the change

- **A class carries no money.**
  - The batch's **Classes** shows date, time, subject, topic, instructor, kind and a status: Scheduled, Held (computed from its
    end time) or Cancelled.
  - Old classes marked Paid or Not taken show as **Held** or **Not taken**. They stay locked: no edit and no delete. Their payment
    in history points at them.
- **The class edit and Add class dialogs** lose the rate field.
- **Picking an instructor**, on a class, in Add class or in the routine preview, only offers instructors already in the list. The
  option to type a new name and create one goes (D11).
- **A routine upload:**
  - A name that matches an instructor or alias is assigned as now.
  - A name that matches no one is kept on the class **as written** (`instructor_text`), with no instructor. The preview lists these
    names: "Not in the instructor list: <names>. The classes show the name as written. To link them, choose an instructor with
    Change, or ask the Master Admin to add them."
  - **Change** on the preview still links a name to an existing instructor and remembers the spelling (alias).
- **Pay-only classes** (added from the instructor page with "not in the routine", 17 Nov) stay hidden from students as now. They are
  left out of the batch's **Classes** list too, since they exist only for an old payment. That payment still shows in history.
- **Nothing pay-related is shown on the batch Overview:** the paid, owed and "needs a rate" figures go. Class counts stay.

### 3.7 Who can do what after the change

| Action | Master Admin | System Admin |
|---|---|---|
| Instructor payments and Offline instructors pages, instructor page | Yes | No (not in the menu; the page says it is for the Master Admin) |
| Record, edit or void an instructor payment | Yes | No (403) |
| Add, edit or archive an instructor | Yes | No (403) |
| Upload or confirm a routine, add or edit a class, pick an existing instructor | Yes | Yes (unchanged) |
| Payment records: instructor payments listed and corrected | Yes | No (fees and the rest unchanged) |
| Finance | Yes (unchanged) | No (unchanged) |

## 4. Data

One migration, `V107__instructor_pay_simple.sql` (V107 is free once the uncommitted V107 work is dropped; see 7.1):

```sql
ALTER TABLE offline_instructor_other_payments ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'OTHER';
ALTER TABLE offline_instructor_other_payments ADD CONSTRAINT offline_instructor_other_payments_kind_check
    CHECK (kind IN ('LECTURE', 'EXAM', 'OTHER'));
ALTER TABLE offline_instructor_other_payments ADD COLUMN class_count INTEGER NULL;
ALTER TABLE offline_instructor_other_payments ADD CONSTRAINT offline_instructor_other_payments_class_count_check
    CHECK (class_count IS NULL OR class_count BETWEEN 1 AND 200);
ALTER TABLE offline_instructor_other_payments ALTER COLUMN reason DROP NOT NULL;
CREATE INDEX offline_instructor_other_payments_paid_on_idx ON offline_instructor_other_payments (paid_on DESC);
```

- `offline_instructor_other_payments` becomes **the** instructor payment table: every new payment goes there. Its name stays, to
  avoid touching Finance, Payment records and `payment_edits` (kind `OTHER`). Existing rows become kind `OTHER` with their reason.
- **Kept but no longer written:** `offline_class_payments`, `offline_class_decisions`, `offline_batch_instructor_rates`, and
  `offline_classes.amount`, `rate_override` and the stored statuses PAID, PAID_PENDING, NOT_TAKEN and NOT_TAKEN_PENDING. History,
  Finance and rollback need them. A later cleanup can drop the rates table.
- Only additions, a nullable column and a dropped NOT NULL: the build before this release runs on the migrated database.

## 5. API, file by file

All paths are under `api/src/main/java/com/coachingcenter/api/`.

### 5.1 New

| File | What |
|---|---|
| `offline/OfflineInstructorPayService.java` | `record(instructorId, actor, body)`, saved APPROVED with kind and class count, validation as 3.1. `edit(id, actor, body)`: applies at once, sets `corrected_at`, writes a FINALIZED `payment_edits` row (kind OTHER, before and after values, reason; extra fields kept in the JSON) and an audit entry. `voidPayment(kind, id, actor, reason)` for OTHER and old CLASS payments; an old class payment's class keeps its status. `history(instructorId, filters)`, `summary(instructorId)`, `all(filters)` and `cards()`: the union of both tables, as in 3.2 to 3.4. |
| `offline/OfflineInstructorPayController.java` | `GET /api/v1/offline-instructors/{id}/payments`, `GET /api/v1/offline-instructors/{id}/pay-summary`, `POST /api/v1/offline-instructors/{id}/payments`, `PATCH /api/v1/offline-instructor-payments/{id}` (OTHER), `POST /api/v1/offline-instructor-payments/{kind}/{id}/void`, `GET /api/v1/offline-instructor-payments` (list and cards). Every route requires `OFFLINE_INSTRUCTOR_PAY`. |
| Permission `OFFLINE_INSTRUCTOR_PAY` | In `auth/permission/Permissions.java`, granted to MASTER_ADMIN only in `OfflineClassPermissionContributor` (renamed `OfflineInstructorPermissionContributor`). |

Editing an old class payment uses the existing `POST /api/v1/payment-records/CLASS/{id}/edits`, which applies at once for the Master
Admin. Its rule "a note is needed if the amount differs from the class amount" is dropped, since there is no due amount any more.

### 5.2 Changed

| File | Change |
|---|---|
| `offline/OfflineClassService.java` | Remove `recordPayment`, `requestNotTaken`, `approvePayment`, `rejectPayment`, `voidPayment`, `approveNotTaken`, `rejectNotTaken`, `approvePayments`, `approveNotTakenMany`, `unsettled`, `counts`, `snapshot`, and their records (`PaymentView`, `DecisionView`, `BulkResult`, `UnsettledPage`, `UnsettledCounts`, `Snapshot`). `ClassView` drops `amount`, `rateOverride`, `payment` and `notTakenRequest`. `create` and `update` stop accepting `rateOverride`; `create` stops accepting `inRoutine` and `replaceClassId`, which only the instructor page sent. `list` leaves out `in_routine = false` classes. Edit and delete stay refused for PAID and NOT_TAKEN classes, with the message "This class has a payment or a not-taken mark in the records, so it can't be changed." |
| `offline/OfflineClassController.java` | Remove the routes `/offline-classes/{id}/payment`, `/offline-classes/{id}/not-taken`, `/offline-class-payments/**`, `/offline-class-decisions/**`, `/offline-classes/unsettled` and `/offline-classes/unsettled/counts`. |
| `offline/OfflineRoutineService.java` | `confirm` stops calling `withNewInstructors` (method removed). `matchInstructor` drops `newName`, so only an existing instructor is accepted, and `existingOrNew` is removed. `applyRow` stops setting `amount`, and `rate()` is removed. The `unmatchedNames` view stays and is used for the preview message (3.6). |
| `offline/OfflineRoutineMatcher.java` | Unchanged apart from dropping the V107 hunks (7.1). SETTLED still keeps PAID and NOT_TAKEN classes as they are. |
| `offline/OfflineInstructorController.java` | `GET` stays on `OFFLINE_BATCH_ROUTINE` (System Admin pickers). `POST`, `PATCH` and `archive` move to `OFFLINE_INSTRUCTOR_PAY`. |
| `offline/OfflineInstructorService.java` | The list view drops figures from classes, if any. Paid this month and last payment come from the pay service. |
| `offline/OfflineOtherPaymentController.java` / `OfflineOtherPaymentService.java` | Removed; folded into the new pay service. Their audit names stay for old entries. |
| `offline/PaymentRecordController.java` | `CLASS` and `OTHER` kinds come from `OFFLINE_INSTRUCTOR_PAY`, not `OFFLINE_CLASS_SETTLE`. |
| `offline/PaymentRecordService.java` | `OTHER_ROWS` shows `coalesce(reason, initcap(kind))` as the item. The CLASS "note when the amount differs" check is removed. |
| `finance/FinanceService.java` | The OFFLINE_INSTRUCTORS source description becomes `'Instructor pay: ' || coalesce(o.reason, initcap(o.kind))`; totals are unchanged. **By instructor** keeps paid, and drops owed, unpriced and the class count (or counts payments instead). `InstructorRow` changes to match. |
| `staffnotifications/StaffNotificationKind.java` | Keep `OFFLINE_CLASSES_TO_SETTLE`, `OFFLINE_CLASSES_OVERDUE` and the class payment decision kinds so old notices render. Nothing writes them. |
| `auth/permission/Permissions.java` | Remove `OFFLINE_CLASS_SETTLE` and `OFFLINE_CLASS_APPROVE` once nothing uses them. Check that the web role payload (`canSettleOfflineClass`, `canApproveOfflineClass`) is built from this list and update it. |

### 5.3 Deleted

`offline/OfflineBatchRateController.java`, `OfflineBatchRateService.java`, `OfflineBatchInstructorRate.java`,
`OfflineBatchInstructorRateRepository.java`, `OfflineClassDigestJob.java`, `OfflineOtherPaymentController.java`,
`OfflineOtherPaymentService.java`.

`OfflineClassPayment`, `OfflineClassDecision` and their repositories stay as read models if the pay service uses JPA for the old rows.
Otherwise they are deleted, and the pay service reads with SQL.

## 6. Web, file by file

All paths are under `web/src/`.

| File | Change |
|---|---|
| `components/shell/staff-nav.ts` | `instructorPayments` and `offlineInstructors`: `access: { MASTER_ADMIN: "full" }`. |
| `components/shell/staff-shell.tsx`, `lib/api/exam/instructor-pay.ts` | Remove `useInstructorPayAttention` and the sidebar count. |
| `app/(staff)/staff/offline-instructor-payments/page.tsx`, `offline-instructors/page.tsx`, `offline-instructors/[id]/page.tsx` | `RequireStaff roles={["MASTER_ADMIN"]}`. |
| `components/staff/instructor-pay/instructor-payments-page.tsx` | Rewritten as 3.4: cards, filters (URL params) and the payments table. |
| `components/staff/instructor-pay/instructor-detail.tsx` | Rewritten: header and details as now, the summary from 3.3, and `Tabs` with `record` and `history` (`?tab=`, default `record`). The class list, Add class, Record other payment and Set rate go. |
| `components/staff/instructor-pay/record-payment-form.tsx` (new) | The 3.1 form. Used inline in the Record payment tab, and in a dialog for Edit (with the correction reason, and old class rows limited to their fields). |
| `components/staff/instructor-pay/payment-history.tsx` (new) | The 3.2 table: filters, total, Edit and Void (void dialog with reason). |
| `components/staff/instructor-pay/instructor-list.tsx` | Columns from 3.5; figures from the API, not from classes. |
| `components/staff/instructor-pay/instructor-form-dialog.tsx` | Unchanged (Master Admin only now). |
| `components/staff/instructor-pay/pay-model.ts` | Reduced to what the new pages need (month helpers, labels for type and class count). Class-based figures go. |
| **Deleted** (`instructor-pay/`) | `class-list.tsx`, `class-payment-dialog.tsx`, `not-taken-dialog.tsx`, `set-rate-dialog.tsx`, `instructor-add-class-dialog.tsx`, `other-payments.tsx`, `reject-dialog.tsx` (if nothing else uses it). |
| **Deleted** (`offline/`) | `batch-rates.tsx`, `payment-decisions.tsx`. |
| `components/staff/offline/batch-detail.tsx` | Remove the **Instructor rates** tab. |
| `components/staff/offline/batch-classes.tsx` | Remove the amount column, rate pill, pay status and actions. Statuses as in 3.6. |
| `components/staff/offline/batch-overview.tsx` | Remove the paid, owed and needs-rate figures. Keep the class counts. |
| `components/staff/offline/batch-pay-format.ts` | Drop `amountOf` and the pay statuses. Rename to `class-format.ts` if little is left. |
| `components/staff/offline/add-class-dialog.tsx` | No rates: instructors sorted by name, existing only. |
| `components/staff/offline/class-dialogs.tsx` | Remove the "Rate for this class" field. |
| `components/staff/offline/instructor-change-dialog.tsx` | Remove "type a new name" (`useSaveInstructor`). Pick from the list only. |
| `components/staff/offline/routine-preview.tsx` | Remove the "Exam row: check who is paid" flag text and the V107 "replaced by hand" section (7.1). New message for unmatched names (3.6). The `newName` label for unknown names becomes "<name> (not in the list)". |
| `lib/api/exam/instructor-pay.ts` | New types and hooks: `InstructorPayment`, `PaySummary`, `useInstructorPayments`, `usePaySummary`, `useRecordInstructorPayment`, `useEditInstructorPayment`, `useVoidInstructorPayment`, `useAllInstructorPayments`. Remove the unsettled, class payment, decision and other-payment hooks. |
| `lib/api/exam/offline-batch-pay.ts` | Remove rates and class payment types and hooks. `ClassRow` drops `amount`, `rateOverride`, `payment` and `notTakenRequest`. |
| `lib/auth/guards.tsx` | Replace `canSettleOfflineClass` and `canApproveOfflineClass` with `canPayInstructors`, which only the Master Admin has. |
| `components/staff/finance/finance-tabs.tsx` | **By instructor:** remove the owed and pending columns, keep paid (and payments). |
| `components/staff/payment-records/payment-records-page.tsx` | No change beyond what the API sends; the instructor kinds disappear for a System Admin. |
| `components/staff/notifications/notification-text.ts` | Keep the old kinds' text (old notices). |
| `i18n/messages/{en,bn}/instructorPay.ts`, `batchPay.ts`, `staffOffline.ts`, `finance.ts` | New strings in English and natural Bangla (tabs "Record payment" / "পেমেন্ট লিখুন", "Payment history" / "পেমেন্টের ইতিহাস", types, fields, errors, summary, the unmatched-names message). Unused keys removed from both languages so they stay in step. |

## 7. Order of work

### 7.1 Stage 0: clear the ground (before any code)

The uncommitted tree holds three separate pieces of work:
- the **join code switch** (24 Nov);
- **the admitted date is never guessed** (18 Nov: `setup-form.tsx`, `staffOffline.ts` `admittedOnHint`, `checklist.test.tsx`);
- **V107, exchange "replaced by hand"** (18 Nov).

1. Commit the join code switch and the admitted-date change as two commits, or keep them aside: Shahriar's call.
2. **Drop V107.** Revert only its hunks:
   - `V107__class_replaced_by_hand.sql` (delete the file);
   - `OfflineClass.replacesClassId`;
   - `OfflineClassService` `setReplacesClassId`;
   - `OfflineRoutineMatcher` REPLACED;
   - `OfflineRoutineService` `replacedByHand`;
   - `routine-preview.tsx` replaced section;
   - `batchPay.ts` (en/bn) `replacedByHand*`;
   - `offline-batch-pay.ts` `replacedByHand`;
   - `kept.test.tsx`, `OfflineClassSettleIT`, `OfflineAdminIT` and `OfflineRoutineMatcherTest` V107 cases.

   Look at each hunk before reverting it. Anything that is not V107 stays.
3. Check the dev database (`coaching_center_sample`): if `flyway_schema_history` has version 107, remove that row and drop
   `offline_classes.replaces_class_id`, with Shahriar's go (sample DB rule). Otherwise the new V107 will not apply there.

**Gate:** a clean tree, or only the commits Shahriar chose, and a dev DB without V107.

### 7.2 Stages

| Stage | Work | Gate |
|---|---|---|
| 1 | Migration (section 4); `OfflineInstructorPayService` and controller; permission `OFFLINE_INSTRUCTOR_PAY`; instructor create, edit and archive moved to it; Payment records kinds; Finance description. | Code review of the API diff. |
| 2 | Removals (5.2, 5.3): class pay, rates, digest job, other-payment service, routine instructor creation, class view trimmed; Finance **By instructor**; `AuditLogIT` route list. | It compiles; a grep for each removed route and hook finds nothing. |
| 3 | Web, pay pages: menu and guards, instructor page (tabs, form, history, summary), Instructor payments page, Offline instructors list, hooks. | Shahriar checks on the dev server with the sample DB: record, edit, void, history with old class payments. |
| 4 | Web, batch side: rates tab, classes, overview, Add class, class edit, instructor change, routine preview; Finance **By instructor**; dead files and keys removed. | Typecheck; Shahriar uploads a routine with an unknown name on dev and checks a batch page. |
| 5 | Tests (section 8). | Written; run only when Shahriar says. |
| 6 | Docs: client guide section 8 rewritten; the "who does what" table (System Admin rows for instructor pay go); section 7 (routine names). | Read-through. |
| 7 | Release (section 9). | Live checks done. |

## 8. Tests

| File | Change |
|---|---|
| `api/.../offline/OfflineClassSettleIT.java` | Replaced by `OfflineInstructorPayIT`. Covered: the Master Admin records a payment (Lecture with 4 classes, Exam, Other); Other without a description is a 400; a future date is a 400; a past date is fine; the batch is optional. Edit applies at once, sets `corrected_at` and writes a FINALIZED `payment_edits` row; void with a reason. The history lists an old class payment (seeded) with its class's kind; the summary and cards count approved rows only. A System Admin gets 403 on every pay route and on instructor create. |
| `OfflineRoutineIT.java` | Confirming with an unknown name makes **no** instructor; the class keeps `instructor_text` with no instructor; `matchInstructor` with `newName` is a 400 and with an existing instructor still sets the alias. No `amount` is set. |
| `OfflineInstructorDataIT.java`, `OfflineClassNoticesIT.java`, `OfflineAdminIT.java` | Drop the rate, payment and not-taken steps; keep the class and routine checks. |
| `StaffNotificationWriterTest.java` | Drop the digest kinds' writer cases (the kinds stay for rendering). |
| `common/audit/AuditLogIT.java` | Route list: remove the old routes, add the new ones. |
| `finance` ITs | Totals the same with an old class payment and a new payment; **By instructor** without owed. |
| `web/tests/instructor-pay/*` | `payments-page.test.tsx` rewritten (cards, filters, list); new `instructor-detail.test.tsx` (tabs, form rules from 3.1, history, edit, void, old class row limited fields); `nav.test.tsx` (System Admin sees neither item); `notifications.test.ts` (old kinds still render); fixtures updated. |
| `web/tests/batch-pay/*` | `rates.test.tsx` deleted. `classes.test.tsx`, `tabs.test.tsx`, `publish.test.tsx` and `add-class-instructors.test.tsx` lose the pay and rate parts. `preview.test.tsx` gets the unmatched-names message. Fixtures updated. |

## 9. Release

1. **Clear the waiting entries (D12).** On the server, each of these must be 0. The Master Admin clears them on the current pages
   first.
   ```sql
   select count(*) from offline_class_payments where status = 'PENDING';
   select count(*) from offline_class_decisions where status = 'PENDING';
   select count(*) from offline_instructor_other_payments where status = 'PENDING';
   select count(*) from payment_edits where status = 'PENDING' and kind in ('CLASS', 'OTHER');
   ```
2. **Note the figures:** Finance's instructor expense for this month and for all time, and the number of approved class payments and
   other payments. Back up the database.
3. **Deploy the API and the web build together**, the web built with `OFFLINE_ADMISSION=1`. Flyway runs V107 (or the next free number
   if something else ships first).
4. **Live checks (Master Admin):**
   - Finance's figures match step 2.
   - An instructor with old class payments shows them in Payment history.
   - Record a ৳1 test payment, edit it, then void it. Finance is back to the step 2 figure.
   - A routine upload (or preview) with an unknown name makes no instructor.
5. **Live checks (System Admin):** neither page is in the menu; `/staff/offline-instructors` says it is for the Master Admin;
   routines and classes still work.
6. **Rollback:** the previous jar and web build. The migration is additive, so the old build runs on it; payments recorded in the
   meantime show in the old build as "other payments" with their reason (or an empty reason for Lecture and Exam).

## 10. Risks

- **Waiting entries left at release** would sit with no page to decide them. Step 9.1 prevents it.
- **"Owed" disappears.** The office loses the reminder of who is unpaid. This is the client's choice, and the client guide says so.
- **Unknown routine names are no longer instructors.** Classes still show the name. To pay that person, the Master Admin adds them
  first, then links the name with **Change** on the next preview (or on the class).
- **Old classes stay locked** (Paid or Not taken), so their payments keep their batch and subject.
- **The dev database and V107** (7.1 step 3): skipping the check breaks Flyway on dev.

## 11. Not in this change

- Dropping the old tables and columns (a later cleanup).
- Any change to fees, staff pay, online payments or results.
- Exporting the instructor payment list (Finance's **All entries** export already includes them).

## 12. As built (24 Nov)

- **`payment-decisions.tsx` stays.** It is the fee approval buttons (Fee approvals, the fees tab), not instructor pay; section 6 was wrong to list it.
- **Voiding a class payment from before unlocks its class** (PAID back to SCHEDULED), as a void did before, so the class can be edited again.
- **One more route:** `GET /api/v1/offline-instructor-payments/figures` gives each instructor's paid-this-month, last payment and batches for
  the Offline instructors list, so that list does not read every batch's classes. Master Admin only.
- **Instructor add, edit and archive** are `OFFLINE_INSTRUCTOR_PAY` (Master Admin). The list stays on `OFFLINE_BATCH_ROUTINE`, so a System
  Admin can still pick an instructor on a class or a routine row. The "type a name" option in the Change instructor dialog accepts only a
  name or alias already in the list.
- **Add class (batch)** still lists the batch's instructors first: they are now the ones who teach its classes, not the ones with a rate.
- **Edits of a new payment** write a FINALIZED `payment_edits` row with the money fields Payment records reads, plus kind, class count, batch
  and description after them. The reference stays optional (Payment records' own edit still requires one).
- **Removed for good:** `OfflineBatchRate*`, `OfflineClassDigestJob`, `OfflineClassNotices`, `OfflineOtherPayment*`, the class pay routes and
  their web screens (`class-list`, `class-payment-dialog`, `not-taken-dialog`, `set-rate-dialog`, `instructor-add-class-dialog`,
  `other-payments`, `reject-dialog`, `batch-rates`). The old notice kinds stay in the enum and the text, so old notices still read.
- **Tests:** `OfflineClassSettleIT` became `OfflineClassIT` (held, delete, student routine, no pay); `OfflineInstructorPayIT` is new;
  `OfflineClassNoticesIT` and `rates.test.tsx` are deleted; `OfflineRoutineIT`, `OfflineInstructorDataIT`, `FinanceIT`, `AuditLogIT` and the
  batch-pay and instructor-pay web tests are updated. Web typecheck and lint pass. No Java compile or test run yet.
