# Implementation prompt: student page overhaul

Two parts. Part A is the S0 follow-up, to run first. Part B (S1 onward) starts only after Shahriar's go on S0. Paste the block for the part you're starting into a new session.

---

## Part A: S0 follow-up (paste from here)

You are finishing S0 of the student page overhaul for the coaching-center exam site. Work only in `F:\cc-wt\nr01-ui`, branch `ui/nr01-ui`. First run `git -C /f/cc-wt/nr01-ui rev-parse --show-toplevel` and `git branch --show-current`. They must print `F:/cc-wt/nr01-ui` and `ui/nr01-ui`. If not, stop and report. Do not run Maven from any other checkout.

**Read first, in order:**
1. `docs/exam-site/new-requirements/student-page/plan.md`: the source of truth (section 4, S0).
2. `docs/exam-site/new-requirements/student-page/progress/S0-review.md`: what S0 did and the review accounts.
3. `docs/exam-site/new-requirements/nr-01/contracts.md`, section 7 (compatibility rules). Do not break them.

**Decisions already made for this follow-up (do not re-open them):**

- **F1. Expired seed row.** Add one expired grant to the seed, so the "expired" state can be seen on the dev server. Give student 4 (তানভীর হাসান) a second manual exam grant on a seeded exam whose access has already ended, such as a past-window exam, so the grant is expired. Insert-only, behind its own marker, and idempotent. Keep the existing accounts.
- **F2. Popular packages on the home page need no API change.** `GET /api/v1/public/live-exam-modules?popular=true` already exists. Do not add an endpoint. Verify the seeded popular package (b8) appears with that filter, and add an assertion to `CategoryBranchExamsIT` or `DemoContentSeedIT`.
- **F3. Expired access shows its past end date.** Today an expired grant reads as `NONE` with `expiresAt: null`. Change it so that `state` stays `NONE` (so an expired holder can still buy again, as decided in C2), but `expiresAt` carries the end date of the expired grant. Only when no live access exists. Where the object is built, look at `StudentOwnAccessService` and `LiveExamAccess` (`candidates`, `best`). The change is additive: no field is removed, and existing responses keep their meaning. Update `CategoryBranchExamsIT` to assert the new value.
- **F4. Bangla for the new seed rows.** Keep the drafted terms already in the seed. Do not block on them. List each term in `S0-review.md` under "Bangla to confirm", so Shahriar can correct them later.

**Tests for this follow-up:**
- `CategoryBranchExamsIT`, `DemoContentSeedIT`, and the access classes `StudentAccessIT` and `AccessExpiryIT` (the access object changed).
- One run, from the worktree:
  `Set-Location F:\cc-wt\nr01-ui; powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent INT -Tests "CategoryBranchExamsIT,DemoContentSeedIT,StudentAccessIT,AccessExpiryIT"`
  Run it in the background and wait for the completion notice. Do not poll. Never run `mvn` directly. Never print or commit a password.
- Do not run `-Full` in this follow-up.

**Full-suite check for S0.** The full suite after S0 is being run by the orchestrator from the UI worktree. If the orchestrator reports a failure, fix only what it names. Do not start on other failures.

**Rules:**
- Commit each change separately, ending with `Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>`. Never use `--no-verify`.
- Do not touch web code, the staging database or the staging server. Do not deploy.
- Do not start S1.
- If a name or pattern is unclear, read the existing code first. If still unclear, stop and ask.
- Search before reading. Don't read whole files over 500 lines.

**When done:** update `S0-review.md` with a "Follow-up" section (what changed, commit hashes, test results with counts, the new review account and case for the expired row, and the Bangla list). Do not push. Commit only; the orchestrator merges. Stop and report in five lines.

---

## Part B: S1 onward (paste later, after Shahriar's go on S0)

Not yet. Part B will be written after S0 is accepted. Standing rule for every part: workers commit only, never push. The orchestrator merges. It will follow `plan.md` section 4 (S1 navigation, S2 browsing and purchase, S3 popular and favourites), with one worker per step and a review note and push at the end of each step.
