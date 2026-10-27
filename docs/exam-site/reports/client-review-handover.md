# Exam site: review guide, accounts, rules and user manual

Written for the client. This is a **test copy** of the exam site with made-up data, so you can see every case without setting anything up. Nothing you do here affects the real site.

How to use this guide:

1. **Part 1** gives you the addresses and every test login.
2. **Part 2** lists who may do what.
3. **Part 3** lists the rules the system follows. **Part 4** lists the decisions behind them.
4. **Part 5** is the step-by-step manual for each kind of user.
5. **Part 6** is a short tour of the test data. **Part 7** lists the things we need you to confirm.

---

## Part 1. Addresses and logins

### Two addresses

| | Address | Who uses it |
|---|---|---|
| Student site | https://srv2089893.hstgr.cloud | students |
| Admin site | https://admin.187-52-115-111.nip.io | Master Admin, System Admin, Instructor |

Staff who sign in on the student address are told to use the admin address, and students who sign in on the admin address are told to use the student address. You can sign in with the **phone number** (type it as `01711000011`) or the **email**.

### Test accounts (all five use the same password)

**Password for every test account: `root1234`**

| Role | Name | Phone | Email | Student code |
|---|---|---|---|---|
| System Admin (staff) | ডেমো স্টাফ | 01711000001 | staff@example.com | |
| Instructor | ডেমো ইন্সট্রাক্টর | 01711000002 | instructor@example.com | |
| Student A | সুমন আহমেদ | 01711000011 | student1@example.com | DEMO-STU-001 |
| Student B | রিনা বেগম | 01711000012 | student2@example.com | DEMO-STU-002 |
| Student C | করিম উদ্দিন | 01711000013 | student3@example.com | DEMO-STU-003 |

**Master Admin.** This account is **not** one of the test accounts. It is your own account, created for you at handover, and it signs in on the admin address. Several tasks in this guide can only be done by the Master Admin (publishing, releasing results, prices, instructors, payments, announcements). **"Forgot password" does not work for the Master Admin account, by design.** Keep that password safe. A lost one can only be fixed by whoever manages the server.

These test accounts exist only on this test copy and will be removed before the real launch.

### What each test account is set up with

| Account | What it has |
|---|---|
| **Student A** | Has bought the **BJS Full Mock Package**. One finished MCQ with a published result (35 of 50). One finished written paper with a published result (32 of 50, a comment and a marked PDF). One written paper handed in and still waiting for marks. A new 11 PM exam on each of the next seven nights, alternating MCQ and written. |
| **Student B** | Has bought the **BJS Written Package**. Two written papers handed in, waiting for marks. |
| **Student C** | Has bought **one written exam** only, and has handed it in. A failed payment for the MCQ package, so you can try buying it. |
| **Instructor** | Assigned to the Written package and the Full Mock package. Four written papers are waiting to be marked. |
| **System Admin** | Sees students, payments, packages and exams. |

Four packages are published: **BJS Preliminary MCQ** (3 MCQ exams) and **Bar Council MCQ** (2 MCQ exams), which nobody owns so you can try buying them; **BJS Written** (4 written exams); and **BJS Full Mock** (MCQ and written, with the nightly exams). Two main categories, "BJS Exam" and "Bar Council Exam", hold four sub-categories between them.

**For the admin screens there is more:**

- **Draft packages and exams waiting to be published** (students cannot see them): a package "BJS Mains Package (draft)" that is saved and ready, with a written and an MCQ exam in it; a package "Judicial Service Mock (draft)" that has only just been started; a ready MCQ draft "Penal MCQ (new)" inside the published MCQ package; and a written draft "Evidence written (new)" that still lacks its question paper and date. They are listed on the Participation page, in the drafts panel.
- **Two written papers marked but not yet released** (Student A's and Student B's), one of them flagged "ready for release" by the instructor. They show as "awaiting publish" on the dashboard.
- **Payments in every state:** five paid, two failed, one cancelled and **three pending**. Open "পেমেন্ট" for all of them and "অপেক্ষমাণ পেমেন্ট" for the pending ones.

---

## Part 2. Who can do what

Four kinds of user. A **visitor** is anyone without an account. A visitor can read the public catalog (categories, packages, exam lists), but not questions or PDFs.

