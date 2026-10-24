# Exam Module Requirements Brief and Audit Task

**How to use this copy.** Shahriar agreed the decisions in [decisions.md](decisions.md) on 2026-10-23. Where this brief and that file disagree, `decisions.md` wins. The audit this brief asks for is finished. Do not repeat it. Paragraphs marked outdated in `docs/phases/phase-3/` are not requirements.



**Deadline:** a working student-facing website for the exam module by **2 November 2026**. **Audience:** the AI agent that will audit the existing system and plan the work. **Source:** requirements from the latest client meeting. Where this brief conflicts with the earlier PRD (v0.2), **this brief wins**.

## 1. Goal

The client needs a website where students can **purchase exams or a full exam module** and take **written** and **MCQ** exams. The client has an exam running now. The **student website is the priority** and must be robust and very student friendly. The admin and instructor interfaces can be basic for November 2nd and improved afterwards, depending on the client's preference and the time left.

## 2. Current state (as reported by the developer)

- Backend (Spring Boot, PostgreSQL) already implements: live written exams, MCQ exams and their rules, email and phone registration/login for students, and Master Admin / System Admin role wiring.
- **No frontend exists yet** for the student website.
- **Google sign-up is missing.**
- Unknown: whether the backend actually behaves as described in section 4 and supports the changes in this brief. **That is what the audit must determine.**
- Payments: the client has an existing **SSLCommerz** integration on his old website. Its credentials will be reused. Never write credentials into code, docs or Git. Use environment variables.
- Infrastructure: a new dedicated Hostinger KVM 2 server will be used (see section 10). The earlier shared-VPS deployment context is outdated.

**Note on payments:** exam checkout for this deadline is **SSLCommerz**. Course and book payments, including the existing manual bKash/Nagad verification and any later bKash/Nagad gateway, are out of this delivery. Do not change those rows or screens.

## 3. Domain context

The platform serves law students and aspirants (for example BJS and Bar exam preparation). Content is organised as:

```
Category            e.g. BJS Exam, Bar Exam
  Subcategory       e.g. 19th BJS Exam, 18th BJS Exam
    Exam module     (package: description, routine, rules, list of exams)
      Exam          (written or MCQ; individually purchasable)
```

## 4. Student requirements

### 4.1 Account

- Register and log in with **email, phone number, and Google** (Google is new).
- No SMS OTP for now. Phone registration must work without OTP verification.
- Interface language: **Bangla and English**, switchable by the student.

### 4.2 Home and browsing

- After login, the home screen offers two entry points: **MCQ** and **Written**.
- Browsing flow: type (Written/MCQ) → category → subcategory → exam module page.
- The **module page** shows description, routine, rules, and other information needed to understand the offer, plus the **full list of exams** (past, live and upcoming) with their status.
- Before purchase, the student can see the exam list but **never the questions**.

### 4.3 Purchase

- Buy **each exam separately** or the **whole module/package** (SSLCommerz).
- Buying a full module grants access to **all its exams**, including exams that were already held before the purchase, and future exams.
- A student who bought a module can view the whole exam list, but sees **questions only when an exam is live** (or already held and eligible, see 4.7).

### 4.4 Student dashboard

- Purchased exams and modules.
- **Upcoming-exam notification 1 to 2 days before** the exam (in-app only: no push, email or SMS).
- A **Live exams** section that shows exams currently open.
- Notification when results are published, with the student's marks.

### 4.5 Taking an exam (both types)

- Exam page shows title, description and rules.
- A **Take exam** button becomes active **only when the exam is live** (for example, an exam starting at 12:00 PM becomes startable at 12:00 PM). Start/end times must be enforced **server-side**.
- Questions must be shown **like an exam paper**, not as plain UI text. If the question is text, it must be styled as a formal paper (layout, headings, numbering, spacing). Bangla rendering must be correct.
- If the question is a **PDF**, it is displayed **inside the page** with an embedded viewer (no new tab, no link). **No download button.** Apply deterrents: disable download/print/right-click controls where practical and overlay a **watermark with the student's name/ID**. Full prevention of screenshots is not possible, so the goal is deterrence.
- **Exam language follows what the admin chose** when creating the exam (Bangla or English). It is **not** shown in both languages.

