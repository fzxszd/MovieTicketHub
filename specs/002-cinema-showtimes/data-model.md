# Data Model: 影院与场次查询

## Modeling Rules

- 数据库时间统一保存为带 `Z` 的 UTC ISO-8601 字符串；查询日期按影院的 IANA 时区解释。
- 金额保存为整数最小货币单位（人民币“分”），不得保存浮点金额或格式化字符串。
- 所有跨页面和 API 关联都使用稳定 ID；名称、时间和影厅文字仅用于显示。
- 场次状态和版本由后端维护，客户端只保存当次响应快照。

## Backend Persistent Entities

### Cinema

| Field | Type | Rules |
|---|---|---|
| `id` | TEXT | 主键，稳定且不可复用，例如 `cin_bj_001` |
| `name` | TEXT | 非空 |
| `address` | TEXT | 非空 |
| `cityCode` | TEXT | 非空、索引，例如 `CN-BJ` |
| `district` | TEXT | 非空、用于筛选 |
| `latitude` | REAL nullable | 与 longitude 成对出现，范围 -90..90 |
| `longitude` | REAL nullable | 与 latitude 成对出现，范围 -180..180 |
| `timeZone` | TEXT | 非空 IANA zone，例如 `Asia/Shanghai` |
| `status` | enum | `OPEN`、`TEMPORARILY_CLOSED`、`CLOSED` |
| `recommendationRank` | INTEGER | 非负，较小者优先；不使用用户画像 |

### Auditorium

| Field | Type | Rules |
|---|---|---|
| `id` | TEXT | 主键，稳定且不可复用 |
| `cinemaId` | TEXT | 外键 `Cinema.id`，级联限制删除 |
| `name` | TEXT | 非空，例如“6号激光厅” |
| `seatLayoutRef` | TEXT nullable | 后续选座功能使用的布局引用 |
| `status` | enum | `ACTIVE`、`INACTIVE` |

### Showtime

| Field | Type | Rules |
|---|---|---|
| `id` | TEXT | 主键，稳定且不可复用 |
| `movieId` | INTEGER | 非空、索引，与电影目录 ID 对应 |
| `cinemaId` | TEXT | 外键 `Cinema.id`，冗余用于高效查询且必须与 auditorium 一致 |
| `auditoriumId` | TEXT | 外键 `Auditorium.id` |
| `startsAt` | TEXT | 非空 UTC ISO-8601 时间点 |
| `endsAt` | TEXT | 非空，必须晚于 startsAt |
| `language` | TEXT | 非空，例如 `zh-CN` |
| `format` | TEXT | 非空，例如 `2D`、`IMAX` |
| `basePriceMinor` | INTEGER | 非负，单位由 currency 决定 |
| `currency` | TEXT | 3 位 ISO 4217，当前为 `CNY` |
| `salesStatus` | enum | `ON_SALE`、`STOPPED`、`SOLD_OUT`、`CANCELLED` |
| `version` | INTEGER | 从 1 开始；价格、时间、影厅或状态变化时递增 |
| `updatedAt` | TEXT | 后端更新时间，UTC ISO-8601 |

### Relationships

```text
Cinema 1 ─── * Auditorium
Cinema 1 ─── * Showtime
Auditorium 1 ─── * Showtime
Movie(external catalog) 1 ─── * Showtime
```

## Domain Models (Android)

### Money

- `amountMinor: Long`
- `currency: String`
- 只提供比较和格式化所需行为；不通过 `Double` 往返。

### Cinema

- `id: String`
- `name: String`
- `address: String`
- `cityCode: String`
- `district: String`
- `timeZone: String`
- `distanceMeters: Int?`
- `minimumPrice: Money`
- `showtimes: List<Showtime>`（至少一个符合当前条件且可售）

### Showtime

- `id: String`
- `movieId: Int`
- `cinemaId: String`
- `auditoriumId: String`
- `auditoriumName: String`
- `startsAt: String`（解析后的 UTC instant 可由 formatter 使用）
- `endsAt: String`
- `timeZone: String`
- `language: String`
- `format: String`
- `basePrice: Money`
- `status: ShowtimeStatus`
- `unavailableReason: UnavailableReason?`
- `version: Long`

