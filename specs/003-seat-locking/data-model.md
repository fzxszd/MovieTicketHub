# Data Model: 座位展示、选座与锁座

## Modeling Rules

- 静态影厅布局与场次库存分离；客户端选择不是库存状态。
- 时间全部由服务端生成并保存为 UTC ISO-8601；锁期固定 600 秒。
- 金额使用整数最小货币单位；跨服务关联只使用稳定 ID。
- 所有锁定数据必须绑定由 Bearer 会话解析出的 `userId`。

## Backend Persistent Entities

### AuditoriumSeat

| Field | Type | Rules |
|---|---|---|
| `auditoriumId` | TEXT | 联合主键、外键，来自 002 |
| `seatId` | TEXT | 联合主键，在影厅内稳定 |
| `rowIndex` | INTEGER | `>= 0`，用于布局 |
| `columnIndex` | INTEGER | `>= 0`，用于布局 |
| `rowLabel` | TEXT | 非空、用户可读 |
| `seatLabel` | TEXT | 普通/情侣座非空；空缺/过道可为空 |
| `positionType` | enum | `SEAT`、`AISLE`、`EMPTY`、`COUPLE_LEFT`、`COUPLE_RIGHT` |
| `priceZoneId` | TEXT nullable | 可售座必须存在 |
| `isUsable` | BOOLEAN | 维护停用座为 false |

主键：`(auditorium_id, seat_id)`；布局中的 `(row_index, column_index)` 唯一。

### ShowtimeSeat

| Field | Type | Rules |
|---|---|---|
| `showtimeId` | TEXT | 联合主键、外键，来自 002 |
| `seatId` | TEXT | 联合主键，必须属于场次影厅 |
| `status` | enum | `AVAILABLE`、`LOCKED`、`SOLD`、`UNAVAILABLE` |
| `unitPriceMinor` | INTEGER | `>= 0` |
| `currency` | TEXT | 3 位 ISO 4217，当前 `CNY` |
| `lockId` | TEXT nullable | LOCKED 时必填，其他状态为空 |
| `orderId` | TEXT nullable | SOLD 时必填，其他状态为空 |
| `version` | INTEGER | 从 1 开始，每次库存变化递增 |
| `updatedAt` | TEXT | UTC 时间点 |

约束：同一 `(showtimeId, seatId)` 只有一行；`lockId` 和 `orderId` 不能同时存在。

### SeatLock

| Field | Type | Rules |
|---|---|---|
| `id` | TEXT | 主键，服务端生成 |
| `userId` | TEXT | 非空，来自认证会话 |
| `showtimeId` | TEXT | 非空、外键 |
| `idempotencyKey` | TEXT | 非空；与 userId 联合唯一 |
| `seatFingerprint` | TEXT | 排序后 seatId 集合的哈希 |
| `status` | enum | `ACTIVE`、`RELEASED`、`EXPIRED`、`CONVERTED`、`INVALIDATED` |
| `createdAt` | TEXT | UTC 服务端时间 |
| `expiresAt` | TEXT | `createdAt + 600 秒`，不可续期 |
| `releasedAt` | TEXT nullable | 释放/失效时填写 |
| `releaseReason` | enum nullable | `USER`、`LOGOUT`、`EXPIRED`、`SHOWTIME_STOPPED`、`ORDER_CANCELLED` |
| `totalPriceMinor` | INTEGER | 所有明细价格之和，`>= 0` |
| `currency` | TEXT | 所有明细相同的 3 位货币代码 |

唯一：`(user_id, idempotency_key)`。同一用户、场次、seatFingerprint 若已有未到期 ACTIVE 锁，返回该锁而不创建新锁。

### SeatLockItem

| Field | Type | Rules |
|---|---|---|
| `lockId` | TEXT | 联合主键、外键 SeatLock |
| `showtimeId` | TEXT | 联合主键辅助一致性检查 |
| `seatId` | TEXT | 联合主键、外键 ShowtimeSeat |
| `rowLabel` | TEXT | 锁定时显示快照 |
| `seatLabel` | TEXT | 锁定时显示快照 |
| `priceZoneId` | TEXT | 锁定时区域快照 |
| `unitPriceMinor` | INTEGER | 锁定时单价快照，`>= 0` |
| `currency` | TEXT | 3 位货币代码 |

