# Tasks: 账号认证与会话

**Input**: Design documents from `/specs/001-account-authentication/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/auth-api.openapi.yaml`, `quickstart.md`

**Tests**: 本项目宪章要求核心认证、授权、迁移和跨层行为具有自动化测试；每个用户故事先编写失败测试，再实现。

**Organization**: 任务按用户故事组织，并包含明确文件路径、依赖关系和独立验证条件。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与相邻任务并行，目标文件不同且不依赖未完成任务
- **[Story]**: 对应 `spec.md` 的用户故事
- 所有任务都使用项目根目录相对路径

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立认证实现所需的配置、测试入口和源文件边界。

- [X] T001 在 `backend/app.py` 中集中声明 30 天会话有效期、15 分钟失败窗口、5 次失败阈值、15 分钟阻断时长和数据库 schema 版本配置，并支持测试配置覆盖
- [X] T002 [P] 在 `app/build.gradle.kts` 中添加 Android 单元测试协程、Ktor MockEngine、Room migration test 和必要测试依赖
- [X] T003 [P] 在 `gradle/libs.versions.toml` 中登记 T002 所需依赖版本与别名，保持现有 Kotlin/Compose/Ktor BOM 版本一致
- [X] T004 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/RemoteApiEndpoints.kt` 中分离本地 debug 与 release 后端基础地址，并为认证和同步路由定义常量
- [X] T005 [P] 在 `backend/test_auth.py` 中建立临时 SQLite、Flask test client、账号注册和 Bearer 请求辅助夹具

**Checkpoint**: 双端认证开发与测试骨架就绪。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成所有用户故事共同依赖的安全数据模型、会话机制、客户端分层和迁移。

**⚠️ CRITICAL**: 此阶段完成前不得开始用户故事实现。

- [X] T006 在 `backend/test_auth.py` 中添加旧同步密码迁移、事务回滚、账号/会话/限流表约束测试并确认测试先失败
- [X] T007 在 `backend/app.py` 中实现幂等 schema 迁移：创建 `user_accounts`、`auth_sessions`、`auth_throttle`，将旧 payload 密码强哈希导入并原子删除明文字段
- [X] T008 [P] 在 `backend/auth.py` 中实现邮箱规范化、用户名 2–30 字符校验、密码 8–64 字符校验、Werkzeug 密码哈希与校验、随机令牌生成和令牌摘要函数
- [X] T009 在 `backend/auth.py` 中实现 Bearer 会话解析、30 天绝对到期、撤销检查、账号状态检查和统一未认证错误，不记录密码、原始令牌或 Authorization 头
- [X] T010 在 `backend/auth.py` 中实现 15 分钟窗口内 5 次失败触发 15 分钟阻断的持久化逻辑，对未知邮箱与错误密码保持相同响应语义
- [X] T011 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/auth/AuthUser.kt` 中创建不含密码的账号模型，字段为稳定 ID、规范邮箱、2–30 字符用户名和可选头像
- [X] T012 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/auth/AuthError.kt` 中定义输入、重复邮箱、无效凭据、网络、限流、未认证和服务异常错误
- [X] T013 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/auth/AuthState.kt` 中定义 `Checking`、`Guest`、`Authenticated`、`RecoverableError` 以及不含敏感数据的 `PendingAuthAction`
- [X] T014 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/auth/AuthRepository.kt` 中定义注册、登录、恢复、退出、认证状态观察和受保护操作上下文契约
- [X] T015 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/AuthDto.kt` 中实现与 `contracts/auth-api.openapi.yaml` 对齐且密码字段只写的序列化 DTO
- [X] T016 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/AuthRemoteApi.kt` 中定义注册、登录、当前用户和退出接口契约
- [X] T017 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/AuthRemoteApiImpl.kt` 中实现认证 HTTP 调用、Bearer 头和状态码到 `AuthError` 的映射，并禁止认证正文日志
- [X] T018 [P] 在 `app/src/test/java/me/ibrahim/moviesapp/compose/data/auth/SessionStoreTest.kt` 中添加令牌加密往返、篡改失败、到期清除、退出清除和离线待撤销队列测试替身场景
- [X] T019 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/auth/SessionStore.kt` 中使用 Android Keystore AES/GCM 加密保存令牌、IV、到期时间和非权威用户提示，禁止持久化密码
- [X] T020 [P] 在 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/data/database/Migration7To8Test.kt` 中添加 Room 7→8 迁移测试，验证收藏/订单/资料保留且 `password`、`isLoggedIn` 列消失
- [X] T021 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/database/UserEntity.kt`、`MoviesDatabase.kt`、`DatabaseFactory.kt` 与 `MoviesDao.kt` 中实现 v8 无密码账号缓存、旧记录可空且认证后补齐的唯一 `userId`、显式 7→8 迁移并移除破坏性迁移与本地登录标志查询
- [X] T022 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/repository/AuthRepositoryImpl.kt` 中实现单一 `AuthState`、安全会话持久化、资料缓存和并发认证操作互斥
- [X] T023 在 `app/src/main/java/me/ibrahim/moviesapp/compose/di/NetworkModule.kt`、`RepositoryModule.kt` 与 `CoreModule.kt` 中注册 `AuthRemoteApi`、`SessionStore`、`AuthRepository` 及认证 ViewModel 依赖
- [X] T024 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/SyncDto.kt`、`data/repository/MoviesRepositoryImpl.kt`、`domain/MoviesRepository.kt`、`presentation/settings/SettingsViewModel.kt` 与 `SettingsScreen.kt` 中移除密码、本地认证和本地改密逻辑，令账号域业务通过 `AuthRepository` 获取当前用户并把改密标记为范围外功能

**Checkpoint**: 安全认证基础与无损迁移就绪，后续故事可以按优先级实现。

---

## Phase 3: User Story 1 - 注册普通用户账号 (Priority: P1) 🎯 MVP

**Goal**: 访客使用用户名、唯一邮箱和两次一致密码创建账号，并自动获得有效会话。

**Independent Test**: 使用未注册邮箱提交有效信息创建账号；空字段、无效邮箱、用户名边界、8–64 字符密码边界、不一致确认密码和重复邮箱均得到规定结果。

### Tests for User Story 1

- [X] T025 [US1] 在 `backend/test_auth.py` 中添加注册成功、邮箱大小写去重、字段校验、重复提交、密码只存哈希和响应不回显密码的契约测试并确认先失败
- [X] T026 [P] [US1] 在 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/login/LoginViewModelTest.kt` 中添加注册字段校验、提交互斥、成功自动认证和错误映射测试并确认先失败
- [X] T027 [P] [US1] 在 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/login/LoginScreenTest.kt` 中添加注册模式、确认密码、字段错误、密码隐藏和处理中按钮禁用测试并确认先失败

### Implementation for User Story 1

- [X] T028 [US1] 在 `backend/app.py` 中实现 `POST /auth/register`，强制用户名 2–30 字符、规范邮箱唯一、密码 8–64 字符且与确认密码一致，并原子创建账号与会话
- [X] T029 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/login/LoginContract.kt` 中定义登录/注册模式、字段值、逐字段错误、提交状态和一次性导航事件
- [X] T030 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/login/LoginViewModel.kt` 中实现注册输入校验、单次提交、调用 `AuthRepository.register` 和错误状态更新
- [X] T031 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/login/LoginScreen.kt` 中增加确认密码字段、内联错误、处理中状态与可访问语义，并将所有文字迁移到 `app/src/main/res/values/strings.xml` 和 `values-zh/strings.xml`
- [X] T032 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/login/LoginActivity.kt` 中返回注册成功结果与来源上下文，不直接自动执行受保护业务动作

**Checkpoint**: US1 可独立演示注册、重复邮箱和自动登录，是首个可交付 MVP。

---

## Phase 4: User Story 2 - 登录并恢复会话 (Priority: P1)

**Goal**: 已有用户安全登录，有效会话在重启时恢复，过期/撤销会话回到游客状态。

**Independent Test**: 使用预置账号登录，重启后保持同一账号；错误凭据统一提示；会话撤销、到期和网络故障产生不同且安全的结果。

### Tests for User Story 2

- [X] T033 [US2] 在 `backend/test_auth.py` 中添加登录成功、未知邮箱/错误密码同形响应、15 分钟内 5 次失败触发 15 分钟阻断、会话查询、过期和撤销测试并确认先失败
- [X] T034 [P] [US2] 在 `app/src/test/java/me/ibrahim/moviesapp/compose/data/repository/AuthRepositoryImplTest.kt` 中添加正确登录、会话保存、有效恢复、无效清除、网络恢复错误和并发提交测试并确认先失败
- [X] T035 [P] [US2] 在 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/splash/SplashViewModelTest.kt` 中添加无令牌、有效令牌、过期令牌和暂时网络失败的启动路由测试并确认先失败