| Task | Visitor | Student | Instructor | System Admin | Master Admin |
|---|:-:|:-:|:-:|:-:|:-:|
| Browse the public catalog | yes | yes | yes | yes | yes |
| Create their own account | yes | | | | |
| Buy an exam or a package | | yes | | | |
| Sit an exam | | yes (with access) | | | |
| See their own results | | yes | | | |
| Create or edit categories, packages (modules), exams, questions | | | | yes | yes |
| **Set a price** (package or exam) | | | | no | **yes** |
| **Publish** a package or an exam | | | | no | **yes** |
| Delete or archive a category, package or exam | | | | no | **yes** |
| Assign an instructor to a package | | | | no | **yes** |
| Enrol a student directly in a package (no payment) | | | | no | **yes** |
| Grant or revoke access by hand | | | | no | **yes** |
| Create students | | | | yes | yes |
| Create instructors and System Admins | | | | no | **yes** |
| Suspend or restore students | | | | yes | yes |
| Suspend or delete instructors; delete students and System Admins | | | | no | **yes** |
| See the marking list and open a script | | | own packages only | list only | yes |
| Download an answer script, mark it, save a draft mark | | | **yes** (own packages) | no | yes |
| Mark a script "ready for release" | | | **yes** | no | yes |
| **Release written results** to students | | | no | no | **yes** |
| See payments and pending payments | | | | read only | **yes** (confirm or fail them) |
| Send announcements | | | | no | **yes** |
| Read the activity log and the system status page | | | | no | **yes** |
| Change their own password and phone | | yes | yes | yes | yes |

Notes:

- The **System Admin never evaluates or publishes.** They prepare the catalog and look after students.
- The **Instructor never publishes or releases.** They mark scripts. The Master Admin releases the results.
- **Nobody can set, see or reset another person's password.**
- **Nobody can create, change, suspend or delete the Master Admin** from the panel.
- The admin menu shows each person only what they may open. A missing item is not a fault.

### The admin menu (Bangla / English)

| Menu item | Opens | Who sees it |
|---|---|---|
| ড্যাশবোর্ড / Dashboard | counts, the next 14 days of exams, shortcuts | all staff |
| ক্যাটাগরি / Categories | category tree | Master Admin, System Admin |
| মডিউল / Modules | packages. An instructor sees only theirs, and clicking one opens its scripts | all staff |
| অংশগ্রহণ / Participation | per exam: sitting, handed in, awaiting marks, ready to release | all staff |
| খাতা মূল্যায়ন / Marking | the marking list and the marking screen | Master Admin, Instructor (System Admin reads the list) |
| ঘোষণা / Announcements | send a notice to students | Master Admin |
| শিক্ষার্থী / Students | students, their access, their exam history | Master Admin, System Admin |
| শিক্ষক / Teachers (Instructors) | instructors | Master Admin |
| সিস্টেম অ্যাডমিন / System admins | System Admin accounts | Master Admin |
| পেমেন্ট / Payments | every payment | Master Admin (System Admin reads) |
| অপেক্ষমাণ পেমেন্ট / Pending payments | payments not yet settled | Master Admin (System Admin reads) |
| কার্যক্রম লগ / Activity log | who did what, and when | Master Admin |
| সিস্টেম স্ট্যাটাস / System status | whether the settings are in place | Master Admin |
| আমার অ্যাকাউন্ট / My account | change own password and phone | all staff |

---

## Part 3. The rules

### Accounts and signing in

- **Students register themselves** with name, phone, email and password. The account works at once. There is no SMS code. Confirming the email does not block signing in.
- **Phone:** starts with `01`, eleven digits in all (for example `01711000011`). One account per phone and per email.
- **Password:** 8 to 72 characters.
- **Accounts made by an admin** (students, instructors, System Admins) start **not activated**, with no password. The admin never types the password. A **one-time activation code** is shown once to the admin and also emailed to the person. The person opens "কোড দিয়ে অ্যাকাউন্ট চালু করুন" (Activate with a code) on the sign-in page and enters their phone, the code and their own new password. The code is valid for **7 days**. The admin can send a new one.
- **Wrong passwords:** after **5 wrong attempts within 15 minutes** sign-in is locked for a while. The message never says which part was wrong.
- **Forgot password:** a reset code is emailed and is valid for **10 minutes**. It does not work for the Master Admin.
- **Staying signed in:** the sign-in refreshes itself quietly for up to 30 days. After that, or after signing out, you sign in again.
- **Suspending** an account blocks it on its very next click, even if it is signed in.
- **Google sign-in** is for students only and is switched off for now (the button says "শীঘ্রই", soon).
- **Students who buy need a phone number** on their account. Without one, the buy button asks them to add it.

