# Research: 影院与场次查询

## 1. 权威数据来源

**Decision**: 由 Flask/SQLite 后端保存并返回影院、影厅、场次、票价和售票状态；移除运行时随机排片。

**Rationale**: 宪章要求票务状态以后端为唯一权威源。当前 `LocalCinemaProvider` 会根据随机种子生成场次，无法表达取消、停售或并发变化，也不能跨设备保持一致。

**Alternatives considered**:

- 继续使用本地 provider：实现快，但无法满足 FR-004、FR-011 和 FR-012。
- 客户端缓存优先、后台刷新：会短暂允许用户基于过期数据进入选座，不适合作为本阶段默认行为。
- 直接依赖第三方影院 API：当前没有稳定凭据或契约，且会让验收依赖外部服务。

## 2. 时间与影院日期

**Decision**: 数据库存储 UTC ISO-8601 时间和影院 IANA 时区；服务端以影院当地日期筛选，客户端按返回的影院时区显示。

**Rationale**: 仅保存 `14:30` 无法判断日期、跨午夜或夏令时。UTC 时间点加 IANA 时区可以确定地完成未来/已结束判定，并满足 FR-016 与 SC-006。

**Alternatives considered**:

- 保存本地日期和时间字符串：读取直观，但遇到时区规则变化或重复时刻时不唯一。
- 使用设备时区：旅途中会把影院场次显示到错误日期。
- 仅保存固定 UTC 偏移：无法正确处理采用夏令时的影院。

## 3. 金额表示

**Decision**: API 和 Domain 使用 `amountMinor` 整数分与 ISO 4217 `currency`；只在 UI 层格式化。

**Rationale**: 当前价格为字符串，排序和计算容易出现解析与精度问题。整数最小货币单位能稳定比较和传递，并防止选座使用不同价格表示。

**Alternatives considered**:

- `Double`：易产生二进制浮点误差。
- 格式化字符串：适合显示，不适合作为业务值或排序键。

## 4. 查询与聚合边界

**Decision**: `GET /v1/movies/{movieId}/cinema-showtimes` 在服务端完成过滤、排序，并按影院返回场次；可售日期用单独端点获取。

**Rationale**: FR-009 要求只保留仍有匹配可售场次的影院。服务端统一执行可避免不同客户端产生不同结果，也减少传输无关场次。日期端点让客户端先构建日期选择器而无需下载全部排片。

**Alternatives considered**:

- 分别获取影院和场次再由客户端关联：请求更多，并可能组合不同版本的快照。
- 返回所有排片后本地筛选：实现简单，但权威规则散落到客户端且扩展性差。

## 5. 距离排序与位置隐私

**Decision**: 经纬度是查询的可选参数，后端仅用于当次 Haversine 距离计算，不保存、不写日志；无坐标时把 `distance` 排序降级为 `recommended` 并返回 `appliedSort` 和 `sortNotice`。

**Rationale**: 距离比较需要统一单位和计算方式，但位置不是完成购票的必要数据。显式降级满足 FR-008，同时符合数据最小化。

**Alternatives considered**:

- 强制定位权限：会阻止拒绝授权的用户使用核心流程。
- 客户端计算距离：需要向客户端暴露并维护全部坐标，且多端结果可能不一致。
- 无定位时按 0 距离排序：结果具有误导性。

## 6. 场次复核和陈旧数据

**Decision**: 每个场次返回单调递增 `version`。点击时调用 `GET /v1/showtimes/{id}/validation?observedVersion=...`；响应包含 `UNCHANGED`、`CHANGED` 或 `UNAVAILABLE` 及最新快照。

**Rationale**: 复核必须同时检查可售性、开始时间、影厅和价格，而不仅是“是否存在”。版本使变化判断明确；最新快照让客户端可解释变化并刷新页面。

**Alternatives considered**:

- 仅重新加载列表：难以把用户点击与新结果精确关联。
- 客户端时间戳过期策略：时间新鲜不等于状态正确。
- 复核时直接锁座：超出本功能范围，且违反“登录过程不自动锁座”。

## 7. 匿名浏览与认证门禁

**Decision**: 查询和复核接口允许匿名访问；进入选座前客户端检查会话，匿名用户转到登录并只保存待继续的场次 ID。后续锁座/购票服务仍必须服务端鉴权。

**Rationale**: FR-014 明确允许匿名浏览，又要求选座时认证。保存 ID 而非整个旧快照，登录后可再次复核且不会自动锁座。

**Alternatives considered**:

- 所有场次接口要求登录：与匿名浏览冲突。
- 登录成功立即锁座：用户尚未选择座位，且会产生无意库存占用。
- 仅隐藏选座按钮：不能替代业务门禁或服务端授权。

## 8. 数据库演进与开发数据

**Decision**: 在现有 `initialize_database` 中以附加式、幂等 SQL 新增 `cinemas`、`auditoriums`、`showtimes` 与索引，并用固定 fixture 的 upsert 种子数据支持开发和测试。

**Rationale**: 项目当前没有迁移框架；附加表不会破坏同步数据，幂等初始化兼容现有启动方式。固定数据可以稳定验证筛选、跨午夜和状态变化。

**Alternatives considered**:

- 立即引入 Alembic：对当前单文件 SQLite 服务过重，可在需要破坏性迁移或多环境发布时引入。
- 每次启动删除重建：会损坏已有用户同步数据。
- 继续随机生成：不可重复且无法可靠测试。

## 9. Android 分层迁移

**Decision**: 新建 `domain/cinema`、独立 DTO/Mapper/API/Repository，以及两个页面各自的 Contract/ViewModel；导航参数改为稳定 ID 和整数金额。

**Rationale**: 当前 ViewModel 和 Composable 直接访问 `LocalCinemaProvider`，违反宪章分层原则；`MoviesRepository` 已包含大量无关职责。独立边界能让状态规则和映射独立测试，又不必增加 Gradle 模块。

**Alternatives considered**:

- 继续扩展 `MoviesRepository`：改动少，但进一步混合电影目录、账号、同步、评论、订单和场次职责。
- 新增多个 Gradle feature 模块：隔离更彻底，但对当前项目规模和本次功能属于不必要复杂度。

## 10. 测试策略

**Decision**: 后端以 Flask test client 做契约/集成测试；Android 以 mapper、ViewModel 单测和关键 Compose UI 测试为主，端到端步骤记录在 quickstart。

**Rationale**: 核心风险集中在服务端过滤/时间/复核与客户端状态转换。现有依赖已支持 JUnit、Compose UI Test 和 Python `unittest`，无需新增测试框架。

**Alternatives considered**:

- 只做人工测试：无法稳定覆盖跨午夜、版本变化和所有售票状态。
- 只做 UI 测试：执行慢，且难以定位服务端业务规则错误。
