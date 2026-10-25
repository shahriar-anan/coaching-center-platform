# Auth and mail

Written 24 October 2026. Revised the same day after a second pass against the code. Slices 1–9 were implemented on 24 October 2026. Refund stays unstarted until the refund rules in this file are confirmed.

Confirming this plan accepts four product messages as email: a payment receipt, an exam-time mail, a one-hour reminder, and a later refund mail. That confirmation does not edit `decisions.md`. The three inbox events stay off email.

This plan does not change [../decisions.md](../decisions.md). In-app notices stay rows the website loads. The three inbox events in [../steps/05-notifications.md](../steps/05-notifications.md) stay off email. The one-hour exam mail below is a separate message Shahriar asked for on 24 October 2026. It does not replace the one-to-two-day inbox reminder.

Course and book payments stay out. Do not add mail to the manual bKash or Nagad screens.

Package for mail: `com.coachingcenter.api.auth` for account mail, `com.coachingcenter.api.exams` for exam mail. Exam mail is Flyway `V34__exam_outbound_mail.sql`. Do not edit `V1`–`V34`. The `REFUND` kind is already allowed on that table. Slice 10 does not need a new migration to store the row.

Tests are `*IT` classes against real PostgreSQL. They use a fake sender. They do not call Brevo. Do not use H2.

## 1. What the system does now

Slices 1–9 are in the code. The paragraphs that used to say the website has no register page, that activation is not emailed, that `DEV_EXPOSE_EMAIL_CODES` is unread, and that no job selects upcoming exams are the 24 October gap list. Do not follow them.

### Accounts

| Call | What it does | Mail |
|---|---|---|
| `POST /api/v1/auth/register` | Creates an `ACTIVE` student with a password and a phone. Login works before the email is verified. | Issues an `EMAIL_VERIFY` code and asks the sender to deliver it. A failed send does not roll back the account. |
| `POST /api/v1/auth/verify-email` | Marks `email_verified_at` when the code is valid. | None. |
| `POST /api/v1/auth/verify-email/resend` | Issues a new code when the address exists and is still unverified. An unknown address and an already verified address look the same. | Sends only when a new code is issued. |
| `POST /api/v1/auth/login` | Email or phone, plus password. Requires `ACTIVE` and a password hash. A Google-only account has no password and cannot use this call. | None. |
| `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` | Rotates or revokes the refresh token. The website cookie is httpOnly, `Secure`, `SameSite=Strict`, path `/api/v1/auth`. | None. |
| `POST /api/v1/auth/forgot-password` | Always returns the same sentence. | Sends a `PASSWORD_RESET` code only for an `ACTIVE` user who is not the Master Admin. Pending, inactive, unknown, and Master Admin get no mail. |
| `POST /api/v1/auth/reset-password` | Takes the code and the new password. Revokes that user's refresh tokens. A Master Admin code is refused and is not consumed. | One plain-text password-changed note, with no code, after success. A failure sends nothing. |
| `POST /api/v1/auth/change-password` | Requires the current password. | The same password-changed note after success. A failure sends nothing. |
| `POST /api/v1/auth/activate` | Pending student, system admin, or instructor sets a password from the one-time code. | None at this call. The code was emailed when it was issued. |
| `POST /api/v1/students`, and the same shape for system admins and instructors | Name, phone, email. No password. Status `PENDING_ACTIVATION`. | Emails the activation code. The admin JSON still returns it once. A failed send does not roll back create. |
| `POST /api/v1/students/{id}/activation-code`, `POST /api/v1/admins/{id}/activation-code`, `POST /api/v1/instructors/{id}/activation-code` | Reissue. Instructor reissue requires `ADMIN_MANAGE`. Only `PENDING_ACTIVATION` can be issued a code. Active or inactive is `409`. A missing, deleted, or wrong-role id is `404`. | Emails the new code. The previous unused code is consumed. |
| `GET /api/v1/auth/google` and the callback | Student only. A verified Google email links or creates the student. A new student has no phone until `POST /api/v1/me/phone`. | None. |
| `GET /api/v1/me` | Id, phone, email, `emailVerifiedAt`, role, status, name, student code, `phoneRequired`. A student may change their own display name. Email stays as stored. | None. |

