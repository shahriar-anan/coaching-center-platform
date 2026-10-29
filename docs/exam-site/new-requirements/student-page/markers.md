# Student page markers

A marker is a place where the web can't use an API field yet. The screen shows a neutral placeholder, and the code carries a `// NR-01 marker:` comment naming the field (plan.md, section 3). Each marker is listed here, with the step that adds it and the step that removes it.

| Marker | Field or endpoint | Screen and file | Added in | Removed in |
|---|---|---|---|---|

No markers. S0 changes only the API and the demo seed. S1, S2 and S3 use every API field the screens need (`pricing`, `packages`, `access.expiresAt`, `popular`, `favourite`, the favourites endpoints, `/me/live-exam-access` with its expiry, and the category `branch` list), so the web has nothing to mark.

## Phase 4 (2026-10-29)

No API markers. Every Phase 4 API change (N1, P1 to P4, E1) is in place, so the web uses the fields directly. Unfinished UI work (S4.3, S4.4 compact Wish List, S4.6 remaining links, S4.8 overflow check) is listed as open items in `progress/S4-review.md`, not as markers.
