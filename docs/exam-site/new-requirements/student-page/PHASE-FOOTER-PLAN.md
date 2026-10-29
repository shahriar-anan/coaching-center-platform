# Footer: old-site content, new layout, and where it shows

Status: planned 2026-10-29. Not started. Branch `ui/nr01-ui`, worktree `F:\cc-wt\nr01-ui`. Commit only, no push.

## 1. Why

The client wants the exact footer content from the old site (`client-vps`, the live site until cutover) back on the new site. The new site keeps its own design and theme. This phase also decides which pages show a footer, because the current footer shows on every page.

## 2. What the code does today (checked 2026-10-29)

- The footer is part of `StudentShell` (`web/src/components/shell/student-shell.tsx`, footer at about line 339). It has six links (help, about, contact, terms, privacy, refund), a copyright line, and a `nav` with the label `common.footer.label`.
- `StudentShell` is used by:
  - `app/(site)/layout.tsx`: every public and student page (home, browse, exam, sitting, results, notices, account, payments, wish list, legal pages, blog, publications, resources, media, about, contact, help).
  - `app/(auth)/layout.tsx` for the student surface: login, register, set-phone, verify, and similar pages.
  - `components/student/landing/landing-page.tsx` (wide landing page, `/`).
- Only the tab bar hides, and only on exam sitting pages (`hidesTabBar`, `/exam/{id}/sit`). The footer does not hide anywhere.
- The staff and admin panels (`app/(staff)`, `app/(admin)`, `StaffShell`, `AdminShell`, `StaffAuthShell`) have no footer. This plan leaves them without one.
- Legal and info pages that exist on the new site: `/help`, `/about`, `/contact`, `/terms`, `/privacy`, `/refund`, plus `/blog`, `/publications`, `/resources`, `/media`.
- Text keys: `web/src/i18n/messages/en/common.ts` and `bn/common.ts`, under `footer`.

## 3. What the research says (ed-tech and general practice)