The website has `/login`, `/register`, `/forgot-password`, `/reset-password`, `/activate`, and `/account`. A student who signs in on those paths lands on `/account`. Staff still land on `/students`. `/account` shows whether `emailVerifiedAt` is set, resends verification when it is not, and changes the password. The Google button and the set-phone page stay in step 6.

### How mail is chosen

| Profile | Sender | Starts without a Brevo key |
|---|---|---|
| `dev` | `LoggingEmailSender` | Yes. It never opens SMTP. A key in `api/.env` does not change that. With `DEV_EXPOSE_EMAIL_CODES=true` it also logs the verification, reset, and activation codes. The HTTP body stays unchanged. The password-changed line is `Password changed message queued` and has no code. |
| `staging`, `prod` | `SmtpEmailSender` for account mail, `SmtpExamMailSender` for exam mail | No. Blank `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` (or `BREVO_API_KEY` when the password is unset), or `MAIL_FROM_ADDRESS` aborts startup. The expose flag cannot print a code on these profiles. |

Account messages on staging and prod are plain text: the verification code, the reset code with the path `/reset-password`, the activation code with the path `/activate`, and the password-changed note. From `SYMAM LAW <no-reply@symamlaw.com>` when those variables are set. Host `smtp-relay.brevo.com`, port `587`, STARTTLS. The public code endpoints are limited to 3 sends per address per hour, 10 per IP per hour, and 60 seconds between sends. Activation mail and the password-changed note do not count against that limiter. The dev profile raises the limits and sets the cooldown to zero.

`DevExposeEmailCodesIT` keeps passing: staging with the flag on does not put a code in the response or the log.

The admin delivery check (`MAIL_TEST_ENABLED`, `POST /api/v1/admin/mail-test`) was removed on 25 October 2026. Section 4 is when the real Brevo values go on the server.

The VPS audited on 24 October 2026 is on `prod` and reads `EMAIL_HOST`, not `MAIL_*`. That jar is not this build.

### Exams and money

A succeeded SSLCommerz validation creates a `live_exam_grants` row for one exam or one module, then enqueues one payment receipt after that transaction commits. Statuses on `live_exam_orders` are `INITIATED`, `SUCCEEDED`, `FAILED`, and `CANCELLED`. A succeeded order is not moved to cancelled. There is still no `REFUNDED` status, no SSLCommerz refund call, and no grant revocation. Slice 10 waits on the refund rules in section 2.

A roster mark is a separate grant. It does not create an order. A new roster row on a published dated exam enqueues exam-time mail.

`liveAt` is optional. Null means the date is not announced and no exam mail is sent. Setting or clearing it returns the exam to draft. The server clock owns the window. `@EnableScheduling` is on. The payment recheck stays every 10 minutes. `ExamMailService.scan` runs every 5 minutes and sends the one-hour mail inside the window in section 2.

Exam mail rows live in `exam_outbound_mail`. The row stores kind, key, user id, attempt count, and status. It does not store a code or a body. On `dev`, exam mail logs `Exam message queued` and nothing else. Step 5, the website inbox, is still not built.

## 2. Rules in force

Slices 1–9 follow this section. Slice 10 does not.

### Account mail

1. Keep verification and reset on the staging and prod sender. The dev sender stays silent unless the expose flag below is on.
2. The expose path lives only in `LoggingEmailSender`, which is a `dev` profile bean. When `DEV_EXPOSE_EMAIL_CODES=true`, that bean logs the verification code, the reset code, and the activation code. The HTTP body stays unchanged. Staging and prod create `SmtpEmailSender` instead, so the flag cannot print a code there. `DevExposeEmailCodesIT` runs staging with the flag on and must keep passing. The `.env.example` comment matches this.
3. Forgot-password sends a code only for an `ACTIVE` user who is not the Master Admin. The Master Admin, a `PENDING_ACTIVATION` account, an `INACTIVE` account, and an unknown address all get the same public sentence and no mail. Reset of a code that belongs to the Master Admin is refused. That account changes its password only with the current password. A pending account uses the activation code, which lasts 7 days. A reset code lasts 10 minutes.
4. After a successful reset, and after a successful change-password, send one plain-text note that the password changed. The note contains no code. A failed attempt sends nothing.
5. Every call that issues an activation code also sends that code. The admin JSON still returns the code once. A failed send does not fail the create or the reissue. Dev logs the code only when the expose flag is on. A deleted account is not mailed. `PENDING_ACTIVATION` is the only status that can be issued a code.

   Add `POST /api/v1/instructors/{id}/activation-code`. It matches the student and system-admin reissue calls. `ADMIN_MANAGE` is required, so only the Master Admin can call it. A system admin receives 403. A missing id, a deleted user, or any role other than `INSTRUCTOR` receives 404. The previous unused activation code is consumed, the new code is returned once, and the mail is sent.

   The same pending check applies to the student and system-admin reissue routes. An `ACTIVE` or `INACTIVE` account receives 409. Reissue is not a way for an admin to set another person's password. The UI already shows the action on pending rows only.

