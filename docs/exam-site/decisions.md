# Decisions

Shahriar agreed these on 2026-10-23. They override the client brief and the Phase 3 scope where those disagree.

## Product

- The student exam client is the existing Next.js app in `web/`. The Flutter app is out of this delivery.
- Students can buy one exam or the whole module. A module purchase covers exams published later in that module. An exam purchase covers that exam only.
- The published catalog is public. No login and no roster row are required to read it. Questions and PDFs stay off that read.
- The module page is the exam list. The routine is that same list: the exam title, and the date and time from `liveAt`. A null `liveAt` means the date and time are not announced yet. There is no separate routine document. Status pills on that list are a later website concern. Prices are the purchase step. The public access payload remains the field list in `plans/02-public-catalog.md`, with `liveAt` and `endAt` null when the date is unannounced.
- A Master Admin roster mark remains a way to grant access. Take exam accepts a roster grant or a purchase grant.
- The website language switch stays Bangla and English for shop copy and chrome. On the question page, the timer, save, submit, marks labels, title, description, and rules follow that switch. The question body is the stored stems, options, or written PDF. `paperLanguage` is `BN` or `EN` and selects the typeface for that body. The site language does not replace the body, so the paper may differ from the site language.
- The watermark is drawn by the website over the PDF viewer, using the student's name and id. The API keeps serving the PDF inline, with no public URL.
- Admin and instructor screens stay basic. They come after a student can sit an exam the API already holds.

## Exam rules that stay
 **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- Server clock controls the live window. Take exam during the window uses the shared end.
- An exam lasts at least 2 minutes (`durationMinutes` is 2 or more on create and update).
- Publishing an exam needs the English and Bangla descriptions and rules. An exam can be saved without them, but it cannot be published until they are filled in (decided by the client, 2026-10-26).
- Only the Master Admin publishes an exam. A System Admin edits the exam but cannot publish it (decided 2026-10-26). **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- Inside one module, the later live window starts at least five minutes after the earlier end. Another module may use the same clock time. A null `liveAt` is not a window and is left out of that check. Publishing an exam that has a start runs the check against every other published exam in the module that has a start. Setting or clearing `liveAt` returns the exam to draft, so the new time is live only after the next publish accepts it. **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- An MCQ has one later attempt, and only when the student never tapped Take exam during the window. It uses a personal timer and stays off the live leaderboard. A student who sat the live MCQ cannot start it. A written exam has no later attempt. `POST /api/v1/live-exams/{id}/retake` rejects a written exam.
- A published exam may have a null `liveAt`. That row stays on the module exam list with a blank date and time. Take exam, the later MCQ attempt, the personal routine, and the leaderboard stay closed until `liveAt` is set and the server clock is inside the window. `durationMinutes` stays required. **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- A wrong MCQ option adds nothing. There is no negative-mark field until the client asks for one. **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- MCQ answers can change until the deadline. The deadline posts the saved answers without a student submit call. A blank paper scores zero. The score is returned as soon as the paper posts. The chosen option, the correct option, and the explanation wait until the sitting ends. **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- The MCQ leaderboard opens five minutes after the live window, live participants only, highest score first, ties share a rank. **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- A written answer is one PDF, `%PDF` checked, 80 MB ceiling, compressed, latest file only. No stored PDF at the deadline is failed to submit and scores zero. That no-submit zero stays on the live board. It is not an instructor mark.
- MCQ questions stay stems and options. The written question stays one PDF. **Superseded by NR-01 (28 October 2026); see the NR-01 section below.
- One instructor per module. A System Admin does not evaluate and does not publish.

## Written results