- Login and registration pages usually leave out the full footer. One platform's help docs say its footer is left off landing and login/registration pages. Source: [MentorTools footer](https://support.mentortools.com/en/articles/4087043-look-and-feel-footer).
- Where a learner has no custom footer, a minimal fallback shows only cookie and legal links. Source: [AccessLMS footer help](https://intercom.help/accesslmsevo/en/articles/17266055).
- Keep the footer lean; too many links reduce usability. Legal links (privacy, terms, cookies) and contact details are the standard items. Sources: [UXPin footer design best practices](https://www.uxpin.com/studio/blog/footer-design-basics/), [LogRocket footer practices](https://blog.logrocket.com/ux-design/website-footer-design-practices), [National Cancer Institute design system footer](https://designsystem.cancer.gov/components/footer).
- Learning platforms configure the footer per academy (LearnWorlds, ClassOnLive), so the content is a site setting rather than fixed text. Sources: [LearnWorlds footer](https://support.learnworlds.com/support/solutions/articles/12000025461-how-to-edit-your-school-s-footer), [ClassOnLive footer](https://ayuda.classonlive.com/en/articles/4122705-customize-the-top-menu-header-and-the-bottom-menu-footer-of-your-academy).
- No source we found says whether a timed exam screen should show a footer. The usual reasoning (fewer exits during a timed test) is our inference, not a sourced rule.

Summary of the research: legal links and contact on the main pages; a minimal footer on auth pages; no full footer on the timed sitting screen.

## 4. Decisions (recommended defaults; the owner can change any of them)

| Decision | Default in this plan | Why |
|---|---|---|
| Full footer (all content) | Every page under `(site)` except the sitting page | Client wants the content back; the public site needs it |
| Minimal footer | Login, register, set-phone, verify, and other auth pages: copyright and legal links only | Fewer exits from sign-up; matches the research |
| No footer | Exam sitting page `/exam/{id}/sit` and its written upload view; the exam start confirmation if it is a separate route | A timed sitting must not offer exits or extra links |
| Result, submitted and preview pages | Full footer | They are not timed |
| Staff and admin panels | No footer (unchanged) | Internal tools |
| Landing page `/` | Full footer, wide layout | It is the public home |
| Content source | Hard-coded copy from the old site, in the message catalogs (not a database setting) | The client asked for the exact content now; a setting can come later |

## 5. Old-site analysis (Phase A, read-only)

Rules (strict):
- The old site is the client's live site until cutover. Read only. No writes, deletes, edits, moves, service restarts, package installs, artisan, npm or composer commands, migrations, or cache clears.
- Database: SELECT only, and only if the footer text is not in the files.
- SSH: Windows OpenSSH from PowerShell, alias `client-vps`. Git Bash ssh is refused. Pipe scripts with `Get-Content -Raw script.sh | ssh client-vps "tr -d '\r' | sed '1s/^\xEF\xBB\xBF//' | bash -s"`.
- Do not print secrets from any `.env` (database, mail, keys). Copy only public text: names, addresses, phone numbers, email, links, copyright text.
- Keep remote commands small: grep with patterns, `sed -n` on specific files, `ls`. Do not dump folders.
- Save all captured text as UTF-8 in the scratchpad (`C:\Users\HP\AppData\Local\Temp\claude\f--coaching-center-platform\03c29828-ddfb-44ca-a9c7-5e49ff5d59c3\scratchpad\`). Check the Bangla by reading the saved file back; if it shows question marks, change the capture method and redo it.

Where to look on the old box:
- Laravel: `/var/www/html/symamlaw-backend` (views under `resources/views`, layouts, partials; the database name is in its `.env` as `DB_DATABASE`).
- Next.js apps: `/root/symamlaw`.
- Known from the owner's notes: four legal and info pages exist on the old site.

Output per footer item: EN text, BN text, the link target, the source file and line, and where the value comes from (hard-coded, a settings row, or a config value). Anything not on the old site is recorded as "not on old site". Nothing is invented.

Layout output: sections (brand block, short about text, link columns, contact block, social icons, copyright bar), desktop column count, phone stacking, and any icons. Colours are for reference only.

Phase A writes `footer-old-site.md` in the scratchpad and reports the table and layout summary. Phase B does not start until the table is in the report.

## 6. Footer build (Phase B)

- Structure, desktop (lg and up): a grid of columns for the old site's groups (brand, links, contact), inside the current frame. Phone: stacked, one column, the copyright line last.
- Theme: current tokens only (`border-border`, `bg-card`, `text-muted`, `text-meta`, and the existing spacing and type scale). No old-site colours or fonts.
- Links: only to pages that exist on the new site. A link to a missing page is not added; it is listed in the report.
- Copy: the old site's exact text in `common.ts` under `footer` (EN and BN with the same keys). Where the old site has English only, the BN key keeps the draft and is listed.
- Page rules from section 4: the footer component takes a variant (`full`, `minimal`, `none`) chosen by the route. The sitting route and auth routes get `minimal` or `none` as decided above. The variant is decided in one place (a function of the pathname next to `hidesTabBar`).
- Accessibility: the footer stays a `<footer>` with a labelled `<nav>`; links have visible focus; no link is only an icon.
- Layout checks: 320, 375, 768 and 1280 px, text report only (no screenshots), no horizontal scroll, no clipped text.

## 7. Tests

- Footer renders the full content in English and Bangla (full variant).
- Minimal variant on auth routes: only copyright and legal links.
- No footer on `/exam/{id}/sit`.
- Staff and admin routes have no footer.
- Links to missing pages are not rendered.
- Keys exist in both `en` and `bn` (existing parity test).

## 8. Checks and commits

- Lint touched files (exit 0). Targeted vitest for the tests above; report counts.
- One `npm run build` at the end with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8092` on the command line, under Git Bash, taking and releasing the heavy lock `F:\coaching-center-platform\.heavy.lock`.
- Commit: Phase A has no repo change. Phase B commits: the copy and keys; the variant rule and the page wiring; the tests. Each commit message ends with `Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>`.
- Do not start or stop servers. The owner's servers are stopped; the coordinator starts them for checking.

## 9. Open questions for the owner

1. Confirm the page matrix in section 4, especially: no footer on the sitting page, and minimal footer on login and register.
2. Should the footer also show on the staff login page (`StaffAuthShell` currently has none)? Default: no.
3. Bangla for the old site's English-only items: keep as draft, or leave the English? Default: keep the draft and list the items.
