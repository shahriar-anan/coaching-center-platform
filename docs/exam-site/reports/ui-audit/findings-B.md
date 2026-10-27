# UI audit findings: Agent B (sitting, results, leaderboard)

Status: **partial.** The post-reset round covers the live MCQ window, the later attempt, the written upload and replace, the written draft and publish, the MCQ result and leaderboard, the blank paper, the landscape and slow-network checks, the no-access student, Chromium at all widths, Firefox and WebKit on key pages. The MCQ window for e0005 (12:09 to 12:14 UTC) is now walked in full: the start check with the page left open (B-17), the sitting, the withheld review, the staff release, the revealed review and the leaderboard, in Edge, WebKit and Firefox. Still open, with reasons, in the checklist below: the written deadline with no file (not reached before 13:58 UTC), and the staff comparison routes (owned by C).

## Header

| Field | Value |
|---|---|
| Agent | B (sitting, results, leaderboard) |
| Database | `coaching_center_qa_2`. Start log shows `DB_URL=jdbc:postgresql://localhost:5432/coaching_center_qa_2`, port 8082, profile `dev`. I started the API myself from the scratchpad, so `api/.env` (which points at dev) was not read. |
| API | Built from `F:\cc-wt\audit-b\api` at commit `c2cd843` (`mvnw package -DskipTests`), run as `java -jar`. Restarted twice at 11:36 UTC for the WebKit origin change (see "Incident"). |
| Web | Production build (`npm run build`, `npm run start`) from `F:\cc-wt\audit-b\web`, commit `c2cd843`, on `localhost:3002`. WebKit used a second build from `F:\cc-wt\audit-b-alt` (same commit) behind a local HTTPS front on `127.0.0.1:3443`. |
| Reset | Done at 10:50 UTC (16:50 Dhaka), approved by Shahriar, per 2.2a. Backup: `scratchpad\backups\coaching_center_qa_2-2026-10-26.dump` (190,039 bytes, `pg_dump -Fc`), taken before the drop. Recreated empty. |
| Seed result | First start after the reset stopped at seed time with `The Master Admin must exist before the demo seed runs`. No users existed. Cause not found. A second start with the same environment succeeded: 0 ERROR lines, 8 seeded accounts log in (students 5101 to 5105, instructor, system admin, Master Admin), catalog shows BJS Exam and Bar Council Exam. |
| Dates and windows | Dhaka time (UTC+6). Windows used: MCQ e0012 16:58 to 17:18 (20 min); written e0006 16:58 to 19:58; MCQ e0011 17:24 to 17:29 (5 min, blank test); MCQ e0005 18:09 to 18:14 (5 min, browser review). |
| Roles | `student` = 5101 (module 3 purchase grant, modules 1 and 2 by roster grant). `student-second` = 5102 (module 3 roster grant). `student-missed` = 5103 (missed e0002, then later attempt). `student-noaccess` = 5104 (no module, no phone). `guest` = not signed in. Instructor and Master Admin acted through the API only (marking and publish); their staff screens are Agent C's. |
| Browsers | Edge (Chromium): full matrix. Firefox: key pages at 360 and 1440. WebKit: key pages at 360 and 1440, signed in (see "WebKit"). |
| Fallback from section 2.3 | Not used |
| Pre-reset evidence | `snapshots/B/pre-reset/` (293 files). Kept as evidence only. Nothing in this file cites them as walks done in this round. |

## Incident: the API restart re-ran the demo seed

To run WebKit I needed the browser origin `https://127.0.0.1:3443` in the API's `WEB_ORIGIN`, which meant two restarts at 11:36 UTC. Each start runs the demo seed, which reset the seeded exam windows and e0013's published mark for 5101 (back to 38). Sittings and submissions survived. Consequences:

- Captures taken after 11:36 UTC that depend on the e0012, e0011 or e0006 windows were deleted (40 PNGs from the WebKit, Firefox and dark runs). They are not cited.
- The e0012 and e0011 windows could not be re-published after they had closed (the API refuses publish after the window), so their review and leaderboard states after the restart could not be restored. Captures before 11:36 UTC are valid and are cited.
- For the browser review in WebKit and Firefox I used a new window (e0005, 18:09 to 18:14).

## Changes made to B's own database

Needed for the walks; the next round starts with a reset. `liveAt` and `durationMinutes` on e0012, e0006, e0011 and e0005 (set, final-saved, published). Roster grants for 5101 on modules 1 and 2 and for 5102 on module 3. e0012 answers released (`answers-released`) after the withheld review was captured. Written mark on 5101's e0013 changed by a draft and a publish (captured in both states, then reverted by the seed at 11:36).

