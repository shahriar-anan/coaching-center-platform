# api-live-roster tests

PostgreSQL only. No manual check.

Shared setup: a published module with one published exam and one draft exam. Three students, two of them sharing a name and differing in phone and email.

## LiveRosterIT

- A System Admin mark returns `403` and creates no row.
- Marking by email marks that one account. Marking by phone marks that one account.
- Name search returns both students who share the name, with phone and email. Marking one does not mark the other.
- A paste of one known email, one known phone, and one unknown email marks two students and returns the unknown value.
- The marked student receives the published exam and does not receive the draft. An unmarked student receives no exam for that module.
- An exam published after the mark appears for the marked student without a second mark.
