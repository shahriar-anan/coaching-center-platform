# Phase 2 Scope: Catalog & Acquisition (Courses, Library, Purchase)

**Depends on:** Phase 1 (Foundation & Identity) must be complete — roles, permissions, authentication, and the delete/permission conventions established there are reused, not redesigned.
**Companion file:** `phase2_client_and_nontechnical_tasks.md` covers decisions and non-code tasks; this file covers only business scope.

This document describes **what the system must do and the rules it must follow**. It intentionally does not prescribe how to structure code, which tools to use, or how to organize the project — those are implementation decisions for whoever builds this. Read every stage fully before starting; later stages depend on rules established in earlier ones.

---

## 1. What Phase 2 Is

Phase 1 gave the platform users. Phase 2 gives it something those users can acquire: courses and library books, both purchasable, both leading to some form of access once acquired. This phase covers the **backend API** for all of this plus **mobile app UI**, including automated testing of the backend behavior. Web admin screens for managing courses and books are **not** required in this phase — course and book creation/management happens through the backend API directly (see Section 8, Assumption A1).

By the end of Phase 2, a student should be able to open the app, see their home screen, browse courses and library books, purchase either one, and — once an admin verifies the payment — get access to the course content they bought.

---

## 2. Two Catalog Types, One Underlying Pattern

A course and a library book are different in detail but the same in shape: both are something a student can browse, both have a price, and both involve an acquisition step that moves a student from "doesn't have it" to "has it." Design both against this shared pattern rather than as two unrelated features — it keeps the purchase and payment-verification logic in one place instead of two.

| | Course | Library Book |
|---|---|---|
| What is acquired | Access to course content, for a limited time | A physical book, picked up or delivered |
| Acquisition record | Enrollment | Order |
| Can expire | Yes, per-course access duration | No — it's a one-time physical handoff |
| Alternate acquisition path | Admin creates it directly (offline admission), no payment | Cash on pickup, no online payment |

---

## 3. A Critical Modeling Decision: Exams Are Not Part of the Course Tree

This matters now even though exams themselves are a later phase, because getting it wrong here means restructuring the course content tree later.

The coaching center sometimes runs exams that belong to a specific course (e.g. a chapter test for enrolled students), but also sometimes runs **standalone exams open to any student**, with or without any purchase — the example given was a pre–Bar Exam assessment available to the whole user base regardless of enrollment.

**Rule:** the course content tree (Section 4) must contain only two kinds of nodes — sections and lectures. It must never contain an exam as a tree member. When exams are built in a later phase, an exam will optionally *reference* a course (by pointing at it), rather than *living inside* the course's content tree. This keeps a standalone, purchase-independent exam possible without any rework. Treat this as a hard constraint on the content tree's design, not a suggestion.

---

## 4. Course Module — Business Rules

### 4.1 The course entity
A course has: a title, a description, a cover image, a price, a delivery mode (online, offline, or hybrid), and a status (draft, published, or archived). Only published courses are visible to students browsing the catalog. Draft and archived courses remain visible to admins.

Each course also defines **how long a student's access lasts once they acquire it** — this varies per course, so it must be configurable per course rather than a fixed system-wide rule (e.g. one course might grant 6 months of access, another might run until a fixed date, another might be lifetime). Decide the exact representation during planning (Section 8, Open Question Q1), but the business requirement — that duration is set per course by an admin — is fixed.

If a course's price changes after students have already purchased it, **existing purchases are not affected** — a student's enrollment should remember what they actually paid, independent of the course's current list price.

### 4.2 The content tree
Courses vary in depth: some are a flat list of lectures, others go Course → Subject → Chapter → Lecture, others sit somewhere in between. The content structure must therefore be a **tree of arbitrary depth** that an admin builds freely, not a fixed set of levels.

Two kinds of nodes exist in the tree:
- **Section** — a container with a name (e.g. "Subject," "Chapter," "Week" — the label is just a name an admin types, not a fixed category). A section can contain other sections or lectures.
- **Lecture** — a leaf node. It cannot contain other nodes.

Nodes have an explicit order within their parent, and admins can reorder or move nodes within the tree.

### 4.3 Lecture content
Per the PRD, a lecture is not just a title — it carries actual content:
- A reference to a video (the MVP decision is an unlisted YouTube video; see the PRD for the reasoning and limitations).
- Lecture notes (text).
- Attached materials — PDFs, images, or other supporting files, associated either with a specific lecture or with the course as a whole.