- An instructor save stores marks, an optional comment, and the evaluated PDF as a draft. Shahriar, 2026-10-27: the marked PDF is optional on a save when the paper already has one. A save without a file keeps the stored PDF. The first save needs a PDF.
- Reads never post a sitting. The deadline job posts due sittings every minute. Shahriar, 2026-10-27.
- An assigned instructor reads the question paper only after the exam's window has ended, and only for a published exam. Shahriar, 2026-10-27.
- The marked PDF the student sees carries no watermark (Shahriar, 2026-10-27). This is a web change, not an API one.
- A save by the Master Admin is also a draft. Publish is a separate action.
- Only the Master Admin publishes. The instructor cannot publish. A System Admin cannot publish.
- Until publish, the student does not see the marks, the comment, or the evaluated PDF, and that row stays off the leaderboard.
- A later save to a released result does not hide it. The student keeps the released mark and PDF. The new mark and PDF wait as a correction until the Master Admin releases them. Replaces the earlier rule that a save returned the result to draft. Shahriar, 2026-10-27.
- An instructor can mark a paper "Ready for release". The flag is a signal and not a gate: Master Admin can release a paper without it. Releasing only the ready papers skips the completeness gate, because the marker has said they are finished. A later save clears the flag. The instructor never releases. Shahriar, 2026-10-27 (recommended approach, Moodle marking workflow).
- On a phone (below 768 px) the marking page opens on the script, with the mark in a second tab and the current mark shown as a chip. Desktop stays side by side. Shahriar, 2026-10-27.
- A notice goes to the student on each release that changes the mark they see, including a correction. A save sends nothing. Shahriar, 2026-10-27.
- MCQ results have no release state. They show "Auto-scored", as the decisions of 2026-10-23 say the score is returned when the paper posts. Shahriar, 2026-10-27. Conflict noted: the client brief (4.6) says MCQ results appear after the admin publishes.
- Master Admin releases an exam's results together with one action. The release is refused while a paper is still awaiting a mark or a sitting is still open (`409 EXAM_MARKING_INCOMPLETE`, with the counts). A failed-to-submit zero needs no mark and does not block the release. Per-paper release stays for single cases. Shahriar, 2026-10-27.
- The student still does not see who evaluated the paper.

## Accounts and notices

- Email and phone registration stay, with no SMS OTP.
- Google sign-in is for students. It links to an existing student account when Google reports that email as verified. It does not link or sign in an admin or instructor. A new Google user sets a phone once, with no OTP. A password is optional for a Google-only account.
- A suspended account loses access on its next request, including a token it already holds. Suspending also ends its refresh sessions. Shahriar, 2026-10-27: a suspended instructor is blocked at once, not after the 15-minute token lifetime.
- Notices are rows the website loads. No email, SMS, or push for these notices.
- A reminder goes to students who already have access, 1–2 days before the live window.
- An MCQ notice is sent when that student's score posts. A written notice is sent when the Master Admin publishes.

## Payments and the server

