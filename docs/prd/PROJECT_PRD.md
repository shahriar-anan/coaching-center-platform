# Product Requirements Document (PRD)
## Coaching Center Management & Learning Platform

**Version:** 0.4 (supersedes v0.3 and v0.2)
**Status:** Draft. Assumptions reviewed and answered by the product owner; items still marked ⚠ need a decision (see Section 14.3)
**Last updated:** 2026-09-24

---

## 0. What changed

| Area | v0.2 | v0.3 |
|---|---|---|
| Platforms | Android + iOS + web | **Android + web admin for MVP. iOS later.** |
| Web app | Admin panel | Admin panel **only**. All student features live in the Android app. |
| Roles | Master/System Admin plus Super Admin/Admin/Teacher/Evaluator | **Three roles: Master Admin, System Admin, Student.** Others deferred. |
| Contradictions | Payments and attendance both "in MVP" and "out of scope" | Both are **in MVP**. Section 4 of v0.2 is replaced. |
| Auth | Spring Security or Firebase Auth; Google and Facebook login | **Own JWT auth (Spring Security).** Facebook dropped. Google login is optional/stretch. FCM used for push only. |
| Lectures | Unspecified | **Unlisted YouTube videos played inside the app for the MVP**, plus PDF notes. No live classes. |
| Library | Not present | **New module:** students order physical books online. |
| Analytics | Out of scope | **Basic Master Admin analytics in MVP** (payments, attendance, student growth). |
| Attendance | Mechanism undecided | **QR scan for physical sessions, button for online sessions, for all student types.** |
| Course access | Undecided | **Per-course access duration set by admin.** |
| File storage | S3-compatible | **VPS local disk for MVP**, behind an interface so cloud storage can be swapped in. |
| Payments | bKash and Nagad gateways | **Gateway abstraction + manual TrxID verification first**; gateway integration when merchant credentials arrive. |
| Exam rules | Open | **No negative marking, one attempt. Written submissions are final once submitted.** |
| Delete access | Unspecified | **Master Admin only. System Admin can create/edit where granted but can never delete.** |

**Changes in v0.4 (after assumption review)**

| Area | Change |
|---|---|
| Attendance | Applies to **all** student types. Online students mark attendance with a button; physical sessions use QR. |
| Course structure | Fixed Subject/Chapter hierarchy replaced by a **flexible content tree** of variable depth. |
| Passwords | **Only the user controls their password.** Admin-set temporary passwords and admin resets are removed. Replaced by one-time activation codes and email-based recovery. |
| Lecture video | **Unlisted YouTube for the MVP** (confirmed with the client). Built behind a `VideoProvider` interface so a protected provider can replace it later. |
| Library | Book stock tracking removed. |
| Admin accounts | Exactly **one Master Admin**; unlimited System Admins. |
| Language | UI in **Bangla and English** (content is not translated). |
| Attendance visibility | Students see their attendance percentage. It has no effect on anything. No automatic attendance/expiry notifications. |
| Storage | No retention policy for now. |
| Confirmed | Assumptions 1, 4 to 8, 10, 12 confirmed (Section 14.1). |

---

## 1. Product Overview

A commercial, course-based coaching platform for a coaching center with roughly 600 to 700 students today and expected growth to about 10,000.

- **Student Android app (Flutter):** discover and buy courses, watch lectures, read notes and materials, take MCQ exams, submit written exams, view results, mark attendance, read notices, receive push notifications, order library books.
- **Admin web panel (Next.js):** manage everything the students see, plus payments, attendance, analytics and admin accounts.
- **Backend (Spring Boot, Java 21):** one REST API serving both clients.
- **Database:** PostgreSQL.

The platform serves online students, offline (physical) students, and hybrid students in one system.

## 2. Goals

1. Digitize the coaching center's core academic and commercial workflows.
2. Give students one Android app for courses, exams, materials, notices, results, attendance and book orders.
3. Let staff run the center from a single admin panel without developer intervention.
4. Support both online purchases and offline admissions in one enrollment model.
5. Give the Master Admin visibility into revenue, attendance and student growth.
6. Provide a foundation that scales from about 600 to 10,000+ students without redesign.
7. Keep student data, exam data and financial data secure, with permissions enforced in the backend.

