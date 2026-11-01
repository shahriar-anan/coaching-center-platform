# QA Checklist: Segment 1 fixes and additions (31 Oct 2026)

What this covers: everything built from `qa-report.md` Segment 1 in this run, plus the two fixes found on the way (results page, archived results). Plan: `docs/exam-site/plans/ui-delivery/qa-segment-1-plan.md`.
Tick each box. If a case fails, note the item id (for example `HDR-03`), the page, the width or device, and the language.

## 0. Before you start

- [ ] Restart the API (it has new endpoints) and the web app. Both were stopped for this work.
- [ ] Dev database `coaching_center_dev`, accounts from `qa-report.md` (Master Admin +8801700000001, Student +8801711000011, Instructor +8801711000003).
- [ ] Test each case in **English and Bangla** unless it says otherwise, in **light and dark** mode on the landing page.
- [ ] Phone widths: 320, 360, 390, 640 px. Desktop: 1024, 1280, 1440 px. Chrome DevTools and one real phone if possible.
- [ ] DevTools console open on every public page for the visitor checks.
- [ ] Nothing was seen in a browser by the agents. Treat everything below as untested.

## 0b. Test accounts (dev database `coaching_center_dev`, fake seed data)

Sign in with the phone number or the email. Student site: `http://localhost:3000/login`. Staff and admin: the same address (or the staff host on staging).

| Role | Phone | Email | Password |
|---|---|---|---|
| Master Admin | +8801700000001 | master@example.com | `ChangeMeMaster1!` (from `MASTER_ADMIN_PASSWORD`; if it was changed after the first login, use the changed one) |
| System Admin 1 | +8801711000001 | staff1@example.com | `root1234` |
| System Admin 2 | +8801711000002 | staff2@example.com | `root1234` |
| Instructor 1 | +8801711000003 | instructor1@example.com | `root1234` |
| Instructor 2 | +8801711000004 | instructor2@example.com | `root1234` |
| Student 1 (has 2 submitted written sittings) | +8801711000011 | student1@example.com | `root1234` |
| Student 2 | +8801711000012 | student2@example.com | `root1234` |
| Student 3 (BJS package; Written pack by enrolment; a failed paper with a retake) | +8801711000013 | student3@example.com | the sample password |
| Student 4 (Bar Council package; a low score; a hidden review) | +8801711000014 | student4@example.com | the sample password |
| Student 5 (Bar Council package; started the free test; one failed payment) | +8801711000015 | student5@example.com | the sample password |
| Student 6 (almost empty; one pending payment; shows the empty states) | +8801711000016 | student6@example.com | the sample password |

- **The sample seed is the data to test against.** Build it with `SEED_SAMPLE=true` as in `build-sample-db.md`. It holds Students 1 to 6, the 20 exams, the sittings, orders, reviews and announcements that the checklist refers to. Its ids and where each feature sits are in `seed-map.md`. Students 3 to 6 exist only in the sample seed.
- The sample password is `SEED_SAMPLE_PASSWORD`, the value you set when you build the sample database. It is not written in any file.
- `root1234` is `SEED_DEV_PASSWORD` in `api/.env`; the dev seed gives it to every account except the Master Admin.
- A third student, `+8801744628303` (shahriaranany6@gmail.com), also exists in the dev database. It is not part of the seed and its password is not recorded here.
- Old-seed accounts in `api/.env` (`SEED_ADMIN_*`, `SEED_STUDENT_*`, password `root1234`) are unused because `SEED_ENABLED=false`.
- Activation, reset and email codes are written to the API console (`DEV_EXPOSE_EMAIL_CODES=true`); read them from the API log.
- Lockout is on: 5 wrong passwords in 15 minutes locks the account for a while.
- Servers: API `http://localhost:8092`, web `http://localhost:3000`.

## 1. Brand: "Symam Law" (REQ-001)