### 4.4 Who can see what
- **Catalog browsing** (course list and detail, including its content tree structure — titles only, not the actual video/notes) is visible to any authenticated student, enrolled or not, so they can decide whether to purchase.
- **Lecture content itself** — the actual video and notes/materials — is only accessible to a student with an **active** enrollment in that course. An enrollment that has expired or was never activated does not grant access, even though the course and its structure remain visible.
- Admins (both roles) can see everything regardless of enrollment, for content management purposes.

---

## 5. Enrollment — Business Rules

An enrollment is the record that connects one student to one course and determines whether they currently have access.

### 5.1 How an enrollment is created
Two independent paths, matching the PRD's online/offline model:

1. **Online purchase** — a student buys the course through the app. The enrollment starts in a pending state and only becomes active once the payment is verified (Section 6). Real payment gateways are not part of this phase; see Section 6 for how this is simulated.
2. **Offline admission** — an admin creates the enrollment directly for a student who was admitted in person. No payment step is involved. The enrollment becomes active immediately.

### 5.2 Enrollment lifecycle
An enrollment has a status that reflects where it is: awaiting payment, active, expired, or cancelled. Active means the student currently has content access. Expired means the access window (Section 4.1) has passed — the enrollment record and the student's purchase history remain visible, but content access is locked. A cancelled enrollment (e.g. a payment that failed or was reversed) grants no access and is distinguishable from a naturally expired one.

### 5.3 What an enrollment remembers
At minimum: which student, which course, which delivery mode, how it was created (online purchase or offline admission), when it started, when it expires (if applicable), its current status, and the price actually paid at the time of purchase (Section 4.1).

### 5.4 Re-purchase and expiry
Whether a student can buy the same course again after their access expires, and whether that extends or replaces the prior enrollment, is an open business question — see Section 8, Open Question Q2. Do not assume either answer; confirm before building this edge case.

---

## 6. Purchase & Payment — Business Rules (Manual Mode)

Real bKash/Nagad gateway integration is out of scope for this phase (it depends on merchant credentials the client does not yet have). Phase 2 instead implements the **manual verification flow** described in the PRD, which is a complete, real purchase flow from the student's point of view — only the final payment confirmation step is manual rather than automated.

### 6.1 The purchase flow (course or book)
1. The student chooses to buy a course, or places a library book order with online payment selected.
2. The student is shown the coaching center's payment number and asked to pay manually (outside the app, via their bKash/Nagad app) and then enter the transaction reference ID they received.
3. A payment record is created in a pending state, linked to whatever is being purchased (the course enrollment or the book order).
4. An admin reviews pending payments and marks each one as successful or failed, after checking the transaction reference against what actually arrived.
5. On success: a course purchase activates the enrollment; a book order advances to paid/ready. On failure: nothing is granted, and the student can be informed.

### 6.2 One payment concept, two uses
Design the payment record so it can represent a payment for *either* a course enrollment or a book order, rather than building two separate, parallel payment systems. This mirrors the PRD's own design (a generic payment entity distinguished by what it's paying for) and avoids duplicating the verification workflow.

### 6.3 Cash payments (library only)
A book order may also be paid in cash on pickup, with no payment record and no online step at all — the order simply moves through its status states (Section 7) until an admin marks it collected. Confirm who is allowed to mark a cash payment received; the PRD restricts this to the Master Admin, consistent with the rule that System Admins never handle money directly.

### 6.4 What the student sees while pending
A student should be able to see their own purchase/order history and its current status (pending, successful, failed) at any time, even before an admin has acted on it.

---

## 7. Library Module — Business Rules

### 7.1 The book catalog
A book has: title, author, cover image, description, price, and an available/unavailable flag. There is no stock count or inventory tracking in the MVP (confirmed in the PRD) — "available" is a simple admin-controlled toggle, not a quantity.

### 7.2 Placing an order
A student browsing the library can order a book, choosing:
- **Fulfillment method:** pickup at the coaching center, or delivery (address required if delivery is chosen).
- **Payment method:** online (manual verification per Section 6) or cash on pickup.
- Contact phone number is required regardless of method.

### 7.3 Order status
The exact sequence of states differs by payment method:
- **Online payment:** placed → paid → ready → delivered/collected.
- **Cash on pickup:** placed → ready → collected (no "paid" state, since payment happens at pickup).
- Either path can move to cancelled before fulfillment completes. Confirm who can cancel and under what conditions (Section 8, Open Question Q3).

### 7.4 No delivery logistics
There is no shipping cost calculation, no courier integration, and no delivery tracking beyond the status field itself. Fulfillment is handled by the coaching center's staff outside the app; the app only reflects the current status.

