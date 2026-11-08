# Plan: payments and installments on direct enrolment

Status (9 November 2026): **released.** Live since 01:45 Dhaka on 9 November, Flyway 80, including the package extension in section 14 and the gaps in section 14.11. Release plan and record: [direct-enrolment-release.md](direct-enrolment-release.md) and `../reports/client-review-deploy.md`. The browser walkthrough (10.3), the regression checks (10.4) and the Master Admin's live checks are still open. Section 14.2 has the audit. Section 0 records what was built and where the build differs from this plan. The site is live and takes real payments, so section 11 (how this ships) governs every phase.

## 0. Build log

### Phase 1: schema, enrolment with a payment, price quote (built)

| File | What it does |
|---|---|
| `api/src/main/resources/db/migration/V79__direct_enrolment_payments.sql` | Three new tables: `live_exam_payment_plans`, `live_exam_installments`, `live_exam_direct_payments`. Nothing existing is changed |
| `auth/permission/Permissions.java`, `exams/LiveExamPermissionContributor.java` | `LIVE_EXAM_INSTALLMENT`, Master Admin only |
| `common/audit/AuditRecorder.java` | `INSTALLMENT_PLAN_CREATED`, `INSTALLMENT_PAYMENT_RECORDED` and their entity names |
| `exams/InstallmentSchedule.java` | Pure rules: validation (section 4) and receipt figures. No database |
| `exams/LiveExamPaymentPlanService.java` | Writes the plan, its schedule, the first payment and its receipt number, inside the grant's transaction |
| `exams/LiveExamGrantAdminService.java` | `grant` takes the optional `payment`; `reason` is required only for No charge; a free exam refuses a payment; a race on the one-active-grant index returns `GRANT_ALREADY_ACTIVE`. New `quote` |
| `exams/LiveExamGrantAdminController.java` | New `GET /api/v1/live-exam-grants/quote`, permission `LIVE_EXAM_GRANT` |
| `src/test/.../exams/InstallmentScheduleTest.java` | Unit tests for the schedule rules and receipt figures. Written, not run |

Behaviour without a `payment` is unchanged, so the current web forms keep working against this API.

### Phase 2: payments, receipts and the routes (built, not compiled or tested)

| File | What it does |
|---|---|
| `api/src/main/resources/db/migration/V80__installment_notices_and_mail.sql` | Re-creates `student_notices_kind_check` from its V60 list and `exam_outbound_mail_kind_check` from its V38 list, each with the new kinds. Adds `student_notices.installment_id` and `direct_payment_id` |
| `exams/InstallmentCalculator.java` (new) | Pure. Each row's paid and uncovered amounts, the row state, and the plan status: COMPLETED, ACCESS_REVOKED, OVERDUE, DUE_SOON or ACTIVE |
| `exams/InstallmentPlanReader.java` (new) | Read model: the staff list (20 a page, most overdue first), the summary, a plan's detail, a receipt's figures, and a student's plans by grant. Ownership through `studentOwner` |
| `exams/InstallmentPlanService.java` (new) | Writes, each under a `FOR UPDATE` lock on the plan: record a payment, void the latest, change the unpaid rows, restore access |
| `exams/InstallmentPlanController.java` (new) | Staff routes. Each checks `LIVE_EXAM_INSTALLMENT` |
| `exams/StudentInstallmentController.java` (new) | `GET /api/v1/me/installment-plans/{id}` and `GET /api/v1/me/installment-payments/{id}/receipt.pdf`. Another student's row is 404 |
| `exams/LiveExamReceiptPdf.java` | `renderDirect` for a direct payment. Shared pieces extracted: `header` and `billedTo` take plain values, `itemRow`, `detailsBlock`. The online receipt's coordinates, text and order are unchanged |
| `exams/InstallmentSchedule.java` | Adds `coveredCount` and `validateChange` (the schedule-change rules) |
| `exams/LiveExamPaymentPlanService.java` | `REFERENCE` and `MAX_NOTE` made package-private, so the payment route uses the same rules |
| `exams/LiveExamGrantAdminService.java` | `GrantView` gains `paymentPlanId` and `receiptNo`, filled by `grant()` when a payment was taken. The 12-argument `view(grant)` stays for revoke |
| `exams/LiveExamModuleService.java`, `exams/LiveExamGrantRepository.java` | Package delete is refused with `PACKAGE_HAS_INSTALLMENTS` while a plan's package grant is unrevoked (native `exists` query). Archive is unchanged |
| `exams/StudentOwnAccessService.java` | Offline rows: a revoked grant with a plan stays listed (`accessRemoved`), the deleted-at filter is skipped for such rows, and each row gains `payment` (plan, paid, due, next date, status) or null |
| `exams/ExamMailQueue.java`, `exams/ExamMailTexts.java`, `common/mail/EmailTemplates.java` | `INSTALLMENT_RECEIPT`: the row is written in the payment's transaction and sent by the existing scan. Bilingual `installmentReceipt` template with the receipt number and a link to `/my-modules`. Nothing goes to an unverified address or for a voided payment |
| `exams/ExamOutboundMail.java` | `INSTALLMENT_RECEIPT` constant |
| `common/audit/AuditRecorder.java` | `INSTALLMENT_PAYMENT_VOIDED`, `INSTALLMENT_SCHEDULE_CHANGED`, `INSTALLMENT_ACCESS_RESTORED` |
| `common/error/ErrorCode.java` | `INSTALLMENT_OVERPAYMENT`, `INSTALLMENT_DUPLICATE_REFERENCE`, `INSTALLMENT_VOID_NOT_LATEST`, `INSTALLMENT_RESTORE_REFUSED`, `PACKAGE_HAS_INSTALLMENTS`. The web copy is not touched (Phase 5) |
| `src/test/.../exams/InstallmentCalculatorTest.java` | Unit tests for standing, coverage and the schedule-change rules. Written, not run |

Deviations in Phase 2:

| Plan said | Built | Why |
|---|---|---|
| V80 kind lists: the V60 and V38 lists plus the Phase 3 kinds (section 5) | `exam_outbound_mail` also allows `INSTALLMENT_PAYMENT_VOIDED` and `INSTALLMENT_SCHEDULE_CHANGED` | The Phase 2 brief lists them for the mail table. Nothing inserts them in Phase 2 |
| Section 9, Phase 1 row, puts `InstallmentCalculator` in Phase 1 | Built in Phase 2, with the read model | Phase 1 built only `InstallmentSchedule`. The staff detail and the student's offline rows are the first to need coverage and status |
| Section 6: `PUT .../schedule` replaces the unpaid rows | The request is the whole schedule. Its first rows must equal the covered rows exactly, or `VALIDATION_FAILED` on `schedule` | One explicit list, validated in one place. E42 is a comparison, not an inference |
| Section 7: receipt email "queued after commit with AfterCommit" | The row is written in the payment's transaction and sent by the 5-minute scan | Phase 2 brief, item 5. A send failure cannot touch the payment, and the scan retries |
| Section 4: restore refused when the student has access "on the target" | Checked with `LiveExamAccess`, on any route (a package, a roster or the exam itself) | Otherwise the student could hold two routes to the same exam |
| Section 4, C9: package with a live plan cannot be deleted | Only MODULE-scope grants count | An exam grant that names a package does not depend on that package |
| Section 4 status list | `ACCESS_REVOKED` takes precedence over `OVERDUE`. The Overdue filter therefore lists active plans only | A revoked plan is shown as revoked. Phase 5 may need a separate flag if the Overdue filter must include revoked plans |
| Voiding a payment already voided | `INSTALLMENT_VOID_NOT_LATEST` | No separate code, and the payment is no longer the latest live one |
| Refusals: overpayment, duplicate reference, void, restore | HTTP 409 | They conflict with the plan's current state. 400 is for bad input |
| Receipt email for a payment voided before the scan runs | Not sent | The receipt is no longer valid. The void notice is Phase 3 |
| Offline rows: the exam's end | Read with deleted rows included | A card with a plan keeps its end even if the exam is soft-deleted |
| Package deletion message | English only, `PACKAGE_HAS_INSTALLMENTS` | The web copy is Phase 5 |

Not verified by reading, to check when the tests are run: compilation; the SQL in `InstallmentPlanReader` and `InstallmentPlanService` against V79 and V80; the native `exists` query; Spring wiring with the new constructor arguments (`LiveExamModuleService`, `ExamMailTexts`, `StudentOwnAccessService`); the receipt PDF for PART PAID, PAID, VOID, a one-row plan, a long title and an adjusted price; that the online receipt still renders identically on the same order; the Bangla email copy, which Shahriar must read.

### Phase 3: notifications, reminders and the staff tab (built, not compiled or tested)

| File | What it does |
|---|---|
| `exams/StudentNoticeWriter.java` | Five money kinds, each with its own insert. `installment_id` or `direct_payment_id` is set, `exam_id` and `module_id` are null, and the ACTIVE student check is in the SQL. Keys: the payment id; student, installment and due date (plus the ISO week for overdue); plan and time for a schedule change |
| `exams/NoticeGroup.java` | Installment kinds map to EXAMS explicitly in `of()`. `isInstallment` added |
| `exams/StudentInboxService.java` | `FROM` joins the installment, the payment, the plan and the target (no deleted-at filter), and hides a due-soon or overdue notice whose `installment_id` is null. `NoticeView.installment` (`NoticeInstallment`) added. Unread counts and mark-all-read use the same `FROM` |
| `staffnotifications/StaffNotificationKind.java` | `INSTALLMENT_PAYMENT_RECORDED`, `INSTALLMENT_DUE_SOON`, `INSTALLMENT_OVERDUE` |
| `staffnotifications/StaffNotificationGroup.java` (new) | `INSTALLMENT` and `OTHER`, and `parse` |
| `staffnotifications/StaffNotificationService.java`, `StaffNotificationController.java` | `group` filter on the list. `unread-count` gains `byGroup`; `count` is kept |
| `exams/InstallmentStaffNotices.java` (new) | Payment notice written after commit (`AfterCommit`, own transaction, failure logged). Reminder notice joins the job's transaction. Payloads are built with `LinkedHashMap` and `fitText`, with no reference |
| `exams/InstallmentReminderJob.java` (new) | Cron 06:30 Dhaka, off with `app.exam-jobs.scheduled`, `AtomicBoolean`, one transaction per plan. Takes the plan's row lock, reads the oldest uncovered row, calls `SalaryReminders.reminderFor`, then the student's notice and email and the Master Admin's notice and email |
| `exams/InstallmentPlanReader.java` | `dueRow(installmentId)` (facts at send time) and `installmentId(planId, seq)` |
| `exams/ExamMailQueue.java` | `enqueueInstallmentReminderForStudent` (verified, active) and `enqueueInstallmentReminderForMaster`. `masterAdmin()` made package-private |
| `exams/ExamMailTexts.java` | `prepare` branches for DUE_SOON and OVERDUE, re-checked at send time. `installmentKey` |
| `exams/ExamOutboundMail.java` | `INSTALLMENT_DUE_SOON`, `INSTALLMENT_OVERDUE` |
| `common/mail/EmailTemplates.java` | `installmentReminder`, bilingual, one template for the student and the Master Admin |
| `exams/InstallmentPlanService.java` | Recording: student notice and staff notice. Voiding: student notice. Schedule change: student notice |
| `exams/LiveExamPaymentPlanService.java` | First payment (inside the grant): student notice, receipt email, staff notice |
| `resources/application.properties` | `app.installments.due-soon-days` (default 3) |
| `src/test/.../exams/InstallmentNoticesTest.java` (new) | Keys, tabs, payload masking and nulls, staff group parsing. Written, not run |
| `src/test/.../staffnotifications/StaffNotificationWriterTest.java` | The kind list now holds the three new kinds |

No new migration: V80 already allows every kind and has both columns with `ON DELETE SET NULL`.

Deviations in Phase 3:

