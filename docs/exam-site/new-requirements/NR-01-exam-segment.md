# NR-01: Exam segment (authoring, window and duration, archived participants, editing after publish)

**Status:** agreed 28 October 2026. API plan written in [nr-01/](nr-01/README.md). The UI comes later in a separate plan.
**Raised:** 28 October 2026, by the client via Shahriar
**Roles affected:** Master Admin, System Admin (authoring); Instructor (written marking); Student (sitting, results, leaderboard)

## Requirement

As Shahriar relayed it on 28 October 2026:

1. **Exams in the sidebar.** One staff page that lists exams under their modules, nothing else, so creating and updating an exam inside a module is quick.
2. **The old site's exam rules mostly still hold**: price, discount, free exams and the rest. Some may not carry over. Each one is decided below.
3. **The live window and the exam duration are separate.** For example, the window is 10:00–11:00 and the duration is 40 minutes.
   - A student who starts inside the window is a **live participant**, even if they submit after 11:00.
   - When results are published, live participants are on the leaderboard with their marks.
   - A student with access who starts after the window is an **archived participant**. This applies to MCQ and written, under the same rules. They are not on the leaderboard. When their result is published they see the position they would have had among the live participants.
4. **Editing.** The exam cannot be edited during the live window, as today. After the window it stays **published**, not archived, and every field can be edited without side effects. When an edited exam is published, the Master Admin first sees a list of what changed (price, description, titles, question paper and so on) and confirms it.

## Today: the old site (read-only inspection of `client-vps`, 28 October 2026)

Laravel at `/var/www/html/symamlaw-backend`, MariaDB. The admin and student sites are the Next.js apps under `/root/symamlaw`. Nothing was changed on the server.

**Structure**

- Exam categories have two levels: 9 top-level and 62 subcategories. An exam sits directly in a subcategory.
- A **package** is a separately sold bundle of exams (`package_items`). It has a price, a **percent discount**, an **access duration in days** (60–360) and a "popular" flag. One exam can be in several packages. There are 7 packages; the largest holds 36 exams.

**Exam fields**

- Title, description, excerpt, image.
- `exam_type`: `static` (practice, no window) or `live`.
- `exam_format`: `mcq` or `written`.
- `mode`: `exam`, `preview` or `both`. Preview shows the paper without sitting it.
- Category, subject.
- `start_datetime` and `end_datetime` (the live window) and `duration` in minutes.
- `total_marks`, `pass_marks`, `total_questions`.
- `price`, `discount` and `is_free`. The code applies the discount as a percent, although the admin form labels it "Flat discount".
- Negative marking: on or off, plus an amount.
- `is_answer_changable`.
- A published flag.
- `publish_live_result`: a Master Admin switch that reveals live results.

**How it was used (252 exams)**

| Kind | Count | Typical settings |
|---|---|---|
| Static MCQ | 138 | Mostly free (124 free) |
| Static written | 5 | The only written exams. None was live |
| Live MCQ | 109 | Mostly priced 70–200 with a 30–50% discount. 25 free |

- Negative marking is on in 236 of 252 exams, almost always 0.25.
- 8,952 of the 8,979 questions are MCQ worth 1 mark. Only one question has more than one correct answer.
- Live windows were usually 3 to 16 hours with a duration of 25 to 50 minutes. This confirms that the window and the duration were separate.

**Sitting**

- Start was blocked before `start_datetime`. The check against `end_datetime` is commented out, so a live exam could be started at any time after it opened.
- The timer was personal: start plus `duration`. It was never cut at the window end.
- Attempts were unlimited in practice:
  - 225 student–exam pairs have 2 attempts.
  - One student has 68 attempts on one exam.
  - The website says "contact your administrator to retake", and the admin could reset a stuck attempt.
- The `turned_in_late` submission type exists but was never used. 95 of the 225 live submissions were made after the window had ended.

**Results**

- MCQ is scored at submit, with negative marks. A question with several correct answers scores only when every correct option is chosen.
- A live exam's results stay hidden until the Master Admin turns on `publish_live_result`.
- The ranking shows every result at or above the pass mark, highest score first, then earliest submission. It does **not** separate live from late attempts, and repeat attempts appear too.

**Purchase**

- A student buys an exam or a package. The price is after the percent discount. Package access ends after the package's duration in days.
- A free exam needs no purchase.
- A coupons table exists but has 0 rows.

**Editing**

- There is no lock. An admin could edit anything, questions included, at any time, even during a live window.

## Today: the new system (this repo, branch `demo/client-review`)

**Structure**

- Category tree, then module (one category, optional price), then exam (optional price).
- A module purchase covers every exam in it, including exams published later. An exam purchase covers that exam only.
- No discount, no free flag and no access expiry.
- No negative marking (`decisions.md`: "no negative-mark field until the client asks for one").
- A pass percent instead of pass marks.

**Live window, sitting and leaderboard**

