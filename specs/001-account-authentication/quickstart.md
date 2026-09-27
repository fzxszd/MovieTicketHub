# Quickstart Validation: 账号认证与会话

本指南用于功能实现完成后的端到端验证，不包含实现代码。

## Prerequisites

- JDK 17、Android Studio、Android SDK 35，以及 API 24+ 模拟器或设备
- Python 3.12+
- 项目根目录为 `C:\MoviesApp-Compose-main\MoviesApp-Compose-main`
- debug 环境使用模拟器地址 `http://10.0.2.2:5000`；release 验证必须使用 HTTPS

## 1. Backend Setup

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.txt
backend/.venv/Scripts/python.exe -m unittest discover -s backend -p "test_*.py" -v
backend/.venv/Scripts/python.exe backend/app.py
```

另开一个 PowerShell 验证健康状态：

```powershell
Invoke-RestMethod http://127.0.0.1:5000/health
```

预期返回 `{"status":"ok"}`，并且认证测试覆盖注册、重复邮箱、登录、统一错误、限流、会话恢复、退出和同步接口授权。

## 2. Android Automated Checks

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\gradlew.bat assembleDebug --console=plain
```

预期所有任务成功；Room 7→8 migration test 必须证明账号资料和业务数据保留、密码列被删除。

## 3. Registration Journey (US1)

1. 清除测试应用数据并启动应用。
2. 确认访客可以进入电影列表。
3. 从受保护操作进入登录页并切换到注册。
4. 分别提交空字段、无效邮箱、少于 8 个字符的密码和不一致确认密码。
5. 使用用户名 `Alice`、邮箱 `Alice@example.com` 和有效密码完成注册。
6. 再使用 ` alice@EXAMPLE.com ` 注册。

预期：每个字段错误均有明确提示；首次注册成功并建立会话；第二次注册被识别为重复邮箱；密码不出现在日志、Room 或同步 JSON。

## 4. Login & Session Restore (US2)

1. 主动退出后，分别使用错误邮箱和错误密码登录。
2. 使用正确邮箱和密码登录。
3. 强制停止并重新启动应用。
4. 在后端将测试会话标记为过期或撤销，再次启动。
5. 停止后端并尝试登录或恢复会话。

预期：错误凭据使用同一提示；正确凭据登录成功；有效会话自动恢复；无效会话回到游客状态；网络失败显示可重试错误且不伪造登录成功。

## 5. Protected Operations (US3)

1. 退出登录并浏览电影列表和详情。
2. 检查游客推荐区域显示热门或正在上映内容，并标注非个性化来源。
3. 依次尝试收藏、请求个性化推荐、进入选座和确认购票。
4. 从其中一个操作完成登录，并在没有收藏时检查推荐备用内容。
5. 添加收藏后重新获取推荐，检查“根据你的收藏推荐”来源说明。
6. 模拟推荐服务失败，再检查备用内容、失败说明和重试入口。
7. 模拟没有任何电影可用，检查明确空状态且不存在无限加载。
8. 返回来源后检查原操作是否已自动产生副作用。

预期：公开浏览可用；游客、无收藏和推荐失败都有标明来源的备用内容；没有电影时显示明确空状态；四类受保护操作都要求认证；匿名状态不会写入收藏、占座或订单；登录后返回来源，但用户需要明确继续原操作。

## 6. Logout & Account Isolation (US4)

1. 以账号 A 登录并创建可识别的收藏数据。
2. 退出并重启应用。
3. 确认账号 A 的私有数据不可访问。
4. 以账号 B 登录，检查收藏、推荐和订单。

预期：退出后进入登录页面，可选择继续以游客身份浏览；本地活动令牌已清除且服务端会话被撤销（离线时进入待撤销队列）；账号 B 看不到账号 A 的私有数据。

## 7. Contract & Security Checks

接口形状以 [auth-api.openapi.yaml](./contracts/auth-api.openapi.yaml) 为准，并检查：

- 未携带 Bearer 令牌访问 `/auth/me`、`/sync/push`、`/sync/pull` 均返回未认证；`/auth/logout` 为幂等接口并返回 204。
- 已退出、过期或被撤销令牌不能继续访问私有数据。
- 服务端数据库不存在明文密码或原始会话令牌。
- release Manifest/网络安全配置禁止 cleartext，认证请求日志不记录请求正文或 Authorization 头。

## Exit Criteria

- `requirements.md` 保持全部通过。
- 自动化测试、后端测试和 Android debug 构建全部成功。
- `spec.md` 中 SC-001 至 SC-009 均有可重复验证记录。
- 任何无法执行的验证必须记录原因、风险和后续负责人，不能静默跳过。

## 2026-09-25 自动验证记录

- `python -m unittest discover -v`：12 个后端认证/同步测试通过。
- `gradlew testDebugUnitTest`：16 个 Android JVM 单元测试通过，0 失败、0 跳过。
- `gradlew :app:compileDebugAndroidTestKotlin`：Room 迁移与 Compose 认证流程测试编译通过。
- `gradlew assembleDebug assembleRelease`：debug APK 与未签名 release APK 均构建成功；release 合并清单确认禁用 cleartext。
- 真机/模拟器上的 `connectedDebugAndroidTest` 仍需在已连接设备时执行。
