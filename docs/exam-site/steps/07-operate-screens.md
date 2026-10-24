# Step 7 — Operate screens

Basic website screens so a Master Admin and an instructor can run an exam without hand-written requests. Catalog authoring comes after a student can already sit an exam that exists in the API.

## Outcome

- An instructor sees assigned modules only, opens a written submission, downloads the answer PDF, and saves marks, an optional comment, and a marked PDF. The screen shows that the save is a draft.
- The Master Admin reviews that draft and publishes it. A System Admin does not get the publish action.
- The Master Admin sees a small participation count per exam: sitting, submitted, failed to submit, and written drafts waiting for publish.

## Bind these

- These screens use the step 1 API. They do not compute pass or fail in the browser.
- One instructor per module stays. Do not add per-exam assignment.
- The instructor does not see a publish control. The student path is step 6, not this shell.
- Put the screens in the existing admin shell in `web/src/app/(admin)/`, behind the current role gate.
- Counts are a read of participants that already exist. They are not a new analytics product.
- Same web checks as step 6: lint, build, component tests with mocked `fetch`. Shahriar does the browser pass.

## Where the code is

- Evaluation API: the table in [current-api.md](../current-api.md). Publish is added by step 1.
- Admin shell: `web/src/components/admin-shell.tsx`.
- Role permissions today: instructor has `LIVE_EXAM_EVALUATE` and no catalog manage permission. Master Admin has both.

## Plan must name

- Routes for the instructor list, the draft form, and the Master Admin publish action.
- The count payload and which role can read it.
- Component tests: instructor save shows draft, System Admin has no publish control, counts render from the payload.

## Later, still basic

Category, module, and exam create, the language flag, the question PDF upload, and the roster mark. Plan these only after the sitting and publish screens exist. Do not pull them forward to block step 6.

## Out of this step

The public shop, checkout, and the student inbox. Course and book payments, including manual bKash/Nagad verification.