### Categories, packages and exams

- **Category tree:** a category can have sub-categories, to any depth. Every title has a **Bangla** and an **English** version.
- A **package (module)** sits in one category, has Bangla and English titles and descriptions (**all four required**), and may have a price. A package holds **MCQ and written exams**.
- **Status:** a package and an exam are first a **Draft** (students cannot see it), then **Final saved**, then **Published**. Only the Master Admin publishes. **Archived** items disappear from the student site but keep their posted results.
- **Every edit to something published sends it back to draft.** It must be published again.
- An exam is **MCQ** or **written**, chosen when it is created, and **cannot be changed afterwards**.
- **Publishing an exam needs:** Bangla and English descriptions and rules, a pass percent, at least one question (MCQ) or the question PDF and maximum marks (written), and the final save. The editor's checklist shows what is missing.
- **Every exam lasts at least 2 minutes.**
- An exam may have **no date yet** ("date to be announced"). Students see it with a blank date and cannot take it until a start time is set.
- **Times are Dhaka time.** The end is worked out from the start and the duration.
- **Within one package, a later exam must start at least 5 minutes after the earlier one ends.** Another package may use the same hour.
- **Once an exam's start time has passed**, its schedule, question paper and file cannot be changed.
- An exam **cannot be deleted** once students have sat it or bought it. Archive it instead.
- **A paper's language** (Bangla or English) only changes the typeface of the questions. It does not translate them.
- **Rules text:** one rule per line. Students see each line as a numbered rule.

### Prices and buying

- Only the Master Admin sets prices, on a package and on an exam. **No price means it cannot be bought.** An exam without its own price still comes with its package.
- A student can **buy one exam** (only that exam) or **a whole package**. A package purchase also covers exams added to the package later, with no extra payment.
- Payment goes through **SSLCommerz** (bKash, Nagad, Rocket and cards). On this test copy it is the **sandbox**, so use a test card and never a real one.
- A failed or cancelled payment gives no access. A paid one gives access at once.
- Access can also come from **direct enrolment** by the Master Admin (no payment) or from a **grant by hand**.

### Sitting an exam

- Students can start only **inside the exam's window** (from its start to its end). The **server clock** decides, not the student's phone.
- **Everyone's paper closes at the same moment**, the exam's end time, even if someone starts late.
- **MCQ:** answers can be changed until the end. When the time ends, the saved answers are posted by themselves. A **blank paper scores zero**. A **wrong answer takes nothing off** (no negative marking). The score appears as soon as the paper is posted.
- **Written:** the student sees the question paper (a PDF, with their name on it, no download) and uploads **one PDF** of their answers, up to **80 MB**. A new upload replaces the old one until the end. **No upload at the end means "not submitted" and a score of zero.**
- **Later attempt (practice), MCQ only:** a student who **never started** the exam during its window gets **one** practice attempt later, with their own timer. It is **not on the merit list**. A student who sat the live exam cannot take it. A written exam has **no** later attempt.
- **Merit list:** opens **5 minutes after the exam ends**, live sittings only, highest score first, equal scores share a rank. A written paper appears only after its result is released, except a "not submitted" zero, which is shown at once.
- **Pass mark:** a student passes when their score reaches the exam's pass percent of the full marks. An MCQ's full marks are the total of its question marks. A written exam's full marks are set on the exam.

### Marking and releasing written results

