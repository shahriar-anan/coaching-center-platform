# Phase 0 — Orientation and Rules Extract

Date: 2026-10-19 | Mode: read-only | Auditor: coding agent

No commands were run against the API, the database, or the VPS during this phase. The rules below were later superseded.

**2026-10-22:** The acceptance reference is the Rules Extract in `audit/phase-1.md`. Ignore the precedence list and the Rules Extract in this file. They still describe the replaced exam model (`api-exam-*`, module trees, and the old lifecycle).

## Superseded on 2026-10-19

The human said the PRD is outdated and that the exam system needs a full reimplementation. The confirmed rules are Sections 9, 10, and 15–17 of `docs/phases/phase-3/phase-3-scope.md`. Section 17 is evaluation: automatic MCQ scores, an instructor assigned per module, and a written total plus evaluated PDF that the student sees on save. The extract below is the previous Phase 3 reading. Do not use it as the acceptance reference.

## Precedence used for later phases

When documents disagree, later checks use this order:

1. `docs/phases/phase-3/phase-3-scope.md` and the Phase 3 stage plans that spell out the same rules (`api/.cursor/plans/api-exam-*.md`, `api/.cursor/rules/api-exam-invariants.mdc`). These are the exam-specific documents, and they are newer than the PRD.
2. `docs/prd/PROJECT_PRD.md` exam sections, only where Phase 3 is silent.
3. What the backend actually does. Not executed in this phase. `docs/phases/phase-3/phase-3-status.md` claims every exam API stage has exited. That claim is **INFERRED** from the status file until a later phase runs the checks.

The audit brief is a checklist. It is not a source of exam rules.

## Sources

| Path | Document date | File last write | Role |
|---|---|---|---|
| `docs/phases/phase-3/phase-3-scope.md` | none in file | 2026-10-16 02:13 | Binding exam business rules |
| `docs/phases/phase-3/phase-3-status.md` | none in file | 2026-10-16 03:01 | Stage exits and explicit non-goals |
| `api/.cursor/plans/api-exam-access.md` | none in file | 2026-10-16 02:02 | Student catalog and pre-start payload |
| `api/.cursor/plans/api-exam-written.md` | none in file | 2026-10-16 02:14 | Written upload, lock, storage |
| `api/.cursor/rules/api-exam-invariants.mdc` | none in file | 2026-10-16 02:14 | Cross-cutting exam invariants |
| `api/.cursor/plans/api-exam-mcq.md` | none in file | 2026-10-16 03:07 | Attempt, timer, save, score |
| `api/.cursor/plans/api-exam-results.md` | none in file | 2026-10-16 03:07 | Publish, pass, rank (not re-read line by line; scope §§11–12 and the status exit are the rule text) |
| `docs/prd/PROJECT_PRD.md` | 2026-09-24, v0.4 | 2026-10-02 07:50 | Older product PRD. Exam sections 5.6, 5.7, 5.8, 6.5 |
| `docs/phases/README.md` | none in file | not recorded | Phase index |
| `README.md` | none in file | not recorded | Repo map |

Searched `docs/` for exam specs. There is no `coaching_center_prd.md`. The scope cites `phase3_client_and_nontechnical_tasks.md`. That file is **MISSING** from the repo.

`phase-3-status.md` says Phase 3 is complete and API-only. `docs/phases/README.md` still marks Phase 2 as in progress. The scope header says Phase 2 must be complete before Phase 3. The status file says Phase 2's phone and browser exits do not block the exam API. That is a process note, not a missing exam rule.

## 5.1 Repository discovery

| Item | Status | Evidence |
|---|---|---|
| Backend, web, mobile, docs | `DONE-VERIFIED` | One git repo. `api/` Spring Boot, `web/` Next.js, `mobile/` Flutter, `docs/` specs. `README.md` describes the same split. |
| Build and versions | `DONE-VERIFIED` | `api/pom.xml`: Spring Boot parent `4.1.1`, `java.version` 21, Maven. Wrappers: `api/mvnw`, `api/mvnw.cmd`. Flyway and PostgreSQL driver are dependencies. springdoc `3.0.1` is on the classpath. |
| Package layout | `DONE-VERIFIED` | Modular monolith under `com.coachingcenter.api`. Exam code is `api/src/main/java/com/coachingcenter/api/exams/` (48 Java files), including module, access, MCQ attempt, written submission, results, and question bank types. |
| Git | `DONE-VERIFIED` | Branch `catalog-acquisition`. Last commit `e9f57b0` at 2026-10-16 04:05 +0600: "Record course and exam routes in the OpenAPI inventory so the audit test matches the API." Tracked dirty files seen: `mobile/lib/screens/catalog_browse_tab.dart`, `mobile/test/support/course_fixtures.dart`, and one mobile Android build report. Untracked: `docs/Client Request/`. No `TODO` or `FIXME` in `api/src/main/java/com/coachingcenter/api/exams/`. |
| Frontend | `DONE-VERIFIED` | `web/` exists. `package.json`: Next `16.3.6`, React 19, Tailwind 4, TypeScript, Vitest. App Router: `web/src/app/`. Routes found are admin catalog, students, payments, book orders, login, and activate. No `exam` match under `web/src`. No component-library dependency. `web/src/lib/api/client.ts` calls `fetch`. `web/README.md` is still the create-next-app text. |
| README, OpenAPI, Postman | `PARTIAL` | Root `README.md` and per-app READMEs exist. OpenAPI is generated from the running app (`/v3/api-docs`), not a file under `api/src/main/resources`. No Postman collection found. Whether the live spec matches the exam routes is a Phase 1 check. |

