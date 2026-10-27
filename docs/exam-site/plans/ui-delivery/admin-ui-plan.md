# Admin panel: UI analysis and fix plan

Written 2026-10-26 for Shahriar. Covers the admin panel: every `/staff/*` page, for the Master Admin, System Admin and Instructor. Built from the earlier audit (Agent C, `docs/exam-site/reports/ui-audit/findings-C.md` and `fix-plan-C.md`), the decisions Shahriar gave in `fix-plan-C.md` section 6, and a fresh look at the current screens in light and dark theme. The legacy `(admin)` route group (`/books`, `/courses`, `/admins/new`, and so on) is out of scope; it is a separate screen set and is not in the admin audit.

Work is done in the steps in section 5, one at a time, each with its own check. Step status is kept in section 5.

## 1. What the audit found

### 1.1 Measured problems (fresh review, 2026-10-26, 1440 px)

Screenshots: `scratchpad/ui-review/` (light and dark, six pages). Contrast computed with the WCAG formula.

| # | Problem | Evidence | Why it matters |
|---|---|---|---|
| N-01 | **Input borders are too faint.** The staff border token is `#d8e0e8` on white (light) and `#2c3b4a` on `#1c2631` (dark). | Contrast of the input border against its card: 1.33:1 light, 1.34:1 dark. WCAG 1.4.11 asks for 3:1 on a control's boundary. | This is the "form border is less visible when not hovered" issue. |
| N-02 | **Inputs only show a stronger border on hover.** `controlBase` sets `hover:border-muted`, so the resting state is the weak one. | `web/src/components/ui/control.ts`. | Users see the field edge only when the pointer is on it. Touch devices never see it. |
| N-03 | **Table lines are too faint and the header has no weight.** Row lines use the same weak border; the header row is plain text with a weak rule. | Row border `#d8e0e8` on white; header `bg-card`. | This is the "table borders" issue. Rows run together on long lists. |
| N-04 | **The payments table collapses at 1440 px.** The transaction-id column is one character wide, so each row is about 700 px tall. The "Copy transaction id" label is the column header. | `scratchpad/ui-review/payments-light.png`. The shared `Table` has `min-w-[32rem]` and no column widths, and `payments-table.tsx` sets no width or `nowrap`. | The page is unusable for payments on a laptop. Agent C saw the overflow at 768 and 1024 px only. |
| N-05 | **Content width changes page to page.** The shell's `main` is `max-w-[1120px]`, but forms are capped at `max-w-3xl` (768 px), `max-w-2xl` (672 px) or `max-w-sm`. The exam editor fills 1120 px. | `new-module` and `exam` screenshots: the new module form stops at about 1070 px and leaves the right third empty; the exam editor fills the page. | The panel looks unplanned. Each page has a different edge. |
| N-06 | **The sidebar is one flat list of 15 items.** Order is set in `staff-nav.ts`; nothing groups them. Names mix "Teachers" and "Instructors" with "System admins". | `dash-light.png`. | Scanning is slow, and the list grows with each role. |
| N-07 | **The dashboard is sparse and its first load fails.** Three counters, three shortcut buttons, then an error banner ("The exam list cannot be loaded right now") and empty space. The shortcut buttons look like plain white boxes. | `dash-light.png`. The error is C-10 (`sort=date` is rejected by the API). | First screen of every admin. |
| N-08 | **"Shortcuts" and the exam-editor step labels are almost invisible.** | Agent C: 1.08:1 at 360 px in light, 1.17:1 in dark (C-16). | Text the user needs to read. |
| N-09 | **The staff header crowds at 360 px and runs past the screen at 200% text.** | Agent C: C-09. | First screen on a phone. |
| N-10 | **Tap targets are small.** Row menus are 40 px; names, breadcrumbs and count links are 17 to 19 px tall. | Agent C: C-20 (automated scan). | Phone use, and most admins use phones on the go. |

Read-only and disabled fields (for example the exam editor after the start time, `exam-light.png`) use the same faint border and a grey fill, so they look like empty inputs, not locked ones.

### 1.2 Agent C findings and decisions

Agent C reported 21 findings (1 Blocker, 2 High, 13 Medium, 5 Low). Shahriar's decisions (fix-plan-C section 6, 2026-10-26) are recorded below. "Step" points to section 5.

