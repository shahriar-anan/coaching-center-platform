# Exam and package reviews: plan

Status: approved by Shahriar on 1 November 2026 (packages added by him; every other decision follows the recommendation in section 9). Wave 1 and wave 2 start together.

## 1. What the old site had

| Part | What it was | Keep? |
|---|---|---|
| Home "Top Reviews" (`components/Home/Reviews.jsx`) | A carousel (react-slick, 3 cards on desktop, 2 on tablet, 1 on phone, autoplay every 5 s). Each card: grey default avatar, name, role ("Student", "Law Graduate"), quote icon, a Bangla comment. | The **look** only. |
| Its data | Five reviews **hard-coded in the file**. No backend, no stars, nobody could write one. The names are not real students. | **No.** Showing them would present made-up reviews as real. |
| Backend `review_ratings` table | `course_id`, `rate`, `comment`, `user_id`. A model with no controller, route or screen. **0 rows** on the live database. | No. Nothing to migrate. |

The old site never had a working review feature. The new one starts clean, with reviews written by students who really sat the exam.

## 2. What the current site gives us

- **Who sat what.** `live_exam_participants` has one row per attempt (`exam_id`, `student_id`, `attempt_no`, `status`, `submitted_at`). `status = 'SUBMITTED'` (or later states that came from it) proves the student sat that exam. `LiveExamParticipantRepository.findSatExamIds` already lists the exams a student sat.
- **Who holds which package.** `StudentOwnAccessService.mine(studentId)` returns the student's `MyModule` rows (via purchase or grant, active or expired) and `MyExam` rows. A package is `live_exam_modules`; its exams are `live_exam_package_exams` (`LiveExamMemberships.activeModuleIds(examId)` gives the packages an exam is in).
- **Student pages after an exam:** `/exam/{id}/submitted`, `/exam/{id}/result`, `/results`, My exams. Exam detail is `exam-page.tsx`; package detail is `module-page.tsx`.
- **Landing page** has `Section` with two alternating tones; the new section goes between "How to start" and "Contact".
- **Staff curation precedent:** `PUT /api/v1/live-exams/{id}/popular` and `/live-exam-modules/{id}/popular` through `PopularService`, with `AuditRecorder`.
- **Permissions** are named in `Permissions.java` with contributors; every staff write is audited, and `AuditLogIT` lists the operations.
- **Students have no photo.** Cards show an initial in a circle.
- **The Flutter app** uses the same API, so the endpoints serve it later.

## 3. The feature in one paragraph

A student can rate an exam or a package 1–5 stars and optionally write a comment. One review per student per exam and one per student per package; both can be edited or deleted at any time. A rating alone counts at once. A comment shows publicly only after staff approve it. Exam and package pages show the average and the approved comments. Staff pick the best approved reviews to feature in "What students say" on the landing page.

## 4. Rules

| Rule | Detail |
|---|---|
| Who can review an **exam** | A signed-in student with at least one `SUBMITTED` attempt on it. Checked on the server on every write. |
| Who can review a **package** | A signed-in student who holds that package (purchase or grant, active or expired) **and** has submitted at least one exam that is in it. |
| When | From the moment they submit. The review is about the exam or package, not their marks. |
| How many | One per student per target (database unique keys). Editing replaces it. |
| Content | Stars 1–5, required. Comment optional, plain text, at most 1000 characters, trimmed. No HTML, links are not clickable. |
| Moderation | Stars only: `APPROVED` at once. With a comment: `PENDING` until staff approve. Editing the comment sends it back to `PENDING`. Staff can `HIDDEN` it with a reason; the student sees "Not published" and the reason. |
| Average | Every review that is not `HIDDEN`. A pending comment does not block its stars. Shown only when there are at least 3 ratings. |
| Name shown publicly | First name and last initial, e.g. "Farhana A.". Never phone, email or id. |
| Target removed or archived | Removed (soft-deleted) targets drop out of public views; archived ones keep showing their reviews. |
| Student deactivated or deleted | Their reviews leave public views and averages. |

## 5. Data (one migration, V56)

`live_exam_reviews` (the name covers both targets; it belongs to the live-exam catalog)

| Column | Type | Note |
|---|---|---|
| `id` | uuid PK | |
| `exam_id` | uuid null FK live_exams | |
| `module_id` | uuid null FK live_exam_modules | |
| `student_id` | uuid FK users | |
| `rating` | smallint | CHECK 1–5 |
| `comment` | text null | ≤ 1000 characters (checked in service) |
| `status` | varchar(16) | `PENDING`, `APPROVED`, `HIDDEN` |
| `hide_reason` | varchar(300) null | shown to the student |
| `featured` | boolean default false | only when `APPROVED` and has a comment |
| `moderated_by`, `moderated_at` | uuid null, timestamptz null | |
| `created_at`, `updated_at` | timestamptz | |

CHECK exactly one of `exam_id`, `module_id` is set. Unique `(exam_id, student_id)` where `exam_id` is not null; unique `(module_id, student_id)` where `module_id` is not null. Index `(exam_id, status)`, `(module_id, status)`. Partial index on `featured` where true.

## 6. API (the contract the screens are built against)

All JSON. Camel case. Errors use the existing error shape. `{kind}` is `exam` or `package`; `{id}` is the exam id or the package id.

**Student (own data only; role STUDENT)**