## Walk results (section 11.3, B)

| Walk | Result |
|---|---|
| MCQ before the window: Take exam closed | Pass. Disabled "Start exam" with the start time (5101, e0012, 16:58). Captured `exam-mcq-before` at all widths and both languages. |
| MCQ inside the window: opens, answers, change, save state | Pass. 40 options (10 questions). Save: "সেভ হচ্ছে…" then "সেভ হয়েছে". Flag works. |
| MCQ submit: dialog counts, flagged list, score at once | Pass. Dialog: 2 answered, 8 unanswered, flagged 3. Score card: 5 / 50, not passed, shown at once. |
| MCQ e0005 (12:09 to 12:14 UTC): window check, sitting, submit, deadline, review, release, leaderboard | Pass with two notes. Start button stayed disabled with the page left open (B-17); after leaving and returning it started. Submit with 2 answers: dialog counts correct, score 0 / 50 shown at once. At the deadline the review showed the chosen answers only; after staff release the correct options and explanations appeared (10 correct options, 10 explanations). Leaderboard at 12:19: 5101 rank 1, score 0, marked "mine". |
| Chosen, correct and explanation hidden until the sitting ends | Pass with a gate. After the deadline and before staff release the correct flags and explanations are omitted. After release they show. See B-18. |
| Deadline auto-post | Pass. 5102 touched nothing: posted at 17:18 with 0 / 50 and the result appeared. 5101's late tap during the throttle check changed an answer before the deadline, so the posted score was 10 / 50 (not a fault of the deadline). |
| Blank paper scores zero and shows it | Pass. 5101, e0011 (5-minute window, blank): 0 / 50, review shown. |
| Leaderboard: opens after the window, live rows only, ties share a rank | Pass on the live board. e0012 at 17:23 (5 minutes after the 17:18 end): 5101 rank 1 with 10 (marked "mine"), 5102 rank 2 with 0. e0011: 5101 rank 1 with 0 (marked "mine"). Tie: not re-tested after the reset (pre-reset, 5 and 5 were both rank 1). |
| Later attempt (MCQ, missed) | Pass. 5103 on e0002 (window ended 17:18): "Practice" offered. Practice note shown: "এটি অনুশীলন। এর নম্বর মেধাতালিকায় আসবে না।". Personal timer, 30 minutes. |
| Written sitting: PDF viewer, watermark, wrong type, over 80 MB, valid upload, replace | Pass for upload and replace. Text file and PNG-as-PDF refused ("ফাইলটি পিডিএফ নয়…"). 81 MB refused ("…৮০.০ MB-এর চেয়ে বড়…"). Upload button disabled until a valid file. Valid PDF uploaded ("খাতা জমা দেওয়া হয়েছে") and replaced by a second file. Watermark legible in light theme, barely visible in dark (B-02). |
| Written sitting after a reload | Fail. The "stored" line is gone after a reload (B-01). |
| Written draft: hidden; publish: visible | Pass, with captures. Instructor saved a draft (34, comment, marked PDF). The student's result showed no score, comment or marked PDF, and no leaderboard row. Master Admin published. The result showed score 34, comment and marked PDF, and the leaderboard row was back. Captures `exam-written-result-draft` and `exam-written-result-published` (run at 11:27 UTC, before the restart). |
| Written: a later save returns to draft | Pass at the API: the draft save returned the e0013 result to draft, and publish restored it. |
| Written: no file at deadline (failed to submit) | Not reached. The written window closes at 13:58 UTC (19:58 Dhaka). |
| `/results` and history: every state | Partly. Results and Marking tabs only. A missed exam is not listed anywhere on `/results` (B-08, confirmed for 5103 in browser). |
| Account with no access opens `/sit` by URL | Pass. 5104 is sent to `/exam/…` (not a blank page). That page shows a red payment-unavailable notice (checkout is off in this build) and an "add phone" button, because 5104 has no phone. The access reason is not shown (B-11). |
| Landscape 800 x 360 on the sitting page | Pass. Timer bar bottom 152 px; submit bar 304 to 348 px; no horizontal overflow at 800 px. |
| Slow network (400 kbps, 400 ms) on the sitting page | Pass. Paper visible in about 3 seconds. Save states: "সেভ হচ্ছে…" then "সেভ হয়েছে". Run on 5101. |
| Dark theme | Captured at 360 and 1440, both languages: results, notices, routine, my-modules, written sitting, written result, guest routine, and the MCQ result (pre-reset). Post-reset MCQ review and leaderboard in dark (e0005, Edge, 360 and 1440) are done; the MCQ sitting in dark after the reset is not captured. |

