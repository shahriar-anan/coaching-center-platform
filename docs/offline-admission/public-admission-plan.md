# Public offline admission: students apply from the batch cards

Plan written 13 November 2026, from Shahriar's brief of the same day. Nothing is built yet. Each stage waits for a go.

## 0. Build log

Each stage adds what was built, where it differs from this plan, and the test results.

P17 applied: batch default items removed; V95 drops offline_batch_items.

### Web side (stages 2, 4, 6, and the change requests P13 to P16)

- **Stage 2, batch catalogue:** staff batch form gained the English and Bangla descriptions, the photo (preview, Remove, Undo; sent on
  Save), "Admissions open" switch, display order and the "Given at onboarding" picker (TargetPicker). Batch Overview shows the photo and
  the descriptions. Public: `/admission` cards (`BatchCard`), `/admission/batches/{id}` (routine PDF via the existing PdfViewer). The landing
  "Admission" item goes to `/admission` while the offline switch is on, `/register` when off. Dashboard "Offline activities" tile below the
  door cards. The student form moved from `/admission` to `/admission/form` (the gate and print back link follow).
- **Stage 4, applying:** `/admission/apply/{batchId}`: the batch from the URL, "Change batch". Signed-in: `POST /api/v1/me/admission/apply`,
  then the form on the newest admission routes. Visitor: `POST /api/v1/public/offline-applications` (started by the first complete email),
  `PUT .../{token}/form`, `PUT .../{token}/photo`, `POST .../{token}/submit`; token in sessionStorage per batch. Confirmation panel. Reopen
  `/admission/reopen/{linkToken}`. Staff queue: Source column and filter (`source` query). Detail: "Matches the account of ..." or "A new account
  will be made" before Onboard.
- **Stage 6, joining and activation:** onboard dialog says which email gets which code (7 days). After onboarding "Code sent on {date}" and
  "Send a new code" (`POST .../resend-code`). `/admission/join` (`POST /api/v1/me/admission/join`). `/activate` takes email or phone.
  My admission lists one section per batch (per-admission routes `/api/v1/me/admissions/{id}/...`) when there are several.
- **P13:** desk start refuses a phone with an open admission (409 `OPEN_ADMISSION`), naming the batch; no start offered.
- **P14:** onboard dialog line "{student} already has access to {batches}." from `alreadyAccessTo`.
- **P15:** `/offline-activities` replaces the My admission page. A student sees My admission first (one section per batch), then the
  published batches not already in. With no admission: the batch cards. Visitors and staff: the cards. `/my-admission` redirects there
  (`redirects()` in `next.config.ts`, temporary). Links to the page updated (tile, account row, menu, join, sent panel).
- **P16:** not done by the web side. The two seed batches need a staff API call (not SQL) after the API build; nothing was created here.

API the web code assumes (not in the contract as written; please confirm):
- `AdmissionView.batchId`, and `batchNameEn/Bn` on every state (the tile and the list name the batch while under review).
- Public batch view: `photoUrl` (an API path served without sign-in), `descriptionEn/Bn`, `admissionsOpen`, `routineUploaded`.
- Public application: start body `{batchId, email}` returns `{token, admission}`; reopen returns the same shape.
- Public and staff batch: `PUT|DELETE .../photo` (no items route; P17).
- Admission detail: `source`, `matchedAccount {id, fullName, email}`, `alreadyAccessTo [{nameEn, nameBn}]`, `codeKind`, `codeSentAt`, `codeExpiresAt`.
- Desk refusal: 409 code `OPEN_ADMISSION` with `details {batchNameEn, batchNameBn}`.
- Gap: no GET for a public application by token, so a reload of the visitor form starts blank (the token is kept, the draft is not).

Known limits: the My admission sections keep their DOM ids, so several batches on one page repeat `#my-admission-*` anchors (the glance
strip jumps to the first). "Email confirmed" in the queue (section 6) is not built: P10 dropped the email step.

Tests: full web suite, 299 files. Last full run: 1998 passed, 1 failed (`course-management` "opens a material in a new browser tab", which
passes when run alone; a timing flake under load). tsc --noEmit clean. ESLint: no errors on changed files (two existing `<img>` warnings).
Failures fixed on the way: admission gate and redirect expectations (paths moved), a Bangla batchPay key-order and phone-key placement
(i18n catalogue alignment test), a dashboard crash on a non-array answer (tile now guards it), router-mock issues in finance and queue tests,
a finance range test that assumed a two-year cap while the enforced limit is 3653 days (the API comment says two years; please confirm).

