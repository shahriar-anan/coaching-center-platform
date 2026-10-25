# WS0 — Foundation

Branch `ui/ws0-foundation`, agent A. Starts with everyone else. WS3, WS4 and WS5 build against the **Contracts** below until this merges, then merge `catalog-acquisition` into their branches. Publish the contracts as code first, then fill in the details, and keep the contracts stable. When this is merged, agent A moves to WS2. Read [README.md](README.md) first.

Add every dependency in the README list in your first commit (including `pdfjs-dist` for WS4), so nobody else touches `package.json`.

## Goal

Give every page workstream the same tokens, shells, components, data helpers, formatting and auth routing, so pages only compose. Nothing here is a feature page.

## Owns

- `web/package.json`, `web/package-lock.json` (the dependencies listed in README)
- `web/src/app/layout.tsx`, `web/src/app/providers.tsx`, `web/src/app/globals.css`; delete `web/src/app/page.tsx` (the redirect). The home is `web/src/app/(site)/page.tsx`, which WS0 creates as a one-line placeholder and WS3 owns from then on.
- Stubs that other workstreams replace (WS0 creates them with the interface below, then ownership moves):
  - `web/src/components/student/notice-bell.tsx` → WS2. `export function NoticeBell(): JSX.Element | null`, rendered by `StudentShell` for signed-in students. The stub returns `null`.
  - `web/src/components/pdf/pdf-viewer.tsx` (WS0 keeps this file): the `next/dynamic` wrapper exporting `PdfViewer(props: { load: () => Promise<Blob>; watermark?: string; allowDownload?: boolean; fileName?: string })` with a skeleton. Everyone imports `PdfViewer` from `@/components/pdf/pdf-viewer`. The viewer body lives in `web/src/components/pdf/pdf-viewer-impl.tsx` → **WS4 replaces that file** (and may add sibling files in `components/pdf/`), keeping the props. As built by WS0 on 25 October.
- `web/src/app/(site)/layout.tsx`, `web/src/app/(auth)/layout.tsx`, `web/src/app/(staff)/layout.tsx`, `web/src/app/not-found.tsx`, `web/src/app/error.tsx`
- `web/src/components/ui/**` (shared components)
- `web/src/components/shell/**` (`StudentShell`, `StaffShell`, `Logo`, `LocalePill`, `ThemePill`, `NoticeBell` slot)
- `web/src/lib/format/**`, `web/src/lib/time/**`, `web/src/lib/url/**`
- `web/tests/helpers/**` (test helpers and fixtures)
- `web/src/lib/api/client.ts` (add `apiUpload` and `apiBlob`; keep `apiRequest` unchanged)
- `web/src/lib/api/query-client.ts`, `web/src/lib/api/exam/types.ts`
- `web/src/lib/auth/session-context.tsx`, `web/src/lib/auth/guards.tsx`, `web/src/components/require-auth.tsx`
- `web/src/i18n/locale-context.tsx`, `web/src/i18n/messages/index.ts`, `web/src/i18n/messages/{bn,en}/common.ts`, and an empty stub per namespace below
- `web/src/components/admin-shell.tsx` (nav only: hide course, book, book-order and course-payment items, per U2)
- `web/src/proxy.ts` (remove the `/` redirect; add host routing, section 9)
- `web/src/lib/config.ts`, `web/.env.example` (section 9)
- Delete `web/src/app/sandbox/`, `web/src/app/live-exams/`, and their tests

## Contracts (frozen at the start)

WS3, WS4 and WS5 build against this list before WS0 merges. Implement exactly these paths, names and props. Adding optional props is fine; renaming or removing is a contract change: tell the integrator first.

**Tailwind token classes** (from `@theme`, both surfaces): `bg-bg`, `bg-card`, `text-text`, `text-muted`, `border-border`, `bg-accent`, `text-accent`, `text-on-accent`, `bg-action`, `text-on-action`, `text-ok`, `bg-ok`, `text-warn`, `bg-warn`, `text-danger`, `font-site` (Hind Siliguri), `font-bn` (Noto Sans Bengali), `font-latin` (Inter). Radii `rounded-card` (16 px site, 8 px staff), `rounded-control` (12 px site, 8 px staff).

**Components** (`@/components/ui/<file>`; one component per file, named export):

