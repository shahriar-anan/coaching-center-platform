# Release plan: instructor pay made simple, admission fixes and join codes off (Flyway V107)

Status (24 November 2026): **plan only. Nothing is deployed.** Written from the live record in
`../reports/client-review-deploy.md` (last release 22 Nov, `95705ad`, Flyway 106), the release rules of
`media-release-deploy.md` and `direct-enrolment-release.md`, and the scripts in `web/scripts/`. Every step on the box needs
Shahriar's go.

## 1. What is being released

Branch `demo/client-review`, 7 commits after the live release `95705ad` (the last live code):

| Commit | What |
|---|---|
| `eaec62d`, `ce5aea8` | Docs only (22 Nov record, offline-hidden incident). |
| `afb9da0` | `release-build.sh` refuses to build without `OFFLINE_ADMISSION=1|0`. |
| `25048c1` | Admission setup leaves the admitted date for the admin to enter (the 18 Nov fix). |
| `b6b790e` | Same-batch phone/email clash counts only students admitted to the batch. |
| `85e7a1c` | Onboarding admits a student with an account at once; join codes off by default (`OFFLINE_JOIN_CODE`). |
| `2d78524` | Instructor pay made simple: Master Admin records payments by hand; class pay removed; V107. |

Schema: **one migration, `V107__instructor_pay_simple.sql`**, additive only: a `kind` column (default `OTHER`), a nullable
`class_count`, `reason` made nullable, one index. No existing row is changed.

Behaviour changes students and staff will notice:
- The Master Admin's **Onboard** admits account holders at once. No join code is sent. Codes already out keep working.
- **System Admins lose** Instructor payments, Offline instructors and every instructor pay action. They can still see classes
  and routines.
- **Routine upload no longer creates instructors.** Unknown names stay on the class as written.
- **Finance, By instructor** shows payments and amount paid only (no owed or waiting).
- **Classes carry no pay.** No rates, no Instructor rates tab, no Amount column, no Record payment on a class.

## 2. Blockers: must be closed before the day

