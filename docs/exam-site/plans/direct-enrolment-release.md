# Plan: release of direct enrolment installments and the package add-exams flow (Flyway V79 and V80)

Status (9 November 2026, 01:46 Dhaka): **released.** Live is `9bb0ec6`, Flyway 80. Record and rollback:
`../reports/client-review-deploy.md`, "Direct enrolment installments and package add-exams". Open: section 6.5 steps 4 to 6
(they need the Master Admin's sign-in), the browser walkthrough and regression checks in 4.2, and the three ESLint errors in the
record. Follows [media-release-deploy.md](media-release-deploy.md) and section 11 of
[direct-enrolment-installments.md](direct-enrolment-installments.md).

## 1. What ships

Release commit: `demo/client-review` at `8331118`, plus the commit that adds this plan (docs only). Live today: `80c1f0a`,
Flyway 78.

| Update | Commits | What it adds | Schema |
|---|---|---|---|
| Direct enrolment installments | `38d3b4b`, `87f0831` | Full price, installments or No charge on exams and packages; a receipt per payment; Installments page and notification tab; 06:30 Dhaka reminders; roster and direct grant can no longer both give access to a package; overlap warnings; exam-time email on direct enrolment | `V79`: 3 new tables. `V80`: 2 check constraints widened, 2 nullable columns on `student_notices` |
| Package add-exams flow | `3de942e`, `8331118` | Wide picker with filters; bulk add of up to 500 exams, staged as one revision on a published package; package page loads every exam; package card price wraps instead of being cut off | none |

Both the API and the website change. **API first, then website**: the old website works against the new API because `payment`
is optional, while the new website against the old API would look as if it worked and silently drop the payment.

No new setting is required. `INSTALLMENTS_DUE_SOON_DAYS` defaults to 3, and the reminder job follows
`app.exam-jobs.scheduled`, which is unset on the box and defaults to on.

## 2. Rules for this release

1. **Production data is kept.** No migration changes an existing row. V80 re-creates two check constraints with every existing
   kind kept.
2. **Backup first, on the day**: a fresh database dump and an archive of the uploaded files, checked and copied to the PC.
3. **Rehearse on a copy first** (section 4). The release does not start until the rehearsal passes.
4. **A quiet hour.** No open sitting and no payment started in the last 15 minutes. The API restarts once, about 20 s. Stay
   clear of the 06:30 Dhaka reminder job.
5. **Every step on the box needs Shahriar's go.** Read-only checks are the only exception.
6. **No real student gets a test email.** Test enrolments use an account Shahriar owns.
7. **Do not change the live package while testing.** A bulk add on a published package stages a revision. Test on a draft.

## 3. State of the live site

Read on the box on 9 November 2026 at 00:57 Dhaka, read-only. Repeat the changing values on the day.

| Check | Result | What it means |
|---|---|---|
| Jar SHA-256 | `905bfb4e…0aba0` | The `80c1f0a` release, as recorded |
| Flyway | 78, success | V79 and V80 are the next two |
| New tables already present | 0 | V79 will not collide |
| `student_notices_kind_check` | The 13 kinds V80 copies | V80 loses no kind |
| `exam_outbound_mail_kind_check` | The 5 kinds V80 copies | V80 loses no kind |
| Roster rows / live manual grants | 0 / 0 | The new one-way rule has nothing to conflict with today |
| Students, orders, grants, notices, mail | 90, 5, 3, 33, 11 | Small tables, so V80 is instant |
| Packages with exams | 1 package, 4 exams | Test the bulk add on a draft, not on this one |
| Disk, memory | 8% of 96 GB used, 6.2 GB available | Enough |
| Services, API errors in 24 h | All 4 active, 0 errors | Healthy |
| Mail settings | Present, including `MASTER_ADMIN_EMAIL` | Receipts and Master Admin emails have what they need |

## 4. Before the day

### 4.1 Decisions for Shahriar

| # | Point | Recommendation |
|---|---|---|
| 1 | Release window | A quiet hour at Dhaka night, finished before 06:00 |
| 2 | Bangla copy, an exit criterion in plan 10.5 | Read it before release. The new installment, overlap and picker strings have not been reviewed |
| 3 | Test account for the live check (section 6.5) | An account with a verified email Shahriar owns |
| 4 | Remove the test records after the live check | Yes, with a script written from the actual rows and shown first |
| 5 | Push `demo/client-review` to GitHub | Yes, before the release. `origin` is 24 commits behind, so this code exists only on the PC |

### 4.2 Gates on the PC

Tests run only on Shahriar's say-so. These are the gates, in order.

| # | Gate | State today |
|---|---|---|
| 1 | Full API suite on the release commit, fresh database | Not run on this commit. Last full run: 1,022 tests passed, before `PackageEnrolmentRulesIT` and the add-exams tests. Those new classes passed when run on their own |
| 2 | Full web suite | Not run on this commit. Last full run: 255 files, 1,735 tests passed, before the add-exams work and the card edit |
| 3 | Web type check and ESLint | Type check passed for the add-exams work. ESLint has not been run on direct enrolment; for the add-exams work it is unconfirmed |
| 4 | Browser walkthrough, plan 10.3 | Not done for section 14 or the picker. Cover: enrol with each payment type on an exam and a package, a second payment, void, receipt PDF, the Installments page and tab, the overlap warning, the picker on a draft and a published package, reorder past 100 exams, a long price on a package card, phone width, Bangla |
| 5 | Regression, plan 10.4 (R1 to R12) | Not done. R1 and R4 need the rehearsal copy or a live check |

Known gap: **no test checks that the receipt email is queued or sent.** Gate 4 or the live check must cover it.

### 4.3 Builds

1. Commit this plan on `demo/client-review` and check that branch out. `release-build.sh` refuses a dirty tree.
2. Jar: `./mvnw -q -DskipTests package` in `api/`. Copy to `F:\cc-releases\api-<commit>.jar` and record its SHA-256.
3. Website, under Git Bash so the API base stays empty:
   `SITE_URL=https://symamlaw.com STAFF_URL=https://admin.symamlaw.com bash web/scripts/release-build.sh <out.tgz>`. Record its
   SHA-256. The script runs `release-checks.sh`.

## 5. Rehearsal on a copy (PC)

1. Dump the live database, read-only on the box: `sudo -u postgres pg_dump -Fc coaching` to
   `/var/backups/coaching/coaching-pre-installments-rehearsal-<stamp>.dump`. Check with `pg_restore --list`, copy to
   `F:\exam-vps-backups\`, compare SHA-256 on both sides. The PC's link to the box drops, so run box steps detached and retry.
2. Restore into a new local database `coaching_rehearsal_installments`. Never `coaching_center_sample` or `coaching_center_dev`.
3. Record row counts for `users`, `live_exam_orders`, `live_exam_grants`, `student_notices`, `exam_outbound_mail` and
   `staff_payout_accounts`.
4. **Protect the real students on the copy.** Start with `SPRING_PROFILES_ACTIVE=dev`, because the SMTP sender only exists under
   `staging` and `prod`. Add `--app.exam-jobs.scheduled=false` and `STAFF_PAY_JOBS_ENABLED=false`, leave `MAIL_*` and the
   SSLCommerz settings empty, and keep every `SEED_*` off.
5. **The payout key.** The copy holds sealed payout rows, and the app refuses to start with no key. Use a throwaway key with
   `PAYOUT_KEY_VERSION=1`, never the production key. If it is refused, clear the sealed columns on the copy only. The payout
   screen is not part of this rehearsal.
6. Start the new jar. Expect exactly 2 migrations applied (79 and 80). Row counts match step 3.
7. Smoke the new jar on the copy: enrol with installments and record a second payment, download the receipt, void a payment,
   the roster refusal in both directions, a bulk add on a draft package.
8. **Rollback proof.** Start the previous jar (`F:\cc-releases\api-80c1f0a.jar`) on the same upgraded copy, with the rows from
   step 7 and at least one queued installment email. It must start, students must still have access, the notice list must
   load, and the queued installment email must be skipped, not sent or stuck. The code says it is skipped
   (`ExamMailTexts.prepare` returns null for a kind it does not know); this step proves it.
9. Start the new jar again on the copy: clean start, the rows from step 7 are still there.
10. Drop the database and its files, and delete the local dump only if the on-the-day dump replaces it. Record the results here.

## 6. On the day

Everything on the box goes through Windows OpenSSH (`exam-kvm`), detached with `nohup` where it matters. Use one `<stamp>`
(`YYYYMMDD-HHMMSS`) for every backup. Use the label `installments`: `before-enrol` already exists on the box from an older
release.

### 6.1 Checks and backups

1. No open sitting and no payment started in 15 minutes. If either exists, wait.
2. `df -h /`, `systemctl status coaching-api coaching-web nginx --no-pager`, and the jar SHA-256 still `905bfb4e…`.
3. Backups: `coaching-pre-installments-<stamp>.dump`, checked and copied to the PC; `files-pre-installments-<stamp>.tgz` of
   `/var/lib/coaching-api/files`, copied to the PC; `/etc/coaching-api.env.before-installments-<stamp>`;
   `/opt/coaching-api/api.jar.before-installments-<stamp>`.

### 6.2 The website, staged

`powershell -NoProfile -File web\scripts\deploy-web.ps1 -Package <out.tgz> -DryRun`. Stages and checks without swapping.

### 6.3 The API

1. `scp` the jar to `/tmp/api.jar`, check the SHA-256, then `sudo install -o deploy -g deploy -m 640 /tmp/api.jar /opt/coaching-api/api.jar`.
2. `sudo systemctl restart coaching-api` and watch `journalctl -u coaching-api -f`. Expect 2 migrations (79 and 80) and a clean
   start. If it stops, go to section 7.

### 6.4 The website

`powershell -NoProfile -File web\scripts\deploy-web.ps1 -Package <out.tgz>`. The previous release is kept as
`/opt/coaching-web.before-<stamp>`.

### 6.5 Checks on the live site

1. Preflight: `MODE=live SITE=symamlaw.com STAFF=admin.symamlaw.com` with `docs/exam-site/deploy/live-payment-preflight.sh`.
   All 21 pass.
2. Public, signed out: home, `/packages`, the live package page with its price fully visible on the card, `/mcq`, `/written`,
   the notice board. Staff login on both hosts.
3. Online purchase unchanged: the checkout page opens for a priced exam (no payment needed).
4. **Add-exams (Master Admin), on a new draft package only:** filter, select all, bulk add, confirm the skipped-exam summary,
   reorder, then delete the draft.
5. **Direct enrolment (Master Admin), with the test account from 4.1:** no real payment moves, since this only records one.
   Enrol on a small priced exam with an installment plan. Check the receipt PDF, the receipt email, the exam-time email, the
   Exams notice for the student and the Installments tab for the Master Admin. Record a second payment, then void it.
6. Roster rule on a draft package: direct grant blocks a roster add. Revoke the grant.
7. `journalctl -u coaching-api --since '-15 min'`: no errors.
8. Remove the test records if decision 4 says so.

### 6.6 The next morning

The first reminder job runs at 06:30 Dhaka. Read its log. With no due installments it should do nothing and send nothing.

### 6.7 Record

Add the release to `reports/client-review-deploy.md`: commit, SHA-256s, the Flyway result, backups with their stamp, preflight
result, what was and was not checked, and the rollback steps. Update `current-api.md` for the enrolment routes, and the status
line of `direct-enrolment-installments.md`, which still says section 14 is untested.

## 7. Rollback

| Problem | Action |
|---|---|
| The API does not start | Read the first exception. Otherwise put `api.jar.before-installments-<stamp>` back and restart. The previous jar runs on Flyway 80 (rehearsal step 8) |
| The website is broken | Stop `coaching-web`, put `/opt/coaching-web.before-<stamp>` back, start it. The API needs no change |
| Direct enrolment misbehaves after the release | Roll back the website first, so nobody can record more payments, then the API if needed |
| Data damage | Restore `coaching-pre-installments-<stamp>.dump`, only with Shahriar's approval, accepting that writes since the dump are lost |

The new tables and columns stay after a rollback, since nothing old reads them. Payments recorded before it stay in the
database and reappear when the feature returns. Queued installment emails are skipped. Tell the Master Admin if a rollback
happens after real plans exist, because the old screens cannot show them.

## 8. What goes live with known limits

- Moving a student from an exam plan to a package does not close the exam plan. The warning says so.
- Receipt emails go only to verified addresses, and nothing tells the admin when one is skipped. The notice and PDF always work.
- Payments on direct enrolment are recorded by the Master Admin. Students cannot pay an installment online.
