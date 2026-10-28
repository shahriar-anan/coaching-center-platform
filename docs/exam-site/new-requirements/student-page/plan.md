# Student page UI overhaul: decisions and plan (S1 to S3)

Recorded 29 October 2026 on `ui/nr01-ui`. Builds on `steps.md`. Colours, fonts and spacing tokens do not change. Layout, navigation and flow do change, following current LMS and ed-tech practice.

## 1. Research basis

Searches on LMS and ed-tech navigation returned mostly vendor documentation and generic guides. Findings used:

- Virtual College's Enable LMS moved learner navigation from a sidebar to a top bar, because labelled items read more clearly than icons alone (https://www.virtual-college.co.uk/resources/lms-update).
- Sidebars suit dashboards with many options; top navigation suits simpler systems (https://grandavehousing.calpoly.edu/news/lms-dashboard-ui-design-best).
- Hybrid layouts put utilities such as notifications at the top and main navigation to the side (https://grandavehousing.calpoly.edu/news/lms-ui-a-guide-to). Here utilities sit top-right and the main links sit in the top bar.
- Vendor documentation keeps student navigation to the most important areas: courses, dashboard, messages, profile (https://grandavehousing.calpoly.edu/news/lms-dashboard-ui-design-best).
- Several LMS products collapse navigation into a menu on small screens (https://help.edly.io/docs/navigating-through-the-lms-0).
- The searches found no documented convention for "My exams" tabs or for favourites on exam platforms. Those decisions rest on the product brief, not on a source.

Points from general knowledge, not confirmed by the searches: marketplace apps (Udemy, Coursera) use a top bar with "My learning", a wishlist and notifications, and mobile apps use a bottom bar of four or five items. The mobile bar already exists in this codebase.

## 2. Decisions

| Question | Decision | Reason |
|---|---|---|
| Q1 top bar or sidebar | **Top bar on desktop, bottom tab bar on phones.** No sidebar for students | Students have few destinations. A sidebar suits the staff admin, which already has one. The mobile bar exists |
| Q2 links in the bar | **Desktop bar:** Home, MCQ, Written, My exams, Results, Routine. Right side: notices bell, language, theme, avatar menu | The bell and routine are requested. The doors are the main entry points |
| Q3 "My exams" | **One page with tabs:** Exams (purchased or granted, with status) and Packages (purchased or granted). Favourites is a separate page | One place for what the student owns, as in marketplace "My learning" pages |
| Q4 phone layout | **Bottom bar:** Home, My exams, Results, Notices, Account (the existing five). MCQ and Written are reached from Home. Routine opens from Home and Account | Five items is the usual limit. The current five already work |
| Q6 category page | **Subcategories as cards on top, all exams below** | Matches the brief. Subcategories are navigation; exams are content |
| Q7 nested subcategories | **The whole branch.** A switch under the list shows only the category's own exams | The brief asks for all exams under a category |
| Q11 several packages | **Every package shows**, as a card on the exam detail | Brief |
| Q12 buy exam with packages | **"Buy this exam" first, package cards below.** Shown only when the exam has a price | The exam's own price stays the primary action |
| Q13 no package | **Nothing shows** where packages would be. The exam's own purchase options still show | Brief |
| Q14 free exams | **"Free, start"** for a signed-in student | Free exams need no purchase |
| Q15 discounts | **List price struck through, a percent badge, "until 30 November"** when an end date exists | Matches the API's `pricing` object |
| Q16 access on the detail | **"Access until 28 November"** for an expiring grant. Needs a small API change (section 4) | Expiry is currently only in "my access" |
| Q17 purchase sheet | **In scope.** Shows the effective price the order charges, the list price struck through, and the package it comes from | The API charges the effective price, so the sheet must say so |
| Q18 popular and favourites | **Sub-step S3.** Popular on the home page and on cards; a Popular marker on the exam detail; favourite toggles on cards and the detail; a Favourites page linked from the account menu | Brief |

**The favourites link.** The brief says a segment of the screenshot holds it. That segment is the account menu, so Favourites is added there, between "My account" and "Notices".

## 3. Steps

Each sub-step lists its files, the API it uses, and its tests. Tests follow the agreed rule: lint on touched files, unit tests for logic that could break, and a build once per step. No end-to-end tests.

### S1: navigation

| Sub-step | Work | Files | Tests |
|---|---|---|---|
| S1.1 | Desktop top bar with the Q2 links for signed-in students. Visitors keep their current bar | `components/shell/student-shell.tsx` | Unit test: the links show for students, not for visitors |
| S1.2 | Account menu gains Favourites. Routine stays in the menu | `student-shell.tsx`, `UserMenu` | Covered by S1.1's test |
| S1.3 | "My exams" page with Exams and Packages tabs. Keeps the current page's data | `app/(site)/my-modules/`, a new `my-exams` component | Unit test: tab split and empty states |
| S1.4 | Phone bar kept. Labels follow the new wording | `student-shell.tsx` | Lint only |
| S1.5 | Build, and a headless check that nothing overflows at 320, 375 and 768 px (text report only, no screenshots) | none | The headless check |

### S2: browsing

| Sub-step | Work | Files | Tests |
|---|---|---|---|
| S2.1 | Category page: subcategory cards on top, exam list below, with the branch switch (Q6, Q7). Doors use the same flow | `components/student/catalog/door-page.tsx`, a new category exam list | Unit test: the branch switch's query |
| S2.2 | Exam row: title, date and time, type, status, price (list price struck through when discounted), package tags | `exam-row.tsx` | Unit test: price formatting (list, discount, free, no price) |
| S2.3 | Exam detail: buy-exam button, then every package card (Q11, Q12). Nothing where packages would be (Q13) | `exam-page.tsx`, `included-list.tsx` | Unit test: purchase option rules (price, package count, owned) |
| S2.4 | Package cards on the exam detail: title, price, exam count, link to the unchanged package screen | `exam-page.tsx`, a new package card | Covered by S2.3 |
| S2.5 | Free and access states on the detail (Q14, Q15, Q16) | `exam-page.tsx`, `access.ts` | Unit test: access and free states |
| S2.6 | Purchase sheet: effective price, list price struck through, the package it comes from (Q17) | `components/student/purchase/purchase-sheet.tsx` | Unit test: the amount shown |

### S3: popular and favourites

| Sub-step | Work | Files | Tests |
|---|---|---|---|
| S3.1 | API: `GET /api/v1/public/live-exams` gains a `popular` filter, as the category exam list already has | `LiveExamPublicController.java`, `LiveExamPublicService.java`, one IT test | The IT test (Maven, once) |
| S3.2 | Home page: a "Popular" section with exam cards and a popular badge | `components/student/home/home-page.tsx`, a popular section | Unit test: the section's empty state |
| S3.3 | Popular marker on exam rows and the exam detail | `exam-row.tsx`, `exam-page.tsx` | Covered by S2.2's test |
| S3.4 | Favourite toggle on exam rows, package cards and the exam detail, using `favourite` from the API and `PUT`/`DELETE /api/v1/me/favourites` | the same components, a new `use-favourites` hook | Unit test: toggle, optimistic state, rollback on error |
| S3.5 | Favourites page with Exams and Packages tabs and empty states. Linked from the account menu (S1.2) | a new `app/(site)/favourites/` route and component | Unit test: tabs and empty states |

## 4. API changes and web markers

| Need | Status | Action |
|---|---|---|
| Popular filter on the public exam list (S3.1) | Missing | Small API change, one IT test |
| Access expiry on the exam detail (Q16) | Missing from the exam's `access` object | Small API change: an `expiresAt` field. Until it exists, the web shows "Access until" only where the "my access" list provides it |
| Effective price, discount, list price (Q15, Q17) | In the API (`pricing`, `packages`), not read by the web | Web change only |
| Favourite flag on rows and the detail (S3.4) | In the API for students | Web change only |

**Markers.** Where the web can't yet use an API field, the code shows a visible placeholder and a `// NR-01 marker:` comment naming the field and this file. The markers are listed in `markers.md` at the end of the work, so they can be removed when the field is read.

## 5. Order and review

1. S1 first, because it sets the navigation everything else uses. Shahriar reviews S1 on the dev server and sends fixes.
2. S2 next, after S1 is merged into `ui/nr01-ui`.
3. S3 last. S3.1 is the only API slice in this plan. Its IT test runs once.
4. After each step the agent pushes to `ui/nr01-ui`, and Shahriar merges.
5. The full web check (lint, unit tests, build) runs once after S1, once after S2 and once after S3.

## 6. Open points for Shahriar

1. Confirm that Favourites goes in the account menu.
2. Confirm the two small API changes: the popular filter (S3.1) and `expiresAt` on the exam's access object (Q16).
3. Confirm that the purchase sheet is part of S2 (Q17), as decided above.
