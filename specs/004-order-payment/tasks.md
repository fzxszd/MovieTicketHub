# Tasks: 订单创建与支付

**Input**: `/specs/004-order-payment/` 设计文档  
**Prerequisites**: 001 安全会话、002 场次、003 座位锁定，以及本目录 `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/`

**Tests**: 宪章要求交易状态、幂等和跨层行为具有自动化验证；各故事测试必须先写并先失败。

## Phase 1: Setup

- [ ] T001 创建后端订单/支付服务与测试骨架 `backend/orders.py`、`backend/payments.py`、`backend/test_orders.py`、`backend/test_payments.py`
- [ ] T002 [P] 创建 Android Order Domain 与 Repository 骨架 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/order/Order.kt`、`PaymentAttempt.kt`、`Ticket.kt`、`OrderRepository.kt`
- [ ] T003 [P] 创建 Android DTO/Mapper/API/Repository 骨架 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/OrderDto.kt`、`data/mappers/OrderMapper.kt`、`data/network/OrderRemoteApi.kt`、`data/repository/OrderRepositoryImpl.kt`
- [ ] T004 [P] 添加订单、支付、票据、错误、处理中和无障碍中英文资源到 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh/strings.xml`

## Phase 2: Foundational

**⚠️ CRITICAL**: 先确认 001→002→003 均已实现，本阶段完成前不得开始故事代码。

- [ ] T005 验证 001 Bearer 用户、002 场次状态和 003 SeatLock/ShowtimeSeat 服务边界，在 `backend/auth.py`、`backend/showtimes.py`、`backend/seats.py` 与 `backend/orders.py` 禁止客户端 userId、金额或显示名称成为权威输入
- [ ] T006 在 `backend/app.py` 添加幂等迁移，创建 orders、order_items、payment_attempts、payment_events、tickets、status_events 及外键/索引
- [ ] T007 在 `backend/orders.py` 实现整数分 Money、UTC 时钟注入和状态转换守卫，保证 `totalMinor == subtotalMinor + feeMinor` 且 `paymentDeadline <= lock.expiresAt`
- [ ] T008 在 `backend/orders.py` 实现脱敏 StatusEvent 写入，字段仅含实体/前后状态/reason/correlationId/UTC 时间，不含凭据或原始回调
- [ ] T009 [P] 在 `backend/payments.py` 定义 PaymentProvider 接口和环境配置读取，密钥/地址不得硬编码或记录
- [ ] T010 在 `backend/payments.py` 实现仅开发测试环境可用的 SimulatedPaymentProvider，支持 SUCCESS/FAILED/CANCELLED/PENDING 且客户端不能直接置成功
- [ ] T011 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/order/Order.kt` 实现七种订单状态、Money、1–6 项快照和 allowedActions
- [ ] T012 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/order/PaymentAttempt.kt` 与 `Ticket.kt` 实现支付/票据状态，未 PAID 订单不得映射 READY credential
- [ ] T013 在 `app/src/main/java/me/ibrahim/moviesapp/compose/domain/order/OrderRepository.kt` 定义报价、创建订单、发起支付、列表、详情与票据接口
- [ ] T014 [P] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/dto/OrderDto.kt` 与 `data/mappers/OrderMapper.kt` 实现契约 DTO、未知状态拒绝、整数金额与 UTC 映射
- [ ] T015 在 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/OrderRemoteApi.kt` 与 `data/repository/OrderRepositoryImpl.kt` 实现 Bearer、16–128 字符 Idempotency-Key、401/404/409/5xx 和结果未知映射
- [ ] T016 在 `app/src/main/java/me/ibrahim/moviesapp/compose/di/NetworkModule.kt`、`RepositoryModule.kt` 与 `CoreModule.kt` 注册 Order API、Repository 和三个订单相关 ViewModel

## Phase 3: User Story 1 - 确认订单并创建待支付订单 (P1)

**Goal**: 从当前用户有效锁生成权威报价并幂等创建唯一待支付订单。

**Independent Test**: 有效锁可创建一笔明细/金额正确的订单；过期/他人锁拒绝；报价变化重新确认；重复提交不重复。

- [ ] T017 [P] [US1] 先编写锁归属/ACTIVE/截止/场次校验、1–6 项快照、整数金额和报价版本测试到 `backend/test_orders.py`
- [ ] T018 [P] [US1] 先编写同一 lockId 唯一订单、相同幂等键返回原订单、报价变化 AMOUNT_CHANGED 和事务回滚测试到 `backend/test_orders.py`
- [ ] T019 [P] [US1] 先编写 OrderQuote/OrderDetail DTO 映射和未知状态测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/data/OrderMapperTest.kt`
- [ ] T020 [P] [US1] 先编写报价加载、完整明细、金额变化重新确认、防重复和会话失效的 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/order/OrderConfirmationViewModelTest.kt`
- [ ] T021 [P] [US1] 先编写电影/影院/影厅/场次/座位/单价/费用/货币/总额展示和确认状态 UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/order/OrderConfirmationScreenTest.kt`
- [ ] T022 [US1] 在 `backend/orders.py` 实现从有效 SeatLock 和 002 场次生成 OrderQuote、服务端费用/总额计算及 quoteVersion
- [ ] T023 [US1] 在 `backend/orders.py` 用 `BEGIN IMMEDIATE` 实现订单创建：重算报价、校验 acceptedQuoteVersion、唯一 seatLockId/幂等键、PENDING_PAYMENT 与支付截止
- [ ] T024 [US1] 在 `backend/app.py` 实现 `GET /v1/seat-locks/{lockId}/order-quote` 和 `POST /v1/orders` 的 Bearer、201/200/409 契约
- [ ] T025 [US1] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/order_confirmation/OrderConfirmationContract.kt` 定义不可变 Loading/Content/Creating/AmountChanged/Error 状态与事件/effect
- [ ] T026 [US1] 实现 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/order_confirmation/OrderConfirmationViewModel.kt`，每次确认使用一次并复用订单幂等键，金额变化必须等待再次确认
- [ ] T027 [US1] 实现 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/order_confirmation/OrderConfirmationScreen.kt`，显示全部权威明细、截止时间、变化差异和防重复确认
- [ ] T028 [US1] 更新 `app/src/main/java/me/ibrahim/moviesapp/compose/core/Routes.kt` 与 `MoviesNavGraph.kt`，从 003 只传 lockId 到确认页，再只传 orderId 到支付页
- [ ] T029 [US1] 移除 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentScreen.kt` 对导航 price 字符串的依赖，支付页改由 orderId 加载权威金额

