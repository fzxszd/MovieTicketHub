# Tasks: 座位展示、选座与锁座

**Input**: `/specs/003-seat-locking/` 中的设计文档  
**Prerequisites**: `001-account-authentication` 已提供安全 Bearer 会话；`002-cinema-showtimes` 已提供稳定 showtime/auditorium ID；本目录 `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/`

**Tests**: 宪章要求座位并发与订单状态具备自动化验证，因此测试任务必须先编写并先失败。

## Format: `[ID] [P?] [Story] Description`

- `[P]` 表示文件不同且依赖已满足时可并行
- 用户故事阶段必须带 `[US1]`、`[US2]` 或 `[US3]`
- 每项任务均包含明确文件路径

## Phase 1: Setup

**Purpose**: 建立 Seat 功能的代码与测试边界。

- [ ] T001 创建后端座位服务和测试骨架 `backend/seats.py`、`backend/test_seats.py`、`backend/test_seat_concurrency.py`
- [ ] T002 [P] 创建 Android Seat Domain 模型和 Repository 骨架 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/seat/SeatLayout.kt`、`SeatLock.kt`、`SeatRepository.kt`
- [ ] T003 [P] 创建 Android Seat DTO、Mapper、Remote API 和 Repository 实现骨架 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/SeatDto.kt`、`data/mappers/SeatMapper.kt`、`data/network/SeatRemoteApi.kt`、`data/repository/SeatRepositoryImpl.kt`
- [ ] T004 [P] 新增座位状态、锁定、冲突、倒计时、错误和无障碍中英文资源到 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh/strings.xml`

---

## Phase 2: Foundational

**Purpose**: 建立所有故事共享的安全会话、数据库结构、事务工具和 Android 数据边界。

**⚠️ CRITICAL**: T005 必须确认 001/002 已实现，且本阶段完成前不得开始用户故事实现。

- [ ] T005 验证并接入 001 的 Bearer 会话解析和 002 的稳定 showtime/auditorium 查询接口，在 `backend/auth.py`、`backend/showtimes.py` 与 `backend/seats.py` 中禁止接受客户端 userId 或显示名称作为资源身份
- [ ] T006 在 `backend/app.py` 添加幂等 schema 迁移：创建 auditorium_seats、showtime_seats、seat_locks、seat_lock_items；保证 `(showtime_id, seat_id)` 唯一且 `lock_id` 与 `order_id` 不能同时存在
- [ ] T007 在 `backend/seats.py` 创建固定影厅布局/库存 fixture，确保 `rowIndex >= 0`、`columnIndex >= 0`、可售座必须有 priceZoneId，并覆盖过道、空缺、情侣座和不可售座
- [ ] T008 在 `backend/seats.py` 实现 UTC 服务端时钟注入、600 秒锁期、到期判定和幂等回收工具，明确 `now == expiresAt` 即过期
- [ ] T009 在 `backend/seats.py` 实现 `BEGIN IMMEDIATE` 事务辅助、库存版本递增和标准化错误映射，异常时必须完整回滚
- [ ] T010 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/seat/SeatLayout.kt` 实现布局、位置类型和库存状态模型，其中客户端 `SELECTED` 不得写入服务端库存枚举
- [ ] T011 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/seat/SeatLock.kt` 实现 ACTIVE/RELEASED/EXPIRED/CONVERTED/INVALIDATED、1–6 条锁明细和整数分金额模型
- [ ] T012 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/seat/SeatRepository.kt` 定义座位快照、创建/读取/释放锁和释放当前用户全部锁的接口
- [ ] T013 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/SeatDto.kt` 与 `data/mappers/SeatMapper.kt` 实现 OpenAPI DTO、未知枚举拒绝、UTC 时间和整数金额映射
- [ ] T014 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/SeatRemoteApi.kt` 与 `data/repository/SeatRepositoryImpl.kt` 实现 Bearer、Idempotency-Key、401/404/409/5xx 和结果未知错误映射
- [ ] T015 在 `app/src/main/java/me/ibrahim/moviesapp/compose/di/NetworkModule.kt`、`RepositoryModule.kt` 与 `CoreModule.kt` 注册 Seat API、Repository 和 `SeatSelectionViewModel`

