# Prompt: plan the QA Segment 1 changes

You are planning, not building. Read the QA report and produce an implementation plan for every Segment 1 finding. Do not change application code, the database, config files or any deployed environment.

## Read first

1. `docs/exam-site/qa/qa-report.md`. This is your source of truth: the environment baseline, the index of findings, BUG-001 to BUG-006, REQ-001 to REQ-012, the Segment 1 results table and the observations.
2. `docs/exam-site/decisions.md`, section "Landing page and the client mockup": the old site (symamlaw.com) styles only the landing page `/`; the client mockup styles every other student page. Do not break that split.
3. `docs/exam-site/plans/ui-delivery/landing-page-plan.md`: it already describes the old-site category section (rows 6 and 7).
4. `docs/exam-site/client-brief.md`, `docs/exam-site/ui/client-mockup.html`, and `docs/exam-site/todays-goals.md` for scope and agreed behaviour.
5. The code each finding points to. Start from the files named in the report and follow them outward (web under `web/src`, API under `api/src/main`). Verify every file, line and claim in the report against the current code before you rely on it; the report was written from a dev build that is ahead of staging.

## What to produce

One plan file: `docs/exam-site/plans/ui-delivery/qa-segment-1-plan.md`. For every item (BUG-001 to BUG-006, REQ-001 to REQ-012) include:

- **Understanding**: the problem restated in your own words, and the current behaviour you confirmed in the code (file and line).
- **Approach**: the change you recommend, the files likely to change, and any API or data change. Give one recommendation. Mention an alternative only if the choice genuinely belongs to the client.
- **Edge cases and risks**: what could break, what else uses the same component or string.
- **Tests**: unit or component tests to add or change, and the manual checks from the QA report to re-run (PUB-xx ids).
- **Size**: S, M or L, with one line of reasoning.
- **Depends on / blocks**: other items, assets or client answers it needs.

Then add these sections:

1. **Grouping and order**: group the items into workstreams that can be done by separate agents without clashing (for example: brand and copy; landing page sections; footer and legal pages; browse pages and filters; error handling and session; results system). Give a build order and say which groups can run in parallel and which touch the same files.
2. **Shared decisions**: choices that affect several items and must be made once. At least: the type-first flow for exam type, then category, then module, then exam (BUG-003 and REQ-003); the "Symam Law" naming in both languages (REQ-001); how to treat Archived, Live and practice exams (REQ-007, REQ-009, REQ-003); the term used for "page" in Bangla (BUG-006, REQ-011).
3. **Questions for the client or the tester**: one numbered list, each question short, with your recommended default so work can proceed if there is no answer. Include the open questions already listed inside REQ-007 and REQ-003, the Bangla form of the brand name, the dropdown versus chooser page for BUG-003, and real social and app-store URLs for REQ-004.
4. **Assets needed**: footer icons, the payment-methods image and the old-site legal text (REQ-004), the old-site category section reference (REQ-003). Say where each can be taken from.
5. **Release notes**: what needs a Bangla and English review, and what should be checked on staging and not only on dev.

## Rules for specific items

- **BUG-001 (results page)**: do not plan a fix yet. First write an analysis of how the result system works: participations, statuses, marking, release of results and answers, the API endpoints and the page that consumes them. State why the page shows nothing and list what a correct "every exam the student has taken, any status" view must include. Then plan the fix from that analysis. Link to Segments 7 and 8 as related, untested areas.
- **BUG-002 (401 on refresh)**: do not weaken the refresh endpoint's security. Take care of the admin-area session behaviour (the web build API base must stay empty; an absolute base once logged admins out on refresh).
- **REQ-004 (legal pages)**: the old-site text must be copied exactly. Plan how to fetch it read-only (the old-site SSH access is documented in project memory and the docs) and how to verify the copy word for word. Do not paraphrase.
- **REQ-010 and REQ-011** are deferred. Plan them at outline level only (scope, rough size, what to decide first).
- Keep the Bangla and English message files in step (`web/src/i18n/messages/bn` and `en`).
- Follow the existing code style, naming and comment density in each area.

## Ground rules

- Read-only on the database, the old site, the staging servers and git history. No deploys, no commits, no migrations.
- Do not start implementation. If you find a defect or gap that is not in the report, add it to a short "Found while planning" list at the end of the plan, with file and line, and do not edit the QA report.
- If the report and the code disagree, say so and follow the code, noting the difference.
- Be concrete. Name files, components, endpoints and strings. Avoid general advice.

## Finish with

A short summary in your reply: the number of items planned, the workstreams and their order, the top five risks, and the questions that block work. Give the path of the plan file.
