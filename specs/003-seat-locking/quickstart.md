# Quickstart: 座位展示、选座与锁座

本指南用于实现完成后的本地验证；先完成 `001-account-authentication` 和 `002-cinema-showtimes`。

## Prerequisites

- JDK 17、Android SDK API 35 与模拟器/设备
- Python 3.12+
- 后端已有测试账号、可售场次、影厅布局和库存 fixture

## 1. Run automated checks

```powershell
python -m pip install -r backend/requirements.txt
python -m unittest discover -s backend -p "test_*.py"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

无连接设备时可跳过最后一项，但必须在交付记录中说明。

## 2. Start backend

```powershell
$env:MOVIES_BACKEND_DB = (Resolve-Path .\backend\data).Path + '\movies_backend.db'
python backend/app.py
```

先通过 001 登录获得 Bearer token。以下示例中的 `$token`、`$showtimeId`、`$lockId` 替换为实际值。

## 3. Contract smoke test

```powershell
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod "http://localhost:5000/v1/showtimes/$showtimeId/seats" -Headers $headers

$lockHeaders = @{
  Authorization = "Bearer $token"
  'Idempotency-Key' = 'manual-test-00000001'
}
$body = @{ seatIds = @('A-01', 'A-02'); observedInventoryVersion = 1 } | ConvertTo-Json
$lock = Invoke-RestMethod "http://localhost:5000/v1/showtimes/$showtimeId/seat-locks" -Method Post -Headers $lockHeaders -ContentType 'application/json' -Body $body
$lockId = $lock.id
Invoke-RestMethod "http://localhost:5000/v1/seat-locks/$lockId" -Headers $headers
Invoke-RestMethod "http://localhost:5000/v1/seat-locks/$lockId" -Method Delete -Headers $headers
```

检查锁期正好为 600 秒、金额为整数分、重复 POST 返回相同 lockId/expiresAt，DELETE 可安全重复。

## 4. Manual acceptance

### Scenario A: Authoritative layout (US1)

1. 已登录用户从一个可售场次进入选座。
2. 核对屏幕方向、排号、座号、过道、空缺、情侣座、不可售座和价格区域。
3. 用另一个账号创建锁后等待最多 5 秒，确认第一个页面显示“他人锁定”。
4. 停止后端并刷新，确认页面不继续把旧座位显示为可选，且提供重试/返回场次。
5. 使用屏幕阅读器逐个聚焦座位，确认读出排号、座号、价格和状态。

### Scenario B: Atomic lock (US2)

1. 选择 1–6 个座位，确认第 7 个不会加入且有明确提示。
2. 两个账号同时锁同一座位，重复 100 次；每次最多一个 ACTIVE 锁。
3. 选择多个座位，并让其中一个先被另一账号占用；确认整组失败且其他座位仍可用。
4. 模拟首次请求服务端成功但客户端超时，使用相同 Idempotency-Key 重试；确认返回同一 lockId 且截止时间不变。
5. 锁成功后确认座位与逐座价格快照、总额和 10 分钟倒计时正确。

### Scenario C: Lock lifecycle (US3)

1. 主动释放，确认其他账号在 5 秒内可锁定这些座位。
2. 用可控服务端时钟推进到 `expiresAt`，确认锁变 EXPIRED、不能进入订单且座位恢复。
3. 修改设备系统时间和让应用进入后台，确认返回后以服务端锁详情为准，截止时间不延长。
4. 退出账号，确认其全部未转订单锁被释放。
5. 把场次改为 STOPPED/CANCELLED，确认活动锁 INVALIDATED 并显示原因。
6. 模拟未支付订单取消，确认对应 ACTIVE 锁释放且座位恢复可用。
7. 模拟 004 把锁转成 CONVERTED/SOLD，再执行清理，确认座位仍为 SOLD。

## 5. Completion evidence

实现交付时在此追加：自动化测试结果、100 次并发统计、三组手工场景结果、未执行检查及原因、API/模型偏差说明。
