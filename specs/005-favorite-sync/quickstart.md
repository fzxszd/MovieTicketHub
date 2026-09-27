# Quickstart: 用户收藏与跨设备同步

## Automated checks

```powershell
python -m unittest discover -s backend -p "test_*.py"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

## Start and smoke test

```powershell
python backend/app.py
$headers = @{ Authorization = "Bearer $token"; 'Idempotency-Key' = 'favorite-test-000001' }
$body = @{ targetState = $true; deviceId = 'device-test-000001'; localSequence = 1 } | ConvertTo-Json
Invoke-RestMethod 'http://localhost:5000/v1/favorites/123' -Method Put -Headers $headers -ContentType 'application/json' -Body $body
Invoke-RestMethod 'http://localhost:5000/v1/favorites?afterRevision=0' -Headers @{ Authorization = "Bearer $token" }
```

## Acceptance flows

### US1–US2

1. 登录后分别从列表、详情和推荐入口收藏/取消同一电影，确认全部入口一致。
2. 快速重复操作 100 次，最终状态等于最后明确操作，服务端只有一条关系。
3. 游客点击时进入登录但不自动收藏；收藏页覆盖 loading/content/empty/error。
4. 下架电影保留 ID，显示不可用且不映射为其他电影。

### US3: Two-device convergence

1. A 收藏，B 同账号同步后出现；A 取消，B 再同步后消失。
2. A/B 离线对同一电影作相反操作，按不同恢复顺序同步，确认最大 serverRevision 获胜并最终一致。
3. 重复提交、超时和丢响应各 100 次，确认 revision 不重复增加、状态不反复翻转。
4. 账号 C 始终看不到 A/B 的数据。

### US4: Offline and account lifecycle

1. 离线查看缓存并添加/取消，确认显示 PENDING 而非 CONFIRMED。
2. 恢复网络后按 localSequence 同步并收敛；拒绝时回滚到权威状态并显示错误。
3. 会话失效时暂停；同账号重新认证后恢复。切换账号时旧数据零闪现且旧队列不上传。
4. 明确退出时确认提示未同步更改将被清除，然后清空该账号本地私有缓存/队列。
5. 服务端确认添加和取消后，分别验证推荐刷新信号触发。

## Completion evidence

记录测试结果、双设备集合、100 次幂等统计、账号泄露检查、用户可用性测试和任何未执行项。