Estimate impact of 5.1: `none`. The student exam site is not in `web/` today. That is expected from the PRD ("admin panel only") and from Phase 3 ("no mobile or web UI"). Building those screens is the fast-track, not a surprise gap in this phase.

## Rules Extract

"Backend?" means whether this phase proved the API does it.

- **UNCHECKED** — rule is in the newer exam docs. Matching classes or a status-file exit exist. Behavior was not run.
- **N/A** — client screen behavior, or explicitly out of the exam API.
- **NOT SPECIFIED** — the newer exam docs do not state it.

| Topic | Type | Rule used for later checks | Source | Backend? |
|---|---|---|---|---|
| Exam types, states, lifecycle | Both | An exam module is its own catalog item: title, root description, price, publish status `draft` / `published` / `archived`. Its tree holds sections and papers only. A paper may have an MCQ component, a written component, or both. | Scope §§1, 4 | UNCHECKED |
| Exam types, states, lifecycle | MCQ | Schedule is per MCQ component. Derived lifecycle: draft → scheduled → active at the window start → ended → results published. Active/ended is time-driven. The module publish status does not decide active or ended. A start is accepted only while derived state is `ACTIVE`. | Scope §9; `api-exam-mcq.md` "Locked reading" | UNCHECKED |
| Exam types, states, lifecycle | Written | Own deadline, independent of the MCQ window. Statuses: `DRAFT` → `UNDER_REVIEW` → `EVALUATED`. There is no `SUBMITTED` status. | Scope §10; `api-exam-written.md` slice 1 | UNCHECKED |
| Eligibility and attempts | Both | One access mode per module. `OPEN`: any logged-in student, no course link. `ENROLLED_ONLY`: active enrollment in the one linked course; access ends when that enrollment ends. `PURCHASE_REQUIRED`: own purchase, no course link, access does not expire. Mode and course link must match. | Scope §§5–7 | UNCHECKED |
| Eligibility and attempts | MCQ | One attempt per student per MCQ component. A second start is rejected. The owner resumes with GET. A student who never starts has no attempt row and no invented zero. | Scope §9; `api-exam-mcq.md`; status "Held" | UNCHECKED |
| Eligibility and attempts | Written | One submission row per student per written component. No resubmit. No admin reopen. | Scope §10; written plan | UNCHECKED |
| Window, duration, deadline | MCQ | Start/end window plus a duration copied onto the attempt at start. Deadline is the earlier of `started_at + duration` and the live `endsAt`. Shortening `endsAt` can close an in-progress attempt. Later edits do not lengthen the copied duration. | `api-exam-mcq.md` | UNCHECKED |
| Window, duration, deadline | Written | Server deadline. After it, a draft cannot be submitted and stays `DRAFT`. The server does not auto-submit at the deadline. | Written plan "Locked reading" | UNCHECKED |
| Timer authority and auto-submit | MCQ | Server clock only (`Instant.now()`). Client `now`, `startedAt`, and `submittedAt` are ignored. In-progress payload includes `serverNow` and `deadlineAt`. At expiry, the next owner call finalizes with the answers already stored. Submit after the deadline does not extend the timer. | Scope §9; MCQ plan | UNCHECKED |
| Timer authority and auto-submit | Written | Server clock for the deadline. No auto-submit. | Written plan | UNCHECKED |
| Pre-exam information | MCQ | Student with access reads bilingual rules text, timing fields, `questionCount`, and `maxMarks`. No stems, options, `correct`, or `explanation` before the attempt starts. Catalog lists published modules only. | `api-exam-access.md`; scope §4.3; status `api-exam-access` exit | UNCHECKED |
| Pre-exam information | Written | Same rules payload, plus each question stem and marks. `explanation` stays hidden. | Access plan | UNCHECKED |
| In-exam behavior | MCQ | Start freezes question order, stem, options, correct flag, explanation, marks, and the paper pass percent. Order is the admin order. No shuffle. `PUT .../answers` replaces the full selection; a question omitted from that array is cleared. While `IN_PROGRESS`, the student payload omits `correct`, `explanation`, score, and pass. | MCQ plan; invariants | UNCHECKED |
| In-exam behavior | MCQ | Navigation, status indicators, and a review screen are client UI. The API does not define them. | PRD §5.6. Phase 3 does not add them. | N/A |
| In-exam behavior | MCQ | Restoring saved answers after refresh: PRD §5.6 requires autosave. The MCQ plan defines PUT, and does not say the GET body includes the saved selection. | PRD §5.6; MCQ plan gap | UNCHECKED — confirm in Phase 3 |
| In-exam behavior | Written | Before final submit, re-upload, reorder, and remove have no edit-count limit. Final submit requires at least one image and locks the submission. | Scope §10; written plan | UNCHECKED |
| Scoring | MCQ | No negative marking. No partial credit. One correct option. Unanswered scores 0. The correct option scores full marks. Any other option scores 0. Score is the sum of marks awarded. | Scope §9; MCQ plan; status "Held" | UNCHECKED |
| Scoring | Written | Manual. An admin enters marks per question and one feedback text. Either admin role may evaluate. | Scope §§10–11 | UNCHECKED |
| Submission rules | MCQ | Manual submit or finalize-on-expiry. Scoring is immediate and automatic. Double start is rejected. A save at or after the deadline returns `ATTEMPT_EXPIRED` and does not change answers; the attempt is finalized first. | Scope §9; MCQ plan | UNCHECKED |
| Submission rules | Written | Final submit locks. Second submit is rejected. No reopen route. | Scope §10; invariants | UNCHECKED |
| Submission statuses | MCQ | `IN_PROGRESS`, `SUBMITTED`. | MCQ plan | UNCHECKED |
| Submission statuses | Written | `DRAFT`, `UNDER_REVIEW`, `EVALUATED`. | Written plan | UNCHECKED |
| Upload rules | Written | Accepted upload types: JPEG, PNG, WebP. Cap `EXAM_IMAGE_MAX_BYTES` default 15 MiB (15728640). A configured cap of 20 MiB or more must fail startup. Over-cap upload stores nothing. Server applies EXIF orientation, drops metadata, scales the longest side to at most 1600px (does not enlarge), encodes JPEG quality 0.72, and stores only that JPEG under `FILE_STORAGE_DIR` with an unguessable name. | Written plan; invariants | UNCHECKED |
| Upload rules | Written | Maximum image count. | Newer exam docs do not state one. PRD §5.7 says 10. See conflict C2. | NOT SPECIFIED in Phase 3 |
| Evaluation | MCQ | Automatic on submit. | Scope §9 | UNCHECKED |
| Evaluation | Written | Manual, then evaluated. Students do not evaluate. | Scope §10 | UNCHECKED |
| Result visibility | MCQ | `immediateResults` per component. If true, the student receives score, maximum, chosen option, correct option, and explanation on submit. Pass/fail is included only when the paper has no written component. If false, those fields stay hidden until an admin publishes. | Scope §§9, 11.3; MCQ plan | UNCHECKED |
| Result visibility | Written | Hidden until publish. A visible written result includes the explanation frozen at submit. There is no correct option. | Scope §11.3 | UNCHECKED |
| Result visibility | Both | Combined score is a plain sum, shown when both component scores are visible. Pass percent is a whole number 0–100, snapshotted at MCQ start, or at written submit if there was no MCQ. Pass when `score × 100 >= passPercent × maximum`. | Scope §§11.2, 11.4 | UNCHECKED |
| Result visibility | Both | Rank and leaderboard use that paper score, ties share a rank and the next rank skips. Students who did not sit are omitted. A student sees rank and the board only after every component on the paper is published. Immediate MCQ results do not include rank. Admins can read the board before publish. The board shows name, rank, score, maximum, pass/fail. No phone number. | Scope §§11.4–11.5 | UNCHECKED |
| Answer / feedback after submit | MCQ | Review (chosen, correct, explanation) appears only together with a visible score. Hidden while the score is hidden. | Scope §11.3 | UNCHECKED |
| Answer / feedback after submit | Written | Explanation with the published result. One feedback text from the admin. | Scope §§10, 11.3 | UNCHECKED |
| Security / integrity | Both | In-progress MCQ omits correct answers and explanations. Another student cannot read an attempt. Written file bytes are not a public URL; `GET /api/v1/exam-files/{id}` is the owner or an admin with `EXAM_READ`. Question-bank curation is Master Admin only. Deletes use `EXAM_DELETE`, Master Admin only. | Invariants; MCQ plan; written plan; scope §8.3 | UNCHECKED |
| Explicitly not required | Both | Shuffle, extra attempts, retakes, negative marking, partial credit, weighted combined score, random paper from the bank, extra time, late written submit, rubrics, second marker, proctoring, notices, batches, payment gateway, exam screens. | `phase-3-status.md` "Held for a later phase"; scope §14 | N/A |
| Open / TBD inside Phase 3 | Both | Scope §12: "No open questions remain for this phase." The conflicts below are between the PRD and Phase 3, not open items inside Phase 3 itself. | Scope §12 | — |