A send that fails, including verification and reset, does not roll back the account change. The public auth limiter stays on the public code endpoints only. Activation mail and the password-changed note do not count against it.

Google sign-in sends no mail. Linking and creating a Google student already sets `emailVerifiedAt`. Do not send a verification code for that.

Codes are 8 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. Verification and reset last 10 minutes. Activation lasts 7 days. Five wrong attempts use up a code. The reset call takes the code and the new password. It does not take the email. The mail contains the code and the path `/reset-password`. It does not put the code in a URL. The activation mail names `/activate` and says the phone is required there too. The forgot-password label is “কোড পাঠান”. `docs/exam-site/ui/student-flow-prompts.md` matches that.

### Exam mail

These messages go only to an `ACTIVE` student who still has that access at the moment of sending. An unverified email still receives them. An instructor, a system admin, and the Master Admin do not. The body is plain text, UTF-8, Bangla first and English second, because the user row has no language preference. It names that student’s own exam or module, using the stored Bangla and English titles. It does not name another student, a phone number, a card, a store id, a store password, or a `val_id`. Times in the body are Asia/Dhaka. The database stays UTC.

The public auth limiter does not apply. A batch for one exam must not be counted as ten requests from one IP. Each message is one row, inserted once, with a unique key. The row stores the kind, the key, the user id, the attempt count, and a status of pending, sent, skipped, or failed. It does not store a code or a rendered body. The body is built from the current rows at send time. Access is checked again immediately before SMTP. Lost access marks the row skipped and sends nothing. A failed attempt retries that same row, up to 3 attempts, then the row stays failed. A later run does not insert a second row. SMTP failure does not roll back the order, the grant, or the publish. The job is single-flight and takes a `Clock`, so tests do not sleep and do not call Brevo. One API process owns the schedule. Do not run a second prod process that also sends.

| Message | When | Unique key | Not sent when |
|---|---|---|---|
| Payment receipt | The order becomes `SUCCEEDED` and the grant is stored. The body names the exam or the module, the amount, `BDT`, and the `tranId`. | order id | The order is `FAILED`, `CANCELLED`, or still `INITIATED`. A second validation of the same order. |
| Exam time | A published exam in a published module has a non-null `liveAt`, and the student has a roster row for that module, an exam grant, or a module grant. Fires on publish and again when access is added later. | student, exam, that `liveAt` | `liveAt` is null. The exam or the module is not published. The student has no grant. The same key already exists. |
| One hour before start | No `HOUR_BEFORE` row exists for that student, exam, and `liveAt`, the exam and module are still published, and the server clock is after `liveAt` minus 70 minutes and before `liveAt` minus 15 minutes. The first choice is the band from 70 minutes out to 50 minutes out. A process that was down still sends once in the catch-up band, from 50 minutes out until 15 minutes out. | student, exam, that `liveAt`, kind `HOUR_BEFORE` | `liveAt` is null, already passed, or inside the last 15 minutes. The exam or module is not published. The student has no grant. |
| Refund | A later refund slice moves a succeeded order to refunded and removes the grant this order created. | order id, kind `REFUND` | No refund action exists yet. Do not send this mail from cancel or from a failed payment. |

The exam-time mail is the notice that a date exists. Changing `liveAt` returns the exam to draft, so the next publish is a new instant and a new mail. A student who is rostered or who buys after publish gets that same mail once, because access added later uses the same key. A module purchase can therefore send the receipt and one exam-time mail per dated published exam in that module. Two grants for the same student and exam still produce one mail. An exam published inside the last hour sends the exam-time mail immediately and the one-hour mail as well when more than 15 minutes remain. Inside the last 15 minutes only the exam-time mail is sent.

