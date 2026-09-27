# Implementation Plan: 订单创建与支付

**Branch**: `004-order-payment` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-order-payment/spec.md`

## Summary

把当前客户端延时 1.5 秒后直接显示“支付成功”并写入 Room 的模拟流程，替换为后端权威订单与支付状态机。Android 只传稳定 `lockId`/`orderId`；服务端根据 003 的有效锁生成订单报价，用整数分重新计算金额并以幂等事务创建唯一待支付订单。支付通过可替换 `PaymentProvider` 接口完成，本阶段默认实现可控的本地模拟渠道；只有经过签名验证或服务端主动查询得到的权威结果，才能原子地更新支付、订单、座位和唯一电影票。

## Technical Context

**Language/Version**: Kotlin 2.1.0（Android，JVM target 1.8）；Python 3.12（后端）  
**Primary Dependencies**: Jetpack Compose、Navigation Compose 2.8.6、Ktor 3.0.3、Koin 4.0.2、kotlinx.serialization 1.8.0、Flask 3.0.3；Python 标准库 HMAC/SQLite  
**Storage**: 后端 SQLite（订单、订单项目、支付尝试、支付事件、电影票、审计事件）；客户端不保存权威订单  
**Testing**: Python `unittest` + Flask test client + 并发测试；JUnit 4；Compose UI Test  
**Target Platform**: Android API 24–35；Flask HTTP API  
**Project Type**: Android 移动应用 + Python API 服务  
**Performance Goals**: 支付五类状态在已知或处理中结果到达后 5 秒内显示；100 次重复/并发测试零重复订单、扣款语义或票据  
**Constraints**: 依赖 001 会话、002 场次和 003 有效锁；金额为整数最小单位；支付截止不晚于锁截止；客户端永不决定成功；本阶段不接真实资金渠道  
**Scale/Scope**: 单用户数十笔订单；每订单 1–6 个座位；4 个用户故事、订单/支付/票据 API 与 3 个主要 Compose 页面

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

| Principle | Result | Evidence |
|---|---|---|
| I. 分层架构与单向数据流 | PASS | 独立 OrderRepository、DTO/API/Mapper、各页面 Contract/ViewModel；UI 不写 Room 或推断交易状态。 |
| II. 购票与座位状态必须正确 | PASS | 订单创建、支付结果、锁转换、座位售出和票据生成均由后端幂等事务控制。 |
| III. 账号、隐私与安全优先 | PASS（有前置依赖） | 私有端点全部使用 001 Bearer 会话并按 userId 隔离；支付事件验签；敏感字段禁止日志。 |
| IV. 推荐规则 | PASS（不适用） | 不处理推荐。 |
| V. 可恢复体验与明确状态 | PASS | 明确待支付、处理中、已支付、失败、取消、过期和未知恢复状态；禁止客户端假成功。 |
| 技术与业务约束 | PASS | 后端为权威源；金额整数分；环境密钥不进仓库；迁移为附加式。 |
| 质量门槛 | PASS | 幂等、并发、恢复、跨账号、票据唯一性和构建测试均列入任务。 |

### Post-design gate

| Principle | Result | Design confirmation |
|---|---|---|
| 分层与数据流 | PASS | `data-model.md` 与契约以 orderId/lockId 关联，Android 只渲染服务端状态。 |
| 高一致性交易 | PASS | 支付成功事务同时写订单、支付尝试、锁/座位和唯一票据；事件唯一约束防止重复。 |
| 安全与隐私 | PASS | Webhook 使用独立签名安全方案；用户端使用 Bearer；跨账号统一 404，审计记录脱敏。 |
| 可恢复体验 | PASS | `PENDING/UNKNOWN` 通过查询恢复，应用重启只按订单 ID重新获取，不重复发起支付。 |
| 测试与构建 | PASS | quickstart 和 tasks 覆盖 7 条 SC、20 条 FR 及异常中断点。 |

无宪章例外；发布前必须依次完成 001、002、003。

## Architecture and Transaction Flow

1. 订单确认页用 `lockId` 请求权威 `OrderQuote`，展示电影、影院、影厅、场次、座位、逐项价格、费用、货币、总额和报价版本。
2. 客户端确认时发送 `lockId`、`quoteVersion` 与 `Idempotency-Key`。服务端事务重新校验会话、锁归属/状态/截止、场次状态和金额；报价变化返回最新报价并要求再次确认。
3. 同一 lockId 最多一个有效订单；重复键或重复确认返回原订单。`paymentDeadline = min(lock.expiresAt, configured deadline)`。
4. 发起支付前再次校验订单和锁，创建唯一 `PaymentAttempt` 并调用 `PaymentProvider`。默认模拟 provider 只用于开发/测试，可确定地产生 SUCCESS/FAILED/CANCELLED/PENDING。
5. 客户端返回只触发订单查询，不代表成功。PENDING/UNKNOWN 显示处理中并周期查询；禁止用新幂等键盲目重复支付。
6. 权威 provider 结果经签名 webhook 或服务端 provider 查询进入统一处理器。成功处理在一个 `BEGIN IMMEDIATE` 事务中幂等地更新尝试和订单、把 003 锁转 CONVERTED、座位转 SOLD，并插入唯一 Ticket。
7. 失败/取消保留可追溯尝试；若订单和锁仍有效可重新创建一次支付尝试。订单到期则 EXPIRED 并调用 003 释放未购买座位。
8. 订单列表/详情全部从后端恢复；只有 PAID 订单返回有效票据。票据展示失败可重试读取，不得重新付款。

## Payment Provider Boundary

- 定义 `PaymentProvider.create_payment()`、`query_payment()`、`verify_webhook()` 接口，业务层不依赖具体渠道字段。
- 开发默认 `SimulatedPaymentProvider`，通过仅测试环境允许的场景码控制结果；UI 不可直接设置订单成功。
- 生产 provider 密钥、webhook secret 和地址只能来自环境配置；日志不得包含完整凭据、Bearer、银行卡/钱包信息或原始回调正文。
- provider transaction reference、provider event ID 和 client idempotency key 均设置唯一约束。
- 对“客户端超时但渠道后续成功”，订单维持 PAYMENT_PROCESSING；权威成功仍可完成交易。若锁已到期且座位未售，则进入 `REVIEW_REQUIRED` 审计路径，不静默丢弃资金结果。

## Failure Recovery and Audit

- 任何客户端 5xx/超时都视为结果未知；用相同幂等键查询/重试，并优先 GET order。
- 应用重启通过用户订单列表和 orderId 恢复；本地 Route 不携带金额、状态或票据事实。
- 每次订单/支付状态转换写 `status_events`：entity type/id、from/to、reason code、correlation ID、UTC 时间；不记录敏感正文。
- 订单到期在读写路径按服务端时间即时生效并幂等释放座位，后台清理仅作为补充。
- 票据行以 orderId 唯一；若二维码/展示 payload 生成暂时失败，订单仍 PAID，票据状态为 GENERATING/READY/FAILED_RETRYABLE，可安全重试生成。

## Migration and Rollback

- 附加、幂等创建 `orders`、`order_items`、`payment_attempts`、`payment_events`、`tickets`、`status_events`，不把现有 Room `MovieOrder` 迁移为权威交易。
- Android 切换后停止 `PaymentViewModel.saveOrder()` 和 `MoviesRepository.insertOrder()` 作为购买成功路径；旧本地记录可只读展示或在明确迁移策略后删除。
- 回滚客户端不会撤销服务端已支付订单；服务回滚必须保留表和 webhook 接收/查询能力，避免丢失在途支付结果。

## Verification Strategy

- 后端：报价/金额差异、唯一订单、支付前复核、所有结果状态、100 次重复/并发、webhook 验签、原子售座/票据、超时释放、跨账号和审计脱敏。
- Android：确认状态、价格变化重新确认、防重复点击、UNKNOWN 恢复、应用重启、订单列表/详情、只有 PAID 展示票据。
- Compose：完整金额/座位信息、支付方法与同意、处理中/失败/取消/过期操作、无障碍与资源字符串。
- 最终执行后端测试、Android 单测/构建/UI 测试及 quickstart 人工场景。

## Project Structure

### Documentation

```text
specs/004-order-payment/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/order-payment-api.yaml
└── tasks.md
```

### Source Code

```text
backend/
├── app.py
├── orders.py
├── payments.py
├── test_orders.py
└── test_payments.py