## Phase 4: User Story 2 - 安全完成支付 (P1)

**Goal**: 幂等发起支付，仅根据权威渠道结果原子完成订单、售座和票据，正确处理失败/取消/未知。

**Independent Test**: 五类结果、重复提交/通知和并发查询 100 次，零重复交易效果、订单成功转换或票据。

- [ ] T030 [P] [US2] 先编写支付前订单归属/状态/场次/锁/金额复核，以及失败/取消后锁有效时可重试测试到 `backend/test_payments.py`
- [ ] T031 [P] [US2] 先编写相同支付幂等键、provider transaction/event 唯一、重复 webhook 和查询并发 100 次测试到 `backend/test_payments.py`
- [ ] T032 [P] [US2] 先编写成功事务原子更新 PaymentAttempt/Order/SeatLock/ShowtimeSeat/Ticket，任一步失败完整回滚的测试到 `backend/test_payments.py`
- [ ] T033 [P] [US2] 先编写 webhook 签名拒绝、PENDING/UNKNOWN 恢复、迟到成功和 REVIEW_REQUIRED 测试到 `backend/test_payments.py`
- [ ] T034 [P] [US2] 先编写支付方法、单次提交、PROCESSING 轮询、五类状态和不根据客户端返回判成功的 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentViewModelTest.kt`
- [ ] T035 [P] [US2] 先编写最终金额同意、支付中禁用、成功/失败/取消/未知/过期反馈和可用操作 UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentScreenTest.kt`
- [ ] T036 [US2] 在 `backend/payments.py` 实现支付前权威复核、PaymentAttempt 创建、provider 调用和同键返回原尝试
- [ ] T037 [US2] 在 `backend/payments.py` 实现 HMAC webhook 验签、唯一事件消费和服务端主动查询统一结果处理器
- [ ] T038 [US2] 在 `backend/payments.py` 与 `backend/seats.py` 实现成功 `BEGIN IMMEDIATE` 事务：订单 PAID、锁 CONVERTED、座位 SOLD、唯一 Ticket 和审计事件
- [ ] T039 [US2] 在 `backend/payments.py` 实现 FAILED/CANCELLED/PENDING/UNKNOWN/REVIEW_REQUIRED 转换和锁仍有效时的安全重试规则
- [ ] T040 [US2] 在 `backend/app.py` 实现 `POST /v1/orders/{orderId}/payment-attempts` 与验签 `POST /v1/payment-provider/webhooks`
- [ ] T041 [US2] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentContract.kt` 定义权威状态、allowedActions、处理中查询和一次性导航 effect
- [ ] T042 [US2] 重构 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentViewModel.kt`，移除本地 saveOrder/延时成功，使用同一支付幂等键并每 3 秒查询 PROCESSING
- [ ] T043 [US2] 重构 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentScreen.kt`，只按服务端状态显示支付结果、错误和安全重试

## Phase 5: User Story 3 - 查看订单与电影票 (P2)

**Goal**: 当前用户可恢复自己的订单列表/详情，只有 PAID 订单显示唯一可重试生成的电影票。

**Independent Test**: A 能查看自己的订单/票据，B 和游客不能探测；所有非 PAID 状态无有效 credential。

- [ ] T044 [P] [US3] 先编写订单分页、详情归属统一 404、PAID 唯一票据、非 PAID 无凭证和票据重试生成测试到 `backend/test_orders.py`
- [ ] T045 [P] [US3] 先编写订单列表/详情/票据状态及跨账号错误映射的 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/orders/OrdersViewModelTest.kt`
- [ ] T046 [P] [US3] 先编写列表、详情完整字段、状态允许操作和 PAID-only 票据 UI 测试到 `app/src/androidTest/java/me/ibrahim/moviesapp/compose/presentation/orders/OrdersScreenTest.kt`
- [ ] T047 [US3] 在 `backend/orders.py` 实现当前用户分页列表、详情和 Ticket GENERATING/READY/FAILED_RETRYABLE 安全生成/读取
- [ ] T048 [US3] 在 `backend/app.py` 实现 `GET /v1/orders`、`GET /v1/orders/{orderId}` 与 `GET /v1/orders/{orderId}/ticket`，跨账号不泄露资源存在性
- [ ] T049 [US3] 实现 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/orders/OrdersContract.kt`、`OrdersViewModel.kt` 与 `OrdersScreen.kt`
- [ ] T050 [US3] 实现 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/orders/OrderDetailScreen.kt`，完整展示状态、快照、支付时间、允许操作及 PAID-only 票据
- [ ] T051 [US3] 更新 `app/src/main/java/me/ibrahim/moviesapp/compose/core/Routes.kt` 与 `MoviesNavGraph.kt`，新增订单列表/详情路由且只传稳定 orderId

