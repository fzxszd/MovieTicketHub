# Tasks: 用户收藏与跨设备同步

## Implementation status (2026-09-26)

所有可在当前环境完成的实现任务均已完成并验证；T050 仅保留真实设备/人工验收阻塞项。

### Phase 1–2: Setup and Foundation

- [X] T001 后端收藏服务、测试骨架
- [X] T002–T004 Android Domain/API/资源骨架
- [X] T005–T008 Bearer 隔离、服务端表、revision、幂等
- [X] T009–T015 Room 实体/DAO/迁移、DTO、Repository、设备标识、DI

### Phase 3–4: US1/US2

- [X] T016–T018 授权、状态、UI 测试骨架
- [X] T019–T025 明确 set API、operation-first 写入、详情状态、登录门禁
- [X] T026–T028 列表/分页/状态测试骨架
- [X] T029–T032 增量查询、Contract、账号隔离 ViewModel、空/错误/离线 UI

### Phase 5: US3

- [X] T033–T034 批量顺序、幂等、设备哈希和同步测试
- [X] T035–T039 服务端批量同步、串行协调、Room 投影/游标、confirmed revision

### Phase 6: US4

- [X] T040–T041 离线与账号隔离测试骨架
- [X] T042–T045 operation queue、会话核对、退出清理、离线/失败提示

### Phase 7: Polish

- [X] T046 旧 payload 迁移后不再作为新收藏权威
- [X] T047 MoviesRepository 的旧 email 收藏 API 与 SyncDto 收藏载荷已移除；收藏入口已切换到 FavoriteRepository
- [X] T048 日志/载荷安全审查
- [X] T049 自动化性能、重复提交和同步验证
- [ ] T050 Quickstart 中的 connected Android、双设备、三账号和人工验收（外部 blocker）
- [X] T051 FR/SC/宪章追踪，见 `requirement-traceability.md` 与 `acceptance-evidence.md`

## External blockers

T050 需要当前环境未提供的 Android 模拟器/真机、网络切换能力和人工验收参与者；其余实现、测试、构建和静态验证均已完成。

## Dependency order

`001 auth → server/Room foundation → US1 + US2 → US3 sync → US4 offline → polish`。
同步冲突使用服务端接受顺序和 `serverRevision`，不使用 toggle、设备时间、email 或完整集合覆盖。
