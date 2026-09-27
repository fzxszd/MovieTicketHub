# Tasks: 影院与场次查询

**Input**: Design documents from `/specs/002-cinema-showtimes/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/showtimes-api.yaml`

**Tests**: 宪章要求核心业务规则具备自动化测试，因此每个用户故事均包含先写且先失败的测试任务。

**Organization**: 任务按用户故事组织；完成 Setup 与 Foundational 后，每个故事都可独立实现和验收。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同阶段其他标记任务并行（文件不同且无未满足依赖）
- **[Story]**: `US1`、`US2`、`US3` 对应 `spec.md` 中的用户故事
- 每项任务都包含明确文件路径

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立场次功能的后端、Android 和测试文件边界。

- [X] T001 创建后端场次服务与测试骨架文件 `backend/showtimes.py` 和 `backend/test_showtimes.py`
- [X] T002 [P] 创建 Android 场次 Domain 包与模型骨架 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/cinema/Cinema.kt`、`Money.kt`、`Showtime.kt`、`ShowtimeQuery.kt`
- [X] T003 [P] 创建 Android DTO、Mapper、远程 API 和 Repository 骨架 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/CinemaShowtimeDto.kt`、`data/mappers/CinemaShowtimeMapper.kt`、`data/network/CinemaRemoteApi.kt`、`data/repository/CinemaRepositoryImpl.kt`
- [X] T004 [P] 添加场次功能所需的通用中英文资源键（标题、日期、筛选、排序、状态、错误、重试和可访问性描述）到 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立所有用户故事共享的数据库、契约映射、依赖注入和状态基础。

**⚠️ CRITICAL**: 本阶段完成前不得开始用户故事实现。

- [X] T005 在 `backend/app.py` 的幂等初始化中新增 cinemas、auditoriums、showtimes 表、外键、唯一约束和查询索引，并保证现有同步表不受影响
- [X] T006 在 `backend/showtimes.py` 实现固定 ID 的可重复开发种子数据与 UTC/IANA 时区解析工具，禁止运行时随机排片
- [X] T007 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/cinema/Money.kt` 与 `Showtime.kt` 实现整数分金额、售票状态、不可选原因和版本模型
- [X] T008 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/cinema/Cinema.kt` 与 `ShowtimeQuery.kt` 实现影院聚合、查询条件、排序类型及输入校验
- [X] T009 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/cinema/CinemaRepository.kt` 定义可售日期、影院场次查询和场次复核接口及领域错误类型
- [X] T010 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/CinemaShowtimeDto.kt` 按 `specs/002-cinema-showtimes/contracts/showtimes-api.yaml` 建立序列化 DTO
- [X] T011 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/mappers/CinemaShowtimeMapper.kt` 实现 DTO 到 Domain 的严格映射、未知枚举处理与无效数据拒绝
- [X] T012 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/CinemaRemoteApi.kt` 实现三个契约端点、查询参数编码、超时和 HTTP 错误映射
- [X] T013 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/repository/CinemaRepositoryImpl.kt` 实现 `CinemaRepository` 并确保不把网络失败伪装为空结果
- [X] T014 在 `app/src/main/java/me/ibrahim/moviesapp/compose/di/NetworkModule.kt`、`RepositoryModule.kt` 与 `CoreModule.kt` 注册场次 API、Repository 和两个场次 ViewModel

**Checkpoint**: 后端 schema 与 Android 数据边界就绪，用户故事可开始实现。

---

## Phase 3: User Story 1 - 查看电影可用场次 (Priority: P1) 🎯 MVP

**Goal**: 用户可从电影详情按影院当地日期查看权威的影院与可售场次，并获得明确的加载、内容、空和错误状态。

**Independent Test**: 仅完成本故事时，匿名用户可选择电影和日期，看到至少一家影院及完整场次信息；无数据和断网分别显示可操作状态。

### Tests for User Story 1

- [X] T015 [P] [US1] 先编写可售日期、影院当地日期、跨午夜、未来/已开始判定和空结果的失败后端测试到 `backend/test_showtimes.py`
- [ ] T016 [P] [US1] 先编写 DTO 映射、整数金额和影院时区字段的失败单元测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/data/CinemaShowtimeMapperTest.kt`
- [ ] T017 [P] [US1] 先编写加载/内容/空/错误、切换日期和重试保留日期的失败 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionViewModelTest.kt`
- [ ] T018 [P] [US1] 先编写日期选择、影院列表、完整场次字段及空/错误操作入口的失败 UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionScreenTest.kt`

### Implementation for User Story 1

