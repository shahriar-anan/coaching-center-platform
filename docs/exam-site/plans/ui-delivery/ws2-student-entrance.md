# WS2 — Student entrance, account, notices, policy pages

Branch `ui/ws2-student-entrance`, agent A in phase 2, cut from `catalog-acquisition` after WS0 is merged. Read [README.md](README.md) and [ws0-foundation.md](ws0-foundation.md). Pages and states follow [../../ui/student-flow-prompts.md](../../ui/student-flow-prompts.md) Prompt 1, and the Notices and Account pages of Prompt 2, with the changes listed here.

**Copy:** Bangla quoted in this file shows intent only. Write the final Bangla by [bangla-copy.md](bangla-copy.md) (its glossary and rewrites win, for example লগইন not প্রবেশ, সরাসরি ভর্তি not রোস্টার, শিক্ষক not ইনস্ট্রাক্টর, তারিখ পরে জানানো হবে). Build every link to the other surface with `siteHref()` or `staffHref()` from `lib/config`.

## Owns

- `web/src/app/(auth)/login`, `register`, `forgot-password`, `reset-password`, `activate`, `set-phone`
- `web/src/app/(site)/account/**` (profile, password, phone, email verification, payment history at `/account/payments`)
- `web/src/app/(site)/notices`
- `web/src/app/(site)/help`, `about`, `contact`, `terms`, `privacy`, `refund`
- `web/src/app/(auth)/login/google` (the return page after Google sign-in)
- `web/src/components/student/notice-bell.tsx` (replace WS0's stub; keep its export signature, because `StudentShell` renders it)
- `web/src/lib/api/exam/account.ts`, `web/src/lib/api/exam/notices.ts`
- `web/src/i18n/messages/{bn,en}/{auth,account,notices,legal}.ts`
- Remove the old `app/login`, `app/register`, `app/forgot-password`, `app/reset-password`, `app/activate`, `app/account`, and move their tests here
- Tests under `web/tests/ws2/**`

The landing page `/` belongs to WS3 (it shares the home with the signed-in dashboard).

## Pages

| Route | What | API |
|---|---|---|
| `/login` | Email or phone, password, show/hide, "প্রবেশ করুন". Disabled Google button with "শীঘ্রই" (U10). Links: register, forgot, activate. One generic failure sentence. Lock state. `?next=` honored. Staff sign in here too. | `POST /auth/login` |
| `/register` | Name, phone (helper "০১ এবং আরও ৯ সংখ্যা"), email, password (8–72). Separate inline duplicate-phone and duplicate-email errors (map the API codes). Success screen "অ্যাকাউন্ট তৈরি হয়েছে" with "প্রবেশ করুন", and a line that a verification code was emailed. | `POST /auth/register` |
| `/forgot-password` | Email, "কোড পাঠান". Always the generic sentence. Respect the rate-limit error. | `POST /auth/forgot-password` |
| `/reset-password` | Code, new password. Invalid-code sentence. Success to login with the "নতুন পাসওয়ার্ড দিয়ে প্রবেশ করুন" note. | `POST /auth/reset-password` |
| `/activate` | Phone, code, new password. Invalid-code sentence. Success to login. | `POST /auth/activate` |
| `/set-phone` | One phone field, for a student whose `phoneRequired` is true. Return to `?next=`. | `POST /me/phone` |
| `/account` | Profile card: name (editable), email (read-only) with verified or "যাচাই হয়নি" and "যাচাইয়ের কোড পাঠান", then a code field to verify; phone with "বদলান" (the own-phone change exists now; the "cannot change" copy in the prompt is out of date); student code. Change password (current and new). Language and theme repeat the header pills. Logout with confirm. | `GET /me`, `PATCH` name (check the existing call), `POST /auth/verify-email/resend`, `POST /auth/verify-email`, `POST /auth/change-password`, `POST /auth/change-phone` |
| `/account/payments` | The student's orders, newest first: product title, scope (মডিউল or পরীক্ষা), amount, status pill, method (bKash, VISA...) when known, date, transaction id with copy. A pending row links to the payment result page. Empty: "এখনো কোনো পেমেন্ট নেই". | `GET /me/live-exam-orders` (WS1 F) |
| `/notices` | List with unread gold dot, "সব পঠিত", each row opens the exam page. Kinds: reminder, MCQ score, written result. Empty state. Paged. | `GET /me/notices`, `POST /me/notices/{id}/read`, `POST /me/notices/read-all` |
| Bell | In the header for signed-in students: count from `/me/notices/unread-count` (poll on focus and every 2 minutes), opens `/notices`. | `GET /me/notices/unread-count` |
| `/login/google` | The API sends the browser here after Google (`app.google.post-login-path`). Success: refresh the session and continue to `next` or `/`; `phoneRequired` goes to `/set-phone`. Error codes from the query (`GOOGLE_ACCOUNT_INACTIVE`, `GOOGLE_ROLE_NOT_ALLOWED`, unverified email, a generic failure) show the sentences from Prompt 1 and a link back to `/login`. The button stays disabled tonight (U10), but this page must exist so turning Google on later needs no web change. | `GET /me` |
| `/help` | Help centre, LMS standard: searchable FAQ accordion (filters the loaded list) in sections কেনা ও পেমেন্ট, লাইভ পরীক্ষা, এমসিকিউ, রিটেন খাতা আপলোড, ফলাফল ও মেধাতালিকা, অ্যাকাউন্ট. Answers state the real rules (one live sitting, the later MCQ attempt and its leaderboard rule, PDF only up to 80 MB, written results after publish, module purchase covers future exams). A contact card at the end. Placeholder banner like the policy pages. | none |
| `/about`, `/contact`, `/terms`, `/privacy`, `/refund` | Readable long-form pages in the student shell, with a visible "নমুনা লেখা — ক্লায়েন্ট বদলাবেন" banner. Refund page says purchases are digital access and refunds are handled by the center through the payment provider. Contact page shows placeholder phone, email and address. Bangla and English. | none |

Not-found and error pages use the student shell with "কিছু পাওয়া যায়নি" and "হোমে ফিরুন".

## Rules

- Never say which login field was wrong. Never show a countdown the server did not send.
- Passwords use `autocomplete` `current-password` or `new-password`. Email uses an email keyboard; phone uses a numeric keyboard.
- After an auth success, invalidate the `me` query.
- Each form keeps values after an error and focuses the first invalid field.

## LMS polish

- Registration shows inline strength guidance for the password (length only; the server's rule is 8–72), and the phone field formats as the student types. *(P1)*
- After the first sign-in of a new student, a one-time welcome card on the home (owned by WS3) is triggered by a `localStorage` flag WS2 sets on registration success: `exam-portal.welcome=1`. *(P1)*
- Account page shows a small "নিরাপত্তা" section: last password change is not in the API, so only "পাসওয়ার্ড বদলান" and "সব ডিভাইস থেকে বেরিয়ে যান" if `POST /auth/logout` revokes all sessions; otherwise leave that button out. Check the API before drawing it. *(P2)*

## Tests

Login (success routes by role, generic failure, lock, `next`), Google return page states, help search, register (duplicate phone and email states), forgot (always generic), account (verify flow, password errors, phone change), payments list (statuses, empty), notices (mark one, mark all, empty), bell count.

## Report

**Done (agent A, phase 2).** All P0 pages in the table: `/login` (with `?next=`, generic failure, lock, wrong-audience message on split hosts, disabled Google button with "শীঘ্রই"), `/login/google` (error codes from `?error=`, otherwise refresh and route, `phoneRequired` to `/set-phone`), `/register` (separate duplicate-phone and duplicate-email errors, success screen, sets `exam-portal.welcome=1`), `/forgot-password` (always the generic sentence), `/reset-password`, `/activate` (`অ্যাকাউন্ট চালু করুন`), `/set-phone`, `/account` (verify email, change phone, change password, logout with confirm), `/account/payments` (`GET /api/v1/me/live-exam-orders`, merged row shape), `/notices` (mark one, mark all, open exam), `NoticeBell` (count from `/me/notices/unread-count`, refresh on focus and every 2 minutes), `/help` (native-details FAQ with search), `/about`, `/contact`, `/terms`, `/privacy`, `/refund` (placeholder banner). The old `app/login`, `app/register`, `app/forgot-password`, `app/reset-password`, `app/activate` and `app/account` are deleted, and their test moved to `tests/ws2/`. Data hooks: `lib/api/exam/account.ts`, `lib/api/exam/notices.ts`.

**P1 done:** the welcome flag (`exam-portal.welcome=1` on registration success).
**P2 done:** none.
**Skipped:** the student name edit (there is no API endpoint for it, so the name is shown read-only with a note); the notice count on the bottom tab bar (the count shows on the bell only); password strength meter and live phone formatting (P1, not built); the account security section (P2; `POST /auth/logout` does not revoke all sessions).

**Checks (after merging `catalog-acquisition` at 70e1ec7):** `tsc --noEmit` clean; `npm test` 151 passed in 17 files; `npm run lint` 0 errors, 2 warnings (both pre-existing, in old admin pages); `npm run build` passed under the heavy lock.

**Notes for the integrator and other agents:**
- Any page that renders `RequireStudent` or `RequireStaff` must sit inside `<Suspense>`, because both call `useSearchParams`. Done for login, Google return, set-phone, account, payments, notices, and the staff layout.
- Login redirects straight to the destination on success. The Prompt 1 "হোমে যান" confirmation card is not built.
- The bottom tab bar in `StudentShell` (WS0 file) has no notice badge yet. `NoticeBell` has the count, so the shell can reuse `useUnreadCount` if the integrator wants it there.
- `/login` and the other auth routes now live in `(auth)`. Nothing else in the app should link to the old paths.
- Payment statuses are `PENDING`, `PAID`, `FAILED`, `CANCELLED` (the API's public names). Pending rows link to `/purchase/result?tranId=` (WS3).

**Contract issues:** none.
