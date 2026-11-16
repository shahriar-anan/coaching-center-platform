# Plan: four fixes (16 Nov 2026)

Status: **built 16 Nov with the recommended decisions (D1–D5); V105; browser check 23/23; not deployed.**

1. Opening Admissions signs the Master Admin out.
2. Delete a student from a batch so the roll can be used again.
3. A visitor who presses Apply must sign up or log in first, then sees the form.
4. Batch cards and pages show the course fee only, never the number of instalments.

---

## 1. Admissions signs the Master Admin out

### What happens (found in the live log and database, 16 Nov)

- The live API answered **401 to 44 photo requests** (`GET /api/v1/offline-admissions/{id}/photo`) in the last day.
- Each one: the photo is refused, the site refreshes the sign-in successfully, the photo is asked for again with the new sign-in,
  and it is **refused again**. At 13:40 a fresh login was refused 9 seconds later.
- The site treats a second 401 as "your session ended" and signs the admin out (`web/src/lib/api/client.ts`). The photo shows on
  the Admissions list, the admission page and the setup panel, so **opening Admissions signs the Master Admin out**.

### Why

- One admission on live (READY, with a roll and 1 payment) belongs to a **student account that was deleted** afterwards.
- The photo code looks the student up among accounts that are not deleted. When it finds none, it answers **401 "Authentication
  is required"** (`StudentPhotoService.requireStudent`). That should be "not found", not "not signed in".
- Deleting a student account is allowed while the student has an **open admission**. Only paid orders and exam access block it
  today. So the admission was left pointing at a deleted account.

### Fix

- **The photo:**
  - Built: the staff photo route reads the stored photo without checking the account, so a deleted account's photo still shows
    to staff; with no file it answers **404** and the page shows the initial.
  - `requireStudent` answers 404 for a missing account on every route that uses it. A signed-in student's own photo route
    still answers 401 when the caller has no account, from its own check.
- **Safety net on the site:** a photo or file that fails to load never signs anyone out by itself. Only the API's sign-in
  refresh failing does. This guards against any other route answering 401 by mistake.
- **Deleting an account:** a student with an admission that is not closed **cannot be deleted** (decision D1). The message
  says to close or delete the admission first.
- **The admission already left behind on live:**
  - The admission page shows "**Account deleted**" in place of the student's name link.
  - The admin can close it or delete it (issue 2).
- **Account checks on other admission routes:** any other staff route that loads the student (detail, fees, setup side) shows the
  admission even when the account is deleted. Covered by a new test.

### Checks

- API test: an admission whose account is deleted. The list, the detail and the photo answer 200 / 404 (never 401), and deleting
  a student with an open admission is refused.
- Web test: a 401 on a photo does not sign out.
- Browser check: Master Admin opens Admissions and that admission's page with a deleted-account admission in the list.

---

## 2. Delete a student from a batch, so the roll is free

### Today

- **Close** ends an admission: future dues are cancelled and exam access is revoked. But the admission **keeps its roll**, and
  rolls are unique across the site (`V96`), so the roll can never be given again.
- The suggested roll is **the highest roll in the batch plus one**, so a roll freed in the middle would never be suggested.

### The new action: "Delete from batch" (Master Admin only)

On the admission page, behind a confirmation that names the student, the roll and the batch, with a required reason.

- **The roll is freed at once.** It is kept on the audit record and history, but no longer held by the admission.
- **Access ends:** every exam and package granted through the admission is revoked. Future dues are cancelled.
- **The admission leaves the batch:** it is gone from the Admissions list, the batch's students, the roll lists, the results
  upload's expected students, reminders and overdue lists.
- **What it had stays where money and results need it** (decision D2):
  - Recorded **payments stay**, so Finance and receipts do not change. They are listed as from "a deleted admission" with
    the student's name.
  - **Published results stay** with the roll they were published under, as history.
  - With **no payments and no results**, the admission and its form, items and dues are **removed completely**.
- **The student's account is not touched.** They keep their account, their online exams and anything they bought themselves.
  They can apply again later.
- **The student is told** in the app (Notices). No email.

### Roll suggestion fills gaps (decision D3)

- The suggested roll becomes **the lowest free roll from the batch's roll start**. A freed roll is offered to the next student
  first.
