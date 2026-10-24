# Current VPS mail audit — symamlaw.com

Read-only inspection on 23 October 2026. SSH was the existing `client-vps` alias, as root, to `srv633348.hstgr.cloud` (`88.222.215.187`). Server time at the start of the session was 18:19 +06 (`Asia/Dhaka`).

Nothing on the VPS was modified. No service was restarted, no mail was sent, no queue job was dispatched or retried, no DNS record was changed, and no external account was opened. Secret values were not copied into this report.

Labels:

- **Verified** — observed in this session from the host, from application files, or from a DNS query.
- **Inferred** — a conclusion that follows from those observations.
- **Unknown** — not established from this host.

Other sites on this VPS were left alone. They are mentioned only where a process list would otherwise look incomplete.

## 1. Executive Summary

The old symamlaw applications are still running. Mail is sent by the Laravel API, not by the Next.js sites, and not by a mail server on the VPS.

**Verified:** Laravel is configured for SMTP to `smtp.gmail.com`, port `587`, encryption `tls`. The authenticated mailbox is at `gmail.com`. The From address and the Reply-To address are the same mailbox at `symamlaw.com`, and they are hardcoded in the controllers. A database queue and one Supervisor worker deliver that mail. The worker is running. Its log records 324 completed `SendMailJob` runs from 30 December 2025 through 23 October 2026, including two today, each finishing in a few seconds. The `jobs` table was empty at inspection. No Postfix, Exim, Dovecot, or Sendmail package is installed, and nothing listens on the mail ports.

**Verified:** Public DNS for `symamlaw.com`, read today from `ns1.dns-parking.com` and from `1.1.1.1`, has no MX, no SPF TXT, no DMARC TXT, and no TXT at the DKIM names that were queried.

**Inferred:** A job marked done means the SMTP conversation returned success to Symfony Mailer. It does not mean the message reached an inbox. The domain publishes no sender authentication, and the authenticated account is a consumer Gmail mailbox rather than a mailbox at `symamlaw.com`. Gmail can accept the message and still rewrite the From line, or receivers can treat it as unauthenticated.

**Unknown:** Who controls the Gmail account, whether that person can keep using it, and whether Google has been told to allow the `symamlaw.com` From address.

**Superseded on 24 October 2026.** Shahriar decided the new API uses this same Gmail SMTP account. The decision is in `docs/exam-site/decisions.md`. The old Laravel queue still stays on this VPS for the old application. SPF, DKIM, and DMARC are still absent. Do not change DNS as part of this audit.

## 2. VPS and Application Inventory

| Fact | Observation | Label |
|---|---|---|
| OS | Ubuntu 24.04.4 LTS (Noble) | **Verified** |
| Hostname | `srv633348.hstgr.cloud` (short name `srv633348`) | **Verified** |
| Public IPv4 | `88.222.215.187/24` on `eth0` | **Verified** |
| Clock | 23 October 2026, 18:19 +06, NTP synchronized, timezone `Asia/Dhaka` | **Verified** |
| Uptime | 5 days 20 hours at the start of this session | **Verified** |
| SSH account | root | **Verified**. This does not identify the Gmail account owner or the domain owner |
| PHP | 8.2.35 CLI; `php8.2-fpm` running | **Verified** |
| Database | MariaDB 10.11.14, listening on `127.0.0.1:3306` only | **Verified** |
| Web server | nginx 1.24.0, listening on 80 and 443 | **Verified** |
| Java | not installed, no Java process, nothing listening on 8080 | **Verified** |
| Docker, PM2, Redis | binaries absent; no Redis listener | **Verified** |
| Node | not on the default `PATH`. The running Next.js processes use `/root/.nvm/versions/node/v22.23.4/bin/node` | **Verified** |

Running application pieces for this domain:

| Piece | Where | How it is running | Label |
|---|---|---|---|
| Laravel API | `/var/www/html/symamlaw-backend` | php-fpm. Nginx `backoffice.symamlaw.com` uses that tree's `public` directory as its root | **Verified** |
| Public website | `/root/symamlaw/symamlaw-frontend` | `next-server` on port 3000, parent command `sh -c next start`, started 23 October 2026 12:35. Nginx `symamlaw.com` proxies to `127.0.0.1:3000` | **Verified** |
| Admin website | `/root/symamlaw/symamlaw-admin` | `next-server` on port 3001, same start time. Nginx `admin.symamlaw.com` proxies to `localhost:3001` | **Verified** |
| Queue worker | same Laravel tree | Supervisor program `symamlaw-worker`, pid 84708 at inspection, user `www-data` | **Verified** |

Laravel framework constraint in `composer.json` is `^10.10`. **Verified.**

