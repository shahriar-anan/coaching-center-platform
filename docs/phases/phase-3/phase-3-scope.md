# Phase 3 Scope: Exam Module

**Depends on:** Phase 1 (Foundation & Identity) and Phase 2 (Catalog & Acquisition) must be complete. The permission/delete conventions from Phase 1 and the purchase/payment pattern from Phase 2 are reused, not redesigned.
**This phase is backend-only.** API implementation and automated testing. No mobile or web UI work is part of this phase.
**Companion file:** `phase3_client_and_nontechnical_tasks.md` covers decisions and non-code tasks.

This document describes business scope and rules only — not how to structure code or which tools to use.

---

## 1. What Phase 3 Is

Phase 3 builds the Exam Module: a standalone, purchasable catalog item — structurally the same *kind* of thing as a Course (something browsable and purchasable) — that can optionally also be linked to an existing course. It covers MCQ and written exams, a reusable question bank, a Master-Admin-only tool for curating that bank from existing exam content, and the full attempt/evaluation/result lifecycle for both exam types.

---

## 2. Bilingual Content (Cross-Cutting Requirement) — Scoped Narrowly

**Bilingual support applies only to names, titles, and descriptions/rules text — never to question content.** Specifically:

**Bilingual (Bangla and English), both versions required from the admin:**
- The module's own title and root-level description.
- Section and paper titles/names in the content tree.
- Each MCQ and written component's own description/rules text (Section 4.3).

**Single-language only — never bilingual:**
- **Everything inside a question itself:** the question text, MCQ options, the correct answer, and any explanation text. A question is authored and stored in exactly one language, whatever language the admin wrote it in. There is no second-language field for question content anywhere in this phase, including in the Question Bank (Section 8).

This is a change from the earlier project-wide assumption (Phase 1) that only the app's own UI chrome needed bilingual support, while admin-entered content would stay single-language. For the exam module, naming and descriptive text is now bilingual, but this does not extend to the academic content of a question — that stays single-language, matching how question authoring already works elsewhere. Whether the broader naming/description bilingual requirement should retroactively apply to Phase 2's course and library content is a separate decision (see `phase3_client_and_nontechnical_tasks.md`), not something this phase should assume or change on its own.

---

## 3. The Exam Module Is Not Part of a Course's Content Tree
## 4. Exam Module Structure

