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
| Upload the routine PDF, add or edit a class | Yes | Yes |
| Add an offline instructor, record and correct instructor payments | No | Yes |
| Upload an exam result sheet | Yes | Yes |
| **Publish** an exam result | No | Yes |
| See the **Finance** page | No | Yes |

The offline pages are in the staff menu under **Offline admission**: **Admissions**, **Fee approvals** and **Batches**, and, for the
Master Admin only, **Instructor payments** and **Offline instructors**. **Finance** is under **Money**.

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
- **A new batch is a draft.** Students don't see it and it can't be chosen for an admission. When it is ready, the Master Admin
  presses **Publish** on the batch page.
- **A published batch can't be edited.** To change its details, fee, photo or WhatsApp link, press **Move to draft**, edit it, then
  **Publish** again. While it is a draft it leaves the Admission page; students already in the batch keep everything. Routines,
  classes and results can be managed at any time.

---

## 3. Admitting a student: three ways in

### 3.1 A student who walks in (desk admission)

1. **Admissions → Add new student**, and type the student's mobile number.
2. The site shows a **10-minute activation code**. Read it to the student, with the four steps on the screen.
3. On their own phone, the student opens the site, taps **Activate account**, enters the mobile number, the code and a new
   password. They are signed in and taken straight to the **admission form**.
4. The student fills in the form (name, parents' names, date of birth, university, session / batch, mobile, addresses; the NID
   and the emergency number are optional; "Same as present address" copies the address) and adds a photo,
   then presses **Submit**. Their answers are saved as they type.

If the code runs out, open the admission and press **Send a new code**. A phone number that already has an open admission
is refused, and the message names the batch.

### 3.2 A student who applies online

1. On the public **Admission** page the student sees the open batches as cards and presses **Apply** on one. The cards and the
   batch page show the **course fee only**, never the number of instalments.
2. **An account comes first.** A student who is not signed in sees **Create an account** and **Log in**. After either, they come
   straight back to that batch's form, signed in. (The email code can be checked later from their account page.)
3. They fill in the form and submit it, as in 3.3.
4. Someone who started an application before 16 November without an account carries on by creating an account (or logging in)
   with the **same email and mobile number** and pressing **Apply** on the same batch: their application, with what they typed,
   continues under the account.

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
   - **Change an amount:** the course fee stays the same and the instalments after it share what is left. For example,
     ৳12,000 in 2 with the first set to ৳4,000 makes the second ৳8,000. Changing the last one moves the ones before it. If the
     amount is more than the fee allows, the fee grows to the total of the rows instead.
   - **Add or remove an instalment:** the course fee becomes the total of the rows. Due dates can be changed freely.
   - **Start again evenly:** type a new course fee, number of instalments or first due date above the table.
   - The dates must go forward, the first can't be before the admission date, and the last must be on or before the course end.
4. Press **Ready for approval**. The Master Admin is told.
5. The **Master Admin** presses **Onboard**. The dialog shows a summary of the fee and dates and the suggested **roll number**,
   which can be changed. Press **Onboard student**.

What happens next depends on how the student came in:

| Student | After onboarding |
|---|---|
| Desk admission | Admitted at once. No code is needed. |
| Student with an account (they applied while signed in, or the form's email is their account's) | Admitted at once. No code is needed. They are told in the app and by email, and see the batch in their account. |
| Online applicant with no account | An **activation code** goes to their email (valid 7 days). They activate with their email and the code, and are admitted. |

**Join codes are switched off** (since 24 November). Students with an account used to get a join code to type in before they were
admitted; now **Onboard** admits them straight away. If a student was sent a join code before this change and has not used it, the
admission shows that the code went out: the code still works, or you can press **Onboard** to admit the student now, and the old
code then stops working. Join codes can be switched back on later if you need them.

**Warnings to watch for:**
- **The mobile number or email is on another person's admission in the same batch:** onboarding is off until the form is sent
  back and fixed, or the other admission is closed. This is the only phone or email clash that stops onboarding.
- **The mobile number belongs to an account outside the batch:** onboarding goes ahead. The new account is made without a phone,
  and the student signs in with their email and the activation code.
- **The account already has this batch:** send the application back, or close it.
- **The activation code has expired:** use **Send a new code**. The old code stops working.

**Closing an admission** (Master Admin): **Close admission**, with a reason (required). Before onboarding, the application simply
ends. After onboarding, access stops and future unpaid fees are cancelled; fees already past stay owed. A closed admission keeps
its roll.

**Deleting a student from a batch** (Master Admin): at the bottom of the admission page, **Delete from batch**, with a reason.

