# NR-01 agent prompts (in start order)

Start order: **F alone → (F merged) → C → D → B → A**, a few minutes apart. Each prompt is complete; paste it as is into a Claude Code session opened in that agent's worktree, or pass it to the Agent tool.

Before each prompt, the orchestrator has done the setup line above it.

---

## 1. Agent F (stage 1, alone)

Setup (orchestrator):
- commit this plan on `nr01/integration`, cut from `demo/client-review`;
- `git branch nr01/f-foundation nr01/integration`;
- `git worktree add F:\cc-wt\nr01-f nr01/f-foundation`.

```
You are agent F on NR-01, the exam segment API change for the coaching-center exam site.
Worktree: F:\cc-wt\nr01-f, branch nr01/f-foundation. Run `git rev-parse --show-toplevel` before your first edit.
It must print your worktree. Write only inside it. The one exception is the heavy-lock file, which the test
script handles for you.

Read these first, in order, and follow them exactly:
1. docs/exam-site/new-requirements/NR-01-exam-segment.md (requirement and all agreed decisions)
2. docs/exam-site/new-requirements/rules.md
3. docs/exam-site/new-requirements/nr-01/README.md (team, ownership, running Maven, how agents work)
4. docs/exam-site/new-requirements/nr-01/contracts.md (you build sections 2 to 5 exactly)
5. docs/exam-site/new-requirements/nr-01/f-foundation.md (your slices F1 to F5)
6. docs/exam-site/current-api.md, for the live-exam parts

You are alone in this stage. You may edit any API file the slices need. Do not touch web/.
Behaviour must not change: every existing test stays green.

Running Maven: only through
  powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent F -Tests "ClassA,ClassB"
The script takes the machine-wide heavy lock, uses your own database (cc_test_nr01_f), and loads credentials
without printing them. Never run mvn directly. Never print or commit a password. This PC has no Docker.
If Flyway reports a checksum mismatch after you edit a migration, add -ResetDb. Run the full suite (-Full)
once, at the end, in the background.

Work one slice at a time. For each slice: write the code and tests, run its classes through the script,
write docs/exam-site/new-requirements/nr-01/progress/f-<slice>.md (what changed, the real test result, any
deviation), then commit. Use your session's commit attribution line. Never use --no-verify. Do not push,
deploy, or touch staging or client-vps.

Read time from the injected Clock. Tests use MutableClock and never sleep. Use real PostgreSQL, never H2.

Your last slice (F5) writes progress/f-done.md. It lists every place your code differs from contracts.md;
every remaining read of LiveExam.getModuleId() and of the legacy question_* columns (file and line); the
existing *IT classes with their owners; and the full-suite result with real counts.
Then report: slices done, test counts (paste any failure), deviations, questions, anything skipped.
```

---

## 2. Agent C (start first in stage 2)

Setup (orchestrator), after F is merged and contracts.md is corrected from `f-done.md`:
- `git branch nr01/c-commerce nr01/integration`, and likewise `nr01/d-sitting`, `nr01/b-packages`, `nr01/a-authoring`;
- `git worktree add F:\cc-wt\nr01-c nr01/c-commerce`, and likewise `nr01-d`, `nr01-b`, `nr01-a`.

```
You are agent C (commerce and access) on NR-01, the exam segment API change for the coaching-center exam site.
Worktree: F:\cc-wt\nr01-c, branch nr01/c-commerce. Run `git rev-parse --show-toplevel` before your first edit.
It must print your worktree. Write only inside it.
Agents A, B and D work in parallel in their own worktrees. You never edit their files. You talk only to the
orchestrator, through your report or by stopping with a question.

Read these first, in order, and follow them exactly:
1. docs/exam-site/new-requirements/NR-01-exam-segment.md
2. docs/exam-site/new-requirements/rules.md
3. docs/exam-site/new-requirements/nr-01/README.md (ownership table, running Maven, how agents work)
4. docs/exam-site/new-requirements/nr-01/contracts.md (frozen; never change it yourself)
5. docs/exam-site/new-requirements/nr-01/c-commerce.md (your slices C1 to C3)
6. docs/exam-site/new-requirements/nr-01/progress/f-done.md
7. docs/exam-site/current-api.md, for orders, grants and access

Change only the files the README ownership table gives to C. The one shared exception is your own block in
AuditLogIT.API_OPERATIONS, marked "C". For any other file, stop and ask the orchestrator for the exact change;
keep working on slices that do not need it. No migrations: if you need one, ask.

Running Maven: only through
  powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent C -Tests "ClassA,ClassB"
It waits for the machine-wide heavy lock (other agents share this PC), and uses your database
cc_test_nr01_c. Never run mvn directly, and never print or commit a password. On a Flyway checksum error,
add -ResetDb. Keep runs to your slice's classes. Run -Full only once, at the end, in the background.

Each slice: merge nr01/integration first; write the code and tests; run them through the script; write
docs/exam-site/new-requirements/nr-01/progress/c-<slice>.md; commit with your session's attribution line.
Never use --no-verify. Do not push, deploy, or touch web/, staging or client-vps.
Read time from the injected Clock. Tests use MutableClock and never sleep. Real PostgreSQL, never H2.
Record API changes in nr-01/api-changes/c.md and UI consequences in nr-01/web-impact/c.md.

You merge first in this stage, because everyone's tests use access. Finish C1 before anything else. When
C1 is green, stop and report it, so the orchestrator can merge it early. Then continue with C2 and C3.

When done: merge nr01/integration, run -Full, and report: slices done; test command and real counts (paste
any failure); files changed outside your ownership (should be none); questions; anything skipped.
```