- **Stage 1, batch catalogue (API), 13 Nov.** V93: description (2,000 max), photo columns, `admissions_open`, `display_order`,
  `offline_batch_items` (one row per default exam or package, unique per batch). Batch photo PUT/DELETE (same processing as the profile
  photo, 5 MB), items PUT (published exams and packages only, replaced as a whole). Public: `GET /api/v1/public/offline-batches`,
  `.../{id}`, `.../{id}/photo`, `.../{id}/routine.pdf` (the live student copy). Differences: the public list shows every non-archived
  batch, including those with admissions closed (the card says "Admissions closed"); the batch view returns the items sorted (exams
  first). Null fields are left out of JSON, as elsewhere in the API. Tests: `OfflineBatchCatalogueIT` (6). The switch filter and the
  OpenAPI list cover the new routes.
- **Stages 3 and 5, applying and onboarding (API), 13 Nov.** V94: `student_id` and `created_by` nullable, `source`, the access token,
  the join code (hash, expiry, sent at and by, used at, failures), the reopen link (hash, expiry), `matched_user_id`; the one-open rule is
  per student and batch (plus one per student while there is no batch); one open public application per email and batch; the
  `ADMISSION_JOIN_CODE` and `ADMISSION_BATCH_ADDED` notice kinds. Routes: the public application (start, form, photo, submit, reopen), the
  signed-in `GET /me/admissions`, `POST /me/admission/apply`, `POST /me/admission/join`, the per-admission items, routine, batch routine and
  fees under `/me/admissions/{id}/`, and `POST /offline-admissions/{id}/resend-code`. Onboarding: a public applicant with no match gets a
  pending account and the activation code by mail (7 days), and is onboarded at once; a match or a signed-in applicant gets a join code
  by mail and a notice, and stays Ready until they join. Activation takes the email or the phone. The gate lets a student through while they
  have an onboarded admission. Differences: (1) the join code's wrong-try counter is a column (`join_code_failures`), since rule 9 needs
  it; (2) the default exams and packages are copied when the batch is set in the setup, not at onboarding, because the ready check needs
  at least one item; (3) the public application token is kept as a hash, and a repeat start for the same email rotates it, as the plan
  says it returns that application; (4) the rate limit on application starts is not built (see the report); (5) the staff queue shows
  the source and the matched account from SQL, and the detail carries the match. P13: the desk refuses a phone with an open admission and
  names the batch; an existing account with no open admission keeps the duplicate-phone refusal it had. P14: onboarding a student who
  already has a batch is allowed; the onboard detail carries `alreadyAccessTo` (the batch names), the student gets an
  `ADMISSION_BATCH_ADDED` notice, and the student email lists every batch they now have. Tests: `OfflineApplicationIT` (8),
  `OfflineOnboardingIT` (8, covering the desk refusal and the second batch), the offline group, `AuditLogIT` and the auth tests pass.
- **Full API suite, fresh `coaching_it_public`, 13 Nov (after stages 1, 3 and 5):** 211 classes, 1208 tests, 0 failures, 0 errors.
  Log: `api/var/full-suite-public.log`.

## 1. What changes

Today a student only reaches the admission form after staff start an admission for them (the desk, by phone number), and the staff
choose the batch during setup. The client wants it the other way round:

- The offline batches are shown to everyone, as cards, the way packages are shown.
- A student picks a batch card, presses **Apply**, and fills in the admission form. That batch is the one the form is for.
- Staff or the Master Admin review the form. The Master Admin onboards.
- Onboarding emails a code to the address on the form. A new student uses it to activate their account. A student who already has an
  account uses it to join the batch from a link.
- After that the student has **My admission** and the normal online sections.

Routines, classes, fees, instructor pay and finance stay as they are.

### 1.1 Decisions (Shahriar, 13 November 2026)