- [ ] **BRD-01** Header shows "Symam Law" (not "Exam Portal" / "এক্সাম পোর্টাল" / "এক্সামপোর্টাল") in both languages.
- [ ] **BRD-02** Bangla also shows the Latin "Symam Law" (not "সাইমাম ল'") in the header, hero, footer, page titles and welcome text.
- [ ] **BRD-03** Browser tab title and the page-title template read "Symam Law" on `/`, `/mcq`, `/packages`, `/login` (both languages).
- [ ] **BRD-04** Signed-in student welcome text: "Welcome to Symam Law, {name}!" / Bangla equivalent.
- [ ] **BRD-05** Admin login footer reads "Symam Law · Admin panel" (English) / Bangla equivalent.
- [ ] **BRD-06** Account page and receipt show the new name where the site name appears (`/account`, a receipt).
- [ ] **BRD-07** Welcome and verification **emails** (read them in the API log) say "Symam Law", with no "exam portal"; the page title and signature end with "SYMAM LAW". Staff welcome email too.
- [ ] **BRD-08** Receipt **PDF**: tagline "Online law exams" and "Thank you for choosing Symam Law!" (no "ExamPortal", no doubled name).
- [ ] **BRD-09** Calendar export from the routine (`.ics`) has no "Exam Portal" in its product id.
- [ ] **BRD-10** Search the site for the old names in both languages: none visible anywhere (try the 404 page, the help page, the legal pages).

## 2. Header (HDR, BUG-003, BUG-005, REQ-010)

### 2.1 Visitor, logged out
- [ ] **HDR-01** Phone (< 640 px): only the logo image shows, **no name text** beside it. Screen reader still announces the home link.
- [ ] **HDR-02** From 640 px up: the logo shows with the "Symam Law" text beside it.
- [ ] **HDR-03** Phone and tablet (< 1280 px): a text button **"Menu"** (no hamburger icon) shows on **every public page**: `/`, `/mcq`, `/written`, `/packages`, `/about`, `/terms`, `/exam/<id>`, `/login`.
- [ ] **HDR-04** The Menu lists eight items in this order: Admission form, Exam, Packages, Notices, Publications, Blog, Media, Resources. Exam appears as a group with **MCQ** and **Written** entries (no single "Exam" link).
- [ ] **HDR-05** Admission form goes to `/register`. MCQ goes to `/mcq`, Written to `/written`. Packages goes to `/packages`. Notices goes to `/notices` (a visitor lands on login and returns after login).
- [ ] **HDR-06** Publications, Blog, Media, Resources each open the "Coming soon" page with a back button. These pages must not be indexed.
- [ ] **HDR-07** Language and theme appear as menu items at the bottom of the Menu. Switching language or theme from the menu works and the menu closes.
- [ ] **HDR-08** Keyboard: Tab to Menu, Enter opens it, arrow keys move between items (including language and theme), Escape closes and focus returns to the button.
- [ ] **HDR-09** The header stays visible while a menu is open, even if you scroll.
- [ ] **HDR-10** 1280 px and up: the eight links show in the bar. **Exam** is a button with a chevron that opens MCQ and Written. On the landing page only (known gap: inner pages have no link bar on wide desktop, only logo and controls).
- [ ] **HDR-11** Header fits at 320 px: logo, Menu button and the login / sign-up pill do not overlap or wrap. Check with large system text too.
- [ ] **HDR-12** From 400 px up, the language and theme pills show in the bar; below 640 px check which controls are in the bar and that nothing is duplicated.

### 2.2 Signed-in student
- [ ] **HDR-20** No hamburger icon on phones.
- [ ] **HDR-21** The account trigger always shows the **profile icon beside the first name** (for example "Student"), on phone and desktop. A long name is cut with an ellipsis and does not push the header out.
- [ ] **HDR-22** On phones (< 640 px) the language and theme switches are inside the account menu as plain items; they work by keyboard. From 640 px up they stay in the bar.
- [ ] **HDR-23** The sidebar toggle (panel icon) does **not** show on phones; it shows from 1024 px up on pages with the side menu.
- [ ] **HDR-24** Account menu still has Home, My account, Payments, Favourites, Notices, Routine, Admin panel (staff only) and Log out.
- [ ] **HDR-25** Notice bell and bottom tab bar still work as before.