### ShowtimeQuery

| Field | Type | Validation/default |
|---|---|---|
| `movieId` | Int | 正数 |
| `localDate` | ISO date | 必填，以影院当地日期解释 |
| `cityCode` | String | 必填 |
| `districts` | Set<String> | 默认空（全部） |
| `startLocalTime` | HH:mm nullable | 与 endLocalTime 可独立为空 |
| `endLocalTime` | HH:mm nullable | 若两者都有，必须晚于 start |
| `minPriceMinor` | Long nullable | 非负 |
| `maxPriceMinor` | Long nullable | 非负且不小于 min |
| `sort` | enum | `RECOMMENDED`（默认）、`DISTANCE`、`PRICE` |
| `latitude/longitude` | Double nullable | 必须成对；只用于当次请求 |

### CinemaShowtimeResult

- `serverTime: String`
- `query: ShowtimeQuery`
- `appliedSort: SortType`
- `sortNotice: SortNotice?`
- `availableDistricts: List<String>`
- `cinemas: List<Cinema>`

### ShowtimeValidation

- `result: UNCHANGED | CHANGED | UNAVAILABLE`
- `latest: Showtime`
- `changedFields: Set<PRICE | START_TIME | END_TIME | AUDITORIUM | STATUS>`
- `messageCode: String`

## Derived Rules

1. “可售”必须同时满足：影院 `OPEN`、影厅 `ACTIVE`、场次 `ON_SALE`、`startsAt > serverTime`。
2. `SOLD_OUT`、`STOPPED`、`CANCELLED`、已开始和已结束都可以在已加载页面中展示，但不能执行进入选座；查询的默认可售列表只把可售场次计入 FR-009。
3. 影院 `minimumPrice` 是当前筛选结果内所有可售场次的最小 `basePrice`，不能使用影院的历史最低价。
4. 日期匹配通过把 `startsAt` 转换到该影院 `timeZone` 后取 local date。
5. 时间范围比较也使用影院当地时间；不跨日的输入要求 end > start。跨午夜场次通过日期+UTC instant 正确表达，不用“结束时间小于开始时间”的字符串规则推断。
6. `DISTANCE` 只有在有效坐标存在且影院有坐标时生效；否则整个查询降级为 `RECOMMENDED`，不混合伪距离。
7. `PRICE` 按 `minimumPrice.amountMinor` 升序，相同价格用 recommendationRank、影院 ID 稳定排序。
8. `RECOMMENDED` 仅使用运营 rank、是否有近期可售场次和稳定 tie-breaker，不读取个人收藏。

## State Transitions

### Showtime sales state

```text
ON_SALE ──售罄──> SOLD_OUT
ON_SALE ──运营停售──> STOPPED
ON_SALE ──取消──> CANCELLED
SOLD_OUT ──库存释放──> ON_SALE
STOPPED ──恢复销售──> ON_SALE
```

- `CANCELLED` 为终态。
- 每次状态、票价、开始/结束时间或影厅变化都必须原子地递增 `version`。
- “已开始/已结束”是由 `serverTime` 和时间点派生的不可选原因，不额外写入可漂移的数据库状态。

### Client screen state

```text
Idle -> Loading -> Content | Empty | Error
Content -> Filtering -> Content | Empty | Error
Content -> Revalidating -> AuthRequired | ReadyForSeatSelection | Stale | Error
Stale -> Content (使用 latest 快照刷新)
Error -> Loading (Retry，保留 query)
```

## Database Constraints and Indexes

- `auditoriums(cinema_id, name)` 唯一。
- `showtimes(movie_id, starts_at)` 索引用于日期窗口查询。
- `showtimes(cinema_id, starts_at)` 索引用于影院分组。
- `showtimes(auditorium_id, starts_at)` 唯一或冲突校验，防止同一影厅同一时刻重复排片。
- 外键校验开启；种子数据使用明确 ID 和 upsert，不删除已有用户同步表。