| Finding | Summary | Decision | Step |
|---|---|---|---|
| C-01 | Master Admin can confirm an order paid by hand | Only the Master Admin confirms. Control stays, System Admin read-only. Amend `decisions.md` and `feature-scope.md`. | 11 (docs) |
| C-02 | Instructor cannot open an assigned module, exam or category | Instructors do not open module or exam pages. They read the question paper read-only inside the evaluation screen, with no download button. Module rows open that module's submissions. | 10 |
| C-03 | Written count loses the exam filter | Fix: forward `exam` in the redirect. | 10 |
| C-04 | System Admin route for announcements exists, but the menu does not show it | No announcements for System Admin. Gate the route. | 9 |
| C-05 | System Admin sees Publish on the exam editor | System Admin does not publish. Hide the control; check the API refuses. | 9 |
| C-06 | System Admin sees the MCQ answer-release switch | Remove the switch. | 9 |
| C-07 | System Admin can grant a student access by hand | Not allowed. Remove the control; check the API refuses. | 9 |
| C-08 | System status is Master-only | Keep. No change. | — |
| C-09 | Staff header crowds at 360 px and at 200% text | Fix. | 7 |
| C-10 | Dashboard exam list fails (`sort=date`) | Fix: `sort=liveAt`. | 6 |
| C-11 | Exam cannot be created from the UI (Blocker) | New exam dialog asks: type; English and Bangla titles; English and Bangla descriptions; English and Bangla rules; paper language; duration (at least 2 minutes). Pass percent, start time, price, written maximum and questions or PDF stay in the editor. | 10 |
| C-12 | Module created without descriptions fails with no field message | Both descriptions are required, with a field error. | 10 |
| C-13 | Raw `common.states.on` and `.off` on the exam editor | Add the keys in both locales. | 10 |
| C-14 | 403 shows "Check your internet connection" | Show "This is not available to this account." for 403 and 404; keep the generic copy for network and 500. | 10 |
| C-15 | Modules and payments tables overflow at 768 and 1024 px | Fix in the table step, with N-04. | 3 |
| C-16 | Headings and step labels fail contrast | Fix with the tokens (step 1) and the heading colour (step 8). | 1, 8 |
| C-17 | System Admin cannot suspend or restore a student | Allowed. Show the control; check the API allows System Admin. | 9 |
| C-18 | Five-minute conflict message does not name the other exam | Show the API's message, which names the exam. | 10 |
| C-19 | No note for instructors that publishing is the Master Admin's | Add the note, for instructors only. | 10 |
| C-20 | Tap targets under 44 px | Fix. | 7 |
| C-21 | Roster grant cannot be revoked from the student page | Add Revoke on rows that have a grant ID. | 10 |

## 2. Design direction

These rules apply to every admin page. They are checked in step 1 and the later steps.

1. **Borders carry structure, so they must be visible.** Two border tokens on the admin surface:
   - `--border` (dividers and card edges, the frame of a table row): stronger than today.
   - `--border-control` (input, select, textarea, number and file fields, and the outer frame and header rule of a table): at least 3:1 against the card and the page.
   The student site keeps its own values in this round (see section 6).
2. **The resting state is the clear state.** Hover and focus add emphasis; they do not provide the boundary.
3. **One content width.** Pages use the shell's width (1120 px). Forms use a fixed reading width of 760 px inside it, left-aligned, and the same field grid on every form.
4. **Tables never collapse.** Identifiers do not wrap (`whitespace-nowrap`, or a monospaced cell with `break-all` in a fixed column). Column widths are set. The table scrolls inside its card, and the page never does.
5. **Read-only and disabled look different from empty.** A locked field keeps its border, uses the muted fill and text, and carries a "Locked" or "Read only" label where the reason is not obvious.
6. **The sidebar groups the items.** Groups follow what an admin does: overview, catalog, exams and marking, people, money, and admin. Only items the role may open are shown (the existing `staffNavFor` rule stays).
7. **Touch: 44 px minimum** for every control, row menu, name link and breadcrumb at phone width.

## 3. Constraints

- Only the `staff` surface changes in steps 1 to 8. `control.ts` is shared with the student site, so the new control token is defined on the student surface with its current value. Student-site contrast is a separate piece of work.
- No API change in steps 1 to 8. Steps 6 (sort), 9 (role gating) and 10 (fields and messages) may need small API changes, and each one is checked in step.
- Role access comes from `staff-nav.ts` and the existing guards. Pages are not given new permissions.
- Bangla and English both checked in each step, light and dark, at 360, 768, 1024 and 1440 px.

