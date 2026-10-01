# Slice 2 — Session

Date: 2026-09-26

## Done

- In-memory access token store (`src/lib/auth/token-store.ts`); no browser storage for tokens.
- `SessionProvider` restores session with `POST /auth/refresh`, then `GET /me`; rejects `STUDENT` with logout.
- Login page at `/login` with `X-Client-Type: web` and generic API error mapping.
- `401` handling: one refresh and retry in `apiRequest`; second failure clears session and routes to `/login`.
- Logout calls `POST /auth/logout` with bearer + cookie, then clears memory.
- Protected admin shell via `RequireAuth` + `(admin)` layout; `src/proxy.ts` only redirects `/` → `/students`.

## Tested

| Check | Result |
|---|---|
| `npm run build` | Passed |

## Failed

None. Browser exit against a running API was not run in this session.

## Next

Slice 3 — admin accounts (Master Admin only).