- The **roll becomes free** at once, and it is the first roll suggested for the next student.
- Access to the batch's exams and packages stops, and every unpaid fee is cancelled.
- The admission leaves every list (Admissions, the batch's students, results, reminders).
- **Payments and published results stay**, so Finance does not change.
- The student's account is not touched. They are told in the app that the admission is closed.

**A student account with an open admission cannot be deleted.** Close the admission or delete it from the batch first. If an
account was deleted before this rule, its admission shows **Account deleted**; close it or delete it from the batch.

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
5. An instructor name the site does not know is **not** added as an instructor: the class keeps the name as the routine writes it.
   If it is someone already in the list under another spelling (for example "Milon" for "Milon Hossain"), use **Change** on that row
   and choose them; every row with that name follows, and the site remembers the spelling. A new instructor is added by the Master
   Admin on **Offline instructors** (section 8).
6. Press **Confirm N classes**.

**An exam batch's routine** (a table of Exam, Date, Exam Topics and Time, such as the 20th MCQ Exam Batch routine):

- Tick **This is an exam batch routine** in the upload dialog and set the exam time (for example 4:00 PM, 120 minutes). Every exam
  gets that time unless its row has its own.
- Each exam (Exam-01, Exam-02 …) becomes an exam class on its date. Results are uploaded against these exams as usual.
- No instructor is set from the upload. After confirming, you can set the instructor of each exam from the batch's **Classes**.
- Students see the exam number, date and time in their routine, and the full topics in the routine PDF. The topics are not
  copied, because the Bangla in this PDF does not come out as the text it shows.
- A new upload matches each exam by its number, so an exam whose date moves keeps its class and its result.

### What the routine does

- The routine counts **from the day you upload it**. Dates before that day are added as earlier classes, and you can still
  change them.
- Uploading a **changed routine** later replaces only the classes from that day onward. Exams that have a result, and classes paid
  or marked "not taken" before 24 November, are never changed by an upload.
- Students see the routine **without instructor names**, and the PDF they open is a copy with the instructor column removed
  and the SYMAM LAW watermark. They are told when the routine changes.

---

## 8. Paying instructors (Master Admin)

**Since 24 November, instructor pay no longer comes from the classes.** Uploading a routine or adding a class adds nothing to pay:
there are no instructor rates, nothing is "owed", and classes are not marked paid or "not taken". The Master Admin records each payment
by hand. System Admins do not see these pages.

**Offline instructors** lists every instructor: their phone, the batches they teach, what they were paid this month and their last
payment. **Add instructor** adds one; **Edit** changes the name, phone or the other names the routine uses; **Archive** removes one who
no longer teaches (not while a class still names them).

Open an instructor to see what they were paid in total and this month, their last payment and their batches, and two tabs:

**Record payment**

1. The **date** is today; change it for a payment made on another day (not a future one).
2. Choose the **type**: Lecture, Exam or Other.
3. The **number of classes** is optional and only for the record, for example 4 for four lectures. The amount is not worked out from it.
4. Enter the **amount**, the **method** (Cash, bKash, Nagad, Rocket, Bank) and, if you have one, a **reference**.
5. Choose a **batch** if the payment belongs to one: Finance then counts it in that batch's instructor cost.
6. A **description** says what the payment is for. It is required for Other (for example travel, a bonus or an advance).
7. Press **Record payment**. It counts at once.

**Payment history** lists every payment to the instructor, newest first, with a total. Payments for classes made before 24 November are
there too, shown as "Class: subject on date".

- **Edit** corrects a payment. Say why: the reason is kept in **Payment records** under **Correction history**. A payment from before
  24 November can change only its date, amount, method, reference and note.
- **Void** removes a payment made by mistake, with a reason. It leaves Finance and the totals, and cannot be edited again.

**Instructor payments** shows what was paid this month, last month and this year, and every payment to every instructor. Choose the
dates (this month to start with), a batch, an instructor or a type. Each row opens its instructor.

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
  instructor** (what each was paid, and in how many payments).
- **All entries** lists every amount behind the figures, and **Export CSV** downloads it for Excel.

### Correcting a recorded payment

**Money → Payment records** lists every recorded payment: course fees, and (Master Admin only) instructor payments and direct
enrolment payments.

- **Filters**: today, this month, last month or custom dates; the kind; a batch; an instructor; a search by name, reference or
  receipt number.
- **Edit** a payment to change its amount, date, method, reference or note. Say why: the reason is kept.
- **A System Admin's correction waits** for the Master Admin. Until then the payment and Finance stay as they were, and the row
  says **Correction waiting**.
- **The Master Admin's own correction applies at once.**
- **Waiting corrections** (the second tab) shows each waiting correction with the old and new values. The Master Admin presses
  **Finalize** or **Reject** (with a reason). The admin who asked gets a notification either way.
- **Correction history** lists the finalized and rejected ones.
- Once finalized, **Finance, the fee pages and the instructor pages all show the new amount**. A corrected fee receipt keeps its
  number and says **Corrected on** with the date.
- Limits: a fee payment cannot be more than the student owes. On a direct enrolment plan, only the latest payment's amount can
  change.

---

## 11. A student who forgot their password

While email does not arrive: open the student under **People → Students**, press **Password reset code** (Master Admin), and
give the code to the student. On the sign-in page they choose **Forgot password**, then **"I already have a code from the
office"**, and type the code with a new password within 10 minutes. They should not press **Send code**, which replaces it.

---

## 12. Two changes for all students

- **Exam times are no longer emailed.** When an exam is published or its time changes, students are told in the app (Notices)
  only. Payment receipts and purchase emails still go out. Please tell your students through an announcement.
- **Written exams: the answer script is saved as a draft.** When a student uploads their PDF, it is saved on the server. They
  can replace it until they press **Submit**. If time runs out first, the last file they uploaded is submitted for them, even if
  they closed the page. A student who uploads nothing is marked as not submitted.
