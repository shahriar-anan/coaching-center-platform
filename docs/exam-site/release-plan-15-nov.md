# Release plan: demo/client-review to the live site (written 15 Nov 2026, revised the same day)

## Update, 15 Nov afternoon: release brought forward to today

Shahriar asked to deploy **today**, as soon as it is safe, because the client wants the features now. The phone and browser
checks of section 5 were dropped by his choice; everything else below was done.

**Done (15 Nov, 13:40 to 14:06 Dhaka):**
- **Date rule** built and tested (commit `c59a881`).
- **Suites on the release code:** full API suite 1,267/1,267; full web suite 2,151/2,151; typecheck clean.
- **Rehearsal** on a dump of live taken at 13:44 Dhaka (users 200, students 192, 187 sittings with 18 answer scripts,
  1,202 notices), restored locally, release jar with the dev profile, no mail or payment credentials, jobs off, offline on:
  - **Migrations:** 19 applied (80 to 99) in 0.38 s, started in 20 s, 0 errors.
  - **Data:** row counts identical in 10 tables.
  - **Smoke:** 16/16 checks passed (student sign-in, earlier results and written script record, notices, packages, no
    admission form for existing students, Finance on real money, offline routes up and empty).
  - **Rollback:** the previous live jar `9bb0ec6` starts on the upgraded copy (0 errors); only the new features are missing.
  - **Cleanup:** the dump was removed from the box after the copy was checked (SHA-256 match).
- **Production web build:**
  - The first build failed: the five new admission and offline-activities pages lacked the Suspense boundary a production
    build needs; dev never showed it. Fixed in `c1e7d54`.
  - Then built: package `web-c1e7d54-20261115-080231.tgz`, build `Y5y_KkpDafToUJat8uHxb`, release checks passed (API base
    empty, hosts `https://symamlaw.com` and `https://admin.symamlaw.com`).
  - Run locally: the menu's Admission link goes to `/admission`, and the offline pages answer.
- **Jar** `api-c59a881.jar`, SHA-256 `e1e0bcb7…f793`, uploaded to `/tmp/api.jar` on the box and verified.
- **Web package** staged on the box by a dry run, nothing swapped.

**Held:** at 14:06 Dhaka a written LIVE exam was running (14:00 to 17:10) with an open sitting. The swap waits until it ends
and no sitting is open (section 6.2, step 1).

**Still needed from Shahriar before the swap:** `OFFLINE_ADMISSION_ENABLED=true` in `/etc/coaching-api.env`.

---

Status before the update: **plan only, nothing deployed.** Decisions of 15 Nov (Shahriar):
- **Offline admission ships switched on.**
- **Window:** about 01:00 Dhaka on the night of 16 to 17 Nov.
- **Students are told by staff announcements.**
- **No receipt check through the 10 BDT payment.**

Weekday rule: a weekday that does not match its date only warns, because the admin fixes it in the routine preview. Wrong dates
still block confirming: unreadable, out of order, or outside the batch's dates.

## 1. What is live, and what this release adds

**Live today (read from the box, 15 Nov, read-only):**
- API `/opt/coaching-api/api.jar` and web `/opt/coaching-web`, built 8 Nov (release of 9 Nov 01:45 Dhaka, commit `9bb0ec6`).
- Database at **Flyway 80**. 194 users, 186 students. Files 253 MB in `/var/lib/coaching-api/files`; nightly backups in
  `/var/backups/coaching/nightly`.
- Settings: `OFFLINE_ADMISSION_ENABLED` is not set; the first `WEB_ORIGIN` entry is `https://symamlaw.com` (email links use it).
- Box: headless OpenJDK 21, FreeType present, `fc-list` finds no system fonts. Disk 88 GB free, 6 GB memory available.

**This release:** 33 commits (`9bb0ec6..1c14732`) plus the date rule below, **19 migrations V81 to V99**.

### 1.1 Changes every user meets

| Area | Change |
|---|---|
| Written exams | Upload saves a **draft on the server**; Replace until Submit; the server submits the last draft at the deadline even with the page closed (V99). The file is copied when picked, a dropped upload retries twice, a PDF the server cannot shrink is kept as sent, a password-locked PDF is refused with a clear reason. Fixes the failed live upload of 15 Nov. |
| Exam mail | **No exam-time or hour-before email.** Exam changes reach students as in-app notices. Receipts, purchase notices and installment mail still go out, now from a background worker over one kept-open SMTP session (login codes and password resets share that session). |
| Pricing | The **System Admin** may set price and discount; publishing stays with the Master Admin. |
| Packages | **WhatsApp group link** on packages (V98). |
| Finance | **Finance** page for the Master Admin: income and expenditure, online and offline. |
| Notices | Link words say where they go; offline result notices sit under Results. |
| Public menu | "Admission" in the site menu opens the **offline batch list** (`/admission`) instead of sign-up. The header's Sign up stays. With no batches it says none are open. |
| Smaller | Phone fixes for the written upload, discounted prices and the tab bar (`da2221b`); dialog width; `/my-admission` redirects to `/offline-activities`. |