## 3. Scope

### 3.1 In MVP

**Student Android app**
- Authentication and account management
- Dashboard
- Course discovery, purchase and enrollment
- My Courses: lectures (YouTube video), PDF notes, exercises, materials
- MCQ exams
- Written exams (photo upload)
- Results
- Attendance (QR scan or button)
- Library (book ordering)
- Notices and push notifications
- Profile, settings, help

**Admin web panel**
- Admin authentication and role-based permissions
- Dashboard and analytics (Master Admin)
- Student, batch and enrollment management (including offline admission)
- Course, subject, chapter, lecture and material management
- Attendance session management
- Exam and question bank management, written submission evaluation, result publishing
- Payment and purchase management
- Library management (books and orders)
- Notice and notification management
- Admin account management, audit logs

**Backend**
- Auth, REST APIs, business logic, PostgreSQL, file storage, FCM integration, audit logging

### 3.2 Out of MVP

- iOS app and App Store in-app-purchase compliance
- Web version of the student experience
- Live classes, video hosting/DRM, in-app chat, parent portal
- Digital books or in-app book reading, book delivery tracking, shipping fee calculation
- Coupons and discounts
- Automated refunds
- Facebook login; SMS OTP verification
- Attendance effects on eligibility or results, and automatic attendance/expiry notifications
- Certificates, AI tutoring, advanced BI
- Teacher, Evaluator and Super Admin roles
- Migration of legacy data from the old system (done after the build)
- Cloud/S3 storage (planned upgrade path)
- DRM or protected video hosting (planned upgrade path)

---

## 4. Roles and Permissions

Permissions are enforced **in the backend authorization layer**, never only hidden in the UI.

### 4.1 Student
Access their enrolled courses, materials and exams, submit exams, view published results, mark attendance, read notices, order books, manage permitted profile fields.

### 4.2 Master Admin
Highest role in the MVP. Full operational and financial authority, including pricing, payments, analytics and admin account management. There is exactly **one** Master Admin account (created during deployment; no API creates another). System Admin accounts are unlimited.

### 4.3 System Admin
Operational role. Cannot control money: no pricing, no payment changes, no financial reports. Can create and edit permitted entities but has **no delete access** of any kind (see 4.5).

### 4.4 Permission matrix (confirmed for the MVP; may change with client requirements)

| Capability | Master Admin | System Admin |
|---|---|---|
| View / create / edit students, activate / deactivate | Yes | Yes |
| Create batches | Yes | Yes |
| Create offline admission (enrollment) | Yes | Yes |
| Record offline/cash payment amount | Yes | No |
| Extend or change enrollment access expiry | Yes | No |
| Create / edit course content (subjects, lectures, materials) | Yes | Yes |
| Set course price and access duration | Yes | No |
| Create / manage exams and questions | Yes | Yes |
| Evaluate written exams | Yes | Yes (confirmed) |
| Publish / unpublish results | Yes | Yes (confirmed) |
| Open attendance sessions, correct attendance | Yes | Yes |
| Notices and notifications | Yes | Yes |
| View payments and who bought what | Yes | Yes (view only) |
| Verify manual payments, cancel, refund, edit payment records | Yes | No |
| Payment gateway configuration | Yes | No |
| Library: manage books and handle order fulfillment | Yes | Yes |
| Library: set book price, mark cash received | Yes | No |
| Analytics: financial | Yes | No |
| Analytics: non-financial operational dashboard | Yes | Yes |
| Create/manage admin accounts, view audit logs | Yes | No |
| **Delete** any entity (students, batches, courses, lectures, materials, exams, questions, notices, books, sessions, etc.) | Yes | **No (confirmed)** |

### 4.5 Delete rule (confirmed)