## Phase 6: User Story 4 - 处理订单超时和恢复 (P3)

**Goal**: 中断后从服务器恢复；未支付订单超时释放座位；PAID 永不被错误取消。

**Independent Test**: 在创建、处理中、成功回调前中断并重启，状态准确；推进服务端时间后仅未支付订单过期释放。

- [ ] T052 [P] [US4] 先编写服务端截止、读写触发过期/释放、PAID 清理保护、处理中迟到成功和重启恢复测试到 `backend/test_payments.py`
- [ ] T053 [P] [US4] 先编写应用恢复立即查询、未知不重复支付、会话重新认证和 PAID 不倒退的 ViewModel 测试到 `app/src/test/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentViewModelTest.kt`
- [ ] T054 [US4] 在 `backend/orders.py` 实现按服务端时间的幂等订单过期，在读写路径调用 003 释放未售座位并跳过 PAID/CONVERTED/SOLD
- [ ] T055 [US4] 在 `backend/payments.py` 实现应用/渠道中断后的 provider 查询恢复与迟到结果处理，库存不可转换时进入 REVIEW_REQUIRED
- [ ] T056 [US4] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentViewModel.kt` 和 `presentation/orders/OrdersViewModel.kt` 实现前台恢复、重启加载和会话失效后重新校验
- [ ] T057 [US4] 在 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentScreen.kt` 与 `presentation/orders/OrderDetailScreen.kt` 显示明确处理中、过期、人工处理和安全返回/刷新入口
- [ ] T058 [US4] 在 `backend/test_payments.py` 验证成功/失败/取消/未知/超时五类结果在 5 秒内可查询为已知状态或明确 PROCESSING

