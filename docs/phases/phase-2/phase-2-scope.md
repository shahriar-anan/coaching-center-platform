# Phase 2 Scope: Catalog & Acquisition (Courses, Library, Purchase)

**Depends on:** Phase 1 (Foundation & Identity) must be complete — roles, permissions, authentication, and the delete/permission conventions established there are reused, not redesigned.
**Companion file:** `phase2_client_and_nontechnical_tasks.md` covers decisions and non-code tasks; this file covers only business scope.

This document describes **what the system must do and the rules it must follow**. It intentionally does not prescribe how to structure code, which tools to use, or how to organize the project — those are implementation decisions for whoever builds this. Read every stage fully before starting; later stages depend on rules established in earlier ones.

---

## 1. What Phase 2 Is

Phase 1 gave the platform users. Phase 2 gives it something those users can acquire: courses and library books, both purchasable, both leading to some form of access once acquired. This phase covers the **backend API** for all of this, the **student mobile app**, and **admin management screens on the web** for courses, the library, payment verification, offline admission, and book-order fulfillment. The web app stays administrators only. Students do not browse, purchase, or open content there. Those actions stay on mobile.

By the end of Phase 2, a student should be able to open the app, see their home screen, search and browse courses and library books by an English or Bangla title, purchase a course or a book, and — once an admin verifies the payment — get access to the course content they bought. Some course content is available without a purchase, as defined in Section 4.1a. Access is not on a timer. It lasts for the current time the course is open. If the Master Admin closes the course, that access ends. If the course is opened again later, a previous purchase does not come back; the student has to acquire it again.

---

## 2. Two Catalog Types, One Underlying Pattern

A course and a library book are different in detail but the same in shape: both are something a student can browse and search, both have a price, and both involve an acquisition step that moves a student from "doesn't have it" to "has it." Design both against this shared pattern rather than as two unrelated features — it keeps the purchase and payment-verification logic in one place instead of two.

| | Course | Library Book |
|---|---|---|
| What is acquired | Access to course content for the current time the course is open | A physical book, picked up or delivered |
| Acquisition record | Enrollment | Order |
| Can expire | No time limit. The Master Admin can close the course, which removes it from the student catalog and ends access. Reopening it starts fresh: previous purchasers must acquire it again | No — it's a one-time physical handoff |
| Alternate acquisition path | Admin creates it directly (offline admission), no payment. A full-free course needs no purchase at all | Cash on pickup, no online payment |
| Names | English title and Bangla title, both searchable. The rest of the course is written in one language | English title and Bangla title, both searchable. Other book fields are a single language |

---

## 3. A Critical Modeling Decision: Exams Are Not Part of the Course Tree

This matters now even though exams themselves are a later phase, because getting it wrong here means restructuring the course content tree later.

The coaching center sometimes runs exams that belong to a specific course (e.g. a chapter test for enrolled students), but also sometimes runs **standalone exams open to any student**, with or without any purchase — the example given was a pre–Bar Exam assessment available to the whole user base regardless of enrollment.

**Rule:** the course content tree (Section 4) must contain only two kinds of nodes — sections and lectures. It must never contain an exam as a tree member. When exams are built in a later phase, an exam will optionally *reference* a course (by pointing at it), rather than *living inside* the course's content tree. This keeps a standalone, purchase-independent exam possible without any rework. Treat this as a hard constraint on the content tree's design, not a suggestion.

---

## 4. Course Module — Business Rules

### 4.1 The course entity
A course has: an English title, a Bangla title, one content language (Bengali or English), a description, a cover image, a price, a delivery mode (online, offline, or hybrid), an access type (Section 4.1a), and a status (draft, published, or archived). The content language is chosen when the course is created. Description, section names, lecture titles, and lecture notes are written once, in that language. They are not stored twice.

Only the titles are bilingual. A student can find a course by either title. A search for the English name and a search for the Bangla name must both return that course.

Only a **published and open** course is visible to students browsing or searching the catalog. Draft, archived, and closed courses stay visible to admins and are absent from the student catalog and from student search.