| # | Decision |
|---|---|
| P1 | One batch per application. The batch comes from the card the student pressed Apply on; the form does not offer a batch list. |
| P2 | Batches are shown as cards on the **Admission** page (landing menu) and in an **Offline activities** tile on the student dashboard, below the door cards. |
| P3 | Anyone can apply, signed in or not. The account of a new student is made at onboarding, not before. |
| P4 | A student who is signed in when applying still confirms with the emailed code at the end. Their form is prefilled from the account. |
| P5 | **Superseded by P17.** |
| P6 | A batch has a title, an optional description, an optional photo, and its routine. The card also shows the monthly fee, the start and end dates, and whether admissions are open (a switch on the batch). No seat limit for now. |
| P7 | The desk flow (staff start an admission from a phone number, for a walk-in) stays as a second way in. |
| P8 | The emailed code lasts 7 days. The admin can send a new one. |
| P10 | No email confirmation when applying (decided 13 Nov, after the first draft). The applicant types their email and sends the form. |
| P11 | A student can be in several online batches at once: one open admission per batch, each with its own fees, dues and access. |
| P12 | The reopen link ("changes requested") is needed. Emails use the same look and wording style as the existing templates in `EmailTemplates`. |
| P13 | The desk (walk-in, phone number) is for new students only. A phone number that already has an open offline admission is refused at the desk, with the name of the batch they are in. An existing account with no open offline admission is admitted as before. |
| P14 | A student already running in one batch can be onboarded to a second batch (from their application, by the Master Admin). The onboard dialog and the student email say so: "{student} already has access to {batch}", and the student email lists the batches they now have. |
| P15 | My admission lives inside **Offline activities** (`/offline-activities`). A student with no offline admission sees only the published batches. A student with one or more offline admissions sees My admission (one section per batch they are in) above the published batches. |
| P16 | After the build, two seed batches are created (title, description, photo optional, fee, dates, default exams or packages, routine optional) so the Admission cards and Offline activities show something to try. They are created through the staff API, not by SQL. |
| P17 | A batch is offline and carries no exams or packages. Exams and packages are chosen per student, in the admission setup, when the Master Admin onboards them, as today. The batch form has no exam or package picker, the batch has no default items, and onboarding copies nothing from the batch. The batch table `offline_batch_items` is dropped. |
| P18 | Exams and packages are optional when an admission is marked ready. The ready check no longer asks for one; the setup still offers them per student. |
| P9 | The dashboard tile shows a student's own batch with a link to My admission, and the other batches on offer. |

### 1.2 Choices made while planning (say if any is wrong)

| # | Choice | Why |
|---|---|---|
| Q1 | **Dropped (P10): no email confirmation at submission.** | Decided by Shahriar. The onboarding code is the check that the email works. |
| Q2 | **Several open admissions at once (P11): one per batch.** A second application for the same batch while one is open opens the existing one. | Shahriar wants this. The data is already one row per admission; the single-admission lookups and screens are changed to work per batch (section 5). |
| Q3 | **An applicant who is not signed in is matched to an existing account by email, then by phone**, when the Master Admin onboards. A match joins that account (the join code); no match makes a new account (the activation code). The review screen shows the match before onboarding. | The same person must not end up with two accounts. |
| Q4 | **"Changes requested" for an applicant who is not signed in** sends an email with a private link that reopens their form. The link lasts 7 days. | They have no account to sign in to. |
| Q5 | **Activation accepts the email or the phone with the code.** | The code arrives by email, so the student will type the email. |
| Q6 | The public admission pages show while the offline feature switch is on (`app.offline-admission.enabled` and `NEXT_PUBLIC_OFFLINE_ADMISSION`), like everything offline. With it off, the landing menu item goes back to the register page. | One switch for the whole offline segment. |

## 2. The student journeys

### A. A new student (no account)

1. Landing page, menu **Admission** (`/admission`): the batch cards. A card shows the photo (or a plain header), the title, the start and
   end dates, the monthly fee, and **Apply** (or "Admissions closed" when the switch is off). Tapping the card opens the batch page
   (`/admission/batches/{id}`): the description, the dates, the fee, the routine (the student copy, no instructor names), and Apply.
2. **Apply** opens the form for that batch (`/admission/apply/{batchId}`). The batch shows at the top, with "Change batch" back to the
   cards. The form is the current admission form (name, parents, date of birth, email, mobile, university, address, photo and the rest).
3. **Send**. A confirmation page: "Your application for {batch} was sent. We will email {email} when it is reviewed." A copy goes to the
   email too.
4. The office reviews it (section 6). If they ask for changes, the student gets an email with the reason and a link back to the form.
5. **Onboarded**: an email "You are admitted to {batch}" with the activation code, valid 7 days, and a link to `/activate`. The student
   enters the email (or phone), the code, and a password twice. They are signed in and see My admission and the online sections.

