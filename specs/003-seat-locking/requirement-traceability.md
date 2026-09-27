# 003 Seat Locking — Requirement Traceability

| Requirement | Implementation | Automated evidence | Status |
|---|---|---|---|
| FR-001 Valid session | `backend/app.py` seat routes resolve Bearer sessions | `test_authenticated_snapshot_and_atomic_idempotent_lock` | PASS |
| FR-002 Authoritative layout | `backend/seats.py::seats_snapshot` | layout test, mapper test | PASS |
| FR-003 Seat metadata/statuses | seat schema, DTOs, mapper and screen | mapper test | PASS |
| FR-004 Non-colour semantics | Compose `contentDescription` | code review; device TalkBack pending | PARTIAL |
| FR-005 Select/deselect, max six | `SeatSelectionViewModel` | ViewModel test | PASS |
| FR-006 Refresh inventory | five-second polling and selected-seat reconciliation | ViewModel test | PASS |
| FR-007 Atomic locking | `BEGIN IMMEDIATE`, conditional updates | atomic conflict test | PASS |
| FR-008 Single active owner | uniqueness/status updates | 100-way concurrency test | PASS |
| FR-009 Lock snapshot | `_lock_json` | lock API test | PASS |
| FR-010 Server 10-minute expiry | `LOCK_SECONDS`, server timestamps, monotonic UI timer | expiry test | PASS |
| FR-011 Idempotency | key and seat-fingerprint lookup | idempotency test | PASS |
| FR-012 Active release | release route/repository/ViewModel | lifecycle test | PASS |
| FR-013 Expiry/logout/stop release | release service, logout integration, stop hook | expiry/logout test; stop hook unit implementation | PARTIAL |
| FR-014 Sold seats retained | `convert_lock_to_sold` and release guard | SOLD protection test | PASS |
| FR-015 Fail closed before payment | lock revalidation and order quote endpoint | unit/build evidence; UI instrumentation pending | PARTIAL |
| FR-016 Order uses lockId/snapshot | `PaymentRoute(lockId)` and quote-by-lock endpoint | navigation code review | PARTIAL |
| FR-017 Stop/cancel invalidates locks | `set_showtime_sales_status` invokes invalidation | implementation present; endpoint-level test pending | PARTIAL |

`PARTIAL` rows require the instrumented/manual checks listed in `acceptance-evidence.md`; no requirement is treated as fully accepted solely from those code paths.