**Checkpoint**: 数据库、认证、事务和 Android 数据边界就绪。

---

## Phase 3: User Story 1 - 查看实时座位图 (Priority: P1)

**Goal**: 已登录用户可查看权威影厅布局及可选、他人锁定、本人锁定、已售和不可用状态，失败时不展示旧可用状态。

**Independent Test**: 使用固定复杂布局和两个账号，对照数据库库存验证页面结构、状态刷新、错误恢复与无障碍语义。

### Tests for User Story 1

- [ ] T016 [P] [US1] 先编写会话校验、场次可售校验、布局合并、五种库存状态、过期锁视为可用和库存版本的失败后端测试到 `backend/test_seats.py`
- [ ] T017 [P] [US1] 先编写 DTO 映射、布局位置、未知状态、整数价格和 UTC 时间的失败单元测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/data/SeatMapperTest.kt`
- [ ] T018 [P] [US1] 先编写加载/内容/刷新/错误、5 秒轮询、乱序版本忽略、前台恢复和失效本地选择移除的失败测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModelTest.kt`
- [ ] T019 [P] [US1] 先编写屏幕方向、复杂布局、状态文字、错误不展示旧可用状态和完整座位语义的失败 UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionScreenTest.kt`

### Implementation for User Story 1

- [ ] T020 [US1] 在 `backend/seats.py` 实现按稳定 showtime/auditorium ID 合并 AuditoriumSeat 与 ShowtimeSeat 的权威座位快照，并在读取时回收已过期锁
- [ ] T021 [US1] 在 `backend/app.py` 实现受 Bearer 保护的 `GET /v1/showtimes/{showtimeId}/seats`、场次不可售 409 和不泄露资源的 401/404 响应
- [ ] T022 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionContract.kt` 定义不可变 Loading/Content/Refreshing/Error 状态、selectedSeatIds overlay、事件和 effect
- [ ] T023 [US1] 重构 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModel.kt`，移除 `generateMockSeats()` 和 Room 库存读取，使用 SeatRepository 加载、5 秒轮询、前台刷新及 inventoryVersion 防乱序
- [ ] T024 [US1] 重构 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionScreen.kt`，按二维位置展示屏幕、排、座、过道、空缺、情侣座、价格区域和五种权威状态
- [ ] T025 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionScreen.kt` 为每个可交互座位添加排号、座号、价格和状态语义，并为所有状态提供非颜色区分

**Checkpoint**: US1 可独立演示实时、可访问的权威座位图。

---

## Phase 4: User Story 2 - 选择并临时锁定座位 (Priority: P1)

**Goal**: 用户选择 1–6 座并全有或全无地锁定 10 分钟；并发冲突、重复提交和结果未知均安全可恢复。

**Independent Test**: 两账号并发争抢 100 次无重复有效锁，多座位任一冲突时零部分锁定，相同请求不延长截止时间。

### Tests for User Story 2

- [ ] T026 [P] [US2] 先编写 `seatIds` 数量 1–6、去重后数量不变、同场次归属、可售位置、统一货币和服务端求和校验测试到 `backend/test_seats.py`
- [ ] T027 [P] [US2] 先编写全有或全无、任一冲突完整回滚、相同幂等键/有效座位组合返回原锁且不延长 expiresAt 的失败测试到 `backend/test_seats.py`
- [ ] T028 [P] [US2] 先编写至少 100 次双账号同时锁同座、每轮最多一个 ACTIVE 锁且无重复占用的并发测试到 `backend/test_seat_concurrency.py`
- [ ] T029 [P] [US2] 先编写选择/取消、最多 6 座、第 7 座提示、刷新移除冲突、Locking 防重复和结果未知复核的失败 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModelTest.kt`
- [ ] T030 [P] [US2] 先编写选择摘要、逐座价格、服务端总额、冲突高亮、处理中禁用和 10 分钟倒计时的失败 UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionScreenTest.kt`

### Implementation for User Story 2

- [ ] T031 [US2] 在 `backend/seats.py` 实现规范化 seatFingerprint 和 `(user_id, idempotency_key)` 幂等查询，重复请求必须返回原 lockId/createdAt/expiresAt
- [ ] T032 [US2] 在 `backend/seats.py` 实现 `BEGIN IMMEDIATE` 全有或全无锁定：清理到期锁、验证全部 1–6 座、插入 SeatLock/SeatLockItem、条件更新库存并校验更新数量
- [ ] T033 [US2] 在 `backend/seats.py` 保存每座 `unitPriceMinor >= 0`、3 位 currency 和服务端求和的 totalPriceMinor，禁止信任客户端总额
- [ ] T034 [US2] 在 `backend/app.py` 实现 `POST /v1/showtimes/{showtimeId}/seat-locks`，要求 16–128 字符 Idempotency-Key，并返回 201 新锁、200 幂等原锁或带冲突 seatId 的 409
- [ ] T035 [US2] 扩展 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionContract.kt`，加入最大座位提示、Locking、Locked、Conflict、ErrorUnknown 和一次性导航 effect
- [ ] T036 [US2] 扩展 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModel.kt`，实现 1–6 座选择、提交前刷新、一次生成并复用幂等键、冲突移除与结果未知复核
- [ ] T037 [US2] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModel.kt` 以 serverTime/expiresAt 和 monotonic elapsed time计算显示倒计时，禁止客户端延长有效期
- [ ] T038 [US2] 更新 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionScreen.kt`，显示选择摘要、逐座/总价、最大数量提示、冲突座位、锁定进度和服务端截止倒计时

**Checkpoint**: US1+US2 构成核心 MVP，可安全锁座并交给订单确认。

---

## Phase 5: User Story 3 - 管理锁定生命周期 (Priority: P2)

**Goal**: 锁可恢复、主动释放并在到期、退出或场次停售时失效；已转订单座位永不被清理开放。

**Independent Test**: 分别触发主动释放、到期、退出、停售和订单转换，其他账号在 5 秒内看到正确库存，SOLD 永不恢复。

### Tests for User Story 3

- [ ] T039 [P] [US3] 先编写锁归属隐藏、读取恢复、重复释放、到期、退出全部释放、场次停售、订单取消释放和 CONVERTED/SOLD 清理保护测试到 `backend/test_seats.py`
- [ ] T040 [P] [US3] 先编写主动释放、后台恢复复核、设备改时不延长、截止阻止导航、会话失效和失效原因的 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModelTest.kt`
- [ ] T041 [P] [US3] 先编写锁详情导航只传 lockId、过期不进入订单和恢复后单次导航的测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/core/SeatLockNavigationTest.kt`

### Implementation for User Story 3

- [ ] T042 [US3] 在 `backend/seats.py` 实现按当前用户读取、主动释放、退出批量释放、到期回收、场次停售失效、订单取消释放和只释放仍指向该 lockId 的 LOCKED 库存行
- [ ] T043 [US3] 在 `backend/app.py` 实现 `GET/DELETE /v1/seat-locks/{lockId}` 与 `DELETE /v1/users/me/seat-locks`，保证幂等且不泄露其他用户资源
- [ ] T044 [US3] 在 `backend/auth.py` 的退出事务中先调用共享 Seat service 释放 ACTIVE 未转订单锁，再撤销会话；失败时记录脱敏可恢复状态
- [ ] T045 [US3] 在 `backend/showtimes.py` 的停售/取消事务及供 004 使用的订单取消路径中调用共享 Seat service，使相关 ACTIVE 锁失效或释放且不触碰 CONVERTED/SOLD 库存
- [ ] T046 [US3] 扩展 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModel.kt` 与 `SeatSelectionScreen.kt`，实现释放确认、锁恢复、后台复核、到期/会话/场次失效状态及安全返回路径
- [ ] T047 [US3] 更新 `app/src/main/java/me/ibrahim/moviesapp/compose/core/Routes.kt` 与 `MoviesNavGraph.kt`，让订单确认只接收稳定 lockId，并在导航前重新验证 ACTIVE 归属和截止时间

