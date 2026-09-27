# Implementation Plan: 座位展示、选座与锁座

**Branch**: `003-seat-locking` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-seat-locking/spec.md`

## Summary

把当前客户端生成的 8×8 模拟座位和本地订单反查，替换为 Flask/SQLite 后端权威的影厅布局、场次库存和临时锁定。Android 通过独立 `SeatRepository` 加载并轮询库存，以不可变状态管理 1–6 个本地选择；提交时携带幂等键，后端在单个 `BEGIN IMMEDIATE` 事务中清理到期锁、验证全部座位并全有或全无地创建 10 分钟锁。客户端只使用服务端截止时间倒计时，锁状态未经重新验证时不得进入订单确认。

## Technical Context

**Language/Version**: Kotlin 2.1.0（Android，JVM target 1.8）；Python 3.12（后端）  
**Primary Dependencies**: Jetpack Compose、Navigation Compose 2.8.6、Ktor 3.0.3、Koin 4.0.2、kotlinx.serialization 1.8.0、Flask 3.0.3  
**Storage**: 后端 SQLite（座位布局、场次库存、锁定及锁定明细）；客户端不把库存快照持久化为事实  
**Testing**: Python `unittest` + Flask test client + 并发线程测试；JUnit 4；Compose UI Test  
**Target Platform**: Android API 24–35；Flask HTTP API  
**Project Type**: Android 移动应用 + Python API 服务  
**Performance Goals**: 95% 座位图请求 3 秒内得到最新状态或错误；释放/到期状态 5 秒内对其他用户可见  
**Constraints**: 需要 `001-account-authentication` 的 Bearer 会话和 `002-cinema-showtimes` 的稳定 showtime/auditorium ID；最多 6 座；锁期固定 600 秒；库存与时间仅以后端为准；SQLite 并发写入必须串行且原子  
**Scale/Scope**: 单场约 50–500 座；高峰同一场次几十个并发锁请求；1 个选座页面、5 个座位/锁定 API 操作

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

| Principle | Result | Evidence |
|---|---|---|
| I. 分层架构与单向数据流 | PASS | 新增 Seat Domain/API/Repository；Composable 只消费 state 和发送 event，不访问 Room 或 HTTP。 |
| II. 购票与座位状态必须正确 | PASS | 服务端是唯一库存源；锁定全有或全无、幂等、按权威时间过期，并以数据库约束阻止重复有效占用。 |
| III. 账号、隐私与安全优先 | PASS（有前置依赖） | 所有端点从 Bearer 会话派生 userId；不接受客户端 userId。必须先完成 001 的安全会话。 |
| IV. 推荐规则 | PASS（不适用） | 本功能不使用推荐数据。 |
| V. 可恢复体验与明确状态 | PASS | 明确 loading/content/error/refreshing/locking/locked/expired/conflict/session-expired 状态并提供安全恢复路径。 |
| 技术与业务约束 | PASS | 后端管理库存和锁；金额用整数分；API、迁移与回滚均有设计。 |
| 质量门槛 | PASS | 自动化并发、原子性、幂等、过期、退出释放、状态恢复和无障碍测试列入任务。 |

### Post-design gate

| Principle | Result | Design confirmation |
|---|---|---|
| 分层与数据流 | PASS | `SeatRepository` 是 Presentation 唯一入口；`data-model.md` 分离服务器实体、Domain 与 UI 状态。 |
| 权威库存与并发 | PASS | 契约只返回服务端库存版本和截止时间；事务算法及唯一约束覆盖锁定竞争。 |
| 认证与隐私 | PASS（条件式） | OpenAPI 对所有操作声明 BearerAuth；用户归属从令牌取得。001 未实现前不得发布本功能。 |
| 可恢复和可访问性 | PASS | 轮询/恢复时不展示过期可选状态；座位语义包含排、座、区域、价格和状态，不只依赖颜色。 |
| 测试与构建 | PASS | quickstart 覆盖双账号 100 次争抢、设备时间篡改、后台恢复和重复提交。 |

无设计级宪章例外；存在必须按顺序完成的跨功能前置条件：001 → 002 → 003。

## Architecture and State Flow

1. `SeatSelectionViewModel` 接收稳定 `showtimeId`、`auditoriumId`，经 `SeatRepository` 请求权威座位图。
2. 后端先验证 Bearer 会话和场次仍可售，再把布局与当前有效库存合并成一个带 `inventoryVersion`、`serverTime` 的快照。
3. UI 只允许 `AVAILABLE` 座位进入本地 `SELECTED` overlay，最多 6 个；服务端状态不会被本地选择覆盖。
4. 页面可见期间每 5 秒轮询，并在回到前台、手动重试和提交前刷新；库存变化会移除失效选择并提示。
5. 确认时客户端生成一次性 `Idempotency-Key`。后端开启 `BEGIN IMMEDIATE`，按服务端时间判定旧锁失效，验证全部 seatId 后一次创建锁和明细；任何冲突整组回滚。
6. 成功响应包含 lockId、serverTime、expiresAt、座位与金额快照；客户端以 `expiresAt - serverTime` 作为基准，用 monotonic elapsed time显示倒计时，但不得据此延长服务端锁。
7. 进入订单确认前以及应用恢复后，客户端读取锁详情重新验证；仅 `ACTIVE` 且属于当前用户的锁可以继续。
8. 返回、主动取消或退出登录调用释放接口；支付成功后由 004 在同一后端事务把座位转为 `SOLD`，本功能的清理逻辑不得释放已售座位。

## Transaction and Concurrency Design

- `showtime_seats(showtime_id, seat_id)` 是库存唯一行，状态只允许 `AVAILABLE`、`LOCKED`、`SOLD`、`UNAVAILABLE`。
- 锁请求使用 `BEGIN IMMEDIATE` 获取 SQLite 写锁，首先把 `expires_at <= now` 且未转订单的锁标为 `EXPIRED` 并释放对应 `LOCKED` 行，然后验证完整座位集合。
- 所有座位可用时，在同一事务内插入 `seat_locks`、`seat_lock_items` 并条件更新库存；更新数量不是请求数量时回滚并返回冲突 ID。
- `(showtime_id, seat_id)` 主键保证单库存记录；有效锁的独占通过库存行的 `lock_id` 和条件更新保证，而不是依赖客户端顺序。
- 相同当前用户、场次、规范化座位集合的有效锁直接返回原结果，不刷新 `expiresAt`；相同 `Idempotency-Key` 也返回原响应语义。
- 到期是否可用通过查询时的服务端时间判定，因此即使清理任务延迟也不会继续占座；读写路径均执行轻量到期回收，满足 5 秒可见目标。

## Failure Recovery and Security

- 网络超时后客户端使用相同幂等键查询/重试，不生成新键；服务端可能成功时 UI 显示“正在确认”，不能退回普通可提交状态。
- 刷新或锁详情无法验证时，禁用进入订单确认；保留 lockId 以便网络恢复后核对。
- 401 清除本地会话并转登录；登录不会自动重建或延长旧锁，重新认证后必须按归属读取。
- 所有端点从 Bearer 令牌取得用户 ID，404/403 响应不得泄露他人锁详情；日志只记录脱敏 request/lock/showtime 标识。
- 价格快照为每座整数分和货币；总额由服务端求和。客户端展示值不能成为订单价格来源。
- 不使用客户端墙上时钟决定锁是否有效；设备改时只影响系统显示风险，不改变服务器状态。

## Migration and Rollback

- 以附加、幂等迁移新增 `auditorium_seats`、`showtime_seats`、`seat_locks`、`seat_lock_items` 和索引，不修改 001/002 表的既有语义。
- 002 创建影厅/场次时为新场次生成库存行；开发 fixture 使用固定布局，覆盖过道、空位、情侣座、不可售位和多个价格区域。
- Android 移除 `generateMockSeats()`、Room `getOccupiedSeats()` 和显示名称反查；旧 Room 订单表暂由 004 迁移，不把它作为库存回退。
- 服务回滚时保留附加表；已创建锁继续按 `expiresAt` 自然失效。禁止回滚到客户端本地占座作为生产替代。

## Verification Strategy

- 后端：布局合并、权限、6 座上限、全有或全无、100 次双账号争抢、幂等、超时/释放/退出/停售、已售保护和价格快照。
- Android：DTO 映射、本地选择规则、轮询更新、冲突选择移除、服务端倒计时基准、后台恢复、超时未知状态和单次导航。
- Compose：布局结构、缩放、所有状态文字/语义、错误不展示旧可用状态、最大座位提示、锁定处理中禁用。
- 最终运行后端测试、Android 单测、Debug 构建和可用时的 instrumentation 测试，并按 quickstart 记录证据。

## Project Structure

### Documentation (this feature)

```text
specs/003-seat-locking/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── seat-locking-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── app.py
├── seats.py
├── test_seats.py
└── test_seat_concurrency.py

