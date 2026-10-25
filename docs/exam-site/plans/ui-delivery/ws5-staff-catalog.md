# WS5 — Staff homes and catalog authoring

Branch `ui/ws5-staff-catalog`, agent E, from the start. Build against the WS0 and WS1 **Contracts** until those merge, then merge `catalog-acquisition` into your branch. When this is merged, agent E moves to WS6a. Read [README.md](README.md) and [ws0-foundation.md](ws0-foundation.md). Pages 5–16, 29 (catalog dialogs) and 30 of [../../ui/admin-prompt.md](../../ui/admin-prompt.md) are the spec, including its "Rules the server enforces" for accounts, catalog, roster and instructor. Differences are listed here.

**Copy:** Bangla quoted in this file shows intent only. Write the final Bangla by [bangla-copy.md](bangla-copy.md) (its glossary and rewrites win, for example লগইন not প্রবেশ, সরাসরি ভর্তি not রোস্টার, শিক্ষক not ইনস্ট্রাক্টর, তারিখ পরে জানানো হবে). Build every link to the other surface with `siteHref()` or `staffHref()` from `lib/config`.

## Owns

- `web/src/app/(staff)/staff/page.tsx` (role homes)
- `web/src/app/(staff)/staff/categories/**`, `.../modules/**` (including the `roster` and `instructor` tabs), `.../exams/[examId]/page.tsx` (the exam editor; `.../exams/[examId]/leaderboard` belongs to WS6)
- `web/src/components/staff/catalog/**`, `web/src/components/staff/home/**`
- `web/src/lib/api/exam/staff-catalog.ts`, `web/src/lib/api/exam/staff-home.ts`
- `web/src/i18n/messages/{bn,en}/staffCatalog.ts`
- Tests under `web/tests/ws5/**`

## Pages

