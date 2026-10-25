# A8 — Purchase email to the Master Admin

Written 25 October 2026. Until now a successful payment emailed only the student (the receipt), and D7 in [README.md](README.md) said staff get no email. Shahriar changed that on 25 October 2026: the Master Admin is told about every purchase. [README.md](README.md) practices apply.

## Decisions

| # | Question | Choice |
|---|---|---|
| N1 | How often | One email per successful payment. No daily summary. |
| N2 | Recipient | The email on the active Master Admin account. No separate setting. It follows the account through the handover. |
| N3 | When | Queued in the same step as the student's receipt: after the order is `SUCCEEDED` and its grant exists. A failed or cancelled order sends nothing. A repeated IPN or return sends nothing more. |
| N4 | Content | Bangla and English, like the receipt: student name, phone, email, exam or module title, amount, payment channel when known, `tranId`, and the payment time in Asia/Dhaka. No card number. |
| N5 | Skip | No active Master Admin with an email when sending, or the order no longer `SUCCEEDED`: the row becomes `SKIPPED`. A send failure retries like every exam mail (3 attempts). |
| N6 | Other staff | None. No in-app notice. The A3 badges and the A7 payments list stay the other staff views. |

## Schema

Flyway `V38__exam_mail_purchase_notice.sql` replaces `exam_outbound_mail_kind_check` to allow `PURCHASE_NOTICE`. Key: the order id, so `(kind, message_key)` keeps it to one row per order. `user_id` is the Master Admin.

## Code

`ExamOutboundMail.PURCHASE_NOTICE`. `ExamMailQueue.enqueueForSucceededOrder` inserts it beside `PAYMENT_RECEIPT`. `ExamMailTexts.prepare` writes it. Delivery and retries are unchanged.

## Tests (`ExamMailIT`)

- A verified payment queues one receipt to the student and one notice to the Master Admin. The notice text names the student, phone, exam, amount, channel and `tranId`.
- A second verification of the same order adds no row.
- A failed verification queues nothing.
- With no active Master Admin, the notice is `SKIPPED` and the receipt still sends.