**Checkpoint**: 三个故事全部可验收，锁定的完整生命周期闭环。

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T048 删除 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModel.kt` 对模拟座位和 `MoviesRepository.getOccupiedSeats()` 的依赖，并在 `domain/MoviesRepository.kt`、`data/repository/MoviesRepositoryImpl.kt`、`data/database/MoviesDao.kt` 移除不再使用的本地库存接口
- [ ] T049 [P] 在 `backend/test_seat_concurrency.py` 增加代表性 50–500 座数据下的 p95 座位图时间与释放后 5 秒可见断言
- [ ] T050 [P] 审核 `backend/seats.py`、`backend/app.py` 与 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/SeatRemoteApi.kt` 的日志，确保不记录 Bearer、完整幂等键、用户标识或敏感请求正文
- [ ] T051 检查 `backend/app.py` 的 schema 索引和迁移回滚兼容，并验证旧同步数据及 001/002 表不受影响
- [ ] T052 按 `specs/003-seat-locking/quickstart.md` 执行后端测试、Android 单测/构建/UI 测试、100 次争抢及三组手工验收，并追加 Completion evidence
- [ ] T053 对照 `specs/003-seat-locking/spec.md`、`plan.md`、`data-model.md` 与 `contracts/seat-locking-api.yaml` 做最终追踪审查，确认所有宪章门槛及 17 条 FR 均有验证证据

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup** → **Foundational**，T005 明确阻塞于功能 001 和 002 的安全实现。
- **US1** 与 **US2** 都是 P1；US2 使用 US1 的座位快照模型，实际顺序为 US1 后 US2。
- **US3** 依赖成功创建锁的 US2。
- **Polish** 依赖计划交付的故事完成。