## Browser coverage

| Browser | Pages | Result |
|---|---|---|
| Chromium (Edge) | All routes in the coverage table, all widths, bn and en, light; dark for the pages listed above | Run |
| Firefox | `/exam/…/sit` (written), `/results`, `/exam/…/result` (written, published), each at 360 and 1440, bn, light | Run, signed in |
| WebKit | Same key pages at 360 and 1440, bn, light, signed in through the local HTTPS front | Run, signed in (see "WebKit") |

MCQ review (withheld and released) and leaderboard for e0005 ran in all three browsers: Edge at all widths in both languages, WebKit and Firefox at 360 and 1440 in Bangla. Dark theme for the same pages: Edge at 360 and 1440.

### WebKit

Signed-in WebKit did not keep the session on `http://` localhost. WebKit does not send the `Secure` refresh cookie over plain HTTP, and the API sets that cookie as `Secure`. Chromium and Firefox treat localhost as a secure context, which is why they work. Workaround used: a self-signed certificate for `127.0.0.1`, a small HTTPS proxy in front of the API (port 8082) and the web (port 3004), a second web build (`F:\cc-wt\audit-b-alt`, commit `c2cd843`) with the API base set to `https://127.0.0.1:3443`, and the API's `WEB_ORIGIN` extended to that origin. With that, the WebKit refresh returned 200 and the session held. This is a documented alternative on a local machine. It is not a real Safari, so the WebKit screenshots are "emulated only, confirm on device".

## Findings

Severity counts: Blocker 0, High 2, Medium 12, Low 5 (19 findings).

### B-01: The stored written script is not shown after a reload
- Route / role / width / language / theme / browser: `/exam/[examId]/sit` (written) / student / 360 and 1440 / Bangla / light and dark / Edge and WebKit
- What the user sees: After a successful upload the page shows "খাতা জমা দেওয়া হয়েছে". After a reload that line is gone and the upload box is empty. Nothing says a script is already stored.
- Expected: The stored state survives a reload until the deadline. The copy for it exists but is never used: `storedEarlier` (`web/src/i18n/messages/bn/sitting.ts:81`).
- Evidence: `web/src/components/student/sitting/written-sitting.tsx:41`, `:66`, `:128`. The student read has no answer field (`api/src/main/java/com/coachingcenter/api/exams/LiveExamSittingService.java:529`). The API keeps the state (`LiveExamStudentStatus.java:57`). Browser run after the reset: `walk-written`, step `after-reload`: no stored message.
- Severity: High (a live-sitting control the student cannot check)
- Type: state
- Confidence: confirmed in browser (post-reset)

### B-16: Opening the exam page by reload or deep link shows the wrong action for the student
- Route / role / width / language / theme / browser: `/exam/[examId]` / student (has access) / all widths / Bangla / Edge
- What the user sees: From inside the app: "Continue exam" for a written exam in its window. After a reload of the same URL: "Buy this exam · ৳500.00" and no "Continue exam". Before the reset the same reload showed no action at all. A student who already has the exam is offered a purchase.
- Expected: Plan 3.6: refresh and deep link behave the same as in-app navigation. Decisions: a student with access sees Take exam or Continue inside the window, not a purchase offer.
- Evidence: The public exam read runs before the session is restored, so the access block is empty (the guest state) and the student's own read (`/api/v1/live-exams/{id}`) never runs: `web/src/components/student/catalog/exam-page.tsx:72-76`, `web/src/lib/api/exam/catalog.ts:220-226`, `:308-316`. The API gives the right access for a token (`/api/v1/public/live-exams/{id}` with a token: `access.state = MODULE`). Browser run `probe-nav-r2` after the reset; `probe-nav` before it.
- Severity: High (a purchase offer for an owned exam, and no way to start a sitting from a link)
- Type: navigation
- Confidence: confirmed in browser (post-reset)