- **Only the Master Admin can delete.** This applies to every entity in the system: students, batches, courses, subjects, chapters, lectures, materials, exams, questions, notices, books, attendance sessions and any other record.
- The Master Admin may grant the System Admin **create and edit** access on permitted entities, but **never delete access**.
- "Delete" includes archiving, removing, and any soft-delete action that hides a record from normal use. It does not include reversible state changes such as activating/deactivating a student, unpublishing an exam or closing an attendance session.
- The rule is enforced in the **backend authorization layer** (a System Admin request to any delete endpoint returns `403 Forbidden`), and the delete controls are also hidden in the web UI.
- Deletions are **soft deletes by default** (record flagged as deleted and hidden) so that historical academic, attendance and financial records are preserved. Permanent purging of exam, payment or attendance history is not exposed in the UI ⚠.
- Every deletion is written to the audit log with user, entity, timestamp and metadata.

---

## 5. Student Android App

### 5.1 Authentication and Account

**Principle (confirmed): passwords are controlled by the user alone.** No admin, including the Master Admin, can set, see or reset anyone's password. Passwords are stored only as salted hashes.

- Login with **phone number or email plus password**. Phone number is mandatory and unique. It is not OTP-verified in the MVP.
- **Email is required for students** ⚠, because it is the password-recovery channel (every Android user already has a Google account, so this is a small burden). Email is verified with a short-lived code or link at registration ⚠, so a mistyped address cannot silently break recovery.
- **Self-registration** for students who buy online: name, phone, email, password.
- **Admin-created accounts** (offline admissions and System Admins): the admin enters name, phone and email **without any password**. The system generates a **one-time activation code** (valid for a limited time, for example 7 days). The admin hands the code to the student in person. The student enters phone plus code in the app and **sets their own password**. The admin never sees or types a password.
- **Forgot password:** self-service through an emailed one-time reset link or code.
- **Lost access to email:** the admin can **re-issue an activation code** after checking the student's identity. The student then sets a new password themselves. ⚠
- **Change password:** logged-in user, requires the current password.
- If a self-registered account and an offline admission share the same phone number, the admin **links** the enrollment to the existing account instead of creating a duplicate.
- The single Master Admin is created during deployment (bootstrap) with an initial password from the environment, changed on first login. Because there is only one Master Admin, a documented developer-run recovery procedure is required in addition to email reset.
- Google sign-in is optional/stretch. Facebook is not included.
- Admin controls which profile fields students can edit.

### 5.2 Dashboard
Greeting, enrolled courses (with expiry warnings), upcoming exams, recent notices, latest results, notification indicator. Show frequently used items only.

### 5.3 Course Discovery, Purchase and Enrollment
- Course list and details: title, description, image, duration, price, delivery type (Online / Offline / Hybrid), contents summary, access duration, purchase status.
- Students may buy multiple courses. Prices are fixed per course. No coupons or discounts in the MVP.
- **Purchase flow:** select course → choose bKash or Nagad → payment → verification → enrollment created → course appears in My Courses.
- Access is granted only after **verified payment**, or when an authorized admin enrolls the student manually.
- **Access duration:** each course has an admin-defined access period (days or fixed end date). The enrollment stores `expires_at`. Expired courses remain visible with history but content is locked. Admin can extend access.

### 5.4 My Courses and Lectures

**Flexible structure (confirmed).** Courses differ in depth: some are Course → Subject → Chapter → Lecture, others are simply Course → Lecture. The content model is therefore a **tree**, not fixed levels.

- A course contains a tree of **sections** (containers the admin names freely, such as subject, chapter, module or week) and **lectures** (leaves).
- Sections can contain other sections or lectures. Lectures can also sit directly under the course.
- Maximum depth: 5 levels ⚠, to keep in-app navigation usable.
- Items are ordered by position within their parent. Admins can reorder and move them.
- Materials and exams can be attached to the course or to any section.
- The app shows the tree as a drill-down list, so shallow courses look simple and deep courses stay navigable.

A **lecture** contains: title, YouTube video, lecture notes, PDF resources and exercise questions. No live classes.

#### 5.4.1 Lecture video (YouTube for the MVP)

