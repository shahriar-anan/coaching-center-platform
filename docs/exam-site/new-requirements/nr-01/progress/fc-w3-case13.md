# NR-01 final check, W3, case 13: staff exam list filters, paging and sorting

**Outcome:** new test class, 6 tests, all green. No production change needed.

**Test:** `api/src/test/java/com/coachingcenter/api/exams/StaffExamListFiltersIT.java`
- `theTitleSearchFindsABanglaTitleAndAnEnglishOne`: `q` finds a Bangla title (two exams), an English title, and is case-insensitive.
- `pagingReturnsTheTotalsAndEachPageOnceInOrder`: `size=2` gives 5 total, 3 pages; pages 0, 1 and 2 each hold their own exams in title order; page 3 is empty.
- `sortsByLiveAtWithUndatedLast`: ascending by liveAt puts the undated exam last; descending keeps the dated exams in reverse order.
- `sortsByTitleInBothDirections`: `title` asc and desc.
- `sortsByUpdatedAtAndNewestFirstIsTheDefault`: `updatedAt` asc and desc; with no sort the list is newest first.
- `anUnknownSortKeyIsRefused`: `sort=mode` gives 400 `VALIDATION_FAILED`.

**Already covered:** `StaffExamListIT.eachFilterNarrowsTheListAndAnExamInTwoPackagesCarriesBoth` covers each filter on its own (`categoryId`, `packageId`, `standalone`, `type`, `mode`, `status`, `phase`, `q` in English, `pending`, `popular`) and the 400 on a bad phase. Those are not repeated here.

**Test-setup notes (not code bugs):** the first run failed on the Bangla query. I had pre-encoded the value with `URLEncoder`, and MockMvc's `get(String)` encodes the path again. Passing the raw Bangla text fixed it; the server behaves correctly.

**Observation, not a decided rule:** descending `liveAt` puts undated exams first (PostgreSQL's default for DESC), while ascending puts them last. The NR-01 rulings only say undated last for the student's package list, so the staff desc case is left unasserted. Flagged for the orchestrator to decide.
