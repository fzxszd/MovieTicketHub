# Quickstart: 基于收藏的个性化电影推荐

```powershell
python -m unittest discover -s backend -p "test_*.py"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
python backend/app.py
```

## Acceptance

1. 为账号准备至少 2 部具有共同类型/主题的 confirmed 收藏，请求推荐，核对 PERSONALIZED 标签、排除已收藏/已购/不可用、每项真实原因。
2. 固定收藏、目录和算法版本连续请求 5 次，Top-10 集合和顺序完全一致。
3. confirmed 新增和取消收藏后刷新，分别确认依据加入和移除；pending 时显示同步提示且不冒充最新偏好。
4. 用另一账号登录，确认旧结果/原因零闪现且只使用新账号收藏。
5. 分别测试游客、零收藏、1 部收藏、全部缺特征、服务超时和个性化空结果，确认热门/正在上映标签及 fallbackReason。
6. 清空可用目录，确认明确 empty/retry，而非无限加载。
7. 推荐服务失败时走完电影浏览、收藏和购票，确认零阻断。
8. 上报重复 eventId，确认只计一次；检查指标不含收藏列表、令牌或原因来源标题。
9. 邀请至少 20 名代表性用户判断推荐来源，记录是否达到 80% 正确率。

## Completion evidence

记录自动化结果、3 秒 p95、5 次稳定性、账号隔离、收藏变化、核心流程、指标脱敏和 20 人辨识测试。