| Plan said | Built | Why |
|---|---|---|
| Section 7 and brief: add the installment kinds to `NoticeGroup.EXAMS` `kinds()` | Not added. `kinds()` for EXAMS stays the other tabs' kinds, and the installment kinds are mapped to EXAMS in `of()` | The query uses "not in" for EXAMS, so listing them there would hide every installment notice from Exams |
| Brief item 7: an email row for a voided payment and a schedule change | In-app notice only | Section 7's table and C6 say in-app. V80 still allows the two mail kinds, and nothing inserts them |
| Section 6.2 and brief item 5: skip students who are not active | The student side is skipped (the writers check ACTIVE in SQL). The Master Admin is still told | Section 6.2's last sentence and E10 |
| Section 7: staff notification in the payment's transaction | Recorded after commit with `AfterCommit`, in its own transaction, and a failure is logged. Reminders keep the job's per-plan transaction, with checked payloads | `notify` joins the caller's transaction, and a throw would roll back the payment (section 9.1). Staff pay uses the same join, and only its payloads are checked |
| Phase 2 receipt email for the first payment | Now queued in `open()` | Phase 2 missed it. Section 7 says "payment recorded (including the first)" |
| `byGroup` keys (plan silent) | `INSTALLMENT` and `OTHER` | `OTHER` is the complement, so the counts add up to `count` |
| Installment block (plan silent) | Payment notices show that payment's snapshot. Reminders show what is uncovered on the row and the plan's position now. A schedule change shows the position with no amount or date | Receipts stay true (section 4). A schedule change has no single row |
| Reminder job (plan silent on locking) | Takes `FOR UPDATE` on the plan row, as the payment writes do | A payment recorded during a run is then seen, and no reminder follows it |
| Schedule-change notice link (plan silent) | `direct_payment_id` is the plan's latest payment, voided or not | Payments are never deleted, so the link always holds |
| `NoticeView.installment.dueOn` (plan silent) | ISO date string | Matches the other dates in this API |
| Existing test `StaffNotificationWriterTest` (not in the plan's list of tests to update) | Kind list updated | It pins the full list, so it would fail on the new kinds |

Not verified by reading, to check when the tests are run: compilation; the inbox `FROM` and `SELECT` against V79 and V80; `dueRow`, `installmentId` and the job's `for update` query; that `AfterCommit` runs the staff notice in its own transaction on the grant and payment paths; that the job is not started by `PostgresIntegrationTest`; the reminder timing on a clock moved across the due date (E53 to E55); the rendered reminder emails and the Bangla copy, which Shahriar must read; that a null Bangla title and 9+ digit names both pass `checkPayload` (E61 and E62); and the first-payment receipt email now queued (step 3 of 10.3).

### Phase 4: staff web, enrolment with payments (built, not compiled, type-checked or tested)

| File | What it does |
|---|---|
| `api/.../exams/LiveExamGrantAdminService.java` | `quote` takes an optional `studentId`. `QuoteView` gains `unfinishedPlanId` and `accessHeld`, both null without a student. The student must exist (404 otherwise) |
| `api/.../exams/LiveExamGrantAdminController.java` | `GET /api/v1/live-exam-grants/quote` takes `studentId` |
| `api/.../exams/LiveExamPaymentPlanService.java` | `unfinishedPlanId`: the student's latest plan on the target with an unpaid balance, revoked or not (voided payments ignored) |
| `web/src/components/staff/enrol/payment-model.ts` (new) | Pure: parses money in whole paisa, the schedule rules, month-end dates, the cash reference, the grant body. No React |
| `web/src/components/staff/enrol/payment-fields.tsx` (new) | The payment fields and the read-only schedule. Shows the rules' errors once the admin tries to save |
| `web/src/components/staff/enrol/payment-enrol-form.tsx` (new) | Under the chosen student: the quote, the two warnings, the fields, the confirmation dialog, the submit, and the success notice |
| `web/src/components/staff/enrol/one-student-enrol.tsx` | Opt-in `payment` slot (Master Admin only) and `initialStudent`. Without the slot it behaves as before, for the roster tab |
| `web/src/components/staff/enrol/direct-enrolment.tsx` | One student at a time. The bulk list, outcomes and copy are gone. `?student=`, `?exam=`, `?package=` work |
| `web/src/components/staff/enrol/exam-enrol.tsx` | Uses the payment slot, and a "Manage installments" link for the Master Admin |
| `web/src/app/(staff)/staff/enrol/page.tsx` | Passes `initialStudent` (one student) |
| `web/src/app/(staff)/staff/enrolments/page.tsx` | "Manage installments" for the exam or package |
| `web/src/components/staff/exams/exam-row.tsx`, `catalog/module-list.tsx` | Menu item "Installments" for the Master Admin, to `/staff/installments?examId=` or `?moduleId=` |
| `web/src/components/staff/access/student-access-section.tsx` | "Manage installments" beside the grant button, Master Admin only |
| `web/src/lib/api/exam/staff-admin.ts` | Types `GrantQuote` and `GrantResponse`. `useGrantAccess` untouched (no caller) |
| `web/src/i18n/messages/en/staffAdmin.ts` and `bn` | `enrol` section replaced: `enrol.payment.*`, `manageInstallments`, `chooseTargetFirst`. Bulk-only keys removed |
| `web/src/i18n/messages/` `staffExams.ts` and `staffCatalog.ts`, en and bn | `row.installments`, `rowMenu.installments` |
| `web/tests/staff-admin/direct-enrolment.test.tsx` | Rewritten for one student, the three types, the warnings, the duplicate error, the success state and a free exam. Written, not run |
| `web/tests/staff-admin/payment-model.test.ts` (new) | Parsing, paisa sums, month ends, Dhaka today, cash reference, schedule rules, body shapes. Written, not run |

`roster-tab.tsx`, `enrolled-list.tsx` and `copy.test.ts` are unchanged.

Deviations in Phase 4:

| Plan said | Built | Why |
|---|---|---|
| Section 8: enrolled-list rows get a Paid or Due pill linked to the plan | Not built | `LiveExamEnrolmentListService` returns no plan id, paid or due. The pill waits for that API change |
| Section 8: a "Manage installments" link on each access row that has a plan | Only the link beside the grant button | `StudentLiveExamAccessService` rows carry no plan id. Waits for the same kind of API change |
| Section 8: `/staff/enrol` search selects one student | The search and choice are `OneStudentEnrol`, shared with the exam panel | One search and one payment slot for both screens |
| Section 8: fields include paid today for every type | Full payment shows the whole total, read only. Only Installments has the input | A full payment is one row paid whole (section 4) |
| Section 8: per-installment amount | Hidden when there are two installments. The second takes the rest, shown in the schedule | With one middle row the amount has no meaning |
| Section 8: fields prefilled | Total, paid, date, next date, amount per installment and cash reference are suggestions until typed. They then follow the form and are never overwritten | Rule 8: a typed value is left alone |
| Section 8: success links to the receipt and the new plan | Both go to `/staff/installments/{planId}`, where the receipt lives | The grant response has no payment id for the receipt route. Phase 5 shows the receipt on the plan |
| C4 and E33: cash twice on one day gives different references | A new plan's first payment is number 1, so two Cash enrolments of one target on one day give the same reference on two different plans. The API allows that. A second Cash payment on the same plan (Phase 5) gets number 2 | The brief sets number 1 for a new plan. E33 applies to payments on one plan |
| Section 8: the form asks for confirmation | The confirmation is a dialog with the summary, and a failed save keeps the form and its message | C7 |
| Plan silent: default method | Cash | Its reference is then filled in at once |
| Plan silent: payment fields for System Admin | Not shown. Only the Master Admin gets the slot (E13, E67). A System Admin on the exam panel keeps the reason form | The API refuses a System Admin's payment with 403 |
| Plan silent: `QuoteView` `accessHeld` for a free exam | True for any student, since a free exam is open to all. The form hides the warning for a free exam | Literal reading of "access by any route". The warning would only mislead there |
| Section 8 copy: remove bulk keys | Removed `listHeading`, `count`, `listRequired`, `failedNote`, `finished`, `findHeading`, `packageRequired`, `examRequired`, `submit`, `reasonHint`. Kept `add`, `inList`, `remove`, `listEmpty`, `searchPlaceholder` and the rest, which the announcement picker uses | Nothing else uses the removed keys. Grepped first |
| Brief: `useGrantAccess` removed if unused | Left in place, untouched | It has no caller, and it is not the bulk path. Removing it is a separate tidy-up |
| Plan silent: a second file for the form | `payment-enrol-form.tsx` holds the quote, warnings, confirmation and submit | Keeps `payment-fields.tsx` presentational |

Not verified by reading, to check when the tests are run: TypeScript and Java compilation; the quote's null UUID parameters in `unfinishedPlanId` against Postgres, and its SQL against V79; the Radix confirmation and the date inputs in a real browser; the phone width and dark mode; the Bangla copy, which Shahriar must read; the links on the split hosts (absolute URLs in the row menus). **The links to `/staff/installments` 404 until Phase 5**, so this phase must ship with Phase 5 (section 9.1).

### Phase 5: staff web, the Installments pages and the links (built, not compiled, type-checked or tested)

| File | What it does |
|---|---|
| `api/.../exams/LiveExamEnrolmentListService.java`, `LiveExamEnrolmentListController.java` | API gap closure. `forModule` and `forExam` take `withMoney`. `EnrolledStudent` gains `paymentPlanId`, `paid`, `due`, `planStatus`, set only for a caller holding `LIVE_EXAM_INSTALLMENT` and only when the grant has a plan. The controller passes the permission |
| `api/.../exams/InstallmentPlanReader.java` | `byGrantIds(Collection<UUID>)`, the plans on a set of grants, through the existing plan query |
| `api/.../exams/StudentLiveExamAccessService.java`, `StudentLiveExamAccessController.java` | API gap closure. `AccessRow` gains `paymentPlanId`, set only with `withPlans`, read in one query on `live_exam_payment_plans`. The two-argument `list` stays for `AccessExpiryIT` |
| `web/src/lib/api/exam/staff-installments.ts` (new) | Types, the list, summary, detail, record, schedule, void, restore and revoke hooks, `invalidateInstallmentQueries` (plans, `staffEnrol`, access rows, the notification keys), and the receipt download through `apiFile` |
| `web/src/components/staff/installments/installment-model.ts` (new) | Pure: filters, status tone and key, summary figures, schedule lines and the frozen count, the suggested amount, a free cash reference, reference masking, the payment check and the schedule check |
| `web/src/components/staff/installments/installments-list.tsx`, `installment-plan-detail.tsx`, `record-payment-dialog.tsx`, `schedule-change-dialog.tsx` (new) | The list, the plan detail with its actions, and the two dialogs |
| `web/src/app/(staff)/staff/installments/page.tsx`, `[planId]/page.tsx` (new) | Routes, `RequireStaff` Master Admin |
| `web/src/components/shell/staff-nav.ts` | `installments` item, money group, Master Admin only, `CalendarClock` |
| `web/src/components/staff/notifications/notifications-page.tsx`, `notification-text.ts` | Installments tab with its badge (Master Admin only); the three kinds |
| `web/src/lib/api/exam/staff-notifications.ts` | The three kinds, the `group` parameter, `byGroup` on the count (optional, read with `?.`) |
| `web/src/components/staff/enrol/enrolled-list.tsx` | Paid or Due pill, linked to the plan, Master Admin only |
| `web/src/components/staff/access/student-access-section.tsx` | Per-row "Installments" link, in the Master Admin column only |
| `web/src/components/staff/enrol/payment-model.ts` | `cashReference` takes an optional payment number, default 1, so Phase 4 callers are unchanged |
| `web/src/lib/api/exam/staff-admin.ts`, `web/src/components/staff/catalog/errors.ts` | Four installment codes mapped; `PACKAGE_HAS_INSTALLMENTS` known to the catalog |
| `web/src/i18n/messages/` `common.ts`, `staffAdmin.ts`, `staffCatalog.ts`, `staffNotifications.ts`, en and bn | Copy: `staffNav.installments`, `staffAdmin.installments.*`, `staffAdmin.enrolments.plan`, `staffAdmin.access.planLink`, the four error codes, `staffCatalog.errors.PACKAGE_HAS_INSTALLMENTS`, the tab, the empty state and the three kinds. Bangla in natural terms: কিস্তি, রসিদ, বকেয়া, লেনদেনের রেফারেন্স |
| `web/tests/staff-admin/installments-model.test.ts` (new) | Filters, status mapping, summary, schedule state, cash reference, masking, the payment and schedule checks. Written, not run |

Tests not changed: `web/tests/s1-b/staff-nav.test.ts` and `web/tests/staff-ui-clean/nav-by-role.test.tsx` pin no list that gains the item, and the `staff-notifications` tests mock the count without `byGroup`, so they should pass unchanged. `copy.test.ts` still holds because the two language files have the same keys.

Deviations in Phase 5:

| Plan said | Built | Why |
|---|---|---|
| Section 6: the services read the caller's permission | The controllers pass a boolean, and the services take it as a parameter | Keeps the services free of the security context. The two-argument access `list` stays for the existing IT |
| Section 8: a "Manage installments" link to `/staff/installments?studentId=` on the access section | Already there from Phase 4, beside the grant button. This phase adds only the per-row link | Avoids a second link to the same page |
| Section 8: payments show the reference | Shown as the last four characters. The full text is in the receipt | Section 9.1: a full reference is never shown |
| Section 8: "Void latest payment" as an action | A Void button on the latest live payment's row, and no other | The API refuses any other payment (E37), so only that row offers it |
| Section 8: the schedule edited in place | A dialog. Paid rows are read only; unpaid rows can be changed, added and removed, and the total is checked before saving | A dialog keeps the check and the confirmation together |
| Phase 4 deviation: a second Cash payment on a plan gets number 2 | The generated reference uses the first payment number not already used on the plan | Same rule as E33, and a reference that is taken is never offered |
| Section 7: the Installments tab (plan silent on Unread) | The tab lists every installment notice, read or not. The badge counts the unread ones | Matches how the Exams tab of the student's notices works |
| Plan silent: sentences for a payment that clears the plan | `INSTALLMENT_PAYMENT_RECORDED_FULL`, chosen on `due` = 0 | "Still to pay: 0" reads wrong |
| Plan silent: a new namespace for the Installments copy | Put under `staffAdmin.installments`, which the copy tests already cover | No change to the message registry |
| Plan silent: the list shows the phone | Not shown. The search still matches it, as the API does | Section 9.1: no phone number on screen where it is not needed |
| Plan silent: the schedule check | Done in the browser with the API's rules, and the API still decides on save | The screen can say which row is wrong before the request |
| Phase 2 deviation: package delete message is English only | The message is in en and bn under `staffCatalog.errors.PACKAGE_HAS_INSTALLMENTS`, shown through the existing delete dialog | Closes the Phase 2 deviation |

Not verified by reading, to check when the tests are run: Java and TypeScript compilation; the `IN (:grants)` queries against V79 (the plan lookup by grant and the access rows); Spring wiring of `LiveExamEnrolmentListService` with its new `InstallmentPlanReader` argument; the Radix tabs and dialogs on a phone width and in dark mode; the receipt download's file name in a browser; that `ReasonDialog` and `ConfirmDialog` show the mapped message when a call fails; the schedule check against a partly paid plan; the Bangla copy, which Shahriar must read; and the package delete message through `module-details.tsx`.

### Phase 6: student web (built, not compiled, type-checked or tested)

| File | What it does |
|---|---|
| `web/src/lib/api/exam/student-installments.ts` (new) | Types with optional fields (no reference typed), `useStudentInstallmentPlan`, the receipt path, and `downloadStudentInstallmentReceipt` through `apiFile`, named by the receipt number |
| `web/src/lib/api/exam/account.ts` | `OfflinePayment` type. `accessRemoved` and `payment` on the offline rows. `endsAt` optional |
| `web/src/lib/api/exam/notices.ts` | `NoticeInstallment`, and `installment` on `Notice` |
| `web/src/lib/api/missing.ts` | `optionalDate` (YYYY-MM-DD only) and `optionalAmount` |
| `web/src/components/student/catalog/offline-payment.ts` (new) | Pure: plan status pill, schedule state pill, method label, the card's link rule, the top pills, card keys, voided and settled flags |
| `web/src/components/student/catalog/offline-payment-panel.tsx` (new) | The View payments dialog: plan figures, schedule with states, payments with Download receipt and a Voided pill |
| `web/src/components/student/catalog/offline-purchases.tsx` | Cards with a plan show Paid, Due, the next date, the status pill and View payments. A removed card's title is plain text |
| `web/src/components/student/catalog/owned-card.tsx` | `TitleText` takes no `href` as plain text. `OwnedCard` takes an optional `footer` |
| `web/src/components/student/catalog/my-exams-rows.ts` | `MY_EXAMS_TABS` and `parseMyExamsTab` |
| `web/src/components/student/catalog/my-exams-page.tsx` | The tab reads `?tab=` and writes it back. Local state follows the URL |
| `web/src/app/(site)/notices/_components/installment-notice.ts` (new) | Pure: the five kinds, `INSTALLMENT_HREF`, the tone, the title, and the figures a row shows |
| `web/src/app/(site)/notices/_components/notice-kind.ts` | Five kinds in `NOTICE_KINDS`. `noticeHref` returns the offline tab for them before the exam check |
| `web/src/app/(site)/notices/page.tsx` | `InstallmentRow` beside `AnnouncementRow`. The icon map and the dispatch in `NoticeRow` |
| `web/src/i18n/messages/` en and bn: `notices.ts`, `account.ts` | `notices.kind.INSTALLMENT_*`, `notices.installment.*`, `account.payments.offline.status`, `paidLine`, `dueLine`, `nextLine`, `settled`, `viewPayments` and `panel.*` |
| `web/tests/student-nav/offline-payment.test.ts` (new) | Pills, the link rule, card keys, flags, and the optional-field helpers |
| `web/tests/notices/installment-notice.test.ts` (new) | Kinds, the href, tones, titles and the figures on a row |
| `web/tests/student-nav/offline-tab.test.tsx` (new) | `?tab=offline`, the tab written to the URL, a removed card with no link, the money, and the payment panel |
| `web/tests/stu-lms/notice-kind-rows.test.tsx` | A row case for each of the five kinds, which the kind-coverage check needs |

Not changed: `web/src/app/(site)/account/payments/...` (the online receipt and the payment history), `downloadReceiptPdf`, `notice-tabs.ts` (the group comes from the API), `web/tests/student-nav/my-exams.test.tsx` (it still passes, since the tab follows local state as well as the URL).

Deviations in Phase 6:

| Plan said | Built | Why |
|---|---|---|
| Section 8: the tab reads `?tab=` from the URL | The URL is the source. The local tab is set at once on a click, and follows the URL when it changes | The router update is asynchronous, so a click must not wait for it. The existing tab test mocks the router and does not re-render |
| Section 8: pills for Overdue, Due soon and Completed | Also Active and Access ended. A removed card with a plan shows Access ended in place of the access end date | Shahriar's list for this phase. A removed plan has no end date that means anything to the student |
| Plan silent: tone of the void and schedule-change notices | Warning tone | Same as a changed exam time (`EXAM_TIME_CHANGED`) |
| Plan silent: file name of a receipt | The receipt number. Without one, `receipt`. The existing download falls back to the order ID | Section 9.1: no reference, phone or ID in a file name |
| Plan silent: reference on a payment | Not typed in the student API client and not shown | Section 9.1 |
| Plan silent: receipt number on a payment notice | Shown as a chip | It is a receipt number, not a reference |
| Plan silent: card key | Exam or package ID with the plan ID, or the grant date when there is no plan | A revoked card and a new one for the same exam (E11) must not share a key |
| Plan silent: next installment on a card | The date only | The offline payment has no next amount. Section 8 asks for the date |
| Plan silent: a date from the API | `formatDate` on the `YYYY-MM-DD` string | Read as UTC midnight, which is 06:00 on the same day in Dhaka, so the day is right |
| Plan silent: shared files | `missing.ts` gains two helpers. `owned-card.tsx` gains an optional `href` and a `footer` | Additive. The other importers are unchanged |
| Plan silent: existing test updates | `notice-kind-rows.test.tsx` gets five rows | `NOTICE_KINDS` grew, and the test requires a row for each kind. `my-exams.test.tsx` did not need a change |

Not verified by reading, to check when the tests are run: TypeScript compilation (for example `isMissing` narrowing on `row.payment` and the optional-field helpers); the Radix dialog in jsdom; that the router replace updates `?tab=` in the real app and Back returns to the same tab; the file name the server sends in Content-Disposition; the phone width and dark mode of the cards, the panel and the notice row; the Bangla copy, which Shahriar must read (it uses কিস্তি, পেমেন্ট, রসিদ, বাকি and বাতিল, as in the Phase 5 staff copy); that the server's JSON keeps the field names used here (`planId`, `nextDueOn`, `accessRemoved`, `installmentNo`).

### Where the build differs from this plan

| Plan said | Built | Why |
|---|---|---|
| V79 includes the `student_notices` columns and the kind lists | V79 has only the three tables. The notice and mail kind lists move to V80, written in Phase 3 | Phase 1 does not write any notice. V80 can copy the current lists when the kinds are first used |
| The quote route takes `studentId` and returns warnings | Quote takes only the target. The warnings come in Phase 4 | The warnings read the plan and access tables, which the Phase 2 list endpoints provide |
| The grant response returns the plan and receipt | Response is unchanged for now | The Phase 2 receipt routes need the plan id, so it is added with them |

### Review fixes (8 November 2026, after the build and the test runs)

| Plan said | Built now | Why |
|---|---|---|
| Section 8 and Phase 5: the payment table shows the reference masked, the last four characters | The Master Admin sees the full reference on the plan page, and it is still never in a URL, a log or a notification | Without the full reference the Master Admin cannot check a bKash or bank payment on screen. The masking helper and its test are removed |
| Section 5: a schedule change deletes the unpaid rows and inserts them again | A changed row is updated in place, a new row is added, and rows past the new end are removed. An unchanged row keeps its id | The old way gave every unpaid row a new id, which detached its notices and its reminders, and could send a repeat overdue email in the same week |
| Section 10: integration tests for the payment routes | `InstallmentPlanIT` added: enrolment with a payment, overpayment and duplicate reference, the void rule, the schedule change keeping ids, revoke and restore, the student's own plan, the package delete guard and the staff role check | The unit tests cover the rules; these cover the routes and the database |

## 1. What is being built

Today a direct enrolment gives access and records no money. The client also takes payment in person, sometimes in installments, and wants the site to keep the record.

When the Master Admin enrols a student directly, they will record:

- what the student agreed to pay
- what the student paid today, how, and the transaction reference
- for an installment plan: how many installments, how much each, and when the next one falls due

Each payment produces its own receipt showing the total, the amount paid so far, the amount due, and the next due date. Both the student and the Master Admin are told when an installment is coming up, when it is overdue, and when a payment is recorded.

| Role | Sees |
|---|---|
| Master Admin | Payment fields on the direct enrolment form. A new **Installments** page: every plan, what is due and overdue, record a payment, download a receipt, revoke access. Every screen that shows a direct enrolment links to it. A new **Installments** tab in the notification centre. Reminder emails |
| System Admin, Instructor | Nothing new |
| Student | Paid, due and next date on the **Offline purchases** tab of My exams, with a receipt PDF for each payment. Notices under the **Exams** tab of Notices. Receipt and reminder emails if their email is verified |

### 1.1 Decisions (Shahriar, 8 November 2026)

| ID | Decision |
|---|---|
| D1 | The total defaults to the current list price, with the discount if one is active. The Master Admin can raise or lower it for each enrolment, for example because installments take longer to pay |
| D2 | Payments live in new tables linked to the grant. Direct enrolments do not go back into `live_exam_orders` (V64 removed them on purpose) |
| D3 | The Master Admin sets the number of installments, the next installment date (default one month ahead) and the amount per installment |
| D4 | Student and Master Admin both get installment notifications and overdue notifications. The student's sit under the Exams tab. The Master Admin's sit under a new tab called Installment |
| D5 | The first payment gives full access. An overdue installment changes nothing automatically; the Master Admin may revoke access by hand |
| D6 | The Master Admin chooses the amount and method in the direct enrolment section. A transaction reference is required. Only the Master Admin records payments |
| D7 | Each payment has its own receipt number. The receipt lists the total, paid so far, due, and next date. The student sees it in the My Orders area and through a notification |
| D8 | Existing direct enrolments are left as they are. Nothing is backfilled |
| D9 | No link to Staff pay, which is a separate area. No refund handling: refunds stay offline |
| D10 | The form offers **Full payment**, **Installments** and **No charge**. No charge gives access with no money and a required reason, for scholarships or anyone the Master Admin chooses to admit free. Free exams are already open to everyone and need no enrolment |
| D11 | The number of installments **includes today's payment**. "3 installments" means today plus two more, and the receipt reads "Installment 1 of 3" |
| D12 | No bulk enrolment. Direct enrolment is one student at a time |
| D13 | A transaction reference is still required for every method, but the client has no cash receipt book. For cash, the site fills in a generated number related to the exam or package |
| D14 | Reminders go in-app **and by email**. Due-soon 3 days before; overdue the day after the due date, then weekly until paid (the same timing as staff salaries) |
| D15 | Two places for the Master Admin: an **Installments tab in the notification centre** and a dedicated **Installments page** in the sidebar |
| D16 | The Master Admin can **void the latest payment** with a reason and **change the unpaid schedule**. Neither is a refund |
| D17 | ~~The package page's **Roster** tab is out of scope and stays exactly as it is~~ **Replaced on 8 November by D21 (section 14)** |
| D18 | **Every screen that shows a direct enrolment links to the Installments page**, so the enrolment is managed from there (section 8) |
| D19 | The bulk list is removed for every payment type, No charge included |
| D20 | Reminder emails go to both the student and the Master Admin |

### 1.2 Choices made while planning (say if any is wrong)

| ID | Choice | Why |
|---|---|---|
| C1 | Methods are Cash, bKash, Nagad, Rocket and Bank | The Staff pay list plus cash |
| C4 | The cash reference looks like `CASH-1A2B3C4D-20261108-1`: the first 8 characters of the exam or package ID (exams have no code of their own), the payment date, and the payment number on that plan. The form fills it in and the Master Admin can edit it | D13. The payment number keeps it unique on the plan, so the double-submit check still works |
| C5 | **Restore access** on a plan whose access was revoked | The main D5 path is: overdue, revoke, student pays. Without this, giving access back would mean a new plan and charging the student twice |
| C6 | The student is told in-app when a payment is voided or the schedule changes | Otherwise they keep a receipt that is no longer valid, or expect a date that has moved |
| C7 | The enrolment form asks for confirmation, with a summary, before saving | Saving gives access and emails the student straight away, so a mistyped amount reaches them at once |
| C8 | One reminder per plan per run, for the oldest unpaid installment | With two installments overdue, the student and Master Admin would otherwise get two messages a week for one debt |
| C9 | A package with a live installment plan cannot be deleted. It can still be archived | Exams already have this protection (`LiveExamRemoval`). Packages do not, and deleting one would end the access a student is paying for |

## 2. Worked example

A package lists at 6,000 BDT with no discount. The student agrees to 6,300 BDT over three installments and pays 2,100 BDT by bKash on 8 November.

| # | Amount | Due | State |
|---|---|---|---|
| 1 | 2,100 | 8 Nov 2026 | Paid, receipt `RCPT-2026-000140` |
| 2 | 2,100 | 8 Dec 2026 | Next |
| 3 | 2,100 | 8 Jan 2027 | Upcoming |

The student has full access from 8 November. Receipt 140 shows Total 6,300, Paid so far 2,100, Due 4,200, Next 2,100 on 8 Dec 2026. Notices go out from 5 December. If nothing is paid, overdue notices go out on 9 December and every week after. The Master Admin decides whether to revoke.

## 3. What exists today, and what it means here

| Finding | Where | Effect on this plan |
|---|---|---|
| A direct enrolment is one `live_exam_grants` row with `source = MANUAL` and no money | `LiveExamGrantAdminService.grant` | The plan links to this row by `grant_id` |
| Two screens post to that endpoint: the bulk page (`direct-enrolment.tsx`) and the one-student panel on the exam page (`exam-enrol.tsx`) | `web/src/components/staff/enrol/` | Both get the same payment fields. The bulk page becomes one student (D12). `useGrantAccess` in `staff-admin.ts` has no caller |
| The package Roster tab also "enrols", but writes `live_exam_roster`, with no grant | `roster-tab.tsx`, `LiveExamRosterService` | No plan can attach. Replaced by D21: the package page now enrols with a grant (section 14) |
| `OneStudentEnrol` is shared by the exam panel and the Roster tab | `one-student-enrol.tsx`, `roster-tab.tsx:60` | Payment fields are opt-in by prop. Since D21 the package page opts in too (section 14) |
| Exams and packages have no short code | `LiveExam`, `LiveExamModule` | The cash reference uses the start of the ID (C4) |
| Receipts read only from paid orders, and the student's PDF route rejects anything else | `LiveExamOrderHistoryService.receipt` | A new PDF route and a new input for the PDF |
| `LiveExamReceipts.next` draws numbers from one sequence | `live_exam_receipt_seq` (V52) | Reused, so numbers stay unique across online and offline payments |
| PDF text is Latin only (Helvetica, `ascii()`) | `LiveExamReceiptPdf` | The reference must be validated as ASCII, or it would vanish from the receipt |
| New notice kinds land under Exams with no mapping | `NoticeGroup.of` | D4 is met for free on the API side |
| A student notice stores no text, and the inbox reads values at view time | `StudentInboxService` | Notices need a link to the installment or payment |
| The inbox hides any notice whose exam or package is soft-deleted | `StudentInboxService.FROM` | Installment notices name neither, and take their title from the plan |
| Exams with a grant cannot be deleted; students with a grant cannot be deleted. Packages can be deleted at any time | `LiveExamRemoval`, `AccountService.delete`, `LiveExamModuleService.delete` | Only the package needs a new guard (C9). A student can still be suspended |
| The API leaves null fields out of its JSON, and ignores fields it does not know | `application.properties` (`non_null`) | The web must treat these fields as optional, and the API must ship first (section 11) |
| A race on the one-active-grant index is not caught, so it surfaces as a server error | `LiveExamGrantAdminService.grant` | Map it to `GRANT_ALREADY_ACTIVE` |
| The mail table is scanned every 5 minutes and retried, and a row whose text cannot be built is marked skipped | `ExamMailService.scan`, `ExamMailDelivery` | Reminder emails get retries for free, and the send-time check is simple |
| Master Admin emails link with `ctx.forRole(MASTER_ADMIN, …)`, which goes to the admin host | `EmailTemplates.purchaseNotice` | Reminder emails to the Master Admin must do the same |
| `student_notices.kind` has a check constraint | latest is V60 | The migration must re-list every kind |
| Staff notifications carry a JSON payload. Any key containing `account`, `bank`, `card` and so on, or any text with 9+ digits, **throws** | `StaffNotificationWriter.checkPayload` | The reference and phone must never go in a payload. An exception there would roll back the payment. Titles and names pass through `fitText` |
| The Offline purchases list hides revoked grants | `MANUAL_EXAMS`, `MANUAL_MODULES` in `StudentOwnAccessService` | **A student revoked for non-payment would lose sight of receipts for money already paid.** The queries must change (section 8) |
| The Offline purchases tab is `useState`, not in the URL | `my-exams-page.tsx:65` | A notice cannot link to it until the tab reads `?tab=offline` |
| `NoticeRow` assumes an exam: its title and "Open exam" action come from `notice.exam` | `notices/page.tsx` | Installment notices need their own row, as announcements have |
| The staff notification list has only an unread filter and one count | `StaffNotificationService` | Needs a group filter and a per-group count |
| The salary job and `SalaryReminders` already do this timing, safely across missed runs | `staffledger/` | Same shape. `SalaryReminders` is public and reusable as is |

## 4. Rules

**Amounts**
- Money is `NUMERIC(12,2)`, in BDT, more than zero, with at most two decimals. More decimals are refused, not rounded.
- The API uses `BigDecimal` and compares with `compareTo`, never `equals` (2100.0 and 2100.00 are the same amount). The form adds up in whole paisa, never in JavaScript floats.
- The agreed total is fixed once the plan exists. To change it: void the payments, revoke the access, enrol again.
- The API computes the standard price itself with `ExamPricing` and stores it. It never trusts a price sent by the browser.
- `ExamPricing.MINIMUM_PAYABLE` (10 BDT) is an SSLCommerz limit and does not apply here.

**Schedule**
- Rows are numbered from 1. Row 1 is today's payment: its amount must equal the amount paid and its date must equal the payment date. Full payment is a plan with one row, and Installments needs at least two.
- Row amounts must add up to the agreed total. Dates must strictly increase. Between 1 and 24 rows.
- Rows after the first must fall today or later (Dhaka date), including on a backdated payment, so a plan never starts out overdue.
- The form builds the rows from its three fields. Row 1 is the amount paid today. Rows 2 to n−1 use the per-installment amount. The last row takes the remainder and must be more than zero.
- Dates after the next date are computed from that date with `plusMonths(i)`, not chained, so 31 January does not drift to the 28th for the rest of the plan.
- The API receives explicit rows and validates them. The rule that builds them lives in one place, the form, and a later schedule change uses the same validator.

**Payments**
- A payment is applied to rows in order, oldest first. A row is covered once the running total of payments reaches the running total of rows. An amount smaller than the row leaves the rest due on it. A larger one carries forward, so one payment can clear two rows.
- Coverage is computed, not stored, by one pure class (`InstallmentCalculator`), as `StaffPayCalculator.uncovered` does for pay.
- A payment above the amount due is refused. The payment date cannot be in the future (Dhaka date).
- A reference is always required. For Cash the form fills in the generated number (C4); the other methods are typed.
- The same reference twice on one plan is refused, ignoring case and outer spaces, which also stops a double submit. The same reference on two different plans is allowed, since one bKash payment may cover two enrolments.
- Recording takes a row lock on the plan, so two requests cannot overpay. Paid so far is read under that lock.
- A payment can be recorded after access is revoked, because a student may settle first. Recording does not restore access.
- The receipt number is drawn last in the transaction. A sequence value is not returned on rollback, so drawing it after validation avoids gaps.

**Receipts stay true.** Each payment row stores what its receipt says at that moment: paid so far, due, next date and amount, installment number and count. The installment number is the first row the payment touches. A receipt reprinted a year later is unchanged.

**Void.** Only the latest live payment can be voided, so later receipts never become wrong. A reason is required. The row stays, the number is never reused, and its PDF is stamped VOID. Voiding the only payment leaves access in place (the Master Admin decides, as in D5). No reminders go out for a plan with no live payment, since it is a correction in progress, but the Installments page still shows it as overdue.

**Change schedule.** Fully covered rows are frozen. The remaining rows are replaced and must add up to what is left, under the same rules as a new plan. A partly paid row is replaced too, and its part payment carries into the new first row. A new date restarts that row's reminders, because the date is part of the dedupe key. Old receipts are not changed.

**Access.** Follows D5. Revoking uses the existing `DELETE /api/v1/live-exam-grants/{id}` and reason dialog. Revoking stops reminders, keeps the plan and receipts, and leaves the balance on record. A grant retired after its access ran out counts as revoked. Access that has simply run out, with no new grant, does not stop reminders, because the money is still owed.

**Restore access.** Allowed on a plan whose grant is revoked. It writes a new grant with the old grant's `created_at`, so the original access period still applies, and points the plan at it. The revoked grant stays for the record. Refused if the student has active access again, if the exam or package is no longer published, or if the access period has already ended.

**Enrolling again.** A new enrolment after a revoke makes a new grant and a new plan. The form warns when that student has an unfinished plan on the same exam or package, and links to it, so restoring is the obvious choice. It also warns when the student already has access another way (a package, the roster or an online purchase), since the API does not block charging for those.

**Package deletion.** Refused while the package has a plan with an unrevoked grant (C9). Archiving is still allowed and keeps the students it has.

**Status** is computed for display: `ACTIVE`, `DUE_SOON`, `OVERDUE`, `COMPLETED`, `ACCESS_REVOKED`.

## 5. Schema: `V79__direct_enrolment_payments.sql`

Confirm the next free number when building. New tables plus two widened check constraints; no existing row changes.

```sql
CREATE TABLE live_exam_payment_plans (
    id               UUID PRIMARY KEY,
    grant_id         UUID NOT NULL UNIQUE REFERENCES live_exam_grants (id),
    student_id       UUID NOT NULL REFERENCES users (id),
    list_price       NUMERIC(12,2) NULL,       -- null when the target has no price
    discount_percent INTEGER NULL,
    standard_price   NUMERIC(12,2) NULL,       -- the price after discount, at enrolment
    agreed_total     NUMERIC(12,2) NOT NULL CHECK (agreed_total > 0),
    created_by       UUID NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX live_exam_payment_plans_student_idx ON live_exam_payment_plans (student_id, created_at DESC);

CREATE TABLE live_exam_installments (
    id        UUID PRIMARY KEY,
    plan_id   UUID NOT NULL REFERENCES live_exam_payment_plans (id),
    seq       SMALLINT NOT NULL CHECK (seq BETWEEN 1 AND 24),
    amount    NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    due_on    DATE NOT NULL,
    CONSTRAINT live_exam_installments_plan_seq_key UNIQUE (plan_id, seq)
);

CREATE TABLE live_exam_direct_payments (
    id                UUID PRIMARY KEY,
    plan_id           UUID NOT NULL REFERENCES live_exam_payment_plans (id),
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    method            VARCHAR(16) NOT NULL CHECK (method IN ('CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK')),
    reference         VARCHAR(100) NOT NULL CHECK (length(btrim(reference)) > 0),
    paid_on           DATE NOT NULL,
    note              VARCHAR(500) NULL,
    receipt_no        VARCHAR(24) NOT NULL UNIQUE,
    -- what the receipt says, fixed when the payment is recorded
    paid_to_date      NUMERIC(12,2) NOT NULL,
    due_after         NUMERIC(12,2) NOT NULL,
    next_due_on       DATE NULL,
    next_due_amount   NUMERIC(12,2) NULL,
    installment_no    SMALLINT NOT NULL,
    installment_count SMALLINT NOT NULL,
    created_by        UUID NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL,
    voided_at         TIMESTAMPTZ NULL,
    voided_by         UUID NULL REFERENCES users (id),
    void_reason       VARCHAR(500) NULL,
    CONSTRAINT live_exam_direct_payments_void_check CHECK (
        (voided_at IS NULL) = (voided_by IS NULL) AND (voided_at IS NULL) = (void_reason IS NULL))
);
CREATE INDEX live_exam_direct_payments_plan_idx ON live_exam_direct_payments (plan_id, created_at);
CREATE UNIQUE INDEX live_exam_direct_payments_reference_unique
    ON live_exam_direct_payments (plan_id, upper(reference)) WHERE voided_at IS NULL;

ALTER TABLE student_notices
    ADD COLUMN installment_id UUID NULL REFERENCES live_exam_installments (id) ON DELETE SET NULL,
    ADD COLUMN direct_payment_id UUID NULL REFERENCES live_exam_direct_payments (id);
-- Re-create student_notices_kind_check: every kind in V60, plus
--   INSTALLMENT_PAYMENT_RECEIVED, INSTALLMENT_DUE_SOON, INSTALLMENT_OVERDUE,
--   INSTALLMENT_PAYMENT_VOIDED, INSTALLMENT_SCHEDULE_CHANGED
-- Re-create exam_outbound_mail_kind_check: every kind in V38, plus
--   INSTALLMENT_RECEIPT, INSTALLMENT_DUE_SOON, INSTALLMENT_OVERDUE
```

Receipt numbers reuse the existing `live_exam_receipt_seq` (V52), so no new sequence is needed. Changing a schedule updates a changed unpaid row in place, adds new rows and removes the rows past the new end, so an unchanged row keeps its id and its notices (review fix, section 0). The inbox hides a due-soon or overdue notice whose `installment_id` is null, because it refers to a date that no longer applies. The condition goes in `FROM`, so the unread counts agree.

`plan.grant_id` is updatable, because Restore access moves it. When re-creating a check constraint, copy the current list exactly: 13 kinds in V60 and 5 in V38. Leaving one out would break an existing feature at its next insert.

## 6. API

All new code goes in the `exams` package (`LiveExamReceipts` and `StudentNoticeWriter` are package-private there), using `JdbcTemplate` as `staffledger` does.

New permission `LIVE_EXAM_INSTALLMENT`, Master Admin only, in `LiveExamPermissionContributor`.

| Route | Who | Purpose |
|---|---|---|
| `GET /api/v1/live-exam-grants/quote?scope=&moduleId=&examId=&studentId=` | Grant | List price, discount, price after discount, free or not. With a student: any unfinished plan on this target, and any access they already hold, for the form's warnings |
| `POST /api/v1/live-exam-grants` | Grant, plus Installment when `payment` is sent | Gains an optional `payment` object. Absent means No charge, exactly as today |
| `GET /api/v1/installment-plans?status=&q=&studentId=&examId=&moduleId=&page=` | Installment | The list, most overdue first. The ID filters serve the links from other screens (D18) |
| `GET /api/v1/installment-plans/summary` | Installment | Total due, overdue count, due-soon count |
| `GET /api/v1/installment-plans/{id}` | Installment | Schedule with computed state, payments |
| `POST /api/v1/installment-plans/{id}/payments` | Installment | Record a payment |
| `PUT /api/v1/installment-plans/{id}/schedule` | Installment | Replace unpaid rows |
| `POST /api/v1/installment-plans/{id}/restore-access` | Installment | Give revoked access back on the same plan |
| `POST /api/v1/installment-payments/{id}/void` | Installment | Void the latest payment, with a reason |
| `GET /api/v1/installment-payments/{id}/receipt.pdf` | Installment | Staff copy of a receipt |
| `GET /api/v1/me/offline-purchases` | Student | Each row gains `payment`: total, paid, due, next date, status, or null |
| `GET /api/v1/me/installment-plans/{id}` | Student | Own plan: schedule and payments. Another student's is 404 |
| `GET /api/v1/me/installment-payments/{id}/receipt.pdf` | Student | Own receipt. Another student's is 404 |

```jsonc
// POST /api/v1/live-exam-grants
{
  "studentId": "…", "scope": "MODULE", "moduleId": "…",
  "reason": "",                 // required only when payment is absent
  "payment": {
    "agreedTotal": 6300.00,
    "amount": 2100.00,
    "method": "BKASH",
    "reference": "TXN8841",     // printable ASCII, 1 to 100 characters
    "paidOn": "2026-11-08",
    "note": null,
    "schedule": [
      { "amount": 2100.00, "dueOn": "2026-11-08" },
      { "amount": 2100.00, "dueOn": "2026-12-08" },
      { "amount": 2100.00, "dueOn": "2027-01-08" }
    ]
  }
}
```

- `GrantRequest.reason` loses `@NotBlank`. The service requires it when `payment` is absent, and stores "Offline payment" when it is blank and a payment is present.
- `grant()` stays one transaction: grant, plan, rows, first payment, notices and audit commit or roll back together. A failed payment leaves no access.
- A `DataIntegrityViolationException` from the one-active-grant index, on a double click or two tabs, returns `GRANT_ALREADY_ACTIVE`, not a server error.
- The student routes check that the caller is the owner and answer 404 otherwise. The staff routes check `LIVE_EXAM_INSTALLMENT`. `SecurityConfig` only requires a signed-in user, so each route must do its own check.
- A free exam accepts only No charge. An unpriced target has a null quote, and the Master Admin types the total.
- Validation errors use `VALIDATION_FAILED` with field details, as `ExamPricing.validate` does.
- New error codes: `INSTALLMENT_OVERPAYMENT`, `INSTALLMENT_DUPLICATE_REFERENCE`, `INSTALLMENT_VOID_NOT_LATEST`, `INSTALLMENT_RESTORE_REFUSED`, `PACKAGE_HAS_INSTALLMENTS`. Add them to `ErrorCode`, the web error maps, and the en and bn copy.
- New audit actions: `INSTALLMENT_PLAN_CREATED`, `INSTALLMENT_PAYMENT_RECORDED`, `INSTALLMENT_PAYMENT_VOIDED`, `INSTALLMENT_SCHEDULE_CHANGED`, `INSTALLMENT_ACCESS_RESTORED`, with entity types for plan and payment. The reference is not put in a log line.
- `LiveExamEnrolmentListService` and `StudentLiveExamAccessService` gain `planId`, paid, due and status on rows that have a plan. These fields are null unless the caller holds `LIVE_EXAM_INSTALLMENT`, so a System Admin who can read a student's access sees no money.

### 6.1 Receipt PDF

Extend `LiveExamReceiptPdf` with a second render method for a direct payment. `header` and `billedTo` are changed to take plain values rather than an order, so both receipts share them. The online receipt's layout must not change.

- Item row: title, Package or Exam, validity, standard price.
- Totals: Subtotal, Price adjustment (only when the agreed total differs, with its sign), **Total**, Paid on this receipt, Paid so far, **Due**.
- Next installment line: "BDT 2,100.00 on 08 Dec, 2026", absent once fully paid.
- Stamp: `PAID` when nothing is due, `PART PAID` otherwise, `VOID` for a voided payment.
- Payment details: Status, Paid on, Payment method, Transaction reference, Receipt number, Installment "2 of 3".
- The `SSLCommerz` gateway row and the order ID are absent.
- A title with no English text falls back to "Exam" or "Package", since the PDF cannot print Bangla. The online receipt's fallback prints an order ID, which does not exist here.
- A plan with one row prints no installment line.

### 6.2 Reminder job

`InstallmentReminderJob`, shaped like `StaffSalaryJob`:
- Runs daily at 06:30 Dhaka, off when `app.exam-jobs.scheduled` is false, guarded by `AtomicBoolean`.
- One transaction per plan, so one failure leaves the rest to the next run.
- Skips plans whose grant is revoked, and plans with no live payment. A suspended student gets nothing, but the Master Admin is still told.
- Calls `SalaryReminders.reminderFor(dueOn, uncovered, today, days)` for the **oldest row that is not fully covered**, once per plan (C8). The amount shown is what is uncovered on that row. Reads the days from `app.installments.due-soon-days`, default 3.
- `reminderFor` returns nothing on the due date itself. The last notices before it are the due-soon ones, and that is intended.
- Dedupe keys hold the installment id and due date, and for overdue also `SalaryReminders.isoWeek(weekStart)`, per recipient, so a repeat run adds nothing and a missed run catches up.
- Each reminder writes the in-app notices and queues the two emails in the same transaction. Delivery happens after commit, and `ExamMailService.scan` retries anything pending.

## 7. Notifications and how each thing reaches each person

The online purchase flow already sets the pattern: an in-app notice, a receipt email to the verified address, and a PDF in the account. Direct payments follow it.

| Event | Student | Master Admin |
|---|---|---|
| Payment recorded (including the first) | Notice `INSTALLMENT_PAYMENT_RECEIVED` under Exams. Receipt email if the account email is verified. PDF on the Offline purchases tab | Notification `INSTALLMENT_PAYMENT_RECORDED`. PDF on the Installments page |
| Due within 3 days | Notice `INSTALLMENT_DUE_SOON` under Exams, and an email | Notification `INSTALLMENT_DUE_SOON`, and an email |
| Overdue, then weekly | Notice `INSTALLMENT_OVERDUE` under Exams, and an email | Notification `INSTALLMENT_OVERDUE`, and an email |
| Payment voided | Notice `INSTALLMENT_PAYMENT_VOIDED` under Exams. Receipt shows VOID | Seen on the plan and in the audit log |
| Schedule changed | Notice `INSTALLMENT_SCHEDULE_CHANGED` under Exams | Seen on the plan and in the audit log |

- The existing `ACCESS_GRANTED` notice still fires on enrolment.
- The `PURCHASE_NOTICE` email is not sent, since the Master Admin is the one recording the payment.
- There is no typed receipt address on this flow, so a student with no verified email gets no email. They still get the notices and the receipt in the account. The form says so.
- The Master Admin's address comes from the existing `ExamMailQueue.masterAdmin()`. The link in their email uses `ctx.forRole(Role.MASTER_ADMIN, "/staff/installments/{planId}")`, so it opens on the admin host.
- Mail goes through `exam_outbound_mail`, queued after commit with `AfterCommit`, with kinds `INSTALLMENT_RECEIPT` (key = payment id), `INSTALLMENT_DUE_SOON` and `INSTALLMENT_OVERDUE` (key = recipient, installment id, due date, and for overdue the week). Each has its own bilingual `EmailTemplates` method and a branch in `ExamMailTexts.prepare`.
- Like the online receipt, the receipt email carries the receipt number and a link, not a PDF attachment.
- A reminder email is checked again at send time. If the row has been paid, rescheduled or the access revoked in the meantime, it is skipped.

**Student notices.** `StudentNoticeWriter` gains methods with their own insert statements that store `installment_id` or `direct_payment_id`, and leave `exam_id` and `module_id` null, so a later change to the exam or package cannot hide a notice about money. `StudentInboxService` joins through the plan, without a deleted-at filter, and adds an `installment` block to `NoticeView` (title, amount, due date, paid, due, receipt number).

**Staff notifications.** Add three `StaffNotificationKind` values. Payload: `studentName`, `titleEn`, `titleBn`, `amount`, `dueOn`, `due`. All text through `fitText`. Never the reference. Link: `/staff/installments/{planId}`. Build the payload with a `LinkedHashMap`, because `Map.of` throws on a null title.

**Installment tab.** `GET /api/v1/me/staff-notifications` gains `group=INSTALLMENT`, and `unread-count` adds `byGroup`, keeping `count` as is. Installment kinds also appear under All and Unread, as a student's Exams notices also appear under All.

## 8. Web

**Direct enrolment** (`direct-enrolment.tsx`, `exam-enrol.tsx`, `one-student-enrol.tsx`)
- A shared component `components/staff/enrol/payment-fields.tsx` with a pure `payment-model.ts` that builds and checks the schedule and builds the cash reference.
- `/staff/enrol` becomes one student (D12): the package or exam picker, a student search that selects one student, then the payment fields. The list, the per-student results and the bulk copy (`listHeading`, `count`, `finished`, `failedNote`) are removed. `?student=`, `?exam=` and `?package=` keep working.
- `OneStudentEnrol` gains an opt-in slot for the payment fields. `exam-enrol.tsx` uses it, and since D21 so does `roster-tab.tsx` (section 14).
- Type choice: Full payment (default), Installments, No charge. A free exam shows only No charge.
- Fields: standard price (read only), agreed total, paid today, method, reference, payment date. For Installments: number of installments, per-installment amount, next installment date (one month ahead), and a read-only schedule preview. Choosing Cash fills in the reference (C4).
- The reason is required only for No charge.
- When the total differs from the standard price, the form shows the difference.
- The generated cash reference updates with the date while it is unedited, is left alone once the admin has typed in it, and is cleared on switching to another method if unedited.
- The two warnings from section 4 (an unfinished plan, access already held) show once both a student and a target are chosen.
- Submit opens a confirmation with the student, item, total, paid today, due, and next date (C7). The button is disabled while saving.
- The success state links to the receipt and to the new plan, clears the student and keeps the target.

**Links to the Installments page** (D18). Master Admin only. A row with a plan links straight to `/staff/installments/{planId}`. A No charge row has no plan and no link.

| Screen | File | Link |
|---|---|---|
| Direct enrolment page | `direct-enrolment.tsx` | "Manage installments" at the top, and on success |
| Exam page, enrol panel | `exam-enrol.tsx` | "Manage installments", to `/staff/installments?examId=` |
| Enrolled list (used on the enrol page, the exam panel and `/staff/enrolments`) | `enrolled-list.tsx` | Paid or Due pill on each row, linked to the plan |
| Enrolled students page | `app/(staff)/staff/enrolments/page.tsx` | "Manage installments" for that exam or package |
| Exam row menu | `exams/exam-row.tsx` | New item, to `/staff/installments?examId=` |
| Package row menu | `catalog/module-list.tsx` | New item, to `/staff/installments?moduleId=` |
| Student page, access section | `access/student-access-section.tsx` | On each row with a plan, and "Manage installments" to `/staff/installments?studentId=` beside the enrol button |

**Installments page** (new): `/staff/installments`, `/staff/installments/[planId]`
- Add `installments` to `staff-nav.ts`, group `money`, Master Admin only, with label in `common.staffNav`.
- List: summary band (total due, overdue, due soon), filters (All, Overdue, Due soon, Completed, Access revoked), search, and columns Student, Item, Total, Paid, Due, Next due, Status.
- The list reads `studentId`, `examId` and `moduleId` from the URL, shows which filter is on, and can clear it.
- Detail: schedule with states; payments with receipt download; Record payment (amount prefilled with the next row, method, reference, date); Change schedule; Void latest; Revoke access using `ReasonDialog`; Restore access when revoked.
- After any change, the list, detail, summary, enrolled list and bell queries are refreshed.

**Notification centre** (`notifications-page.tsx`, `notification-text.ts`, `staff-notifications.ts`)
- A third tab, Installments, with a badge, for the Master Admin only.
- The three kinds added to `KNOWN_KINDS` and the sentence builder.

**Student**
- `my-exams-page.tsx`: the tab reads `?tab=` from the URL.
- `offline-purchases.tsx`: cards with a plan show Paid, Due and Next date, and a pill for Overdue or Completed. A View payments panel shows the schedule and a Download receipt button for each payment. Cards without a plan are unchanged.
- `StudentOwnAccessService`: `MANUAL_EXAMS` and `MANUAL_MODULES` become `revoked_at IS NULL OR` a plan exists on that grant, returning an access-removed flag. A row with a plan also ignores the deleted-at filter on its package. Such cards stay visible for their receipts, but do not link to the exam as if it were open. After a restore the old revoked grant has no plan, so the student sees one card.
- `notices/page.tsx`, `notice-kind.ts`, `notices.ts`: the five kinds, and an `InstallmentRow` beside `AnnouncementRow`, with its title from `notice.installment`, overdue in the warning tone, and a "View payments" action to `/my-modules?tab=offline`. `noticeHref` returns that path for these kinds.
- Fields the API can leave out (`payment`, `nextDueOn` and so on) are optional in the types, and read with `isMissing`.

**Copy.** Every string in en and bn. Bangla is written as a Bangla speaker would say it, matching terms already in `web/src/i18n/messages/bn/`. Installment is **কিস্তি**.

## 9. Phases

Each phase can be reviewed alone. Phases 1 to 3 are API only and change nothing users see.

| Phase | Work | Done when |
|---|---|---|
| 1. Schema and enrolment | V79, permission, audit and error codes, `InstallmentCalculator`, schedule validator, quote route, `payment` on the grant | A grant with a payment creates the plan, rows and first payment in one transaction. Without one it behaves as today |
| 2. Payments and receipts | Plan list and detail, record payment, schedule change, void, restore access, package-delete guard, PDF, receipt email, staff and student receipt routes, offline list changes | A receipt prints correctly for part paid, paid and void. The online receipt is unchanged |
| 3. Notifications | Notice kinds, inbox join, staff kinds, group filter and count, reminder job, reminder emails | Notices and emails go out once each, on the right days, and stop when paid or revoked |
| 4. Staff web: enrolment | Payment fields on both forms, `/staff/enrol` reduced to one student | All three types work on both screens. The package page is covered by section 14 |
| 5. Staff web: Installments | Page, detail, actions, notification tab, links from every screen in section 8 | The Master Admin can run a plan from start to finish, starting from any of those screens |
| 6. Student web | Offline tab, receipts, notices, URL tab | A student sees the same numbers as the Master Admin |
| 7. QA and release | Section 10 and 11 | Every exit criterion in 10.5 met. Live and recorded |

### 9.1 Guardrails during the build

Phases 1 to 6 go live together in one release. A half-shipped set is not safe: phase 4 removes the bulk list, and phase 5 adds the screens its links open.

**Do not change.** These are outside this feature, and a diff that touches them needs a reason:
- `LiveExamOrder*`, `SslCommerzGateway` and the checkout.
- ~~`roster-tab.tsx` and `LiveExamRosterService` (D17).~~ Replaced by D21: section 14 lists the changes allowed to these two files.
- The online receipt's output. `LiveExamReceiptPdf` is refactored only as far as sharing `header` and `billedTo`.
- The existing `POST /api/v1/live-exam-grants` behaviour when `payment` is absent.
- `SalaryReminders` and the Staff pay tables. Reuse only.

**Rules that are easy to break by accident**
- Nothing that can throw may run inside the money transaction after the payment is written, unless it is part of the same all-or-nothing result. `StaffNotificationWriter.notify` is `@Transactional`, so catching its exception does not help: the transaction is already marked for rollback. Keep payloads valid instead (section 7).
- Reminders and mail run outside the payment. A failed email must never undo or block a recorded payment. Mail is queued in the transaction and sent after commit.
- Every date is a Dhaka `LocalDate`, from `clock.withZone(DHAKA)`. Never `LocalDate.now()` with the server's zone. Inject `Clock`, so tests can move time.
- The new job uses the existing `app.exam-jobs.scheduled` flag, which `PostgresIntegrationTest` already turns off.
- No reference, phone or full transaction detail in logs or staff notification payloads.
- Every new string in en and bn, in natural Bangla.
- Every new staff route checks permission itself, and every student route checks ownership.

**At the end of each phase:** the API compiles, the web type-checks, the diff matches the phase, and the plan is updated if anything turned out differently. The test suites run when Shahriar says so.

## 10. Test criteria and edge cases

Automated tests are written with each phase. **They run only when Shahriar says so.** QA uses a separate database. `coaching_center_sample` is not reloaded or cleaned without Shahriar's go-ahead.

### 10.1 Automated tests

| Area | Must prove |
|---|---|
| `InstallmentCalculator` | Exact, partial and carry-over payments, the final payment, voided payments ignored, the oldest uncovered row found correctly |
| Schedule validator and `payment-model.ts` | Sum, order, row 1 rules, month-end dates, remainder on the last row, whole-paisa adding, cash reference format |
| Enrolment API | All-or-nothing, No charge unchanged, price stored by the server, every refusal in 10.2 |
| Payment API | Overpayment, duplicate reference, concurrent recording, void only on the latest, snapshots correct |
| Permissions | System Admin, Instructor and student get 403 on staff routes. Another student's plan and receipt are 404. Money fields absent for a System Admin on the access section |
| Reminder job | Timing, weekly repeat, missed run catch-up, no repeat, stops on payment, revoke and rescheduling, one reminder per plan |
| Email | Each kind sent once to the right person with the right host in its link. None for an unverified student. Skipped at send time when no longer true. A send failure leaves the payment intact and is retried |
| Notices | The five kinds land under Exams, unread counts are right, and a deleted package does not hide them. Installment tab shows only its kinds |
| PDF | Part paid, paid, void, one-row plan, adjusted price, English-less title, long title across pages. The online receipt is unchanged |
| Migration | V79 applies on a copy of production data. Every old notice and mail kind can still be inserted |
| Web | Payment fields, cash reference, one-student page, confirmation, warnings, Installments page and URL filters, links per screen by role, third notification tab, `InstallmentRow`, offline cards, `?tab=offline`. The Roster tab shows no payment fields |

Existing tests that will need updating: `web/tests/staff-admin/direct-enrolment.test.tsx` (bulk removed), `web/tests/staff-admin/copy.test.ts`, `web/tests/staff-admin/error-codes.test.tsx`, `web/tests/s1-b/staff-nav.test.ts`, `web/tests/staff-ui-clean/nav-by-role.test.tsx`, `web/tests/staff-notifications/`, `web/tests/notices/notice.test.tsx`, `web/tests/student-nav/my-exams.test.tsx`, `LiveGrantAdminIT`.

### 10.2 Edge cases

Each has a test, or is checked in 10.3.

**Enrolment and price**

| # | Case | Expected |
|---|---|---|
| E1 | Exam or package has no price | Quote is empty; the total is typed; the receipt shows no adjustment line |
| E2 | Free exam | Only No charge is offered; the API refuses a payment |
| E3 | Discount ends between opening the form and saving | The typed total stands; the server stores the price at save time |
| E4 | Total raised or lowered | Receipt shows the adjustment with the right sign |
| E5 | Student already has an active grant on the target | 409, no plan, no payment, no receipt number on the record |
| E6 | Double click or two tabs on enrol | One grant, one plan, one payment; the other gets `GRANT_ALREADY_ACTIVE` |
| E7 | Exam or package unpublished while the form is open | Refused, nothing saved |
| E8 | Any step fails after the grant is written | Nothing remains: no grant, plan, rows, payment, notice or email |
| E9 | Student holds the exam through a package or roster | Warning shows; enrolment is still allowed |
| E10 | Student suspended | Enrolment works as today; no notice or email reaches them; the Master Admin is told |
| E11 | Unfinished plan exists on the target after a revoke | Warning with a link; a new plan is allowed if the admin continues |
| E12 | No charge | No plan, no receipt, no pill, no Installments row link. Reason required |
| E13 | System Admin posts a `payment` | 403 |

**Amounts and schedule**

| # | Case | Expected |
|---|---|---|
| E14 | Rows do not add up, even by 0.01 | Refused |
| E15 | Three decimals, zero, negative or a huge amount | Refused |
| E16 | Row 1 differs from the amount paid or the payment date | Refused |
| E17 | Dates out of order, repeated, or a later row in the past | Refused |
| E18 | Paid today is the whole total on an Installments plan | Refused: nothing left to schedule. The form suggests Full payment |
| E19 | Per-installment amount leaves the last row at zero or less | The form blocks it and the API refuses |
| E20 | Amount does not divide evenly (1,000 over 3 after 400 today) | Last row takes the remainder; rows still add up exactly |
| E21 | Next date is the 31st | 31 Jan, 28 or 29 Feb, 31 Mar. No drift |
| E22 | 24 rows, and 25 rows | 24 accepted, 25 refused |
| E23 | Payment date backdated, including into last year | Accepted; a future date is refused; receipt number uses the paid year |
| E24 | Reference is Bangla, blank or only spaces | Refused. Cash is prefilled |

**Later payments**

| # | Case | Expected |
|---|---|---|
| E25 | Exact installment | Row covered; next row shown |
| E26 | Less than the installment | Rest stays due on that row; reminders continue with the smaller amount |
| E27 | Enough for two installments | Both covered; receipt states the first row it touches |
| E28 | Final payment | Due 0, no next date, stamp PAID, status Completed, reminders stop |
| E29 | More than is due | `INSTALLMENT_OVERPAYMENT` |
| E30 | Two admins or tabs record at once | Serialised by the lock; the second is judged on the new balance |
| E31 | Same reference again, in a different case or with spaces | `INSTALLMENT_DUPLICATE_REFERENCE` |
| E32 | Same reference on another student's plan | Allowed |
| E33 | Cash twice on one day | Different generated references |
| E34 | Payment on a completed plan | Refused as overpayment |
| E35 | Payment after access was revoked | Accepted; access stays revoked; receipt issued |

**Void and schedule change**

| # | Case | Expected |
|---|---|---|
| E36 | Void the latest | Balance goes back; number stays used; PDF stamped VOID; student told |
| E37 | Void an older payment | `INSTALLMENT_VOID_NOT_LATEST` |
| E38 | Void the only payment | Access stays; no reminders; list shows it overdue |
| E39 | Void then re-record with the same reference | Accepted, with a new receipt number |
| E40 | Void on a completed plan | Back to active; reminders resume |
| E41 | Reschedule with a partly paid row | Part payment carries forward; rows add up to what is left |
| E42 | Reschedule tries to change a covered row | Refused |
| E43 | Earlier receipts after a reschedule or void | Unchanged |
| E44 | Reminder already sent, then rescheduled | Old notice hidden; new date gets its own reminders; unread count agrees |

**Access**

| # | Case | Expected |
|---|---|---|
| E45 | Overdue, nothing done | Access unchanged (D5) |
| E46 | Revoke | Access ends, reminders stop, plan and receipts stay visible to both sides |
| E47 | Restore | Access back, same plan and balance, original access period, one card for the student |
| E48 | Restore when access is active again, unpublished, or the period has ended | `INSTALLMENT_RESTORE_REFUSED` or `GRANT_ALREADY_ACTIVE`, nothing changed |
| E49 | Access period runs out with money owed | Access ends as normal; reminders continue |
| E50 | Student later buys the same exam online after expiry | Old grant is retired; plan shows access revoked with its balance; receipts stay |
| E51 | Delete a package with a live plan | `PACKAGE_HAS_INSTALLMENTS`. Archive works |
| E52 | Delete an exam or student with a plan | Refused by the existing rules |

**Reminders and messages**

| # | Case | Expected |
|---|---|---|
| E53 | 3 days before, 1 day before, on the day | Due-soon once; nothing on the day |
| E54 | 1 day after, 7 later, 8 later | Overdue on day 1 and day 8 only |
| E55 | Server down on a reminder day | Next run catches up, once |
| E56 | Job runs twice in a day | No duplicates anywhere |
| E57 | Two rows overdue | One reminder, for the oldest |
| E58 | Paid after queueing, before sending | Email skipped |
| E59 | Student email unverified or missing | In-app only; payment unaffected |
| E60 | Mail server down | Payment saved; email retried; marked failed after the maximum attempts |
| E61 | Name or title with 9+ digits, or a null Bangla title | Payment and notification both succeed |
| E62 | Reference with 9+ digits | Never in a payload; payment succeeds |

**Receipts and screens**

| # | Case | Expected |
|---|---|---|
| E63 | Student opens another student's receipt or plan by URL | 404 |
| E64 | Student name in Bangla only | PDF prints without it, as the online receipt does |
| E65 | Online and offline payments in the same month | Receipt numbers never collide |
| E66 | Bilingual and dark mode, phone width | Every new screen reads properly |
| E67 | Row with a plan on a screen a System Admin can open | No money and no link |
| E68 | Student's existing direct enrolments and No charge rows | Look exactly as they do today |

### 10.3 End-to-end walkthrough after the full build

Run on the QA database with real emails going to a test inbox. To test due dates, move the job's date with an injected clock in the test environment, or set the schedule to suit.

| Step | Do | Check |
|---|---|---|
| 1 | Master Admin opens `/staff/enrol` from an exam's menu | Exam already chosen; one student only; price filled in |
| 2 | Choose Installments: 3 installments, raised total, bKash, reference; confirm | Summary correct; plan created; access works at once |
| 3 | As the student | Exam can be sat; notice under Exams; email arrived; card shows paid, due, next date; PDF correct |
| 4 | As the Master Admin | Plan in Installments; Installment tab has the notification; the PDF matches the student's |
| 5 | Follow each link in the section 8 table | Each reaches the right plan or filter |
| 6 | Run the job 3 days before the next date | Student and Master Admin each get one notice and one email |
| 7 | Run it the day after the date, then a week later | Overdue on both; access still works |
| 8 | Revoke access | Student cannot sit; receipts still visible; reminders stop |
| 9 | Record a late payment, then Restore access | Second receipt correct; access back; same plan |
| 10 | Void that payment, then record it again | First stamped VOID; student told; new number issued |
| 11 | Change the last row's date | New date on both sides; earlier receipts unchanged |
| 12 | Record the final payment | Completed; stamp PAID; due 0; reminders stop |
| 13 | New enrolment: Full payment by cash | Reference generated; one receipt; no reminders |
| 14 | New enrolment: No charge | Access works; no plan, receipt or link |
| 15 | Switch to Bangla, then to a phone-width window | All of the above reads properly |
| 16 | Sign in as System Admin and Instructor | No Installments page, tab, link or money |

### 10.4 Regression: what must still work

| # | Check |
|---|---|
| R1 | A real online purchase: checkout, payment return, access, receipt PDF, receipt email, Master Admin email |
| R2 | The online receipt looks the same as before |
| R3 | `/staff/payments`, `/staff/orders` and the student's payment history: same rows and totals. Direct payments do not appear there |
| R4 | Refunding an online order |
| R5 | Staff pay: salary job, ledger, its notifications, and the Unread and All tabs |
| R6 | Every existing student notice kind, unread counts and mark-all-read |
| R7 | Exam reminders, exam-time emails, and the hour-before email |
| R8 | Students already on a package's roster keep their access and can be removed. New package enrolments go through section 14 |
| R9 | Revoking any grant from the existing screens |
| R10 | Existing direct enrolments still give access and list correctly |
| R11 | Exam and package archive and delete, other than the new package guard |
| R12 | Student and admin sign-in on both hosts |

### 10.5 Exit criteria

The release goes ahead only when all of these are true:

1. All automated suites pass, including the updated existing tests.
2. Every case in 10.2 passes. Any exception is written down and agreed by Shahriar.
3. The 10.3 walkthrough passes end to end.
4. Every check in 10.4 passes.
5. V79 and the rollback have both been rehearsed on a copy of production data.
6. No open defect can lose a payment, show a wrong amount, or show one student's data to another.
7. Bangla copy has been read by Shahriar.

## 11. How this ships

The site is live and takes real money. The release follows the steps in [media-release-deploy.md](media-release-deploy.md).

1. Back up the database and the current build before starting.
2. Rehearse V79 on a copy of production data, then start the **previous jar** against that copy to prove it still runs with V79 applied.
3. **API first, then web.** The old web keeps working against the new API because `payment` is optional. The new web against the old API would look like it worked while the payment was silently dropped, since the API ignores fields it does not know. Check the web chunks for the empty API base before the swap, as on earlier releases.
4. V79 adds tables and widens two check constraints. Online purchases are not touched. Run the 10.4 checks that apply to the live site after release.
5. The first reminder job runs at 06:30 Dhaka the next morning. Check its log and the first emails.
6. Enrol a test student with a small real plan, record a second payment, void it, then remove the test data with Shahriar's approval.
7. Rollback: restore the previous jar and web build. The new tables can stay, since nothing old reads them. Old screens show any new notice with the generic label, and queued installment emails are marked skipped. Payments recorded before the rollback stay in the database and reappear when the feature returns.
8. Record the release in `docs/exam-site/reports/client-review-deploy.md`, and update `docs/exam-site/current-api.md`.

## 12. Not in this work

- Students paying an installment online. Payments are recorded by the Master Admin only
- Refunds, and any link to Staff pay
- Automatic suspension of access when overdue
- Changing the agreed total on an existing plan
- A notice to the student when access is revoked (revoking sends none today)
- SMS
- The mobile app
- Bulk enrolment (D12)
- Adding new students to a package's old roster from any screen (D21 replaces it with enrolment by grant)
- A notice to the student when access is restored
- Counting direct payments in revenue totals: they stay out of `/staff/payments`, `/staff/orders` and the student's online payment summary

## 13. What the robustness review changed

Reviewed against the code on 8 November 2026. Each item is now built into the sections above.

| Problem found | Result if left | Fix |
|---|---|---|
| No way back after revoking for non-payment | Student pays, and giving access back charges them a second time | Restore access (section 4) |
| Exam-linked notices are hidden if the target is soft-deleted | An overdue notice disappears | Notices carry no exam or package (section 7) |
| Packages can be deleted while being paid for | Student loses access and the card with their receipts | Delete refused, and plan rows ignore the deleted-at filter (sections 4, 8) |
| `Map.of` throws on a null title, and `notify` marks the transaction for rollback | One missing Bangla title undoes a payment | `LinkedHashMap`, valid payloads (sections 7, 9.1) |
| Reminder on every overdue row | Several messages a week for one debt, now also by email | One per plan (section 6.2) |
| Voiding the only payment makes row 1 overdue the next morning | A correction sends a student an overdue email | No reminders with no live payment (section 4) |
| Student not told of a void or a moved date | They keep a dead receipt or the wrong date | Two more notices (section 7) |
| Double click on enrol hits the unique index | Server error shown to the admin | Mapped to `GRANT_ALREADY_ACTIVE` (section 6) |
| Row 1 was not tied to the payment, and later rows could be in the past | Receipts and schedule disagree; a plan is overdue at birth | Explicit rules (section 4) |
| Floats on the web, `equals` on `BigDecimal` | Valid plans refused over 0.01 | Whole paisa, `compareTo` (section 4) |
| Receipt number drawn before validation | Gaps in receipt numbers | Drawn last (section 4) |
| Case differences get past the duplicate check | The same payment recorded twice | `upper(reference)` index (section 5) |
| Saving emails the student at once | A typing mistake reaches the student | Confirmation step (section 8) |
| Master Admin email links could open on the student host | Link fails | `ctx.forRole` (section 7) |
| `NoticeRow` assumes an exam | Blank title and a wrong "Open exam" button | `InstallmentRow` (section 8) |
| Null fields are left out of JSON | Screens break on `undefined` | Optional types, `isMissing` (section 8) |
| Re-created check constraints can drop a kind | An existing feature fails at its next insert | Copy the lists exactly, and test old kinds (sections 5, 10.1) |
| Rollback path unproven | A failed release could not be undone | Previous jar rehearsed against V79 (section 11) |

## 14. Package enrolment extension

Planned 8 November 2026. Not started beyond the uncommitted work in 14.2. **Nothing here is built until Shahriar says so.** No new migration is needed.

### 14.1 Decisions (Shahriar, 8 November 2026)

| ID | Decision |
|---|---|
| D21 | Packages take payments exactly as exams do. Direct enrolment on a package offers Full payment, Installments and No charge, on the package page and on the direct enrolment page. Replaces D17 |
| D22 | A student who already has access through a package's old roster cannot be enrolled in that package again. The form blocks it and asks the Master Admin to remove the roster entry first. The API refuses it too |
| D23 | When a student's exam and package overlap, in either direction, the form **warns and does not block**. Any price difference is sorted out by hand, through the agreed total |
| D24 | Every direct enrolment sends the exam-time email, for an exam as well as a package, with any payment type |
| D25 | The overlap warning names the package. An exam page row reached through a package links to that package (section 14.11) |
| D26 | Moving a student from an exam plan to a package does **not** close the exam plan. Its unpaid balance stays due and in "total due", and there is no Close plan action yet. The warning says so clearly in that case (section 14.11) |

### 14.2 Audit: what still treats packages as taking no payment

The earlier wrong statement that packages take no payment was one edit, reversed the same day. This audit checked all the code for anything that still behaves that way.

| # | Where | What it does now | State | Action |
|---|---|---|---|---|
| A1 | `direct-enrolment.tsx` | Payment form is back for packages. One leftover from the wrong edit: the Manage installments button is hidden until a target is chosen | Uncommitted | Restore the committed version of that button |
| A2 | `roster-tab.tsx` (package page) | Now uses the payment form and creates a grant | Uncommitted, correct | Keep, and finish with 14.5 |
| A3 | `POST .../roster` and `POST .../roster/paste` | Still give package access with no money. No screen calls them now | Open | Keep the routes, because 14 integration tests use them to set up access. Add the guard in 14.4 |
| A4 | Copy `staffCatalog.setup.rosterNote` (en, bn) | "Enrol someone directly to let them sit without paying" | Wrong | Reword (14.6) |
| A5 | Copy `staffOps.paymentDetail.rosterLink` and `rosterHint` | "Give access without payment", "lets the student sit the exam without paying" | Wrong | Reword (14.6) |
| A6 | Copy `staffCatalog.roster.emptyBody` | Tells the admin to paste a list, which no longer exists | Stale | Reword (14.6) |
| A7 | Package enrolment by grant | Sends no exam-time email. The old roster route did (`ExamMailService.onRosterAdded`) | Regression in A2 | Send it (14.4) |
| A8 | Package receipt PDF | Validity always prints "Lifetime", even when the package's access ends | Wrong for a paid package | Fix (14.4) |
| A9 | Package card on Offline purchases | Always links to the MCQ door, so a written-only package opens the wrong page. Older bug, now on a paid card | Wrong | Fix (14.5) |

Checked and correct for packages: the quote, price snapshot, plan, schedule, payments, void, restore, reminders, notices, student routes, and the Installments list and detail. All the payment integration tests in fact use packages. The gap is the other way round: **no integration test enrols an exam with a payment** (14.8).

Other regressions from A2, fixed in 14.5:
- The list lost its search and page numbers.
- Removing a roster student no longer warns that the student has already sat an exam (`removeStartedBody`).
- The form no longer shows "already enrolled".
- A student with both a grant and a roster row shows only the grant, so the roster entry cannot be removed.
- `useRoster`, `useAddToRoster`, `usePasteRoster`, `useRosterSearch` and `useRemoveFromRoster` have no callers.

### 14.3 Rules

- **Live roster access blocks enrolment.** The check is "a roster row whose access has not ended". It applies to Full payment, Installments and No charge.
- **An expired roster row does not block.** It gives no access, and the list hides it, so blocking would leave the admin stuck.
- **The reverse is refused too.** The roster route refuses a student who holds a live direct (manual) package grant. An online purchase next to a roster row stays allowed, as it is today, because existing behaviour and tests depend on it.
- **Converting a roster student** takes two steps: remove the roster entry, then enrol with the payment. Sittings and results are kept when a roster entry is removed.

**Exam and package overlap (D23)**

- **What counts as overlap.** Enrolling in a package: the student has live access to an exam inside it through that exam's own grant (a direct enrolment, an online purchase or a bundle) or through another package. Enrolling on an exam: the student has live access to a package containing it, through a grant, an online purchase or the roster.
- **What does not count.** Revoked or expired access, a free exam, and an exam no longer in the package.
- **The same-package roster rule (D22) still blocks.** A roster entry on a package only warns when the enrolment is on a single exam inside it.
- **The warning is specific.** It names each exam or package and how the student holds it. For a payment plan it also shows paid and due, with a link to the plan.
- **It shows for all three payment types,** and again in the confirmation dialog. The API never refuses an overlap.
- **Nothing is adjusted automatically.** The total still starts at the standard price, and the warning says to lower it by hand if credit is due. The receipt then shows the existing Price adjustment line.
- **The two plans stay separate.** Each keeps its own schedule, payments, receipts and reminders. Revoking one does not affect the other.
- **Settling an old exam plan after moving to a package.** Paying it off ends it. Otherwise revoking the old exam access stops its reminders, and the student keeps the exam through the package (open question 1).

**Exam-time email (D24)**

- Sent after the enrolment commits, for each dated, published exam it gives access to. A failure is logged and never affects the enrolment or the payment.
- **An exam that is already over is skipped.** The existing queue has no such check, so enrolling in a package with past exams would otherwise send a mail for each one. The check is added only to this new path, so the existing mail is unchanged.
- It uses the existing message key (student, exam, start time), so a student who already has that email gets no repeat.
- Restore access sends it too. The key stops a repeat, and a time that changed while access was revoked is still announced.
- It follows the existing address rule: any email on the account, verified or not. Receipts and installment reminders remain verified-only.

### 14.4 API

| Change | File |
|---|---|
| New error `STUDENT_ON_ROSTER` (409), raised in `grant()` for package scope before any plan is written | `LiveExamGrantAdminService`, `ErrorCode` |
| New error `STUDENT_HAS_DIRECT_ENROLMENT` (409) on the roster route. Paste skips such students and lists them in a new `alreadyEnrolled` field | `LiveExamRosterService` |
| Both paths take a row lock on the package, so a roster add and an enrolment cannot race. Add `lockById` to the package repository if it has none | `LiveExamModuleRepository` |
| Quote gains `onRoster`, `rosterStarted`, `directGrantActive`, and `overlaps`: a list of `{ kind: EXAM or PACKAGE, id, titleEn, titleBn, via, planId, paid, due }`. Only sent when the quote names a student. Built from `LiveExamPackageExamRepository.findActiveByModuleId` for a package and `LiveExamMemberships.activeModuleIds` for an exam, with `LiveExamAccess` and `InstallmentPlanReader.byGrantIds` | `LiveExamGrantAdminService` |
| Enrolled rows gain `onRoster`, `startedAny`, `grantScope`. Package list gains a `q` search | `LiveExamEnrolmentListService` |
| New `ExamMailService.onDirectEnrolment(scope, targetId, studentId)` and `ExamMailQueue.enqueueForDirectEnrolment`, called after commit from `grant()` and `restoreAccess()`. Reuses `consider` and skips exams whose window has ended. `onRosterAdded` stays as it is | `ExamMailService`, `ExamMailQueue`, `LiveExamGrantAdminService`, `InstallmentPlanService` |
| Package receipt validity prints "As set for the package". It is fixed text, so a reprint never changes when exams are added or removed | `LiveExamReceiptPdf` |
| Offline package rows gain the door, so the card opens the right page | `StudentOwnAccessService` |

### 14.5 Web

- **Blocked state on the enrol form.** With `onRoster`, the payment fields are replaced by a message and a **Remove from roster** button. The confirmation warns when the student has already sat an exam. After removal the quote refetches and the form opens.
- **Already enrolled.** With `directGrantActive`, the form says so and cannot be submitted.
- **Overlap warning** on both the exam and package forms: one line per item in `overlaps`, with paid, due and a plan link where there is a plan, and a reminder to adjust the total by hand. It replaces the generic "access held" warning when `overlaps` has items, and is repeated in the confirmation dialog. Submitting is never disabled by it.
- **Package list:** restore search and page numbers. Show Remove on any row with a roster entry, including a student who also has a grant. Restore the started-exam warning.
- **Exam page list:** a row from a package grant is labelled as coming through the package. Its Revoke button is replaced with a link to the package, because revoking there would end access to the whole package.
- **Dead code:** remove the five unused roster hooks.
- **Student card:** the package link uses the door from the API.
- **A1:** restore the button.

### 14.6 Copy (en and natural bn)

| Key | New meaning |
|---|---|
| `staffCatalog.setup.rosterNote` | Enrol a student directly and record the payment, or choose No charge |
| `staffOps.paymentDetail.rosterLink` | Open direct enrolment |
| `staffOps.paymentDetail.rosterHint` | Record a payment taken in person, or give access with No charge |
| `staffCatalog.roster.emptyBody` | Search above to enrol someone |
| New | The roster-block message, the Remove from roster button, the overlap warning lines (exam held, package held, how it is held, adjust the total by hand), the error text for both new codes, and "through the package" |

### 14.7 Edge cases for package enrolment

| # | Case | Expected |
|---|---|---|
| P1 | Student has live roster access; enrol with Full, Installments or No charge | Blocked on the form; API 409 `STUDENT_ON_ROSTER`; nothing written, no receipt number used |
| P2 | Roster row whose access has ended | Not blocked; enrolment works |
| P3 | Remove the roster entry, then enrol | Quote refetches, form opens, plan created, new access period starts |
| P4 | Remove a roster student who has sat an exam | Warned first; results kept |
| P5 | Roster route for a student with a live direct grant | API 409; paste lists them under `alreadyEnrolled` |
| P6 | Roster add and enrolment at the same moment | One wins, the other is refused; never both |
| P7 | Student already has both (from before this change) | Shown once, with Remove available. Count such rows on production before release |
| P8 | Student bought the package online, then direct enrolment | Refused by the access check, as today |
| P9 | Direct enrolment active, then online purchase of the same package | Not offered, as today |
| P10 | Package has no price | Quote empty; total typed; no adjustment line on the receipt |
| P11 | Package discount ends, or price changes, after enrolment | Plan keeps its agreed total and snapshot |
| P12 | Package has only free exams | Payment still allowed; access has no end |
| P13 | Package unpublished or archived while the form is open | Refused, nothing saved |
| P14 | Package archived during a plan | Access and reminders continue; new enrolment and Restore refused |
| P15 | Delete a package with a live plan; with only revoked plans | Refused; allowed, and receipts stay visible |
| P16 | Exam added to the package during a plan | Student gets it; total unchanged |
| P17 | Exam removed from the package during a plan | Access to it follows the existing rule; total unchanged; the Master Admin can change the schedule |
| P18 | Package access ends with money owed | Access ends; reminders continue |
| P19 | Student pays for one exam, then enrols in a package containing it | Warning names the exam with paid, due and plan link; allowed (D23, details in P31 to P42) |
| P20 | Student has the package, then enrols on an exam inside it | Warning names the package and how it is held; allowed (D23) |
| P21 | Exam page shows a package-grant student | Labelled; no Revoke; link to the package |
| P22 | Revoke a package grant | All the package's exams close together; plan and receipts stay |
| P23 | Restore when the student was since added another way | Refused, nothing changed |
| P24 | Re-enrol after a revoke | Unfinished-plan warning; a new plan is allowed |
| P25 | `?moduleId=` on the Installments list | Package plans only, not plans on single exams inside it |
| P26 | Package with dated, upcoming exams | Exam-time email once per exam and time, to the email on the account; none for undated exams (full cases in P43 to P52) |
| P27 | Written-only package card | Opens the written door |
| P28 | Package receipt | Says "Package", "As set for the package", never "Lifetime" |
| P29 | Package title in English missing | Receipt prints "Package"; notices still show the title |
| P30 | System Admin on a package | No enrol tab, no money; API 403 |

**Exam and package overlap (D23)**

| # | Case | Expected |
|---|---|---|
| P31 | Package enrolment; student is on an installment plan for one exam inside | Warning with the exam, paid, due and plan link; submitting still works |
| P32 | Several exams inside overlap | Every one is listed |
| P33 | Exam inside was bought online or in a bundle | Listed as bought; no plan figures |
| P34 | Exam inside is held on No charge | Listed as direct, with no money figures |
| P35 | Student holds the exam through a different package | Listed as overlap |
| P36 | Exam enrolment; student holds a package by grant, online purchase or roster | Warning names the package and how it is held; the D22 block does not apply |
| P37 | The exam is in two packages the student holds | Both are listed |
| P38 | Access revoked or expired, a free exam, or an exam removed from the package | No warning for it |
| P39 | No charge chosen with an overlap | Same warning, and enrolment works |
| P40 | Total lowered by hand after the warning | Plan uses the typed total; receipt shows the negative Price adjustment |
| P41 | Both plans active | Separate payments, receipts and reminders; one reminder per plan, so the student can get two |
| P42 | Old exam access revoked after moving to the package | Its reminders stop; the student still sits it through the package; Restore on that plan is refused while the package covers it |
| P42a | Package revoked later, exam grant still live | The student keeps that exam only, and its plan is unaffected |
| P42b | Access changes after the form was opened | The warning may be out of date, but the API does not depend on it, so nothing is refused or lost |

**Exam-time email (D24)**

| # | Case | Expected |
|---|---|---|
| P43 | Exam enrolment on a dated, upcoming exam, with Full, Installments or No charge | One exam-time email |
| P44 | Undated exam, or hidden category branch | None |
| P45 | Exam, or exams inside a package, whose window has ended | None for those; upcoming ones still sent |
| P46 | Student already has this email through another route | No repeat |
| P47 | Exam time changes after enrolment | Existing publish flow sends the new time once |
| P48 | Restore access | No repeat for the same time; sent if the time changed while revoked |
| P49 | Access revoked before the email is sent | Skipped at send time by the existing check |
| P50 | Mail server down | Enrolment and payment stand; the existing scan retries |
| P51 | Suspended student, or no email on the account | None |
| P52 | Enrolment refused or rolled back | None, because nothing is queued until commit |

### 14.8 Tests

Run only when Shahriar says so.

- **API:** P1 to P9, P13 to P15, P22 to P26, P28, P30, the quote `overlaps` for P31 to P38, the separate-plan cases P41, P42 and P42a, and the email cases P43 to P52.
- **Mail:** extend `ExamMailIT`, which already captures sent mail.
- **Exam scope:** add an exam-enrolment integration test (enrol, record, void, receipt, reminder), since every current one uses a package.
- **Web:** blocked state and removal, already enrolled, the overlap warning on both forms and in the confirmation (with submit still enabled), list search and paging, roster Remove on a double row, the exam-page label, the student card door.
- **Regression:** the 14 integration tests that use the roster route must still pass, then the full API and web suites.

### 14.9 Order of work

1. API: errors, guards, lock, quote fields including `overlaps`, list fields, the exam-time email on enrol and restore, receipt text, door.
2. Web: blocked state, list, exam-page label, A1, dead code, card door.
3. Copy in en and bn.
4. Tests, then the full suites on a fresh test database.
5. Commit on `feature/direct-enrolment-installments`, including the uncommitted A1 and A2 work.
6. Before release, run a read-only count of students with both a roster row and a live direct grant on production (P7).

### 14.10 Open questions

Answered 8 November: overlap warns and does not block (D23), and exam enrolments send the exam-time email (D24).

1. After a student moves from an exam to a package, revoking the old exam access stops its reminders, but its unpaid balance still counts in the Installments "total due", and there is no way to close a plan. Is that acceptable (recommended for now), or should the plan add a Close plan action with a reason?

### 14.11 Package name in the warning, and the package link on exam rows

Planned 8 November 2026. Not built. No new migration. The web already reads both fields (`OverlapItem.packageTitleEn` and `packageTitleBn`, `EnrolledRow.moduleId`), so most of the work is in the API.

**Decision (D25, Shahriar):** the overlap warning names the actual package, and an exam-page row reached through a package links to that package.

#### Issue 1: the package name in the warning

Today, when a student holds an exam through a different package, `LiveExamOverlaps.examRoute` knows which package it is, but the `Hit` keeps only the exam's title. The warning then reads "through the package package".

| Change | File |
|---|---|
| `QuoteOverlap` and `Hit` gain `packageTitleEn` and `packageTitleBn`. They are null when not needed, and the API leaves them out | `LiveExamGrantAdminService`, `LiveExamOverlaps` |
| `examRoute` fills them from the package it found, for both `PACKAGE` and `ROSTER` routes. `openPackage` already returns that package | `LiveExamOverlaps` |
| An exam's own grant, and every `PACKAGE`-kind item, leave them null, because the item's own title already says it | `LiveExamOverlaps` |
| New wording for an exam held on another package's roster: "on the roster of the package {package}". Today it says only "on the package roster", which does not say which package. A package item keeps the existing short text | `overlap-model.ts`, `overlap-notice.tsx`, en and bn `staffAdmin.ts` (bn: "{package} প্যাকেজের রোস্টারে") |
| When several exams come through the same package plan, its paid and due line shows once, on the first of those exams. Otherwise it looks as if the student owes the same money twice | `overlap-model.ts`, `overlap-notice.tsx` |
| Fix the spelling "instalments" to "installments" in the English `overlap.plan` and `overlap.planNoAmounts` | en `staffAdmin.ts` |

The fallback word stays in the web as a safety net for an older API during release.

#### Issue 2: the package link on exam rows

Today the exam page labels a package student "Through the package" and removes Revoke, but cannot link to the package, because the row has no package ID.

| Change | File |
|---|---|
| `EnrolledStudent` and `Row` gain `moduleId`. `MODULE_GRANTS` selects `g.module_id`, `ROSTER_ROWS` selects `r.module_id`, and `EXAM_GRANTS` selects `NULL::uuid` | `LiveExamEnrolmentListService` |
| `toView` and `withPlan` carry it through | `LiveExamEnrolmentListService` |
| On the exam page, show **Open the package** on any row with a `moduleId`, so a roster student gets the link too. Today only a package grant would | `enrolled-list.tsx` |
| The link opens the package's enrolment tab directly (`/staff/modules/{id}?tab=roster`), where that student can be managed | `enrolled-list.tsx` |

On a package's own list the link is not shown, because the admin is already there.

#### The "moved from an exam" warning (D26)

This is the case where a student who is paying an exam in instalments enrols in a package that contains that exam. The exam plan is not closed, so the warning must say so, in the same block as the overlap.

- **Shown when:** the enrolment is on a package, and an overlap item is an exam with a plan that still has money due.
- **Wording (English):** "This student is still paying for {exam} in instalments: paid {paid}, still due {due}. Enrolling in this package does not close that plan. The {due} stays owed, and stays in the Installments total due, until it is paid. If the exam is no longer needed, the balance must be dealt with by hand. Revoking the exam's own access stops its reminders but does not clear the balance."
- **Wording (Bangla):** natural wording written in the same meaning, checked against the existing Bangla terms (কিস্তি, বকেয়া, পরিশোধিত).
- **Other plans in the overlap** (a package plan on an exam enrolment, or any plan) get a shorter line: "Paying in instalments: paid {paid}, still due {due}. This plan stays open."
- **The line appears** in the warning on the form and in the confirmation, and it is never removed by a choice the admin makes, because it describes money that is still owed.

#### Edge cases

| # | Case | Expected |
|---|---|---|
| P68 | Package enrolment; the student pays for an exam inside it in instalments | The moved-from-exam line appears with the exam's paid and due, and the plan link |
| P69 | Same, but the exam plan is already paid in full | The line is not shown; the exam appears as a normal overlap |
| P70 | Two exam plans inside the package | One moved-from-exam line for each, each with its own figures |
| P53 | Package enrolment; an exam inside is held through another package's grant | "through the package {name}", with that package's name in the current language |
| P54 | Same, but through another package's roster | "on the roster of the package {name}" |
| P55 | Exam held by its own grant | No package name; existing text |
| P56 | Exam enrolment; the student holds a package containing it | The item's own title is the package; existing text; no repeated name |
| P57 | Package has a title in only one language | The other language falls back to it, as titles do elsewhere |
| P58 | Two exams come through one package plan | Both are listed; paid and due appear once |
| P59 | Two exams come through two different package plans | Each shows its own paid and due |
| P60 | Other package has no plan (online purchase or No charge) | Named, with no money line |
| P61 | Exam page; student through a package grant | "Through the package", no Revoke, and a link that opens that package's enrolment tab |
| P62 | Exam page; student through a package roster | "On the roster", and the same link |
| P63 | Exam page; student with their own exam grant | Revoke as before; no package link |
| P64 | Student holds the exam through two packages | Shown once, linking to one of them. The other is still on the student's access page |
| P65 | Package archived after enrolment | The link still opens the package for staff |
| P66 | Package list | No package link on its rows; `moduleId` present but unused |
| P67 | System Admin or Instructor calls either route | 403 as before; the new fields are never seen |

#### Tests (run only when Shahriar says so)

- **API:** extend `LiveEnrolmentListIT` to check `moduleId` on a package grant, a roster row and an exam grant (null). Add `packageTitleEn` and `packageTitleBn` checks for P53 to P56 to the overlap test in 14.8.
- **Web:** extend `overlap-model.test.ts` for the wording choice and the paid-and-due-once rule. Add tests for the exam-row link on P61 to P63.

#### Order of work

1. API: `moduleId` on rows, then the package titles on overlaps.
2. Web: wording choice, paid-and-due-once, the exam-row link and its `?tab=roster` target, copy in en and bn.
3. API `test-compile` and web `tsc`.
4. Then the tests, with the rest of section 14, when Shahriar says so.
