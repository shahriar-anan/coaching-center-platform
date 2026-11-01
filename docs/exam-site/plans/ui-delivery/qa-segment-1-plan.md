# QA Segment 1 plan: landing page, header, footer and packages

Status: plan only, nothing built. Written for Shahriar (orchestrator) and the agents that build it.
Source: `docs/exam-site/qa/qa-report.md` (dev build on `demo/client-review`, ahead of staging). Code checked on 2026-10-31.
Decision kept: the old site styles the landing page `/` only; the client mockup styles every other student page (`decisions.md`, "Landing page and the client mockup"). `/packages` is not the landing page, so it follows the mockup.

## 0. Scope of this plan

Shahriar's order: **the landing page first, including the header and the footer.** This file plans only that workstream. It covers:

| ID | What | Source |
|---|---|---|
| REQ-001 | Rename "Exam Portal" to "Symam Law" | report |
| REQ-002 | Prominent Symam Law brand in the hero | report |
| REQ-003 | Landing exam-category section, type first | report |
| REQ-004 | Footer rebuild and exact legal pages | report |
| REQ-005 | About Us on phones (follows REQ-004 text) | report |
| REQ-006 | Redesign the sections below the doors | report |
| REQ-010 | Header items not built: **decided, option B** (outline below) | report + decision |
| BUG-003 | Header "Exam" goes straight to MCQ | report |
| BUG-005 | Sidebar-style icon on phones | report |
| **HDR-M** | **New.** Phone header shows the logo only, no name text | Shahriar, 31 Oct |
| **PKG-1** | **New.** A dedicated Packages listing page with a category filter, and the header "Packages" link points to it | Shahriar, 31 Oct |

**Not in this plan yet** (to be planned when Shahriar gives the order): BUG-001 (results analysis first), BUG-002, BUG-004, BUG-006, REQ-007, REQ-008, REQ-009, REQ-011, REQ-012. REQ-012 (default cover) touches `PackageCover`, which PKG-1 uses; PKG-1 must not change that component.

## 1. Decisions taken (from Shahriar's answers)

1. **Header, option B.** All eight items show on desktop and on phones: Admission form, Exam, Packages, Notices, Publications, Blog, Media, Resources. Publications, Blog, Media and Resources keep the existing `ComingSoonPage`. They are built later (their own plans).
2. **Packages gets its own page**, with a category filter. Layout is chosen in PKG-1 below.
3. **Phone header shows the logo image only.** The name text beside it is hidden below 640 px (confirmed, 31 Oct).
4. **The brand is "Symam Law" in both languages** (confirmed, 31 Oct). It is a brand name, so Bangla does not transliterate it. The hero label "SYMAM LAW · সাইমাম ল'" becomes "Symam Law".
5. **No hamburger on phones** (Shahriar, 31 Oct, from a screenshot of the signed-in student header: panel icon, logo, bell, hamburger, "Student"). The language and theme switches and the other menu items move into the **account menu**, whose trigger shows a **profile icon beside the user's first name** (e.g. "Student"), on phones and on desktop. Today the icon shows only below 380 px.
6. **Old legal text is fetched from the old site over SSH and copied exactly** (confirmed, 31 Oct).
7. **Agents:** two at a time, model Haiku 5.5. Shahriar is told only when a step (wave) finishes.

Defaults used where Shahriar has not answered yet are marked **(default)** and listed in section 5.

## 2. What the code does today (verified)

- `student-shell.tsx:347-374` `WideNav`: the eight links show only from 1280 px (`xl:flex`). **Below that, phones have no way to reach any of them.** "Exam" and "Packages" both link to `/mcq` (lines 353-354). "Admission form" links to `/register`.
- `student-shell.tsx:148-170` `SettingsMenu`: a hamburger icon, visible below 400 px, holding the language and theme pills. This is the likely "sidebar open icon" of BUG-005 (**unconfirmed**; the real sidebar toggle at line 435 is for signed-in students only). Needs the tester's phone width or screenshot.
- `logo.tsx:51`: the name beside the mark hides only below 400 px (`compact` -> `max-[399px]:sr-only`). The `Logo` component is also used by the staff shells and the footer.
- **The logo image already contains the words "SYMAM LAW"** (`public/brand/logo-light.png`). After the rename the text beside it repeats the name on desktop.
- "Exam Portal" strings: `en/common.ts:7` (+ `brand.first/second` lines 5-6), `en/account.ts:61`, `en/auth.ts:29`, `en/home.ts:23`, `en/legal.ts:60`, and the Bangla "এক্সাম পোর্টাল" in `bn/common.ts:7`, `bn/account.ts:61`, `bn/auth.ts:29`, `bn/legal.ts:60`, `app/layout.tsx:7-8` (page title and template), `(site)/page.tsx:10,13` (title and Open Graph), `catalog-helpers.ts:188` (calendar `PRODID`). The report lists only the English files.
- `landing/categories.tsx` and `landing-data.ts:39-60`: one card per top-level category, but cards are **filtered by module count** (line 59). On dev, BJS has no package (its exams are standalone), so it is hidden and only "Bar Council Enrolment" shows. This explains REQ-003's "one package card". The card link defaults to MCQ when the category has MCQ (`landing-data.ts:52`): the same type-first fault as BUG-003.
- `landing/hero.tsx:19,28-33`: the brand is a small `text-label` line; Online batch / Offline batch are `<li>` chips, not buttons (the report calls them buttons).
- `site-footer.tsx`: contact, company links, social links as **text**, app buttons greyed out behind `APP_AVAILABLE = false`. `APP_STORE_URL` is Apple's own "Apple Store" app (id375380948) and `GOOGLE_PLAY_URL` is an Expo build link, both wrong. No payment strip.
- `public/brand/payment-methods.png` exists and **nothing uses it**; the purchase sheet shows bKash, Nagad, Rocket and Visa separately (`purchase-sheet.tsx:119-122`).
- `legal-page.tsx` shows a warning banner and two short paragraphs from `legal.ts`; all four pages say "The final ... will be added soon". The old-site text is not in the repo.
- Routes `/publications`, `/blog`, `/media`, `/resources` render `ComingSoonPage` (`robots: noindex`). `/notices` is the signed-in student's inbox and sends visitors to login.
- Packages data: `GET /api/v1/public/live-exam-modules` already takes `categoryId`, `examType`, `q`, `popular`, `page`, `size` (`LiveExamPublicController.java:79-93`). `PackageCard` (`components/ui/package/package-card.tsx`) exists. **No API change expected**; one thing to verify (PKG-1).

