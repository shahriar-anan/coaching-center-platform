# B3: staff exam list (agent B)

Committed together with B1 and B2 and B4 to B6 (shared files).

## Changed
- `StaffExamListService` (one page query, then batched lookups for packages, question counts, staged revisions and instructor names) and `StaffExamListController`.
- `LiveExamRepository.searchForStaff`, `LiveExamQuestionRepository.countByExamIds`, `LiveExamRevisionRepository.findExamIdsIn`.
- An instructor (`LIVE_EXAM_EVALUATE` without `EXAM_READ`) sees only `instructor_id = self`.

## Tests: `StaffExamListIT` 2/2 (green)
- Test fixes: the setup runs before the window opens (questions are editable only then); the "moving" exam stays a draft (publishing a standalone exam is A's path, see b-b1.md); `pricing.effectivePrice` is absent when null.