**Verified:** nginx, php-fpm, and Supervisor were already running when this session connected. php-fpm's own log shows those processes were started at 06:34 today, which is later than the machine's 5-day uptime, so they were restarted earlier today by someone or something else. This audit did not restart them.

**Inferred:** The three symamlaw applications are in production use, not a leftover install. The websites answer through Nginx, and the queue worker is processing jobs.

**Unknown:** No systemd unit for the Next.js apps turned up in the unit list. Their parent is a shell running `next start`. Whether a reboot brings them back was not proven. The queue worker is different: Supervisor is configured with `autostart=true` and `autorestart=true`.

**Verified:** There is no Spring Boot process and no Spring mail configuration on this host. A replacement mail stack has not been installed here.

Port 3002 belongs to another site's Next.js process. Its mail setup was not inspected.

## 3. Current Mail Configuration

Runtime mail settings come from `/var/www/html/symamlaw-backend/.env`. That file is mode `0775`, owner root, group `www-data`, so every local user can read it. Its mtime is 30 December 2025 and its size is 1748 bytes. **Verified.**

There is no `bootstrap/cache/config.php`. `bootstrap/cache/` holds `packages.php` and `services.php` from 30 December 2025, and neither file contains `smtp.gmail.com`. **Verified:** Laravel is not serving a cached config. The `.env` values below are the values the running application loads.

| Setting | Value | Label |
|---|---|---|
| `MAIL_MAILER` | `smtp` | **Verified** |
| `MAIL_HOST` | `smtp.gmail.com` | **Verified** |
| `MAIL_PORT` | `587` | **Verified** |
| `MAIL_ENCRYPTION` | `tls` | **Verified** |
| `MAIL_USERNAME` | present, domain `gmail.com` | **Verified** domain only |
| `MAIL_PASSWORD` | present, 16 characters, not empty | **Verified** presence and length only |
| `MAIL_FROM_ADDRESS` | present, domain `symamlaw.com` | **Verified** domain only |
| `MAIL_FROM_NAME` | the literal `${APP_NAME}` inside quotes in the file | **Verified** |
| `APP_NAME` | `SymamLaw` | **Verified** |
| `MAIL_URL` | absent | **Verified** |
| `MAIL_EHLO_DOMAIN` | absent | **Verified** |
| `QUEUE_CONNECTION` | `database` | **Verified** |
| `APP_ENV` | `local` | **Verified** |
| `APP_DEBUG` | `false` | **Verified** |
| `APP_URL` | `https://backoffice.symamlaw.com` | **Verified** |
| `LOG_CHANNEL` / `LOG_LEVEL` | `stack` / `debug` | **Verified** |

**Inferred:** phpdotenv expands `${APP_NAME}` for `MAIL_FROM_NAME`, so the framework default sender name would be `SymamLaw`. The mail classes do not use that default. They pass the display name `Symamlaw` themselves. See section 4.

**Verified:** `config/mail.php` is the Laravel 10 file. The default mailer is `env('MAIL_MAILER', 'smtp')`. The smtp mailer reads the variables above. The same file still contains the unused framework examples for SES, Postmark, Mailgun, sendmail, log, failover, and round-robin. Those examples are not selected.

**Verified:** `.env` has no `MAILGUN_*`, `POSTMARK_*`, or `RESEND` / `SENDGRID` keys. `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` are empty. `AWS_DEFAULT_REGION` is set and is not, by itself, an SES account. No transactional email API is configured.

**Verified** from `vendor/laravel/framework/src/Illuminate/Mail/MailManager.php`: when encryption is `tls` and the port is not 465, Laravel builds a Symfony DSN scheme of `smtp`. The `smtps` scheme is selected only for port 465. Port 587 with scheme `smtp` is the STARTTLS path. Implicit TLS was not selected.

**Inferred:** A 16-character password is the length of a Gmail app password. The file does not say whether it is an app password, and this audit did not try it.

Other environment files:

| File | Mail keys | Label |
|---|---|---|
| `/var/www/html/symamlaw-backend/.env.example` | Laravel sample: host `mailpit`, port `1025`, encryption `null`, From at `example.com` | **Verified.** This is not the runtime file |
| `/root/symamlaw/symamlaw-frontend/.env` and `.env.save` | no `MAIL_` or `EMAIL_` keys | **Verified** |
| `/root/symamlaw/symamlaw-admin/.env` | no `MAIL_` or `EMAIL_` keys | **Verified** |

The frontend and admin files were classified by key name only. Their other values were not printed.

**Verified:** The hardcoded From address in the four send sites is identical to `MAIL_FROM_ADDRESS`. It is not the same address as `MAIL_USERNAME`. The Reply-To address is the same `symamlaw.com` address. Local-parts are omitted here on purpose.