| Route | Who | Spec | API |
|---|---|---|---|
| `/staff` | all staff | Pages 5–7, 30. Add a "দ্রুত কাজ" row for Master Admin and System Admin (নতুন মডিউল, নতুন পরীক্ষা, পেমেন্ট দেখুন, শিক্ষার্থী খুঁজুন) and "আজকের পরীক্ষা" (today's dated exams with their live sitting counts). *(quick actions P1)* Master Admin and System Admin: three cards "নম্বরের অপেক্ষায়", "প্রকাশের অপেক্ষায়" (both from the participation summary, linking to WS6's participation and submissions), "পেমেন্ট অপেক্ষমাণ" (`totalElements` of the orders list with `status=INITIATED`, linking to `/staff/payments?status=INITIATED`), then the next dated exams from the participation list with their counts. Instructor: assigned module cards with their counts, or the empty sentence. Each card links to `/staff/submissions/m/[moduleId]` (WS6a, by URL) and to `/staff/participation?moduleId=` (WS6a); the instructor never opens a catalog editor. | `GET /live-exam-participation/summary`, `GET /live-exam-participation?sort=...`, `GET /live-exam-orders?status=INITIATED&size=1`, `GET /live-exam-modules` |
| `/staff/categories` | MA, SA | Page 8. Tree with indentation and expand and collapse, status chips, search within the loaded tree, "নতুন ক্যাটাগরি". Master Admin row menu: archive and delete; delete with children explains why it failed. | `GET/POST/PATCH/DELETE /live-exam-categories`, `POST .../archive` |
| `/staff/categories/new`, `/staff/categories/[id]` | MA, SA | Page 9. Titles side by side (stacked on phones), parent select (none for root), position, save, the dirty line "সেভ হয়নি" and a leave warning. | same |
| `/staff/modules` | MA, SA, Instructor (assigned only, read) | Page 10. Search, filters category and status, columns name, category, price, status, exam counts (MCQ and written). | `GET /live-exam-modules` |
| `/staff/modules/new`, `/staff/modules/[id]` | MA, SA | Page 11. Tabs: বিবরণ, পরীক্ষা, রোস্টার (MA), ইনস্ট্রাক্টর (MA). Details: titles, descriptions, category, price (MA only; SA sees the amount as text), final save, publish, the "returns to draft" helper, archive and delete (MA) with named confirms. Archived is read-only. | `GET/POST/PATCH/DELETE /live-exam-modules`, `.../final-save`, `.../publish`, `.../archive` |
| `/staff/modules/[id]?tab=exams` | MA, SA | Page 12. Columns name, type, language, date, status, and links to the exam editor, participation and the staff leaderboard. "নতুন পরীক্ষা" asks MCQ or written once. | `GET/POST /live-exam-modules/{id}/exams` |
| `?tab=roster` | MA | Page 13, now with the full member list (WS1 C): table name, phone, email, code, added date, remove (the confirm names the student and warns when `startedAny`). Search above it adds people ("যোগ করুন" on a hit not already listed). Paste box, one email or phone per line, then the result "১২ জন যোগ হয়েছে" and the unmatched lines. Empty: "এই মডিউলে কেউ নেই।" | `GET .../roster` (WS1 C), `GET .../roster/search`, `POST .../roster`, `POST .../roster/paste`, `DELETE .../roster/{studentId}` |
| `?tab=instructor` | MA | Page 14. Empty state with "ইনস্ট্রাক্টর দিন" opening the instructor directory as a picker (search, status and assigned filters). Filled state with name, phone and "সরান" (confirm). Conflict message on a second assign. | `GET /instructors`, `POST/DELETE .../instructor` |
| `/staff/exams/[examId]` (MCQ) | MA, SA | Page 15. Meta: titles, descriptions, rules (side by side), paper language, duration, pass percent, start with the switch "তারিখ এখনো নয়". Questions: stem, options (add up to six, at least two), one correct (radio), marks, explanation; add, edit inline, reorder (up and down buttons, plus drag on desktop), delete with confirm; running total "মোট ৫০". Final save, publish. The five-minute clash names the other exam. After the start the form is read-only and says so. Delete exam is MA only. | `GET/PATCH/DELETE /live-exams/{id}`, `.../final-save`, `.../publish`, `POST/PATCH/DELETE .../questions` |
| `/staff/exams/[examId]` (written) | MA, SA | Page 16. The same meta, maximum marks, and one question PDF: name, "দেখুন" (the shared PDF viewer from WS4, staff may view), and replace by upload. | `POST/GET .../question-pdf` |

## LMS additions

- **Student preview** on both exam editors: "শিক্ষার্থী যেভাবে দেখবে" opens a read-only render of the paper (MCQ: stems and options in the paper typeface, no correct marks; written: the PDF in `PdfViewer` with a sample watermark). Built locally in WS5's components; do not import WS4's paper. *(P1)*
- **Paste many questions** on the MCQ editor: a dialog takes plain text in a documented format (question line, then four option lines, the correct one starting with `*`, an optional `ব্যাখ্যা:` line, a blank line between questions), shows a parsed preview with errors per block, then creates them one by one with progress and stops on the first server error. *(P2)*
- **Checklist before publish** on module and exam editors: a short list of what is still missing (price for a module that should be sold, pass percent, at least one question, the question PDF, final save), each linked to its field. The publish button stays disabled until the list is clear. *(P1)*
- **Next steps after publish**: a toast with "রোস্টারে যোগ করুন" (Master Admin) or "অংশগ্রহণ দেখুন". *(P2)*

## Editor rules

- No autosave (admin prompt). A dirty form warns before leaving. Saving a published record shows "প্রকাশিত থাকলে সেভ করলে আবার ড্রাফট হবে।" next to the button.
- Show the server's validation errors on the fields they name. Map conflict codes (five-minute clash, ended window, category with children, module has participants or purchases) to plain sentences.
- Times are entered in Asia/Dhaka with `DateTimeInput`; the end time is shown, never asked.
- The question editor is a dynamic import so the staff bundle stays small for other pages.
- Bangla and English fields are both always visible; the locale only changes labels and which title the lists show.

## Tests

