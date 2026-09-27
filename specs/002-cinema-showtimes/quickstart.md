# Quickstart: 影院与场次查询

本指南用于实现完成后的本地验证。文档生成阶段不会修改或运行应用代码。

## Prerequisites

- JDK 17 与 Android SDK（API 35）
- Android 模拟器或 API 24+ 设备
- Python 3.12+
- 从仓库根目录执行命令

## 1. Run backend tests

```powershell
python -m pip install -r backend/requirements.txt
python -m unittest discover -s backend -p "test_*.py"
```

预期：同步 API 原有测试和场次查询/复核新增测试全部通过。

## 2. Start the backend

```powershell
$env:MOVIES_BACKEND_DB = (Resolve-Path .\backend\data).Path + '\movies_backend.db'
python backend/app.py
```

Android 模拟器使用 `http://10.0.2.2:5000`。真机需要把客户端开发环境地址改为电脑在局域网中的地址；生产地址不得硬编码到源码。

## 3. Smoke-test the contract

```powershell
Invoke-RestMethod 'http://localhost:5000/v1/movies/1/showtime-dates?cityCode=CN-BJ'
Invoke-RestMethod 'http://localhost:5000/v1/movies/1/cinema-showtimes?cityCode=CN-BJ&date=2026-09-26&sort=recommended'
Invoke-RestMethod 'http://localhost:5000/v1/movies/1/cinema-showtimes?cityCode=CN-BJ&date=2026-09-26&sort=distance'
```

检查：

- 日期按升序返回，只包含至少一个未来可售场次的日期。
- 影院结果中的每家影院至少有一个符合条件且可售的场次。
- 未带坐标请求 `distance` 时，`appliedSort` 为 `recommended`，并返回 `LOCATION_UNAVAILABLE`。
- 金额为 `amountMinor` 整数与 `CNY`，时间为带时区的 ISO-8601 UTC 时间点。

使用查询结果中的场次 ID 和版本进行复核：

```powershell
Invoke-RestMethod 'http://localhost:5000/v1/showtimes/showtime-id/validation?observedVersion=1'
```

## 4. Run Android checks

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

若没有连接设备，可跳过最后一条，但交付说明必须记录未执行的 instrumentation 测试及风险。

## 5. Manual acceptance flow

### Scenario A: Browse available showtimes (US1)

1. 退出账号，以匿名状态打开一部电影详情。
2. 点击购票，确认默认选中最近仍有可售场次的日期。
3. 确认影院卡显示名称、地址、最低价；场次显示影院当地开始/结束时间、语言/版本、影厅、价格。
4. 切换到无排片日期，确认出现空状态和更换日期/重试入口。
5. 停止后端后重试，确认错误状态与空状态不同，且当前日期和筛选不丢失。

### Scenario B: Filter and sort cinemas (US2)

1. 分别设置区域、时间范围和价格范围，确认只保留有匹配可售场次的影院。
2. 选择最低价排序，确认使用当前筛选结果的最低可售价升序。
3. 拒绝定位后选择距离排序，确认显示降级说明且仍可使用推荐排序结果。
4. 提供测试坐标，确认距离显示为统一单位且排序正确。
5. 点击清除筛选，确认电影与日期不变，区域/时间/价格恢复默认。

### Scenario C: Revalidate before seat selection (US3)

1. 登录后点击一个可售场次，确认只发起一次复核并使用稳定 `showtimeId` 导航。
2. 在点击前从测试数据中修改场次价格并递增版本，确认客户端阻止进入选座，展示价格变化并刷新。
3. 把场次改为 `CANCELLED`、`STOPPED`、`SOLD_OUT`，以及把开始时间改到过去，分别确认不能进入选座且显示原因。
4. 匿名点击可售场次，确认进入登录页但不锁座；登录后回到该场次，再次复核后才能进入选座。
5. 快速连续点击购票按钮，确认复核期间按钮禁用且不会重复导航。

### Scenario D: Time-zone boundaries

1. 使用包含跨午夜结束时间的场次，确认它仍归属于开始时间所在的影院当地日期。
2. 把设备时区改成与影院不同的时区，确认场次仍按影院时区显示。
3. 使用测试时钟覆盖场次开始前、开始时和结束后，确认可选状态边界准确。

## 6. Completion evidence

交付时记录：

- 后端测试、Android 单测、构建与 UI 测试结果；
- 上述四组手工场景的通过情况；
- 任何未执行检查的原因；
- API 契约或数据库结构若有偏差，对应文档已同步更新。
