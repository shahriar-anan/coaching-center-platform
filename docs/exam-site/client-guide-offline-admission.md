# Offline admission: how it works

This guide is for the SYMAM LAW office: the Master Admin and the System Admins (staff). It explains the new **offline**
part of the site: admitting students to your in-person batches, collecting the course fee in instalments, uploading the batch routine, paying
instructors, publishing exam results, and seeing all of your income and expenses on the **Finance** page.

Nothing changed for students who buy exams online. Existing students, orders and exams stay as they were.

---

## 1. Who does what

| Task | System Admin (staff) | Master Admin |
|---|---|---|
| Create and edit batches | No | Yes |
| Start a desk admission, review forms, send a form back, set up a batch and fee | Yes | Yes |
| **Onboard** a student (the final approval) | No | Yes |
| Record a fee payment | Yes, it waits for the Master Admin's approval | Yes, it counts at once |
| Upload the routine PDF, set instructor rates, add a class | Yes | Yes |
| Record an instructor payment or a "not taken" class | Yes, it waits for approval | Yes, it counts at once |
| Upload an exam result sheet | Yes | Yes |
| **Publish** an exam result | No | Yes |
| See the **Finance** page | No | Yes |

The offline pages are in the staff menu under **Offline admission**: **Admissions**, **Fee approvals**, **Batches**,
**Instructor payments** and **Offline instructors**. **Finance** is under **Money**.

---

## 2. Before you start: create a batch

**Batches → New batch** (Master Admin).

- Give the batch its name in English and Bangla, its start and end dates, the **course fee** (the total a student pays for the
  batch, for example ৳9,000) and the number of **monthly instalments** (filled in from the dates; you can change it).
- Set the **starting roll number**, for example 201001. The first student you onboard gets this roll; later students get the
  next free one. A roll number is never used twice, even across batches.
- Optional: a cover photo, a description, and the **WhatsApp group link**. Students see a "Join the WhatsApp group" button
  once they are admitted.
- **Admissions open** decides whether the batch is shown on the public Admission page, where new students can apply.

---

## 3. Admitting a student: three ways in

### 3.1 A student who walks in (desk admission)

1. **Admissions → Add new student**, and type the student's mobile number.
2. The site shows a **10-minute activation code**. Read it to the student, with the four steps on the screen.
3. On their own phone, the student opens the site, taps **Activate account**, enters the mobile number, the code and a new
   password. They are signed in and taken straight to the **admission form**.
