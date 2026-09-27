# Research: 账号认证与会话

## Decision 1: 服务端权威认证

**Decision**: 注册、登录、会话验证和退出全部由 Flask 后端完成；Android 不再通过 Room 中的密码判断身份。

**Rationale**: 当前客户端本地比较密码并把明文密码同步到服务器，无法可靠授权跨设备数据，也违反项目宪章。服务端权威可以统一邮箱唯一性、会话撤销、登录保护和私有数据访问。

**Alternatives considered**:

- 继续本地认证：实现简单，但任何拥有数据库文件的人都能读取凭据，且无法安全保护后端接口，拒绝。
- 使用第三方身份平台：能力完整，但超出当前本地课程项目范围并引入外部账号依赖，暂不采用。

## Decision 2: 可撤销的不透明会话令牌

**Decision**: 登录/注册成功返回高熵随机令牌；客户端持有原始令牌，服务端只保存 SHA-256 摘要、用户、到期时间和撤销时间。令牌默认绝对有效期为 30 天，不采用滑动续期。

**Rationale**: 不透明令牌易于立即撤销，服务端可直接控制会话，适合单体 Flask + SQLite。只保存摘要能降低数据库泄漏后令牌被直接复用的风险。

**Alternatives considered**:

- JWT：可无状态验证，但即时退出和撤销需要额外黑名单或短期令牌/刷新令牌体系，本阶段复杂度更高。
- 永久设备令牌：体验简单，但泄漏后的风险窗口不可接受。

## Decision 3: 密码使用 Werkzeug 强哈希

**Decision**: 使用 Flask 已依赖的 Werkzeug `generate_password_hash` / `check_password_hash`，选择其受支持的强内存哈希默认方案；数据库只保存哈希字符串。

**Rationale**: 无需自制密码算法或额外维护密码学依赖，哈希格式包含参数和随机盐，便于未来升级。

**Alternatives considered**:

- 可逆加密密码：服务端必须持有解密密钥，数据库与密钥同时泄漏会暴露全部密码，拒绝。
- 直接 SHA-256：速度过快且不适合密码抵抗暴力破解，拒绝。

## Decision 4: Android Keystore 加密会话存储

**Decision**: `SessionStore` 使用 Android Keystore 中不可导出的 AES/GCM 密钥加密令牌，密文、IV、到期时间和账号摘要保存在应用私有偏好文件；密码从不持久化。

**Rationale**: 支持 minSdk 24，不依赖已废弃的高级封装 API；会话存储与 Room 业务缓存分离，清除或轮换更明确。

**Alternatives considered**:

- 明文 SharedPreferences/Room：令牌可直接读取，违反宪章。
- 每次启动重新输入密码：安全但违反有效会话自动恢复需求。

## Decision 5: 认证状态采用单一状态机

**Decision**: Domain 暴露 `AuthState`：`Checking`、`Guest`、`Authenticated(user)`、`RecoverableError`。注册和登录表单另有不可变 `LoginUiState` 表达模式、字段、提交中和错误。

**Rationale**: 可以避免多个 `isLoggedIn` 布尔值不同步，使启动恢复、退出和受保护操作都观察同一来源；符合 MVI 和单向数据流。

**Alternatives considered**:

- 各 ViewModel 自己读取 Room 登录标志：容易产生竞态和跨页面状态漂移，拒绝。

## Decision 6: 受保护操作显式续接

**Decision**: 未登录用户触发收藏、推荐、选座或购票时，记录仅包含类型和导航参数的 `PendingAuthAction`；登录成功返回来源，用户明确再次确认原操作。不得保存密码，也不得在登录后自动产生收藏、锁座或订单。

**Rationale**: 满足“返回原业务上下文”同时避免用户不知情的副作用，尤其是锁座和交易相关操作。

**Alternatives considered**:

- 登录成功后自动执行全部原操作：对收藏尚可，但对选座/购票可能产生意外状态，拒绝。
- 登录后总是回首页：安全但破坏上下文和验收场景。

## Decision 7: 明文密码数据显式迁移并删除

**Decision**: 后端对旧同步 payload 中 1–64 字符的非空密码执行一次性哈希导入并原子移除明文字段；迁移用户名取清理后的旧名称，若不满足 2–30 字符则使用规范邮箱本地部分生成合法展示名。Android Room 7→8 只迁移公开账号资料，删除密码和本地登录标志。旧客户端升级后需要首次重新登录。

**Rationale**: 在保留现有演示账号和业务数据的同时终止明文密码传播。不能把旧 `isLoggedIn` 视作服务端会话。

**Alternatives considered**:

- 破坏性清库：简单但会删除收藏和订单，违反迁移要求。
- 保留密码列以兼容旧代码：持续扩大安全风险，拒绝。

## Decision 8: 持久化登录保护

**Decision**: 后端按规范化邮箱和来源地址维护失败窗口；15 分钟内累计 5 次失败后阻断 15 分钟。响应对不存在账号和错误密码保持一致，并返回统一的剩余等待提示。

**Rationale**: 满足连续失败登录保护，并避免服务重启立即清空保护状态或通过响应差异枚举账号。

**Alternatives considered**:

- 仅客户端限制：可被绕过，拒绝。
- 仅进程内计数：服务重启会丢失，不适合可重复验收。

## Decision 9: 传输安全按环境分离

**Decision**: 本地 Android 模拟器到开发机允许 debug 专用 HTTP；release 配置必须使用 HTTPS 地址并拒绝 cleartext。认证请求和响应日志必须禁用正文记录。

**Rationale**: 兼顾本地课程环境可运行性与宪章的生产安全要求，避免把开发例外带入交付构建。

**Alternatives considered**:

- 所有环境允许 cleartext：密码和令牌可被窃听，拒绝。
- 本地自签名 HTTPS：更接近生产，但证书分发和信任配置对当前课程项目成本较高。