### Implementation for User Story 2

- [X] T036 [US2] 在 `backend/app.py` 中实现 `POST /auth/login` 与 `GET /auth/me`，应用统一无效凭据消息、15 分钟内 5 次失败触发 15 分钟阻断、会话创建和有效性检查
- [X] T037 [US2] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/login/LoginViewModel.kt` 与 `LoginScreen.kt` 中实现登录提交、网络/凭据/限流区分、邮箱保留、密码隐藏及安全重试
- [X] T038 [US2] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/splash/SplashViewModel.kt` 中调用 `AuthRepository.restoreSession()` 并输出游客、已认证和可恢复错误启动状态
- [X] T039 [US2] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/splash/SplashActivity.kt` 与 `SplashScreen.kt` 中根据恢复结果进入 `MainActivity` 的游客或认证模式，不再强制所有用户进入登录页
- [X] T040 [US2] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/core/MoviesApp.kt` 中初始化认证状态观察，确保进程恢复时只有 `AuthRepository` 决定当前身份

**Checkpoint**: US2 可独立验证登录、重新启动恢复、失效清除与网络错误。

---

## Phase 5: User Story 3 - 浏览与受保护操作 (Priority: P2)

**Goal**: 游客保持电影浏览能力，但收藏、个性化推荐、选座和购票在执行前统一要求登录并保留来源上下文。

