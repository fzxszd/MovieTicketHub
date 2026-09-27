# Data Model: 账号认证与会话

## Backend Entities

### UserAccount

服务端权威普通用户账号。

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID string | 主键，创建后不可变 |
| `email_normalized` | string | 唯一、非空、去除首尾空格并转小写 |
| `email_display` | string | 用户提交邮箱的规范展示值，非空 |
| `username` | string | 去除首尾空格后 2–30 个可见字符 |
| `password_hash` | string | 强密码哈希；禁止返回客户端或写入日志 |
| `status` | enum | `active`、`disabled`；本功能只创建 `active` |
| `created_at` | UTC timestamp | 创建时写入，不可为空 |
| `updated_at` | UTC timestamp | 资料或密码哈希变化时更新 |

**Relationships**: 一个账号拥有零到多个 `AuthSession`，并关联一份现有的用户同步数据。

### AuthSession

可撤销的设备登录会话。

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID string | 主键 |
| `user_id` | UUID string | 外键指向 `UserAccount.id`，非空 |
| `token_hash` | hex string | 原始高熵令牌的 SHA-256 摘要，唯一；原始令牌不落库 |
| `created_at` | UTC timestamp | 会话建立时间 |
| `expires_at` | UTC timestamp | 默认创建后 30 天，必须晚于 `created_at` |
| `revoked_at` | UTC timestamp/null | 退出或管理员撤销时写入 |
| `last_used_at` | UTC timestamp | 成功验证会话时更新，不能延长绝对到期时间 |

**Valid when**: 当前时间早于 `expires_at`、`revoked_at` 为空、所属账号状态为 `active`。

### AuthThrottle

登录失败保护记录。

| Field | Type | Rules |
|-------|------|-------|
| `key` | string | 规范化邮箱与来源地址形成的不可歧义键，主键 |
| `failure_count` | integer | 当前窗口失败次数，不小于 0 |
| `window_started_at` | UTC timestamp | 当前统计窗口开始时间 |
| `blocked_until` | UTC timestamp/null | 达到阈值后的临时阻断截止时间 |

**Lifecycle**: 成功登录清除该邮箱相关失败记录；窗口到期后重新计数；阻断响应不得暴露账号是否存在。

## Android Domain Models

### AuthUser

| Field | Type | Rules |
|-------|------|-------|
| `id` | string | 服务端稳定账号 ID，非空 |
| `email` | string | 规范邮箱，非空 |
| `username` | string | 2–30 个可见字符 |
| `avatarUri` | string/null | 可选公开资料，不含认证秘密 |

### AuthState

```text
Checking
  ├── no token ───────────────> Guest
  ├── valid token ────────────> Authenticated(AuthUser)
  ├── expired/revoked token ──> Guest (clear local token)
  └── temporary network error ─> RecoverableError

Guest
  ├── register/login success ─> Authenticated(AuthUser)
  └── public browsing ────────> Guest

Authenticated
  ├── logout ────────────────> Guest
  ├── session rejected ──────> Guest (clear local token)
  └── transient request error > Authenticated (do not invent a new session)
```

### LoginUiState

| Field | Type | Rules |
|-------|------|-------|
| `mode` | enum | `login` 或 `register` |
| `username` | string | 注册模式必填 |
| `email` | string | 登录和注册必填 |
| `password` | string | 仅内存保存，离开页面时丢弃 |
| `confirmPassword` | string | 仅注册模式必填，仅内存保存 |
| `fieldErrors` | map | 按字段展示可纠正错误 |
| `submitState` | enum | `idle`、`submitting`、`success`、`error` |
| `generalError` | error/null | 凭据、网络、限流或服务错误 |

### PendingAuthAction

| Variant | Required context | Resume behavior |
|---------|------------------|-----------------|
| `FavoriteMovie` | movie ID | 返回电影上下文，由用户再次确认收藏 |
| `ViewRecommendations` | 来源页面 | 返回来源，重新请求个性化推荐 |
| `SelectSeats` | movie/showtime identifiers | 返回场次，用户重新进入选座 |
| `Purchase` | order draft identifier | 返回确认页，用户再次确认，不自动下单 |

不得包含密码、令牌、完整支付信息或已经生效的业务操作。

## Android Persistence

### UserEntity (Room v8)

| Field | Type | Rules |
|-------|------|-------|
| `email` | string | 主键，规范邮箱 |
| `userId` | string/null | 服务端账号 ID；旧数据迁移时允许为空，下一次成功认证后必须补齐；非空值唯一 |
| `name` | string | 展示用户名 |
| `avatarUri` | string/null | 可选 |

`password` 与 `isLoggedIn` 在 v8 删除。当前登录身份来自 `AuthRepository` 和有效远程会话，而不是 Room 行。

### SessionEnvelope (encrypted private storage)

| Field | Type | Rules |
|-------|------|-------|
| `ciphertext` | bytes/base64 | AES/GCM 加密后的原始令牌 |
| `iv` | bytes/base64 | 每次写入随机生成，不得复用 |
| `expiresAt` | UTC instant | 用于启动前快速识别显然过期的令牌 |
| `userIdHint` | string/null | 非权威提示，不可用于授权 |

退出时若服务端不可达，活动令牌必须从 `SessionEnvelope` 移入独立的加密待撤销队列；队列中的令牌不能恢复为登录会话，只用于后续向服务端补偿撤销。

## Migration Rules

1. 后端迁移先创建新表，再逐条读取旧同步 payload。
2. 有有效邮箱和 1–64 字符非空旧密码的记录转换为 `UserAccount` 强哈希；相同规范邮箱只生成一个账号。旧名称清理后若不满足 2–30 字符，使用规范邮箱本地部分生成合规展示名。
3. 无论能否生成账号，写回同步 payload 时都删除 `password` 字段；迁移事务失败时全部回滚。
4. Android Room 7→8 新建无密码的临时用户表，复制邮箱、姓名、头像并把旧记录的 `userId` 设为空，替换旧表并创建仅约束非空值的唯一索引。
5. 旧 `isLoggedIn` 不产生 `SessionEnvelope`；升级后的首次访问按游客处理，直到远程登录成功。
6. 收藏、订单、评分、评论和点赞数据必须保留，并继续按邮箱隔离。
