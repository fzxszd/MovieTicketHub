# Spec 004 Acceptance Criteria

- [x] Android uses stable `lockId` to load a server quote and displays server movie, venue, showtime, seats, item prices, fees, currency and total.
- [x] Order creation sends quoteVersion and an idempotency key; duplicate confirmation returns the same order.
- [x] Payment requests use a stable orderId/idempotency key and render server status; client UI cannot mark an order PAID.
- [x] Order list/detail and PAID-only ticket loading are available through the Android repository and screens.
- [x] Backend expires pending orders on read/write and releases unsold seats; status transitions are auditable and webhook events are deduplicated.
- [x] Duplicate webhook idempotency (100 repetitions) and configured HMAC signature rejection/acceptance are covered by backend tests.
- [x] Client treats PROCESSING/UNKNOWN as non-success and polls authoritative order state; paid ticket reads are idempotent and can regenerate a missing ticket.
- [x] Simulated Alipay/WeChat balances are checked before payment and debited exactly once only after server PAID with ticketReady=true.
- [x] Payment confirmation is explicit and idempotent; duplicate confirmation returns the same ticket.
- [x] Resume-payment resolves a lock to a server order before navigation, and payment reloads authoritative order details by orderId.
- [x] GET /v1/tickets is account-scoped and Settings displays paid electronic tickets with venue, showtime, seats, amount, and ticket identity.
- [ ] Human accessibility review and the manual end-to-end acceptance scenarios remain external evidence.
- [ ] Real payment-channel credentials/compliance are explicitly out of scope and remain an external blocker for production deployment.
