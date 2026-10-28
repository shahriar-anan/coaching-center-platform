# Student page UI overhaul: decisions and plan (S0 to S3)

Recorded 29 October 2026 on `ui/nr01-ui`, and revised the same day after a review of the plan against the code. It builds on `steps.md`. Colours, fonts and spacing tokens don't change. Layout, navigation and flow do, following current LMS and ed-tech practice.

## 1. Research basis

The searches on LMS and ed-tech navigation returned mostly vendor documentation and generic guides. These findings were used:

- Virtual College's Enable LMS moved learner navigation from a sidebar to a top bar, because labelled items read more clearly than standalone icons (https://www.virtual-college.co.uk/resources/lms-update).
- Sidebars suit dashboards with many options, and top navigation suits simpler systems (https://grandavehousing.calpoly.edu/news/lms-dashboard-ui-design-best).
- Hybrid layouts put utilities such as notifications at the top (https://grandavehousing.calpoly.edu/news/lms-ui-a-guide-to).
- Student navigation is kept to the most important areas (https://grandavehousing.calpoly.edu/news/lms-dashboard-ui-design-best).
- Several LMS products collapse navigation into a menu on small screens (https://help.edly.io/docs/navigating-through-the-lms-0).

No documented convention turned up for "My exams" tabs or for favourites on exam platforms. Those decisions rest on the brief. Two points come from general knowledge, not from the searches:
- marketplace apps (Udemy, Coursera) use a top bar with "My learning", a wishlist and notifications;
- mobile apps use a bottom bar of four or five items.

## 2. Decisions

| Question | Decision | Reason |
|---|---|---|
| Q1 top bar or sidebar | **Top bar on desktop, bottom tab bar on phones.** No sidebar for students | Students have few destinations. The staff admin already has a sidebar. The phone bar already exists |
| Q2 links in the bar | **Desktop:** Home, MCQ, Written, My exams, Results, Routine. Right side: notices bell, language, theme, account menu | The brief asks for the bell and routine. The doors are the main entry points |
| Q3 "My exams" | **One page, two tabs:** Exams and Packages, both purchased or granted. Favourites is a separate page | One place for what the student owns |
| Q4 phone layout | **The existing five-item bottom bar:** Home, My exams, Results, Notices, Account. MCQ and Written are reached from Home, Routine from Home and Account | Five is the usual limit, and these five already work |
| Q6 category page | **Subcategory cards on top, the exam list below** | Subcategories are navigation, exams are content |
| Q7 nested subcategories | **The whole branch by default.** A switch shows only the category's own exams. **This needs an API change** (section 4, API1) | Exams created in a package sit in that package's leaf category, so a top-level category's own list is usually empty |
| Q11 several packages | **Every package shows** as a card on the exam detail | Brief |
| Q12 buy exam with packages | **"Buy this exam" first, the package cards below.** The button shows only when the exam has its own price | The exam's own price stays the primary action |
| Q13 no package | **Nothing shows** where packages would be | Brief |
| Q14 free exams | **"Free, start"** for a signed-in student, and **"Free, log in to start"** for a visitor | Free exams need no purchase |
| Q15 discounts | **The list price struck through, a percent badge, and "until 30 November"** when there is an end date | This matches the API's `pricing` object |
| Q16 access on the detail | **"Access until 28 November"** for an expiring grant. **This needs an API change** (API3) | Expiry is only in "my access" today |
| Q17 purchase sheet | **In scope**, including the bundle picker on the package page. It shows the effective price the order charges, with the list price struck through | The API charges the effective price |
| Q18 popular and favourites | **Step S3.** Popular on the home page and on cards, and a Popular marker on the exam detail. A favourite toggle on cards and on the detail. A Favourites page linked from the account menu | Brief |
| Visitors (new) | The browse flow (S2) works for visitors as well. Buy and favourite actions send them to login and then back to the page | The browse pages are public |
| Wording (new) | The student-facing copy says "package" instead of "module", in English and Bangla. URLs stay as they are | Decided in the UI discussion |

The favourites link goes in the account menu, between "My account" and "Notices", following the screenshot.

## 3. Working rules for this plan

- **One worker at a time, one sub-step group per session.** This avoids conflicts and test-lock waits.
- **Models:**
  - Haiku for layout and wiring.
  - Sonnet for the exam detail and the purchase logic (S2c), where purchase rules and access states interact.
  - The orchestrator reviews each step's diff before it's merged.
- **Tests:**
  - lint on the touched files;
  - unit tests only for logic that could break;
  - one build and one headless overflow check per step, as a text report at 320, 375 and 768 px, with no screenshots.
  - No end-to-end tests.
  - API sub-steps run their IT classes once.
- **Copy:** each sub-step adds its labels in English and Bangla. The agent drafts the Bangla, uses any terms Shahriar has given, and lists the new terms in the step's review note.
- **Markers:** where the web can't use an API field yet, the screen shows a neutral placeholder and the code carries a `// NR-01 marker:` comment naming the field. Each marker is listed in `markers.md`.
- **Review:** after each step the agent writes `progress/<step>-review.md`, pushes to `ui/nr01-ui`, and stops. The review note says what changed, which pages to open, which accounts to use, what to check, and any markers. Shahriar tests on the dev server and sends fixes. The next step starts only after his go.

## 4. Steps

### S0: review data and API additions (do first)

Without this, the dev server has nothing to show for S2 and S3: the demo seed has no discounted, free, popular or practice exams.

| Sub-step | Work | Files | Tests |
|---|---|---|---|
| S0.1 (API1) | `GET /api/v1/public/live-exam-categories/{id}/exams` gains `branch=true`, which includes exams in every subcategory. The default stays as today | `LiveExamPublicController`, `LiveExamPublicService`, a category repository query | One new IT class `CategoryBranchExamsIT` |
| S0.2 (API2) | `GET /api/v1/public/live-exams` gains `popular=true` | the same files | In the same IT class |
| S0.3 (API3) | The exam's `access` object gains `expiresAt` (null for lifetime), from `LiveExamAccess.state` | `StudentOwnAccessService` | In the same IT class |
| S0.4 | The demo seed gains the NR-01 cases, insert-only and idempotent: a discounted exam and package, a free exam, a popular exam and package, an exam in two packages, a standalone exam in a category, a student with an expiring grant, and a written exam with a question PDF | `DemoContentSeed.java`, `DemoContentSeedIT.java` | `DemoContentSeedIT` |
| S0.5 | Run S0's IT classes once. Restart the local API with the seed on, and list the review accounts in `progress/S0-review.md` | none | as listed |

### S1: navigation

| Sub-step | Work | Files | Tests |
|---|---|---|---|
| S1.1 | Desktop top bar with the Q2 links for signed-in students. Visitors keep their current bar | `components/shell/student-shell.tsx` | Unit test: the links show for students and not for visitors |
| S1.2 | The account menu gains Favourites, and keeps Routine | `student-shell.tsx` (`UserMenu`) | Covered by S1.1 |
| S1.3 | "My exams" page with Exams and Packages tabs. It reads `GET /api/v1/me/live-exam-access` and shows each row's status, "Access until", and an expired badge | `app/(site)/my-modules/`, a new my-exams component, `lib/api/exam/catalog.ts` types | Unit test: the tab split, expiry and expired states, and the empty states |
| S1.4 | The phone bar stays, with labels in the new wording | `student-shell.tsx` | Lint only |
| S1.5 | Student-facing "module" becomes "package", in English and Bangla | `i18n/messages/{en,bn}/catalog.ts`, `account.ts`, `common.ts` and the other student namespaces | Lint and the existing tests |
| S1.6 | Build, headless overflow check, review note | none | as listed |

### S2: browsing and purchase

| Group | Sub-step | Work | Files | Tests |
|---|---|---|---|---|
| S2a | S2.1 | Category page: subcategory cards on top, the exam list below, using `branch=true` with the own-exams switch. "Load more" paging. The doors use the same flow. A category with nothing shows "শীঘ্রই আসছে" (decision D-b) | `components/student/catalog/door-page.tsx`, a new category exam list, `catalog.ts` hooks | Unit test: the branch switch and the query keys |
| S2a | S2.2 | Exam row: title, date and time, type, status, price (with the list price struck through when discounted), package tags | `exam-row.tsx` | Unit test: price formatting for list, discount, free and no price |
| S2b | S2.3 | Exam detail: the buy-exam button, then every package card (Q11, Q12), and nothing when there are no packages (Q13) | `exam-page.tsx`, `included-list.tsx` | Unit test: the purchase option rules (own price, package count, already owned) |
| S2b | S2.4 | Package card on the exam detail: title, effective price, exam count, and a link to the unchanged package page | a new package card component | Covered by S2.3 |
| S2b | S2.5 | Free, discount and access states on the detail (Q14, Q15, Q16), using `pricing` and `access.expiresAt` | `exam-page.tsx`, `components/student/purchase/access.ts`, `types.ts` | Unit test: the free and access states |
| S2c | S2.6 | Purchase sheet and bundle picker: the effective price, the list price struck through, the package the item comes from, and bundle totals at effective prices | `components/student/purchase/purchase-sheet.tsx`, `components/student/catalog/bundle-selection.tsx` | Unit test: the amounts shown, including a bundle |
| S2c | S2.7 | Build, headless overflow check, review note | none | as listed |

### S3: popular and favourites

| Sub-step | Work | Files | Tests |
|---|---|---|---|
| S3.1 | Home page: a "Popular" section of exam cards with the popular badge (`popular=true`, from S0.2) | `components/student/home/home-page.tsx`, a popular section | Unit test: the empty state |
| S3.2 | A Popular marker on exam rows and the exam detail | `exam-row.tsx`, `exam-page.tsx` | Covered by S2.2 |
| S3.3 | A favourite toggle on exam rows, package cards and the exam detail. It uses `favourite` from the API and `PUT` and `DELETE /api/v1/me/favourites/...`. Visitors are sent to login | the same components, a new `use-favourites` hook | Unit test: the toggle, its optimistic state, and the rollback on error |
| S3.4 | Favourites page with Exams and Packages tabs and empty states, linked from the account menu | a new `app/(site)/favourites/` route and component | Unit test: the tabs and the empty states |
| S3.5 | Build, headless overflow check, review note | none | as listed |

## 5. Workers

| Step | Model | Notes |
|---|---|---|
| S0 | Haiku (high effort) | One API slice and the seed. The orchestrator reviews the queries and the seed before the merge |
| S1 | Haiku | Layout and copy |
| S2a, S2b | Haiku | Lists and cards |
| S2c | Sonnet | The purchase amounts and bundle totals |
| S3 | Haiku | Toggles and a page |

Each worker gets the brief template from `../nr-01-ui/README.md` and works only on the files listed for its step.

## 6. Order

S0, then S1, then S2 (S2a, S2b, S2c), then S3. Each step ends with its review note and a push, and waits for Shahriar's go. The full web check (lint, all unit tests, build) runs once at the end of each step. The API's full suite runs once, after S0.

## 7. Done means

- Every sub-step is done or listed as skipped with Shahriar's OK.
- The markers are listed in `markers.md`.
- Each step's review note exists, and Shahriar has signed off the step.
- The full web check is green after S3, and the API suite is green after S0.

## 8. Changes made in the review (29 October 2026)

1. **New S0.** It covers three small API changes and the review data. The category branch filter (API1) was missing: the category exam list returns only the category's own exams, so Q7's "whole branch" needs it. The seed has no NR-01 cases, so the dev-server review had nothing to show.
2. **API3 (`expiresAt`)** became an explicit sub-step. Before, it was only in a table.
3. **Visitors are covered.** Browse works without login, and buy and favourite send visitors to login.
4. **The student-facing "module" to "package" wording** became sub-step S1.5. It was decided earlier but missing from the steps.
5. **The bundle picker** joined the purchase work (S2.6). It also showed raw prices.
6. **"My exams"** now names its API (`/me/live-exam-access`) and shows expiry.
7. **Every step gets a build, an overflow check and a review note.** Before, only S1 had them.
8. **Workers, models and order are stated:** one worker at a time, and Sonnet only for the purchase amounts.
9. **Paging and empty states** are stated for the category list.