There is **no access-duration field** and no expiry date. A purchase does not run out after a number of days or on a calendar date. Closing and reopening is the Master Admin's action alone, and it defines the access window:

- **Close** takes the course off the student catalog. Students cannot open its content while it is closed. Access from purchases and admissions made during that opening ends with the close. There is no separate action that locks only the free lectures.
- **Reopen** puts the course back in the student catalog and starts a new access window. Purchases and admissions from the previous window do not grant access. A student who wants the course again has to acquire it again (a new purchase, or a new offline admission).

The access type is fixed when the course is created. A full-free course cannot be turned into a freemium course.

If a course's price changes after students have already purchased it, **existing purchases for the current opening are not affected** — a student's enrollment should remember what they actually paid, independent of the course's current list price.

### 4.1a Who can open content without a purchase
There are two access types.

1. **Freemium.** The admin chooses which lectures are free, and may also mark a material attached to the course (not to a lecture) as free on its own. The choice is explicit, not "the first N" calculated by the system. The free set may be empty: empty means nothing is free and every lecture and material waits for a purchase. Free lectures, the materials attached to those lectures, and course-level materials marked free are available to every student who has an account, with no purchase, while the course is open. The rest require an active enrollment from the current opening.
2. **Full free.** Every lecture and material is available to every student who has an account, while the course is open. There is no purchase step for that course.

A lecture marked free stays free. A course-level material marked free stays free. The admin can add more free lectures or free course-level materials later. The admin cannot take a free lecture or a free course-level material and make it paid.

Both types follow the close/reopen rule in Section 4.1. A closed course is not in the student catalog and does not serve content to students.

### 4.2 The content tree
Courses vary in depth: some are a flat list of lectures, others go Course → Subject → Chapter → Lecture, others sit somewhere in between. The content structure must therefore be a **tree of arbitrary depth** that an admin builds freely, not a fixed set of levels.

Two kinds of nodes exist in the tree:
- **Section** — a container with a name (e.g. "Subject," "Chapter," "Week" — the label is just a name an admin types, not a fixed category). A section can contain other sections or lectures. The name is a single language, the course's content language.
- **Lecture** — a leaf node. It cannot contain other nodes. On a freemium course, a lecture is either free or paid. That flag is how the admin selects the free set in Section 4.1a. Once free, it cannot be made paid.

Nodes have an explicit order within their parent, and admins can reorder or move nodes within the tree.

### 4.3 Lecture content
A lecture is not just a title. It carries:
- A reference to a video. The video is an embedded YouTube link, not a file uploaded by the platform.
- Lecture notes (text), in the course's content language.
- Attached materials — PDFs, images, or other supporting files, associated either with a specific lecture or with the course as a whole. Each material file is limited to **100 MB**. On a freemium course, a course-level material has its own free flag (Section 4.1a).

This phase stores material files and cover images on the **local API server**. A later phase replaces that store with **Cloudinary** for upload and download. Cloudinary is the chosen service. Integrating it is not part of Phase 2.

### 4.4 Who can see what
- **Catalog browsing and search** (course list and detail, including its content tree structure — titles only, not the actual video/notes) is visible to any authenticated student for courses that are published and open. Search matches the English title or the Bangla title. A closed, draft, or archived course does not appear.
- **Free content** on a freemium course, and **all content** on a full-free course, is available to any authenticated student while the course is published and open.
- **Paid lecture content** — the actual video, notes, and materials — is only accessible to a student with an **active** enrollment from the **current** opening, and only while the course is open. An enrollment from before the last close does not grant access. An enrollment that was never activated does not grant access.
- Admins (both roles) can see everything regardless of enrollment or close state, for content management purposes. Only the Master Admin can close or reopen a course.

---

## 5. Enrollment — Business Rules

An enrollment is the record that connects one student to one course and determines whether they currently have access to the paid content.

A full-free course does not need an enrollment for content access. Freemium free lectures, and course-level materials marked free, do not need an enrollment either. Those still require the course to be open.

### 5.1 How an enrollment is created
Two independent paths, for courses that have something to buy:

