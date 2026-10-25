# WS0 — Foundation

Branch `ui/ws0-foundation`. Starts first. WS2–WS6 cut their branches from `catalog-acquisition` after this merges, so finish fast and keep the contracts stable. Read [README.md](README.md) first.

## Goal

Give every page workstream the same tokens, shells, components, data helpers, formatting and auth routing, so pages only compose. Nothing here is a feature page.

## Owns

- `web/package.json`, `web/package-lock.json` (the dependencies listed in README)
- `web/src/app/layout.tsx`, `web/src/app/providers.tsx`, `web/src/app/globals.css`; delete `web/src/app/page.tsx` (the redirect). The home is `web/src/app/(site)/page.tsx`, which WS0 creates as a one-line placeholder and WS3 owns from then on.
- Stubs that other workstreams replace (WS0 creates them with the interface below, then ownership moves):
  - `web/src/components/student/notice-bell.tsx` → WS2. `export function NoticeBell(): JSX.Element | null`, rendered by `StudentShell` for signed-in students. The stub returns `null`.
  - `web/src/components/pdf/pdf-viewer.tsx` → WS4. `export function PdfViewer(props: { load: () => Promise<Blob>; watermark?: string; allowDownload?: boolean; fileName?: string }): JSX.Element`, default-exported through `next/dynamic` with a skeleton. The stub renders a "PDF" placeholder. WS5 and WS6 import it from the start.
- `web/src/app/(site)/layout.tsx`, `web/src/app/(auth)/layout.tsx`, `web/src/app/(staff)/layout.tsx`, `web/src/app/not-found.tsx`, `web/src/app/error.tsx`
- `web/src/components/ui/**` (shared components)
- `web/src/components/shell/**` (`StudentShell`, `StaffShell`, `Logo`, `LocalePill`, `ThemePill`, `NoticeBell` slot)
- `web/src/lib/format/**`, `web/src/lib/time/**`, `web/src/lib/url/**`
- `web/src/lib/api/query-client.ts`, `web/src/lib/api/exam/types.ts`, `web/src/lib/api/exam/keys.ts`
- `web/src/lib/auth/session-context.tsx`, `web/src/lib/auth/guards.tsx`, `web/src/components/require-auth.tsx`
- `web/src/i18n/locale-context.tsx`, `web/src/i18n/messages/index.ts`, `web/src/i18n/messages/{bn,en}/common.ts`, and an empty stub per namespace below
- `web/src/components/admin-shell.tsx` (nav only: hide course, book, book-order and course-payment items, per U2)
- Delete `web/src/app/sandbox/`, `web/src/app/live-exams/`, and their tests

## Tasks

### 1. Tokens and fonts

`globals.css` defines CSS variables for two themes on two scopes and maps them into Tailwind 4 with `@theme`. No hard-coded colors in components.

Student scope (`[data-surface="site"]`):

| Token | Light | Dark |
|---|---|---|
| `--bg` | `#f6f4ee` | `#0e1424` |
| `--card` | `#ffffff` | `#172038` |
| `--text` | `#14213d` | `#eef0f6` |
| `--muted` | `#5d6578` | `#a3abc0` |
| `--accent` (gold) | `#c8962e` | `#e0b254` |
| `--on-accent` | `#14213d` | `#14213d` |
| `--border` | `#e3dfd3` | `#26304d` |
| `--warn` (pending orange) | `#c2410c` (text), `#e8590c` (fill) | same fill |
| `--ok` | `#2f9e44` | `#40c057` |
| `--danger` | `#b42318` | `#f97066` |

Staff scope (`[data-surface="staff"]`): `--bg #f4f6f8`, `--card #ffffff`, `--text #1c2430`, `--muted #5c6b7a`, `--border #d8e0e8`, `--action #1f4e79`, `--on-action #ffffff`, `--danger #9b2c2c`, `--ok #1f7a4d`, `--warn #9a6700`. Dark: `--bg #12181f`, `--card #1c2631`, `--text #e7edf2`, `--muted #9aabba`, `--border #2c3b4a`, `--action #7eb0d8`, `--on-action #12181f`.