- **The window is the duration.** `endAt = liveAt + durationMinutes`.
- Take exam is open only inside that window, and the deadline is the shared end, not the start plus the duration (`LiveExamSittingService.take`). A student who starts at 10:50 in a 10:00 exam with 40 minutes gets 10 minutes.
- Later attempt: MCQ only, once, only if the student never started the live sitting, with a personal timer. A written exam has no later attempt.
- The leaderboard is live participants only. The MCQ board opens 5 minutes after the window. The written board shows released rows.

**Locking and editing**

- **The API** locks the schedule, scoring, paper language, questions and PDF from `liveAt` onwards, forever (`CONTENT_LOCKED`). Titles, descriptions, rules and price can still be changed through the API after that.
- **The web editor** is stricter. Once `liveAt` has passed, every section is read-only, price and text included (`exam-editor.tsx` `locked`).
- **Nothing archives an exam automatically.** Archive is a manual Master Admin action, and it removes the exam from every student page. What the client calls "archived after the window" is most likely the read-only editor or the student-side "ended" state. This needs confirming (question E1).
- Any edit before `liveAt` puts the exam back to draft. A draft disappears from students until it is final-saved and published again.

## Open questions

See the conversation of 28 October 2026. Answers are recorded in **Decisions** below as they come in.

## Decisions

Round 1, answered by Shahriar on 28 October 2026. Numbers match the questions in the conversation.

| # | Decision | Date |
|---|---|---|
| A1 | One exam may belong to **several modules**. | 2026-10-28 |
| A2 | Access expiry is set **per exam**: a number of days, or lifetime. Modules have no expiry of their own. Agent to stress-test this (round 2). | 2026-10-28 |
| A3 | No exam image, excerpt or subject. **Popular**: the Master Admin can mark a module or an exam as popular, and it shows on student pages. **Favourites**: a student can mark an exam as a favourite, and it shows on a Favourites page or tab. | 2026-10-28 |
| A4 | Old-site data is out of scope. | 2026-10-28 |
| A5 | **Module is renamed Package.** Scope of the rename is in round 2. | 2026-10-28 |
| B5 | Percent discount on packages and exams. The original price is shown crossed out. | 2026-10-28 |
| B6 | A discount may have an optional end date. | 2026-10-28 |
| B7 | A free exam can be sat by any signed-in student without buying. Free exams may sit inside paid packages. Free live participants count on the leaderboard. | 2026-10-28 |
| B8 | No coupons. | 2026-10-28 |
| C10 | **Preview** is an option chosen when the exam is created. With it on, students can see the paper but cannot take it (for promotion). Details are in round 2. | 2026-10-28 |
| C11 | Negative marking per exam, default 0.25. | 2026-10-28 |
| C12 | Keep the pass percent. | 2026-10-28 |
| C13 | Keep the old per-exam MCQ switch **"answers can be changed"**. | 2026-10-28 |
| C14 | **Multi-answer MCQ is required.** No images in questions. MCQ options are chosen through a radio circle on the left, not by clicking the whole option. That is a UI change outside this plan. | 2026-10-28 |
| D15 | Deadline = the student's own start + duration, with no cap at the window end. Only the start has to fall inside the window. | 2026-10-28 |
| D16 | The window must be at least as long as the duration. | 2026-10-28 |
| D17 | Drop the five-minute clash rule between windows. | 2026-10-28 |
| D18 | Publishing without a date stays allowed. | 2026-10-28 |
| D19 | MCQ: **unlimited retakes**. Written: **one** retake, only when a Master Admin or System Admin grants it. | 2026-10-28 |
| D20 | An archived participant can sit any time after the window while their access lasts. | 2026-10-28 |
| D21 | Written result release is unchanged (Master Admin). **MCQ results are shown at submit**: marks; position on the current live leaderboard if live, or the relative position message if archived; correct answers and explanations. No waiting for the window to end. | 2026-10-28 |
| D22 | The client accepts the answer-leak risk. | 2026-10-28 |
| D23 | Relative position wording: "Your score would have ranked #12 of 340 live participants". Ties take the better rank. MCQ shows it at submit, written when the paper is released. | 2026-10-28 |
| D24 | **Keep the old pass-mark filter on the leaderboard**: only results at or above the pass mark are listed. | 2026-10-28 |
| D25 | Staff see live and archived participants counted separately. | 2026-10-28 |
| E27 | **No edit lock.** The client can edit an exam at any time, including during the live window, because windows run 12–16 hours. | 2026-10-28 |
| E28 | A change to an answer key or a question re-scores the MCQ papers already submitted. | 2026-10-28 |
| E29 | The question PDF can be replaced after students have sat. The client knows the risk. **The evaluator and the student must see the exact PDF that student sat.** | 2026-10-28 |
| E30 | The window can be moved at any time, as on the old site (the old site had no lock). | 2026-10-28 |
| E31 | Staged edits. The published version stays live until the Master Admin publishes, after a field-by-field change list. | 2026-10-28 |
| E32 | System Admin stages edits, only the Master Admin publishes, and the Master Admin sees a "changes waiting" badge. | 2026-10-28 |
| E33 | Agent decides. Proposal: a notice (no mail) to students with access, only when the time, duration or paper changes. | 2026-10-28 |
| E34 | Packages follow the same staged edit and publish rules. | 2026-10-28 |
| F35 | Exams page grouped by package with filters, in the Catalog group. Participation is renamed "Results & marking". | 2026-10-28 |
| F36 | 10–15 packages expected. | 2026-10-28 |

