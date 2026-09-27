# Implementation Plan: 基于收藏的个性化电影推荐

**Branch**: `006-movie-recommendations` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

## Summary

用后端确定性的内容相似度推荐替换当前客户端随机权重算法。推荐只读取 005 已确认的当前账号收藏、公开电影特征和 004 已购买电影；至少 2 部具有可用特征的收藏时生成个性化结果，否则返回明确标注的热门/正在上映备用内容。结果按稳定规则排序、携带真实原因与版本，失败自动降级且不阻断浏览、收藏或购票。

## Technical Context

**Language/Version**: Kotlin 2.1.0；Python 3.12  
**Dependencies**: Compose/Ktor/Koin/kotlinx.serialization；Flask/SQLite/Python 标准库  
**Storage**: 后端公开电影目录快照、推荐运行记录和脱敏聚合指标；客户端仅当前账号短期缓存  
**Testing**: Python unittest；JUnit 4；Compose UI Test  
**Platform**: Android API 24–35 + Flask API  
**Performance**: 95% 请求 3 秒内返回 personalized/fallback/empty；固定输入连续 5 次 Top-10 完全稳定  
**Constraints**: 依赖 005 已确认收藏与 004 已购 ID；不使用浏览历史/位置/其他用户私有收藏；广告不混入；推荐失败不阻断核心流程  
**Scale**: 每用户数百收藏、候选数千、返回 10–20 项

## Constitution Check

### Pre-design gate

| Principle | Result | Evidence |
|---|---|---|
| 分层与数据流 | PASS | 独立 RecommendationRepository/API/Domain/UI contract。 |
| 票务正确性 | PASS | 只读已购 ID，不修改票务状态。 |
| 账号与隐私 | PASS | 个性化只用当前 Bearer 用户的 confirmed favorites；缓存按账号清理。 |
| 推荐透明与用户中心 | PASS | 真实原因、备用标签、取消收藏生效、确定性排序和指标均为核心设计。 |
| 可恢复体验 | PASS | loading/personalized/fallback/empty/error 明确；失败降级。 |

### Post-design gate

- **PASS**：数据模型区分 PERSONALIZED、FALLBACK_POPULAR、FALLBACK_NOW_PLAYING。
- **PASS**：原因只能由实际匹配特征生成；否则使用不声称个性化的通用说明。
- **PASS**：契约允许游客但不会读取私有信号；账号切换先清缓存。
- **PASS**：任务覆盖稳定性、隔离、降级、解释、转化指标和核心流程不受阻。

无宪章例外；005 是个性化推荐的强制前置功能。

## Architecture and Algorithm

1. 后端 `movie_catalog` 保存当前可浏览电影的公开特征（类型、主题标签、语言、演职人员可选）、热度、新鲜度和可用状态；通过明确刷新任务更新。
2. 有效登录用户读取 005 `isFavorite=true` 的已确认收藏。至少 2 部收藏且至少产生一个特征权重时建立 profile；否则走 fallback。
3. 特征权重为收藏中出现频率经归一化的确定性向量。候选分数由类型/主题重合、热度和新鲜度组成，权重为版本化服务端配置；不加入随机噪声。
4. 默认从“新发现”中排除已收藏、004 已购买及不可用电影；去重后按 score 降序、popularity 降序、movieId 升序稳定排序。
5. 原因取贡献最高的真实共享特征，例如“与你收藏的科幻电影相似”；无法可靠生成则该结果不进入 personalized，改用诚实 fallback 标签。
6. 游客、收藏不足、特征不足、超时/错误或空个性化结果使用可用电影的热门/正在上映稳定排序，并返回 `fallbackReason`。
7. 推荐响应包含 recommendationId、algorithmVersion、favoriteRevision、catalogVersion 和每项原因。收藏 confirmed revision 变化使旧缓存失效；pending 状态显示“偏好同步中”并继续使用最后 confirmed revision 或 fallback。
8. 客户端只渲染服务端 source/reason；切号/退出先清除内存推荐。点击事件用 recommendationId+movieId 上报；收藏/购票转化由相应服务端事件关联聚合。

## Privacy, Metrics, and Failure Isolation

- 推荐计算按 userId 隔离，但指标存储以 recommendationId/algorithmVersion/source/action 聚合；不记录原始收藏列表、原因源电影标题、令牌或外部身份。
- 失败、超时或数据缺失只返回 fallback/empty 和可重试状态，绝不影响电影目录、收藏、场次或订单接口。
- 算法版本和权重由后端配置并记录；回滚只切换版本，不更改收藏。

## Migration and Rollback

- 新增 `movie_catalog`、`recommendation_runs`、`recommendation_items`、`recommendation_metrics`；导入现有公开电影数据时保留稳定 movieId。
- 删除 `MoviesRepositoryImpl.calculateRecommendations()` 的随机逻辑、可调客户端权重和跨职责 flows；不迁移其结果为权威缓存。
- 回滚推荐服务时客户端继续显示通用电影列表；不影响 001–005。

## Verification Strategy

- 后端测试：账号隔离、confirmed-only、阈值/特征不足、确定性 Top-10、去重/排除、真实原因、fallback、版本与指标脱敏。
- Android 测试：五种状态、来源标签、pending 提示、切号零闪现、重试与核心流程不阻断。
- 验收：新增/取消收藏下一刷新 100% 生效；20 人中 80% 能辨别个性化与备用。

## Project Structure

```text
specs/006-movie-recommendations/{plan.md,research.md,data-model.md,quickstart.md,tasks.md,contracts/recommendations-api.yaml}
backend/{app.py,recommendations.py,test_recommendations.py,test_recommendation_isolation.py}
app/src/main/java/me/ibrahim/moviesapp/compose/
├── data/{dto/RecommendationDto.kt,mappers/RecommendationMapper.kt,network/RecommendationRemoteApi.kt,repository/RecommendationRepositoryImpl.kt}
├── domain/recommendation/{Recommendation.kt,RecommendationRepository.kt}
├── presentation/recommendation/{RecommendationContract.kt,RecommendationSection.kt,RecommendationViewModel.kt}
└── di/{CoreModule.kt,NetworkModule.kt,RepositoryModule.kt}
```

**Structure Decision**: 推荐算法和解释在后端，Android 只管理明确状态与展示；保持单 app/单 Flask 服务，不引入 ML 框架。

## Hybrid ranking amendment

The recommendation service now uses two stages. Stage 1 computes content similarity from synchronized Maoyan `cat` and `star` features, with bounded popularity (`wish` + `sc`) and release-date freshness. Stage 2 computes item-item collaborative similarity from account-scoped confirmed favorites, paid orders, and recommendation clicks. The collaborative score is additive and deterministic; when cross-account behavior is insufficient it is zero and Stage 1 remains the complete fallback.

## Complexity Tracking

无宪章豁免。版本化内容相似度足以满足当前需求，比黑盒模型更可解释、可测和可回滚。