Unpaid cancel and failed payment stay on the result page. They do not send mail.

### Refund is a product gap, not a template

A refund mail with no refund action would describe something the API cannot do. The refund slice waits for a separate confirmation of all of these:

- Who may refund. The proposal is Master Admin only.
- The order must be `SUCCEEDED`. A full refund only, through the SSLCommerz refund call, for the amount and currency stored on the order.
- The grant created by that order is removed. A module refund removes that module grant. An exam the student already sat stays sat. The plan does not delete a submitted paper.
- The public status becomes refunded. The receipt mail is not rewritten.

Until that confirmation, slices stop before the refund. Cancel of an unpaid checkout stays as it is.

### Left on the inbox, not on email

Step 5 still owns these, as website rows, when that step is built:

- One reminder, one to two days before the window, for a student who already has access.
- That student’s MCQ score, when the score posts.
- That student’s written result, when the Master Admin publishes it.

Marks and the evaluated PDF stay off email. A one-hour mail that only says the exam is about to start does not include a score.

### Website

Slice 9 is in the website. Shahriar’s browser pass is still open. The Google button and the set-phone page are not in this slice.

| Route | Calls |
|---|---|
| `/register` | `POST /api/v1/auth/register`. Success tells the student they can sign in. It does not trap them on a verify wall. |
| `/forgot-password` | `POST /api/v1/auth/forgot-password`. The confirmation sentence is always the same. |
| `/reset-password` | The code from the mail and the new password. `POST /api/v1/auth/reset-password` takes those two fields, then login. |
| `/activate` | Already present. It keeps posting the code and the new password. |
| Account | Resend verification. Show whether `emailVerifiedAt` is set. Change password posts the current password and the new one. |

Copy goes through `web/src/i18n`. Automated checks are `npm run lint`, `npm run build`, and component tests with mocked `fetch`. Do not boot the API for those tests. Shahriar does the browser pass.

## 3. Slice order

1. **Dev codes.** Done. `DEV_EXPOSE_EMAIL_CODES=true` on `dev` logs the verification, reset, and activation codes. Staging and prod do not.
2. **Who receives a reset.** Done. Forgot-password sends only for an `ACTIVE` user who is not the Master Admin.
3. **Password-changed note.** Done. Sent after reset and after change-password. The note has no code.
4. **Activation mail and instructor reissue.** Done. `POST /api/v1/instructors/{id}/activation-code` is Master Admin only and only for a pending instructor. No Flyway change.
5. **Outbound table and sender port.** Done. Flyway `V34`. Account codes stay in `one_time_codes`.
6. **Payment receipt.** Done. Enqueued after the grant transaction commits.
7. **Exam-time mail.** Done.
8. **One-hour job.** Done. Single-flight scan every five minutes.
9. **Auth pages.** Done in code. `/register`, `/forgot-password`, `/reset-password`, and `/account`. Shahriar’s browser pass is still open. Progress notes are `api/.cursor/progress/auth-and-mail/`.
10. **Refund.** Not started until the refund rules above are confirmed.

Local checks use the dev profile and the fake exam sender. They do not call Brevo. A real inbox delivery is section 4, after this jar is the server process.

## 4. Deployment: connect Brevo

Do this when this build is the process running on the server. A local `dev` run never opens SMTP, so `MAIL_*` on this PC does not deliver mail. The jar audited on the new VPS on 24 October 2026 reads `EMAIL_HOST`, `EMAIL_PORT`, and `EMAIL_FROM`. This API does not read those names. Putting `MAIL_*` on that box before this jar is deployed does nothing. Local slices do not edit the VPS.

Brevo does not need a URL on this API. The API connects out to `smtp-relay.brevo.com`. The domain records for `symamlaw.com` are already in Namecheap. Section 5 is the account and DNS work. It is already done. The SMTP login and key stay in the password manager until the steps below.