---

## 8. Open Questions and Assumptions

### 8.1 Assumption made in writing this scope
**A1 — Web admin screens are not required in Phase 2.** The instruction for this phase was explicitly backend-plus-mobile. Courses and books must still be creatable and manageable somehow, so this phase assumes course/book/content management happens through the backend API directly (created and verified via automated tests), without a web UI being built yet. If this is wrong — if there needs to be a way for non-technical staff to create courses or books before Phase 3 — say so before this phase starts, since it changes the scope materially.

### 8.2 Questions that need an answer before or during this phase

- **Q1 — Access duration representation.** Should a course's access duration be set as a number of days from purchase, a fixed calendar end date, or should the admin choose either per course? The business rule (duration varies per course, admin sets it) is fixed; the exact input shape is not.
- **Q2 — Re-purchase after expiry.** If a student's course access expires, can they buy it again? Does a new purchase extend the existing enrollment or create a new one alongside the expired one?
- **Q3 — Order/enrollment cancellation.** Can a student cancel their own pending course purchase or book order before it's processed, or is cancellation an admin-only action in every case?
- **Q4 — Refund effect.** If a payment is later marked refunded, does that automatically revoke the enrollment it granted, or does an admin handle access removal as a separate manual step?
- **Q5 — Material file limits.** Are there size or file-type restrictions expected for course cover images, book covers, or lecture materials (PDFs, images)? Needed for both storage planning and UI validation.
- **Q6 — Required delivery fields.** What exactly must a student provide for book delivery — full address, area/district only, or something more structured (useful if the client wants delivery zones later)?

---

## 9. Incremental Task Breakdown

This phase is intentionally broken into stages with their own exit conditions, so it does not need to be built or reviewed as one single pass. Each stage should be demonstrably working before the next begins; later stages depend on earlier ones.

### Stage 1 — Course catalog and content structure (admin-facing, API only)
Build the course entity, its status (draft/published/archived), the content tree (sections and lectures, arbitrary depth, ordering, reordering), and lecture content (video reference, notes, materials). No student-facing access yet — this stage is entirely about admins being able to construct a course.
**Exit condition:** a course with a multi-level tree and lecture content can be created, edited, and reordered through the API, and is correctly rejected from containing anything other than sections and lectures (Section 3).

### Stage 2 — Library catalog (admin-facing, API only)
Build the book entity and its available/unavailable toggle.
**Exit condition:** books can be created, edited, and toggled through the API.

### Stage 3 — Student-facing browsing (read-only)
Expose published courses and available books to students: course list, course detail (including the content tree's structure, titles only), book list, book detail. No purchase yet, no lecture content access yet.
**Exit condition:** a student can browse the full catalog and see what exists, but cannot yet acquire or access anything.

### Stage 4 — Purchase and order placement
Build the manual-payment purchase flow (Section 6) for both courses and books, and the book-specific order flow including cash-on-pickup (Section 7). This stage creates pending enrollments/orders and pending payment records; it does not yet grant access.
**Exit condition:** a student can initiate a course purchase or a book order through either payment method, and can see its pending status in their own purchase history.

### Stage 5 — Payment verification and access grant
Build the admin-side verification step: reviewing pending payments, marking them successful or failed, and the resulting effects — activating a course enrollment, advancing a book order's status, or marking a cash order collected. Also build the offline-admission path for courses (admin grants an enrollment directly, no payment).
**Exit condition:** a full purchase can be completed end to end — student purchases, admin verifies, student gains access (for a course) or the order visibly advances (for a book) — and an offline admission grants course access without any payment step.

### Stage 6 — Enrolled content access
Gate lecture video/notes/materials behind an active enrollment check (Section 4.4), including correctly locking access once an enrollment expires.
**Exit condition:** a student with an active enrollment can open a lecture's content; a student without one, or with an expired one, cannot, even if they know the course exists.

### Stage 7 — Mobile home page and navigation
Build the app's home screen: a header showing the logged-in student's basic info, and two tabs — Courses and Library — wired to the browsing, purchase, and (for courses) content-access work from the earlier stages.
**Exit condition:** a student opens the app, lands on the home page, and can reach everything built in Stages 3 through 6 through the Courses and Library tabs.

---

## 10. What Remains Out of Scope

Exams in any form (including the standalone/open-access type discussed in Section 3 — only the modeling accommodation is made now, not the feature itself), attendance, notices, push notifications, real payment gateway integration, batches as a scheduling/targeting concept, and web admin screens (Section 8, Assumption A1). None of these should be started as part of Phase 2.