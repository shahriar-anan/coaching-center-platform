# PDF viewer fix: old browsers and heavy answer scripts

Status, 22 Nov 2026: decisions D1–D4 taken as recommended. **Stage 0 is live** on exam-kvm (01:01 Dhaka; see
[nginx-conf-timed-log.md](../deploy/nginx/nginx-conf-timed-log.md)). **Stages 1–3 and 1.4 are implemented in the working
tree, not committed, not deployed.**

Final check, 22 Nov 2026 (working tree, including the unrelated uncommitted offline-routine work and V107):
- API: full suite on a fresh test database: 1,299 tests in 217 classes. The one failure was `AuditLogIT`, whose route list
  lacked the new `POST /api/v1/public/client-events`; with the route added it passes 3/3.
- Web: full Vitest suite: 324 files, 2,222 tests, all passed. Type-check and lint clean.
- Production check build with the release settings (empty API base, symamlaw.com / admin.symamlaw.com): built, and
  `check_release` passed. `public/pdf.worker.min.mjs` is byte-for-byte the legacy 6.4.299 worker; `public/pdfjs/`
  holds wasm (13), cmaps (169), standard_fonts (16) and iccs (2); the viewer chunk carries the legacy polyfills; the
  old-engine check passes against the shipped worker.
- `OpenApiSnapshotIT` compares nothing (it only writes `target/openapi.json`), so it needs no update.

Changes from the plan:
- The no-gain marker is also written for skipped files (unreadable, too large), so the sweep does not retry them every 10 minutes.
- The sweep reads stored names from the database (scripts and papers) instead of walking the file store, so only exam
  files get copies.
- Student pages scope the in-memory cache: the sitting's paper by its deadline, and the result page by the visit,
  because student routes serve the *latest* attempt and a bare path could show an old attempt after a retake.
- Seven PDF routes now return `Resource` and take `original`; `AuditLogIT` lists the new client-events route.

The client reports that PDFs open slowly or not at all during exams and evaluation. The investigation (code, production nginx logs 7–21 Nov, stored files, and a local reproduction) found two causes. The server is not one of them: all 428 PDF GETs returned 200 with the file in full, and the box is idle.

| | Cause | Who it hits | Evidence |
|---|---|---|---|
| 1 | pdfjs-dist 6.4.299's modern build calls `Uint8Array.prototype.toHex` (document fingerprint, **every** PDF), `Map.getOrInsertComputed`, `Math.sumPrecise` and `Uint8Array.fromBase64` with no fallback. Browsers older than about Chrome 140 / Safari 18.2, and Android in-app WebViews, fail before page 1. | Students on older phones and in Facebook/Messenger browsers. Any PDF, any size. | 12 Nov 12:03–12:05: a Chrome 138 student downloaded the full 170 KB paper 25 times in 2 min (retry loop). In Node 24 (V8 13.6, about Chrome 136), the modern build fails with `hashOriginal.toHex is not a function` and the legacy build opens every page. |
| 2 | Answer scripts are phone-camera scans of 20–25 MB (pages of about 2400×3300 JPEG, 14–38 pages, about 30 MB decoded per page). The viewer downloads the whole file on every mount (`no-store`, no client cache), does not abort on unmount, and keeps every rendered canvas at up to 2× pixel ratio. | Evaluators (especially on phones), and students opening their marked or submitted script. | 15 Nov 06:01–06:03: an evaluator on Android loaded the same 23 MB script 11 times in 2 min, with many cut-off transfers. Nine stored PDFs are over 20 MB. `PdfCompressor` only recompresses content streams, so scans never shrink. |

## Principles

- **Originals are never changed.** The upload path stays exactly as it is (the 15 Nov upload incident). Smaller copies made for viewing are disposable, and can be deleted and rebuilt at any time.
- **No Flyway migration.** Everything fits in the existing columns and file store.
- **Ship in stages**, each one releasable and reversible on its own, outside exam windows.
- Tests are written along with the code, but they run only when Shahriar says so (standing rule).

## Stage 0: measure first (ops, about 15 minutes, no code)

So that we can prove the fix worked rather than guess from byte counts:

1. On exam-kvm, add a `log_format timed` with `$request_time $upstream_response_time $body_bytes_sent $http_user_agent` and use it in `access_log` for the three server blocks. Then run `nginx -t` and `systemctl reload nginx`. Record it in `docs/exam-site/deploy/nginx/`.
2. Keep the log probes from the investigation (retry bursts per browser, partial transfers) as `docs/exam-site/reports/pdf-log-probe.sh`, to re-run a week after release.