**Decision (confirmed with the client):** lectures use **unlisted YouTube videos** played inside the app with the YouTube IFrame player. No API key is needed for playback. Videos must be set to Unlisted (not Public) with embedding allowed.

**Known limitations, accepted for the MVP:**
- Anyone who has a video link can watch and share it outside the app.
- Access is controlled only inside the app and API. The backend returns a lecture's video ID only to students with an active enrollment and stops returning it after the enrollment expires. A student who saved the link can still open it on YouTube.
- Downloading and screen recording are not prevented.

**Design:**
- Each lecture stores `video_provider` (`YOUTUBE`) and `video_ref` (the video ID) behind a `VideoProvider` interface. A protected provider (for example VdoCipher or Bunny Stream) with short-lived playback tokens can replace it later without changing the data model.
- Video IDs are returned only through authenticated endpoints, never in public course listings.
- The admin panel accepts a YouTube URL or ID and validates and extracts the video ID.
- Optional low-cost measure: block screenshots on the lecture screen with the Android secure-window flag. Test this with the embedded player before relying on it.

**Upgrade trigger:** revisit a protected provider if link sharing or piracy becomes a real problem. Cost would depend on total viewing hours.

### 5.5 Materials
- Types: PDF, JPG/JPEG, PNG.
- Organized by course, subject, chapter, type. Searchable and filterable.
- Files are served through **authenticated endpoints only**, never public or guessable URLs. Students can access only materials for courses they are actively enrolled in.

### 5.6 MCQ Exams
- Pre-exam screen: title, course, time window, duration, question count, total marks, instructions.
- During exam: question navigation, answer selection and change, question status indicators, timer, review screen.
- **Rules (confirmed): no negative marking; one attempt per student.**
- Timer and window are **server-authoritative**. Local state with periodic autosave; the exam auto-submits when time expires.
- Automatic evaluation on submission.
- Result detail visibility (score only vs. correct answers) is configurable per exam. Results are visible only after admin publication unless the exam is configured otherwise.

### 5.7 Written Exams
- Student reads instructions and deadline, writes on paper, photographs pages (camera or gallery), reviews, reorders or removes images, then confirms submission.
- Limits: up to 10 images per submission; images compressed on the phone (about 1600px longest side, JPEG quality about 70); server rejects images over 3 MB.
- States: `DRAFT → SUBMITTED → UNDER_REVIEW → EVALUATED` (result then published with the exam results). "Not started" is derived from absence of a submission.
- **Confirmed: once submitted, a written exam is final.** No resubmission and no admin reopen. Students can still retry uploads while in `DRAFT`.
- Deadline enforced by the server.

### 5.8 Results
List of results per exam: marks, total, percentage, status. Details (teacher feedback, written marks, remarks) shown per admin settings. No ranking or grade system in MVP.

### 5.9 Notices and Notifications
- Notices: title, description, date, attachments, priority, target (all, course, batch).
- Push via **FCM**, with deep links (exam, notice, material, result, order). Admin chooses per notice whether to send a push.

### 5.10 Attendance

Applies to **every student type** (confirmed): offline, online and hybrid.

**Sessions.** An admin creates a class session (course, batch, date/time, **method: QR or BUTTON**) and opens it. Closing the session marks anyone who did not check in as `ABSENT`.

**QR method (physical classes)**
1. The admin panel displays a QR code containing the session ID plus a **short-lived signed token that rotates every 30 to 60 seconds**, so screenshots shared over messaging apps stop working.
2. The student scans it in the app.
3. The backend validates the token, the open session, the student's active enrollment in that course/batch, and that no record exists yet.

**Button method (online classes)**
1. While the session is open, eligible students see a **"Mark attendance"** button. No QR is needed.
2. The backend validates that the session is open (server time), the student has an active enrollment, and no record exists yet.
3. Known limitation: a button can be pressed without actually watching. This is accepted for the MVP. The open window is enforced by the server.

**Eligibility by delivery mode** ⚠: OFFLINE students use QR sessions, ONLINE students use button sessions, HYBRID students can use either.