### 4.6 Exam-type rules

| Rule | Written | MCQ |
| --- | --- | --- |
| Answer method | Student uploads a **PDF only** (no photos) | Student selects options |
| Resubmit | Allowed while the exam time remains | Allowed while the exam time remains |
| Retake | **Not allowed** | **Unlimited retakes** for anyone with access |
| Marking | Manual, by the assigned instructor | **Automatic**, using the answers the admin entered at creation |
| Result visibility | After the **admin publishes** | After the **admin publishes** (not instant) |

### 4.7 Past exams

- Past exams in a purchased module are accessible to the buyer. **Assumption to confirm:** past MCQ exams are retakeable; past written exams show their paper for viewing but cannot receive a new submission once the window has closed.

### 4.8 Results and leaderboard

- Results appear only after publication.
- **Leaderboard per exam** (not per module), showing **student names publicly**.
- Standard LMS features are expected (history, marks, ranking). Keep them simple for November 2nd.
- **Open:** for MCQ retakes, which attempt counts on the leaderboard (first or best)? Assumption until confirmed: **best attempt**.

## 5. Instructor requirements

- Logs in with credentials **created by the admin**.
- Sees **only exams assigned to them** and only the submissions for those exams.
- Opens each student's uploaded PDF, **annotates/corrects it with a digital device**, uploads the **marked PDF** back, and enters the **marks**. The student can later see the marked PDF and marks.
- Saves results as a **draft**. The instructor **cannot publish** without admin permission; the **admin publishes** after checking.

## 6. Admin requirements (Master Admin and System Admin)

- Create and manage categories, subcategories, modules and exams (written and MCQ).
- For MCQ, enter the correct answers at creation, used for automatic marking.
- Choose the exam language (Bangla or English) per exam.
- Upload question content as **text or PDF**.
- Create instructor accounts and assign exams to instructors.
- Review and **publish results** (MCQ and written).
- See analytics: who is taking exams, how many students, participation and results.
- Master Admin / System Admin payment permissions as in the PRD (System Admin cannot change prices or financial records). Must be enforced in the backend.
- For November 2nd these screens may be basic.

## 7. Priorities

**Must have by 2 Nov**

1. Student website: registration/login (email, phone, Google), browsing, module pages, SSLCommerz purchase, dashboard, live exams, written and MCQ taking, notifications, results and leaderboard.
2. Backend support for every rule in sections 4 to 6, enforced server-side.
3. Admin/instructor functions sufficient to run the ongoing exam (basic UI or existing backend).

**Can follow**

- Polished admin and instructor interfaces (per client preference).
- Advanced analytics, SMS OTP, anything else from the PRD not listed above. Course and book payments stay out of this delivery.

## 8. Audit task for the agent

Examine the existing codebase and database. Do **not** change code yet. Produce a report that covers:

1. **Requirement-by-requirement table** for sections 4 to 6 with a status: *Implemented and correct / Implemented but differs / Partially implemented / Missing*, plus file or endpoint references.
2. **Data model fit:** can the schema represent Category → Subcategory → Module → Exam, per-exam and per-module purchases, access grants, MCQ attempts (unlimited) versus written submissions (one final), exam language, instructor assignment, and draft versus published results?
3. **Access control:** verify server-side enforcement of: no question access before purchase or before the live window, instructor limited to assigned exams, instructor cannot publish, System Admin cannot touch payments, students cannot see other students' private data.
4. **Exam engine checks:** server-controlled start/end times, resubmission until the deadline, no written retake, MCQ auto-marking correctness, publication gating, leaderboard per exam.
5. **Files:** PDF upload limits and validation, private storage with no public or guessable URLs, how question PDFs can be streamed to an in-page viewer without a download link, and how marked PDFs are stored and returned.
6. **Payments:** how SSLCommerz can be integrated (initiate, callback/IPN verification, idempotency, order-to-access mapping, failure and refund states).
7. **Auth:** state of email/phone auth and what Google sign-in needs (account linking when the same email already exists).
8. **Notifications:** what exists for 1-2 day reminders and result-published alerts with in-app delivery only (no push, email or SMS).
9. **API readiness for the frontend:** list endpoints needed by each student screen, and which are missing, with OpenAPI status.
10. **Gap list with effort estimates**, ordered so the minimum working exam flow comes first, and a **risk list** for the 2 November deadline.
11. **Deployment notes:** what must be prepared on the new dedicated KVM 2 server (see section 10), including domain, DNS, HTTPS, backups and the SSLCommerz callbacks.

