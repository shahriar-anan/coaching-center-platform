# Requirements review (1 November 2026)

What this is: every requirement you gave in this session, what was built, the tests that cover it, and what is still open. "Built" means the code is written and the automated tests pass. Nothing below has been checked in a browser unless the last column says so.

Status key: **Done** = built and tested. **Done, needs a look** = built and tested, but the look or feel needs you. **Open** = not finished. **Decision** = waiting for your answer.

## 1. Landing page, header, footer and packages (QA Segment 1)

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 1.1 | Phone header hides the name; the logo stays | `components/shell/logo.tsx`, `student-shell.tsx` | Done, needs a look | Header tests in `tests/landing` |
| 1.2 | Visitor "Menu" on phones and tablets, with the eight items | `components/shell/site-menu.tsx` | Done, needs a look | `tests/landing/site-menu.test.tsx` |
| 1.3 | Brand name "Symam Law" everywhere, Bangla shows the Latin name | messages, emails, receipt | Done | `tests/ws0`, email template tests |
| 1.4 | Footer copies the old site's footer, phone and desktop | `components/shell/site-footer.tsx` | Done, needs a look | `tests/student-footer` (16 tests) |
| 1.5 | Footer on every page except the timed exam paper | `student-shell.tsx` `footerVariant` | Done | `tests/student-footer` |
| 1.6 | Packages page with a category filter | `components/student/catalog/packages-page.tsx` | Done, needs a look | `tests/pkg1`, `tests/ws-p4` |
| 1.7 | Stats box under the banner (old site's figures) | `landing/numbers.tsx` | Done, needs a look | `tests/landing` |
| 1.8 | "What students say" section on the landing page | `landing/reviews.tsx` | Done, needs a look | `tests/reviews/landing-reviews.test.tsx` |
| 1.9 | "Begin your preparation" section made compact | `landing/closing.tsx` | Done, needs a look | `tests/landing` |
| 1.10 | Background tones alternate cleanly, no hard lines | `landing/section.tsx` | Done, needs a look | `tests/landing` |

## 2. Student home and catalog

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 2.1 | Nav and home label "My exams" becomes "My orders" | `messages/*/common.ts`, `home.ts` | Done | `tests/student-nav/top-bar.test.tsx` |
| 2.2 | Home has four doors in one grid: MCQ, Written, **My orders** and **Packages** (an icon, a title and one line each; My orders leads to `/my-modules`, Packages to `/packages`, the page the landing page's packages "See all" uses). The My orders list and the Popular list are no longer on the home | `home/home-page.tsx`, `door-icon.tsx`, `public/icons/orders.svg`, `packages.svg` | Done, needs a look | `tests/home/popular-half.test.tsx`, `tests/ws3/home-page.test.tsx` |
| 2.3 | "Live now" list removed from home | `home/home-page.tsx` | Done. The "Live now" badge on the "Next exam" card still shows; **Decision**: remove it too? | `tests/ws3/home-page.test.tsx` |
| 2.4 | Filter tabs (All, Free, Live, Archived) are a pill row on the package page; vertical list stays on the category sidebar | `exam-view-tabs.tsx` | Done, needs a look | `tests/reviews/review-places.test.tsx` |
| 2.5 | Package page has a Reviews tab; exam page has a Reviews button leading to its own page | `module-page.tsx`, `exam-page.tsx`, `exam-reviews-page.tsx` | Done, needs a look | `tests/reviews/review-places.test.tsx` |
| 2.6 | Results tab "Other" renamed "In progress" | `messages/*/result.ts` | Done | `tests/ws4/results.test.tsx` |

## 3. Reviews (exams and packages)

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 3.1 | Students rate an exam after sitting it, and a package after holding it and sitting one of its exams | `LiveExamReviewService`, `ReviewRule` | Done | `ExamReviewIT` (14 tests) |
| 3.2 | A comment waits for staff approval; stars count at once | `LiveExamReviewService` | Done | `ExamReviewIT` |
| 3.3 | Average shown from 3 ratings; hidden review not counted | `LiveExamReviewPublicService` | Done | `ExamReviewIT` |
| 3.4 | Student card on submitted, result and package pages; "Rate" on the results page and My exams | `components/student/reviews/` | Done, needs a look | `tests/reviews` |
| 3.5 | Staff Reviews page with tabs, filters, Approve, Hide with reason, Feature, deep links from exam and package menus | `components/staff/reviews/`, `app/(staff)/staff/reviews` | Done, needs a look | `tests/staff-reviews` (39 tests) |
| 3.6 | Teachers read their own exams' reviews, no actions | `LiveExamReviewModerationService` | Done | `ExamReviewIT` |
| 3.7 | Staff Reviews page is in the sidebar with a pending count | `staff-nav.ts` | Done, needs a look | `tests/s1-b/staff-nav.test.ts` |

## 4. Admin: Direct enrolment

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 4.1 | Choose a package or exam by search and pick, not a long dropdown | `components/staff/target-picker.tsx` | Done, needs a look | `tests/staff-admin/target-picker.test.tsx` (14 tests) |
| 4.2 | Student picker reused, behaviour unchanged | `components/staff/student-picker.tsx` | Done | `tests/staff-admin/student-picker.test.tsx` |
| 4.3 | The picker only fetches one page at a time | `lib/api/exam/target-search.ts` | Done | 120-exam test in the picker tests |

## 5. Announcements

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 5.1 | Four audiences: all students, a package, an exam, selected students | `AnnouncementService`, `AnnouncementAudience` | Done | `AnnouncementAudiencesIT` (13 tests) |
| 5.2 | Package and exam announcements reach the holders (paid or free) | `AnnouncementAudience` | Done | `AnnouncementAudiencesIT` |
| 5.3 | Package and exam pages show the announcement to holders | `me/announcements`, `announcement-card.tsx` | Done, needs a look | `tests/announcements` |
| 5.4 | Notices page shows announcements, with links to the package or exam | `notices/page.tsx` | Done, needs a look | `tests/announcements/notices-announcement-links.test.tsx` |
| 5.5 | A preview of the recipient count before sending | `GET /announcements/preview`, staff form | Done, needs a look | `AnnouncementAudiencesIT`, `tests/staff-admin` |
| 5.6 | Withdraw an announcement (hidden from pages and notices, kept on record) | `PUT /announcements/{id}/withdraw` | Done | `AnnouncementAudiencesIT` |
| 5.7 | Expired or revoked access does not count as holding | `AnnouncementAudience` | Done | `AnnouncementAudiencesIT` |
| 5.8 | A student who held a package before an exam was removed from it still gets that exam's announcements (the gap closed as Option A) | `AnnouncementAudience.stillCovers` | Done | `AnnouncementAudiencesIT.examAnnouncementsStillReachStudentsWhoHeldThePackageBeforeTheExamLeftIt` |
| 5.9 | System Admins may send announcements | permission contributor | Done | `LiveGrantAdminIT` (changed on purpose) |

## 6. MCQ sitting rules

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 6.1 | After an MCQ submit, the paper is not shown again; the answers and explanations show at once after submit | `LiveExamSittingService`, `ReviewRule` | Done | `LiveSittingIT` (38 tests) |
| 6.2 | Answers and explanations show at once after an MCQ submit, for every exam, whatever the live window, the attempt's deadline or the release switch | `ReviewRule` | Done. **Decided (1 Nov 2026, by the owner):** the answers show at submit. This replaces the earlier rule that held the key back until the attempt's deadline, the end of the live run or a release. Accepted risk: a student who finishes early can see the key while others in the same exam are still sitting. | `LiveSittingIT`, `ExamMcqAttemptIT`, `MyExamResultsIT` |
| 6.3 | The score appears under Results at once | `result`, `/me/exam-results` | Done | `MyExamResultsIT` |
| 6.4 | Written: uploading the PDF is the submission; no second upload; paper not offered after upload | `LiveExamSittingService`, web | Done | `LiveSittingIT` |
| 6.5 | Results tab shows the submitted written exam under "Being marked" | `results-hub.tsx` | Done, needs a look | `tests/submit-lock/results-tabs.test.tsx` |
| 6.6 | The paper looks like a printed exam paper (white, black, numbered, answer-sheet bubbles); site header hidden | `paper-layer.tsx`, `paper-question.tsx` | Done, **needs a look** (not checked in a browser; CSS classes not built) | `tests/mcq-paper` (17 tests) |

## 7. Published edits and question editing

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 7.1 | Published exam edits reach the students | API reads checked; web refresh on focus and after publish | Done, **needs a browser check** (the exact on-screen bug was not reproduced; the API reads were already correct) | `ExamRevisionIT` (13), `tests/publish-refresh` (3) |
| 7.2 | Moving a question on a published exam works | `mcq-questions.tsx` | Done | `tests/ws5/mcq-reorder.test.tsx`, `ExamRevisionIT` |
| 7.3 | A reorder-only change is kept and published | `ExamChangeListBuilder` | Done. **Decision**: a reorder counts as a question change and notifies students. Keep, or make silent? | `ExamRevisionIT` (14 tests) |

## 8. Seed data and QA

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 8.1 | One small seed for the dev database and the client review, same accounts, plus four classmates | `SampleSeed` | In progress (agent) | `SampleSeedIT` when finished |
| 8.2 | QA checklist, seed map, and how to build the sample database | `docs/exam-site/qa/` | In progress (agent) | |
| 8.3 | Remove the other databases, old seed code and sample scripts | | **Open, waiting for your go.** Not done on purpose | |
| 8.4 | Staging server reload | | **Open, waiting for your go.** The staging data holds the client's marking and purchases | |

## 9. Copy and language

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 9.1 | Bangla uses "পাতা", never "পেজ" | all `messages/bn` files | Done (48 uses in 14 files replaced; the only remaining words are in two rule-guard tests) | `tests/reviews/messages.test.ts`, 721 tests in the changed areas |
| 9.2 | No "পেজ" in new text | | Done for new text | |

## 10. Staff and admin

| # | Requirement | Where | Status | Evidence |
|---|---|---|---|---|
| 10.1 | Lint errors in `staff/enrolments/page.tsx` and `student-shell.tsx` | | Known, left alone on purpose | `eslint` reports them |

## 11. Checks still to run

- Full API suite: **Open**, after the seed agent finishes.
- Full web suite: **Open**, after the copy agent finishes.
- Dev servers: stopped. Started again at the end for you.

## 12. Decisions I need from you

1. **Reorder notifies students (7.3).** Keep it, or make reorder-only changes silent?
2. ~~Review timing (6.2)~~ decided: each student's own deadline.
3. **"Live now" badge on "Next exam" (2.3).** Remove it too?
4. **Cleanup (8.3) and staging reload (8.4).** Give the go for each, after you have tested the new sample database.