**Statuses:** Present, Absent, Late (after an admin-defined threshold), Excused.
**Admin corrections:** allowed for both admin roles and audit-logged.
**Student view:** attendance history and **attendance percentage per course**. It is informational only and affects no eligibility, results or notifications.

### 5.11 Library (Book Purchase)
Physical books only. No digital reading, no delivery tracking, no shipping fee calculation.

- Student browses books (cover, title, author, price, availability).
- Student places an order and provides contact phone and, if delivery is wanted, an address.
- **Fulfillment:** `PICKUP` or `DELIVERY`, handled manually by admins.
- **Payment method (confirmed): online (bKash/Nagad) or cash on pickup.**

**Order statuses**
- Online: `PLACED → PAID → READY → DELIVERED`
- Cash: `PLACED → READY → COLLECTED` (admin marks cash received)
- Either: `CANCELLED`

No stock tracking in the MVP (confirmed). Admins simply mark a book available or unavailable. Book orders reuse the same payment module as courses.

### 5.12 Profile, Settings, Help
Profile view/edit (permitted fields), password change, notification preferences, privacy policy and terms, logout, contact information, FAQ, issue reporting.

---

## 6. Admin Web Panel

Desktop-first web app (Next.js). Language ⚠ (see open questions).

### 6.1 Dashboard and Analytics
**Master Admin (MVP analytics)**
- **Payments:** total revenue, revenue by period, by course, by payment method (bKash / Nagad / manual / cash), pending, failed and refunded payments, book order revenue.
- **Student growth:** new students per month, total and active students, enrollments per course, online vs. offline admission split, expiring enrollments.
- **Attendance:** attendance rate by course and batch, per-session attendance, students below an attendance threshold.
- **Academic (light):** upcoming exams, pending written submissions, average exam scores.

**System Admin:** operational dashboard only (no financial figures).

### 6.2 Student, Batch and Enrollment Management
- Student list, search, filters, profile, add/edit, activate/deactivate, view exam history, results, attendance, enrollments.
- Batches: create, edit, archive, assign students and courses.
- **Enrollment types:** `ONLINE_PURCHASE`, `OFFLINE_ADMISSION`.
- **Delivery modes:** `ONLINE`, `OFFLINE`, `HYBRID`.
- A student may have multiple enrollments over time. Each has status, expiry and delivery mode.
- Offline workflow: admission at the center → admin creates or links student account → creates enrollment → course appears in the app → attendance and academic activity tracked centrally. No online payment is required.

### 6.3 Course Management
Create/edit/archive courses; description, image, price (Master Admin), access duration (Master Admin), delivery mode; the flexible content tree (sections and lectures at any depth), lectures (YouTube video link, notes, PDFs, exercises); materials.

### 6.4 Attendance Management
Create and open/close class sessions (QR or button method), display the rotating QR for QR sessions, view live attendance count, manual marking and corrections, attendance history and reports by student, course and batch.

### 6.5 Exam Management
- Create exams: title, description, type (MCQ/Written), course/batch target, window, duration, total marks, result visibility, instructions.
- States: `DRAFT → SCHEDULED → ACTIVE → ENDED → RESULT_PUBLISHED → ARCHIVED` (ACTIVE/ENDED derived from time).
- **Question bank** independent of exams: text, optional image, options, correct answer, marks, category (from a flexible category tree independent of the course tree), explanation. Reusable across exams. Archive instead of delete.
- Build exams from bank questions; reorder; preview.
- **Written submission review:** pending list, filters, image viewer with page navigation, marks and feedback entry, save, mark evaluated.
- Result management: review, publish/unpublish (both admin roles), student-wise and exam-wise views.

### 6.6 Payments and Purchases
See Section 7. Payment list and detail, manual verification queue (Master Admin), purchase history per student, reports.

### 6.7 Library Management
Book catalog (title, author, cover, price, available flag), order list with filters, status updates, cash-received marking (Master Admin).

