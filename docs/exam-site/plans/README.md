# Plans

The planning agent writes one file here per step, named like the step: `01-written-publish.md`.

A plan adds slice order, endpoints, schema, and tests. It does not change a decision in `../decisions.md`.

| Plan | Status |
|---|---|
| [01-written-publish.md](01-written-publish.md) | Confirmed and implemented. Flyway `V29`. |
| [02-public-catalog.md](02-public-catalog.md) | Confirmed and implemented. Access rules and the paper-language flag. Prices stay out. Flyway `V30`. |
| [resolve-api-gaps.md](resolve-api-gaps.md) | Done. Steps 2a and 2b implemented. Step 2b owns Flyway `V31`. |
| [03-purchase-sslcommerz.md](03-purchase-sslcommerz.md) | Confirmed and implemented. Flyway `V32`. The “written for a go-ahead” line in that file is the original header. Checkout screens stay in step 6. |
| [04-google-sign-in.md](04-google-sign-in.md) | API code is in the tree. Flyway `V33`. The plan file was not marked confirmed, and it does not claim a live Google sign-in. The website button and the set-phone page stay in step 6. |
| [api-completion/](api-completion/README.md) | Confirmed 25 October 2026, implementation in progress. Goals A1–A6: inbox and deadline settle job (`V35`), instructor directory, participation counts, student status, authoring deletes, instructor unassign. |
| [auth-and-mail.md](auth-and-mail.md) | Slices 1–9 implemented 24 October 2026. Refund stays unstarted. Brevo SMTP is a deployment step in section 4, after this jar is the server process. |

Steps 1, 2, 2a, 2b, and 3 are implemented. Step 1 owns Flyway `V29`. Step 2 owns `V30`. Step 2b owns `V31`. Step 3 owns `V32`. Google identity is `V33`. Exam mail is `V34`.

## Audit

[server-migration-audit.md](server-migration-audit.md) is done. The report is `../domain-ssl-migration-audit.md`. The new box is bought. See [../decisions.md](../decisions.md). Do not run the audit again.