## Stage 1: every browser can open PDFs (Cause 1, web only)

**1.1 Switch to the legacy build.** It ships in the same package (`pdfjs-dist/legacy/build`). It is the same pdf.js with the polyfills included, and adds about 65 KB to the viewer chunk and about 57 KB to the worker. Both load only when a PDF is opened.
- [pdf-viewer-impl.tsx:45](../../../web/src/components/pdf/pdf-viewer-impl.tsx#L45): `import("pdfjs-dist/legacy/build/pdf.mjs")`. Type imports can stay on `pdfjs-dist`.
- Replace `web/public/pdf.worker.min.mjs` with `legacy/build/pdf.worker.min.mjs`, keeping the eslint header. The API and worker versions must match, or pdf.js refuses to start.
- Add `web/scripts/copy-pdfjs-assets.mjs`, which copies the worker and the folders from 1.2 out of `node_modules`. Run it from `prebuild`, so the copy can never drift from the installed version again. The vendored worker has been copied by hand since 25 Oct.
- [deploy-on-box.sh:53](../../../web/scripts/deploy-on-box.sh#L53) and [pdf-viewer.test.tsx:104](../../../web/tests/ws4/pdf-viewer.test.tsx#L104) check that file. Update them if the name changes. The plan keeps the same name; Next serves `public/` with `max-age=0`, so browsers revalidate and pick up the new file.

**1.2 Serve pdf.js's decoders and fonts.** pdf.js 6 decodes JBIG2 (black-and-white scanner mode, common in CamScanner and Adobe Scan), JPEG 2000 and ICC colour through WebAssembly. Without `wasmUrl`, those images can come out blank. Copy `wasm/`, `standard_fonts/`, `cmaps/` and `iccs/` to `public/pdfjs/` with the script above. Then pass `wasmUrl`, `standardFontDataUrl`, `cMapUrl`, `cMapPacked: true` and `iccUrl` to `getDocument`.

**1.3 Say what went wrong.** Today every failure shows the same "could not load" message with Retry. That is why one student pressed Retry 25 times.
- Split the error state into **download failed** (network or API error: "Check your connection" plus Retry) and **this browser can't show it** (pdf.js threw after the file arrived: "Open this page in Chrome, or update Chrome", plus a button that copies the page link). In-app browsers are the main case, so the copy should name Facebook/Messenger. Write the Bangla as natural Bangla, not a translation.
- Retry stays, but after two parse failures it says plainly that retrying won't help on this browser.

**1.4 (Decision D2) Report viewer failures.** A small `POST /api/v1/public/client-events` gets `{kind:"pdf", stage:"download|open|render", message, path-kind}`. The server adds the user agent. It is sent with `sendBeacon`, rate-limited per IP, and carries no file names or user data. It writes one log line (`client_event kind=pdf ...`); no table. Next time we see failures in `journalctl` the same day, instead of reconstructing them from nginx logs.

## Stage 2: the viewer stops wasting downloads and memory (Cause 2, web)

**2.1 Download progress and abort.**
- `apiFile` / `apiBlob` ([client.ts:172-215](../../../web/src/lib/api/client.ts#L172)) take an `onProgress(received, total)`. Read `response.body` with a reader; the total comes from `Content-Length`. Fall back to `response.blob()` when there is no body stream.
- `PdfViewerProps.load` becomes `load(options: { signal: AbortSignal; onProgress }) => Promise<Blob>`. The viewer creates an `AbortController` per attempt and aborts it on unmount, so a closed tab or dialog stops its download.
- The loading state shows "Downloading 4.2 of 23 MB" (Bangla digits in bn) instead of a bare skeleton. A slow download then looks like progress, not like a hang.
- Call sites to update (14): written-sitting (2), submitted-page, result-page (2), paper-views (2), question-paper-panel, marked-pdf-view, exam-editor, written-paper, routine-pdf-uploader, batch-routines, routine-section, batch-page, exam-preview, pdf-attachment-card. Most become `load={(o) => apiBlob(path, o)}`.

**2.2 Keep downloaded files for the page session.**
- New `web/src/components/pdf/pdf-blob-cache.ts`: an in-memory LRU keyed by API path plus a version (for example `markedAt`). It holds at most 3 files and 80 MB, and only completed downloads.
- A new `cacheKey` prop on `PdfViewer` turns it on. Use it in paper-views (the Answer / Question / Marked tabs), the result page tabs and the written sitting. Switching tabs then re-opens from memory instead of downloading 23 MB again.
- The cache is cleared when the access token is cleared (sign-out or session lost), via `token-store`. A student's question paper is already on their screen, so holding the same bytes in memory for the page's life adds no exposure. Nothing goes to disk or `localStorage`.

**2.3 Use less memory while scrolling.** In `PdfPage` ([pdf-viewer-impl.tsx:347-432](../../../web/src/components/pdf/pdf-viewer-impl.tsx#L347)):
- **Unload far pages.** A second observer with a wide margin (about 3 screens) sees a page leave. The page then sets `canvas.width = canvas.height = 0`, calls `page.cleanup()` and goes back to "not near". It renders again when it comes back. Today a 38-page scan keeps 38 canvases; after this change it keeps about 6–8.
- **Cap the canvas area per page**, not only the pixel ratio: `ratio = min(devicePixelRatio, 2, sqrt(MAX_AREA / (w × h)))`, with `MAX_AREA` about 4 Mpx on touch devices and 8 Mpx elsewhere. Zoomed-in pages stay sharp enough and can't blow past mobile canvas limits.
- Cancel and skip renders for pages that left the screen during a quick scroll.
- Destroy the document (`doc.destroy()`) on unmount and on retry. Today only `loadingTask.destroy()` runs, after the effect.

## Stage 3: smaller copies of scans for viewing (Cause 2, API)

The biggest win: a 23 MB script should open as about 4–7 MB.

**3.1 `PdfViewCopyMaker`** (new, `api/.../files/`). PDFBox 3.0.5 is already on the classpath.
- Open the original with a temp-file stream cache (`IOUtils.createTempFileOnlyStreamCache()`), so a 25 MB file doesn't sit in the heap several times over.
- Walk every page's resources, including nested form XObjects. Handle each image stream once (tracked by `COSStream` identity), but only when **all** of these hold:
  - the image is larger than 2000 px on its long edge
  - the filter is `DCTDecode` or `FlateDecode`
  - it has 8 bits per component
  - the colour space is Gray or RGB (direct or ICC with 1 or 3 components)
  - there is no `SMask` / `Mask`
- Scale such an image to a **1800 px long edge** (about 150 dpi on A4; Decision D1) with bicubic interpolation in steps. Re-encode with `JPEGFactory.createFromImage(doc, scaled, 0.72f)` and put it back under the same resource name. The page's drawing matrix still places it at the same size, so nothing on the page moves. An evaluator's annotations and drawn marks are vectors on top of the image and are untouched.
- Skip anything else: JPX, JBIG2, CCITT, CMYK, masked images, or any image that fails to decode. A PDF where nothing shrank is fine.
- Save with object-stream compression. Keep the result only if it is **70% of the original or less**. Otherwise write a "no gain" marker, so the file is not tried again.
- Hard limits: skip originals over 100 MB or with more than 300 images. Catch and log every failure (`pdf view copy skipped ... reason=`); never throw.

**3.2 Storage: next to the original, not in the database.** Write the copy as `<storedName>.view.pdf`: first to a temp name, then an atomic rename. The no-gain marker is a zero-byte `<storedName>.view.none`. Both exist only when the original does:
- `LocalFileStorage.delete` also deletes both siblings. `commitReplacement` goes through `delete`, so replacing a draft or a marked script cleans up automatically.
- On submit, the draft's stored name moves to the answer column ([LiveExamSittingService.java:608](../../../api/src/main/java/com/coachingcenter/api/exams/LiveExamSittingService.java#L608)). The copy is keyed by stored name, so it follows with no extra code.
- Nothing lists the storage directory today, so the extra files can't be mistaken for orphans. Backups may include or skip them; they can be rebuilt.

**3.3 When copies are made.**
- After commit of `storeDraft` (student script), the marked-script upload ([LiveExamEvaluationService.java:570](../../../api/src/main/java/com/coachingcenter/api/exams/LiveExamEvaluationService.java#L570)) and question-paper uploads, enqueue the stored name if the file is over 3 MB. Do this in an `afterCommit` hook, so a rolled-back upload is never processed.
- **One** background thread with a bounded queue. The box has 2 vCPUs; scaling one 8 MP page takes about 0.2–0.4 s, so a 38-page script takes about 15 s of one core. Upload latency does not change, because the student's request returns before any of this work.
- **Quiet window:** the worker pauses while any written exam has a deadline within ±10 minutes, so it never competes with the end-of-exam upload rush. The deadline job already knows these times.
- `@Scheduled(fixedDelay = 10 min)` sweep: find participant draft, answer, pending and evaluated files and question-paper files that are over 3 MB and have neither sibling, and enqueue them. This covers restarts and makes the **backfill of the existing 20–25 MB files automatic** after release.

**3.4 Serving.**
- New `LocalFileStorage.viewPath(stored)`: the `.view.pdf` path when it exists, otherwise the original.
- Use it in `answerPdf`, `studentAnswerPdf`, `evaluatedPdf`, `staffEvaluatedPdf`, `draftPdf` and both `questionPdf` methods.
- Add `?original=true` on the staff and student download routes. The viewer's **Download** button fetches the original, so a downloaded file is always exactly what was uploaded. `PdfViewer` gets an optional `download` loader; without one, it saves the blob it is showing.
- Return `ResponseEntity<Resource>` (`FileSystemResource`) instead of `byte[]` from `Files.readAllBytes`. This streams from disk, so there are no 25 MB heap copies per request; it sets `Content-Length`, which progress in 2.1 needs; and Spring adds Range support for free. The `OpenApiSnapshotIT` snapshot may change shape (still binary) and will need refreshing.
- Response header `X-Pdf-Copy: view|original` for diagnostics.

**3.5 Not doing (considered).**
- Streaming pdf.js with range requests (first page before the whole file arrives). It needs pdf.js to fetch with our token and 401-refresh logic. Once copies are 4–7 MB and downloads show progress, it isn't worth the risk.
- Shrinking on the student's phone before upload. That is a heavier change in the upload flow that just stabilised. Revisit if copies are not enough.

## Verification (run on Shahriar's go)

| What | How |
|---|---|
| Old browsers open PDFs | Add the Node reproduction script (V8 13.6) as `web/scripts/check-pdfjs-old-engine.mjs`. It must pass with the shipped worker, and it can run in CI. Then install Chrome 136 with `npx @puppeteer/browsers install chrome@136`, point Playwright at it against the local dev servers (sample DB), and open a sit page, a result page and a marking page: **modern fails, legacy works** before the switch, and all work after it. |
| JBIG2 / JPX scans render | Fixture PDFs with JBIG2 and JPEG 2000 images open with the wasm folder served. |
| Viewer | Vitest: abort on unmount, progress events, cache hit on a tab switch, cache cleared on sign-out, far pages release their canvas, area cap. |
| View copies | Unit: a synthetic 30-page PDF with 2400×3300 JPEG pages shrinks by at least 70% and keeps page count and page sizes; masked, CMYK, JBIG2 and corrupt inputs are skipped without an exception. IT: a draft becomes an answer and the copy follows; replacing a draft deletes the old copy; `?original=true` returns the uploaded bytes exactly. |
| Readability | After the backfill on production, an evaluator opens three real scripts side by side with Download (original) at 100% and 200%. If handwriting suffers, raise the long edge to 2000–2200 px (it is a property) and delete `*.view.*` so the sweep rebuilds them. |
| Real devices | One older Android phone in Chrome, one in Facebook's in-app browser, one desktop. Open a question paper, a 20 MB+ script and switch tabs. |
| One week later | Re-run the Stage 0 probe: retry bursts should be near zero, answer-script bytes per request should fall from about 23 MB to about 5 MB, and `$request_time` for PDF GETs should drop. |

## Release

1. **Web release A (Stages 1 + 2).** No API change is needed. The new `load` signature works against the current API: progress falls back to unknown size, because `byte[]` responses already carry `Content-Length`. Ship it outside exam windows with the usual web build swap; roll back by restoring the previous `/opt/coaching-web` directory. This fixes Cause 1 by itself.
2. **API release B (Stage 3, plus 1.4 if approved).** No Flyway migration. Copies start with the first sweep, 10 minutes after startup. Roll back by restoring the previous jar; the `*.view.*` files are ignored by the old jar and can be deleted with `find /var/lib/coaching-api/files -name '*.view.*' -delete`.
3. A and B can be one evening, A first. Record both in `docs/exam-site/reports/client-review-deploy.md` as usual.

Rough effort: Stage 1 half a day, Stage 2 one day, Stage 3 one and a half days, plus verification.

## Decisions needed

- **D1, view-copy resolution.** Recommended: a 1800 px long edge at JPEG quality 0.72, giving about 4–7 MB for a 25-page script. Larger (2200 px, about 7–10 MB) is safer for faint pencil but slower on phones. It can be tuned after release either way, because originals are kept.
- **D2, viewer failure reports (1.4).** Recommended: yes. The endpoint is tiny and anonymous, and it would have shown this problem on day one.
- **D3, release order.** Recommended: web release A first, as soon as possible, since it alone fixes "not showing at all". Then B.
- **D4, quiet window.** Recommended: pause copy-making within ±10 min of written-exam deadlines. Alternatively, run the backfill only at night (Dhaka 01:00–06:00).
