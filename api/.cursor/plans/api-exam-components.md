# api-exam-components

Stage plan for Phase 3 Stage 3. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Sections 4.3, 9, and 11.2, and Stage 3. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-components.md](../tests/api-exam-components.md). The stage rule is [api-exam-components.mdc](../rules/api-exam-components.mdc).

Start only after `api-exam-questions` exits. Do not start `api-exam-access` until this stage exits.

Configure the shells from Stage 2. Do not start attempts, accept uploads, publish results, or compute a student's obtained score. The combined figure in this stage is the paper's maximum, not a student's result.

When the slice's checks finish, write `api/.cursor/progress/api-exam-components/slice-1-component-config.md`.

## Slice 1 — Component configuration and combined maximum

Flyway `V12__exam_component_config.sql`. Add nullable `pass_percent` integer on `exam_nodes`. It is set only on a `PAPER`. A section rejects it with `400`, code `VALIDATION_FAILED`. Add to `paper_components`:

- `description_en` and `description_bn`, nullable until configured.
- MCQ: `starts_at`, `ends_at`, `duration_seconds`, `immediate_results` boolean default false, `scheduled` boolean default false.
- Written: `deadline_at`.

`PATCH /api/v1/exam-components/{componentId}` under `EXAM_MANAGE`.

MCQ body: both descriptions, `startsAt`, `endsAt`, `durationSeconds`, `immediateResults`. `endsAt` must be after `startsAt`. `durationSeconds` must be greater than zero. A written field on an MCQ component returns `400`, code `VALIDATION_FAILED`.

Written body: both descriptions and `deadlineAt`. An MCQ field on a written component returns `400`, code `VALIDATION_FAILED`.

`PATCH /api/v1/exam-papers/{paperId}` under `EXAM_MANAGE` sets `passPercent`, a whole number from 0 through 100 (Section 11.4). A value outside that range returns `400`, code `VALIDATION_FAILED`.

`POST /api/v1/exam-components/{componentId}/schedule` under `EXAM_MANAGE` sets `scheduled` true on an MCQ component whose descriptions and window are already stored. Scheduling a written component, or an MCQ component that is missing either description, returns `400`, code `VALIDATION_FAILED`. Scheduling, and saving a written `deadlineAt`, returns `409`, code `PASS_MARK_REQUIRED`, when the paper's `passPercent` is null.

Admin `GET` returns the stored fields. Lifecycle on an MCQ component is derived from the server clock, not stored as a manually advanced status:

- `DRAFT` when `scheduled` is false.
- `SCHEDULED` when scheduled and now is before `startsAt`.
- `ACTIVE` when scheduled and now is at or after `startsAt` and before `endsAt`.
- `ENDED` when scheduled and now is at or after `endsAt`.

Do not add an admin route that sets `ACTIVE` or `ENDED`. The derived lifecycle does not read the module's publish status (Section 9). A draft or archived module does not stop the clock. Results-published is the next results stage. `immediateResults` defaults to false and only records the later visibility choice.

Scheduling an MCQ component does not require a question to be attached yet. An admin may set the window and add questions afterward. Starting an attempt with no questions is rejected in the attempt stage. The same split applies to a written deadline and a later upload.

A paper `GET` includes `passPercent`. It includes `combinedMaxMarks` when both component rows exist: the sum of marks on questions attached to the MCQ component plus the sum of marks on questions attached to the written component. A paper with one component omits `combinedMaxMarks`. The value is computed. Do not store a weight column.

The module root descriptions and the two component descriptions are three separate pairs. Patching one does not change the others.

## Stage exit

A paper can be configured with a scheduled MCQ component, a deadline-bound written component, or both. Each has its own bilingual rules text. The paper has a pass percent. A combined maximum is available when both components exist.
