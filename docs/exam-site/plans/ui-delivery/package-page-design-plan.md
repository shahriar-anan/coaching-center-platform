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
| Module page: cover, title, meta line, buy block, included list | To implement (this round) |
| Tabs: Exams, Routine, About | To implement (this round) |
| Mobile sticky buy bar | To implement (this round) |
| Exam rows: text type badge, no emoji, status or price on the right | To implement (this round) |
| Routine: cards on mobile, table from 768 px | To implement (this round) |
| Stat tiles removed | To implement (this round) |
| Checks in section 5 (widths, light and dark, overflow) | After implementation |
| API restart with migration V41 and demo cover upload | After implementation |
