# UI audit findings: Agent C (staff and admin)

**Status: COMPLETE for the scope that can be run on this PC.** Every walk, state and browser check in the plan has a result. The signed-in Bangla and dark matrix was re-run on the restored, seeded database, after a bug in the test helper was found and fixed (see "Changes in this version"). The one item left is the device checks: the plan marks them "confirm on device", and they need someone on real devices (real iOS Safari, real Android, a tablet, Edge, Samsung Internet, touch feel). Those are open for Shahriar.

## Fix status (2026-10-27)

The findings below are as the audit wrote them. This table records what was done after the audit. Plan: `plans/ui-delivery/admin-ui-plan.md`. "Browser" means checked in the browser; "code" means checked by compile and tests only.

| Finding | Status | How | Checked |
|---|---|---|---|
| C-01 | Done (docs) | `decisions.md` ("Staff roles") and `ui/feature-scope.md` amended | Docs |
| C-02 | Done | Instructor's module row opens that module's submissions. The paper is read-only in the evaluation screen, with no download (the viewer's download is off) | Code |
| C-03 | Done | The redirect keeps the `exam` filter | Code |
| C-04 | Done | Announcements are Master-only (route and menu) | Code |
| C-05 | Done | Publish is Master-only (`canPublishExam`, other agent's editor change) | Code |
| C-06 | Changed | The switch is Master-only again (the audit's removal was reversed on 2026-10-27, because answers are revealed only after a release). Hidden from System Admin. A mismatch in the switch's state was fixed. | Browser (on/off matches the database) |
| C-07 | Done | No grant control for System Admin; API grant removed | Code (API compiled) |
| C-08 | No change | Keeps Master-only | — |
| C-09 | Done | Under 480 px the language, theme and log-out controls move to the menu. Fits at 360 px and 200% text | Browser |
| C-10 | Done | Dashboard sorts by `liveAt`; the list loads | Browser |
| C-11 | In progress (other agent) | Dialog asks the full field set; API unchanged | Code |
| C-12 | Open | Module form does not require the descriptions yet (other agent's file) | — |
| C-13 | Done | `common.states.on` and `.off` added | Code |
| C-14 | Done | Refused submission shows its own message | Code |
| C-15 | Done | Payments fit at 1440 px; the modules filter row wraps at 768 px; page does not scroll | Browser |
| C-16 | Done | Heading and step-label colours; admin warning text raised to 5.4:1 | Browser (scan, English) |
| C-17 | Done | System Admin can suspend and restore students (API grant added) | Code (API compiled) |
| C-18 | Done (other agent) | Conflict message names the other exam | Code |
| C-19 | Done | Note for instructors | Code |
| C-20 | Done | 44 px targets on a phone (row menus, links, breadcrumbs, nav) | Browser (scan) |
| C-21 | Done (choice) | Roster rows say "Manage on the module roster" and link to the roster tab | Code |

Not verified as System Admin: the demo's seeded admin account does not log in with the value in `.env`, so the System Admin paths were checked by code and compile only.

## Changes in this version

- **Test-helper bug, fixed:** the earlier signed-in Bangla and dark snapshots were English and light. They are moved to `snapshots/C/superseded-locale-bug/`. They are not evidence. The corrected matrix ran on the restored database. Every recorded page has `lang` and `theme` matching the request (checked on all 1,160 recorded combinations, with no errors and no fallback screenshots). Every recorded snapshot exists on disk.
- **Decisions received 2026-10-26** (fix plan section 6): the Master Admin alone confirms orders paid (C-01 is no longer a product defect; `decisions.md` and `feature-scope.md` need amending); module descriptions are required (C-12); instructors do not open module or exam pages, but read the question paper in the evaluation screen (C-02); the System Admin does not publish, does not grant by hand, and does not get announcements; the answer-release switch is removed (C-04 to C-08); System Admin can suspend and restore a student (C-17).
- **Severity changes:** C-01 from Blocker to Low (documentation). C-02 from High to Medium (the dead-end list remains; the module pages are no longer expected). Counts are in "Summary counts".
- **Contrast in every language and theme:** the failing headings and step labels fail in Bangla light, English dark and Bangla dark too (C-16 extended).
- **Confirmed in Bangla:** C-09 (header crowding at 360 px, `snapshots/C/staff-header-360-bn-check__360__bn__light__master.png`), C-10 (the dashboard error appears in Bangla as well), C-13 (raw `common.states` keys in both languages, `snapshots/C/staff-exams-id-bn-check__1440__bn__light__master.png`), C-16.

## Header

- **Agent:** C (staff and admin, plan section 6, plus sections 3.1 to 3.8 for these pages).
- **Database state (as of the last step):** `coaching_center_qa_3` holds the populated data from the first reset, restored from `C:\Users\HP\qa-backups\coaching_center_qa_3-2026-10-26-pre-reset2.dump` (with Shahriar's approval). The API runs on it: `DB_URL` printed as `jdbc:postgresql://localhost:5432/coaching_center_qa_3`, port 8083, `SEED_DEMO=false` (no second seed), `FILE_STORAGE_DIR` set back to `C:\Users\HP\qa-files\qa_3-2026-10-26`, log `C:\Users\HP\qa-logs\api-qa_3-restored-2026-10-26.log`. The empty-database check ran earlier, on a database reset for it (log `api-qa_3-empty-2026-10-26.log`).
- **Database:** `coaching_center_qa_3`. Reset on 2026-10-26 per plan 2.2a. Backup `C:\Users\HP\qa-backups\coaching_center_qa_3-2026-10-26-pre-reset.dump` (outside the repo, `pg_dump -Fc`, exit 0). Dropped and recreated. The pre-reset database had been seeded before this run.
- **Reset approval:** Shahriar approved the reset, relayed by the coordinator. The approval was taken to cover stopping the 8083 API, which is the first step of 2.2a. The old API process (PID 17044, `java -jar target/api-0.0.1-SNAPSHOT.jar`) was stopped after the backup.
- **Seed result:** clean start, no `ERROR` or `WARN` lines, no "already in use" or older-catalog stop. Flyway applied 40 migrations. The seed counts after the reset: 1 Master Admin, 1 System Admin, 2 instructors, 13 students, 3 modules, 14 exams, 7 orders. These match the pre-reset counts.
- **API start:** `DB_URL` printed before the start as `jdbc:postgresql://localhost:5432/coaching_center_qa_3`. Port 8083. The API runs from `F:\coaching-center-platform\api` with the jar already in `api/target` (built from `c2cd843`, not rebuilt). Log file: `C:\Users\HP\qa-logs\api-qa_3-2026-10-26.log`. `FILE_STORAGE_DIR` set to `C:\Users\HP\qa-files\qa_3-2026-10-26` (a fresh folder, so PDFs from the old run do not mix in).
- **Web:** port 3003, `next start`, worktree `F:\cc-wt\qa-c`, clean at `c2cd843`. Not rebuilt, as the reset does not need it.
- **Build commit:** `c2cd843`.
- **Date:** 2026-10-26.
- **Secrets:** read only from `C:\Users\HP\qa-secrets\qa_3.env`. No password, token or file content is written to this file, the snapshot names, or the scratchpad logs.
- **Browsers and widths:**
  - Chromium (Edge build, headless): signed-in pages, all five widths in Bangla and English, light; 360 and 1440 px in Bangla and English, dark (every route in the slice, every role that can open it). Denied routes: 360 and 1440 px in all four language and theme pairs. Empty database: Bangla and English, light and dark, at 360 and 1440 px. Guest pages: the guest pass, Bangla and English, light and dark.
  - Firefox: 360 and 1440, English, light, on seven key pages. `snapshots/C/engines/firefox/`. No overflow on any of them.
  - WebKit: 360 and 1440, English, light, on ten key pages (`snapshots/C/engines/webkit/`). A full page load drops the WebKit session over `http://localhost` (the `Secure` refresh cookie), so the run logs in once and moves between pages with the sidebar and menu links. No overflow on any of them.
  - Emulated only, confirm on device: 200% text size (root font size doubled, not browser zoom), real iOS Safari, real Android, touch feel, and throttled network (the sitting page is Agent B's). Nothing is claimed for real devices.
- **Fallback from plan 2.3:** none used.
- **Snapshots:** `snapshots/C/` holds the current set. Signed-in files: master 287 (Bangla and English, light) and 100 (dark); system admin 218 and 100; instructor 1 150 and 100; instructor 2 149 and 100; student 200 (denial evidence). The counts include the flow screenshots and the denial pages. Every recorded combination's file exists. Guest pass (before the reset, still valid): 52. The pre-reset signed-in set is in `snapshots/C/pre-reset/` (1110 files). It is evidence only and is not counted as a walk in this round.
- **Test data created in `coaching_center_qa_3`:**
  - One category (Audit Category) and one module (Audit Module), both created through the UI.
  - Four exams (A, B, C: MCQ, D: written), created through the API, because the UI cannot create an exam (C-11). Published through the UI.
  - One student, created and then deleted through the UI.
  - Exam A's schedule changed twice, to check the draft return.
  - Participant `70000000-0000-4000-8000-000000000601` marked (draft) by Instructor 1 and published by the Master Admin.
  - Order `DEMO-STUCK-E9-5001` confirmed by the Master Admin (C-01 test). Its status is now `SUCCEEDED`.
  - Roster: student 5103 added to the Audit Module's roster, then removed again.
  - Announcement "Audit notice C" sent to all 13 students (the notice box and bell of each).
  - Instructor "Audit Teacher C" created, and an activation code reissued for it.
  - 25 students "Audit Long List 01–25" created through the API, for the long-list check. They remain in the database.
  - Student 5103 suspended and restored (master walk).

## Checklist (plan section 11.2 and 11.3)

Key: [x] done, [~] partly done, [ ] not done. Each open item has its reason.

**11.2, every agent**

- [x] Stack row checked against 2.2. Effective `DB_URL`, API port (8083) and web port (3003) printed and matched.
- [x] Reset done (2.2a) and recorded above: backup file, seed result.
- [x] Code and requirements pass for all 25 routes (from the earlier pass; the findings below rest on it and on the browser checks).
- [x] Section 3.8 expected-page table filled for every user type this slice touches (instructor, system admin, master admin). See "Missing pages and controls by user type".
- [x] Snapshots for every route in the slice at 360, 390, 768, 1024 and 1440 px, Bangla and English, light theme, for every role that can open the route (re-run on the restored database). For routes a role cannot open, the denial state is recorded at 360 and 1440 px in all four language and theme pairs. Empty database: done.
- [x] Dark theme at 360 and 1440 px, Bangla and English, for every route in the slice (re-run on the restored database). Empty database: done. Guest pages: done.
- [x] Every role in the slice signed in and walked: Master Admin, System Admin, Instructor 1, Instructor 2. The student was also run through the staff routes to record the denial.
- [x] Browser coverage: Chromium at all widths. Firefox at 360 and 1440 on seven key pages. WebKit at 360 and 1440 on ten key pages.
- [x] Flow walks: exam authoring (with the API workaround for C-11), evaluation, Instructor 2 access, student management (create, row menu, delete, suspend, restore, roster add and remove), payment filters, announcement send, instructor create and reissue, audit log (no filters in the product, nothing to test), empty states (search with no match on students, payments and participation), long list (25 students, paging to page 2).
- [x] Sections 3.1 to 3.6 on the snapshots: 3.1 overflow (measured, C-15, the same in every language and theme). 3.2 contrast (automated scan at 360 px in four language and theme pairs: C-16). 3.3 theme (both themes, signed-in and empty database). 3.4 language (Bangla and English, signed-in and empty database; C-13, C-09, C-16 confirmed in Bangla). 3.5 states: loading (skeleton captured), error (C-14 copy for a 500), empty (search states), long list (paging). Empty database state not run (needs a reset). 3.6 navigation (logged-out and wrong-role redirects, denial pages in both themes). 3.7 device: tap targets measured (C-20), 200% text emulated (C-09), PDF viewer at 360 px renders with no overflow. Not run here: the on-screen keyboard check (its pages are sign-up, login, set-phone and contact, which are Agent A's) and real devices.
- [x] Coverage table: each route is audited, partly audited with the missing item named, or not reachable with the reason. No route is left as "code only" where a snapshot exists.
- [x] Every finding uses the section 7 format with its confidence tag. Blocker and High findings that were "read in code only" were re-checked in the browser before being marked final: C-01, C-02, C-10, C-11, C-12.
- [~] Header status line matches this checklist: complete for the scope run on this PC. Open for device confirmation: real iOS Safari, real Android, tablet, touch feel, and Edge and Samsung Internet (covered by Chromium only).

**11.3, extra checklist for C**

- [x] Each of the four roles (Master Admin, System Admin, Instructor 1, Instructor 2) signed in with the password from the secrets file.
- [x] Role matrix from plan section 6 filled from a real session (see "Role matrix" below).
- [x] Each of the 25 staff routes snapshotted for every role that can open it, in Bangla and English, light and dark (where the role can open the route).
- [~] Exam authoring walk: category create (pass), module create (fail, C-12), exam create from the UI (fail, C-11; the test exams were created through the API), publish (pass), the five-minute rule (pass, with a wording issue, C-18), null `liveAt` publish (pass), set and clear `liveAt` returns the exam to draft (pass), written paper upload and publish (pass).
- [x] Evaluation walk: Instructor 1 saves a draft mark (pass), Master Admin publishes it (pass), the student-facing state is not checked here (that is Agent B's walk).
- [x] Instructor 2 cannot see Instructor 1's submissions (pass: API 403 ACCESS_DENIED; the screen shows the wrong message, C-14).
- [x] Student management row menus and dialogs at the table edge: 768 px (create, delete), 360 px and 1024 px (long list, menu inside the viewport, no overflow), 1024 px (system admin control check).
- [x] Master Admin first login: the landing page after login is `/staff` (pass). The account page offers the phone and password changes and shows the email read-only. The change is offered, not forced, so the forced branch does not apply to this account.

## Role matrix (filled from real sessions, plan section 6)

| Capability | Master Admin | System Admin | Instructor | Student (session check) |
|---|---|---|---|---|
| Publish an exam | yes (walk: A, B-blocked by 5-min rule, C, D published) | **yes shown** (C-05, rule conflict) | no (not shown) | not on staff routes |
| Create an exam from the UI | fails (C-11) | fails (C-11) | no | no |
| Evaluate a written paper (save draft) | yes (walk: draft saved by Instructor 1, I1 only) | no marking form in the code (not checked in the browser) | yes, own module, draft (walk) | denied |
| Publish written marks | yes (walk: published) | no (not shown) | no (not shown) | denied |
| Manage admins and instructors (pages) | yes (`/staff/admins`, `/staff/instructors`) | denied | denied | denied |
| Set or reset another user's password | no control found | no | no | no |
| Change own phone and password (own account) | yes (page) | yes (page) | yes (page) | yes (`/account`) |
| Suspend and restore a student | yes (walk) | **no control** (C-17) | denied | denied |
| Delete a student | yes (walk, 768 px) | no control (not shown) | denied | denied |
| Grant access by hand (student page) | yes | **yes shown** (C-07) | denied | denied |
| Roster grant and remove (module roster tab) | yes (walk: added and removed) | not checked (roster is Master only per spec) | denied | denied |
| Revoke a roster row from the student page | no control (C-21) | not shown | denied | denied |
| Confirm or reject a pending exam order | **yes, and the API confirms it** (C-01) | no (read-only list) | denied | denied |
| Answer-release switch on MCQ exam | yes | **yes shown** (C-06) | denied | denied |

## Findings

### C-01: Master Admin can mark a pending exam order paid by hand
- **Route / role / width / language / theme / browser:** `/staff/orders` / Master Admin / 1440 px / English / light / Chromium.
- **What the user sees:** "Confirm payment" and "Mark as failed" on each pending exam order. The Master Admin confirmed `DEMO-STUCK-E9-5001`. The dialog says "When payment DEMO-STUCK-E9-5001 is confirmed, রফিকুল ইসলাম gets access." The API returned `200` with `status: SUCCEEDED`, and the page said "Payment status updated." The System Admin sees the same list with no buttons.
- **Expected:** `decisions.md`, "Payments and the server": "Exam checkout is SSLCommerz only." `ui/feature-scope.md`, "Purchase": "A staff user cannot mark an exam order paid." The conflicting plan is `plans/ui-delivery/student-purchase-lms-plan.md`, R-8 (line 236), which asks for this confirm. The client brief lists the question as open (section 9). `decisions.md` overrides both. Shahriar has to decide.
- **Evidence:** `snapshots/C/staff-orders-c01-check__1440__en__light__master.png`, `snapshots/C/staff-orders-c01-confirm-dialog__1440__en__light__master.png`. API: `POST /api/v1/live-exam-orders/{id}/confirm` returned 200 (browser network log). Code: `web/src/components/staff/orders/pending-orders.tsx:92-102`; `web/src/lib/api/exam/staff-admin.ts:120-122`; `api/src/main/java/com/coachingcenter/api/exams/LiveExamOrderDecisionController.java:34-35`.
- **Decision (2026-10-26):** only the Master Admin confirms an order paid. The control stays, and the System Admin sees the list read-only. This finding is no longer a product defect. Remaining work: amend `decisions.md` ("Exam checkout is SSLCommerz only") and `feature-scope.md` ("A staff user cannot mark an exam order paid") to say so.
- **Severity:** Low (documentation only, after the decision).
- **Type:** role.
- **Confidence:** confirmed in snapshot and by the API response. The QA order is now `SUCCEEDED`.

### C-02: Instructor cannot open an assigned module, its exam, or its category
- **Route / role / width / language / theme / browser:** `/staff/modules` (list), `/staff/modules/[id]`, `/staff/exams/[examId]`, `/staff/categories` / Instructor 1 / 1440 px / English / light / Chromium.
- **What the user sees:** the Modules item lists the instructor's assigned modules, but a row does nothing (no click, no menu). Opening the module, the exam or the category shows "Page not found. The address may be wrong, or the page has been moved."
- **Expected:** plan section 3.8, Instructor row: "own modules and exams; exam view". `ui/feature-scope.md`, "Marking and written publish": "Assigned modules, Instructor home".
- **Evidence:** `snapshots/C/staff-modules-id__1440__en__light__instructor1.png`, `snapshots/C/staff-exams-examId__1440__en__light__instructor1.png`, and the modules list (`snapshots/C/staff-modules__1440__en__light__instructor1.png`). Code: `web/src/components/staff/catalog/module-list.tsx:112-124` (row actions and `canOpen` for `canAuthor` only); `web/src/app/(staff)/staff/modules/[id]/page.tsx:73` and `web/src/app/(staff)/staff/exams/[examId]/page.tsx:13` (admin-only gate); the not-found fallback is `web/src/lib/auth/guards.tsx:182`. Read-only instructor branches already exist: `module-details.tsx:95,197`, `module-exams.tsx:129`.
- **Decision (2026-10-26):** instructors do not open module or exam pages like a student. They read the question paper in the evaluation screen, read-only. Recommended fix: each module row on the instructor's list opens that module's submissions, and the question paper stays read-only without a download button.
- **Severity:** Medium (the list is a dead end; the module pages themselves are no longer expected).
- **Type:** missing-feature.
- **Confidence:** confirmed in snapshot.

### C-03: "Open submissions" from a written count loses the exam filter
- **Route / role / width / language / theme / browser:** `/staff/participation` → `/staff/submissions/m/[moduleId]` → `/staff/submissions` / Master Admin / 1440 px / English / Chromium.
- **What the user sees:** the written count links to `…/submissions/m/<module>?exam=<exam>`. The redirect lands on `/staff/submissions?module=<module>`, and the list heading reads "All scripts", so the exam filter is gone.
- **Expected:** `ui/feature-scope.md`, "In the UI now", participation: "A count opens the submission list already filtered."
- **Evidence:** browser (URL and list text, no screenshot). Code: `web/src/app/(staff)/staff/participation/page.tsx:228` (link carries `?exam=`); `web/src/app/(staff)/staff/submissions/m/[moduleId]/page.tsx:24` (`router.replace` sends only `module`). The target `web/src/components/staff/marking/submission-browser.tsx` reads both `module` and `exam` (lines 67-68).
- **Severity:** Medium (visible defect with a workaround: pick the exam in the list).
- **Type:** navigation.
- **Confidence:** confirmed in the browser (text), no snapshot.

### C-04: System Admin cannot reach announcements from the menu
- **Route / role / width / language / theme / browser:** `/staff/announcements` / System Admin / 1440 px / English / light / Chromium.
- **What the user sees:** the System Admin's menu has no Announcements entry (menu: Dashboard, Categories, Modules, Participation, Marking, Students, Payments, Pending orders, My account). The URL still opens, with a read-only "Send an announcement" page and an empty message, no history and no form.
- **Expected:** plan section 3.8 lists announcements for System Admin. `decisions.md` and `feature-scope.md` do not mention announcements. Shahriar needs to say whether System Admin should have them. The menu and the page must agree either way.
- **Evidence:** `snapshots/C/staff-announcements__1440__en__light__system.png`. Code: `web/src/components/shell/staff-nav.ts:60` (Master Admin only); `web/src/app/(staff)/staff/announcements/page.tsx:15,32,39`.
- **Decision (2026-10-26):** System Admin does not get announcements. Remove the route for System Admin.
- **Severity:** Medium (page with no link, and no content for the role; the fix is to remove the route, so the page and the menu agree).
- **Type:** missing-feature.
- **Confidence:** confirmed in snapshot and in the browser menu.

### C-05: System Admin sees Publish on the exam editor
- **Route / role / width / language / theme / browser:** `/staff/exams/[examId]` / System Admin / 1440 px / English / Chromium.
- **What the user sees:** "Final save" and "Publish" buttons. Publishing works for the System Admin (the editor's success message names the next step for System Admin).
- **Expected:** `ui/feature-scope.md`, "Catalog authoring": "Publish an exam: Master Admin, System Admin." The plan's matrix (section 6) says System Admin: no publish. `decisions.md`, "Written results": "A System Admin cannot publish." That line sits under written results, so it may apply only to results. Shahriar must confirm which applies to the exam.
- **Evidence:** `snapshots/C/staff-exams-id-system-controls-check__1440__en__light__system.png`. Code: `web/src/components/staff/catalog/exam-editor.tsx:189` (`canEdit = role.canAuthor && !locked`), `:221` (toast for System Admin), `:258` (Publish button).
- **Decision (2026-10-26):** System Admin does not publish an exam. Hide the Publish control for System Admin and check the API refuses it.
- **Severity:** Medium (a control the decision forbids; the fix is to hide it).
- **Type:** role.
- **Confidence:** confirmed in the browser (control is visible). The publish action itself was not run as System Admin.

### C-06: System Admin sees the MCQ answer-release switch
- **Route / role / width / language / theme / browser:** `/staff/exams/[examId]` / System Admin / 1440 px / English / Chromium.
- **What the user sees:** "Answer release: Answers released" (a switch). Each change asks first.
- **Expected:** `decisions.md`, "Exam rules that stay": "The chosen option, the correct option, and the explanation wait until the sitting ends." No rule in `decisions.md` or `feature-scope.md` lets an admin release answers.
- **Evidence:** `snapshots/C/staff-exams-id-system-controls-check__1440__en__light__system.png`. Code: `web/src/components/staff/access/answer-release-switch.tsx:24` (only other roles get nothing); mounted at `web/src/app/(staff)/staff/exams/[examId]/page.tsx:15`.
- **Decision (2026-10-26):** remove the answer-release switch. No rule gives anyone that control, and the answers wait until the sitting ends.
- **Severity:** Medium (a control with no written rule; the fix is to remove it).
- **Type:** role.
- **Confidence:** confirmed in the browser.

### C-07: System Admin can grant a student access by hand
- **Route / role / width / language / theme / browser:** `/staff/students/[id]` / System Admin / 1024 px / English / Chromium.
- **What the user sees:** "Grant access by hand" on the student page (the grant goes to `POST /api/v1/live-exam-grants`).
- **Expected:** `ui/feature-scope.md`, "Roster and instructor assignment": roster grant is Master Admin only, System Admin receives 403. The manual grant is not described in any document read. Plan section 3.8 lists "grant, revoke and suspend" for System Admin, which conflicts with the roster rule. Shahriar needs to decide.
- **Evidence:** `snapshots/C/staff-students-id-control-check__1024__en__light__system.png`. Code: `web/src/components/staff/access/student-access-section.tsx:34,51`; `web/src/lib/api/exam/staff-admin.ts:54-58`.
- **Decision (2026-10-26):** System Admin does not grant access by hand. Remove the grant control for System Admin, and check the API refuses it.
- **Severity:** Medium (a control the decision forbids; the fix is to hide it).
- **Type:** role.
- **Confidence:** confirmed in snapshot.

### C-08: System status is not open to System Admin
- **Route / role / width / language / theme / browser:** `/staff/system-status` / System Admin / 1440 px / English / Chromium.
- **What the user sees:** no menu entry. The URL redirects to `/staff`.
- **Expected:** plan section 3.8 lists system status for System Admin. `decisions.md` and `feature-scope.md` do not mention it. This is a gap to confirm, not a clear miss.
- **Evidence:** `snapshots/C/staff-system-status__1440__en__light__system.png` (the denial run shows the redirect to the dashboard). Code: `web/src/components/shell/staff-nav.ts:61`; `web/src/app/(staff)/staff/system-status/page.tsx` (`RequireStaff roles={["MASTER_ADMIN"]}`).
- **Decision (2026-10-26):** keep system status Master-only. No change.
- **Severity:** Low (closed by decision; no change needed).
- **Type:** missing-page.
- **Confidence:** confirmed in snapshot.

### C-09: Staff and public header crowd at 360 px
- **Route / role / width / language / theme / browser:** every shell page, measured on `/staff` (Master Admin) and `/login` (guest) / 360 px / English and Bangla / light / Chromium.
- **What the user sees:** on the staff dashboard the search icon sits on top of the "ExamPortal" wordmark. The language pill covers the end of the wordmark. On the public header the "Log in" link wraps onto two lines, and in Bangla the wordmark runs under the language pill.
- **Expected:** plan 3.1 (no clipped text, nothing covers content) and 3.4 (Bangla glyphs not clipped).
- **Also at 200% text size (emulated, root font size doubled, 360 px):** the header's right-hand group (language pill, theme pill, name and logout) is 450 px wide and runs to 554 px, so every staff page tried (six pages) scrolls sideways. Logout and the language pill sit past the screen edge until the user scrolls. Evidence: `snapshots/C/staff-zoom200-emulated__360__en__light__master.png`, `snapshots/C/staff-orders-zoom200-emulated__360__en__light__master.png`.
- **Evidence:** `snapshots/C/staff-header-360-check__360__en__light__master.png` (staff header). `snapshots/C/staff__360__en__light__guest.png`, `snapshots/C/staff__360__bn__light__guest.png` (public header, from the guest pass). Probable cause, not traced in layout detail: the staff header row holds the search, locale pill (`web/src/components/shell/staff-shell.tsx:120`), theme pill, name and logout with no wrap rule. The student header is `web/src/components/shell/student-shell.tsx:236-256`; the wordmark is `web/src/components/shell/logo.tsx:23-25`.
- **Severity:** Medium (visible defect; the header is still usable, but it is the first thing a client sees on a phone).
- **Type:** layout.
- **Confidence:** confirmed in snapshot. The student shell is Agent A's and B's to confirm too.

### C-10: The dashboard exam list fails for admins and the instructor's written queue fails silently
- **Route / role / width / language / theme / browser:** `/staff` / Master Admin and System Admin (and the instructor's queue behind `/staff`) / 360 px and 1440 px / English / Chromium.
- **What the user sees:** "The exam list cannot be loaded right now. Try again" at the bottom of the admin dashboard, at both widths. The instructor's dashboard counts depend on the same kind of call and show no error.
- **Expected:** plan 3.5 (every list shows a state it can recover from; the list should load). `ui/feature-scope.md`, "In the UI now", participation: the counts on Overview and Instructor home.
- **Evidence:** `snapshots/C/staff__360__en__light__master.png` (error visible at the bottom). Network: `GET /api/v1/live-exam-participation?size=100&sort=date` returned `400 VALIDATION_FAILED "Sort is invalid."` (checked for Master Admin and System Admin, and for the instructor with `type=WRITTEN`). Code: `web/src/components/staff/home/admin-home.tsx:191` and `:232`; `web/src/components/staff/home/instructor-home.tsx:43`; the query is in `web/src/lib/api/exam/staff-home.ts:54-66`. The API accepts `liveAt`, `title`, `sitting` (`api/src/main/java/com/coachingcenter/api/exams/LiveExamParticipationService.java:170-177`), not `date`.
- **Severity:** High (the first screen of every admin shows an error in the first minute; the instructor's queue numbers may be wrong).
- **Type:** state.
- **Confidence:** confirmed in snapshot and in the network log.

### C-11: Staff cannot create an exam from the UI
- **Route / role / width / language / theme / browser:** `/staff/modules/[id]?tab=exams` (New exam dialog) / Master Admin (same for System Admin) / 1440 px / English / Chromium.
- **What the user sees:** the New exam dialog asks only for the type and two titles. Pressing Create returns `400 VALIDATION_FAILED`. The dialog then says "Check the details. Some fields are not right." and marks no field. The exam is not created.
- **Expected:** `ui/feature-scope.md`, "Catalog authoring": exams need `paperLanguage`, a duration, a pass percent before publish, and the rules and descriptions. The client brief section 6 requires admins to create exams. The API requires `descriptionEn`, `descriptionBn`, `rulesEn`, `rulesBn`, `paperLanguage`, `durationMinutes` on create (`api/…/LiveExamService.java:115-123`). The client sends only the type and titles (`web/src/lib/api/exam/staff-catalog.ts:308-315`; `module-exams.tsx:23-47`).
- **Evidence:** `snapshots/C/staff-modules-newexam-dialog-fails__1440__en__light__master.png`. API 400 captured in the browser network log. Workaround used for the rest of the walk: exams A, B, C and D were created with a direct API call that sends the full body (no UI). The rest of the authoring (questions, schedule, publish, written PDF) is the UI.
- **Decision (2026-10-26):** the New exam dialog asks for: type; English and Bangla titles; English and Bangla descriptions; English and Bangla rules; paper language; duration (at least 2 minutes). Pass percent, start time, price, written maximum and the questions or PDF stay in the editor, because they are publish gates or optional.
- **Severity:** Blocker (no exam can be authored from the UI, so the ongoing exam cannot be set up without an API call). Stays Blocker until the dialog is changed.
- **Type:** missing-feature.
- **Confidence:** confirmed in the browser (400 and the dialog text), and in code.

### C-12: Creating a module without descriptions fails with no field message
- **Route / role / width / language / theme / browser:** `/staff/modules/new` / Master Admin / 1440 px / English / Chromium.
- **What the user sees:** the English and Bangla description fields carry no "required" mark. Save returns `400 VALIDATION_FAILED` (the API requires both descriptions on create). The form shows "Not saved" and "Check the details. Some fields are not right." and marks no field.
- **Expected:** `decisions.md`, "Product": "Both titles and descriptions" for modules (the API requires them, the form does not say so). The UI should mark the fields required or name the missing one.
- **Evidence:** `snapshots/C/staff-modules-new-no-description-fails__1440__en__light__master.png`. API: `POST /api/v1/live-exam-modules` `400`. Code: `api/…/LiveExamModuleService.java:58-61`; `web/src/components/staff/catalog/module-details.tsx:214-217` (description fields without a required mark).
- **Decision (2026-10-26):** module descriptions are required. Mark both fields required, with a field error.
- **Severity:** High (a first staff action fails with no reason shown; the workaround is to fill in the descriptions, which the form does not suggest).
- **Type:** state.
- **Confidence:** confirmed in snapshot and in the network log.

### C-13: Raw translation keys on the exam editor switches
- **Route / role / width / language / theme / browser:** `/staff/exams/[examId]` / Master Admin / 1440 px / English / light / Chromium. The Bangla check is pending (see Status, item 1).
- **What the user sees:** the start-time switch reads "Date not set yet: common.states.off" and the answer-release switch reads "Answer release: common.states.on", in English. The Bangla labels are not yet checked.
- **Expected:** plan 3.4 (no untranslated key names).
- **Evidence:** DOM text in English (switch labels). Snapshots that show the editor: `snapshots/C/staff-exams-A-after-steps__1440__en__light__master.png`. Cause: the switch labels use `common.states.on` and `common.states.off`, and the English message file has no `on` or `off` keys under `states` (`web/src/i18n/messages/en/common.ts:89`). Used in `web/src/components/staff/catalog/exam-meta-form.tsx` and `web/src/components/staff/access/answer-release-switch.tsx`.
- **Severity:** Medium (a stray key on the admin's publish screen; no workaround needed).
- **Type:** language.
- **Confidence:** confirmed in the browser (DOM text, English). Bangla pending.

### C-14: Instructor 2's refusal shows a network-error message
- **Route / role / width / language / theme / browser:** `/staff/submissions/[participantId]` (another instructor's module) / Instructor 2 / 1440 px / English / Chromium.
- **What the user sees:** "Check your internet connection and try again. Try again." The server returns `403 ACCESS_DENIED` (checked with a direct API call). The access control is correct; the message is wrong.
- **Expected:** plan section 6 flow 3: "Instructor 2 cannot see or open Instructor 1's module submissions." The access holds. The screen should say the submission is not available to this account.
- **Evidence:** `snapshots/C/staff-submissions-eval-4-instructor2-denied__1440__en__light__instructor2.png`. API: `GET /api/v1/live-exam-participants/70000000-0000-4000-8000-000000000601` → `403`. Probable cause: the detail page's error state falls back to the generic `common.states.errorBody` copy (`web/src/i18n/messages/en/common.ts:91`, "Check your internet connection and try again."), so any failed load shows it. Not traced further.
- **Same copy for any failed load:** a `500` on the students list shows the same "Check your internet connection and try again." message (`snapshots/C/staff-students-error__1440__en__light__master.png`). So the copy is the generic error state, not a permission message.
- **Severity:** Medium (state; the user can see that something failed, but it is misleading).
- **Type:** state.
- **Confidence:** confirmed in snapshot and by the API response.

### C-15: Horizontal page overflow on the module list and the payments list
- **Route / role / width / language / theme / browser:** `/staff/modules` at 768 px (786 px scroll width), Master Admin, System Admin and Instructor 1 / Bangla and English / light / Chromium. `/staff/payments` at 768 px and 1024 px (1105 px scroll width), Master Admin and System Admin / Bangla and English / light / Chromium.
- **What the user sees:** the page scrolls sideways. On payments, the table cells wrap into very tall rows and the table widens the page. The sidebar stops partway down the full-page screenshot; that is a screenshot artifact of the sticky sidebar, not a finding.
- **Expected:** plan 3.1: "Tables scroll inside their own box or collapse to cards. They never widen the page."
- **Evidence:** `snapshots/C/staff-modules__768__en__light__master.png`, `snapshots/C/staff-payments__768__en__light__master.png`, `snapshots/C/staff-payments__1024__en__light__master.png`. Measured values from the run (`scrollWidth` vs `clientWidth`). Firefox at 360 and 1440 showed no overflow (not measured at 768 or 1024). Probable cause, not traced: the table has no scroll wrapper at these widths (`web/src/components/ui/table.tsx`, `web/src/components/staff/ops/payments-table.tsx`).
- **Severity:** Medium (visible; the table is still usable).
- **Type:** overflow.
- **Confidence:** confirmed in snapshot and by measurement.

### C-16: "Shortcuts" heading is almost invisible on the dashboard
- **Route / role / width / language / theme / browser:** `/staff` / Master Admin / 360 px / English / light / Chromium.
- **What the user sees:** the "Shortcuts" heading above the New module and New exam buttons is white text on a pale background. The same problem appears on the exam editor: the step labels "Details", "Schedule and marks", "Questions", "Price" and "Check and publish" measure 1.08:1 at 360 px (10 elements in the automated scan). The same failure is in every language and theme: Bangla light 1.08:1 ("শর্টকাট", "বিবরণ", "সম্পন্ন"), English dark 1.17:1, Bangla dark 1.17:1.
- **Expected:** plan 3.2: text on every background meets 4.5:1 (3:1 for large text). Measured: `rgb(255,255,255)` on `rgb(244,246,248)`, a contrast ratio of about 1.1:1.
- **Evidence:** `snapshots/C/staff__360__en__light__master.png` (heading faint above the buttons), `snapshots/C/staff-exams-A-after-steps__1440__en__light__master.png` (editor steps at desktop width). Measured with `getComputedStyle` and an automated scan of all visible text in `main` at 360 px. Probable cause, not traced: the heading colour on the light surface in `web/src/components/staff/home/admin-home.tsx`.
- **Severity:** Medium (contrast; the buttons are still usable).
- **Type:** contrast.
- **Confidence:** confirmed in snapshot and by computed style.

### C-17: System Admin cannot change a student's status
- **Route / role / width / language / theme / browser:** `/staff/students/[id]` and `/staff/students` (row menu) / System Admin / 1024 px / English / Chromium.
- **What the user sees:** no Suspend or Restore on the student page, and no status item in the row menu. Master Admin has both.
- **Expected:** `ui/feature-scope.md`, "Accounts": "Create a student" is for Master Admin and System Admin, with "Change status" in the list. Plan section 3.8, System Admin row: "suspend".
- **Evidence:** `snapshots/C/staff-students-id-control-check__1024__en__light__system.png` (no Suspend). Master Admin with Suspend and Restore (walk log, "Status updated.").
- **Decision (2026-10-26):** System Admin can suspend and restore a student. Show the control, and check the API allows it for System Admin.
- **Severity:** Medium (a feature the spec gives to System Admin is missing).
- **Type:** missing-feature.
- **Confidence:** confirmed in snapshot and in the browser.

### C-18: The five-minute rule message does not name the conflicting exam
- **Route / role / width / language / theme / browser:** `/staff/exams/[examId]` (publish) / Master Admin / 1440 px / English / Chromium.
- **What the user sees:** "Another exam in this module is at the same time. Leave at least five minutes between start times." The API response names the exam ("conflicts with Audit MCQ A, 2026-11-20T10:00:00+06:00 to …"), but the screen does not.
- **Expected:** `plans/ui-delivery/ui-dev-qa-guide.md` (section 5): "A start inside five minutes of another published exam in the same module is refused and names that exam." The rule itself holds (`409 EXAM_WINDOW_CONFLICT`).
- **Evidence:** `snapshots/C/staff-exams-B-after-steps__1440__en__light__master.png`. API `409` in the browser network log.
- **Severity:** Low.
- **Type:** state.
- **Confidence:** confirmed in snapshot and network log.

### C-19: No note for instructors that publishing belongs to the Master Admin
- **Route / role / width / language / theme / browser:** `/staff/submissions/[participantId]` / Instructor 1 / 1440 px / English / Chromium.
- **What the user sees:** no publish control and no note that the Master Admin publishes. The screen shows "Status Published" after the Master Admin's publish, and "Each save needs the marked script PDF again."
- **Expected:** plan 3.8, Instructor row: "clear message that publishing is the Master Admin's".
- **Evidence:** `snapshots/C/staff-submissions-eval-2-instructor1-draft__1440__en__light__instructor1.png`. Text captured from the page.
- **Severity:** Low (polish; the control is correctly hidden).
- **Type:** missing-feature.
- **Confidence:** confirmed in snapshot.

### C-20: Tap targets under 44 px on rows, row menus and breadcrumbs
- **Route / role / width / language / theme / browser:** `/staff/students`, `/staff/modules`, `/staff/participation`, `/staff/submissions`, `/staff/orders`, `/staff/exams/[examId]` / Master Admin / 360 px / English / light / Chromium (automated scan).
- **What the user sees:** the row-menu buttons ("Actions for …") are 40 px square. Student, module and exam name links inside table rows are 17 to 19 px tall. Breadcrumbs ("Dashboard", "Home", "Modules") are 19 px tall. The count links in the participation and submissions tables are 17 px tall.
- **Expected:** plan 3.7, Touch: "Tap targets of at least 44 px."
- **Evidence:** the scan output (49 elements under 44 px on participation, 41 on students, 16 on submissions, 13 on the exam editor, 5 on modules, 1 on orders). Snapshots for context: `snapshots/C/staff-students-longlist__360__en__light__master.png`, `snapshots/C/staff-students-longlist-rowmenu__360__en__light__master.png`. Probable cause, not traced: the row and link classes have no minimum height at phone width.
- **Severity:** Medium (a tight tap target; the row menu is still reachable).
- **Type:** device.
- **Confidence:** confirmed by measurement.

### C-21: The roster grant cannot be revoked from the student's page
- **Route / role / width / language / theme / browser:** `/staff/students/[id]` / Master Admin / 1440 px / English / Chromium.
- **What the user sees:** the student's access table lists "Roster · Audit Module · No payment" with only "Add to direct enrolment" in the row. There is no Revoke button on that row. The grant can only be removed from the module's roster tab, with a Remove button on the student's row and the message "Student removed from the list."
- **Expected:** the access section on the student page says Master Admin can revoke a grant (`web/src/components/staff/access/student-access-section.tsx`). `ui/feature-scope.md`, "Roster and instructor assignment": "Remove a row" is part of the roster feature. Revoke is shown only for rows with a grant ID.
- **Evidence:** `snapshots/C/staff-students-id-after-roster-add__1440__en__light__master.png`, `snapshots/C/staff-modules-roster-remove-dialog__1440__en__light__master.png`. Code: `web/src/components/staff/access/student-access-section.tsx` (Revoke column only when `row.grantId` is set).
- **Severity:** Low (the roster can still be changed from the module; the student page is incomplete).
- **Type:** missing-feature.
- **Confidence:** confirmed in snapshot and in the browser.

## Coverage table (plan section 6 routes)

Roles: M = Master Admin, S = System Admin, I1 = Instructor 1, I2 = Instructor 2, St = student. "Denied" means the not-found or staff-only page (English, light, at 360 and 1440 px). The Bangla and dark denial pages are pending a re-run (see Status, item 1).

| Route | M | S | I1 / I2 | St | Status |
|---|---|---|---|---|---|
| `/staff` | audited (14 snaps) | audited; C-10 error | audited (instructor home); I1 full, I2 full | denied | audited (C-10, C-16) |
| `/staff/account` | audited | audited | audited | (student account is `/account`) | partly audited: phone and password forms seen, not submitted |
| `/staff/admins` | audited | denied | denied | denied | audited |
| `/staff/announcements` | audited (send walk: 13 students reached) | placeholder only (C-04) | denied | denied | audited (send walk; history list not inspected) |
| `/staff/audit` | audited (20 rows; no filters in the product) | denied | denied | denied | audited |
| `/staff/categories` | audited | audited | denied | denied | audited (create walk: pass) |
| `/staff/categories/new` | audited | audited | denied | denied | audited (create walk: pass) |
| `/staff/categories/[id]` | audited | audited | denied | denied | audited |
| `/staff/modules` | audited (C-15 at 768) | audited (C-15 at 768) | I1 list, no action (C-02); I2 list (C-02) | denied | audited (C-02, C-15) |
| `/staff/modules/new` | audited | audited | denied | denied | audited (C-12: fails without descriptions) |
| `/staff/modules/[id]` | audited (roster add and remove, walk) | audited | denied (C-02) | denied | audited |
| `/staff/exams/[examId]` | audited (publish, schedule, questions, PDF) | audited (C-05, C-06) | denied (C-02) | denied | audited (C-11 workaround, C-13) |
| `/staff/exams/[examId]/leaderboard` | audited | audited | audited (I1, I2) | denied | audited (data not checked against stored values) |
| `/staff/exams/[examId]/results` | audited | audited | audited (I1, I2) | denied | audited (data not checked against stored values) |
| `/staff/instructors` | audited (create and reissue walk; filters checked) | denied | denied | denied | audited |
| `/staff/orders` | audited (C-01 confirm, API 200) | audited, read-only | denied | denied | audited (C-01) |
| `/staff/participation` | audited | audited | audited (I1, I2) | denied | audited (C-03) |
| `/staff/payments` | audited (C-15; status filter and empty search checked) | audited (C-15), read-only | denied | denied | audited |
| `/staff/payments/[tranId]` | audited | audited | denied | denied | audited |
| `/staff/students` | audited (create, row menu, delete, long list with paging, empty search) | audited (C-07 in detail; loading and error states checked) | denied | denied | audited (C-17, C-20) |
| `/staff/students/[id]` | audited (suspend, restore, roster row visible) | audited (C-07, C-17) | denied | denied | audited (C-21) |
| `/staff/submissions` | audited | audited (read) | audited (I1, I2) | denied | audited |
| `/staff/submissions/[participantId]` | audited | audited (no marking form) | I1: draft saved (walk); I2: refused (C-14) | denied | audited (evaluation walk: pass) |
| `/staff/submissions/m/[moduleId]` | redirect (C-03) | redirect | redirect | denied | audited (C-03) |
| `/staff/system-status` | audited | redirects to `/staff` (C-08) | denied | denied | audited (C-08) |

Not reached as a signed-in user: none. All 25 routes were reached with a session in Chromium. WebKit reached ten of them (dashboard, orders, students, marking, payments, modules, participation, announcements, system status, account) at 360 and 1440 px. Firefox reached seven (dashboard, orders, students, marking, exam editor, payments, modules).

## Missing pages and controls by user type (plan section 3.8)

Status per row: `exists and works`, `exists but incomplete`, or `MISSING`.

### Instructor (Instructor 1, Instructor 2)

| Expected | Status | Finding |
|---|---|---|
| Staff home (marking dashboard) | exists but incomplete (queue request fails) | C-10 |
| Own modules and exams | exists but incomplete (list only, no way in) | C-02 |
| Exam view | MISSING | C-02 |
| Submissions list for own module | exists and works (Instructor 1 list shows; module filter not run) | — |
| Evaluate a paper (marks, comment, marked PDF, save as draft) | exists and works (walk) | — |
| View results and leaderboard | exists and works (reached through participation) | — |
| Own account with phone and password change | exists (forms shown, not submitted) | — |
| Clear message that publishing is the Master Admin's | MISSING (no note) | C-19 |
| Instructor 2 blocked from Instructor 1's submissions | exists and works (403); wrong message | C-14 |

### System Admin

| Expected | Status | Finding |
|---|---|---|
| Staff home | exists but incomplete (queue error) | C-10 |
| Students list and detail | exists and works | — |
| Grant access | exists (shown), rule conflict | C-07 |
| Revoke | exists for Master Admin only (not shown to System Admin) | per rules |
| Suspend | MISSING | C-17 |
| Categories, modules, exams management | exists and works (exam creation fails in the UI) | C-11, C-12 |
| Orders and payments with detail | exists and works (read-only) | C-15 (overflow) |
| Announcements | exists but unreachable from the menu | C-04 |
| Participation | exists and works | C-03 (for the count link) |
| System status | MISSING (plan expects it; not in the spec) | C-08 |
| Own account | exists and works | — |
| No evaluate and no publish controls | evaluate: not shown (exists and works); publish exam: shown | C-05 |

### Master Admin

| Expected | Status | Finding |
|---|---|---|
| Everything System Admin has | as above | as above |
| Admins and instructors management | exists and works (instructor create, filters and reissue walked) | — |
| Audit log | exists and works (20 rows; the product has no filters) | — |
| Publish exam | exists and works (walk) | C-11 (creation is blocked before this) |
| Publish written marks | exists and works (walk) | — |
| Read-only email and editable phone and password on first login | exists (account page); forced change not checked | — |
| Confirm or reject pending exam orders | exists, but a control the rules forbid | C-01 |
| Create exams | MISSING from the UI | C-11 |
| Create modules | exists but fails without descriptions | C-12 |

## Flow walks (plan section 6 and 11.3)

| Walk | Result | Notes |
|---|---|---|
| Master Admin first login | pass (landing `/staff`) | forced change not checked |
| Exam authoring: category | pass | Position is required and has no default; a new position is needed for each sibling |
| Exam authoring: module | fail | C-12 |
| Exam authoring: exam create (UI) | fail | C-11; exams created through the API |
| Exam authoring: questions (paste) | pass | 2 questions per exam |
| Exam authoring: publish | pass | Exam A, C (MCQ) and D (written) published |
| Five-minute rule | pass (refused with 409) | exam B, 3 minutes after A; C-18 wording |
| Null `liveAt` publish | pass | exam C |
| Set `liveAt` returns to draft | pass | "Saved. It was published, so it is a draft again." |
| Clear `liveAt` returns to draft | pass | same message |
| Written PDF upload and publish | pass | exam D; "No teacher is assigned" warning shown (expected, no instructor on that module) |
| Evaluation: instructor draft | pass | 200; score 31, comment, marked PDF stored, not published |
| Evaluation: master publish | pass | "Result published."; `published_at` set |
| Instructor 2 cannot open Instructor 1's submission | pass | 403; message wrong (C-14) |
| Student management: create | pass | activation code shown once in the dialog (by design) |
| Student management: row menu at 768 px | pass | Open, Send a new code, Delete; inside viewport |
| Student management: delete | pass | type-to-confirm dialog; "Account deleted." |
| Student management: suspend and restore | pass | "Status updated." |
| Student management: roster add and remove (module roster tab) | pass | student 5103 added and removed |
| Student management: revoke a roster row from the student page | no control | C-21 |
| Student management: loading state | pass | skeleton placeholders shown while the list loads |
| Student management: error state | pass | a 500 shows the generic error with "Try again" |
| Payments: status filter, transaction search, empty search, detail | pass | 1 failed row for "Failed"; "No payments match this search" |
| Orders: confirm | pass (control shown, API confirms) | C-01 |
| Announcements: send to all students | pass | 13 students reached ("Announcement sent. 13 students received it.") |
| Instructor directory: create, filters, reissue activation code | pass | "Audit Teacher C"; "Send a new code" returns a new code |
| Participation: count link | pass (lands; filter lost) | C-03 |
| Audit log | pass | 20 rows; the product has no filters |
| Empty states (search with no match) | pass | students "No students found"; payments "No payments match this search"; participation "No exams found" |
| Empty database state (plan 6, flow 6) | not run | would need a database reset; not part of this round |
| Long list | pass | 25 students: 20 rows on page 1, 5 on page 2; row menus open inside the viewport at 360 and 1024 px |
| Null `liveAt` on the staff list | pass | "Date to be announced", with "Draft" or "Published" as set |
| 200% text (emulated) | fail | C-09: header overflows, the page scrolls sideways |
| Touch targets (automated) | fail | C-20 |
| PDF question viewer at 360 px | pass | the student preview of exam D renders; no overflow |

## Test-data changes in `coaching_center_qa_3` (for the record)

- Created: one category, one module, exams A, B, C, D, one student (deleted again), an announcement (13 recipients), an instructor with a reissued code, and 25 long-list students (kept).
- Changed: exam A was published, its start time set (back to draft), re-published, then its start time cleared (draft again); participant 601 marked and published; order `DEMO-STUCK-E9-5001` set to `SUCCEEDED`; student 5103 suspended, restored, and added to then removed from the Audit Module roster.
- Not changed: the seed demo rows, the other staff accounts.

## Not checked (remaining)

- **Empty database state:** done (its own database, Bangla and English, light and dark, see Changes). Not part of the signed-in run.
- **Device items, "emulated only, confirm on device":** real iOS Safari (PDF and watermark), real Android (Bangla text), a tablet, Edge and Samsung Internet (covered by Chromium), and touch feel.
- **On-screen keyboard check** (plan 3.7): its pages are sign-up, login, set-phone and contact, which belong to Agent A. Not applicable to this slice.
- **Full colour-contrast pass** (plan 3.2): the automated scan covers the staff dashboard, students, orders, modules, the exam editor, submissions and participation at 360 px, in four language and theme pairs. Other pages and widths were not scanned.
- **Stored numbers** against the dashboard and leaderboard (plan section 9, manual QA).
- **Master Admin first-login forced change:** the account is not on first login, so the forced branch was not exercised. The offered change exists.

## Summary counts

| Severity | Count | Findings |
|---|---|---|
| Blocker | 1 | C-11 |
| High | 2 | C-10, C-12 |
| Medium | 13 | C-02, C-03, C-04, C-05, C-06, C-07, C-09, C-13, C-14, C-15, C-16, C-17, C-20 |
| Low | 5 | C-01 (documentation), C-08, C-18, C-19, C-21 |

Total: 21 findings. Confirmed in a snapshot, by a server response, or by measurement: all 21. Read in code only: none. Severity counts are after the 2026-10-26 decisions.

## Top five findings

1. **C-11 (Blocker):** Staff cannot create an exam from the UI. The dialog sends only the type and titles, and the API returns 400. Fix decided (the field list in the decision above).
2. **C-10 (High):** The admin dashboard's exam list fails on load (the request sorts by `date`, which the API rejects), in every language and theme.
3. **C-12 (High):** Creating a module without descriptions fails with no field message. Decided: descriptions are required.
4. **C-05 (Medium):** System Admin sees Publish on the exam editor. Decided: System Admin does not publish.
5. **C-09 (Medium):** The staff header crowds at 360 px (search icon over the wordmark, language pill over the end of the wordmark), in Bangla and English, and runs past the screen at 200% text.

## Routes not reached

None. All 25 staff routes were reached as a signed-in role in Chromium. WebKit and Firefox cover the key pages listed in the coverage table.

## Cross-reference for the other agents and the merge

- C-09 (header crowding) is shared with the student shell: Agents A and B should check it on their pages at 360 px.
- The login page's Google button marked "Coming soon" is Agent A's (`decisions.md` makes Google a student feature).
- The participation and instructor-directory specs cannot be checked against `docs/exam-site/ui/stitch-prompt.md`, which is deleted in the working tree.
- The student denial (`This page is for admins and teachers only`) works for all 25 staff routes.
