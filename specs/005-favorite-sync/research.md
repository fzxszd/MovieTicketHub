# Research: 用户收藏与跨设备同步

## 1. 同步协议

**Decision**: 使用“明确目标状态操作 + 服务端 revision 增量拉取”，而不是整份集合覆盖。

**Rationale**: `SET true/false` 可幂等重试；revision 能让多设备只拉变化并保留删除 tombstone。整包覆盖会让旧设备复活已取消收藏。

**Alternatives considered**: toggle 无法安全重放；整集合 last-write-wins 数据量大且易误删并发更新。

## 2. 冲突顺序

**Decision**: 每个新接受操作由服务端事务分配单调递增 `serverRevision`，较大 revision 为最终状态，不使用设备时间。

**Rationale**: 设备时钟不可信；该规则直接实现“最后一个被服务端接受的明确操作”。

**Alternatives considered**: 客户端时间戳可被改时；添加优先会违反明确取消意图。

## 3. 幂等

**Decision**: 每次本地意图生成 UUID `clientOperationId`，服务端按 userId+opId 唯一并返回原处理结果。

**Rationale**: 超时、丢响应和 Worker 重试不会重复翻转状态。

## 4. 本地模型

**Decision**: Room 分开保存当前投影、操作队列和同步游标；投影同时记录 confirmedState 与 desiredState。

**Rationale**: UI 可明确区分已确认与待同步，并能在拒绝时回到权威状态。

## 5. 离线调度

**Decision**: 使用应用内唯一同步协调器，在网络恢复、前台、登录恢复和手动重试触发；同账号串行提交，指数退避。本阶段不新增后台常驻调度依赖。

**Rationale**: 保证本地顺序且避免多个后台任务并发上传同一队列。

## 6. 账号隔离

**Decision**: 队列/缓存以服务端 userId 派生的内部 accountId 分区；Worker 执行前核对当前安全会话。明确退出清除该账号本地私有数据；会话暂失效则暂停并只允许同账号重认证恢复。

**Rationale**: 防止切号闪现和下一个账号上传旧队列，同时符合宪章退出清理要求。

## 7. 电影快照

**Decision**: 收藏关系只依赖稳定 movieId；本地保存有限展示快照，在线时合并最新目录，下架时保留 ID 和不可用说明。

**Rationale**: 资料变化不应删除用户收藏，也不能把下架 ID 映射为别的电影。

## 8. 推荐触发

**Decision**: 只有服务端确认 revision 变化后触发当前账号推荐失效；添加和移除都触发，pending 不触发权威推荐。

**Rationale**: 推荐必须跨设备一致且不能把未同步意图冒充事实。

## 9. API 形态

**Decision**: 提供 GET 列表/增量变化、PUT 单电影明确状态和 POST 批量 sync；全部从 Bearer 派生 userId。

**Rationale**: 在线操作可轻量提交，离线恢复可按序批量，同时共用相同幂等处理器。

## 10. 旧同步迁移

**Decision**: 一次性从旧 payload 去重导入新表后移除 favorites 字段权威性；旧 `/sync/push` 永不覆盖新表。

**Rationale**: 避免新旧双写造成收藏复活或跨账号污染。

## 11. 设备标识

**Decision**: `deviceId` 是应用首次安装生成的高熵随机标识，不使用硬件、广告或系统账号标识；服务端只保存其哈希。

**Rationale**: 同步只需要区分客户端操作来源，不需要收集可跨应用追踪的设备身份。
