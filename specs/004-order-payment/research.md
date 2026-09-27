# Research: 订单创建与支付

## 1. 支付渠道选择

**Decision**: 定义可替换 `PaymentProvider`，本阶段使用后端可控的 `SimulatedPaymentProvider`，不接真实资金渠道。

**Rationale**: 规格要求权威成功/失败语义，但当前项目没有商户账号、密钥或合规环境。模拟适配器可完整验证幂等、异步结果和恢复，又不会造成真实扣款风险。

**Alternatives considered**: 客户端延时模拟不权威；直接接支付宝/微信需要商户配置、安全评审和平台 SDK，不应由文档阶段假设完成。

## 2. 订单报价与金额重确认

**Decision**: 先以 lockId 获取带 `quoteVersion` 的权威订单报价，再创建订单；服务端创建时重新计算，变化则返回最新报价并阻止创建。

**Rationale**: 用户必须先看到完整金额，同时 FR-004 要求金额变化时再次确认。版本化报价能区分用户同意的金额与最新金额。

**Alternatives considered**: 客户端从锁自行求和缺少费用规则；直接创建再提示差异会产生用户未同意的订单。

## 3. 订单与支付幂等

**Decision**: 创建订单和发起支付分别要求 Idempotency-Key，并以用户+键唯一；lockId 对有效订单唯一，provider event/transaction reference 也唯一。

**Rationale**: 移动网络重试、连续点击和重复 webhook 可能同时发生，多层唯一约束比单纯按钮禁用可靠。

**Alternatives considered**: 仅客户端防抖不能处理网络重试；只按 orderId 去重无法识别重复支付尝试语义。

## 4. 支付结果权威性

**Decision**: 只有验签 webhook 或服务端主动查询 provider 得到的状态可改变交易结果；客户端返回页只触发查询。

**Rationale**: 客户端状态可伪造、丢失或重复。服务端确认满足宪章和 FR-009。

**Alternatives considered**: 客户端 success callback 直接置 PAID 存在伪造和假成功风险。

## 5. 原子成功转换

**Decision**: 在单个 `BEGIN IMMEDIATE` 事务中消费唯一支付事件、更新 PaymentAttempt/Order、把锁转 CONVERTED、座位转 SOLD 并插入唯一 Ticket。

**Rationale**: 支付成功不能留下“扣款成功但座位未售”或重复票据。SQLite 写事务与约束能保证当前部署的一致性。

**Alternatives considered**: 分步骤提交会产生部分成功；客户端补偿不可在崩溃后可靠执行。

## 6. 未知与迟到支付结果

**Decision**: 超时映射为 PROCESSING/UNKNOWN，客户端轮询订单；迟到成功正常幂等处理。若锁/座位已无法转换，记录 `REVIEW_REQUIRED`，保留权威资金记录并阻止自动成功票据。

**Rationale**: 未知不等于失败，盲目重付可能重复扣款；但资金成功与库存不可用也不能静默忽略。

**Alternatives considered**: 超时立即失败会误导用户；迟到成功强行售座可能重复售票。

## 7. 订单到期

**Decision**: `paymentDeadline` 不晚于 seat lock `expiresAt`。读写路径按服务端时间即时把无成功支付的订单置 EXPIRED，并调用 003 释放座位；清理任务仅补充。

**Rationale**: 不依赖后台任务存活，并能让用户查询时立即看到权威状态。

**Alternatives considered**: 仅客户端倒计时可被修改；仅定时任务可能延迟释放。

## 8. 票据生成

**Decision**: Ticket 以 orderId 唯一，支付成功事务先创建票据记录；展示凭证可异步从 GENERATING 到 READY，失败为 FAILED_RETRYABLE，不回滚 PAID。

**Rationale**: 票据展示故障不应诱导重复付款，同时必须保证未支付订单永远没有有效票据。

**Alternatives considered**: 同步生成全部展示内容会扩大支付事务失败面；失败时回滚支付状态与权威渠道结果冲突。

## 9. 私有资源与审计

**Decision**: 所有用户 API 从 Bearer 会话派生 userId，跨账号订单统一 404；状态事件记录脱敏 ID、前后状态、reason、correlationId 和时间。

**Rationale**: 既防止枚举他人订单，又满足问题调查和幂等追踪，不保存支付凭据。

**Alternatives considered**: body 传 email 可冒充；记录完整回调方便调试但违反数据最小化。

## 10. Android 恢复模型

**Decision**: 导航只传 lockId 或 orderId；ViewModel 每次进入从 OrderRepository 获取权威状态，PROCESSING 每 3 秒查询并在后台恢复时立即刷新。

**Rationale**: Route 中的金额/状态会过期；服务端 ID 能恢复应用被关闭后的准确状态。

**Alternatives considered**: Room 订单作为事实会与支付结果漂移；保存完整 DTO 到导航参数难以版本兼容。
