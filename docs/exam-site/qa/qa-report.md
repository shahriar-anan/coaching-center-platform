# Exam Site QA Report

Tester: Shahriar Anan · Started: 2026-10-31 · Branch under test: `demo/client-review`
Environment: dev build, local (web :3000, API :8092, DB coaching_center_dev), Chrome desktop and phone width. See baseline below.

## Test environment baseline (captured 2026-10-31, read-only)

**Code**
- Branch `demo/client-review`, last commit `35b8605` (30 Oct, staff logout fix). The working tree is **dirty**: 23 modified and 8 new files, all from the marking-browser-by-category work (API `DevSeed`, `LiveExamParticipationService`, `LiveExamSubmissionFeedService`; web staff marking, participation, submissions, home; tests). So the dev build is *ahead of* what is deployed to staging. A bug seen here may not exist on staging, and the reverse.
- Next.js 16.3 / React 19 web; Spring Boot API (Java 21); Flyway at migration **V55**, `ddl-auto=validate`.

**Running services**
| Service | Where | Notes |
|---|---|---|
| Web (`next dev`) | `localhost:3000` | up |
| API (Spring Boot, dev profile) | `localhost:8092` | up. Web uses `.env.development.local` (8092), which overrides `.env.local` (8080). Nothing listens on 8080 |
| PostgreSQL 17 | `localhost:5432` | API is connected to `coaching_center_dev` |

**Dev profile settings that differ from production**
- Mail send limits and resend cooldown are effectively off (10000/hour, 0 s), and the reset-response delay is 0. **Rate limiting and brute-force timing cannot be judged here**; verify on staging.
- Lockout is still active: 5 failures in 15 min. Access token 15 min, refresh 30 days, email/reset codes 10 min, 5 code attempts.
- `DEV_EXPOSE_EMAIL_CODES=true`: activation, reset and email codes are written to the API log (`LoggingEmailSender`), so read the code from the API console.
- Payments use the **SSLCommerz sandbox**. Callback base was changed 2026-10-31 from `localhost:8080` to `localhost:8092` (needs an API restart). The gateway's server-to-server IPN cannot reach localhost, so only the browser redirect return can be tested here; IPN belongs on staging.
- `SEED_ENABLED=false` (old seed), `SEED_DEV=true` (minimal `DevSeed`, runs once).
- Timestamps are stored in UTC. The site's display zone is Asia/Dhaka.

**Database `coaching_center_dev`** (12 MB; 29 other databases on this server are test or QA copies, so check the name before trusting any query)
- Accounts (all dev-seeded; only the Master Admin has ever logged in):
  | Role | Phone | Email |
  |---|---|---|
  | Master Admin | +8801700000001 | master@example.com (email not verified) |
  | System Admin ×2 | +8801711000001 / 002 | staff1 / staff2@example.com |
  | Instructor ×2 | +8801711000003 / 004 | instructor1 / instructor2@example.com |
  | Student ×2 | +8801711000011 / 012 | student1 / student2@example.com |
  Password for everyone except the Master Admin comes from `SEED_DEV_PASSWORD` in `api/.env` (not recorded here).
- Catalogue: 2 top-level categories (Bangladesh Judicial Service (BJS); Bar Council Enrolment) with 2 children (Assistant Judge Recruitment Exam; Advocate Enrolment Exam); **1 module** (Bar Council Complete Preparation Package, manual access, published); 6 exams, all published, 20 min each; 3 question papers, 30 questions, 120 options; 2 package-exam links.
- Exams: 2 free live MCQ (BJS Civil Procedure; Bar Council General), 2 free live written (BJS Criminal Law; Bar Council Civil Law), 1 **paid** live written (Bar Council Criminal Law), 1 **paid** MCQ practice (Penal Code Practice Set). Prices set 2026-10-31 for testing: Criminal Law written = ৳500; Penal Code Practice = ৳300 with 20% discount until 2026-11-30.
- Live dates are 29 Oct, 30 Oct (×2) and 1 Nov (Dhaka time). **Three of the four free live exams are already past** and only one is still upcoming.
- Activity: 2 submitted participations (marking queue has data), 1 exam grant, 1 student notice, **0 payments**, 1 refresh token. Everything else (orders, receipts, wishlist, audit) is empty or near-empty. Anything needing volume (pagination, leaderboards with many rows, large lists) cannot be tested without extra data.