### B-17: The start button does not turn on when the window opens, unless the page is left and reopened
- Route / role / width / language / theme / browser: `/exam/[examId]` / student / 360 / Bangla / Edge
- What the user sees: The page keeps "Start exam" disabled after the window opens. Pre-reset, the walk found it enabled only after it left and returned to the page. Focus refresh was not tested.
- Expected: Take exam opens inside the window without a manual reload (the server clock decides).
- Evidence: The exam page reads the status on mount only (`web/src/lib/api/exam/catalog.ts:308-316`, `staleTime: 0`, focus refresh only). Clean retest after the reset (e0005 walk): the page was opened before 12:09 UTC and left open through the window's start (API status OPEN from 12:09:41). At 12:10:41 "Start exam" was still disabled, with no navigation. After leaving the page and returning, the button was enabled and the sitting started. Log `e0005-a` and `e0005-b`.
- Severity: Medium (High if a student who only waits on the page cannot recover by switching tabs or focusing the window; focus refresh not tested)
- Type: state
- Confidence: confirmed in browser (post-reset clean retest)

### B-18: Correct options and explanations are held back until staff release them
- Route / role / width / language / theme / browser: `/exam/[examId]/result` (MCQ, after the deadline) / student / all widths / Bangla / Edge
- What the user sees: After the deadline the review shows the chosen answers only. The correct option and the explanation are missing until a Master Admin or System Admin turns on "answers released". After the release they appear.
- Expected: Decisions, "Exam rules that stay": "The chosen option, the correct option, and the explanation wait until the sitting ends." The spec ends them with the sitting, not with a staff action.
- Evidence: `api/src/main/java/com/coachingcenter/api/exams/LiveExamSittingService.java:393` (`reveal` depends on `answersReleased`); `LiveExamAnswerReleaseService.java`; the staff switch `web/src/components/staff/access/answer-release-switch.tsx`. Snapshots: `exam-mcq-result-withheld` and `exam-mcq-result-released` (run before the restart).
- Severity: Medium (a requirement mismatch for Shahriar to decide; the control exists)
- Type: missing-feature
- Confidence: confirmed in browser and API (pre-reset)

### B-19: The file-size error reads "৮০.০ MB" on the Bangla page
- Route / role / width / language / theme / browser: `/exam/[examId]/sit` (written) / student / all widths / Bangla / Edge
- What the user sees: "ফাইলটি ৮০.০ MB-এর চেয়ে বড়। ছোট একটি ফাইল দিন।" The unit is English and the number has a decimal. The page's own copy ("ফাইলটি ৮০ এমবির বেশি") is not used on this path.
- Expected: Bangla unit and number formatting (plan 3.4).
- Evidence: `web/src/components/ui/file-drop.tsx:20` (`" MB"` after a decimal number). Post-reset probe `probe-upload-errors`.
- Severity: Medium
- Type: language
- Confidence: confirmed in browser (post-reset)

### B-15: The header overflows at 360 px in Bangla, and the wordmark runs into the language switch (shared shell)
- Route / role / width / language / theme / browser: every page under the shell / 360 / Bangla / light and dark / Edge, Firefox and WebKit
- What the user sees: The wordmark is partly covered by the language switch and the bell badge. "Log in" wraps. In Bangla the header's right-hand group (bell, language, theme, name button) is 3 px wider than the screen, so every Bangla page scrolls sideways by 3 px. English does not overflow. The same overflow appears in Firefox and WebKit on the results and sitting pages.
- Expected: Plan 3.1: no horizontal page scroll at 360 px; long text wraps and never covers a control.
- Evidence: `probe-overflow` (bn scrollWidth 363). Snapshots: `results__360__bn__light__student__webkit.png`, `results__360__bn__light__student__firefox.png`, `exam-written-sit-live__360__bn__light__student.png`. The header is shared, so the same defect is on A's and C's pages.
- Severity: Medium
- Type: layout
- Confidence: confirmed in browser (post-reset)

### B-02: The watermark on the question paper disappears in dark theme
- Route / role / width / language / theme / browser: `/exam/[examId]/sit` (written) / student / 360 and 1440 / Bangla / dark / Edge
- What the user sees: In light theme the diagonal name and code are clearly legible. In dark theme the page canvas is still white and the mark is barely visible.
- Expected: Plan 5.5: legible but not blocking. Decisions: the watermark is drawn over the PDF viewer, with the student's name and id.
- Evidence: Light: `exam-written-sit-live__360__bn__light__student.png` (legible). Dark: `exam-written-sit-live__360__bn__dark__student.png` (barely visible). `web/src/components/pdf/pdf-watermark.tsx:23` (`text-text` at `opacity-[0.09]`); `web/src/components/pdf/pdf-viewer-impl.tsx:364` (`bg-white`).
- Severity: Medium (it is the identifying mark in dark theme)
- Type: contrast
- Confidence: confirmed in snapshot (dark)