### 2.3 Signed-in staff
- [ ] **HDR-30** Staff headers and the staff sidebar logo still show the logo with text (unchanged), at all widths.

## 3. Landing page `/` (REQ-002, REQ-003, REQ-006)

### 3.1 Hero
- [ ] **LND-01** The brand is a clear lockup above the headline: logo image + "Symam Law" in large accent type, centred on phones, left on large screens.
- [ ] **LND-02** On a 360 px phone the two door cards (MCQ, Written) and the sign-up button are still reachable without a long scroll. Note how far down they sit.
- [ ] **LND-03** Still one main headline; "Online batch / Offline batch" chips, registered-students line and the call-now card still show.
- [ ] **LND-04** Logo image switches correctly in dark mode.

### 3.2 Exam categories (type first)
- [ ] **LND-10** An **MCQ / Written** switch sits at the top of the section; MCQ is selected by default; both buttons can be pressed by keyboard and by touch.
- [ ] **LND-11** Under MCQ the cards list top-level categories that have MCQ packages or exams. Every card link goes to `/mcq/c/<id>`.
- [ ] **LND-12** Under Written the cards list categories for Written; every link goes to `/written/c/<id>`. A card never opens the other type.
- [ ] **LND-13** **BJS** (it has standalone exams and no package) now appears under the types it has exams for. Bar Council Enrolment appears with its package.
- [ ] **LND-14** Each card shows the title, a counts line (exams and packages, a zero part is left out) and a "From ৳..." line only when a price exists. No fake price.
- [ ] **LND-15** Switching type changes counts and prices to that type only.
- [ ] **LND-16** A type with no categories shows a short empty line; if neither type has any, the whole section is hidden.
- [ ] **LND-17** Loading state shows the switch and skeleton cards.
- [ ] **LND-18** The two doors in the hero still go to `/mcq` and `/written`.

### 3.3 Numbers strip
- [ ] **LND-20** Three tiles (packages, MCQ exams, written exams) each with an icon, a gold figure and a label; dividers on desktop; Bangla digits in Bangla.
- [ ] **LND-21** The strip hides when there are no packages.