| Export | Key props |
|---|---|
| `Button` | `variant: "primary" \| "secondary" \| "ghost" \| "danger"`, `loading?`, `fullWidth?`, `href?` (renders a link), native button props |
| `IconButton` | `label` (required), `icon`, `onClick` |
| `Card` | `href?` (link card with chevron), `as?`, `className?`, `padding?: "md" \| "lg"` |
| `Field` | `label`, `hint?`, `error?`, `required?`, `children` (one input) |
| `TextInput`, `PasswordInput`, `Textarea`, `NumberInput`, `Select`, `Checkbox`, `Switch` | native props plus `invalid?` |
| `DateTimeInput` | `value: string \| null` (ISO UTC), `onChange(iso \| null)`, shows Asia/Dhaka |
| `FileDrop` | `accept: "pdf"`, `maxBytes`, `file?: { name; size; uploadedAt? }`, `onFile(file)`, `uploading?`, `progress?` (0–100), `error?` |
| `Pill` | `tone: "muted" \| "accent" \| "warn" \| "ok" \| "danger"`, `pulse?`, `children` (words) |
| `Badge` | `count` |
| `StatRow`, `Stat` | `Stat({ value, label })` inside `StatRow` |
| `Table<T>` | `columns: { key; header; cell(row) ; mobileLabel?; align? }[]`, `rows: T[]`, `rowKey(row)`, `onRowClick?`, `loading?`, `empty?: ReactNode` (cards under 640 px) |
| `Pagination` | `page`, `totalPages`, `onPage(page)` |
| `SearchInput` | `value`, `onChange` (already debounced 300 ms), `placeholder` |
| `Tabs` | `tabs: { value; label; badge? }[]`, `value`, `onChange` |
| `Dialog`, `ConfirmDialog` | `open`, `onOpenChange`; confirm adds `title`, `body`, `confirmLabel`, `tone?: "danger"`, `typeToConfirm?: string`, `onConfirm(): Promise<void>` |
| `Sheet` | `open`, `onOpenChange`, `title`, `children` (bottom sheet on phones) |
| `useToast()` | `toast({ title, tone?: "ok" \| "danger" \| "info" })` |
| `Skeleton` | `variant: "text" \| "card" \| "rows"`, `count?` |
| `EmptyState` | `title`, `body?`, `action?: ReactNode`, `icon?` |
| `ErrorState` | `onRetry`, `message?` |
| `PageHeader` | `title`, `breadcrumbs?: { label; href? }[]`, `actions?` |
| `Countdown` | `deadline: string` (ISO), `onEnd?()`, `variant?: "inline" \| "bar"`, `warnAt?: number[]` (minutes) |
| `CopyField` | `value`, `label` |
| `OneTimeCodeDialog` | `open`, `code`, `personName`, `onClose` |
| `OfflineBanner` | none (shows when `navigator.onLine` is false) |

**Shell** (`@/components/shell/…`): `StudentShell` and `StaffShell` are applied by the route-group layouts; pages never render a shell. Pages render `PageHeader` and content only. `Logo` (one place to swap in a logo file later).

**Hooks and helpers:**

- `@/lib/auth/session-context`: `useSession()` → `{ session: { status: "loading" \| "anonymous" \| "authenticated"; user? }, login, logout, refreshMe }`; `useUser()`.
- `@/lib/auth/guards`: `RequireStudent`, `RequireStaff({ roles })`, `useRole()` → `{ role, isStudent, isMasterAdmin, isSystemAdmin, isInstructor, canEditPrice, canDelete, canManageRoster, canAssignInstructor, canEvaluate, canPublishResult, canSeePayments, canSeeAudit, canManageStaff }`.
- `@/lib/config`: `apiBaseUrl`, `apiUrl(path)`, `siteUrl`, `staffUrl`, `splitHosts`, `siteHref(path)`, `staffHref(path)`, `studentHome`, `staffHome`, `studentLogin`, `staffLogin`, `googleEnabled` (section 9). Every link or redirect between the student site and the admin panel goes through these.
- `@/lib/api/client`: `apiRequest<T>(path, { method?, body?, signal? })` (existing), plus `apiUpload<T>(path, formData, { onProgress? })` (XHR with the token and refresh) and `apiBlob(path)` (authorized file as a `Blob`).
- `@/lib/api/query-client`: the configured `QueryClient`; use `useQuery` and `useMutation` from `@tanstack/react-query` directly.
- `@/lib/api/exam/types`: `PageResponse<T>`, `Me`, `CategoryNode`, `PublicModule`, `PublicExam`, `StudentExam`, `MyStatus` (the nine statuses), `ExamType`, `PaperLanguage`, `OrderStatus`, `ApiErrorCode`.
- `@/lib/format`: `formatNumber`, `formatMoney`, `formatDate`, `formatTime`, `formatDateTime`, `formatDuration`, `formatRelative`, `pickTitle`, `pickText(record, field)`, all taking the locale from `useLocale()` or an explicit argument.
- `@/lib/time/clock`: `serverNow(): number`, `useServerNow(intervalMs)`.
- `@/lib/url/list-params`: `useListParams(defaults)` → `[params, setParams]`.
- `@/i18n/locale-context`: `useLocale()` → `{ locale, setLocale, t(key, params?) }`; keys are `namespace.key`.