- A script becomes available to mark **after the exam's deadline has passed**.
- The **Instructor** opens the script, gives marks (from 0 to the full marks), may add a comment, and uploads a **marked PDF**. The first save needs the PDF. Later saves may leave it out to keep the stored one. A save is only a **draft**.
- The Instructor may mark a script **"ready for release"**. That is a signal to the Master Admin, not a requirement. Any later save clears it.
- **Students see nothing** (marks, comment, marked PDF) **until the Master Admin releases the result**. The student never sees who marked the paper.
- The Master Admin can **release the whole exam at once**, **release only the papers marked ready**, or **release one paper**. A whole-exam release is **refused while any paper still awaits a mark or any sitting is still open**. A "not submitted" zero needs no mark and does not hold the release up.
- **A correction after release:** if a mark is changed after release, the **student keeps seeing the released mark** until the Master Admin releases the correction. Each release that changes what a student sees sends them a notice.
- **MCQ results need no release.** They are scored at once ("Auto-scored").
- The marked PDF a student downloads has **no watermark**.
- **One instructor per package.** To change, remove the current one first.
- An instructor reads a question paper **only after the exam has ended**, and cannot download it.

### Notices

- Notices are **in the site only** (the bell and the notices page). They are not email, SMS or push messages.
- Students get notices for: a purchase that gave access, a reminder **1 to 2 days before** an exam they can sit, "starting soon" about an hour before, "open now", their **MCQ score**, a **released written result** (including a correction), and **announcements**.
- Only the Master Admin sends announcements, to **all students** or to **the students of one package**. A sent announcement cannot be edited or deleted.
- Email is used for account matters (activation, password reset, confirming an email) and payment receipts. The one-hour-before email is switched off.

---

## Part 4. Decisions that shape the product

These were agreed during the project. If you want one changed, tell us. Some change a lot of the system.

| # | Decision | What it means for you |
|---|---|---|
| 1 | The exam site is a website. The mobile app is not part of this delivery. | Students use the site on a phone or a computer. |
| 2 | Students buy **one exam or a whole package**. A package covers exams added later. | A new exam in a bought package needs no new payment. |
| 3 | The catalog is public. | Anyone can see titles, descriptions, dates and prices. Questions and PDFs stay hidden. |
| 4 | The package page **is** the exam list and the routine. There is no separate routine document. | The date and time of each exam is the schedule. A blank date means "to be announced". |
| 5 | The site language switch covers the site. The question paper keeps the language it was written in. | A Bangla paper stays Bangla when the site is switched to English. |
| 6 | The watermark with the student's name is drawn by the site over the question PDF. | Students cannot download the paper. |
| 7 | **Only the Master Admin publishes** exams and packages, **sets prices**, **assigns instructors** and **releases written results**. | The System Admin prepares; the Master Admin approves. |
| 8 | The System Admin does not grant access by hand, send announcements or confirm payments. They **can** suspend and restore students. | Money and access stay with the Master Admin. |
| 9 | Publishing an exam needs Bangla and English descriptions and rules. | Nothing half-written goes live. |
| 10 | Exams in one package start at least **5 minutes** apart. | No two exams of a package overlap. |
| 11 | An MCQ has **one later attempt**, only for students who never started the live exam. A written exam has none. | Missing the live window is not final for MCQ, but a practice attempt does not rank. |
| 12 | **No negative marking.** A wrong answer adds nothing. | Scores never go below zero. |
| 13 | A written answer is **one PDF**, up to 80 MB, latest upload only. No upload means zero. | Students cannot send several files. |
| 14 | The merit list opens 5 minutes after the exam, live sittings only, ties share a rank. | A practice attempt never ranks. |
| 15 | Written results stay hidden until the **Master Admin releases** them. A later correction does not hide the released mark. | Students never see a half-finished mark, and a correction never makes a result vanish. |
| 16 | An instructor may flag a paper **"ready for release"**. It is a signal, not a gate. | The Master Admin can release without it. |
| 17 | Releasing a whole exam is refused while papers still wait for marks or sittings are open. | A release never leaves someone out by accident. |
| 18 | MCQ results are scored automatically with no release step. | MCQ students see their score as soon as the paper posts. |
| 19 | The student never sees who marked their paper. | Marking stays private. |
| 20 | Each instructor marks the packages assigned to them, and only after the exam has ended. One instructor per package. | Clear responsibility for every script. |
| 21 | Students register with phone and email, with no SMS code. | Registration is quick and costs nothing. |
| 22 | Google sign-in is for students only. | Staff always use a password. |
| 23 | Suspending an account stops it immediately. | A suspended person is locked out on their next click. |
| 24 | Nobody sets another person's password. Passwords are reset by email only. The Master Admin's cannot be reset from the site. | No admin ever knows a user's password. |
| 25 | Notices live in the site, not by email, SMS or push. | Students must open the site to see them. |
| 26 | Payment is SSLCommerz only. Course and book payments are outside this delivery. | Only the exam site is covered here. |
| 27 | An exam can be archived by the Master Admin. It leaves the student site, keeps its results, and cannot be changed. | Old exams can be retired without losing anything. |
| 28 | The exam editor has no separate "Final save" button for exams. Publishing records the final save itself. (Packages keep their Final save button.) | Fewer steps for exams. |
| 29 | The landing page looks like your old site. Every other student page follows the client's mockup. | The two styles are kept apart on purpose. |
| 30 | The admin and student sites have **separate addresses**. | Each audience gets its own front door. |