**Implications for testing**
1. Segment 1 can run fully on this data. The module/exam browse paths are thin (1 module).
2. Payment segments: prices and callback host are now fixed; restart the API first. IPN still needs staging.
3. Activation, reset and email-code flows: read codes from the API log.
4. Rate-limit, lockout-timing and mail-limit tests belong on staging.
5. Because the dev build includes uncommitted marking changes, tag every Segment 8 finding with "dev build" or "staging".

## How to read this report

- **BUG-nnn**: something that exists but behaves wrongly.
- **REQ-nnn**: a new requirement or a change to agreed behaviour.
- **Severity** (bugs): S1 blocker (data loss, money, security, cannot sit exam) · S2 major (feature broken, workaround exists) · S3 minor (wrong but harmless) · S4 cosmetic.
- **Priority** (requirements): P1 before launch · P2 soon after · P3 later.
- **Status**: Open · Fixed · Verified · Won't fix · Needs decision.
- Test IDs (e.g. `PUB-07`) point to the checklist given for each segment, so every finding traces to a test.

## Segment progress

| # | Segment | Status | Bugs | Reqs |
|---|---------|--------|------|------|
| 1 | Public site and navigation (logged out) | Done; findings logged | 6 | 12 |
| 2 | Registration, activation and login | Not started | 0 | 0 |
| 3 | Password, account and profile | Not started | 0 | 0 |
| 4 | Student home, catalogue and exam preview | Not started | 0 | 0 |
| 5 | Purchase and payment (SSLCommerz, bKash, Nagad) | Not started | 0 | 0 |
| 6 | Sitting an exam (MCQ and written) | Not started | 0 | 0 |
| 7 | Results, leaderboard and notices | Not started | 0 | 0 |
| 8 | Staff: instructor marking and submissions | Not started | 0 | 0 |
| 9 | Staff: exam, module and category management | Not started | 0 | 0 |
| 10 | Staff: students, enrolments, payments, orders | Not started | 0 | 0 |
| 11 | Admin and Master Admin: staff accounts, audit, system status | Not started | 0 | 0 |
| 12 | Cross-cutting: i18n, responsive, accessibility, security, performance | Not started | 0 | 0 |

## Index of findings

| ID | Type | Sev / Pri | Title | Test |
|----|------|-----------|-------|------|
| BUG-001 | Bug | S2 | Student results page shows no results | PUB-23 (as reported) |
| BUG-002 | Bug | S3 | Anonymous visitors get a 401 on `/auth/refresh` on every page | PUB-01, 02 |
| BUG-003 | Bug | S2 | Landing "Exam" header link goes straight to MCQ | PUB-05 |
| BUG-004 | Bug | S3 | Unknown exam id shows a "check your internet connection" error | PUB-30 |
| BUG-005 | Bug | S3 | Sidebar open icon shows on phones on the landing page | PUB-28 (as reported) |
| BUG-006 | Bug | S4 | 404 page Bangla wording ("পাতা") reads badly | PUB-29 |
| REQ-001 | Req | P1 | Rename "Exam Portal" to "Symam Law" | Landing |
| REQ-002 | Req | P1 | Make the Symam Law name prominent in the hero | Landing |
| REQ-003 | Req | P1 | Landing exam-category section modelled on the old site, following the MCQ / Written flow | Landing |
| REQ-004 | Req | P1 | Footer rebuild: legal pages copied from old site, social and app icons, payment-methods strip | Landing / PUB-13..16 |
| REQ-005 | Req | P2 | Polish the About Us page on phones | Landing / PUB-13 |
| REQ-006 | Req | P1 | Redesign landing sections below the MCQ / Written doors | PUB-28 (as reported) |
| REQ-007 | Req | P1 | Exam browse filter tabs: All, Free, Live, Archived | Browse pages |
| REQ-008 | Req | P2 | Hide "include exams in subcategories" on the last category level | PUB-24 |
| REQ-009 | Req | P2 | Pagination for long exam lists (potential gap) | PUB-25 |
| REQ-010 | Req | P3 | Build the header sections not yet implemented | PUB-03 |
| REQ-011 | Req | P2 | Full Bangla and English translation review | PUB-08..12 |
| REQ-012 | Req | P2 | Default cover for exams and packages shows the title, not initials | Landing / catalogue |

