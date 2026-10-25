# WS7 — Integration, deploy, walkthrough

Shahriar's main session, on `catalog-acquisition`: the **integrator**. It builds no pages. It answers agents' questions, keeps the contracts, merges, runs checks, and deploys. Read [README.md](README.md).

## Merge order

1. **WS0** as soon as its contracts are in code and its checks pass (aim for under 1.5 hours). Then tell agents C, D and E to run `git merge catalog-acquisition` in their branches, and tell agent A to cut `ui/ws2-student-entrance`.
2. **WS1** next: the touched `*IT` classes and the full API suite from clean, alone (`mvn -q clean test`; only `BookOrderIT` and `CoursePurchaseIT` may fail). Then tell C, D, E and A to merge again, and agent B to cut `ui/ws6b-staff-ops`.
3. **WS4** (the `PdfViewer` others reuse), then **WS3**, **WS2**, **WS5** (then tell agent E to cut `ui/ws6a-staff-people`), **WS6a**, **WS6b**, each as soon as it reports done.
4. After every merge: `npm run lint`, `npm test`, `npm run build` (under the heavy lock), and tell the remaining agents to merge `catalog-acquisition`.

Use `git merge --no-ff <branch>`. There should be no shared-file conflicts: query keys live in each workstream's own hooks file, and WS0 registers every message namespace up front. A conflict outside the branch's **Owns** list means a rule was broken: keep the owner's version and send the other change back.

Set a **freeze** time (for example two hours before the client sees it). After freeze, agents only fix bugs in what they already built.

After all merges: delete any `_preview` pages, grep for leftover inline strings and `TODO`, and check that the old `(admin)` nav shows no course, book or course-payment item. Grep the web source for `hstgr.cloud`, `http://` and `https://` outside `lib/config.ts`, `.env*` and tests: there should be none.

**Bangla pass.** Read every Bangla message file against [bangla-copy.md](bangla-copy.md): glossary terms, the client's words, natural word order, Bengali digits, day periods, no word-for-word translations. Fix in the message files only. Note any new term in the guide.

## Pre-deploy checks

- API: full suite from clean, then `mvn -DskipTests clean package`. Record the jar SHA-256.
- Web: lint 0 errors, tests green, then build with `NEXT_PUBLIC_API_BASE_URL=` (empty, same origin), `NEXT_PUBLIC_SITE_URL=https://srv2089893.hstgr.cloud`, `NEXT_PUBLIC_STAFF_URL=https://srv2089893.hstgr.cloud`, `NEXT_PUBLIC_GOOGLE_ENABLED=false` and `npm run build` (this replaces the earlier build with an absolute API URL), swap `sharp` to Linux ([../vps-staging-deploy.md](../vps-staging-deploy.md) slice 0 step 5), copy `public/` (including `pdf.worker.min.mjs`) and `.next/static` into the standalone folder, pack it, and record the SHA-256.
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

## Moving to separate student and admin URLs (later, at launch)

No code change is needed; only configuration ([ws0-foundation.md](ws0-foundation.md) section 9):

1. DNS: point both names (for example `exam.example.com` and `admin.exam.example.com`) at the server.
2. Nginx: one server block per name, both forwarding `/` to `127.0.0.1:3000` and `/api/` to `127.0.0.1:8080`; one Let's Encrypt certificate for both names.
3. Web: rebuild with `NEXT_PUBLIC_SITE_URL` and `NEXT_PUBLIC_STAFF_URL` set to the two origins (or set the runtime `SITE_URL` and `STAFF_URL`, if WS0 found that `proxy.ts` reads them at runtime), redeploy.
4. API env: `WEB_ORIGIN=<student origin>,<admin origin>` (student first, because payment returns go to the first origin), `SSLCZ_CALLBACK_BASE_URL=<student origin>`, `GOOGLE_REDIRECT_URI` on the student origin; restart the API.
5. Check: the admin host's `/` opens the admin panel; `/staff` on the student host redirects to the admin host; a payment returns to the student site; staff and student sessions stay separate.

## Docs to update at the end

- `docs/exam-site/ui/feature-scope.md`: a line at the top pointing to this plan, and that its "API later" section and the stale lines are superseded.
- `docs/exam-site/todays-goals.md` section 4 (W1–W8) with the status.
- `docs/exam-site/current-api.md` (WS1 already appends its section).
- Demo data: note in `decisions.md` that staging has demo content and how to remove it before real use.

## Report

(Fill in as the merges and the deploy happen.)