**Independent Test**: 游客浏览电影后分别触发四类受保护动作，全部被拦截且无副作用；登录成功返回来源，仍需用户明确继续原操作。

### Tests for User Story 3

- [X] T041 [US3] 在 `backend/test_app.py` 中添加同步接口缺少、过期、撤销和他人令牌时拒绝访问，以及有效令牌只能读写自身数据的测试并确认先失败
- [X] T042 [P] [US3] 在 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/AuthGateTest.kt` 中添加四类 `PendingAuthAction`、取消登录、成功返回和禁止自动副作用测试并确认先失败
- [X] T043 [P] [US3] 在 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/AuthProtectedFlowsTest.kt` 中添加游客浏览可用、收藏/个性化推荐/选座/购票入口拦截，以及游客、无收藏、推荐失败、无电影时备用内容与来源标签测试并确认先失败

### Implementation for User Story 3

- [X] T044 [US3] 在 `backend/app.py` 中为 `POST /sync/push` 与 `GET /sync/pull` 强制 Bearer 会话，移除客户端可提交的账号身份和密码字段，并从会话推导用户
- [X] T045 [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/auth/AuthRequiredDialog.kt` 中实现统一登录提示、取消、登录启动和无敏感数据的来源上下文返回
- [X] T046 [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/core/Routes.kt` 与 `MoviesNavGraph.kt` 中传递 `PendingAuthAction`，登录成功后回到原电影/场次/确认上下文但不自动产生副作用
- [X] T047 [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/movies_detail/MovieDetailViewModel.kt` 与 `MovieDetailScreen.kt` 中为收藏和进入购票流程添加认证门禁
- [X] T048 [P] [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/movies_list/MoviesListViewModel.kt`、`MoviesListState.kt` 与 `MoviesListScreen.kt` 中允许游客浏览，仅向已登录且有收藏的用户请求个性化推荐；游客、无收藏或推荐失败时展示标注为“热门推荐/正在上映”的备用内容，个性化成功时标注“根据你的收藏推荐”，无电影时显示明确空状态和重试入口
- [X] T049 [P] [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/favorite/FavoriteMoviesViewModel.kt` 与 `FavoriteMoviesScreen.kt` 中阻止游客读取或写入账号收藏并显示登录入口
- [X] T050 [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/seat_selection/SeatSelectionViewModel.kt`、`SeatSelectionScreen.kt`、`payment/PaymentViewModel.kt` 与 `PaymentScreen.kt` 中在选座和购票确认前重新校验有效身份
- [X] T051 [US3] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/repository/MoviesRepositoryImpl.kt` 中为同步请求附加当前 Bearer 令牌、处理 401 会话失效并停止匿名私有数据同步

**Checkpoint**: US3 可独立验证公开浏览与全部受保护操作的无副作用拦截。

---

## Phase 6: User Story 4 - 主动退出登录 (Priority: P3)

**Goal**: 用户主动退出后，本地敏感会话立即清除、服务端会话撤销，重启不恢复且账号私有数据不串用。

**Independent Test**: 账号 A 退出并重启后保持游客状态；账号 B 登录后无法看到账号 A 的收藏、推荐或订单。

### Tests for User Story 4

- [X] T052 [US4] 在 `backend/test_auth.py` 中添加退出幂等、会话撤销后拒绝访问和不同账号会话隔离测试并确认先失败
- [X] T053 [P] [US4] 在 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/settings/SettingsViewModelTest.kt` 中添加在线退出、离线本地退出、私有状态清理和账号切换测试并确认先失败

### Implementation for User Story 4

- [X] T054 [US4] 在 `backend/app.py` 中实现 `POST /auth/logout`，撤销当前令牌且重复退出不会恢复或创建会话
- [X] T055 [US4] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/repository/AuthRepositoryImpl.kt` 与 `data/auth/SessionStore.kt` 中实现服务端撤销尝试、活动令牌无条件停用、离线令牌加密待撤销队列、认证状态转游客和账号域内存缓存清理
- [X] T056 [US4] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/settings/SettingsViewModel.kt`、`SettingsScreen.kt` 与 `presentation/login/LoginScreen.kt` 中实现退出确认、处理中状态、退出后返回登录页面及继续以访客身份浏览入口
- [X] T057 [US4] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/repository/MoviesRepositoryImpl.kt` 中停止退出账号的自动同步并清除私有内存快照，确保下一个账号不会继承状态

**Checkpoint**: US4 可独立验证退出、重启和账号隔离。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 完成跨故事安全、可访问性、发布配置、文档和验收门槛。

- [X] T058 [P] 将所有认证、会话、门禁和错误文字补充到 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh/strings.xml`，修复相关乱码并移除新增硬编码文字
- [X] T059 [P] 在 `app/src/main/AndroidManifest.xml`、`app/src/debug/AndroidManifest.xml` 与 `app/src/debug/res/xml/network_security_config.xml` 中仅为 debug 本地服务开放 cleartext，确保 release 认证流量只允许 HTTPS
- [X] T060 在 `app/src/main/java/me/ibrahim/moviesapp/compose/di/NetworkModule.kt` 中清理认证日志：不得记录 Authorization、密码、令牌、完整请求/响应正文或账号私有数据
- [X] T061 [P] 更新 `backend/README.md` 和根目录 `README.md`，记录认证接口、环境配置、HTTPS 要求、数据库迁移、测试命令及不再同步密码
- [X] T062 执行 `backend/.venv/Scripts/python.exe -m unittest discover -s backend -p "test_*.py" -v` 并把失败项修复在对应的 `backend/test_auth.py`、`backend/test_app.py` 或实现文件
- [X] T063 执行 `.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest assembleDebug --console=plain` 并把失败项修复在对应 Android 测试或实现文件
- [X] T064 按 `specs/001-account-authentication/quickstart.md` 完成 US1–US4 手工验收，以至少 10 次代表性注册、登录和错误纠正尝试核对百分比指标，并记录 SC-001 至 SC-009（包括五类推荐状态）的结果到 `specs/001-account-authentication/quickstart.md`
- [X] T065 复核 `specs/001-account-authentication/contracts/auth-api.openapi.yaml`、`data-model.md` 与最终实现一致，并确认数据库中不存在明文密码或原始会话令牌

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 可立即开始；T002 与 T003 需在 Gradle 同步前同时完成。
- **Phase 2 Foundational**: 依赖 Phase 1，阻塞所有用户故事。T006 必须先于 T007；T018 先于 T019；T020 先于 T021；T011–T017 完成后才能做 T022–T024。
- **US1 (Phase 3)**: 依赖 Phase 2，是建议 MVP。
- **US2 (Phase 4)**: 依赖 Phase 2；可与 US1 后端以外的工作并行，但完整演示需要已有账号。
- **US3 (Phase 5)**: 依赖 Phase 2 和可用登录流程（US2）；各业务入口任务可并行。
- **US4 (Phase 6)**: 依赖 Phase 2 和 US2 会话；可与 US3 并行。
- **Polish (Phase 7)**: 依赖计划交付的全部用户故事。

### User Story Dependency Graph

```text
Setup → Foundation → US1 (注册) ─┐
                    US2 (登录) ─┼→ US3 (受保护操作) ─┐
                               └→ US4 (退出) ───────┼→ Polish
```

### Parallel Opportunities

- T002、T003、T004、T005 可并行。
- T011、T012、T013、T015、T016、T018、T020 可在后端迁移工作之外并行。
- 每个故事中的后端测试、Android 单元测试和 UI 测试目标文件不同，可按 `[P]` 标记并行。
- US3 的推荐、收藏和交易入口分别位于不同文件，可在统一门禁 T045–T046 后并行。
- US3 与 US4 在 US2 完成后可由不同开发者并行。

## Parallel Examples

### User Story 1

```text
T025 backend/test_auth.py 注册契约测试
T026 LoginViewModelTest.kt 注册状态测试
T027 LoginScreenTest.kt 注册界面测试
```

### User Story 3

```text
T048 MoviesListViewModel.kt / MoviesListScreen.kt 推荐门禁
T049 FavoriteMoviesViewModel.kt / FavoriteMoviesScreen.kt 收藏门禁
T050 SeatSelection* / Payment* 交易门禁（T045–T046 完成后）
```

## Implementation Strategy

### MVP First

1. 完成 Phase 1 和 Phase 2。
2. 完成 US1 注册故事并独立验证。
3. 再完成 US2，使账号可以再次登录和恢复会话。
4. 在认证基础稳定后接入 US3 和 US4，避免在不可信身份机制上构建业务门禁。

### Incremental Delivery

- **Increment 1**: 安全注册与自动会话（US1）。
- **Increment 2**: 登录、恢复、失效处理（US2）。
- **Increment 3**: 游客浏览和四类受保护操作（US3）。
- **Increment 4**: 退出和账号隔离（US4）。
- 每个增量必须通过自己的独立测试，不能以最终集成阶段替代故事验收。

## Requirement Traceability

| Scope | Primary Tasks |
|-------|---------------|
| FR-001–FR-007 注册与自动会话 | T025–T032 |
| FR-008–FR-013 登录、错误、限流与恢复 | T033–T040 |
| FR-014–FR-016 游客浏览与认证门禁 | T041–T051 |
| FR-017–FR-018 退出与重启 | T052–T057 |
| FR-019 密码隐藏与不回显 | T008、T015、T025、T027、T031、T060 |
| FR-020 连续失败保护 | T006、T010、T033、T036 |
| FR-021 账号数据隔离 | T041、T049、T051、T053、T057 |
| FR-022 明确认证范围 | T028、T036、T061、T065 |
| FR-023–FR-024 推荐备用内容与来源说明 | T043、T048、T058、T064 |
| SC-001–SC-009 验收指标 | T062–T065 |

## Notes

- `[P]` 只用于不同目标文件且没有未完成依赖的任务。
- 用户故事测试必须先运行并确认失败，再实现对应行为。
- 不得为了通过测试恢复本地明文密码、匿名同步或 `isLoggedIn` 权威标志。
- 每完成一个阶段都可停下按 Checkpoint 独立验证。
