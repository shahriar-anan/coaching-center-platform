# Slice 9 — A7 staff payments, mail-test removal, deadline batch test

Done 25 October 2026, after the VPS staging deploy.

- A7: Flyway `V37__live_exam_order_payment_channel.sql`. `SslCommerzGateway` reads `card_type`, falling back to `card_brand`, trimmed to 64 printable characters, into `Verification.paymentChannel`. `LiveExamOrderTx.verify` stores it when the order becomes `SUCCEEDED`. That covers the IPN, the browser return and the recheck job, because they all go through `verify`.
- `GET /api/v1/live-exam-orders?status=&tranId=&q=`: Master Admin only, through the new permission `LIVE_EXAM_ORDER_READ`. `LiveExamOrderAdminService` uses SQL like the participation read. `accessGranted` means a grant row has this `order_id`. An unknown status is `400`.
- Mail test removed: `MailTestController`, `MailTestEndpointCondition`, `ErrorCode.MAIL_TEST_LIMITED`, `app.mail.test-enabled` and `AppProperties.Mail.testEnabled`, `SmtpEmailSender.sendPasswordResetTest`, the security permit, and three test classes. `SmtpEmailSenderTest` now checks redaction through `sendPlainNow`.
- `LiveSittingIT.theDeadlineJobSettlesAtMostOneBatchPerRun`: 501 due sittings inserted with the oldest deadlines in the table. The first run settles exactly 500, the second settles the last.
- Tests: `LiveExamPurchaseIT` 14, `SslCommerzGatewayTest` 12, `AuditLogIT` 2, `ExamMailIT` 12, `LiveExamPaymentRecheckTest` 6, `LiveSittingIT` deadline tests 4, `SmtpEmailSenderTest` 8, `DevExposeEmailCodesIT` 1. All pass.

Not done: failure isolation in the deadline job (one bad sitting does not block others) is still untested. Shahriar asked only for the batch limit.
