# Onboarding review fixes (13 Nov 2026)

A code review of the two onboarding flows (desk and online) found the issues below. For each issue the plan gives why it happens, what
the fix is and why it is right, and the test that proves it. The fixes are made in one pass. Testing uses **new, empty databases**; the
sample database is not touched.

## Decisions taken with Shahriar (13 Nov)

| # | Question | Decision |
|---|---|---|
| D1 | Which admissions can be closed? | **Any state.** A reason is required. Before onboarding there are no grants or dues to undo; a live join code stops working. |
| D2 | The same email starts an application for a batch it already applied to | **A resume link is emailed** (7 days). Nothing private is shown on screen and the old browser token keeps working. |
| D3 | Account matching for a public applicant | **Email only.** A phone that belongs to another account is shown to staff as a warning, and onboarding is refused until the form is fixed. |
| D4 | Staff action to put an existing student into a batch | **Not now.** Existing students apply online while signed in. Kept as a later item. |

Rules that follow from the plan and need no new decision:

- R1. The admission lock (the "gate") is for **desk** admissions only (offline-admission plan section 8). An online application never locks
  a student out of exams, checkout or the site.
- R2. A join code is tied to the setup the Master Admin approved. When the admission leaves READY, the code is cleared.
- R3. A notice or email that says a batch was added is sent when the batch is really added (the join), not when the code is mailed.

## 1. Onboarding flow

### F1. Signed-in Apply and Join return the wrong record (High)
- **Why:** `POST /me/admission/apply` and `/join` return `AdmissionSummary`; the web expects `AdmissionView` and puts it in the
  admission cache. The form then reads `admission.form` from a record that has none.
- **Fix:** both routes return `AdmissionView` (`form.view(admission)`). The summary record is kept only where it is still used.
- **Test:** `OfflineApplicationIT` apply/join assert `form`, `hasPhoto` and `state` in the body.

### F2. Re-opening the apply page as a signed-in student shows "could not start" (High)
- **Why:** a second Apply for the same batch is refused with 409, but the page expects the open application back (refresh in the middle
  of filling the form).
- **Fix:** Apply returns the student's own open admission for that batch (200) instead of refusing. It is their own data, so this is safe.
- **Test:** applying twice returns the same id, and no second row.

### F3. A join-pending admission is stuck on the staff detail (High)
- **Why:** after the code is sent, the admission stays READY with `joinCodeSentAt`. The detail shows Onboard (refused by the API) and no
  Send a new code button (it shows only for ONBOARDED).
- **Fix:** the API detail gets `joinPending` and `canResendCode` (the exact rules `resendCode` uses). The web hides Onboard while
  `joinPending`, shows "Waiting for the student to join" with the code date, and shows Send a new code whenever `canResendCode`.
- **Test:** `OfflineOnboardingIT` checks the flags before and after onboarding and after the join; web tests for the detail.

### F4. Lock on online applicants (High)
- **Why:** the API gate refuses exams and checkout for any pending admission, and the web gate looks at the newest admission only. A
  student who bought exams online and applies for a batch, or an onboarded student applying for a second batch, is locked out.
- **Fix (R1):** the API gate counts only pending `DESK` admissions. `AdmissionView` gets `source`, and the web gate treats an admission
  as pending only when it is a desk admission.
- **Test:** `OfflineAdmissionGateIT`: a signed-in applicant can still check out; a desk student is still refused. Web gate unit tests.

### F5. Admissions can be closed only after onboarding (High) — D1
- **Why:** `close()` accepts ONBOARDED only. Spam, abandoned, and "already has this batch" applications stay forever, and the matched-batch
  refusal tells staff to close it.
- **Fix:** close works from every state but CLOSED. Before onboarding: no dues or grants exist, the join code and reopen link are cleared,
  and the student (if there is an account) gets the closed notice; a public applicant with no account gets no mail. The web shows Close
  for every open state.
- **Test:** close from SUBMITTED, from READY with a join code (the code then fails), and from ONBOARDED as before.

### F6. An old join code still works after the setup changes (High) — R2
- **Why:** send back and back to review leave the join code in place; `join()` accepts any READY admission with a code.
- **Fix:** clear the code hash, expiry and sent time when a READY admission is sent back, moved back to review, or closed.
- **Test:** onboard → back to review → ready → the old code is refused; Onboard works again.