---

## 3. Agent D

```
You are agent D (sitting, scoring, results and marking) on NR-01, the exam segment API change for the
coaching-center exam site.
Worktree: F:\cc-wt\nr01-d, branch nr01/d-sitting. Run `git rev-parse --show-toplevel` before your first edit.
It must print your worktree. Write only inside it.
Agents A, B and C work in parallel in their own worktrees. You never edit their files. You talk only to the
orchestrator, through your report or by stopping with a question.

Read these first, in order, and follow them exactly:
1. docs/exam-site/new-requirements/NR-01-exam-segment.md
2. docs/exam-site/new-requirements/rules.md
3. docs/exam-site/new-requirements/nr-01/README.md (ownership table, running Maven, how agents work)
4. docs/exam-site/new-requirements/nr-01/contracts.md (frozen; section 7, the compatibility rules, matters
   most to you)
5. docs/exam-site/new-requirements/nr-01/d-sitting.md (your slices D1 to D8)
6. docs/exam-site/new-requirements/nr-01/progress/f-done.md
7. docs/exam-site/current-api.md, for sitting, results, evaluation and leaderboard

Change only the files the README ownership table gives to D. The one shared exception is your own block in
AuditLogIT.API_OPERATIONS, marked "D". For any other file, stop and ask the orchestrator for the exact change;
keep working on slices that do not need it. No migrations: if you need one, ask.
The current website must keep working: keep the legacy kind and myStatus vocabularies, and the retake
without an attempt (contracts section 7, items 7 to 9).

Running Maven: only through
  powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent D -Tests "ClassA,ClassB"
It waits for the machine-wide heavy lock, and uses your database cc_test_nr01_d. Never run mvn directly,
and never print or commit a password. On a Flyway checksum error, add -ResetDb. Keep runs to your slice's
classes. Run -Full only once, at the end, in the background.

Each slice: merge nr01/integration first; write the code and tests; run them through the script; write
docs/exam-site/new-requirements/nr-01/progress/d-<slice>.md; commit with your session's attribution line.
Never use --no-verify. Do not push, deploy, or touch web/, staging or client-vps.
Read time from the injected Clock. Tests use MutableClock and never sleep. Real PostgreSQL, never H2.
Record API changes in nr-01/api-changes/d.md and UI consequences in nr-01/web-impact/d.md.

Until A is merged, PaperVersionIT inserts the v2 paper through repositories; say so in your report.
Until C is merged, access has no expiry or free rule; write those cases so they pass after C's merge, and
run them again after you merge nr01/integration.

When done: merge nr01/integration, run -Full, and report: slices done; test command and real counts (paste
any failure); files changed outside your ownership (should be none); questions; anything skipped.
```

---

## 4. Agent B

