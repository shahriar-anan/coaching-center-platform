# NR-01 contracts (frozen after stage 1)

Agent F builds everything in sections 2–5. From stage 2 onwards only the orchestrator changes this file. If F has to deviate, F records it in its final report and the orchestrator corrects this file before stage 2 starts.

"Package" in prose means `live_exam_modules` / `LiveExamModule` in code (R1). API paths keep `live-exam-modules`.

## 1. Vocabulary

| Term | Meaning |
|---|---|
| **Window** | `liveAt` (inclusive) to `endAt` (exclusive). Stored as `live_exams.live_at` and `live_exams.ends_at`. Both are null for a practice exam and for a live exam whose date is not announced. |
| **Duration** | `durationMinutes`. A sitting's deadline is `startedAt + durationMinutes`. The window end never caps it (D15). |
| **Mode** | `LIVE` (has, or will have, a window) or `PRACTICE` (no window, open from publish). |
| **Phase** (computed, never stored) | `UNANNOUNCED` (live, no date), `UPCOMING` (now < liveAt), `LIVE` (liveAt ≤ now < endAt), `ENDED` (now ≥ endAt; the student UI calls this "archived exams"), `PRACTICE`. |
| **Attempt kind** | `LIVE`: the first attempt, started inside the window. `ARCHIVED`: the first attempt, started after the window. `PRACTICE`: the first attempt of a practice exam. `RETAKE`: any later attempt. Set at start and never changed (R18). |
| **Board attempt** | The attempt that can be on the leaderboard: `LIVE` for a live exam, `PRACTICE` for a practice exam. |
| **Grant start** | When a student's access began: the grant's `created_at`, or the roster row's `created_at`. |
| **Access end** | `null` (lifetime) if `accessDays` is null or the exam is free. Otherwise `anchor + accessDays`, where `anchor = max(grant start, endAt)` for a live exam, or `max(grant start, publishedAt)` for a practice exam. If a live exam has no `endAt` yet, the access has **not started counting** and does not expire (A2'). |
| **Working copy** | What staff edit. For a draft exam or package it is the row itself. For a published one it is the row with its pending revision applied. |
| **Pending revision** | Staged edits to a published exam or package, stored as JSON until the Master Admin publishes them or anyone discards them. |

## 2. Schema (Flyway, written by F)

The last applied version today is `V44`. F adds `V45`–`V51` exactly as described here: names, columns and constraints. The SQL details are F's. Every migration backfills existing rows so the current data, and the staging data, stay valid. No stage 2 agent adds a migration. If one is needed, the orchestrator assigns `V52+` and merges it first.

### V45__nr01_exam_home_and_packages.sql
- `live_exams.category_id UUID REFERENCES live_exam_categories(id)`. Backfill from the exam's module, then `SET NOT NULL`. Index on `(category_id) WHERE deleted_at IS NULL`.
- `live_exams.module_id` loses `NOT NULL`. It is kept only as "created in" for history. **No rule reads it after stage 2.** Integration drops it with `V52` if nothing reads it any more ([integration.md](integration.md) step 4).
- New table `live_exam_package_exams`:
  - Columns: `id UUID PK`, `module_id UUID NOT NULL → live_exam_modules`, `exam_id UUID NOT NULL → live_exams`, `position INT NOT NULL`, `added_at TIMESTAMPTZ NOT NULL`, `removed_at TIMESTAMPTZ NULL`.
  - A unique partial index on `(module_id, exam_id) WHERE removed_at IS NULL`, and indexes on `exam_id` and `(module_id, position)`.
  - Backfill one row per existing non-deleted exam: `added_at = created_at`.
- `live_exams.instructor_id UUID NULL → users`. Backfill from `live_exam_instructors` through the exam's module. `live_exam_instructors` stays, as the **package default instructor** (R3).

### V46__nr01_window_mode_attempts.sql
- `live_exams.ends_at TIMESTAMPTZ NULL`. Backfill `live_at + duration_minutes`.
- `live_exams.mode VARCHAR(16) NOT NULL DEFAULT 'LIVE'`, `CHECK (mode IN ('LIVE','PRACTICE'))`. Then drop the default.
- `CHECK ((live_at IS NULL) = (ends_at IS NULL))`.
- `CHECK (ends_at IS NULL OR ends_at >= live_at + make_interval(mins => duration_minutes))` (D16).
- `CHECK (mode = 'LIVE' OR live_at IS NULL)`.
- `live_exams.published_at TIMESTAMPTZ NULL`. Backfill `updated_at` for rows that are `PUBLISHED` or `ARCHIVED`. It is set on the first publish and never cleared. It is the practice anchor.
- `live_exam_participants`:
  - Drop `live_exam_participants_one_idx`.
  - Add `attempt_no INT NOT NULL DEFAULT 1`. Unique `(exam_id, student_id, attempt_no) WHERE deleted_at IS NULL`. Unique `(exam_id, student_id) WHERE status = 'IN_PROGRESS' AND deleted_at IS NULL`, so a student has at most one open attempt.
  - The kind check becomes `('LIVE','ARCHIVED','RETAKE','PRACTICE')`. Run `UPDATE … SET kind = 'ARCHIVED' WHERE kind = 'RETAKE'`, because today's single later attempt was a first attempt after the window.
- New table `live_exam_retake_grants`:
  - Columns: `id UUID PK`, `exam_id → live_exams NOT NULL`, `student_id → users NOT NULL`, `granted_by → users NOT NULL`, `reason VARCHAR(500) NULL`, `created_at NOT NULL`, `used_participant_id → live_exam_participants NULL`, `revoked_at NULL`, `revoked_by → users NULL`.
  - Unique `(exam_id, student_id) WHERE revoked_at IS NULL`. This allows one written retake (R14).

### V47__nr01_mcq_scoring.sql
- Drop `live_exam_options_one_correct_idx`.
- `live_exam_questions.multiple_answer BOOLEAN NOT NULL DEFAULT false`.
- `live_exam_answers`: drop the primary key and add `PRIMARY KEY (participant_id, question_id, option_id)`.
- `live_exams.negative_marks NUMERIC(5,2) NULL CHECK (negative_marks IS NULL OR negative_marks >= 0)`. Null or 0 means no negative marking.
- `live_exams.answers_changeable BOOLEAN NOT NULL DEFAULT true`.
- `live_exam_participants.score` and `pending_score` become `NUMERIC(8,2)`.
- New table `live_exam_answer_confirmations`: `participant_id → live_exam_participants`, `question_id → live_exam_questions`, `confirmed_at TIMESTAMPTZ NOT NULL`, `PRIMARY KEY (participant_id, question_id)`. It records final answers when "answers can be changed" is off (R9). Entity `LiveExamAnswerConfirmation`, with its repository.

### V48__nr01_question_papers.sql
- New table `live_exam_question_papers`:
  - Columns: `id UUID PK`, `exam_id → live_exams NOT NULL`, `version INT NOT NULL`, `stored_file_name`, `original_file_name`, `byte_size`, `content_type` (the same types as the `live_exams.question_*` columns), `created_at NOT NULL`, `created_by → users NULL`.
  - Unique `(exam_id, version)`.
- `live_exams.question_paper_id UUID NULL → live_exam_question_papers`: the **published** paper. A pending revision may point at a newer version.
- Backfill version 1 from each exam's `question_*` columns, and set `question_paper_id`.
- `live_exam_participants.question_paper_id UUID NULL → live_exam_question_papers`. Backfill every written participant with version 1 of its exam.
- The `live_exams.question_*` columns stay and are mirrored from the published paper until integration `V52` drops them (only if nothing reads them).

### V49__nr01_commerce_and_catalog.sql
- `live_exams`:
  - `is_free BOOLEAN NOT NULL DEFAULT false`;
  - `discount_percent INT NULL CHECK (BETWEEN 1 AND 99)`;
  - `discount_ends_at TIMESTAMPTZ NULL`;
  - `access_days INT NULL CHECK (> 0)`;
  - `popular BOOLEAN NOT NULL DEFAULT false`;
  - `preview_enabled BOOLEAN NOT NULL DEFAULT false`;
  - `CHECK (NOT is_free OR (price IS NULL AND discount_percent IS NULL))`.
- `live_exam_modules`: `discount_percent`, `discount_ends_at` and `popular`, with the same rules as on exams.
- `live_exam_orders`:
  - `module_id` loses `NOT NULL`.
  - The scope check becomes: `MODULE` needs `module_id`, and `exam_id` null; `EXAM` needs `exam_id`; `BUNDLE` needs `module_id` and `exam_id` null.
  - Add `list_price NUMERIC(12,2) NULL` and `discount_percent INT NULL`. They record what the student saw at checkout (R19).
- `live_exam_grants.module_id` loses `NOT NULL`. `EXAM` grants may leave it null.
- New table `student_favourites`:
  - Columns: `id UUID PK`, `student_id → users NOT NULL`, `exam_id → live_exams NULL`, `module_id → live_exam_modules NULL`, `created_at NOT NULL`.
  - `CHECK (num_nonnulls(exam_id, module_id) = 1)`.
  - Unique partial indexes on `(student_id, exam_id)` and `(student_id, module_id)`.

### V50__nr01_revisions.sql
- `live_exam_revisions`: `id UUID PK`, `exam_id → live_exams NOT NULL UNIQUE`, `payload JSONB NOT NULL`, `created_by → users NOT NULL`, `created_at`, `updated_by → users NOT NULL`, `updated_at`.
- `live_exam_module_revisions`: the same, with `module_id` in place of `exam_id`.
- `live_exam_change_log`:
  - Columns: `id UUID PK`, `exam_id NULL`, `module_id NULL`, `changes JSONB NOT NULL` (the change list that was published), `rescored INT NOT NULL DEFAULT 0`, `published_by → users NOT NULL`, `published_at NOT NULL`.
  - `CHECK (num_nonnulls(exam_id, module_id) = 1)`.

### V51__nr01_notice_kind.sql
- Recreate `student_notices_kind_check` with every kind listed in `V40`, plus `EXAM_UPDATED`.

## 3. Entities and enums (written by F)

Field names in Java follow the columns in camelCase. String constants follow the existing style (`public static final String` on the entity), not Java enums.

- **`LiveExam`.**
  - Adds: `categoryId`, `instructorId`, `endsAt`, `mode` (`LIVE`, `PRACTICE`), `publishedAt`, `negativeMarks` (`BigDecimal`), `answersChangeable`, `questionPaperId`, `free`, `discountPercent`, `discountEndsAt`, `accessDays`, `popular`, `previewEnabled`.
  - `moduleId` becomes nullable.
- **`LiveExamModule`.** Adds `discountPercent`, `discountEndsAt`, `popular`.
- **`LiveExamQuestion`.** Adds `multipleAnswer`.
- **`LiveExamAnswer`.** Uses the composite id `(participantId, questionId, optionId)`.
- **`LiveExamParticipant`.**
  - Adds `attemptNo` and `questionPaperId`.
  - `score` and `pendingScore` become `BigDecimal`.
  - Constants: `LIVE`, `ARCHIVED`, `RETAKE`, `PRACTICE`.
- **`LiveExamOrder`.** Adds `listPrice`, `discountPercent`. `moduleId` becomes nullable.
- **`LiveExamGrant`.** `moduleId` becomes nullable.
- **New entities and repositories:** `LiveExamPackageExam`, `LiveExamQuestionPaper`, `LiveExamRetakeGrant`, `StudentFavourite`, `LiveExamRevision`, `LiveExamModuleRevision`, `LiveExamChangeLog`, `LiveExamAnswerConfirmation`.

**Repository methods F must provide** (stage 2 may add more):

- `LiveExamPackageExamRepository`:
  - `findActiveByExamId(examId)`;
  - `findActiveByModuleId(moduleId)`, ordered by position;
  - `findByExamId(examId)`, including removed rows;
  - `existsActive(moduleId, examId)`.
- `LiveExamParticipantRepository`:
  - `findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc`;
  - `findOpen(examId, studentId)`;
  - `findBoardAttempts(examId)`: kind `LIVE` or `PRACTICE`, not deleted.
- `LiveExamQuestionPaperRepository`: `findTopByExamIdOrderByVersionDesc`, `findById`.
- `LiveExamRetakeGrantRepository`: `findActive(examId, studentId)`.

## 4. Shared helpers (built by F, frozen afterwards)

All are plain Spring components or static helpers in `com.coachingcenter.api.exams`, with unit tests. Stage 2 agents call them and do not edit them. A bug fix goes through the orchestrator.

```java
/** Window, phase, classification and deadline. */
final class ExamWindow {
  static String phase(LiveExam exam, Instant now);                  // UNANNOUNCED | UPCOMING | LIVE | ENDED | PRACTICE
  static boolean canStartFirstAttempt(LiveExam exam, Instant now);  // practice: published; live: liveAt != null && now >= liveAt
  static String firstAttemptKind(LiveExam exam, Instant startedAt); // PRACTICE | LIVE (startedAt < endsAt) | ARCHIVED
  static Instant deadline(LiveExam exam, Instant startedAt);        // startedAt + durationMinutes
  static Instant lastLiveDeadline(LiveExam exam);                   // endsAt + durationMinutes, null if no window
  static void validate(String mode, Instant liveAt, Instant endsAt, int durationMinutes); // throws EXAM_WINDOW_INVALID
}

/** Effective price at an instant. Discount is active when discountPercent != null and (discountEndsAt == null || now < discountEndsAt). */
final class ExamPricing {
  static BigDecimal effective(BigDecimal price, Integer discountPercent, Instant discountEndsAt, Instant now); // HALF_UP to 2 places; null price → null
  static PriceView view(LiveExam exam, Instant now, boolean moduleOnly);
  static PriceView view(LiveExamModule module, Instant now);
  static void validate(BigDecimal price, boolean free, Integer discountPercent, Instant discountEndsAt); // VALIDATION_FAILED with fieldErrors
}
record PriceView(BigDecimal price, Integer discountPercent, String discountEndsAt, BigDecimal effectivePrice, boolean free, boolean moduleOnly) {}

/** MCQ scoring (R8, P-a). Pure. */
final class McqScoring {
  static BigDecimal questionScore(int marks, Set<UUID> correct, Set<UUID> chosen, BigDecimal negativeMarks);
  // chosen empty → 0; chosen equals correct → +marks; otherwise → -negativeMarks (or 0 when null)
  static BigDecimal total(List<QuestionKey> key, Map<UUID, Set<UUID>> chosenByQuestion, BigDecimal negativeMarks);
  static int maximum(List<QuestionKey> key);          // sum of marks
  static boolean passed(BigDecimal score, int maximum, Integer passPercent); // score*100 >= passPercent*maximum
  record QuestionKey(UUID questionId, int marks, Set<UUID> correctOptionIds) {}
}

/** Re-scores every submitted MCQ attempt of an exam from the current key. Returns the number of attempts whose score changed. */
@Component class McqRescorer { int rescore(UUID examId); }

/** Access end (A2'). */
final class AccessWindow {
  static Instant end(LiveExam exam, Instant grantStart); // null = no end (lifetime, free, or not started counting)
  static boolean expired(LiveExam exam, Instant grantStart, Instant now);
}

/** Who may sit and who may read. Signatures frozen. F ships a correct version for membership and removal (R4);
 *  C adds free exams and expiry inside the same methods. */
@Component class LiveExamAccess {
  AccessState state(UUID studentId, LiveExam exam, Instant now);
  boolean canSit(UUID studentId, LiveExam exam, Instant now);   // state.granted && !state.expired
  boolean hasEver(UUID studentId, LiveExam exam);               // any grant, roster row or attempt ever: for reading own results
  boolean hasModule(UUID studentId, UUID moduleId);             // unchanged meaning
}
record AccessState(boolean granted, String source /* ROSTER | MODULE | EXAM | BUNDLE | MANUAL | FREE */, Instant grantStart, Instant endsAt, boolean expired) {}

/** A package grant or roster row covers an exam when a membership row (module_id, exam_id) exists with
 *  removed_at == null, or removed_at > grant start (R4). F implements this inside LiveExamAccess. */

/** Change list returned before publish (E31). */
record ChangeList(String revisionId, List<FieldChange> fields, List<QuestionChange> questions,
                  PaperChange paper, List<MembershipChange> packageExams, Impact impact) {}
record FieldChange(String field, Object before, Object after) {}
record QuestionChange(UUID questionId, Integer position, String change /* ADDED | REMOVED | STEM | OPTIONS_TEXT | CORRECT | MARKS | EXPLANATION | MULTIPLE */,
                      Object before, Object after) {}
record PaperChange(Integer beforeVersion, Integer afterVersion, String afterFileName) {}
record MembershipChange(UUID examId, String titleEn, String titleBn, String change /* ADDED | REMOVED */) {}
record Impact(int papersToRescore, boolean notifiesStudents, int studentsToNotify) {}
```

F wires `LiveExamService`, `LiveExamSittingService` and the evaluation service only as far as needed to compile and keep the current tests green ([f-foundation.md](f-foundation.md) F4). Stage 2 replaces that wiring.

## 5. Error codes and permissions (added by F)

**New `ErrorCode` values** (HTTP status in brackets):

| Code | Status | Meaning |
|---|---|---|
| `EXAM_WINDOW_INVALID` | 400 | The window ends before `liveAt + duration`; only one date is set; or a practice exam has dates. |
| `QUESTION_STRUCTURE_LOCKED` | 409 | A question or option is being added or removed after someone has started the exam (R16). |
| `ANSWER_LOCKED` | 409 | "Answers can be changed" is off and the question is already answered or confirmed (R9). |
| `ATTEMPT_IN_PROGRESS` | 409 | The student already has an open attempt. |
| `ACCESS_EXPIRED` | 409 | The student's access to this exam has ended. |
| `PENDING_CHANGES_STALE` | 409 | The `revisionId` sent to publish is not the current one. |
| `NO_PENDING_CHANGES` | 409 | Discard or publish called with nothing staged on a published item. |
| `EXAM_ALREADY_IN_PACKAGE` | 409 | |

**Reused codes:** `EXAM_NOT_FOR_SALE` (free or unpriced exam in an order), `RETAKE_NOT_ALLOWED`, `SITTING_NOT_OPEN`, `CONTENT_LOCKED` (no longer raised for schedule, scoring or text; remove those raises), `EXAM_WINDOW_CONFLICT` and `EXAM_WINDOW_ENDED` (**no longer raised**; keep the enum values).

**New permissions** (`Permissions` and `LiveExamPermissionContributor`):

| Permission | Master Admin | System Admin | Instructor | Student |
|---|---|---|---|---|
| `LIVE_EXAM_POPULAR` | yes | | | |
| `LIVE_EXAM_RETAKE_GRANT` | yes | yes | | |
| `LIVE_EXAM_PACKAGE_EDIT` (membership, staged package edits) | yes | yes | | |
| `LIVE_EXAM_FAVOURITE` | | | | yes |

Publishing a revision uses the existing `LIVE_EXAM_PUBLISH`, which is Master Admin only. Preview uses `LIVE_EXAM_BROWSE`.

**New audit actions.** F adds these as constants on `AuditRecorder`:
- `LIVE_EXAM_REVISION_PUBLISH`, `LIVE_EXAM_REVISION_DISCARD`;
- `LIVE_EXAM_MODULE_REVISION_PUBLISH`, `LIVE_EXAM_MODULE_REVISION_DISCARD`;
- `LIVE_EXAM_RETAKE_GRANT`, `LIVE_EXAM_RETAKE_REVOKE`;
- `LIVE_EXAM_PACKAGE_EXAM_ADD`, `LIVE_EXAM_PACKAGE_EXAM_REMOVE`;
- `LIVE_EXAM_INSTRUCTOR_ASSIGN`, `LIVE_EXAM_INSTRUCTOR_UNASSIGN`;
- `LIVE_EXAM_POPULAR_SET`.

## 6. Endpoints

All under `/api/v1`. "Changed" means an existing route whose body or behaviour changes. Times are ISO-8601 UTC. Money is a decimal string or number, as the API does today.

### Exam fields: `ExamView` gains these (A assembles them; values come from the helpers and from D and C)

| Field | Notes |
|---|---|
| `endAt` | **Now `ends_at`** (the window end). Before NR-01 it was `liveAt + duration`; after the backfill the values are identical. |
| `mode`, `phase`, `categoryId` | |
| `packages` | `[{id, titleEn, titleBn}]`, active memberships |
| `instructor` | `{id, name}` or null. Staff only. |
| `negativeMarks`, `answersChangeable`, `previewEnabled`, `popular`, `accessDays` | |
| `pricing` | `PriceView`. The old top-level `price` stays, for compatibility. |
| `pendingChanges` | `true` when a revision exists. Staff only. |
| `questions[].multipleAnswer` | |
| Student only | `access {granted, source, endsAt, expired}`, `attempts` (count), `myStatusDetail`, and `sitting`, which is now the **latest** attempt and also carries `attemptNo` and `attemptKind` (§7 items 7–8) |

### A: authoring
| Route | Who | |
|---|---|---|
| `POST /live-exams` | Master Admin, System Admin | **New.** Standalone exam: `categoryId` plus the create fields. |
| `POST /live-exam-modules/{moduleId}/exams` | Master Admin, System Admin | Changed. Also sets `categoryId` from the package, adds the membership, and inherits the package instructor. |
| `PATCH /live-exams/{id}` | Master Admin, System Admin | Changed. New fields. No schedule or content lock. On a published exam it **writes the revision**. `popular` is not accepted here (see B). |
| `POST/PATCH/DELETE /live-exams/{id}/questions…` | Master Admin, System Admin | Changed. `multipleAnswer`, several correct options, R16 lock. On a published exam the change goes into the revision. |
| `POST /live-exams/{id}/question-pdf` | Master Admin, System Admin | Changed. Creates a new paper version. On a published exam the revision points at it. |
| `GET /live-exams/{id}` | staff | Changed. Returns the **working copy**, plus `pendingChanges`. |
| `GET /live-exams/{id}/pending-changes` | Master Admin, System Admin | **New.** `ChangeList`, or `404` when there is none. |
| `DELETE /live-exams/{id}/pending-changes` | Master Admin, System Admin | **New.** Discards the revision. |
| `POST /live-exams/{id}/publish` | Master Admin | Changed. Body `{revisionId?}`. On a published exam with a revision: validate, apply, re-score, log, notify. `revisionId` is required and must match the current revision. |
| `GET /live-exam-pending-changes` | Master Admin, System Admin | **New.** `{exams, packages, items:[{kind: EXAM\|PACKAGE, id, titleEn, titleBn, updatedAt, updatedBy}]}`, for the badge. |
| `GET /live-exams/{id}/preview` | Student | **New.** Published exam with `previewEnabled`, in phase `ENDED` or `PRACTICE`. MCQ: stems, options and `multipleAnswer`, with no `correct` and no explanation. Written: `{hasPaper}`. Otherwise `404`. |
| `GET /live-exams/{id}/preview/question-pdf` | Student | **New.** The published paper, under the same rule. |
| `POST /live-exams/{id}/copy` | Master Admin, System Admin | Changed. Copies the new fields. Same category; memberships are copied too. No dates, no price, no `popular`. |

### B: packages and catalog
| Route | Who | |
|---|---|---|
| `POST /live-exam-modules/{id}/exams/{examId}` | Master Admin, System Admin | **New.** Adds an existing exam. On a published package it is staged. |
| `DELETE /live-exam-modules/{id}/exams/{examId}` | Master Admin, System Admin | **New.** Removes it (sets `removed_at`). On a published package it is staged. |
| `GET /live-exams` | staff | **New.** Cross-package list. Filters `packageId`, `categoryId`, `standalone`, `type`, `mode`, `status`, `phase`, `q`, `pending`, `popular`, plus paging. Rows carry the `ExamView` staff fields. |
| `PATCH /live-exam-modules/{id}` | Master Admin, System Admin | Changed. Discount fields. On a published package it writes the package revision. |
| `GET/DELETE /live-exam-modules/{id}/pending-changes`, `POST /live-exam-modules/{id}/publish` | as for exams | Changed or new. Same pattern as exams. |
| `PUT /live-exams/{id}/popular`, `PUT /live-exam-modules/{id}/popular` | Master Admin | **New.** `{popular: bool}`. Applied immediately, not staged. |
| Public and student category, package and exam reads | Public, Student | Changed. They expose `popular`, `pricing`, `mode`, `phase`, `packages`, and the **standalone exams of a category** (`GET /public/live-exam-categories/{id}/exams`, **new**). `popular=true` filter on the package and exam lists. |
| `GET /me/favourites` | Student | **New.** `{exams:[…], packages:[…]}` in public row shape. |
| `PUT/DELETE /me/favourites/exams/{id}`, `PUT/DELETE /me/favourites/packages/{id}` | Student | **New.** Idempotent. |

### C: commerce and access
| Route | Who | |
|---|---|---|
| `POST /live-exam-orders` | Student | Changed. Charges the effective price. A free exam gets `EXAM_NOT_FOR_SALE`. An `EXAM` order needs no `moduleId`. Records `listPrice` and `discountPercent`. |
| `GET /me/live-exam-access`, `GET /students/{id}/live-exam-access` | Student, staff | Changed. Each row has `endsAt` and `expired`. Free exams are not listed. |
| `POST /live-exam-grants` | Master Admin | Changed. An `EXAM` grant without a `moduleId`. |
| Order list, detail and history | | Changed. `listPrice` and `discountPercent`. |

### D: sitting, results, marking
| Route | Who | |
|---|---|---|
| `POST /live-exams/{id}/take` | Student | Changed. First attempt. Kind and deadline from `ExamWindow`. Needs `canSit`. If an attempt already exists, it returns the open one, or `SITTING_NOT_OPEN`. |
| `POST /live-exams/{id}/retake` | Student | Changed. MCQ: allowed after a finished attempt, with no open attempt. Written: needs an active retake grant, at most once. Returns `TakeView`, now with `attemptNo`. |
| `PUT /live-exams/{id}/answers` | Student | Changed. Each item is `{questionId, optionIds:[…]}`; `{questionId, optionId}` is still accepted. Enforces `multipleAnswer` and `answersChangeable`. |
| `PUT /live-exams/{id}/answers/{questionId}` | Student | **New.** `{optionIds, confirm?}`. One question. `confirm: true` makes it final when `answersChangeable` is off (R9). |
| `GET /live-exams/{id}/result?attempt=` | Student | Changed. Defaults to the latest attempt. MCQ: score, maximum, passed, answers and explanations **as soon as it is submitted**, and `position`. Written: as today, with `position` after release. |
| `GET /live-exams/{id}/attempts` | Student | **New.** The student's own attempts: `attemptNo`, `kind`, `startedAt`, `submittedAt`, `status`, `score` (written only once released). |
| `GET /live-exams/{id}/leaderboard` | Student, staff | Changed. Board attempts only; at or above the pass mark (D24); ties share the better rank and are ordered by earliest submit. Students see full rows only once phase is `ENDED` or `PRACTICE`; during `LIVE` they see `rows: []` plus `me`. |
| `POST /live-exams/{id}/retake-grants`, `DELETE /live-exams/{id}/retake-grants/{grantId}` | Master Admin, System Admin | **New.** `{studentId, reason?}`. Written exams only. Revoke only while unused. |
| `POST/DELETE /live-exams/{id}/instructor` | Master Admin | **New.** Per-exam instructor. The package route stays: it sets the package default, and with `applyToExams=true` it also sets every member exam. |
| Results, participation, submission feed and student results | staff | Changed. Counts split into `live`, `archived`, `retake` (and `practice`). Rows carry `kind` and `attemptNo`. Instructor scope comes from `live_exams.instructor_id`. |
| `GET /live-exam-participants/{id}`, evaluator question PDF | staff | Changed. Serve the paper version that participant sat. |

`position` shape: `{type: "CURRENT" | "RELATIVE" | "BOARD", rank: int|null, of: int, belowPass: bool}`.
- `CURRENT` is for a live attempt during the window.
- `RELATIVE` is for archived attempts and retakes: the rank this score would take among the board attempts.
- `BOARD` is for a live attempt after the window, or a practice first attempt.
- `rank` is null when `belowPass`.

## 7. Compatibility rules (the current website keeps working)

The web is not changed in this plan, so the API must keep these:

1. **No field is removed or renamed.** Fields are only added. `price` stays at the top level. `kind` gains values. `score` becomes a decimal, which is the same type in JSON.
2. `PUT /answers` still accepts `{questionId, optionId}`.
3. `sitting` stays one object: the latest attempt.
4. **The staff exam editor must keep saving.** Staff `GET` returns the working copy, so edits made to a published exam stay visible after a reload. The web's existing publish button sends no `revisionId`. For that case only, publish accepts a missing `revisionId` when the caller is Master Admin, and publishes the current revision. Log it at `warn`, and list it in web-impact for removal later.
5. The web's `final-save` call on a published exam with a revision is harmless and returns the view.
6. Routes keep their paths, and error codes the web maps keep their meaning.
7. **Attempt kind, legacy field.** The web types `kind` as `"LIVE" | "RETAKE"` and treats `RETAKE` as "own timer, off the merit list". Wherever `kind` is returned today (`sitting`, `TakeView`, the result), keep `kind` in that vocabulary and add the full value as `attemptKind`:
   - `LIVE` → `kind: "LIVE"`;
   - `ARCHIVED`, `RETAKE` and `PRACTICE` → `kind: "RETAKE"`.

   New fields and staff rows use `attemptKind` only.
8. **Student status, legacy field.** The web has a fixed `myStatus` set, and an unknown value would break its status pill. `myStatus` keeps today's nine values, and the new detail goes in `myStatusDetail`:
   - `ARCHIVED_OPEN` → `myStatus: "MISSED"`, `retakeAvailable: true` for MCQ (the web then offers its later-attempt button);
   - `PRACTICE_OPEN` → `"OPEN"`;
   - `EXPIRED` → `"MISSED"`, `retakeAvailable: false`.
9. **The current web's later-attempt flow.** `POST /retake` by a student with **no** attempt on an MCQ exam whose window has ended behaves as `take` (it creates the `ARCHIVED` first attempt). Without this, the current website's later-attempt button would break.
10. **Free exams add no recipients.** The bulk "students with access" queries used for publish, reminder and update notices and mail stay based on grants and roster rows. A free exam does not message every student.
