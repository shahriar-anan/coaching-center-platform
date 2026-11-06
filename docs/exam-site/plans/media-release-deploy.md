# Plan: release of the media features and the payout encryption (Flyway V75 to V78)

Status: planned, not started. Written 7 November 2026 for Shahriar and the agent who runs the release. Nothing in this plan
has been run on the live server. The site takes real money (live SSLCommerz store since 5 November), so every step on the
box needs Shahriar's go, and section 2 governs the whole release.

## 1. What ships

Release commit: `demo/client-review` at `de1c901` (the merge of `integ/media-features`). Live today: `fdefac8` (5 November),
Flyway 74.

| Part | What it adds | Schema |
|---|---|---|
| Payout encryption (staff pay phase 9) | Payout account numbers sealed with AES-256-GCM; plain columns kept and written too | `V75`: nullable columns on `staff_payout_accounts` |
| Notice photos and PDF (F1) | Up to 6 photos and one PDF per public notice; one photo shown at a time | `V76`: new table `site_notice_files` |
| Student profile picture (F2) | Optional picture on `/account` and in the header; shown beside the student's reviews | `V77`: new table `student_photos` |
| Package routine PDF (F3) | Optional routine PDF on the package's Routine tab | `V78`: new table `live_exam_module_routines` |
| Reviews | Reviewer pictures on public reviews, featured reviews and the staff queue | none |
| Admin | "Signed up" date on the staff student page | none |
| Website only | Discount tag on package cards, dark-mode fix, browse starts on the category's own exams | none |

Both the API and the website change. The API goes first; the website follows in the same window.

Tests at the release commit: the full API suite (973 tests, 0 failures, on a fresh database) and the full web suite (246
files, 1,584 tests) passed before the last two web edits. The last edit, which hides the next button for a single notice
photo, has not been run. Step 3.1 runs the notice tests once before the build.

## 2. Rules for this release

1. **Production data is kept.** `V75` adds nullable columns and changes no row. `V76` to `V78` create new tables only. No
   existing row is changed by any migration. The plain payout columns are not dropped in this release.
2. **Backup first, on the day.** A fresh `pg_dump -Fc coaching` and an archive of the uploaded files in
   `/var/lib/coaching-api/files`, both checked and copied to the PC outside the repository, before anything changes.
3. **Rehearse on a copy first** (section 4). The release does not start until the rehearsal passes.
4. **A quiet hour.** No open sitting (`live_exam_participants` with status `IN_PROGRESS`) and no payment started in the last
   15 minutes (`live_exam_orders` with status `INITIATED`). The API restarts take about 20 s each, and this release
   restarts it twice.
5. **The payout key never touches the repository, the database, the logs, a chat or a screenshot.**
6. **Every step on the box needs Shahriar's go**, and Shahriar approves the release in writing first, because it changes a
   live table that holds payment data (staff pay plan, phase 9).

## 3. Before the day

### 3.1 On the PC

1. Run the notice tests once on the release commit (the single-photo change is untested):
   `npx vitest run tests/notices tests/notices-all` in `web/`. Only with Shahriar's go.
2. Build the jar from `de1c901`: `./mvnw -q -DskipTests package` in `api/`. Record its SHA-256.
3. Build the website: `SITE_URL=https://symamlaw.com STAFF_URL=https://admin.symamlaw.com bash web/scripts/release-build.sh
   <out.tgz>`. The script runs `release-checks.sh` (empty API base, both host URLs baked in). Record the package SHA-256.

### 3.2 The payout key (Shahriar)

1. Generate 32 random bytes, base64 encoded, with a cryptographic generator, for example in PowerShell:
   `[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`.
2. Store it in the password manager, and keep a second copy off the server. A lost key means the sealed numbers cannot be
   read again; the plain columns are the only fallback while they exist.
3. Key version is `1`.

### 3.3 Decisions to confirm

| Point | Current state |
|---|---|
| Release window | To be chosen by Shahriar: a quiet hour, Dhaka night |
| Reviewer pictures are public | Decided 7 November: anyone sees a reviewer's picture beside an approved review. Tell students once, since earlier the picture was private |
| EXIF orientation bug | Not fixed. Portrait phone photos can be stored sideways (covers, notices, avatars). Ship as it is, or fix first |
| QA sign-off | Device QA against the checklist `reports/media-features-qa.md`, with every fail fixed or accepted |

## 4. Rehearsal on a copy (PC, before the day)