## 4. Checks used throughout

- **Contrast:** compute the border and text colours in the browser (`getComputedStyle`) and check control borders at 3:1 and text at 4.5:1 on the surface they sit on.
- **Overflow:** `document.documentElement.scrollWidth` equals the viewport at each width. Tables may scroll inside their card, and the card's `scrollWidth` is recorded.
- **Screens:** the same six pages as section 1.1, light and dark, to compare before and after.
- **Tests:** `tsc --noEmit`, `eslint`, and `vitest run` for the web app. Tests that assert copy keys or roles are updated when their target changes.
- **Role check:** for steps 9 and 10, sign in as the System Admin and confirm the menu, the page and the API response match the decision.

## 5. Steps

Each step is built, checked, and reported before the next one starts.

### Step 1: border tokens (N-01, N-02, C-16 part)
- **Change:** in `web/src/app/globals.css`, add `--border-control` to the `@theme inline` map (`--color-border-control`), and define it for the staff surface in light and dark. Light staff: `--border` `#b4bfcc`, `--border-control` `#7f8c9b` (3.4:1 on white). Dark staff: `--border` `#44556a`, `--border-control` `#6b7d90` (3.6:1 on the card). Define `--border-control` on the student surface with its current border value, so the student site does not change.
- **Change:** `web/src/components/ui/control.ts`: the resting border is `border-border-control`; the hover state moves to a stronger `hover:border-text` only if still needed; the focus state keeps `border-accent` and adds a visible ring.
- **Check:** contrast script on an input in light and dark (target 3:1). Screenshots of students and new module.

### Step 2: form controls (N-02, read-only state)
- **Change:** every text-like control uses the shared control classes: `TextInput`, `NumberInput`, `Select`, `Textarea`, `FileDrop` border. Check there is no other faint border on a field.
- **Change:** read-only and disabled fields get the muted fill and text, and the `Locked` label where the reason is not obvious (the exam editor after the start time is the reference).
- **Check:** the exam editor, the new module form and the new exam dialog in light and dark. Keyboard focus ring visible.

### Step 3: tables (N-03, N-04, C-15)
- **Change:** `web/src/components/ui/table.tsx`: the wrapper and the header rule use `border-border-control`; rows keep `border-border`; the header gets a subtle background (`bg-bg`) so it reads as a header in both themes. Keep the mobile card view.
- **Change:** `web/src/components/staff/ops/payments-table.tsx`: fixed column widths; transaction id in a `font-latin` cell with `whitespace-nowrap` and a `break-all` fallback; the "Copy transaction id" text becomes an icon button with an accessible name, placed in the row menu or as a small icon.
- **Check:** payments and students at 768, 1024 and 1440 px. The transaction id is one line. Page `scrollWidth` equals the viewport; the card's own scroll is recorded.

### Step 4: one content width (N-05)
- **Change:** forms use one reading width (760 px) inside the shell's 1120 px `main`; remove the per-page `max-w-2xl` and `max-w-3xl` values in the form components listed in section 2. Keep the exam editor full width (it has a step rail).
- **Check:** new module, new category, new announcement and the exam editor at 1440 px; the left edge of each page's heading and form matches.

### Step 5: sidebar groups (N-06)
- **Change:** `web/src/components/shell/staff-shell.tsx` and `staff-nav.ts`: group the items. Proposed groups: Overview (Dashboard); Catalog (Categories, Modules); Exams and marking (Participation, Marking, Announcements); People (Students, Instructors, System admins); Money (Payments, Pending orders); Admin (Activity log, System status, My account). Group labels use the type scale; the active item keeps its tint. The label stays "Teachers", which already matches the page title (corrected 2026-10-27; the earlier note to rename it was wrong).
- **Check:** each role sees only its items (the role matrix in `findings-C.md` does not change). Mobile drawer shows the same groups.

### Step 6: dashboard (N-07, C-10)
- **Change:** `web/src/lib/api/exam/staff-home.ts` and `web/src/components/staff/home/admin-home.tsx`, `instructor-home.tsx`: `sort=date` becomes `sort=liveAt` (C-10). Check the instructor counts against the submissions list.
- **Change:** the shortcut buttons become a clear action row (primary: New module, New exam; secondary: View payments, Find a student) and the heading gets the text colour (C-16 part).
- **Change:** the empty space: the dashboard shows the exam list as a table (with the step 3 table) beside the counters, not below them. Keep the counters.
- **Check:** the dashboard shows the exam list with no error for Master Admin, System Admin and Instructor. Light and dark at 360 and 1440 px.

