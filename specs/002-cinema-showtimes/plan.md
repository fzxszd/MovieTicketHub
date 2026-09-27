# Implementation Plan: 影院与场次查询

**Branch**: `002-cinema-showtimes` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-cinema-showtimes/spec.md`

## Summary

将现有由 `LocalCinemaProvider` 随机生成的影院与场次替换为 Flask/SQLite 后端的权威排片数据。Android 客户端通过独立的 `CinemaRepository` 获取可售日期、影院分组和场次，支持区域/时间/价格筛选及推荐/距离/最低价排序，并用不可变 UI 状态呈现加载、成功、空和错误。选择场次时先读取最新权威快照；仅当用户已登录且场次仍可售、关键字段未变化时，才把稳定的电影、影院、影厅和场次 ID 传入选座流程。

## Technical Context

**Language/Version**: Kotlin 2.1.0（Android，JVM target 1.8）；Python 3.12（后端）  
**Primary Dependencies**: Jetpack Compose BOM 2025.01.01、Navigation Compose 2.8.6、Ktor 3.0.3、Koin 4.0.2、kotlinx.serialization 1.8.0、Flask 3.0.3  
**Storage**: 后端 SQLite（影院、影厅、场次及迁移版本）；客户端本功能不新增持久化缓存  
**Testing**: JUnit 4、Compose UI Test、Python `unittest` + Flask test client  
**Target Platform**: Android API 24–35；本地/可部署的 Flask HTTP API  
**Project Type**: Android 移动应用 + Python API 服务  
**Performance Goals**: 正常网络下 95% 查询在 3 秒内进入列表或空状态；筛选/排序交互在已返回数据上无明显卡顿  
**Constraints**: 后端为场次、状态和票价唯一权威源；影院当地时区解释日期；金额使用整数分；匿名用户只可浏览；位置坐标可选且不持久化  
**Scale/Scope**: 单城市数十家影院、每部电影未来 14 天数百个场次；2 个现有 Compose 页面、1 套查询 API 和 1 个复核 API

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

| Principle | Result | Evidence |
|---|---|---|
| I. 分层架构与单向数据流 | PASS | 计划新增 Domain 模型/Repository、Data API/实现和 Presentation Contract/ViewModel；Composable 不直接访问本地或网络数据源。 |
| II. 购票与座位状态必须正确 | PASS | 后端持有场次、价格和状态；进入选座前强制复核，变化时阻断并刷新。 |
| III. 账号、隐私与安全优先 | PASS | 浏览接口公开；进入选座依赖已验证会话；坐标只作为当前请求可选参数且不存储。 |
| IV. 推荐规则 | PASS（不适用） | 本功能的“推荐排序”是影院场次排序，不读取用户收藏画像，也不改变电影推荐逻辑。 |
| V. 可恢复体验与明确状态 | PASS | UI Contract 明确 loading/content/empty/error/revalidating/auth-required/stale 状态，并保留查询条件。 |
| 技术与业务约束 | PASS | 延续 Kotlin/Compose/MVI/Clean/Ktor/Koin 与 Flask/SQLite；API、迁移和回滚在本计划中定义。 |
| 质量门槛 | PASS | 自动化测试覆盖查询、筛选、时区、不可售状态、复核和导航；交付前执行后端测试与 Android 构建。 |

### Post-design gate

| Principle | Result | Design confirmation |
|---|---|---|
| 分层与数据流 | PASS | `data-model.md` 区分 API/Domain/UI 概念；`CinemaRepository` 是 Presentation 唯一数据入口。 |
| 权威状态与金额 | PASS | `contracts/showtimes-api.yaml` 使用稳定 ID、整数分、ISO-8601 UTC 时间和 `version`；复核响应显式表达变更。 |
| 认证与隐私 | PASS | 契约仅让坐标出现在查询参数中；选座门禁在客户端会话和后续受保护票务 API 两处执行。 |
| 恢复与可访问性 | PASS | `quickstart.md` 包含错误、空结果、无定位、跨午夜和重复点击验证；用户文字进入资源文件。 |
| 测试与构建 | PASS | `tasks.md` 将包含后端集成测试、Domain/ViewModel 单测、Compose UI 测试和构建验证。 |

无宪章例外需要记录。

## Architecture and Data Flow

1. `CinemaSelectionScreen` 发送日期、城市、筛选和排序事件给 `CinemaSelectionViewModel`。
2. ViewModel 构造 `ShowtimeQuery` 并调用 `CinemaRepository.getCinemaShowtimes()`；Repository 通过 Ktor 调用 Flask API，将 DTO 映射为 Domain 模型。
3. Flask 按电影、影院当地日期和可售状态查询 SQLite，并在服务端执行筛选、排序和距离计算；响应按影院聚合。
4. `TimeSelectionScreen` 只显示所选影院在当前查询中的场次，并由 `TimeSelectionViewModel` 管理明确状态。
5. 用户点击场次后，ViewModel 防重复地调用 `revalidateShowtime(showtimeId, observedVersion)`。
6. 若匿名，先导航到登录并保留待继续的场次 ID，不创建锁座；若数据改变或不可售，留在当前页并展示最新数据；若一致且已登录，使用稳定 ID 导航到选座。

## Backend Authority, Failure Recovery, and Security

- SQLite 保存规范化影院、影厅、场次；客户端不得自行生成场次或把缓存状态覆盖服务端状态。
- API 返回 `serverTime`、影院 `timeZone`、场次 `startsAt`/`endsAt`（UTC）和单调递增 `version`。客户端按影院时区格式化。
- 查询超时、断网或 5xx 时保留当前日期、筛选和排序，并提供重试。无匹配结果和请求失败是不同状态。
- 复核使用场次 ID 与已观察版本；不可售或数据变化返回最新快照及机器可读原因，客户端不得继续旧导航。
- 浏览 API 不要求登录。登录状态仅用于进入选座门禁；真正的锁座与购票仍必须由后续票务接口在服务端鉴权。
- 经纬度不写入数据库或日志；未提供坐标时禁用“距离”语义并降级到推荐排序，同时在响应和 UI 中说明。

## Migration and Rollback

- 新增幂等的 SQLite schema 初始化和种子函数；现有 `user_sync_data`、`global_comments` 表保持不变。
- 开发种子从固定 fixture 创建影院/影厅/场次，禁止运行时随机生成，确保测试可重复。
- Android 完成 API 路径后移除 `CinemaSelectionViewModel`、`TimeSelectionScreen` 和导航对 `LocalCinemaProvider` 的依赖；旧 DTO 的其他用途确认清零后再删除。
- 如客户端发布需分阶段进行，可在短期保留旧 provider 代码但不作为运行时回退；服务异常必须显示错误，不能回退到伪造场次。
- 回滚应用版本时，新增表可保留且不影响旧同步接口；API 回滚只需撤销路由，数据迁移为附加式，无破坏性降级。

## Verification Strategy

- 后端集成测试：按当地日期查询、只返回有匹配可售场次的影院、全部筛选/排序、无坐标降级、跨午夜、不可售状态、版本复核与错误响应。
- Android 单元测试：DTO 映射、金额/时区格式化、ViewModel 状态转换、清除筛选、重试保留上下文、复核变化和认证门禁。
- Compose UI 测试：加载/空/错误内容、禁用场次原因、筛选控件、可访问性描述及防重复点击。
- 端到端人工验证：按 `quickstart.md` 启动 Flask 和 Android 模拟器，走完浏览、筛选、复核、登录跳转和稳定 ID 导航。
- 最终门槛：`python -m unittest discover -s backend -p "test_*.py"`、`./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug`（无设备时记录跳过 instrumentation 的原因）。

## Project Structure

### Documentation (this feature)

```text
specs/002-cinema-showtimes/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── showtimes-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── app.py
├── showtimes.py
├── test_app.py
└── test_showtimes.py