### 3.4 Upcoming exams
- [ ] **LND-30** The soonest upcoming exam is a large navy card: date block (day, month), time, type pill, title, package name, price or "Free", a relative label (**Today / Tomorrow / In N days**) and a "View exam" button.
- [ ] **LND-31** The next four exams show in a compact list (date chip, title, type pill, time). Each links to the exam page.
- [ ] **LND-32** Desktop: spotlight left, list right. Phone: spotlight, then list.
- [ ] **LND-33** Past exams never show. If nothing is upcoming the section is hidden. (Dev data: only the 1 Nov exam is upcoming at the time of writing; check against today's date.)
- [ ] **LND-34** Dates and times are in Dhaka time. Check "Today / Tomorrow" around midnight Dhaka.

### 3.5 Packages
- [ ] **LND-40** Up to 6 packages, popular ones first, using the same card as `/packages`.
- [ ] **LND-41** Desktop: 3 columns. Phone: a **sideways-scrolling row** with the next card peeking; the page itself does not scroll sideways.
- [ ] **LND-42** "See all packages" goes to `/packages`.

### 3.5b How it works
- [ ] **LND-50** Three steps with icons, step numbers and text, joined by a line (horizontal on desktop, vertical on phone).
- [ ] **LND-51** "Secure payment via SSLCommerz" and four payment logos (bKash, Nagad, Rocket, Visa) are sharp, have alt text and sit on a light background in dark mode.

### 3.6 Contact and closing
- [ ] **LND-60** Contact is one panel: phone numbers as large tap-to-call buttons, a WhatsApp button (opens `wa.me` in a new tab), address with a map link, hours, email, and three social icon links. Stacks on phones.
- [ ] **LND-61** Closing band: gold circles, title, lead, a gold **Create account** button (clearly visible in light mode) and a **Browse packages** button to `/packages`, plus the login link.
- [ ] **LND-62** Section rhythm alternates plain and tinted; no layout jump while loading; nothing moves by itself.
- [ ] **LND-63** Whole page at 320, 768, 1280 px in light and dark: no horizontal scroll, no clipped text, Bangla titles wrap properly.

## 4. Packages page `/packages` (PKG-1)

- [ ] **PKG-01** Header "Packages" opens `/packages` (visitor and signed-in student). The page uses the normal student column.
- [ ] **PKG-02** Filter bar: search box, **Category** dropdown (indented tree, first option "All categories") and the All / MCQ / Written control.
- [ ] **PKG-03** Selecting a **parent category** lists the packages of its **subcategories too** (needs the API restart). Selecting a leaf shows only its own.
- [ ] **PKG-04** Searching narrows by title (English and Bangla titles). Clearing the box restores the list.
- [ ] **PKG-05** Filters are kept in the URL (`?category=&type=&q=`); reload and back/forward keep them; a copied link restores them.
- [ ] **PKG-06** A count line shows the number of results. "Load more" appears only when there are more than 12 and loads the next page without losing earlier ones.
- [ ] **PKG-07** No match: empty state with "Clear filters" that resets everything.
- [ ] **PKG-08** Cards: 1 column on phones, 2 from 640 px; covers show the title when no image is uploaded (see section 9); price shows, "price not set" when none.
- [ ] **PKG-09** Known gaps to note, not fail: a free package shows ৳0; the exam count on the card is MCQ + Written even when a type is selected; a package with both types opens its MCQ page when no type is selected.
- [ ] **PKG-10** Loading skeleton and the error message (stop the API: connection message; check a server error message too).
- [ ] **PKG-11** Dev data has only 1 package: say so in your notes; volume (paging) needs seeded data.

## 5. Footer and legal pages (REQ-004, REQ-005)

- [ ] **FTR-01** Social links are **icons** (Facebook, WhatsApp, LinkedIn, YouTube), each with an accessible name, opening in a new tab. Check each URL is the right account (client to confirm).
- [ ] **FTR-02** App Store and Google Play buttons show with icons and the "Coming soon" label (greyed, not clickable). The real links are still pending from the client.
- [ ] **FTR-03** The **payment strip** (SSLCommerz image) shows above the copyright row: sharp on a large and a high-density screen; on a 320 px phone it scrolls sideways **inside its own box** and the page does not scroll sideways.
- [ ] **FTR-04** Payment strip has descriptive alt text in both languages.
- [ ] **FTR-05** The minimal footer on sign-in pages (`/login`, `/register`, `/activate`...) is unchanged (copyright and legal links only). No footer on the exam sitting page.
- [ ] **FTR-06** Footer contact details match the real ones (phone +8801601561225, info@symamlaw.com, hours, Rajshahi address).
- [ ] **FTR-10** `/terms`, `/refund`, `/privacy`, `/about` show the **old site's text** with headings, paragraphs and lists, no "will be added soon" banner. Compare each against `symamlaw.com` word for word (the agent's check was empty for all four pages; spot-check at least two).
- [ ] **FTR-11** In **Bangla**, the four legal pages show the **English** text (the old site has no Bangla). No placeholder banner. (Client informed.)
- [ ] **FTR-12** Page titles are unchanged ("Terms", "Privacy policy", "Refund policy", "About us"); the old headings differ slightly ("Privacy Policies", "Return & Refund Policies").
- [ ] **FTR-13** `/about` on a phone: readable width, headings and spacing good, no overflow at 320 px.
- [ ] **FTR-16** Legal pages (terms, refund, privacy, about) reading design: a collapsed "On this page" list (numbered links to each section, opens and closes, links scroll to the right heading below the sticky header); headings have a gold bar on the left; paragraphs are dark and easy to read; lists have gold bullets; comfortable line spacing and padding; fine on a 320 px phone and in dark mode. The text itself is unchanged.
- [ ] **FTR-17** Bangla names: the footer link, the page title and the breadcrumb for Privacy read **প্রাইভেসি পলিসি** (the English term in Bangla script). The "On this page" label reads "এই পেজে".
- [ ] **FTR-14** Items to show the client (copied unchanged): About text is generic shop copy; Terms promise a 7-day money-back guarantee while the Refund page says 60/40/25% within 15/22/30 days; the Refund page's phone number differs from its call link.
- [ ] **FTR-15** Help and Contact pages: **known gap**, Contact still shows placeholder details (01700-000000, info@example.com). Record, do not fail this segment.