---

## Part 5. User manual

Menu names are given in Bangla, as shown by default, with the English in brackets. The language button at the top switches between Bangla and English, and the moon or sun button switches the colours.

### 5.1 Everyone

**Sign in**
1. Open the right address (Part 1). Students use the student site, staff use the admin site.
2. Type your **email or phone** and your **password**, then press the sign-in button.
3. Wrong too often? Wait a while and try again.

**Forgot your password** (not available for the Master Admin)
1. On the sign-in page choose "পাসওয়ার্ড ভুলে গেছেন?".
2. Type your email. The page always says "if an account exists, a code was sent". That wording is the same for every address, on purpose.
3. Open the email, then enter the code and a new password within **10 minutes**.

**Activate an account an admin made for you**
1. Your admin gives you a code, or you receive it by email. It is valid for 7 days.
2. On the sign-in page choose "কোড দিয়ে অ্যাকাউন্ট চালু করুন".
3. Enter your phone, the code and a new password. Then sign in.

**Change your password or phone**
- Students: open "অ্যাকাউন্ট" (Account). Staff: open "আমার অ্যাকাউন্ট" (My account).
- Changing the password needs your current password. Changing the phone also needs it.
- The Master Admin changes their own password and phone the same way.

### 5.2 Student guide

The student menu has: হোম (Home), আমার পরীক্ষা (My exams), রেজাল্ট (Results), নোটিশ (Notices) and অ্যাকাউন্ট (Account).

**Create an account**
1. On the student site choose "অ্যাকাউন্ট খুলুন" (Create account).
2. Enter your name, phone (`01…`, eleven digits), email and a password of 8 to 72 characters.
3. You are signed in at once.

**Find an exam and buy it**
1. From the home page choose a door: **এমসিকিউ** (MCQ) or **রিটেন** (written). Or use the menu "এক্সাম" and "প্যাকেজ".
2. Open a category, then a **package**. The package page lists its exams with date, time, type and price.
3. Choose **Buy the exam** (this exam only) or **Buy the whole package** (every exam in it, now and later). If your account has no phone, the page asks you to add one first.
4. You are taken to the SSLCommerz payment page. Pay, and you are returned to the site.
5. When the payment is confirmed the exam or package appears in your list ("আমার মডিউল" on the home page, and "আমার পরীক্ষা"). A failed or cancelled payment gives no access, and you can try again.

**See what is coming**
- The home page and the routine page list your exams, soonest first. A blank date means the date is not announced yet.
- The bell and "নোটিশ" (Notices) show reminders. You get one 1 to 2 days before, and one about an hour before.

**Sit an MCQ exam**
1. Open the exam when its window opens. Choose "Take exam". It only works inside the window.
2. Read each question and choose one option. Your choices are saved as you go, and you can change them until the end.
3. You may submit early to see your score, but the paper stays open until the end time and you can still change answers.
4. When the time ends, your saved answers are posted by themselves. A blank paper scores zero.
5. Your score appears as soon as the paper is posted.

**Sit a written exam**
1. Open the exam inside its window and choose "Take exam". The question paper opens on screen with your name across it. It cannot be downloaded.
2. Write your answers on paper and scan them into **one PDF** (up to 80 MB).
3. Upload the PDF before the end time. Uploading again replaces the earlier file. After the end time no upload is accepted.
4. If you uploaded nothing, the exam is marked "not submitted" and scores zero.
5. Your marks, the teacher's comment and the marked PDF appear **after the Master Admin releases the result**. You get a notice.

