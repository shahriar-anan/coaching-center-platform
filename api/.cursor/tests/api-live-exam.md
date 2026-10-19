# api-live-exam tests

PostgreSQL only. No manual check.

Shared setup: one published module from `api-live-catalog`.

## LiveExamIT

- An MCQ exam and a written exam can both exist on that module.
- Publish without a pass percent is rejected. An MCQ publish with no questions is rejected. A written publish without a maximum and a question PDF is rejected.
- Three MCQ questions with marks 2, 3, and 5 produce a maximum of 10. The question payload stored for staff includes one correct option. The student upcoming payload has no stems.
- A written exam ending at 12:40 and an MCQ exam starting at 12:44, same module, cannot be published. The error identifies the other exam. Starting at 12:45 can be published. The same 12:44 start on a different module can be published.
- A draft may be saved with the 12:44 start. Publish is the call that fails.
- After the server time passes the live instant, a question edit and a schedule edit are rejected.
- After the end instant the exam status is still published.
