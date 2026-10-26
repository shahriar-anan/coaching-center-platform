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

- Server clock controls the live window. Take exam during the window uses the shared end.
- Inside one module, the later live window starts at least five minutes after the earlier end. Another module may use the same clock time. A null `liveAt` is not a window and is left out of that check. Publishing an exam that has a start runs the check against every other published exam in the module that has a start. Setting or clearing `liveAt` returns the exam to draft, so the new time is live only after the next publish accepts it.
- An MCQ has one later attempt, and only when the student never tapped Take exam during the window. It uses a personal timer and stays off the live leaderboard. A student who sat the live MCQ cannot start it. A written exam has no later attempt. `POST /api/v1/live-exams/{id}/retake` rejects a written exam.
- A published exam may have a null `liveAt`. That row stays on the module exam list with a blank date and time. Take exam, the later MCQ attempt, the personal routine, and the leaderboard stay closed until `liveAt` is set and the server clock is inside the window. `durationMinutes` stays required.
- A wrong MCQ option adds nothing. There is no negative-mark field until the client asks for one.
- MCQ answers can change until the deadline. The deadline posts the saved answers without a student submit call. A blank paper scores zero. The score is returned as soon as the paper posts. The chosen option, the correct option, and the explanation wait until the sitting ends.
- The MCQ leaderboard opens five minutes after the live window, live participants only, highest score first, ties share a rank.
- A written answer is one PDF, `%PDF` checked, 80 MB ceiling, compressed, latest file only. No stored PDF at the deadline is failed to submit and scores zero. That no-submit zero stays on the live board. It is not an instructor mark.
- MCQ questions stay stems and options. The written question stays one PDF.
- One instructor per module. A System Admin does not evaluate and does not publish.

## Written results

- An instructor save stores marks, an optional comment, and the evaluated PDF as a draft.
- A save by the Master Admin is also a draft. Publish is a separate action.
- Only the Master Admin publishes. The instructor cannot publish. A System Admin cannot publish.
- Until publish, the student does not see the marks, the comment, or the evaluated PDF, and that row stays off the leaderboard.
- A later save returns the result to draft until the Master Admin publishes again.
- The student still does not see who evaluated the paper.

## Accounts and notices

- Email and phone registration stay, with no SMS OTP.
- Google sign-in is for students. It links to an existing student account when Google reports that email as verified. It does not link or sign in an admin or instructor. A new Google user sets a phone once, with no OTP. A password is optional for a Google-only account.
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
