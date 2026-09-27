# MovieTicketHub 认证与同步后端

Flask 服务负责账号注册、登录、可撤销会话和按账号隔离的数据同步。SQLite 默认保存到
`backend/data/movies_backend.db`。密码只保存 Werkzeug `scrypt` 哈希，原始会话令牌只返回
给客户端一次，数据库仅保存 SHA-256 摘要。

## 本地启动

在项目根目录运行：

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.txt
backend/.venv/Scripts/python.exe -m unittest discover -s backend -p "test_*.py" -v
backend/.venv/Scripts/python.exe backend/app.py
```

Android 模拟器的 debug 构建通过 `http://10.0.2.2:5000` 访问服务。健康检查：

```powershell
Invoke-RestMethod http://127.0.0.1:5000/health
```

## 接口

- `POST /auth/register`：创建账号并返回 30 天会话。
- `POST /auth/login`：邮箱密码登录；15 分钟内连续失败 5 次会阻断 15 分钟。
- `GET /auth/me`：验证 Bearer 会话并返回当前用户。
- `POST /auth/logout`：幂等撤销当前会话。
- `POST /sync/push`、`GET /sync/pull`：仅操作 Bearer 会话所属账号的数据。

旧 `user_sync_data` 中的明文密码在首次启动时原子迁移为账号密码哈希，并从同步 JSON
删除。生产部署必须置于 HTTPS 反向代理后；不要记录密码、令牌或 Authorization 请求头。