- [X] T019 [US1] 在 `backend/showtimes.py` 实现按电影、城市和影院当地日期计算可售日期及场次可选性的权威查询规则
- [X] T020 [US1] 在 `backend/app.py` 实现 `GET /v1/movies/{movieId}/showtime-dates` 和基础 `GET /v1/movies/{movieId}/cinema-showtimes` 路由、参数校验及标准错误响应
- [X] T021 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionContract.kt` 定义不可变 UI state、用户事件和一次性导航/消息 effect
- [X] T022 [US1] 重构 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionViewModel.kt`，经 `CinemaRepository` 加载默认可售日期和影院列表并实现重试保留上下文
- [X] T023 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/time_selection/TimeSelectionContract.kt` 定义所选影院的加载、内容、空和错误状态及事件
- [X] T024 [US1] 新增 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/time_selection/TimeSelectionViewModel.kt`，只通过 `CinemaRepository` 管理影院场次状态
- [X] T025 [US1] 重构 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionScreen.kt`，使用资源文字、日期选择器及明确加载/内容/空/错误组件
- [X] T026 [US1] 重构 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/time_selection/TimeSelectionScreen.kt`，移除 `LocalCinemaProvider`，按影院时区显示场次完整字段与不可选原因
- [X] T027 [US1] 更新 `app/src/main/java/me/ibrahim/moviesapp/compose/core/Routes.kt` 与 `MoviesNavGraph.kt`，让影院到场次页面只传稳定 movieId、cinemaId 和 localDate，并清除导航层对本地影院数据的反查

**Checkpoint**: US1 可独立演示；P1 MVP 完成。

---

## Phase 4: User Story 2 - 筛选并比较影院 (Priority: P2)

**Goal**: 用户可按区域、时间和价格筛选，并按推荐、距离或最低可售价排序；无定位时获得明确降级说明。

**Independent Test**: 给定多影院固定数据，组合筛选只返回仍含匹配可售场次的影院；三种排序稳定正确，清除筛选不改变电影与日期。

### Tests for User Story 2

- [X] T028 [P] [US2] 先编写区域/时间/价格组合筛选、FR-009 影院剔除、推荐/价格/距离排序及无坐标降级的失败后端测试到 `backend/test_showtimes.py`
- [ ] T029 [P] [US2] 先编写应用/清除筛选、保留电影日期、降级提示和错误后保留条件的失败 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionViewModelTest.kt`
- [ ] T030 [P] [US2] 先编写筛选面板、排序菜单、清除按钮和无位置提示的失败 Compose UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionScreenTest.kt`

### Implementation for User Story 2

- [X] T031 [US2] 在 `backend/showtimes.py` 实现区域、影院当地时间和整数分价格筛选，确保每家返回影院至少含一个匹配可售场次
- [X] T032 [US2] 在 `backend/showtimes.py` 实现稳定推荐、最低可售价、Haversine 距离排序，以及无有效坐标时的推荐降级和位置数据不落库规则
- [X] T033 [US2] 扩展 `backend/app.py` 的影院场次路由以解析全部筛选/排序/坐标参数并返回 `requestedSort`、`appliedSort`、`sortNotice` 和可用区域
- [X] T034 [US2] 扩展 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionContract.kt` 与 `CinemaSelectionViewModel.kt`，实现筛选草稿、应用、清除、排序和位置可用性状态
- [X] T035 [US2] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionScreen.kt` 实现可访问的区域/时间/价格筛选 UI、三种排序、最低价/距离展示和降级说明

**Checkpoint**: US1 与 US2 都可独立验证，筛选失败不会破坏已有查询上下文。

---

## Phase 5: User Story 3 - 选择场次进入选座 (Priority: P3)

**Goal**: 用户选择场次时先复核权威状态；只在已登录且快照未变时使用稳定 ID 进入选座，变化、不可售和重复点击都被安全处理。

**Independent Test**: 分别模拟未变化、涨价、改时、换厅、停售、取消、售罄、已开始、匿名和连续点击，只有“已登录 + 未变化 + 可售”会导航一次。

### Tests for User Story 3