## Bugs

### BUG-001 · S2 · Student results page shows no results
- **Found in:** Segment 1 (reported against PUB-23); the logged-in `/results` page.
- **Observed:** The full results page does not show a student's results.
- **Expected:** A student sees **every exam they have taken**, whatever the status of that exam (any state: pending marking, marked, results released, answers released, and so on).
- **Note for whoever picks this up:** Details are deliberately not listed. **Analyse the whole result system first** (how results, participations, statuses, marking and release are modelled and exposed by the API and consumed by the page), and only then address the issue. Do not patch the page alone. Re-test against Segments 7 and 8, which touch the same data.
- **Status:** Open

### BUG-002 · S3 · 401 on `/auth/refresh` for anonymous visitors
- **Found in:** PUB-01 and PUB-02, on every page while logged out (private window, Chrome, desktop).
- **Observed:** The console shows `POST http://localhost:8092/api/v1/auth/refresh 401 (Unauthorized)` on each page load. Stack: `rawFetch` and `apiRequest` in `client.ts` → `refresh` (`client.ts:277`) → the effect in `session-context.tsx:147` inside `SessionProvider`, mounted from `providers.tsx` and `layout.tsx`.
- **Expected:** A visitor with no session produces no failing request and a clean console.
- **Notes for the agent:** The session provider tries a refresh on mount to restore a session. The 401 is the expected API answer when no refresh cookie exists, but the browser still logs it. Options include a cheap "has a session" hint (non-secret flag or cookie) to skip the call, or a lightweight session-probe endpoint that returns 200 with an anonymous state. Do not weaken the refresh endpoint's security. Check what happens to a returning logged-in user (refresh must still work) and to a user whose refresh token has expired.
- **Acceptance:** No red console error on any public page for a logged-out visitor. A logged-in user still stays logged in after a page refresh. Admin refresh behaviour is unchanged (see the web-build-api-base memory note: an absolute API base logged admin out on refresh).
- **Status:** Open

### BUG-003 · S2 · Landing "Exam" header link goes straight to MCQ
- **Found in:** PUB-05, landing page header.
- **Observed:** The header's Exam entry opens the MCQ exams directly.
- **Expected:** The user first chooses the **type of exam** (MCQ or Written), then goes to that type's browse page. The same type-first flow applies to the landing category section (REQ-003).
- **Notes for the agent:** The two doors (MCQ and Written) already exist on the landing page and in `/mcq` and `/written`. Choose between a dropdown with the two types or a chooser page. Apply to the desktop header, the phone menu and any footer link that points to the same place. Ask the client which is preferred.
- **Acceptance:** From any page, the Exam entry never lands on a single type without the user having chosen it. Works with keyboard and on phone.
- **Status:** Open (design choice needed)

### BUG-004 · S3 · Wrong message for an unknown exam id
- **Found in:** PUB-30. URL used: `http://localhost:3000/exam/123123`.
- **Observed:** The page tells the user to "check your internet connection".
- **Expected:** A clear "this exam was not found" message (exam removed or the address is wrong) with a way back to the exam lists. A connection message should appear only for real network or server failures.
- **Notes for the agent:** The English text is `i18n/messages/en/common.ts:137` (`errorBody`) and probably comes from the generic error boundary (`app/error.tsx`). The API's 404 for the exam is probably treated as a generic failure. Separate not-found (404) from network (no response) and server (5xx) errors. Check the same for category and module ids and the result, sit and leaderboard pages. Both languages.
- **Acceptance:** `/exam/123123`, `/exam/abc` and a deleted exam each show the not-found message. With the API stopped, the connection message appears.
- **Status:** Open

### BUG-005 · S3 · Sidebar open icon on phones, landing page
- **Found in:** Reported against PUB-28, landing page, logged out, phone width.
- **Observed:** A sidebar open icon is shown on phones.
- **Expected:** It should not be shown on the landing page on phones. _(Confirm whether this applies to all logged-out public pages or only the landing page; see 1.3.)_
- **Status:** Open