### 1.2 Offline admission (switched on)

Desk admission by phone, public applications from batch cards, review, setup, onboarding with roll numbers, join codes,
activation codes, fees with receipts and reminders, offline exam results (Excel upload, publish, leaderboard), batch routines read
from the PDF with a student copy without instructor names, offline instructor pay with approval, the student's Offline activities
page. All of it starts empty: nothing happens to any existing student until staff create a batch or an admission.

New scheduled jobs, which act on offline data only:
- 06:30 Dhaka: fee reminders.
- 21:00 Dhaka daily: unsettled-class digest to staff.
- 21:00 Sunday: weekly reminder to staff.

### 1.3 Database changes that touch tables in use

- **New nullable columns:**
  - `live_exam_grants.access_ends_at`
  - `exam_outbound_mail.payload`
  - `live_exam_modules.whatsapp_url`
  - four `live_exam_participants.draft_*` columns

  Existing rows are untouched.
- `users.email` becomes nullable (V87).
- **Allowed values widened** for `student_notices.kind`, `live_exam_grants.source` and `exam_outbound_mail.kind` (V84, V94, V97).
  **Checked against live data on 15 Nov:** every value in use is in the new lists.
- Everything else is new tables. **No migration deletes or rewrites existing data.** V95 drops an offline table created earlier in
  the same release, so it is empty.
- No new code runs on startup.

## 2. What was checked, and what was not

**Checked (local, dev data, 15 Nov):**
- **Test suites:**
  - Full API suite 1266/1266 on a fresh database.
  - Full web suite 2150/2150.
  - Typecheck clean.
- **Browser checks, screens reviewed:**
  - Catalog and System Admin pricing 19/19.
  - Offline batches and results first publish 26/26.
  - Written draft flow 14/14 (before the latest upload fixes).
  - Admissions: desk 23/23, public 33/33, matched accounts 35/35.
  - Offline result updates with the client's real sheets 34/34.
  - Instructor pay and Finance 54/54.
  - Student notices counted across whole batches.

**Not checked yet:**
1. **Migrations on real data** (V81 to V99 have only run on test databases).
2. **A production build**: every browser check ran on `next dev`.
3. **The latest written-upload fixes in a browser and on a real phone** (tests only).
4. **Real SMTP** for the background worker and the kept-open session.
5. **Finance on real money.**
6. **Rendering the routine student copy on the box**: it turns pages into images, which needs Java 2D and fonts; the box has no
   system fonts listed. It works on the PC; not proven on Linux.
7. **Offline with the switch on in a production build**, and the **date rule, not built yet.**

## 3. Risks if deployed without the checks

- **Live exam:** a written exam runs today 14:00 to about 17:10 Dhaka; a restart during it breaks open sittings. (The window
  avoids this.)
- **Migrations:** they are unrehearsed on real data; Flyway is forward-only, so a failure needs a restore.
- **Production build:** an unverified build (wrong API base or flags) could break login or hide pages for everyone.
- **Mail:** if the kept-open session misbehaves, login codes, password resets and receipts could stall.
- **Routines:** if rendering fails on the box, an admin cannot confirm a routine. Nothing is lost, but the feature is blocked.
- **Old open tabs:** after the swap, a student who kept a tab open may see errors until they reload.

## 4. Protecting the live data

1. **No migration deletes or rewrites data** (1.3), and the allowed-value rules were checked against live values.
2. **A fresh backup at the window:**
   - A `pg_dump -Fc` of the database, checked with `pg_restore --list`.
   - A tar of `/var/lib/coaching-api/files`.
   - Both **copied off the box to the PC** before anything is swapped.
   - The nightly backup is a second copy.
3. **The rehearsal proves the backup method:** the dump used for the rehearsal is restored and migrated on the PC first, so the
   restore path is known to work before it might be needed.
4. **Answer scripts are never deleted.** Submitting promotes the draft. Replacing a draft removes only the earlier draft file.
   Stored answer scripts and marked PDFs from before the release are not touched.
5. **Freeze during the window:** staff are asked not to publish, edit exams, record payments or mark scripts from 00:45 to about
   02:00 Dhaka.
6. **Sessions survive:** `JWT_SECRET` is unchanged, so nobody is logged out by the deploy.
7. **App rollback keeps the data:** the old jar and web run on the V99 schema, because every change is additive or widening.
   Rolling back the app does not need a database restore.

## 5. Work and checks before the window (16 Nov)

**Build (needs a go):**
1. **The date rule for routines.** Confirming a routine is off while any row has an unreadable date, a date out of order, or a date
   outside the batch's start and end. A weekday mismatch only warns. Tests in the API and the web; then both full suites again on
   the release commit.

**Must check (blocking):**
2. **Rehearsal on a copy of live.**
   - **Prepare:**
     - `pg_dump -Fc` on the box (read-only).
     - Restore into a new local database `coaching_center_rehearsal`.
     - Start the release jar against it with **mail to the log only and every job off** (dev profile,
       `app.exam-jobs.scheduled=false`, `app.staff-pay.jobs-enabled=false`).
     - Offline **on**, as in production.
   - **Check:**
     - Record how long Flyway takes, and that it ends at 99 with no failure.
     - Row counts of the main tables are the same before and after (users, students, grants, orders, participants, notices).
