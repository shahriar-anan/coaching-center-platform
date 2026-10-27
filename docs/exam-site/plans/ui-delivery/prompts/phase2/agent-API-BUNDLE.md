# Prompt: API-BUNDLE (phase 2, bundle order, plan D1)

You are agent **API-BUNDLE**. You build one payment for several exams in one module (decision D1). You work on the API only. The web side already exists behind the flag `NEXT_PUBLIC_BUNDLE_ORDERS`, and that flag stays off.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/api-bundle -b ui/api-bundle catalog-acquisition`
2. Work only in `F:\cc-wt\api-bundle`. Never write under `F:\coaching-center-platform\` except the lock file.
3. The tests need test database settings, and `api/.env` has none. In the shell, read `DB_USERNAME` and `DB_PASSWORD` from `F:\coaching-center-platform\api\.env`, then set `TEST_DB_URL=jdbc:postgresql://localhost:5432/coaching_center_test`, `TEST_DB_USER` and `TEST_DB_PASSWORD`. Never print the values. Never point a test run at `coaching_center_dev` or `coaching_center_qa`.

## Read first

- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: section 2 (D1 and the defaults), section 7 (V39, merged), section 8 (the bundle contract), section 9.2 (your files), section 9.4 (the gate).
- `api/src/main/java/com/coachingcenter/api/exams/`: `LiveExamOrderService.java` (`initiate`, `readTarget`, `rejectOwned`, `findOpen`, `productName`, the `OrderView` record), `LiveExamOrderTx.java` (`insert`, `verify`, `confirm`, `writeGrant`), `LiveExamOrderController.java`, `LiveExamPaymentRecheck.java`, `LiveExamOrderAdminService.java`, `LiveExamOrderHistoryService.java`, and `ExamMailService.onOrderSucceeded`.
- V39 (`api/src/main/resources/db/migration/V39__admin_access_and_announcements.sql`): `live_exam_order_exams`, the scope check that allows `BUNDLE`, `live_exam_grants.source` that allows `BUNDLE`, and one active grant per student and exam.
- Existing tests that cover orders: `LiveExamPurchaseIT`, `OrderFulfillmentIT`, `LiveExamPaymentRecheckTest`, `LiveGrantAdminIT`, `ExamMailIT`, `AuditLogIT`.

## Current state (checked 26 Oct)

- `readTarget` accepts exactly one `moduleId` or one `examId`. A body with `examIds` gets `VALIDATION_FAILED`. The web already sends `{ examIds }` (`web/src/lib/api/exam/purchase.ts`).
- V39 has the table and the scope check. No Java code writes or reads them yet.
- `OrderView` carries `examId` only.

## Owned files

- `LiveExamOrderService.java`: the bundle path only. Share helpers with the single-module and single-exam paths, but do not change their behaviour.
- `LiveExamOrderTx.java`: the bundle insert and the bundle grants.
- New classes `LiveExamOrderBundle*.java`, if you need them.
- Read paths for `BUNDLE`: `LiveExamOrderAdminService`, `LiveExamOrderHistoryService`, `LiveExamPaymentRecheck`, and the repository queries, only where a null exam id breaks them.
- New tests: `BundleOrderIT.java` under `api/src/test/java/com/coachingcenter/api/exams/`, and a unit test if it helps.
- `docs/exam-site/current-api.md`: one heading, `API-BUNDLE`.
- Your report section in `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`.

Do not touch: the notice writers (`StudentNoticeWriter`, `StudentInboxService`, `StudentReminderJob`). You only call `purchaseGranted`. Do not touch `LiveExamPublicService`, the migrations V1 to V40, or the web.

## Items

1. **B1, contract.** `OrderView` gets `List<UUID> examIds`. The change is additive: keep every existing field. For a bundle the list holds the exams. For a single exam or a module, decide the value (`null` or a one-item list) and write it in `current-api.md`.
2. **B2, validation.** `POST /api/v1/live-exam-orders` with `{ examIds: [...] }`:
   - Exactly one of `moduleId`, `examId`, `examIds`. Otherwise `VALIDATION_FAILED` (400), as today.
   - 1 to 20 distinct ids. An empty list, a duplicate, or more than 20 gives 400.
   - All the exams belong to one module. Otherwise 400.
   - Each exam exists, is published, and sits in a published, visible module (404, as for one exam). Each has a price (`PRICE_REQUIRED`, 409).
