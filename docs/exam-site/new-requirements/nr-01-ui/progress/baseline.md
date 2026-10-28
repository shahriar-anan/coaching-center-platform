# UI baseline (29 October 2026)

Branch `ui/nr01-ui` at `e826aa0` (the pushed NR-01 API work), before any UI change.

| Check | Result |
|---|---|
| `npm run lint` | exit 0 (2 pre-existing warnings in old admin pages) |
| `npm test` | exit 0 (116 files, 700 tests) |
| `npm run build` | exit 0 |

All three were run in the main checkout under the heavy-job lock.