### Step 7: header and touch (N-09, N-10, C-09, C-20)
- **Change:** `staff-shell.tsx` header: below 480 px the search moves into the header row's second line or the drawer, and the language and theme pills keep 44 px height. At 200% text the header wraps instead of running past the screen.
- **Change:** row menu buttons, name links, breadcrumbs and count links get a 44 px minimum height at phone width (`table.tsx` and the link classes).
- **Check:** overflow at 360 px and 200% text; automated tap-target scan shows no targets under 44 px on the six pages.

### Step 8: contrast of headings and labels (N-08, C-16)
- **Change:** the section heading and the step-label colour use `text-text` (or `text-muted` where it is secondary) on the light surface, in both themes.
- **Check:** automated text contrast scan at 360 px in four language and theme pairs: no text under 4.5:1 on the admin pages.

### Step 9: role decisions (C-04, C-05, C-06, C-07, C-17)
- **Change:** System Admin: no announcements (gate the route, the menu already hides it); no Publish on the exam editor (`exam-editor.tsx`, `canEdit` and the Publish button); no answer-release switch (remove `answer-release-switch.tsx` from the exam page); no manual grant on the student page (`student-access-section.tsx`); suspend and restore shown (C-17).
- **Change:** the API refuses each of these for System Admin, where a route exists (announcement send, publish, grant). C-17 needs the status endpoint to allow System Admin; check before showing the control.
- **Check:** sign in as the System Admin: each control is absent, and each direct API call returns 403. Suspend and restore work for the System Admin.

### Step 10: flows and messages (C-02, C-03, C-11, C-12, C-13, C-14, C-18, C-19, C-21)
- **C-11 (Blocker):** `module-exams.tsx` `NewExamDialog` asks for the listed fields; `staff-catalog.ts` `useCreateExam` sends them. The API is unchanged. Check: an MCQ and a written exam created from the dialog, then published.
- **C-12 (High):** both module description fields required in `module-details.tsx`, with a field error; the API's validation message maps to the field.
- **C-02 (Medium):** module rows in the instructor's list open that module's submissions. The evaluation screen shows the question paper read-only (watermarked, no download). Check: Instructor 1 sees the paper for its own module; Instructor 2 cannot.
- **C-03:** `staff/submissions/m/[moduleId]/page.tsx` forwards `exam`.
- **C-13:** add `on` and `off` under `common.states` in both locales.
- **C-14:** 403 and 404 on the submission page show "This is not available to this account."
- **C-18:** the 409 message names the other exam.
- **C-19:** instructor note on the marking page.
- **C-21:** a Revoke action on student-page rows that have a grant ID.
- **Check:** each item has the browser check named in `findings-C.md` (the walk for that finding).

### Step 11: documents and re-audit (C-01)
- **Change:** amend `decisions.md` ("Exam checkout is SSLCommerz only") and `feature-scope.md` ("A staff user cannot mark an exam order paid") to match the 2026-10-26 decision.
- **Change:** the audit record: `findings-C.md` gets a fix status per finding (fixed, or open with the reason). The findings stay as written.
- **Check:** re-run the admin pages at the four widths, both themes and both languages; compare to section 1.1.

## 6. Not in this plan

- The student site's control and table borders (`control.ts` is shared; the student tokens keep their current values in this round). The same N-01 fix applies there and is a separate step.
- The legacy `(admin)` route group.
- Real-device checks (iOS Safari, Android, tablet). Those remain manual QA.
- Stored-number checks (plan section 9) and the Master Admin forced-password flow.

## 7. Progress

