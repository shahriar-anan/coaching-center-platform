# Slice 1 — Client foundation

Date: 2026-09-26

## Done

- Replaced the create-next-app starter layout: Geist plus Noto Sans Bengali, app metadata, and Tailwind base styles.
- Added `NEXT_PUBLIC_API_BASE_URL` via `.env.local.example` (default `http://localhost:8080` in `src/lib/config.ts`).
- Added API types in `src/lib/api/types.ts` aligned with `/v3/api-docs`, plus `ApiError` and stable `code` → i18n key mapping.
- Added `src/lib/api/client.ts` fetch wrapper (bearer token, `X-Client-Type: web`, `credentials: 'include'` on `/api/v1/auth/*`).
- Added English and Bangla catalogs (`src/i18n/messages/*`) with `LocaleProvider` defaulting to English and `LocaleSwitcher`.
- Added `npm run check:openapi` (`scripts/check-openapi-types.mjs`) to verify core paths when the API is up.

## Tested

| Check | Result |
|---|---|
| `npm run build` | Passed |

## Failed

None.

## Next

Slice 2 — session (login, in-memory access token, refresh on load, protected admin shell).
