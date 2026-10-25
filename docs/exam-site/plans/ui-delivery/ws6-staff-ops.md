# WS6 — Staff people, participation, marking, payments, audit

Two branches, both cut from `catalog-acquisition` in phase 2 (after WS0 and WS1 are merged, so the real contracts are in the tree):

**Copy:** Bangla quoted in this file shows intent only. Write the final Bangla by [bangla-copy.md](bangla-copy.md) (its glossary and rewrites win, for example লগইন not প্রবেশ, সরাসরি ভর্তি not রোস্টার, শিক্ষক not ইনস্ট্রাক্টর, তারিখ পরে জানানো হবে). Build every link to the other surface with `siteHref()` or `staffHref()` from `lib/config`.

- **WS6a** `ui/ws6a-staff-people`, agent E after WS5 merges: students, instructors, system admins, participation, submissions and marking.
- **WS6b** `ui/ws6b-staff-ops`, agent B after WS1 merges: payments and payment detail, audit log, staff account, staff leaderboard.

The page table below marks each row 6a or 6b. Read [README.md](README.md) and [ws0-foundation.md](ws0-foundation.md). Pages 4 and 17–29 of [../../ui/admin-prompt.md](../../ui/admin-prompt.md) are the spec, with its server rules for accounts, participation, marking and payments. Differences are listed here. The old course-admin student pages under `(admin)` show which student calls exist; build new pages, do not edit those.

## Owns

**WS6a:** `web/src/app/(staff)/staff/students/**`, `.../instructors/**`, `.../admins/**`, `.../participation/**`, `.../submissions/**`; `web/src/components/staff/people/**`; `web/src/lib/api/exam/staff-people.ts`; `web/src/i18n/messages/{bn,en}/staffPeople.ts`; tests under `web/tests/ws6a/**`.

**WS6b:** `web/src/app/(staff)/staff/payments/**`, `.../audit/**`, `.../account/**`, `.../exams/[examId]/leaderboard/**`; `web/src/components/staff/ops/**`; `web/src/lib/api/exam/staff-ops.ts`; `web/src/i18n/messages/{bn,en}/staffOps.ts`; tests under `web/tests/ws6b/**`.

A payment row links to a student (`/staff/students/[id]`, WS6a) and a student's access list links to a payment (`/staff/payments?tranId=`, WS6b): link by URL only, never import across.

## Pages

| Route | Who | Spec | API |
|---|---|---|---|
| `/staff/students` | MA, SA | Page 18. Search, columns name, phone, email, code, status. "নতুন শিক্ষার্থী" (name, phone, email; never a password), then the `OneTimeCodeDialog`. Row actions: active or inactive, "কোড আবার দিন" (pending only), delete (MA, type-the-name confirm). | `GET/POST /students`, status, activation-code, `DELETE` (check the exact calls in `lib/api` and `current-api.md`) |
| `/staff/students/[id]` | MA, SA | Page 18 detail. Name (editable), contact, status, and the access section (WS1 D): each module with its reason pill (রোস্টার, মডিউল কেনা, পরীক্ষা কেনা), exam names for an exam purchase, a link to the module, a link to the payment for a purchase, and for MA "রোস্টারে যোগ" or "রোস্টার খুলুন". Empty: "কোনো পরীক্ষায় অ্যাক্সেস নেই". | `GET /students/{id}`, `GET /students/{id}/live-exam-access` |
| `/staff/instructors` | MA | Page 19. Search, filters status and assigned, columns name, phone, email, status, modules ("কোনো মডিউল নেই"). Create (name, phone, email), code dialog, reissue for pending. | `GET/POST /instructors`, `POST /instructors/{id}/activation-code` |
| `/staff/admins` | MA | Page 20. Paged list (no search), create without password, status, reissue, delete with confirm. The Master Admin is never a row. | `/admins` calls |
| `/staff/participation` | all staff | Page 17. Filters module, type, from, to, text, sort (date, title, sitting) in the URL. Columns per the spec; MCQ rows show "—" in the written-only columns. A written count opens that module's submissions filtered to the exam. Instructor sees assigned modules only (the server filters). | `GET /live-exam-participation` |
| `/staff/submissions` | MA, SA, Instructor | Landing for the nav item: modules with written exams (instructor: assigned only), each with awaiting-mark and drafts-awaiting-publish counts, linking to the module's list. | `GET /live-exam-modules`, `GET /live-exam-participation` |
| `/staff/submissions/m/[moduleId]` | same | Page 21. Module name in the breadcrumb, counts above, client-side filters on the loaded rows (all, has an answer, failed to submit, draft, published) and name search. Columns per the spec. | `GET /live-exam-modules/{moduleId}/submissions` |
| `/staff/submissions/[participantId]` | same | Pages 22–24 by role. Instructor and MA: "উত্তরপত্র ডাউনলোড" (only when a file exists; this is staff, a download is allowed), the answer opened in the shared PDF viewer, then marks (0 to maximum), optional comment, required marked PDF (`FileDrop`), primary "ড্রাফট সেভ" with the persistent note that the student cannot see it yet and that saving again hides a published result. MA also: "ফলাফল প্রকাশ করুন" (confirm), disabled until a draft exists, then the "প্রকাশিত" chip. SA: read-only facts including the examiner's name, no download, no form. No-file state: "জমা হয়নি · নম্বর দেওয়া যাবে না", no form. | `GET /live-exam-participants/{id}`, `.../answer-pdf`, `POST .../evaluation` (multipart), `POST .../publish` |
| `/staff/exams/[examId]/leaderboard` | MA, SA, assigned Instructor | Page 27. Read-only, same rules as the student board. | `GET /live-exams/{id}/leaderboard` |
| `/staff/payments` | MA, SA (read-only) | Page 25 on the A7 API. Search box (`q`: name, phone, email, code) and a separate transaction-id field (`tranId`), status filter (অপেক্ষমাণ, পরিশোধিত, ব্যর্থ, বাতিল), all in the URL. Columns time, student, phone, product, scope, amount, method (`paymentChannel`, for example bKash or VISA), status, access (`accessGranted`), transaction id with copy. Row copy from the spec ("নিশ্চিত হলে অ্যাক্সেস খুলবে", "পরের পরীক্ষাও খুলবে", "শুধু এই পরীক্ষা", "অ্যাক্সেস হয়নি"). No row action changes an order. Module, exam and date filters from the spec are not in the API; leave them out. | `GET /live-exam-orders` |
| Payment detail | MA, SA | Page 26 as a side panel or `/staff/payments/[tranId]` reading the list with `tranId=`. Adds updated time, `valId`, links to the student and the module or exam, and the access line. MA only: "পেমেন্ট ছাড়া দিতে রোস্টার খুলুন". No refund button. | same |
| `/staff/audit` | MA | Page 28. Paged, newest first, columns time, action (translated label), entity type, entity id, actor. No search. | `GET /audit-log` |
| `/staff/account` | all staff | Page 4, updated: name and role read-only, email with verification (resend and code), phone with "বদলান" (own-phone change), change password, logout. | `GET /me`, `POST /auth/change-password`, `POST /auth/change-phone`, verify-email calls |