### B-03: A student's marked script is viewed without the watermark
- Route / role / width / language / theme / browser: `/exam/[examId]/result` (written, published) / student / all widths / read in code only
- What the user sees: After publish, "view marked script" opens the evaluated PDF with no watermark prop.
- Expected: Decisions: the watermark is drawn over the PDF viewer. Feature-scope limits the wording to the question paper, so Shahriar to decide.
- Evidence: `web/src/components/student/result/result-page.tsx:215-219`
- Severity: Medium
- Type: visibility
- Confidence: read in code only (not opened in a browser)

### B-04: The "Passed" count on the results hub drops to 0 on the Marking tab
- Route / role / width / language / theme / browser: `/results` / student / all widths / read in code only
- What the user sees: The summary's "Passed" value comes from marks queries that exist only on the Results tab, so it reads 0 on the Marking tab.
- Expected: The summary is the same on both tabs.
- Evidence: `web/src/components/student/result/results-hub.tsx:60-70`, `:70`
- Severity: Medium
- Type: state
- Confidence: read in code only (no student with both a passed result and a marking row in this seed)

### B-05: A Bangla digit is hard-coded for a failed-to-submit score on the English page
- Route / role / width / language / theme / browser: `/exam/[examId]/result` (written, failed to submit) / student / English / read in code only
- What the user sees: "০" in the score cell whatever the site language.
- Expected: Numerals follow the site language (plan 3.4).
- Evidence: `web/src/components/student/result/result-page.tsx:132`
- Severity: Medium
- Type: language
- Confidence: read in code only (no failed-to-submit student in the seed)

### B-06: Review jump links are 40 px high
- Route / role / width / language / theme / browser: `/exam/[examId]/result` (MCQ, after the deadline) / student / phone widths / read in code only
- What the user sees: The question-number links are `h-10` (40 px). The live paper's grid uses 44 px.
- Expected: Tap targets at least 44 px (plan 3.7).
- Evidence: `web/src/components/student/result/result-page.tsx:254`
- Severity: Medium
- Type: device
- Confidence: read in code only

### B-07: The routine leaves out exams with no start time, but the decisions say the routine is the module list
- Route / role / width / language / theme / browser: `/routine` / student / all widths / read in code only
- What the user sees: An exam with `liveAt` null does not appear on `/routine`.
- Expected: decisions.md, "Product": "The routine is that same list ... A null `liveAt` means the date and time are not announced yet." Feature-scope line 73 says the opposite. decisions.md overrides feature-scope, so this reading is used. Shahriar to confirm.
- Evidence: `api/src/main/java/com/coachingcenter/api/exams/LiveExamService.java:414`; `web/src/components/student/catalog/catalog-helpers.ts:141-143`
- Severity: Medium
- Type: missing-feature
- Confidence: read in code only (a conflict between two sources)

### B-08: `/results` does not list every state a student can be in
- Route / role / width / language / theme / browser: `/results` / student-missed (5103) / all widths / Bangla / Edge
- What the user sees: The Results tab shows finished exams and the Marking tab shows awaiting ones. The exam 5103 missed (e0002) is not listed. The page shows the empty state "এখনো কোনো ফলাফল নেই … পরীক্ষা দেওয়ার পর ফলাফল এখানে দেখা যাবে" with a link to the routine.
- Expected: Plan 5.7: `/results` and history list every state. The missed state is visible only on the module list and the exam page.
- Evidence: Snapshot `results__360__bn__light__student-missed.png`. `web/src/components/student/result/results-hub.tsx:19-20`, `:51`; `api/src/main/java/com/coachingcenter/api/exams/LiveExamStudentStatus.java:32-69`.
- Severity: Medium
- Type: missing-feature
- Confidence: confirmed in snapshot

### B-09: Result and leaderboard pages have no breadcrumb links back
- Route / role / width / language / theme / browser: `/exam/[examId]/result`, `/exam/[examId]/leaderboard` / student / all widths / read in code only
- What the user sees: The breadcrumb is a single unlinked item.
- Expected: Plan 3.6: every page has a way back.
- Evidence: `web/src/components/student/result/result-page.tsx:77`, `web/src/components/student/result/leaderboard-page.tsx:39`
- Severity: Low
- Type: navigation
- Confidence: read in code only