3. **Production web build** with `NEXT_PUBLIC_OFFLINE_ADMISSION=1` and the API base **empty**. Grep the built chunks for both
   before anything else. Run it with `next start` against the rehearsal API.
4. **Browser smoke on that build, desktop and 390 px.**
   - **Students:**
     - Login, home, a package and its WhatsApp link, an MCQ sitting, results, notices, payments.
     - An earlier written result (old data).
     - The written flow with a normal PDF, a PDF with bytes before its header, an owner-locked scan, a password-locked PDF,
       a 20 MB file on throttled 3G, and a connection dropped mid-upload.
   - **Staff:**
     - Publish an exam: no exam-time mail is queued.
     - System Admin pricing.
     - Finance totals for October and November against the payments page and the direct enrolment list.
   - **Offline** (on this rehearsal only, test data):
     - Create a batch, upload the client's routine PDF, preview, and the date rule.
     - Desk admission, onboard, the student's Offline activities.
     - A result upload.
   - **Existing students:** check that none is sent to an admission form.
5. **One real Android phone:** choose a large scanner PDF from Files and from Drive on the rehearsal build (LAN or tunnel).

**Should check:**
6. **Routine rendering on the box, before the window.** Copy the release jar to the box and run its routine renderer once on the
   client's PDF from the command line (no service, no database), to see that the student copy renders with the box's Java and
   fonts. If it fails, install `fonts-dejavu-core` (or fix it) before the window; this changes nothing in the live service.
7. **Real SMTP from the PC:** one password-reset mail to a test inbox through the real mail server, from a local run with every
   recipient forced to that inbox.

## 6. The deploy (night of 16 to 17 Nov)

### 6.1 Before the window (16 Nov, day)
1. Tag the release commit (after the date rule and green suites), e.g. `release-2026-11-17`.
2. Build the API jar (`mvnw -DskipTests package`) and the web build with the release flags; check the chunks again.
3. Copy them to the box as `/opt/coaching-api/api.jar.new` and `/opt/coaching-web.new`. Nothing is swapped.
4. Shahriar adds `OFFLINE_ADMISSION_ENABLED=true` to `/etc/coaching-api.env` (it takes effect at the restart in the window).
5. The client posts the staff announcement (exam times in-app only; written answers saved as a draft, submitted by the student or
   at the deadline) and the freeze notice for staff.

### 6.2 In the window (about 01:00 Dhaka)
1. **Check nothing is open:** no open written sitting, no exam live in the next three hours, no pending exam mail rows (same
   read-only query as 15 Nov).
2. **Back up (section 4.2):** dump, files tar, both copied to the PC, the dump listed with `pg_restore --list`.
3. **Keep the old artifacts:** `api.jar.before-offline-<stamp>`, `coaching-web.before-offline-<stamp>`.
4. **Swap the API:** stop `coaching-web`, swap the jar, restart `coaching-api`. Watch the log until Flyway reports V99 and the app
   is up; check that `flyway_schema_history` has all 99 successful.
   - On a Flyway error: stop the service and restore (6.5, full).
5. **Swap the web:** swap the directory, start `coaching-web`.
6. **Downtime:** about one to two minutes.

### 6.3 Smoke test right after (live)
- **API log:** no ERROR since the start; offline routes answer 200 for staff (switch on).
- **As a student account:** home, a package, notices, results, an earlier written result, payments.
- **As staff:** the admin home, an exam page, Finance opens with plausible totals, the offline pages open (empty).
- **Mail:** a password reset for a test account; the code must arrive. That proves the kept-open SMTP session.
- **Routine rendering:**
  1. Create a batch named "Test batch, delete me", upload the client's routine PDF and confirm it.
  2. Open the student copy, then archive the batch.
  3. If confirming fails, it saves nothing; fonts on the box are then the first fix.
- **Do not publish or edit real exams** just to test.

### 6.4 After the release
- 06:30 Dhaka: the fee reminder job runs with no admissions (no mail expected). 21:00: the digest runs (no classes).
- **18 Nov, the first written exam on the new flow:**
  - Watch `answer-draft` PUTs, `submit` POSTs and any upload errors in the API and nginx logs during the sitting.
  - After the deadline, check that every open sitting with a draft became SUBMITTED.
- Record the release in this file (commit, times, Flyway duration, smoke results) and update the memory notes.

### 6.5 Rollback
- **App only** (schema stays at V99, data kept):
  - Put back `api.jar.before-offline-<stamp>` and `coaching-web.before-offline-<stamp>` and restart both.
  - Drafts saved since the deploy stay in the database, unused by the old code; students upload again.
  - Any offline records stay, unused.
- **Full** (only if Flyway failed or data is wrong):
  1. Stop both services.
  2. Restore the dump into a new database and point the env at it (Shahriar).
  3. Restore the files tar if needed, put the old artifacts back, and start.
  4. Anything written after the backup is lost, which is why the window has a staff freeze.