**Test helpers** (`web/tests/helpers/`): `renderWithProviders(ui, { user?, locale?, route? })` (session, locale, query client, router mock), `mockApi({ "GET /api/v1/...": body | (req) => body, ... })` over `fetch`, and fixtures `fixtures.student`, `fixtures.masterAdmin`, `fixtures.systemAdmin`, `fixtures.instructor`, `fixtures.categoryTree`, `fixtures.module`, `fixtures.mcqExam`, `fixtures.writtenExam`.

Publish this section's code first (stubs are fine), commit it, and tell the integrator, so the phase-1 page agents can merge it early if they want.

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

`StudentShell` (site surface): 760 px column; header with `Logo`, then on the right the `NoticeBell` (signed-in students only; WS2 supplies it through a slot), `LocalePill` (shows `EN` in Bangla), `ThemePill`, and either the student's first name (opens a small menu: হোম, আমার অ্যাকাউন্ট, নোটিশ, লগআউট) or "লগইন". Footer with links to সাহায্য ও প্রশ্নোত্তর, আমাদের সম্পর্কে, যোগাযোগ, শর্তাবলি, গোপনীয়তা নীতি, রিফান্ড নীতি, and "© এক্সাম পোর্টাল". All copy follows [bangla-copy.md](bangla-copy.md). Page transitions fade up 6 px.