Theme: `data-theme="light|dark"` on `<html>`, default from `prefers-color-scheme`, toggle stored in `localStorage`. An inline script in `layout.tsx` sets it before paint, so there is no flash. Wrap storage access in `try`.

Fonts with `next/font/google`: Hind Siliguri (400, 500, 600, 700; Bengali and Latin subsets) as the student face; Noto Sans Bengali (400, 700) for Bangla question bodies and as the staff Bangla face; Inter (400, 500, 600) for staff Latin text and English paper bodies. Expose them as CSS variables. Check the font docs in `node_modules/next/dist/docs/` first.

Radius 16 px for student cards, 12 px for student buttons, 8 px for staff. Spacing on Tailwind's 4 px scale. Motion: 150–250 ms, and none under `prefers-reduced-motion`.

### 2. Shared components (`components/ui/`)

Each is small, typed, themed by tokens, keyboard-usable, and has one test. Variants come from the surface (site or staff), not separate components.

`Button` (primary, secondary, ghost, danger-text; `loading`; full width on site), `IconButton` (needs `label`), `Link` wrapper, `Card` (static and as a link with chevron), `Field` (label, hint, error, required) with `TextInput`, `PasswordInput` (show and hide), `Textarea`, `Select`, `Checkbox`, `Switch`, `NumberInput`, `DateTimeInput` (Asia/Dhaka in, ISO UTC out), `FileDrop` (accept list, size limit, PDF magic-byte check, replace), `Pill` (tone and text, optional pulse), `Badge`, `Stat` and `StatRow` (the mockup's three-cell result row), `Table` (sticky header; under 640 px renders rows as stacked cards from the same column definitions), `Pagination` (server pages), `SearchInput` (300 ms), `Tabs`, `Dialog`, `ConfirmDialog` (names the object; optional "type the name" check), `Sheet` (bottom sheet on phones, dialog on desktop), `Toast` (polite live region), `Skeleton` (text, card, table-rows), `EmptyState`, `ErrorState` (retry, keeps last content), `PageHeader` (title, breadcrumbs, actions), `Breadcrumbs`, `Countdown` (from a server deadline plus clock offset; announces each minute only), `CopyField`, `OneTimeCodeDialog` (shows an activation code once with Copy and the "will not be shown again" line).

### 3. Shells

`StudentShell` (site surface): 760 px column; header with `Logo`, then on the right the `NoticeBell` (signed-in students only; WS2 supplies it through a slot), `LocalePill` (shows `EN` in Bangla), `ThemePill`, and either the student's first name (opens a small menu: হোম, অ্যাকাউন্ট, নোটিশ, বেরিয়ে যান) or "প্রবেশ". Footer with links to আমাদের সম্পর্কে, যোগাযোগ, শর্তাবলি, গোপনীয়তা, ফেরত নীতি, and "© এক্সাম পোর্টাল". Page transitions fade up 6 px.

`StaffShell` (staff surface): left sidebar with `Logo`, role chip (মাস্টার অ্যাডমিন, সিস্টেম অ্যাডমিন, ইনস্ট্রাক্টর) and the nav for that role; top bar with page title, breadcrumbs, `LocalePill`, `ThemePill`, the person's name, and logout. Under 768 px the sidebar is a drawer behind a menu button.

Staff navigation (one config file in `components/shell/staff-nav.ts`; pages link to routes their workstream builds):

| Item | Route | Master Admin | System Admin | Instructor |
|---|---|---|---|---|
| ড্যাশবোর্ড | `/staff` | yes | yes | yes |
| ক্যাটাগরি | `/staff/categories` | yes | yes | |
| মডিউল | `/staff/modules` | yes | yes | yes (assigned only) |
| অংশগ্রহণ | `/staff/participation` | yes | yes | yes |
| খাতা মূল্যায়ন | `/staff/submissions` | yes | yes (read only) | yes |
| শিক্ষার্থী | `/staff/students` | yes | yes | |
| ইনস্ট্রাক্টর | `/staff/instructors` | yes | | |
| সিস্টেম অ্যাডমিন | `/staff/admins` | yes | | |
| পেমেন্ট | `/staff/payments` | yes | yes (read only) | |
| অডিট লগ | `/staff/audit` | yes | | |
| অ্যাকাউন্ট | `/staff/account` | yes | yes | yes |

The menu badges on খাতা মূল্যায়ন come from `GET /api/v1/live-exam-participation/summary`: the instructor sees papers awaiting a mark, the Master Admin drafts awaiting publish.

### 4. Auth routing

Change `session-context.tsx` and add `lib/auth/guards.tsx`:

- After login: a student goes to `?next=` when it is a same-site student path, else `/`. Staff go to `/staff`. Remove the old redirects to `/purchase/result`, `/account` and `/students`.
- `RequireStudent` (sitting, notices, account, payment history, purchase result): signed-out users go to `/login?next=<current>`. Staff see "এই অংশ শিক্ষার্থীদের জন্য" and a link to `/staff`.
- `RequireStaff roles=[...]`: signed-out users go to `/login?next=`. A student sees "এই অংশ কর্মীদের জন্য" and a link home. A staff role not in `roles` sees a not-found page.
- `useRole()` returns the role and helpers (`isMasterAdmin`, `canEditPrice`, `canPublishResult`, `canSeePayments`, ...) from one table, so pages never compare role strings.
- An expired session returns to `/login?next=` with "আবার প্রবেশ করুন".
- Logout calls `POST /api/v1/auth/logout`, clears the query cache, and goes to `/`.

### 5. Data layer

`providers.tsx` adds the `QueryClientProvider` with the defaults in README. `lib/api/exam/types.ts` declares the shared response types used by more than one workstream (category tree, module, exam, `myStatus`, order status, `PageResponse<T>`, me). Each page workstream owns its own hooks file (`lib/api/exam/<area>.ts`) and adds its keys to `keys.ts` only through its own exported object in that file: WS0 creates `keys.ts` with one empty object per area so nobody edits another's keys.

`lib/time/clock.ts` records the offset between the API's `Date` header and the device clock on every response, and exposes `serverNow()`.

`lib/url/list-params.ts`: `useListParams({ page, size, q, ...filters })` reads and writes the URL query.

### 6. Formatting

`lib/format/` with `formatNumber`, `formatMoney` (`৳ ২,৫০০.০০`, `৳ 2,500.00`), `formatDate` (`১২ নভেম্বর ২০২৬`), `formatTime` (`দুপুর ১২:০০`, using Bangla day periods ভোর, সকাল, দুপুর, বিকেল, সন্ধ্যা, রাত), `formatDateTime`, `formatDuration` (`৪০ মিনিট`), `formatRelative` for the countdown, `pickTitle(record, locale)`. All in Asia/Dhaka. Unit tests for each, both locales, including midnight and the day-period edges.

### 7. Messages

Split the catalogs: `messages/{bn,en}/<namespace>.ts` merged by `messages/index.ts`. Keep the existing keys working (move them into `legacy.ts`). Namespaces and owners: `common` (WS0), `auth`, `account`, `notices`, `legal` (WS2), `catalog`, `purchase`, `home` (WS3), `sitting`, `result` (WS4), `staffCatalog` (WS5), `staffPeople`, `staffOps` (WS6). WS0 creates each file as an empty object.

### 8. Old admin and stubs

`admin-shell.tsx`: keep only items that are not courses, books, book orders or course payments. Delete `app/page.tsx` (the redirect) and create the placeholder `app/(site)/page.tsx`. Delete `/sandbox`, `/live-exams` and their tests.

## Acceptance

- `npm run lint`, `npm test`, `npm run build` pass.
- A throwaway `app/(site)/_preview` and `app/(staff)/staff/_preview` page shows every component in both themes and both languages. Delete these before merge, or keep them behind a dev-only check.
- Login as a student lands on `/`, staff on `/staff`. A student opening `/staff` sees the staff-only message.

## Report

(Agent fills this in.)
