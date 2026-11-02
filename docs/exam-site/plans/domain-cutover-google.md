# Plan: the current site on exam.symamlaw.com, with Google sign-in

Status: planned, not started. Nothing on the box has changed. Written 3 November 2026 for Shahriar and the agent helping him on the box.

**Goal:** the current code runs on the VPS. Students use `https://exam.symamlaw.com`, staff use `https://admin.exam.symamlaw.com`, both with valid certificates, and students can sign up and sign in with Google.

**Not in this plan:** live SSLCommerz payments. The site stays on the `staging` profile and the sandbox store. Live payments are phases 6 to 8 of [live-domain-and-sslcommerz.md](live-domain-and-sslcommerz.md), which is still the guide for them. This plan replaces its phases 0 to 3. See D2 before telling students about the site.

## 1. Where things stand (checked read-only on 3 November)

| Item | Found | Meaning |
|---|---|---|
| DNS | `exam` and `admin.exam` both answer `187.52.115.111` | Done. No Namecheap work. No CAA record blocked the check |
| Box | Ubuntu, 91 GB free, 6.5 GB memory free, Java 21, Node 22, ports 80 and 443 open, Certbot 2.9 with its timer | Ready |
| Nginx | Only `coaching` and `coaching-admin` are enabled. The new hosts fall to the catch-all and get a wrong certificate | Needs phase 3 |
| App | Jar and website of 30 October, Flyway 55. No open sittings | 13 migrations to apply (V56 to V68) |
| Env | `WEB_ORIGIN`, `SSLCZ_CALLBACK_BASE_URL` and the web unit name the old hosts. `GOOGLE_*` are absent | Needs phases 2, 4 |
| Data | 7 students, 1 System Admin, 2 instructors, 30 exams, 1 published package, 13 submitted attempts, 5 sandbox orders (4 succeeded, 1 failed), 8.8 MB of files. The demo accounts share a known password | Content stays, accounts go. See D1 |
| Backups | No scheduled database or file backup on the box. Last dump 29 October | Needs phase 1 |
| Google | A client id and secret exist in the PC's `api/.env`, with a localhost redirect. A sign-in over HTTPS on a real host has never been run | Needs phase 2 |

V64 deletes orders with method `DIRECT`. The box is at V55 and that method only arrives with V63, so it deletes nothing there. The other migrations add tables or columns, or widen constraints.

## 2. Decisions

Decided by Shahriar on 3 November 2026, except D4, which is the default unless he says otherwise.

| ID | Decision |
|---|---|
| D1 | **Exam content on the running VPS site stays; every account except the Master Admin goes.** Exams, questions, the package, categories and question papers carry into the new deployment. The database is migrated in place, so nothing is imported. The other 10 accounts are deleted in phase 6, with everything that belongs to them. All 10 are demo accounts, including the `@gmail.com` ones |
| D2 | **Students are not told about the site before live payments.** The address stays private. On the sandbox store a test card "pays" and gives real access to a paid exam |
| D3 | **The existing Google client.** Shahriar adds the new callback in Google Cloud and sets the app to In production. No code change is needed: the API reads the client from the env file |
| D4 | The old hosts redirect to the new ones in phase 7. Their certificates stay as the way back |
| D5 | **The Master Admin keeps the account and email on the VPS.** Nothing is rebuilt |
| D6 | Certificate expiry mail goes to `shahriaranany6@gmail.com` |

## 3. Who does what

**Shahriar:** the Google Cloud Console steps, and every edit of `/etc/coaching-api.env` (secrets stay out of chat, Git and this file).

**Agent:** builds, uploads, Nginx, Certbot, the web unit, checks and the deploy record. Every server write is shown before it runs, as in [vps-staging-deploy.md](vps-staging-deploy.md).

Use Windows OpenSSH from PowerShell (`ssh exam-kvm`). Git Bash `ssh` is refused by the box.

## 4. Order of work

Each phase ends with a check. Stop at a failed check, and do not start the next phase.

### Phase 0. Make the commit releasable (PC)

1. The full API suite passes on the release commit, run from a worktree of that exact commit. The web suite passes too. Only the known `BookOrderIT` and `CoursePurchaseIT` failures may be accepted, and neither failed in the last runs.
2. Clear the tree: `release-build.sh` refuses a dirty one, and `docs/Client Request/MCQ template/` is untracked. List that folder in `.git/info/exclude`, so the client's file and the placeholder explanations stay out of Git.
3. Commit this plan.

Check: `git status --porcelain` is empty.

### Phase 1. Back up, rehearse, and release the code on today's hosts

The code change and the host move stay separate, so a failure has one cause. The exam content must survive (D1), so the 13 migrations run on a copy first.

