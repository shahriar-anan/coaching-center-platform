# WS4 — Sitting, results, leaderboard

Branch `ui/ws4-student-sitting`, agent D, from the start. Build against the WS0 and WS1 **Contracts** until those merge, then merge `catalog-acquisition` into your branch. Finish the `PdfViewer` early: WS5 and WS6 reuse it, and WS4 merges before them. Read [README.md](README.md) and [ws0-foundation.md](ws0-foundation.md). States follow Prompt 3 of [../../ui/student-flow-prompts.md](../../ui/student-flow-prompts.md), except the MCQ layout (U11) and the saved-selection behavior (D-a). The rules in "Rules the API enforces" there are binding.

**Copy:** Bangla quoted in this file shows intent only. Write the final Bangla by [bangla-copy.md](bangla-copy.md) (its glossary and rewrites win, for example লগইন not প্রবেশ, সরাসরি ভর্তি not রোস্টার, শিক্ষক not ইনস্ট্রাক্টর, তারিখ পরে জানানো হবে). Build every link to the other surface with `siteHref()` or `staffHref()` from `lib/config`.

## Owns

- `web/src/app/(site)/exam/[examId]/sit/**`, `.../result/**`, `.../leaderboard/**`, `web/src/app/(site)/results/**`
- `web/src/components/student/sitting/**`, `web/src/components/student/result/**`, and `web/src/components/pdf/**` **except `pdf-viewer.tsx`**: WS0's `pdf-viewer.tsx` is the lazy wrapper everyone imports; put the viewer body in `pdf-viewer-impl.tsx` (replacing WS0's stub) and siblings like `pdf-document.tsx`. Keep the props, because WS5 and WS6 import it. `allowDownload` is false for students and true for staff answer scripts.
- `web/src/lib/api/exam/sitting.ts`
- `web/public/pdf.worker.min.mjs` (copied from `pdfjs-dist` by a `postinstall`-free script or a checked-in copy; must work in the standalone build)
- `web/src/i18n/messages/{bn,en}/{sitting,result}.ts`
- Tests under `web/tests/ws4/**`

## Entry

`/exam/[examId]/sit?start=live|retake` comes from the exam page (WS3). With `start=live` call `POST /api/v1/live-exams/{id}/take`; with `start=retake` call `POST .../retake`; then replace the URL with `/exam/[examId]/sit`. Without `start`, read `GET /api/v1/live-exams/{id}`: an open sitting renders; anything else returns to `/exam/[examId]` (or to the result when the sitting has ended). Requires a signed-in student (`RequireStudent`) with a phone. Map errors: `SITTING_NOT_OPEN` (window closed or not started) back to the exam page with the reason; not owned (404) back to the exam page.

## MCQ paper (U11)

- **Sticky bar** under the header (a card, not a new color): exam title, `Countdown` from the sitting deadline and `serverNow()`, save state ("সংরক্ষিত", "সংরক্ষণ হচ্ছে…", "সংরক্ষণ হয়নি · আবার চেষ্টা"), answered count "১২/৫০", and a "প্রশ্ন" button that opens the jump grid.
- **Jump grid**: numbers in a grid, answered filled gold, unanswered outlined; tap scrolls to that question. On desktop it can sit under the bar; on phones it is a bottom sheet.
- **Paper**: all questions on one page, numbered, the stem in the paper's typeface (`BN` uses Noto Sans Bengali, `EN` uses Inter), options as full-width 44 px+ rows labeled ক খ গ ঘ (Bangla paper) or A B C D. The selected option has a gold border and the word "নির্বাচিত". Tapping the selected option again clears it.
- **Autosave**: every change sends the whole selection with `PUT /api/v1/live-exams/{id}/answers`, debounced 400 ms, one request in flight, the latest selection always wins, retry with backoff (1 s, 2 s, 4 s, then show the retry button). Keep a backup of the selection in `localStorage` keyed by exam, student and sitting start, and merge it with the server's saved `selected` on load (server wins when they differ and the server copy is newer). Clear the backup when the sitting ends. Warn before leaving the page while a save is pending.
- **Submit**: primary "জমা দিন" at the end of the paper and in the bar. The confirm dialog shows answered and unanswered counts. `POST .../submit` returns the score: show "৩৮ / ৫০ · উত্তীর্ণ" and "সময় শেষ হওয়া পর্যন্ত উত্তর বদলানো যাবে।" The paper stays editable until the deadline, without any answer key.
- **Deadline**: when the countdown reaches zero, stop accepting taps, flush the last save, and move to `/exam/[id]/result` without asking the student to refresh. The server posts saved answers anyway.
- **Later attempt**: the banner "এটি পরে দেওয়া চেষ্টা। মেধাতালিকায় উঠবে না।" and the timer labeled "আপনার সময়".
- **Flag for review** (LMS standard): a "পরে দেখব" toggle on each question, kept in the same `localStorage` backup (never sent to the server). Flagged numbers show a marker in the jump grid, and the submit dialog lists them. *(P1)*
- **Time warnings**: toasts and a polite screen-reader announcement at 10, 5 and 1 minute left (`Countdown warnAt`). In the last minute the bar turns to the warning tone.
- **Connection**: `OfflineBanner` "ইন্টারনেট সংযোগ নেই · উত্তর এই ডিভাইসে রাখা হচ্ছে" while offline; saving resumes and flushes when back online.
- **Text size**: A− / A+ in the bar, three steps, remembered per device. *(P2)*
- No bottom tab bar on this page (WS0 hides it) and no links that leave the paper except through the leave guard.
- Focus stays on the chosen option; the countdown never steals focus and announces only each minute.

