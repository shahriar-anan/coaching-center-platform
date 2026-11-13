# Offline admission: release plan

Status: prepared 13 November 2026. Not deployed. The feature is built behind a switch that is OFF by default.

## 1. What ships

The migrations V81 to V86 (batches, admissions, fees, grant cap and kinds, mail payload, routine uploader). All of them only add tables, columns and constraint kinds, so there is no backfill. The API and web changes are in these commits, in order:

| Commit | What |
|---|---|
| 9b39e34 | Stage 1: schema, the course-end access cap, permissions |
| 1641743 | Stage 2: desk creation, the student form, the pending gate |
| ba63c08 | Stages 3 and 4: review, setup, batches, onboarding, fees, reminders |
| ba9ef90 | Read routes for the student and the admins (My admission, flags, counts, overdue, history) |
| dcd6595 | Web: the student admission form |
| 7f193ec | Web: staff admission screens and My admission |
| (switch commit) | The feature switch, API and web |

The tests and their results are recorded in the build log of [offline-admission-plan.md](offline-admission-plan.md), section 0.

## 2. Before anything is deployed

1. Take a backup of the production database, and keep the file.
2. Confirm the current production schema is at V80 (`select max(version) from flyway_schema_history`). V81 to V86 must apply on top of it.
3. Confirm the feature switch is OFF in production: `OFFLINE_ADMISSION_ENABLED` is unset, or set to `false`. Without it, every offline route answers 404 and the desk ignores the offline flag.

## 3. Deploy order

1. Deploy the API with the switch OFF. Flyway runs V81 to V86. Check the log for the migrations, and check that the health endpoint answers.
2. Check that `GET /api/v1/public/offline-admission/status` answers `{"enabled": false}`.
3. Deploy the web with `NEXT_PUBLIC_OFFLINE_ADMISSION` unset. Check that the staff menu has no "Offline admission" group and that the Students page has no offline checkbox.
4. Nothing changes for students or staff at this point. Online sign-ups and purchases behave as before.

## 4. Staging walkthrough (switch ON on staging only)

Set `OFFLINE_ADMISSION_ENABLED=true` on staging and `NEXT_PUBLIC_OFFLINE_ADMISSION=1` for the web build. Then walk through section 2 of the plan, end to end:

1. Desk: a System Admin creates a student with "Offline admission" ticked, and reads out the activation code.
2. Student: activates with the OTP, lands on the form, takes a photo with the camera, fills every field, submits. Check the "Under review" page.
3. Review: the admin sends the form back with a reason. The student sees the reason, fixes the form, and submits again.
4. Setup: the admin picks a batch (the fee and end date fill in), adds an exam and a package, and sees any overlap warning. Marks it ready. The checklist shows nothing missing.
5. Onboarding: the Master Admin presses Onboard, reads the confirmation, and confirms. The student gets the approval email and notice, and can open My admission.
6. Fees: a System Admin records a cash payment (it waits as pending). The Master Admin approves it. The student gets the receipt email and notice, and can download the receipt PDF.
7. Reminders: run the reminder job with a test clock (3 days before, on the day, 3 days after). Check the notices, the mails and the admin's overdue notice.
8. Closing: the Master Admin changes the fee and the end date, then closes the admission. The student keeps a read-only My admission page, and the grants end.
9. Phone check at 360 px and 390 px: the form, the queue, the detail, My admission and the print view. This is the step that has not been done yet.

## 5. Production switch-on

Only after the staging walkthrough passes and Shahriar agrees:

1. Set `OFFLINE_ADMISSION_ENABLED=true` on the API, restart it.
2. Rebuild the web with `NEXT_PUBLIC_OFFLINE_ADMISSION=1`. The build must bake an EMPTY `NEXT_PUBLIC_API_BASE_URL`. A non-empty value logged the admin out on every refresh in October (see the "web build API base" note in the project memory).
3. Check the staff menu and the student's form on production with one test account.

## 6. Rollback

- Set `OFFLINE_ADMISSION_ENABLED=false` and restart the API. Every offline route answers 404, and the desk stops creating admissions. No data is deleted.
- The web returns to its previous state after a rebuild with the flag unset.
- The migrations are not reversed. They only add tables and columns that nothing else reads.

## 7. Known gaps before switch-on

These are recorded here so they are a decision, not a surprise:

- The batch page's per-student money pills and the students-list badge still read each admission's schedule rather than a single field. They work, but they read more rows than they need.
- Students are not told about a routine upload that was made before V86 (their count is shown as unknown).
- The automatic course-end close job is not built. The Master Admin closes admissions by hand.
- The course-ending staff notice is not built (its kind is not in the enum).
- Voiding a payment sends the student a notice but no email (the mail kind is not in the V84 constraint).
- The phone layout, the camera and gallery picking, and the A4 print pagination have not been checked in a browser.
- Bangla copy in the new screens needs a native-speaker read.
- A desk account always has an email (the column is NOT NULL), so no admission mail is skipped for a missing address.
