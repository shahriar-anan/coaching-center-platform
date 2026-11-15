# Plan: applying for a batch never locks a student out (15 Nov 2026)

Status: **built 15 Nov; typecheck and lint clean; tests updated, not yet run; not committed.** Website only, no API or
database change.

## 1. The problem (seen on live, 15 Nov)

A signed-in student applied for a batch. From then on every student page sent them to `/admission/form`, which showed "Your
admission is under review" and a Log out button. Home, exams, packages, results and notices were out of reach. After the Master
Admin pressed Onboard, the student still saw the same screen.

**Cause 1: the website lock is wider than the API's.**
- [student-shell.tsx](../../web/src/components/shell/student-shell.tsx) and [gate.ts](../../web/src/lib/admission/gate.ts) lock
  **any** student whose newest admission is not onboarded.
- The API ([OfflineAdmissionGate.java](../../api/src/main/java/com/coachingcenter/api/offline/OfflineAdmissionGate.java)) locks
  exams and checkout only for a **desk (walk-in)** admission, and never for a student with an onboarded batch.

**Cause 2: an applicant with an account is not finished by Onboard.**
- Onboard emails them a **join code**. The admission stays **Ready** until they enter it on `/admission/join`.
- The student screens show Ready as "under review": no join box, no link.

## 2. The rule after the fix

| Student | Site |
|---|---|
| Applied online, or from their account (any state) | Everything open, as before applying |
| Has an onboarded batch, and another application in progress | Everything open |
| Walk-in (desk) admission not onboarded, and no onboarded batch | Held on the form and its print view, as now (the API refuses exams and checkout for them anyway) |

The website lock uses the same rule as the API, read from the list of the student's admissions (`/me/admissions`), not just the
newest one.

## 3. Where a student sees an application

Each application shows its step, with the action for that step:

| Step | Shown as | Action |
|---|---|---|
| Form not sent (AWAITING_FORM) | Finish your form | Open the form |
| Changes asked (CHANGES_REQUESTED) | The office asked for changes | Open the form |
| Submitted, or Ready (no join code yet) | Under review | View / print my form |
| Ready, join code sent | Approved: enter the join code from your email | Enter join code |
| Onboarded | You are in this batch | Open Offline activities |

It shows in three places:
1. **The batch's own page** (`/admission/batches/{id}`): a "Your application" panel in place of Apply.
2. **The home page's Offline activities tile**: one line per application. The join and form steps are added.
3. **Offline activities**: one card per application. A student whose applications are all in progress now sees them there, and
   the batches on offer, instead of being sent to the form.

**The form page** (`/admission/form`) after submitting:
- Shows **Go to home**. Log out stays only for a walk-in student, who is held on this page.
- When the join code is out, it shows the approval and an **Enter join code** button.

**The menu:** "My admission" shows when any batch is onboarded, not only when the newest admission is.

## 4. Files

- `web/src/lib/admission/gate.ts`: `isLockedOut(admissions)` (the API's rule), `admissionStep(admission)` (the table in section 3),
  and `admissionRedirect` takes the lock.
- `web/src/components/shell/student-shell.tsx`: reads the admissions list; the lock, the tab bar, the side menu and the menu link
  follow it.
- `web/src/components/student/admission/admission-pages.tsx` and `under-review.tsx`: the approval and join step; Go to home.
- `web/src/components/public-admission/batch-page.tsx`: the "Your application" panel.
- `web/src/components/student/home/home-page.tsx`: the tile's join and form lines.
- `web/src/components/student/my-admission/my-admission-page.tsx`: no redirect; pending cards carry their action.
- `web/src/i18n/messages/{en,bn}/…`: new strings, natural Bangla.
- Tests: `tests/offline-admission/gate.test.ts`, `shell-redirect.test.tsx`, and the admission page, batch page, home tile and
  my-admission tests that change.

## 5. Checks and release

1. Typecheck; the changed tests; then the full web suite (when Shahriar says).
2. Browser check on the dev site with an account applicant: apply, browse exams while in review, onboard, join from the batch page.
3. Production web build and swap of the website only (the API is unchanged), with the previous build kept for rollback.

## 6. Browser check plan (15 Nov, before the test suites)

Seen on live first (read only): the one admission is **READY, source ACCOUNT, join code out**, the exact case of cause 2.

Dev site (API on 8092 with V100 applied to `coaching_center_sample`, web on 3000), phone width 390 and desktop, English. Every page
is screenshotted and checked for raw keys, sideways scroll and script errors.

**A. The reported case: a signed-in student applies (flow-applicant).** The student is "Rahim", a seeded account with no offline
admission.
1. Rahim opens the batch page and presses Apply. The form is prefilled; he fills it in and submits. The confirmation shows.
2. **No lock while in review.** From there he opens Home, My exams, Results, Notices, Account and Offline activities. Each page
   shows its own content, the URL stays, and the tab bar is there. Nothing sends him to `/admission/form`.
3. **The step shows where it belongs:**
   - the batch page has "Your application · Under review" and no Apply;
   - the home tile has "<batch>: under review";
   - Offline activities has the card with "View / print my form";
   - pressing Apply again on the same batch shows the step, not a dead form.
4. Staff set up a course fee of ৳7,000 in 6 instalments. The setup screen shows ৳1,166 × 5 + ৳1,170. Staff mark it ready; the
   Master Admin onboards it. A join code is emailed (the dev log).
5. **Onboarded, before the code:** Rahim still has the whole site.
   - `/admission/form` says "Your admission is approved" with **Enter join code**;
   - the batch page and the home tile both offer the join code;
   - the notice is in Notices.
6. Rahim follows **Enter join code** from the batch page, enters the code and lands on Offline activities. The page shows the
   batch, the course fee ৳7,000, and 6 instalments of ৳1,166 × 5 and ৳1,170. "My admission" is in the menu, and the batch page
   says "You are in this batch".
7. **A second application does not hide the first.** Rahim applies for a second batch and submits. The home card for the first
   batch, the menu link and the whole site stay.

**B. The walk-in rule still holds (flow-desk).** A desk student who has not been onboarded is still held on the form, with Log out,
and is admitted at once by the Master Admin.

**C. Join by email code (flow-matched part A).** An applicant whose email matches an account joins with the code; the resend works.

**D. Course fee on the staff side:**
- the batch form has Course fee and Monthly instalments (filled in from the dates);
- the batch list and detail show both;
- the batch cards say "Course fee: ৳7,000, in 7 monthly instalments";
- change terms on an onboarded admission spreads the rest over the open instalments.