## 6. Exam browse filters (REQ-007, REQ-008, REQ-009)

On a category page, for example Bar Council Enrolment and its child category (`/mcq/c/<id>` and `/written/c/<id>`).

- [ ] **FLT-01** Tabs **All / Free / Live / Archived** show above the exam list (Bangla: সব / ফ্রি / লাইভ / আর্কাইভ). They wrap on phones and each is at least 44 px tall.
- [ ] **FLT-02** **All** lists every published exam of the category (as before).
- [ ] **FLT-03** **Free** lists only free exams (any mode, practice included). A paid exam and an exam sold only inside a priced package do not show.
- [ ] **FLT-04** **Live** lists live-mode exams that have not ended (not announced, upcoming, running). Ended exams and practice exams do not show.
- [ ] **FLT-05** **Archived** lists live-mode exams whose window has ended (three of the four free live exams on dev). Practice exams never show here.
- [ ] **FLT-06** The practice exam (Penal Code Practice Set) appears under All and, if free, Free; **never** under Live or Archived.
- [ ] **FLT-07** The chosen tab is kept in the URL as `?view=`; reload keeps it; an invalid value shows All.
- [ ] **FLT-08** Each empty tab shows its own message; All on a leaf category with nothing still shows "Coming soon".
- [ ] **FLT-09** Changing a tab, or the subcategory switch, starts a new list from page 1; Load more still works and focus is not lost.
- [ ] **FLT-10** On a **module (package) page** the same tabs filter the exams shown with the same rules.
- [ ] **FLT-11** The "include exams in subcategories" switch shows **only on a category that has subcategories** and is hidden on the last level (check both dev child categories).
- [ ] **FLT-12** Pagination: with more than 20 exams in a category (seed some) "Load more" appears and keeps the tab. With six exams it does not appear.
- [ ] **FLT-13** Known limit (not a failure): a module page loads at most 100 exams.
- [ ] **FLT-14** API (optional): `GET /api/v1/public/live-exam-categories/{id}/exams?view=FREE|LIVE|ARCHIVED|ALL|bogus` (bogus behaves as ALL).

## 7. Not-found and error messages (BUG-004)

- [ ] **ERR-01** `/exam/123123`, `/exam/abc` and a deleted or unknown exam id show **"This exam was not found"** with a button back (not "check your internet connection"). English and Bangla.
- [ ] **ERR-02** An unknown package (`/mcq/m/<bad>`, `/written/m/<bad>`) and an unknown category (`/mcq/c/<bad>`) show a not-found message with a way back.
- [ ] **ERR-03** The result, leaderboard and sit pages of an unknown exam show not-found.
- [ ] **ERR-04** Stop the API: pages show the **connection** message with Retry; start it again and Retry works.
- [ ] **ERR-05** Make the API answer a server error (5xx) if you can: pages show **"something went wrong on our side"** with Retry, not the connection text. Spot-check a student screen (routine, favourites, notices, payments) and a staff screen (students, payments, exams).
- [ ] **ERR-06** The unknown-address page (`/hoho`) still shows the 404 screen.
- [ ] **ERR-07** On the sit page, an exam you cannot reach now says "This exam was not found" (the old "You do not have access" text no longer shows; the API gives the same answer for both). Decide if that is acceptable.
- [ ] **ERR-08** Screens that show an error banner when there is no data and no error (exam editor, staff package page) still show the connection line; not changed.

## 8. Default cover (REQ-012)

Anywhere an exam or package has no uploaded cover: package card, module page, exam page, purchase sheet, staff cover uploaders.

