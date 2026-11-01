# Testing guide: check the sample site from start to finish

Who this is for: you, checking the work from this round in the browser. Follow the steps in order. Each step says what to do, what you should see, and where to note a problem.

What you need: PostgreSQL running on this PC, the `api/.env` file as it is, and a browser (Chrome, a desktop window and a phone-width window from DevTools).

Servers used: API on **http://localhost:8092**, website on **http://localhost:3000**.

How to report a problem: write the item number from this guide, the page, the width (phone or desktop), the language (English or Bangla), and what you saw. Screenshots help.

---

## Part 0: Start from a clean sample database

The sample database is separate from your current dev database, so the dev data is not touched.

**0.1 Build it** (once). In PowerShell, with the sample password chosen by you (8 to 72 characters):

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -U postgres -d postgres -c "CREATE DATABASE coaching_center_sample"
cd F:\coaching-center-platform\api
$env:DB_URL = "jdbc:postgresql://localhost:5432/coaching_center_sample"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SEED_SAMPLE = "true"
$env:SEED_ENABLED = "false"
$env:SEED_DEV = "false"
$env:SEED_SAMPLE_PASSWORD = Read-Host "Sample password"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8092"
```

Wait for the line "Started ApiApplication". Stop it with Ctrl+C.

**0.2 Check the seed loaded.** In a **new** PowerShell window (window A), start the API without the seed settings and leave it running:

```powershell
cd F:\coaching-center-platform\api
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8092"
```

In a third window (window B), run the check script:

```powershell
cd F:\coaching-center-platform
$env:MASTER_ADMIN_PASSWORD = Read-Host "Master Admin password (api/.env)"
.\docs\exam-site\qa\scripts\check-sample-db.ps1 -BaseUrl http://localhost:8092
```

Expected: every line says PASS and the script exits with 0. If a line says FAIL, note it and stop there.

**0.3 Start the website.** In a third window:

```powershell
cd F:\coaching-center-platform\web
npx next dev -p 3000
```

Open http://localhost:3000.

**Expected:** the home page loads with the stats box, and the sample exams are visible.

---

## Part 1: The sample accounts

Sign in at http://localhost:3000/login with the phone number or the email. Password: the sample password you chose (students and staff), or the Master Admin password.

| Who | Phone | Email |
|---|---|---|
| Student 1 (main explorer) | +8801711000011 | student1@example.com |
| Student 2 | +8801711000012 | student2@example.com |
| Student 3 | +8801711000013 | student3@example.com |
| Student 4 | +8801711000014 | student4@example.com |
| Student 5 | +8801711000015 | student5@example.com |
| Student 6 (almost empty) | +8801711000016 | student6@example.com |
| System Admin 1 | +8801711000001 | staff1@example.com |
| Teacher 1 | +8801711000003 | instructor1@example.com |
| Teacher 2 | +8801711000004 | instructor2@example.com |
| Master Admin | +8801700000001 | master@example.com |

**1.1** Sign in as Student 1 and sign out. **Expected:** the header shows the name; sign-out goes to the home page.

---

## Part 2: Visitor site (signed out)

**2.1 Home page.** **Expected:** the banner, then the stats box (874+ students, 2+ mentors, 200+ downloads, 1000+ materials), then the exam categories, the packages, the upcoming exams, "How to start", **What students say** (two cards: a featured exam review and a featured package review), contact, and the closing band "Begin your preparation today".

**2.2 Footer on every page.** Open `/`, `/mcq`, `/packages`, `/login`, `/terms`. **Expected:** the same footer each time: the logo, contact details, company links, the app section, the payment strip, then the social icons and copyright. Not on the exam paper (Part 6).

**2.3 Phone header.** Narrow the window to 390 px. **Expected:** only the logo mark, no name text. A **Menu** button. Open it: eight items in order (Admission form, Exam with MCQ and Written, Packages, Notices, Publications, Blog, Media, Resources). Publications, Blog, Media and Resources show "Coming soon".

**2.4 Exam categories.** Open `/mcq`. **Expected:** "Bangladesh Judicial Service (BJS)" and "Bar Council Enrolment". Open Bar Council Enrolment: its subcategories are Advocate Enrolment Exam and **Short Courses**. Short Courses shows **Coming soon** and no exams (the empty-category case).

**2.5 Packages page.** Open `/packages`. **Expected:** three packages: BJS Preliminary Package (25% off shown), Bar Council Complete Preparation Package (popular badge), Advocate Written Pack. The category filter narrows the list.

**2.6 Visitor cannot see the draft.** Search for "Draft Paper" in `/mcq`. **Expected:** not found (exam 20 is a draft).

---

## Part 3: Student 1 (main explorer)

Sign in as Student 1.

**3.1 Home.** **Expected:** the greeting, then **four doors** in one grid: MCQ, Written, **My orders** and **Packages**. Each door has an icon, a title and one line, and nothing else. My orders opens all your orders; Packages opens the packages page (the same page the landing page's packages "See all" opens). The old "Live now" list is gone. "Next exam" shows the nearest exam; "Coming up" lists upcoming exams.

**3.2 My orders.** Open **My orders** in the menu (it was "My exams"). **Expected:** the title reads "My orders". Two tabs: Exams and Packages.

**3.3 The package P2.** Open "Bar Council Complete Preparation Package" from My orders. **Expected:**
- The Exams tab with an All, Free, Live, Archived pill row above the list (a pill row, not the vertical sidebar).
- An **Announcements** card above the tabs, with the latest announcements for P2 (newest first, three shown, "Show all" for more).
- Tabs: Exams, Exam routine, About, **Reviews**. Open Reviews: the Reviews section with the average 4.7 and the count, and the published comment from another student. Student 1's own review card shows "How was this package?" with a star choice (Student 1 has rated the package 5 already, so it shows the rating and Edit and Delete).
- The rating badge next to the title ("★ 4.7 (3)").

**3.4 The exam page of exam 8 (finished, result passed).** Open Penal Code (exam 8) from P2. **Expected:**
- The side card has **View result** (not "Review answers") and **Reviews (n)**. The Reviews button leads to `/exam/…/reviews`.
- Open Reviews: the reviews page with a back link, the average, the star card for students who sat it, and the approved comments.

**3.5 The result of exam 8.** Click **View result**. **Expected:** the score with passed, and the answers and explanations of the review (the review is open because the exam is finished and answers are released or the time is over).

**3.6 Results tab.** Open **Results** in the menu. **Expected:** three tabs: Results, Being marked, In progress.
- **Results:** exam 1 (answers released), exam 8, exam 12 (published written result, 32 of 50), exam 16.
- **Being marked:** exam 14 (Constitution Written, awaiting marking).
- **In progress:** nothing for Student 1 (the seed has no open sitting).

**3.7 Exam 2 (live, free, MCQ, negative marking).** Student 1 has not sat exam 2, so the exam page offers **Start the paper**. Do not start it yet if you only want to check the layout; Part 6 does that.

**3.8 Leaderboard of exam 1.** Open exam 1 and its leaderboard. **Expected:** the three passing students are listed. The two below the pass mark are not listed (the product lists only passers); staff see the count.

**3.9 Reviews from Student 1.** Open the Rate action from the results page or My orders for a finished exam Student 1 has sat but not rated. **Expected:** the star card asks "How was this exam?". Tap 4 stars: saved at once. Tap "Add a comment", type a comment, save. **Expected:** "Thanks. Your comment will appear after review." The comment does **not** appear publicly until staff approve it (Part 8).

**3.10 Notices.** Open Notices. **Expected:** the Announcements tab shows the all-students announcement, the P2 announcement and the selected-students announcement (Student 1 is one of the selected). A package or exam announcement shows the target and an **Open the package** or **Open the exam** link. A withdrawn announcement (on P1) does **not** appear.

**3.11 Wish list.** Open Favourites. **Expected:** exam 2 and P2 (the seed's wish list).

**3.12 Account and payments.** Open Account, then Payments. **Expected:** the paid orders with receipts (O1 for P2). Staff-only orders do not appear.

---

## Part 4: Student 6 (the empty states)

Sign in as Student 6.

**4.1 Empty states.** **Expected:** My orders shows the empty message "You haven't bought any exams yet" with a way to pick a package. Results shows the empty message. Favourites is empty. Notices shows the welcome announcement only.

**4.2 Pending payment.** Account, Payments: one **pending** order for P3, and one cancelled order for exam 7.

---

## Part 5: Student 2 and Student 3 (access that ends, retake, failed submit)

**5.1 Student 2, exam 17 access.** Sign in as Student 2. Open Criminal Law Mock (exam 17). **Expected:** the access line says it ends in about 3 days. The exam is finished and awaiting marking.

**5.2 Student 2, exam 12 failed to submit.** Results, Results tab: CrPC Written (exam 12) shows the "failed to submit" outcome.

**5.3 Student 3, retake.** Sign in as Student 3. Open CrPC Written (exam 12). **Expected:** the retake grant is shown (a written retake is offered once staff grant it; the seed grants it).

**5.4 Student 3 holds P1 and P3.** My orders: P1 (BJS Preliminary Package, a paid package) and P3 (by enrolment).

---

## Part 6: The MCQ exam paper (the new look)

Sign in as Student 1, open **Penal Code (exam 2)**, and start the paper. Starting it creates a sitting, so do it only when you are ready to check the paper; the sitting closes at 20 minutes.

**6.1 The page.** **Expected:**
- A **white sheet** with black text in both light and dark theme.
- The site header and footer are **not** visible (the paper covers the page).
- A title, "Time: 20 minutes · Full marks: 50 · Wrong answer: −0.25", the student's name and code, and an **Instructions** block open at the top.
- Questions numbered 1, 2, 3 with the marks in square brackets, options as lettered bubbles (A, B, C, D).

**6.2 Choosing an answer.** Tap an option. **Expected:** its bubble fills black with a white letter, the text is bold, and the answered count goes up. Tap another option in the same question: it moves (single answer). Reload: the answer is still there.

**6.3 Flagging.** Tap "Mark". **Expected:** it reads "Marked", and the number in the grid shows a thick outline. Tap again to unmark.

**6.4 The grid.** Open the "Questions" button. **Expected:** numbered squares; answered ones are filled black; tapping a square scrolls to that question.

**6.5 Text size.** Use A− and A+. **Expected:** the question and option text change size. The choice is remembered after a reload.

**6.6 Timer.** **Expected:** the countdown in the bar. In the last minute it inverts (black band, white digits). Warnings at 10, 5 and 1 minutes are spoken by the screen reader only.

**6.7 Submit.** Press **Submit** at the bottom. **Expected:** a dialog with the answered and unanswered counts and the flagged numbers. Confirm. **Expected:** you land on the result page with the score and the note "Your answers and the explanations open at {time}." (the review is closed until the attempt's time runs out). The paper does **not** reopen. Going back to the exam page shows **View result**, not the paper.

**6.8 Results tab after submit.** Open Results. **Expected:** exam 2 appears under **Results** straight away, with the score.

**6.9 Phone width.** Repeat 6.1 at 390 px. **Expected:** the questions and bubbles are still readable; the bottom Submit bar does not cover the last question.

---

## Part 7: The written exam (upload is the submission)

Use Student 3 on the live written exam 15 (CPC Written). Student 3 holds P3 by enrolment, so the exam is open to them. Student 1 does not hold P3, so do not use Student 1 here.

**7.1 Start and upload.** Start CPC Written (exam 15). Upload a PDF. **Expected:** the upload succeeds, and the exam now shows "View submission" on the exam page.

**7.2 No second upload.** Try to upload again. **Expected:** refused: "The answer script was already submitted."

**7.3 Paper not offered.** Open the paper again. **Expected:** the question paper is no longer offered after the upload (during the sitting).

**7.4 Results tab.** Open Results: the written exam is under **Being marked**, not "In progress".

---

## Part 8: Reviews moderation (staff)

Sign in as the **System Admin** (staff1@example.com) or the Master Admin.

**8.1 Sidebar.** Open the Reviews item under "Exams and marking", after Announcements. **Expected:** a count badge for waiting reviews (the seed has at least one waiting comment).

**8.2 Waiting tab.** **Expected:** rows for the waiting comments (exam 1 from Student 2, package P2 from Student 4, and the comment Student 1 writes in 3.9 if you did it). Each row shows the stars, the comment, the student, the target, the date.

**8.3 Approve.** Press Approve on a waiting row. **Expected:** a toast, the row moves to Published, the count drops, and the comment now shows on the exam or package page (Part 3.3 and 3.4).

**8.4 Hide.** Open the Hidden tab: the hidden comment from Student 4 on exam 1 is there with its reason. **Expected:** it does not show publicly.

**8.5 Feature.** On an approved review with a comment, press Feature. **Expected:** it appears in "What students say" on the home page.

**8.6 Filters.** Filter by Type (Package), by a target (pick P2 from the picker), by stars (5), by Featured only, and search by "Student 1". **Expected:** the list and the page address change; Back restores the earlier filter.

**8.7 Teacher view.** Sign in as Teacher 1. **Expected:** the Reviews page shows their own exams' reviews with **no** action buttons and a note that they can only read.

---

## Part 9: Announcements (staff)

Sign in as the Master Admin (or System Admin).

**9.1 The form.** Open Announcements. **Expected:** four audience cards: All students, A package, An exam, Selected students.

**9.2 All students.** Choose All students, write a title, press Send. **Expected:** the confirm dialog says "This will notify about N students" (a number from the preview). Confirm: the toast shows the exact count.

**9.3 A package.** Choose A package, pick P1. **Expected:** the dialog says students who join later will see it on the page. Student 3 holds P1 (paid). Student 5's payment for P1 failed, so Student 5 is not a holder.

**9.4 An exam.** Choose An exam, pick exam 11 (free, Student 5 has started it). **Expected:** the count is the students who have started it (Student 5), since a free exam has no paid holders.

**9.5 Selected students.** Choose Selected students, add Student 1 and Student 2. **Expected:** the count line says 2 students; the send reaches exactly them.

**9.6 History and withdraw.** Open the history. **Expected:** the seeded announcements with their audience and target, the recipients, the date, Sent or Withdrawn. The audience filter narrows the list. Press Withdraw on one, confirm: it reads Withdrawn, and Student 1 no longer sees it in Notices or on the package page.

**9.7 Removed-from-package rule.** Remove an exam from a package, then send an exam announcement to that exam: a student who held the package before the removal still gets it. (Tested in the automated tests; a manual check is optional.)

---

## Part 10: Direct enrolment (staff)

Sign in as the Master Admin. Open **Direct enrolment**.

**10.1 Pick a target.** **Expected:** a search box, a category filter and a type filter, and a list of about six rows that scrolls inside its own box. Type "BJS": the list narrows. Choose "Load more" for more rows. Choosing a row collapses the list to a "Chosen" line with a Change button.

**10.2 Enrol a student.** Pick a student in step 2 (search "Student 4"), add to the list, give a reason, press Enrol. **Expected:** a result for the student. Check the student's My orders. (Student 4 holds P2; use a student who does not hold the package you choose.)

**10.3 Presets.** From a package's menu choose direct enrolment: the package is already the chosen line.

---

## Part 11: Published edits reach students (staff and student)

**11.1 Edit a published exam.** As the Master Admin, open a published exam, change its title and one question stem, and publish the change (through the staged changes dialog).

**11.2 The student sees it without a reload.** Keep Student 1's exam list open in another window. Come back to that window. **Expected:** the new title appears (the page refreshes when you return to it).

**11.3 Reorder questions.** On a published exam with three or more questions, move a question up. **Expected:** the change is staged; publish it; the student sees the new order. A reorder-only change notifies students (decision 1 in the requirements review).

---

## Part 12: Answer key after submit (the rule that was changed)

**12.1 Early submit.** Sign in as Student 6 (who has not sat exam 2) and start exam 2 (a live, free MCQ). Submit early. **Expected:** the result page shows the score and the note; there is no answer key and no explanations until the review opens.

**12.2 Review opens.** After the attempt's time has passed (or when staff release the answers from the exam's screen), reopen the result. **Expected:** the answers and explanations now show.

**12.3 A saved answer after submit.** Go back to the paper of exam 2 in the same tab. **Expected:** the paper does not reopen; the result page is shown instead.

---

## Part 13: Automated checks (for your record)

These run without the browser. Run them when you have time.

- API, full suite: `cd F:\coaching-center-platform\api` then `.\mvnw.cmd test`. Expected: BUILD SUCCESS. Two older payment tests may fail with 409 (course and book payments are out of scope, and this was known before this round).
- Web, full suite: `cd F:\coaching-center-platform\web` then `npx vitest run`. Expected: all test files pass.
- Type check: `npx tsc --noEmit` in `web`. Expected: no output.
- Lint: `npx eslint` in `web`. Expected: only the two known findings in `staff/enrolments/page.tsx` and `student-shell.tsx`.

---

## What is still open (not in this guide's checks)

- **Decisions:** reorder notifies students; the answer key opens after the last live deadline, not the window end; the "Live now" badge on the Next exam card. See `requirements-review.md` section 12.
- **Cleanup:** the other databases on this PC, the old seed code and the old sample scripts are still there. They wait for your go.
- **Staging:** the client's server is unchanged. It waits for your go, because it holds the client's marking and purchases.
- **Not checked in a browser by anyone:** the layout at phone width, the paper's fonts and colours in the browser, and the Bangla wording by a native speaker.
