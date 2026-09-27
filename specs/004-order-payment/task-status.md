# Actual task status audit — 004

Audited against source and tests on 2026-09-25.

Partially implemented: T001, T002, T003, T006, T007, T010, T013, T015, T022–T024, T036, T038, T047–T048, T054.

Verified automated work: payment creation, authoritative successful callback, duplicate callback idempotency, ticket creation, list/detail/ticket endpoints in `backend/test_payments.py`.

Not complete: all Android screen/ViewModel migration tasks, HMAC webhook verification, provider polling/recovery, status-event auditing, full order/payment state coverage, mapper/UI tests, 100-way tests, task-specific acceptance evidence, and manual/device tests.

No task checkbox is marked complete solely because a partial implementation exists.

## Re-audit after Android order-flow implementation — 2026-09-26

Completed in this pass: Android quote/create/pay/order/ticket contracts and repository; server-authoritative order confirmation and payment UI; stable lockId/orderId navigation; order list/detail recovery; payment idempotency and polling; backend detailed order responses, pagination, expiry seat release, status-event writes, webhook de-duplication, ticket retry-on-read; `backend/test_orders.py` and Android repository/order/payment unit coverage.

Remaining: real-provider credentials/compliance, exhaustive manual accessibility review, and the four end-to-end acceptance scenarios requiring human observation. The automated duplicate/idempotency, signature, order, ticket-retry-on-read, backend, JVM and emulator checks are complete for the current environment.
