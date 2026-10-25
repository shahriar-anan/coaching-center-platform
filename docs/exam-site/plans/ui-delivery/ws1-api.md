# WS1 — API additions and demo seed

Branch `ui/ws1-api`, cut now. Runs beside WS0. API only: `api/` and the API docs. Follow [../api-completion/README.md](../api-completion/README.md) practices (Flyway only, `PageResponse`, one `ErrorCode` per condition, `404` for what a caller may not see, `*IT` on real PostgreSQL, routes added to `AuditLogIT`, `current-api.md` updated). The next Flyway version is `V39`. **Never edit `V1`–`V38`;** `V37` and `V38` are applied on the staging box.

Web workstreams code against the contracts below with mocked `fetch`. Keep them exactly, or write the change in **Report** before WS3, WS5 and WS6 merge.

## Owns

`api/src/**`, `api/.cursor/progress/ui-delivery/**`, `docs/exam-site/current-api.md` (append a section "Added for the UI delivery").

## Tasks

### A. Saved selection during an open MCQ sitting (default D-a)

`GET /api/v1/live-exams/{id}` for a student with an open sitting fills `selected` on each `StudentOption` (true for the option saved by `PUT .../answers`, false otherwise). `correct` and `explanation` stay null until the review rule allows them. The later attempt (`RETAKE`) behaves the same. **Test** (`LiveSittingIT`): save two answers, read during the sitting, see `selected` true on those two and `correct` null everywhere; after the deadline, the review still shows both.

### B. Exam-type filter for the two doors (U3, D-b)

Optional `examType=MCQ|WRITTEN` on:

| Call | Effect |
|---|---|
| `GET /api/v1/public/live-exam-categories` | Each category's `modules` keeps only modules that have at least one published exam of that type. A category stays when it, or a descendant, keeps a module, or when it has no modules at all anywhere below it (shown as coming soon). Other categories drop out. |
| `GET /api/v1/public/live-exam-modules` | Only modules with a published exam of that type. |
| `GET /api/v1/public/live-exam-modules/{id}/exams` | Only exams of that type. |
| `GET /api/v1/live-exam-modules` (student) and `GET /api/v1/live-exam-modules/{moduleId}/exams` (student) | Same filters on the grant-filtered reads. |

Each public module row also gains `mcqCount` and `writtenCount` (published exams of each type), so a card can say "৩টি এমসিকিউ · ২টি রিটেন" and the module page can say the purchase covers both. An unknown `examType` is `400 VALIDATION_FAILED`. No filter keeps today's behavior. **Tests** (`LivePublicCatalogIT`, `LiveCatalogIT`): a mixed module appears in both doors with only that type's exams; a written-only module is absent from the MCQ door; an empty category appears in both; the counts are right; bad value 400.

### C. Roster membership list (U8)

`GET /api/v1/live-exam-modules/{moduleId}/roster?q=&page=&size=` — Master Admin (`LIVE_EXAM_ROSTER`). Rows: `studentId`, `name`, `phone`, `email`, `studentCode`, `addedAt`, and `startedAny` (true when the student has a sitting in this module, so the remove dialog can warn that the result stays). Name order, `q` matches name, phone, email or code. Deleted module 404. System Admin and Instructor 403, student 403. **Test** (`LiveRosterIT`).

### D. Student access list (U8)

`GET /api/v1/students/{id}/live-exam-access` — Master Admin and System Admin (`STUDENT_READ`). One row per module the student can sit, ordered by module title: `moduleId`, `moduleTitleEn`, `moduleTitleBn`, `via` (`ROSTER`, `MODULE_PURCHASE`, or `EXAM_PURCHASE`), `exams` (for `EXAM_PURCHASE`, the exam ids and titles bought; empty means the whole module), `orderId` and `tranId` when it came from a purchase. A student with both a roster row and a purchase shows both reasons. Unknown student 404. Instructor 403. **Test** (new `StudentAccessIT`).

### E. System Admin reads exam payments (U7)

Grant `LIVE_EXAM_ORDER_READ` to `SYSTEM_ADMIN` in `LiveExamPermissionContributor`. Update `a7-staff-payments.md` decision P1. **Test** (`LiveExamPurchaseIT`): System Admin 200 on the list, Instructor and student still 403.

### F. Student's own payment history (U12)

`GET /api/v1/me/live-exam-orders?page=&size=` — student only (`LIVE_EXAM_BROWSE`). Own orders, newest first: `orderId`, `tranId`, `status` (`PENDING`, `PAID`, `FAILED`, `CANCELLED`, the same words as `/live-exam-orders/status`), `amount`, `currency`, `scope`, the exam or module titles in both languages, `paymentChannel`, `createdAt`. No `valId`, no other student. **Test** (`LiveExamPurchaseIT`): own rows only, staff 403.

### G. Demo seed (U6)

A staging-only seeder, `DemoContentSeed`, run when `SEED_DEMO=true` on the `staging` (or `dev`) profile. `prod` refuses to start with `SEED_DEMO=true` (extend `ProfileStartup`). Idempotent: fixed UUIDs, insert only what is missing. All demo rows use fixed UUIDs listed in one class, and `api/.cursor/progress/ui-delivery/demo-cleanup.sql` deletes exactly those rows (in FK order) for when the client starts real work.

Content (Bangla titles from the UI prompts, English titles too):

- Categories: বিজেএস এক্সাম (children ১৯তম বিজেএস and ২০তম বিজেএস as modules under it, see below), বার কাউন্সিল এক্সাম (empty, coming soon), and একাডেমিক (one MCQ-only module "সাধারণ আইন অনুশীলন").
- Module ১৯তম বিজেএস, price 2500.00, published, with: সিআরপিসি (MCQ, 10 questions, 40 minutes, pass 40, starts tomorrow 12:00 Dhaka relative to the seed day), পেনাল কোড (MCQ, 10 questions, already ended yesterday, with 8 demo students' posted scores so results and the leaderboard show, including a shared rank), সাক্ষ্য আইন (MCQ, starts 30 minutes after the seed run, for a live walkthrough), সিপিসি (written, undated, maximum 50, a small generated question PDF), ইভিডেন্স (written, ended yesterday, two demo answers: one published with marks and comment, one draft awaiting publish).
- Module ২০তম বিজেএস, price 2000.00, published, two undated exams, one with no price.
- Accounts (passwords from env, never in code): an instructor (`SEED_INSTRUCTOR_*`, active, assigned to ১৯তম বিজেএস), and 8 demo students (`demo-student-N@example.com`, phones `0170000010N`, no password, so nobody can sign in as them). The existing `SEED_STUDENT_*` student gets a roster mark on ১৯তম বিজেএস.
- MCQ questions are real-looking law questions in Bangla with four options, one correct, and a one-line explanation.

Document the run in `api/.cursor/progress/ui-delivery/demo-seed.md`: env names, how to run, how to undo, and that relative dates are fixed at the moment of the run (re-run after cleanup to refresh them).

**Tests** (`DemoContentSeedTest` or IT): runs twice without duplicates, refuses on `prod`, the cleanup script removes every demo row and nothing else.

## Order

A, E, F (small) first, then B, C, D, then G. Run each touched `*IT`, then the full suite alone (`mvn -q clean test`; only `BookOrderIT` and `CoursePurchaseIT` may fail).

## Report

(Agent fills this in, including any contract change.)
