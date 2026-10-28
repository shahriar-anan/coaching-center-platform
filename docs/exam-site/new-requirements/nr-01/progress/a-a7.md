# A7: retire the answers-released switch (agent A)

## What changed

Nothing in code. `PUT /api/v1/live-exams/{id}/answers-released` stays and stores its value (`answers_released_at`, `answers_released_by`), and it gates nothing. D removes the reads: answers and explanations show at submit (P-d, D21'). The control can be removed from the UI (see `web-impact/a.md`).

## Tests

No new test. The existing route's behaviour is covered by the sitting tests (`LiveSittingIT`), which passed in the A3 and A4 run. This slice is a note only, so no test invocation was run for it.

## Deviations

None.