### BUG-006 · S4 · 404 page: Bangla wording
- **Found in:** PUB-29. URL used: `http://localhost:3000/hoho`.
- **Observed:** The proper not-found screen shows, but it renders "page" as the Bangla word "পাতা", which the tester finds poor.
- **Expected:** Natural Bangla for a web page, term to be decided with the client. "পাতা" appears in many strings (for example `i18n/messages/bn/common.ts:226`, `catalog.ts:61`, `auth.ts:41`, `purchase.ts`).
- **Notes for the agent:** Handle with REQ-011 so one consistent term is chosen across the site, not just on this page.
- **Status:** Open (decision needed on the term)

## Requirements and changes

### REQ-001 · P1 · Rename "Exam Portal" to "Symam Law"
- **Where:** The name beside the icon in the site header (landing page and others).
- **Change:** Show **Symam Law**.
- **Notes for the agent:** The string appears in more than the header. Known places: `i18n/messages/en/common.ts:7`, `en/account.ts:61`, `en/auth.ts:29` ("Exam Portal · Admin panel"), `en/home.ts:23`, `en/legal.ts:60`, and the calendar export identifier in `student/catalog/catalog-helpers.ts:188`. Check the Bangla files, page titles and metadata, emails, receipts and PDFs too. Confirm with the client how the Bangla form is written (the hero already shows "SYMAM LAW · সাইমাম ল'").
- **Acceptance:** No "Exam Portal" text remains anywhere user-visible, in either language.
- **Status:** Open

### REQ-002 · P1 · Make the Symam Law name prominent in the hero
- **Where:** The landing hero (screenshot 1: "SYMAM LAW · সাইমাম ল'" is a small line above a large Bangla headline).
- **Change:** The brand must be clear and noticeable, so a visitor sees at once that the site belongs to Symam Law. Currently it is small.
- **Notes for the agent:** Keep the headline and the Online batch / Offline batch buttons. Decide the treatment (larger or heavier brand line, logo lockup) within the current dark hero theme. Check phone width and both languages.
- **Status:** Open

### REQ-003 · P1 · Landing exam-category section modelled on the old site, following the MCQ / Written flow
- **Where:** The landing page's Exam section.
- **Current state (new site):** a single dark card under a pill headed "এক্সাম" (Exam). The card has a plain cover with MCQ and Written pills, the title "বার কাউন্সিল এনরোলমেন্ট", "১টি প্যাকেজ" (1 package), "শুরু ৳৫০০ থেকে" (from ৳500) and an Explore button. It shows one category only, as a package-style card.
- **Reference (old site, `symamlaw.com`):** "Exam Category" (এক্সাম ক্যাটাগরি) with a subtitle about starting your success journey; cards "19th BJS MCQ Exam" and "19th BJS Special Model Test (MCQ)", each a rounded card with a blue exam icon, the title and an Explore (এক্সপ্লোর করুন) button, on a pink background. This is the **target pattern**: the old exam site's landing page shows the **main exam categories**.
- **Change:** Show the main (top-level) categories as cards leading into each category, the way the old site does, but styled and placed to fit the current theme. Not a single package card.
- **Exam-type flow:** Categories are only listed **after the user has chosen MCQ or Written** (the doors on `/mcq` and `/written`). The landing section must follow the same flow and must not show categories detached from an exam type. Two ways to do it: (a) an MCQ / Written switch at the top of the section that changes which categories are listed, with each card going to `/mcq/c/<id>` or `/written/c/<id>` to match; or (b) the section sits after the two doors and the user picks a type first. Pick one with the client. Either way, a category card must never open a page for a type the user did not choose. Handle it together with BUG-003 (header "Exam" link) so the whole site has one flow: type first, then category, then module, then exam.
- **Edge cases for the agent:** a category with exams of only one type (hide it, or show it greyed, under the other type); a category with no exams yet (coming-soon); parent categories with child categories; the card's count and "from ৳" price must be for the chosen type only.
- **Notes for the agent:** Read the real categories from the API (`/api/v1/public/live-exam-categories`, which on dev returns BJS and Bar Council Enrolment). Only the look follows the old site. `docs/exam-site/plans/ui-delivery/landing-page-plan.md` (rows 6 and 7) already describes the old-site section and plans to feed it from the new API with real counts and prices. Keep it within the landing-page styling decision (the old site styles `/` only).
- **Status:** Open