- [X] T036 [P] [US3] 先编写复核的 UNCHANGED/CHANGED/UNAVAILABLE、版本与变更字段、未知场次及时间边界失败测试到 `backend/test_showtimes.py`
- [ ] T037 [P] [US3] 先编写复核中防重复、陈旧数据提示、不可售原因、匿名认证门禁和单次导航的失败 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/time_selection/TimeSelectionViewModelTest.kt`
- [ ] T038 [P] [US3] 先编写复核进度、禁用按钮、变更提示和不可选状态的失败 Compose UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/time_selection/TimeSelectionScreenTest.kt`
- [ ] T039 [P] [US3] 先编写选座路由稳定 ID 与最新金额传递的失败导航测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/core/ShowtimeNavigationTest.kt`

### Implementation for User Story 3

- [X] T040 [US3] 在 `backend/showtimes.py` 实现场次版本比较、最新快照、变更字段和权威不可售原因计算
- [X] T041 [US3] 在 `backend/app.py` 实现 `GET /v1/showtimes/{showtimeId}/validation` 路由及 400/404/500 错误契约
- [X] T042 [US3] 扩展 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/time_selection/TimeSelectionContract.kt` 与 `TimeSelectionViewModel.kt`，实现防重复复核、变化刷新、不可售反馈和 ready-to-navigate effect
- [X] T043 [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/time_selection/TimeSelectionScreen.kt` 接入复核状态，禁用不可售/处理中按钮并显示价格、时间或状态变化
- [X] T044 [US3] 更新 `app/src/main/java/me/ibrahim/moviesapp/compose/core/Routes.kt`，让 `SeatSelectionRoute` 携带 movieId、cinemaId、auditoriumId、showtimeId、latestVersion 和最新整数分金额
- [ ] T045 [US3] 更新 `app/src/main/java/me/ibrahim/moviesapp/compose/core/MoviesNavGraph.kt`，接入已有会话状态和登录返回路径；匿名只保存待继续场次 ID，登录后再次复核且不自动锁座

**Checkpoint**: 三个用户故事全部可独立验收，旧快照和匿名状态都不能绕过进入选座门禁。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 清理旧路径并完成性能、可访问性、文档和构建门槛。

- [ ] T046 [P] 补齐影院/场次交互的 content description、语义状态、最小触控区域和屏幕阅读顺序到 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/cinema_selection/CinemaSelectionScreen.kt` 与 `presentation/time_selection/TimeSelectionScreen.kt`
- [X] T047 删除场次运行路径对 `app/src/main/java/me/ibrahim/moviesapp/compose/data/local/LocalCinemaProvider.kt` 和 `data/dto/MaoyanDto.kt` 中旧影院 DTO 的引用，并在确认无引用后移除对应旧代码
- [ ] T048 [P] 在 `backend/test_showtimes.py` 增加查询计时与代表性数据量检查，并检查 `backend/app.py` 的查询使用 `(movie_id, starts_at)` 与 `(cinema_id, starts_at)` 索引
- [X] T049 审核日志与配置，确保 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/CinemaRemoteApi.kt` 和 `backend/app.py` 不记录坐标、会话凭据或生产地址
- [ ] T050 按 `specs/002-cinema-showtimes/quickstart.md` 执行后端测试、Android 单测、Debug 构建、可用时的仪器测试及四组人工验收，并把实际结果追加到该文件的 Completion evidence
- [ ] T051 对照 `specs/002-cinema-showtimes/spec.md`、`plan.md`、`data-model.md` 和 `contracts/showtimes-api.yaml` 做最终追踪审查，修正文档或实现偏差并确认所有宪章门槛通过

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 无依赖，可立即开始。
- **Phase 2 Foundational**: 依赖 Phase 1，阻塞所有用户故事。
- **US1 (P1)**: 依赖 Phase 2；完成后即形成可演示 MVP。
- **US2 (P2)**: 依赖 Phase 2 的查询模型；可与 US1 的 UI 后半段并行，但合并前需保留 US1 状态语义。
- **US3 (P3)**: 依赖 Phase 2；复核端点可并行开发，最终导航集成依赖 US1 的稳定场次模型。
- **Phase 6 Polish**: 依赖计划交付的用户故事完成。

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 (MVP)
                    ├-> US2
                    └-> US3 -> stable-ID seat navigation
US1 + US2 + US3 -> Polish
```

### Within Each User Story

1. 先写测试并确认因缺少实现而失败。
2. 后端规则先于路由；Domain/DTO 映射先于 Repository/ViewModel。
3. ViewModel 状态先于 Compose UI。
4. 故事检查点必须独立通过后再视为完成。

### Parallel Opportunities

- T002、T003、T004 可并行。
- T007、T008、T010 可在 T005/T006 进行时并行。
- US1 的 T015–T018、US2 的 T028–T030、US3 的 T036–T039 各自可并行编写。
- Foundation 完成后，后端 US2 筛选与 US3 复核可以由不同开发者并行；涉及同一 Android 页面时应串行合并。
- T046 与 T048 可并行。

## Parallel Example: User Story 1

```text
并行 A: T015 backend/test_showtimes.py（日期与可售规则）
并行 B: T016 CinemaShowtimeMapperTest.kt（契约映射）
并行 C: T017 CinemaSelectionViewModelTest.kt（状态流）
并行 D: T018 CinemaSelectionScreenTest.kt（UI 状态）

随后: T019 -> T020；T021 -> T022；T023 -> T024；最后 T025 -> T026 -> T027
```

## Implementation Strategy

### MVP First

1. 完成 T001–T014（Setup + Foundation）。
2. 完成 T015–T027（US1）。
3. 运行 US1 的后端、Mapper、ViewModel 和 UI 测试。
4. 停止并演示“匿名浏览 + 日期切换 + 空/错误恢复”；此时已是可交付 MVP。

### Incremental Delivery

1. **MVP**: 权威日期和场次浏览（US1）。
2. **Increment 2**: 组合筛选、排序与位置降级（US2）。
3. **Increment 3**: 复核、认证门禁和稳定 ID 选座导航（US3）。
4. **Hardening**: 清理本地伪数据、可访问性、性能和全量验证（Phase 6）。

## Notes

- `[P]` 只表示文件级无冲突；仍须满足任务依赖。
- 不得以 `LocalCinemaProvider` 作为断网回退，因为它会伪造票务事实。
- 查询 API 可以匿名；锁座与购票 API 的服务端鉴权属于后续功能，但客户端导航门禁不能省略。
- 每个故事完成时提交对应自动化测试结果，最后按 quickstart 记录未执行检查及风险。
