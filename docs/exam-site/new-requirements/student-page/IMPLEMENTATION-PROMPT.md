# Implementation prompt: student page overhaul (start with S0)

Paste everything below the line into a new session.

---

You are implementing the student page overhaul for the coaching-center exam site. Work only in the worktree `F:\cc-wt\nr01-ui`, branch `ui/nr01-ui`. Start by running `git -C /f/cc-wt/nr01-ui rev-parse --show-toplevel` and `git branch --show-current`. They must print `F:/cc-wt/nr01-ui` and `ui/nr01-ui`. If not, stop and report.

## Read first, in this order

1. `docs/exam-site/new-requirements/student-page/plan.md`: the decisions, the steps, the files, the tests, and the review rules. This is the source of truth.
2. `docs/exam-site/new-requirements/student-page/steps.md`: the earlier draft and its open questions (answered in plan.md).
3. `docs/exam-site/new-requirements/nr-01-ui/conversation-decisions.md`: the decisions for the whole UI, including wording, the watermark and the rules for working with the API.
4. `docs/exam-site/new-requirements/nr-01-ui/README.md`: the working rules and the worker brief.
5. `docs/exam-site/new-requirements/nr-01/contracts.md`, section 7 (compatibility rules). Do not break them.

## What to do now: S0 only

Implement **S0** from `plan.md` section 4, and nothing else:

- **S0.1 (API1):** `GET /api/v1/public/live-exam-categories/{id}/exams` gains a `branch` boolean parameter (default false). With `branch=true`, the list includes exams in every subcategory under that category. The default stays exactly as it is today. Use `LiveExamPublicController` and `LiveExamPublicService.categoryExams`. Add the query needed to find the descendant category ids. The category tree is stored with `parent_id` in `live_exam_categories`.
- **S0.2 (API2):** `GET /api/v1/public/live-exams` gains a `popular` boolean parameter (default false). Filter by `popular = true`. The existing `upcoming` behaviour must not change.
- **S0.3 (API3):** the exam's `access` object (returned by the public and student exam reads, built in `StudentOwnAccessService`) gains `expiresAt` (ISO UTC, or null for lifetime, free, or undated). Take the value from `LiveExamAccess.state(...)`, which already computes `endsAt`. Check the field name against `AccessInfo` in `web/src/lib/api/exam/types.ts` and keep the JSON shape additive.
- **S0.4:** extend `DemoContentSeed.java` with the NR-01 review cases. It must be insert-only and idempotent, like the existing seed. Add: a discounted exam and a discounted package; a free exam; a popular exam and a popular package; one exam in two packages; a standalone exam in a category (no package); a student with an expiring grant; a written exam with a question PDF. Keep the existing accounts and passwords as they are. Look at the existing seed's style first and match it.
- **S0.5:** run the API IT classes once. Put the new IT class in `api/src/test/java/com/coachingcenter/api/exams/`, named `CategoryBranchExamsIT`, and include the popular and expiry cases in it. Also run `DemoContentSeedIT`.

## Rules

- **Maven only through the runner script.** From PowerShell, in the worktree: `Set-Location F:\cc-wt\nr01-ui; powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent INT -Tests "CategoryBranchExamsIT,DemoContentSeedIT"`. It waits for the shared test lock, which is expected. Run it once with a long timeout or in the background, and wait for its completion notice. Do not poll. Never run `mvn` directly. Never print or commit a password. Do not use `-Full` in this step.
- **Web checks.** Only if you change web code in S0 (you should not need to). If you do, take the heavy lock by writing `UI` into `F:\coaching-center-platform\.heavy.lock`, run the check, and always delete the lock afterwards.
- **No change to the main checkout.** Work only in `F:\cc-wt\nr01-ui`. Never commit to `demo/client-review` or `nr01/integration`.
- **Do not push to `demo/client-review`.** You may push `ui/nr01-ui` after the step is green.
- **Commits:** one commit per sub-step, with a message in the usual style, ending with `Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>`. Never use `--no-verify`.
- **Scope:** do not change web code, the compatibility rules, the staging database or the staging server. Do not start S1.
- **Do not guess.** If a field name, a query or a seed pattern is unclear, read the existing code first (grep, then a 50 to 100 line window). If it's still unclear, stop and ask.
- **Token care.** Search before reading. Do not read whole files over 500 lines. Keep status updates to one line.

## When S0 is done

1. Write `docs/exam-site/new-requirements/student-page/progress/S0-review.md` with:
   - what changed, sub-step by sub-step, with commit hashes;
   - the test commands and their real results, with counts;
   - the review accounts: which login, which role, which exams each one can see, and which seeded exam each case uses (discounted, free, popular, two packages, standalone, expiring grant, written with PDF);
   - anything you were unsure about.
2. Add any new markers to `docs/exam-site/new-requirements/student-page/markers.md` (create it if missing).
3. Push `ui/nr01-ui`.
4. Stop and report in five lines: sub-steps done, commit hashes, test results, the review file path, anything blocking.

Do not start S1 until Shahriar has reviewed S0 and sent his go.
