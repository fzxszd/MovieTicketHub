# Requirement → Implementation → Test/Evidence

| Requirements | Implementation | Evidence |
|---|---|---|
| FR-001–FR-006 | Bearer-protected quote/order endpoints; Android `OrderRepository` and idempotent create flow | `backend/test_orders.py`, `app/src/test/.../OrderRepositoryTest.kt` |
| FR-007–FR-012 | Payment attempt API, server status rendering, stable payment key and polling | `backend/test_payments.py`, `test_payment_security.py`, `PaymentViewModel.kt` |
| FR-013–FR-015 | Expiry cleanup releases locks; ticket returned only for PAID orders | `payments.py`, order/ticket endpoints |
| FR-016–FR-017 | Account-scoped list/detail APIs and Android order recovery screens | `OrdersViewModel.kt`, `OrderDetailViewModel.kt` |
| FR-018–FR-019 | No HTTP logging; status events and payment event de-duplication | `orders.py`, `payments.py`, `app.py` |
| FR-020 | No refund, transfer, coupon, invoice or venue validation implementation | scope review |

## Current payment-chain evidence

| Requirement | Implementation | Test/Evidence |
|---|---|---|
| Simulated accounts and balance safety | `PaymentAccountStore`, `PaymentViewModel` | Android JVM tests and build; insufficient balance path does not confirm payment |
| Explicit server confirmation | `POST /v1/orders/{orderId}/payment-confirmations` | `backend/test_payments.py` confirmation/idempotency test |
| Ownership and idempotency | owner check plus attempt/order state guards | non-owner and duplicate confirmation tests |
| Paid ticket list and recovery navigation | `GET /v1/tickets`, `OpenPaymentOrder`, Settings tickets UI | backend ticket-list assertion and Android build |

Unmet evidence is explicitly listed in `acceptance-criteria.md`; it is not silently treated as complete.