Round 2, answered by Shahriar on 28 October 2026. **These replace any round 1 row they conflict with.**

| # | Decision | Date |
|---|---|---|
| A2' | Access days are counted **from the window end, or from the grant if that is later**: `max(grant start, window end) + N days`. "Lifetime" means no end. For an undated exam the clock has not started yet. A practice exam has no window, so its anchor is the later of the grant and the publish time. Expiry stops new attempts and the paper. A student's own results, answers and marked PDF stay readable. Free exams never expire. | 2026-10-28 |
| C9 | **Practice exams are included.** Mode `PRACTICE`: no window, open from publish. The leaderboard is built from each student's first attempt. | 2026-10-28 |
| C10' | Preview opens only after the live window has ended, or at any time for a practice exam. It shows questions and options without correct answers or explanations. | 2026-10-28 |
| D21' | Answers and explanations at submit are confirmed, even during the window. The client accepts the leak. | 2026-10-28 |
| E26 | "Archived" on the old site meant a live exam whose window has ended. It stayed published and could still be sat. The staff **Archive** action (which hides an exam) is relabelled **Withdraw** in the UI. The API status stays `ARCHIVED`. Students get an "Archived exams" grouping. | 2026-10-28 |
| R1 | Module → Package is a rename in **screens and copy only**. The API paths and tables keep `module`. | 2026-10-28 |
| R2 | **An exam's home is a category** (required). Packages are bundles holding zero or more exams. An exam may belong to no package. An exam created inside a package takes the package's category and joins it. "Add existing exam" puts an exam into another package. | 2026-10-28 |
| R3 | The instructor moves **onto the exam**. A new exam inherits the package's instructor. | 2026-10-28 |
| R4 | When an exam is removed from a package, students who held that package before the removal keep the exam while their access lasts. | 2026-10-28 |
| R5 | Buying exam X gives access to X only. | 2026-10-28 |
| R6 | Popular: a "Popular" section on the student home and a badge on cards. | 2026-10-28 |
| R7 | Exams and packages can both be favourited, without buying first. | 2026-10-28 |
| R8 | Multi-answer scoring: exact match earns full marks; a non-empty wrong selection loses the negative mark; a blank scores 0. The paper says "select all that apply". | 2026-10-28 |
| R9 | With "answers can be changed" off, a single-answer question is final at the first choice. A multi-answer question is final when the student confirms it. | 2026-10-28 |
| R10 | Marks stay per question. | 2026-10-28 |
| R11 | The leaderboard holds only the live attempt (practice: the first attempt). MCQ retakes are never on it, and each retake gets a relative position. A retake started during the window is still a retake. | 2026-10-28 |
| R12 | While the window is open, students see only their own position. The full board opens at the window end. | 2026-10-28 |
| R13 | A score below the pass mark shows "Below pass mark, not ranked", for live and relative positions alike. | 2026-10-28 |
| R14 | Written retake: granted on the student's result by the Master Admin or a System Admin, with an optional reason and an audit entry. It is marked and released like any paper, sits beside the first result, and is never on the leaderboard. | 2026-10-28 |
| R16 | Once any student has started the exam, questions and options cannot be added or removed. Wording, answer key, marks and explanations can still change. Changes to scoring re-score submitted papers. | 2026-10-28 |
| R17 | A student mid-sitting sees new wording and keys after a reload. Their duration is fixed when they start. | 2026-10-28 |
| R18 | Live or archived is decided when a sitting starts and never changes, even if the window moves later. | 2026-10-28 |
| R19 | A checkout already started keeps its price. | 2026-10-28 |
| R20 | Staged (unpublished) edits are visible to admins only. | 2026-10-28 |
| R21 | On the Exams page, an exam in several packages appears under each one with an "also in…" note. | 2026-10-28 |
| UI | **Every UI change is outside this plan** and gets its own plan later. This plan is API and data only, and the current website must keep working. | 2026-10-28 |

Defaults the plan takes. Shahriar can change any of them:

- **P-a:** a negative total is allowed, as on the old site, so the total is not floored at 0.
- **P-b:** preview needs a signed-in student. Public preview would put the paper PDF on an open URL without a watermark.
- **P-c:** new exams default to "answers can be changed" on and negative marking off. When negative marking is switched on, the UI suggests 0.25.
- **P-d:** the existing "answers released" switch is retired, because answers now show at submit. The endpoint is kept and does nothing.
- **P-e:** the "exam updated" notice is in-app only, no mail. It is sent only when the time, duration or paper changes.

## Plan

[nr-01/README.md](nr-01/README.md)

## Slices

Written after the decisions are agreed.

## Record
