# Marking browser by category (30 Oct 2026)

## The problem

The staff marking screens were built when every written exam sat inside a module (package). On staging all 12 written exams are in **no** package (`moduleId` is null in `GET /api/v1/live-exam-participation`). The only package, "Yearly Package", holds none of them. Exams belong to a category directly (`live_exams.category_id`).

What breaks, verified on staging on 30 Oct:

1. **Error box on "Mark" / "Open scripts".** [participation/page.tsx:199](../../../web/src/app/(staff)/staff/participation/page.tsx) builds `/staff/submissions?module=${row.moduleId}&exam=…`. Null fields are left out of the JSON, so the link reads `module=undefined`. The browser sends `moduleId=undefined` to `GET /api/v1/live-exam-submissions`. The UUID conversion fails, and the API answers **500 INTERNAL_ERROR**, because `ApiExceptionHandler` has no handler for a type mismatch. The screen then shows "Check your internet connection".
2. **Paper links carry `?m=undefined`.** These links are in [submission-browser.tsx](../../../web/src/components/staff/marking/submission-browser.tsx) at lines 204 and 332, and in [instructor-home.tsx](../../../web/src/components/staff/home/instructor-home.tsx) at lines 128 and 160. On the paper screen, the queue feed fails silently: Previous/Next, J/K and "save and next" are missing, and "back to list" goes to the broken URL again.
3. **The tree has no categories.** [buildBrowserTree](../../../web/src/components/staff/marking/marking-queue.ts) builds categories only from a module's `categoryPath`. Package-less exams all land under one module node with an empty label, and the real categories never show.
4. **Clicking an exam does nothing.** `selectionFor` returns null when the exam has no module.
5. **Choosing a category can't work.** It reads the feed once per module of the category (`useFeedByModules`), and package-less exams have no module.
6. **Instructor home lists exams by package.** In "My modules", `ModuleCard` filters rows by `moduleId`, so an instructor whose exams are in no package sees no exam cards.
7. **Teacher column.** It reads the module's instructor. It should read the exam's instructor (`live_exams.instructor_id`).

## Decisions (confirmed with Shahriar)

- The tree is **All scripts → Category (nested, any depth) → Exam**. Packages are **not** in the tree. The package can stay as a table column, display only, showing "—" when an exam has none.
- An instructor still sees only the exams assigned to them (`e.instructor_id`). Their tree is built from those exams' categories.
- Choosing a category shows the papers of every exam in that category **and its sub-categories**.

## Plan

### API (`api/`)

A1. **`GET /api/v1/live-exam-submissions` takes `categoryId`** (optional UUID).
- In `LiveExamSubmissionFeedService.list`, take the category plus all its descendants and add `AND e.categoryId IN (:categoryIds)`.
- Build the descendant set from `LiveExamStaffModuleService.categoriesById(categories)` (walk `parentId`). It is already loaded in that method.
- Keep `moduleId` and `examId` working as they do now. Add an overload or change the signature, and update every caller (`grep -rn "feed.list\|\.list(actor" api/src`).
- An unknown categoryId gives an empty page, not an error.

A2. **Each feed row carries `instructorName`**: the exam's instructor's name, read in one batch like `evaluatorNames` (from admin profiles by `e.instructorId`). It is null when there is none.

A3. **Each participation row carries `categoryPath`** (root first, `[{id,titleEn,titleBn}]`) beside the existing `categoryId` / `categoryTitleEn` / `categoryTitleBn`.
- In `LiveExamParticipationService.list`, load `categoriesById` once per request and map each row's `category_id` with `LiveExamStaffModuleService.pathOf`.
- Use the same `CategoryRef` record the feed uses. Inject `LiveExamCategoryRepository`.

