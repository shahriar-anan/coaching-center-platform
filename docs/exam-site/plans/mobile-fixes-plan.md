# Mobile fixes: discounted prices, exam page overflow, written upload

Status: implemented 13 Nov 2026. Web Vitest suite passed (256 files, 1742 tests); type-check and lint not run. Also in this round: the phone tab bar stays visible (the footer is padded clear of it), the written sitting keeps the paper above the upload, long file names truncate in the upload box, and dialogs shrink to fit long unbroken text. Photo-to-PDF (C2) is out of scope and stays as it is: PDF only. Device checks (C7) still to do. Implemented: C1, C3, C4 (upload card first on phones), C5, C6 (stall timeout, size-based early send, no retry storm), A1 (wrapping package rows, Bangla discount digits), B (overflow-wrap on rich text and the exam title). Not done: paisa rounding (a product decision), and the in-app-browser file-picker change (C7 first).

Evidence comes from reading the code and from a Playwright run in Chromium with phone emulation (360×740 and 390×844, touch on) against the local dev servers on `coaching_center_sample`. The written-exam sitting was driven with route stubs: real login as a sample student, a fabricated open sitting, and a stubbed upload POST. No database row was changed.

## What we found

### A. Discounted prices squeeze or hide titles (confirmed)

A discounted `PriceLine` (effective price, struck list price, discount chip, end date) is about 250 px wide. Two row layouts still give it its full width and leave the title whatever space is left.