### B-10: A resume button for a sitting that has just ended bounces to the exam page
- Route / role / width / language / theme / browser: `/exam/[examId]` then `/exam/[examId]/sit` / student / all widths / read in code only
- What the user sees: Between the deadline and the posting job, "resume" shows for `IN_PROGRESS`; opening the paper sends the student back to the exam page.
- Evidence: `web/src/components/student/catalog/exam-page.tsx:168`; `web/src/components/student/sitting/sitting-entry.tsx:135-141`
- Severity: Low
- Type: navigation
- Confidence: read in code only

### B-11: A student with no access is sent to the exam page, which shows the phone and payment messages instead of the access state
- Route / role / width / language / theme / browser: `/exam/[examId]/sit` / student-noaccess (5104) / 360 / Bangla / Edge
- What the user sees: Redirect to `/exam/[examId]`. The page shows a red "payment not available right now" notice (checkout is off in this build) and the "add phone" button (5104 has no phone). It does not say that the student has no access to this exam.
- Expected: Plan 5.8: a clear blocked state that says why.
- Evidence: `web/src/components/student/sitting/sitting-entry.tsx:94-110`. Snapshot `exam-mcq-sit-noaccess__360__bn__light__student-noaccess.png`.
- Severity: Low
- Type: state
- Confidence: confirmed in snapshot (for a student without a phone; a student with a phone was not tested)

### B-12: The submit confirmation dialog does not move focus into it
- Route / role / width / language / theme / browser: `/exam/[examId]/sit` (MCQ) / student / all widths / read in code only
- What the user sees: Focus stays on the Submit button behind the dialog. There is no focus trap.
- Expected: Plan 3.2: keyboard-reachable dialogs.
- Evidence: `web/src/components/student/sitting/mcq-paper.tsx:490-558`
- Severity: Low
- Type: visibility
- Confidence: read in code only

### B-13: On the notices page a row with no link cannot be marked read one at a time
- Route / role / width / language / theme / browser: `/notices` / student / all widths / read in code only
- What the user sees: A notice with no exam link is a `div`, so opening it does not mark it read. The only way to clear it is "mark all read".
- Expected: Decisions and feature-scope: "mark one read or mark all read".
- Evidence: `web/src/app/(site)/notices/page.tsx:118-119`. The API sends reminder, open, published, score and result kinds to 5101 (19 notices).
- Severity: Low
- Type: missing-feature
- Confidence: read in code only

### B-14: A guest opening `/routine` sees a connection error, not a sign-in prompt
- Route / role / width / language / theme / browser: `/routine` / guest / 360 to 1440 / Bangla and English / light and dark / Edge
- What the user sees: The page shows "check your internet connection, try again". The routine call returns 401 to a guest and the page reports it as a network failure.
- Expected: A guest goes to login, like `/results`, `/notices` and the sitting pages (plan 3.6).
- Evidence: `routine__360__bn__light__guest.png` (pre-reset); post-reset `routine__*__light__guest.png` and `routine__*__dark__guest.png`. `web/src/app/(site)/routine/page.tsx` has no `RequireStudent`; `web/src/components/student/catalog/routine-page.tsx:76-83`.
- Severity: Medium
- Type: navigation
- Confidence: confirmed in snapshot

## Coverage

Routes in B's slice. "Audited" means snapshots at every width in both languages plus a flow result. Items still missing are named.

| Route | Status |
|---|---|
| `/exam/[examId]` (before the window, the sitting states, MCQ and written) | Audited for the states in the walk table (student 5101 before, during and after). Shared with A. B-16 and B-17 apply. |
| `/exam/[examId]/sit` (MCQ live, submitted, open blank) | Audited: all widths and both languages for 5101 (submitted) and 5102 (open, blank). Landscape 800 x 360 and slow network. Dark: done for the sitting page's review and leaderboard via e0005 (Edge, 360 and 1440). |
| `/exam/[examId]/sit` (written) | Partly audited: upload, wrong types, size, replace, reload; all widths in light for 5101; dark at 360 and 1440. Failed-to-submit at the deadline (13:58 UTC) not reached. |
| `/exam/[examId]/sit?start=retake` (later attempt, missed MCQ) | Audited for the flow (5103): practice note, personal timer, submit. Screens at 360 only. |
| `/exam/[examId]/result` (MCQ, withheld and released) | Partly audited: all widths for the withheld and released states on 5101 (window e0012, run before the restart). Blank paper result (e0011) at all widths. Released state in WebKit and Firefox: done via e0005 (360 and 1440, Bangla). |
| `/exam/[examId]/result` (written, published) | Audited: all widths for the published state (5101, e0013). Draft state captured at 11:27 UTC (before the restart). |
| `/exam/[examId]/leaderboard` (MCQ, e0012 and e0011) | Audited at all widths for 5101 (e0012 and e0011). 5102's board at all widths. Ties not re-tested after the reset. |
| `/exam/[examId]/leaderboard` (written, e0013) | Audited at all widths (5101). |
| `/results` | Audited: all widths, both languages, light, for 5101; 5103 (missed) at all widths. Dark at 360 and 1440 (5101). |
| `/routine` | Audited: student (5101) at all widths; guest at all widths (light) and dark. |
| `/notices` | Audited: student (5101) at all widths, dark at 360 and 1440. Reminder rows' content not checked against the API beyond the kinds list. |
| `/staff/exams/[examId]/leaderboard`, `/staff/exams/[examId]/results` | Not walked. Owned by Agent C. |

