# Step 4 — Google sign-in

API only. The button is step 6. Do not reopen the completed `api-identity` slices. Add a new auth path beside them.

## Outcome

A student can sign in with Google. If Google says the email is verified and that email already belongs to an account, the Google identity attaches to that account. If the email is new, the sign-in creates a student who still has to set a phone, with no SMS OTP. A Google-only account can exist without a password.

## Bind these

- Do not link on an unverified email.
- Google sign-in is for students only. Do not link or sign in an admin or instructor.
- Linking never creates a second account for an email that already exists.
- No actor sets, sees, or resets another user's password. The Google path does not return a password or an activation code.
- Phone stays required before the student can buy or take an exam. Collect it once, with no OTP.
- Email and phone register, login, and the email reset stay as they are.
- Client id and client secret come from environment variables. The plan names them and does not contain secret values.
- `X-Client-Type: web` still returns the refresh token only as an httpOnly cookie, as in Phase 1.

## Where the code is

- `AuthController` and `AuthService.register` require name, phone, email, and password today.
- `SecurityConfig` lists the public auth posts. The Google callback has to be added there deliberately.
- Users and roles: `com.coachingcenter.api.auth` and `com.coachingcenter.api.users`. Role `STUDENT` is the account this creates.
- The identity plan's "no Google" line is marked outdated as a product ban. The completed slices stay closed.

## Plan must name

- The start call and the callback.
- The stored Google subject, and the unique link to one user.
- The response when the email matches, when it is new, and when Google did not verify the email.
- How a new user submits a phone, and what buy or take returns before that phone exists.
- The `*IT` cases for link, create, unverified email, and a repeated callback.

## Out of this step

The website button, catalog, payments, and notifications.
