# Sample seed map: where to see each feature

Built from the sample seed (`SEED_SAMPLE=true`, see `build-sample-db.md`). Plan: `docs/exam-site/plans/ui-delivery/sample-seed-plan.md`, section 8.
The ids below are the real ones in the sample. They follow the fixed-id scheme: the first group names the table (`a` categories, `b` packages, `e` exams, `5` students, `6` teachers, `4` staff, `7` sittings, `c` orders, `d` grants, `3` reviews, `8` announcements, `0` retake grants). Ids are fixed, so the same id names the same row on your PC and on the client's server.

Accounts: all sample accounts use the sample password (`SEED_SAMPLE_PASSWORD`), except the Master Admin (`MASTER_ADMIN_*`). Phones and emails are in `QA-Checklist.md` section 0b.

## Categories

| Name | Id | Where to see it |
|---|---|---|
| Bangladesh Judicial Service (BJS), root | `a0000000-0000-4000-8000-000000000001` | `/mcq`, top level |
| Assistant Judge Recruitment Exam | `a0000000-0000-4000-8000-000000000002` | `/mcq` subcategory switch |
| BJS Preliminary | `a0000000-0000-4000-8000-000000000003` | `/mcq` subcategory switch; exams 1 to 6 |
| Bar Council Enrolment, root | `a0000000-0000-4000-8000-000000000004` | `/mcq`; exams 16 and 17 |
| Advocate Enrolment Exam | `a0000000-0000-4000-8000-000000000005` | exam 19 (practice), package 2 |
| Preliminary | `a0000000-0000-4000-8000-000000000006` | exams 8, 9, 10, 11, 20 |
| Written | `a0000000-0000-4000-8000-000000000007` | "Written" tab or subcategory: exams 12 to 15; package 3 |
| **Short Courses (empty)** | `a0000000-0000-4000-8000-000000000008` | "Short Courses" under Bar Council Enrolment: shows **Coming soon** (no exams) |

## Packages

| Package | Id | Price | What it shows |
|---|---|---|---|
| P1 BJS Preliminary Package | `b0000000-0000-4000-8000-000000000001` | 1,500, **25% off** until 30 days after the run day | discount case; exams 3, 4, 5 |
| P2 Bar Council Complete Preparation Package | `b0000000-0000-4000-8000-000000000002` | 2,500, **popular** | MCQ and written together; exams 8, 9, 10, 12, 14 |
| P3 Advocate Written Pack | `b0000000-0000-4000-8000-000000000003` | 800 | written only; exams 12, 13, 15 |

## Exams

Exam ids run `e0000000-0000-4000-8000-0000000000NN` (NN = 01 to 20). The window is 20 minutes per attempt.

| No | Title | Id | State and what it shows |
|---|---|---|---|
| 1 | Civil Procedure | `…000000000001` | **Finished** (14 days ago). Free. Leaderboard: the three passers are listed, two below the pass mark are counted for staff; review average 4.7; answers released |
| 2 | Penal Code | `…000000000002` | **Live now**. Free. Negative marking 0.25 per wrong answer |
| 3 | Evidence | `…000000000003` | **Upcoming** in 3 days, countdown. 300, in P1 |
| 4 | Constitution | `…000000000004` | **Upcoming** in 10 days. 300, in P1 |
| 5 | CrPC | `…000000000005` | **Live now**, paid, in P1: a holder can take it now |
| 6 | CPC Revision | `…000000000006` | **Upcoming** tomorrow. 400, **25% off**; no package (discounted single exam) |
| 7 | Civil Law Written | `…000000000007` | **Upcoming** in 7 days. Written, 500, question paper PDF, **preview on**; teacher 1 |
| 8 | Penal Code | `…000000000008` | **Finished** (7 days ago). 300, in P2. Result passed and failed |
| 9 | Evidence | `…000000000009` | **Live now**, in P2 (open for holders) |
| 10 | General Law | `…000000000010` | **Upcoming** in 5 days, in P2. **Popular** exam |
| 11 | Free Sample Test | `…000000000011` | **Live now**. Free (free-announcement case). Student 5 has started it |
| 12 | CrPC Written | `…000000000012` | **Finished** (10 days ago). Written, 500, in **two packages** (P2 and P3). Teacher 1 |
| 13 | Penal Code Written | `…000000000013` | **Finished** (8 days ago). Written, 500, in P3. Marked but not released. Teacher 1 |
| 14 | Constitution Written | `…000000000014` | **Finished** (5 days ago). Written, 500, in P2. Awaiting marking. Teacher 2 |
| 15 | CPC Written | `…000000000015` | **Live now**. Written, 500, in P3: upload is open. Teacher 2 |
| 16 | General Law Test | `…000000000016` | **Finished** (3 days ago). Free: "sit once more" |
| 17 | Criminal Law Mock | `…000000000017` | **Finished** (27 days ago). Written, 500, question paper PDF, **access 30 days**: Student 2's access ends in **3 days**. Awaiting marking. Teacher 2 |
| 18 | General Knowledge | `…000000000018` | **No date** yet ("not announced"). Free |
| 19 | Practice Set | `…000000000019` | **Practice** (no window). 300 |
| 20 | Draft Paper | `…000000000020` | **Draft**: staff see it, students never do |