### F7. Anyone can open an application by typing its email (High, privacy) — D2
- **Why:** `start` returns a fresh token and the full form for an open application with that email and batch, and kills the real token.
- **Fix:** `start` answers 409 `APPLICATION_EXISTS` and emails a resume link to that address (valid 7 days, same reopen mechanism). The
  existing token is not changed. The web shows "You already applied for this batch. We emailed you a link to continue."
  The resume link opens the form in any open form state (AWAITING_FORM or CHANGES_REQUESTED).
- **Test:** second start gets 409 with no form in the body; the old token still saves; the resume link opens the form.

### F8. Account matching by phone (Medium) — D3
- **Why:** a typed phone can link a stranger's account, which then sees the form and gets the dues.
- **Fix:** match by email only. When the form's phone belongs to another account, the detail shows a warning ("This phone belongs to X")
  and onboarding is refused with a clear message until the form is fixed. The queue's matched name uses the email only.
- **Test:** phone-only match → onboarding refused, no link; email match → join code.

### F9. The applicant's photo is lost at onboarding (Medium)
- **Why:** the photo stays on the application; once `studentId` is set the photo is looked up on the account only.
- **Fix:** when onboarding links or makes an account, the application photo is copied to the account if it has none. Photo lookups fall
  back to the application photo when the account has none.
- **Test:** onboard a public applicant → the detail still has the photo; a new account has a profile photo.

### F10. Form routes and the read route point at different admissions (Medium)
- **Why:** `GET /me/admission` takes the newest open admission; the form routes take the newest one whose form is open.
- **Fix:** `current()` uses the same rule as the form routes (form-open first, then newest open, then newest closed).
- **Test:** two admissions, the older sent back → `GET /me/admission` returns the sent-back one.