### B. A student with an account who is signed in

1. The same cards, from the landing menu or the **Offline activities** tile on the dashboard.
2. Apply opens the form, prefilled from the account (name, email, phone, photo). The email is the account's and is already confirmed,
   so there is no email step.
3. Send. The application shows on their dashboard tile as "Under review".
4. Onboarded: an email with the join code and a link, and a notification with the same link (`/admission/join`). The page asks for the
   code. With it, the student is in the batch and My admission opens.

### C. A student with an account who applied without signing in

The form's email (or phone) matches an account at onboarding (Q3). The student gets the join email and, when they next sign in, the
notification. Same as B from step 4.

### D. A walk-in at the desk

Unchanged: staff start an admission from a phone number, the student activates with the code the desk reads out, fills in the form, and
the office reviews and onboards. The only difference is that the batch can now be chosen at the desk from the same list of batches.

## 3. The batch catalogue

### 3.1 What a batch holds (new fields)

| Field | Notes |
|---|---|
| Description, English and Bangla | Optional. Plain paragraphs (no rich text), up to 2,000 characters. |
| Photo | Optional. JPEG, PNG or WebP, up to 5 MB, re-encoded and resized on the server like the profile photo. Shown 16:9 on the card. |
| Admissions open | On or off. Off shows "Admissions closed" on the card and refuses new applications; open applications carry on. |
| Display order | So the office can put the main batch first. |

The fee, the start and end dates and the routine already exist. An archived batch is never shown.

### 3.2 Cards

The cards reuse the package card layout: photo on top, title, a line with the dates, the fee as "৳{fee} a month", and the action. On a
phone one card per row, two from tablet width, three on desktop. Empty state: "No offline batches are open right now."

## 4. Data (migrations V93 onward)

- **V93 batches:** `description_en`, `description_bn`, photo columns (stored name, type, size, uploaded by and at), `admissions_open`
  (default true), `display_order`. No items table: a batch carries no exams or packages (P17).
- **V94 admissions:** `student_id` becomes nullable (an application from someone not signed in has no account until onboarding). New:
  `source` (`PUBLIC`, `ACCOUNT`, `DESK`), the join code (hash,
  expiry, sent at, used at), the reopen link token (hash, expiry), `matched_user_id` (the account found at review, Q3).
  `offline_admission_submissions.submitted_by` becomes nullable for the same reason.
- The one-open-admission unique index is replaced (P11): one open admission per student **per batch** (`student_id, batch_id` where
  status is not CLOSED). For applications without an account, one open application per email per batch.

## 5. API

Public (no sign-in, feature switch on, rate limited per IP and per email):

- `GET /api/v1/public/offline-batches` and `GET /api/v1/public/offline-batches/{id}`: the cards and the batch page. Open batches first.
- `GET /api/v1/public/offline-batches/{id}/routine.pdf`: the student copy of the live routine (no instructor names).
- `POST /api/v1/public/offline-applications`: starts an application for a batch with the form's email (no email code, P10). Returns an
  application token kept in the browser so the form can be saved as a draft.
- `PUT .../{token}/form`, `PUT .../{token}/photo`, `POST .../{token}/submit`.
- `GET /api/v1/public/offline-applications/reopen/{linkToken}`: the form again after "changes requested" (Q4).

Signed-in students:

- `GET /api/v1/me/admissions`: every admission of the student (one per batch), newest first.
- `POST /api/v1/me/admission/apply` with the batch: starts their application, prefilled from the account (B). Refused only for the
  same batch while an application is open (P11).
- The fees, items, routine and batch-routine PDF routes take the admission id (`/api/v1/me/admissions/{id}/...`). The current
  `/api/v1/me/admission/...` routes keep answering the newest admission, so existing screens still work.
- `POST /api/v1/me/admission/join` with the code: joins the onboarded admission (B, C).

Staff:

- The batch form takes the new fields; `PUT/DELETE /api/v1/offline-batches/{id}/photo`. No items route (P17).
- The admission queue gains the source and, for applications without an account, the matched account (Q3).
- Onboarding (Master Admin) does one of: make the account and email the activation code; or email the join code (and notify). It copies
  nothing from the batch: exams and packages are chosen per student in the setup (P17).
- `POST /api/v1/offline-admissions/{id}/resend-code`: a new code, the old one stops working (P8).

