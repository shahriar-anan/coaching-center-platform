# Phase 3 Scope: Live Exam System

**Confirmed:** 2026-10-19. This document replaces the earlier exam-module plan (course bundle, purchase, a paper with both an MCQ and a written part, photo answers, and a question bank). That earlier build is history. It is not the acceptance target.

**Depends on:** Phase 1 identity and Phase 2's manual-payment and category patterns, which stay as they are. This phase does not redesign them.

**This phase is the API.** Student and admin screens are a later delivery. They are listed in Section 12 so the API can serve them. Do not build those screens in an API stage.

The PRD is not a source for these rules.

---

## 1. What this phase is

A Master Admin or a System Admin organizes exams in a category tree, publishes exam modules, and puts MCQ exams and written exams inside a module. The Master Admin marks which students may see a module. At a published live time the exam starts for every eligible student at once. When that window ends the exam stays published. A student who never tapped Take exam during that window may take it once later, on a personal timer, off the live leaderboard.

MCQ scores are automatic. Written papers are marked by one instructor assigned to the module, or by the Master Admin.

---

## 2. Language

Bangla and English are both required for category titles, module titles, module descriptions, and exam names, descriptions, and rules.

Question text, options, the correct answer, explanations, the written comment, and the instructor's comment are one language. Do not add a second column for them.

---

## 3. Categories and modules

### 3.1 Category tree

Exam categories are their own tree. A category may contain categories, with no depth limit. Science may contain Physics, and Physics may contain Physics 1st paper. This tree is not the course category tree, and it is not a tree of sections inside a module.

A Master Admin or a System Admin may add a category. Delete is Master Admin only. Delete of a category that still has a subcategory or a module is rejected.

Archive keeps the record for Master Admin and System Admin. It can archive a category that already holds subcategories and modules, and it does not delete them. An archived category is closed: a new child, a new module, a move onto it, and publish of a module already in that branch are rejected. A student cannot list or open a module when its category, or any category above it, is archived.

### 3.2 The module

A module is placed under one category, at any depth, including directly under a top-level category. The same category may hold more than one module.

A module has a Bangla title, an English title, a Bangla description, and an English description. Creating or editing it stores a draft on each change. A final save is a separate step. Publish comes after that final save. Students see a published module only.

The current client uses access mode `MANUAL` only. `OPEN`, `ENROLLED_ONLY`, and `PURCHASE_REQUIRED` remain in the product for a later LMS and are not the path for this delivery. A `MANUAL` module has no course link and no purchase.

---

## 4. Exams

Each exam belongs to one module and is either MCQ or written. The same module may contain both. A Master Admin or a System Admin chooses the type when creating the exam.

Creating or editing an exam stores a draft on each change. A final save is a separate step. Publish comes after that final save. Students see published exams only. A marked student sees every published exam in the module, including exams published later.

Before publish, the exam has a pass percent, a whole number from 0 through 100.

- An MCQ exam's maximum is the sum of its question marks. It has no PDF.
- A written exam's maximum is the number set on that exam. Its question paper is one PDF.

The student passes when `score × 100` is at least `pass percent × maximum`. Otherwise the student fails. A blank MCQ, with no answer saved, fails even when the pass percent is 0. A paper with at least one saved answer uses that formula, including a score of zero.

### 4.1 Live window

The publisher sets one live instant and a duration. Store the instant as an absolute time. Show it in `Asia/Dhaka`. The server clock decides the start and the end. A client clock cannot move either boundary.

The end is the live instant plus the duration. A 40-minute exam that goes live at 12:00 ends at 12:40 for every live participant, including a student who taps Take exam at 12:10.

Before the live instant, an eligible student sees the name, description, rules, start, and end. The written question PDF stays hidden. MCQ stems and options stay hidden.

At the live instant the exam is deemed started. The question PDF, the MCQ questions, and the schedule lock at that instant. Edits before it are allowed. Stems, options, and the question PDF stay hidden until the student taps Take exam. After that tap they stay available until that sitting's deadline. The chosen option, the correct option, and the explanation stay hidden until the deadline.

At the end instant the live sitting closes. The exam stays published. Do not set the archived status for that end. An eligible student can still open that exam and retake it. Archive is a separate admin action. An archived module, or a module under an archived category, is hidden from students, including when they know the exam id, and take and retake are rejected. A result that was already posted stays readable by that student. Publish is rejected when the end instant is not still ahead of the server clock.

Inside one module, two published exams need five clear minutes between the earlier end and the later start. An end at 12:40 blocks another start in that module before 12:45. Overlap is blocked. The alert names the other exam and its start and end. A draft may still hold a conflicting time. The check runs at publish and when a published window changes. Exams in another module never block publish, including when the same students are marked on both. A retake is not a published window and does not take part in this check.

### 4.2 MCQ questions

Questions are entered one by one. Each has a stem, two or more options, one correct option, marks, and an optional explanation. Order is the author's order. Do not shuffle. There is no negative mark, no partial mark, and no second correct option.

### 4.3 Written question paper

One PDF, uploaded by a Master Admin or a System Admin. It locks at the live instant. It is not a file download. After Take exam, and until that sitting's deadline, the website may show it inline to that participant, and only while the student is still marked and the module is still published. The response is inline, is not cached, and may be framed only by the website. Before the tap, after the deadline, after the roster row is removed, and after the module is archived or unpublished, the bytes are rejected.

---

## 5. Who may see a module

