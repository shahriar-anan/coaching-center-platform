# Step 3 — Purchase and SSLCommerz

API only. Depends on the public catalog existing, because the shop has to show a price per exam and a price for the module.

## Outcome

A student can start an SSLCommerz checkout for one exam or for one module. A verified payment grants access. A module grant covers exams published later in that module. An exam grant covers that exam only. The roster grant still works beside it. Take exam, the question PDF, and the routine accept either grant.

## Bind these

- Credentials and store id come from environment variables. The plan names the variable names. It does not contain secret values.
- Verify the callback or IPN on the server before granting access. A browser redirect alone does not grant access.
- The same callback twice grants access once.
- Failed and cancelled checkouts do not grant access. The Master Admin can still mark the student on the roster.
- System Admin cannot set live exam prices and cannot verify or alter live exam orders. `EXAM_PRICE` is Master Admin only. There is no staff call that marks a live exam order paid.
- Do not reuse `exam_module_purchases` or the `EXAM_MODULE` payable rows. Those belong to the superseded model.
- Course and book checkout is out of this delivery. Do not change `payments`, `PAYMENT_VERIFY`, or the bKash/Nagad verification screens. Exam orders use SSLCommerz on their own tables.
- Prices live on the live exam module and the live exam. The migration is the next Flyway version after step 2b (`V31`). An exam with a null `liveAt` can be purchased and stays on the module list. Take exam stays closed until `liveAt` is set and the window is open.
- Access for a sitting is: published exam, category branch not archived, and (roster row for the module or a successful grant for the module or for that exam).

## Where the code is

- Roster check: `LiveExamModuleService.getPublished`, `LiveExamService.list`, `LiveExamService.get`, and `LiveExamSittingService.take`.
- Course and book payments (out of this delivery): `purchases/PaymentAccessController`, `PaymentAccessPermissionContributor`.
- Superseded exam purchase to leave alone: `ExamModulePurchaseController`, `ExamModulePurchaseService`.

## Plan must name

- Order states from initiation through success, failure, and cancel.
- The initiate call, the callback or IPN call, and the grant written on success.
- How a module grant is evaluated when an exam is published after the payment.
- Idempotency key and what a repeated IPN returns.
- What Take exam returns with no grant, with a roster row, with an exam grant, and with a module grant.
- The `*IT` cases, using a fake gateway or a signed test payload. Do not call SSLCommerz from the test suite.

## Blocked on

A public HTTPS domain for the callback. The plan records that as an external blocker with no owner inside the repo. Sandbox versus live credentials is still an open question in [decisions.md](../decisions.md). The plan can name both profiles and must not pick the live one silently.

## Out of this step

The checkout pages (step 6), Google, and the notification copy. The access grant is what step 5's reminder uses. Course and book payments, including manual bKash/Nagad verification.
