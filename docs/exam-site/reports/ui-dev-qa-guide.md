# UI dev QA guide: a local run for manual testing

For Shahriar's manual QA of the exam site on a Windows PC. It runs the API and the Next.js site on this machine, against a local database that is kept apart from the test database. Written for WS9 (25 October 2026). Every command here was run from the worktree before this file was merged; the boot check is at the end.

Names only appear for secrets. Put real values in `api/.env` and `web/.env.local`, both gitignored. Never commit them.

## 1. Database

Use `coaching_center_qa`. Do not use `coaching_center_test`: the integration tests create and remove data there.

Check first, then create only if it is missing. On this PC the database already exists, so the step is a check only. Replace `<pg-user>` with the local PostgreSQL role from `api/.env` (`DB_USERNAME`). The password comes from the prompt, not from the command line.

```
"C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -U <pg-user> -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='coaching_center_qa'"
```

If the output is empty, create it:

```
"C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -U <pg-user> -d postgres -c "CREATE DATABASE coaching_center_qa"
```

pgAdmin: right-click Databases, Create, Database, name `coaching_center_qa`.

Flyway runs the migrations when the API starts. Do not run SQL by hand.

## 2. API

Set these in `api/.env`. The `dev` profile loads that file on its own.

| Name | Example (not a secret) | Why |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/coaching_center_qa` | The QA database from step 1. |
| `DB_USERNAME` | the local role | |
| `DB_PASSWORD` | your local password | |
| `JWT_SECRET` | any 32 or more characters, local only | Signs tokens. |
| `WEB_ORIGIN` | `http://localhost:3000` | CORS for the web site. For the split host in step 6, add the two `*.localhost` origins, comma-separated. |
| `FILE_STORAGE_DIR` | `var/files` (default) | Where uploaded PDFs are stored. |
| `DEV_EXPOSE_EMAIL_CODES` | `true` | Writes verification and reset codes into the API log (step 4). |
| `MASTER_ADMIN_PHONE` | `+8801700000001` | The Master Admin, created on the first start only. |
| `MASTER_ADMIN_EMAIL` | `master-qa@example.com` | |
| `MASTER_ADMIN_NAME` | `Master Admin` | |
| `MASTER_ADMIN_PASSWORD` | a local password, not reused anywhere | Read only when the database has no Master Admin. |
| `SEED_ENABLED` | `false` on a database that already has these accounts; `true` only on a fresh database | Creates the `SEED_ADMIN_*` and `SEED_STUDENT_*` accounts below. If the phone in `SEED_ADMIN_PHONE` or `SEED_STUDENT_PHONE` already belongs to another account, the API stops at start-up with `<NAME>_PHONE is already in use.` That is deliberate: the seed does not overwrite accounts. |
| `SEED_ADMIN_PHONE`, `SEED_ADMIN_EMAIL`, `SEED_ADMIN_NAME`, `SEED_ADMIN_PASSWORD` | local values | A System Admin account. |
| `SEED_STUDENT_PHONE`, `SEED_STUDENT_EMAIL`, `SEED_STUDENT_NAME`, `SEED_STUDENT_PASSWORD` | local values | A student account. |
| `SEED_DEMO` | `true` | Loads the demo catalog (step 4). Idempotent. |
| `SEED_INSTRUCTOR_PHONE`, `SEED_INSTRUCTOR_EMAIL`, `SEED_INSTRUCTOR_NAME`, `SEED_INSTRUCTOR_PASSWORD` | local values | The demo instructor. The password is required when `SEED_DEMO=true`. |
| `SEED_STUDENT_EMAIL` | the `SEED_STUDENT_EMAIL` above | Puts that student on the demo roster of the 19th BJS module. |
| `PAYMENT_BKASH_NUMBER`, `PAYMENT_NAGAD_NUMBER` | any local number | Shown on the manual payment screens. |
| `SSLCZ_STORE_ID`, `SSLCZ_STORE_PASSWORD`, `SSLCZ_BASE_URL`, `SSLCZ_CALLBACK_BASE_URL` | leave empty | No SSLCommerz checkout locally (see step 8). |

Start the API from `api/`. Use `clean` (the Maven rule for this repo). Keep the terminal open.

```
.\mvnw.cmd clean spring-boot:run "-Dspring-boot.run.profiles=dev"
```

It listens on `http://localhost:8080`. Flyway prints its migration lines, then Tomcat reports the port.

The seed logs nothing on success. Its failures are logged as errors and roll the run back. Check the result with a request instead:

```
curl -s http://localhost:8080/api/v1/public/live-exam-categories
```

After `SEED_DEMO=true`, the response lists the demo categories (BJS exam, Bar Council exam, Academic).

## 3. Web

Create `web/.env.local` with these lines:

```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_SITE_URL=http://localhost:3000
NEXT_PUBLIC_STAFF_URL=http://localhost:3000
NEXT_PUBLIC_GOOGLE_ENABLED=false
```

From `web/`, run:

```
npm run dev
```

Open these in the browser:

| URL | What it is |
|---|---|
| `http://localhost:3000/` | Student home, the two doors (MCQ and written). |
| `http://localhost:3000/mcq` | MCQ door. |
| `http://localhost:3000/written` | Written door. |
| `http://localhost:3000/login` | Sign in (phone or email, password). |
| `http://localhost:3000/staff` | Staff home (admin, instructor, and the Master Admin). |

## 4. Accounts and codes