- The admin can still type any free roll.

### Data (V105)

- `offline_admissions`: `deleted_at`, `deleted_by`, `deleted_reason`, `released_roll` (the roll it had).
- Lists and counts skip deleted admissions.
- All additive, so a rollback is safe.

### Checks

- API tests:
  - delete frees the roll, and a new admission takes it;
  - the suggestion offers the freed roll;
  - access revoked and dues cancelled;
  - with payments: Finance unchanged;
  - without payments: the rows are gone;
  - staff (System Admin) refused;
  - published results unchanged.
- Web test: the confirmation and the reason.
- Browser check: delete, then onboard another student onto that roll.

---

## 3. Sign up or log in before the form

### Today

- A visitor who presses **Apply** gets the form straight away with no account. The email they type starts an application, and
  a code is emailed. An account is made for them at onboarding.
- Sign-up already signs the student in and can send them back to a page (`?next=`). After sign-up it shows an **optional** email
  code step.

### New

- **Apply as a visitor** shows the batch and two buttons: **Create an account** and **Log in**. Both carry the apply page as
  `next`.
- **Create an account** signs the student in and goes **straight to the form** for that batch (decision D4). The email code can
  be checked later from the account page, as today.
- **Log in** (and Google sign-in) returns to the form the same way.
- **A signed-in student** gets the form at once, as today. Nothing changes for them.
- **The Admission page and batch pages** say "You need an account to apply" next to Apply when nobody is signed in.
- **The no-account form is no longer offered.**
  - **Applications already started without an account keep working** until they finish: on live 10 are in progress
    (4 waiting for the form, 1 sent back, 5 submitted). Their reopen links and codes still work (decision D5).
  - The API refuses **new** no-account starts.

### Checks

- Web tests: visitor sees the two buttons, not the form; `next` is kept through sign-up and login.
- Browser check:
  - a visitor signs up from a batch card and lands on the filled-in-ready form, signed in;
  - an existing student logs in from the same spot;
  - an old reopen link still opens its form.

---

## 4. Course fee only, no instalment count

### Today

- Every student-facing place shows "Course fee: ৳9,000, in 7 monthly instalments". It comes from one helper
  (`batchFeeLine`), used by:
  - the batch card;
  - the batch page;
  - the batch strip above the form;
  - the student home.
- The public batch API also sends the count (`installments`).

### New

- All four places show **"Course fee: ৳9,000"** only, in both languages.
- The **public API stops sending** the instalment count, so it is not visible anywhere on the student side.
- **Unchanged:**
  - the admin's own screens (setup, batch form);
  - an onboarded student's **own payment schedule** on My admission, which lists their dues and dates. That is their bill,
    not the batch's advert.
- The batch **description** is the admin's text. If it mentions instalments, the admin edits it.

### Checks

- Web tests updated (the fee line), en and bn.
- Browser check: card, batch page, form strip, home. No "instalment" or "কিস্তি" on any of them.

---

## Decisions needed

| # | Question | Recommended |
|---|---|---|
| D1 | Deleting a student **account** with an open admission | **Refuse** until the admission is closed or deleted |
| D2 | "Delete from batch" when the student **has paid** | **Keep the payments** (Finance unchanged), free the roll, remove the rest |
| D3 | Roll suggestion | **Lowest free roll** from the roll start, so freed rolls are reused first |
| D4 | After sign-up from Apply | **Straight to the form**; verify the email later |
| D5 | Applications already started without an account (10 on live) | **Let them finish** as they are |

## Order of work

1. **Issue 1 first:** it is the one stopping the Master Admin now. It needs no migration and can be released alone. That
   release would be API plus web, with the sign-out safety net.
2. Issue 4 (small, web and one API field).
3. Issue 3 (web routing, one API refusal).
4. Issue 2 (V105, the delete action, the roll suggestion).
5. Then:
   - API and web suites;
   - a browser check of all four;
   - commit and push;
   - deploy only on your go, with the quiet check first.

## Until issue 1 is released

The Master Admin can reach the admissions again only after the fix. Until then, the page of that one admission (and the list
it is on) signs them out. Restoring the deleted account would also stop it. I will not change live data without your go.