- [ ] **CVR-01** The cover shows the **title** (not initials) on the same gradient and circles, with the type label (MCQ / Written / Package) still readable.
- [ ] **CVR-02** Long English and long Bangla titles wrap, are clamped to 2 lines on phones and 3 from 640 px with an ellipsis, and never overflow the card.
- [ ] **CVR-03** Check the 16:9 frame (cards) and the 160 px band (detail headers); card sizes do not change.
- [ ] **CVR-04** The title text is readable (white) in light and dark mode.
- [ ] **CVR-05** A screen reader reads the title once on cards (the cover text is decorative).
- [ ] **CVR-06** Uploading a real cover still replaces the fallback; the staff cover uploader still works.

## 9. Results page (BUG-001) and archived results

### 9.1 Student results list `/results`
- [ ] **RES-01** Log in as the dev student with sittings on standalone exams (the two submitted written exams). The page now **lists them**.
- [ ] **RES-02** A submitted written exam that is not yet marked shows under **"Being marked"** with an "awaiting result" pill; the score is not shown.
- [ ] **RES-03** After staff **publish** the written result, the row moves to **Results** with the score, outcome and a notice arrives (see section 10 for the staff side).
- [ ] **RES-04** A finished MCQ shows under **Results** with its score as soon as it is submitted (even before its deadline).
- [ ] **RES-05** An attempt still running or a submitted attempt with no visible score shows under the third tab; a retake shows the practice pill.
- [ ] **RES-06** Exams the student **missed or has not sat** do **not** appear on this page.
- [ ] **RES-07** MCQ / Written filter narrows all tabs, the summary and the badges.
- [ ] **RES-08** Summary (taken, passed, waiting) and tab badges count only the rows **loaded so far**; they correct themselves after "Load more". Known behaviour, note whether it bothers you.
- [ ] **RES-09** More than 20 sittings: "Load more" appears and loads the next page; an empty tab with more pages offers Load more.
- [ ] **RES-10** A written mark is **never** visible before publish (check the API response of `/api/v1/me/exam-results` too: `resultVisible` false, `score` and `passed` absent).
- [ ] **RES-11** Another student's sittings never appear; a staff account gets no access to this student endpoint.
- [ ] **RES-12** Empty student (no sittings): friendly empty state with a link to the routine.
- [ ] **RES-13** Error states: connection message with the API stopped.

### 9.2 Result page `/exam/<id>/result`
- [ ] **RES-20** Opening a result for an exam that is in **no package** works (score, outcome, questions, comment).
- [ ] **RES-21** **Archived exam**: staff archive an exam the student has sat; the exam page 404s as before (archived stays off the site), but the student's `/results` row still opens its **result** page with the title, type, score, outcome, review and marked-script button.
- [ ] **RES-22** In that archived case the leaderboard link is hidden and the rank shows "none".
- [ ] **RES-23** If both reads fail (a bad id) you see the not-found page.
- [ ] **RES-24** Known limit: in the archived case the question letters/script follow the interface language (the paper language is not sent).
- [ ] **RES-25** Regression: a normal published exam result page is unchanged (rank, leaderboard link, retake, evaluated PDF).

## 10. Related areas to re-test with Segments 7 and 8 (not tested yet)

- [ ] **REL-01** Staff marking and publishing a written result: the student gets the notice and the results page moves the row (same data).
- [ ] **REL-02** Leaderboard for a standalone exam and an archived one.
- [ ] **REL-03** Notices page and unread badge after a release.
- [ ] **REL-04** Staff marking browser (uncommitted marking work was in the tree during this run; its own tests are in `web/tests/ws9-mrk`).

## 11. Visitor console and session (BUG-002)

