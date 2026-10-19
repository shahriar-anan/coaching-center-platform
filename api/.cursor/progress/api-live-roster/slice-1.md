# Slice 1 — Manual roster

Done. Flyway `V26__live_exam_roster.sql` adds `live_exam_roster`. Only a Master Admin can search, mark, paste, or remove. A System Admin mark returns `403` and writes no row.

Email and phone search each return one student, with phone and email. A name search can return several. Paste accepts email and phone only, marks each exact match, and returns values with no account. A marked student sees the published exams in that module, including an exam published after the mark, and does not see drafts. An unmarked student sees none of that module. Removing the roster row hides the module. It does not delete a participant row; participants are stored separately in the next stage.

`mvn test -Dtest=LiveRosterIT,LiveExamIT,LiveCatalogIT` on PostgreSQL 17.9, database `coaching_center_test`. Flyway applied V26. LiveRosterIT: 5 tests, 0 failures. The catalog and exam suites still pass with the roster gate.

Next: `api-live-sitting`.
