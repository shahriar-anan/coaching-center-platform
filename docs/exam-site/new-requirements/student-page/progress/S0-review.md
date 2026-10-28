# S0 review: API additions and review data

Written 29 October 2026 on `ui/nr01-ui`. S0 covers the API changes the student page needs and the demo data to review them. Written for Shahriar, who tests on the dev server. No web code changed.

## What changed

| Sub-step | Change | Commit |
|---|---|---|
| S0.1 (API1) | `GET /api/v1/public/live-exam-categories/{id}/exams` takes `branch` (default `false`). With `branch=true` it lists the exams of every subcategory, at any depth. Exams in a hidden subcategory stay out. The default list is unchanged | `76c4880` |
| S0.2 (API2) | `GET /api/v1/public/live-exams` takes `popular` (default `false`). `upcoming=true` is still required | `51f43e7` |
| S0.3 (API3) | The `access` object on public module and exam responses gains `expiresAt` (ISO UTC). It is `null` for lifetime access, a free exam, or no live access. An expired grant reads as `NONE`, so `expiresAt` is `null` there too. Module access carries the field as well, because the record is shared | `3698c57` |
| S0.4 | The demo seed gains the NR-01 review cases (see below). Insert-only: a marker package (`MODULE_SALE`) decides whether the block has run, so an older database gets only the new rows. Existing accounts and passwords are unchanged. One account is added (student 4) | `1367e6e` |
| S0.5 | `CategoryBranchExamsIT`: five tests for branch, popular and expiry. Fixed in `ad3dee3` (a compile error) and `9c630e2` (an expectation, see below) | `9c02464`, `ad3dee3`, `9c630e2` |

`DemoContentSeedIT` counts moved with the seed: students 3 to 4, grants 5 to 6, modules 6 to 8, exams 25 to 30, questions 120 to 160, options 480 to 640, announcement notices 3 to 4. `markers.md` is created with no entries, because S0 changes no web code.

## Tests

Command, run through the runner (never `mvn` directly):

    powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent INT -Tests "CategoryBranchExamsIT,DemoContentSeedIT"

Runs in this session:

1. **Compile error.** `examAs` took a `UUID` but got a `String`. No test ran. Fixed in `ad3dee3`.
2. **19 of 20 passed.** `CategoryBranchExamsIT.theExpiringGrantShowsWhenItEndsAndLifetimeAndFreeAccessShowNone` expected `EXAM` for a manual grant. The state is `MANUAL` (`LiveExamAccess`). Fixed in `9c630e2`.
3. **Final run: green, exit code 0.** Surefire reports: `CategoryBranchExamsIT` 5 tests, 0 failures, 0 errors. `DemoContentSeedIT` 15 tests, 0 failures, 0 errors.

The full API suite has not run. The plan asks for it once after S0, so it is still to do.

## Review accounts

Password for every demo account: the shared demo password Shahriar set in `SEED_DEMO_PASSWORD`. Log in with the phone or the email.

| Account | Role | Phone | Email | What they can see |
|---|---|---|---|---|
| ডেমো স্টাফ | SYSTEM_ADMIN | 01711000001 | staff@example.com | Admin screens |
| Demo instructor | INSTRUCTOR | 01711000002 | instructor@example.com | Marking for the written and full packages |
| সুমন আহমেদ (DEMO-STU-001) | Student | 01711000011 | student1@example.com | Full mock package (e10, e11, e12, the daily 11 PM papers e20 to e26, e41) and exam e2 |
| রিনা বেগম (DEMO-STU-002) | Student | 01711000012 | student2@example.com | Written package (e6 to e9, e40) and exam e30 |
| করিম উদ্দিন (DEMO-STU-003) | Student | 01711000013 | student3@example.com | Exam e6 only |
| তানভীর হাসান (DEMO-STU-004) | Student (new in S0) | 01711000014 | student4@example.com | Free exam e61 and exam e62 through a manual grant that expires |

Students who are not listed have no access and see the public catalog only.

## Which seeded exam covers each review case

| Case | Seeded exam or package | Notes |
|---|---|---|
| Discounted exam and package | Exam e60 (400, 25 percent off) and package b7 "BJS Discount Package" (1000, 25 percent off) | Both discounts end 30 November 2026, 23:59:59 Dhaka |
| Free exam | Exam e61 "Free MCQ" | Standalone in the root category. No price, no package. Any signed-in student starts it |
| Popular package and exam | Package b8 "BJS Popular Package" (900, popular) and exam e63 "Popular MCQ" (300, popular) | |
| Exam in two packages | Exam e64 "Two-package MCQ" | In package b7 and package b8. No price of its own, so it is bought through either package |
| Standalone exam in a category | Exams e61 and e62 | Both sit directly in "BJS Exam", the root category. The root's own list shows these two, and `branch=true` shows every exam in the tree |
| Written exam with a question PDF | Exam e62 "Written with question paper" | 300, standalone, 30 days of access after the window ends |
| Expiring grant | Student 4 holds a manual exam grant on e62 | Access ends 30 days after e62's window end |

Branch check: `GET …/live-exam-categories/{BJS Exam}/exams?branch=true` returns every published exam under "BJS Exam", and the Bar Council exams e30 and e31 stay out.

## Things to check and open points

1. **Staging.** The seed runs at startup only when `SEED_DEMO=true` is set. If staging has that flag, its next start after this code is deployed adds the review rows and student 4 to the staging database. Do not deploy this branch to staging until this is decided. Nothing was run against staging.
2. **Clock.** The seed's dates are relative to the run date. The popular upcoming test, and the `upcoming` list in general, compare against the server clock, so they stop matching after the seeded dates pass (the latest review exam is 8 November 2026).
3. **No expired row.** No seed row has an expired grant, so S1.3's expired state can't be seen on the dev server. Add one in S0 if you want it.
4. **`expiresAt` on module access.** The field is on the shared `Access` record, so package access has it too. The web can ignore it until S2.
5. **Expired access reads `expiresAt: null`.** An expired grant reports `NONE` and no end date. Say if you want the past date shown instead.
6. **Popular applies to exams only.** `popular=true` on the upcoming list keeps exams marked popular. It does not include exams in a popular package.
7. **Web type.** `AccessInfo` in `web/src/lib/api/exam/types.ts` does not have `expiresAt` yet. S2.5 adds it. Per the rules, S0 does not change web code.
8. **Bangla copy.** I drafted the Bangla for the new seed rows, and it has not been checked. These are the new terms, for Shahriar to confirm: ছাড়ের প্যাকেজ (discount package), জনপ্রিয় প্যাকেজ (popular package), ছাড়ের এমসিকিউ, বিনামূল্যের এমসিকিউ (free MCQ), প্রশ্নপত্রসহ রিটেন (written with question paper), দুই প্যাকেজের এমসিকিউ (two-package MCQ), and the student name তানভীর হাসান.