- [ ] **SES-01** Private window, logged out: open `/`, `/mcq`, `/packages`, `/about`, `/login`. The console shows **no red 401** on `/api/v1/auth/refresh`. You should see one `GET /api/v1/auth/session` returning `{"hasSession":false}` (status 200) and **no** refresh call.
- [ ] **SES-02** Log in, then reload any page: you stay logged in (probe `hasSession:true`, then one refresh, then `/me`).
- [ ] **SES-03** Log in on the student host and open the **admin/staff** host login: behaviour is as before (each audience only logs in on its own host). After login on the staff site, a page refresh keeps the admin logged in (regression from the empty-API-base incident).
- [ ] **SES-04** Expire or delete the refresh cookie (DevTools > Application > Cookies, name `refresh_token`): reload; you become anonymous with no error screen.
- [ ] **SES-05** Let the refresh token expire naturally (cookie present but invalid): one expected 401 on refresh, then anonymous; you are asked to log in again.
- [ ] **SES-06** Log out: reload shows anonymous without a 401. Log in again immediately: still works (the probe response is not cached).
- [ ] **SES-07** Probe response headers: `Cache-Control: no-store`; response body has no token.
- [ ] **SES-08** Slow connection: the page does not flash the signed-out header for a signed-in user longer than before (note any flash).
- [ ] **SES-09** Staging only: web build API base is **empty** (relative URLs). Check a built admin page refresh keeps the session.

## 12. Bangla wording (BUG-006)

- [ ] **BAN-01** The 404 page title and body use **"পেজ"** (not "পাতা").
- [ ] **BAN-02** Spot-check Bangla messages that mention a page: catalog (module/category not found), auth, purchase, notices, results, exam sitting, staff screens; all say "পেজ", natural grammar (পেজটি, পেজে, পেজের).
- [ ] **BAN-03** List pagination labels now read "পেজ বদলান", "আগের পেজ", "পরের পেজ", "{n}টির মধ্যে {m} নম্বর পেজ".
- [ ] **BAN-04** Left on purpose: "পাতার মাপে আনুন" (fit to PDF page) and the PDF page labels using "পৃষ্ঠা". Confirm.
- [ ] **BAN-05** The full translation review (REQ-011) is **not** done; note any other awkward Bangla you see for that task.
- [ ] **BAN-06** Decision recorded in `docs/exam-site/plans/ui-delivery/bangla-copy.md`.

## 13. Accessibility and responsive sweep

- [ ] **A11Y-01** Keyboard only (PUB-35): tab through the landing page, header menu, packages filters, category tabs, results tabs; focus is visible, nothing traps focus.
- [ ] **A11Y-02** Screen reader spot check: Menu button name, Exam group label, tab buttons (pressed state), footer icons, payment strip alt text.
- [ ] **A11Y-03** Tap targets at least 44 px on phones (Menu, tabs, filters, footer icons).
- [ ] **A11Y-04** Large system text / 200 % zoom: header and menus remain usable.
- [ ] **A11Y-05** Reduced-motion setting: no hover lift or transitions beyond minimal.
- [ ] **A11Y-06** Dark mode: logo, covers, cards, tabs, footer all readable.

## 14. Staging-only checks

- [ ] **STG-01** Header and menu on iOS Safari and a narrow Android Chrome.
- [ ] **STG-02** `/packages` and the landing categories against the **real catalogue** (volume, subcategory behaviour, prices).
- [ ] **STG-03** Payment strip loading time on a slow connection (image is 9561 px wide, 436 KB; the site serves a resized copy).
- [ ] **STG-04** Open Graph title and tab title show "Symam Law".
- [ ] **STG-05** Rate limits, mail limits and lockout timing (dev has them off).
- [x] **STG-06** The three new integration tests (`CategoryBranchExamsIT`, `MyExamResultsIT`, `SessionProbeIT`) were run on 31 Oct on a scratch database and pass. Run the full API suite again on CI or a machine with Docker. Locally it needs `SEED_DEV=false` and a `TEST_DB_*` database, because `api/.env` has `SEED_DEV=true` and the dev seed refuses to run outside the dev profile.

## 15. Known gaps and open items (record, do not fail)