1. Dump the live database (read-only on the box): `sudo -u postgres pg_dump -Fc coaching` to
   `/var/backups/coaching/coaching-pre-media-rehearsal-<stamp>.dump`, check it with `pg_restore --list`, copy it to
   `F:\exam-vps-backups\`, and compare the SHA-256 on both sides.
2. Restore it into a new local database `coaching_rehearsal_media` on the PC. Never the sample or dev databases.
3. Before: record row counts for `staff_payout_accounts`, `staff_ledger`, `live_exam_orders`, `users`, `site_notices`,
   `live_exam_modules` and `live_exam_reviews`, and the masked number of every payout account.
4. Start the new jar against the copy with a throwaway key (not the production key), `PAYOUT_BACKFILL_ENABLED=true` and all
   `SEED_*` off. Expect Flyway to apply exactly 4 migrations (75 to 78) and the backfill to log only counts.
5. After: the row counts match; every payout account has sealed values that open back to the plain value; the masked numbers
   are unchanged; the Master Admin sees the full number on the account screen.
6. Restart with the backfill off. Then start the **previous jar** (`fdefac8`) against the same copy: it must start (Flyway
   ignores the newer migrations) and show payout accounts correctly. This is the rollback path.
7. Smoke the new jar on the copy: the public package and notice lists, a draft notice with a photo and a PDF, a routine PDF on
   a draft package, a student picture, the staff reviews page.
8. Drop `coaching_rehearsal_media` and its file folder afterwards. Record the results in the release record.

## 5. On the day

Everything on the box goes through Windows OpenSSH (`exam-kvm`), as in earlier releases. Use one stamp for every backup:
`<stamp>` = `YYYYMMDD-HHMMSS`.

### 5.1 Checks and backups (read-only, then backups)

1. No open sitting and no recent payment start (rule 4). If either exists, wait.
2. Count payout accounts with a number. Record it (it was 1 on 7 November).
3. `df -h /`, `systemctl status coaching-api coaching-web nginx --no-pager`, `sha256sum /opt/coaching-api/api.jar`.
4. Backups: the database dump as in section 4 step 1 (named `coaching-pre-media-<stamp>.dump`), copied to the PC; the
   uploaded files with `sudo tar czf /var/backups/coaching/files-pre-media-<stamp>.tgz -C /var/lib/coaching-api files`,
   copied to the PC; `/etc/coaching-api.env` to `/etc/coaching-api.env.before-media-<stamp>`; the jar to
   `/opt/coaching-api/api.jar.before-media-<stamp>`. The website is kept by the deploy script.

### 5.2 The website, staged (dry run)

`powershell -NoProfile -File web\scripts\deploy-web.ps1 -Package <out.tgz> -DryRun`. This stages and checks the release on
the box without swapping it.

### 5.3 The API

1. Add to `/etc/coaching-api.env`: `PAYOUT_ENCRYPTION_KEY=<key>`, `PAYOUT_KEY_VERSION=1`, `PAYOUT_BACKFILL_ENABLED=true`.
   Edit on the box only; do not echo the key.
2. `scp` the jar to `/tmp/api.jar`, check its SHA-256 against step 3.1, then
   `sudo install -o deploy -g deploy -m 640 /tmp/api.jar /opt/coaching-api/api.jar`.
3. `sudo systemctl restart coaching-api` and watch `journalctl -u coaching-api -f`: Flyway applies 4 migrations (75 to 78),
   the backfill logs its counts, the app starts. If it stops, see section 6.
4. Check the backfill: every payout account with a number now has sealed values (count only, no values printed).
5. Set `PAYOUT_BACKFILL_ENABLED=false`, restart again, check the log is clean.

### 5.4 The website

`powershell -NoProfile -File web\scripts\deploy-web.ps1 -Package <out.tgz>`. The previous release is kept as
`/opt/coaching-web.before-<stamp>`.

### 5.5 Checks on the live site

1. The preflight: `MODE=live SITE=symamlaw.com STAFF=admin.symamlaw.com` with `docs/exam-site/deploy/live-payment-preflight.sh`.
   All checks pass.
2. Public, signed out: `https://symamlaw.com` loads; `/packages` shows the live package with its discount tag and end date;
   the notice board loads; a package page's Routine tab is unchanged when there is no PDF.
3. Staff (Master Admin): the payout account screen shows the same masked number and the full number as before; the staff
   reviews page loads; a student's page shows "Signed up".
4. A draft notice with one photo and one PDF, seen in the staff preview, then deleted. It is never published.
5. `journalctl -u coaching-api --since '-15 min'` has no errors and no payout number.
6. One SSLCommerz path still works: the checkout page opens for a priced exam (no payment needed).

### 5.6 Record

Add the release to `reports/client-review-deploy.md`: commit, jar and package SHA-256s, the Flyway result, the payout account
count and backfill result (counts only), backups with their stamp, the preflight result, and the rollback steps below.

## 6. Rollback

| Problem | Action |
|---|---|
| The API does not start | Read the first `IllegalStateException`. A missing or bad key names itself: fix the env and restart. Otherwise put `api.jar.before-media-<stamp>` and `coaching-api.env.before-media-<stamp>` back and restart. The previous jar starts on the new schema (rehearsal step 6) |
| The website is broken | Stop `coaching-web`, put `/opt/coaching-web.before-<stamp>` back, start it. The API needs no change: the old website ignores the new fields |
| A payout number looks wrong | Roll the API back as above. The plain columns, kept current by dual write, are what the previous jar reads |
| Data damage | Restore `coaching-pre-media-<stamp>.dump`, only with Shahriar's approval, accepting that writes since the dump are lost |

**After a rollback of the API:** if a payout account is changed while the previous jar runs, its sealed values become stale,
and the backfill only fills empty ones. Before the new jar goes out again, clear the sealed columns of every account changed
since the rollback, so the backfill seals them again.

Files uploaded through the new features stay on disk after a rollback. They are harmless and are listed by the new tables.

## 7. After the release

- Device QA on the live site for anything the QA copy could not show, within the first day.
- The plain payout columns stay until Shahriar approves the verification (staff pay plan, phase 9, step 7). Dropping them
  is a later release with its own migration.
- Stop the local QA servers and drop `coaching_center_qa_media` when QA no longer needs them.
- Open follow-ups: the EXIF orientation fix (if not shipped), the 10 BDT minimum message from the live payment test.
