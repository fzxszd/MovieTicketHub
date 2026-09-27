# Implementation Plan: 账号认证与会话

**Branch**: `未创建（未启用 Git 扩展）` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-account-authentication/spec.md`

## Summary

将现有“Room 本地明文密码 + 未认证同步接口”的演示式登录替换为服务端权威账号认证。Flask 后端负责邮箱唯一性、密码哈希、登录保护和可撤销会话；Android 客户端通过独立认证仓库管理注册、登录、会话恢复和退出，并使用 Android Keystore 加密保存不透明会话令牌。访客可以浏览电影，收藏、个性化推荐、选座和购票通过统一认证门禁拦截。现有明文密码数据通过显式迁移转换或清除，不再进入同步负载、日志或 Room。

## Technical Context

**Language/Version**: Android Kotlin 2.1.0（JVM 17 构建环境，minSdk 24）；后端 Python 3.12.7  
**Primary Dependencies**: Jetpack Compose、Lifecycle/ViewModel、Kotlin Coroutines/Flow、Ktor 3.0.3、kotlinx.serialization 1.8.0、Room 2.6.1、Koin 4.0.2；Flask 3.0.3、Werkzeug 密码哈希、Python sqlite3  
**Storage**: Android Room 用于账号资料与账号域业务缓存；Android Keystore + 加密偏好文件用于会话令牌；后端 SQLite 用于账号、会话、登录保护和同步数据  
**Testing**: JUnit 4、AndroidX/Compose UI 测试、Room migration 测试；Python unittest + Flask test client + 临时 SQLite  
**Target Platform**: Android 7.0（API 24）及以上；本地/课程演示 Flask 服务，生产部署必须位于 HTTPS 反向代理之后  
**Project Type**: Android 移动客户端 + Flask API 服务  
**Performance Goals**: 正常网络下 95% 登录在 30 秒内完成；有效会话启动恢复在 5 秒内给出结果；认证操作完成后 3 秒内展示反馈  
**Constraints**: 服务端为认证唯一权威；客户端不得存储密码；令牌必须可撤销并加密保存；15 分钟内 5 次失败登录触发 15 分钟阻断；重复提交必须受控；游客浏览不依赖认证服务；debug 可访问模拟器本机 HTTP，release 禁止明文认证流量  
**Scale/Scope**: 单 Android 应用、单 Flask 服务、普通用户一种角色；4 个用户故事、22 条功能需求；不包含邮箱验证、找回密码、第三方登录和管理员权限

## Constitution Check

*GATE: Phase 0 前检查，Phase 1 设计完成后再次检查。*

| 宪章约束 | 设计响应 | Gate |
|----------|----------|------|
| 分层架构与单向数据流 | 新建 Domain `AuthRepository` 契约，Data 层实现网络与会话存储，Presentation 仅消费不可变 `AuthState` 与发送事件 | PASS |
| 服务端权威和私有数据授权 | 注册、登录、会话验证、退出及同步接口全部由后端认证；客户端本地标志不作为授权依据 | PASS |
| 密码与令牌安全 | 密码仅在认证请求中短暂存在；后端只保存强哈希；Room、同步 JSON、日志不再包含密码；令牌在设备端加密保存、服务端仅保存摘要 | PASS |
| 明确加载、成功、错误与恢复状态 | 登录/注册/恢复使用显式状态；区分输入、凭据、网络、限流和会话失效，并提供安全重试 | PASS |
| 字符串资源与可访问性 | 所有新增用户文字进入默认及中文字符串资源；输入、按钮、错误和处理中状态具有可访问语义 | PASS |
| 自动化测试与构建门槛 | 计划包含后端认证/授权测试、Android 仓库/ViewModel/迁移/UI 测试和双端构建验证 | PASS |
| 数据迁移和回滚 | Room 7→8 与后端 SQLite 增量迁移均保留非密码数据；迁移前备份，失败即停止启动而非破坏性重建 | PASS |

**Pre-design gate result**: PASS，无需宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/001-account-authentication/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── auth-api.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/me/ibrahim/moviesapp/compose/
├── core/
│   ├── MoviesApp.kt
│   ├── MoviesNavGraph.kt
│   └── Routes.kt
├── domain/auth/
│   ├── AuthError.kt
│   ├── AuthRepository.kt
│   ├── AuthState.kt
│   └── AuthUser.kt
├── data/
│   ├── auth/
│   │   └── SessionStore.kt
│   ├── database/
│   │   ├── DatabaseFactory.kt
│   │   ├── MoviesDao.kt
│   │   ├── MoviesDatabase.kt
│   │   └── UserEntity.kt
│   ├── dto/
│   │   ├── AuthDto.kt
│   │   └── SyncDto.kt
│   ├── network/
│   │   ├── AuthRemoteApi.kt
│   │   └── AuthRemoteApiImpl.kt
│   └── repository/
│       └── AuthRepositoryImpl.kt
├── di/
│   ├── CoreModule.kt
│   ├── NetworkModule.kt
│   └── RepositoryModule.kt
└── presentation/
    ├── auth/AuthRequiredDialog.kt
    ├── login/{LoginActivity.kt,LoginContract.kt,LoginScreen.kt,LoginViewModel.kt}
    ├── splash/{SplashActivity.kt,SplashViewModel.kt}
    ├── favorite/{FavoriteMoviesScreen.kt,FavoriteMoviesViewModel.kt}
    ├── movies_detail/{MovieDetailScreen.kt,MovieDetailViewModel.kt}
    ├── movies_list/{MoviesListScreen.kt,MoviesListViewModel.kt}
    ├── seat_selection/{SeatSelectionScreen.kt,SeatSelectionViewModel.kt}
    ├── payment/{PaymentScreen.kt,PaymentViewModel.kt}
    └── settings/{SettingsScreen.kt,SettingsViewModel.kt}

app/src/test/java/me/ibrahim/moviesapp/compose/
├── data/auth/
├── data/repository/
└── presentation/

app/src/androidTest/java/me/ibrahim/moviesapp/compose/
├── data/database/
└── presentation/login/

backend/
├── app.py
├── auth.py
├── requirements.txt
├── test_app.py
└── test_auth.py
```