| Where | Code | Measured at 360 / 390 / 430 px |
|---|---|---|
| Exam page, "packages with this exam" card | [exam-packages.tsx:73](../../../web/src/components/student/catalog/exam-packages.tsx#L73): price wrapper is `shrink-0` | Title column **0 px** / 14 px / 54 px. The package name is invisible and "৫টি পরীক্ষা" breaks one word per line. |
| Package page, exam card with a discounted exam | [exam-row.tsx:133](../../../web/src/components/student/catalog/exam-row.tsx#L133) grid `minmax(0,1fr)_auto` plus [exam-row.tsx:147](../../../web/src/components/student/catalog/exam-row.tsx#L147) `whitespace-nowrap` | Title column **30 px** / 60 px. The title breaks one word per line, the date runs into the price, and the summary is cut off. |

The package card and the exam list row were already fixed in 8331118 (they wrap the price under the content). These two were missed.

Smaller issues in the same component ([price-line.tsx](../../../web/src/components/student/catalog/price-line.tsx)):
- The discount chip prints `String(percent)`, so the Bangla UI shows "19% ছাড়" with Latin digits (line 50).
- The effective price shows paisa, for example ৳৮৯.৯১. Decide whether discounted prices round to whole taka; the server's charged amount must match.
- On the exam and package detail cards the price block is `justify-end text-end` (line 43) under a left-aligned "মূল্য" label. With a real price such as ৳১২,৫০০ plus a dated discount it wraps into a ragged right-aligned block.

### B. The exam details page scrolls sideways with real content (confirmed)

A description containing a long link (for example a Google Drive syllabus URL) made the page **606 px wide on a 360 px screen**, so the whole page scrolls sideways. `richTextBody` in [rich-text-view.tsx:10](../../../web/src/components/ui/rich-text-view.tsx#L10) sets no `overflow-wrap`. A long Bangla title and a line of underscores wrapped fine. The same text style is used for rules, notices and announcements, so they share the bug.

### C. Written exam: why "Submit" seems not to work on phones

The upload itself works in Chromium emulation: choosing a real PDF enables the button, the confirm sheet opens, and exactly one upload goes out. The failures come from the conditions around it. In order of likelihood:

1. **A failed file read fails silently (confirmed by simulation).** On Android, a file picked from Drive, WhatsApp, Downloads or "Recent" is read lazily and can throw `NotReadableError`. [file-drop.tsx:61](../../../web/src/components/ui/file-drop.tsx#L61) `take()` and `choose()` in [written-sitting.tsx:59](../../../web/src/components/student/sitting/written-sitting.tsx#L59) have no try/catch. Result: an uncaught promise error, **no message, and the submit button stays disabled**. To the student, the button "does not work".
2. **Phones produce photos, not PDFs (confirmed).** Choosing a JPEG gives "ফাইলটি পিডিএফ নয়" and the button stays disabled. Most students scan or photograph pages with the camera. Nothing on the page helps them turn photos into a PDF, and the picker is limited to `application/pdf`.
3. **The submit button looks active before a file is chosen.** The primary button "খাতা জমা দিন" is only dimmed (`opacity-60`) and never explains why it is off. The real first step, "পিডিএফ ফাইল বেছে নিন", is the secondary button. Students tap the big primary button first, and nothing happens.
4. **The question paper traps scrolling (confirmed).** The PDF box is `h-[70vh]` ([pdf-viewer-impl.tsx:279](../../../web/src/components/pdf/pdf-viewer-impl.tsx#L279)) and fills everything below the timer. Four full-screen swipes scrolled the PDF about 2,100 px and **the page 0 px**. On a 390×844 phone the submit button is at y≈1354, and the page only moves once the student has scrolled through the whole paper. Many students will think there is no submit area, or that it is broken.
5. **The chosen file is lost if the tab reloads (likely, not reproduced).** The picked file lives only in React state (`pending`). Opening the file picker or a scanner app on a low-memory Android phone often kills the background tab, and the pdf.js canvases (up to 2× pixel ratio) add memory pressure. When the student returns, the page reloads, nothing is chosen, and the button is disabled again.
6. **Uploading over mobile data (likely).** Scans can be tens of MB. [client.ts sendUpload](../../../web/src/lib/api/client.ts#L220) has no timeout and no retry, and any network drop gives only the generic "could not be uploaded" message. The 10-second auto-send window before the deadline is far too short for a large file on 3G/4G.
7. **In-app browsers (to check on a device).** Many students open links from Facebook or Messenger. Some in-app WebViews do not open the file picker for a programmatic `input.click()`, so tapping "choose file" would do nothing.

## Plan

### Phase 1: written upload (ship first, it blocks exams)

1. **Never fail silently.** Wrap the reads in `FileDrop.take()` and `WrittenSitting.choose()` in try/catch and show a clear Bangla message such as "ফাইলটি পড়া যায়নি। ফাইলটি ফোনে ডাউনলোড করে আবার বেছে নিন।". Keep only one magic check (FileDrop already does it; `isPdf` repeats it). Accept a `%PDF-` header anywhere in the first 1024 bytes, as PDF readers do.
2. **Reorder the upload card for phones.**
   - Put the upload card first, or give it a sticky bottom bar "খাতা আপলোড করুন" that scrolls to it.
   - Make "choose file" the primary button until a file is staged. Only then show "খাতা জমা দিন" as primary.
   - While submit is off, show a one-line reason under it ("আগে পিডিএফ ফাইল বেছে নিন").
   - Make the `<label htmlFor>` and input a real tappable target instead of a hidden input opened with `.click()`, which also helps in-app browsers.
3. **Stop the scroll trap.**
   - On phones, show the question paper collapsed: a preview of the first page plus an "open full screen" button, using the viewer's existing overlay mode. Keep the inline 70vh box from `md` up.
   - Or cap it at about 50vh with `overscroll-behavior: auto` and a visible "scroll page" handle.
4. **Photos to PDF in the browser.**
   - Allow `accept="application/pdf,image/*"` and multiple images.
   - Images become a single PDF on the client (pdf-lib, or a small canvas → JPEG → PDF writer), downscaled to about 1600 px on the long side, with page order the student can check.
   - The server still receives only a PDF, so the API stays as it is. Needs a product decision, since the instructions currently say photos are not accepted.
5. **Survive tab reloads.** Keep the staged file in IndexedDB keyed by exam id and sitting until it is uploaded, and restore it on mount. Wrap access in try/catch; private mode may refuse it.
6. **Make the upload robust on mobile data.**
   - Add an XHR timeout and abort-safe progress.
   - On a network error, give a specific message and a "try again" that keeps the staged file.
   - Raise `AUTO_SEND_MS` to a size-based lead, for example 10 s plus file size divided by 100 KB/s, capped at a few minutes, and warn when a large file is staged close to the deadline.
   - Show the file size before the student confirms.
7. **Device checks** (Playwright emulation cannot cover these): Android Chrome on a low-end phone (picking from Drive, WhatsApp and Downloads), iOS Safari (Files app), and the Facebook/Messenger in-app browser on both.

### Phase 2: price and overflow

1. Change `PackageLinkCard` ([exam-packages.tsx](../../../web/src/components/student/catalog/exam-packages.tsx)) and `PackageExamCard` ([exam-row.tsx](../../../web/src/components/student/catalog/exam-row.tsx)) to the layout 8331118 used for the package card: title and meta take the full width, and the price sits on its own row under them, aligned to the end. Drop `shrink-0` and `whitespace-nowrap` around `PriceLine`.
2. `PriceLine`: format the percent with `formatNumber`. Left-align the detail-size variant (the end date may go on its own line). Settle the paisa question with the payments side.
3. Add `[overflow-wrap:anywhere]` (or `break-words`) to `richTextBody` and to the plain-text branch of `RichTextView`. Add `min-w-0` where a grid or flex child holds user text.
4. Check the same widths on the purchase sheet, the bundle selection and the sticky `ActionBar` with a long title and a five-digit discounted price.

### Phase 3: keep it fixed

Turn the probe used for this report into a phone layout check. It opens each catalog and detail page at 360 and 390 with long titles, long links and five-digit discounted prices injected through route stubs, and fails when `scrollWidth > clientWidth` or a title column is under 120 px. Add the written-upload flow (choose PDF, choose photo, read failure, confirm, upload) to the same check. It runs only when asked, as with the other suites.

## Order and size

| Step | Size | Risk |
|---|---|---|
| C1 silent read failure, C3 button order and hint | small | low; fix first |
| C4 scroll trap | small to medium | low |
| A1 and A2 price layouts, B rich-text wrap | small | low |
| C5 IndexedDB staging, C6 upload timeout and lead time | medium | medium (deadline logic) |
| C2 photos to PDF | medium | needs a product decision; affects marking (page quality) |
| Phase 3 check | small | none |
