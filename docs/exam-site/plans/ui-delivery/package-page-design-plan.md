# Package page design: analysis and plan

Written 2026-10-26 for Shahriar. This plan comes before any further code change. It refines the layout in [package-redesign-plan.md](package-redesign-plan.md) (sections 4 to 6) using two inputs: a look at the module page as it is on screen today, and the sales-page patterns that course platforms use. No code has been changed for this plan.

## 1. The current module page, as it looks today

Source: full-page screenshots of `/mcq/m/<module>` at 1440 px and 390 px, taken from the running site (build from before the package redesign).

| # | What is on screen | Why it reads as poor |
|---|---|---|
| A1 | The package title is only a text heading, above a plain box. There is no image and no cover. | The product has no visual identity, so it looks like a list rather than something to buy. |
| A2 | The description and the summary line sit in a box of the same weight as everything below. | Nothing marks the most important information. |
| A3 | Four boxes stack one under another: the routine stat tile, the buy box, the routine table and the exam list. All are white with the same border and padding. | The eye has no entry point and no sense of order. Scrolling is long and flat. |
| A4 | The routine stat tile is a large empty box with an icon, a label and one date. | It looks like a button with no clear action. Its value (one date) is less useful than the page it links to. |
| A5 | The price is a small heading ("মডিউল ৳১,৫০০") and a sentence. The buy button is the only strong element. | The price is the decision point, and it is the weakest text on the page. |
| A6 | The routine table has a header row, a dividing line for each row and four columns of text. | On a phone it becomes a scrolling table inside a card. On desktop it takes more space than the exam list it sits above. |
| A7 | Each exam row uses the same generic emoji (📝) in a beige square, the title, a short line, and a small price with "কিনুন ›". | The emoji is decoration, not information. The buy action is a small coloured link, not a button. |
| A8 | The count "৫" in the exam list heading is a tiny circle in the corner. | Easy to miss, and it does not say what it counts. |
| A9 | The page has no clear call to action at the top. A visitor has to scroll past the description and the routine to reach "পুরো মডিউল কিনুন". | On a phone the buy button is well below the first screen. |

Summary: the page has no image, no clear hierarchy, too many equal boxes, and a weak price. The information is correct; the presentation does not sell or guide.

## 2. What course platforms do

