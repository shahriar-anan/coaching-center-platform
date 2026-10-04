# web-catalog — slice 6 (course management screens)

## Done

- Course create and update use Details, the course view, and Content. Details holds the category chain. Saving opens the view, which shows this course and the categories above it.
- A category that still has a child stays, and the error is shown on Details.
- Content outline: one card per section, a lecture list, and an Actions menu for reorder and delete. Tapping a saved lecture opens its details beside the outline. Edit opens the form, and Save returns to the details. Saving an empty YouTube URL clears the video. The Section menu sends parentId only when it changed. Course files stay visible under the sections. Master Admin can delete a section, lecture, or file.
- A section that still has a lecture is not removed. A lecture delete reloads that lecture's files away with it.
- System Admin does not see price, archive, delete, close, or reopen.

## Verify

`npm test`, `npm run lint`, `npm run build`, `npm run check:openapi`.

## Next

Shahriar browser exit → `browser-exit.md`.
