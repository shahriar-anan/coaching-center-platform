# Slice 10 — A8 purchase email to the Master Admin

Done 25 October 2026.

- Flyway `V38__exam_mail_purchase_notice.sql` adds `PURCHASE_NOTICE` to `exam_outbound_mail_kind_check`.
- `ExamMailQueue.enqueueForSucceededOrder` queues the notice beside the receipt when the order's grant exists. Key: the order id. Recipient: the oldest active Master Admin with an email (production has one).
- `ExamMailTexts.purchaseNotice` re-checks at send time: the order is `SUCCEEDED` with its grant, and the recipient is still an active Master Admin with an email. Otherwise the row is `SKIPPED`. Bangla and English text: student name (`-` without a profile), phone, email, title, amount, channel (`-` when unknown), `tranId`, time in Asia/Dhaka. The receipt and the notice share a `titles` helper.
- `ExamMailIT` 13 run, 0 failures: the notice reaches an active Master Admin with the expected details and no `val_id`, a second verification adds no row, failed and cancelled orders queue none, and a recipient made inactive before the send is `SKIPPED` while the receipt still sends.

Live on staging since 02:45 UTC (08:45 Asia/Dhaka) on 25 October 2026. Another session deployed a jar built from the working tree, so this uncommitted A8 work went with it: jar SHA-256 `aa3a61fd…ff65e2`, Flyway 38 applied. Checked afterwards: `ExamMailTexts.class`, `ExamMailQueue.class` and `ExamOutboundMail.class` in that jar are byte-identical to a clean local build of the committed A8 code, and the jar's `V38` matches the file (SHA-256 `341d9c94…2075e3`). No A8 code changed after the deploy, so the VPS needs no redeploy for A8. Do not edit `V38`: it is applied on the VPS. Any schema fix goes in `V39`.

Not tested live yet: the rebuilt staging database has no catalog or orders. The first successful staging payment will email the client's Master Admin inbox (`symam.law@gmail.com`).

Full suite from clean: 302 run, only `BookOrderIT` and `CoursePurchaseIT` fail (known, reused test database).

Not tested: no active Master Admin at enqueue time (no row is queued). Testing it would mean deactivating every Master Admin in the shared test database, which breaks the classes that sign in as the bootstrap Master Admin.