Sources checked:
- [Zenler: best course landing page structure](https://www.zenler.com/blog/best-course-landing-page-structure-new-zenler): hero, problem and promise, audience, outcomes, curriculum, instructor, proof, what's included, price and guarantee, FAQ, final call to action.
- [Mewayz: course landing pages that convert](https://mewayz.com/blog/course-landing-pages-that-convert-7-design-principles-backed-by-real-data): clean layout, generous white space, few colours and fonts, one primary action.
- [Teachable: sales page examples](https://teachable.com/blog/sales-page-examples) and [Teachable: create and customise a sales page](https://teachable.com/blog/create-and-customize-your-sales-page): the default blocks are banner, text, curriculum, image with text, and pricing. Large bold headline, subheadings that split the page into sections, a visual under the headline, and generous spacing.

These sources are vendor blogs, so they are directional. The patterns agree with each other, and they agree with what the screenshot shows is missing.

Patterns taken from them:
1. **Hero first:** a visual, the title, one short line, and one primary action.
2. **Price next to the action,** not far below the description.
3. **Curriculum as a list** of items, each with a short line, not as a grid of boxes.
4. **What is included** as a short list with ticks.
5. **Sections in a fixed order** that answers buyer questions in turn: what it is, what is included, when, price, rules and questions.
6. **Few visual levels.** One heading size for sections, one body size, one small size for meta text. Few colours. Gold is used for the one action and the price, not for every link.
7. **Single column** on mobile, with a sticky action when the main button scrolls away.

## 3. Plan for the module page (in the 760 px student column)

The student pages stay in the 760 px column, as the client mockup requires. The layout below is one column at every width, with a wider two-column hero only where the column allows it.

### 3.1 Order of the page
1. **Cover** (16:9, full width of the column, rounded corners). Fallback when no image is uploaded: the gradient from the package redesign.
2. **Title** (one heading, 24 px on mobile, 28 px on desktop) and the type as plain text, not a pill.
3. **Meta line:** exam count · next exam date (one line, small text).
4. **Buy block:** price (large, 28 px), "What's included" ticks (exam count, future exams, paper language), one primary button. For a signed-in owner: the progress bar instead of the price.
5. **Tabs:** *Exams* (default), *Routine*, *About*. Replaces the four stacked boxes. The tab bar uses the existing Tabs component.
6. **Exams tab:** the list of exams (see 3.3).
7. **Routine tab:** a list of dated cards on mobile; a table from 768 px up.
8. **About tab:** the full description and, later, the rules.

### 3.2 Mobile action bar
When the buy block scrolls out of view on a phone, a bottom bar appears with the price and the same primary button. It is hidden for owners and when no price is set. This is the one sticky element on the page.

### 3.3 Exam rows
- Left: a small type badge (MCQ or Written as text in a pill, not an emoji).
- Middle: the title on one line, then the date and time in small text, then the summary clamped to two lines.
- Right: the status pill if the student has the exam, the access badge if it is included, or the price as a plain text value with "Buy" as a text button.
- Each row is one tap target of at least 44 px tall.

### 3.4 Routine
- Mobile: each exam as a card with the date in bold on the left and the time range below it.
- Desktop (768 px and up): the table, with a clear header row and no vertical lines.

### 3.5 Stat tiles
Remove the three stat tiles from the page. Their information moves into the meta line (exam count, next date) and into the tabs. Owners keep a single progress line in the buy block.

### 3.6 Type and colour
- Headings: 24 px (page title), 28 px desktop; section headings 18 px bold.
- Body text: 15 to 16 px, line height 1.6 (Bangla needs the extra height).
- Meta text: 13 px, muted colour.
- Gold (`accent`) only for the primary button, the price and the active tab. Links and status use the text colours.
- No emoji as icons on this page.

### 3.7 States
- **Loading:** a skeleton with the shape of the cover, two lines of title, and the buy block.
- **No exams yet:** an empty state with one line and the other door (MCQ or written) as a link.
- **No price:** the buy block says the price is not set yet, and shows no button.
- **Owner:** the buy block becomes "Unlocked" with the progress bar; the mobile bar is hidden.

## 4. What changes from the current build

| Current | Planned |
|---|---|
| Four stacked white boxes | One hero, one buy block, then tabs |
| Stat tiles as large empty buttons | Removed; moved to the meta line and the tabs |
| Routine table on all screen sizes | Cards on mobile, table from 768 px |
| Emoji icon on every exam | Type badge as text |
| Small coloured "কিনুন ›" link | Price and a plain text button |
| No action near the top on mobile | Sticky bottom bar when the buy block is off screen |
| Title with no image | Cover at the top (fallback gradient until uploaded) |

## 5. Checks before this is called done

- Visual: the page reads top to bottom in the order of section 3.1 at 360, 390, 768 and 1440 px, in light and dark theme.
- Overflow: no horizontal scroll at any width.
- Tap targets: every button, tab and exam row is at least 44 px tall.
- Contrast: text on the cards and the gold button meets 4.5:1 in both themes.
- Bangla: no clipped glyphs at 16 px with line height 1.6.
- Sticky bar: appears only when the buy block is off screen, and never covers the exam list's last row.
- Owner and no-price states: each one checked with a demo account.
- The purchase sheet opens from the buy block and from the sticky bar, and its summary uses the same cover.

## 6. Decisions (confirmed by Shahriar, 2026-10-26)

1. **Tabs:** yes. The module page has three tabs: Exams (default), Routine and About. About is shown only when the package has a description.
2. **Mobile sticky bar:** yes. It appears when the buy block scrolls out of view, shows the price and the same button, and is hidden for owners and when no price is set.
3. **Stat tiles:** removed. Their information is in the meta line and the tabs.

## 7. Implementation status

| Item | Status |
|---|---|
| Module page: cover, title, meta line, buy block, included list | Done, committed `e387608` |
| Tabs: Exams, Routine, About | Done, committed `e387608` |
| Mobile sticky buy bar | Done, committed `e387608` |
| Exam rows: text type badge, no emoji, status or price on the right | Done, committed `e387608` |
| Routine: cards on mobile, table from 768 px | Done, committed `e387608` |
| Stat tiles removed | Done, committed `e387608` |
| Checks in section 5 (widths, light and dark, overflow) for the module page | Done: no overflow at 360, 390, 768, 1440 px in light and dark (see section 10) |
| API restart with migration V41 and demo cover upload | Done |
| Module page and cover work (sections 1 to 6) | Done, committed as `e387608` |
| Exam details page (section 8) | Implemented, not yet committed. Browser checks running (see section 10) |
| Purchase flow: sheet and result pages (section 9) | Implemented, not yet committed. Unit tests pass; browser checks not yet run |

## 8. Per-exam details page

Source: `/exam/<id>`, current build, from screenshots at 1440 px and the code in `exam-page.tsx`.

### 8.1 What is on the page today

| # | What is on screen | Why it reads as poor |
|---|---|---|
| E1 | The exam title is text only, with a single type badge. No image, and no link back to the package's cover. | The page has no visual identity, like the module page had before the cover. |
| E2 | Four small "fact" tiles (time, start, language, price) with a gold top border and emoji icons (⏱️ 🗓️ 🌐 💳). | The emoji are decoration, the same pattern the module page dropped. The tiles are the right information in the wrong form. |
| E3 | One full-width gold button, then a ghost "লিংক কপি করুন" floating at the right with empty space around it. | The secondary action has no context and its position looks accidental. |
| E4 | Rules are numbered twice: the rule text already starts with "১.", and the list adds "1." in front. | A visible defect in the rules block, and it appears on every exam. |
| E5 | Description, rules and paper are separate boxes, each with a small icon square. | Same stacked-box pattern as the module page before the redesign. |
| E6 | The price is shown in the fact tile. For a student who does not own the exam, a price block at the bottom repeats it (already removed in the current build; the screenshot predates that change). | Duplicate information. |
| E7 | Nothing in the header shows the exam's state (not yet open, open now, submitted, result ready). The only state cue is the one button. | A student cannot tell at a glance whether to wait, start, or view a result. |
| E8 | The countdown ("Starts in") appears only for owners, below the buy area. | The most useful timing information is hidden for the people most likely to need it. |
| E9 | There is no path back to the package except the breadcrumb, and the breadcrumb is small. | Students browsing one exam lose the context of the package. |

### 8.2 Reference patterns

Course and exam detail pages on platforms of this kind follow the same pattern: a clear header with the item's identity, one primary action that changes with the state, a short fact list, and then the detail sections in a fixed order. Sources used for the module page apply here too: [Zenler on course landing page structure](https://www.zenler.com/blog/best-course-landing-page-structure-new-zenler), [Teachable's sales page examples](https://teachable.com/blog/sales-page-examples), and [Mewayz on course pages that convert](https://mewayz.com/blog/course-landing-pages-that-convert-7-design-principles-backed-by-real-data).

### 8.3 Target design

1. **Context strip at the top:** a small link "← <package title>" above the title, and the package's cover as a thin 16:9 banner on phones (about 120 px high, cropped), full width on desktop.
2. **Title block:** the title, the type badge as text, and the status line (for example "Opens 24 Oct, 12:00" or "Open now, 40 minutes" or "Submitted" or "Result ready").
3. **Facts as a plain list,** not tiles: duration, start and end, paper language, and full marks or price. Labels are small and muted; values are bold. No emoji.
4. **One primary action** whose label follows the state. The state table is in 8.4. The secondary action ("copy link") becomes a text link under the primary button, aligned with it.
5. **Sections in a fixed order:** About this exam (description), Rules (numbered by CSS only), Paper (written exams only), Your result (owners, when available).
6. **Rules:** strip the leading number from each rule line before showing the list, so the number appears once.
7. **Mobile sticky bar:** the same bar as the module page, shown when the primary action is off screen. Hidden when the action is "Resume" or "View result" and there is no price.
8. **No price block at the bottom.** The price appears once, in the fact list and in the primary action.

### 8.4 States and their primary action

| State | Primary action | Status line |
|---|---|---|
| Visitor, not signed in | "Log in to buy · ৳X" | Opens on <date> |
| Student, not owned, priced | "Buy this exam · ৳X" | Opens on <date> |
| Student, owned, not yet open | Disabled "Starts <date>" with a countdown | Starts in <countdown> |
| Owned, open now | "Start exam" (opens the rules confirmation) | Open now, ends <time> |
| Owned, in progress | "Resume" | In progress |
| Owned, submitted, window open | "Resume" (to review) | Submitted |
| Owned, result not yet published | "View result" | Result pending |
| Owned, result published | "View result" | Result ready |
| Owned, missed MCQ, practice allowed | "Practice" | Missed |
| No price set, not owned | Disabled "Price not set yet" | Opens on <date> |

### 8.5 Checks for the exam page
- Every state in 8.4 is reachable with a demo account (or a demo row) and shows the right action.
- The rules list shows each number once.
- No emoji on the page. No horizontal overflow at 360, 390, 768 and 1440 px; light and dark.
- The package cover banner is cropped, not stretched, and the page has no layout shift when it loads.

## 9. Purchase flow

Source: the purchase sheet (`purchase-sheet.tsx`) and the result page (`purchase-result.tsx`), current build.

### 9.1 What is on the page today

| # | What is on screen | Why it reads as poor |
|---|---|---|
| P1 | The sheet is a dialog with an item summary (cover, title, price), a sentence, a trust line, a refund link, and two buttons. | Correct content, but no visual order: the price is not the main thing, and the sentence says the same thing as the title. |
| P2 | The payment methods appear as one text line ("bKash, Nagad, Rocket, card"). | Students cannot recognise the methods at a glance. The brand's payment-methods image exists in the repo and is not used. |
| P3 | No indication of the steps: sign in, add phone, confirm here, pay on the bank page, return. | A student who is sent to the payment page does not know what comes next. |
| P4 | After payment the result page uses the same empty-state layout for paid, failed, pending and not-found. Only the transaction ID is shown, in a monospace font. | No order summary, amount, date or payment method on the success page, and no receipt link. The success state looks like an error page. |
| P5 | Pending and failed states have a sentence and a button, with no explanation of what will happen next. | Users in a pending state do not know whether to wait, retry or contact support. |
| P6 | The two buttons ("Pay" and "Cancel") are the same width and weight, stacked. | Cancel looks like a second choice of equal value. |
| P7 | The refund and trust lines are small muted text placed after the summary. | Trust content is hard to find at the moment it matters: when the student is about to pay. |

### 9.2 Reference patterns

- **Single-purpose checkout:** one clear summary, one primary button, and trust cues close to the button. Sources: [Ruzuku on course checkout pages](https://www.ruzuku.com/learn/tools/checkout-page/course-conversion) and [Checkout Page on checkout design](https://checkoutpage.com/blog/checkout-page-design).
- **Show hidden costs and methods early:** the price, and the payment methods, appear before the button. Source: [Checkout Page on checkout design](https://checkoutpage.com/blog/checkout-page-design).
- **Success page content:** the order summary, the amount paid, the transaction ID, the payment method, access to a receipt, and support instructions. Source: [Stripe on payment-successful pages](https://stripe.com/resources/more/payment-successful-pages).
- **Pending is not success:** the page says processing is underway, what will happen next, and when access will appear. Distinct visuals for pending, succeeded and failed. Source: [Banked's redirect docs](https://docs.banked.com/partner/checkout/redirects) and [Breez's payments UX guide](https://sdk-doc-liquid.breez.technology/guide/uxguide_display.html).
- **Failed states:** the research found no solid source on failure recovery. The plan uses the common pattern: say what failed in plain words, keep the item, offer "Try again" on the same exam, and give a support contact.

### 9.3 Target design

**The sheet (before paying)**
1. **Order summary card:** the cover thumbnail, the title, the exam count for a package (or the type for an exam), and the price in large type.
2. **What you get:** the included list (already built for packages); for an exam, its type, its date and "access until the exam is over".
3. **Payment row:** the payment-methods image (bKash, Nagad, Rocket, cards) with the line "Secure payment by SSLCommerz".
4. **Next steps:** three short lines: "You will be taken to the payment page", "Pay with your chosen method", "Access appears here after payment". This is the step indicator, in text, not a stepper.
5. **Refund line:** placed directly above the button, not after the trust text.
6. **Buttons:** the primary button reads "Pay ৳X securely" and fills the width. "Cancel" is a text button below it.

**The result page (after payment)**
Each state has its own icon, colour and layout:
- **Success:** a check icon in a green circle, "Access unlocked" as the heading, an order card (item, amount, date, transaction ID, payment method), two buttons ("Open exam" or "Go to package", and "Download receipt" to the existing receipt page), and a support line.
- **Pending:** a clock icon, "Payment is being confirmed", one sentence on what happens next and when access will appear, and "Check again" with the time of the last check.
- **Failed:** a cross icon, "Payment did not go through", the reason in plain words, "Try again" that returns to the same exam or package sheet, and a support contact.
- **Cancelled:** a neutral icon, "Payment cancelled", "No money was taken", and "Back to the exam".
- **Not found:** a neutral page with "We could not find this payment" and links to "My modules" and "Home".

### 9.4 Checks for the purchase flow
- The sheet shows the summary, the methods image and the three next-step lines at 360 px without overflow; the primary button is at least 44 px tall.
- Each result state in 9.3 is reachable: success and pending by a demo order, failed and cancelled by setting the demo order's status, not-found by an unknown transaction ID.
- No real payment is made in these checks. The SSLCommerz sandbox round trip stays on the manual QA list (plan section 9 of the earlier redesign plan).
- Light and dark theme, and the payment-methods image is not stretched.

### 9.5 Decisions needed
1. **Payment logos:** use the existing payment-methods image in the sheet? I recommend yes.
2. **Receipt link on success:** link to the existing receipt page? I recommend yes.
3. **Failure contact:** which support phone or WhatsApp number should the failed state show? The site already has contact numbers in the footer.

**Confirmed by Shahriar (2026-10-26):** items 1 and 2 are yes. Item 3 is pending, so the failed state uses the first footer number for now and the support number can be changed later.

**Implementation notes:** the success page links to the payments list (`/account/payments`) rather than to one receipt, because the result page knows only the transaction ID and the receipt page needs the order ID. A per-order receipt link needs the order ID on the result page, which is a later change.

## 10. Progress record and deviations (as of 2026-10-26)

This section is the record to check before anything else in the package work is changed. Where the built code differs from sections 3, 8 or 9, the difference is listed here and the code is correct for that item.

### 10.1 Done

| Area | State | Where |
|---|---|---|
| API: cover migration, entity fields, permission, cover service, endpoints, views, integration test | Done | Commit `e387608` |
| Module page redesign (sections 3 and 7) | Done | Commit `e387608` |
| Package cards, landing "Our packages", purchase sheet summary and included list | Done | Commit `e387608` |
| Staff cover uploader (Master Admin only) | Done | Commit `e387608` |
| Demo: migration V41 applied; sample cover uploaded to one demo module | Done | Demo database `coaching_center_demo` |
| Exam page redesign (section 8) | Built, not committed | Working tree |
| Rule numbering fix (number shown once) | Built, not committed | Working tree (`lms-blocks.tsx`) |
| Purchase sheet: payment-methods image, next steps, refund above the button, price on the pay button, cancel as text button (section 9.3) | Built, not committed | Working tree |
| Purchase result: one layout per state, success summary with amount, receipts link, failure help line (section 9.3) | Built, not committed | Working tree |

### 10.2 Checks

| Check | Result |
|---|---|
| Web type check | Passed after the exam and purchase changes |
| Web lint on the changed folders | Passed |
| Web unit tests, full suite | 617 of 619 passed before the purchase test update; the two failures were the old pay button name. The purchase tests now pass (37 of 37). The full suite has not been re-run since that update |
| API cover and catalog integration tests | Passed (9 tests). The API has not changed since |
| Module page browser check at 360, 390, 768, 1440 px, light and dark | Passed: no horizontal overflow; sticky bar appears only after the buy block scrolls away |
| Exam page browser check at 360, 390, 768 and 1440 px, light and dark | Passed: no horizontal overflow |
| Exam page sticky bar | Not verified. The demo exam page is 1350 px tall at 390 px, so the action never scrolls out of view and the bar correctly stays hidden. Needs a longer exam (rules and description) or a shorter screen to test |
| Purchase sheet and result pages in a browser | Not run. The sheet needs a signed-in student; the result page needs a payment status |
| Staff cover uploader in a browser | Not run. Covered by unit tests and the API integration test |
| Live SSLCommerz payment | Not run. Remains on the manual QA list |

### 10.3 Deviations from the plan

Module page (section 3):
- **Included list:** shows two lines (current exam count and future exams). The plan's third line, paper language, is not included.

Exam page (section 8):
- **8.3 item 5, "Your result" section:** not built as its own section. The pass mark appears as a section for owners; the result itself is reached through the primary action.
- **8.3 item 7, sticky bar:** the plan said it would be hidden for Resume and View result. It is shown whenever the primary action can be taken now, including those two states. It is hidden only for states that cannot be taken yet (not announced, upcoming for owners).
- **8.4, no price set and not owned:** the plan said a disabled "Price not set yet" button. The page shows no button; the price line says the price is not set.
- **8.3 item 2, status line:** the timing line under the title shows the start time, or a countdown for owners before the start. The state is shown by the status pill in the header, not as a separate "Open now, ends <time>" sentence.

Purchase flow (section 9):
- **9.3, included list for exams:** not built. The included list appears only for packages.
- **9.3, receipt link:** the success page links to the payments list ("View receipts"), not to one receipt. This is the decision recorded in section 9.5.
- **9.3, pending state:** the "time of last check" is not shown.
- **9.3, cancelled state:** the button is "Try again" (back to the same exam or package), not "Back to the exam". Both lead to the same place.
- **9.3, failure support line:** uses the first footer phone number as a placeholder until the support number is confirmed (section 9.5).
- **Transaction ID:** now shown in the success details only. Other states no longer show it above the message.

Design (from the earlier redesign plan, `package-redesign-plan.md`):
- **Cover upload timestamp:** the plan's separate upload-time field is not built. The timestamp is inside the cover path as a version number.
- **Image format:** the image pipeline re-encodes uploads, so a PNG may be served as JPEG. The public endpoint accepts this.
- **Gradient fallback labels:** "MCQ", "Written" and "Package" are in English on Bangla pages.
- **Sticky bar position:** sits above the student tab bar using the shell's `--tabbar-h` variable.

### 10.4 Still open

1. Commit the exam page, rule numbering, purchase sheet and purchase result changes (not yet committed).
2. Record the exam page browser check result in 10.2.
3. Re-run the full web test suite once more after the test update.
4. Browser checks for the purchase sheet (signed-in student needed) and each result state.
5. Support phone number for the failed state (section 9.5, item 3).
6. Manual QA: live SSLCommerz payment and the success, pending, failed and cancelled paths.

### 10.5 Final style recheck (2026-10-26)

This supersedes the open items in 10.2 and 10.4 where they overlap.

Checked in a browser, signed in as a fresh demo student (a demo account created for this check, not one of the seeded students):
- **Exam page (student, 390 px):** rules shown once, primary action clear, module offer below it, cover banner, no overflow.
- **Purchase sheet (390 px):** the summary card with cover and price, the payment-methods strip, the three next steps, the refund link above the pay button, the price on the pay button, and Cancel as text.
- **Result page, not-found state (390 px):** renders with the new layout.
- **MCQ list (390 and 1440 px):** no overflow.
- **Exam page (guest) at 360, 390, 768 and 1440 px, light and dark:** no overflow.

Style fixes made in this recheck:
- The emoji lock in the sheet's trust line is now an icon.
- The sentence "You will be taken to the payment page" was removed from below the buttons, because next step 1 says the same.
- The payment-methods strip is larger and fills the card width.
- The exam page breadcrumb no longer repeats the package name; the link above the title stays.
- The ⚖️ emoji on the category cards was removed, so no emoji remain as icons on these pages.

Still not checked in a browser:
- The success, pending and failed result states. They need a real or staged payment order. They use the same layout component as the not-found state, which was checked.
- The exam page's sticky bar on a long exam page (see 10.2).
- The staff cover uploader in a browser.
- The live SSLCommerz payment (manual QA).

Not yet committed: the exam page, rules fix, purchase sheet, purchase result, and this recheck's fixes. Tests after the last fixes: purchase and catalog tests, 73 of 73; type check and lint pass. The full web suite has not been run since the last test update.

### 10.6 Seeded checks (2026-10-26)

These supersede the "still not checked" items in 10.5.

Data used (demo database `coaching_center_demo` only):
- Four staged orders for the demo test student created for this check: `STAGE-PAID-001` (SUCCEEDED), `STAGE-FAIL-001` (FAILED), `STAGE-CANC-001` (CANCELLED) on the demo exam, and `STAGE-PEND-001` (INITIATED) on the demo module. The amount and title come from the order details the sheet normally saves in the browser, which were set for the check.
- The demo exam's description was made long for the sticky-bar check and then restored to its original text.
- A demo cover was uploaded to the demo module earlier (section 10.1).

Results:

| Check | Result |
|---|---|
| Success result page (390 px) | Passed: the title, amount paid, transaction ID, receipt link and buttons are shown in the right order |
| Failed result page (390 px) | Passed: the failure message, the "try again" button and the help line are shown |
| Cancelled result page | Rendered with the neutral layout (checked by its title text) |
| Pending result page (390 px) | Passed: the warning layout and the pending pill are shown |
| Not-found result page (390 px) | Passed (10.5) |
| Exam sticky bar on a long exam (390 px) | Passed: hidden before scrolling, shown after the buy block scrolls away |
| Staff cover uploader (Master Admin, 1280 px) | Passed: the section shows, a PDF is refused with the message, a PNG uploads and the saved message appears, and the 16:9 preview updates |

Copy and style fixes made during these checks:
- The pending page's footer no longer tells the student they can leave the page, because the main text says to keep it open. The footer now says that access appears in My modules once payment is confirmed, even if the page is closed.
- The cover section heading now reads "Cover image" (or the Bangla equivalent) instead of "Upload cover", because it also covers replacing and removing a cover.

Still open:
- The cover heading change and the pending footer change are in the code but are not in the running site until it is rebuilt.
- The live SSLCommerz payment remains a manual QA item.
- Nothing is committed yet.
