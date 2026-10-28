# S3 review: popular and favourites

Written 29 October 2026 on `ui/nr01-ui`, for Shahriar's full QA. Covers S3.1 to S3.5 of `plan.md` section 4. This note also closes Part B: the full web check is at the end.

## What changed

| Sub-step | Change | Commit |
|---|---|---|
| S3.1 | The signed-in home has a Popular section, between the upcoming and my-exams sections. It lists the popular exams still to come (`live-exams?upcoming=true&popular=true`) and the popular packages (`live-exam-modules?popular=true`). When nothing is popular it says "Nothing popular yet." instead of disappearing | `1889022` |
| S3.2 | A popular exam shows a Popular marker on its row and on its detail page | `1889022` |
| S3.3 | A favourite mark on exam rows, package cards and the exam detail. A student's mark changes at once, goes back with a message if the API refuses, and sends PUT or DELETE on `/api/v1/me/favourites/exams/{id}` or `/packages/{id}`. A visitor is sent to log in, then back to the page. Staff see no mark. The row's title is the only link, so the mark never opens the row | `1889022` |
| S3.4 | The Favourites page (`/favourites`, linked from the account menu) with Exams and Packages tabs and an empty state for each | `17a2a18` |
| S3.5 | Build and headless overflow check (below), this note | this commit |

Also in Part B's final check, three copy assertions in `tests/ws3` that S1.5 had changed were updated (commit `5c957cf`). The earlier subset runs had not covered that folder; the full suite did.

### Decisions taken in this step

- **Popular exams need `upcoming=true`.** The API only serves the upcoming list, so the home section asks for `upcoming=true&popular=true`. Popular means the exam's own flag; an exam that is only in a popular package does not count.
- **Popular on the home page is the signed-in home only.** The visitor home is the landing page, which is unchanged.
- **Favourites on a row sit above the title link.** The row is a box with the title as a stretched link, and the mark sits above it. This keeps the mark out of the link, which HTML does not allow.
- **Package page exam rows have no favourite mark.** The plan says "exam rows", and the category list, the home section, the favourites page and the exam detail all have one. The package page's own exam list still uses the older row, so it has no mark. Say if you want it added there too.
- **Optimistic mark, rolled back on error.** The mark follows the tap at once. If the API refuses, it goes back and a short message appears under the mark. The favourites list is asked again after each change.

## Pages to open

Signed in as a student (accounts in `S0-review.md`; the password is in your own notes):

- Home: the Popular section between "Coming up" and "My exams". The popular exam from the S0 seed (`…0063`, "Popular MCQ") and the popular package (`…0008`) should show, each with a Popular marker.
- Any exam row (MCQ or Written list, the category page): tap the star. It fills, and it stays filled after a reload.
- The exam detail: the star under "Copy link", and the Popular marker by the type.
- Account menu → Favourites: the Exams and Packages tabs, and the empty states for a student with no favourites.

Visitor: the star is a link to log in. After logging in you come back to the same page.

## Tests

Full web suite, run under the heavy lock: **125 files, 753 tests, all passing.** That includes the new files in `tests/student-buy` (seven files, 44 tests for S3, and the S2 and S1 tests in the same folder).

New in S3: `tests/student-buy/favourites.test.tsx` (the toggle on and off, the optimistic change, the rollback with a message, the visitor's login link, no mark for staff, the URLs) and `tests/student-buy/popular-and-favourites.test.tsx` (the Popular section's empty state and its rows, the popular marker, and the Favourites page's tabs and empty states).

## Lint and type check

- `npm run lint`: no errors. Two warnings in files outside this work (`authorized-file.tsx`, `book-details-form.tsx`), both about `<img>`.
- `tsc --noEmit`: clean.

## Build

`npm run build`, run once at the end of S3 (exit 0), and once more as the final check (exit 0). The dev server was stopped for each build and restarted afterwards.

## Headless overflow check (S3.5)

Visitor pages at 320, 375 and 768 px, text only:

| Page | 320 px | 375 px | 768 px |
|---|---|---|---|
| `/mcq` | fits | fits | fits |
| `/mcq/c/…0002` (category, with the new exam list) | fits | fits | fits |
| `/exam/…0063` (popular exam, with the Popular marker) | fits | fits | fits |
| `/mcq/m/…0008` (popular package) | fits | fits | fits |
| `/favourites` | visitor is sent to log in, so this measured the login page; fits | fits | fits |

No page scrolls sideways. The boxes past the edge are the same decorative spans as before, and at 320 px the "বিবরণ" button on the package page, which its tab strip clips. Please check the star and the Popular marker on a phone.

**Not checked:** the signed-in pages (the stars' filled state, the Popular section on the home, and the Favourites page), because the review password is still not available here. See S1-review.md.

## Bangla to confirm

| Bangla | English | Where |
|---|---|---|
| জনপ্রিয় | Popular (the badge and the section title) | Exam rows, package cards, home |
| এখনো জনপ্রিয় কিছু নেই। | Nothing popular yet. | Home, empty |
| পছন্দের তালিকা | Favourite (the star's label) and Favourites (the page title) | Star, account menu, page |
| সংরক্ষণ হয়নি। আবার চেষ্টা করুন। | Could not save. Try again. | Star, on error |
| পরীক্ষা / প্যাকেজ | Exams / Packages (tabs) | Favourites page |
| এখনো কোনো পছন্দের পরীক্ষা নেই / কোনো পরীক্ষার তারা চিহ্নে চাপ দিলে তা এখানে থাকবে। | No favourite exams yet / Tap the star on an exam to keep it here. | Favourites, Exams tab, empty |
| এখনো কোনো পছন্দের প্যাকেজ নেই / কোনো প্যাকেজের তারা চিহ্নে চাপ দিলে তা এখানে থাকবে। | No favourite packages yet / Tap the star on a package to keep it here. | Favourites, Packages tab, empty |

## Markers

None in S3. The web uses `favourite`, `popular` and the favourites endpoints the API now has.

## Part B summary

- S1 (navigation): S1.1 to S1.6 done. Review note `S1-review.md`.
- S2 (browsing and purchase): S2.1 to S2.7 done. Review note `S2-review.md`.
- S3 (popular and favourites): S3.1 to S3.5 done. This note.
- Known gaps, for your decision: the package page's exam rows have no favourite mark; the signed-in pages (see S1-review.md) were not checked headlessly.
