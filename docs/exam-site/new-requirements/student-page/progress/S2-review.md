# S2 review: browsing and purchase

Written 29 October 2026 on `ui/nr01-ui`, for Shahriar's full QA. Covers S2.1 to S2.7 of `plan.md` section 4.

## What changed

| Sub-step | Change | Commit |
|---|---|---|
| S2.1 | The category page lists its subcategories as cards, then its exams. The exams come from the category exam list, with `branch=true` by default; a switch shows only the category's own exams. Load more pages twenty at a time. A category with nothing in it and no subcategory shows the coming-soon note (D-b). Packages no longer sit on the category page; the exam page lists them | `b0101b9` |
| S2.2 | Each exam row shows the title, type, date and time, the window status word, its packages (two names and "+N more"), and the price. A discounted price shows the list price struck through, the charged price, the percent off and the end date. Free, package-only and no-price rows say so in words | `b0101b9` |
| S2.3 | The exam detail's buy button comes first, and only when the exam has a price of its own. Every package the exam is in is listed below it. An exam in no package shows no packages section. The old one-package buy button and its sheet are gone from this page | `6c77661` |
| S2.4 | Each package card shows the title, the exam count, and the price (struck through when discounted), and links to the package page, which is unchanged | `6c77661` |
| S2.5 | "Free, log in to start" for a visitor, and "Free, start" on an open exam a student holds for free. "Access until {date}" for a held grant, and "Your access ended on {date}" for an expired one, with buying open again. The price fact shows the discount and its end date | `6c77661` |
| S2.6 | The purchase sheet charges the effective price, shows the list price struck through and the discount, and names the package the exam comes from. The bundle picker totals at effective prices. The package's own sheet charges its effective price | `180f2c1` |
| S2.7 | Build, headless overflow check (below), this note | this commit |

### Decisions taken in this step

- **Packages leave the category page.** The category page now shows subcategories and exams (Q9). Packages are reached from the exam page's package cards and from My exams. The package page itself is unchanged.
- **The exam page's module buy button is removed.** Its job is done by the package cards (Q11, Q12). Buying a whole package still happens on the package page, with its own buy button.
- **Discount end date.** The price line shows "until {date}" from the API's `discountEndsAt`. An ended discount comes back with the effective price equal to the list price, so it shows as a plain price.
- **Bundle picker.** It keeps its layout. The rows show the charged price; the list price is not struck through in the picker.
- **Commit grouping.** S2.3 to S2.5 share the purchase-sheet props that S2.6 adds, so commit `6c77661` does not type-check on its own. The tip of the branch does. S2.1 and S2.2 share the catalog copy and types in one commit.

## Pages to open

Visitor (signed out), which can be checked on the dev server without a password:

- `/mcq`: the MCQ door. The category cards are unchanged.
- `/mcq/c/a0000000-0000-4000-8000-000000000002` (MCQ category): subcategory cards at the top, then the exams, with the "Include exams in subcategories" switch on. The switch on shows every exam in the MCQ branch, including the exams sold in the discount and popular packages. The list pages with Load more when a category has more than twenty.
- `/exam/e0000000-0000-4000-8000-000000000060` (discounted exam, 400 with 25% off): "Buy this exam" at the charged price, after the discount, with "Free, log in to start" on the free exam `…000061`.
- `/exam/e0000000-0000-4000-8000-000000000063` (popular MCQ, in a package): no buy button, the package card below.
- `/mcq/m/b0000000-0000-4000-8000-000000000007` (discount package): the package page as before, with the exam rows at their charged prices.

Signed in as a student: the account list, the purchase sheet and the access lines. Those pages need the review password (see below).

## Tests

Run through the web helper under the heavy lock.

| Files | Tests | Result |
|---|---|---|
| `tests/student-buy/price-view.test.ts` (new): the price label for list, discount, ended discount, free, package-only and no price; the charged amount | 10 | pass |
| `tests/student-buy/category-exams.test.tsx` (new): branch on by default and the switch, Load more, coming-soon, the empty line with subcategories, and the query keys | 5 | pass |
| `tests/student-buy/exam-row.test.tsx` (new): a discounted row, a plain price, free, no price, package-only, package tags, the window status word, and the phase mapping | 8 | pass |
| `tests/student-buy/exam-page.test.tsx` (new): the buy button and its price, free for a visitor, package-only, no packages, access until, expired, free start, and a discount struck through | 8 | pass |
| `tests/student-buy/purchase-sheet.test.tsx` (new): the charged price with the list price struck, the package line, and a plain price | 2 | pass |
| `tests/pur-stu`, `tests/pur-fnd`, `tests/stu-lms`, `tests/student-nav`: existing tests of the bundle, ownership, sheet and access | 168 across the folders with the new tests | pass |

Type check (`tsc --noEmit`): clean. Lint on every touched S2 folder: clean.

## Build

`npm run build` under the heavy lock: exit 0.

## Headless overflow check

Visitor pages, at 320, 375 and 768 px, text only:

| Page | 320 px | 375 px | 768 px |
|---|---|---|---|
| `/mcq` | fits | fits | fits |
| `/mcq/c/…0002` (category) | fits | fits | fits |
| `/mcq/m/…0007` (package) | fits; a "বিবরণ" button sits past the edge (box 281 to 342 px, viewport 320 px) inside its own tab strip, so the page does not scroll | fits | fits |
| `/exam/…0060` (exam) | fits | fits | fits |

Every page fits. The boxes past the edge are decorative spans that were already there, plus the "বিবরণ" button above, which is clipped by its tab strip and does not scroll the page. Please look at it on a phone.

**Not checked:** the signed-in pages (the purchase sheet's live state, access lines, and the bundle picker), because the review password is still not available here. See S1-review.md.

## Bangla to confirm

| Bangla | English | Where |
|---|---|---|
| শুধু প্যাকেজে কেনা যাবে | Only in a package | Exam rows and the exam price |
| বিনামূল্যে | Free | Exam rows and the detail |
| {percent}% ছাড় / {date} পর্যন্ত | {percent}% off / until {date} | Price line |
| তালিকা মূল্য | List price (for screen readers) | Struck price |
| পরীক্ষা / আরও দেখুন / উপ-বিভাগ | Exams / Load more / Subcategories | Category page |
| উপ-বিভাগের পরীক্ষাও দেখান | Include exams in subcategories | Category switch |
| এই বিভাগে এখনো কোনো পরীক্ষা নেই। / এখানে যোগ হওয়া পরীক্ষা এই তালিকায় দেখা যাবে। | No exams in this category yet. / Exams added here will appear on this list. | Category page, empty |
| আরও {n}টি | +{n} more | Exam row package tags |
| তারিখ ঘোষণা হবে / আসছে / চলছে / শেষ হয়েছে / অনুশীলন | Date to be announced / Upcoming / Live now / Ended / Practice | Window status |
| বিনামূল্যে, শুরু করতে লগইন করুন / বিনামূল্যে, শুরু করুন | Free, log in to start / Free, start | Exam buttons |
| {date} পর্যন্ত অ্যাক্সেস / আপনার অ্যাক্সেস {date} তারিখে শেষ হয়েছে | Access until {date} / Your access ended on {date} | Exam detail |
| এই পরীক্ষাসহ প্যাকেজ | Packages with this exam | Exam detail |
| প্যাকেজে: {titles} | In package: {titles} | Purchase sheet |

## Markers

None in S2. The web uses the `pricing`, `packages` and `access.expiresAt` fields the API now sends.