### REQ-004 · P1 · Footer rebuild
- **Where:** The site footer (`components/shell/site-footer.tsx`) and the pages it links to.
- **Changes:**
  1. Copy these pages **from the old site, fully and exactly as written**: Terms and Conditions, Return/Refund policy, Privacy Policy, About Us. Replace the current text of `/terms`, `/refund`, `/privacy`, `/about` (Bangla and English; the current legal text is in `i18n/messages/*/legal.ts`).
  2. Add icons and links for: **App Store, Google Play** (confirmed: the Google Play app badge), **Facebook, WhatsApp, LinkedIn, YouTube**.
  3. Add the **payment-methods strip** the old site's footer has: one long image showing all accepted payment options. Required in the new footer too.
- **Notes for the agent:** The icon files will be supplied when work starts. The payment strip is a single **ready-made image** on the old site (a picture, not individual logos), and it **can be reused as it is**: copy the image file from the old site's footer (read-only access is in the old-site SSH memory) or get it from the client, and add it as a static asset. Do not rebuild it from separate logos. Check that it matches what the new site accepts (SSLCommerz, bKash, Nagad; see the launch-status memory) and that it is sharp and fits on phones, in a wrapped or scrollable form, without horizontal page scroll. Give it descriptive alt text. The real URL for each link must come from the client. Old-site text can be read over the read-only SSH route in the memory notes. Keep the copied wording unchanged, including its legal meaning for payments and refunds.
- **Acceptance:** Each page matches the old site's text word for word. All icons and the payment strip present, each icon linking to the right place, opening in a new tab, with alt text. Footer works on phones.
- **Status:** Open (assets and URLs pending)

### REQ-005 · P2 · Polish the About Us page on phones
- **Where:** `/about` at phone widths.
- **Change:** The client does not like the phone layout; polish it. Combine with the copy change in REQ-004.
- **Status:** Open (needs design direction; get specifics from the client)

### REQ-006 · P1 · Redesign landing sections below the doors
- **Where:** Landing page, everything after the MCQ and Written doors (for example Upcoming exams, Popular exams / packages).
- **Problem:** The tester calls them "very badly designed". They should look **professional and well crafted**. This is a big gap.
- **Notes for the agent:** Use the client mockup style. Plan the content of each section, how it behaves when empty or long, how past exams are treated, and the phone layout. Show a design for review before building. Related: REQ-003.
- **Status:** Open

### REQ-007 · P1 · Exam browse filter tabs
- **Where:** The exam browse pages (`/mcq`, `/written`, and the category and module pages that list exams).
- **Change (client request):** A tab-based filter with **All, Free, Live, Archived**. **Archived means exams whose live window has ended.**
- **Questions to settle before building:** (a) Is "Live" an exam inside its window right now, or any exam with mode LIVE (including upcoming)? (b) Practice exams have no live window; they should appear under All, and Free if free, and never under Archived, correct? (c) Should the selected tab survive a reload (URL parameter)? (d) How does it combine with the "include exams in subcategories" switch and with pagination (REQ-009)? (e) Do the tabs show counts?
- **Data to test with:** the dev exams (4 free live, 1 paid live written, 1 paid practice); three are already past.
- **Status:** Open