**Unknown:** A configured From address does not prove that anyone can open that mailbox, or that Google has verified it as an allowed "Send mail as" identity.

## 4. Mail-Related Application Features

Mail in source is one job and one mailable. There is no second mail stack in the Next.js apps. A search of those trees, excluding `node_modules` and `.next`, hit a mail-related string only in a `package-lock.json`.

### What the code sends

`app/Jobs/SendMailJob.php` implements `ShouldQueue`. Its `handle` method calls `Mail::to(...)->send(new CustomMail(...))`. **Verified.**

`app/Mail/CustomMail.php` imports `ShouldQueue` and does not implement it. The queued unit is the job, not the mailable. The mailable builds a Symfony envelope whose From and Reply-To come from the constructor, so the hardcoded controller addresses override `config/mail.php`'s from-address for these messages. The view is always `user.email_template_otp`. Four title strings choose the body copy: `updatepass`, `forgotpassword`, `emailverification`, and `resendOTP`. Each body says the code is valid for two minutes. **Verified.**

Call sites, all in PHP, all dispatching `SendMailJob`:

| Route | Controller method | Title | Subject line | Label |
|---|---|---|---|---|
| `POST /api/registration` | `AuthController::registration` | `emailverification` | `Verify Your Email - Symam Law` | **Verified** |
| `POST /api/forgot-password/get-otp` | `AuthController::forgotPasswordOTP` | `forgotpassword` | `Forgot your Password - Symam Law` | **Verified** |
| `POST /api/otp/resend-otp` | `AuthController::resendOTP` | `resendOTP` | `Resend OTP - Symam Law` | **Verified** |
| `POST /api/registration/user` | `UserController::userRegistration` | `emailverification` | `Verify Your Email - Symam Law` | **Verified** |

The fourth route sends only when that email is already on a user row. Otherwise it returns "This Email is not registered by the Admin." The OpenAPI text on the matching confirm route describes an instructor or admin. **Verified.**

`POST /api/email/verify`, `POST /api/forgot-password/post-otp`, and `POST /api/registration/user/submit-otp` check a code. They do not send mail.

`login` refuses a user whose `email_verified_at` is null, on both branches that were inspected. **Verified.**

`socialLoginGoogle` writes `email_verified_at` in application code and does not dispatch `SendMailJob`. **Verified.**

### What is in source and not on a send path

| Item | Observation | Label |
|---|---|---|
| `updatepass` branch in `CustomMail` | no caller sets that title | **Verified** unused |
| Blade files `email_verification_template`, `forgot_password_otp_template`, `update_password_otp_template` | present under `resources/views/user/`; no reference outside their own files | **Verified** unused |
| `EventServiceProvider` maps `Registered` to Laravel's `SendEmailVerificationNotification` | `User` does not implement `MustVerifyEmail` (the import is commented out). No application code dispatches `Registered` | **Verified** that the mapping is unused by this app's code |
| `password_reset_tokens` | table exists and has 0 rows | **Verified.** The app does not use Laravel's password-broker table |
| Exam-result mail, exam reminders, instructor or student notification classes | no `Mail::` send, no notification class, no listener that sends mail, no command directory | **Verified** absent from this app |
| `app/Console/Kernel.php` `schedule` | empty aside from a commented example | **Verified** |

`SubmissionController`, `OrderController`, and `CertificateController` mention email only in OpenAPI examples or as a stored field. They do not dispatch `SendMailJob`.

### Source versus production

**Verified:** The routes above are in `routes/api.php`, php-fpm is serving this tree, and the worker log shows `SendMailJob` completing in production through today. The OTP flows are not dead code.

**Verified:** The worker log does not record which of the four flows ran. Production activity is "the job class completed," not a per-feature count.

**Inferred:** Exam results and reminders are not part of the live mail system, because they are absent from the source that this host is serving and there is no scheduler entry that could send them from outside the repository.

## 5. Queue, Scheduler, and Background Workers

**Verified** queue configuration:

| Item | Value |
|---|---|
| Driver | `database`, table `jobs`, queue name `default` |
| `retry_after` | 90 seconds |
| `after_commit` | false |
| Failed jobs | driver `database-uuids` (the `QUEUE_FAILED_DRIVER` variable is unset, so the config default applies), table `failed_jobs` |
| Supervisor program | `/etc/supervisor/conf.d/symamlaw-worker.conf` |
| Command | `php /var/www/html/symamlaw-backend/artisan queue:work --sleep=3 --tries=3 --max-time=3600` |
| Processes | 1, user `www-data`, `autostart` and `autorestart` |
| Worker stdout | `/var/www/html/symamlaw-backend/storage/logs/worker.log` |
| `stopwaitsecs` | 3600 |

