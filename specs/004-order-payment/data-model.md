# Data Model: 订单创建与支付

## Rules

- 金额统一为整数最小货币单位，同一订单只允许一种 3 位 ISO 4217 货币。
- 用户身份仅从 Bearer 会话取得；所有时间为服务端 UTC ISO-8601。
- 订单、支付、座位和票据状态只能通过后端事务转换。

## Persistent Entities

### Order

| Field | Type | Rules |
|---|---|---|
| `id` | TEXT | 主键，稳定不可复用 |
| `userId` | TEXT | 非空、索引 |
| `seatLockId` | TEXT | 非空、唯一；属于 userId |
| `idempotencyKey` | TEXT | 非空；与 userId 联合唯一 |
| `quoteVersion` | INTEGER | `>= 1` |
| `status` | enum | `PENDING_PAYMENT`、`PAYMENT_PROCESSING`、`PAID`、`PAYMENT_FAILED`、`CANCELLED`、`EXPIRED`、`REVIEW_REQUIRED` |
| `subtotalMinor` | INTEGER | `>= 0` |
| `feeMinor` | INTEGER | `>= 0` |
| `totalMinor` | INTEGER | 必须等于 subtotal+fee |
| `currency` | TEXT | 恰好 3 位 |
| `createdAt` | TEXT | UTC |
| `paymentDeadline` | TEXT | 不晚于锁 expiresAt |
| `paidAt` | TEXT nullable | PAID 时必填 |
| `version` | INTEGER | 从 1 开始，每次状态变化递增 |

### OrderItem

订单创建时的不可变快照：`orderId`、`movieId`、`movieTitle`、`cinemaId/name`、`auditoriumId/name`、`showtimeId`、`startsAt`、`timeZone`、`seatId`、`rowLabel`、`seatLabel`、`priceZoneId`、`unitPriceMinor`、`currency`。每订单 1–6 项，seatId 唯一。

### PaymentAttempt

| Field | Type | Rules |
|---|---|---|
| `id` | TEXT | 主键 |
| `orderId` | TEXT | 外键 |
| `userId` | TEXT | 必须等于订单用户 |
| `idempotencyKey` | TEXT | 与 userId 联合唯一 |
| `provider` | TEXT | 本阶段 `SIMULATED` |
| `method` | enum | `ALIPAY_SIMULATED`、`WECHAT_SIMULATED` |
| `amountMinor/currency` | Money | 必须等于订单最终金额 |
| `status` | enum | `CREATED`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`CANCELLED`、`UNKNOWN`、`REVIEW_REQUIRED` |
| `providerTransactionRef` | TEXT nullable | 非空时唯一 |
| `createdAt/updatedAt` | TEXT | UTC |

### PaymentEvent

`id`、唯一 `providerEventId`、provider、paymentAttemptId、eventType、payloadHash（不保存原始敏感正文）、signatureVerified、receivedAt、processedAt、processingResult。

### Ticket

| Field | Type | Rules |
|---|---|---|
| `id` | TEXT | 主键 |
| `orderId` | TEXT | 唯一；订单必须 PAID |
| `userId` | TEXT | 订单所有者 |
| `status` | enum | `GENERATING`、`READY`、`FAILED_RETRYABLE` |
| `credential` | TEXT nullable | READY 时必填；高熵、不可推测 |
| `issuedAt` | TEXT | UTC |

### StatusEvent

`id`、`entityType`、`entityId`、`fromStatus`、`toStatus`、`reasonCode`、`correlationId`、`occurredAt`。禁止存 Bearer、支付凭据、原始 webhook 或不必要个人信息。

## API/Domain Models

### OrderQuote

- `lockId`、`quoteVersion >= 1`
- movie/cinema/auditorium/showtime 快照
- `items` 1–6 项
- `subtotal`、`fees`、`total`，均为 Money
- `expiresAt` 不晚于锁截止

### OrderSummary / OrderDetail

- 稳定 orderId、状态、金额、创建/截止/支付时间
- Detail 包含完整 OrderItem；只有 PAID 可包含 Ticket
- `allowedActions`: `PAY`、`RETRY_PAYMENT`、`REFRESH`、`VIEW_TICKET` 或空

## State Transitions

```text
PENDING_PAYMENT -> PAYMENT_PROCESSING -> PAID
PENDING_PAYMENT -> PAYMENT_FAILED | CANCELLED | EXPIRED
PAYMENT_PROCESSING -> PAID | PAYMENT_FAILED | CANCELLED | REVIEW_REQUIRED
PAYMENT_FAILED -> PAYMENT_PROCESSING (锁仍有效、场次可售、金额复核通过且使用新的支付尝试)
CANCELLED -> PAYMENT_PROCESSING (锁仍有效、场次可售、金额复核通过且用户明确重试时使用新的支付尝试)
PAID, EXPIRED -> terminal
```

- 客户端永远不能直接触发 PAID。
- PAID 转换必须同时保证座位 SOLD、锁 CONVERTED 和唯一 Ticket 存在。
- 迟到成功但库存不可安全转换时进入 REVIEW_REQUIRED，不能伪装 PAID。

## Validation and Invariants

1. 创建订单前锁必须 ACTIVE、属于当前用户、未到期，且场次可售。
2. `acceptedQuoteVersion` 必须等于最新报价版本；客户端展示金额不同则返回 `AMOUNT_CHANGED`。
3. `paymentDeadline <= seatLock.expiresAt`。
4. seatLockId 最多对应一个有效订单；重复请求返回原订单。
5. 发起支付时订单总额必须与 provider 请求金额完全一致。
6. provider event ID、provider transaction ref、订单票据均唯一。
7. 未 PAID 订单不得返回 READY 票据或有效 credential。
8. 订单/锁/支付归属不匹配时统一拒绝且不泄露其他用户数据。

## Indexes

- `orders(user_id, created_at DESC)`、`orders(status, payment_deadline)`
- `payment_attempts(order_id, created_at DESC)`、唯一 provider transaction ref
- `payment_events(provider_event_id)` 唯一
- `status_events(entity_type, entity_id, occurred_at)`
- `tickets(order_id)` 唯一