| Method | Path | Does |
|---|---|---|
| GET | `/api/v1/me/reviews/{kind}/{id}` | `{canReview, reviewed: false}` or `{canReview, reviewed: true, review: MyReview}`. Always 200. |
| PUT | `/api/v1/me/reviews/{kind}/{id}` | Body `{rating, comment}` (comment optional). Creates or replaces. Returns `MyReview`. 403 `REVIEW_NOT_ALLOWED` if not eligible, 400 on bad input, 404 if the target does not exist. |
| DELETE | `/api/v1/me/reviews/{kind}/{id}` | Removes it. 204. |
| GET | `/api/v1/me/reviews` | All the caller's reviews: `[{kind, targetId, targetTitleEn, targetTitleBn, review: MyReview}]`, newest first. Used by the results hub and My exams to hide "Rate" on rows already rated. |

`MyReview` = `{id, rating, comment, status, hideReason, createdAt, updatedAt}`.

**Public (no sign-in)**

| Method | Path | Does |
|---|---|---|
| GET | `/api/v1/public/reviews/{kind}/{id}?page=0&size=10` | `{ratingAverage, ratingCount, stars: {"1":n,...,"5":n}, page: {content: [PublicReview], page, size, totalElements, totalPages}}`. `ratingAverage` is null when `ratingCount` < 3. Only `APPROVED` reviews with a comment are in `content`, newest first. |
| GET | `/api/v1/public/reviews/featured` | Up to 9 `FeaturedReview`, newest featured first. |
| (existing) | the public exam and package views | add `ratingAverage` and `ratingCount` (average null below 3) |

`PublicReview` = `{id, name, rating, comment, createdAt}`; `FeaturedReview` = `PublicReview` plus `{kind, targetId, targetTitleEn, targetTitleBn}`. `name` is first name + last initial.

**Staff** (new permission `LIVE_EXAM_REVIEW_MODERATE`, System Admins and Master Admin; instructors get read-only `GET` for reviews of their own exams)

| Method | Path | Does |
|---|---|---|
| GET | `/api/v1/staff/reviews?status=&kind=&targetId=&rating=&page=&size=` | Queue, `PENDING` first then newest. Row: `{id, kind, targetId, targetTitleEn, targetTitleBn, studentId, studentName, rating, comment, status, hideReason, featured, createdAt, updatedAt, moderatedAt}`. Page envelope as the other staff lists. |
| GET | `/api/v1/staff/reviews/pending-count` | `{count}` for the sidebar badge |
| PUT | `/api/v1/staff/reviews/{id}/status` | `{status: "APPROVED" or "HIDDEN", reason}`; reason required for `HIDDEN`. Hiding clears `featured`. |
| PUT | `/api/v1/staff/reviews/{id}/featured` | `{featured: true or false}`; only for an `APPROVED` review with a comment, else 409. |

Every staff write is recorded by `AuditRecorder`.

## 7. Screens

**Student**
1. **Submitted page and result page (exam):** a card "How was this exam?" with five tappable stars. A tap saves the rating at once; "Add a comment" opens the text box. After saving: "Thanks. Your comment will appear after review."
2. **Package page (`module-page.tsx`), when the student is eligible:** the same card, "How was this package?".
3. **Results hub and My exams:** a small "Rate" action on rows the student sat but has not reviewed.
4. **Edit:** the card shows their review with Edit and Delete and its status ("Published", "Waiting for review", "Not published: reason").

**Public**
5. **Exam page and package page:** a Reviews section at the end: average with stars and count, a 5-to-1 breakdown, approved comments with "Show more". Hidden when there are no ratings; the average hidden below 3.
6. **Landing page:** "What students say" (শিক্ষার্থীদের মতামত). The old card shape: initial circle, name, what they reviewed, stars, comment, quote mark. Three cards on desktop, sideways scroll with snap on phones, no autoplay. The whole section hides when nothing is featured.

**Staff**
7. **Reviews page** in the staff sidebar: tabs Pending, Published, Hidden; filter by exam or package and stars; each row has stars, comment, student name (linked to their staff profile), target, date, and Approve, Hide (asks for a reason), Feature. A pending count in the sidebar.

Not now: stars on exam rows in lists (few exams will have 3 ratings at launch).

## 8. Delivery

| Wave | Work | Tests |
|---|---|---|
| 1. API | V56, entity, repository, service (eligibility for both targets, upsert, moderation, averages), controllers, permission, audit entries, public views get `ratingAverage` and `ratingCount` | `ExamReviewIT`: cannot review an unsat exam or a package not held, one per target, edit resets to pending, stars-only auto-approved, hidden excluded from averages, public views contain no ids of students or phones, average null below 3; `AuditLogIT` list |
| 2. Student and public UI | Review card (exam and package pages), Rate actions, Reviews section on exam and package pages, landing section, bn and en copy | Vitest for the card states, the section, empty hiding |
| 3. Staff UI | Reviews page, sidebar entry with count | Vitest for approve, hide with reason, feature |
| 4. QA | QA checklist section 18, sample reviews in the dev seed | Manual |

Waves 1 and 2 run together against the contract in section 6. Wave 3 follows wave 1.

## 9. Decisions

| # | Question | Decision |
|---|---|---|
| Q1 | Do comments wait for staff approval? | Yes. Stars count at once. |
| Q2 | Name shown | First name and last initial. |
| Q3 | When can a student review | Right after submitting. |
| Q4 | Stars on exam rows in lists | Later. |
| Q5 | Who moderates | System Admins and the Master Admin. Instructors read the reviews of their own exams, student names included, and cannot change them. |
| Q6 | Landing reviews | Staff-picked (featured). |
| Q7 | Reviews for packages | **Yes** (Shahriar, 1 November): exams and packages. |
| Q8 | Difficulty rating | Not now. |