### REQ-008 · P2 · Hide "include exams in subcategories" on the last category level
- **Where:** Category pages (`components/student/catalog/category-exams.tsx`, the switch between the whole branch and the category's own exams).
- **Change:** On the last category in a tree (no subcategories) the checkbox must **not be shown**, since it has no meaning there.
- **Acceptance:** Shown on a category with subcategories; hidden on one without. Check both child categories in dev.
- **Status:** Open

### REQ-009 · P2 · Pagination for long exam lists (potential gap)
- **Where:** All exam browse pages.
- **Observation:** Pagination appears not to exist. Exam lists can become very long, so it is likely needed. It could not be tested with only 6 exams.
- **Notes for the agent:** The API already supports `page` and `size` (default 20, max 100). Decide between pages and "load more", and how it works with the filter tabs (REQ-007). Test with seeded volume.
- **Status:** Open (confirm against a large data set)

### REQ-010 · P3 · Header sections not yet built
- **Where:** Header items: Admission form, Packages, Notices, Publications, Blog, Media, Resources.
- **Observation:** They are not configured. This is to be done later, so it is an addition and not a defect.
- **Notes for the agent:** Each needs its own scope. Several have routes today (`/publications`, `/blog`, `/media`, `/resources`, `/notices`) but no full implementation. Until built, decide whether the links are hidden or marked as coming soon.
- **Status:** Deferred

### REQ-011 · P2 · Full Bangla and English translation review
- **Where:** All pages (PUB-08 to PUB-12: the language switch, persistence, rendering and number formats work; the **translation text itself** has problems).
- **Observation:** There are a large number of translation keys. A review will be done later. See BUG-006 for one concrete example.
- **Notes for the agent:** Message files are in `web/src/i18n/messages/{bn,en}`. Build a glossary first (page, exam, result, and so on) with the client, then sweep every file so terms are consistent.
- **Status:** Deferred

### REQ-012 · P2 · Default cover for exams and packages
- **Where:** Every place an exam or package card or detail header shows a cover that has not been uploaded. Built in `web/src/components/ui/package/package-cover.tsx` (`PackageCover`), used by the package card, module page, exam page, purchase sheet and the staff cover uploaders.
- **Observed:** With no uploaded image the cover shows the title's **first letters** (for example "BQ"), over a shaded gradient with the type label (MCQ / Written / Package).
- **Change:** Show the **title itself** in place of the initials, on the **same background** as today (keep the existing gradient and circles).
- **Notes for the agent:** Long titles and Bangla titles need handling: wrapping, a line limit with an ellipsis, a readable size on phones and the 16:9 and 160px band frames. Decide what happens with the small type label under the title. The landing card in the screenshot shows a plain cover with pills but no initials, so check whether the landing section uses the same component.
- **Acceptance:** Exam and package cards and detail pages with no cover show the full (or neatly clipped) title on the current background, in both languages, without overflow.
- **Status:** Open

## Segment 1 results (what was tested)

Environment: Chrome, desktop and phone width, private window, logged out, DevTools open, dev build against `coaching_center_dev`.

| Test | Result | Item |
|------|--------|------|
| PUB-01 | Pass with console error | BUG-002 |
| PUB-02 | Pass with console error | BUG-002 |
| PUB-03 | Partly: unbuilt header sections and footer | REQ-010, REQ-004 |
| PUB-04 | Pass (logged out) | |
| PUB-05 | Fail | BUG-003 |
| PUB-06 | Mostly pass; the "Exam" section disagrees with the theme | REQ-003 |
| PUB-07 | Pass | |
| PUB-08 to 12 | Pass; translation quality is the problem | REQ-011 |
| PUB-13 to 18 | Not tested separately; covered by the footer and page requirements | REQ-004, REQ-005 |
| PUB-19 | Pass; changes requested | REQ-003, REQ-007 |
| PUB-20 | Pass | |
| PUB-21 | Pass | |
| PUB-22 | Pass | |
| PUB-23 | Reported: results page empty (see 1.3) | BUG-001 |
| PUB-24 | Pass; checkbox change requested | REQ-008 |
| PUB-25 | No pagination exists | REQ-009 |
| PUB-26 | Pass | |
| PUB-27 | Pass; "may need more checking" | |
| PUB-28 | Reported: landing page issues (see 1.3) | BUG-005, REQ-006 |
| PUB-29 | Pass; wording poor | BUG-006 |
| PUB-30 | Fail: wrong message | BUG-004 |
| PUB-31 | Pass | |
| PUB-32 to 37 | Pass | |

### 1.3 Checks confirmed
- **PUB-23** (search, filter, sort on browse pages), **PUB-28** (return to the requested page after login) and **PUB-27** (protected URLs while logged out): all work as expected (confirmed by the tester, 31 Oct).
- **PUB-35** (keyboard only) was reported as part of a group and could still get a closer pass.
- The results-page finding was reported against PUB-23 and the landing findings against PUB-28, so BUG-001, BUG-005 and REQ-006 keep those references "as reported".

## Observations (not bugs, worth remembering)

- The pink "19th BJS MCQ Exam" screenshot is the **old live site** (symamlaw.com), not the dev site. The running dev API (port 8092, started 31 Oct 00:05 from `api/target/classes`) serves `coaching_center_dev`, whose categories are BJS and Bar Council Enrolment. This is the reference for REQ-003.
- Segment 1 findings were made on a dev build that is ahead of staging (uncommitted marking work). None of these findings relate to that work.