1. Dump the database and archive `/var/lib/coaching-api/files` into `/var/backups/coaching/`. Copy both to the PC, outside the repo. Keep the running jar and website.
2. Build the jar with `mvn -DskipTests clean package` from the clean commit. Record its SHA-256.
3. **Rehearse on the PC.** Restore the dump into a new local database, `coaching_vps_rehearsal`. Count users, exams, questions, attempts, answers and orders. Start the new jar against it with seeding off, and let Flyway go from 55 to 68. Count again. Sign in, open a past result, a leaderboard and the payments list. Every count must match. Stop here if one does not. Keep this copy for the phase 6 rehearsal, then drop it.
4. Build the website with today's hosts, under Git Bash:

   ```bash
   SITE_URL=https://srv2089893.hstgr.cloud STAFF_URL=https://admin.187-52-115-111.nip.io \
     bash web/scripts/release-build.sh <out.tgz>
   ```

5. Install the jar as `deploy:deploy 640` and restart `coaching-api`. Watch Flyway go from 55 to 68.
6. Run `web/scripts/deploy-web.ps1 -Package <out.tgz> -DryRun`, then without `-DryRun`.
7. Add a nightly job for a database dump and a file archive, keeping 14 days.

The Google button is on in this build and the API has no Google values yet, so the button shows an error on the old hosts until phase 5. They are not in student use, so this is acceptable.

Check: Flyway reports 68. The counts on the box match the ones taken before the release, and an existing question PDF and result still open. Sign in on both old hosts. Publish a test draft, upload the Word file, see the header in a sitting. The preflight passes with `MODE=sandbox` and the old hosts. A manual run of the backup job writes a dump that `pg_restore --list` can read.

### Phase 2. Prepare Google (Shahriar, Google Cloud Console)

Nothing here affects the running site.

1. In the existing client (D3), confirm the type is **Web application**.
2. Add this authorized redirect URI, character for character:

   `https://exam.symamlaw.com/api/v1/auth/google/callback`

   Keep the localhost one for development.
3. On the consent screen set the app name and logo, support email, and links to the privacy policy and terms. Add `symamlaw.com` to the authorized domains.
4. Set publishing status to **In production**. In Testing, only listed test users can sign in. This flow asks for `openid`, `email` and `profile` only, which normally needs no Google review. If the console asks for verification, do that first.

Check: the redirect URI is saved and the status reads In production.

### Phase 3. Nginx and certificates

1. Install `docs/exam-site/deploy/nginx/exam.symamlaw.com` and `admin.exam.symamlaw.com` into `sites-available`, link them, run `nginx -t`, reload.
2. Set the expiry email on the Certbot account, which has none today: `certbot update_account --email shahriaranany6@gmail.com`.
3. Issue one certificate per host, then test renewal:

   ```bash
   certbot --nginx -d exam.symamlaw.com --redirect
   certbot --nginx -d admin.exam.symamlaw.com --redirect
   certbot renew --dry-run
   ```

Check: both hosts load over HTTPS with a valid certificate, HTTP redirects to HTTPS, and the old hosts still work. Signing in on the new hosts may fail until phase 4. That is expected.

### Phase 4. Move the app to the new hosts (one sitting)

The new website build comes first, so the wait between steps is as short as possible.

1. Build from the same commit, under Git Bash:

   ```bash
   SITE_URL=https://exam.symamlaw.com STAFF_URL=https://admin.exam.symamlaw.com \
     bash web/scripts/release-build.sh <out.tgz>
   ```

2. Back up `coaching-web.service` and copy the env file to `coaching-api.env.before-hosts`.
3. Set `SITE_URL` and `STAFF_URL` in the unit, then `systemctl daemon-reload`. No restart yet.
4. Shahriar edits the env file:

   | Name | Value |
   |---|---|
   | `WEB_ORIGIN` | `https://exam.symamlaw.com,https://admin.exam.symamlaw.com` |
   | `SSLCZ_CALLBACK_BASE_URL` | `https://exam.symamlaw.com` |

   The student host must be first. Google sign-in and the payment return both go to the first origin.

5. Restart `coaching-api`, then run `deploy-web.ps1`. Its box-side check refuses a bundle that does not match the unit.

Check: the preflight with default hosts passes. A student signs in on the student host and staff on the admin host, and both stay signed in after a reload and a new tab. The student host sends `/staff` to the admin host, and the admin host sends `/register` back. An account's password reset mail links to the right host. Everyone signs in again once, because the session cookie belongs to the old host.

### Phase 5. Turn on Google

1. Shahriar adds these to the env file:

   | Name | Value |
   |---|---|
   | `GOOGLE_CLIENT_ID` | the existing client's id, as in the PC's `api/.env` |
   | `GOOGLE_CLIENT_SECRET` | the existing client's secret, as in the PC's `api/.env` |
   | `GOOGLE_REDIRECT_URI` | `https://exam.symamlaw.com/api/v1/auth/google/callback` |

   Leave `GOOGLE_POST_LOGIN_PATH` unset, so it keeps `/login/google`.

2. Restart `coaching-api`.

Check, in a private window on the student host:

| Case | Expected |
|---|---|
| New Google account, from Register | Back on the site signed in, asked for a phone once. Reload keeps the session |
| Same account again, from Login | Signed in, no phone prompt |
| Existing student whose email matches | Linked to the same account, purchases and results intact |
| Admin or instructor email | Refused with the message for `GOOGLE_ROLE_NOT_ALLOWED`, no session |
| Cancel on Google's screen | Back on the login page with the cancelled message |
| New Google student tries to take or buy an exam with no phone | Asked for the phone first |
| Admin host | No Google button |

