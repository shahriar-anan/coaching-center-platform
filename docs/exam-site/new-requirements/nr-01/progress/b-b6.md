# B6: favourites (agent B)

Committed together with B1 to B5 (shared files).

## Changed
- `FavouriteService` and `FavouriteController`: `GET /me/favourites` (exams and packages in the public row shape; hidden items skipped, not deleted); `PUT`/`DELETE` for exams and packages, idempotent; 404 unless published and in a visible branch.
- Public and student rows carry `favourite` (student only, `null` for a visitor).
- `StudentFavouriteRepository.findByStudentId`.

## Tests: `FavouritesIT` 3/3 (green). `AuditLogIT` 3/3 (green, with the B block added to `API_OPERATIONS`).