## Written sitting

- Sticky bar with the countdown.
- **Question PDF**: `GET /api/v1/live-exams/{id}/question-pdf` as a blob with the auth header, rendered with `pdfjs-dist` into canvases (dynamic import; the worker from `/pdf.worker.min.mjs`). Fit to width, page controls, pinch and button zoom, lazy-render pages near the viewport. A watermark layer over the canvas repeats "নাঈম হাসান · S18492017" (the student's name and code from `/me`) diagonally at low contrast. No download, no open-in-new-tab, no print control, and the context menu is suppressed on the viewer. Do not claim screenshots are blocked.
- **Answer upload**: the mockup's dashed drop zone, "খাতার পিডিএফ এখানে আপলোড করুন", "শুধু পিডিএফ, সর্বোচ্চ ৮০ এমবি". Check the `%PDF` header and size before sending. Upload with progress (use `XMLHttpRequest` through a helper that adds the token and refresh handling, since `fetch` has no upload progress). After storing: file name, size, upload time, and "বদলে দিন". Replacement is allowed until the deadline. An image shows "শুধু পিডিএফ।" and the zone stays ready.
- **Deadline**: as MCQ, move to the result.

## Result (`/exam/[examId]/result`)

`GET /api/v1/live-exams/{id}/result`. Mockup stat row of three cells.

- MCQ: নম্বর "৩৮ / ৫০", ফল উত্তীর্ণ or অনুত্তীর্ণ (from the server), মেধাক্রম (from the leaderboard row when it is open, else "—"). Review list after the deadline: each question with "আপনার উত্তর" (orange border) and "সঠিক" (green border) as words and borders, and the explanation. Later attempts are labeled as such.
- Written, failed to submit: নম্বর "০", ফল "জমা হয়নি", মেধাক্রম "—".
- Written, stored and not published: প্রাপ্ত নম্বর "—", মেধাক্রম "—", খাতা মূল্যায়ন "অপেক্ষমাণ". No comment and no marked script.
- Written, published: marks, pass, rank, the comment without any name, and "মূল্যায়িত খাতা দেখুন" opening the same PDF viewer on `GET .../evaluated-pdf`.
- Link "মেধাতালিকা".

## Results hub (`/results`) — the student's gradebook

The bottom tab "ফলাফল" opens this. Data: the student's modules (`GET /api/v1/live-exam-modules`) and each module's exams (`GET /api/v1/live-exam-modules/{id}/exams`; the student owns few modules, so one request per module is fine, run in parallel and cached). Show exams whose `myStatus` is `SUBMITTED` (after the window), `AWAITING_RESULT`, `RESULT_READY` or `FAILED_TO_SUBMIT`, newest first, in two tabs:

- **ফলাফল**: `RESULT_READY` and `FAILED_TO_SUBMIT` rows with title, module, type, date, marks "৩৮ / ৫০" and the pass pill (from `GET .../result`, fetched for the visible rows only), linking to the result page.
- **মূল্যায়ন চলছে**: `AWAITING_RESULT` written rows with "খাতা অপেক্ষমাণ".

Summary strip on top (P1): exams taken, passed, results waiting. Empty: "এখনো কোনো ফলাফল নেই" with a link to `/routine`.

## Leaderboard (`/exam/[examId]/leaderboard`)

`GET /api/v1/live-exams/{id}/leaderboard`. Closed: "মেধাতালিকা দুপুর ১২:৪৫ এ খুলবে।" (five minutes after the end) or the undated sentence. Open: table নাম, ক্রম, নম্বর, পূর্ণমান, ফল; ties share a rank and the next rank skips; the student's own row is highlighted; no phone column. Paged if the response is paged.

## Performance

The PDF viewer and `pdfjs-dist` load only on the written sitting and on "মূল্যায়িত খাতা দেখুন". The MCQ paper must stay smooth with 100 questions on a mid-range phone: memoize question rows, update only the changed row on a tap.

## Tests

Results hub tabs and empty state, flag markers in the grid and submit dialog, time-warning thresholds, offline flush, take and retake entry and error redirects, autosave debounce and latest-wins, backup merge with server `selected`, submit dialog counts and score card, deadline transition, PDF file checks (image rejected, size), upload replace state, each result state, leaderboard closed and ties.

## Report

**Done (P0):**
- Sitting entry: `?start=live` and `?start=retake` take or retake the exam, then replace the URL. A reload resumes the open sitting. The phone-missing, closed and not-found cases go back to the exam page or show the message.
- MCQ paper: all questions on one page, sticky bar with the countdown and save state, jump grid, autosave (one request in flight, 400 ms debounce, latest selection wins, backoff 1/2/4 s), per-device backup merged with the server's saved selection, deadline moves to the result without a refresh, later attempt labelled as practice, submit dialog with counts.
- Written sitting: question PDF inline with the watermark (name and code), no download; answer upload with progress, `%PDF` check, 80 MB limit, replace until the deadline; deadline moves to the result.
- Result: MCQ score, pass, and review after the deadline; written failed-to-submit, awaiting marks, published (comment and marked script in the viewer, no download).
- Leaderboard: closed until five minutes after the window, with the opening time; undated; table with shared ranks.
- `/results` hub: two tabs (results, being marked), summary strip, marks and pass for visible rows only, empty states.
- Route pages: `sit`, `result`, `leaderboard`, `results`, each guarded by `RequireStudent` inside `Suspense`.

**P1 done:** flag for review (local only), time warnings at 10, 5 and 1 minute, connection banner while offline, results summary strip, later-attempt banner.

**P2 done:** none.

**Skipped:**
- Text size control (P2).
- Own row highlighted on the leaderboard, and the student's merit rank on the result page. Both need a "this is you" flag from the API (see contract issues).
- Written upload has no unit test for the replace state or the deadline transition. The offline flush and take/retake redirects have no tests yet.
- Visual checks at 320 px and 1440 px, light and dark, and keyboard use were not run. The brief leaves the browser pass to the integrator.

**Contract issues:**
1. `GET /api/v1/live-exams/{id}` does not return the open sitting's `deadlineAt` or `startedAt`. The page keeps the take response in session storage for this tab as a workaround.
2. Leaderboard rows have no "this is you" flag. Needed for the own-row highlight and the result page's rank.
3. The result response has no `kind` (LIVE or RETAKE), so the result page cannot label the later attempt.

**Notes for other agents:**
- `pdf-viewer-impl.tsx` holds the viewer body with the props unchanged (`load`, `watermark`, `allowDownload`, `fileName`, plus optional `label`). `pdf-document.tsx` re-exports it.
- `public/pdf.worker.min.mjs` has an `eslint-disable` comment on its first line, because the vendored code fails the project's lint rules. Its version test still matches.
- `RequireStudent` (WS0) reads `useSearchParams`, so any route that uses it must sit inside a `Suspense` boundary, or the build fails on prerendering.