A4. **A bad query parameter is a 400, not a 500.**
- In `ApiExceptionHandler`, add a handler for `MethodArgumentTypeMismatchException` (and `MissingServletRequestParameterException` if it isn't handled) → `400 VALIDATION_FAILED`, with a message naming the parameter.
- Order matters: it must sit above the catch-all `Exception` handler. Spring picks the most specific match, but verify.

A5. **Tests** (integration tests next to `ArchivedAndRetakeMarkingIT` / `LiveEvaluationIT`, same setup style):
- The feed with `categoryId` returns papers of exams in that category and in a child category, and not other categories.
- An instructor with `categoryId` still sees only their own exams.
- Feed rows carry `instructorName`.
- Participation rows carry a root-first `categoryPath` for a nested category.
- `moduleId=undefined` → 400 `VALIDATION_FAILED`.

### Web (`web/`)

Read `web/AGENTS.md` first. Match the surrounding style: comment density, the `t()` keys in **both** `en` and `bn` message files, and URL state through `useListParams`.

W1. **Types** in `lib/api/exam`:
- `ParticipationRow`: add `categoryId?: string | null` and `categoryPath: CategoryRef[]` (default `[]` when missing), and make `moduleId` optional/nullable.
- `FeedRow`: make `moduleId` / `moduleTitleEn` / `moduleTitleBn` optional/nullable, and add `instructorName?: string | null`.
- `FeedParams`: add `categoryId: string`, and send it in `feedUrl` (`queryString` already drops empty values).
- Delete `useFeedByModules` if nothing else uses it.

W2. **`buildBrowserTree` in `marking-queue.ts`**: rebuild it from the participation rows only.
- Signature: `(exams, pick, allLabel)`. Drop the `modules` argument.
- For each written exam, walk `row.categoryPath` root first, creating each `cat:<id>` node once, and push `exam:<id>` under the deepest one.
- Exams with an empty path go under a root-level "Uncategorised" node (new i18n key, en + bn), id `cat:none`.
- Each node's count is the `awaitingMarking` sum under it. The return value is `{ tree, examCategory: Map<examId, categoryId>, categoryExams: Map<categoryId, examId[]> }`. `categoryExams` includes descendants, for the tab counts.
- `Selection` becomes `{ cat, exam }`. `selectionFor("exam:x")` returns `{ cat: "", exam: x }`, always, with no lookup that can fail. `selectedNode` is updated to match.
- `groupByCategory` stays if the instructor home still needs it after W5. Otherwise remove it.

W3. **`submission-browser.tsx`**:
- Params: `cat`, `exam`, `status`, `sort`, `q`, `page`. Keep reading `module` only as a legacy deep link (W6): when set, filter the feed by `moduleId` and show a small removable "Package: <title>" line. There is no tree node for it.
- **One** `useFeed` call for every scope: `{ categoryId: cat === "none" ? "" : cat, examId: exam, moduleId: legacyModule, … }`, with real pagination for a category too. "Uncategorised" can only be reached through its exams, so it is fine for `cat:none` to only expand. Alternatively, filter client-side. Pick the simpler one and document it in a comment.
- Delete the merged-pages/`truncated` logic.
- `scopeExams` / tab counts use `categoryExams`.
- The Teacher column reads `row.instructorName ?? "—"`. Drop `useMarkingModules` from this file if nothing else needs it.
- The Package column shows `—` when the row has no module.
- Paper links: `/staff/submissions/${participantId}?exam=${examId}`. Keep the current list's `cat`/`status` so "back to list" returns to the same view: build the href with `URLSearchParams` and include `back` filters only when they're set.
- **No `undefined`/`null` may ever be put in a URL.** Build hrefs with `URLSearchParams` and skip empty values.

W4. **Paper screen** `submissions/[participantId]/page.tsx`:
- The queue is the feed for `?exam=` (all statuses, the browser's sort). That gives Previous/Next, J/K and "save and next".
- Also honour an old `?m=` when it is a real UUID. Ignore the literal strings `"undefined"` and `"null"`, and anything that isn't a UUID.
- `browser` (the back link) is `/staff/submissions?exam=…` (plus `cat` if it was passed). `toPaper` keeps the same query.

W5. **Instructor home** `instructor-home.tsx`:
- "My modules" becomes "My exams", grouped by category path. Use the exam's `categoryPath` joined with " › ", or "Uncategorised".
- One card per category, listing its written exams with their waiting counts. Each "open" link goes to `/staff/submissions?exam=<id>`.
- The "Start marking" button and recent-row links use `?exam=`.
- Keep the i18n keys tidy: rename or add keys in en + bn and remove the unused ones. Update `messages.test.ts` if it checks key parity.

W6. **Other links into the browser**:
- `participation/page.tsx:199` → `/staff/submissions?exam=${examId}` (keep `&status=AWAITING` for "Mark").
- `submissions/m/[moduleId]/page.tsx` keeps redirecting with `module=` (legacy, W3).
- `module-list.tsx:191` stays as it is (a real module id).
- Grep `web/src` for `submissions?` and `?m=` and fix any other spot that can put an empty id in a URL.

W7. **Error copy.** `ErrorState` keeps its default for real network failures. Where the browser and the paper screen show it, pass a message when the error is an `ApiError` with `status >= 400`:
- a 4xx: "This list could not be loaded. Reset the filters and try again."
- a 5xx: "The server had a problem. Try again in a moment."

Add the keys in en + bn. Use the same Bangla register the existing `staffMarking` bn strings use.

W8. **Tests** (vitest, `web/tests/ws9-mrk/` with its `fixtures.ts`):
- Update the `marking-queue.test.ts` tree tests: nested categories, an uncategorised exam, counts summed up the chain, and `selectionFor` on an exam with no module.
- `submission-browser.test.tsx`:
  - choosing a category calls the feed with `categoryId`;
  - choosing an exam calls it with `examId`;
  - no request URL ever contains `undefined` or `null`;
  - paper links use `?exam=`.
- `instructor-home.test.tsx`: exams with no package show under their category.
- `marking-screen.test.tsx`: the queue comes from `?exam=`, and `?m=undefined` is ignored.
- Make the fixtures use `moduleId: null` for at least some rows, since that's the real data.

## Checks before handing back

- API: `cd api && ./mvnw -q test -Dtest='<the new and touched ITs>'`, then `./mvnw -q verify` if time allows. ITs run against the **`coaching_center_gate`** database, **never `coaching_center_dev`**. Postgres 17 is running locally as a Windows service. Find how the test DB is configured (`src/test/resources`, env vars) before running anything.
- Web: `cd web && npx tsc --noEmit`, `npm run lint`, `npm test`. Everything must pass. Report any failure that was already there before your change separately, with its output.
- **No commit, no push, no deploy.** Leave the changes in the working tree on `demo/client-review`.

## Out of scope

- Moving exams into packages, or changing what a package is.
- The results page (`/staff/exams/[examId]/results`) and its release panel: they key on the exam and already work.
- The deadline-job lag between participation counts and the feed (a paper past its deadline counts as "awaiting" before the job posts it).

## Part 2: other failures in the same flow (added 30 Oct, after the first pass)

Scope: the exam evaluation system only. No new features; make what exists work correctly. Do this after items A1–A5 / W1–W8, in the same working tree.

P1. **Results data goes stale after marking.** `useInvalidateMarking` ([staff-marking.ts](../../../web/src/lib/api/exam/staff-marking.ts)) refreshes `staffMarking`, `staffPeople` and `staffHome`, but not `staffResults`. Saving a mark, releasing one paper and bulk release leave the results report and the release panel on their old counts for up to 60 s (default `staleTime`). Add `["staffResults"]` to that invalidation. Then drop the duplicate `staffResults` invalidation in `useSetReady` if it is now redundant. Test: after a save or a release, the `staffResults` queries are invalidated.

P2. **Tab counts and the list use different rules.** The counts come from `LiveExamParticipationService` (exams with `e.status = 'PUBLISHED'`, and a paper past its deadline counts as awaiting before the deadline job posts it). The list comes from `LiveExamSubmissionFeedService` (any exam status, only posted papers, and an inner join on `StudentProfile`).
- Give the feed the same exam-status rule as the counts (`e.status = :published`; check the constant name in `LiveExam`).
- Make the student join tolerate a missing profile if the data model allows it (left join, name falls back to an empty string). If it does not, leave it and say why in the report.
- Do **not** try to remove the up-to-one-minute lag from the deadline job. Add a one-line comment where the two rules meet.
- Test: a draft/archived exam's papers are not in the feed; published exams' are.

P3. **Save errors are misleading.** `saveErrorText` in [submission-form.tsx](../../../web/src/components/staff/marking/submission-form.tsx) maps `VALIDATION_FAILED` to the marks-range text and everything else to the generic error. The API sends specific codes: `MARKS_OUT_OF_RANGE` (400), `EVALUATION_NOT_ALLOWED` (409, a paper with no stored PDF or a paper not submitted), `ACCESS_DENIED` (403), `VALIDATION_FAILED` (400, also "a marked PDF is required the first time").
- `MARKS_OUT_OF_RANGE` → the marks-range text. `VALIDATION_FAILED` → a "check the marks and the marked PDF" text.
- `EVALUATION_NOT_ALLOWED` → "This paper cannot be marked (it has no answer PDF)." `ACCESS_DENIED` → "You cannot mark this paper."
- Anything else keeps the generic text. Add the keys in en + bn. Same fix for the ready and release actions if they share this function. Check `errorMessageKey` in `web/src/lib/api/errors.ts` first and reuse it where it fits.
- Test: each code shows its own text.

P4. **The 100-exam cap is silent.** The participation list is capped at `MAX_SIZE = 100` per request, and the browser reads one request of `FEED_PAGE_MAX`. Past 100 exams, exams vanish from the tree and the tab counts with no sign. Show the existing "truncated"-style warning when the participation response says `totalElements > size`. Do not add pagination.

Not to change: System Admin cannot mark (API and UI agree); an exam with no instructor is marked by the Master Admin only.

## Progress (as of 31 Oct 2026)

Nothing is committed. Everything below is in the working tree on `demo/client-review`.

| Item | State |
|---|---|
| Part 1: A1–A5 (API) and W1–W8 (web) | Done. Package-less exams are grouped by their own category, `categoryId` filter includes descendants, `?exam=` links work, bad `moduleId` returns 400 instead of 500. |
| Part 2: P1–P4 | Done. Results list refreshes after marking, feed uses the same published-exam rule as the counts, save errors show per-code text, the 100-exam cap shows a warning. |
| New tests | `MarkingBrowserIT`, `MarkingFeedRulesIT`, `web/tests/ws9-mrk/part2-*.test.tsx`, plus updated ws9-mrk and ws5 home tests. |
| Checks run by agents | API 669 tests, then 78 targeted tests, passed. Web 1082 tests passed. `tsc` clean. Lint: 3 errors and 3 warnings, all in files this work did not touch. |
| Full API suite on the final combined tree | **Not run.** Deferred by Shahriar until their other changes are in. Run it against `coaching_center_gate`, never `coaching_center_dev`. |
| Browser check | Left to Shahriar (no browser testing by Claude). |

### Dev environment (local only)

- `coaching_center_dev` was wiped and reseeded with the minimal dev seed: `DevSeed.java` and `DevSeedRunner.java`, switched on by `SEED_DEV=true` and `SEED_DEV_PASSWORD` in the gitignored `api/.env`. Dev profile only; refuses prod. `DemoContentSeed` (staging) is untouched apart from `storePdf` becoming package-private.
- Content is named for the BJS and Bar Council enrolment exams: two root categories (BJS, Bar Council Enrolment), each with one sub-category; six exams (open now, finished with two papers waiting, opens tomorrow, practice, package) and one paid package.
- Accounts: Master Admin from `MASTER_ADMIN_*` in `.env`; staff1/2, instructor1/2, student1/2 `@example.com` with the seed password. Student 1 holds a manual grant on the package.
- No IT was written for `DevSeed`; it was checked live against the dev database.

### Known gaps, not fixed

- Instructor home still caps at 100 exams without a warning.
- The paper screen's load error message is generic.
- Uploaded files from the old dev data remain on disk, unreferenced.
- Dev seed windows run to about 29 Nov 2026; after that, drop the schema and restart to reseed.
