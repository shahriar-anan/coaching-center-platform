# WS7 — Integration, deploy, walkthrough

Shahriar's main session, on `catalog-acquisition`. Starts when WS0 is ready, again when each later workstream reports done, and ends with the deploy. Read [README.md](README.md).

## Merge order

1. **WS0** first, alone. Run `npm run lint`, `npm test`, `npm run build`. Tell WS2–WS6 to cut their branches now.
2. **WS1** next: full API suite from clean, alone (`mvn -q clean test`; only `BookOrderIT` and `CoursePurchaseIT` may fail).
3. Then **WS2, WS3, WS4, WS5, WS6** in that order, each as soon as it reports done. After each merge run lint, tests and build. WS4 before WS5 and WS6, because they reuse its PDF viewer.

Use `git merge --no-ff ui/wsN-...`. A conflict outside a workstream's **Owns** list means a rule was broken: keep the owner's version and send the other change back to its workstream. Expected shared touches are only `keys.ts` (separate objects) and `messages/index.ts` (one import line per namespace).

After all merges: delete any `_preview` pages, grep for leftover inline strings and `TODO`, and check that the old `(admin)` nav shows no course, book or course-payment item.

## Pre-deploy checks

- API: full suite from clean, then `mvn -DskipTests clean package`. Record the jar SHA-256.
- Web: lint 0 errors, tests green, `NEXT_PUBLIC_API_BASE_URL=https://srv2089893.hstgr.cloud npm run build`, swap `sharp` to Linux ([../vps-staging-deploy.md](../vps-staging-deploy.md) slice 0 step 5), copy `public/` (including `pdf.worker.min.mjs`) and `.next/static` into the standalone folder, pack it, and record the SHA-256.
- Bundle check: from the build output, every student route's first-load JavaScript is under the budget in README. Note any over it.

## Deploy (approval for each server write, as in the staging deploy plan)

1. Back up: `pg_dump` to `/var/backups/coaching/coaching-pre-ui-<date>.dump`, copy to `F:\exam-vps-backups\`, compare SHA-256. Keep the current jar as `api.jar.pre-ui` and the web folder as `/opt/coaching-web.pre-ui`.
2. Shahriar adds to `/etc/coaching-api.env`: `SEED_DEMO=true`, `SEED_INSTRUCTOR_PHONE`, `SEED_INSTRUCTOR_EMAIL` (an inbox he owns), `SEED_INSTRUCTOR_NAME`, `SEED_INSTRUCTOR_PASSWORD`, and checks the `SEED_STUDENT_*` and `SEED_ADMIN_*` values are present. He deletes the unused `MAIL_TEST_ENABLED` line.
3. Install the jar, restart, watch the log until started: Flyway at the expected version, demo seed lines, no error. Then `SEED_DEMO=false` and restart again.
4. Install the website: unpack to a new folder, swap folders, restart `coaching-web`, check `127.0.0.1:3000` on the box, then the public URL.
5. Smoke from the PC: `/`, `/mcq`, `/written`, a module, an exam, `/login`, `/staff` (redirects to login), public API 200, `/api/v1/me` 401.

Rollback: the pre-ui jar and web folder, restart both; restore the dump only if the database is wrong.

## Walkthrough for Shahriar (browser pass before showing the client)

Do it on a phone (or 375 px) and a desktop, in Bangla, once in English, once in dark mode.

**Student** (seed student; then a newly registered student):

1. Home shows the hero, two doors and, signed in, the countdown, "এখন চলছে" or "আসন্ন", and "আমার মডিউল".
2. MCQ door: বিজেএস → ১৯তম বিজেএস shows only MCQ exams; Written door shows only written ones; বার কাউন্সিল shows "শীঘ্রই আসছে".
3. As the new student, buy one exam with the sandbox (card or bKash) and see Paid; the receipt arrives and the client's inbox gets "New purchase" (tell the client first).
4. Sit সাক্ষ্য আইন while its window is open (or set its start a few minutes ahead from the staff editor): answer, reload, see answers kept, submit, see the score, wait for the deadline, see the review.
5. Written সিপিসি after setting a start time: view the PDF with the watermark, upload a PDF, replace it, try an image.
6. পেনাল কোড result and leaderboard show the demo scores and a shared rank.
7. Notices, account (verify email, change phone, change password), payment history, the policy pages.

**Staff:**

1. Client's Master Admin: home cards, create a category, module and MCQ exam with questions, set a price, publish; roster a student; assign the instructor; payments list with the method and access columns.
2. Instructor: only ১৯তম বিজেএস, submissions for ইভিডেন্স, open one, save a draft mark with a marked PDF; no publish button.
3. Master Admin: publish that result; the student sees it and gets a notice.
4. System Admin: catalog without price, delete, roster or instructor; payments read-only; submissions read-only; no audit log.

Record each step as pass or fail in `docs/exam-site/reports/ui-delivery.md`, with the jar and web SHA-256 values and anything left for later.

## Docs to update at the end

- `docs/exam-site/ui/feature-scope.md`: a line at the top pointing to this plan, and that its "API later" section and the stale lines are superseded.
- `docs/exam-site/todays-goals.md` section 4 (W1–W8) with the status.
- `docs/exam-site/current-api.md` (WS1 already appends its section).
- Demo data: note in `decisions.md` that staging has demo content and how to remove it before real use.

## Report

(Fill in as the merges and the deploy happen.)
