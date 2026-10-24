# Slice 6 — Payment receipt

Done. After the grant transaction commits, `LiveExamOrderTx.verify` enqueues one receipt for that succeeded order. A second validation does not send a second mail. Failed and cancelled orders send none. An SMTP failure leaves the order `SUCCEEDED` and the grant in place. The same row retries up to three times, then stays `FAILED`. The body names the exam or module, the amount, BDT, and the tran id. It does not include the val id, the phone, or the store credentials.

`ExamMailIT` passed on PostgreSQL 17, database `coaching_center_test`.