3. **B3, ownership.** Refuse the whole request with `ALREADY_PURCHASED` (409) if the student owns any listed exam: a roster row or a module grant for the module, or an active exam grant for the exam. Nothing is inserted.
4. **B4, insert.** One `live_exam_orders` row: `scope = BUNDLE`, `module_id` set, `exam_id` null, amount = sum of the prices, currency BDT, method SSLCOMMERZ, status INITIATED. One `live_exam_order_exams` row per exam. Both in one transaction. Use the same tran id generator as the other orders.
5. **B5, open orders.** A second request with the same exam set while the order is INITIATED returns that order, so the student gets the same checkout. A different set creates a new order. Say in the report how you match the set.
6. **B6, grants.** Verify (IPN and return) and confirm (Master Admin) both create one grant per exam: `scope = EXAM`, `source = BUNDLE`, `order_id` = the order. Make the "already granted" check per exam, so a replay adds nothing and a partial earlier grant is completed. One `purchaseGranted` notice per exam. Keep the amount, currency, store and tran id checks as they are.
7. **B7, read paths.** The staff order list and detail, the student's order list and receipt, the pending-order page, and `productName` all handle `scope = BUNDLE`, which has no exam id. Say in the report what the product name is for a bundle.
8. **B8, recheck job.** `LiveExamPaymentRecheck` checks bundle orders the same way as the others, and reads only INITIATED rows.
9. **B9, mail.** `afterCommit examMail.onOrderSucceeded(orderId)` must not fail for a bundle. Check what it does with an exam id. Send the purchase mail once per order, not once per exam, unless the code already does otherwise.
10. **B10, tests** in `BundleOrderIT`:
    - three exams give one order, three grants with source BUNDLE, three PURCHASE_GRANTED notices, and the amount is the sum;
    - one owned exam gives 409 ALREADY_PURCHASED and no order row;
    - exams from two modules give 400;
    - a duplicate id, an empty list, and 21 exams give 400;
    - an unpublished exam gives 404, and a missing price gives 409 PRICE_REQUIRED;
    - the same set twice gives the same order, and a different set gives a new one;
    - a valid IPN grants all exams once, and a replayed IPN adds nothing;
    - a failed verification gives FAILED and no grant;
    - Master Admin confirm of a bundle grants all exams, and reject grants none;
    - the single-exam and single-module tests still pass.

## Rules

- Commit messages end with `Co-Authored-By: Claude <your model name> <noreply@anthropic.com>`.
- One commit per item ID. The message starts with the ID.
- **Lock.** Maven runs only while `F:\coaching-center-platform\.heavy.lock` holds your line `API <pid> <time>`. If the file is empty or missing, write your line. If it holds another name, wait. Poll with a short check every 30 seconds, not a sleep loop. Remove only your own line when the run ends. If `INT` holds it, the integrator is running the full suite, so wait.
- Run only the touched classes, not the full suite. The integrator runs the full suite after the merge: `./mvnw -B -Dtest=BundleOrderIT,LiveExamPurchaseIT,OrderFulfillmentIT,LiveExamPaymentRecheckTest,LiveGrantAdminIT,ExamMailIT,AuditLogIT verify`.
- Do not change any response except the additive `examIds`.
- Do not turn the bundle flag on anywhere. Do not change the web. If the web cannot show a bundle without a change, write that in the report and stop.

## Done

1. Add "Report — API-BUNDLE" at the end of `ws9-gaps-and-polish.md`: each item as done, partly or skipped, with its commit hash, and each test class with its count.
2. Send:

```
READY TO MERGE: ui/api-bundle (agent API-BUNDLE)
Last commit: <hash>
Checks: <classes and results>
Items done: B1 to B10 | partly: <…> | skipped: <…>
Contract: POST /api/v1/live-exam-orders { examIds } returns OrderView with scope BUNDLE and examIds (one line)
Migration: none | V41 (stopped for approval)
```

Stop after the READY block and wait for the integrator.
