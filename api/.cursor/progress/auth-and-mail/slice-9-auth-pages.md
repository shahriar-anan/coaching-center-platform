# Slice 9 — Auth pages

Done. The website has `/register`, `/forgot-password`, `/reset-password`, and `/account`. Register says the student can sign in and does not trap them on a verify wall. Forgot-password always shows the same confirmation and the button says “Send code” / “কোড পাঠান”. Reset takes the code and the new password, then offers login. Account shows whether the email is verified, resends verification, and changes the password. A student who signs in on these paths lands on `/account`. `/activate` is unchanged. The Google button and the set-phone page are not in this slice. The forgot-password prompt in `docs/exam-site/ui/student-flow-prompts.md` now says the mail contains a code.

`npm test` in `web`: 41 tests passed, including 6 auth-page tests with mocked fetch. `npm run lint` reported two existing image warnings and no errors. `npm run build` succeeded. Shahriar still does the browser pass.

Refund (slice 10) is not started.