## Conflicts

Each one is recorded. Later phases judge behavior against the "Use" column, not against the PRD, unless you say otherwise.

| ID | Topic | Older text | Newer text | Use |
|---|---|---|---|---|
| C1 | Upload size | PRD §5.7 and §14.1 item 4: server rejects images over 3 MB. | Written plan: 15 MiB default; 20 MiB or more fails startup. | 15 MiB rule |
| C2 | Image count | PRD §5.7: up to 10 images. | Phase 3 scope and the written plan do not state a maximum. "No limit" in those docs refers to how many times the student may edit before submit. | **Open.** Do not treat 10 as required, and do not treat "unlimited images" as required. |
| C3 | Who compresses | PRD §5.7: phone compresses to about 1600px, JPEG quality about 70. | Written plan: server does EXIF, metadata strip, 1600px, JPEG 0.72, and stores only that file. | Server processing is required. Phone compression is optional. |
| C4 | Written statuses | PRD §5.7: `DRAFT → SUBMITTED → UNDER_REVIEW → EVALUATED`. | Written plan: `DRAFT → UNDER_REVIEW → EVALUATED`. | No separate `SUBMITTED` |
| C5 | Score vs review | PRD §5.6: result detail can be score only, or include correct answers. | Phase 3: one visibility switch. A visible score includes the review. There is no score-only mode. | Phase 3 |
| C6 | Rank | PRD §5.8 and §14.1 item 5: no ranking or grade system in the MVP. | Scope §§11.4–11.5 and §12: pass percent, rank, and leaderboard are settled. | Phase 3 |
| C7 | What an "exam" is | PRD §6.5: one exam is MCQ or written, targeted at a course or batch, with states through `RESULT_PUBLISHED` and `ARCHIVED`. | Scope §4: module, tree, paper, and optional pair of components. Batches are out of scope (§14). | Phase 3 |
| C8 | Who may sit | PRD implies course/batch membership. | Scope §6: `OPEN`, `ENROLLED_ONLY`, `PURCHASE_REQUIRED`. | Phase 3 |
| C9 | Question bank | PRD §6.5: bank is the authoring source, with optional question images, a category tree, and archive instead of delete. | Scope §8: questions are written onto a paper; a bank is a Master Admin grouping and does not detach them. No random draw. | Phase 3. Question images are NOT SPECIFIED. |
| C10 | Language of names | PRD v0.4: admin-entered content is not translated. | Scope §2: module, section, paper, and component rules text are bilingual. Question content stays one language. | Phase 3 for exams |
| C11 | Autosave shape | PRD §5.6: local state and periodic autosave. | MCQ plan: one PUT replaces the whole selection; omitted questions are cleared. | PUT contract. Whether GET returns the saved selection is still unstated. |

