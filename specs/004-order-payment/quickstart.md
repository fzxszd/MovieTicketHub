# Quickstart: 订单创建与支付

用于实现完成后的本地验证。先完成 001、002、003；本阶段支付渠道是后端模拟适配器，不会产生真实扣款。

## Automated checks

```powershell
python -m pip install -r backend/requirements.txt
python -m unittest discover -s backend -p "test_*.py"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

无设备时记录未执行的 instrumentation 测试。

## Start backend

```powershell
$env:MOVIES_BACKEND_DB = (Resolve-Path .\backend\data).Path + '\movies_backend.db'
$env:PAYMENT_PROVIDER = 'simulated'
$env:PAYMENT_WEBHOOK_SECRET = 'local-test-secret-not-for-production'
python backend/app.py
```

生产密钥不得写入仓库。先登录并创建有效 seat lock，准备 `$token` 与 `$lockId`。

## Contract smoke flow

```powershell
$auth = @{ Authorization = "Bearer $token" }
$quote = Invoke-RestMethod "http://localhost:5000/v1/seat-locks/$lockId/order-quote" -Headers $auth

$createHeaders = @{ Authorization = "Bearer $token"; 'Idempotency-Key' = 'order-test-00000001' }
$orderBody = @{ lockId = $lockId; acceptedQuoteVersion = $quote.quoteVersion } | ConvertTo-Json
$order = Invoke-RestMethod 'http://localhost:5000/v1/orders' -Method Post -Headers $createHeaders -ContentType 'application/json' -Body $orderBody

$payHeaders = @{ Authorization = "Bearer $token"; 'Idempotency-Key' = 'payment-test-000001' }
$payBody = @{ method = 'ALIPAY_SIMULATED' } | ConvertTo-Json
Invoke-RestMethod "http://localhost:5000/v1/orders/$($order.id)/payment-attempts" -Method Post -Headers $payHeaders -ContentType 'application/json' -Body $payBody
Invoke-RestMethod "http://localhost:5000/v1/orders/$($order.id)" -Headers $auth
```

重复创建订单和支付时使用相同幂等键，确认 ID、金额和最终效果不重复。

## Manual acceptance

### US1: Order confirmation

1. 从有效锁进入确认页，核对电影、影院、影厅、当地场次、1–6 个座位、逐项价格、费用、货币和总额。
2. 重复点击确认，只产生一个订单。
3. 改变服务端报价版本，确认页面阻止创建、展示差异并要求再次确认。
4. 使用过期、已释放或他人的 lockId，确认拒绝且不泄露信息。

### US2: Payment outcomes

1. 依次让模拟渠道返回 SUCCESS、FAILED、CANCELLED、PENDING/UNKNOWN，验证页面状态与允许操作。
2. 连续点击、网络重试和重复 webhook 各执行 100 次，确认无重复订单、交易效果、座位出售或票据。
3. 模拟成功 webhook 与主动查询并发，确认只发生一次 PAID 转换。
4. 模拟客户端超时后渠道成功，确认先显示处理中，随后恢复 PAID，而不是要求再次支付。
5. 场次在付款前停售，确认阻止支付。

### US3: Orders and tickets

1. 账号 A 查看自己的列表和详情；账号 B 与游客访问相同 orderId 均得到不泄露资源的拒绝。
2. PAID 订单显示唯一票据；其他所有状态不返回有效 credential。
3. 模拟票据展示生成失败，确认订单仍 PAID，可安全重试票据且不重新付款。

### US4: Timeout and recovery

1. 在创建订单、支付处理中、渠道成功但客户端未收到三个位置关闭应用，重启后均从服务器恢复。
2. 推进服务端时间超过 paymentDeadline，确认订单 EXPIRED 且未出售座位释放。
3. 模拟迟到成功但库存已不可转换，确认进入 REVIEW_REQUIRED，保留审计且不伪造票据。
4. 确认已 PAID 订单永不被超时清理取消。

## Completion evidence

交付时追加测试命令结果、100 次重复/并发统计、四组人工场景、未执行检查和原因，以及 API/模型偏差。
\n+2026-09-26 自动验证：Python 后端 37 项测试通过；Android `testDebugUnitTest`、`assembleDebug`、`compileDebugAndroidTestKotlin` 通过；Pixel_7 API 35 上 8 项 connected instrumentation 测试通过。订单报价、幂等创建、服务端支付状态、订单列表/详情和票据读取链路已接入。
\n+剩余证据：100 次重复/并发压力矩阵、完整人工端到端验收、无障碍人工验收，以及真实支付渠道凭据/合规评审。