**Missed an MCQ?** If you never started it during its window, the exam page offers **one practice attempt** with its own timer. It does not appear on the merit list. A written exam has no practice attempt.

**See your results**
- "রেজাল্ট" (Results) lists your exams. An MCQ shows your score at once. A written exam shows no marks until it is released.
- Open a result to see your score, whether you passed and (once released) the comment and the marked PDF.
- **Merit list:** opens 5 minutes after the exam ends. It shows live sittings only.
- Correct answers and explanations for an MCQ appear in the result only once staff have released the answers for that exam (see Part 7, item 1).

### 5.3 System Admin and Master Admin: creating the catalog

The order is always **category, then package (module), then exam**. The dashboard shows these first steps on an empty site.

#### Create a category or sub-category (ক্যাটাগরি / Categories)
1. Open "ক্যাটাগরি" and choose **"New category"**.
2. Fill in the **English title** and the **Bangla title**. Both are required.
3. **Parent category:** leave "None (top level)" for a main category. To make a sub-category, choose its parent.
4. Save. To add a sub-category later, open the parent and use "Add subcategory".
5. **Archive** hides a category and everything inside it from students (Master Admin only). **Delete** works only when the category has no sub-categories or packages (Master Admin only).

#### Create a package (মডিউল / Modules)
1. Open "মডিউল" and choose **"New module"**.
2. Fill in the English and Bangla **titles** and **descriptions**. All four are required.
3. Choose the **category**. A new category can be created from here.
4. **Price** (৳): only the Master Admin sets it. Leave it blank and the package cannot be bought. Up to two decimals.
5. Choose "Create module". The package page opens with four tabs: **Details**, **Exams**, **Direct enrolment** and **Instructor**.
6. When the details are right, choose **Final save**. When the checklist says everything is in place, the Master Admin chooses **Publish**.
7. A System Admin sees the Publish card read-only ("Only the Master Admin publishes").
8. Saving a published package sends it back to draft. **Archive** makes it read-only and hides it from students. **Delete** asks you to type the Bangla title to confirm (Master Admin only).

#### List the exams in a package (the Exams tab)
- The tab lists each exam with its type, date, status, duration, price and whether it has questions or a paper.
- Each row has an actions menu: **Edit**, **Make a copy** (adds "(copy)" to the title), **Archive** (Master Admin only).
- The links "Participation" and "Merit list" open that exam's reports.

#### Create an exam
1. In the package, open the **Exams** tab and choose **"New exam"**.
2. Choose the **type**, **MCQ** or **written**. It cannot be changed later.
3. Fill in the English and Bangla **titles**, **descriptions** and **rules** (one rule per line), the **paper language** and the **duration** (at least 2 minutes). Choose "Create".
4. The exam editor opens. It has sections, each marked Done, To do or Needs fixing:
   - **Details:** titles, descriptions, rules, paper language, duration.
   - **Schedule and marks:** the start time (Dhaka time), the pass percent (0 to 100) and, for a written exam, the **maximum marks**. Or choose "Date to be announced". The end time shows under the start time.
   - **Questions** (MCQ) or **Question paper** (written).
   - **Price** (Master Admin only).
   - **Check and publish.**
5. Each section has its own **Save**. **"Save all changes"** at the top saves every unsaved section of a draft.
6. After you create an exam, a card asks "Exam saved. What next?": **create another exam**, or **assign an instructor**.

**MCQ questions**
- Choose **Add question**. Enter the question, its **marks** (at least 1), **2 to 6 options**, choose the **one correct option** with the round button, and add an optional **explanation** that students see with their results. Save the question.
- Questions can be moved up or down, edited or deleted. Deleting a question sends a published exam back to draft.
- **Paste many questions:** a faster way. Type or paste in this form, with a blank line between questions: a line starting `প্রশ্ন:`, options written `ক)`, `খ)`, `গ)`, `ঘ)`, and a `*` at the end of the one correct option. The page checks the list first, then creates the questions.
- The exam's full marks are the total of the question marks.

**Written question paper**
- Upload **one PDF** (the page shows the size limit). Students cannot download it; they see it on screen with their name across it.
- Enter the **maximum marks** in the schedule section.
- After the start time the file cannot be replaced.