### Dependency Graph

```text
001 auth + 002 showtimes
          ↓
Setup → Foundation → US1 layout → US2 atomic lock (MVP) → US3 lifecycle → Polish
```

### Parallel Opportunities

- T002–T004 可并行。
- T010、T011、T013 可并行；后端 T006–T009 按顺序完成。
- US1 的 T016–T019、US2 的 T026–T030、US3 的 T039–T041 各组可并行先写。
- 后端服务任务与 Android ViewModel/UI 任务在契约/Domain 完成后可由不同开发者并行。
- T049 与 T050 可并行。

## Parallel Examples

```text
US1: T016 backend rules | T017 mapper | T018 ViewModel | T019 Compose UI
US2: T026 validation | T027 atomic/idempotent | T028 concurrency | T029 ViewModel | T030 UI
US3: T039 lifecycle backend | T040 lifecycle ViewModel | T041 navigation
```

## Implementation Strategy

### Recommended MVP

1. 完成 T001–T015。
2. 完成 US1 的 T016–T025。
3. 完成 US2 的 T026–T038。
4. 停止并验证权威座位图、6 座上限、全有或全无、100 次并发和幂等恢复。

US1 单独可演示座位图，但真正可用的业务 MVP 必须包含同为 P1 的 US2 原子锁座。

### Incremental Delivery

1. 权威可访问座位图（US1）。
2. 原子、幂等的 10 分钟锁座（US2，核心 MVP）。
3. 释放、恢复、退出和停售生命周期（US3）。
4. 清理旧本地库存路径并完成性能、安全与全量验证。

## Notes

- 不得用 Room 订单、客户端选择或本地时间作为库存事实。
- 网络结果未知时必须复用原 Idempotency-Key；不得盲目创建新锁。
- 支付成功后的 `CONVERTED/SOLD` 由 004 完成，但本功能必须现在就保证清理不会释放 SOLD。