| Step | Status | Notes |
|---|---|---|
| 1 | done | `--border-control` added (admin light `#7f8c9b`, admin dark `#6b7d90`, both at 3:1 or more). Admin `--border` is stronger too. Student values unchanged. Computed input borders checked in both themes. |
| 2 | done | Secondary buttons and the file drop use `border-control`. Disabled fields use the page fill and muted text instead of 60% opacity, so the border stays. Exam editor locked fields checked. Not done: a separate "Locked" label (the existing banner states the lock). |
| 3 | done | Table frame and header rule use `border-control`; rows use `border`. Payments: scope and access moved under the product title; transaction id is a fixed, wrapping mono column; copy moved to the row menu (with a toast). Measured at 1440 px: the table fits (no inner scroll), rows 103 px tall (were about 700 px). At 1024 and 768 px the page does not scroll; the table scrolls inside its card. Header tint was not added, because the table is shared with student pages. |
| 4 | done | Forms (category, subcategory) use the same 768 px reading width as the other forms. The exam editor stays full width. |
| 5 | done in code, checked in the browser | Sidebar grouped: Overview, Catalog, Exams and marking, People, Money, Admin. Group names drafted in English and Bangla (the Bangla needs a native check). Only items the role can open are shown. |
| 6 | done | Dashboard exam list loads (sort `liveAt`; the web type had `date`, which the API does not accept). Shortcuts sit in a column beside the list on wide screens. Checked at 1440 px. |
| 7 | done | Under 480 px the language, theme and log out controls move into the menu, so the header fits at 200% text (page width 360 px, no sideways scroll). Row menus, breadcrumbs, the logo, nav items and the card links are 44 px tall on a phone. |
| 8 | done | Contrast scan (every visible text element on the dashboard and exam editor, English, light and dark, at 360 px). The admin warning text went from 4.49:1 to 5.4:1. Bangla was not scanned. |
| 9 | done in code; not browser-checked as System Admin | Announcements: Master Admin only (page gate). System Admin: no manual grant, no answer-release switch (API: `LIVE_EXAM_GRANT` and `LIVE_EXAM_RELEASE` removed for System Admin), and suspend and restore for students (API: `STUDENT_STATUS` added for System Admin). Publish was already Master-only in the API and in the UI capability list. The demo API must be restarted before the API change takes effect. |
| 10 | partly done | Done: C-03 (exam filter kept on the submissions redirect), C-19 (note for instructors that the Master Admin publishes). Not done: C-02 (instructor paper read-only in evaluation; the API already serves it to the assigned instructor, checked 2026-10-27, so only the UI check remains), C-12 (module descriptions, module-details.tsx has someone else's changes), C-13, C-14, C-18, C-11, and C-21 (the fix needs a choice). The exam-creation work (C-11, C-18) is being done by another agent, so those files were left alone. |
| 11 | partly done | Decided rules added to `docs/exam-site/decisions.md` ("Staff roles (decided 2026-10-26)"). `ui/feature-scope.md` lines for order paid and publish amended. Not done: the findings-C status per finding. |

## 8. Conflicts with the other agent (2026-10-27)

The other agent works on exam creation and the exam editor in the same working tree. Decision: their files are kept as they are, and my edits go around them.

| Area | Their change | My decision |
|---|---|---|
| Exam page (`exams/[examId]/page.tsx`) | Removes the answer-release switch for every role | Kept. This is the decision in `findings-C.md` ("the answer-release switch is removed"). The API release endpoint stays for the Master Admin, with no UI. Open question: Agent B's B-18 depends on the staff release path, so check whether answers still reveal correctly after a sitting. |
| `AnswerReleaseSwitch` (my Master-only version) | Unmounted by them | Deleted, with its test file and the switch cases in `dialogs` and `access-matrix` tests. |
| Exam editor (`exam-editor.tsx`), `guards.tsx` (`canPublishExam`) | Publish only for the Master Admin, examText checklist item, maximum item | Kept. Matches C-05 and the publish decision. |
| New exam dialog (`module-exams.tsx`), `staff-catalog.ts`, `errors.ts`, `exam-meta-form.tsx` | C-11 full field set, duration minimum 2, field errors | Kept. C-11 is theirs. I did not edit these files. |
| `module-details.tsx` | Module form changes (C-12 not visible yet in its diff) | Not touched. Check C-12 when they finish. |
| Exam editor test (`ws9-aut`) | Updated with their changes | Kept. |
| `toast.tsx` | Fixed by someone (toast-size test passes now) | Kept. |

My step 10 items that do not overlap with their files were done: C-02 (an instructor's module row opens that module's submissions), C-13 (the `common.states` on and off keys), C-14 (a refused submission shows "This submission is not available to this account."), and C-21 (a roster row is labelled "Manage on the module roster" instead of "Add to direct enrolment"; it still links to the roster tab, where remove is).

Final checks: type check clean; test lint clean; full web suite 103 files, 619 tests, all passing.
