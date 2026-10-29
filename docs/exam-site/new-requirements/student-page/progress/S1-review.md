# S1 review: navigation

Written 29 October 2026 on `ui/nr01-ui`, for Shahriar's full QA. Covers S1.1 to S1.6 of `plan.md` section 4.

## What changed

| Sub-step | Change | Commit |
|---|---|---|
| S1.1 | A top bar for signed-in students, from large width (1024 px) up: Home, MCQ, Written, My exams, Results and Routine, beside the logo, the notices bell, language, theme and the account menu. The header is wider for students from that width up (760 px below it, as before). The content column stays 760 px | `d7bd99f` |
| S1.2 | The account menu gains Favourites between My account and Notices. The link goes to `/favourites`, which is created in S3.4, so it is a 404 until then. Routine uses the same label as the top bar | `d7bd99f` |
| S1.3 | My exams (the route keeps `/my-modules`): two tabs, Exams and Packages. Each row shows the title, the type (exams), the access state (lifetime, "Access until" the end date, or Expired), and where the access came from. Live access first, expired after. Empty state for each tab. Reads `GET /api/v1/me/live-exam-access`, and the public reads for the titles | `57d15ef` |
| S1.4 | The phone bar keeps its five items (Home, My exams, Results, Notices, Account). Its labels were already the new wording, so there was no copy to change. It is now shown below 1024 px (it was below 768 px) | none (lint only) |
| S1.5 | "module" becomes "package" in the student copy, English and Bangla: account, catalog, home, landing, legal, purchase, result, and the purchase label in common. Staff screens keep their words. Key names and URLs are unchanged | `446942e` |
| S1.6 | Build, and the overflow check (below). This note | this commit |

### Decisions taken in this step

- **Top bar breakpoint: 1024 px, not 768 px.** At 768 px the logo, six links and the right-hand controls don't fit in one bar. Between 768 and 1023 px the phone bar stays. Say if you want the top bar from 768 px and the links in a smaller type.
- **Header width.** Signed-in students get a 1152 px header from 1024 px up, so the bar has room. Content stays 760 px.
- **Home section "My modules" is now "My exams".** It links to My exams, so it takes that name. The purchase page's "Go to package" and the "My exams" link take the same names.
- **Legal copy changed.** The terms page asks "What do I get when I buy a package?" and the answer changes to match. This is student-facing legal text: please check it.
- **Dead keys removed.** `catalog.myModules` keeps only the two door links, which My exams uses.

## Pages to open

Signed in as a student (accounts in `S0-review.md`):

- Any page, wide enough (1024 px or more): the top bar. Check the six links, the current page marked, and the account menu order (My account, Favourites, Notices, Routine, Log out).
- Any page below 1024 px: the bottom bar, five items.
- `/my-modules` (My exams): the Exams and Packages tabs. Student 4 (তানভীর হাসান) has two rows on the Exams tab: the written paper e62 with access until a date, and the expired paper e65 below it. The free exam e61 is not a grant, so it is not listed.
- Visitor (signed out): `/`, `/mcq`, `/written`. No student bar, and the login and sign-up pill as before.

## Tests

Run through the web helper (`npx vitest run`, under the heavy lock):

| Files | Tests | Result |
|---|---|---|
One run over these folders: `tests/student-nav` (new: the top bar, My exams, and the source labels moved from `tests/stu-lms/my-modules-access.test.tsx`), `tests/pur-stu`, `tests/pur-fnd`, `tests/landing`, `tests/stu-lms`, `tests/ws9`, `tests/ws9-aut` and `tests/ws0`. Result: 65 files, 399 tests, all passing. Five copy assertions in the purchase, access and staff preview tests moved to the new wording.

Lint (`eslint`) on every touched file: clean.

Removed with S1.3: `components/student/catalog/my-modules-page.tsx` and `tests/stu-lms/my-modules-access.test.tsx`. Its source-label tests live on in `tests/student-nav/access-source.test.tsx`.

## Build

`npm run build` in the worktree (under the heavy lock): exit 0.

## Headless overflow check

Headless Chrome, at 320, 375 and 768 px, reported as text only (`scratchpad/overflow.mjs`).

| Page | 320 px | 375 px | 768 px |
|---|---|---|---|
| `/` (visitor) | fits (9 boxes past the edge, all hidden or decorative) | fits (same) | **sideways scroll**: the login and sign-up pill runs past the right edge (page width 786 px, viewport 753 px) |
| `/mcq` (visitor) | fits | fits | fits |
| `/written` (visitor) | fits | fits | fits |
| `/login` | fits | fits | fits (the viewport is 753 px with the scrollbar) |

- The skip link ("মূল অংশে যান") is off-screen by design, so it is expected in every row.
- The `/` sideways scroll at 768 px is on the visitor header, which S1 does not change (the new bar is for students only). I have not confirmed it against the old code. Please check it on the dev server.

**Not checked:** the signed-in pages (the top bar, My exams, and the rest of the student pages). The overflow check cannot log in: the review password is not in any file I can read, and the seeded login in the main checkout's `api/.env` does not work against the review database. **To finish this check, I need the password for one review student** (or Shahriar can run the same check). Pages that were not checked are listed in the report.

## Bangla to confirm

| Bangla | English | Where |
|---|---|---|
| এমসিকিউ | MCQ | top bar |
| রিটেন | Written | top bar |
| রুটিন | Routine | top bar, account menu |
| পছন্দের তালিকা | Favourites | account menu |
| শিক্ষার্থী মেনু | Student menu (the bar's label) | top bar |
| আমার পরীক্ষা | My exams | page title, home section, purchase links |
| পরীক্ষা / প্যাকেজ | Exams / Packages (tabs) | My exams |
| {date} পর্যন্ত অ্যাক্সেস | Access until {date} | My exams row |
| সীমাহীন অ্যাক্সেস | Lifetime access | My exams row |
| মেয়াদ শেষ | Expired | My exams row |
| এখনো কোনো পরীক্ষা নেই / কেনা বা পাওয়া পরীক্ষা এখানে দেখা যাবে। | No exams yet / Exams you buy or are given will show here. | My exams, Exams tab |
| এখনো কোনো প্যাকেজ নেই / কেনা বা পাওয়া প্যাকেজ এখানে দেখা যাবে। | No packages yet / Packages you buy or are given will show here. | My exams, Packages tab |
| বিস্তারিত পাওয়া যাচ্ছে না | Details are not available | My exams, when a title does not load |
| প্যাকেজ (for "module") | Package | S1.5, across the student copy |

## Markers

None in S1. The web uses every field the API has for these screens.