4. The student fills in the form (name, parents' names, date of birth, university, NID, mobile, addresses) and adds a photo,
   then presses **Submit**. Their answers are saved as they type.

If the code runs out, open the admission and press **Send a new code**. A phone number that already has an open admission
is refused, and the message names the batch.

### 3.2 A student who applies online

1. On the public **Admission** page the student sees the open batches as cards and presses **Apply** on one.
2. They fill in the same form, with their email, and submit it. They do not need an account yet.
3. If they apply again with the same email for the same batch, they are told they already applied. If their form was not sent
   yet, they get an email link to finish it.

### 3.3 A student who already has an account

A signed-in student can press **Apply** on a batch card too. The form starts filled in from their account.

---

## 4. Reviewing and onboarding

All new forms appear in **Admissions**, under **Forms to review**.

1. **Review** the form and photo.
2. If something is wrong, press **Send back** and give a reason (quick reasons are offered, such as "Photo unclear"). The
   student is emailed a link to fix the form and send it again. The reason is shown to them exactly as you write it.
3. Press **Edit setup** and choose the **batch**. The admission date and the course end date are filled in. The batch's course
   fee is split evenly into its instalments, shown as a table of due dates and amounts (whole taka, the rest on the last:
   ৳7,000 in 6 is ৳1,166 five times, then ৳1,170).
   - **Change any row:** type its own due date and amount, add an instalment or remove one. The course fee is then the total
     of the rows. For example, ৳10,000 can be ৳4,000, ৳2,000, ৳2,000, ৳1,000 and ৳1,000.
   - **Start again evenly:** type a new course fee, number of instalments or first due date above the table.
   - The dates must go forward, the first can't be before the admission date, and the last must be on or before the course end.
4. Press **Ready for approval**. The Master Admin is told.
5. The **Master Admin** presses **Onboard**. The dialog shows a summary of the fee and dates and the suggested **roll number**,
   which can be changed. Press **Onboard student**.

What happens next depends on how the student came in:

| Student | After onboarding |
|---|---|
| Desk admission | Admitted at once. No code is needed. |
| Online applicant with no account | An **activation code** goes to their email (valid 7 days). They activate with their email and the code, and are admitted. |
| Student with an account (by email) | A **join code** goes to their email (valid 7 days). They enter it on the **Join your batch** page and are admitted. |

**Warnings to watch for:**
- **The phone number belongs to another account:** onboarding is off until the form is sent back and fixed.
- **The account already has this batch:** send the application back, or close it.
- **The code has expired:** use **Send a new code**. The old code stops working.

**Closing an admission** (Master Admin): **Close admission**, with a reason (required). Before onboarding, the application simply
ends. After onboarding, access stops and future unpaid fees are cancelled; fees already past stay owed.

---

## 5. What the student sees

Once admitted, the student's **Offline activities** page shows:

- their **batch**, **roll number** and the **WhatsApp** button;
- the **next class** and the **routine**;
- **fees**: the course fee, each monthly instalment, what is paid, what is overdue, and a **receipt** for every payment;
- **results** of the batch's exams, with the leaderboard.

Students are told in the app (Notices) when they are approved, when the routine changes, when a fee is received or due, and
when a result is published or corrected.

---

## 6. Course fee instalments

**Payments before onboarding:**
- A payment can be taken as soon as the setup has a batch and instalments, before the student is onboarded.
- The admission page shows a **Payments** card with the planned instalments, what is paid and what is still owed.
- When the student is onboarded, the payments are matched to the instalments in order.
- If the plan is changed later, the course fee can't go below what is already paid.

1. Open the student's admission, the **Fees** tab, and press **Record payment**.
2. The amount starts at the next due amount. Choose the method (Cash, bKash, Nagad, Rocket, Bank) and the date it was paid.
3. A **reference** is required. For cash it is filled in for you (for example CASH-20261115); change it if the receipt book has
   its own number.
4. A payment recorded by **staff** waits in **Fee approvals** until the Master Admin approves it. The Master Admin's own payment
   counts at once.
5. Each approved payment gets a numbered **receipt**:
   - when the Master Admin records a payment, **Open receipt** appears straight away;
   - every approved payment on the admission has **Open receipt**;
   - the student can download it too.

**Reminders:** the student is reminded three days before a fee is due, on the day, and three days after if it is still unpaid.
The admins are told about overdue fees, and again each week while they stay unpaid. **Change terms** on the admission changes
the course fee, the number of instalments or the end date. Instalments already paid or already due stay as they are; what is left
of the course fee is spread over the other instalments.

---

## 7. The batch routine

### Uploading it

1. **Batches →** the batch **→ Routines → Upload routine**, and choose the routine PDF.
2. The site reads every class from the PDF and shows a **Check routine** page: date, day, time, subject, topic, instructor and
   kind (lecture or exam).
3. Rows that need a look are marked with the reason, for example "No instructor yet" or "Exam row: check who is paid". Any
   cell can be corrected by clicking it.
4. **Nothing stops you confirming.** Possible problems are listed above the rows as warnings: a date earlier than the row above,
   a date outside the batch's dates, a weekday that does not match its date, or a row with no subject. You can correct any row
   first, or confirm the routine as it is. A row whose date or time can't be read is left out when you confirm, unless you type
   it in.
5. An instructor name the site does not know yet is added as a new instructor when you confirm. If it is someone already in
   the list under another spelling (for example "Milon" for "Milon Hossain"), use **Change** on that row and choose them; every
   row with that name follows, and the site remembers the spelling.
6. Press **Confirm N classes**.

### What the routine does

- The routine counts **from the day you upload it**. Dates before that day are added as earlier classes, and you can still
  change them.
- Uploading a **changed routine** later replaces only the classes from that day onward. Classes already paid or marked
  "not taken", and exams that have a result, are never changed by an upload.
- Students see the routine **without instructor names**, and the PDF they open is a copy with the instructor column removed
  and the SYMAM LAW watermark. They are told when the routine changes.

---

## 8. Paying instructors

1. **Batches →** the batch **→ Instructor rates**: set each instructor's **rate per class** for that batch. Every class takes
   that rate, unless you give a class its own rate (**Edit** on the class, "Rate for this class").
2. To use a substitute, press **Change** on that class and pick, or type, the person who took it.
3. After a class has ended, it appears on **Instructor payments → To settle**, grouped by instructor, with the amount due.
4. Press **Record payment** (the amount, the method, the date paid; a note is needed if the amount differs from the amount
   due), or **Mark not taken** with a reason (instructor absent, class cancelled, holiday).
5. Staff entries wait under **Waiting for approval**. The Master Admin can approve several at once, or reject one with a
   reason. The staff member is told the decision.
6. Paid and not-taken classes move to **History**. Changing a rate later never changes a class already paid.

The admins get one notice a day at 9 pm when held classes are still unsettled, and a weekly reminder on Sundays for classes
waiting more than a week.

**Offline instructors** lists every instructor, their batches, their classes this month and what is owed now.

---

## 9. Exam results

### Uploading a result

1. Each exam is a class of kind **Exam** in the batch, from the routine or added by hand (**Classes → Add class**).
2. **Batches →** the batch **→ Results**, then **Upload result** on the exam (from the exam's date onward).
3. Choose the exam type (**MCQ**, **MCQ + Written** or **Written**), enter the **pass mark** if you use one, and choose the
   Excel sheet (.xlsx), in your usual layout.
4. Press **Check file**. The site reads the sheet and works out each student's marks, rank and pass or fail itself. It shows:
   - full marks and negative marking, read from the sheet;
   - how many rows matched a student, and who is **absent** (expected but not in the sheet);
   - every row where your sheet's own total or position differs from the site's calculation, so you can check them.
5. **One sheet per batch.** A roll number that belongs to a student of **another batch** blocks the upload; remove those rows
   (or upload them to their own batch). A roll that no student has is also refused.
6. Press **Save as staged**. Nothing is shown to students yet.

### Publishing

The **Master Admin** opens the result and presses **Publish**. Every student of the batch is told in the app. Each student sees
their mark, their rank (for example "1st of 50"), pass or fail, and the **leaderboard** of the exam, with names and roll
numbers.

### Correcting a published result

Upload the corrected sheet on the same exam. It is kept as a **new version, staged**; students keep seeing the published
result until the Master Admin publishes the new one. Students are then told "Exam result updated". A staged version can also
be thrown away with **Discard staged**. If another admin's staged version is about to be replaced, the upload says so.

---

## 10. Finance (Master Admin)

**Money → Finance** shows all income and expenses, online and offline, by the date each was paid.

- **Income**: online orders, direct enrolment, offline fees.
- **Expenditure**: staff pay and offline instructor payments.
- Choose the period (this month, last month, this year, all time or custom), tick the sources to include, and pick one batch if
  you like.
- The three cards show **Income**, **Expenditure** and **Net**, compared with the period before. The chart shows each month.
- The tabs show the totals **by source**, **by batch** (fees, instructor cost, net and margin of each batch) and **by
  instructor** (paid, owed and waiting for approval).
- **All entries** lists every amount behind the figures, and **Export CSV** downloads it for Excel.

---

## 11. Two changes for all students

- **Exam times are no longer emailed.** When an exam is published or its time changes, students are told in the app (Notices)
  only. Payment receipts and purchase emails still go out. Please tell your students through an announcement.
- **Written exams: the answer script is saved as a draft.** When a student uploads their PDF, it is saved on the server. They
  can replace it until they press **Submit**. If time runs out first, the last file they uploaded is submitted for them, even if
  they closed the page. A student who uploads nothing is marked as not submitted.