每个锁 1–6 条明细；同一锁内 seatId 不得重复。

## Relationships

```text
Auditorium 1 ─── * AuditoriumSeat
Showtime 1 ─── * ShowtimeSeat
AuditoriumSeat 1 ─── * ShowtimeSeat (across showtimes)
SeatLock 1 ─── 1..6 SeatLockItem
SeatLockItem * ─── 1 ShowtimeSeat
Order 0..1 ─── 1 SeatLock (由 004 建立)
```

## Android Domain Models

### SeatLayoutSnapshot

- `showtimeId: String`
- `auditoriumId: String`
- `auditoriumName: String`
- `screenLabel: String`
- `serverTime: Instant`
- `inventoryVersion: Long`
- `rows: List<SeatRow>`

### SeatPosition

- `seatId: String?`
- `rowIndex: Int`，必须 `>= 0`
- `columnIndex: Int`，必须 `>= 0`
- `rowLabel: String`
- `seatLabel: String?`
- `positionType: SEAT | AISLE | EMPTY | COUPLE_LEFT | COUPLE_RIGHT`
- `priceZoneId: String?`
- `price: Money?`
- `inventoryStatus: AVAILABLE | LOCKED_BY_ME | LOCKED_BY_OTHER | SOLD | UNAVAILABLE`
- `unavailableReason: String?`

`SELECTED` 不写入本模型，由 UI state 的 `selectedSeatIds` 覆盖显示。

### SeatLock

- `id: String`
- `showtimeId: String`
- `status: ACTIVE | RELEASED | EXPIRED | CONVERTED | INVALIDATED`
- `serverTime: Instant`
- `createdAt: Instant`
- `expiresAt: Instant`
- `items: List<LockedSeat>`，数量严格 1–6
- `totalPrice: Money`
- `releaseReason: ReleaseReason?`

### LockConflict

- `conflictingSeatIds: Set<String>`，不得为空
- `latestInventoryVersion: Long`
- `messageCode: SEAT_CONFLICT | SHOWTIME_UNAVAILABLE | SESSION_INVALID`

## State Transitions

### Seat inventory

```text
AVAILABLE ──create lock──> LOCKED
LOCKED ──release/expire/invalidate──> AVAILABLE
LOCKED ──paid order (004)──> SOLD
AVAILABLE ──maintenance──> UNAVAILABLE
UNAVAILABLE ──restore──> AVAILABLE
SOLD ──> terminal for this feature
```

清理锁时仅更新仍指向该 `lockId` 且状态为 LOCKED 的行；不得更新 SOLD 行。

### Seat lock

```text
ACTIVE ──user release/logout──> RELEASED
ACTIVE ──server deadline──> EXPIRED
ACTIVE ──showtime stopped/cancelled──> INVALIDATED
ACTIVE ──paid order (004)──> CONVERTED
```

所有非 ACTIVE 状态均为本功能终态，重复释放返回当前状态且没有副作用。

### Client selection

```text
Loading -> Content | Error
Content -> Refreshing -> Content | Error(no stale availability)
Content(selected 1..6) -> Locking -> Locked | Conflict | ErrorUnknown
Locked -> Verifying -> ReadyForOrder | Expired | Released | Invalidated | ErrorUnknown
```

`ErrorUnknown` 必须禁用订单导航，仅允许使用同一幂等键重新验证。

## Validation Rules

1. 创建锁请求 `seatIds` 数量必须为 1–6，去重后数量必须不变。
2. 所有 seatId 必须属于同一请求 showtime 的影厅、是可售位置且当前有效状态为 AVAILABLE。
3. 场次必须仍可售且未开始；用户会话必须有效。
4. 所有座位货币必须相同；总额由服务端对明细求和。
5. 客户端传入的 `inventoryVersion` 只用于冲突提示，不得绕过当前库存检查。
6. 有效锁判断使用服务端 `now < expiresAt`；恰好等于截止时间即过期。
7. 读取他人锁详情返回不泄露资源存在性的错误；不得返回 userId 或他人座位明细。

## Indexes

- `showtime_seats(showtime_id, status)`：座位图库存合并。
- `seat_locks(user_id, status, expires_at)`：退出释放和恢复。
- `seat_locks(showtime_id, status, expires_at)`：场次停售和过期回收。
- `seat_lock_items(showtime_id, seat_id)`：冲突与订单转换校验。
