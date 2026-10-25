# WS6 — Staff people, participation, marking, payments, audit

Branch `ui/ws6-staff-ops`, cut after WS0 merges. Read [README.md](README.md) and [ws0-foundation.md](ws0-foundation.md). Pages 4 and 17–29 of [../../ui/admin-prompt.md](../../ui/admin-prompt.md) are the spec, with its server rules for accounts, participation, marking and payments. Differences are listed here. The old course-admin student pages under `(admin)` show which student calls exist; build new pages, do not edit those.

## Owns

- `web/src/app/(staff)/staff/students/**`, `.../instructors/**`, `.../admins/**`, `.../participation/**`, `.../submissions/**`, `.../payments/**`, `.../audit/**`, `.../account/**`, `.../exams/[examId]/leaderboard/**`
- `web/src/components/staff/people/**`, `web/src/components/staff/ops/**`
- `web/src/lib/api/exam/staff-people.ts`, `web/src/lib/api/exam/staff-ops.ts`
- `web/src/i18n/messages/{bn,en}/{staffPeople,staffOps}.ts`
- Tests under `web/tests/ws6/**`

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

## Rules

- Lists are server-paged with page, search and filters in the URL; the submission list is the one that filters loaded rows on the client, because the server sends a module's rows together.
- Activation codes appear once in `OneTimeCodeDialog` and are never stored in the query cache or logs.
- Destructive actions confirm and name the person or object; deleting a person asks to type the name.
- Status words, not colors alone. Times in Asia/Dhaka. Money as ৳ with two decimals.

## Tests

Student create code dialog, delete MA only, access list reasons, instructor filters, participation filters and "—" for MCQ, submission form per role (instructor no publish, SA read-only, MA publish disabled until a draft), payments filters and read-only SA, audit paging, staff account phone change.

## Report

(Agent fills this in.)
