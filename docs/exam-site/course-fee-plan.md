# Plan: the course fee is the total, paid in instalments the admin chooses (15 Nov 2026)

Status: **built 15 Nov (go given); compiles, typecheck and lint clean; tests updated, not yet run; not committed.** The live batch and
admission turned out to be the client's test data, so no special handling of them was needed beyond the V100 backfill. Replaces the wording-only commits `f16903d` and `2ab5e13` (never
deployed), which called the monthly amount "course fee".

## 1. Decisions (Shahriar, 15 Nov)

| # | Decision |
|---|---|
| C1 | The **course fee is the total** a student pays for the batch. Students pay it month by month because few can pay it all at once. |
| C2 | The **admin chooses the number of instalments** (for example 6, or 1 to pay in full). |
| C3 | Instalments are **whole taka, the remainder on the last**: ৳7,000 in 6 is ৳1,166 × 5 and ৳1,170. |
| C4 | Live data: the batch "20th BJS Exam (MCQ and Written) Batch" has a **course fee of ৳9,000**. Its one admission (Ready, not onboarded) has a **total of ৳7,000** for that student. |

## 2. Why it matters now

Today the fee is a **monthly** amount repeated for each month from the first due date to the course end. The live admission
(৳7,000, first due 15 Dec, course end 31 May) would be onboarded as **6 × ৳7,000 = ৳42,000**. Nothing is charged before
onboarding. **Until this ships, that admission must not be onboarded** (Shahriar to tell the office).

## 3. How it will work

**Batch form:**
- **Course fee (total, ৳)**, for example 9,000.
- **Instalments** (1 to 24), defaulting to the number of months between the batch's start and end.
- Shown to students as "Course fee ৳9,000 · in 7 monthly instalments".