```
You are agent B (packages and catalog) on NR-01, the exam segment API change for the coaching-center exam
site.
Worktree: F:\cc-wt\nr01-b, branch nr01/b-packages. Run `git rev-parse --show-toplevel` before your first edit.
It must print your worktree. Write only inside it.
Agents A, C and D work in parallel in their own worktrees. You never edit their files. You talk only to the
orchestrator, through your report or by stopping with a question.

Read these first, in order, and follow them exactly:
1. docs/exam-site/new-requirements/NR-01-exam-segment.md
2. docs/exam-site/new-requirements/rules.md
3. docs/exam-site/new-requirements/nr-01/README.md (ownership table, running Maven, how agents work)
4. docs/exam-site/new-requirements/nr-01/contracts.md (frozen; never change it yourself)
5. docs/exam-site/new-requirements/nr-01/b-packages.md (your slices B1 to B6)
6. docs/exam-site/new-requirements/nr-01/progress/f-done.md
7. docs/exam-site/current-api.md, for categories, modules, the public catalog and the roster

Change only the files the README ownership table gives to B. The one shared exception is your own block in
AuditLogIT.API_OPERATIONS, marked "B". For any other file, stop and ask the orchestrator for the exact change;
keep working on slices that do not need it. No migrations: if you need one, ask.
Package revisions (B4) follow the same pattern A uses for exams, but they are your own code: do not import
A's classes. Tell the orchestrator as soon as B4 is committed. The instructor's package list belongs to D,
not you.

Running Maven: only through
  powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent B -Tests "ClassA,ClassB"
It waits for the machine-wide heavy lock, and uses your database cc_test_nr01_b. Never run mvn directly,
and never print or commit a password. On a Flyway checksum error, add -ResetDb. Keep runs to your slice's
classes. Run -Full only once, at the end, in the background.

Each slice: merge nr01/integration first; write the code and tests; run them through the script; write
docs/exam-site/new-requirements/nr-01/progress/b-<slice>.md; commit with your session's attribution line.
Never use --no-verify. Do not push, deploy, or touch web/, staging or client-vps.
Read time from the injected Clock. Tests use MutableClock and never sleep. Real PostgreSQL, never H2.
Record API changes in nr-01/api-changes/b.md and UI consequences in nr-01/web-impact/b.md.

When done: merge nr01/integration, run -Full, and report: slices done; test command and real counts (paste
any failure); files changed outside your ownership (should be none); questions; anything skipped.
```

---

## 5. Agent A (start last; it is the largest and merges last)

```
You are agent A (exam authoring and staged edits) on NR-01, the exam segment API change for the
coaching-center exam site.
Worktree: F:\cc-wt\nr01-a, branch nr01/a-authoring. Run `git rev-parse --show-toplevel` before your first edit.
It must print your worktree. Write only inside it.
Agents B, C and D work in parallel in their own worktrees. You never edit their files. You talk only to the
orchestrator, through your report or by stopping with a question.

Read these first, in order, and follow them exactly:
1. docs/exam-site/new-requirements/NR-01-exam-segment.md
2. docs/exam-site/new-requirements/rules.md
3. docs/exam-site/new-requirements/nr-01/README.md (ownership table, running Maven, how agents work)
4. docs/exam-site/new-requirements/nr-01/contracts.md (frozen; section 7, the compatibility rules, matters
   to your publish and editor reads)
5. docs/exam-site/new-requirements/nr-01/a-authoring.md (your slices A1 to A7)
6. docs/exam-site/new-requirements/nr-01/progress/f-done.md
7. docs/exam-site/current-api.md, for live exams, questions, publish, copy and archive

Change only the files the README ownership table gives to A. The one shared exception is your own block in
AuditLogIT.API_OPERATIONS, marked "A". For any other file, stop and ask the orchestrator for the exact change;
keep working on slices that do not need it. No migrations: if you need one, ask.
StudentNoticeWriter.examUpdated and McqRescorer already exist (F); call them, do not change them.
The package case of the pending badge (A5) waits for B: keep that test @Disabled("needs B") until the
orchestrator tells you B is merged.

Running Maven: only through
  powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent A -Tests "ClassA,ClassB"
It waits for the machine-wide heavy lock, and uses your database cc_test_nr01_a. Never run mvn directly,
and never print or commit a password. On a Flyway checksum error, add -ResetDb. Keep runs to your slice's
classes. Run -Full only once, at the end, in the background.

Each slice: merge nr01/integration first; write the code and tests; run them through the script; write
docs/exam-site/new-requirements/nr-01/progress/a-<slice>.md; commit with your session's attribution line.
Never use --no-verify. Do not push, deploy, or touch web/, staging or client-vps.
Read time from the injected Clock. Tests use MutableClock and never sleep. Real PostgreSQL, never H2.
Record API changes in nr-01/api-changes/a.md and UI consequences in nr-01/web-impact/a.md.

When done: merge nr01/integration, run -Full, and report: slices done; test command and real counts (paste
any failure); files changed outside your ownership (should be none); questions; anything skipped.
```

---

## Orchestrator checklist (main session)

1. Commit the plan on `nr01/integration`. Cut `nr01/f-foundation`, add its worktree, and start F.
2. When F reports: review it against contracts.md; run `nr01-mvn.ps1 -Agent INT -Full`; merge it into `nr01/integration`; correct contracts.md from `progress/f-done.md`; commit.
3. Cut the C, D, B and A branches and worktrees from `nr01/integration`. Start them in that order, a few minutes apart.
4. Answer questions. Make cross-owner edits on `nr01/integration` and tell the affected agents to merge it.
5. Merge in the order C (C1 early if it reports), D, B, A, each when it reports green.
   - After each merge, run `-Agent INT -Full` and tell the others to merge `nr01/integration`.
   - Resolve `AuditLogIT` conflicts by keeping every agent's block.
   - After B, tell A to re-enable the package badge test.
6. Run [integration.md](integration.md). Report to Shahriar.