On phones (under 768 px), a signed-in student also gets a fixed **bottom tab bar** (LMS standard): হোম `/`, আমার পরীক্ষা `/my-modules`, রেজাল্ট `/results`, নোটিশ `/notices` (with the unread badge from `NoticeBell`'s query), অ্যাকাউন্ট `/account`. 56 px tall, icons with labels, active tab in gold, safe-area padding, hidden on `/exam/[id]/sit` so it never covers the paper. Page content gets bottom padding equal to the bar. On desktop the same items sit in the header menu.

`StaffShell` (staff surface): left sidebar with `Logo`, role chip (মাস্টার অ্যাডমিন, সিস্টেম অ্যাডমিন, শিক্ষক) and the nav for that role; top bar with page title, breadcrumbs, `LocalePill`, `ThemePill`, the person's name, and logout. Under 768 px the sidebar is a drawer behind a menu button.

Staff navigation (one config file in `components/shell/staff-nav.ts`; pages link to routes their workstream builds):

| Item | Route | Master Admin | System Admin | Instructor |
|---|---|---|---|---|
| ড্যাশবোর্ড | `/staff` | yes | yes | yes |
| ক্যাটাগরি | `/staff/categories` | yes | yes | |
| মডিউল | `/staff/modules` | yes | yes | yes (assigned only) |
| অংশগ্রহণ | `/staff/participation` | yes | yes | yes |
| খাতা মূল্যায়ন | `/staff/submissions` | yes | yes (read only) | yes |
| শিক্ষার্থী | `/staff/students` | yes | yes | |
| শিক্ষক | `/staff/instructors` | yes | | |
| সিস্টেম অ্যাডমিন | `/staff/admins` | yes | | |
| পেমেন্ট | `/staff/payments` | yes | yes (read only) | |
| কার্যক্রম লগ | `/staff/audit` | yes | | |
| আমার অ্যাকাউন্ট | `/staff/account` | yes | yes | yes |

The menu badges on খাতা মূল্যায়ন come from `GET /api/v1/live-exam-participation/summary`: the instructor sees papers awaiting a mark, the Master Admin drafts awaiting publish.

### 4. Auth routing

Change `session-context.tsx` and add `lib/auth/guards.tsx`:

- After login: a student goes to `?next=` when it is a same-site student path, else the student home. Staff go to the staff home. Both destinations come from `lib/config` (section 9), never a hard-coded path or host. Remove the old redirects to `/purchase/result`, `/account` and `/students`.
- `RequireStudent` (sitting, notices, account, payment history, purchase result): signed-out users go to `/login?next=<current>`. Staff see "এই পাতাটি শুধু শিক্ষার্থীদের জন্য" and a link to the staff home.
- `RequireStaff roles=[...]`: signed-out users go to `/login?next=`. A student sees "এই পাতাটি শুধু অ্যাডমিন ও শিক্ষকদের জন্য" and a link to the student home. A staff role not in `roles` sees a not-found page.
- `useRole()` returns the role and helpers (`isMasterAdmin`, `canEditPrice`, `canPublishResult`, `canSeePayments`, ...) from one table, so pages never compare role strings.
- An expired session returns to `/login?next=` with "সেশন শেষ হয়েছে। আবার লগইন করুন।"
- Logout calls `POST /api/v1/auth/logout`, clears the query cache, and goes to `/`.

### 5. Data layer

`providers.tsx` adds the `QueryClientProvider` with the defaults in README. `lib/api/exam/types.ts` declares the shared response types used by more than one workstream (category tree, module, exam, `myStatus`, order status, `PageResponse<T>`, me). Each page workstream owns its own hooks file (`lib/api/exam/<area>.ts`) and declares its query keys inside it, prefixed with the area (`["catalog", ...]`, `["sitting", ...]`). There is no shared keys file. To invalidate another area's cache after a mutation (for example a payment making the catalog stale), call `queryClient.invalidateQueries({ queryKey: ["catalog"] })` by prefix; never import another area's file.

`lib/time/clock.ts` records the offset between the API's `Date` header and the device clock on every response, and exposes `serverNow()`.

`lib/url/list-params.ts`: `useListParams({ page, size, q, ...filters })` reads and writes the URL query.

### 6. Formatting

`lib/format/` with `formatNumber`, `formatMoney` (`৳ ২,৫০০.০০`, `৳ 2,500.00`), `formatDate` (`১২ নভেম্বর ২০২৬`), `formatTime` (`দুপুর ১২:০০`, using Bangla day periods ভোর, সকাল, দুপুর, বিকেল, সন্ধ্যা, রাত), `formatDateTime`, `formatDuration` (`৪০ মিনিট`), `formatRelative` for the countdown, `pickTitle(record, locale)`. All in Asia/Dhaka. Unit tests for each, both locales, including midnight and the day-period edges.

### 7. Messages

Split the catalogs: `messages/{bn,en}/<namespace>.ts` merged by `messages/index.ts`. Keep the existing keys working (move them into `legacy.ts`). Namespaces and owners: `common` (WS0), `auth`, `account`, `notices`, `legal` (WS2), `catalog`, `purchase`, `home` (WS3), `sitting`, `result` (WS4), `staffCatalog` (WS5), `staffPeople` (WS6a), `staffOps` (WS6b). WS0 creates each file in both languages as an empty object and imports all of them in `messages/index.ts`, so no other workstream ever edits `index.ts`. A missing key renders the key itself in development so gaps are visible.

### 8. Old admin and stubs

`admin-shell.tsx`: keep only items that are not courses, books, book orders or course payments. Delete `app/page.tsx` (the redirect) and create the placeholder `app/(site)/page.tsx`. **Also remove the `/` → `/students` redirect in `web/src/proxy.ts`** (the Next 16 middleware); otherwise the new home never renders. Keep the file only if something else needs it. Delete `/sandbox`, `/live-exams` and their tests.

### 9. Environments, URLs, and the two sites

At launch the student site and the admin panel will have **different URLs** (for example `https://exam.example.com` for students and `https://admin.exam.example.com` for staff; the names are not decided). Tonight on staging they share one host, `https://srv2089893.hstgr.cloud`, with staff under `/staff`. The same code must work in both setups, switched only by environment values. Agents must never hard-code a host, and must build cross-site links only through `lib/config`.

**Environment values (web):**

| Name | Read | Staging tonight | Later (two hosts) | Meaning |
|---|---|---|---|---|
| `NEXT_PUBLIC_SITE_URL` | build time | `https://srv2089893.hstgr.cloud` | `https://exam.example.com` | Student site origin |
| `NEXT_PUBLIC_STAFF_URL` | build time | `https://srv2089893.hstgr.cloud` | `https://admin.exam.example.com` | Admin panel origin. Equal to the site URL means one host. |
| `NEXT_PUBLIC_API_BASE_URL` | build time | empty | empty | Empty means same origin: the browser calls `/api/...` on whichever host it is on, and that host's Nginx forwards `/api/` to the API. Set it only for local development (`http://localhost:8080`). |
| `NEXT_PUBLIC_GOOGLE_ENABLED` | build time | `false` | `true` once configured | Turns the disabled Google button into a working one (U10) without code changes. |

Write them in `web/.env.example` with comments, and keep `web/.env.local` for development only (`http://localhost:8080` and `http://localhost:3000`). Check in `node_modules/next/dist/docs/` whether `proxy.ts` can read plain runtime variables in a standalone server; if it can, also accept `SITE_URL` and `STAFF_URL` at runtime so one build can move between hosts. If not, the build is made per environment, and WS7 documents that.

**`lib/config.ts` contract** (replaces today's single `apiBaseUrl` export, and keeps it):

- `apiBaseUrl` (`""` means same origin), `apiUrl(path)`.
- `siteUrl`, `staffUrl`, `splitHosts` (true when the two origins differ).
- `siteHref(path)`: a relative path when the visitor is already on the student site or the hosts are not split; otherwise the absolute student URL.
- `staffHref(path)`: the same for the admin panel. Staff routes always start with `/staff`, on both setups.
- `studentHome` = `siteHref("/")`, `staffHome` = `staffHref("/staff")`, `staffLogin` = `staffHref("/login")`, `studentLogin` = `siteHref("/login")`.

Rules for every workstream: links inside one surface use `next/link` with relative paths. Any link or redirect from the student site to the admin panel, or back, uses `siteHref` or `staffHref`: post-login routing, the "শুধু শিক্ষার্থীদের জন্য" and "শুধু অ্যাডমিন ও শিক্ষকদের জন্য" pages, "অ্যাডমিন প্যানেল" links for signed-in staff, and staff links to a student's public exam page.

**Host routing in `proxy.ts`** (only when `splitHosts`):

- On the admin host: `/` redirects to `/staff`. `/staff/**`, the auth pages (`/login`, `/forgot-password`, `/reset-password`, `/activate`), `/_next/**` and static files are served. Any student route redirects to the same path on the student site.
- On the student host: `/staff/**` redirects to the same path on the admin host.
- With one host, no host routing: both surfaces live side by side.
- The staff layout always sends `<meta name="robots" content="noindex, nofollow">`; the student site stays indexable.

**Sessions across two hosts.** The refresh cookie belongs to the host the user signed in on, so a student session and a staff session never mix. With split hosts, each login page accepts its own audience: on the student site a staff account sees "অ্যাডমিন প্যানেলে লগইন করুন" with a link to `staffLogin` instead of being signed in; on the admin host a student sees "শিক্ষার্থীদের সাইটে লগইন করুন" with a link to `studentLogin`. With one host, the login page accepts everyone and routes by role.

**What the API and server need** (WS7 sets them at deploy; listed here so the foundation matches):

- `WEB_ORIGIN` lists both origins, **student site first** (`https://exam.example.com,https://admin.exam.example.com`): the API sends the browser back to the first origin after a payment.
- `SSLCZ_CALLBACK_BASE_URL` and the Google redirect URI use the student origin.
- Nginx has a server block for each host name. Both forward `/` to the Next server on `127.0.0.1:3000` and `/api/` to the API on `127.0.0.1:8080`. One certificate covers both names.

**Tests:** `lib/config` helpers in both modes; proxy routing on the admin host, on the student host, and with one host; login audience messages with split hosts.

## Acceptance

- `npm run lint`, `npm test`, `npm run build` pass.
- A throwaway `app/(site)/_preview` and `app/(staff)/staff/_preview` page shows every component in both themes and both languages. Delete these before merge, or keep them behind a dev-only check.
- Login as a student lands on `/`, staff on `/staff`. A student opening `/staff` sees the staff-only message.

## Report

(Agent fills this in.)