`SendMailJob` defines no `$tries`, `$timeout`, `$backoff`, or `failed()` method. The worker's `--tries=3` is what limits retries. The command does not pass `--timeout`. **Inferred:** the worker's default timeout of 60 seconds applies. `retry_after` of 90 seconds is longer than that, which is the usual arrangement.

**Verified:** `supervisorctl status` showed `symamlaw-worker_00` RUNNING. Supervisor's own log shows the process exiting with status 0 and being spawned again every hour (13:35, 14:35, 15:35, 16:35, 17:35 today). That matches `--max-time=3600`. It is a planned recycle, not a crash loop.

**Verified:** root and `www-data` have no crontab. `/etc/crontab` and `/etc/cron.d` have no `artisan` or `schedule:run` line for this app. The scheduler is not installed. Scheduled reminder mail cannot be running.

Other Supervisor programs exist for other sites. They were not reviewed.

### Queue tables at inspection

| Table | Rows | Label |
|---|---|---|
| `jobs` | 0, none reserved | **Verified.** No backlog at 18:19 |
| `failed_jobs` | 16, all `App\Jobs\SendMailJob` | **Verified** |
| `password_reset_tokens` | 0 | **Verified** |
| notifications | no matching table | **Verified** |

Failed-job timestamps run from 9 May 2024 16:53 to 28 May 2025 12:02. Nothing has been written to `failed_jobs` since then. Categories, taken from the exception class and the SMTP code inside the exception, with addresses and message text discarded:

| Category | Jobs | What was kept from the text | Label |
|---|---|---|---|
| Authentication | 11 | SMTP code `535` on a `TransportException` | **Verified** code. **Inferred** that `535` here is an authentication rejection |
| Connection | 4 | timeout or "connection could not" wording, no SMTP code retained | **Verified** as connection-class wording |
| Recipient or content rejection | 1 | SMTP code `550` | **Verified** code |
| Months | 2024-05 (7), 2024-11 (4), 2025-05 (5) | | **Verified** |

One of the 16 exceptions was `UnexpectedResponseException`; the other 15 were `TransportException`.

### What the worker log shows after those failures

`worker.log` is 504 KB and starts on 30 December 2025, which is after the last failed job. In that file:

| Outcome | Count | Label |
|---|---|---|
| `RUNNING` | 325 | **Verified** |
| `DONE` | 324 | **Verified** |
| `FAIL` | 0 | **Verified** |
| Duration of `DONE` lines | 1 to 14 seconds | **Verified** |

Completions by month: 2025-12 (1), 2026-01 (2), 2026-02 (1), 2026-03 (4), 2026-04 (4), 2026-05 (8), 2026-06 (3), 2026-07 (161), 2026-08 (76), 2026-09 (30), 2026-10 (34). **Verified.**

Today's two completions, in `Asia/Dhaka`: 12:18:50 to 12:18:53, and 15:04:29 to 15:04:33. Both say `DONE`. **Verified.**

One `RUNNING` line on 30 July 2026 20:37:59 has no paired `DONE`. The next `SendMailJob` line is `RUNNING` at 20:39:30 and `DONE` at 20:39:34. The gap is about 90 seconds, the same value as `retry_after`. **Inferred:** that attempt was interrupted, most likely by the hourly worker recycle, and a later attempt finished. It was not written to `failed_jobs`. Whether the interrupted attempt had already handed a message to Gmail is **unknown**.

`laravel.log` is 189 MB, world-readable (`0775`), and its parsed dates run from 30 December 2025 to 22 October 2026. A full scan found zero lines containing `smtp`, `mailer`, `SendMail`, or `TransportException`. Nine lines on 11 August 2026 contain the letters `gmail` because a gmail.com address appeared inside an admissions SQL error. Those lines are not an SMTP session. **Verified.**

**Inferred:** Successful SMTP handoff is not written to `laravel.log`. The `DONE` lines are the delivery evidence this host has, and they mean the job returned without throwing. Symfony Mailer throws when the server does not accept the message, so a `DONE` job is evidence the configured transport accepted it. It is not evidence about the recipient's inbox, spam folder, or a later bounce.

The Laravel queue is a useful picture of how this app separated "save the user" from "send the mail." It is not a component Spring Boot can attach to. The `jobs` and `failed_jobs` tables store serialized PHP. A Spring worker must not consume them.

## 6. System-Level Mail Services

**Verified:** `dpkg` shows no installed package for Postfix, Exim, Sendmail, Dovecot, OpenDKIM, OpenDMARC, nullmailer, msmtp, or ssmtp. `postconf`, `exim`, `sendmail`, and `dovecot` are not on `PATH`. No process name matches those daemons.