## Students, staff and teachers

| Who | Id | Phone | Email | What to check |
|---|---|---|---|---|
| Student 1 | `50000000-0000-4000-8000-000000000001` | +8801711000011 | student1@example.com | The main explorer: P2; results in every state (5 rows); wish list: exam 2 and P2 |
| Student 2 | `50000000-0000-4000-8000-000000000002` | +8801711000012 | student2@example.com | Written pack (P3); failed to submit exam 12; exam 17 access ends in 3 days |
| Student 3 | `50000000-0000-4000-8000-000000000003` | +8801711000013 | student3@example.com | BJS package (P1); P3 by enrolment; failed exam 12 with a retake |
| Student 4 | `50000000-0000-4000-8000-000000000004` | +8801711000014 | student4@example.com | P2; low score (exam 1); a hidden review |
| Student 5 | `50000000-0000-4000-8000-000000000005` | +8801711000015 | student5@example.com | P2; started the free test (exam 11); one failed payment (P1) |
| Student 6 | `50000000-0000-4000-8000-000000000006` | +8801711000016 | student6@example.com | Almost empty: the empty states; one pending payment (P3); one cancelled order |
| System Admin 1 | `40000000-0000-4000-8000-000000000001` | +8801711000001 | staff1@example.com | Staff screens |
| System Admin 2 | `40000000-0000-4000-8000-000000000002` | +8801711000002 | staff2@example.com | Staff screens |
| Teacher 1 | `60000000-0000-4000-8000-000000000001` | +8801711000003 | instructor1@example.com | Marking queue: exam 13 (one script awaiting). Exams 7, 12, 13 |
| Teacher 2 | `60000000-0000-4000-8000-000000000002` | +8801711000004 | instructor2@example.com | Marking queue: exams 14 and 17 (two scripts awaiting). Exams 14, 15, 17 |
| Master Admin | from `MASTER_ADMIN_*` (not created by the seed) | +8801700000001 | master@example.com | Announcements, pending orders, reviews moderation, leaderboards |

## Results hub, sittings and marking

Sittings ids run `70000000-0000-4000-8000-0000000000NN` (NN = 01 to 22). MCQ results are visible at once.

| Sitting | Exam | Student | Result |
|---|---|---|---|
| 01 to 05 | 1 | S1, S2, S3, S4, S5 | 40 pass, 15 fail, 35 pass, 10 fail, 30 pass |
| 06, 07 | 2 | S2, S4 | 34.25 pass, 14.5 fail (negative marking) |
| 08 | 5 | S3 | 35 pass |
| 09, 10, 11 | 8 | S1, S5, S4 | 40 pass, 15 fail, 30 pass |
| 12 | 11 | S5 | 30 pass (started the free test) |
| 13, 14, 15 | 16 | S1, S2, S5 | 25 pass, 30 pass, 15 fail |
| **16** | 12 | S1 | **Published** 32/50 pass (written) |
| **17** | 12 | S3 | **Published** 18/50 fail; retake available |
| **18** | 12 | S2 | **Failed to submit** (no upload) |
| **19** | 13 | S2 | **Marked, not released** 28/50 |
| **20** | 13 | S3 | **Awaiting marking** |
| **21** | 14 | S1 | **Awaiting marking** |
| **22** | 17 | S2 | **Awaiting marking** (27 days old) |

The seed holds no sitting in progress: the client starts one (a practice paper, or any open exam).

Results hub: S1 shows the published result (exam 12) and the one awaiting marking (exam 14). S2 shows the failed-to-submit paper (exam 12) and the marked, not released one (exam 13).

Marking queues (`live-exam-submissions`, status AWAITING): Teacher 1 sees 1 (sitting 20, exam 13). Teacher 2 sees 2 (sittings 21 and 22, exams 14 and 17).

