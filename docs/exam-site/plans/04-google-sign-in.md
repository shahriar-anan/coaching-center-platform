# Step 4 — Website Google sign-in

API only. The API classes and Flyway `V33` are in the tree. This file was not marked confirmed, and a live Google sign-in has not been run. Do not edit `V1`–`V33`. Package `com.coachingcenter.api.auth`. The phone gate also touches `com.coachingcenter.api.exams`. The website button and `POST /api/v1/me/phone` page stay in step 6. Account mail does not send a verification code for Google sign-in.

Decisions in `../decisions.md` ("Accounts and notices") and the rules in `../steps/04-google-sign-in.md` stay in force. This plan adds the slice order, the callback URL, the columns, the calls, and the tests.

The Google button is step 6, wave 1, in `../steps/06-student-website.md`. This plan defines the browser contract that button will call. It does not add the button, a Flutter client, or a mobile OAuth client.

Automated tests use a stub token endpoint and a locally signed ID token. They do not call Google. A manual sign-in waits until the client has registered the callback below and Shahriar runs it in a browser. This plan does not claim that live sign-in works.

## 1. Executive summary

A student on `https://symamlaw.com` starts Google sign-in with a top-level navigation to the API. The API keeps the client secret, sends the browser to Google with `state`, `nonce`, and PKCE, and receives the authorization code at `https://api.symamlaw.com/api/v1/auth/google/callback`.

Spring Security's OAuth2 client and JOSE support exchange the code and validate the OpenID Connect ID token. The API then resolves Google's `sub` to one user. A verified email that already belongs to an `ACTIVE` student is linked. An admin or instructor email is refused. A new email creates an `ACTIVE` `STUDENT` with no password and no phone. The API sets the existing `refresh_token` httpOnly cookie and redirects the browser to the website. The access token stays in memory, obtained by the existing `POST /api/v1/auth/refresh` call. It is not placed in the URL.

Purchase and Take exam reject a student whose phone is still null. The student submits the phone once, with no SMS OTP, through `POST /api/v1/me/phone`. Email and phone registration, login, and password reset stay as they are.

The proposed callback `https://api.symamlaw.com/auth/google/callback` does not match this API. Every application route is under `/api/v1`, and there is no servlet context path. The callback to register is `https://api.symamlaw.com/api/v1/auth/google/callback`.

## 2. Current authentication architecture

Verified from the codebase on this plan's date.

| Fact | Where |
|---|---|
| Spring Boot `4.1.1`, Java `21`, Maven | `api/pom.xml` |
| Flyway, `ddl-auto=validate`, PostgreSQL | `api/src/main/resources/application.properties` |
| Stateless JWT filter. CSRF disabled. No form login | `SecurityConfig` |
| Access token: HMAC JWT, 15 minutes, claims `sub` (user id) and `role` | `JwtService`, `app.jwt.access-token-minutes` |
| Refresh token: 32 random bytes, stored as a hash, 30 days, rotated, reuse revokes the family | `AuthService.issueTokens`, `RefreshToken` |
| `X-Client-Type: web` sets `refresh_token` as an httpOnly, `Secure`, `SameSite=Strict` cookie on path `/api/v1/auth`. The JSON body omits `refreshToken` | `AuthController.tokenResponse` |
| `X-Client-Type: mobile` returns `refreshToken` in JSON and sets no cookie | same method |
| Website client keeps the access token in memory and calls `POST /api/v1/auth/refresh` with `credentials: 'include'` | `web/src/lib/api/client.ts`, `web/src/lib/auth` |
| CORS credentials are allowed for `app.cors.allowed-origins`. Dev defaults to `http://localhost:3000`. Prod and staging require `WEB_ORIGIN` | `WebCorsConfig`, `application-dev.properties`, `application-prod.properties` |
| Self-registration creates an `ACTIVE` student with a password and a required phone. Email verification does not block login | `AuthService.register` |
| Admin-created accounts start as `PENDING_ACTIVATION` with a null password hash | `AccountService.newPendingUser` |
| Login requires `ACTIVE`, a password hash, and a matching password. A null hash fails login | `AuthService.login` |
| Password reset is an email code. No actor sets another user's password | `AuthService.forgotPassword`, `AuthService.resetPassword` |
| Roles: `STUDENT`, `MASTER_ADMIN`, `SYSTEM_ADMIN`, `INSTRUCTOR` | `Role`, `V28__live_exam_evaluation.sql` |
| Statuses: `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`. There is no `RESTRICTED` status | `V1__identity.sql` |
| `users.phone` is `NOT NULL`, unique among rows with `deleted_at IS NULL`, and must match `+8801` plus nine digits | `V1__identity.sql`, `PhoneNumbers` |
| `users.email` is `NOT NULL` and unique on the same partial index. Registration stores it lowercased | `V1__identity.sql`, `AuthService` |
| `password_hash` is already nullable | `User`, `V1__identity.sql` |
| `GET /api/v1/me` returns `MeView`. Jackson omits null fields | `AuthController.me`, `spring.jackson.default-property-inclusion=non_null` |
| Errors are `code`, `message`, optional `fieldErrors`, `timestamp`, `requestId` | `ApiError` |
| Purchase starts at `POST /api/v1/live-exam-orders`. Take exam is `POST /api/v1/live-exams/{id}/take`. Retake is `POST /api/v1/live-exams/{id}/retake` | `LiveExamOrderController`, `LiveExamSittingController` |
| Tests extend `PostgresIntegrationTest` and run against real PostgreSQL. `SchemaIT` lists the `users` columns exactly | `api/src/test/java/com/coachingcenter/api` |