1. **Online purchase** — a student buys the course through the app. The enrollment starts in a pending state and only becomes active once the payment is verified (Section 6). Real payment gateways are not part of this phase; see Section 6 for how this is simulated.
2. **Offline admission** — an admin creates the enrollment directly for a student who was admitted in person. No payment step is involved. The enrollment becomes active immediately.

### 5.2 Enrollment lifecycle
An enrollment has a status: awaiting payment, active, or payment failed. This phase has **no cancelled status** and **no time-based expired status**. Nobody cancels an enrollment in the app. A student who wants out contacts customer support, and staff handle that outside the system.

Active means the student acquired the course during the current opening and may open paid content while that opening lasts. Closing the course ends that access. The old enrollment stays on record as history, and it does not grant access after the close or after a later reopen. A new opening needs a new enrollment.

### 5.3 What an enrollment remembers
At minimum: which student, which course, which delivery mode, how it was created (online purchase or offline admission), when it started, its current status, the price actually paid at the time of purchase (Section 4.1), and which opening it belongs to. It does not store an expiry time.

### 5.4 Buying the same course again
While a student already has an active enrollment for the current opening, they do not buy that course again. After the Master Admin closes the course and later reopens it, the previous enrollment does not apply, and the student can purchase it again. The same is true of offline admission: an admission from the previous opening does not carry over; a new admission does.

---

## 6. Purchase & Payment — Business Rules (Manual Mode)

Real bKash/Nagad gateway integration is out of scope for this phase (it depends on merchant credentials the client does not yet have). Phase 2 instead implements the **manual verification flow** described in the PRD, which is a complete, real purchase flow from the student's point of view — only the final payment confirmation step is manual rather than automated.

### 6.1 The purchase flow (course or book)
1. The student chooses to buy a course, or places a library book order with online payment selected.
2. The student is shown the coaching center's payment number and asked to pay manually (outside the app, via their bKash/Nagad app) and then enter the transaction reference ID they received.
3. A payment record is created in a pending state, linked to whatever is being purchased (the course enrollment or the book order).
4. An admin reviews pending payments and marks each one as successful or failed, after checking the transaction reference against what actually arrived.
5. On success: a course purchase activates the enrollment for the current opening; a book order advances to paid/ready. On failure: nothing is granted, and the student can be informed. Verifying a course payment does not grant access if the course is closed.

### 6.2 One payment concept, two uses
Design the payment record so it can represent a payment for *either* a course enrollment or a book order, rather than building two separate, parallel payment systems. This mirrors the PRD's own design (a generic payment entity distinguished by what it's paying for) and avoids duplicating the verification workflow.

### 6.3 Cash payments (library only)
A book order may also be paid in cash on pickup, with no payment record and no online step at all — the order simply moves through its status states (Section 7) until an admin marks it collected. Marking a cash payment received is restricted to the Master Admin, consistent with the rule that System Admins never handle money directly.

### 6.4 What the student sees while pending
A student should be able to see their own purchase/order history and its current status (pending, successful, failed) at any time, even before an admin has acted on it.

### 6.5 No online refund, and no cancellation
There is no refund action, no refunded payment state, and no cancellation of a course purchase or a book order. Money returned to a student, or a delivery the student no longer wants, is handled by customer support outside the app. The system must not revoke an enrollment or an order from inside the purchase flow. Ending course access is the Master Admin close action in Section 4.1, which ends the opening for every student.

---

## 7. Library Module — Business Rules

### 7.1 The book catalog
A book has: an English title, a Bangla title, author, cover image, description, price, and an available/unavailable flag. Only the titles are bilingual, and a student can find a book by either title. Author and description are a single language. There is no stock count or inventory tracking in the MVP — "available" is a simple admin-controlled toggle, not a quantity.

### 7.2 Placing an order
A student browsing the library can order a book, choosing:
- **Fulfillment method:** pickup at the coaching center, or delivery.
- **Payment method:** online (manual verification per Section 6) or cash on pickup.
- Contact phone number is required regardless of method.

Delivery is fully manual. The app does not calculate shipping, call a courier, or track a parcel. For a delivery order the student must enter a **full address**. A second, optional field (area or landmark) exists so staff can scan the list. The Master Admin or a System Admin reads that address and arranges delivery themselves, then updates the order status.