1. Deploy this build. The server process is the `staging` or `prod` profile. Those profiles refuse to start when `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, or `MAIL_FROM_ADDRESS` is blank.
2. Put these in the server environment, from the password manager. Do not put the key in Git or in a document.
   - `MAIL_HOST=smtp-relay.brevo.com`
   - `MAIL_PORT=587`
   - `MAIL_USERNAME` is the Brevo SMTP login, not the API key
   - `MAIL_PASSWORD` is the standard SMTP key
   - `MAIL_FROM_ADDRESS=no-reply@symamlaw.com`
   - `MAIL_FROM_NAME=SYMAM LAW`
   - Leave `MAIL_REPLY_TO` unset
3. Leave Brevo’s IP lock off until `187.52.115.111` is on the allow list. A lock that omits that address drops every message. A laptop address changes, so it is a poor allow-list entry.
4. Start the process. Send one verification mail and one password-reset mail to one mailbox Shahriar can open. Use the staging profile for the real codes. Done 25 October 2026, after a fix: the SMTP transport had connected with no credentials.
5. Confirm both messages arrived and the From address is `no-reply@symamlaw.com`. The API log contains no SMTP key, no full recipient when a masked form is enough, and no code on a failure line. The sender already redacts the key and the code from failure text. Keep that.
6. Done: the `MAIL_TEST_ENABLED` flag and its endpoint were removed on 25 October 2026.

Do not point this check at the live SSLCommerz store. One API process owns the schedule. A second prod process would send the same exam mail again.

## 5. What Shahriar does in Brevo and DNS

Checked again on 24 October 2026, after Shahriar completed the account steps. The zone is no longer on Hostinger. Public NS are `dns1.registrar-servers.com` and `dns2.registrar-servers.com`, which is Namecheap SimpleDNS. Later DNS edits for this domain go in the Namecheap account, under Advanced DNS for this domain. Hostinger’s `dns-parking.com` zone is no longer what the internet uses.

Public DNS shows Brevo’s ownership TXT, `brevo1._domainkey` and `brevo2._domainkey` CNAMEs, and `_dmarc` with `p=none` reporting to Brevo. The apex, `www`, `api`, `admin`, and `backoffice` still resolve to the old VPS, `88.222.215.187`. Moving the nameservers did not point the site at `187.52.115.111`.

The SMTP login and SMTP key exist in Brevo and in Shahriar’s password manager. They are not on the new VPS and not in `api/.env`. Leave them there until section 4, which is the deployment step.

The apex SPF TXT is `v=spf1 include:spf.efwd.registrar-servers.com ~all`. That authorizes Namecheap forwarding. It is a separate record from Brevo’s DKIM. If Brevo later asks for an SPF include, merge it into this one TXT. A second SPF TXT would make both invalid.

Steps 1–6 below were done on 24 October 2026. Do not repeat them. Steps 7–10 are still open and follow section 4.

1. Done. The Brevo account owns `symamlaw.com` as transactional SMTP, not as a marketing campaign.
2. Done. The sending domain is authenticated in Namecheap SimpleDNS: ownership TXT, `brevo1._domainkey`, `brevo2._domainkey`, and `_dmarc` with `p=none`. Later DNS edits stay on that Advanced DNS page.
3. Done for the current records. Keep `p=none` until one test message has landed, then tighten it from Brevo’s instructions.
4. Done. Sender `no-reply@symamlaw.com`, display name `SYMAM LAW`. `MAIL_REPLY_TO` stays unset. The zone has no MX, so that address cannot receive replies.
5. Done. The SMTP login is in the password manager. It is `MAIL_USERNAME`. It is not the API key.
6. Done. The standard SMTP key is in the password manager. It is `MAIL_PASSWORD`. Do not paste it into chat, Git, or a markdown file.
7. Still open. The server values in section 4 go on the box only after this build is the running process. A local `dev` process ignores them. The older jar on the VPS does not read them.
8. Leave Brevo’s IP lock off until the new VPS address `187.52.115.111` is on the allow list. A lock that does not include that address drops every production message. A laptop address changes, so it is a poor allow-list entry.
9. A key with no successful send for 90 days is deactivated even when it has no expiry date. After go-live, a normal verification mail keeps it alive. Before go-live, the staging check in section 4 is that send.
10. Open the plan’s transactional daily limit in Brevo’s billing page before the first real exam. One module mailed at the same hour can be a burst. The code queues the rows. The account limit is still Brevo’s.

The Gmail SMTP account stays on the old site. Do not copy it onto the new VPS.