Then run one sandbox payment on the student host. Pass means `sslcommerz validation accepted`, `live exam payment granted`, and a `POST .../ipn` with status 200 in the log. It comes before phase 6, so the test account and order are removed with the rest.

Failure guide: `redirect_uri_mismatch` means the Console and `GOOGLE_REDIRECT_URI` differ. `access_blocked` or a test-user message means the app is still in Testing. A 503 means one of the three values is empty. Returning signed out means the refresh cookie was not kept, the main risk named in [04-google-sign-in.md](04-google-sign-in.md), section 14. Stop and report that one, because the fix is a code change.

### Phase 6. Remove every account except the Master Admin

This is a hard delete, not a soft delete, and only a dump brings it back. It also removes any account made during the Google and payment tests.

| Goes with the 10 accounts (counts of 3 November) | Stays |
|---|---|
| 13 attempts, with their answers and answer confirmations | 30 exams and their questions and options |
| 5 sandbox orders and 5 grants | The package and categories |
| 18 notices, 7 student profiles, 3 admin profiles, favourites, sign-in tokens, one-time codes, queued mail | Question papers and their PDFs |
| 2 instructor assignments, and the instructor named on 1 exam (cleared, the exam stays) | The Master Admin, and the announcement and change history written by that account |
| 8 answer scripts and 3 marked PDFs on disk | The audit log, which is append-only. Its 10 rows keep the old ids and have no link to `users` |

Results, leaderboards and the payment totals start empty.

1. Write `remove-non-master-accounts.sql`. `demo-cleanup.sql` is not used, because it matches only the seed's fixed ids and would also delete the seeded exams. The new script runs in one transaction and stops at the first error. It keeps the Master Admin by role, and refuses to run unless exactly one such account exists.
2. Rehearse it on `coaching_vps_rehearsal`. Exam, question, option and package counts must not move, and `users` must end with one row. Sign in as the Master Admin and open an exam.
3. On the box, dump first and copy the dump to the PC. List the attempt files before the delete, because the rows naming them are about to go.
4. Run the script. Remove the listed files.
5. Shahriar removes `MASTER_ADMIN_PASSWORD`, `MAIL_TEST_ENABLED`, `SEED_DEMO` and `SEED_DEMO_PASSWORD` from the env file, and keeps the other `MASTER_ADMIN_*` lines. The Master Admin already exists, so the startup step does nothing. Restart `coaching-api`.
6. From here the Master Admin creates any real System Admin or instructor from the admin site.

Check: `users` has one row, the Master Admin. The Master Admin signs in on the admin host, reloads and stays signed in. Exam counts match phase 1. A published written exam still opens its question PDF. Payments, results and notices are empty. A new student can register, by password and by Google.

### Phase 7. Finish

1. Point the old hosts at the new ones with a 308, per D4. Keep their certificates.
2. Remove old `/opt` releases and jars only after a week of clean running, keeping the last two.
3. Record the commit, jar SHA, build id and checks in `docs/exam-site/reports/client-review-deploy.md`. Update `current-api.md`, `decisions.md` and the status line of `live-domain-and-sslcommerz.md`.

Check: the full preflight passes, `certbot.timer` is active, and the first nightly backup exists.

## 5. Rollback

| From | How | Time |
|---|---|---|
| Phase 1, API | Put the kept jar back and restore the dump. The old jar cannot run on the V68 schema, so the dump is required | 10 min |
| Phase 1, website | Move the `coaching-web.before-*` release back, restart. The deploy script does this itself if the health check fails | 1 min |
| Phase 3 | Remove the two links from `sites-enabled`, `nginx -t`, reload | 1 min |
| Phase 4 | Restore `coaching-api.env.before-hosts` and the unit, `daemon-reload`, put the previous website back, restart both | 5 min |
| Phase 5 | Remove the three `GOOGLE_*` lines, restart. The button then shows an error until a build with it off is released | 2 min |
| Phase 6 | `pg_restore --clean` from the dump, with Shahriar's OK | 5 min |

Accounts created through Google after phase 5 are lost if the database is restored from an earlier dump.

## 6. Known risks

- **Old site:** nothing here touches it, and its DNS records stay as they are. `symamlaw.com` timed out from the PC on 3 November. Check this separately, because this plan does not change it.
- **Sandbox payments on a real address:** see D2.
- **Sample content stays visible:** the published sample exams stay on the site (D1). Before students are told about it, withdraw what should not be public.
- **One account left:** after phase 6 the Master Admin is the only staff account, so its mailbox and password are the only way in. Written papers cannot be marked until an instructor is created and assigned.
- **Word explanations:** the file with explanations holds placeholders. Do not upload it to a real exam.
- **Google email linking:** a verified Google email matching a student links silently, as decided. Admin and instructor emails are refused.
- **Time:** the phases that run on the box need about 2 to 3 hours in one working session, plus Google Console time. Pick a time with no live exam.