### F11. Join does not re-check the setup (Medium)
- **Why:** the batch may be archived or an item bought between the code and the join; the join then fails with a staff message.
- **Fix:** the join re-runs the ready checks and the item check, and answers a student message ("This admission needs the office to
  look at it again. Contact the office.") with code ADMISSION_STATE; the code is not used up.
- **Test:** archive the batch after the code is sent → join refused with the student message.

### F12. Wrong activation codes are never counted (Medium)
- **Why:** the failed try is written and then the transaction rolls back with the refusal.
- **Fix:** `activate` does not roll back on its own refusal (`noRollbackFor = ApiException`); nothing else is written before it.
  Activating with an emailed code also marks the email as verified.
- **Test:** five wrong codes lock the code (counted in the database).

### F13. Smaller flow issues (Low)
- Wrong join codes are counted on every waiting admission of the student, not only the newest.
- A setup that moves the admission into a batch the student already has open answers a clear 409, not a 500.
- The desk "new code" refuses non-desk admissions and admissions with no account (instead of a 500 or a 7-day text on a 10-minute code).
- The desk start refusal for a phone with an open admission answers 409 `OPEN_ADMISSION` with the batch names in `details`, which the web
  already reads.

## 2. Student notices and emails

### N1. Every offline notice shows blank (High)
- **Fix:** the notices page knows every offline kind: ADMISSION_APPROVED, ADMISSION_CHANGES_REQUESTED, ADMISSION_UPDATED, ADMISSION_CLOSED,
  ADMISSION_JOIN_CODE, ADMISSION_BATCH_ADDED, BATCH_ROUTINE_UPDATED and the OFFLINE_FEE kinds, with natural Bangla and English labels and a
  link (Offline activities, the join page, or the form).
- **Test:** web notice-kind tests for label key and link.

### N2. New-account students (B) get no approval notice or email (High)
- **Why:** onboarding runs while the account is pending; notices and mails go to active accounts only.
- **Fix:** when the account is activated, an onboarded admission whose approval was never written gets its notice and email then (same
  dedupe key, so never twice).
- **Test:** onboard B → no notice → activate → one notice and one mail row.

### N3. No fee emails for desk (A) and new-account (B) students (High)
- **Fix:** fee receipts and reminders use the account email whether or not it is verified (rule C6, as every other admission mail does).
- **Test:** `OfflineFeeIT` receipt mail row exists for an unverified email.

### N4. Copy-code link in the join email opens the activation page (Medium)
- **Fix:** a `join` purpose on the code page goes to `/admission/join#code=`; the activation email uses `activate`. The join page reads
  the code from the link.
- **Test:** email template test for the link; web code-card test.

### N5. "Changes requested" email for account holders opens the batch cards (Medium)
- **Fix:** the button goes to `/admission/form`.

### N6. "Batch added" notice sent before the join (Medium) — R3
- **Fix:** it is sent at the join, after the batch is added.

### N7. Signed-in applicants get no "application received" email (Low)
- **Fix:** the first submit of an ACCOUNT application sends it to the account email.

### N8. Copy fixes (Low)
- "বাচ" → "ব্যাচ". The received email's button: "ব্যাচগুলো দেখুন / View the batches". The approval email drops the empty exams row and
  says "your admission is approved" without "the exams below" when there are none.

## 3. Admin and student screens

| # | Fix |
|---|---|
| W1 | `alreadyAccessTo` is sent as `{nameEn, nameBn}` pairs, so the Onboard warning shows. |
| W2 | The Onboard dialog has its own line for desk admissions (no code; the student is in at once). |
| W3 | The match line uses the linked account once the code is sent ("Linked to the account of …"). |
| W4 | Clear refusals show the API's message instead of "someone else changed this". |
| W5 | Send a new code follows `canResendCode` (F3). |
| W6 | Field names: staff batch `photoUploaded`, public batch `routineAvailable` (the web types follow the API). |
| W7 | The student's pending admission shows its batch name; a waiting join code shows "Enter your join code" with a link. |
| W8 | `codeKind` and `codeExpiresAt` are removed from the web type (the API never sends them). |
| W9 | Close is offered for every open state (F5), with the same reason dialog. |

## 4. Later (not in this pass)
- D4: a staff action to add an existing student to a batch.
- The old public test batches ("New test", "Test batch").

## 5. Testing
1. API integration tests run against a new database made for this pass (`coaching_it_review`), never the sample database.
2. Updated and new tests: `OfflineApplicationIT`, `OfflineOnboardingIT`, `OfflineAdminIT`, `OfflineAdmissionGateIT`, `OfflineFeeIT`,
   `ActivationIT`, and the mail template tests. Then the whole offline and auth test set.
3. Web: `tsc`, the staff offline admission, public admission, my-admission, gate and notices tests.
4. A new dev database (`coaching_center_review`) migrated from empty, used for a browser pass over both flows.

## 6. Build log

Done on 13 Nov, in the order of the plan. Nothing committed.

**API (compiles; offline and activation suites pass on the new database `coaching_it_review`)**
- F1 Apply and Join return the full admission view. F2 Apply returns the student's open admission for the batch.
- F3 detail carries `joinPending` and `canResendCode`. F4 gate locks only DESK admissions. F5 close works from any open state.
- F6 send-back, back-to-review and close clear the join code (`OfflineAdmissionAdminService.clearJoinCode`).
- F7 a repeat application by email answers 409 `APPLICATION_EXISTS`, mails a resume link (5-minute gap), and never returns the form.
- F8/D3 matching is by email only; a phone owned by another account refuses onboarding with a message on `mobile`.
- F9 the application photo is copied to the account (`StudentPhotoService.adoptIfMissing`).
- F10 the read route and the form routes pick the same admission.
- F11 join re-checks the setup and answers a student message (ADMISSION_STATE); the code is not used up.
- F12 `activate` does not roll back a counted wrong code (`noRollbackFor`), and an emailed code verifies the email.
- F13 wrong-code tries count on every waiting admission; desk regenerate refused for online admissions; desk refusal is `OPEN_ADMISSION` with names in `details`.
- N2 the approval notice and mail go out on activation (`OfflineApplicationService`/`OfflineAdmissionOnboardingService.onActivated`).
- N3 fee receipts and reminders go to any account email, verified or not (C6); `OfflineFeeIT` passes.
- N4 join-code copy link goes to `/admission/join` (purpose `join`); N5 changes-requested button goes to `/admission/form`.
- N6 batch-added notice is written at the join. N7 signed-in applicants get the received mail.
- N8 "বাচ" fixed; received button "ব্যাচগুলো দেখুন"; the empty exams row is left out.

**Tests changed to the corrected behaviour**
- `OfflineApplicationIT`: repeat email is refused with `APPLICATION_EXISTS` and the first token still works; a signed-in re-apply returns the existing admission.
- `OfflineOnboardingIT`: phone-only match refused (D3); email match joins; batch-added notice at join (R3); `alreadyAccessTo` pairs.
- `OfflineMyAdmissionIT`: the batch name is shown before onboarding; fee and dates stay hidden.
- New in `OfflineOnboardingIT`: join-pending flags and code cleared on back-to-review (F3, F6); close of a waiting application (F5); approval on activation (N2).
- New `OfflineAdmissionGateTest`: only a desk admission locks (F4).

**Web (type-check clean, lint clean, full suite 300 files / 2,029 tests pass)**
- Types: `photoUploaded` (staff batch), `routineAvailable` (public batch), removed `codeKind`/`codeExpiresAt`, added `matchedLinked`, `phoneOwnerName`, `joinPending`, `canResendCode`; student view gains `source`, `joinCodePending`.
- Actions: Onboard hidden while a join code waits and disabled for a phone owned by another account; Close in every pre-onboarding state; phone warning; desk onboard says no code is sent.
- Notices: the offline kinds have labels in English and Bangla and links; an exam-less row no longer repeats its label.
- Code card: the join purpose opens `/admission/join#code=`.
- Errors: clear refusals show "This cannot be done right now", not "someone else changed it".
- New unit test `tests/staff-offline-admission/offline-error.test.ts`.

**Not done or not proven in this pass**
- No browser check on a fresh dev database (`coaching_center_review`). The browser pass is still to do.
- No full API suite run (you asked to hold full runs). The earlier unknown failures `CategoryBranchExamsIT` and `PaymentVerifyIT` are still uninvestigated.
- Without a test: F9 photo copy, F11 join re-check, F13 desk refusal details (the existing suites pass, but nothing asserts the new code), N4 code card.
- Existing public test batches ("New test", "Test batch") still open; not decided.
- Not done, as decided: D4 (add an existing student to a batch).
- The public apply's "already applied" message comes from the API's English text; a Bangla key is not added yet.
- The notices page still files offline kinds under the Exams tab (API `NoticeGroup`).

## Full browser check of the admission flows (15 Nov)
Run on coaching_center_review with three Playwright flows (desk 23/23, public 33/33, matched account 35/35), screens reviewed.

Covered: desk walk-in (code, activation by phone, gate, form, setup, ready, duplicate refusal, onboard, roll, WhatsApp, notice); public
applicant (batch card, form, confirmation, same email after and before sending, resume link, send back, reopen and resend, onboard with a
new account, activation by email); matched account (join code, Send a new code, old code refused, join); phone-owner warning; account
already holds the batch; close before onboarding; signed-in student applying from a card; the batch Students tab roll.

Fixed during the check:
- The queue search missed a desk admission by phone before its form was filled; it now also matches the account's phone.
- A desk admission's form now starts with the desk phone (01...), so the student does not type it twice.
- The same email applying again showed only "could not save"; it now says it already applied, with "Apply with another email".
- A resume link is mailed only while the form can still be changed (a sent form's link opened "cannot be changed").
- Reopen and resume links are logged in dev (as "Message link"), so these flows can be tested without real mail.
- A reopened form showed the mobile as +880...; it shows 01..., as the hint asks.
- Close: the reason is required (it said optional and the API refused an empty one); before onboarding the dialog speaks of the
  application, not access and dues.
- "Already has access to this batch" shows from the review on, not only at Ready.
- The status line no longer says "You can onboard" while a join code is out or a clash blocks onboarding.
- (Reverted the same day: pinning dialog footers to the bottom undid the earlier decision in fb17c23; the buttons stay at the end of the form.)

Tests: the seven admission IT classes 87/87 on a fresh database (OfflineApplicationIT gains the "no link for a sent form" check); web
suite 2143/2143; tsc clean.
