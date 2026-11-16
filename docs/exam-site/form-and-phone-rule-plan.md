# Plan: admission form changes and the phone rule at onboarding (16 Nov 2026)

Status: **asked by Shahriar 16 Nov ("make plan and then implement, do not run tests yet"); implemented, tests not run.**

## 1. The form

| Field | Today | New |
|---|---|---|
| Semester / batch | label "Semester / batch" | label **"Session / Batch"** (bn "সেশন / ব্যাচ"). Same field, same data. |
| National ID number | required | **optional**. When given it must still be 10, 13 or 17 digits. |
| Emergency mobile number | required | **optional**. When given it must still be a valid mobile number. |
| Permanent address | typed separately | a **"Same as present address"** box under it: ticked, it copies the present address and follows it while ticked. Unticking keeps the text to edit. |

- The API (`formProblems`) stops requiring the NID and the emergency number, so the submit, the ready check and onboarding all
  accept a form without them.
- The form shows those two without the required star.
- Forms already submitted are unaffected.

## 2. The phone rule at onboarding

### Today

Onboarding an applicant who has no account is **refused** when the form's mobile number belongs to **any** account on the site
with a different email ("This mobile number belongs to the account of …"). The admission page warns about it.

### New

- **Refused only for the same batch.** Onboarding is refused when the applicant's **mobile number or email** is also on **another
  person's open admission in the same batch** (not closed, not deleted). The check looks at that admission's form and at its
  account's phone and email. The message names the other person.
- **Every other case goes ahead.**
  - The mobile belongs to an account outside this batch: onboarding goes on.
  - **Matched by email:** an applicant whose email has an account is still joined to that account (join code), as today.
  - **No account with that email:** a new account is made as today. If another account already has the mobile number (phones are
    unique per account), the new account is made **without a phone**. The student signs in with their **email** and the
    activation code. The admission keeps the mobile number on its form.
- **The admission page:**
  - The old warning "the mobile belongs to another account" is replaced by a plain note: the new account will have no phone, and
    the student signs in with email.
  - A new warning names the other person when the same-batch rule will refuse onboarding.
- The desk start (an account made from a phone number alone) still refuses a phone that already has an account. That is a
  different step and was not asked.

## 3. While email does not arrive (Shahriar's yes, 16 Nov)

### 3.1 An application started without an account carries on under the account

- A signed-in student who presses **Apply** on a batch gets the application they started there **without an account** (same
  email), linked to their account, with everything they typed. No second application is made.
- This works for an unfinished form, a form sent back, and one already submitted.
- **Safety:**
  - Email checks are optional and mail may not arrive, so an email alone is not proof.
  - It links only when the account's **email and phone both match** the application, or the account's email is verified.
    Otherwise a new application starts, and the old one is left alone.
- When linked:
  - the application's browser token and emailed link stop working;
  - its photo becomes the account's photo if the account has none;
  - the audit log records it (`OFFLINE_APPLICATION_LINKED`).

### 3.2 A password reset code from the Master Admin

- On a student's page, the Master Admin presses **Password reset code** (active accounts only). The code is shown once, to read
  out or send on WhatsApp; nothing is mailed.
- The student opens **Forgot password → "I already have a code from the office"** (a new link to the Reset password page), and
  types the code with a new password.
  - The code works for **10 minutes**, as an emailed one does.
  - Pressing **Send code** on the Forgot password page would replace it, so the dialog says not to.
- The Master Admin only (`STUDENT_PASSWORD_RESET`). Audited (`PASSWORD_RESET_CODE_ISSUED`).

## 4. Checks (when Shahriar says to run tests)

- **API tests:**
  - a form without NID and emergency number submits, readies and onboards;
  - a mobile owned by another account outside the batch onboards, with an email-only account;
  - a mobile or email shared with another open admission in the same batch is refused;
  - a closed or deleted one does not count.
- **Web tests:**
  - the two optional fields have no star;
  - the "Same as present address" box copies and follows;
  - the "Session / Batch" label;
  - the admission page warnings.
- **More API tests:**
  - linking with the same email and phone;
  - no linking for an unverified email with another phone, and linking for a verified email;
  - the reset code: the Master Admin only, works on the reset page, nothing mailed.
- **More web test:** the reset code button.
- Browser check of the form, an onboarding with a shared phone, linking after sign-up, and a handed-over reset code.
