# Step 5 — Website inbox

API only. The website loads these rows. There is no mobile app, no push, no email, and no SMS for this inbox.

## Outcome

A student can read their notices and mark them read. Three events create a notice:

- One to two days before a live window, for a student who already has a roster grant or a purchase grant.
- When that student's MCQ score posts.
- When the Master Admin publishes that student's written result.

## Bind these

- One reminder per student per exam. A second scheduler run does not insert another.
- The reminder uses the server clock and `live_at`. A client clock does not create it. An exam with a null `live_at` gets no reminder.
- Students without a grant do not get the reminder.
- The MCQ notice fires from the existing post path (`LiveExamSittingService` deadline post and the submit post). It does not wait for an admin.
- The written notice fires from the step 1 publish action, not from the instructor save.
- The payload can name the exam and the student's own marks. It does not include another student's row, a phone number, or the evaluator.
- Delivery is a table the website reads. Do not add a websocket, a push provider, or a mail send for these three events.

## Where the code is

- No notification type exists under `api/src/main/java`.
- Score post: `LiveExamSittingService.post` and `postIfDue`.
- Publish: the endpoint step 1 adds.
- Grants: roster today, purchase after step 3. If this step is planned before step 3 has landed, the reminder query must include the purchase grant as soon as that table exists, and the plan says so.

## Plan must name

- The table and the migration (`V29` or later, after whatever step 1 and step 3 already added).
- `GET` the current student's notices and the mark-read call.
- The scheduler interval and the 1–2 day window.
- The `*IT` cases: one reminder, no duplicate, no grant means no reminder, MCQ notice on post, written notice on publish and not on draft save, a student cannot read another student's notices.

## Out of this step

The inbox screen (step 6) and any admin broadcast.