Browsers: Edge at all widths for the routes above. Firefox at 360 and 1440 for `/exam/…/sit` (written), `/results`, `/exam/…/result` (written, published). WebKit at 360 and 1440 for the same pages, through the HTTPS front. MCQ review and leaderboard also run in Firefox and WebKit at 360 and 1440 (Bangla) via e0005.

## Section 3.8 expected pages (Student, B's pages)

| Expected page or control | Status | Note |
|---|---|---|
| Routine | exists but incomplete | Unannounced exams missing (B-07); guest error (B-14) |
| Notices, including reminders and result notices | exists but incomplete | Mark-one-read gap (B-13). Reminder, open, published, score and result kinds are sent by the API (19 for 5101). |
| Results and exam history | exists but incomplete | Missed and open states missing (B-08) |
| Per-exam result | exists and works (MCQ, after release) / exists but incomplete (written) | Watermark on the marked script (B-03); numerals (B-05); correct options held until release (B-18) |
| Per-exam leaderboard | exists and works | Live rows, rank, "mine" marking, zero-score rows. Ties not re-tested after the reset. |
| Take exam and the later MCQ attempt | exists but incomplete | Works from in-app navigation. A reload shows the wrong action (B-16). The start control does not switch on by itself (B-17). The later attempt works (practice note, own timer, off the board). |
| Written upload and replace | exists but incomplete | Stored state lost on reload (B-01). Size and type errors show (B-19 wording). |
| Session-expired handling | not checked | Shared with A and C |

## Checklist (section 11.2)

| Item | Status | Reason or evidence |
|---|---|---|
| Stack row checked: effective `DB_URL`, API port and web port printed and matched | Ticked | `jdbc:postgresql://localhost:5432/coaching_center_qa_2`, 8082, 3002 |
| Reset per 2.2a, recorded in the header | Ticked | Backup 190,039 bytes, drop and recreate done, seed result recorded. First start failed at the seed (Master Admin missing); second start succeeded with 0 ERROR lines. Cause of the first failure not found. |
| Code and requirements pass for every route in the slice | Ticked | Sections above; code read at `c2cd843`. |
| Section 3.8 table filled for every user type the slice touches | Ticked | Student rows above. Instructor and Master Admin: API only for marking and publish; their screens are Agent C's. |
| Snapshots at 360, 390, 768, 1024, 1440, bn and en, light, for every route in the slice | Partly ticked | Done for the student and guest routes in the coverage table. Pre-reset snapshots are in `pre-reset/` and not cited as walks. |
| Dark theme at 360 and 1440, bn and en, for every route in the slice | Partly ticked | Done: results, notices, routine, my-modules, written sitting, written result, guest routine, MCQ result (pre-reset). Not run post-reset: the MCQ sitting, MCQ review and leaderboard. Reason: the e0012 and e0011 windows were reset by the API restart and could not be re-published (see Incident). The e0005 window covers these at 12:09 UTC. |
| Every role in the slice signed in and walked | Ticked for student 5101, 5102, 5103 and 5104, and the guest. Instructor and Master Admin through the API only. | Staff UI is Agent C's. |
| Browser coverage: Chromium at all widths; WebKit and Firefox at 360 and 1440 for key pages | Partly ticked | Chromium: all widths. Firefox: 360 and 1440 on key pages (run). WebKit: 360 and 1440 on key pages through the HTTPS front (run; see the WebKit section). MCQ review and leaderboard run in Firefox and WebKit at 360 and 1440 (Bangla) via e0005. |
| Every flow walk has a result | Partly ticked | Not reached: written deadline with no file (13:58 UTC). |
| Sections 3.1 to 3.7 checks run on the snapshots, with a note per page | Partly ticked | Overflow measured on each page by script; contrast checked by eye on the written paper (B-02); touch targets from the code (B-06). Notes in the walk table. |
| Coverage table: every route is audited, partly audited with the missing item, or not reachable | Ticked | Coverage table above. |
| Every finding uses the section 7 format with its confidence tag | Ticked | Each finding has a confidence tag. Read-in-code-only items are Medium or Low. |
| Header status matches the checklist | Ticked | "partial" |