Auth: `POST /api/v1/auth/activate` accepts `email` or `phone` with the code (Q5).

## 6. Staff screens

- **Batches:** the form gains the descriptions, the photo (with a preview and Remove), "Admissions open", and the order. There is no exam or
  package picker (P17). The batch Overview shows the photo and the description.
- **Admissions queue:** a "Source" column (Website, Account, Desk) and a filter. A new application shows "Email confirmed".
- **Admission detail:** the batch is already chosen. The setup starts from the batch's defaults. For an application without an account
  it shows "Matches the account of {name} ({email})" or "A new account will be made", before Onboard.
- **Onboard dialog:** says which email gets which code, and that it lasts 7 days. After onboarding: "Code sent on {date}" and "Send a new
  code".

## 7. Emails and notifications

| When | To | What |
|---|---|---|
| Application sent | the form's email | "We received your application for {batch}." |
| Changes requested | the form's email | The reason and the reopen link (Q4). |
| Onboarded, new account | the form's email | "You are admitted to {batch}." The activation code, 7 days, and the link to `/activate`. |
| Onboarded, existing account | the account's email, and a notification | The join code, 7 days, and the link to `/admission/join`. |
| Code resent | the same | The new code; the old one is no longer valid. |

All emails are built with the existing `EmailTemplates` helpers (the same frame, title and note layout, with Bangla first and English under it, like the activation and fee emails). Both languages, natural Bangla.

## 8. Student screens

- **Landing menu "Admission"** goes to `/admission` (the cards) while the offline switch is on.
- **`/admission`** (public): the cards. **`/admission/batches/{id}`**: the batch page. **`/admission/apply/{batchId}`**: the form with the
  email step. Then the confirmation page.
- **Offline activities page (`/offline-activities`, P15):** the one place for a student's offline life. Without an offline admission it shows
  the published batches as cards (the same cards as `/admission`, with Apply). With one or more admissions it shows My admission first (one
  section per batch: state, next payment, exams and packages, routine, receipts, the form), then the published batches the student is not
  already in. The old `/my-admission` address redirects here.
- **Dashboard tile "Offline activities"**, below the door cards, links to `/offline-activities`: a single card. With an onboarded admission: "Your batch: {name}" and
  "Open My admission". With an application under review: "{batch}: under review". Then "Batches on offer" (up to three small rows) and
  "See all", to `/admission`.
- **`/admission/join`**: the code field and "Join the batch". Then My admission.
- **`/activate`**: email or phone, the code, the password twice.
- The admission gate (the student who must finish the form) keeps working for desk admissions (D).

## 9. Rules and edge cases

- An application for a batch whose admissions are closed, or archived, is refused with a clear message.
- A student may hold several open admissions, one per batch (P11). Each has its own fees and access; the My admission page lists them.
- The same email applying again to the same batch while an application is open opens that application instead of making a new one.
- An email that belongs to a staff account cannot be used to apply.
- A code is single use, 5 wrong tries lock it, and a new one replaces it.
- A rejected or closed application can be sent again later as a new one.
- Rate limits: application starts per IP per hour (the existing mail limiter applies to the emails they send).

## 10. Stages

1. **Batch catalogue (API):** V93, the new batch fields, the photo, the default items, the public batch list and page and the routine
   copy. Tests.
2. **Batch catalogue (web):** the staff batch form and Overview; the public cards and batch page; the landing menu; the dashboard tile.
3. **Applying (API):** V94, the public application (no email code), the signed-in apply, several admissions per student (P11), the reopen
   link, the queue's source and match. Tests.
4. **Applying (web):** the apply form with the email step and the confirmation; the staff queue and detail changes.
5. **Onboarding and joining (API):** the account or join code at onboarding, resend, activation by email, the join route, the emails
   and the notification. Tests, including a full run of the API suite.
6. **Onboarding and joining (web):** the onboard dialog, `/admission/join`, `/activate`, the tile states, both languages.
7. **Walkthrough and release notes:** the three journeys (A, B, C) and the desk (D) on the sample database, at 360 px and desktop.

## 11. Open points

- **Email confirmation:** dropped for now (P10). It can be added later with the same mail limiter.
- **SMS:** if the client wants phone codes later, it needs an SMS provider (cost per message). Not part of this plan.
- **Seats per batch:** left out (P6). Easy to add to the card and the apply check if the client asks.