### 7.3 Order status
The exact sequence of states differs by payment method:
- **Online payment:** placed → paid → ready → delivered/collected.
- **Cash on pickup:** placed → ready → collected (no "paid" state, since payment happens at pickup).

There is no cancelled state. A student who wants to cancel contacts customer support, outside the app.

Fulfillment status (ready, delivered, collected) may be updated by either admin role, because both handle delivery. Recording that cash was received stays Master Admin only (Section 6.3).

### 7.4 No delivery logistics
There is no shipping cost calculation, no courier integration, and no delivery tracking beyond the status field itself. Fulfillment is handled by the coaching center's staff outside the app; the app only reflects the current status and stores the address they need.

---

## 8. Open Questions and Assumptions

### 8.1 Assumption made in writing this scope
**A1 — Admin management screens are Stage 8 (`web-catalog`).** The API remains the authority. The web panel exposes the management calls already built for `MASTER_ADMIN` and `SYSTEM_ADMIN`: courses, content, files, close and reopen, books, pending-payment verification, offline admission, and book-order fulfillment. It does not add student catalog, purchase, lecture playback, or order placement. Role limits stay the API's: System Admins do not set prices, delete, archive, close, reopen, verify payments, or record cash. One admin read, `GET /api/v1/book-orders`, is part of this stage because cash orders have no payment row and cannot be listed today.

### 8.2 Decided

- **Access window.** No per-course duration and no expiry date. The Master Admin closes a course to remove it from the student catalog and end access. Reopening starts a new window. Previous purchases and admissions do not carry over. See Section 4.1 and Section 5.4.
- **Repeat purchase.** Not while the student already has access for the current opening. Allowed, and required, after a close and a later reopen. See Section 5.4.
- **Cancellation.** None in the app, for course purchases or book orders. Customer support handles that manually. See Section 6.5 and Section 7.3.
- **Refunds.** No online refund. See Section 6.5.
- **Bilingual text.** English and Bangla titles only, for courses and books, and both are searchable. A course also has one content language, Bengali or English, chosen at creation. Other fields are that single language. See Section 4.1 and Section 7.1.
- **Free materials.** On a freemium course, a course-level material can be marked free on its own. A free lecture or free course-level material cannot later be made paid. A full-free course cannot be turned into a freemium course. See Section 4.1a.
- **Closed courses in the catalog.** Students do not see a closed course, and its free lectures are not given a separate lock. See Section 4.1 and Section 4.4.
- **Files.** Lectures are YouTube links. Each material file is at most 100 MB. This phase stores files on the local API server. A later phase moves upload and download to Cloudinary. See Section 4.3 and Section 10.
- **Delivery address.** A delivery order stores a full address and an optional area or landmark. Staff fulfill it themselves. See Section 7.2.

### 8.3 Questions before implementation planning

None. The questions that were open in this section are decided in Section 8.2.

---

## 9. Incremental Task Breakdown

This phase is intentionally broken into stages with their own exit conditions, so it does not need to be built or reviewed as one single pass. Each stage should be demonstrably working before the next begins; later stages depend on earlier ones.

### Stage 1 — Course catalog and content structure (admin-facing, API only)
Build the course entity, its English and Bangla titles, its single content language, its status (draft/published/archived), its access type (freemium or full free, fixed at creation), the Master Admin close/reopen action, the content tree (sections and lectures, arbitrary depth, ordering, reordering), the per-lecture free flag and the course-level material free flag on freemium courses, and lecture content (YouTube link, notes, materials up to 100 MB stored on the local server). No student-facing access yet — this stage is entirely about admins being able to construct a course. Do not add an access-duration or expiry field. Do not allow a full-free course to become freemium, or a free lecture or free course-level material to become paid.
**Exit condition:** a course with a multi-level tree and lecture content can be created, edited, and reordered through the API; both titles are stored and the content language is one of Bengali or English; a freemium course can mark any set of lectures free, including none, and can mark a course-level material free; a file over 100 MB is rejected; only the Master Admin can close and reopen it; and the tree is rejected if it contains anything other than sections and lectures (Section 3).