app/src/main/java/me/ibrahim/moviesapp/compose/
├── core/
│   ├── MoviesNavGraph.kt
│   └── Routes.kt
├── data/
│   ├── dto/CinemaShowtimeDto.kt
│   ├── mappers/CinemaShowtimeMapper.kt
│   ├── network/CinemaRemoteApi.kt
│   └── repository/CinemaRepositoryImpl.kt
├── di/
│   ├── CoreModule.kt
│   ├── NetworkModule.kt
│   └── RepositoryModule.kt
├── domain/cinema/
│   ├── Cinema.kt
│   ├── CinemaRepository.kt
│   ├── Money.kt
│   ├── Showtime.kt
│   └── ShowtimeQuery.kt
└── presentation/
    ├── cinema_selection/
    │   ├── CinemaSelectionContract.kt
    │   ├── CinemaSelectionScreen.kt
    │   └── CinemaSelectionViewModel.kt
    └── time_selection/
        ├── TimeSelectionContract.kt
        ├── TimeSelectionScreen.kt
        └── TimeSelectionViewModel.kt

app/src/test/java/me/ibrahim/moviesapp/compose/
├── data/CinemaShowtimeMapperTest.kt
├── presentation/cinema_selection/CinemaSelectionViewModelTest.kt
└── presentation/time_selection/TimeSelectionViewModelTest.kt

app/src/androidTest/java/me/ibrahim/moviesapp/compose/
├── presentation/cinema_selection/CinemaSelectionScreenTest.kt
└── presentation/time_selection/TimeSelectionScreenTest.kt
```

**Structure Decision**: 保留现有单 Android app 模块和单 Flask 服务。场次从过度膨胀的 `MoviesRepository` 拆成独立 `CinemaRepository`，但不引入新 Gradle 模块；后端查询规则集中到 `backend/showtimes.py`，`app.py` 只负责应用组装和路由接入。

## Complexity Tracking

无需要宪章豁免的复杂度。
