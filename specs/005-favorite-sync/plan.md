# Implementation Plan: 用户收藏与跨设备同步

**Branch**: `005-favorite-sync` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

## Summary

将当前以邮箱为键、整份用户 JSON 推拉并由本地 Room 直接冒充成功的收藏实现，替换为账号隔离的增量同步。服务端以 `(userId, movieId)` 保存唯一权威收藏状态，以全局递增 `serverRevision` 决定“最后一个被服务端接受的明确目标状态”；客户端以操作日志记录 `targetState`、`clientOperationId` 和本地顺序，支持在线即时提交、离线排队、幂等重试和按 revision 拉取。所有入口共享 `FavoriteRepository` 状态流，退出/切号立即清空可见私有状态。

## Technical Context

**Language/Version**: Kotlin 2.1.0；Python 3.12  
**Primary Dependencies**: Compose、Room 2.6.1、Ktor 3.0.3、Koin 4.0.2、kotlinx.serialization；Flask 3.0.3  
**Storage**: 后端 SQLite 权威 favorite_states/operations；Android Room 缓存、操作队列和同步游标  
**Testing**: Python unittest/Flask client/双设备并发；JUnit 4、Room in-memory、Compose UI Test  
**Target Platform**: Android API 24–35 + Flask API  
**Project Type**: Android + API 服务  
**Performance Goals**: 正常网络 95% 操作 2 秒内确认或显示处理中；100 次重复操作无重复关系；同步后双设备集合一致  
**Constraints**: 依赖 001 Bearer；服务端是最终权威；离线队列严格按账号隔离；不用设备时间解决冲突；推荐只接收已确认收藏变化  
**Scale/Scope**: 每用户数百收藏、每设备少量待同步操作；列表/详情/推荐三个入口与收藏列表页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

| Principle | Result | Evidence |
|---|---|---|
| I. 分层与单向数据流 | PASS | 独立 FavoriteRepository、Remote/Local data source、Domain state；所有 Compose 入口共享状态流。 |
| II. 票务正确性 | PASS（不适用） | 不修改场次、库存、订单或支付。 |
| III. 账号与隐私 | PASS | userId 来自 Bearer；缓存/队列以内部 accountId 分区；退出清空界面并阻止跨账号上传。 |
| IV. 推荐以用户为中心 | PASS | 只有服务端确认的添加/移除触发当前账号推荐刷新；无数据时仍由 006 负责降级。 |
| V. 可恢复体验 | PASS | 明确提交中、已确认、待同步、失败、离线、空和错误；拒绝时回到权威状态。 |
| 技术/流程/测试 | PASS | 延续 Room/Ktor/Koin/Flask；迁移、冲突、幂等、账户隔离和构建均有验证。 |

### Post-design gate

- **PASS**：契约不接受 email/userId；同步结果携带服务端 revision 和逐操作结果。
- **PASS**：Room 模型区分 desired/confirmed state 与 operation queue，UI 不把 pending 当 confirmed。
- **PASS**：明确退出清除、会话失效暂停、同账号重新认证恢复和推荐刷新边界。
- **PASS**：tasks 包含服务端、Room、ViewModel、跨设备、泄露和无障碍测试。

无宪章例外；001 是强制前置功能。

## Architecture and Sync Flow

1. 所有收藏按钮发送明确 `SetFavorite(movieId, targetState)`，不发送 toggle；Repository 在当前账号分区内写 FavoriteOperation，并更新 desired state 为 PENDING。
2. 在线时立即调用增量同步；离线时由应用内同步协调器在网络恢复、登录恢复、前台进入和手动重试时按 localSequence 提交。本阶段不新增后台常驻调度依赖。
3. 服务端事务按请求中操作顺序处理。`clientOperationId` 全局幂等；每个新接受操作分配单调递增 `serverRevision`，upsert `(userId,movieId)` 为目标状态并保存 tombstone。
4. 同步响应返回逐操作 ACCEPTED/REJECTED、服务端接受顺序、当前权威状态，以及 sinceRevision 之后的所有账号变化。客户端在一个 Room 事务中确认/回滚操作并应用远端变化。
5. 冲突不比较设备时间；最后一个在服务器事务中被接受的明确目标状态获胜。两设备再次同步后通过 revision 收敛。
6. 收藏列表从当前账号 Room 投影读取，显示 confirmed 与本地 pending overlay；电影资料快照仅供离线展示，联网时按稳定 movieId 补充最新目录信息。
7. 同步拒绝时将 desired 恢复到服务端 currentState，保留可理解错误；有效的新操作可重新排队。
8. 服务端确认变化后发出当前账号 favorite revision，触发 006 推荐重新计算；pending 本地操作不成为服务端推荐事实。