**Split:** students, student detail, instructors, admins, participation, submissions (all three) are **WS6a**. Staff leaderboard, payments, payment detail, audit and staff account are **WS6b**.

## LMS additions

- **Marking queue** (WS6a): on a submission, "আগের" and "পরের" move through the module's list in its current filter order, and "সেভ করে পরেরটি" saves the draft and opens the next paper that has an answer and no draft. Keyboard: `J`/`K` for next and previous when focus is not in a field. A small progress line "৬২টির মধ্যে ১৮টি মূল্যায়িত". *(P1)*
- **Side-by-side marking** on desktop (WS6a): the answer PDF on the left, the mark form on the right, stacked on phones. *(P1)*
- **Payments at a glance** (WS6b): a strip above the list with today's paid count and total, and pending count, computed from the list endpoint with `status` filters (no new API). *(P2)*
- **Copy-friendly support** (WS6b): transaction id, `valId` and phone each have a copy button; the payment detail has "শিক্ষার্থীর পাতা খুলুন". *(P1)*

## Rules

- Lists are server-paged with page, search and filters in the URL; the submission list is the one that filters loaded rows on the client, because the server sends a module's rows together.
- Activation codes appear once in `OneTimeCodeDialog` and are never stored in the query cache or logs.
- Destructive actions confirm and name the person or object; deleting a person asks to type the name.
- Status words, not colors alone. Times in Asia/Dhaka. Money as ৳ with two decimals.

## Tests

Student create code dialog, delete MA only, access list reasons, instructor filters, participation filters and "—" for MCQ, submission form per role (instructor no publish, SA read-only, MA publish disabled until a draft), payments filters and read-only SA, audit paging, staff account phone change.

## Report

### WS6b Report (agent B)

**Done (P0):** `/staff/payments` (search, transaction-id search, status filter, all in the URL; columns per the spec; copy on the transaction id; read only for System Admin, with no action that changes an order), the payment detail at `/staff/payments/[tranId]` (the list read with `tranId=`; access line; MA-only "পেমেন্ট ছাড়া দিতে সরাসরি ভর্তি খুলুন" link to `/staff/modules/{id}?tab=roster`), `/staff/audit` (Master Admin only, paged, no search), `/staff/account` (name and role read only; email verification and resend; password change; phone add or change through a dialog that asks for the current password; logout with a confirm), and `/staff/exams/[examId]/leaderboard` (read only; empty state gives the opening time, five minutes after the window, or "তারিখ পরে জানানো হবে").

**P1 done:** copy buttons on the transaction id, validation id and phone in the payment detail. Links from a payment to the student, module and exam, by URL only.

**P2 skipped:** the payments-at-a-glance strip (pending and paid counts). It would need extra list calls per render; left out for tonight.

**Checks:** `npm run lint` 0 errors (2 warnings in older files, `<img>`). `npm test` 182 passed in 24 files (after merging `catalog-acquisition`). `npm run build` passed under the lock; all five staff routes prerender.