**Verified** listening sockets: 22, 80, 443, 3000, 3001, 3002, MariaDB on `127.0.0.1:3306`, and the local stub resolver on port 53. Nothing listens on 25, 465, 587, 143, 993, 995, or 110.

**Inferred:** This VPS does not host mail. It is an SMTP client of `smtp.gmail.com`. Installing a mail package would not, by itself, have meant the opposite; the missing listener is what settles it.

Nginx server names for this domain are the website, `www`, `admin`, `backoffice`, and `phpmyadmin`. No mail virtual host appeared in those files.

## 7. DNS and Domain Authentication

Queries below were made on 23 October 2026 to the authoritative server `ns1.dns-parking.com`, and the MX, apex TXT, `_dmarc` TXT, and `google._domainkey` TXT queries were repeated through `1.1.1.1`. Both resolvers agreed. An empty answer came back as the SOA for `symamlaw.com` (primary `ns1.dns-parking.com`, mailbox `dns.hostinger.com`, serial `2026102201`). That is a NODATA or NXDOMAIN response, not a mail record.

| Name | Type | Result this session | Label |
|---|---|---|---|
| `symamlaw.com` | A | `88.222.215.187`, TTL 14400 | **Verified** |
| `symamlaw.com` | AAAA | no answer | **Verified** |
| `symamlaw.com` | NS | `ns1.dns-parking.com`, `ns2.dns-parking.com`, TTL 86400 | **Verified** |
| `symamlaw.com` | MX | no answer | **Verified** |
| `symamlaw.com` | TXT | no answer. No SPF record is published at the apex | **Verified** |
| `_dmarc.symamlaw.com` | TXT | no answer | **Verified** |
| `mail.symamlaw.com` | MX and TXT | no answer | **Verified** |
| `autodiscover.symamlaw.com` | CNAME | no answer | **Verified** |
| `_submission._tcp.symamlaw.com` | SRV | no answer | **Verified** |
| DKIM names queried: `default`, `google`, `hostinger`, `s1`, `s2`, `selector1`, `selector2`, `k1`, `mail`, `dkim` under `_domainkey.symamlaw.com` | TXT | no answer at any of those names | **Verified** for those names only |

This matches the DNS audit written earlier the same day (`docs/exam-site/domain-dns-access-audit.md`): no MX, no apex TXT, no DMARC, and no TXT at `default._domainkey`, `google._domainkey`, or `hostinger._domainkey`. That earlier pass is a separate observation. This session re-queried the records rather than copying its conclusion.

**Unknown:** Some other DKIM selector could exist. The selector list above is the usual set, not every possible name. A failed lookup for one name does not prove the domain has no mail software somewhere else.

**Inferred from the records that are present:**

- Inbound mail for `@symamlaw.com` is not published. With no MX, senders follow the A record only as a last resort, and this host is not listening on port 25, so delivery to the domain should fail. Nobody should assume the From address can receive replies or bounces.
- Outbound mail does not depend on an MX. The application submits to Google on port 587.
- Receivers that check SPF for `symamlaw.com` will not find a policy. Receivers that check DMARC will not find a policy. No DKIM key was found at the names above, so this domain is not signing with those selectors.
- Names such as `mail.symamlaw.com` can still resolve to `88.222.215.187` because of the wildcard A record described in the earlier DNS audit. An A record is not an MX and does not mean the VPS receives mail.

**Unknown:** Which Hostinger login can edit the zone. SSH on this VPS cannot. That limit was established in the earlier DNS audit and was not retested by attempting a zone change.

## 8. Delivery and Error Evidence

Three different claims stay separate.

| Claim | Evidence | Label |
|---|---|---|
| The application attempted mail | 325 `RUNNING` lines for `SendMailJob` since 30 December 2025, and 16 older rows in `failed_jobs` | **Verified** attempt |
| The SMTP server accepted the message | 324 `DONE` lines, durations 1–14 seconds, including two on 23 October 2026. No `FAIL` line in that log. `jobs` is empty. `failed_jobs` has had no new row since 28 May 2025 | **Inferred** acceptance by the configured SMTP server. The log does not record the remote host, the SMTP reply text, or a message id |
| The recipient inbox received it | no mailbox was opened, no bounce address exists in DNS, and the application does not record a delivery webhook | **Unknown** |

Historical failures are real and old. Eleven jobs failed with SMTP `535` between May 2024 and May 2025, four failed while connecting, and one received `550`. **Verified** as stored exceptions. **Unknown** what was changed afterward. The `.env` mtime is 30 December 2025, and the worker log from that date forward shows completions only. The current password was not tested.

`laravel.log` does not contain the 2024–2025 failures. Both `laravel.log` and `worker.log` start on 30 December 2025. **Inferred:** logs were truncated or recreated then. Older application log lines are not available. `failed_jobs` is the record that survived.