## 9. Assumptions and open questions

- Past written exams: view only, no new submission (4.7).
- MCQ leaderboard uses the best attempt (4.8).
- Whether SSLCommerz credentials are sandbox or live, and which domain will be used for staging.
- PDF size and page limits for student uploads.
- Password recovery: with no email system, how does a student reset a forgotten password (admin reset, or sign in with Google)?
- Whether unpaid or failed purchases need an admin manual-enrolment fallback before launch.
- Who owns the ongoing exam's data, and whether any must be migrated from the old system.

## 10. Infrastructure change: new dedicated server (supersedes earlier deployment context)

**The client bought the Hostinger KVM 2.** It is provisioned. SSH alias `exam-kvm`, IPv4 `187.52.115.111`, hostname `srv2089893.hstgr.cloud`. Decisions in [decisions.md](decisions.md) win where this section still says the box is not purchased. The earlier "Client VPS & Coaching Center Application — Deployment Context" document described a different, shared production server and is **now outdated**. Where it conflicts with this section, this section wins.

**What changes**

- The new server is **dedicated to this project**. The constraints about not touching existing Nginx sites, MariaDB, PHP, PM2 and Supervisor workers **no longer apply** to it. Those apps stay on the old server.
- Stop using the old server's IP address, hostname, port plan and resource figures anywhere in plans or config.
- The purchased box is 2 vCPU, about 8 GiB RAM, and about 96 GiB disk. Size the stack from those figures, not from the old server's numbers.
- The box is Ubuntu 24.04 with a non-root `deploy` user, SSH keys only, a firewall allowing 22/80/443, PostgreSQL on localhost, unattended upgrades, 2 GiB swap, and fail2ban. Do not reuse the old database password or the old JWT secrets. The one reused secret is the Gmail SMTP account in [decisions.md](decisions.md). Never put secrets in documents or Git.
- Stack stays: Java 21, Spring Boot as a systemd service, PostgreSQL, Nginx as reverse proxy with HTTPS (Let's Encrypt), and Next.js for the web frontend.
- Decide and document how staging and production coexist on one server (separate databases, ports and subdomains, or staging only until cutover), and where uploaded files (question PDFs, student answer PDFs, marked PDFs) are stored. With a single server and no S3, local private storage plus off-server backups is the likely choice. The agent should recommend and justify.
- **Backups must go off the server** (database dumps and uploaded files, on a schedule, with a tested restore).
- **SSLCommerz** needs a public HTTPS domain for its callback/IPN URLs, so the domain and DNS must be ready before payment testing.
- **Rollback:** the old website stays on the old server. Cutover is a DNS switch to the new server, and rollback is switching DNS back.
- The server is purchased. Domain DNS and HTTPS on a `symamlaw.com` name are still on the critical path for 2 November. The agent should list those as blockers with an owner and a date.

**Mandatory housekeeping for the agent**

1. Treat this brief as the current source of truth. Find every older plan file or document the agent holds in context (the PRD v0.2, the deployment context document, earlier notes) and **update or clearly mark as superseded** anything that conflicts with this brief.
2. Produce a short **change log**: file, what was outdated, what it now says. Do not silently overwrite.
3. If two sources still conflict and this brief does not settle it, **list the conflict and ask**, rather than guessing.
4. Remove or rewrite old references to: the shared-server rules, the old IP address and hostname, bKash/Nagad as the MVP payment method, the Flutter-first scope, and any scope item not in this brief that would delay 2 November.
