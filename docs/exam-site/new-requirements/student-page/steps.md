# Student page UI overhaul: steps and sub-steps

Recorded 29 October 2026 on `ui/nr01-ui`. This is the student-side part of the UI plan. It sits beside `../nr-01-ui/conversation-decisions.md`, which holds the written flow and the staff screens. No theme changes: colours, fonts and spacing tokens stay as they are. Layout, navigation and flow change, and the look follows familiar LMS and ed-tech patterns.

Status words: **Decided** (Shahriar agreed), **Proposed** (recommended, awaiting answer), **Open** (question not yet answered).

## Step S1: navigation bar for a signed-in student

Current state (from the screenshot and `components/shell/student-shell.tsx`): the logo and name sit at the left. Right side: notices bell with an unread count, language, theme, and the name opening a menu with Home, My account, Notices, Exam routine, Log out. Results and My modules exist as routes but are not in the bar.

| Sub-step | What changes | Status |
|---|---|---|
| S1.1 | Bar shows the main links: Home, MCQ, Written, My exams, Results, Routine. Notices stays as the bell. Account stays in the menu | Proposed |
| S1.2 | "My exams" becomes one page for purchased exams and packages, with tabs | Proposed (question Q3) |
| S1.3 | Small screens get a bottom tab bar for the main links, and the menu keeps the rest | Proposed (question Q6) |
| S1.4 | The staff "Admin panel" link stays in the menu for staff only | Decided (existing behaviour) |
| S1.5 | Logged-out visitors keep the Login and Sign up control | Decided (existing behaviour) |

## Step S2: browsing an exam from category to detail

Current flow (from `door-page.tsx`, `module-page.tsx`, `exam-page.tsx`): door (MCQ or Written), then category tree, then a **package**, then the exams in it, then the exam. Exams that belong to no package cannot be reached this way.

Target flow: door, then category and subcategories, then **all exams in that category**, then the exam detail. The exam detail shows every package the exam is in, with its price and exam count. Clicking a package opens the package screen as it is now, which does not change.

| Sub-step | What changes | Status |
|---|---|---|
| S2.1 | The door's category page lists subcategories as cards, then the exams directly in that category below them | Proposed (question Q9) |
| S2.2 | Each exam row shows title, date and time, type, status, and price (struck through when discounted) | Proposed (question Q10) |
| S2.3 | The exam detail page shows all purchase options, the same options that the package and exam screens show today | Decided (Shahriar) |
| S2.4 | Under the exam, a "Packages" section lists each package the exam is in: title, price, number of exams, and a link | Decided (Shahriar) |
| S2.5 | An exam in no package shows only its own purchase options | Proposed (question Q13) |
| S2.6 | The package screen is unchanged | Decided (Shahriar) |
| S2.7 | The discount, free and access states show on the exam detail (see questions Q14 to Q16) | Open |

## What this needs from the API

- The public exam list already returns every published exam in a category, with a `packages` list on each row (`GET /api/v1/public/live-exam-categories/{id}/exams`, added in NR-01 B2).
- The exam detail returns `pricing` (list price, discount, effective price), `packages` and the student's `access`. The web does not read `pricing` or `packages` yet.
- Access expiry reaches the student through "my access" only, not on the exam detail (see the UI decisions, section 10).

## Questions for Shahriar

**Navigation**
- **Q1.** Top bar with links beside the logo (S1.1), or a left sidebar like many LMS platforms?
- **Q2.** Which links are in the bar: Home, MCQ, Written, My exams, Results, Routine? Should Routine go in the bar or stay in the menu?
- **Q3.** One "My exams" page with tabs for Exams and Packages, or two separate pages?
- **Q4.** On a phone: a bottom tab bar (S1.3), or keep a menu button only?

**Browsing**
- **Q5.** Should the MCQ door follow the same flow (categories, then all exams, then detail)? My assumption is yes.
- **Q6.** On a category page, should subcategories and exams both appear, or should subcategories open first and exams appear only inside the leaf category? My recommendation: subcategories as cards on top, exams below.
- **Q7.** Should the exam list include exams in nested subcategories (the whole branch), or only the exams directly in the category? My recommendation: direct exams, with a switch to show the whole branch.
- **Q8.** Filters and sort on the list: which ones? My recommendation: sort by date (soonest first), by price, and a popular-first toggle. Search within the category as well?
- **Q9.** Confirm the target flow: door, then category and subcategories, then all exams, then exam detail.
- **Q10.** Exam row details: what should show on each row? My recommendation: title, date and time, type, status pill, price (with a struck list price when discounted), and the package names as small tags.

**Exam detail and purchase**
- **Q11.** When an exam is in several packages, should every package appear as a card, or only the cheapest? My recommendation: every package.
- **Q12.** Should the exam detail show a "Buy this exam" button alongside the package cards, even when the exam has its own price? My recommendation: yes, with the package cards below it.
- **Q13.** For an exam in no package, or with no price of its own: should the page say "Available in a package" and show package cards, or show a message only?
- **Q14.** Free exams: should a signed-in student see "Free, start" on the detail page? My recommendation: yes.
- **Q15.** Discounts: list price struck through, the percentage badge, and "until 30 November" when the discount has an end date. Confirm?
- **Q16.** Access on the exam detail: show "Access until 28 November" for a student who holds the exam? Expiry is not on the exam detail today, so the API needs a small change. Is that acceptable?
- **Q17.** Is the purchase sheet (the checkout dialog) part of these two sub-steps, or a later step? It currently shows the raw price, while the order charges the discounted one.

**Scope**
- **Q18.** Popular section and favourites: in these sub-steps, or later with the student tabs?

## Next

After Shahriar answers Q1 to Q18, the decisions go into this file and the sub-steps are final. Then the plan for S1 and S2 is written, and the work starts.