## Account Lifecycle and Privacy

- 登录后先切换到新 accountId 分区，再发布收藏状态，避免上一账号闪现。
- 会话暂时失效：暂停 worker、隐藏/锁定私有页面，保留队列但只有同一 accountId 重新认证后才能恢复。
- 明确退出/账号切换：先停止 worker并清空内存状态；按 001 策略删除该账号本地收藏缓存与待同步队列（提示未同步更改会丢失），确保个人数据不继续暴露。
- Sync Worker 输入只保存内部 accountId，执行时必须确认当前安全会话 userId 匹配；绝不接受队列中的邮箱作为授权身份。
- 日志不得包含密码、原始令牌、完整收藏集合或其他账号数据。

## Migration and Rollback

- 后端新增 `favorite_states` 与 `favorite_operations`，不再把 `user_sync_data.payload.favorites` 当权威。一次性导入时按用户映射稳定 userId，去重后生成初始 revision，并从旧 payload 移除收藏副本。
- Room 将 `FavoriteMovie` 迁移为 `FavoriteStateEntity`、`FavoriteOperationEntity`、`FavoriteSyncCursorEntity`，使用明确 Migration，禁止 destructive fallback。
- 旧数据只有能映射到已认证 accountId 时才导入；无法归属的收藏不显示也不上载。
- 回滚期间新服务端表保留；旧整包 `/sync/push` 不得覆盖新权威收藏。

## Verification Strategy

- 后端：授权、唯一关系、明确 set 状态、幂等 opId、revision 顺序、两设备冲突/收敛、tombstone、下架电影、增量游标。
- Android：Room 迁移/事务、在线/离线状态、顺序队列、拒绝回滚、会话失效、退出/切号零闪现、三入口一致。
- Compose：loading/empty/offline/error/pending/failed、下架说明、登录门禁与无障碍。
- 集成：设备 A/B 同账号及账号 C 隔离，重复/丢响应/超时 100 次，推荐刷新只发生于已确认变化。

## Project Structure

```text
specs/005-favorite-sync/{plan.md,research.md,data-model.md,quickstart.md,tasks.md,contracts/favorites-api.yaml}

backend/{app.py,favorites.py,test_favorites.py,test_favorite_sync.py}

app/src/main/java/me/ibrahim/moviesapp/compose/
├── data/database/{FavoriteStateEntity.kt,FavoriteOperationEntity.kt,FavoriteSyncCursorEntity.kt,FavoriteDao.kt,MoviesDatabase.kt}
├── data/{dto/FavoriteDto.kt,mappers/FavoriteMapper.kt,network/FavoriteRemoteApi.kt,repository/FavoriteRepositoryImpl.kt,sync/FavoriteSyncCoordinator.kt}
├── domain/favorite/{Favorite.kt,FavoriteRepository.kt}
├── presentation/favorite/{FavoriteMoviesContract.kt,FavoriteMoviesViewModel.kt,FavoriteMoviesScreen.kt}
└── presentation/common/FavoriteActionState.kt

app/src/test/.../{FavoriteRepositoryTest.kt,FavoriteSyncTest.kt,FavoriteViewModelTest.kt}
app/src/androidTest/.../FavoriteFlowScreenTest.kt
```

**Structure Decision**: 保留现有 app 与 Flask 服务，但把收藏从臃肿 MoviesRepository/整包同步拆出。Room 继续作为账号分区缓存与离线队列，不作为最终权威。

## Complexity Tracking

无宪章豁免。操作日志与 tombstone 是支持离线删除、幂等和跨设备收敛所必需，不是预留式复杂度。