**Structure Decision**: 保留现有单 `app` Android 模块和单 Flask 服务，新增独立认证领域与数据组件，不拆分新的 Gradle 模块。后端从 `app.py` 抽出无状态认证辅助逻辑到 `auth.py`，路由与应用工厂仍在 `app.py`，以控制本阶段复杂度。

## Architecture & Flow

1. `AuthRemoteApi` 调用注册、登录、当前会话和退出接口；所有结果映射为 Domain `AuthError`，UI 不解析 HTTP 状态。
2. `AuthRepositoryImpl` 是客户端认证状态唯一入口，持有只读 `StateFlow<AuthState>`，协调远程验证、Keystore 会话存储和 Room 账号资料缓存。
3. 启动时 `SplashViewModel` 调用 `restoreSession()`：没有令牌直接进入游客浏览，有令牌则调用当前会话接口；无效令牌被清除，网络失败不得伪装成有效登录。
4. 注册/登录成功后，服务端返回一次可见的不透明令牌；客户端加密保存，后端仅保存令牌 SHA-256 摘要及到期/撤销状态。
5. `AuthRequiredDialog` 与 `PendingAuthAction` 统一处理收藏、个性化推荐、选座和购票门禁。推荐展示独立于认证门禁：游客、无收藏或推荐失败时使用热门/正在上映内容并标注非个性化来源；个性化结果标注“根据你的收藏推荐”；无电影数据时显示明确空状态和重试。登录成功只返回来源上下文，原操作由用户明确继续，不自动收藏、锁座或下单。
6. 退出时先尝试服务端撤销，再无条件结束本地认证状态并清理当前账号私有内存状态；服务端不可达时把令牌移入加密待撤销队列而非活动会话，后续联网重试。退出后进入登录页面，用户仍可选择以访客身份浏览。
7. `/sync/push` 与 `/sync/pull` 改为 Bearer 认证并从会话推导账号，删除路径/负载中可伪造的账号身份和所有密码字段。

## Migration Strategy

- **Backend**: 在 `initialize_database()` 中以幂等迁移创建 `user_accounts`、`auth_sessions`、`auth_throttle` 和 schema metadata。若旧 `user_sync_data.payload` 含邮箱与密码，则在本地升级时将密码转换为强哈希建立账号，随后原子更新 JSON 以移除密码；无法迁移的记录保留业务数据但不产生可登录账号。
- **Android Room 7→8**: 重建 `UserAccount` 表，保留邮箱、姓名和头像，新增可空服务端账号标识（下次成功认证时补齐），删除 `password` 与 `isLoggedIn`；保留收藏、订单、评分、评论和点赞表。移除 `fallbackToDestructiveMigration()` 并注册显式迁移。
- **Session**: 旧 `isLoggedIn` 标记不升级为真实会话。升级后用户需要首次重新登录，之后使用安全令牌恢复。
- **Rollback**: 迁移前备份本地开发数据库；新代码不会向旧密码字段回写。回滚到旧版本只允许使用备份，避免重新引入明文凭据。

## Post-Design Constitution Check

- 分层、服务端权威、密码与令牌安全、明确状态、字符串资源、迁移和测试要求均已映射到数据模型、接口契约、验证指南和任务生成输入。
- 推荐来源说明与游客、无收藏、服务失败时的非个性化备用内容已纳入设计和验收，符合宪章第四原则。
- debug 本机 HTTP 仅作为开发例外记录在运行指南中；release 构建必须使用 HTTPS 并禁用 cleartext，否则不得交付。
- 未发现需要记录在 Complexity Tracking 中的宪章违反。

**Post-design gate result**: PASS。
