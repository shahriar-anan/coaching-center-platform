# Rules for every new requirement

These come from [../decisions.md](../decisions.md), [../plans/ui-delivery/README.md](../plans/ui-delivery/README.md) and [../plans/client-review-staging.md](../plans/client-review-staging.md). Where this file and those disagree, those win and this file is corrected.

## Scope

- Exam site only: the student website, the staff tool, and the exam API in `com.coachingcenter.api.exams`. Nothing about courses, books, the library, or course and book payments.
- Do not reopen a decision in `decisions.md`. If a requirement needs one changed, write it as a proposed change in the requirement file and ask.
- Reuse before building. Shared components live in `web/src/components/ui`, the shells in `web/src/components/shell`, the staff screens in `web/src/components/staff`. A requirement that needs a second copy of an existing table, dialog or form extracts the shared part instead.

## Roles and access

- Roles are Master Admin, System Admin, Instructor, Student. The one nav table is `web/src/components/shell/staff-nav.ts`; a role sees only the items it may open (absent, not disabled).
- Only the Master Admin publishes an exam and releases results. A System Admin edits but does not publish and does not evaluate. One instructor per module.
- Access is enforced in the API. A hidden button is not a permission.
- Every requirement states which roles see the change and what each may do.

## Look and copy

- Staff screens use the admin visual system (calm, dense, blue on grey, desktop first, usable on a phone). Student pages follow the client's mockup. Do not mix them.
- Every visible string goes in both message catalogs, `en` and `bn`, under `web/src/i18n/messages`. No hard-coded copy. Bangla digits in the Bangla UI come from the shared formatters (`useFormat`).
- Colors are tokens. No new hex values in components.
- Mobile first for students; staff pages must still work at phone width. Touch targets are at least 44 px (`min-h-11`).
- Every list has a loading, empty, error and filtered-empty state, using the shared `Skeleton`, `EmptyState` and `ErrorState`.
- List filters live in the URL (`useListParams`) so a page can be shared or reloaded.

## Code

- `web/AGENTS.md` applies: this is not the Next.js you know. Read the relevant guide in `web/node_modules/next/dist/docs/` before writing route or data code.
- Match the surrounding code: naming, comment density, idiom. Comments say why, not what.
- A schema change is a new Flyway version, never an edit of an applied one. Check the highest existing version in `api/src/main/resources/db/migration` and in [../plans/README.md](../plans/README.md) before naming the next.
- An API addition is added to [../current-api.md](../current-api.md) in the same change.
- Permissions, audit and error codes follow what the exams package already does.

## Checks

- API: name the `*IT` classes in the requirement file; they run against real PostgreSQL. No H2.
- Web: `npm run lint`, `npm run build`, and component tests with mocked `fetch`. Do not boot `api` for web tests. No new CI workflow.
- Run the checks that apply before calling a slice done and report the real output, including failures.
- The agent does not open a browser or run a device check. Shahriar does the browser pass, so each file ends with a short list of things for him to click.

## Safety

- Staging is sandbox only. Never print env values or secrets. Server writes are shown and wait for OK. See [../plans/vps-staging-deploy.md](../plans/vps-staging-deploy.md) hard stops.
- The agent does not push. Commits are made only when Shahriar asks, on `demo/client-review` unless he says otherwise, grouped API, web, docs.
- Seeded demo accounts and demo content are staging only and removable. A requirement that adds seed data says how it is removed.
- Do not touch the client's Master Admin account or the existing bKash/Nagad flows.

## Records

- Each finished requirement gets its status, date and commit in the register in [README.md](README.md).
- A decision Shahriar makes while working a requirement is written in that file's "Decisions" table the same day, with the date.
