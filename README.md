# MovieTicketHub

MovieTicketHub 是一个全栈 Android 电影浏览与影院购票示例项目。它由 Jetpack Compose 客户端与 Flask + SQLite 服务端组成，覆盖账号、场次、锁座、订单、模拟支付、收藏同步和推荐等完整链路。

> 支付渠道仅为模拟实现，项目只适用于学习、开发与演示，不能处理真实资金。

## 功能

- 浏览正在热映和即将上映的电影，支持搜索、影片详情、演职员和简介。
- 游客可浏览；收藏、选座和购票会要求用户登录。
- 支持注册、登录、会话恢复和退出登录；客户端使用 Android Keystore 加密保存令牌，服务端支持会话撤销与登录限流。
- 可按影院、日期、行政区、时间段、价格和距离筛选及排序场次。
- 支持 1–6 个座位的实时选择；服务端负责库存判定、10 分钟座位锁与过期释放。
- 支持服务端权威报价、幂等创建订单、模拟支付宝/微信支付、电子票查询和退款。
- 收藏以 Room 实现离线优先，通过操作队列、幂等键与 revision 进行跨设备增量同步。
- 推荐会结合已确认收藏、购票、热度和新鲜度；游客及冷启动用户会自动降级到安全的通用推荐。

## 架构

```text
Android / Jetpack Compose
  UI ──> ViewModel + StateFlow ──> Repository
                                    ├─ Ktor：猫眼电影数据
                                    ├─ Ktor + Bearer Token：Flask REST API
                                    └─ Room：本地缓存与离线收藏操作

Flask REST API
  auth · showtimes · seats · orders · payments · favorites · recommendations
                                      │
                                   SQLite
```

客户端采用 `presentation`、`domain`、`data` 分层。UI 只渲染状态与发送事件；会话、库存、价格、支付结果和已确认收藏均由服务端作为最终事实来源。

## 技术栈

| 范围 | 技术 |
| --- | --- |
| Android | Kotlin、Jetpack Compose、Material 3、Navigation Compose、ViewModel、StateFlow、Coroutines |
| 客户端数据 | Ktor 3、kotlinx.serialization、Coil 3、Room 2.6、Koin 4、Android Keystore |
| 后端 | Python 3、Flask 3、SQLite、HMAC |
| 测试 | JUnit、Ktor Mock、Room Testing、Compose UI Test、Python `unittest`、Flask test client |

## 本地运行

### 前置条件

- Android Studio、Android SDK 和 API 24+ 模拟器或设备
- 与 Gradle 构建兼容的 JDK
- Python 3.12+

### 1. 启动后端

在项目根目录执行：

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.txt
backend/.venv/Scripts/python.exe backend/app.py
```

后端默认监听 `http://127.0.0.1:5000`。Debug Android 构建已使用 `http://10.0.2.2:5000`，以便模拟器访问宿主机服务。可用以下命令确认服务状态：

```powershell
Invoke-RestMethod http://127.0.0.1:5000/health
```

### 2. 运行 Android 应用

使用 Android Studio 打开项目根目录，选择模拟器或真机并运行 `app` 配置；也可以构建 Debug APK：

```powershell
.\gradlew.bat assembleDebug
```

应用可在未登录时浏览电影；涉及登录、收藏、场次、选座、支付、订单或推荐的完整流程前，请先启动后端。

## 测试

后端测试：

```powershell
backend/.venv/Scripts/python.exe -m unittest discover -s backend -p "test_*.py" -v
```

Android 本地单元测试：

```powershell
.\gradlew.bat testDebugUnitTest
```

## 项目结构

```text
app/                    Android 客户端
  src/main/             Compose UI、ViewModel、Repository、Room、Ktor DTO
  src/test/             本地单元测试
  src/androidTest/      设备与 UI 测试
backend/                Flask API 与 Python 测试
specs/                  功能规格、数据模型、API 契约与验收依据
docs/                   技术与架构文档
```

## 文档与部署注意事项

- 深入的实现说明见 [项目技术总结](docs/项目技术总结.md) 和 [Spring Boot 后端重构成本评估](docs/SpringBoot后端重构成本评估.md)。
- `backend/data/*.db` 是本地开发数据，不应提交到版本控制。
- Release 构建必须设置真实的 HTTPS API 地址；不要使用 Debug 的 `10.0.2.2` 地址。
- 生产环境应通过 HTTPS 反向代理部署 API；如需多实例部署或接入真实支付，应迁移到 PostgreSQL 等共享数据库并接入合规支付服务。