### 4.1 The module
An Exam Module is the purchasable unit — the thing a student browses and buys (e.g. "Physics Exam Module"). It has a title, a **root-level description** (an overview of the module as a whole — what it covers, who it's for), price, and a publish status (draft, published, archived) controlling catalog visibility, same pattern as a course.

This root-level description is distinct from, and sits above, the per-component descriptions described in Section 4.3 — it describes the module overall, not any single paper's rules.

### 4.2 The content tree
Under a module sits a **tree of arbitrary depth**, the same structural pattern used for a course's content tree in Phase 2: a module can be a flat list of papers, or go several levels deep (e.g. subject → sub-topic → paper), depending on what the content actually needs. There is no fixed number of levels.

Two kinds of nodes exist in this tree:
- **Section** — a named container, same role as a course's section.
- **Paper** — a leaf node. This is the actual exam unit (e.g. "Physics 1st Paper," "Physics 2nd Paper").

A module's tree must contain only sections and papers — nothing else nests inside it.

### 4.3 The exam paper
A paper is where the real exam content lives. A paper can carry:
- an **MCQ component**, and/or
- a **written component**.

A paper is not required to have both — some papers may be MCQ-only, some written-only, some both. This variability is intentional; different exam modules and different papers within the same module may have entirely different shapes.

**Each component carries its own description and rules, separate from the other.** The MCQ component has its own description/instructions text (e.g. marking scheme reminder, time limit, navigation notes), and the written component has its own, separate description/instructions text (e.g. how many images are expected, formatting expectations, deadline reminder). These are not shared or inherited from the paper or the module — a paper with both components holds two independent description/rules texts, one per component, in addition to the module's own root-level description (Section 4.1).

---

## 5. Linking an Exam Module to a Course

### 5.1 The link
An exam module may optionally be linked to **one** existing course, and only one (confirmed — one-to-one is sufficient; a module never needs to link to more than one course). This is a direct reference from the module to the course, not a tree relationship — the module still exists and is managed independently; the link only affects how access is granted (Section 5.2).

### 5.2 Bundled access — the core business rule
If a student has an active enrollment in a course, and that course has one or more exam modules linked to it, **the student already has access to those exam modules — no separate purchase step is required.**

Access to a course-linked module **mirrors the student's enrollment status in that course**: available while the enrollment is active, locked if the enrollment expires or was never active, consistent with how course content access itself already works (Phase 2, Section 4.4). This keeps the behavior predictable — a student doesn't retain exam access after their course access has lapsed, since the exam access was never separately paid for.

A course may have multiple exam modules linked to it (e.g. separate modules per subject).

---

## 6. Access Modes

Every exam module has exactly one access mode, and the mode determines both who can reach it and how:

| Mode | Who can access | Course link | Payment |
|---|---|---|---|
| **OPEN** | Any logged-in student | Must not be linked to a course | None — fully free |
| **ENROLLED_ONLY** | Students with an active enrollment in the linked course | Required — must be linked to exactly one course | None directly — bundled into the course purchase (Section 4.2) |
| **PURCHASE_REQUIRED** | Students who have purchased the module itself | Must not be linked to a course | Required — the module is sold on its own |

`OPEN` is the standalone/free case (e.g. the Bar Exam example — open to the whole student base, no purchase, no course). `PURCHASE_REQUIRED` is the standalone/paid case — the module is its own product. `ENROLLED_ONLY` is the course-bundled case described in Section 4.

A module's access mode and its course link must be consistent with the table above — an `OPEN` or `PURCHASE_REQUIRED` module cannot also be linked to a course, and an `ENROLLED_ONLY` module must be.

---

## 7. Purchasing a Standalone Module

For `PURCHASE_REQUIRED` modules, reuse the manual-payment purchase pattern already built in Phase 2 (Section 6 of the Phase 2 scope): the student submits a transaction reference for a manual bKash/Nagad payment, an admin verifies it, and access is granted on success. This is the same generic payment mechanism extended to a new payable type (the exam module), not a new payment system.

**Access is for the lifetime of the module (confirmed).** Once purchased, a student's access to a `PURCHASE_REQUIRED` module does not expire — unlike course enrollment, there is no access-duration concept to configure for a standalone exam module purchase.

---

## 8. The Question Bank

### 8.1 Questions are the atomic reusable unit
A question — whether MCQ or written — is its own record: content, marks, a category/subject tag, and (for MCQ) its options and correct answer. **All of this content is single-language** (Section 2) — a question is authored in one language only, with no bilingual field anywhere on it, including when it's later pulled into a named Question Bank (Section 8.3). Questions are created as they're needed for a specific paper, but they exist independently of that paper once created, and the same question can be attached to more than one paper over time.

### 8.2 Creating exam content
When building a paper's MCQ or written component, an admin can add questions directly (written specifically for that paper) without any extra step — there is no requirement to pre-populate a bank before building an exam. Reuse becomes possible once a question exists, but it is not mandatory up front.

### 8.3 Question Bank curation — Master Admin only
This is a distinct, additional capability on top of the above, restricted to the Master Admin:

The Master Admin can **browse questions across existing exam papers** — regardless of who created them or which paper they currently belong to — and **select a combination of them to form a named Question Bank**. A single bank can mix MCQ and written questions freely; it is a curated collection for future reuse, not a type-restricted list.

Creating a bank this way does not remove or detach a question from the paper it's currently used in — a question can belong to a bank *and* remain in active use in its original paper(s) at the same time. The bank is an organizational grouping, not a move operation.

This capability is for the Master Admin only; System Admins do not get access to question-bank curation, consistent with the Phase 1 pattern of some capabilities being Master-Admin-exclusive even where they aren't delete actions.

---

## 9. MCQ Exam — Business Rules

These rules were confirmed earlier in the project and carry directly into this phase:

- **No negative marking.**
- **One attempt per student** per MCQ component.
- The exam's time window and the per-attempt timer are **server-authoritative** — the student's device clock is never trusted for timing decisions.
- A paper's MCQ component has its own schedule: a start/end window within which students can attempt it, independent of the module's own publish status and independent of any other paper's schedule in the same module.
- **Lifecycle:** draft → scheduled → active (opens automatically at its start time) → ended → results published. The active/ended transition is time-driven, not a manual admin action.
- On submission (manual or automatic at time expiry), scoring is immediate and automatic.
- Whether the student sees their score immediately or only after the admin publishes results is configurable per MCQ component, matching the existing PRD rule for MCQ result visibility.
- When that score is visible, the student also sees the answer review for the attempt: the option they chose, the correct option, and the explanation. The review stays hidden while the score is hidden.

---

## 10. Written Exam — Business Rules

Also confirmed earlier and carried into this phase:

- The student uploads photographed answer pages for the written component.
- **Before final submit, the student can freely re-upload, reorder, and remove images as many times as they want (confirmed).** There is no limit on edits while the submission is still in a draft/unsubmitted state.
- **Once the student performs the final submit action, the submission is locked and final** — no resubmission after that point, and no admin-side reopening either.
- A written component has its own deadline, independent of other components in the same paper or module.
- After submission, the status moves to under-review until an admin evaluates it.
- Evaluation is manual: an admin opens the submission, reviews the uploaded pages, enters marks and written feedback, and marks it evaluated.
- Both Master Admin and System Admin can evaluate written submissions and publish results — already confirmed, no restriction here.

---

## 11. Results

### 11.1 Independent grading
When a paper has both an MCQ and a written component, each is graded and produces its own result **independently** — a student's MCQ score is not dependent on whether the written component has been evaluated yet, and vice versa.

### 11.2 Combined view where it makes sense
Despite being graded independently, a paper with both components should also offer a **combined total** — the MCQ marks and written marks rolled into one figure for that paper — for cases where the coaching center wants a single pass/fail or ranking figure per paper. This combined view sits alongside, not instead of, the two individual component results.

This combined rollup should only apply where both components exist on a paper; a paper with only one component has only one result, with no separate "combined" concept needed.

**The combined total is a simple sum of the two component scores (confirmed for now).** No weighting mechanism is required in this phase — the combined total is just MCQ marks obtained plus written marks obtained.

### 11.3 Publishing
Results (whether a single-component result or the combined view) remain hidden from the student until an admin publishes them, except where an MCQ component is explicitly configured for immediate visibility (Section 9).

The answer review follows that same rule. For an MCQ, the review is the chosen option, the correct option, and the explanation frozen onto the attempt. For a written question, the review is the explanation frozen at submit. A written question has no correct option. The review is hidden while that component's result is hidden.

### 11.4 Pass mark
Each paper has a pass percent, a whole number from 0 through 100. The admin sets it before the paper's MCQ component can be scheduled and before its written deadline can be saved.

The paper score is the combined total when the paper has both components, and the single component score when it has only one. The maximum is the matching maximum. The student passes when `score × 100` is at least `pass percent × maximum`. Otherwise the student fails.

The percent used for a student is the percent on the paper when that student started the MCQ. If the student did not sit an MCQ, it is the percent when they submitted the written component. A later change to the paper's pass percent applies to students who have not yet started.

Pass or fail is part of the paper result, not a separate result for each component. It is shown to the student when the paper score itself is visible. On a paper with both components, that is when both component results are visible. On an MCQ-only paper set to immediate results, it is shown with the score.

### 11.5 Rank and leaderboard
Rank uses the paper score from Section 11.4, highest score first. Students with the same score share a rank, and the next rank skips (1, 1, 3). Students who did not sit are left off the list. Rank does not use a weight.

An admin can read the leaderboard before results are published. A student can read it only after every component on that paper has had its results published. The board shows each sitting student's name, rank, score, maximum, and pass or fail. It does not show a phone number.

A student's own rank is included on their paper result under the same publish rule. Immediate MCQ visibility shows that student's score and answer review. It does not show rank or the leaderboard, because those compare students.

### 11.6 Question statistics
An admin can read statistics for the questions on one component. Students cannot.

MCQ statistics count submitted attempts only. For each question they show how many attempts included it, how many chose the correct option, the correct percent, and how many attempts chose each option. Written statistics count evaluated submissions only. For each question they show how many scripts were marked, the average marks, and that average as a percent of the question's maximum.

Statistics use the questions and options frozen onto each attempt or submission. An in-progress attempt and an unsubmitted draft are not counted.

---

## 12. Settled Decisions

All four items raised during planning are now confirmed and folded into their relevant sections above; this list is a quick reference, not a set of open items:

- **Standalone purchase access:** lifetime, does not expire (Section 7).
- **Combined score:** simple sum of the two component scores, no weighting mechanism (Section 11.2).
- **Course linking:** one-to-one only — a module never links to more than one course (Section 5.1).
- **Written submission edits:** unrestricted re-upload/reorder/remove before final submit; locked only after final submit (Section 10).
- **Answer review:** the correct option and the explanation are shown with the MCQ score, and the written explanation is shown with the written result. Both stay hidden while that result is hidden (Section 11.3).
- **Pass mark, rank, leaderboard, and question statistics:** a paper has a pass percent; rank and the leaderboard use the unweighted paper score; question statistics are an admin read of submitted MCQ attempts and evaluated written scripts (Sections 11.4–11.6).

No open questions remain for this phase.

---

## 13. Incremental Task Breakdown

Backend only. Each stage should be demonstrably working, with tests, before the next begins.

### Stage 1 — Exam Module and content tree
Build the module entity (bilingual title, bilingual root-level description, price, publish status) and its content tree (sections and papers, arbitrary depth, ordering, bilingual names throughout). No components, no access rules, no purchase yet — this stage is purely about the module and paper structure existing and being manageable.
**Exit condition:** a module with a multi-level tree of sections and papers can be created, edited, and reordered through the API, with every name and description entered and retrievable in both Bangla and English.

### Stage 2 — Question entity and authoring
Build the Question record (MCQ and written variants, category/subject tag, marks). **Question content is single-language only** — do not apply the bilingual pattern from Stage 1 here; a question's text, options, and correct answer have no second-language field. Build the ability to add questions directly to a paper's MCQ or written component.
**Exit condition:** questions can be created and attached to a specific paper's components through the API, each question stored in one language only.

### Stage 3 — Paper component configuration
Build the MCQ component's configuration (bilingual description/rules, schedule/window, duration, result-visibility setting) and the written component's configuration (bilingual description/rules, deadline) — each component's description/rules is independent of the other's and of the module's own root-level description (Section 4.3). Build the combined-total concept for papers that have both (Section 11.2). Build the paper pass percent (Section 11.4).
**Exit condition:** a paper can be configured with a scheduled MCQ component, a deadline-bound written component, or both, each with its own bilingual description/rules text, a pass percent, and a combined-total view available when both exist.

### Stage 4 — Course linking and access-mode enforcement
Build the course-link relationship and all three access modes (`OPEN`, `ENROLLED_ONLY`, `PURCHASE_REQUIRED`), including the validation rule tying access mode to whether a course link is present (Section 5), and the bundled-access rule for `ENROLLED_ONLY` modules (Section 4.2).
**Exit condition:** a student's access to a module correctly reflects its access mode — freely open, mirroring a linked course's enrollment status, or gated behind a direct purchase — verified for all three modes.

### Stage 5 — Standalone module purchase
Extend the Phase 2 payment pattern to cover exam modules as a new payable type, for `PURCHASE_REQUIRED` modules.
**Exit condition:** a student can purchase a standalone module through the existing manual-verification flow, and gains access once an admin confirms the payment.

### Stage 6 — MCQ attempt flow
Build the full MCQ attempt lifecycle: starting an attempt, enforcing one attempt per student, server-authoritative timing, submission (manual and automatic at expiry), and immediate automatic scoring.
**Exit condition:** a student can attempt an MCQ component exactly once, cannot exceed the time window regardless of client-side manipulation, and receives an automatically computed score on submission. When that score is visible, the response includes the correct option and the explanation.

### Stage 7 — Written submission flow
Build the written submission lifecycle: image upload, pre-submission editing, final submit (locked thereafter), and the under-review state.
**Exit condition:** a student can upload and finalize a written submission, after which no further student-side changes are possible, and it is visible to admins as pending evaluation.

### Stage 8 — Evaluation and results
Build the admin-side evaluation workflow (marks + feedback entry, marking evaluated) and the results system: independent per-component results, the combined-total rollup where applicable, publish/unpublish control, the answer review, pass or fail, rank, the leaderboard, and question statistics (Sections 11.3–11.6).
**Exit condition:** an admin can evaluate a written submission and publish results; a student sees correct results (individual and combined, as applicable) only once published, except where immediate MCQ visibility is configured. A visible MCQ result includes the correct option and the explanation. A visible written result includes the explanation. The paper result includes pass or fail. After publish, a student can see rank and the leaderboard. An admin can read the leaderboard before publish and can read question statistics.

### Stage 9 — Question Bank curation (Master Admin only)
Build the Master-Admin-only tool for browsing questions across existing exam papers and grouping a selected set — mixing MCQ and written freely — into a named, reusable Question Bank.
**Exit condition:** a Master Admin can create a named bank from questions pulled across multiple different papers, the original papers' use of those questions is unaffected, and a System Admin cannot access this capability.

---

## 14. What Remains Out of Scope

Any mobile or web UI for exams (this phase is API and tests only), attendance, notices, batches as a real entity (course-linking in this phase is a direct module-to-course reference, not a batch-based targeting mechanism), and real payment gateway integration (Stage 5 reuses Phase 2's manual-verification pattern, not a live gateway).