There is no evidence in the current logs of a TLS handshake failure, a Gmail rate limit, or a daily sending quota error. **Verified** as absence inside the retained log text, not as a guarantee that those things never happened.

Account counts, without identities: 776 users, 648 with `email_verified_at` set, 128 without it, 144 with a non-empty `otp` value. Of those codes, 119 sit on unverified users and 25 sit on verified users. 30 rows have an `otp_expire_at` in the past. None had an unexpired `otp_expire_at` at inspection time. **Verified.** **Inferred:** verification has been used a lot. A stored code is not proof the message arrived, and an unverified account is not proof it failed. Abandoned signups look the same.

## 9. Ownership and Security Assessment

| Question | Result | Label |
|---|---|---|
| Which provider does the app use? | Gmail SMTP at `smtp.gmail.com` | **Verified** |
| What kind of mailbox authenticates? | An address at `gmail.com`, not at `symamlaw.com` | **Verified** domain. **Inferred** consumer Gmail rather than Google Workspace for this domain, because Workspace SMTP users are normally at the domain |
| What identity appears as From? | One hardcoded address at `symamlaw.com`, display name `Symamlaw`, also used as Reply-To | **Verified** |
| Is a dedicated transactional provider configured? | No | **Verified** |
| Are credentials missing? | `MAIL_USERNAME` and `MAIL_PASSWORD` are both set. No indirect secret store (Vault, SES keys, API token) is configured for mail | **Verified** |
| Does the VPS show who owns the Gmail account? | No name, billing contact, or Google customer id is recorded beside the username | **Unknown** |
| Does the From domain prove mailbox ownership? | No | **Unknown** by design. The client has to confirm this with whoever holds the Google account |

**Verified** handling problems, without the secret values:

- `/var/www/html/symamlaw-backend/.env` is world-readable. The SMTP password is in that file. Any local account on the VPS can read it. The queue worker runs as `www-data` and needs to read it; the world-read bit is wider than that.
- `storage/logs/laravel.log` is also mode `0775` and has contained personal data in ordinary application errors.
- A gitignored workspace file, `docs/exam-site/config-files/payment-credentials.md`, contains the SMTP password in plaintext. Git ignores `docs/exam-site/config-files/`, so it is not in the repository index. It is still a copy of a live secret. This report does not repeat it. If that file has been shared, the Gmail credential should be rotated by the account owner. Rotation was not performed here.
- The OTP is stored on the `users` row. `forgotPasswordPostOTP` loads the user by the code and the expiry time, and the inspected query does not also require the email. Student verification does match email and code. The new system should not copy the code-only lookup. This audit did not exercise that route.

**Unknown:** Whether Google rewrites the From header to the `gmail.com` address. That depends on the account's "Send mail as" setting, which is not on this server.

Nobody authenticated to Google, Hostinger, or Namecheap during this audit.

## 10. Known Unknowns and Limitations

Not done, because each one would have sent mail, used a credential, or changed something:

- No test message, no password-reset request, no OTP request.
- No login to Gmail, Google Workspace, Hostinger DNS, or Namecheap.
- No `php artisan` command. Artisan can write cache files and can touch the queue.
- No retry of the 16 failed jobs, and no read of their serialized payloads. Payloads contain recipient addresses and codes.
- No connection opened to `smtp.gmail.com`.
- No install of `dig` or any other package. DNS was queried from the workstation.
- DKIM was checked only at the selectors listed in section 7.
- Worker stdout does not include the recipient, the SMTP reply, or the message id. `DONE` cannot be promoted to inbox delivery.
- Logs before 30 December 2025 are gone except for `failed_jobs`.
- The reason for the 2024–2025 `535` failures, and what changed before December 2025, is not on the host.
- Who can edit DNS, who owns the Gmail account, and whether inbound mail at `@symamlaw.com` is required, are questions for the client.
- The VPS service end date is not stored on the server. A separate note says 5 November 2026. That date was not rechecked here.
- Other sites on this machine were not part of the mail inspection.
- Opcache is left at commented `php.ini` defaults. A dedicated opcache drop-in was not found by the search used here. Combined with the missing config cache, the files on disk are the configuration and the code that were assessed.

## 11. Reuse vs. Replace Recommendation

Do not reuse this mail path for Spring Boot.

The old application can keep using it until this VPS is turned off. Jobs are completing, the worker restarts itself cleanly, and the queue is not backed up. That is a reason to leave the old system alone, not a reason to copy it.

Reasons to replace it for the new application:

1. The sending account is a consumer Gmail mailbox. The From address is a different domain. Ownership of the Google account is not established, and a personal mailbox is a poor production dependency.
2. `symamlaw.com` publishes no SPF, DKIM, or DMARC. Gmail can accept the submission and receivers can still distrust the message. Consumer Gmail cannot publish a DKIM key for `symamlaw.com`. Fixing alignment means a provider that signs for this domain, or Google Workspace for this domain. It does not mean pasting the same SMTP password into Spring.
3. There is no MX, so the From address cannot receive bounces or replies.
4. The password is sitting in a world-readable env file and in a local notes file.
5. Volume so far is a few hundred OTP messages in ten months. Exam-result mail, if it is ever required, is a different volume and a different deliverability problem. Gmail's SMTP limits are not a plan.
6. The Laravel queue, Supervisor program, and `failed_jobs` table are PHP-specific. They are a design reference only.

A dedicated transactional provider is the better default: one the client can hold the login for, with DKIM and a bounce webhook. Google Workspace is the alternative if the client specifically wants mailboxes at `@symamlaw.com` and is willing to operate them. Either choice waits on two confirmations: who owns the current Gmail account, and who can edit the Hostinger zone.

Retaining Gmail SMTP is acceptable only as a short bridge for the old app, and only if the account owner confirms they still control it. It is not acceptable as the unexamined production sender for the new site.

## 12. Migration Checklist for Spring Boot

Nothing in this list was implemented.

### What the client needs to confirm

- Who can sign in to the Gmail account that `MAIL_USERNAME` points at, and whether that person is willing to keep it in service for the old app.
- Whether that account is allowed to send as the `symamlaw.com` From address, or whether Gmail has been rewriting the header.
- Who can edit DNS at Hostinger. The Namecheap registration and the Hostinger zone are different logins.
- Whether anyone must receive mail at `@symamlaw.com`. Today the zone says no.
- The From address and display name for the new site.
- Whether exam results, reminders, or the in-app notices from `docs/exam-site/decisions.md` should also be emailed. The old application does not email them. The current product decision says those notices are rows the website loads, with no email.

### DNS, later, and not now

When a provider is chosen and the zone can be edited, the usual records are:

- SPF TXT at the apex, including only the chosen sender.
- DKIM records at the selector names that provider gives. They are often CNAMEs.
- DMARC TXT at `_dmarc.symamlaw.com`, starting with `p=none` and a reporting address the client actually reads, then tightened after mail is clean.
- MX only if the domain should receive mail. Leave MX absent until that is a real requirement. Pointing MX at this VPS would not work: nothing here accepts mail, and this VPS is not the new application server.

Website A records and mail records are independent. Moving the website must not invent an MX, and adding mail records must not be bundled into an unrelated A-record change.

### How Spring Boot should send

- Use environment variables on the new host for the host, port, STARTTLS setting, username, and password, or for the provider's API token. Do not commit them, and do not copy this VPS's `.env` into the repo.
- `spring-boot-starter-mail` with `JavaMailSender` is enough for SMTP on port 587 with STARTTLS. A provider HTTP API is the better fit if the provider offers bounce webhooks.
- Send as one address at `symamlaw.com` only after SPF and DKIM for that address are in place. Use a display name the client chooses. Set Reply-To only if a real mailbox receives it.
- Keep the business transaction and the send separate. Registration, password reset, and result publication should commit even when the provider is down. Record a delivery state. Do not tell the user "an email has been sent" until the provider has accepted it, or use wording that says the message is being sent.
- OTP and password reset: store a hash of the code, bind it to the user id, give it a short expiry, and check user plus code plus expiry. Start the expiry when the message is accepted, or make the TTL long enough to cover a retry. The current two-minute window is tight, and one of the current confirm queries matches the code without the email.
- Exam results and reminders: do not add them by copying this app, because this app does not send them. If a later decision adds them, queue them the same way, one recipient per job, and do not block the publish action on the send.
- Background work: yes for anything that is not an interactive OTP. Use a Spring-owned jobs table or the provider's API with retries and a dead-letter state. Retries should be limited and should not duplicate a message once the provider has accepted it. A single worker is enough at the current volume; more than one worker needs a claim so two workers cannot send the same job.
- In-app notices stay a table the website reads. Email failure must not hide or roll back those rows.
- Alert on failed jobs. This Laravel app writes `failed_jobs` and notifies nobody.

### Before production launch

- The account owner rotates any credential that has lived in the world-readable `.env` or in the local notes file, if that credential will still be used anywhere.
- DNS for the chosen provider is visible from a public resolver.
- One message to a mailbox the client controls shows, in the received headers, the expected From address and passing SPF and DKIM for `symamlaw.com`.
- Password reset and email verification succeed, and an unknown address does not reveal whether an account exists beyond what the product already does.
- Stopping the mail worker, or using a bad password in a staging environment, leaves registration and exam publication intact and leaves a visible failed-delivery record.
- Secrets are absent from git, from images, and from log lines.
- The old VPS worker is left as it is until the old site is switched off. It is not pointed at the new database.