**Admission setup (staff):**
- **Course fee for this student** (starts from the batch's, can be lowered or raised).
- **Number of instalments** (starts from the batch's).
- **First due date** and **course end date**, as today.
- The screen lists every instalment with its date and amount, and the total, before Ready.
- The last instalment must fall on or before the course end, or the setup is refused with that reason.

**Schedule:**
- Instalment *k* is due on the first due date plus *k*−1 months (same day, clamped to the month's end, as today).
- Amounts: `floor(total / n)` whole taka for the first n−1, the remainder on the last. A total with paisa keeps the paisa on the
  last.

**Onboard dialog:** "Course fee ৳7,000 · 6 instalments: ৳1,166 × 5, then ৳1,170", with the dates.

**Change terms (after onboarding):** a new course fee, a new number of instalments and/or a new course end.
- Paid instalments and instalments already past their date stay as they are.
- What is left (new total minus those) is spread over the remaining instalments with the same rounding.
- It is refused when the new total is less than what is already kept, or when no instalment would be left.
- The student is told, as today.

**Student site:**
- Batch card and page: "Course fee ৳9,000 · 7 monthly instalments".
- My admission: **Course fee** (total), **Paid so far**, **Still owed**, then each instalment with its date and amount (the list
  exists today).

**Emails:**
- The approval and terms emails show the course fee and the instalments instead of "Monthly fee".
- The receipt says "Course fee instalment received".
- The reminders keep the "course fee instalment" wording.

**Unchanged:** payments, approvals, receipts numbering, reminders (they read each instalment), Finance (it reads payments), the
staff Fees tab (it lists instalments).

## 4. Data (migration V100)

- `offline_batches`: add `course_fee NUMERIC(12,2)` and `default_installments SMALLINT`.
  - Backfill: `course_fee = default_monthly_fee` (decision C4: the live ৳9,000 is the course fee).
  - `default_installments` = months from `starts_on` to `ends_on`, at least 1 (live: 7).
- `offline_admissions`: add `course_fee NUMERIC(12,2)` and `installments SMALLINT`.
  - **Admission with no instalments yet** (not onboarded): `course_fee = monthly_fee` (decision C4: the live ৳7,000 is the
    total); `installments` = months from the first due date to the course end (live: 6).
  - **Admission with instalments:** `course_fee` = the sum of its live instalments; `installments` = their count (none on live
    today; kept for safety).
- `monthly_fee` and `default_monthly_fee` stay, filled with the first instalment's amount, so the previous jar still reads a
  sensible value if the release is rolled back.

## 5. Changes by file

**API:**
- `OfflineBatch`, `OfflineAdmission`: the new fields.
- `OfflineBatchService`: create and update validate the course fee (> 0) and the instalments (1 to 24); the views carry both.
- `OfflineAdmissionAdminService`: setup takes the course fee and the instalments and validates them against the course end;
  the view carries both and the instalment preview.
- `OfflineAdmissionOnboardingService`: `writeSchedule` splits the total; `terms` re-spreads what is left; the approval and
  terms payloads carry the course fee and instalments.
- `OfflineFeeSchedule`: one tested function for dates and amounts.
- `OfflineAdmissionService` and the public batch view: course fee and instalments for students.
- `AdmissionMail`, `EmailTemplates`: the course fee rows.

**Web:**
- Batch form, list and detail.
- Setup form, summary and draft.
- Onboard dialog; after-onboarding terms dialog.
- Public batch card and page; the student's admission payments.
- Labels in English and Bangla, as natural Bangla: কোর্স ফি (মোট), কিস্তির সংখ্যা, কিস্তি.

**Tests:**
- Update the 25 test files that use the monthly fee.
- New tests:
  - the split and its rounding (7,000/6; 9,000/7; a paisa total; 1 instalment);
  - the last instalment after the course end is refused;
  - terms re-spread with a paid instalment kept;
  - the V100 backfill on an onboarded and a not-onboarded admission.

## 6. Checks and release

1. Both full suites on a fresh database; typecheck.
2. Rehearsal on a fresh dump of live:
   - V100 applies;
   - the live batch reads ৳9,000 in 7;
   - the live admission reads ৳7,000 in 6, and onboarding it on the copy writes ৳1,166 × 5 + ৳1,170;
   - the previous jar still starts.
3. Production web build and its checks.
4. Release as on 15 Nov:
   - quiet check, backup to the PC;
   - API swap with automatic rollback, then the website;
   - smoke checks.
5. Then the office may onboard the waiting student.

## 7. Open points

- **Instalments on the batch:** months between start and end is suggested as the default; the admin can change it per batch
  and per student.
- **Instalment limit:** 24 is assumed as the most; say if longer courses exist.

## 8. Review findings (15 Nov, before the suites)

Fixed:
- Change terms on an admission onboarded before V100 that the backfill could not fill now reads its fee and count from its live
  instalments instead of failing.
- Choosing a batch before the first due date is known leaves the count to the API, which cuts the batch's number to what fits. Before,
  the screen could send a count that does not fit and show a save error.
- The home tile's "Batches on offer" row let the longer fee line squeeze the batch name to one letter per line on a phone. The fee now
  moves under the name.

Known limits of a rollback to the 15 Nov release after V100:
- **Creating a batch fails**, because `course_fee` is required and the old release does not fill it.
- **Onboarding writes the old monthly schedule**: the first instalment's amount (V100 now fills the old columns with it: ৳1,285 for the
  live batch), every month to the course end. That is close to the total but not exact. So **no new batch and no onboarding** while
  rolled back.
- Reading, payments and approvals are unaffected.

## 9. Rehearsal on a fresh copy of live (15 Nov, about 16:00 Dhaka)

- Dump: taken read-only on the server, copied to the PC, hash matched, and restored into `coaching_center_rehearsal`. Before V100:
  Flyway 99, 201 users, 1 batch, 1 admission (the office had closed it by then), 0 dues.
- New jar (current tree, V100 inside) on the copy, with mail and payment credentials removed and jobs off. 18/18 passed:
  - V100 applied;
  - the batch reads ৳9,000 in 7 (old column ৳1,285); the closed admission reads ৳7,000 in 6;
  - the live student applies again from the account, and their profile, results, notices and packages still answer under review;
  - the setup defaults to ৳9,000, cut to the 6 instalments that fit;
  - set to ৳7,000, then ready, onboard, join code, join: ৳1,166 × 5 + ৳1,170, from 15 December to 15 May;
  - Finance, catalog and Payments read.
- Counts after: users, orders, grants and participants unchanged. The only additions are the rehearsal's own admission and its 6 dues.
- Live jar `api-c59a881` on the migrated copy (rollback): starts, and reads batches, the queue, public batches, the student's
  admission, list, fees and results, Finance and the catalog (9/9). It shows the new instalments as written.
- V100 was changed during the rehearsal (the old columns now get the first instalment). It was rebuilt and rehearsed again on a fresh
  restore. The offline ITs passed on it (38/38). The review DB's V100 checksum was updated to match (-1696437556).

## 10. Released (15 Nov, 16:41 Dhaka), with the admission access fix

- **Commit and builds:** `191e097`. Jar `api-191e097.jar` (SHA-256 `a654956d…`). Web `web-191e097-20261115-103612.tgz`, build
  `bY2MNX63xjjvXVvRZwmMv`, offline on.
- **Timing:** swapped during the written live exam (14:00 to 17:10, 1 open sitting, no draft yet) by Shahriar's choice.
- **Backup (16:39):**
  - `coaching-pre-coursefee-20261115-103912.dump` (581 entries) and `files-pre-coursefee-20261115-103912.tgz`, both on the box and
    in `F:\cc-releases\backup-pre-coursefee-20261115-103912` (hashes match);
  - `api.jar.before-coursefee-20261115-103912` and the env copy are on the box.
- **API:** Flyway 100, 0 failed, 1 migration applied, started in 19 s. Live values: the batch ৳9,000 in 7 (old column ৳1,285); the
  closed admission ৳7,000 in 6.
- **Web:** up after 1 s. Previous site kept at `/opt/coaching-web.before-20261115-104207`.
- **Smoke:**
  - offline switch on; nine pages and APIs answer 200; staff routes answer 401 unauthenticated;
  - 0 errors and 0 5xx since the start (4 warnings: the PDF font fallback, as before);
  - counts unchanged (users 201, participants 190, scripts 18, orders 7, grants 31);
  - the public card serves the course fee ৳9,000 in 7.
- **Open:**
  - one real email (a join or reset code) to confirm Brevo delivers;
  - the office checks that the batch's course fee and instalments are right.