app/src/main/java/me/ibrahim/moviesapp/compose/
├── core/{Routes.kt,MoviesNavGraph.kt}
├── data/
│   ├── dto/SeatDto.kt
│   ├── mappers/SeatMapper.kt
│   ├── network/SeatRemoteApi.kt
│   └── repository/SeatRepositoryImpl.kt
├── domain/seat/
│   ├── SeatLayout.kt
│   ├── SeatLock.kt
│   └── SeatRepository.kt
├── di/{CoreModule.kt,NetworkModule.kt,RepositoryModule.kt}
└── presentation/seat_selection/
    ├── SeatSelectionContract.kt
    ├── SeatSelectionScreen.kt
    └── SeatSelectionViewModel.kt

app/src/test/java/me/ibrahim/moviesapp/compose/
├── data/SeatMapperTest.kt
└── presentation/seat_selection/SeatSelectionViewModelTest.kt

app/src/androidTest/java/me/ibrahim/moviesapp/compose/
└── presentation/seat_selection/SeatSelectionScreenTest.kt
```

**Structure Decision**: 延续单 Android app 与单 Flask 服务，不新增 Gradle 模块。锁定规则集中于 `backend/seats.py`；`app.py` 只组装路由。Android 新建独立 Seat 数据边界，避免继续扩张 `MoviesRepository`。

## Complexity Tracking

无需要宪章豁免的复杂度。`BEGIN IMMEDIATE` 是在当前 SQLite 技术栈下实现原子库存的必要机制；如果未来改为多实例高吞吐数据库，应以数据库行锁/约束替代而不改变 API 语义。