## Orders, receipts and grants

| Order | Id | Student | What | Status | Where |
|---|---|---|---|---|---|
| O1 | `c0000000-0000-4000-8000-000000000001` | S1 | P2 | paid (receipt) | payments, receipts |
| O2 | `c0000000-0000-4000-8000-000000000002` | S5 | P2 | paid (receipt) | payments |
| O3 | `c0000000-0000-4000-8000-000000000003` | S4 | P2 | paid (receipt) | payments |
| O4 | `c0000000-0000-4000-8000-000000000004` | S2 | P3 | paid (receipt) | payments |
| O5 | `c0000000-0000-4000-8000-000000000005` | S3 | P1 (discounted, 1,125 of 1,500) | paid (receipt) | payments; discount |
| O6 | `c0000000-0000-4000-8000-000000000006` | S6 | P3 | **pending**: the staff **Pending orders** queue shows exactly this one | staff Pending orders |
| O7 | `c0000000-0000-4000-8000-000000000007` | S5 | P1 | **failed** | payments |
| O8 | `c0000000-0000-4000-8000-000000000008` | S6 | exam 7 | **cancelled** | payments |

Grants (`live-exam-grants`, seven): `d…01` S1 on P2, `d…02` S5 on P2, `d…03` S4 on P2, `d…04` S2 on P3, `d…05` S3 on P1 (from orders O1 to O5); `d…06` **manual**: S3 on P3 (direct enrolment); `d…07` **manual**: S2 on exam 17, with access ending in 3 days. Retake: `00000000-0000-4000-8000-000000000001`, S3 on exam 12, unused.

Pattern for the ids: `d0000000-0000-4000-8000-0000000000NN`.

## Reviews and moderation

Review ids `30000000-0000-4000-8000-0000000000NN` (NN = 01 to 07).

| Review | Target | Student | Stars | Status | What to see |
|---|---|---|---|---|---|
| 01 | exam 1 | S1 | 5 | approved, **featured** | public comment; the average |
| 02 | exam 1 | S2 | 4 | **waiting** (pending) | in the staff queue, not public |
| 03 | exam 1 | S3 | 5 | approved, no comment | counts in the average only |
| 04 | exam 1 | S4 | 2 | **hidden** (reason set) | left out of the average and the public page |
| 05 | P2 | S1 | 5 | approved, **featured** | package average |
| 06 | P2 | S5 | 4 | approved | package average |
| 07 | P2 | S4 | 5 | **waiting** (pending) | counts in the average, not public text |

Average: exam 1 shows **4.7** from three ratings (5, 4, 5), and P2 shows **4.7** from three (5, 4, 5). The public page of exam 1 shows one comment (review 01). Staff Reviews page: the waiting reviews 02 and 07, the hidden 04, the featured 01 and 05.

## Announcements

Announcement ids `80000000-0000-4000-8000-0000000000NN` (NN = 01 to 06). Recipients come from the product's holder rule.

| No | Audience | Target | Recipients | Where |
|---|---|---|---|---|
| 01 | ALL | every student | S1 to S6 (six) | Master Admin Announcements; every student's bell |
| 02 | MODULE | P2 | S1, S4, S5 | P2 package page for S1 and S5 |
| 03 | EXAM | exam 17 | S2 | exam 17 page for S2 |
| 04 | SELECTED | S1, S2 | S1, S2 | S1 and S2 notices |
| 05 | EXAM | exam 11 (free) | S5 | exam 11 page; S5 notices |
| 06 | MODULE | P1 (**withdrawn**) | S3 (kept in the record, not shown) | Master Admin history shows it withdrawn; S3 does not see it |

## Notices

Notices (39): purchase notices (7), MCQ score notices (15), written result notices (2), the retake notice for S3 (1), and one notice per announcement recipient (14). Student 1 and Student 5 see the package and exam pages and their notices.

## Empty states

Student 6 (`50000000-0000-4000-8000-000000000006`): no results, no grants, no favourites, no reviews. Only announcement 01 is in the inbox. Shows the empty states on `/results`, on `/notices`, and on the lists of exams in progress.

## Question bank

One bank, `f0000000-0000-4000-8000-000000009000`, with twelve items: the staff create-exam screens show it. Questions `f0000000-0000-4000-8000-0000000090NN`.

## Papers

Six question papers (written exams 7, 12, 13, 14, 15 and 17) with sample PDFs. Written sittings have sample answer scripts; marked ones have sample marked scripts. The PDFs say "sample copy only".
