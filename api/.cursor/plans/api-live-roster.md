# api-live-roster

**Outdated (2026-10-23):** an unmarked student sees none of the module. The roster grant stays, and a Master Admin is still the only role that changes it. Seeing the published catalog does not require a roster row.

Stage 3. Start only after `api-live-exam` exits. Spec Section 5. Tests: `api/.cursor/tests/api-live-roster.md`. Rule: `api/.cursor/rules/api-live-roster.mdc`.

## Slice 1 — Manual roster

- Only a Master Admin adds or removes a student. A System Admin receives `403`.
- Search by email or phone returns one account. Search by name may return several, each with phone and email.
- A paste of emails and phones marks each exact match. Unknown values are returned and not marked.
- A marked student listing published exams in that module receives them, including an exam published after the mark. An unmarked student receives none of that module.
- Draft exams are absent from the student list.
- Removing a student who has no participant row hides the module. Removing a student who already has a participant row leaves that row. Participant rows themselves are created in the next stage. This stage only keeps the roster delete from erasing a row when one exists.

## Exit

A Master Admin can mark a student by email, phone, or a chosen name match, and by a paste. That student sees the published exams and not the drafts. A System Admin cannot mark anyone.