## Phase 7: Polish & Cross-Cutting

- [ ] T059 删除 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/payment/PaymentViewModel.kt`、`domain/MoviesRepository.kt`、`data/repository/MoviesRepositoryImpl.kt` 中本地订单作为购买成功事实的路径，并制定 `data/database/OrderEntity.kt` 旧记录只读/迁移策略
- [ ] T060 [P] 审核 `backend/orders.py`、`backend/payments.py`、`backend/app.py` 与 `app/src/main/java/me/ibrahim/moviesapp/compose/data/network/OrderRemoteApi.kt`，确保日志/错误不含凭据、Bearer、原始 webhook 或不必要个人信息
- [ ] T061 [P] 在 `backend/test_payments.py` 增加状态事件完整性和脱敏断言，确保每次关键转换可由 correlationId 追踪
- [ ] T062 补齐 content description、状态语义和触控区域到 `app/src/main/java/me/ibrahim/moviesapp/compose/presentation/order_confirmation/OrderConfirmationScreen.kt`、`presentation/payment/PaymentScreen.kt`、`presentation/orders/OrdersScreen.kt` 与 `presentation/orders/OrderDetailScreen.kt`
- [ ] T063 按 `specs/004-order-payment/quickstart.md` 执行全套测试、100 次重复/并发及四组手工验收并追加 Completion evidence
- [ ] T064 对照 `specs/004-order-payment/spec.md`、`plan.md`、`data-model.md` 与 `contracts/order-payment-api.yaml` 做最终追踪审查，确认 20 条 FR、7 条 SC 与宪章门槛

## Dependencies & Execution Order

```text
001 auth → 002 showtimes → 003 seat lock
                              ↓
Setup → Foundation → US1 order → US2 payment (core MVP) → US3 history/ticket
                                              └────────→ US4 recovery
US1–US4 → Polish
```

- US1 与 US2 同为 P1，实际业务 MVP 必须同时完成。
- US3 依赖 US2 的 PAID/Ticket；US4 的超时基础可与 US3 并行，最终恢复依赖 US2。
- 测试组 T017–T021、T030–T035、T044–T046、T052–T053 可在各故事开始时并行。
- T060 与 T061 可并行。

## Recommended MVP

完成 T001–T043：权威报价、唯一订单、模拟渠道、五类支付结果、原子售座/票据、重复与未知恢复。US1 单独只有订单，必须连同 US2 才构成可用购票闭环。

## Notes

## Payment-chain completion update (2026-09-26)

- [x] Server confirmation returns final order/payment status, ticket readiness, ticket id, and server time.
- [x] Confirmation is owner-scoped and idempotent; one success produces one PAID order, SOLD seats, and one ticket.
- [x] Simulated Alipay/WeChat accounts use local configurable balances and debit only after server-confirmed PAID plus ticket readiness.
- [x] Insufficient balance, server rejection, and network failure leave server order and local balance unchanged; order state is refreshed.
- [x] Resume-payment resolves the active lock to a server order before opening payment; recovery routes carry only orderId.
- [x] GET /v1/tickets is user-scoped and Settings renders paid electronic tickets with venue, showtime, seats, amount, and ticket identity.
- [x] Added backend confirmation/idempotency/ownership/ticket-list coverage and Android JVM/build verification.

- 模拟 provider 不等于真实支付上线；真实渠道需要单独商户、密钥、合规和安全评审。
- 客户端回调、按钮状态和本地 Room 记录永远不能把订单置 PAID。
- 网络结果未知必须复用原幂等键并查询，不得盲目重新支付。

## Execution status audit (2026-09-26)

Implemented in this pass: authoritative Android OrderRepository quote/create/pay/order/ticket APIs, DTO/mapper layer, DI registration, order confirmation flow, payment state flow with idempotency and polling, order list/detail screens, stable lockId/orderId navigation, server order detail payloads, expiry seat release, status-event writes, webhook event de-duplication, and backend order coverage.

Remaining external evidence: real payment-provider credentials/compliance, human accessibility review, and manual end-to-end acceptance. Automated provider-signature, duplicate/idempotency, ticket retry-on-read, backend, JVM and emulator checks are complete for this environment.