### Stage 2 — Library catalog (admin-facing, API only)
Build the book entity, its English and Bangla titles, its single-language author and description, and its available/unavailable toggle. Cover images use the same local file store as course materials.
**Exit condition:** books can be created, edited, and toggled through the API, and both titles are stored.

### Stage 3 — Student-facing browsing (read-only)
Expose published, open courses and available books to students: course list, course detail (including the content tree's structure, titles only), book list, book detail, and search by English or Bangla title. Closed, draft, and archived courses are absent. No purchase yet, no lecture content access yet.
**Exit condition:** a student can search and browse the catalog and see what is currently offered, but cannot yet acquire or access anything, and cannot see a closed course.

### Stage 4 — Purchase and order placement
Build the manual-payment purchase flow (Section 6) for paid course content and for books, and the book-specific order flow including cash-on-pickup and the delivery address fields (Section 7). A full-free course has no purchase step. A student who already has an active enrollment for the current opening cannot start another purchase of that course. This stage creates pending enrollments/orders and pending payment records; it does not yet grant access. Do not build cancellation.
**Exit condition:** a student can initiate a course purchase or a book order through either payment method, including a delivery address when delivery is chosen, and can see its pending status in their own purchase history. A second purchase of a course they already hold for this opening is refused.

### Stage 5 — Payment verification and access grant
Build the admin-side verification step: reviewing pending payments, marking them successful or failed, and the resulting effects — activating a course enrollment for the current opening, advancing a book order's status, or marking a cash order collected. Also build the offline-admission path for courses (admin grants an enrollment directly, no payment). Do not build a refund or a cancellation. A successful course payment does not grant access when the course is closed.
**Exit condition:** a full purchase can be completed end to end — student purchases, admin verifies, student gains access (for a paid course that is open) or the order visibly advances (for a book) — and an offline admission grants course access without any payment step. Either admin can advance delivery status. Only the Master Admin can record that cash was received.

### Stage 6 — Content access
Gate content as in Section 4.4. Any authenticated student can open free lectures and free course-level materials on an open freemium course, and all content on an open full-free course. Paid content requires an active enrollment from the current opening and an open course. A closed course is hidden from students and serves no content. Reopening does not restore the previous enrollment; access after a reopen requires a new enrollment.
**Exit condition:** those cases behave as stated — free content, a free course-level material, full-free content, paid content with an active enrollment from this opening, a closed course hidden with no content, and a previous enrollment that does not work after reopen.

### Stage 7 — Mobile home page and navigation
Build the app's home screen: a header showing the logged-in student's basic info, and two tabs — Courses and Library — wired to the browsing, search, purchase, and content-access work from the earlier stages.
**Exit condition:** a student opens the app, lands on the home page, and can reach everything built in Stages 3 through 6 through the Courses and Library tabs, including search in either language.

### Stage 8 — Web admin catalog

Build Next.js screens so a signed-in Master Admin or System Admin can do the course and library management the API already allows. Reuse the Phase 1 admin shell, session, and English/Bangla catalogs. Do not build a student catalog, a purchase form, a lecture player, or a student order form on the web.

Implementation is checked with scriptable commands (`lint`, `build`, OpenAPI type check, mocked component tests). Shahriar runs the browser exit checklist manually. The agent does not open a browser.

**Exit condition:** a Master Admin, in the browser against the running API, can create and publish a course, build its tree and lecture content, set the price, close and reopen, create a book and mark it available, verify a pending payment, admit a student offline, and advance a book order, including recording cash. A System Admin can edit course and book content and advance delivery, and does not see price, delete, archive, close, reopen, payment verification, or cash-collected controls.

---

## 10. What Remains Out of Scope

Exams in any form (including the standalone/open-access type discussed in Section 3 — only the modeling accommodation is made now, not the feature itself), attendance, notices, push notifications, real payment gateway integration, batches as a scheduling/targeting concept, student features on the web, online refunds, in-app cancellation, and **Cloudinary integration**. This phase stores files on the local API server and caps each material file at 100 MB. A later phase must replace that local store with Cloudinary for upload and download. None of the out-of-scope items should be started as part of Phase 2.
