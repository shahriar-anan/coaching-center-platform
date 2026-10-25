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
| [api-completion/](api-completion/README.md) | A1–A6 done 25 October 2026: inbox and deadline settle job (`V35`, `V36`), instructor directory, participation counts, student status, authoring deletes, instructor unassign. A7, the staff view of exam payments (`V37`), done the same day. |
| [vps-staging-deploy.md](vps-staging-deploy.md) | Done 25 October 2026 except the nightly backup (slice 9, deferred). Record: `../reports/vps-staging-deploy.md`. |
| [master-admin-handover.md](master-admin-handover.md) | 25 October 2026: database rebuilt with the client's Master Admin, change own phone deployed and checked live. The staff Account page waits. Record: `../reports/master-admin-handover.md`. Change own phone, staff Account page, and the database rebuild with the client's Master Admin. No Flyway version. |
| [auth-and-mail.md](auth-and-mail.md) | Slices 1–9 implemented 24 October 2026. Refund stays unstarted. Brevo SMTP connected on the VPS on 25 October 2026 (section 4). The admin mail-test endpoint was removed the same day. |

Steps 1, 2, 2a, 2b, and 3 are implemented. Step 1 owns Flyway `V29`. Step 2 owns `V30`. Step 2b owns `V31`. Step 3 owns `V32`. Google identity is `V33`. Exam mail is `V34`. The deadline index is `V35`, student notices `V36`, and the order payment channel `V37`.

## Audit

[server-migration-audit.md](server-migration-audit.md) is done. The report is `../domain-ssl-migration-audit.md`. The new box is bought. See [../decisions.md](../decisions.md). Do not run the audit again.
