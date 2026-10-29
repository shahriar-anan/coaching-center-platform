# Student page, Phase 4B: sign-up confirm password and payment receipt email

Status: planned 2026-10-29. Build after the Phase 4 layout work is committed. Branch `ui/nr01-ui`, worktree `F:\cc-wt\nr01-ui`. Commit only, no push.

## 1. Sign-up: confirm password

**Today.** The sign-up page (`web/src/app/(auth)/register/page.tsx`) has one password field (`name="password"`, around line 201). A mistyped password is only found out later.

**Change.**
- Add a second password field, labelled "Confirm password" (`auth.register.confirmPassword`, English and Bangla keys), right after the password field, with the same show and hide control.
- Validation runs in the browser before the request is sent. If the two values differ, the confirm field shows "Passwords do not match" (`auth.register.passwordMismatch`) and the form is not submitted. The message is linked to the field with `aria-describedby`, and focus moves to the confirm field.
- The confirm value is never sent to the API. The request body is unchanged.
- Every other password rule stays as it is today.
- Nothing changes in the API.

**Tests.** Mismatch blocks submit and shows the message; match submits; the confirm value is not in the request body; show and hide works on the confirm field.

**Check.** Sign up with a mismatch: no request is sent. Sign up with a match: normal flow to the verification step.

## 2. Payment: optional receipt email

**What the user asked for.**
- During payment, ask for an email address where the receipt can be sent. It is optional.
- The student can skip it: a visible "Skip" (or cross) control clears the field and hides the question.
- If the student has a verified account email and enters no receipt email, the receipt goes to the verified account email automatically.
- If the student enters an address, the receipt goes to that address.
- If the account email is not verified and no address is given, no receipt email is sent. The receipt stays available in payment history (print and PDF).

**Rules.**
1. Receipt address used = the address typed in the payment step, if any; otherwise the account email, only when it is verified.
2. The typed address is per order. It is not saved to the profile.
3. An empty or skipped field is stored as no value, never as an empty string.
4. The typed address is validated as an email (trimmed, lower-cased) on the API. An invalid address gets a validation error on the field, and the order is not created.

**API changes (additive, approved in the plan review).**
- Migration V54: `live_exam_orders.receipt_email varchar(254) null`.
- `POST /api/v1/live-exam-orders` accepts an optional `receiptEmail` in the body, for both the single target and the bundle target. Missing or blank means null.
- `OrderView` and the student order list row (`OwnOrderRow`) return `receiptEmail` (null when none), so payment history can show where the receipt went.
- `ExamMailQueue.enqueueForSucceededOrder`: the receipt recipient is `order.receiptEmail` when set; otherwise the student's account email only when `emailVerifiedAt` is set; otherwise no receipt row is queued. The purchase notice to the Master Admin is unchanged.
- `ExamMailTexts` receipt uses the same recipient.

**Web changes.**
- The purchase sheet (`web/src/components/student/purchase/purchase-sheet.tsx`) gets an optional field "Send receipt to (optional)" above the pay button, with a small "Skip" control. Helper line: "We send your receipt here. If you leave it empty, we send it to your verified email."
- If the account email is not verified, the helper line says so ("Verify your email to get receipts by mail") and links to the account page.
- Both the single-item purchase and the bundle purchase show the field.
- The value is sent only when filled in. Skip clears it.
- Payment history detail shows "Receipt sent to: {email}" when the order has one, or "Receipt sent to your account email" when it used the verified account email, or "No receipt email" otherwise.
- Keys in English and Bangla (drafts).

**Tests.**
- API: each recipient rule (typed address wins; verified account email used; unverified account email and nothing typed gives no mail; invalid address rejected; bundle path); the view returns `receiptEmail`; migration applies on the review database.
- Web: field shows in the purchase sheet; skip clears it and the body has no `receiptEmail`; invalid address shows the field error; history detail shows the right line for each case.

**Check.** On the review data: a verified student buys with no address, and a receipt row is queued for the account email. The same student buys with an address, and the row uses that address. An unverified student buys with no address, and no receipt row is queued.

## 3. What is not changing

- Email verification flow, the verification code step and the account email page.
- The purchase notice to the Master Admin.
- Print and PDF receipts from payment history.
- Refunds: none (per the user's decision).

## 4. Open question for the user (not blocking)

Should the typed receipt address also be saved to the student's profile as the default for next time? Default in this plan: no.