## 13. Evidence Appendix

Inspection window: 23 October 2026, about 18:19–18:40 +06. Commands were read-only: `cat` of `/etc/os-release`, `hostname`, `date`, `timedatectl`, `ss`, `ps` with command lines checked for credentials before they were kept, `systemctl list-units`, `supervisorctl status`, `dpkg -l` filtered to mail packages, file reads, `grep` / Python classification that printed domains and presence instead of secret values, `mysql` `SELECT` and `SHOW` against `symamlaw_db` only, and `Resolve-DnsName` from the workstation. `php artisan` was not run. Payloads, passwords, and mailbox local-parts were not printed.

| Area | Finding | Evidence source | Confidence | Limitation or caveat |
|---|---|---|---|---|
| Host | Ubuntu 24.04.4, hostname `srv633348.hstgr.cloud`, clock 18:19 +06, IPv4 `88.222.215.187` | `/etc/os-release`, `hostname`, `timedatectl`, `ip -4 addr` | High | IPv6 was not rechecked this session |
| Apps still running | Laravel under php-fpm, Next.js on 3000 and 3001, Supervisor worker for this app | `ss`, `/proc` cwd and cmdline, nginx `sites-enabled`, `supervisorctl status` | High | Next.js restart-on-reboot was not proven |
| No Spring Boot | `java` absent, no Java process, nothing on 8080 | `command -v`, `ps`, `ss` | High | A Spring app could exist as unused files; no such tree was identified |
| SMTP settings | `smtp.gmail.com:587`, encryption `tls`, username domain `gmail.com`, password present | `.env` classification; `config/mail.php` | High | Password and local-parts were not recorded |
| STARTTLS | Port 587 with encryption `tls` selects DSN scheme `smtp`, not `smtps` | `MailManager::createSmtpTransport` | High | Symfony's STARTTLS behavior follows from that scheme; the socket was not traced |
| From identity | Hardcoded From and Reply-To at `symamlaw.com` match `MAIL_FROM_ADDRESS` and differ from the Gmail username | Controller comparison done on the host; only the boolean and the domain were printed | High | Does not prove Google preserves that From header |
| No config cache | `bootstrap/cache/config.php` absent; cache files do not contain the SMTP host | directory listing and `grep -l` | High | Opcache drop-in was not fully traced |
| No third-party mail API | Mailgun, Postmark, and AWS secret keys absent or empty | `.env` key classification | High | A provider could still be hardcoded; the mail classes call Laravel's `Mail` facade only |
| Features | Four OTP send sites, all through `SendMailJob` | `routes/api.php`, `AuthController`, `UserController`, `CustomMail` | High | Worker log does not say which of the four ran |
| No exam or reminder mail | No other send sites; scheduler empty; no cron | source search, `Kernel.php`, crontabs | High | A manual send from outside the repo would not show up in source |
| Queue | Database driver, one Supervisor worker, `--tries=3`, hourly recycle, 0 queued jobs | `config/queue.php`, supervisor conf, `supervisorctl`, `jobs` count | High | `--timeout` is the framework default, not a flag in the config |
| Recent success | 324 `DONE`, 0 `FAIL` since 30 December 2025; two `DONE` today | `storage/logs/worker.log` | High | `DONE` is job success, not inbox proof |
| Old failures | 16 `SendMailJob` rows, last on 28 May 2025; 11 with SMTP `535` | `failed_jobs`, exception class and code only | High | Exception bodies and payloads were not copied; cause of the `535`s is unknown |
| No local MTA | No mail packages, no mail listeners | `dpkg`, `ss`, `ps` | High | A remote smart host other than the configured Gmail host was not packet-captured |
| DNS | No MX, SPF, DMARC, or DKIM at the names queried | `Resolve-DnsName` against `ns1.dns-parking.com` and `1.1.1.1` | High | Other DKIM selectors were not enumerated |
| Ownership | Gmail account owner is not recorded on the VPS | absence in env and app files | High | The client can settle this outside the VPS |
| Secret handling | `.env` is mode `0775`; a gitignored local notes file also holds the SMTP password | `ls -l` on the VPS; `git check-ignore` locally | High | Values were not copied into this report |

Earlier write-ups of the same host, used as context and then rechecked where it mattered: `docs/exam-site/domain-dns-access-audit.md`, `docs/exam-site/domain-ssl-migration-audit.md`, and `docs/exam-site/production-backup.md`. Where this report and those disagree, this session's observations are the ones above.