## 3. Items

### HDR-M (new) · Phone header: logo only
- **Understanding:** on phones the header holds the logo mark, the name text, pills, a hamburger and the login pill; the text is cut off. Shahriar wants only the logo mark on phones.
- **Approach:** add a `Logo` prop (name `nameFrom`, default unchanged) so the student header hides the text below `sm` (640 px) and shows it from `sm` up **(default; confirm width)**. Keep the text for screen readers (`sr-only`, as now). Staff headers and the footer logo keep their behaviour. Files: `logo.tsx`, `student-shell.tsx:444`.
- **Edge cases:** the link keeps its `aria-label` (`common.brand.home`). If Shahriar decides desktop also drops the text (the image already says Symam Law), this becomes a one-line change. At 320 px check the login pill and the menu button still fit.
- **Tests:** none automated. Manual at 320, 360, 390, 640 px, both languages, large text.
- **Size:** S. **Depends on:** REQ-001 (new name string). **Blocks:** nothing.

### REQ-001 · Rename to Symam Law
- **Understanding:** every user-visible "Exam Portal" / "এক্সাম পোর্টাল" becomes Symam Law. See the verified list in section 2.
- **Approach:** English and Bangla both "Symam Law" (confirmed 31 Oct; a brand name, not transliterated). The hero label `landing.heroLabel` becomes "Symam Law" in both files; Bangla body text that mentions the brand uses the Latin form too. Change `brand.first/second` to a single name (keep two keys only if the two-colour split is wanted; default: one key `brand.name`, the accent colour dropped or applied to "Law"). Update every file in section 2, page titles in `layout.tsx` and `(site)/page.tsx`, the calendar `PRODID`. Then grep both languages for "Exam Portal", "এক্সাম পোর্টাল" and "এক্সামপোর্টাল". Check `api/src/main` for email, receipt and PDF text and report anything found; do not edit the API in this item.
- **Edge cases:** `legal.ts:60` is replaced anyway by REQ-004, so rename there only if REQ-004 has not landed. The "Exam Portal" wording in the mockup HTML is a doc, not shipped. Tests that assert the old strings must be updated (grep `web/tests` for them).
- **Tests:** update the few tests that assert the old strings; the existing bn/en key-parity test still has to pass. Manual: PUB-06 and a grep result.
- **Size:** S-M (many files, trivial edits). **Depends on:** the Bangla form (question 7). **Blocks:** HDR-M, REQ-002, REQ-004.