**Contract issues:**
- The audit list is not newest-first. `AuditLogController` calls `auditLogs.findAll(pageable)` with no sort on `createdAt`, so the order is whatever the database returns. The screen says "newest first" and depends on that. Needs a sort on `createdAt` descending in the API, or the screen note must go.
- There is no single-payment read for staff. The detail page reads the list with `tranId=` and takes the exact match. Works, but a `GET /live-exam-orders/{tranId}` for staff would be cleaner.

**Notes for other agents:**
- Payment routes are `/staff/payments` and `/staff/payments/[tranId]`. Payment rows link to `/staff/students/{id}` (WS6a), `/staff/modules/{id}` and `/staff/exams/{id}` (WS5), and the roster tab `/staff/modules/{id}?tab=roster`.
- The leaderboard relies on the server for the instructor check: an instructor who is not assigned gets an error state, not a list.
- Every route page wraps its content in `<Suspense>` for the URL-reading guards and list params, as the WS4 tip says.
- The brief's Bangla for the MA roster link says সরাসরি ভর্তি, per the copy guide.

### WS6a Report (agent B, round 3)

**Done (P0):** `/staff/students` (search; create with the name, phone and email dialog, then the one-time code dialog; status; reissue for pending accounts; delete for Master Admin with the name typed to confirm), `/staff/students/[id]` (name edit; status; access list from `GET /students/{id}/live-exam-access` with the reasons সরাসরি ভর্তি / মডিউল কেনা / পরীক্ষা কেনা; links to the module, to `/staff/payments/{tranId}`, and for Master Admin to `/staff/modules/{id}?tab=roster`), `/staff/instructors` (Master Admin; search; status and assigned filters in the URL; module list or "কোনো মডিউল নেই"; create; reissue for pending), `/staff/admins` (Master Admin; paged; create without a password; status; reissue; delete with the name typed), `/staff/participation` (all staff; module, type, from/to, sort, text filters in the URL; summary numbers; MCQ rows show "—" in the written-only columns; written counts link to the module's scripts filtered to the exam), `/staff/submissions` (landing: written exams grouped by module with the awaiting and draft counts), `/staff/submissions/m/[moduleId]` (counts above; filters all / answer / failed / draft / published; exam filter; name search on the loaded rows), `/staff/submissions/[participantId]` (the per-role marking screen: instructor and Master Admin read the answer in `PdfViewer` with download, save a draft with marks, comment and the required marked PDF through `apiUpload`; Master Admin publishes behind a confirm, disabled until a draft exists; System Admin sees read-only facts including the examiner's name; no answer means the no-answer line and no form).

**P1 done:** marking queue on the paper page: "আগের" and "পরের" in the module's list order, "সেভ করে পরেরটি" (saves, then opens the next paper with an answer and no draft), J and K when focus is outside a field, and the progress line "{total}টির মধ্যে {marked}টি মূল্যায়িত". Side-by-side on desktop (answer viewer left, form right; stacked on phones).

**P2 skipped:** none in scope. Not built: a payments-style summary strip on the participation page.

**Checks:** `npm run lint` 0 errors (2 existing `<img>` warnings). `npm run typecheck` (`tsc --noEmit`) clean. `npm test` 201 passed in 26 files (includes the WS6a tests in `tests/ws6a/**`). `npm run build` under the lock: **the default Turbopack build fails** inside Next's Google Font loader for `layout.tsx` ("next/font/google queries have exactly one entry", 21 errors). This is not in any WS6a file, and the font declarations and layout are unchanged on this branch. I did not confirm whether the base branch fails the same way. `npx next build --webpack` **passes** on this branch, and every staff route is generated. Turbopack is the default in Next 16, so this needs an owner to look at it before the deploy build.

**Contract issues:**
- `GET /live-exam-participants/{id}` (the `SubmissionView`) returns no comment, so a reopened paper cannot show the saved comment. The form starts with an empty comment box. Needs the comment in the view, or a note that it is write-only.
- The submission view has no exam title or module id. The paper page reads them from the module's written participation rows (`?m=` in the link); a paper opened without `?m=` shows the student's name as the heading and no exam name.
- The instructor who is not assigned to a module gets an error state on its submission list, from the server's refusal. There is no instructor-only participation read beyond the server's filter.

**Notes for other agents:**
- Routes: `/staff/students`, `/staff/students/[id]`, `/staff/instructors`, `/staff/admins`, `/staff/participation`, `/staff/submissions`, `/staff/submissions/m/[moduleId]`, `/staff/submissions/[participantId]`. Links out: `/staff/payments/{tranId}` (WS6b), `/staff/modules/{id}` and `/staff/modules/{id}?tab=roster` (WS5), `/staff/exams/{id}` (WS5).
- The people hooks live in `lib/api/exam/staff-people.ts`, with their own `staffPeople` query-key prefix. Shared helpers were copied rather than imported across workstreams.
- Every route page wraps its content in `<Suspense>`, as the WS4 tip says.
