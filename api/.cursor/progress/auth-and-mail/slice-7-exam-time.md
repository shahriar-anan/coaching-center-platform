# Slice 7 — Exam-time mail

Done. Publishing an exam, publishing its module, adding a roster row, or adding a grant enqueues one exam-time mail for each active student who can sit a published exam with a non-null `liveAt`. A draft, a null `liveAt`, or an unpublished module sends nothing. Republish with a new instant sends once for that instant. The same key sends once. Two grants for the same student and exam still produce one mail. A module purchase can send the receipt plus one exam-time mail per dated published exam in that module.

`ExamMailIT` passed on PostgreSQL 17, database `coaching_center_test`.