- Publications, Blog, Media, Resources are "Coming soon" pages (decided, built later).
- Wide desktop (1280 px+) inner pages have no header link bar for visitors; only the logo and controls.
- Contact page still has placeholder details.
- App Store and Google Play links need real URLs (buttons stay "Coming soon").
- Bangla legal pages show English until a Bangla text exists.
- Large packages (over 100 exams) are not paged on the module page (decided: no).
- Full Bangla and English translation review (REQ-011) is pending.
- Test run 31 Oct: web 1,138 tests pass, `tsc --noEmit` clean, API 677 tests pass (scratch DB, `SEED_DEV=false`). ESLint has 3 pre-existing errors (below).
- Pre-existing lint errors (not from this work): `staff/enrolments/page.tsx` (assign to `module`), and two React rule errors in `student-shell.tsx` around the footer watcher.

## 16. Pagination, scrolling and sticky tabs (needs the 100 sample exams)

Load them with `docs/exam-site/qa/scripts/seed-100-exams.sql` (dev database only; run again any time, it replaces its own rows) and remove them with `remove-100-exams.sql`. They are named "Sample Exam 001 ..." to "100", spread over the four categories: 60 in Advocate Enrolment Exam, 20 in Bar Council Enrolment, 10 in BJS, 10 in Assistant Judge Recruitment. Every 4th is Written. A mix of free, paid and discounted, running now, upcoming, ended and practice.

- [ ] **PAG-01** `/mcq/c/<Bar Council Enrolment>` with the subcategory switch on: the first page shows 20 exams, and "Load more" adds the next 20 until all (about 62 MCQ) are shown; earlier cards stay in place and the page does not jump.
- [ ] **PAG-02** Same category with the switch off: only the exams directly in Bar Council Enrolment (about 15 MCQ) show, no "Load more" needed.
- [ ] **PAG-03** Each tab keeps working with paging: Free (about 21 MCQ, 2 pages), Live (about 25), Archived (about 24). Changing tab starts again from the first page and the URL has `?view=`.
- [ ] **PAG-04** Written (`/written/c/...`): the same checks with about 20 Written exams in the tree.
- [ ] **PAG-05** **Desktop (1024 px and up): the left column (tabs and the checkbox) stays in place while you scroll the long list**, under the header, and does not overlap the footer. The list scrolls on its own beside it.
- [ ] **PAG-06** The tabs column stays usable after "Load more": the pressed tab keeps its focus and highlight.
- [ ] **PAG-07** **Phone: unchanged.** The tabs and the checkbox are one row above the list and scroll away with the page (decision pending: say if you want them stuck on phones too).
- [ ] **PAG-08** Card variety looks right in a long list: a free exam, a paid one, a discounted one (price, struck price, "% off", end date), a practice one ("Date to be announced"), an upcoming one, a running one ("Live now").
- [ ] **PAG-09** Scroll position: after "Load more", back and forward in the browser keep the tab and the loaded list sensible (note what you see).
- [ ] **PAG-10** Performance feel: scrolling through 60+ cards stays smooth; hover colour change works on every card.

## 17. Student sees their own submitted PDF (written exams)

- [ ] **PDF-01** Log in as Student 1 (`+8801711000011`, `root1234`), open `/results`, open a written exam's result. A card **"Your submitted answer"** is shown above the marked script. Bangla: "আপনার জমা দেওয়া উত্তরপত্র".
- [ ] **PDF-02** "View your answer" opens the student's own uploaded PDF in the viewer (pages, zoom as for the marked script); "Close" hides it. Download is not offered, as for the marked script.
- [ ] **PDF-03** It shows **before marking and before publish** (status "Being marked"), after publish (next to the marked script) and for a written exam that is in no package.
- [ ] **PDF-04** It shows for an exam that staff **archived** after the student sat it (result page falls back to the result data).
- [ ] **PDF-05** MCQ results never show this card.
- [ ] **PDF-06** Security: a different student cannot open it (API `GET /api/v1/live-exams/{id}/answer-pdf` answers 404 for them, 401 without a login). A student who did not upload a PDF (failed to submit) sees no card.
- [ ] **PDF-07** Sit a new written exam as a student, upload a real PDF, then open the result: the PDF you uploaded is the one shown.