### BUG-003 + REQ-010 · Header navigation, type first, eight items on phones
- **Understanding:** the "Exam" item opens MCQ directly, "Packages" opens the same place, and phones cannot reach the menu at all. Option B means all eight items show on every width.
- **Approach:**
  - Replace the `WideNav` list with one shared item list used by two views: the desktop bar (from 1280 px, unchanged) and a new **phone/tablet menu** (below 1280 px).
  - **Exam** becomes a two-entry dropdown: MCQ exams (`/mcq`) and Written exams (`/written`). The user always chooses the type **(default: dropdown, no chooser page)**. On the phone menu the same two entries sit under an "Exams" group.
  - **Packages** links to `/packages` (PKG-1).
  - **Admission form** keeps `/register` and is relabelled "Create account" or kept as is **(default: keep the label, since the old site's admission form is a different thing; ask client, question 2)**.
  - **Notices** keeps `/notices`. For a visitor that goes to login and back. **(default: keep it visible; ask client, question 4.)**
  - Publications, Blog, Media, Resources keep `ComingSoonPage`.
  - Also check footer links that point at `/mcq` for "Exam".
  - **Updated 31 Oct:** the stand-alone hamburger (`SettingsMenu`) is **removed on phones everywhere** (BUG-005).
    - **Signed in:** the `UserMenu` trigger always shows the profile icon (`CircleUserRound`) beside the first name, on phones and desktop (today the icon is hidden above 380 px and the name hidden below it). On phones the menu also holds the language and theme controls (`LocalePill`, `ThemePill`). The pills stay in the bar from 400 px up as now, or move wholly into the menu on phones: **move them into the menu below `sm` (default)**. Long names truncate (`max-w-36`).
    - **Visitor (not signed in):** there is no account menu, but the eight header links must still be reachable on phones (option B). Use one **text button "Menu"** (a label, not a hamburger icon) that opens the links, the language and theme controls. This is the only extra control and it needs Shahriar's OK (question 6).
    - Also check the panel icon at the left of the signed-in header (screenshot): the sidebar toggle is `hidden lg:inline-flex` (`student-shell.tsx:440`) and should not show on phones; if it does at phone width, hide it below `lg`.
  - Files: `student-shell.tsx` (nav and menu), new `components/shell/site-menu.tsx`, `i18n/messages/{bn,en}/landing.ts` and `common.ts` (menu and type labels).
- **Edge cases:** keyboard (Escape closes, focus returns), the header hides on scroll down (`useHeaderHidden`): an open menu must keep the header shown. Signed-in students keep their own tab bar and top links, so the site menu is for visitors and staff only. Active-link state. RTL is not a concern (Bangla is LTR).
- **Tests:** a small component test for the menu: it lists eight items, Exam shows two type entries, none of them goes to `/mcq` without the user picking. Manual: PUB-05, PUB-03, keyboard only.
- **Size:** M. **Depends on:** PKG-1 route for the Packages href (a string only), REQ-001. **Blocks:** REQ-003 (shares the type-first rule).

### BUG-005 · Icon on phones, landing page
- **Understanding:** a "sidebar open" icon appears on phones on the landing page. Most likely the `SettingsMenu` hamburger (verified: visible below 400 px). A true sidebar toggle is not rendered for visitors.
- **Approach:** resolved by the menu in the previous item: one clearly labelled Menu button, and no stray hamburger. **If the tester's screenshot shows a different icon, stop and re-check.** Confirm the scope: all logged-out public pages or only the landing page.
- **Edge cases:** the report note says to confirm; a hamburger that opens a nav menu may be exactly what the tester dislikes (question 6).
- **Tests:** manual PUB-28 at 320-640 px. **Size:** S (inside the menu work). **Depends on:** question 6. **Blocks:** nothing.

### REQ-002 · Brand prominent in the hero
- **Understanding:** the hero's "SYMAM LAW · সাইমাম ল'" is a small `text-label` line above the headline (`hero.tsx:19`).
- **Approach:** make the brand a clear lockup above the headline: larger, heavier, accent colour, with the logo mark; keep the headline, the Online/Offline chips, the doors and the sign-up button. Add it as `<p>`, keeping the headline as the only `<h1>`. Both languages; checked at 360 px.
- **Edge cases:** do not push the doors below the fold on phones; hero text should not repeat the header brand twice in a row on desktop (decide with the logo question 4).
- **Tests:** manual only. **Size:** S. **Depends on:** REQ-001. **Blocks:** nothing.

### REQ-003 · Landing exam categories, type first
- **Understanding:** the landing section shows only categories that have packages, as package-style cards (cause verified: `landing-data.ts:59`). The old site shows the main exam categories as simple cards. The new rule: **type first, then category, then module, then exam**.
- **Approach:** an **MCQ / Written switch at the top of the section** **(default, option a)**. The cards list the top-level categories for the chosen type (`useCategoryTree(type)` already filters by `examType`) and each card links to `/mcq/c/<id>` or `/written/c/<id>`. No card ever opens a type the user did not choose. Card content: title, exam and package counts for that type, "from ৳" for that type. Style: old-site pattern (rounded card, blue exam icon, title, Explore button) in the current theme.
  - **Count exams, not only packages.** A category with exams and no packages (BJS on dev) must show. Use `countBranchExams` and `countModules` from `catalog-helpers.ts`, as `DoorPage`'s `CategoryCard` does.
  - Categories with nothing for the chosen type are hidden; a category with no content at all is hidden on the landing page (a "coming soon" card stays on the door pages).
  - The two doors in the hero stay; the new section sits after them, so the switch and the doors do not compete: the doors go to the type pages, the switch narrows the cards.
  - Files: `landing/categories.tsx`, `landing/landing-data.ts` (`categoryCards` takes the type), `landing.ts` messages.
- **Edge cases:** one-type categories, empty trees, parent categories with children, a price only for the chosen type, the tree loading state.
- **Tests:** unit tests for `categoryCards(nodes, type)` (type filter, exams-only category visible, links use the chosen type). Manual PUB-06, PUB-19.
- **Size:** M. **Depends on:** question 2, REQ-001, header work for wording. **Blocks:** REQ-006 (same file area).

### REQ-006 · Sections below the doors
- **Understanding:** Upcoming exams, Popular/packages ("Featured"), Steps, Contact, Closing, Numbers are called "very badly designed". The code is `landing/{numbers,featured,upcoming,steps,contact,closing}.tsx`.
- **Approach:** **Updated 31 Oct: Shahriar delegated the design, so there is no review gate; the decided design is section 9 and is built straight away.** (Original idea: one agent produces a short design note (a static HTML or a markdown with per-section layout, empty and long states, past-exam handling, phone layout) in `docs/exam-site/plans/ui-delivery/landing-sections-design.md`. Shahriar reviews it. Only then a second agent builds. Rules: old-site palette and wide layout (`/` only), one section rhythm (as `landing-page-plan.md` section 3), only real data (`usePopularPackages`, `useUpcomingExams`, counts from the catalog), nothing animates by itself.
- **Edge cases:** past exams are not listed on the landing page (`upcoming` already excludes them); no data hides a section; long Bangla titles.
- **Tests:** manual 360/768/1280, light and dark. **Size:** L (design M + build M). **Depends on:** REQ-003 done (same area), a client style opinion. **Blocks:** nothing.

### REQ-004 · Footer rebuild and exact legal pages
- **Understanding:** the footer needs icons for App Store, Google Play, Facebook, WhatsApp, LinkedIn and YouTube, the old site's payment strip, and the four legal pages must be the old site's text, word for word.
- **Approach:**
  1. **Fetch the old text read-only** over the SSH route in memory `old-site-ssh` (Windows OpenSSH from PowerShell, not Git Bash; pipe scripts with the BOM-safe `tr -d '\r' | sed ... | bash -s` form). Look for the four pages in the Next.js frontend (`/root/symamlaw/symamlaw-frontend`) and the Laravel database/CMS (`/var/www/html/symamlaw-backend`; the footer's `backoffice.symamlaw.com/api/settings` is already public). Save the raw files in the scratchpad, not the repo. No writes on the server.
  2. **Verify word for word:** after the copy, a script extracts the plain text of each page from the new message file and from the saved original, strips markup and whitespace, and compares them. The result must be an empty diff; attach the diff output to the hand-over note. No paraphrasing, no spelling fixes. If a page exists in one language only, say so and ask.
  3. Replace `legal.ts` bodies with structured blocks (headings, paragraphs, lists) and extend `legal-page.tsx` to render them; remove the "placeholder" banner. Keep the Bangla and English files in step.
  4. Footer: icons for the four social links (`lucide-react` has no brand marks for all; use SVG files in `public/brand/social/`), store badges as images, real URLs, `target="_blank"` with `rel="noopener noreferrer"`, alt text. Keep `APP_AVAILABLE` and the "coming soon" state until real store URLs arrive (default; question 9).
  5. Payment strip: first compare `public/brand/payment-methods.png` with the old footer's image (old site section 11 says a very wide SSLCommerz image). Reuse as is; make it responsive (`max-w-full`, `h-auto`, no horizontal scroll), alt text "Accepted payment methods: ...".
  - Files: `site-footer.tsx`, `legal-page.tsx`, `legal.ts` (both), `common.ts` footer keys, `public/brand/social/*`, `public/brand/payment-methods.png`.
- **Edge cases:** the old text may contain Bangla and English mixed, tables, and brand names ("SYMAM LAW" must stay as copied, even where REQ-001 would rename "Exam Portal"). Mentions of old-site payment rules for refunds are copied unchanged (legal meaning). The minimal footer (sign-in pages) shares the legal links.
- **Tests:** the text-diff check above; the existing bn/en parity test. Manual PUB-13 to PUB-16, footer on 320 px.
- **Size:** L (fetch, exact copy, footer). **Depends on:** SSH access and the icon files; real URLs from the client. **Blocks:** REQ-005.

### REQ-005 · About Us on phones
- **Approach:** after REQ-004, a polish pass on `/about` only at phone widths: reading width, headings, spacing, no overflow. Ask the client what they disliked (question 11) before changing the layout. **Size:** S-M. **Depends on:** REQ-004.

### PKG-1 (new) · Packages page with a category filter
- **Understanding:** the header "Packages" item has no page. Shahriar wants a dedicated list with a category filter.
- **Approach:** route `/packages` (`app/(site)/packages/page.tsx`), component `components/student/catalog/packages-page.tsx`, a hook `usePackages({categoryId, examType, q, page})` in `lib/api/exam/catalog.ts` that calls `GET /api/v1/public/live-exam-modules` (infinite query, as `useCategoryExams` does). Messages in `{bn,en}/catalog.ts` or a new `packages.ts`.
  - **Layout (chosen):** the standard 768 px student column (mockup rule). On top: a page header, then one filter bar: a search box (`q`), a **Category** select (the category tree, indented, with "All categories"), and the existing `TypeFilter` (All, MCQ, Written). Below: a grid of `PackageCard`, 1 column on phones and 2 from `sm`, with a "Load more" button (the API pages by `size`; default 12, "load more" fits the existing infinite query). A count line "N packages". Empty state when nothing matches with a "Clear filters" action. Selected filters live in the URL (`?category=&type=&q=`), like `useTypeFilterParam`.
  - On phones the filter bar stacks; the category select is native for easy use.
  - The card's cover uses `PackageCover` unchanged (REQ-012 is separate).
- **Resolved 31 Oct:** `categoryId` matched the exact category only. The API now takes an optional `branch` flag on `GET /api/v1/public/live-exam-modules` (default false, so existing callers are unchanged), using the same `branchOf` walk as the category exam list; the Packages page always sends `branch=true` with a category. Files: `LiveExamModuleRepository.findPublishedCatalog` (`categoryIds` set), `LiveExamPublicService.modules`, `LiveExamPublicController.modules`.
- **Also 31 Oct:** the visitor Menu button shows on every public page; language and theme are plain menu items (keyboard reachable); "Exam Portal" is gone from `EmailTemplates.java` and `LiveExamReceiptPdf.java`.
- **Original check, kept for the record:** whether the API's `categoryId` filter includes sub-categories. `LiveExamPublicService.modules` passes it to `findPublishedCatalog`; if it matches only the exact category, a parent category would show no packages. If so, either send the category and all descendant ids (needs an API change) or add `branch=true` like the exam list. Report to Shahriar before changing the API.
- **Edge cases:** hidden (archived) categories, packages with no price ("no price" text), a free package, a discount (`pricing`), 0 results, a very long list, signed-in students (`favourite`, access state, same card).
- **Tests:** a component test for the filter-to-URL and the query string; a unit test for the URL builder. Manual with the dev data (1 package on dev, so volume cannot be judged; say so).
- **Size:** M. **Depends on:** question 3 (page wanted, confirmed), the API check. **Blocks:** the header Packages link (string only).

## 4. Orchestration: waves, agents and files

I (the main session) act as the orchestrator: I brief each agent, check the diff against this plan, merge, and do not let two agents edit the same file at once. Each agent works in its own git worktree; I merge in the order below. No agent commits, deploys, runs the API or touches any database or server except the read-only old-site fetch in A4.

**Updated 31 Oct:** two agents at a time, Haiku 5.5, in the main working tree (they own separate files, no worktrees), no commits. Wave 1 is A1 and A2 only; the legal-text fetch moves to wave 2. Shahriar gets one report when each wave finishes. A2 puts its messages in `catalog.ts` (no new registered file) so it does not touch `bn.ts` or `en.ts`.

**Test budget:** no full build, no full test run, no end-to-end runs. Each agent runs only: lint on the files it changed, the single bn/en parity test (if it is quick), and its own small new tests. Shahriar does the real testing in the browser. The manual checks in section 3 are his checklist.

| Wave | Agent | Items | Owns (files) | Runs with |
|---|---|---|---|---|
| 1 | **A1 Brand and header** | REQ-001, HDR-M, BUG-003, BUG-005, REQ-010 (option B) | `logo.tsx`, `student-shell.tsx`, new `site-menu.tsx`, `layout.tsx`, `(site)/page.tsx`, `catalog-helpers.ts:188`, message files: only the `brand`, `nav`, `landing.nav*` and rename lines | A2, A4 fetch |
| 1 | **A2 Packages page** | PKG-1 | `(site)/packages/`, `packages-page.tsx`, `catalog.ts` (hook only), a new `packages.ts` message file (registered in `bn.ts`, `en.ts`) | A1, A4 |
| 2 | **A4a Old-site text fetch** | REQ-004 step 1 (read-only) | scratchpad only; writes nothing in the repo | A3, A4b |
| 2 | **A3 Hero and categories** | REQ-002, REQ-003 | `hero.tsx`, `categories.tsx`, `landing-data.ts`, `landing.ts` (hero and category keys) | A4b (different files) |
| 2 | **A4b Footer and legal pages** | REQ-004 steps 2-5, REQ-005 | `site-footer.tsx`, `legal-page.tsx`, `legal.ts`, `common.ts` (footer block), `public/brand/**` | A3 |
| 3 | **A5 Sections design** | REQ-006 design note | docs only | none |
| 4 | **A6 Sections build** | REQ-006 build | `landing/{numbers,featured,upcoming,steps,contact,closing}.tsx` | after Shahriar approves the design |

**Clashes to watch:** `student-shell.tsx` (A1 only), `common.ts` (A1 edits `brand`/`nav`, A4b edits `footer`: separate blocks, merge by hand), `landing.ts` (A1 edits `nav*` keys in wave 1, A3 edits other keys in wave 2: sequential), `legal.ts` (A1 must not touch the bodies; A4b replaces them), `bn.ts`/`en.ts` (A2 adds one import line; I do it at merge). A3 starts after A1 is merged so the shared brand and landing keys are stable.

**Order:** merge A1, then A2, then A3, then A4b, then A5 and review, then A6.

**Checkpoints for Shahriar:** (1) after wave 1: header, phone menu, packages page; (2) after wave 2: hero, categories, footer, legal text and the diff report; (3) the sections design before A6 builds.

## 5. Questions for the client or the tester (with the default I will use)

1. Header Exam: dropdown or chooser page (BUG-003)? **Dropdown.**
2. Admission form: keep the label and link to `/register`, relabel it, or hide? **Keep and link to `/register`.** (The old site's admission form is for offline courses and is a different thing.)
3. Packages page wanted: **yes, confirmed by Shahriar.** Packages across both exam types with a type filter: **yes.**
4. Does desktop keep the name text next to the logo, given the image already says Symam Law? **Keep it.**
5. Notices for logged-out visitors: keep the link (goes to login) or hide it? **Keep it visible.**
6. BUG-005: signed-in header answered by the screenshot (hamburger removed, items go into the account menu with a profile icon). For **visitors** on phones, is a text button "Menu" acceptable so the eight links stay reachable? **Yes.**
7. Bangla form of the brand? **Answered: "Symam Law" in both languages.**
8. Phone width at which the name text hides? **Answered: below 640 px.**
9. Real App Store and Google Play URLs, and are the apps published? **Not yet: store buttons stay "Coming soon".**
10. Social URLs: are the four in `site-footer.tsx` the official ones; is Facebook `facebook.com/symamlaw` instead? **Keep the four current links.**
11. What does the client dislike about About Us on phones? **Reading width and spacing only.**
12. REQ-003: MCQ / Written switch at the top of the section (a) or a section after the two doors (b)? **(a).**
13. Do all four legal pages exist in both languages on the old site? **Copy what exists; flag any gap.**

## 6. Assets needed

| Asset | Where from |
|---|---|
| Social and store icons (Facebook, WhatsApp, LinkedIn, YouTube, App Store, Google Play badges) | Client's files when supplied; otherwise official brand SVGs checked in `public/brand/social/` |
| Payment-methods strip | `public/brand/payment-methods.png` already in the repo; compare with the old footer image on the VPS (read-only) |
| Old legal and About text | Old frontend source and CMS over `client-vps` (read-only); public `backoffice.symamlaw.com/api/settings` |
| Old category section reference | `landing-page-plan.md` rows 6-7, the live `symamlaw.com` page |
| Real store and social URLs | The client |

## 7. Release notes

- **Needs a Bangla and English review:** the new brand name, the phone menu labels, the Exam dropdown entries, the category section text, the Packages page text and filters, the footer labels. The four legal pages are copied from the old site and must **not** be edited by the reviewer.
- **Check on staging, not only on dev:** the browser title and Open Graph name, the footer on a phone, the payment strip on a slow connection, the Packages page against the real catalogue (volume and `categoryId` behaviour), the landing categories against real data (dev has one package and two categories), the header on iOS Safari and a narrow Android Chrome.
- **Dev build is ahead of staging** (uncommitted marking work). None of these items touch it.

## 8. Found while planning (not in the report)

- `(site)/contact/page.tsx` text still has placeholders: phone `01700-000000`, email `info@example.com`, address "Dhaka" (`en/legal.ts:63-73`). The footer has the real details.
- `site-footer.tsx:35-36`: both store URLs are wrong (Apple's own app, an Expo build link).
- `public/brand/payment-methods.png` is unused; the purchase sheet shows separate logos and omits SSLCommerz.
- `landing/categories.tsx` `categoryCards` hides categories that have exams but no packages (`landing-data.ts:59`).
- REQ-002 calls Online/Offline "buttons" but they are non-interactive chips (`hero.tsx:28-33`); decide whether they should link anywhere.
- "Archived" has two meanings: a staff status that hides an exam from students (`decisions.md`, exam archive) and REQ-007's "live window ended". Needs a name before REQ-007 is planned.

## 9. REQ-006 design (decided 31 Oct, built without a review gate)

Sources: general ed-tech landing guidance (a clear section per job, consistent cards, social proof and a how-it-works strip, one closing call to action) and test-series sites that show the next scheduled test with its date and time. Web search found no single authoritative guideline, so these are common patterns, not a standard. Applied to this site's cream, navy and gold theme, the old-site wide layout (`/` only), Bangla first.

**Rules for every section:** one `<h2>`, a centred title with a short gold bar under it and an optional one-line subtitle; sections alternate plain cream and a faint accent tint (`bg-accent/5`, the category section keeps `bg-accent/10`), with navy bands only for the numbers strip and the closing call to action; real data only, a section with no data renders nothing; no motion except a hover lift (`motion-reduce` safe); 44 px tap targets, visible focus rings; works at 320 px with no sideways page scroll.

1. **Numbers strip (navy):** three stat tiles with a lucide icon each (packages, MCQ exams, written exams), a thin divider between tiles on desktop, big gold figure, small label. Same data as today.
2. **Upcoming exams ("Live next"):** a **spotlight card** for the soonest exam (navy, large date block with day number and month, time, type pill, title, package name, price or "Free", a "View exam" button and a relative label: Today, Tomorrow, In N days) and, beside or under it, a **compact list** of the next four exams: each row is a date chip (day over month), title, type pill, time, chevron. Desktop: spotlight left, list right; phone: spotlight then list. Links go to the exam page. Hidden when empty. Past exams never appear (the API lists only upcoming).
3. **Packages (featured):** reuse `PackageCard` unchanged (the same card as `/packages`). Popular packages first, then the rest, up to six. Desktop: three columns, two rows; phone: a **horizontal snap-scroll row** with the next card peeking (cards about 80 % wide). "See all packages" goes to `/packages` (it goes to `/mcq` today).
4. **How it works:** three steps with a lucide icon in a gold-ringed circle, a step number, title and text, joined by a thin line on desktop (vertical on phones). Under the steps, a "Secure payment" row with the payment logos from `public/brand/payment/` (bKash, Nagad, Rocket, Visa) as small images with alt text, plus the text "Secure payment via SSLCommerz" in both languages.
5. **Contact:** one panel in two columns instead of six boxes. Left: the phone numbers as large tap-to-call buttons and a WhatsApp button. Right: address with the map link, office hours, email, and the social icons (reuse `components/shell/social-icons.tsx`, same URLs as `site-info.ts`). Stacks on phones.
6. **Closing call to action (navy):** gold decorative circles like the category covers, the existing title and lead, two buttons ("Create account", "Browse packages" to `/packages`) and the existing login link.

Not included (no real data): testimonials, invented numbers, countdown timers.

## 10. Wave 3 (chosen 31 Oct): browse filters, not-found message, default cover; BUG-001 analysis in parallel

**Why this next:** REQ-007 is P1 before launch and sits on the catalogue pages that the landing page, header and Packages page now lead into. BUG-004 and REQ-012 are small and S3/P2. BUG-001 (S2) needs analysis first and runs in parallel, planned in section 11.

**Report vs code (followed the code):**
- REQ-009 says pagination does not exist. The category exam list already pages (`CATEGORY_EXAM_PAGE = 20`, a "Load more" button in `category-exams.tsx`); it was only invisible with six exams. The module page fetches its exams with `size=100` in one request (`usePublicModuleExams`). So REQ-009 reduces to: keep the category list paged, make the new tabs work with paging, and decide the module page (below).
- The API already knows the exam phases `UNANNOUNCED`, `UPCOMING`, `LIVE`, `ENDED`, `PRACTICE` (`ExamWindow.phase`) and filters category exams by `mode` and `phase` in memory (`LiveExamPublicService.categoryExams`).
- "Archived" has a second meaning in the staff area (exam archived off the student site). The student tab means "the live window has ended" (REQ-007). The tab is labelled **Archived** in English and **আর্কাইভ** in Bangla because the client asked for that word; staff-archived exams never reach students, so the two cannot mix on a student page.

**Decisions (REQ-007, answers to its questions a-e):**
- **Tabs: All, Free, Live, Archived.** One new API query parameter `view` (`ALL` default, `FREE`, `LIVE`, `ARCHIVED`) on `GET /api/v1/public/live-exam-categories/{id}/exams`, applied in `categoryExams` before paging so counts and pages stay correct.
  - `FREE`: the exam is free (no price to pay), any mode, including practice.
  - `LIVE`: mode LIVE and not ended (phases `UNANNOUNCED`, `UPCOMING`, `LIVE`). (a) means "live and still to come or running".
  - `ARCHIVED`: mode LIVE and phase `ENDED`.
  - Practice exams appear under All and Free only, never Live or Archived (b).
- (c) The selected tab lives in the URL as `?view=` (invalid value means All). (d) It combines with the "include subcategories" switch and with "Load more" (changing the tab or the switch starts a new list). (e) No counts on the tabs.
- Where: the category page list (`category-exams.tsx`) uses the API parameter. The module page list (`module-page.tsx`, all exams fetched at once, at most 100) filters on the client with the same rules from each exam's own `liveAt`/`endAt`/mode.
- **REQ-008:** the "include exams in subcategories" switch renders only when the category has subcategories (`hasChildren`).

**Other items in this wave:** BUG-004 (an unknown exam, category or module id shows "not found", a real network failure keeps the connection message; both languages), REQ-012 (`PackageCover` shows the title, not the initials, on the same background).

**Agents:** A7 (REQ-007, 008, 009; API, `catalog.ts`, `category-exams.tsx`, `module-page.tsx`, catalog messages) and A8 (BUG-004, REQ-012; `app/error.tsx`, exam, module and category pages' error handling, `package-cover.tsx`, error messages). Haiku, same rules and test budget as before.

## 11. BUG-001 analysis and fix (31 Oct)

### How the result system works (verified in code)
- **Participations.** One row per attempt in `live_exam_participants` (`LiveExamParticipant`: `examId`, `studentId`, `attemptNo`, `kind` LIVE or retake, `status` IN_PROGRESS / SUBMITTED / FAILED_TO_SUBMIT, `startedAt`, `deadlineAt`, `submittedAt`, `score`, `passed`, `publishedAt`, answer and evaluated-PDF file names).
- **Status the student sees.** `LiveExamStudentStatus.of(...)` derives it from the exam window, the server clock, the student's access and their latest attempt: NOT_ANNOUNCED, UPCOMING, OPEN, IN_PROGRESS, SUBMITTED, AWAITING_RESULT, RESULT_READY, FAILED_TO_SUBMIT, MISSED (legacy vocabulary) plus a finer `myStatusDetail`. MCQ: RESULT_READY once the attempt's deadline has passed (SUBMITTED before that, although the score is already posted and readable). Written: RESULT_READY only after staff publish (`publishedAt`), AWAITING_RESULT after the deadline until then, FAILED_TO_SUBMIT if no PDF was stored.
- **Release.** MCQ is auto-marked and posted on submit; answers and explanations come with the result at once (D21). Written marks, comment and evaluated PDF stay hidden until `publishedAt` is set (`LiveExamSittingService.result`, lines 266-306); a notice is sent on each release (`decisions.md`). A staff-archived exam stays off the student site, but its posted results stay readable.
- **Endpoints for a student.** `GET /api/v1/live-exams/{id}` (exam with `myStatus`), `GET /api/v1/live-exams/{id}/result[?attempt=]`, `GET .../attempts`, `GET /api/v1/me/exam-routine` (the routine, not a history), and the module-driven `GET /api/v1/live-exam-modules` + `GET /api/v1/live-exam-modules/{id}/exams`. **There is no student endpoint that lists the exams a student has taken.** A staff-only one exists (`LiveExamResultsService.history`, `findHistory`).
- **The page.** `results-hub.tsx` calls `useMyModules()` (`/live-exam-modules?page=0&size=50`, which returns only packages the student holds through a roster row or a grant, `findVisibleToStudentCatalog`), then one `/live-exam-modules/{id}/exams` per package, keeps the exams whose `myStatus` is RESULT_READY / FAILED_TO_SUBMIT (tab Results), AWAITING_RESULT (Marking) or MISSED / OPEN / IN_PROGRESS / SUBMITTED (Other), then one `/result` call per Results row for the score.

### Why the page shows nothing (confirmed)
Read-only query on `coaching_center_dev`: the student's two sittings are both SUBMITTED written exams with **zero packages** (not in any package, no roster, no grant). The hub never reaches an exam that is not inside a package the student holds, so those sittings are invisible. This is not a status or release problem. Free standalone exams, an exam bought on its own (per-exam grant) and the results of an exam whose package the student no longer holds all fall in the same hole. Secondary faults: the package list is capped at 50 and each exam list at 50; the hub's "Other" tab lists MISSED and OPEN exams although the student has not taken them; an MCQ that is posted but still before its deadline shows under "Other", not Results; N+1 requests (one `/result` per row).

### What a correct "every exam the student has taken, any status" view must include
- Every exam with at least one participation of this student, whether or not it is in a package, free or paid, live or practice, published or later archived (posted results stay readable). Deleted exams are left out (`findHistory` rule).
- One row per exam (latest attempt) with the attempt count, newest first, paged.
- Per row: exam id, titles, type, `liveAt`, pass percent, the derived status and detail, module title when the exam has one, `submittedAt`, and the score, maximum and pass flag **only when released** (same rule as `result()`: MCQ once submitted; written when published or failed to submit). No leak of a hidden written mark.
- Tabs: Results (a visible score, or failed to submit), Waiting for marking (AWAITING_RESULT), In progress / submitted (the rest). Missed and not-yet-sat exams are not "taken" and are not listed here (they stay on the routine and My exams).

### Fix
1. **API:** new `GET /api/v1/me/exam-results?page&size&examType` (student only, `requireStudent()` as the other `/me` reads; size default 20, max 50) in `LiveExamSittingController`, backed by a new student-facing method beside `history` that reuses `participants`-by-student, `LiveExamStudentStatus.of`, `maximum(exam)` and the release rule. Rows carry a `resultVisible` boolean. No change to existing endpoints and no weakening of any access rule; it reads only the caller's own rows.
2. **Web:** `useMyExamResults` (infinite query, "Load more") in `lib/api/exam/sitting.ts`; `results-hub.tsx` uses it instead of the module fan-out and the per-row `/result` calls (the summary counts use the rows). Cards link to `/exam/{id}/result`; keep the type filter and tabs; both languages.
3. **Check:** the result page (`result-page.tsx`) and leaderboard work for an exam that is in no package (Segments 7 and 8 stay untested and related: release notices, leaderboard, staff marking and publishing).
4. **Tests:** one API integration test (own rows only, standalone exam listed, written score hidden until published) written but not run; web: small tests for the tab rule and the hub with mocked rows. Manual: student1 submits a free written exam, opens Results, sees it under "Waiting for marking"; after staff publish, under Results with the score.
5. **Size:** M. Depends on nothing; blocks nothing, but re-test with Segments 7 and 8.

## 12. Review round 2 (31 Oct, from the browser): hero lockup, category section, footer

Findings from Shahriar's screenshots: (1) the logo image next to "Symam Law" in the hero is not needed (the header already has the logo); (2) the category section looks poor: dark cards on a dark page, a navy heading pill that blends in, and English "MCQ / Written" switch labels in the Bangla page; (3) the footer is too tall, its text is too small (14 px) and it mixes everything into one dense block. Reference for the footer: the old site's footer (logo, contact, four legal links, app buttons, the payment strip, socials, copyright) and the usual ed-tech pattern (Coursera, Udemy, Khan Academy, Unacademy): a few labelled link columns at readable size, social icons next to the brand, one slim bottom bar for copyright and secondary items.

**Hero:** remove the two logo images from the brand lockup; keep "Symam Law" as the large accent name above the headline.

**Category section** (`landing/categories.tsx`):
- Use the shared `Section` component (centred title with the gold bar, `tone="tint"`) instead of the navy pill; add a one-line subtitle ("Choose an exam type, then your category").
- The MCQ / Written switch is a larger segmented control (44 px+ buttons, bold label), centred, with the Bangla labels the rest of the Bangla UI uses (not the English words).
- Cards: `bg-card` with a visible border and soft shadow, centred content, an exam icon in a 56 px gold-tinted circle, the title (up to 2 lines), the counts as small rounded chips ("2 exams", "1 package"), "From ৳500" in bold accent, and a full-width gold "Explore" button with an arrow. Equal heights; 1 column on phones, 2 from 640 px, 3 from 1024 px, centred when there are fewer. Hover lift and accent border only.

**Footer** (`shell/site-footer.tsx`, full variant; the minimal variant stays):
- Text sizes: links and contact lines 16 px (`text-body`), column headings 17 px bold (`text-card-title`), only the copyright line stays 14 px. Tap targets 44 px on phones, 36 px rows on desktop.
- Layout from 1024 px: four columns (brand 1.3fr, Explore, Company, Contact 1.3fr). **Brand column:** the logo, a one-line tagline and the four social icons (44 px round buttons). **Explore:** MCQ exams, Written exams, Packages, Notices. **Company:** About Us, Terms and conditions, Return Refund, Privacy Policy. **Contact:** phone, email, hours, address with icons. From 640 px two columns; on phones a single column, with Explore and Company side by side.
- Removed: the long "Get Mobile App" paragraph. The App Store / Google Play buttons (still "Coming soon", same disabled state) move to the right side of the bottom bar.
- Payment strip: its own centred row between the columns and the bottom bar, at most 896 px wide in the white rounded box, scrolling inside the box on phones (as now).
- Bottom bar: copyright and "Developed by" on the left, the store buttons on the right (stacked on phones), one divider line above.
- Spacing: `py-8` on desktop, `py-6` on phones, `gap-y-8`; target height on desktop under 340 px (it was about 440 px).
- New message keys in both languages: `common.footer.tagline`, `common.footer.exploreHeading`, `common.footer.mcq`, `common.footer.written`, `common.footer.packages`, `common.footer.notices` (reuse `landing.nav*` values where they fit).

## 13. Review round 3 (31 Oct): the student home and the heavy rows

Finding (screenshot of the signed-in home): section titles ("Next exam", "Live now", "Coming up", "Popular", "My orders") are 17 px bold with 24 px gaps, and every item is a tall, padded card with its own border; the content is one narrow 768 px column on a wide screen, so the page is long and half empty. The same pattern, with the same cause, is in: `CatalogExamRow` (category lists, popular, favourites), `ExamRow` (module page), `PackageLinkCard`, the home `RoutineLine` and "My packages" cards, and the cards of the routine, my-exams, results, favourites and notices pages. Two things make rows tall: `p-4` card padding with a 17 px title, and the wish-list heart pinned to the top-right corner, which forces a `pt-11` on the status column.

What established products do for the same job (Coursera "My Learning", Udemy "My learning", Google Classroom "Upcoming", Unacademy and Testbook dashboards): small, quiet section labels with a "See all" link; compact list rows (about 56 to 64 px) with a divider instead of one bordered card per item; the next/live item as the only larger "hero" card with a single clear action; status and price on the right of the row, not under the title; and on wide screens a main column with a narrower side column instead of one long stack.

Decided design:
- **Section label:** `text-label font-bold` in the muted colour (not `text-card-title`), 8 px under it, 20 px between sections (was 24), "See all" in `text-meta` accent on the right.
- **List card:** a section's items sit in one bordered rounded card with `divide-y`, not one card each. A row is a single link, `px-4 py-3`, min height 56 px: line 1 the title (`text-body font-semibold`, one line, clamped); line 2 `text-meta text-muted` (date and time, type, package tags inline); on the right the status pill and price (right-aligned, vertically centred), and the wish-list heart as the last 44 px item in the row (no more absolute corner and no `pt-11`). Hover = background tint, not a bigger border.
- **Hero (Next exam):** one compact card: a gold left bar, a small eyebrow ("Live now" pill or "Starts in"), the title (`text-card-title`), the countdown or date on one line and one gold action button ("Take exam" / "View") on the right; about 88 px tall instead of 140.
- **Doors:** unchanged (Shahriar, 31 Oct: keep the original MCQ and Written door cards; only the sections below them are compacted).
- **Layout:** the signed-in home is wider than the 768 px column: from lg (1024 px) a two-column grid, main column (doors, Next exam, Live now, Coming up) and a 20 rem side column (My packages, Popular). On phones everything stacks in the same order. Other student pages keep the 768 px column.
- **Empty lines** ("No exams coming up", "Nothing popular yet") become a single muted line inside the list card with `py-3`, with no extra margin.
- The shared rows keep their props and test ids so the other pages that use them (category pages, favourites, module page) become compact too.

## 14. Review round 4 (31 Oct): footer on list pages, discounted exam card, category page layout

### 14.1 Is a full footer needed on pages that show a list?
Industry practice, by page type:
- **Public, marketing and catalogue pages** (Udemy, Coursera, Khan Academy, Unacademy course lists, Amazon search): a full footer under the list. It works because these lists end (pagination or a "Load more" button). The known failure is **infinite scroll**, which makes the footer unreachable (Baymard and Nielsen Norman both warn about it). This site uses "Load more", so the footer stays reachable.
- **Signed-in app screens** (Google Classroom, Gmail, Notion, Linear, LMS "My learning" views): no footer, or one line. The person already has navigation, and a 340 px footer under every list pushes the real content up and adds scroll for no gain.
- **Task screens** (sitting an exam, checkout): no footer. Already the case here.
Legal links must stay reachable everywhere (terms, privacy, refund), which a one-line footer does.
**Decision:** visitors keep the full footer on public pages and the landing page. A **signed-in student** gets the **slim footer** (copyright and the legal links, the existing `minimal` variant) on every page; the sitting page keeps none, sign-in pages keep the slim one. One change in `footerVariant` / `StudentShell`.

### 14.2 Discounted exam card (screenshot: "Penal Code Practice Set")
What is broken: the struck-through price, the discounted price, the "20% off · until 30 November" line and the status pill are stacked in one narrow right column that is squeezed by the title, so the title wraps to 3 lines and "Date to be announced" wraps; the package tag is a `rounded-full` pill whose long name wraps into a circle; the row is 310 px tall.
What Udemy, Coursera, Amazon and Flipkart do: the **price is one block**: the current price largest and bold, the original struck through next to it in muted small type, a small green **"20% off" badge**, and the end date as one quiet line ("Offer ends 30 Nov"); it sits at the **bottom of the card** in its own row, not beside the title; status is a separate pill at the top.
**Decision (`CatalogExamRow`, `PriceLine`):** three zones. Top row: type pill, popular pill, status pill on the right, favourite heart last. Middle: the title (up to 2 lines) and one meta line (date or "Date to be announced") that never wraps below the title. Bottom row (thin divider above, `pt-2`): left, the package tag as a single-line rounded-rectangle chip with `truncate` (max 60 % of the row, full name in the title attribute); right, the price block: `৳240` bold, `৳300` struck through muted, a green "20% off" chip, and "until 30 Nov" as `text-meta` under it on wide rows or after it on narrow ones. A free exam shows "Free" in green in the same place. Card height about 110 px (was 310).

### 14.3 Category page: the list is pushed far down
Current order: breadcrumb, title, "Subcategories" heading, a full-size card per subcategory, "Exams" heading with the subcategory switch, a full-width tabs bar, then the list: the first exam starts below 690 px on a 1080 px screen.
Practice elsewhere: Udemy and Coursera category pages put the subcategories as a **row of chips** and the filters on **one toolbar row**, so results start high; Amazon uses a side column for facets; Khan Academy uses chips. The tabs are **underline tabs** or a compact segmented control, not a full-width bar.
**Decision:**
- Subcategories become a **horizontal row of chips** (name and count, wraps or scrolls on phones), no heading and no big cards.
- One **toolbar row** under it: left the All / Free / Live / Archived tabs as compact underline tabs; right the "Include exams in subcategories" switch (only on a category with subcategories); on phones the switch moves under the tabs.
- The "Exams" heading goes (the page title and the toolbar make it obvious); the result count sits at the toolbar's right edge when there is room.
- The same toolbar pattern is used by the module page's tabs.
Target: the first exam row starts at about 380 px.