`api/pom.xml` has `spring-boot-starter-security` and `nimbus-jose-jwt` `10.0.2` for the application's own HMAC access tokens. It has no OAuth2 client starter. There is no Google registration, no `google_sub` column, and no phone-required check. Every current user row has a phone, so purchase and Take exam do not look for a missing phone.

`symamlaw.com` and `api.symamlaw.com` are the same registrable site and different origins. The existing cookie is host-only on the API host. A same-site `fetch` from the website to the API sends it. A navigation that begins on `accounts.google.com` does not send a `SameSite=Strict` cookie. The OAuth state cookie in this plan is therefore `SameSite=Lax`. The refresh cookie stays `Strict`.

## 3. Files and classes inspected

- `api/pom.xml`
- `api/src/main/resources/application.properties`
- `api/src/main/resources/application-dev.properties`
- `api/src/main/resources/application-prod.properties`
- `api/src/main/resources/application-staging.properties`
- `api/src/main/resources/db/migration/V1__identity.sql`
- `api/src/main/resources/db/migration/V28__live_exam_evaluation.sql`
- `api/src/main/resources/db/migration/V32__live_exam_purchase.sql`
- `api/src/main/java/com/coachingcenter/api/auth/config/SecurityConfig.java`
- `api/src/main/java/com/coachingcenter/api/auth/AuthController.java`
- `api/src/main/java/com/coachingcenter/api/auth/AuthService.java`
- `api/src/main/java/com/coachingcenter/api/auth/JwtService.java`
- `api/src/main/java/com/coachingcenter/api/auth/JwtAuthenticationFilter.java`
- `api/src/main/java/com/coachingcenter/api/auth/PhoneNumbers.java`
- `api/src/main/java/com/coachingcenter/api/auth/ClientType.java`
- `api/src/main/java/com/coachingcenter/api/auth/Role.java`
- `api/src/main/java/com/coachingcenter/api/users/User.java`
- `api/src/main/java/com/coachingcenter/api/users/UserRepository.java`
- `api/src/main/java/com/coachingcenter/api/users/StudentProfile.java`
- `api/src/main/java/com/coachingcenter/api/users/AccountService.java`
- `api/src/main/java/com/coachingcenter/api/common/config/AppProperties.java`
- `api/src/main/java/com/coachingcenter/api/common/web/WebCorsConfig.java`
- `api/src/main/java/com/coachingcenter/api/common/error/ApiError.java`
- `api/src/main/java/com/coachingcenter/api/common/error/ErrorCode.java`
- `api/src/main/java/com/coachingcenter/api/exams/LiveExamOrderController.java`
- `api/src/main/java/com/coachingcenter/api/exams/LiveExamOrderService.java`
- `api/src/main/java/com/coachingcenter/api/exams/LiveExamSittingService.java`
- `api/src/test/java/com/coachingcenter/api/SchemaIT.java`
- `api/src/test/java/com/coachingcenter/api/auth/TokenIT.java`
- `api/src/test/java/com/coachingcenter/api/auth/PasswordIT.java`
- `web/src/lib/api/client.ts`
- `web/src/lib/api/types.ts`
- `docs/exam-site/decisions.md`
- `docs/exam-site/steps/04-google-sign-in.md`
- `docs/exam-site/steps/06-student-website.md`