**Publish an exam (Master Admin)**
1. Open "Check and publish". The list shows anything still missing: descriptions and rules in both languages, the pass percent, at least one question or the PDF with the maximum, and the final save.
2. When it says everything is in place, choose **Publish**. A summary shows how students will see it. Confirm.
3. If another exam in the package is too close, the page names it and the time you must start after.
4. A published exam shows no open items. Any later edit sends it back to draft.

**Preview and retire:** "View as a student" (once published) opens the student's view. An exam can be **archived** (Master Admin): students stop seeing it, its results stay readable, and it can no longer be changed. **Delete exam** works only when nobody has sat or bought it.

#### Assign an instructor, enrol students (Master Admin)
- **Instructor tab:** choose "Assign instructor" and pick one person from the instructor list. A package has **one** instructor. To change it, remove the current one first.
- **Direct enrolment tab:** search a student by name, email or phone and add them, or paste a list (one email or phone per line). They can then sit the package's exams without paying. Removing someone later keeps any result they already have.

### 5.4 Admin: people

#### Create a student (Master Admin or System Admin)
1. Open "শিক্ষার্থী" (Students) and choose **New student**.
2. Enter the name, phone and email. There is **no password field**.
3. A **one-time code** is shown once. Give it to the student, who also receives it by email. They activate the account on the sign-in page (see 5.1).
4. In the student list you can search by name, phone, email or code, **suspend or restore** an account, and **send a new code**. Only the Master Admin can delete a student, and only one with no payment or access.
5. Open a student to see their **exam access** and their **exam history**, and to **add them to a package's direct enrolment**. The Master Admin can also **grant** or **revoke** access by hand there. A reason is required and is kept in the activity log.

#### Create an instructor or a System Admin (Master Admin)
1. Open "শিক্ষক" (Teachers) or "সিস্টেম অ্যাডমিন" (System admins) and choose the **New** button.
2. Enter the name, phone and email. The activation code works as for a student.
3. The instructor list shows each person's status and package. It can filter by status and by "has a package" or "no package".
4. A **suspended** instructor is blocked at once. An instructor who has marked work cannot be deleted. Suspend them instead.

### 5.5 Marking and releasing results

#### Instructor: mark scripts
1. Sign in on the admin site. The dashboard shows how many scripts are waiting. Open "খাতা মূল্যায়ন" (Marking).
2. The list shows scripts of **your packages only**, after their exam's deadline. Filter by "Awaiting marking", "Waiting for release", "Ready for release", "Published" or "Not submitted". Search by student name.
3. Open a script. The page shows the **answer script** and, beside it, the **question paper** (read only, no download). On a phone the page opens on the script, with the mark in a second tab.
4. Enter the **marks** (0 up to the full marks). Add an optional **comment**. Upload the **marked PDF** (needed on the first save).
5. Choose **Save draft mark**, or **Save and next** to go to the next script. The student sees nothing yet.
6. When the paper is finished, choose **Mark ready for release**. Saving again clears this flag. You never release results yourself.
7. A paper that was **not submitted** cannot be marked and needs no mark.
8. Comments you write often can be saved and reused with one click. They stay on this device.

#### Master Admin: release results
1. Open "অংশগ্রহণ" (Participation) or the exam's **results** page. The "Next step" column shows what each exam needs: "to mark", "to release" or "all released".
2. On an exam's results page, the **Release results** panel shows the counts: awaiting marking, sittings still open, ready for release, waiting for release.
3. Choose **Release all results**, or **Release ready papers**, then confirm. Each student gets a notice.
4. If papers still need marks, or sittings are still open, **the whole release is refused** and the counts are shown.
5. To release one paper, open it and choose **Publish result**.
6. **A correction:** if an instructor changes a released mark, the paper shows "Change waiting for release". The student keeps the old mark until you choose **Release the change**.
7. MCQ results are automatic and need no release.

### 5.6 Admin: money, notices and records

**Payments** ("পেমেন্ট"): every payment with its status. **Pending payments** ("অপেক্ষমাণ পেমেন্ট"): payments not yet settled. The Master Admin checks the payment at SSLCommerz, then chooses **Confirm payment** (the student gets access) or **Mark as failed**. A reason is required and is kept in the log. A System Admin can read both lists but cannot change them.