## Open rules to carry forward

1. **C2 — maximum number of written images.** Needs a decision only if the client still wants the PRD cap of 10.
2. **In-progress answer restore.** The MCQ plan does not say that GET returns the saved option ids. Phase 3 must check the code and the tests. If they are absent, refreshing the runner loses answers (risk R6).
3. **Answer-sheet retention.** PRD §14.2 says none for now. Phase 3 does not add a retention rule. Treat "no retention policy" as still standing.
4. **Which access mode the first public exam will use.** `OPEN` needs only a logged-in student. `ENROLLED_ONLY` and `PURCHASE_REQUIRED` need course access or a verified manual payment. The fast-track brief excludes payments and admin UI. This can change the estimate. Not an exam-rule gap.

## Carry-forward notes

- Do not treat `phase-3-status.md` ("complete") as proof the API runs. Phase 1 has to build, migrate, and run the exam `*IT` classes.
- `web/` has no exam screens. Phase 7 should start from "student exam UI is unbuilt." Login and the fetch client exist for the admin app.
- Swagger UI was documented as public during Phase 1 (`api/.cursor/progress/api-identity/slice-7-openapi-audit.md`). Re-check in Phase 5. Do not call it exposed until the running profile is seen.
- VPS, Java, and PostgreSQL deployment were not touched. Phase 6 still needs approval before SSH.
- Dirty mobile files are catalog work. They are outside this audit.