Official behavior this design follows:

- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect): authorization-code flow, ID token claims, `sub` as the stable identifier, `email` as a changeable attribute.
- [Google OpenID Connect reference](https://developers.google.com/identity/openid-connect/reference): `iss` is `https://accounts.google.com` or the legacy value `accounts.google.com`. `email_verified` is a boolean. `sub` is at most 255 ASCII characters.
- [Spring Security authorization grants](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html): PKCE is automatic for a public client. A confidential client enables it with `ClientRegistration` `requireProofKey`. `OAuth2AuthorizationRequestCustomizers.withPkce()` stores the verifier for the token request.
- Spring Boot `4.1` supplies `spring-boot-starter-security-oauth2-client`. The older `spring-boot-starter-oauth2-client` name is deprecated. Do not pin a version. The parent BOM selects it.

## 4. Recommended website-only flow

Do not enable Spring Security's stock `oauth2Login()` filter. That filter uses `/oauth2/authorization/{id}` and `/login/oauth2/code/{id}`, and its default request store is the HTTP session. `SecurityConfig` is `SessionCreationPolicy.STATELESS`. Turning sessions on for the whole API would change every other route.

Use the OAuth2 client library from a small controller instead.

1. The website, in step 6, navigates the browser to `GET https://api.symamlaw.com/api/v1/auth/google`. This is a top-level navigation, not a `fetch`.
2. `GoogleSignInController.start` builds an authorization-code request for the registration id `google`:
   - scopes `openid`, `email`, `profile`
   - `state` from Spring's generator
   - `nonce`, because the scope contains `openid`
   - PKCE `S256`, because `requireProofKey` is true
   - `redirect_uri` exactly equal to `GOOGLE_REDIRECT_URI`
3. The controller stores the `OAuth2AuthorizationRequest` in an encrypted, httpOnly cookie and responds `302` to Google's authorization endpoint. The client secret is not in that URL.
4. Google redirects the browser to `GET /api/v1/auth/google/callback` with `code` and `state`, or with `error`.
5. The controller loads the cookie, compares `state` in constant time, and rejects a missing, mismatched, or expired cookie. It then clears the cookie so the same state cannot be presented again.
6. `RestClientAuthorizationCodeTokenResponseClient` exchanges the code. Spring Security 7 renamed the older `DefaultAuthorizationCodeTokenResponseClient`. The client secret and the PKCE verifier stay on the server. The redirect URI in the token request is the same value sent to Google.
7. `OidcIdTokenDecoderFactory` validates the ID token signature against Google's JWKS, then the issuer, audience, and expiry. The controller also checks `nonce`, `azp` when present, and `email_verified`.
8. `GoogleAccountService` resolves the account inside one transaction and `AuthService` issues the existing web refresh cookie. The Google access token is discarded. It is not stored and not returned.
9. The controller responds `302` to `{WEB_ORIGIN}{post-login-path}`. The default path is `/login/google`. The location contains no access token, refresh token, authorization code, or client secret.
10. Step 6's page calls the existing `POST /api/v1/auth/refresh` with `X-Client-Type: web` and credentials. That rotates the refresh cookie and returns `accessToken` in JSON. The page stores that token in memory and calls `GET /api/v1/me`.

`SameSite=Lax` on the state cookie is what allows the browser to send it on Google's top-level return. `SameSite=Strict` on `refresh_token` stays. The cookie is set on the callback response and is sent later on the website's same-site `fetch` to the API, which is the current web session.

The return origin is `WEB_ORIGIN`, the value CORS already trusts. A `next` query parameter is accepted only when it matches `^/[A-Za-z0-9._~/-]{1,200}$`, contains no `//`, and contains no backslash. It is stored inside the encrypted state cookie and appended to the success redirect. Any other `next` is ignored. The callback never reads a return URL from Google or from a new query parameter.

Failure also redirects to that page, with `?error=` set to one of the codes in the contract below. Google's `error_description` is not copied into the URL or the logs.

The callback query string contains the authorization code. Do not add a request logger that records it. There is no such logger today.

## 5. Endpoints and API contracts

Paths sit on `AuthController`'s `/api/v1` prefix, except the two Google navigations, which are their own controller so their redirects stay out of the JSON login methods. `SecurityConfig` permits these three routes and leaves every other auth route as it is.

### `GET /api/v1/auth/google`

| | |
|---|---|
| Auth | None |
| Query | Optional `next`, validated as above |
| Success | `302` to Google. `Set-Cookie: google_oauth` |
| Disabled | `503` `GOOGLE_SIGN_IN_UNAVAILABLE` when the client id, secret, or redirect URI is blank. The API still starts |
| Cookie | httpOnly, `Secure`, `SameSite=Lax`, path `/api/v1/auth/google`, max age 10 minutes, no `Domain` attribute. Value is AES-GCM ciphertext of the authorization request, with a random IV. The key is `JWT_SECRET`, which is already required to be at least 32 bytes. The cookie is not a JWT |

`Secure` matches the refresh cookie. Local HTTP on `localhost` does not store a `Secure` cookie. Manual local sign-in needs HTTPS, or a dev-only relaxation that production cannot select. Automated tests do not need the browser to store it. They drive the repository directly.

### `GET /api/v1/auth/google/callback`

| | |
|---|---|
| Auth | None. The state cookie is the browser binding |
| Query | `code` and `state`, or `error` |
| Success | `302` to the website. `Set-Cookie` clears `google_oauth`. `Set-Cookie` sets `refresh_token` with the existing attributes in `AuthController.refreshCookie`. Client type is `WEB` |
| Google `error=access_denied` | `302` `?error=GOOGLE_CANCELLED`. No token request |
| Bad or missing state, expired cookie, nonce mismatch | `302` `?error=GOOGLE_STATE_INVALID`. No token request |
| Token endpoint or ID token rejection | `302` `?error=GOOGLE_SIGN_IN_FAILED` |
| Account rule failed | `302` `?error=` plus the code from section 6. No refresh cookie |

A second request with the same state finds no cookie and returns `GOOGLE_STATE_INVALID`. Google also treats an authorization code as single-use. Account writes stay safe when two different codes for the same person arrive together, because of the unique indexes and the transaction in section 6.

### `POST /api/v1/me/phone`

| | |
|---|---|
| Auth | Bearer access token |
| Body | `{ "phone": "01XXXXXXXXX" }` |
| Validation | `PhoneNumbers.normalize`. The stored value is `+8801` plus nine digits |
| Success | `200` `MeView` |
| Phone already set | `409` `PHONE_ALREADY_SET` |
| Phone in use by another live row | `409` `DUPLICATE_PHONE` |
| Bad phone | `400` `VALIDATION_FAILED` |

This call does not prove the phone is verified. The API has no phone-verification column and no SMS OTP. `../decisions.md` says the new Google user sets a phone once, with no OTP.

### `GET /api/v1/me`

Unchanged path. `MeView` gains `phoneRequired`, a boolean, so the website can see the requirement when Jackson omits a null `phone`. `phoneRequired` is true only when the signed-in user's phone is null. Existing clients ignore the new field. `web/src/lib/api/types.ts` is updated in step 6, when `MeView.phone` becomes optional.

### Purchase and sitting

`LiveExamOrderService.initiate`, `LiveExamSittingService.take`, and `LiveExamSittingService.retake` check the student phone after the current actor is known and before they create an order or a participant. A null phone returns `403` `PHONE_REQUIRED` with message `A phone number is required.` Password-registered students already have a phone, so this check does not change their result.

Answer save, submit, and the question PDF stay behind Take exam. They do not grow a second phone check.

### Website page, step 6

`GET {WEB_ORIGIN}/login/google` is the default return. On load it calls `POST /api/v1/auth/refresh`. Success stores the access token and calls `GET /api/v1/me`. `phoneRequired: true` shows the phone form, which posts to `POST /api/v1/me/phone`. A non-student `role` does not enter the admin shell. That gate is already a step 6 rule. `?error=` shows a fixed message for that code. The page does not read a token from the query string.

## 6. Account creation and linking

`GoogleAccountService.resolve` runs in one transaction. Email comparison uses `AuthService`'s lowercase trim. `sub` is stored exactly as Google sent it.

Resolution order:

1. Validate the ID token first. A failed validation creates nothing and links nothing.
2. Require `sub`, non-blank, at most 255 ASCII characters.
3. Load the live user with that `google_sub`.
4. When that user exists, sign in that user. Do not change `email`, `role`, `phone`, or `password_hash`. A Google email that has since changed does not overwrite the local email.
5. When no `sub` matches, load the live user by email.
6. When that user is an `ACTIVE` `STUDENT` and `google_sub` is null, and `email_verified` is true, set `google_sub` and sign in. Role and status stay as they are.
6a. When that user is `MASTER_ADMIN`, `SYSTEM_ADMIN`, or `INSTRUCTOR`, redirect `GOOGLE_ROLE_NOT_ALLOWED`. Do not write `google_sub` and do not issue a session. Shahriar decided this on 2026-10-23: Google sign-in is for students only.
7. When that user is `ACTIVE` and `google_sub` is a different value, redirect `GOOGLE_IDENTITY_CONFLICT`. Do not move the link.
8. When that user is `PENDING_ACTIVATION` or `INACTIVE`, redirect `GOOGLE_ACCOUNT_INACTIVE`. Do not write `google_sub` and do not issue a session. Google sign-in does not activate an admin-created account and does not pass the activation code.
9. When no live user has that email, and `email_verified` is true, and `email` is present, insert a student.
10. A missing email redirects `GOOGLE_EMAIL_MISSING`. `email_verified` other than true redirects `GOOGLE_EMAIL_NOT_VERIFIED`. Accept JSON boolean true. Also accept the string `true`, because Google's narrative example has used that form. Reject false, null, and every other value.
11. On a unique-index violation, read again by `google_sub` and then by email, and apply the same rules once. A second conflict redirects `GOOGLE_SIGN_IN_FAILED` and rolls back.

The new student:

- `role` `STUDENT`, `status` `ACTIVE`
- `email_verified_at` set to the current time, because Google verified the email. Do not send the local email-verification code
- `password_hash` null. `AuthService.login` already rejects a null hash, so password login stays closed until the person uses the existing email reset, which can set a password
- `phone` null
- `student_profiles` row with `full_name` from the ID token `name`, trimmed, at most 255 characters. When `name` is missing, use the email local-part. `student_code` uses the same allocator as `AuthService.register`
- `google_sub` set

Sign-in calls a new package method on `AuthService` that uses `issueTokens` with `ClientType.WEB`. It checks `ACTIVE` again before writing the refresh row. It sets `last_login_at`. It does not call `setRole`.

Linking is silent for an `ACTIVE` student whose Google email is verified. That is the rule in `../decisions.md`, narrowed to students. The trade-off: a local student account whose email was never confirmed by this API can be entered by the person who controls that mailbox at Google. Requiring the local password first would block that, and it would also block the one-click sign-in the product asked for. An admin or instructor email is refused. Google sign-in does not change `role` and does not issue a session for those accounts.

`users.email` remains the unique mailbox. `google_sub` is the provider identity. Email is only the attribute used to attach a first-time Google identity to an existing row.

Audit `GOOGLE_ACCOUNT_CREATED` or `GOOGLE_ACCOUNT_LINKED` with the user id. Do not put `sub`, the email, the code, or the ID token in the audit metadata.

## 7. Phone-number requirement

A null `users.phone` means the phone has not been supplied. Nothing in this API marks a phone as verified. Collecting it is not verification. Password registration still requires a phone and is unchanged.

The website learns the gap from `MeView.phoneRequired` after refresh. The server enforces it on:

- `LiveExamOrderService.initiate`
- `LiveExamSittingService.take`
- `LiveExamSittingService.retake`

`403` `PHONE_REQUIRED` is the response. The website shows the phone form. After `POST /api/v1/me/phone` succeeds, those calls proceed under the existing purchase and sitting rules.

Duplicate phones use the existing `DUPLICATE_PHONE` conflict. A second submission after a phone is stored returns `PHONE_ALREADY_SET`. This endpoint is not a phone-change feature.

## 8. Database changes

`V33__google_identity.sql`. Rollback is a later `V34` that drops the index and the column and restores the phone constraint. Do not edit `V1`. Existing rows keep their phones and gain a null `google_sub`.

```sql
ALTER TABLE users DROP CONSTRAINT users_phone_format;
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE users
    ADD CONSTRAINT users_phone_format CHECK (phone IS NULL OR phone ~ '^\+8801[0-9]{9}$');

ALTER TABLE users ADD COLUMN google_sub VARCHAR(255) NULL;
CREATE UNIQUE INDEX users_google_sub_active_idx
    ON users (google_sub) WHERE google_sub IS NOT NULL AND deleted_at IS NULL;
```

A column is enough. The product names one provider. A separate identity table is the right shape if a second provider is added later. It is not needed to link one `sub` to one user.

`User.phone` becomes `nullable = true` so `ddl-auto=validate` matches. `User.googleSub` is a nullable column of length 255. `UserRepository` gains `findByGoogleSubAndDeletedAtIsNull`.

PostgreSQL unique indexes already allow many null phones and many null subjects. The partial index keeps that, and it allows the same `sub` again after a soft delete, which is how email and phone already work.

`SchemaIT.identityTablesMatchThePhaseSchema` adds `google_sub` to the `users` column set in the same change.

## 9. Dependencies and environment

Add, with no version tag:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-client</artifactId>
</dependency>
```

The `4.1.1` parent supplies the version. That starter brings `spring-security-oauth2-client` and `spring-security-oauth2-jose`. Keep `nimbus-jose-jwt` `10.0.2` for the application's HMAC access tokens. Do not verify Google ID tokens with a hand-written signature check. Do not add a Google API client for this flow.

Properties, empty by default so a checkout without Google still boots:

```properties
app.google.client-id=${GOOGLE_CLIENT_ID:}
app.google.client-secret=${GOOGLE_CLIENT_SECRET:}
app.google.redirect-uri=${GOOGLE_REDIRECT_URI:}
app.google.post-login-path=${GOOGLE_POST_LOGIN_PATH:/login/google}
```

Add the matching `Google` record on `AppProperties`. Build one `ClientRegistration` id `google` only when the id, secret, and redirect URI are all non-blank. Use Spring's Google provider metadata for the authorization endpoint, token endpoint, JWKS URI, and issuer. Set `clientAuthenticationMethod` to `client_secret_basic` and `requireProofKey` to true. Scopes are `openid`, `email`, and `profile`.

| Variable | Purpose |
|---|---|
| `GOOGLE_CLIENT_ID` | Audience check and the authorization request. Not a secret. The website does not need it for this flow |
| `GOOGLE_CLIENT_SECRET` | Token request only. Environment or the existing secret mechanism. Never a frontend variable, response, URL, log, or Git file |
| `GOOGLE_REDIRECT_URI` | Exact redirect. Production value `https://api.symamlaw.com/api/v1/auth/google/callback` |
| `GOOGLE_POST_LOGIN_PATH` | Path on `WEB_ORIGIN`. Default `/login/google` |
| `WEB_ORIGIN` | Already the CORS origin and the only return origin. Production example `https://symamlaw.com` |
| `JWT_SECRET` | Already required. Also the AES-GCM key for the state cookie |

Do not rely on Spring's `{baseUrl}` redirect template. Behind the proxy it can become an internal host. The registered URI and `GOOGLE_REDIRECT_URI` are the same string.

Dev and prod use the same variable names. Dev may point `GOOGLE_REDIRECT_URI` at a local HTTPS callback only when that exact URI is also registered on the Google client. Tests set the three values to fixtures and replace the token client and the ID token decoder. They do not read a real secret.

## 10. Security considerations

- The client secret stays in the API process. The authorization URL contains the client id, which Google already treats as public.
- `state` is bound to an encrypted cookie. The callback rejects a missing or different state before it talks to Google.
- PKCE binds the code exchange to the verifier in that cookie.
- `nonce` in the ID token must equal the nonce in the cookie.
- The ID token signature, issuer, audience, and expiry come from Spring's OIDC decoder. Accept both Google issuer strings. `aud` must equal `GOOGLE_CLIENT_ID`. When `azp` is present it must equal that same client id.
- `email_verified` is an application check on top of the decoder. The decoder does not apply the product's linking rule.
- The state cookie is single-use and expires after 10 minutes. `google_oauth_states` stores only the SHA-256 of `state` and its expiry, so a replay is rejected on every API instance.
- The success redirect is `WEB_ORIGIN` plus a fixed path, plus an optional relative `next` that was stored before the browser left for Google.
- The refresh cookie keeps its current flags. The access token is issued only by `POST /api/v1/auth/refresh`.
- Google's access token is not stored. This flow does not request offline access.
- Login, registration, activation, and password reset are not modified. A Google-only account has no password hash, so password login keeps returning `INVALID_CREDENTIALS`.
- Linking never changes `role`. An inactive or pending account gets no session and no `google_sub` write.
- Logs and audit rows omit the client secret, authorization code, ID token, refresh token, and state cookie.

## 11. Implementation tasks

1. Add `V33__google_identity.sql`, the `User` mapping, the repository method, and the `SchemaIT` column list.
2. Add the OAuth2 client starter, `AppProperties.Google`, and the conditional `ClientRegistration`.
3. Add the encrypted cookie repository, `GoogleSignInController.start`, and the `SecurityConfig` permit for that GET.
4. Add the callback: state check, token client, ID token decoder, nonce and claim checks, then `GoogleAccountService`.
5. Issue the web refresh cookie through `AuthService` and redirect. Clear the state cookie on every callback outcome.
6. Add `POST /api/v1/me/phone` and `phoneRequired` on `MeView`.
7. Add `PHONE_REQUIRED` at the start of order initiate, take, and retake.
8. Run `GoogleSignInIT` and the existing `TokenIT` and `PasswordIT` against PostgreSQL.

Stop after the tests pass. The button and the `/login/google` page are step 6.

## 12. Testing and acceptance

`GoogleSignInIT` extends `PostgresIntegrationTest`. The token client and the ID token decoder are test beans. Each ID token is signed by a test key. No test opens a socket to Google.

| Case | Expected |
|---|---|
| Start | `302` to the Google authorization host, with `state`, `nonce`, `code_challenge`, and the configured redirect URI. `Set-Cookie` `google_oauth`. No client secret in the location |
| New student | Callback creates one `STUDENT`, `ACTIVE`, null phone, null password hash, `email_verified_at` set, `google_sub` set, one profile. `302` to the website. `refresh_token` cookie set. JSON body has no refresh token. `POST /api/v1/auth/refresh` with that cookie returns an access token that `GET /api/v1/me` accepts |
| Existing `ACTIVE` student, verified email, null `google_sub` | Same user id. `google_sub` filled. Role, phone, and password hash unchanged. One refresh cookie |
| Existing `ACTIVE` admin or instructor, verified email | `?error=GOOGLE_ROLE_NOT_ALLOWED`. `google_sub` stays null. No refresh cookie |
| `email_verified` false or missing | No insert, no link, `?error=GOOGLE_EMAIL_NOT_VERIFIED` |
| Missing email | No insert, `?error=GOOGLE_EMAIL_MISSING` |
| Wrong audience, wrong issuer, expired token, bad signature | `?error=GOOGLE_SIGN_IN_FAILED`. No insert |
| Missing, mismatched, or expired state. Nonce mismatch | `?error=GOOGLE_STATE_INVALID`. Token client is not called |
| `error=access_denied` | `?error=GOOGLE_CANCELLED`. Token client is not called |
| Token client throws | `?error=GOOGLE_SIGN_IN_FAILED` |
| Repeat the callback | `?error=GOOGLE_STATE_INVALID` |
| Two concurrent creates for one new `sub` | One user. The other request follows the existing-`sub` path |
| Verified email matches a user who already has a different `sub` | `?error=GOOGLE_IDENTITY_CONFLICT`. No overwrite |
| `PENDING_ACTIVATION` or `INACTIVE` | `?error=GOOGLE_ACCOUNT_INACTIVE`. `google_sub` stays null. No cookie |
| `next=https://evil.example` | Ignored. Redirect stays on `WEB_ORIGIN` |
| `next=/dashboard` | Success location ends with that relative path |
| Null phone, `POST /api/v1/live-exam-orders`, take, and retake | `403` `PHONE_REQUIRED` |
| `POST /api/v1/me/phone` with a valid free number | `200`, phone stored, later take is no longer rejected for `PHONE_REQUIRED` |
| Second phone post | `409` `PHONE_ALREADY_SET` |
| Phone already on another live user | `409` `DUPLICATE_PHONE` |
| Password register, login, refresh, forgot-password, reset-password | `TokenIT` and `PasswordIT` stay green |

Acceptance: those tests pass on PostgreSQL, `SchemaIT` sees `google_sub`, a blank Google configuration still boots, and a manual Google login has not been required for this step to be coded. Live sign-in is accepted only after the checklist below and a browser pass.

## 13. Client actions in Google Cloud

The client owns the Google Cloud project. This repository does not change it.

1. Open the project that owns the website's current OAuth client.
2. Confirm the client type is Web application. A desktop, Android, or iOS client is the wrong client for this flow.
3. Add this authorized redirect URI, character for character: `https://api.symamlaw.com/api/v1/auth/google/callback`.
4. Leave the old website callback in place until that website is retired. This API does not use it.
5. On the consent screen, confirm the app name and the domains that Google shows. Add `symamlaw.com` and `api.symamlaw.com` if the screen requires authorized domains.
6. Confirm whether the app is in Testing or In production. In Testing, only listed test users can sign in, and the refresh behavior Google gives those users is limited. A public student body needs In production, plus whatever Google verification the consent screen asks for. This flow requests only `openid`, `email`, and `profile`.
7. Send Shahriar the client id and client secret through the existing secret channel. They are `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` on the API host. They do not go in Git, in the website bundle, or in this document.

Assumption: the current website client is a Web client and Google will accept one more redirect URI on it. If the client cannot be updated, the client creates a new Web client and the same three variables point at that client. The API does not care which Google project it is, as long as the id, secret, and redirect URI are a matching set.

## 14. Risks, assumptions, and unresolved decisions

Assumptions:

- Production API origin is `https://api.symamlaw.com` and the website origin is `https://symamlaw.com`.
- `WEB_ORIGIN` in production is `https://symamlaw.com`.
- The API is reached by the browser at that public host, so the `Secure` cookie is stored.
- One API can encrypt and decrypt the state cookie with `JWT_SECRET`. More than one instance is fine when they share that secret and the database. The cookie is not a server session. The one-time marker is the `state` hash in `google_oauth_states`.
- Step 6 will add the button and `/login/google`. Until then the API can be tested with MockMvc and a direct browser navigation.

Risks:

- A `SameSite=Strict` refresh cookie is set on a response to a navigation that started at Google. Current browser rules store that first-party cookie. The website then sends it on a same-site `fetch`. If a future browser blocks that, the fallback is a new one-time code exchanged by `POST` from the website, still never a long-lived token in the query string. Do not build that fallback unless the browser pass shows the cookie missing.
- Silent link attaches Google to an `ACTIVE` student account that this API has not itself marked `email_verified_at`. That matches the product rule and is the trade-off in section 6. An admin or instructor is refused with `GOOGLE_ROLE_NOT_ALLOWED`.
- `Secure` cookies do not stick on plain `http://localhost`. Manual local sign-in needs a TLS listener or a dev-only cookie flag.

Unresolved, and not decided here:

- The Google Cloud app's Testing versus In production state, and whether Google still wants consent-screen verification. Only the client can see that.
- Whether the existing Web client can take the new redirect URI. Assumed yes until the client confirms.
- A dev-only exception to the `Secure` state cookie. Leave it out unless Shahriar wants local HTTP sign-in.

Decided, and left as they are:

- No SMS OTP. Supplying a phone is not phone verification.
- No second linking step and no password prompt on the Google return.
- No mobile Google client in this step.
