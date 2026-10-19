# Slice 2 — Assigned modules, answer PDF, deadline rows

Done. An instructor listing modules receives only the assigned module. A module with no assignment is absent.

The assigned instructor and the Master Admin download the student's answer PDF. A System Admin, an instructor assigned to another module, and the student are rejected.

A System Admin can read the submission, including the evaluator name. A mark from that account returns `403`. A saved comment is on the student result, a blank comment stays absent, and the student body still has no evaluator.

A written retake with a stored PDF can be marked and stays off the live leaderboard. A live no-submit zero on the same exam remains on the board.

Opening the submission list after the deadline posts papers the student never submitted. No stored PDF is failed to submit and cannot be marked. A stored PDF is submitted and can be marked.

`mvn test -Dtest=LiveEvaluationIT,LiveSittingIT` on PostgreSQL 17.9, database `coaching_center_test`. LiveEvaluationIT: 12 tests, 0 failures. LiveSittingIT: 11 tests, 0 failures.

This closes slice 2.