Only a Master Admin changes the roster. A System Admin receives `403`.

The admin searches registered students by email, phone, or name. Email and phone each match one account. A name may match several. The result shows phone and email, and the admin marks one account. A pasted list accepts email and phone only. Each exact match is marked. A value with no account is shown back and left unmarked.

A marked student can see and take every published exam in that module. A student who is not marked does not see the module.

Tapping Take exam creates the participant row. Removing a student after that tap does not delete the participant row or its result.

---

## 6. The live sitting and the one retake

Opening the upcoming card does not make a participant. Tapping Take exam during the live window does, including when the student never saves an answer.

A student who never tapped during the live window may take the exam once after the window ends. The duration starts on that tap. The attempt is off the live leaderboard. A live participant does not get this retake. There is no second retake.

### 6.1 MCQ

While the sitting is open, the student may change any saved answer. A save replaces the full selection. A question omitted from that save is cleared.

When the sitting's deadline is reached, the server posts the saved answers. The student does not have to call submit for that post. Ninety saved answers out of one hundred are the submission. Nothing saved is a blank paper: every question scores zero and the student fails. MCQ has no "failed to submit" status.

The score, the maximum, and pass or fail are returned as soon as the paper is posted, including an early submit. An early submit does not close the sitting. The student may still change answers until the deadline, and the score at the deadline is the later selection. The chosen option, the correct option, and the explanation wait until that sitting ends. For a live participant that is the shared window end. For the retake it is the end of the personal timer.

The live leaderboard opens five minutes after the live window ends. Until then the student has their own score and does not have the board.

### 6.2 Written answer sheet

The student writes on paper, scans one PDF, and uploads it during the sitting.

- Another upload before the deadline replaces the sheet. The server deletes the previously stored file and keeps only the latest fully stored PDF.
- The ceiling is 80 MB. A larger file is rejected and nothing is stored. An accepted PDF is compressed, and the compressed file is what is stored.
- An upload that is still transferring at the deadline does not count. The last fully stored PDF stands.
- If the deadline arrives with no fully stored PDF, the status is failed to submit. The score is zero, the student fails, and a live participant is on the live leaderboard. The instructor can see that row and does not mark it.
- After the deadline the sheet is locked.

---

## 7. Evaluation

### 7.1 Instructor

`INSTRUCTOR` is a fourth role. The Master Admin creates the account the same way as a System Admin: name, phone, and email, no password, and a one-time activation code. The instructor activates on the admin panel with that phone and code and chooses a password.

The Master Admin assigns one instructor to a module. That instructor can search and browse the assigned modules only, open a written submission, and download the student's answer PDF. A second instructor is not assigned to the same module. An instructor does not create exams, change the roster, set prices, or delete.

A System Admin does not evaluate. A System Admin can read who saved the latest evaluation. The Master Admin may evaluate any written exam in any module, and can download the answer PDF.

### 7.2 Written mark

The marker enters the marks obtained, from 0 through the exam maximum, and may enter a comment. The comment is optional. The marker uploads the evaluated PDF under the same 80 MB compressed-PDF rule.

Saving requires the marks and the evaluated PDF. The student then sees the marks, pass or fail, the comment when one was entered, and the evaluated PDF. The student does not see who evaluated the paper. The admin panel shows the Master Admin or the assigned instructor who saved the latest evaluation.

A later save by that instructor, or by the Master Admin, replaces the marks, the comment, and the evaluated PDF. The previous evaluated file is deleted. Pass or fail and the live leaderboard are recalculated.

The one retake is marked the same way and stays off the live leaderboard.

A written live leaderboard includes a no-submit zero from the deadline. A submitted PDF stays off that board until a mark is saved, then the rank updates.

---

## 8. Leaderboard

Rank uses the exam score, highest first. Equal scores share a rank and the next rank skips. The board shows name, rank, score, maximum, and pass or fail. It does not show a phone number or the evaluator.

A student who never tapped Take exam during the live window is left off the live board. A participant who gave no answer scores zero and is on it.

---

## 9. Permissions

| Action | Master Admin | System Admin | Instructor | Student |
|---|---|---|---|---|
| Create and edit categories, modules, exams | yes | yes | no | no |
| Delete | yes | no | no | no |
| Roster | yes | no | no | no |
| Assign the one instructor | yes | no | no | no |
| Evaluate a written paper | yes, any module | no | assigned module only | no |
| See evaluator name | yes | yes | yes | no |
| Take a published exam | no | no | no | when marked on the module |

---

## 10. What stays out

Shuffling, negative marking, partial credit, a second correct option, extra live attempts, a second retake, a weighted score, a question bank, photo answer pages, a paper that is both MCQ and written, batches, notices, a payment gateway, and proctoring.

Do not put an exam node on a course content tree.

---

## 11. Schema

The previous exam tables (`exam_modules` through `question_banks`, Flyway `V14`–`V23`) stay in place. Do not bend them into this model. New tables start at Flyway `V24`. Package: `com.coachingcenter.api.exams`.

---

## 12. Surfaces the API must be able to serve

These are not built in this phase.

**Student site:** home, login, the list of published exams in marked modules, an MCQ sitting, a written PDF upload, the score, the answer review after the sitting ends, and the live leaderboard when it is open.

**Admin panel:** category tree, module and exam draft / final save / publish, roster search and paste, instructor accounts and the one assignment per module, and written evaluation with the evaluator visible only there.