| Who | Account | Signs in at |
|---|---|---|
| Master Admin | `MASTER_ADMIN_PHONE` or `MASTER_ADMIN_EMAIL`, with `MASTER_ADMIN_PASSWORD` | `/login`, then `/staff` |
| System Admin | `SEED_ADMIN_*` | `/login`, then `/staff` (read and manage, no price, no publish) |
| Student | `SEED_STUDENT_*` | `/login`, then `/` |
| Demo instructor | `SEED_INSTRUCTOR_*` (assigned to the 19th BJS module) | `/login`, then `/staff` |
| Demo students | `demo-student-1@example.com` to `demo-student-8@example.com`, phones `01700000101` to `01700000108` | They have no password, so nobody can sign in as them. |

Verification and reset codes do not go by email locally. With `DEV_EXPOSE_EMAIL_CODES=true`, the API log prints the code after the message line:

- `Email verification code <code>`
- `Password reset code <code>`

Look for the line in the terminal that runs the API.

Sign-in uses a password only. There is no SMS code.

## 5. Make an exam live now

Use the staff exam editor to test sitting, the deadline, the review, and the leaderboard.

1. Sign in as the Master Admin. Open `/staff/modules`, then the 19th BJS module, then the MCQ exam (for example সাক্ষ্য আইন).
2. In the schedule section, set the start to about 3 minutes from now, in Asia/Dhaka time. Set the duration to 5 minutes. Save, then publish. A start inside five minutes of another published exam in the same module is refused and names that exam.
3. Sign in as the student (`SEED_STUDENT_*`) in a second browser or a private window. Open the module from the home page. The exam shows Take exam once the window opens.
4. Take the exam, answer, and leave it. The sitting posts at the deadline. The deadline job runs about once a minute, so give it up to a minute after the deadline.
5. The leaderboard opens five minutes after the window closes (the duration plus five minutes).

For a written exam: the student uploads an answer PDF. The demo instructor marks it under `/staff/submissions`, saves a draft, and the Master Admin publishes it.

## 6. Split host (site and admin on different names)

Production serves the student site and the staff panel on two hosts. Locally you can do the same with `*.localhost`, which browsers resolve to 127.0.0.1 without a hosts-file edit.

Change `web/.env.local`:

```
NEXT_PUBLIC_SITE_URL=http://site.localhost:3000
NEXT_PUBLIC_STAFF_URL=http://admin.localhost:3000
```

Change `api/.env`:

```
WEB_ORIGIN=http://site.localhost:3000,http://admin.localhost:3000,http://localhost:3000
```

Restart both. Then open `http://site.localhost:3000/` for the student site and `http://admin.localhost:3000/staff` for the staff panel. Each route is served on its own host only.

## 7. Typography and visual checklist

Tick each line page by page. The values come from the type scale in `plans/ui-delivery/ws9-gaps-and-polish.md`, Part 3 P1.

Type scale, student: display 64 to 110 px, title 22 px (700), section 20 px (700), card 17 px (700), body 16 px (1.6 line height), meta 14 px muted, label 15 px (600), pill 12 px (700). Student content is never below 16 px.

Type scale, staff: title 22 px (600), section 18 px (600), card 16 px (600), table body 14 px, form body 16 px, meta 13 px muted, label 14 px (500), pill 12 px (600).

Spacing: student gutters 16 px, card padding 18 px, gap between cards 12 px, section gap 24 px. Staff page padding 24 px, card padding 16 to 20 px, table cell 10 by 12 px.

Checklist, for each page:

- [ ] Type matches the scale above, and no `text-[..]` size is visible in the layout.
- [ ] Spacing matches the rhythm above.
- [ ] Loading, empty, error, and success states all render.
- [ ] Works at 320, 375, 768, 1024, and 1440 px, with no horizontal page scroll.
- [ ] Both languages (বাংলা and English) and both themes (light and dark).
- [ ] Keyboard: every control is reachable and the focus ring shows.

Pages to tick: student home, MCQ and written doors, module page (the three chips stay on one row at 320 px), exam page (sticky action bar on phones), sitting (the bar reads at 320 px; jump grid uses 40 px cells), result and leaderboard, login, register, account. Staff: home, categories, modules, module exams (readiness columns), exam editor (numbered sections), submissions (two-pane browser), marking screen, results report, students, payments, participation, audit.

## 8. Known limits on a local run

- **No SSLCommerz checkout.** The `SSLCZ_*` values are empty, so buying an exam or a module stops at the payment step. Purchase and grant flows need the sandbox store and a public HTTPS callback, which only the staging server has.
- **Mail is logged, not sent.** Nothing reaches an inbox. Use the codes in the API log (step 4).
- **Google sign-in is off** (`NEXT_PUBLIC_GOOGLE_ENABLED=false`).
- **Files stay on this PC** under `FILE_STORAGE_DIR`. They are not served from a public URL.
- **Times are Asia/Dhaka.** The server clock controls the live window, so set the start times relative to the current time.
- **No browser pass from the agent.** The API and web boot checks below only confirm that the pages and endpoints answer. Visual and typography review is Shahriar's.

## Boot check (run for this guide)

Run on 25 October 2026 from the worktree `F:\cc-wt\w9-api`, against `coaching_center_qa`, with the lock held and released afterwards.

- API: `./mvnw clean spring-boot:run -Dspring-boot.run.profiles=dev` with `SEED_ENABLED=false` and `SEED_DEMO=true`. Flyway validated 38 migrations. Tomcat started on port 8080 and `Started ApiApplication` was logged. `GET /api/v1/public/live-exam-categories` returned `200` with the demo categories.
- A first attempt with `SEED_ENABLED=true` stopped at start-up with `SEED_STUDENT_PHONE is already in use.` The QA database already held that phone. This is the case described in step 2.
- Web: `npm run dev` in `web/` was ready in about 1.1 s on port 3000. `/` `200`, `/mcq` `200`, `/staff` `200`.
- Both servers were stopped after the check.

Not checked here: the pages' content and layout. That is the visual pass in step 7.
