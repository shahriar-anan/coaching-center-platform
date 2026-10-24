# Step 6 — Student website

This is the priority surface. It lives in `web/`, outside the admin shell. A student session must not enter the admin shell. Do not add Flutter screens.

Plan the waves in this order. A later wave can wait on an API step. The sitting waves can be planned against the current API, with the roster as the grant, before SSLCommerz exists.

## Waves

1. **Account and browse.** Email and phone register and login. Google button when step 4 exists. Bangla and English chrome. Public catalog pages when step 2's field list exists.
2. **Dashboard and live list.** Granted modules, the routine (`GET /api/v1/me/exam-routine`), and the inbox when step 5 exists.
3. **MCQ sitting.** Title, description, rules, and questions laid out as a paper. Answers save until the deadline. The score shows when the API returns it. Review waits until the sitting ends.
4. **Written sitting.** Inline PDF viewer. No download control. Overlay the student's name and id. PDF answer upload, replaceable until the deadline.
5. **Results and leaderboard.** Own result. Written marks appear only after publish. Leaderboard uses the existing read.
6. **Checkout.** Per exam and full module, calling step 3. Show failure without granting access.
7. **Inbox page.** List and mark read, calling step 5.

## Bind these

- On the question page, the timer, save, submit, marks labels, title, description, and rules follow the site language. `paperLanguage` selects the typeface for the question body: the stems, the options, or the written PDF. The site language does not replace that body. Shop titles follow the site toggle.
- The viewer overlay is the watermark. Do not ask the API to stamp the PDF.
- Question stems and the question PDF are requested only after Take exam, from the existing sitting calls.
- Reuse the cookie session: `X-Client-Type: web`, memory access token, httpOnly refresh. See `web/src/lib/auth`.
- Noto Sans Bengali is already loaded in `web/src/app/layout.tsx`. The paper layout has to render Bangla correctly.
- Copy goes through the existing locale catalogs (`web/src/i18n`), not inline strings.
- Automated checks are `npm run lint`, `npm run build`, and component tests with mocked `fetch`. Do not boot `api` for those tests. Shahriar does the browser pass.

## Where the code is

- Admin routes: `web/src/app/(admin)/`. Do not put student exam pages there.
- Admin shell gate: `web/src/components/require-auth.tsx` and `web/.cursor/plans/web-catalog.md`. The "no exams" line in that plan is marked outdated for this website. Course admin screens stay as they are.
- API client: `web/src/lib/api/client.ts`.

## Plan must name

- The route paths for each wave.
- Which API step each wave depends on, and which wave can ship on the roster alone.
- The empty, error, and not-yet-live states for Take exam.
- The component tests for the paper, the PDF overlay, a hidden written score before publish, and a checkout failure.

## Out of this step

Instructor and Master Admin operate screens (step 7). Mobile. Push. Course and book payments, including manual bKash/Nagad verification.