### 6.8 Notices and Notifications
Create/edit/draft/schedule/archive notices, target audience, attachments, importance flag, optional push. Notifications linked to a source object (exam, notice, material, result, order).

### 6.9 Administration
Master Admin only: create admin accounts, assign Master/System role, activate/deactivate, reset access, view audit logs.

### 6.10 Audit Logging
Log: all deletions, student account changes, enrollment and access-expiry changes, payment verification/edits, exam publish/cancel, question edits, result edits and publication, attendance corrections, notice publication, admin role changes. Record user, action, target, timestamp, metadata.

---

## 7. Payments

Applies to **course purchases** and **online book orders** through one generic payment module (`payable_type`: `COURSE_ENROLLMENT` | `BOOK_ORDER`).

### 7.1 Delivery approach
The merchant credentials for bKash and Nagad are not available yet, and onboarding is outside the developer's control.

- **Phase A, manual verification (ships with MVP):** student pays to the center's bKash/Nagad number and submits the TrxID and amount in the app. The payment is `PENDING`. A Master Admin verifies and marks it `SUCCESS`, which triggers enrollment or order confirmation.
- **Phase B, gateway integration:** implement `PaymentProvider` implementations for bKash and Nagad once sandbox/production credentials are available. The rest of the system does not change.
- The client should begin merchant onboarding immediately.

### 7.2 Payment record
Student, payable type and ID, amount, method (`BKASH`, `NAGAD`, `MANUAL_BKASH`, `MANUAL_NAGAD`, `CASH`), transaction/reference ID, status, initiated/successful timestamps, failure reason, verified-by admin.

States: `INITIATED`, `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `REFUNDED`.

### 7.3 Rules
- Backend is authoritative. Payment success is never trusted from the client.
- Gateway callbacks and verifications must be idempotent.
- Refunds are not automated in MVP. The Master Admin may manually mark a payment `REFUNDED` after refunding outside the system, optionally revoking access ⚠.
- Duplicate TrxIDs are rejected.

---

## 8. High-Level Data Model

```text
User (role: STUDENT | MASTER_ADMIN | SYSTEM_ADMIN)
 ├── StudentProfile
 ├── RefreshToken, DeviceToken, OneTimeCode (activation | password reset | email verification)
 └── AdminProfile

Batch
Course ── ContentNode (tree: parent_id, type SECTION | LECTURE, title, position)
   └── Lecture details (video_provider, video_ref, notes, resources)
   ├── Material
   └── Exam ── ExamQuestion ── Question ── QuestionOption