app/src/main/java/me/ibrahim/moviesapp/compose/
├── core/{Routes.kt,MoviesNavGraph.kt}
├── data/{dto/OrderDto.kt,mappers/OrderMapper.kt,network/OrderRemoteApi.kt,repository/OrderRepositoryImpl.kt}
├── domain/order/{Order.kt,OrderRepository.kt,PaymentAttempt.kt,Ticket.kt}
├── di/{CoreModule.kt,NetworkModule.kt,RepositoryModule.kt}
└── presentation/
    ├── order_confirmation/{OrderConfirmationContract.kt,OrderConfirmationScreen.kt,OrderConfirmationViewModel.kt}
    ├── payment/{PaymentContract.kt,PaymentScreen.kt,PaymentViewModel.kt}
    └── orders/{OrdersContract.kt,OrdersScreen.kt,OrdersViewModel.kt,OrderDetailScreen.kt}

app/src/test/java/me/ibrahim/moviesapp/compose/{data/OrderMapperTest.kt,presentation/order/OrderViewModelTest.kt}
app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/order/OrderFlowScreenTest.kt
```

**Structure Decision**: 保留单 Android app 与单 Flask 服务。后端以 `orders.py` 管订单事务、`payments.py` 隔离渠道；Android 使用独立 OrderRepository，不继续扩张 MoviesRepository。

## Complexity Tracking

无宪章豁免。模拟支付渠道是开发阶段可验证边界，不代表真实资金接入；生产接入前必须另行安全与合规评审。