Home cards per role (MA, SA, Instructor), category tree actions per role, module price input MA only, publish disabled until final save, roster add, paste result and remove confirm, instructor assign and conflict, MCQ editor total and one-correct rule, read-only after start, written PDF replace.

## Report

**Done (agent E, phase 1, round 2).** Branch `ui/ws5-staff-catalog`, merged with `catalog-acquisition` (WS0, WS1, WS4 included). P0 pages all exist with loading, empty, error and success states:

- `/staff`: role homes. Master Admin and System Admin get the three cards (নম্বরের অপেক্ষায় / প্রকাশ বাকি / পেমেন্ট অপেক্ষমাণ), today's exams and the upcoming list. Instructor gets assigned module cards or the empty sentence.
- `/staff/categories`, `/new`, `/[id]`: tree with search and expand/collapse. Archive and delete are Master Admin only. Editor with both titles, parent select, position, dirty line and leave warning.
- `/staff/modules`, `/new`, `/[id]` with `?tab=`: list (search, category, status). Module editor with বিবরণ, পরীক্ষা, সরাসরি ভর্তি (Master Admin, roster list from WS1 C, search-to-add, paste, remove with a named confirm) and শিক্ষক (Master Admin, picker from the directory, remove confirm, conflict sentence). Price input for Master Admin only; System Admin sees the amount as text.
- `/staff/exams/[examId]`: one editor for MCQ and written. Meta form with the start switch "তারিখ এখনো নয়", end time computed and shown. MCQ: question list, running total, add/edit dialog (dynamic import), up/down reorder, delete with confirm, one-correct radio. Written: stored PDF opened in `PdfViewer` (staff view, no download), replace by `FileDrop` with upload progress. After the start instant every form is read-only and says so.
- Publish checklist on both editors, each open item links to its field. Publish stays disabled until it is clear.

**P1 done:** student preview on both exam editors (MCQ without correct marks; written through `PdfViewer` with a watermark), checklist before publish, quick actions on the admin home, today's exams.

**P2 done:** none. **Skipped:** paste many questions (P2), next-steps toast after publish (P2), drag reordering (up and down buttons are used instead).

**Checks.** `npm run lint`: 0 errors, 2 warnings (old pages, `<img>`). `npm test` (single process): 24 files, 183 tests passed, including 17 in `tests/ws5/**`. `npm run build`: passed under the lock.

**Not verified here.** The 1440 px and 375 px, light and dark, keyboard and Bangla read-aloud checks are not done. No browser was used, per the rules. Shahriar's browser pass is still needed.

**Gaps and deviations (for the integrator):**
- Modules list: the server filters only by `categoryId`. Search and status filter the loaded page (up to 100 rows). The API has no `q` or `status` for modules. Exam counts per module are not shown, because the module list has no counts. Contract ask if needed.
- Module's current instructor: `GET /api/v1/instructors?assigned=true` filtered on the client by `modules[].id`, first 100 rows. There is no `moduleId` filter. Fine for demo size; a contract ask if the client has more instructors.
- Category status chip is derived: archived, else published if any module is published, else draft.
- The category and exam forms warn on tab close (`beforeunload`) and on the dirty line; in-app links do not ask first.
- The 5-minute clash message is the generic conflict sentence. The server does not send the other exam's name in a field, so it cannot be named.
- Written PDF size limit is 20 MB on the client; the server limit was not checked.
- Preview of the written paper uses the same question-PDF endpoint as staff view, so it relies on staff being allowed to read it (it is, as far as the brief says).

**Links that other workstreams must answer (by URL only):**
- `/staff/participation?moduleId=` (WS6a) from module exams and the instructor card.
- `/staff/submissions/m/[moduleId]?examId=` (WS6a) for the written awaiting count. The `examId` param name is mine. WS6a should accept it or ignore it.
- `/staff/exams/[examId]/leaderboard` (WS6b) from module exams.
- `/staff/payments?status=INITIATED` (WS6b) from the payment card.
- `/staff/students` (WS6a) from the quick actions.

**Contract issues:** none beyond the gaps above.