| # | Blocker | State today | How to close |
|---|---|---|---|
| B1 | Full API suite on the release commit | **Not run.** Only the offline, finance and audit classes ran (231 tests, all pass). The last full run was 1,299 on `95705ad`. Permission, notification and route-list changes could break tests outside those classes. | Fresh database, full suite, on Shahriar's go. Expect 1,299 + the new `OfflineInstructorPayIT` (7) and the changed classes. |
| B2 | Web tests after the last edits | The full web suite passed (2,217) **before** the last label and layout edits. Those edits are in `2d78524`, checked only by typecheck and lint. | Run `tests/instructor-pay` and `tests/batch-pay` at least, then the full web suite, on Shahriar's go. |
| B3 | Browser check | Last full run: 18 steps passed, 0 console errors. Layout and label fixes after it are not yet re-checked (see section 6). | Re-run `check.js` on the dev servers; review the screenshots. |
| B4 | Waiting approvals on live | Unknown. After the release the System Admin's waiting **class** payments, **not-taken** requests and **other** payments can no longer be decided on any screen. | Read-only counts on the box (section 5.1). Clear them on the current pages first. Release only at zero. |
| B5 | Production web build | The release build is made from a clean commit, with `OFFLINE_ADMISSION=1`. | `release-build.sh` on the PC, after B1 to B3. |
| B6 | Branch pushed | `origin/demo/client-review` is 4 commits behind. The live code exists only on the PC. | Push before the release, as the direct enrolment plan asked (Shahriar's go). |

Not blockers, but decide before the day:
- **Mail still not delivered (Brevo).** Activation codes for new accounts and reset codes are handed over by the office. The
  join-code change reduces that load (account holders get no code). Activation codes for new accounts are unchanged.
- **Open live checks from 17 and 22 Nov** (Master Admin sign-in needed): the new staff pages with real data, a real onboarding,
  opening a large script in the admin site. These are not caused by this release, but this release touches the same pages.

## 3. Risk analysis

| Risk | Why it matters | Mitigation |
|---|---|---|
| **Rollback of the API alone** | The old jar (`95705ad`) runs on V107: its inserts omit `kind` (default `OTHER`), its `p.*` selects ignore the new column, and it always writes `reason`. This is reasoning, not yet proof. | Prove it on the rehearsal copy (section 4, step 7), as every earlier release did. |
| **Rollback of the web alone** | The old web calls the new API's removed routes and reads the new Finance shape. | Roll back the web and the API together. Never one without the other. |
| **Web build without the offline flag** | 22 Nov: the offline section vanished for about 15 hours. | `OFFLINE_ADMISSION=1` is required by the script; check `/admission` after the swap. |
| **Waiting approvals orphaned** | A System Admin's class payment waiting at release cannot be approved or rejected later. | B4. |
| **Onboarding behaviour change** | An account holder is admitted at once, with no code. A pending join code no longer re-sends. | The client asked for it. Check one real onboarding with the Master Admin. Existing codes still work. |
| **Finance figures change shape** | By instructor loses owed, pending and class counts. Totals for expenses are unchanged in the code. | Note the expense totals before and after (section 5.3). |
| **Classes' amounts and payments disappear from the batch screens** | Old paid and not-taken classes stay locked, and their payments stay in history. | Live check: an old paid class shows "Read only" and its payment in the instructor's history. |
| **Permission change for System Admin** | Runtime checks come from the database roles, so no cache needs flushing. | Live check: a System Admin is refused the pay pages (403) and still reads classes. |
| **Old notices for class payments** | Notice kinds stay in the enum so old notices still render. Nothing writes the digest kinds any more. | Check one old notice on the notice page after the release. |
| **Route list (AuditLogIT)** | Adding or removing routes without updating the list fails the audit test. | Covered by B1. |

## 4. Rehearsal on a copy (PC, before the day)

Follow `media-release-deploy.md` section 4 with these changes:

1. Dump the live database (read-only on the box), check it with `pg_restore --list`, copy it to `F:\exam-vps-backups\`, and
   compare SHA-256 on both sides.
2. Restore into a new local database `coaching_rehearsal_pay`. Never the sample or dev databases.
3. Record row counts for `offline_instructor_other_payments`, `offline_class_payments`, `offline_classes`, `payment_edits`,
   `offline_admissions`, `staff_ledger` and `users`.
4. **Protect real people on the copy:** `SPRING_PROFILES_ACTIVE=dev`, `--app.exam-jobs.scheduled=false`,
   `STAFF_PAY_JOBS_ENABLED=false`, `MAIL_*` and SSLCommerz empty, every `SEED_*` off. Use a throwaway payout key if the sealed
   columns refuse to start, as the direct enrolment plan says.
5. Start the new jar. Expect exactly one migration applied (V107), with no failures.
6. Smoke on the copy: record a payment, edit it, void it; open an old class payment in history; the Finance summary and by-instructor
   list; a System Admin's pay routes return 403; a routine upload with an unknown name creates no instructor.
7. **Rollback proof:** start the previous jar, the one live now (`F:\cc-releases\api-95705ad.jar`, SHA-256 `920ec382…`), on the same
   upgraded copy. It must start on V107, the old notice list must load, and the classes list
   must load. Then start the new jar again: clean start, the rows from step 6 are still there.
8. Drop the database and its files. Record the results in the release record.

## 5. On the day

Use one stamp `<stamp>` (`YYYYMMDD-HHMMSS`) for every backup, label `pay`. Everything on the box goes through Windows OpenSSH
(`exam-kvm`), detached with `nohup` where it matters.

### 5.1 Read-only checks (no change)

1. **Quiet hour** (rule 4 of the earlier plans): no sitting `IN_PROGRESS` (`live_exam_participants`), no payment `INITIATED` in
   the last 15 minutes (`live_exam_orders`), no exam due within 3 hours.
2. **B4, waiting approvals must all be zero:**
   ```sql
   select count(*) from offline_class_payments where status = 'PENDING';
   select count(*) from offline_class_decisions where status = 'PENDING';
   select count(*) from offline_instructor_other_payments where status = 'PENDING';
   select count(*) from payment_edits where status = 'PENDING' and kind in ('CLASS', 'OTHER');
   ```
   Any non-zero result: stop. The Master Admin clears them on the current pages first.
3. Flyway at 106, none failed. `systemctl status coaching-api coaching-web nginx --no-pager`. `df -h /`.
   `sha256sum /opt/coaching-api/api.jar`.
4. Record for the comparison after: Finance expense totals for this month and all time (from `/api/v1/finance/summary`), and
   the counts in section 4 step 3.

### 5.2 Backups

1. `sudo -u postgres pg_dump -Fc coaching > /var/backups/coaching/coaching-pre-pay-<stamp>.dump`; `pg_restore --list` check; copy
   to `F:\cc-releases\backup-pre-pay-<stamp>\` and compare SHA-256.
2. Uploaded files: `sudo tar czf /var/backups/coaching/files-pre-pay-<stamp>.tgz -C /var/lib/coaching-api files`; copy and check.
3. `/etc/coaching-api.env` to `/etc/coaching-api.env.before-pay-<stamp>`.
4. The jar to `/opt/coaching-api/api.jar.before-pay-<stamp>`. The website is kept by the deploy script.

### 5.3 Build (PC)

1. Commit clean; push (B6).
2. API jar: `./mvnw -q -DskipTests package` in `api/` after B1 passes. Copy to `F:\cc-releases\api-<commit>.jar`; record SHA-256.
3. Website, in Git Bash, after B2 and B3 pass:
   `SITE_URL=https://symamlaw.com STAFF_URL=https://admin.symamlaw.com OFFLINE_ADMISSION=1 bash web/scripts/release-build.sh <out.tgz>`.
   Record SHA-256. The script runs `release-checks.sh` (API base empty, both host URLs baked in, no localhost).

### 5.4 Deploy

1. **Environment:** add `OFFLINE_JOIN_CODE=false` explicitly to `/etc/coaching-api.env`. The code default is already false, but the
   live file should say so, as with the other switches. Check that `OFFLINE_ADMISSION_ENABLED=true` is still there.
2. Website dry run: `powershell -NoProfile -File web\scripts\deploy-web.ps1 -Package <out.tgz> -DryRun`.
3. API: `scp` the jar to `/tmp/api.jar`; check SHA-256; `sudo install -o deploy -g deploy -m 640 /tmp/api.jar /opt/coaching-api/api.jar`;
   `sudo systemctl restart coaching-api`; watch `journalctl -u coaching-api -f`. Expect V107 applied, the app started, no errors.
4. Website: `powershell -NoProfile -File web\scripts\deploy-web.ps1 -Package <out.tgz>`. The previous release stays at
   `/opt/coaching-web.before-<stamp>`.

### 5.5 Live checks, read-only first

1. Preflight: `docs/exam-site/deploy/live-payment-preflight.sh` with `MODE=live SITE=symamlaw.com STAFF=admin.symamlaw.com`. All pass.
2. Public, signed out: `https://symamlaw.com` and `/admission` load (not the "page not found" page); the home menu links to
   `/admission`; `/packages` loads.
3. Staff (Master Admin, with the office): **Instructor payments** lists payments; **Offline instructors** shows paid this month
   and batches; a payment recorded as a ৳1 test on a test instructor, edited, then voided; Finance by instructor shows payments.
   Remove the test records afterwards with a script shown first.
4. System Admin: no instructor pay items in the menu; `/staff/offline-instructor-payments` refused; batch classes still open.
5. An old paid class shows "Read only" and its payment in the instructor's history.
6. A real onboarding with the Master Admin, on a test admission Shahriar owns: no join code is sent; the student is admitted.
7. `journalctl -u coaching-api --since '-15 min'`: no errors, no payout number, no secret. API 0 5xx.
8. Counts unchanged against section 5.1 step 4. Finance expense totals unchanged.

## 6. Browser check status

**B3 closed (24 November).** The check ran on a fresh database (`cc_browser_check`, dropped and rebuilt for the run) against the
dev servers, after the last layout and label fixes. All 18 steps pass, with 0 browser console errors. Screenshots reviewed: the
instructor page, Record payment with the aligned fields, Payment history with the void and the "Corrected on" row, the edit dialog for
an old class payment, the batch Classes tab, the Overview without money, Finance, the Bangla page, and the System Admin's refused
page and Change-instructor dialog.

Two notes from the run:
- The check changes data (it records, edits and voids), so it passes only on a fresh database. Re-running on the same database
  gave false failures: the totals and the Edit step assumed the first run's state. Use a fresh database for every run.
- In the Change-instructor dialog, the refusal line for a name not in the list appears twice (as the hint and as the error).
  Cosmetic; fix or accept before release.

## 7. Rollback

- **API:** stop `coaching-api`; copy `api.jar.before-pay-<stamp>` over `api.jar`; start it. Flyway stays at V107 with the new column
  and nullable `reason`; the old jar runs on it (rehearsal proof, section 4 step 7).
- **Website:** stop `coaching-web`; move `/opt/coaching-web` aside; put `/opt/coaching-web.before-<stamp>` back; start it.
- **Both together.** One without the other breaks the pay pages or Finance.
- Payments recorded after the release stay in the table and show in the old build as "other payments" with their description.

## 8. Decisions for Shahriar

| # | Point | Recommendation |
|---|---|---|
| 1 | Release window | Quiet hour, Dhaka night, finished before 06:00. |
| 2 | Run the full API suite and the web tests (B1, B2) | Yes, before the build. These are the tests he asked for earlier, and the release depends on them. |
| 3 | Clear waiting approvals (B4) | Yes. The Master Admin clears them on the current pages; the release waits at zero. |
| 4 | Push the branch (B6) | Yes, before the build. |
| 5 | Keep join codes off | Yes, as the client asked. Turning them back on is one setting and a restart. |
| 6 | Test records after the live check | Remove them, with a script shown first. |
| 7 | Bangla copy | Read the new instructor pay and admission strings before the release. They were written in natural Bangla but not reviewed by a native reader. |

## 9. Not in this release

- Dropping the old class pay tables (`offline_class_payments`, `offline_class_decisions`, `offline_batch_instructor_rates`) and the
  class amount columns: a later cleanup, after history is confirmed.
- The V107 "class replaced by hand" work, which stays in `git stash` (`stash@{0}`).