- Exam checkout is SSLCommerz. Credentials stay in environment variables. Never write them into code, docs, or Git.
- Course and book payments, including the existing manual bKash/Nagad verification, are out of this delivery. Do not change those rows, screens, or credentials, and do not bend them into the exam orders. Exam checkout is SSLCommerz only.
- The exam site runs on a dedicated Hostinger KVM 2. Shared-VPS rules are outdated. Shahriar bought the box and it was provisioned on 23 October 2026. SSH alias `exam-kvm`, IPv4 `187.52.115.111`, hostname `srv2089893.hstgr.cloud`. The API is running there. The record is [reports/new-vps-provision-audit.md](reports/new-vps-provision-audit.md). `symamlaw.com` DNS still points at the old VPS. Sandbox payment testing ran on the Hostinger hostname on 25 October 2026, so it no longer waits for a `symamlaw.com` name. The read-only investigation of the old VPS is in [Server migration](#server-migration). It is not feature work in this repository.
- Password recovery stays the existing email reset, plus Google. Do not add an admin action that sets a student's password. The reset and the registration verification mail use the SMTP account below.

## Server migration

Shahriar added this on 2026-10-23 so another agent can investigate the current server. This section is the handoff. It does not choose a callback hostname, and it does not choose sandbox or live SSLCommerz credentials.

- The client keeps the existing domain. It is registered at Namecheap. The domain name is not recorded in this repo. Read it from the current server. Do not invent one.
- That domain is already served by Nginx on the current Hostinger VPS, with SSL certificates for the existing website.
- The current VPS expires on 5 November 2026. Shahriar has SSH access to it.
- The replacement Hostinger KVM 2 is bought and provisioned. SSH alias `exam-kvm`, IPv4 `187.52.115.111`, hostname `srv2089893.hstgr.cloud`. Ubuntu 24.04, PostgreSQL on localhost, Java 21, Nginx, and a Let's Encrypt certificate for that Hostinger hostname. Since 25 October 2026 the current API and the Next.js site run there on the `staging` profile, at `https://srv2089893.hstgr.cloud` ([reports/vps-staging-deploy.md](reports/vps-staging-deploy.md)). Do not point public DNS at it until Shahriar says so.
- The new server hosts the newly developed website and the Spring Boot API. The Flutter app uses that same API. The exam-site delivery in this folder is still the website. Flutter screens stay out of that delivery.
- SSLCommerz needs a public HTTPS URL for its callback and IPN. For the client test it is `https://srv2089893.hstgr.cloud` with the sandbox store, and sandbox payments with an IPN passed there on 25 October 2026. The launch hostname and the switch to the live store stay open. On 25 October 2026 Shahriar said the old site keeps `symamlaw.com`, so the exam site's final address is not decided.
- Outgoing mail for the new API is Brevo SMTP on the authenticated `symamlaw.com` domain. Host `smtp-relay.brevo.com`, port `587`, STARTTLS required. From `SYMAM LAW <no-reply@symamlaw.com>`. `MAIL_REPLY_TO` is optional. The SMTP username and the SMTP key stay in `MAIL_USERNAME` and `MAIL_PASSWORD` (or `BREVO_API_KEY` when `MAIL_PASSWORD` is unset). Do not copy them into Git. The earlier Gmail SMTP choice is withdrawn. Do not put those Gmail credentials on the new VPS. The old site's Laravel queue stays on the old VPS. In-app notices stay rows the website loads. They are not email. The admin mail-test endpoint and `MAIL_TEST_ENABLED` were removed on 25 October 2026. A delivery check is a normal register and forgot-password.

The investigation below is finished. The reports are `domain-ssl-migration-audit.md`, `domain-dns-access-audit.md`, and `reports/current-vps-mail-audit.md`. The "do not purchase" line is the rule for that investigation, not a rule for later work.

1. Read the current VPS and report how the domain, DNS, Nginx, and SSL are set up, and what has to move to the new KVM 2.
2. List any information or access still needed from the client, including the Namecheap DNS account if the current server does not show where the records are managed.
3. Do not change the current server. Do not change DNS. Do not change SSL. Do not purchase the KVM 2. Do not write feature code.
4. Stop after the report and wait for Shahriar.

## Master Admin at delivery

Read this before handing the server to the client. The local test values in `api/.env` are not the client's account.

`MASTER_ADMIN_PHONE`, `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_NAME`, and `MASTER_ADMIN_PASSWORD` are read only when the database has no Master Admin. The startup then creates that one account and does not read them again. Changing the four values and restarting leaves the existing account as it is.

The new server already has one Master Admin, created at first boot on 23 October 2026. Editing those variables on that server does nothing.

Do not type a new password into `users.password_hash`. That column stores a hash. A plain password written there cannot log in. Do not update the phone, email, or name with a hand-written SQL statement as the handover. The phone check accepts only `+8801` and nine more digits. The display name is `admin_profiles.full_name`, not a column on `users`.

After the account exists, the signed-in Master Admin can change two things:

- The password, through change password. The current password is required. No other account can set it, and the API refuses a reset of this account.
- The phone, through `POST /api/v1/auth/change-phone` with the current password. `POST /api/v1/me/phone` only fills an empty phone and refuses this account.

The email and the display name stay as they were at creation. The API does not change them, and it refuses a delete or a role change of the Master Admin.

Use one of these at handover:

1. Deliver the database that already has the Master Admin. Shahriar signs in with the password from first boot and changes the password. He changes the phone the same way if the client wants a different number. The email and the name remain the ones from first boot, so those two must already be acceptable to the client.
2. Deliver a database that has no Master Admin. Put the client's phone, email, name, and password in the server environment before the first start. Start once, confirm the account exists, then remove `MASTER_ADMIN_PASSWORD` from the environment. Do not commit it.

## Landing page and the client mockup

Shahriar decided this on 26 October 2026. The client's student-flow mockup (`ui/client-mockup.html`) says to keep a 760 px column and not to become a split marketing page. His later request says the landing page should look like his old site. The two are settled like this:

- **The old site governs the landing page only.** `/`, for anyone who is not a signed-in student, is the wide page from `plans/ui-delivery/landing-page-plan.md`.
- **The mockup governs every other student page.** Catalog, module, exam, sitting, results, notices and account stay in the 760 px column, with the mockup's colours, cards and type. A signed-in student's home is the dashboard in that column.
- **The mockup's entrance sits in the landing hero.** The two door cards (✅ এমসিকিউ · দ্রুত অনুশীলন, ✍️ রিটেন · লিখিত পরীক্ষা) are the first action. "অ্যাকাউন্ট খুলুন" is the main button under them.
- **The logo is the school's real SYMAM mark with the mockup's "এক্সামপোর্টাল" wordmark.** The mockup's ⚖️ emoji was a placeholder.
- Both sources share one palette (cream, gold, navy), the same fonts and the same card style, so the landing page and the app read as one site.

Shahriar confirms this split with the client in the next meeting. If the client wants the mockup everywhere, the landing sections move into the 760 px column. They already stack in that width on a phone.

## Still open

- The exam site's launch address. The old site keeps `symamlaw.com` (Shahriar, 25 October 2026). Then `WEB_ORIGIN`, `SSLCZ_CALLBACK_BASE_URL`, DNS, a certificate, and the live store's registered URL follow.
- When to switch from the sandbox store to the live store. The client confirms bKash and Nagad are enabled on it.
- Whether payments SSLCommerz marks risky (`risk_level`) are granted, held, or flagged. The API grants them today. Shahriar is leaving it as it is for now.
- Whether any data from the client's current exam must be migrated. Do not invent a migration.

Status pills were confirmed on 25 October 2026 and built as A4 (`myStatus`). The sandbox store was chosen for the client test.

## Exam final save (decided 2026-10-27)

- The exam editor has no separate Final save. Publish records the final save itself, then publishes, so the API rule (a final save before publish) still holds. The header keeps Save all changes for drafts.
- Modules keep their Final save button, as before.
- The API is unchanged: `POST /live-exams/{id}/final-save` still exists and is called by the Publish action.

## Exam archive (decided 2026-10-27)

- An archived exam stays off the student website. Master Admin only (`EXAM_DELETE`). Staff still see it with the Archived status, and its posted results stay readable, the same rule the category archive uses.
- An archived exam cannot be changed or published (`409 EXAM_ARCHIVED`).

## Staff roles (decided 2026-10-26)

These came from the admin panel audit (`reports/ui-audit/fix-plan-C.md`, section 6). They override the earlier rows in `ui/feature-scope.md` where those differ.

- Only the Master Admin confirms an exam order paid by hand. The System Admin sees the pending list read-only.
- Only the Master Admin publishes an exam, and only the Master Admin publishes written marks.
- The System Admin does not grant access by hand, does not release MCQ answers, and does not send announcements. Roster grants stay with the Master Admin.
- The System Admin suspends and restores students. Instructor status stays with the Master Admin.
- An instructor does not open a module or exam page. The question paper is read-only inside the evaluation screen, with no download. The Master Admin publishes the result.

## NR-01: the exam segment (decided 28 October 2026)

Shahriar agreed these after the client approved the design. The full record, with the question each answers, is `new-requirements/NR-01-exam-segment.md`. They replace the lines marked **Superseded by NR-01** above. The API is built (plan `new-requirements/nr-01/`); the web changes come in a later plan.

- **Structure.** An exam belongs to a category. A package (the site's word for a module) bundles exams; one exam may be in several packages or in none. The instructor is set on the exam, and a package holds a default that new exams inherit.
- **Live window and duration are separate.** The window must be at least as long as the duration. A sitting ends at its own start plus the duration, with no cap at the window end. A first attempt started inside the window is live; after it, archived. The kind is fixed at start. A practice exam has no window and its leaderboard uses each student's first attempt.
- **Attempts.** MCQ retakes are unlimited. A written exam has one retake, granted by the Master Admin or a System Admin from the student's result, with an optional reason and an audit entry. A retake is marked and released like any paper, sits beside the first result, and is never on the leaderboard.
- **MCQ scoring.** An exam may hold questions with several correct options. A question scores only on an exact match. A wrong, non-empty selection loses the exam's negative mark; a blank scores 0; a total can go below zero. Each exam chooses whether answers can be changed; when not, a single-answer question is final at its first choice and a multi-answer question when the student confirms it.
- **Results at submit.** An MCQ student sees the score, the correct options and the explanations as soon as the paper is submitted, with a position: the current board position for a live attempt, or the rank the score would have taken for an archived attempt or retake. The client accepts that this lets a student pass the key to others. Written results are still released by the Master Admin.
- **Leaderboard.** Live attempts only, at or above the pass mark, ties sharing the better rank. During the live window students see only their own position. A score below the pass mark shows "below the pass mark" and no rank.
- **Editing at any time.** There is no lock, because the client's windows run twelve to sixteen hours and a fix may be needed during one. Editing a published exam or package is staged: students keep the published version until the Master Admin reads the field-by-field change list and publishes it. Publishing re-scores submitted MCQ papers when the answer key, marks or scoring changed. Questions and options cannot be added or removed after the first student has started. The written question PDF may be replaced; a student and their evaluator always see the paper that student sat.
- **Price and access.** Percent discounts with an optional end date, on exams and packages. A free exam can be sat by any signed-in student. Access is set per exam as a number of days counted from the window end (or from the grant if that is later), or for life. Expiry stops new attempts; results stay readable. Prices, discounts and the free setting are Master Admin only.
- **Withdrawing.** An archived (withdrawn) package gives no access through it. A student who has the exam another way keeps it. Removing an exam from a package keeps it for anyone who held the package before.
- **Also decided.** Popular exams and packages (Master Admin) and student favourites; a preview of the questions after the window (or any time for practice) when an exam allows it; no coupons; old-site data is not imported; "Module" is renamed "Package" on screens only, and the "Archive" button becomes "Withdraw".

## Student page Phase 4 (decided 29 October 2026)

Shahriar's answers, recorded in `new-requirements/student-page/PHASE-4-PLAN.md` section 2. The API changes are in `current-api.md`, "Added 2026-10-29 (Student page Phase 4)".

- **Payment history keeps its path** (`/account/payments`), restyled, and is linked from the student menu, the account page, My exams, the package and exam pages, the purchase result, and the purchase-granted notice. The full link table is plan section 6, S4.6.
- **Notice tabs** are Results, Exams and Announcements. `PURCHASE_GRANTED` sits under Exams. A kind that is not mapped (including one added later) sits under Exams. The mapping is one place in the API (`NoticeGroup`).
- **Receipts.** A receipt number (`RCPT-<year>-<6 digits>`) and a PDF for each paid order. The PDF is English only, because PDFBox cannot draw Bangla. The web print view stays Bangla. The paid time is `paidAt` on each order; old paid orders carry an approximate time (their last update).
- **No refunds and no refund states.** The payment history shows paid, awaiting payment, failed and cancelled only.
- **Exam cover.** An exam has its own optional cover, uploaded by the Master Admin like a package cover. An exam without a cover shows its first package's cover, then the placeholder.
- **Category counts** count the whole branch on a parent card and the card's own exams on a leaf (the same value on a leaf). No API change.
- **Filters.** A segmented control (All, MCQ, Written) under the existing tabs on My exams (exam tab only) and Results. Bangla for every label is a draft until the client has seen it.