**Announcements** ("ঘোষণা", Master Admin): enter a Bangla and/or English title and message, choose **all students** or **the students of one package**, and send. A sent announcement cannot be edited or deleted.

**Participation** ("অংশগ্রহণ"): one row per exam with the counts of sitting, handed in, not submitted, awaiting marks and released. Filter by package, type and date. A count opens the scripts already filtered.

**Activity log** ("কার্যক্রম লগ", Master Admin): who did what and when. It cannot be changed.

**System status** ("সিস্টেম স্ট্যাটাস", Master Admin): shows whether each setting (payments, mail and so on) is in place. It never shows the values.

---

## Part 6. A short tour of the test data

| Sign in as | Where | Try this |
|---|---|---|
| Student A | student site | Open the results. See a published MCQ and a published written result, with the marked PDF. Open the notices bell. |
| Student A | student site | Look at the routine. There is an 11 PM exam each night for the next seven days. |
| Student B | student site | See two written papers waiting for a result. |
| Student C | student site | Try to buy the **BJS Preliminary MCQ Package** with the sandbox test card. |
| Instructor | admin site | Open Marking. Mark one of the four papers waiting, upload a marked PDF, mark it ready for release. Two other papers are already marked and show as waiting for release. |
| Master Admin | admin site | Open the exam's results page, then release the paper the instructor marked. Sign in again as that student and see it appear. |
| System Admin | admin site | Look at students, payments (five paid, two failed, one cancelled, three pending), packages and exams, and the draft list on the Participation page. Try to publish a draft: it is not offered. |
| Master Admin | admin site | Open "অপেক্ষমাণ পেমেন্ট" and confirm one pending payment, and mark another as failed (a reason is required). The student's access follows. |
| Master Admin | admin site | Publish the ready draft "Penal MCQ (new)", then publish the draft package "BJS Mains Package (draft)" once its exams are ready. |
| Master Admin | admin site | Create a new category, package and exam, publish it, then buy it as a student. |

**Good to know while you look around**

- Every exam is **20 minutes** long.
- The nightly exams are open only from **11:00 PM to 11:20 PM** (Dhaka time). To try a sitting you must be there at that time.
- A written exam must be written, scanned and uploaded **inside** those 20 minutes.
- The nightly exams cover the next seven days. After that, ask Shahriar to reset the test copy. This also resets the marks and purchases made during the review.
- The exam names, descriptions and questions are **drafts** for you to check. Please mark anything that is wrong.

---

## Part 7. Things we need you to confirm

These came out of checking this guide against the system. Please tell us what you want.

| # | Item | Why it matters |
|---|---|---|
| 1 | **Showing correct answers for MCQ.** Students see the correct options and explanations in their results only after staff release the answers for that exam. The switch that does this was removed from the admin screens, so at the moment **no admin can release the answers**, and students see only their score. One seeded exam (BJS Full Mock, Penal MCQ) already has its answers released so you can see how it looks. | We recommend showing the answers automatically when the exam ends, or putting the switch back for the Master Admin. Which do you want? |
| 2 | **MCQ result timing.** The original brief said MCQ results appear after an admin publishes. The agreed rule is that an MCQ score is shown at once, with no release. | Confirm "show at once". |
| 3 | **Risky payments.** SSLCommerz can flag a payment as risky. Today such a payment is **still granted**. | Do you want these held for a person to check? |
| 4 | **Going live.** The test copy uses the SSLCommerz sandbox. When to switch to the live store, and the final address of the exam site, are still open. | Needed before launch. |
| 5 | **One instructor per package.** A package has exactly one instructor. | Is that enough, or do you need several? |
| 6 | **Written exam length.** The test copy uses 20 minutes for every exam. Real written exams are usually longer. | Set the real durations when you create real exams. |
| 7 | **Exam content.** All titles, descriptions, rules and questions here are drafts. | Please check the wording and the legal accuracy of every question. |
| 8 | **Landing page.** The home page follows your old site and every other student page follows your mockup. | Confirm you are happy with that split. |
| 9 | **Data from your current site.** No data has been moved over. | Tell us if any should be. |

---

## Telling us what to change

For each thing, note: which address, which account, which page, what you expected, and what you saw. A screenshot helps. Please give each point a number, so we can answer them one by one.
