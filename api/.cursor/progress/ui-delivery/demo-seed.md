# Demo seed (WS1 G)

Staging-only demo catalog for the client review: a BJS catalog with MCQ and written exams, a demo instructor, eight demo students, and a posted result set. Code: `DemoContentSeed` and `DemoContentSeedRunner` in `com.coachingcenter.api.exams`. Cleanup: [demo-cleanup.sql](demo-cleanup.sql).

## Environment names

No values are written here. Set them in the server environment, not in code.

| Name | Used for |
|---|---|
| `SEED_DEMO` | `true` turns the seed on at startup. Any other value, or unset, does nothing. |
| `SEED_INSTRUCTOR_PHONE` | Demo instructor's phone (Bangladesh format). |
| `SEED_INSTRUCTOR_EMAIL` | Demo instructor's email. |
| `SEED_INSTRUCTOR_NAME` | Demo instructor's display name. |
| `SEED_INSTRUCTOR_PASSWORD` | Demo instructor's password, 8 to 72 characters. Required. |
| `SEED_STUDENT_EMAIL` | Optional. The existing student with this email gets a roster mark on the 19th BJS module. If it matches no student, the seed logs a warning and skips the roster. |

The eight demo students (`demo-student-N@example.com`, phones `0170000010N`) have no password, so nobody can sign in as them.

## How to run

1. Start the API with the `staging` (or `dev`) profile, `SEED_DEMO=true`, and the `SEED_INSTRUCTOR_*` values set.
2. Watch the log for the startup to finish. The seed runs in one transaction; a failure rolls everything back.
3. Stop `SEED_DEMO` after the run. Leaving it on is harmless (the seed is idempotent) but it does no more than that.

The seed refuses to run on the `prod` profile, and `ProfileStartup` refuses to start prod with `SEED_DEMO=true`. It also refuses any profile that is not `staging` or `dev`.

## What it creates

- Categories: বিজেএস এক্সাম, বার কাউন্সিল এক্সাম (empty, coming soon), একাডেমিক.
- Modules: ১৯তম বিজেএস (2500.00, published), ২০তম বিজেএস (2000.00, published), সাধারণ আইন অনুশীলন (no price).
- Exams in ১৯তম বিজেএস: সিআরপিসি (MCQ, tomorrow 12:00 Dhaka), পেনাল কোড (MCQ, yesterday 12:00, eight posted scores with a shared rank), সাক্ষ্য আইন (MCQ, 30 minutes after the run), সিপিসি (written, undated, question PDF), ইভিডেন্স (written, yesterday 14:00, one published mark and one draft awaiting publish).
- Exams in ২০তম বিজেএস: two undated MCQ papers; paper A is priced 300.00, paper B has no price.
- The academic module has one undated MCQ quiz.
- Instructor assigned to ১৯তম বিজেএস.

## Relative dates

Dates are fixed at the moment of the run, in Asia/Dhaka. Re-running the seed does not move them, because existing rows are skipped. To refresh the dates: run the cleanup, then run the seed again.

## How to undo

Run the cleanup script once, in one transaction, against staging:

```
psql -1 -v ON_ERROR_STOP=1 -f api/.cursor/progress/ui-delivery/demo-cleanup.sql
```

It deletes by the demo id prefixes, so it removes demo rows and nothing else. It also removes orders and grants on demo modules or exams, and notices and mail queued for demo accounts. Stored PDFs stay on disk under `FILE_STORAGE_DIR`; the script's header lists how to find and remove them.

## Tests

`DemoContentSeedIT` covers: refusal on prod and on profiles other than staging or dev; two runs with no duplicates and the roster link; the cleanup removing demo rows and keeping a real row. `ProfileStartupTest` covers the `SEED_DEMO` refusal on prod.