Enrollment (student, course, batch, type, delivery_mode, status, expires_at)
ExamAttempt ── StudentAnswer
WrittenSubmission ── WrittenSubmissionImage
Result
ClassSession (method: QR | BUTTON) ── AttendanceRecord
Payment (payable_type, payable_id)
Book ── BookOrder
Notice, Notification
AuditLog
```

Prefer soft delete/archiving so historical academic and financial records are never lost.

---

## 9. Non-Functional Requirements

- **Security:** HTTPS, password hashing, JWT with refresh rotation, RBAC in backend, input validation, rate limiting (especially auth and QR scan), secure file access, audit logging.
- **Privacy:** no public or predictable URLs for private files; students only see their own data.
- **Data integrity:** eligibility checks on every exam, submission and attendance action; server time is authoritative; archived data is preserved.
- **Performance:** server-side pagination for all admin lists; indexes; exam autosave batching to avoid per-tap writes; targets set after load testing.
- **Scalability:** stateless API, PostgreSQL, ability to move files to cloud storage and to upgrade the VPS without redesign.
- **Time:** stored in UTC, displayed in Asia/Dhaka.
- **Localization:** UI text in Bangla and English (admin-entered content is not translated). All UI strings are externalized from day one, with a Bangla-capable font. API errors carry stable codes so clients can show localized messages.
- **Storage:** VPS local disk behind a `FileStorage` interface. Written scripts are the biggest growth driver; compress on device, no retention policy for now (confirmed), so monitor disk usage and plan the move to cloud storage before the disk fills; include uploads in backups.
- **Availability and operations:** automated PostgreSQL backups (stored off the VPS), health checks, error tracking, monitoring of CPU/RAM/disk.

---

## 10. Technical Architecture

| Layer | Technology |
|---|---|
| Mobile | Flutter (Android for MVP) |
| Web admin | Next.js / TypeScript |
| Backend | Spring Boot, Java 21, modular monolith |
| Database | PostgreSQL, Spring Data JPA, migrations via Flyway or Liquibase |
| Auth | Spring Security, own JWT |
| Push | Firebase Cloud Messaging (no Firebase Auth) |
| Email | Transactional email or SMTP (password recovery and email verification), behind an `EmailSender` interface |
| Files | Local disk, S3-compatible later |
| Video | YouTube IFrame player (unlisted videos) behind a `VideoProvider` interface; protected provider is a future upgrade |
| API docs | OpenAPI / Swagger |
| Deployment | systemd + Nginx + PostgreSQL on the client's VPS (see `client_vps_deployment_context.md`) |

Deployment path: local development → staging subdomain on the VPS → client testing → production cutover, with the old system retained temporarily for rollback. Do not modify existing Nginx sites or MariaDB databases on the shared VPS.

---

## 11. MVP Acceptance Criteria

**Student (Android)**
- Registers, or activates an admin-created account with a one-time code and sets their own password; logs in; recovers a forgotten password by email.
- Buys a course; access appears after verified payment; access locks after expiry.
- Watches YouTube lecture videos and opens PDF notes and materials inside the app; the app stops offering lectures once the enrollment expires.
- Takes an MCQ exam once, auto-submitted at time end, auto-evaluated.
- Uploads written exam images and sees submission status; cannot modify after submission.
- Views results only after publication.
- Marks attendance (QR scan for physical sessions, button for online sessions); views attendance history and percentage.
- Orders a book (online or cash on pickup) and sees its status.
- Reads notices and receives push notifications.

**Admin (Web)**
- Master Admin and System Admin log in with correct, backend-enforced permissions.
- Only the Master Admin can delete entities. A System Admin's delete attempt returns `403` from the API, and deletions are audit-logged.
- Creates students, batches, courses, lectures, materials, notices.
- Creates offline admissions and links accounts.
- Creates question bank, MCQ and written exams; evaluates written submissions; publishes results.
- Runs attendance sessions (QR or button) and corrects records.
- Verifies manual payments (Master Admin) and views purchase history.
- Manages books and orders.
- Master Admin sees payment, attendance and student-growth analytics.
- Sends notices and push notifications.
- Audit log records sensitive actions.

---

## 12. Delivery Plan

**Target: 1 month. Realistic plan: 6 to 8 weeks.** Solo developer with AI assistance.

| Phase | Focus |
|---|---|
| Week 0 (days 1-3) | Finalize this PRD and permission matrix; create Google Play developer account (client-owned) and FCM project; client starts bKash/Nagad merchant applications; decide email provider; repo, CI, Postgres migrations, skeletons |
| Week 1 | Auth, roles, students, batches, courses, enrollments (incl. offline admission), admin shell, Flutter login and course list |
| Week 2 | Content tree and lectures (protected video), PDFs, materials, notices, FCM, Flutter course access, attendance (QR and button) |
| Week 3 | MCQ and written exams, results; **first closed-test build uploaded to Google Play** |
| Week 4 | Manual payments, Library, analytics, staging deployment on VPS |
| Weeks 5-8 (buffer) | Gateway integration, tester feedback, hardening, backups, production cutover |

**Cut candidates if behind:** analytics beyond core numbers, Google login, question-bank extras, attendance reports polish.

**External dependencies and lead times**
- **Google Play:** new personal developer accounts must run a closed test with at least 12 testers opted in for 14 continuous days before applying for production access (organization accounts are exempt but take longer to verify). Verify current rules when creating the account. Plan for the 14-day clock to overlap with development.
- **bKash/Nagad merchant onboarding:** typically needs client business documents. Start immediately.
- **Firebase project (FCM):** free, about an hour to set up.
- **YouTube channel** (client-owned) to host the unlisted lecture videos; needed before lectures are built.
- **Email provider** (client-owned): needed for password recovery and email verification.
- **Domains and DNS** for staging and production.

---

## 13. Future Roadmap

iOS app (with App Store payment policy review), web student portal, gateway-based refunds, coupons, cloud storage migration, SMS OTP, Google/Facebook login, teacher and evaluator roles, rankings and grading, certificates, live classes, parent portal, advanced analytics, legacy data migration.

---

## 14. Decisions Log and Remaining Open Items

### 14.1 Assumptions review

| # | Assumption in v0.3 | Result |
|---|---|---|
| 1 | Multiple courses per student, fixed prices, no coupons | Confirmed |
| 2 | Attendance for offline and hybrid students only | **Changed:** all student types. QR for physical sessions, button for online sessions |
| 3 | Fixed Course → Subject → Chapter → Lecture hierarchy | **Changed:** flexible content tree of variable depth |
| 4 | Written exam limits (10 images, compression, 3 MB cap) | Confirmed |
| 5 | No ranking or grade system | Confirmed |
| 6 | Results visible only after admin publication | Confirmed |
| 7 | Push optional per notice | Confirmed |
| 8 | Phone mandatory and unique, no OTP | Confirmed |
| 9 | Admin-assisted password reset | **Changed:** user-controlled passwords; activation codes and email recovery (Section 5.1) |
| 10 | Permission matrix | Confirmed for now; may change with client requirements |
| 11 | Unlisted YouTube acceptable | **Accepted for the MVP** after discussion with the client. Protected provider is a later upgrade (Section 5.4.1) |
| 12 | Refunds handled manually | Confirmed |
| 13 | Optional book stock count | Not needed; removed |

### 14.2 Open questions answered

| Question | Answer |
|---|---|
| App language | Bangla and English UI text. Content is not translated |
| Google Play account owner | The client. The developer has access until the project is finished |
| bKash/Nagad documents | The client has what is needed (verify at application time) |
| Written exam retention | None for now; monitor disk usage |
| Domains and accounts | Client owns domain and DNS. Other accounts (Firebase, email, video, cloud storage) may not exist yet and are created under client ownership as needed |
| Old system | PHP, MariaDB and Next.js web app, retired after the project is finished |
| Admin counts | Exactly one Master Admin; unlimited System Admins |
| Attendance percentage | Visible to students; no effect |
| Automatic attendance/expiry notifications | Not required |

### 14.3 Still open (decisions needed)

1. **Protected video upgrade (post-MVP):** revisit a DRM provider if link sharing or piracy becomes a problem. Cost depends on total viewing hours.
2. **Email provider** for password recovery and email verification (client-owned).
3. **Confirm the password design** in Section 5.1: activation codes for admin-created accounts, emailed reset, email required for students, and admin re-issue of a code when email access is lost.
4. **Default UI language** on first launch (Bangla or English).
5. **Hybrid attendance eligibility** (Section 5.10) and the **maximum content-tree depth** (Section 5.4).
6. **Google Play account type** (personal or organization), which determines whether the 12-tester, 14-day closed test applies.
7. **Old system identification:** confirm exactly which Nginx site, PM2 process, Supervisor worker and MariaDB database belong to the old coaching system before anything is retired. The VPS hosts other applications too.
8. **Permanent purge policy** for soft-deleted records (Section 4.5).

### 14.4 Accounts and ownership checklist (all client-owned; developer has access during the project)

| Account | Needed for | Status |
|---|---|---|
| Google Play Console | Publishing the Android app | To create |
| Firebase project | Push notifications (FCM) | To create |
| Email provider or SMTP | Password recovery, email verification | To choose |
| YouTube channel | Hosting unlisted lecture videos | Client to provide |
| bKash / Nagad merchant | Online payments | Client applying |
| Domain and DNS | Staging and production hostnames | Exists |
| Cloud storage | Future file storage upgrade | Later |