## WebKit logged-in gap (plan item 5)

Resolved as far as this machine allows. Signed-in WebKit now works through a local HTTPS front with a self-signed certificate for `127.0.0.1`. It is a documented alternative, not a real Safari, so the WebKit screenshots are "emulated only, confirm on device". The same origin had to be added to the API's `WEB_ORIGIN` (`https://127.0.0.1:3443`), which caused the seed re-run at 11:36 UTC (see the Incident).

## Not reached

Open. Shahriar will complete items 1 and 4 by hand; the file stays "partial" until then.

1. Written deadline (13:58 UTC): the no-file failed-to-submit result, and the student's screens after the window closes.
2. Dark-theme MCQ sitting after the reset: not captured.
3. Staff comparison pages: owned by Agent C.
4. Leaderboard tie after the reset: depends on a window with two equal scores.
5. Real Safari and real devices: outside this run.

## Fix status (implemented in `F:\cc-wtudit-b`, branch `ui/audit-b`, uncommitted)

Each finding above keeps its original text. This section records the fix and what was checked after it. Checks were run in Chromium on the fixed build; WebKit and Firefox were not re-run on the fixed build.

| Finding | Status | Check |
|---|---|---|
| B-01 stored script lost on reload | Fixed (API `answerStored` flag, web shows the stored line) | Browser: stored line shown after reload |
| B-02 watermark invisible in dark theme | Fixed (fixed ink colour, higher opacity) | Dark snapshot: watermark visible |
| B-03 marked script without watermark | Fixed (same watermark on the marked script) | Browser: watermark node present |
| B-04 Passed count drops on Marking tab | Fixed in code (marks fetched for every result row) | Code only: no student with a marking row in the seed |
| B-05 Bangla digit on English page | Fixed in code (zero formatted by locale) | Code only: no failed-to-submit student in the seed |
| B-06 40 px jump links | Fixed (44 px) | Browser: 44 px |
| B-07 undated exams missing from routine | Fixed as decided: undated exams show in their own section, last, with the note "The date will be updated here once it is announced." API includes them; the routine and the module page skip the calendar button and the countdown for them | Browser at 360 px in both languages: section shown, no overflow, no errors |
| B-08 `/results` missing missed and open exams | Fixed (third tab, with status pills) | Browser: tab present |
| B-09 breadcrumbs with no way back | Fixed (links to results and the exam result) | Browser: links present |
| B-10 Resume after the deadline | Fixed in code (result offered once the deadline has passed) | Code only: needs a sitting past its deadline before the server posts it |
| B-11 no access on `/sit` | Fixed (clear message with a link back, no redirect) | Browser as 5104: message shown |
| B-12 dialog focus | Fixed (focus starts on Cancel) | Browser: the submit dialog opens with focus on Cancel (checked at 19:30 Dhaka, 5102 on e0011) |
| B-13 unread notice with no link | Fixed (opens and marks read) | Code only: no unread notice without a link in the seed |
| B-14 guest on `/routine` | Fixed (student guard) | Browser: guest goes to login |
| B-15 header overflow at 360 px in Bangla | Fixed (compact header below 380 px) | Browser: page width 360 px |
| B-16 reload hides the owned exam's action | Fixed (public queries keyed on sign-in) | Browser: action shown after reload |
| B-17 Start does not turn on at the window | Fixed (status re-read every 15 s while upcoming) | Browser: switched to Continue with the page left open |
| B-18 correct options only after release | Decision: keep the staff release gate. No code change. | Not applicable |
| B-19 "৮০.০ MB" on the Bangla page | Fixed (Bangla digits and unit, no stray decimal) | Browser: "৮০ এমবি" |

Tests: web type check passes; web test suite 610 of 610 passed before the routine change, and the catalog and student tests (62) passed after it. API compiles, including the updated routine test. The API integration tests were not run, because the suite can connect to `coaching_center_test`.
