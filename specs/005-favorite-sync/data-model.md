# Data Model: 用户收藏与跨设备同步

## Server Entities

### FavoriteState

| Field | Type | Rules |
|---|---|---|
| `userId` | TEXT | 联合主键，从会话取得 |
| `movieId` | INTEGER | 联合主键，稳定且 > 0 |
| `isFavorite` | BOOLEAN | 当前权威目标状态；false 为 tombstone |
| `serverRevision` | INTEGER | 全局单调递增、唯一 |
| `acceptedAt` | TEXT | 服务端 UTC |
| `lastOperationId` | TEXT | 关联最近接受操作 |

### FavoriteOperationRecord

`userId`、`clientOperationId`（联合唯一）、movieId、targetState、deviceIdHash、localSequence、serverRevision、acceptedAt、result。不得保存令牌或邮箱授权身份。

## Android Room Entities

### FavoriteStateEntity

- 主键 `(accountId, movieId)`；`movieId > 0`
- `confirmedState: Boolean`
- `desiredState: Boolean`
- `serverRevision: Long >= 0`
- `syncStatus: CONFIRMED | PENDING | SYNCING | FAILED`
- `lastErrorCode: String?`
- 有限 MovieSnapshot：title/poster/releaseDate/availability；仅用于离线展示

### FavoriteOperationEntity

- `clientOperationId: String` 主键（UUID）
- `accountId`、`movieId`、`targetState`
- `localSequence: Long`，对 accountId 严格递增
- `status: PENDING | SYNCING | REJECTED`
- `retryCount >= 0`、`lastErrorCode?`

同账号同电影可压缩尚未发送操作为最后目标状态，但已经发送/结果未知的 opId 必须保留用于幂等恢复。

### FavoriteSyncCursorEntity

主键 `accountId`；`lastServerRevision >= 0`、`lastSyncAt?`。游标只能在远端变化与操作结果成功写入同一 Room 事务后推进。

## API Models

### FavoriteChange

`movieId`、`isFavorite`、`serverRevision`、`acceptedAt`、可选 MovieSnapshot/availability。

### FavoriteSyncOperation

`clientOperationId`、`movieId`、`targetState`、`localSequence`。userId 不得出现在载荷。

### FavoriteSyncResponse

`serverRevision`、按请求操作对应的 results、`changes`（sinceRevision 之后按 revision 升序）、`hasMore`、`nextRevision`。

## State Rules

```text
CONFIRMED --local intent--> PENDING --> SYNCING --> CONFIRMED
                                      └---------> FAILED/REJECTED -> authority rollback or retry
session expired: PENDING/SYNCING -> PAUSED (coordinator state; rows remain account-isolated)
explicit logout: stop worker -> clear visible state -> purge that account cache/queue
```

## Invariants

1. 服务端 `(userId,movieId)` 最多一行；false tombstone 不删除，以便其他设备同步取消。
2. 新 opId 才分配 revision；重复 opId 返回原结果且不增加 revision。
3. 同步请求的 operations 必须按 localSequence 严格升序、数量 1–100；重复序号拒绝。
4. 冲突最终状态由最大 serverRevision 决定，不比较客户端时间。
5. Room 所有查询必须包含 accountId；发布 UI 状态前先核对当前会话 accountId。
6. 远端拒绝使 desiredState 恢复为权威 confirmedState，并保留可理解错误。
7. 推荐刷新只消费服务端确认的 revision，添加和移除均触发。

## Indexes

- server `favorite_states(user_id, is_favorite, server_revision)`
- server `favorite_operations(user_id, client_operation_id)` 唯一
- Room `favorite_operations(account_id, status, local_sequence)`
- Room `favorite_states(account_id, desired_state)`
